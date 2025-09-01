package com.chris.m3usuite.core.xtream

import android.net.Uri

data class XtreamCreds(
    val scheme: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val output: String = "m3u8"
)

object XtreamDetect {
    fun detectFromGetPhp(m3uUrl: String): XtreamCreds? = runCatching {
        val u = Uri.parse(m3uUrl)
        val path = u.path.orEmpty()
        if (!path.contains("get.php")) return null
        val scheme = u.scheme ?: "http"
        val host = u.host ?: return null
        val port = if (u.port > 0) u.port else if (scheme.equals("https", true)) 443 else 80
        val user = u.getQueryParameter("username") ?: return null
        val pass = u.getQueryParameter("password") ?: return null
        val out = u.getQueryParameter("output") ?: "m3u8"
        XtreamCreds(scheme, host, port, user, pass, out)
    }.getOrNull()

    fun detectFromStreamUrl(url: String): XtreamCreds? = runCatching {
        val u = Uri.parse(url)
        val scheme = u.scheme ?: "http"
        val host = u.host ?: return null
        val port = if (u.port > 0) u.port else if (scheme.equals("https", true)) 443 else 80
        val segs = u.pathSegments ?: emptyList()
        // Case 1: Standard xtream paths with kind prefix (live|hls|movie|series)
        if (segs.size >= 4 && segs[0].lowercase() in listOf("live", "hls", "movie", "series")) {
            val user = segs[1]
            val pass = segs[2]
            val last = segs.lastOrNull().orEmpty()
            val ext = last.substringAfterLast('.', "").lowercase()
            val output = when (ext) {
                "ts", "m3u8", "mp4" -> ext
                else -> "m3u8"
            }
            return@runCatching XtreamCreds(scheme, host, port, user, pass, output)
        }
        // Case 2: Compact paths without kind (/<user>/<pass>/<id>[.ext]) as seen in older lists
        if (segs.size >= 3) {
            val user = segs[0]
            val pass = segs[1]
            val last = segs.lastOrNull().orEmpty()
            val ext = last.substringAfterLast('.', "").lowercase()
            val output = when (ext) {
                "ts", "m3u8", "mp4" -> ext
                "" -> "ts" // default for bare numeric IDs
                else -> "m3u8"
            }
            return@runCatching XtreamCreds(scheme, host, port, user, pass, output)
        }
        null
    }.getOrNull()

    fun parseStreamId(url: String): Int? = runCatching {
        val u = Uri.parse(url)
        val q = u.getQueryParameter("stream_id") ?: u.getQueryParameter("stream")
        if (q != null) return@runCatching q.toIntOrNull()
        val segs = u.pathSegments ?: emptyList()
        // Try last segment (e.g., 12345.ts or 12345.m3u8)
        val lastId = segs.lastOrNull()?.substringBeforeLast('.')?.toIntOrNull()
        if (lastId != null) return@runCatching lastId
        // Some portals use .../<id>/index.m3u8 → check previous segment
        if (segs.size >= 2) segs[segs.size - 2].toIntOrNull() else null
    }.getOrNull()
}
