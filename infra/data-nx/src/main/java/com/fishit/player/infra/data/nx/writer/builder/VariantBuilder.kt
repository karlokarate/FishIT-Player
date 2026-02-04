/**
 * VariantBuilder - Constructs NX_WorkVariant entities.
 *
 * Extracts playback variant construction logic from NxCatalogWriter to reduce CC.
 * Handles:
 * - Variant key construction
 * - Container extraction from playback hints
 *
 * CC: ~4 (well below target of 15)
 */
package com.fishit.player.infra.data.nx.writer.builder

import com.fishit.player.core.model.repository.NxWorkVariantRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds NX_WorkVariant entities.
 */
@Singleton
class VariantBuilder @Inject constructor() {

    /**
     * Build a Variant entity.
     *
     * @param variantKey The computed variant key
     * @param workKey The work key (foreign key to NX_Work)
     * @param sourceKey The source key (foreign key to NX_WorkSourceRef)
     * @param playbackHints Map of playback hints from pipeline
     * @param durationMs Duration in milliseconds
     * @param now Current timestamp
     * @return Variant entity ready for upsert
     */
    fun build(
        variantKey: String,
        workKey: String,
        sourceKey: String,
        playbackHints: Map<String, String>,
        durationMs: Long?,
        now: Long = System.currentTimeMillis(),
    ): NxWorkVariantRepository.Variant {
        return NxWorkVariantRepository.Variant(
            variantKey = variantKey,
            workKey = workKey,
            sourceKey = sourceKey,
            label = "Original", // Default label - could be enhanced with quality info
            isDefault = true,
            container = extractContainerFromHints(playbackHints),
            durationMs = durationMs,
            playbackHints = playbackHints,
            createdAtMs = now,
            updatedAtMs = now,
        )
    }

    /**
     * Extract container format from playback hints.
     *
     * Matches NxCatalogWriter logic with proper key mapping and normalization.
     * Common keys: "xtream.containerExtension", "containerExtension", "extension"
     */
    private fun extractContainerFromHints(hints: Map<String, String>): String? {
        // Check all possible keys (Xtream uses "xtream.containerExtension")
        val ext = hints["xtream.containerExtension"]
            ?: hints["containerExtension"]
            ?: hints["extension"]
        return when (ext?.lowercase()) {
            "mp4" -> "mp4"
            "mkv" -> "mkv"
            "avi" -> "avi"
            "webm" -> "webm"
            "ts" -> "ts"
            "m3u8" -> "hls"  // Normalize m3u8 to hls
            "mov" -> "mov"
            "wmv" -> "wmv"
            "flv" -> "flv"
            else -> ext?.lowercase()?.takeIf { it.isNotBlank() }  // Pass through unknown formats
        }
    }
}
