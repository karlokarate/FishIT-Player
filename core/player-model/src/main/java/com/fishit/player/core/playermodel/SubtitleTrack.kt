package com.fishit.player.core.playermodel

/**
 * Unique identifier for a subtitle track.
 *
 * Wraps a string ID that corresponds to Media3's track group/format indices.
 */
@JvmInline
value class SubtitleTrackId(val value: String) {
    companion object {
        /** Special ID indicating no subtitle track is selected. */
        val OFF = SubtitleTrackId("OFF")
    }
}

/** Source type for subtitle tracks. */
enum class SubtitleSourceType {
    /** Embedded in the media container (MKV, MP4, etc.). */
    EMBEDDED,

    /** External sidecar file (SRT, VTT, ASS, etc.). */
    SIDECAR,

    /** HLS/DASH manifest subtitle track. */
    MANIFEST
}

/**
 * Represents a selectable subtitle/closed caption track.
 *
 * This is a source-agnostic model used throughout the player stack. The player maps
 * Media3/ExoPlayer track groups to this type for consumption by UI and domain layers.
 *
 * @property id Unique identifier for track selection.
 * @property language BCP-47 language code (e.g., "en", "de", "es").
 * @property label Human-readable label (e.g., "English", "Deutsch (SDH)").
 * @property isDefault Whether this track is marked as default in the source.
 * @property isForced Whether this track contains forced/burned-in subtitles.
 * @property isClosedCaption Whether this is a closed caption track (vs. subtitles).
 * @property sourceType Origin of the subtitle track.
 * @property mimeType Optional MIME type (e.g., "text/vtt", "application/x-subrip").
 */
data class SubtitleTrack(
        val id: SubtitleTrackId,
        val language: String?,
        val label: String,
        val isDefault: Boolean = false,
        val isForced: Boolean = false,
        val isClosedCaption: Boolean = false,
        val sourceType: SubtitleSourceType = SubtitleSourceType.EMBEDDED,
        val mimeType: String? = null
) {
    companion object {
        /**
         * Creates a subtitle track from Media3 format data.
         *
         * @param groupIndex Track group index.
         * @param trackIndex Track index within the group.
         * @param language BCP-47 language code.
         * @param label Display label.
         * @param isDefault Default flag.
         * @param isForced Forced flag.
         * @param isClosedCaption CC flag.
         * @param mimeType MIME type.
         */
        fun fromMedia3(
                groupIndex: Int,
                trackIndex: Int,
                language: String?,
                label: String?,
                isDefault: Boolean = false,
                isForced: Boolean = false,
                isClosedCaption: Boolean = false,
                mimeType: String? = null
        ): SubtitleTrack =
                SubtitleTrack(
                        id = SubtitleTrackId("$groupIndex:$trackIndex"),
                        language = language,
                        label = label ?: language ?: "Track ${trackIndex + 1}",
                        isDefault = isDefault,
                        isForced = isForced,
                        isClosedCaption = isClosedCaption,
                        sourceType = SubtitleSourceType.EMBEDDED,
                        mimeType = mimeType
                )
    }
}
