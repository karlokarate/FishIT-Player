package com.fishit.player.playback.xtream

import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.SourceType
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamApiConfig
import com.fishit.player.infra.transport.xtream.XtreamUrlBuilder
import com.fishit.player.playback.domain.DataSourceType
import com.fishit.player.playback.domain.PlaybackSource
import com.fishit.player.playback.domain.PlaybackSourceException
import com.fishit.player.playback.domain.PlaybackSourceFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating Xtream playback sources.
 *
 * Converts a [PlaybackContext] with [SourceType.XTREAM] into a [PlaybackSource]
 * with the proper stream URL and headers.
 *
 * **Supported Content Types:**
 * - Live TV (HLS/MPEG-TS)
 * - VOD (MP4/MKV/etc.)
 * - Series Episodes (MP4/MKV/etc.)
 *
 * **URL Building:**
 * Uses [XtreamUrlBuilder] from transport layer to construct authenticated URLs.
 *
 * **Expected PlaybackContext.extras keys:**
 * - `contentType`: "live" | "vod" | "series"
 * - `streamId` or `vodId` or `seriesId`: Content identifier
 * - `episodeId`: For series episodes
 * - `seasonNumber`: For series episodes (fallback)
 * - `episodeNumber`: For series episodes (fallback)
 * - `containerExtension`: File extension hint (mp4, mkv, m3u8, ts)
 * - `serverHost`, `serverPort`, `username`, `password`: Server credentials
 *
 * **Architecture:**
 * - Does NOT store Xtream configs (stateless factory)
 * - Config must be passed via PlaybackContext.extras or resolved from repository
 * - Returns [PlaybackSource] with appropriate [DataSourceType]
 */
@Singleton
class XtreamPlaybackSourceFactoryImpl @Inject constructor() : PlaybackSourceFactory {

    companion object {
        private const val TAG = "XtreamPlaybackFactory"

        // Extra keys
        const val EXTRA_CONTENT_TYPE = "contentType"
        const val EXTRA_STREAM_ID = "streamId"
        const val EXTRA_VOD_ID = "vodId"
        const val EXTRA_SERIES_ID = "seriesId"
        const val EXTRA_EPISODE_ID = "episodeId"
        const val EXTRA_SEASON_NUMBER = "seasonNumber"
        const val EXTRA_EPISODE_NUMBER = "episodeNumber"
        const val EXTRA_CONTAINER_EXT = "containerExtension"
        const val EXTRA_SERVER_HOST = "serverHost"
        const val EXTRA_SERVER_PORT = "serverPort"
        const val EXTRA_SERVER_SCHEME = "serverScheme"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"

        // Content type values
        const val CONTENT_TYPE_LIVE = "live"
        const val CONTENT_TYPE_VOD = "vod"
        const val CONTENT_TYPE_SERIES = "series"
    }

    override fun supports(sourceType: SourceType): Boolean {
        return sourceType == SourceType.XTREAM
    }

    override suspend fun createSource(context: PlaybackContext): PlaybackSource {
        UnifiedLog.d(TAG, "Creating source for: ${context.canonicalId}")

        // If we already have a direct HTTP URI, use it
        val existingUri = context.uri
        if (existingUri != null && (existingUri.startsWith("http://") || existingUri.startsWith("https://"))) {
            UnifiedLog.d(TAG, "Using existing URI: $existingUri")
            return PlaybackSource(
                uri = existingUri,
                mimeType = determineMimeType(context),
                headers = context.headers,
                dataSourceType = DataSourceType.DEFAULT
            )
        }

        // Build URL from context extras
        val config = extractConfig(context)
            ?: throw PlaybackSourceException(
                message = "Missing Xtream server configuration in context extras",
                sourceType = SourceType.XTREAM
            )

        val urlBuilder = XtreamUrlBuilder(config)

        val contentType = context.extras[EXTRA_CONTENT_TYPE] ?: guessContentType(context)
        val streamUrl = when (contentType) {
            CONTENT_TYPE_LIVE -> buildLiveUrl(urlBuilder, context)
            CONTENT_TYPE_VOD -> buildVodUrl(urlBuilder, context)
            CONTENT_TYPE_SERIES -> buildSeriesUrl(urlBuilder, context)
            else -> throw PlaybackSourceException(
                message = "Unknown content type: $contentType",
                sourceType = SourceType.XTREAM
            )
        }

        UnifiedLog.d(TAG, "Built stream URL for $contentType content")

        return PlaybackSource(
            uri = streamUrl,
            mimeType = determineMimeType(context, contentType),
            headers = buildHeaders(config),
            dataSourceType = DataSourceType.DEFAULT
        )
    }

    /**
     * Extracts Xtream API config from PlaybackContext.extras.
     */
    private fun extractConfig(context: PlaybackContext): XtreamApiConfig? {
        val host = context.extras[EXTRA_SERVER_HOST] ?: return null
        val username = context.extras[EXTRA_USERNAME] ?: return null
        val password = context.extras[EXTRA_PASSWORD] ?: return null

        val port = context.extras[EXTRA_SERVER_PORT]?.toIntOrNull()
        val scheme = context.extras[EXTRA_SERVER_SCHEME] ?: "http"

        return XtreamApiConfig(
            host = host,
            port = port,
            scheme = scheme,
            username = username,
            password = password
        )
    }

    /**
     * Guess content type from context if not explicitly set.
     */
    private fun guessContentType(context: PlaybackContext): String {
        return when {
            context.extras.containsKey(EXTRA_STREAM_ID) -> CONTENT_TYPE_LIVE
            context.extras.containsKey(EXTRA_VOD_ID) -> CONTENT_TYPE_VOD
            context.extras.containsKey(EXTRA_SERIES_ID) || 
                context.extras.containsKey(EXTRA_EPISODE_ID) -> CONTENT_TYPE_SERIES
            context.isLive -> CONTENT_TYPE_LIVE
            else -> CONTENT_TYPE_VOD
        }
    }

    /**
     * Build live stream URL.
     */
    private fun buildLiveUrl(urlBuilder: XtreamUrlBuilder, context: PlaybackContext): String {
        val streamId = context.extras[EXTRA_STREAM_ID]?.toIntOrNull()
            ?: throw PlaybackSourceException(
                message = "Missing streamId for live content",
                sourceType = SourceType.XTREAM
            )

        val extension = context.extras[EXTRA_CONTAINER_EXT]
        return urlBuilder.liveUrl(streamId, extension)
    }

    /**
     * Build VOD stream URL.
     */
    private fun buildVodUrl(urlBuilder: XtreamUrlBuilder, context: PlaybackContext): String {
        val vodId = context.extras[EXTRA_VOD_ID]?.toIntOrNull()
            ?: context.extras[EXTRA_STREAM_ID]?.toIntOrNull()
            ?: throw PlaybackSourceException(
                message = "Missing vodId for VOD content",
                sourceType = SourceType.XTREAM
            )

        val extension = context.extras[EXTRA_CONTAINER_EXT]
        return urlBuilder.vodUrl(vodId, extension)
    }

    /**
     * Build series episode URL.
     */
    private fun buildSeriesUrl(urlBuilder: XtreamUrlBuilder, context: PlaybackContext): String {
        val seriesId = context.extras[EXTRA_SERIES_ID]?.toIntOrNull()
            ?: throw PlaybackSourceException(
                message = "Missing seriesId for series content",
                sourceType = SourceType.XTREAM
            )

        val episodeId = context.extras[EXTRA_EPISODE_ID]?.toIntOrNull()
        val seasonNumber = context.extras[EXTRA_SEASON_NUMBER]?.toIntOrNull() ?: 1
        val episodeNumber = context.extras[EXTRA_EPISODE_NUMBER]?.toIntOrNull() ?: 1
        val extension = context.extras[EXTRA_CONTAINER_EXT]

        return urlBuilder.seriesEpisodeUrl(
            seriesId = seriesId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeId = episodeId,
            containerExtension = extension
        )
    }

    /**
     * Determine MIME type from context and content type.
     */
    private fun determineMimeType(context: PlaybackContext, contentType: String? = null): String? {
        // Check extras first
        context.extras["mimeType"]?.let { return it }

        // Determine from extension
        val extension = context.extras[EXTRA_CONTAINER_EXT]?.lowercase()
        return when (extension) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "m3u8" -> "application/x-mpegURL"
            "ts" -> "video/mp2t"
            else -> when (contentType) {
                CONTENT_TYPE_LIVE -> "application/x-mpegURL" // Assume HLS for live
                else -> null // Let ExoPlayer detect
            }
        }
    }

    /**
     * Build HTTP headers for authenticated streams.
     */
    private fun buildHeaders(config: XtreamApiConfig): Map<String, String> {
        return mapOf(
            "User-Agent" to "FishIT-Player/2.0"
        )
    }
}
