package com.fishit.player.core.model

/**
 * Context for initiating playback in FishIT Player v2.
 *
 * This is the pipeline-agnostic representation of what to play.
 * Each pipeline converts its domain models into a [PlaybackContext]
 * before handing off to the internal player.
 *
 * @property type The type of content being played.
 * @property uri The primary URI/URL for playback (may be resolved by source resolver).
 * @property title Display title for the content.
 * @property subtitle Optional subtitle (e.g., episode info, channel name).
 * @property posterUrl Optional poster/thumbnail URL.
 * @property contentId Generic content identifier (pipeline-specific interpretation).
 * @property seriesId Series ID if applicable (for resume tracking).
 * @property seasonNumber Season number if applicable.
 * @property episodeNumber Episode number if applicable.
 * @property startPositionMs Starting position in milliseconds (for resume).
 * @property isKidsContent Whether this content is kids-appropriate.
 * @property profileId Current profile ID for tracking.
 * @property extras Additional pipeline-specific metadata.
 */
data class PlaybackContext(
    val type: PlaybackType,
    val uri: String,
    val title: String,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val contentId: String? = null,
    val seriesId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val startPositionMs: Long = 0L,
    val isKidsContent: Boolean = false,
    val profileId: Long? = null,
    val extras: Map<String, String> = emptyMap(),
) {
    companion object {
        /**
         * Creates a simple test context for debugging.
         */
        fun testVod(
            url: String,
            title: String = "Test Video",
        ): PlaybackContext =
            PlaybackContext(
                type = PlaybackType.VOD,
                uri = url,
                title = title,
            )
    }
}
