package com.fishit.player.core.playermodel

import com.fishit.player.core.model.SourceType

/**
 * Context for initiating playback in FishIT Player v2.
 *
 * This is the **source-agnostic** representation of what to play.
 * Each source (Telegram, Xtream, File) converts its domain models
 * into a [PlaybackContext] before handing off to the internal player.
 *
 * **Design Principles:**
 * - No source-specific fields (no telegramChatId, no xtreamVodId)
 * - No profile/preference data (Domain layer provides that separately)
 * - No TMDB/series metadata (that's normalization, not playback)
 *
 * @property canonicalId Unique identifier across all sources (e.g., "telegram:123:456", "xtream:vod:789")
 * @property sourceType The source type for factory selection
 * @property uri Direct playback URI if known (may be resolved later by factory)
 * @property sourceKey Key for source-specific resolution (e.g., remoteId for Telegram)
 * @property title Display title for the content
 * @property subtitle Optional subtitle (e.g., episode info, channel name)
 * @property posterUrl Optional poster/thumbnail URL
 * @property headers HTTP headers for authenticated streams
 * @property startPositionMs Starting position in milliseconds (for resume)
 * @property isLive Whether this is live content (affects seeking/duration)
 * @property isSeekable Whether seeking is allowed
 * @property extras Additional source-specific metadata as key-value pairs
 */
data class PlaybackContext(
    val canonicalId: String,
    val sourceType: SourceType,
    val uri: String? = null,
    val sourceKey: String? = null,
    val title: String = "",
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val startPositionMs: Long = 0L,
    val isLive: Boolean = false,
    val isSeekable: Boolean = true,
    val extras: Map<String, String> = emptyMap(),
) {
    companion object {
        /**
         * Creates a simple test context for debugging.
         */
        fun testVod(
            url: String,
            title: String = "Test Video",
        ): PlaybackContext = PlaybackContext(
            canonicalId = "test:${url.hashCode()}",
            sourceType = SourceType.OTHER,
            uri = url,
            title = title,
        )

        /**
         * Creates a Telegram playback context.
         */
        fun telegram(
            chatId: Long,
            messageId: Long,
            fileId: Int,
            remoteId: String,
            title: String,
        ): PlaybackContext = PlaybackContext(
            canonicalId = "telegram:$chatId:$messageId",
            sourceType = SourceType.TELEGRAM,
            uri = "tg://file/$fileId?chatId=$chatId&messageId=$messageId&remoteId=$remoteId",
            sourceKey = remoteId,
            title = title,
        )

        /**
         * Creates an Xtream VOD playback context.
         */
        fun xtreamVod(
            vodId: Long,
            streamUrl: String,
            title: String,
            headers: Map<String, String> = emptyMap(),
        ): PlaybackContext = PlaybackContext(
            canonicalId = "xtream:vod:$vodId",
            sourceType = SourceType.XTREAM,
            uri = streamUrl,
            title = title,
            headers = headers,
        )

        /**
         * Creates an Xtream Live playback context.
         */
        fun xtreamLive(
            channelId: Long,
            streamUrl: String,
            title: String,
            headers: Map<String, String> = emptyMap(),
        ): PlaybackContext = PlaybackContext(
            canonicalId = "xtream:live:$channelId",
            sourceType = SourceType.XTREAM,
            uri = streamUrl,
            title = title,
            headers = headers,
            isLive = true,
            isSeekable = false,
        )
    }
}
