package com.fishit.player.feature.detail.model

import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.ids.PipelineItemId

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
        // FIX: Case-insensitive comparison - DB stores lowercase, some legacy may be uppercase
        get() = sourceType.equals("telegram", ignoreCase = true)

    /** True if this is an Xtream source */
    val isXtream: Boolean
        // FIX: Case-insensitive comparison - DB stores lowercase, some legacy may be uppercase
        get() = sourceType.equals("xtream", ignoreCase = true)
}

/**
 * Convert DetailSourceInfo to MediaSourceRef for playback.
 *
 * **CRITICAL FIX:** Maps sourceType String to SourceType enum correctly.
 * This fixes the bug where sourceType was UNKNOWN during playback.
 */
fun DetailSourceInfo.toMediaSourceRef(): MediaSourceRef {
    return MediaSourceRef(
        sourceType = mapSourceTypeStringToEnum(sourceType),
        sourceId = PipelineItemId(sourceKey),
        sourceLabel = sourceLabel,
        quality = null, // TODO: Build from qualityTag/width/height if needed
        languages = null, // TODO: Build from language if needed
        format = null, // TODO: Build from containerFormat if needed
        sizeBytes = fileSizeBytes,
        durationMs = null, // Not available in DetailSourceInfo
        priority = priority,
        playbackHints = playbackHints,
    )
}

/**
 * Maps sourceType String to SourceType enum.
 *
 * **FIX:** This is the missing piece that caused sourceType=UNKNOWN bug!
 */
private fun mapSourceTypeStringToEnum(sourceTypeString: String): SourceType {
    return when (sourceTypeString.lowercase()) {
        "telegram" -> SourceType.TELEGRAM
        "xtream" -> SourceType.XTREAM
        "io", "local" -> SourceType.IO
        "audiobook" -> SourceType.AUDIOBOOK
        "plex" -> SourceType.PLEX
        else -> SourceType.UNKNOWN
    }
}

