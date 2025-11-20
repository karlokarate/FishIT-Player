package com.chris.m3usuite.core.xtream

import android.net.Uri
import androidx.core.net.toUri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * XtreamConfig – VARIANT-PREFERRED, ALIAS-AWARE, PORT-EXPLICIT
 *
 * Rolle:
 *  - Reine URL-Fabrik (keine Netzwerklogik, kein Port-Raten)
 *  - Baut deterministisch player_api- und Play-URLs (live/vod/series)
 *  - ALLES variabel per Präferenz-Listen; nur definierte Fallbacks sind fix
 *
 * Wichtige Punkte:
 *  - Ports werden IMMER explizit geführt.
 *  - LIVE-/VOD-/SERIES-Container nur über Präferenzen/Fälle – keine harten Vorgaben mehr.
 *  - VOD-Alias (movie|vod|movies|...) kommt i. d. R. aus Capabilities; hier als konfigurierbarer Wert.
 *  - Optionaler basePath (Reverse-Proxy-Unterpfade), sauber normalisiert.
 *  - username/password in Pfaden werden URL-encoded.
 *
 * Zusammenspiel:
 *  - Port & vodKind holst du vorher aus CapabilityDiscoverer (caps.resolvedAliases.vodKind, Port-Resolver).
 *  - Hinweise aus XtreamDetect/fromM3uUrl landen als *Präferenz* (z. B. ganz vorn in den Listen).
 *
 * Keine Netzwerklogik. Keine Kandidatenlisten im Code. Nur Fallbacks:
 *  - live: defaultPref = ["m3u8","ts"]
 *  - vod/series: defaultPref = ["mp4","mkv","avi"]
 */
data class XtreamConfig(
    /** "http" oder "https" – MUSS explizit gesetzt sein. */
    val scheme: String,
    val host: String,
    /** Jeder Port erlaubt; wird immer explizit in URLs geführt. */
    val port: Int,
    val username: String,
    val password: String,
    /**
     * Pfadsegmente/ Aliasse (konfigurierbar):
     * - liveKind bleibt i. d. R. "live".
     * - vodKind kommt aus Capabilities (vod|movie|movies …); Fallback "movie".
     * - seriesKind ist meist "series".
     */
    val pathKinds: PathKinds = PathKinds(),
    /**
     * Optionales Panel-Unterverzeichnis (Reverse Proxy), z. B. "/xtream".
     * Normalisierung: führender Slash erlaubt, *kein* Slash am Ende.
     */
    val basePath: String? = null,
    /**
     * Container-Präferenzen:
     * - Live (Transport): z. B. ["m3u8","ts"] oder ["ts","m3u8"]
     * - VOD/Series (Dateiendung): z. B. ["mp4","mkv","avi"]
     * Hinweise aus M3U/Detect können hier *an erster Stelle* eingefügt werden.
     */
    val liveExtPrefs: List<String> = listOf("m3u8", "ts"),
    val vodExtPrefs: List<String> = listOf("mp4", "mkv", "avi"),
    val seriesExtPrefs: List<String> = listOf("mp4", "mkv", "avi"),
) {
    // Legacy-compat constructor (host, port, user, pass, output[, scheme]) used across UI code.
    // Maps 'output' as a live transport preference.
    @Deprecated("Use primary constructor with scheme + prefs")
    constructor(
        host: String,
        port: Int,
        username: String,
        password: String,
        output: String,
        scheme: String = if (port == 443) "https" else "http",
    ) : this(
        scheme = scheme,
        host = host,
        port = port,
        username = username,
        password = password,
        pathKinds = PathKinds(),
        basePath = null,
        liveExtPrefs = listOf(normalizeLiveExt(output)).filter { it.isNotBlank() }.ifEmpty { listOf("m3u8") },
        vodExtPrefs = listOf("mp4", "mkv", "avi"),
        seriesExtPrefs = listOf("mp4", "mkv", "avi"),
    )

    init {
        require(scheme.equals("http", true) || scheme.equals("https", true)) {
            "scheme must be 'http' or 'https'"
        }
        require(port > 0) { "port must be > 0" }
        require(username.isNotBlank()) { "username must not be blank" }
        require(password.isNotBlank()) { "password must not be blank" }
    }

    /** Basis-URL IMMER inkl. Port (auch 80/443 explizit). */
    val portalBase: String get() = "${scheme.lowercase()}://$host:$port"

    /** player_api-Basis inkl. optionalem basePath. */
    private val playerApiBase: String get() =
        buildString {
            append(portalBase)
            basePath?.normalizedOrNull()?.let { append(it) }
            append("/player_api.php")
        }

    /**
     * player_api-URL mit deterministischer Query-Reihenfolge wie in Referenz-Clients:
     *   /player_api.php?action=... [&extra] &username=... &password=...
     * 'extra' darf mit führendem '&' oder '?' übergeben werden (z. B. "&category_id=*", "&vod_id=123").
     *
     * Erstellt die URL strikt via HttpUrl.Builder, um Encoding/Trennzeichenfehler zu vermeiden.
     */
    fun playerApi(
        action: String,
        extra: String? = null,
    ): String {
        val b =
            okhttp3.HttpUrl
                .Builder()
                .scheme(scheme.lowercase())
                .host(host)
                .port(port)
        basePath?.normalizedOrNull()?.let { path ->
            // split path segments without leading '/'
            path
                .removePrefix("/")
                .split('/')
                .filter { it.isNotBlank() }
                .forEach { seg -> b.addPathSegment(seg) }
        }
        b.addPathSegment("player_api.php")
        // Order: action → extras → username/password
        b.addQueryParameter("action", action)
        if (!extra.isNullOrBlank()) {
            // parse k=v pairs from extra string
            val raw = extra.removePrefix("?").removePrefix("&")
            raw.split('&').filter { it.isNotBlank() }.forEach { pair ->
                val idx = pair.indexOf('=')
                val key = if (idx >= 0) pair.substring(0, idx) else pair
                val value = if (idx >= 0) pair.substring(idx + 1) else ""
                if (key.isNotBlank()) b.addQueryParameter(key, value)
            }
        }
        b.addQueryParameter("username", username)
        b.addQueryParameter("password", password)
        return b.build().toString()
    }

    // ---------------------
    // PLAY-URLs
    // ---------------------

    /**
     * LIVE-Play-URL.
     * Auswahlreihenfolge: extOverride → liveExtPrefs[0] → definierter Fallback ("m3u8").
     * Normalisierung: "hls" → "m3u8".
     */
    fun liveUrl(
        streamId: Int,
        extOverride: String? = null,
    ): String {
        val ext =
            normalizeLiveExt(
                extOverride
                    ?: liveExtPrefs.firstOrNull().orEmpty(),
            ).ifBlank { "m3u8" } // definierter Fallback
        return buildString {
            append(portalBase)
            basePath?.normalizedOrNull()?.let { append(it) }
            append("/")
            append(pathKinds.liveKind)
            append("/")
            append(p(username))
            append("/")
            append(p(password))
            append("/")
            append(streamId)
            append(".")
            append(ext)
        }
    }

    /**
     * VOD-Play-URL.
     * Reihenfolge: container (vom Call; z. B. aus API) → vodExtPrefs[0] → definierter Fallback ("mp4").
     */
    fun vodUrl(
        vodId: Int,
        container: String?,
    ): String {
        val ext =
            sanitizeExt(
                container?.lowercase()?.trim()
                    ?: vodExtPrefs.firstOrNull().orEmpty(),
            ).ifBlank { "mp4" }
        return buildString {
            append(portalBase)
            basePath?.normalizedOrNull()?.let { append(it) }
            append("/")
            append(pathKinds.vodKind)
            append("/")
            append(p(username))
            append("/")
            append(p(password))
            append("/")
            append(vodId)
            append(".")
            append(ext)
        }
    }

    /**
     * Series-Play-URL (empfohlen).
     * Reihenfolge: ext (pro Episode, z. B. aus RawEpisode.container_extension) → seriesExtPrefs[0] → Fallback ("mp4").
     */
    fun seriesEpisodeUrl(
        seriesId: Int,
        season: Int,
        episode: Int,
        ext: String? = null,
    ): String {
        val e =
            sanitizeExt(
                ext?.lowercase()?.trim()
                    ?: seriesExtPrefs.firstOrNull().orEmpty(),
            ).ifBlank { "mp4" }
        return buildString {
            append(portalBase)
            basePath?.normalizedOrNull()?.let { append(it) }
            append("/")
            append(pathKinds.seriesKind)
            append("/")
            append(p(username))
            append("/")
            append(p(password))
            append("/")
            append(seriesId)
            append("/")
            append(season)
            append("/")
            append(episode)
            append(".")
            append(e)
        }
    }

    /**
     * DEPRECATED – nur für Legacy-Panels mit episodeId.
     * Reihenfolge wie VOD.
     */
    @Deprecated("Use seriesEpisodeUrl(seriesId, season, episode, ext)")
    fun seriesEpisodeUrl(
        episodeId: Int,
        container: String?,
    ): String {
        val ext =
            sanitizeExt(
                container?.lowercase()?.trim()
                    ?: seriesExtPrefs.firstOrNull().orEmpty(),
            ).ifBlank { "mp4" }
        return buildString {
            append(portalBase)
            basePath?.normalizedOrNull()?.let { append(it) }
            append("/")
            append(pathKinds.seriesKind)
            append("/")
            append(p(username))
            append("/")
            append(p(password))
            append("/")
            append(episodeId)
            append(".")
            append(ext)
        }
    }

    // ---------------------
    // Factories
    // ---------------------

    /**
     * Factory aus get.php – Schema & Port werden AUS DER URL übernommen.
     * Wichtig: **Kein** implizites 80/443! Ist kein Port vorhanden, -> null (vorher Discovery/Port-Resolver nutzen).
     * 'output' wird als **Präferenz-Hinweis** verwendet (nicht fix):
     *   - Wenn 'output' = ts|m3u8 → liveExtPrefs = [output, <default…>]
     *   - Wenn 'container' gesetzt (manche Panels), → vod/seriesExtPrefs = [container, <default…>]
     */
    companion object {
        fun fromM3uUrl(
            m3u: String,
            // optional known alias from discovery
            discoveredVodKind: String? = null,
            basePath: String? = null,
            defaults: DefaultPrefs = DefaultPrefs(),
        ): XtreamConfig? =
            runCatching {
                val u = m3u.toUri()
                val scheme = (u.scheme ?: "http").lowercase()
                val host = (u.host ?: return null)
                val port = u.port
                if (port <= 0) return null // kein Raten – Discovery zuerst

                val user = u.getQueryParameter("username") ?: return null
                val pass = u.getQueryParameter("password") ?: return null

                val out = (u.getQueryParameter("output") ?: "").lowercase().trim()
                val container = (u.getQueryParameter("container") ?: "").lowercase().trim()

                val livePrefs = buildLivePrefs(out, defaults.liveExtPrefs)
                val vodPrefs = buildFilePrefs(container, defaults.vodExtPrefs)
                val seriesPrefs = buildFilePrefs(container, defaults.seriesExtPrefs)

                XtreamConfig(
                    scheme = scheme,
                    host = host,
                    port = port,
                    username = user,
                    password = pass,
                    pathKinds =
                        PathKinds(
                            liveKind = "live",
                            vodKind = discoveredVodKind?.ifBlank { null } ?: "movie",
                            seriesKind = "series",
                        ),
                    basePath = basePath,
                    liveExtPrefs = livePrefs,
                    vodExtPrefs = vodPrefs,
                    seriesExtPrefs = seriesPrefs,
                )
            }.getOrNull()

        // ---- Helpers für Präferenzen ----
        private fun buildLivePrefs(
            hint: String?,
            defaults: List<String>,
        ): List<String> {
            val norm = normalizeLiveExt(hint)
            return when {
                norm.isNotBlank() && !defaults.contains(norm) -> listOf(norm) + defaults
                norm.isNotBlank() -> listOf(norm) + defaults.filterNot { it == norm }
                else -> defaults
            }
        }

        private fun buildFilePrefs(
            hint: String?,
            defaults: List<String>,
        ): List<String> {
            val e = sanitizeExt(hint?.lowercase()?.trim())
            return when {
                e.isNotBlank() && !defaults.contains(e) -> listOf(e) + defaults
                e.isNotBlank() -> listOf(e) + defaults.filterNot { it == e }
                else -> defaults
            }
        }
    }
}

/** Konfigurierbare Pfadaliase. */
data class PathKinds(
    val liveKind: String = "live",
    val vodKind: String = "movie",
    val seriesKind: String = "series",
)

/** App-Defaults (zentral änderbar, z. B. via Settings/DI). */
data class DefaultPrefs(
    val liveExtPrefs: List<String> = listOf("m3u8", "ts"),
    val vodExtPrefs: List<String> = listOf("mp4", "mkv", "avi"),
    val seriesExtPrefs: List<String> = listOf("mp4", "mkv", "avi"),
)

// ---------------------
// Utils
// ---------------------
private fun String.normalizedOrNull(): String? {
    val t = this.trim()
    if (t.isEmpty()) return null
    var s = if (!t.startsWith("/")) "/$t" else t
    s = s.removeSuffix("/")
    return if (s == "/") null else s
}

private fun normalizeLiveExt(ext: String?): String =
    when (ext?.lowercase()?.trim()) {
        "hls" -> "m3u8"
        "m3u8" -> "m3u8"
        "ts" -> "ts"
        else -> ""
    }

private fun sanitizeExt(ext: String?): String {
    val e = ext?.lowercase()?.trim().orEmpty()
    // zulässige, sichere alnum-Endungen (einfach halten):
    val ok = Regex("^[a-z0-9]{2,5}$")
    return if (ok.matches(e)) e else ""
}

private fun q(s: String): String = Uri.encode(s)

private fun p(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8.name())
