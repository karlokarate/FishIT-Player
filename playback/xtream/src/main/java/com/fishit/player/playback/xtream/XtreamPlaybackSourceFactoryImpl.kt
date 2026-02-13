package com.fishit.player.playback.xtream

import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.SourceType
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.infra.transport.xtream.XtreamCredentialsStore
import com.fishit.player.infra.transport.xtream.XtreamHttpHeaders
import com.fishit.player.playback.domain.DataSourceType
import com.fishit.player.playback.domain.PlaybackSource
import com.fishit.player.playback.domain.PlaybackSourceException
import com.fishit.player.playback.domain.PlaybackSourceFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
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
    constructor(
        private val xtreamApiClient: XtreamApiClient,
        private val credentialsStore: XtreamCredentialsStore,
    ) : PlaybackSourceFactory {
        // Mutex to ensure lazy re-init is idempotent and thread-safe
        private val reinitMutex = Mutex()

        companion object {
            private const val TAG = "XtreamPlaybackFactory"

            // Lazy re-init timeout (bounded to avoid blocking playback indefinitely)
            private const val LAZY_REINIT_TIMEOUT_MS = 3000L

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

            // TRUE streaming formats (safe to accept from containerExtension)
            // Note: mp4 is NOT in this set - it's a container format, not streaming output
            private val STREAMING_FORMATS = setOf("m3u8", "ts")
        }

        override fun supports(sourceType: SourceType): Boolean = sourceType == SourceType.XTREAM

        override suspend fun createSource(context: PlaybackContext): PlaybackSource {
            UnifiedLog.d(TAG) { "Creating source for: ${context.canonicalId}" }

            // Secondary Path: Safe prebuilt URI support (backward compatibility)
            // ONLY validates prebuilt URIs from context.uri, does NOT block session-derived paths
            val existingUri = context.uri
            if (existingUri != null &&
                (existingUri.startsWith("http://") || existingUri.startsWith("https://"))
            ) {
                if (isSafePrebuiltXtreamUri(existingUri)) {
                    UnifiedLog.d(TAG) { "Using safe prebuilt URI for playback (bypassing session)" }
                    // Extract extension from URI for MIME type determination
                    val uriExtension =
                        existingUri
                            .substringAfterLast('.', "")
                            .substringBefore('?') // Remove query params if any
                            .takeIf { it.isNotBlank() }
                    return PlaybackSource(
                        uri = existingUri,
                        mimeType = determineMimeTypeFromExtension(uriExtension),
                        headers = buildHeaders(context),
                        dataSourceType = DataSourceType.XTREAM_HTTP,
                    )
                } else {
                    UnifiedLog.w(TAG) {
                        "Rejected unsafe prebuilt URI (credentials detected), falling back to session-derived path"
                    }
                    // Continue to session-derived path (don't fail, just warn)
                }
            }

            // Primary Path: Session-derived URL building
            // Attempt lazy re-initialization if session is null
            if (xtreamApiClient.capabilities == null) {
                UnifiedLog.w(TAG) { "Xtream session not initialized, attempting lazy re-init" }

                val reinitSuccess = attemptLazyReInitialization()

                if (!reinitSuccess) {
                    // Determine specific failure reason for actionable error message
                    val errorMessage =
                        when {
                            !hasStoredCredentials() ->
                                "Xtream not configured. Please log in to your Xtream account in Settings."
                            else ->
                                "Xtream session unavailable (credentials invalid or server unreachable). " +
                                    "Please check your connection or log in again in Settings."
                        }

                    throw PlaybackSourceException(
                        message = errorMessage,
                        sourceType = SourceType.XTREAM,
                    )
                }

                UnifiedLog.i(TAG) { "Lazy re-initialization succeeded, proceeding with playback" }
            }

            // v2 SSOT: PlaybackHintKeys.Xtream.* (namespaced). Keep legacy keys for compatibility.
            val contentType =
                context.extras[PlaybackHintKeys.Xtream.CONTENT_TYPE]
                    ?: context.extras[EXTRA_CONTENT_TYPE] ?: guessContentType(context)

            // Resolve the output extension first (this considers HLS capability)
            val resolvedExtension = resolveOutputExtension(context)

            val streamUrl =
                when (contentType) {
                    CONTENT_TYPE_LIVE -> {
                        val streamId = context.extras[PlaybackHintKeys.Xtream.STREAM_ID]?.toIntOrNull()
                            ?: context.extras[EXTRA_STREAM_ID]?.toIntOrNull()
                            ?: throw PlaybackSourceException(
                                message = "Missing streamId for live content",
                                sourceType = SourceType.XTREAM,
                            )

                        // Use liveKind from PlaybackHints for correct URL building
                        val liveKind = context.extras[PlaybackHintKeys.Xtream.LIVE_KIND]

                        UnifiedLog.d(TAG) { "Building Live URL: streamId=$streamId, kind=$liveKind, ext=$resolvedExtension" }

                        xtreamApiClient.buildLiveUrl(streamId, resolvedExtension, liveKind)
                    }
                    CONTENT_TYPE_VOD -> {
                        val vodId = context.extras[PlaybackHintKeys.Xtream.VOD_ID]?.toIntOrNull()
                            ?: context.extras[EXTRA_VOD_ID]?.toIntOrNull()
                            ?: context.extras[PlaybackHintKeys.Xtream.STREAM_ID]?.toIntOrNull()
                            ?: context.extras[EXTRA_STREAM_ID]?.toIntOrNull()
                            ?: throw PlaybackSourceException(
                                message = "Missing vodId for VOD content",
                                sourceType = SourceType.XTREAM,
                            )

                        // Use vodKind from PlaybackHints for correct URL building
                        val vodKind = context.extras[PlaybackHintKeys.Xtream.VOD_KIND]

                        UnifiedLog.d(TAG) { "Building VOD URL: vodId=$vodId, kind=$vodKind, ext=$resolvedExtension" }

                        xtreamApiClient.buildVodUrl(
                            vodId,
                            containerExtension = resolvedExtension,
                            vodKind = vodKind,
                        )
                    }
                    CONTENT_TYPE_SERIES -> {
                        val seriesId =
                            context.extras[PlaybackHintKeys.Xtream.SERIES_ID]?.toIntOrNull()
                                ?: context.extras[EXTRA_SERIES_ID]?.toIntOrNull()
                                ?: throw PlaybackSourceException(
                                    message = "Missing seriesId for series content",
                                    sourceType = SourceType.XTREAM,
                                )
                        val episodeId =
                            context.extras[PlaybackHintKeys.Xtream.EPISODE_ID]?.toIntOrNull()
                                ?: context.extras[EXTRA_EPISODE_ID]?.toIntOrNull()
                        val seasonNumber =
                            context.extras[PlaybackHintKeys.Xtream.SEASON_NUMBER]?.toIntOrNull()
                                ?: context.extras[EXTRA_SEASON_NUMBER]?.toIntOrNull() ?: 1
                        val episodeNumber =
                            context.extras[PlaybackHintKeys.Xtream.EPISODE_NUMBER]
                                ?.toIntOrNull()
                                ?: context.extras[EXTRA_EPISODE_NUMBER]?.toIntOrNull() ?: 1
                        // Use seriesKind from PlaybackHints for correct URL building
                        val seriesKind = context.extras[PlaybackHintKeys.Xtream.SERIES_KIND]

                        xtreamApiClient.buildSeriesEpisodeUrl(
                            seriesId = seriesId,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            episodeId = episodeId,
                            containerExtension = resolvedExtension,
                            seriesKind = seriesKind,
                        )
                    }
                    else ->
                        throw PlaybackSourceException(
                            message = "Unknown content type: $contentType",
                            sourceType = SourceType.XTREAM,
                        )
                }

            UnifiedLog.d(TAG) {
                "Built stream URL for $contentType content with extension: $resolvedExtension"
            }

            return PlaybackSource(
                uri = streamUrl,
                mimeType = determineMimeTypeFromExtension(resolvedExtension),
                headers = buildHeaders(context),
                dataSourceType = DataSourceType.XTREAM_HTTP,
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
                    Regex("""/vod/[^/]+/[^/]+/"""),
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
                            !lowerUri
                                .substring(maxOf(0, it - 6), it + 1)
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
         * Resolve the output extension for playback URL.
         *
         * **Content-type aware extension resolution:**
         *
         * **For LIVE streams:**
         * - Use containerExtension if it's a streaming format (m3u8/ts)
         * - Default to ts (most reliable for IPTV)
         *
         * **For VOD/SERIES (file-based content):**
         * - Use container_extension as SSOT (mkv, mp4, avi, etc.)
         * - This is the actual file format on the server!
         * - Fallback to mp4 (VOD) or mkv (Series) if not provided
         *
         * @param context The playback context
         * @return The resolved extension (mkv, mp4, ts, m3u8, etc.)
         */
        private fun resolveOutputExtension(context: PlaybackContext): String? {
            val contentType =
                context.extras[PlaybackHintKeys.Xtream.CONTENT_TYPE]
                    ?: context.extras[EXTRA_CONTENT_TYPE] ?: guessContentType(context)

            // Get containerExtension from hints (the actual file extension on server)
            val containerExt =
                (
                    context.extras[PlaybackHintKeys.Xtream.CONTAINER_EXT]
                        ?: context.extras[EXTRA_CONTAINER_EXT]
                )?.lowercase()
                    ?.trim()

            UnifiedLog.d(TAG) {
                "resolveOutputExtension: contentType=$contentType, containerExt=$containerExt"
            }

            return when (contentType) {
                CONTENT_TYPE_LIVE -> resolveLiveExtension(containerExt)
                CONTENT_TYPE_VOD, CONTENT_TYPE_SERIES ->
                    resolveFileExtension(containerExt, contentType)
                else -> resolveFileExtension(containerExt, contentType)
            }
        }

        /**
         * Resolve extension for LIVE streams.
         *
         * Priority: containerExt (if streaming format) → ts (default, most reliable for IPTV).
         */
        private fun resolveLiveExtension(containerExt: String?): String {
            // If containerExt is a streaming format, use it
            if (containerExt != null && containerExt in STREAMING_FORMATS) {
                UnifiedLog.d(TAG) { "LIVE: Using containerExtension: $containerExt" }
                return containerExt
            }

            // Default for LIVE: ts (more reliable for IPTV than m3u8)
            UnifiedLog.d(TAG) { "LIVE: Defaulting to ts" }
            return "ts"
        }

        /**
         * Resolve extension for file-based content (VOD/SERIES).
         *
         * **container_extension is SSOT for VOD/Series!**
         * The actual file on the server determines the extension.
         *
         * Fallback: mkv (series, consistent with transport layer) or mp4 (VOD, most common).
         */
        private fun resolveFileExtension(
            containerExt: String?,
            contentType: String,
        ): String {
            // Known video container formats (files, not streams)
            val videoContainerFormats = setOf("mkv", "mp4", "avi", "mov", "wmv", "flv", "webm")

            // If containerExtension is a video file format, USE IT!
            // This is the actual file on the server - don't override it.
            if (containerExt != null && containerExt in videoContainerFormats) {
                UnifiedLog.i(TAG) {
                    "$contentType: Using file containerExtension: $containerExt (SSOT)"
                }
                return containerExt
            }

            // If containerExtension is a streaming format (m3u8/ts), use it
            // (Some providers might use HLS for VOD)
            if (containerExt != null && containerExt in STREAMING_FORMATS) {
                UnifiedLog.d(TAG) { "$contentType: Using streaming containerExtension: $containerExt" }
                return containerExt
            }

            // Fallback when containerExtension is missing
            if (contentType == CONTENT_TYPE_SERIES) {
                UnifiedLog.w(TAG) {
                    "$contentType: No containerExtension provided. Using fallback: mkv " +
                        "(consistent with transport layer fallback)"
                }
                return "mkv"
            } else {
                UnifiedLog.w(TAG) {
                    "$contentType: No containerExtension provided, defaulting to mp4."
                }
                return "mp4"
            }
        }

        /** Guess content type from context if not explicitly set. */
        private fun guessContentType(context: PlaybackContext): String =
            when {
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

        /**
         * Determine MIME type from the resolved output extension.
         *
         * **Sniffing-First Policy:** Only adaptive streaming formats (HLS) need an explicit MIME type
         * to route to the correct MediaSource (HlsMediaSource). ALL progressive formats (mp4, mkv,
         * ts, avi, flv, webm, etc.) return null — ExoPlayer's DefaultExtractorsFactory will sniff
         * the container via magic bytes, which is more reliable than extension-based guessing.
         *
         * **Why:** ExoPlayer maps progressive MIME types to CONTENT_TYPE_OTHER anyway, so setting
         * video/mp4 or video/x-matroska has zero effect on format detection. The extension in
         * Xtream URLs tells the SERVER what format to deliver, NOT ExoPlayer what to expect.
         *
         * @param extension The resolved extension (e.g., "m3u8", "ts", "mp4")
         * @return The MIME type only for adaptive formats, null for progressive (sniffing)
         */
        private fun determineMimeTypeFromExtension(extension: String?): String? =
            when (extension?.lowercase()) {
                "m3u8" -> "application/x-mpegURL" // HLS: must route to HlsMediaSource
                else -> null // Progressive: ExoPlayer sniffs via DefaultExtractorsFactory
            }

        /**
         * Build HTTP headers for media playback.
         *
         * **PLATINUM FIX: Uses PLAYBACK headers, NOT API headers!**
         *
         * PLAYBACK headers differ from API headers:
         * - Accept: any MIME type (not application/json)
         * - Accept-Encoding: identity (not gzip - critical for streams!)
         * - Icy-MetaData: 1 (for IPTV metadata)
         *
         * This ensures legacy-parity with v1 and Cloudflare/WAF compatibility.
         */
        private fun buildHeaders(context: PlaybackContext): Map<String, String> =
            XtreamHttpHeaders.withPlaybackDefaults(
                headers = context.headers,
                referer = xtreamApiClient.capabilities?.baseUrl,
                includeIcyMetadata = true,
            )

        /**
         * Attempt to lazily re-initialize the Xtream session using stored credentials.
         *
         * This is a bounded, idempotent operation with mutex protection against parallel calls. It only
         * attempts re-init if credentials are stored; it does NOT prompt for credentials.
         *
         * **Thread Safety:** Protected by reinitMutex to prevent parallel initialization attempts.
         * **Timeout:** 3 seconds total (includes credential read + initialize call). **Error
         * Handling:** Returns false on any failure, logs details for debugging.
         *
         * @return true if re-initialization succeeded and capabilities are now available
         */
        private suspend fun attemptLazyReInitialization(): Boolean {
            return reinitMutex.withLock {
                // Quick check: if already initialized, return success immediately
                if (xtreamApiClient.capabilities != null) {
                    UnifiedLog.d(TAG) { "Session already initialized, skipping lazy re-init" }
                    return@withLock true
                }

                try {
                    val storedConfig =
                        withTimeoutOrNull(LAZY_REINIT_TIMEOUT_MS) { credentialsStore.read() }

                    if (storedConfig == null) {
                        UnifiedLog.w(TAG) { "No stored credentials available for lazy re-init" }
                        return@withLock false
                    }

                    UnifiedLog.d(TAG) {
                        "Attempting lazy re-init with stored credentials: " +
                            "scheme=${storedConfig.scheme}, host=${storedConfig.host}, port=${storedConfig.port}"
                    }

                    val result =
                        withTimeoutOrNull(LAZY_REINIT_TIMEOUT_MS) {
                            xtreamApiClient.initialize(storedConfig.toApiConfig())
                        }

                    val success = result?.isSuccess == true
                    if (!success) {
                        val error = result?.exceptionOrNull()
                        UnifiedLog.w(TAG) {
                            "Lazy re-init failed: ${error?.message ?: "unknown error"}"
                        }
                    }

                    success
                } catch (e: Exception) {
                    UnifiedLog.e(TAG, e) { "Lazy re-init error" }
                    false
                }
            }
        }

        /**
         * Quick check if stored credentials are available (without reading them). Used for better error
         * messages.
         */
        private suspend fun hasStoredCredentials(): Boolean =
            try {
                withTimeoutOrNull(500L) { credentialsStore.read() } != null
            } catch (e: Exception) {
                false
            }
    }
