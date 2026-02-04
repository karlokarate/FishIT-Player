package com.fishit.player.playback.domain.defaults

import com.fishit.player.core.model.PlaybackType
import com.fishit.player.core.model.ResumePoint
import com.fishit.player.core.model.repository.NxWorkUserStateRepository
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.playback.domain.ResumeManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NX-backed ResumeManager implementation.
 *
 * Uses [NxWorkUserStateRepository] for persistence, providing:
 * - Cross-pipeline resume: same movie from Telegram/Xtream shares position via workKey
 * - Profile-aware: each profile has independent resume positions via profileKey
 * - Cloud-sync ready: cloudSyncState is automatically managed by repository
 *
 * **Note:** This is the ONLY ResumeManager implementation. Legacy stub implementations
 * (DefaultResumeManager, ObxResumeManager) have been removed per AUDIT_LEGACY_WILDWUCHS_2026.md.
 */
@Singleton
class NxResumeManager
    @Inject
    constructor(
        private val userStateRepository: NxWorkUserStateRepository,
    ) : ResumeManager {
        companion object {
            private const val TAG = "NxResumeManager"

            /**
             * Default profile key until multi-profile support is added.
             * Matches NX convention: "default" string key instead of numeric ID.
             */
            private const val DEFAULT_PROFILE_KEY = "default"
        }

        override suspend fun getResumePoint(contentId: String): ResumePoint? =
            try {
                val workKey = normalizeToWorkKey(contentId)
                val userState = userStateRepository.get(
                    profileKey = DEFAULT_PROFILE_KEY,
                    workKey = workKey,
                )

                userState?.takeIf { it.resumePositionMs > 0 }?.let { state ->
                    ResumePoint(
                        contentId = contentId,
                        type = PlaybackType.VOD, // Default; actual type not stored in user state
                        positionMs = state.resumePositionMs,
                        durationMs = state.totalDurationMs,
                        updatedAt = state.updatedAtMs,
                        profileId = 1L, // Legacy compatibility; NX uses string keys
                    )
                }
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "Failed to get resume for $contentId: ${e.message}" }
                null
            }

        override suspend fun saveResumePoint(
            context: PlaybackContext,
            positionMs: Long,
            durationMs: Long,
        ) {
            try {
                val workKey = normalizeToWorkKey(context.canonicalId)

                userStateRepository.updateResumePosition(
                    profileKey = DEFAULT_PROFILE_KEY,
                    workKey = workKey,
                    positionMs = positionMs,
                    durationMs = durationMs,
                )

                UnifiedLog.d(TAG) {
                    "Saved resume: ${context.canonicalId} at ${positionMs}ms / ${durationMs}ms"
                }
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Failed to save resume for ${context.canonicalId}" }
            }
        }

        override suspend fun clearResumePoint(contentId: String) {
            try {
                val workKey = normalizeToWorkKey(contentId)
                userStateRepository.clearResumePosition(
                    profileKey = DEFAULT_PROFILE_KEY,
                    workKey = workKey,
                )
                UnifiedLog.d(TAG) { "Cleared resume for $contentId" }
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "Failed to clear resume for $contentId: ${e.message}" }
            }
        }

        override suspend fun getAllResumePoints(): List<ResumePoint> =
            try {
                val continueWatching = userStateRepository.observeContinueWatching(
                    profileKey = DEFAULT_PROFILE_KEY,
                    limit = 50,
                ).first()

                continueWatching.map { state ->
                    ResumePoint(
                        contentId = state.workKey,
                        type = PlaybackType.VOD, // Default; NX doesn't distinguish in user state
                        positionMs = state.resumePositionMs,
                        durationMs = state.totalDurationMs,
                        updatedAt = state.updatedAtMs,
                        profileId = 1L, // Legacy compatibility
                    )
                }
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Failed to get all resume points" }
                emptyList()
            }

        /**
         * Normalize content ID to NX workKey format.
         *
         * The NX system uses workKey as the canonical identifier.
         * If the contentId has a legacy prefix like "movie:" or "episode:",
         * we strip it since NX workKeys don't use this prefix.
         *
         * Examples:
         * - "movie:tmdb-12345" → "tmdb-12345"
         * - "episode:tmdb-12345-s01e01" → "tmdb-12345-s01e01"
         * - "tmdb-12345" → "tmdb-12345" (already normalized)
         */
        private fun normalizeToWorkKey(contentId: String): String {
            val parts = contentId.split(":", limit = 2)
            return if (parts.size == 2 && parts[0] in listOf("movie", "episode")) {
                parts[1]
            } else {
                contentId
            }
        }
    }
