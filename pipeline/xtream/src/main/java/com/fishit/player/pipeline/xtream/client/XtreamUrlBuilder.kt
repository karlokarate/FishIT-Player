package com.fishit.player.pipeline.xtream.client

import okhttp3.HttpUrl

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
        get() = buildString {
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
    fun playerApiUrl(action: String?, params: Map<String, String> = emptyMap()): String {
        return HttpUrl.Builder()
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
                }
                .build()
                .toString()
    }

    // =========================================================================
    // Playback URLs
    // =========================================================================

    /**
     * Build live stream URL.
     *
     * Format: {base}/live/{user}/{pass}/{streamId}.{ext}
     */
    fun liveUrl(streamId: Int, extension: String? = null): String {
        val ext =
                normalizeExtension(
                        extension ?: config.liveExtPrefs.firstOrNull() ?: "m3u8",
                        isLive = true
                )
        return playUrl("live", streamId, ext)
    }

    /**
     * Build VOD stream URL.
     *
     * Format: {base}/{vod|movie|movies}/{user}/{pass}/{vodId}.{ext}
     */
    fun vodUrl(vodId: Int, containerExtension: String? = null): String {
        val ext = sanitizeExtension(containerExtension ?: config.vodExtPrefs.firstOrNull() ?: "mp4")
        return playUrl(vodKind, vodId, ext)
    }

    /**
     * Build series episode URL.
     *
     * Prefers episodeId (direct): {base}/series/{user}/{pass}/{episodeId}.{ext}
     *
     * Fallback (legacy format): {base}/series/{user}/{pass}/{seriesId}/{season}/{episode}.{ext}
     */
    fun seriesEpisodeUrl(
            seriesId: Int,
            seasonNumber: Int,
            episodeNumber: Int,
            episodeId: Int? = null,
            containerExtension: String? = null,
    ): String {
        val ext =
                sanitizeExtension(
                        containerExtension ?: config.seriesExtPrefs.firstOrNull() ?: "mp4"
                )

        // Direct episodeId path (modern panels)
        if (episodeId != null && episodeId > 0) {
            return playUrl("series", episodeId, ext)
        }

        // Legacy series/season/episode path
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

    private fun playUrl(kind: String, id: Int, ext: String): String {
        return buildString {
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
    fun m3uUrl(type: String? = "m3u_plus", output: String? = null): String {
        return HttpUrl.Builder()
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
                }
                .build()
                .toString()
    }

    /**
     * Build xmltv.php URL for EPG data.
     *
     * Format: {base}/xmltv.php?username={user}&password={pass}
     */
    fun xmltvUrl(): String {
        return HttpUrl.Builder()
                .scheme(config.scheme.lowercase())
                .host(config.host)
                .port(resolvedPort)
                .apply {
                    addBasePathSegments()
                    addPathSegment("xmltv.php")
                    addQueryParameter("username", config.username)
                    addQueryParameter("password", config.password)
                }
                .build()
                .toString()
    }

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
    fun catchupUrl(streamId: Int, startTimestamp: Long, durationMinutes: Int): String {
        return HttpUrl.Builder()
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
                }
                .build()
                .toString()
    }

    /**
     * Alternative catchup format (some XUI.ONE panels).
     *
     * Format: {base}/timeshift/{user}/{pass}/{duration}/{start}/{streamId}.ts
     */
    fun catchupUrlAlt(
            streamId: Int,
            startIso: String,
            durationMinutes: Int,
            extension: String = "ts"
    ): String {
        return buildString {
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
            val parsed = runCatching { HttpUrl.parse(url) }.getOrNull() ?: return null

            val username = parsed.queryParameter("username") ?: return null
            val password = parsed.queryParameter("password") ?: return null

            if (username.isBlank() || password.isBlank()) return null

            // Extract base path (everything before get.php or player_api.php)
            val segments = parsed.pathSegments()
            val endpointIndex = segments.indexOfFirst { it in listOf("get.php", "player_api.php") }
            val basePath =
                    if (endpointIndex > 0) {
                        "/" + segments.subList(0, endpointIndex).joinToString("/")
                    } else null

            return XtreamApiConfig(
                    host = parsed.host(),
                    port = parsed.port().takeIf { it != HttpUrl.defaultPort(parsed.scheme()) },
                    scheme = parsed.scheme().uppercase(),
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
            val parsed = runCatching { HttpUrl.parse(url) }.getOrNull() ?: return null

            val segments = parsed.pathSegments()

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
                    } else null

            return XtreamApiConfig(
                    host = parsed.host(),
                    port = parsed.port().takeIf { it != HttpUrl.defaultPort(parsed.scheme()) },
                    scheme = parsed.scheme().uppercase(),
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

    private fun normalizeExtension(ext: String, isLive: Boolean): String {
        val lower = ext.lowercase().trim()
        return when {
            isLive && lower == "hls" -> "m3u8"
            isLive && lower in listOf("m3u8", "ts") -> lower
            !isLive -> sanitizeExtension(lower)
            else -> "m3u8"
        }
    }

    private fun sanitizeExtension(ext: String?): String {
        val lower = ext?.lowercase()?.trim().orEmpty()
        val valid = Regex("^[a-z0-9]{2,5}$")
        return if (valid.matches(lower)) lower else "mp4"
    }

    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}

/** Extension to redact credentials from URLs for logging. */
fun String.redactCredentials(): String =
        this.replace(Regex("(?i)(password|username)=([^&]*)"), "$1=***")
                .replace(Regex("/(live|vod|movie|movies|series)/([^/]+)/([^/]+)/"), "/$1/***/****/")
