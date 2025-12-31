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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

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
    private val credentialsStore: XtreamCredentialsStore
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

        // Output format priority (policy-driven from allowed_output_formats)
        // Priority: m3u8 > ts > mp4 (if explicitly allowed by server)
        private val FORMAT_PRIORITY = listOf("m3u8", "ts", "mp4")
        
        // TRUE streaming formats (safe to accept from containerExtension)
        // Note: mp4 is NOT in this set - it's a container format, not streaming output
        private val STREAMING_FORMATS = setOf("m3u8", "ts")

        /**
         * Select the best output format based on server-allowed formats (SSOT) and HLS capability.
         *
         * Policy priority: m3u8 > ts > mp4
         * - m3u8 (HLS): Best for adaptive streaming, seeks, and compatibility
         * - ts (MPEG-TS): Good fallback, works on most players
         * - mp4: Used if explicitly in allowed_output_formats (provider-specific)
         *
         * **HLS Capability Fallback:**
         * - If HLS module is NOT present at runtime, automatically select ts over m3u8
         * - If only m3u8 is allowed and HLS is unavailable, fail fast with actionable error
         * - This ensures playback works across all device configurations
         *
         * This is POLICY-DRIVEN: allowed_output_formats is the Single Source of Truth.
         * We select the best format from what the server explicitly allows.
         *
         * @param allowedFormats Set of formats the server supports (e.g., {"m3u8", "ts", "mp4"})
         * @param hlsAvailable Whether HLS module is present in the build (default: detect at runtime)
         * @return The best format to use
         * @throws PlaybackSourceException if no supported format is available
         */
        internal fun selectXtreamOutputExt(
            allowedFormats: Set<String>,
            hlsAvailable: Boolean = HlsCapabilityDetector.isHlsAvailable()
        ): String {
            if (allowedFormats.isEmpty()) {
                throw PlaybackSourceException(
                        message = "No output formats specified by server (allowed_output_formats is empty)",
                        sourceType = SourceType.XTREAM
                )
            }
            val normalized = allowedFormats.map { it.lowercase().trim() }.toSet()
            
            // HLS capability-aware selection
            if (!hlsAvailable && "m3u8" in normalized) {
                // HLS module not present - try to fallback to ts
                if ("ts" in normalized) {
                    UnifiedLog.i(TAG) {
                        "HLS module unavailable, falling back from m3u8 to ts (allowed: ${allowedFormats.joinToString()})"
                    }
                    return "ts"
                }
                // Only m3u8 is allowed but HLS unavailable - fail fast with actionable error
                throw PlaybackSourceException(
                    message = "HLS (m3u8) required but media3-exoplayer-hls module is not available in this build. " +
                            "Server only allows: ${allowedFormats.joinToString()}. " +
                            "Please ensure the HLS dependency is included or contact the provider for TS format support.",
                    sourceType = SourceType.XTREAM
                )
            }
            
            // Policy-driven selection: try each format in priority order
            val selected = FORMAT_PRIORITY.firstOrNull { it in normalized }
            
            if (selected != null) {
                return selected
            }
            
            // No supported format found in allowed list
            throw PlaybackSourceException(
                    message =
                            "No supported output format in server's allowed_output_formats. " +
                            "Server allows: ${allowedFormats.joinToString()}, " +
                            "we support (priority): ${FORMAT_PRIORITY.joinToString()}",
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
        // ONLY validates prebuilt URIs from context.uri, does NOT block session-derived paths
        val existingUri = context.uri
        if (existingUri != null &&
                        (existingUri.startsWith("http://") || existingUri.startsWith("https://"))
        ) {
            if (isSafePrebuiltXtreamUri(existingUri)) {
                UnifiedLog.d(TAG) { "Using safe prebuilt URI for playback (bypassing session)" }
                // Extract extension from URI for MIME type determination
                val uriExtension = existingUri.substringAfterLast('.', "")
                    .substringBefore('?') // Remove query params if any
                    .takeIf { it.isNotBlank() }
                return PlaybackSource(
                        uri = existingUri,
                        mimeType = determineMimeTypeFromExtension(uriExtension),
                        headers = buildHeaders(context),
                        dataSourceType = DataSourceType.DEFAULT
                )
            } else {
                UnifiedLog.w(TAG) { "Rejected unsafe prebuilt URI (credentials detected), falling back to session-derived path" }
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
                val errorMessage = when {
                    !hasStoredCredentials() -> 
                        "Xtream not configured. Please log in to your Xtream account in Settings."
                    else -> 
                        "Xtream session unavailable (credentials invalid or server unreachable). " +
                        "Please check your connection or log in again in Settings."
                }
                
                throw PlaybackSourceException(
                    message = errorMessage,
                    sourceType = SourceType.XTREAM
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
                    CONTENT_TYPE_LIVE -> xtreamApiClient.buildLiveUrl(
                        context.extras[PlaybackHintKeys.Xtream.STREAM_ID]?.toIntOrNull()
                            ?: context.extras[EXTRA_STREAM_ID]?.toIntOrNull()
                            ?: throw PlaybackSourceException(
                                message = "Missing streamId for live content",
                                sourceType = SourceType.XTREAM
                            ),
                        resolvedExtension
                    )
                    CONTENT_TYPE_VOD -> xtreamApiClient.buildVodUrl(
                        context.extras[PlaybackHintKeys.Xtream.VOD_ID]?.toIntOrNull()
                            ?: context.extras[EXTRA_VOD_ID]?.toIntOrNull()
                            ?: context.extras[PlaybackHintKeys.Xtream.STREAM_ID]?.toIntOrNull()
                            ?: context.extras[EXTRA_STREAM_ID]?.toIntOrNull()
                            ?: throw PlaybackSourceException(
                                message = "Missing vodId for VOD content",
                                sourceType = SourceType.XTREAM
                            ),
                        resolvedExtension
                    )
                    CONTENT_TYPE_SERIES -> {
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
                        
                        xtreamApiClient.buildSeriesEpisodeUrl(
                            seriesId = seriesId,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            episodeId = episodeId,
                            containerExtension = resolvedExtension
                        )
                    }
                    else ->
                            throw PlaybackSourceException(
                                    message = "Unknown content type: $contentType",
                                    sourceType = SourceType.XTREAM
                            )
                }

        UnifiedLog.d(TAG) { "Built stream URL for $contentType content with extension: $resolvedExtension" }

        return PlaybackSource(
                uri = streamUrl,
                mimeType = determineMimeTypeFromExtension(resolvedExtension),
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
     * Resolve the output extension for playback URL.
     *
     * **Strict Policy (Cloudflare-safe):**
     * Priority 1: allowedOutputFormats policy selection (m3u8 > ts, NO mp4 by default)
     * Priority 2: containerExtension ONLY if TRUE streaming format (m3u8, ts)
     * Priority 3: Default to m3u8
     *
     * **CRITICAL for Cloudflare panels:**
     * - mp4 is NOT in priority list (causes issues with Cloudflare)
     * - Only m3u8 and ts are prioritized
     * - mp4 only used if it's the ONLY format in allowedOutputFormats
     *
     * NOTE: containerExtension from VOD metadata (e.g., "mkv", "mp4") describes the FILE
     * on the server, NOT the streaming output. We only trust allowedOutputFormats.
     *
     * @param context The playback context
     * @return The resolved extension (m3u8, ts, or rarely mp4)
     */
    private fun resolveOutputExtension(context: PlaybackContext): String? {
        // Priority 1: Policy-based selection from allowedOutputFormats (SSOT)
        // This is the ONLY place where format selection is authoritative
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
                UnifiedLog.w(TAG) { "Format selection failed: ${e.message}, defaulting to m3u8" }
                // Default to m3u8 (safest for Cloudflare)
                return "m3u8"
            }
        }

        // Priority 2: Explicit containerExtension - ONLY accept TRUE streaming formats
        // NEVER accept container formats (mkv, avi, mp4) as streaming output hints
        val explicitExt =
                context.extras[PlaybackHintKeys.Xtream.CONTAINER_EXT]
                        ?: context.extras[EXTRA_CONTAINER_EXT]
        if (!explicitExt.isNullOrBlank()) {
            val normalizedExt = explicitExt.lowercase().trim()
            // HARDENED: Only m3u8 and ts from containerExtension
            if (normalizedExt in STREAMING_FORMATS) {
                UnifiedLog.d(TAG) { "Using explicit streaming containerExtension: $normalizedExt" }
                return normalizedExt
            } else {
                UnifiedLog.d(TAG) { 
                    "Ignoring containerExtension=$explicitExt (not a TRUE streaming format)"
                }
                // Fall through to defaults
            }
        }

        // Priority 3: Default to m3u8 (with warning - allowed_output_formats should be present)
        UnifiedLog.w(TAG) { 
            "No allowed_output_formats provided by server, defaulting to m3u8. " +
            "This may fail with some providers. Server should provide allowed_output_formats."
        }
        return "m3u8"
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

    /**
     * Determine MIME type from the resolved output extension.
     * 
     * This maps the actual streaming format (m3u8, ts, mp4) to its MIME type.
     * The extension comes from resolveOutputExtension which considers HLS capability.
     * 
     * @param extension The resolved extension (e.g., "m3u8", "ts", "mp4")
     * @return The MIME type or null to let ExoPlayer auto-detect
     */
    private fun determineMimeTypeFromExtension(extension: String?): String? {
        return when (extension?.lowercase()) {
            "m3u8" -> "application/x-mpegURL"
            "ts" -> "video/mp2t"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            else -> null // Let ExoPlayer detect
        }
    }

    /** Build HTTP headers for authenticated streams. */
    private fun buildHeaders(context: PlaybackContext): Map<String, String> =
            XtreamHttpHeaders.withDefaults(
                    headers = context.headers,
                    referer = xtreamApiClient.capabilities?.baseUrl,
            )
    
    /**
     * Attempt to lazily re-initialize the Xtream session using stored credentials.
     * 
     * This is a bounded, idempotent operation with mutex protection against parallel calls.
     * It only attempts re-init if credentials are stored; it does NOT prompt for credentials.
     * 
     * **Thread Safety:** Protected by reinitMutex to prevent parallel initialization attempts.
     * **Timeout:** 3 seconds total (includes credential read + initialize call).
     * **Error Handling:** Returns false on any failure, logs details for debugging.
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
                val storedConfig = withTimeoutOrNull(LAZY_REINIT_TIMEOUT_MS) {
                    credentialsStore.read()
                }
                
                if (storedConfig == null) {
                    UnifiedLog.w(TAG) { "No stored credentials available for lazy re-init" }
                    return@withLock false
                }
                
                UnifiedLog.d(TAG) { 
                    "Attempting lazy re-init with stored credentials: " +
                    "scheme=${storedConfig.scheme}, host=${storedConfig.host}, port=${storedConfig.port}"
                }
                
                val result = withTimeoutOrNull(LAZY_REINIT_TIMEOUT_MS) {
                    xtreamApiClient.initialize(storedConfig.toApiConfig())
                }
                
                val success = result?.isSuccess == true
                if (!success) {
                    val error = result?.exceptionOrNull()
                    UnifiedLog.w(TAG) { "Lazy re-init failed: ${error?.message ?: "unknown error"}" }
                }
                
                success
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Lazy re-init error" }
                false
            }
        }
    }
    
    /**
     * Quick check if stored credentials are available (without reading them).
     * Used for better error messages.
     */
    private suspend fun hasStoredCredentials(): Boolean {
        return try {
            withTimeoutOrNull(500L) {
                credentialsStore.read()
            } != null
        } catch (e: Exception) {
            false
        }
    }
}
