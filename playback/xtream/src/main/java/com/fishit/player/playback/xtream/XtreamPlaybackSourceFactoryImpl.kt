package com.fishit.player.playback.xtream

import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.SourceType
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamHttpHeaders
import com.fishit.player.playback.domain.DataSourceType
import com.fishit.player.playback.domain.PlaybackSource
import com.fishit.player.playback.domain.PlaybackSourceException
import com.fishit.player.playback.domain.PlaybackSourceFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating Xtream playback sources.
 *
 * Converts a [PlaybackContext] with [SourceType.XTREAM] into a [PlaybackSource] with the proper
 * stream URL and headers.
 *
 * **Supported Content Types:**
 * - Live TV (HLS/MPEG-TS)
 * - VOD (MP4/MKV/etc.)
 * - Series Episodes (MP4/MKV/etc.)
 *
 * **URL Building (Two Paths):**
 *
 * **Primary Path (Session-Derived, Recommended):**
 * - PlaybackContext.uri == null
 * - Derives credentials from active Xtream session (XtreamApiClient)
 * - Delegates URL construction to [XtreamApiClient] methods (buildLiveUrl, buildVodUrl,
 * buildSeriesEpisodeUrl)
 * - Does NOT accept credentials via PlaybackContext.extras (security)
 * - Requires non-secret extras: contentType, streamId/vodId
 *
 * **Secondary Path (Backward Compatibility, Guarded):**
 * - PlaybackContext.uri may contain a prebuilt HTTP/HTTPS URL
 * - URL is validated for safety (must NOT contain credentials)
 * - Credential-bearing URIs are rejected by design (security)
 * - Safe URIs (e.g., CDN streams) are allowed for backward compatibility
 *
 * **Expected PlaybackContext.extras keys:**
 * - `contentType`: "live" | "vod" | "series"
 * - `streamId` or `vodId` or `seriesId`: Content identifier (non-secret)
 * - `episodeId`: For series episodes
 * - `seasonNumber`: For series episodes (fallback)
 * - `episodeNumber`: For series episodes (fallback)
 * - `containerExtension`: File extension hint (mp4, mkv, m3u8, ts)
 *
 * **Architecture:**
 * - Stateless factory with session dependency
 * - Credentials derived from XtreamApiClient.capabilities (session bootstrap)
 * - Returns [PlaybackSource] with appropriate [DataSourceType]
 * - Fails gracefully if session unavailable (e.g., no keystore)
 */
@Singleton
class XtreamPlaybackSourceFactoryImpl
@Inject
constructor(private val xtreamApiClient: XtreamApiClient) : PlaybackSourceFactory {

    companion object {
        private const val TAG = "XtreamPlaybackFactory"

        // Extra keys (NON-SECRET ONLY)
        const val EXTRA_CONTENT_TYPE = "contentType"
        const val EXTRA_STREAM_ID = "streamId"
        const val EXTRA_VOD_ID = "vodId"
        const val EXTRA_SERIES_ID = "seriesId"
        const val EXTRA_EPISODE_ID = "episodeId"
        const val EXTRA_SEASON_NUMBER = "seasonNumber"
        const val EXTRA_EPISODE_NUMBER = "episodeNumber"
        const val EXTRA_CONTAINER_EXT = "containerExtension"

        // Content type values
        const val CONTENT_TYPE_LIVE = "live"
        const val CONTENT_TYPE_VOD = "vod"
        const val CONTENT_TYPE_SERIES = "series"

        // Output format priority (policy: HLS > TS > MP4)
        private val FORMAT_PRIORITY = listOf("m3u8", "ts", "mp4")

        /**
         * Select the best output format based on server-allowed formats.
         *
         * Policy priority: m3u8 > ts > mp4
         * - m3u8 (HLS): Best for adaptive streaming, seeks, and compatibility
         * - ts (MPEG-TS): Good fallback, works on most players
         * - mp4: Only if explicitly allowed (container format, not streaming)
         *
         * @param allowedFormats Set of formats the server supports (e.g., {"m3u8", "ts"})
         * @return The best format to use
         * @throws PlaybackSourceException if no supported format is available
         */
        internal fun selectXtreamOutputExt(allowedFormats: Set<String>): String {
            if (allowedFormats.isEmpty()) {
                throw PlaybackSourceException(
                        message = "No output formats specified by server",
                        sourceType = SourceType.XTREAM
                )
            }
            val normalized = allowedFormats.map { it.lowercase().trim() }.toSet()
            return FORMAT_PRIORITY.firstOrNull { it in normalized }
                    ?: throw PlaybackSourceException(
                            message =
                                    "No supported output format. Server allows: ${allowedFormats.joinToString()}, supported: ${FORMAT_PRIORITY.joinToString()}",
                            sourceType = SourceType.XTREAM
                    )
        }
    }

    override fun supports(sourceType: SourceType): Boolean {
        return sourceType == SourceType.XTREAM
    }

    override suspend fun createSource(context: PlaybackContext): PlaybackSource {
        UnifiedLog.d(TAG) { "Creating source for: ${context.canonicalId}" }

        // Secondary Path: Safe prebuilt URI support (backward compatibility)
        val existingUri = context.uri
        if (existingUri != null &&
                        (existingUri.startsWith("http://") || existingUri.startsWith("https://"))
        ) {
            if (isSafePrebuiltXtreamUri(existingUri)) {
                UnifiedLog.d(TAG) { "Using safe prebuilt URI for playback" }
                return PlaybackSource(
                        uri = existingUri,
                        mimeType = determineMimeType(context),
                        headers = buildHeaders(context),
                        dataSourceType = DataSourceType.DEFAULT
                )
            } else {
                UnifiedLog.w(TAG) { "Rejected unsafe prebuilt Xtream URI (credentials detected)" }
                // Continue to session-derived path or fail
            }
        }

        // Primary Path: Session-derived URL building
        // Verify session is initialized
        if (xtreamApiClient.capabilities == null) {
            throw PlaybackSourceException(
                    message = "Xtream session not initialized. Please connect to Xtream first.",
                    sourceType = SourceType.XTREAM
            )
        }

        // v2 SSOT: PlaybackHintKeys.Xtream.* (namespaced). Keep legacy keys for compatibility.
        val contentType =
                context.extras[PlaybackHintKeys.Xtream.CONTENT_TYPE]
                        ?: context.extras[EXTRA_CONTENT_TYPE] ?: guessContentType(context)
        val streamUrl =
                when (contentType) {
                    CONTENT_TYPE_LIVE -> buildLiveUrlFromContext(context)
                    CONTENT_TYPE_VOD -> buildVodUrlFromContext(context)
                    CONTENT_TYPE_SERIES -> buildSeriesUrlFromContext(context)
                    else ->
                            throw PlaybackSourceException(
                                    message = "Unknown content type: $contentType",
                                    sourceType = SourceType.XTREAM
                            )
                }

        UnifiedLog.d(TAG) { "Built stream URL for $contentType content" }

        return PlaybackSource(
                uri = streamUrl,
                mimeType = determineMimeType(context, contentType),
                headers = buildHeaders(context),
                dataSourceType = DataSourceType.DEFAULT
        )
    }

    /**
     * Validates that a prebuilt Xtream URI does NOT contain credentials.
     *
     * This function conservatively rejects URIs that match common credential patterns:
     * - Userinfo in authority (user:pass@host)
     * - Query params containing username= or password=
     * - Xtream-style credential paths: /live/{user}/{pass}/, /movie/{user}/{pass}/,
     * /series/{user}/{pass}/
     * - Any obvious credential indicators
     *
     * Conservative false-positives are acceptable. Security > compatibility.
     *
     * @param uri The URI to validate
     * @return true if the URI appears safe (no credentials detected), false otherwise
     */
    private fun isSafePrebuiltXtreamUri(uri: String): Boolean {
        val lowerUri = uri.lowercase()

        // Check for userinfo in authority (user:pass@host)
        if (lowerUri.contains("@")) {
            // Check if @ appears before the first / after ://
            val schemeEnd = lowerUri.indexOf("://")
            if (schemeEnd != -1) {
                val pathStart = lowerUri.indexOf("/", schemeEnd + 3)
                val atIndex = lowerUri.indexOf("@", schemeEnd + 3)
                if (atIndex != -1 && (pathStart == -1 || atIndex < pathStart)) {
                    return false // Contains userinfo
                }
            }
        }

        // Check for query parameters with credentials
        if (lowerUri.contains("username=") || lowerUri.contains("password=")) {
            return false
        }

        // Check for Xtream-style credential paths
        // Patterns: /live/{user}/{pass}/, /movie/{user}/{pass}/, /series/{user}/{pass}/
        // We look for /live/x/y/ or /movie/x/y/ or /series/x/y/ where x and y are non-empty
        // segments
        val credentialPathPatterns =
                listOf(
                        Regex("""/live/[^/]+/[^/]+/"""),
                        Regex("""/movie/[^/]+/[^/]+/"""),
                        Regex("""/series/[^/]+/[^/]+/"""),
                        Regex("""/vod/[^/]+/[^/]+/""")
                )

        for (pattern in credentialPathPatterns) {
            if (pattern.containsMatchIn(lowerUri)) {
                return false
            }
        }

        // Additional safety: reject if both ":" and "@" appear in suspicious proximity
        if (lowerUri.contains(":") && lowerUri.contains("@")) {
            val colonIndices = lowerUri.indices.filter { lowerUri[it] == ':' }
            val atIndices = lowerUri.indices.filter { lowerUri[it] == '@' }

            // Skip scheme colon (http:// or https://)
            val filteredColonIndices =
                    colonIndices.filter {
                        it > 6 &&
                                !lowerUri.substring(maxOf(0, it - 6), it + 1)
                                        .matches(Regex("https?:"))
                    }

            // If there's a colon followed by @ within 50 chars, it's likely user:pass@
            for (colonIdx in filteredColonIndices) {
                for (atIdx in atIndices) {
                    if (atIdx > colonIdx && atIdx - colonIdx < 50) {
                        return false
                    }
                }
            }
        }

        // Passed all checks - appears safe
        return true
    }

    /**
     * Build live stream URL using XtreamApiClient.
     *
     * Extension selection priority:
     * 1. Explicit containerExtension from playbackHints (e.g., from VOD info)
     * 2. Policy-based selection from allowedOutputFormats (m3u8 > ts > mp4)
     * 3. XtreamApiClient default (from config prefs)
     */
    private fun buildLiveUrlFromContext(context: PlaybackContext): String {
        val streamId =
                context.extras[PlaybackHintKeys.Xtream.STREAM_ID]?.toIntOrNull()
                        ?: context.extras[EXTRA_STREAM_ID]?.toIntOrNull()
                                ?: throw PlaybackSourceException(
                                message = "Missing streamId for live content",
                                sourceType = SourceType.XTREAM
                        )

        val extension = resolveOutputExtension(context)
        return xtreamApiClient.buildLiveUrl(streamId, extension)
    }

    /**
     * Build VOD stream URL using XtreamApiClient.
     *
     * Extension selection priority:
     * 1. Explicit containerExtension from playbackHints (e.g., from VOD info)
     * 2. Policy-based selection from allowedOutputFormats (m3u8 > ts > mp4)
     * 3. XtreamApiClient default (from config prefs)
     */
    private fun buildVodUrlFromContext(context: PlaybackContext): String {
        val vodId =
                context.extras[PlaybackHintKeys.Xtream.VOD_ID]?.toIntOrNull()
                        ?: context.extras[EXTRA_VOD_ID]?.toIntOrNull()
                                ?: context.extras[PlaybackHintKeys.Xtream.STREAM_ID]?.toIntOrNull()
                                ?: context.extras[EXTRA_STREAM_ID]?.toIntOrNull()
                                ?: throw PlaybackSourceException(
                                message = "Missing vodId for VOD content",
                                sourceType = SourceType.XTREAM
                        )

        val extension = resolveOutputExtension(context)
        return xtreamApiClient.buildVodUrl(vodId, extension)
    }

    /**
     * Build series episode URL using XtreamApiClient.
     *
     * Extension selection priority:
     * 1. Explicit containerExtension from playbackHints (e.g., from episode info)
     * 2. Policy-based selection from allowedOutputFormats (m3u8 > ts > mp4)
     * 3. XtreamApiClient default (from config prefs)
     */
    private fun buildSeriesUrlFromContext(context: PlaybackContext): String {
        val seriesId =
                context.extras[PlaybackHintKeys.Xtream.SERIES_ID]?.toIntOrNull()
                        ?: context.extras[EXTRA_SERIES_ID]?.toIntOrNull()
                                ?: throw PlaybackSourceException(
                                message = "Missing seriesId for series content",
                                sourceType = SourceType.XTREAM
                        )

        val episodeId =
                context.extras[PlaybackHintKeys.Xtream.EPISODE_ID]?.toIntOrNull()
                        ?: context.extras[EXTRA_EPISODE_ID]?.toIntOrNull()
        val seasonNumber =
                context.extras[PlaybackHintKeys.Xtream.SEASON_NUMBER]?.toIntOrNull()
                        ?: context.extras[EXTRA_SEASON_NUMBER]?.toIntOrNull() ?: 1
        val episodeNumber =
                context.extras[PlaybackHintKeys.Xtream.EPISODE_NUMBER]?.toIntOrNull()
                        ?: context.extras[EXTRA_EPISODE_NUMBER]?.toIntOrNull() ?: 1
        val extension = resolveOutputExtension(context)

        return xtreamApiClient.buildSeriesEpisodeUrl(
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeId = episodeId,
                containerExtension = extension
        )
    }

    /**
     * Resolve the output extension for playback URL.
     *
     * Priority:
     * 1. Explicit containerExtension from playbackHints (SSOT from VOD/episode info)
     * 2. Policy-based selection from allowedOutputFormats (m3u8 > ts > mp4)
     * 3. null (let XtreamApiClient use its defaults)
     *
     * @param context The playback context
     * @return The resolved extension, or null to use defaults
     */
    private fun resolveOutputExtension(context: PlaybackContext): String? {
        // Priority 1: Explicit containerExtension (SSOT from enrichment)
        val explicitExt =
                context.extras[PlaybackHintKeys.Xtream.CONTAINER_EXT]
                        ?: context.extras[EXTRA_CONTAINER_EXT]
        if (!explicitExt.isNullOrBlank()) {
            UnifiedLog.d(TAG) { "Using explicit containerExtension: $explicitExt" }
            return explicitExt
        }

        // Priority 2: Policy-based selection from allowedOutputFormats
        val allowedFormatsRaw = context.extras[PlaybackHintKeys.Xtream.ALLOWED_OUTPUT_FORMATS]
        if (!allowedFormatsRaw.isNullOrBlank()) {
            val allowedFormats = allowedFormatsRaw.split(",").map { it.trim().lowercase() }.toSet()
            return try {
                val selected = selectXtreamOutputExt(allowedFormats)
                UnifiedLog.d(TAG) {
                    "Policy-selected extension: $selected from allowed: $allowedFormats"
                }
                selected
            } catch (e: PlaybackSourceException) {
                UnifiedLog.w(TAG) { "Format selection failed: ${e.message}, using defaults" }
                null
            }
        }

        // Priority 3: Let XtreamApiClient use its defaults
        UnifiedLog.d(TAG) { "No format hints, using XtreamApiClient defaults" }
        return null
    }

    /** Guess content type from context if not explicitly set. */
    private fun guessContentType(context: PlaybackContext): String {
        return when {
            context.extras.containsKey(PlaybackHintKeys.Xtream.STREAM_ID) ||
                    context.extras.containsKey(EXTRA_STREAM_ID) -> CONTENT_TYPE_LIVE
            context.extras.containsKey(PlaybackHintKeys.Xtream.VOD_ID) ||
                    context.extras.containsKey(EXTRA_VOD_ID) -> CONTENT_TYPE_VOD
            context.extras.containsKey(PlaybackHintKeys.Xtream.SERIES_ID) ||
                    context.extras.containsKey(EXTRA_SERIES_ID) ||
                    context.extras.containsKey(PlaybackHintKeys.Xtream.EPISODE_ID) ||
                    context.extras.containsKey(EXTRA_EPISODE_ID) -> CONTENT_TYPE_SERIES
            context.isLive -> CONTENT_TYPE_LIVE
            else -> CONTENT_TYPE_VOD
        }
    }

    /** Determine MIME type from context and content type. */
    private fun determineMimeType(context: PlaybackContext, contentType: String? = null): String? {
        // Check extras first
        context.extras["mimeType"]?.let {
            return it
        }

        // Determine from extension
        val extension =
                (context.extras[PlaybackHintKeys.Xtream.CONTAINER_EXT]
                                ?: context.extras[EXTRA_CONTAINER_EXT])?.lowercase()
        return when (extension) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "m3u8" -> "application/x-mpegURL"
            "ts" -> "video/mp2t"
            else ->
                    when (contentType) {
                        CONTENT_TYPE_LIVE -> "application/x-mpegURL" // Assume HLS for live
                        else -> null // Let ExoPlayer detect
                    }
        }
    }

    /** Build HTTP headers for authenticated streams. */
    private fun buildHeaders(context: PlaybackContext): Map<String, String> =
            XtreamHttpHeaders.withDefaults(
                    headers = context.headers,
                    referer = xtreamApiClient.capabilities?.baseUrl,
            )
}
