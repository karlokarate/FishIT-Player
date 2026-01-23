package com.fishit.player.feature.detail.model

/**
 * UI model for a playback source in detail screen.
 *
 * **Architecture (v2 - INV-6 compliant):**
 * - Combines data from NX_WorkSourceRef + NX_WorkVariant into UI-friendly format.
 * - Contains exactly what SourceSelection and PlaybackLauncher need.
 * - No logic, no methods, pure data.
 *
 * **Why combined?**
 * The UI doesn't care about the NX separation of SourceRef vs Variant.
 * It needs: "which sources can I play, and what quality are they?"
 */
data class DetailSourceInfo(
    /** Unique source identifier (NX_WorkSourceRef.sourceKey) */
    val sourceKey: String,
    /** Source type: TELEGRAM, XTREAM, LOCAL */
    val sourceType: String,
    /** Human-readable label for UI (e.g., "Telegram: Movie Group") */
    val sourceLabel: String,
    /** Account key for multi-account display */
    val accountKey: String,
    /** Quality tag: SD, 720p, 1080p, 4K */
    val qualityTag: String?,
    /** Resolution width (for quality comparison) */
    val width: Int?,
    /** Resolution height (for quality comparison) */
    val height: Int?,
    /** Video codec (h264, h265, etc.) */
    val videoCodec: String?,
    /** Container format (mp4, mkv, etc.) */
    val containerFormat: String?,
    /** File size in bytes */
    val fileSizeBytes: Long?,
    /** Language tag */
    val language: String?,
    /** Priority for auto-selection (higher = preferred) */
    val priority: Int,
    /** True if source is currently available */
    val isAvailable: Boolean,
    /** True if playback hints are present and ready */
    val isPlaybackReady: Boolean,
    /**
     * Playback hints for PlaybackSourceFactory.
     *
     * Contains source-specific data needed for URL construction:
     * - Xtream: containerExtension, vodId, episodeId
     * - Telegram: chatId, messageId, remoteId
     *
     * UI doesn't interpret these - just passes them to PlayMediaUseCase.
     */
    val playbackHints: Map<String, String>,
) {
    /** Quality display string for UI (e.g., "1080p H.265") */
    val qualityDisplay: String
        get() = buildString {
            qualityTag?.let { append(it) }
            videoCodec?.let {
                if (isNotEmpty()) append(" ")
                append(it.uppercase())
            }
        }.ifEmpty { "Unknown" }

    /** File size display string (e.g., "2.4 GB") */
    val fileSizeDisplay: String?
        get() = fileSizeBytes?.let { bytes ->
            when {
                bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
                bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1_000_000.0)
                else -> "%.0f KB".format(bytes / 1_000.0)
            }
        }

    /** True if this is a Telegram source */
    val isTelegram: Boolean
        get() = sourceType == "TELEGRAM"

    /** True if this is an Xtream source */
    val isXtream: Boolean
        get() = sourceType == "XTREAM"
}
