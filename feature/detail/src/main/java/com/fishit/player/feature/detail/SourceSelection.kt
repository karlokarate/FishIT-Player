package com.fishit.player.feature.detail

import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.ids.PipelineItemId
import com.fishit.player.core.model.repository.CanonicalMediaWithSources
import com.fishit.player.core.model.repository.CanonicalResumeInfo

/**
 * Pure functions for deterministic source selection from media.sources (SSOT).
 *
 * **Architecture Contract:**
 * - Source selection MUST be derived from current state.media.sources at the moment of use.
 * - No cached/snapshot MediaSourceRef fields in state.
 * - This eliminates race conditions where async enrichment updates sources but stale
 *   selectedSource retains outdated playbackHints.
 *
 * **Selection Priority:**
 * 1. Explicit user selection (by sourceKey)
 * 2. Last resumed source (if available and valid)
 * 3. Highest priority source
 * 4. Best quality source
 * 5. First available source (stable order)
 */
object SourceSelection {
    /**
     * Resolve the active source for playback/display from current media.sources.
     *
     * This is the ONLY entry point for getting a MediaSourceRef for playback.
     * Always call this with the current state.media to get up-to-date playbackHints.
     *
     * @param media Current canonical media with all sources (from state.media)
     * @param selectedSourceKey Optional user-selected sourceKey (for manual selection)
     * @param resume Optional resume info (for "last used" preference)
     * @return The resolved MediaSourceRef, or null if no sources available
     */
    fun resolveActiveSource(
        media: CanonicalMediaWithSources?,
        selectedSourceKey: PipelineItemId? = null,
        resume: CanonicalResumeInfo? = null,
    ): MediaSourceRef? {
        val sources = media?.sources ?: return null
        if (sources.isEmpty()) return null

        // Priority 1: Explicit user selection (by key)
        if (selectedSourceKey != null) {
            val selected = sources.find { it.sourceId == selectedSourceKey }
            if (selected != null) return selected
            // User selection is stale (source no longer exists), fall through to auto-selection
        }

        // Priority 2: Last resumed source (if still available)
        resume?.lastSourceId?.let { lastId ->
            val lastSource = sources.find { it.sourceId == lastId }
            if (lastSource != null) return lastSource
        }

        // Priority 3: By priority value (higher = better)
        val byPriority = sources.maxByOrNull { it.priority }
        if (byPriority != null && byPriority.priority > 0) {
            return byPriority
        }

        // Priority 4: By quality (resolution)
        val byQuality =
            sources
                .filter { it.quality?.resolution != null }
                .maxByOrNull { it.quality!!.resolution!! }
        if (byQuality != null) return byQuality

        // Priority 5: First source (stable order)
        return sources.first()
    }

    /**
     * Check if the active source has all required playbackHints for playback.
     *
     * @param source The resolved source to validate
     * @return List of missing hint keys (empty if all required hints present)
     */
    fun getMissingPlaybackHints(source: MediaSourceRef?): List<String> {
        if (source == null) return listOf("source")

        val missing = mutableListOf<String>()

        when (source.sourceType) {
            SourceType.XTREAM -> {
                val hints = source.playbackHints
                val sourceId = source.sourceId.value

                when {
                    // VOD
                    sourceId.startsWith("xtream:vod:") -> {
                        // VOD requires containerExtension for correct URL building
                        if (hints[PlaybackHintKeys.Xtream.CONTAINER_EXT].isNullOrBlank()) {
                            missing.add(PlaybackHintKeys.Xtream.CONTAINER_EXT)
                        }
                        // vodId is extractable from sourceId, not strictly required in hints
                    }
                    // Live
                    sourceId.startsWith("xtream:live:") -> {
                        // Live streams are usually HLS/TS, container extension not critical
                        // streamId is in sourceId
                    }
                    // Series Episode
                    sourceId.startsWith("xtream:episode:") -> {
                        // Episodes require episodeId for direct playback
                        if (hints[PlaybackHintKeys.Xtream.EPISODE_ID].isNullOrBlank()) {
                            missing.add(PlaybackHintKeys.Xtream.EPISODE_ID)
                        }
                        // containerExtension preferred but not always critical
                    }
                }
            }
            SourceType.TELEGRAM -> {
                val hints = source.playbackHints
                // Telegram requires remoteId for file resolution
                if (hints[PlaybackHintKeys.Telegram.REMOTE_ID].isNullOrBlank() &&
                    hints[PlaybackHintKeys.Telegram.FILE_ID].isNullOrBlank()
                ) {
                    missing.add(PlaybackHintKeys.Telegram.REMOTE_ID)
                }
            }
            SourceType.IO, SourceType.LOCAL -> {
                // Local files: path is in sourceId, no additional hints needed
            }
            else -> {
                // Other source types: no specific validation
            }
        }

        return missing
    }

    /**
     * Check if a source is ready for immediate playback (all required hints present).
     */
    fun isPlaybackReady(source: MediaSourceRef?): Boolean = getMissingPlaybackHints(source).isEmpty()
}
