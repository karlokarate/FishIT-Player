package com.fishit.player.internal.source

import com.fishit.player.core.model.MediaVariant
import com.fishit.player.core.model.NormalizedMedia
import com.fishit.player.core.model.PipelineIdTag
import com.fishit.player.core.model.VariantHealthStore
import com.fishit.player.core.model.VariantPreferences
import com.fishit.player.core.model.VariantSelector
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.PlaybackError
import com.fishit.player.infra.logging.UnifiedLog

/**
 * Orchestrates variant-based playback with automatic fallback.
 *
 * **Purpose:** When playback fails for the primary variant, automatically try the next best variant
 * until one succeeds or all are exhausted.
 *
 * **Algorithm:**
 * 1. Sort variants by preference using [VariantSelector]
 * 2. Filter out known dead variants via [VariantHealthStore]
 * 3. Try to create PlaybackContext for best variant
 * 4. On failure, mark variant unavailable and try next
 * 5. Return success or "no available variants" error
 *
 * **Error Handling:**
 * - 404/gone errors → record hard failure, try next variant
 * - Network timeouts → retry same variant (transient)
 * - Auth errors → propagate to UI (user action needed)
 */
class VariantPlaybackOrchestrator(
        private val sourceResolver: PlaybackSourceResolver,
) {
    companion object {
        private const val TAG = "VariantPlayback"
    }

    /** Result of variant-based playback attempt. */
    sealed class PlaybackResult {
        /** Playback started successfully with the given context. */
        data class Success(
                val context: PlaybackContext,
                val variant: MediaVariant,
        ) : PlaybackResult()

        /** All variants failed. */
        data class AllVariantsFailed(
                val attemptedCount: Int,
                val lastError: PlaybackError?,
        ) : PlaybackResult()

        /** No variants available (all filtered out as dead). */
        data object NoVariantsAvailable : PlaybackResult()
    }

    /**
     * Attempt playback with automatic variant fallback.
     *
     * @param media The normalized media with variants
     * @param prefs User preferences for variant selection
     * @param contextBuilder Function to build PlaybackContext from variant
     * @return PlaybackResult indicating success or failure
     */
    suspend fun attemptPlayback(
            media: NormalizedMedia,
            prefs: VariantPreferences = VariantPreferences.default(),
            contextBuilder: suspend (MediaVariant) -> PlaybackContext,
    ): PlaybackResult {
        // Sort variants by preference
        val sortedVariants = VariantSelector.sortByPreference(media.variants, prefs)

        // Filter out dead variants
        val candidateVariants =
                sortedVariants.filter { variant ->
                    variant.available && !VariantHealthStore.isPermanentlyDead(variant.sourceKey)
                }

        if (candidateVariants.isEmpty()) {
            UnifiedLog.w(TAG, "No available variants for ${media.globalId}")
            return PlaybackResult.NoVariantsAvailable
        }

        var lastError: PlaybackError? = null
        var attemptedCount = 0

        for (variant in candidateVariants) {
            attemptedCount++
            UnifiedLog.d(
                    TAG,
                    "Attempting variant $attemptedCount/${candidateVariants.size}: " +
                            "${variant.sourceKey.pipeline.code}:${variant.qualityTag}"
            )

            try {
                val context = contextBuilder(variant)

                // Attempt source resolution
                val source = sourceResolver.resolve(context)

                UnifiedLog.i(TAG, "Playback started with variant: ${variant.toDisplayLabel()}")

                return PlaybackResult.Success(context, variant)
            } catch (e: Exception) {
                lastError = categorizeError(e, variant)

                when (lastError.type) {
                    PlaybackError.ErrorType.SOURCE_NOT_FOUND -> {
                        // Hard failure - record and try next
                        UnifiedLog.w(
                                TAG,
                                "Variant ${variant.sourceKey} failed with hard error: ${e.message}"
                        )
                        VariantHealthStore.recordHardFailure(variant.sourceKey)
                        variant.available = false
                    }
                    PlaybackError.ErrorType.NETWORK, PlaybackError.ErrorType.TIMEOUT -> {
                        // Transient - still try next variant
                        UnifiedLog.w(
                                TAG,
                                "Variant ${variant.sourceKey} failed with network error: ${e.message}"
                        )
                    }
                    PlaybackError.ErrorType.PERMISSION -> {
                        // Auth error - propagate immediately
                        UnifiedLog.e(TAG, "Auth required for ${variant.sourceKey}")
                        // Don't try other variants - auth issue is account-level
                        return PlaybackResult.AllVariantsFailed(attemptedCount, lastError)
                    }
                    else -> {
                        UnifiedLog.w(
                                TAG,
                                "Variant ${variant.sourceKey} failed with error: ${e.message}"
                        )
                    }
                }
            }
        }

        UnifiedLog.e(TAG, "All $attemptedCount variants failed for ${media.globalId}")
        return PlaybackResult.AllVariantsFailed(attemptedCount, lastError)
    }

    /** Categorize an exception into a PlaybackError. */
    private fun categorizeError(e: Exception, variant: MediaVariant): PlaybackError {
        val message = e.message?.lowercase() ?: ""

        return when {
            message.contains("404") ||
                    message.contains("not found") ||
                    message.contains("file not found") ||
                    message.contains("no such file") -> {
                PlaybackError.sourceNotFound("File not found: ${e.message}")
            }
            message.contains("401") ||
                    message.contains("403") ||
                    message.contains("unauthorized") ||
                    message.contains("forbidden") -> {
                PlaybackError(
                        type = PlaybackError.ErrorType.PERMISSION,
                        message = "Auth required: ${e.message}",
                        isRetryable = false,
                )
            }
            message.contains("timeout") ||
                    message.contains("connection") ||
                    message.contains("network") -> {
                PlaybackError.network("Network error: ${e.message}", isRetryable = true)
            }
            message.contains("moov") || message.contains("mp4") -> {
                // Telegram-specific: moov not ready
                if (variant.sourceKey.pipeline == PipelineIdTag.TELEGRAM) {
                    PlaybackError(
                            type = PlaybackError.ErrorType.SOURCE_SPECIFIC,
                            message = "Source not ready: ${e.message}",
                            isRetryable = true,
                    )
                } else {
                    PlaybackError.decoder("Decoding error: ${e.message}")
                }
            }
            else -> PlaybackError.unknown("Unknown error: ${e.message}")
        }
    }
}
