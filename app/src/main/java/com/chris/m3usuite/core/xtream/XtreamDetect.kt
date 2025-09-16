package com.chris.m3usuite.core.xtream

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * XtreamDetect – FINAL (drop-in kompatibel)
 *
 * Aufgaben
 *  - Aus beliebigen Portal-/Stream-URLs Host/Port/User/Pass/Output extrahieren
 *  - Numerische Stream-IDs robust erkennen (legacy + .ext + Query)
 *  - Hilfs-Datenträger XtreamCreds für einfache Weitergabe
 *
 * Keine externen Abhängigkeiten (nur java.net.URI).
 */
object XtreamDetect {

    // ---------------------------------
    // Public API
    // ---------------------------------

    /**
     * Versucht, aus einer URL (get.php, player_api, live/movie/series Pfad) die Portal-Creds zu gewinnen.
     * Gibt null zurück, wenn nicht ermittelbar.
     */
    fun detectCreds(anyUrl: String): XtreamCreds? {
        // 1) Direkt versuchen: get.php / player_api.php Query
        detectFromQuery(anyUrl)?.let { return it }
        // 2) Kompakte Pfade: /live|/movie|/movies|/vod|/series/{user}/{pass}/...
        detectFromPath(anyUrl)?.let { return it }
        return null
    }

    /**
     * Extrahiert eine numerische ID aus einem Xtream-typischen Pfad.
     * Deckt ab: /.../12345, /.../12345.ts, /.../12345.m3u8, sowie optionale Query-Enden.
     */
    fun parseStreamId(url: String): Int? =
        ID_TAIL.find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()

    // ---------------------------------
    // Internals
    // ---------------------------------

    private fun detectFromQuery(anyUrl: String): XtreamCreds? = runCatching {
        val uri = URI(anyUrl)
        val scheme = (uri.scheme ?: "http").lowercase()
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else if (scheme == "https") 443 else 80
        val query = uri.rawQuery.orEmpty()
        if (query.isEmpty()) return null
        val qp = parseQuery(query)
        val user = qp["username"] ?: return null
        val pass = qp["password"] ?: return null
        val out = (qp["output"] ?: qp["container"] ?: "m3u8").lowercase()
        XtreamCreds(host, port, user, pass, out)
    }.getOrNull()

    private fun detectFromPath(anyUrl: String): XtreamCreds? = runCatching {
        val uri = URI(anyUrl)
        val scheme = (uri.scheme ?: "http").lowercase()
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else if (scheme == "https") 443 else 80
        val path = uri.path ?: return null
        // Erwartete Muster: /live/{u}/{p}/..., /movie|movies|vod/{u}/{p}/..., /series/{u}/{p}/...
        val m = CREDS_PATH.find(path) ?: return null
        val u = urlDecode(m.groupValues[1])
        val p = urlDecode(m.groupValues[2])
        XtreamCreds(host, port, u, p, output = "m3u8")
    }.getOrNull()

    private fun parseQuery(q: String): Map<String, String> {
        if (q.isBlank()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        q.split('&').forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@forEach
            val k = urlDecode(pair.substring(0, idx))
            val v = urlDecode(pair.substring(idx + 1))
            map[k] = v
        }
        return map
    }

    private fun urlDecode(s: String): String = try {
        URLDecoder.decode(s, StandardCharsets.UTF_8.name())
    } catch (_: Throwable) { s }

    // ---- Regexe ----
    private val CREDS_PATH = Regex("/(?:live|movie|movies|vod|series)/([^/]+)/([^/]+)/", RegexOption.IGNORE_CASE)
    private val ID_TAIL = Regex("/(\\d+)(?:\\.[a-z0-9]+)?(?:\\?.*)?$", RegexOption.IGNORE_CASE)
}

/**
 * Leichter Container für erkannte Portal-Creds.
 * Wird von XtreamConfig.fromCreds(...) verwendet.
 */
data class XtreamCreds(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val output: String = "m3u8"
)
