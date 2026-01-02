package com.fishit.player.infra.transport.xtream

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * XtreamUrlBuilder – URL Factory für Xtream Codes API
 *
 * Zentrale URL-Generierung für:
 * - player_api.php Calls (Kategorien, Streams, Details, EPG)
 * - Playback URLs (Live/VOD/Series)
 * - M3U Playlist URLs (get.php, xmltv.php)
 * - Catchup/Timeshift URLs
 *
 * Basiert auf v1 XtreamConfig.kt mit PathKind-Logik.
 */
class XtreamUrlBuilder(
    private val config: XtreamApiConfig,
    private var resolvedPort: Int = config.port ?: 80,
    private var vodKind: String = "vod",
) {
    // =========================================================================
    // Configuration Updates
    // =========================================================================

    fun updateResolvedPort(port: Int) {
        resolvedPort = port
    }

    fun updateVodKind(kind: String) {
        vodKind = kind
    }

    // =========================================================================
    // Base URL
    // =========================================================================

    /**
     * Base URL with scheme, host, port and optional basePath. Example:
     * "https://example.com:8080/panel"
     */
    val baseUrl: String
        get() =
            buildString {
                append(config.scheme.lowercase())
                append("://")
                append(config.host)
                append(":")
                append(resolvedPort)
                config.basePath?.let { bp ->
                    val normalized = normalizeBasePath(bp)
                    if (normalized.isNotEmpty()) append(normalized)
                }
            }

    // =========================================================================
    // Player API URLs
    // =========================================================================

    /**
     * Build player_api.php URL.
     *
     * @param action API action (get_live_streams, get_vod_info, etc.). Null for server info.
     * @param params Additional query parameters.
     */
    fun playerApiUrl(
        action: String?,
        params: Map<String, String> = emptyMap(),
    ): String =
        HttpUrl
            .Builder()
            .scheme(config.scheme.lowercase())
            .host(config.host)
            .port(resolvedPort)
            .apply {
                addBasePathSegments()
                addPathSegment("player_api.php")

                // Action first
                if (!action.isNullOrBlank()) {
                    addQueryParameter("action", action)
                }

                // Extra params
                params.forEach { (key, value) -> addQueryParameter(key, value) }

                // Credentials last
                addQueryParameter("username", config.username)
                addQueryParameter("password", config.password)
            }.build()
            .toString()

    // =========================================================================
    // Panel API URLs (Premium Contract Section 2/8)
    // =========================================================================

    /**
     * Build panel_api.php URL.
     *
     * Used for diagnostics and capability detection per Premium Contract X-10.
     * Some panels expose additional info via panel_api.php.
     *
     * @param action Optional action parameter.
     * @param params Additional query parameters.
     * @return panel_api.php URL.
     *
     * @see <a href="contracts/XTREAM_SCAN_PREMIUM_CONTRACT_V1.md">Premium Contract Section 2/8</a>
     */
    fun panelApiUrl(
        action: String? = null,
        params: Map<String, String> = emptyMap(),
    ): String =
        HttpUrl
            .Builder()
            .scheme(config.scheme.lowercase())
            .host(config.host)
            .port(resolvedPort)
            .apply {
                addBasePathSegments()
                addPathSegment("panel_api.php")

                // Credentials first for panel_api
                addQueryParameter("username", config.username)
                addQueryParameter("password", config.password)

                // Action if specified
                if (!action.isNullOrBlank()) {
                    addQueryParameter("action", action)
                }

                // Extra params
                params.forEach { (key, value) -> addQueryParameter(key, value) }
            }.build()
            .toString()

    // =========================================================================
    // Playback URLs
    // =========================================================================

    /**
     * Build live stream URL.
     *
     * Format: {base}/live/{user}/{pass}/{streamId}.{ext}
     */
    fun liveUrl(
        streamId: Int,
        extension: String? = null,
    ): String {
        val ext =
            normalizeExtension(
                extension ?: config.liveExtPrefs.firstOrNull() ?: "m3u8",
                isLive = true,
            )
        return playUrl("live", streamId, ext)
    }

    /**
     * Build VOD stream URL.
     *
     * Format: {base}/{vod|movie|movies}/{user}/{pass}/{vodId}.{ext}
     */
    fun vodUrl(
        vodId: Int,
        containerExtension: String? = null,
    ): String {
        val ext = sanitizeExtension(containerExtension ?: config.vodExtPrefs.firstOrNull() ?: "m3u8")
        return playUrl(vodKind, vodId, ext)
    }

    /**
     * Build series episode URL.
     *
     * **CRITICAL:** Series episodes MUST use /series/ path (NOT /movie/ or /vod/).
     * Legacy behavior: /series/{user}/{pass}/{episodeId}.{ext} -> 302 redirect to CDN.
     *
     * Format: {base}/series/{user}/{pass}/{episodeId}.{ext}
     *
     * **Why /series/ path:**
     * - Xtream Codes API requires /series/ endpoint for episode playback
     * - Returns 302 Found redirect to tokenized CDN URL (cross-host)
     * - Using /movie/ or /vod/ path fails with 404 or wrong content
     *
     * **Extension Resolution (containerExtension-first, minimal fallback):**
     * 1. If containerExtension provided and valid → USE IT (SSOT)
     * 2. If missing, try: mp4 → mkv → FAIL
     * 3. No m3u8/ts forcing for series (file-based, not adaptive streams)
     *
     * @param seriesId Series ID (used only if episodeId is missing - rare fallback)
     * @param seasonNumber Season number (used only if episodeId is missing)
     * @param episodeNumber Episode number (used only if episodeId is missing)
     * @param episodeId Episode stream ID (REQUIRED for direct playback)
     * @param containerExtension Container format from server (e.g., "mp4", "mkv")
     * @return Series episode playback URL
     * @throws IllegalArgumentException if no valid extension can be determined
     */
    fun seriesEpisodeUrl(
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeId: Int? = null,
        containerExtension: String? = null,
    ): String {
        // SSOT: Use containerExtension if provided
        val ext =
            if (!containerExtension.isNullOrBlank()) {
                sanitizeSeriesExtension(containerExtension)
            } else {
                // Minimal fallback: mp4 → mkv only
                config.seriesExtPrefs.firstOrNull()?.let { sanitizeSeriesExtension(it) }
                    ?: "mp4" // First fallback: mp4
            }

        // Direct episodeId path: /series/user/pass/episodeId.ext (standard approach)
        if (episodeId != null && episodeId > 0) {
            return buildString {
                append(baseUrl)
                append("/series/")
                append(urlEncode(config.username))
                append("/")
                append(urlEncode(config.password))
                append("/")
                append(episodeId)
                append(".")
                append(ext)
            }
        }

        // Legacy fallback: /series/user/pass/seriesId/season/episode.ext (rare)
        return buildString {
            append(baseUrl)
            append("/series/")
            append(urlEncode(config.username))
            append("/")
            append(urlEncode(config.password))
            append("/")
            append(seriesId)
            append("/")
            append(seasonNumber)
            append("/")
            append(episodeNumber)
            append(".")
            append(ext)
        }
    }

    private fun playUrl(
        kind: String,
        id: Int,
        ext: String,
    ): String =
        buildString {
            append(baseUrl)
            append("/")
            append(kind)
            append("/")
            append(urlEncode(config.username))
            append("/")
            append(urlEncode(config.password))
            append("/")
            append(id)
            append(".")
            append(ext)
        }

    // =========================================================================
    // M3U / XMLTV URLs
    // =========================================================================

    /**
     * Build get.php M3U URL for bulk playlist retrieval.
     *
     * Format: {base}/get.php?username={user}&password={pass}&type={type}&output={output}
     *
     * @param type Playlist type: "m3u", "m3u_plus", etc. Null for full M3U.
     * @param output Output format: "ts", "m3u8", "hls". Null for default.
     */
    fun m3uUrl(
        type: String? = "m3u_plus",
        output: String? = null,
    ): String =
        HttpUrl
            .Builder()
            .scheme(config.scheme.lowercase())
            .host(config.host)
            .port(resolvedPort)
            .apply {
                addBasePathSegments()
                addPathSegment("get.php")
                addQueryParameter("username", config.username)
                addQueryParameter("password", config.password)
                type?.let { addQueryParameter("type", it) }
                output?.let { addQueryParameter("output", it) }
            }.build()
            .toString()

    /**
     * Build xmltv.php URL for EPG data.
     *
     * Format: {base}/xmltv.php?username={user}&password={pass}
     */
    fun xmltvUrl(): String =
        HttpUrl
            .Builder()
            .scheme(config.scheme.lowercase())
            .host(config.host)
            .port(resolvedPort)
            .apply {
                addBasePathSegments()
                addPathSegment("xmltv.php")
                addQueryParameter("username", config.username)
                addQueryParameter("password", config.password)
            }.build()
            .toString()

    // =========================================================================
    // Catchup / Timeshift URLs
    // =========================================================================

    /**
     * Build catchup/timeshift URL for archived live streams.
     *
     * Standard format:
     * {base}/streaming/timeshift.php?username={user}&password={pass}&stream={streamId}&start={timestamp}&duration={minutes}
     *
     * @param streamId Live stream ID.
     * @param startTimestamp Unix timestamp of recording start.
     * @param durationMinutes Duration in minutes.
     */
    fun catchupUrl(
        streamId: Int,
        startTimestamp: Long,
        durationMinutes: Int,
    ): String =
        HttpUrl
            .Builder()
            .scheme(config.scheme.lowercase())
            .host(config.host)
            .port(resolvedPort)
            .apply {
                addBasePathSegments()
                addPathSegment("streaming")
                addPathSegment("timeshift.php")
                addQueryParameter("username", config.username)
                addQueryParameter("password", config.password)
                addQueryParameter("stream", streamId.toString())
                addQueryParameter("start", startTimestamp.toString())
                addQueryParameter("duration", durationMinutes.toString())
            }.build()
            .toString()

    /**
     * Alternative catchup format (some XUI.ONE panels).
     *
     * Format: {base}/timeshift/{user}/{pass}/{duration}/{start}/{streamId}.ts
     */
    fun catchupUrlAlt(
        streamId: Int,
        startIso: String,
        durationMinutes: Int,
        extension: String = "ts",
    ): String =
        buildString {
            append(baseUrl)
            append("/timeshift/")
            append(urlEncode(config.username))
            append("/")
            append(urlEncode(config.password))
            append("/")
            append(durationMinutes)
            append("/")
            append(urlEncode(startIso))
            append("/")
            append(streamId)
            append(".")
            append(sanitizeExtension(extension))
        }

    // =========================================================================
    // Credential Parsing (M3U URL → Config)
    // =========================================================================

    companion object {
        /**
         * Parse credentials from a get.php or player_api.php URL.
         *
         * Supported formats:
         * - http://host:port/get.php?username=X&password=Y
         * - http://host:port/player_api.php?username=X&password=Y&action=...
         * - http://host:port/panel/get.php?username=X&password=Y
         *
         * @return XtreamApiConfig or null if URL is invalid.
         */
        fun parseCredentials(url: String): XtreamApiConfig? {
            val parsed = url.toHttpUrlOrNull() ?: return null

            val username = parsed.queryParameter("username") ?: return null
            val password = parsed.queryParameter("password") ?: return null

            if (username.isBlank() || password.isBlank()) return null

            // Extract base path (everything before get.php or player_api.php)
            val segments = parsed.pathSegments
            val endpointIndex = segments.indexOfFirst { it in listOf("get.php", "player_api.php") }
            val basePath =
                if (endpointIndex > 0) {
                    "/" + segments.subList(0, endpointIndex).joinToString("/")
                } else {
                    null
                }

            return XtreamApiConfig(
                host = parsed.host,
                port = parsed.port.takeIf { it != HttpUrl.defaultPort(parsed.scheme) },
                scheme = parsed.scheme.uppercase(),
                username = username,
                password = password,
                basePath = basePath,
            )
        }

        /**
         * Parse credentials from an M3U play URL.
         *
         * Supported format: http://host:port/{live|vod|movie|series}/USER/PASS/STREAMID.ext
         *
         * @return XtreamApiConfig or null if URL is invalid.
         */
        fun parsePlayUrl(url: String): XtreamApiConfig? {
            val parsed = url.toHttpUrlOrNull() ?: return null

            val segments = parsed.pathSegments

            // Find kind segment (live/vod/movie/movies/series)
            val kindIndex =
                segments.indexOfFirst {
                    it.lowercase() in listOf("live", "vod", "movie", "movies", "series")
                }
            if (kindIndex < 0 || segments.size < kindIndex + 4) return null

            val username = segments.getOrNull(kindIndex + 1) ?: return null
            val password = segments.getOrNull(kindIndex + 2) ?: return null

            if (username.isBlank() || password.isBlank()) return null

            // Base path is everything before the kind segment
            val basePath =
                if (kindIndex > 0) {
                    "/" + segments.subList(0, kindIndex).joinToString("/")
                } else {
                    null
                }

            return XtreamApiConfig(
                host = parsed.host,
                port = parsed.port.takeIf { it != HttpUrl.defaultPort(parsed.scheme) },
                scheme = parsed.scheme.uppercase(),
                username = username,
                password = password,
                basePath = basePath,
            )
        }
    }

    // =========================================================================
    // Internal Utilities
    // =========================================================================

    private fun HttpUrl.Builder.addBasePathSegments() {
        config.basePath?.let { bp ->
            val normalized = normalizeBasePath(bp).removePrefix("/")
            if (normalized.isNotEmpty()) {
                normalized.split('/').filter { it.isNotBlank() }.forEach { seg ->
                    addPathSegment(seg)
                }
            }
        }
    }

    private fun normalizeBasePath(path: String): String {
        val trimmed = path.trim()
        val withLeading =
            if (trimmed.isNotEmpty() && !trimmed.startsWith("/")) "/$trimmed" else trimmed
        val withoutTrailing = withLeading.removeSuffix("/")
        return if (withoutTrailing == "/") "" else withoutTrailing
    }

    private fun normalizeExtension(
        ext: String,
        isLive: Boolean,
    ): String {
        val lower = ext.lowercase().trim()
        return when {
            isLive && lower == "hls" -> "m3u8"
            isLive && lower in listOf("m3u8", "ts") -> lower
            !isLive -> sanitizeExtension(lower)
            else -> "m3u8"
        }
    }

    /**
     * Sanitize extension for playback URL building.
     *
     * Accepts both streaming formats and container formats:
     * - Streaming formats (m3u8, ts): For live streams and adaptive VOD
     * - Container formats (mkv, mp4, avi, etc.): For direct file access in VOD/Series
     *
     * For VOD/Series, the container_extension is the SSOT - it describes the actual
     * file on the server (.../movie/.../id.mkv or .../vod/.../id.mp4).
     *
     * For Live, only streaming formats make sense (m3u8, ts).
     */
    private fun sanitizeExtension(ext: String?): String {
        val lower = ext?.lowercase()?.trim().orEmpty()
        // Accept both streaming formats and container formats
        val validFormats = setOf("m3u8", "ts", "mkv", "mp4", "avi", "mov", "wmv", "flv", "webm")
        return if (lower in validFormats) lower else "m3u8"
    }

    /**
     * Sanitize extension for SERIES episode playback URL building.
     *
     * **containerExtension-first with minimal fallback:**
     * 1. If extension is valid container format → USE IT (SSOT)
     * 2. Otherwise → FAIL with IllegalArgumentException
     *
     * **Valid container formats for series (file-based, NOT adaptive streams):**
     * - mkv, mp4, avi, mov, wmv, flv, webm
     *
     * **NOT allowed for series:**
     * - m3u8, ts (streaming formats) - series are direct file downloads, not adaptive streams
     *
     * @param ext Extension to sanitize (e.g., "mp4", "mkv")
     * @return Sanitized extension in lowercase
     * @throws IllegalArgumentException if extension is not a valid container format
     */
    private fun sanitizeSeriesExtension(ext: String): String {
        val lower = ext.lowercase().trim()
        // Valid video container formats (files, not streams)
        val validSeriesFormats = setOf("mkv", "mp4", "avi", "mov", "wmv", "flv", "webm")
        
        if (lower in validSeriesFormats) {
            return lower
        }
        
        // Reject streaming formats for series
        if (lower in setOf("m3u8", "ts")) {
            throw IllegalArgumentException(
                "Invalid extension for series episode: '$ext'. " +
                "Series episodes require container formats (mp4, mkv, avi, etc.), not streaming formats (m3u8, ts)."
            )
        }
        
        // Unknown/invalid extension
        throw IllegalArgumentException(
            "Invalid extension for series episode: '$ext'. " +
            "Valid formats: mp4, mkv, avi, mov, wmv, flv, webm"
        )
    }

    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}

/** Extension to redact credentials from URLs for logging. */
fun String.redactCredentials(): String =
    this
        .replace(Regex("(?i)(password|username)=([^&]*)"), "$1=***")
        .replace(Regex("/(live|vod|movie|movies|series)/([^/]+)/([^/]+)/"), "/$1/***/****/")
