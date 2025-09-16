package com.chris.m3usuite.core.m3u

import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.core.xtream.XtreamDetect
import java.io.BufferedReader
import kotlinx.coroutines.runBlocking
import java.io.Reader
import java.text.Normalizer
import java.util.Locale

/**
 * Unified, robust, low‑RAM M3U/M3U‑Plus parser (drop‑in replacement).
 *
 * Features:
 *  - parseAuto(...): entscheidet selbst (Streaming vs. Liste) nach Größen‑Schwelle
 *  - parseStreaming(source,…): Produktionspfad (batch persist, kein Full‑Materialize)
 *  - parseToList(text): Convenience für kleine Inputs/Tests
 *  - Single‑URL Fallback (Dateien ohne #EXTINF → 1 Live‑Item)
 *  - Hyphen‑Keys (group-title, tvg-logo, …), BOM‑tolerant, Header‑Suffix nach URL ("|UA=…") wird entfernt
 *  - Typ‑Heuristik: URL‑Pattern, Query‑Hints, Group‑Heuristik, Container‑Fallbacks
 *  - Xtream‑ID für ALLE Typen; landet zusätzlich in extraJson.xtream
 *  - Parsing‑Points: trailer, year, rating, imdb/tmdb, catchup, timeshift, audio, aspect, resolution, provider …
 *    → Snapshot aller M3U‑Attribute unter extraJson.m3u.attrs; year/rating zusätzlich auf Top‑Level (Int?/Double?)
 */
object M3UParser {

    // ---------- Public APIs ----------

    /**
     * Automatische Wahl des Parsingpfads.
     * @param approxSizeBytes (optional) bekannte/geschätzte Eingabegröße (z. B. File.length()).
     * @param thresholdBytes ab welcher Größe Streaming verwendet wird (Default ~4MB).
     */
    suspend fun parseAuto(
        source: Reader,
        approxSizeBytes: Long? = null,
        thresholdBytes: Long = 4_000_000,
        batchSize: Int = 2000,
        onBatch: suspend (List<MediaItem>) -> Unit,
        onProgress: ((parsedItems: Long) -> Unit)? = null,
        cancel: (() -> Boolean)? = null
    ) {
        val big = (approxSizeBytes ?: (thresholdBytes + 1)) >= thresholdBytes
        if (big) {
            parseStreaming(source, batchSize, onBatch, onProgress, cancel)
        } else {
            val text = source.readText()
            val list = parseToList(text)
            onBatch(list)
            onProgress?.invoke(list.size.toLong())
        }
    }

    /** Convenience: kleine Inputs → Ergebnisliste. */
    fun parseToList(text: String): List<MediaItem> {
        val out = ArrayList<MediaItem>(4096)
        parseInternal(
            reader = text.reader(),
            onItem = { out += it },
            onFallbackSingleUrl = { url -> out += buildSingleUrlItem(url) },
            cancel = null
        )
        return out
    }

    /** Produktionspfad: streamt und gibt Items batchweise an onBatch ab. */
    suspend fun parseStreaming(
        source: Reader,
        batchSize: Int = 2000,
        onBatch: suspend (List<MediaItem>) -> Unit,
        onProgress: ((parsedItems: Long) -> Unit)? = null,
        cancel: (() -> Boolean)? = null
    ) {
        val batch = ArrayList<MediaItem>(batchSize)
        var count = 0L
        parseInternal(
            reader = source,
            onItem = { item ->
                batch += item
                count++
                if (batch.size >= batchSize) {
                    runBlocking { onBatch(batch.toList()) }
                    batch.clear()
                    onProgress?.invoke(count)
                }
            },
            onFallbackSingleUrl = { url -> batch += buildSingleUrlItem(url) },
            cancel = cancel
        )
        if (batch.isNotEmpty()) {
            runBlocking { onBatch(batch.toList()) }
            onProgress?.invoke(count)
        }
    }

    // ---------- Core Streaming Parser ----------

    private fun parseInternal(
        reader: Reader,
        onItem: (MediaItem) -> Unit,
        onFallbackSingleUrl: (String) -> Unit,
        cancel: (() -> Boolean)?
    ) {
        val br = if (reader is BufferedReader) reader else BufferedReader(reader, 64 * 1024)
        var lastExtInf: String? = null
        var sawAnyExtInf = false
        var firstNonCommentUrl: String? = null
        var firstLine = true

        for (rawLine in br.lineSequence()) {
            if (cancel?.invoke() == true) return
            var line = rawLine
            if (firstLine) {
                firstLine = false
                if (line.isNotEmpty() && line[0] == '\uFEFF') line = line.substring(1) // strip BOM
            }
            line = line.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTM3U", ignoreCase = true)) continue

            if (line.startsWith(EXTINF)) {
                sawAnyExtInf = true
                lastExtInf = line
                continue
            }

            // andere #EXT… Direktiven zwischen EXTINF und URL ignorieren
            if (line.startsWith("#")) continue

            if (firstNonCommentUrl == null) firstNonCommentUrl = line

            if (lastExtInf != null) {
                val item = buildFromPair(lastExtInf!!, line)
                onItem(item)
                lastExtInf = null
            }
        }

        // Single‑URL Fallback: kein EXTINF, aber eine URL gesehen
        if (!sawAnyExtInf && firstNonCommentUrl != null) {
            onFallbackSingleUrl(firstNonCommentUrl!!)
        }
    }

    // ---------- Builders & Helpers ----------

    private fun buildFromPair(extInf: String, urlLine: String): MediaItem {
        val (attrs, rawName) = parseExtInf(extInf)
        val url = urlLine.substringBefore('|').trim() // optionale Header nach '|' entfernen
        val type = inferType(attrs, url)
        val display = stripLangPrefix(rawName)

        val groupTitle = attrs["group-title"]?.takeIf { it.isNotBlank() }?.trim()
        val languageAsGroup = if (groupTitle.isNullOrBlank() && (type == "vod" || type == "series"))
            detectLanguage(rawName, groupTitle, url) else null
        val finalGroup = groupTitle ?: languageAsGroup

        val xtId = XtreamDetect.parseStreamId(url)

        val (year, rating) = parseYearAndRatingFromNameOrAttrs(display, attrs)
        val trailer = attrs["trailer"]?.takeIf { it.isNotBlank() }
        val imdb = attrs["imdb-id"] ?: attrs["imdb"]
        val tmdb = attrs["tmdb-id"] ?: attrs["tmdb"]
        val catchup = attrs["catchup"] ?: attrs["tv-archive"]
        val timeshift = attrs["timeshift"] ?: attrs["tv-archive-duration"]
        val audioTrack = attrs["audio-track"]
        val aspect = attrs["aspect-ratio"]
        val resolution = attrs["resolution"] ?: attrs["tvg-resolution"]
        val provider = attrs["provider"] ?: attrs["provider-name"]

        val extraJson = buildExtraJson(
            attrs = attrs,
            type = type,
            xtreamId = xtId,
            trailer = trailer,
            year = year,
            rating = rating,
            imdb = imdb,
            tmdb = tmdb,
            catchup = catchup,
            timeshift = timeshift,
            audio = audioTrack,
            aspect = aspect,
            resolution = resolution,
            provider = provider
        )

        return MediaItem(
            type = type,
            streamId = if (type == "live") xtId else null,
            name = display,
            sortTitle = normalize(display),
            categoryId = finalGroup,
            categoryName = finalGroup,
            logo = attrs["tvg-logo"],
            poster = attrs["tvg-logo"],
            backdrop = null,
            epgChannelId = attrs["tvg-id"],
            year = year,
            rating = rating,
            durationSecs = null,
            plot = attrs["plot"] ?: attrs["description"],
            url = url,
            extraJson = extraJson
        )
    }

    private fun buildSingleUrlItem(url: String): MediaItem = MediaItem(
        type = "live",
        streamId = XtreamDetect.parseStreamId(url),
        name = "Stream",
        sortTitle = "stream",
        categoryId = null,
        categoryName = null,
        logo = null,
        poster = null,
        backdrop = null,
        epgChannelId = null,
        year = null,
        rating = null,
        durationSecs = null,
        plot = null,
        url = url,
        extraJson = null
    )

    private fun parseExtInf(line: String): Pair<Map<String, String>, String> {
        val payload = line.removePrefix("$EXTINF:")
        val split = payload.split(",", limit = 2)
        val attrsPart = split.getOrNull(0).orEmpty()
        val displayName = split.getOrNull(1)?.trim().orEmpty()

        val attrs = LinkedHashMap<String, String>()
        for (m in ATTR_REGEX.findAll(attrsPart)) {
            attrs[m.groupValues[1].lowercase(Locale.ROOT)] = m.groupValues[2]
        }
        val name = if (displayName.isNotBlank()) displayName else attrs["tvg-name"].orEmpty()
        return attrs to name
    }

    private fun normalize(name: String): String {
        if (name.isBlank()) return name
        val decomposed = Normalizer.normalize(name, Normalizer.Form.NFKD)
        val noMarks = decomposed.replace(Regex("\\p{M}+"), "")
        return noMarks.lowercase(Locale.ROOT)
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
    }

    private fun inferType(attrs: Map<String, String>, url: String): String {
        val grp = (attrs["group-title"] ?: "").lowercase(Locale.ROOT)
        val u = url.lowercase(Locale.ROOT)

        PROVIDER_PATTERNS.firstOrNull { it.regex.containsMatchIn(u) }?.let { return it.type }

        if (Regex("[?&]type=series").containsMatchIn(u)) return "series"
        if (Regex("[?&]type=movie|[?&]type=vod").containsMatchIn(u)) return "vod"
        if (Regex("[?&]type=live").containsMatchIn(u)) return "live"

        if (u.contains("/series") || u.contains("series/") || u.contains("series=")) return "series"
        if (u.contains("/movie") || u.contains("movies/") || u.contains("movie=") ||
            u.contains("/vod") || u.contains("vod/") || u.contains("vod=")) return "vod"

        if (grp.contains("series") || grp.contains("serien") || grp.contains("tv shows")) return "series"
        if (grp.contains("movie") || grp.contains("filme") || grp.contains("vod")) return "vod"

        if (u.endsWith(".mp4") || u.endsWith(".mkv") || u.endsWith(".avi") || u.endsWith(".mov")) return "vod"
        if (u.endsWith(".m3u8") || u.endsWith(".ts")) return "live"

        return "live"
    }

    // ----- small helpers -----
    private fun stripLangPrefix(name: String): String {
        val m = LEADING_COUNTRY_REGEX.find(name)
        return if (m != null) name.removeRange(m.range).trimStart() else name
    }

    private fun detectLanguage(name: String?, group: String?, url: String?): String? {
        fun norm(s: String?) = s?.lowercase(Locale.ROOT).orEmpty()
        val n = norm(name)
        val g = norm(group)
        val u = norm(url)

        BRACKET_LANG_REGEX.find(name.orEmpty())?.groupValues?.getOrNull(1)?.let { raw ->
            val key = raw.lowercase(Locale.ROOT)
            LANG_MAP[key]?.let { return it }
            if (key.length in 2..3) return key.uppercase(Locale.ROOT)
        }
        LEADING_COUNTRY_REGEX.find(name.orEmpty())?.groupValues?.getOrNull(1)?.let { cc ->
            if (cc.length in 2..3) return cc.uppercase(Locale.ROOT)
        }
        for ((k, v) in LANG_MAP) {
            if (g.contains(k) || n.contains(k)) return v
        }
        for ((k, v) in LANG_MAP) {
            if (u.contains("/$k/") || u.contains("_${k}_") || u.contains("-${k}-")) return v
        }
        return null
    }

    private fun parseYearAndRatingFromNameOrAttrs(
        display: String,
        attrs: Map<String, String>
    ): Pair<Int?, Double?> {
        val yearAttr = attrs["year"]?.takeIf { it.matches(Regex("\\d{4}")) }?.toInt()
        val ratingAttr = attrs["rating"]?.takeIf { it.matches(Regex("(10|[0-9])(?:\\.[0-9])?")) }?.toDouble()
        if (yearAttr != null || ratingAttr != null) return yearAttr to ratingAttr

        val pipe = Regex("\\|\\s*(\\d{4})\\s*\\|\\s*([0-9](?:\\.[0-9])?)\\s*\\|?")
        pipe.find(display)?.let { m ->
            val y = m.groupValues.getOrNull(1)?.toIntOrNull()
            val r = m.groupValues.getOrNull(2)?.toDoubleOrNull()
            return y to r
        }
        val paren = Regex("\\((\\d{4})\\)")
        paren.find(display)?.let { m ->
            val y = m.groupValues.getOrNull(1)?.toIntOrNull()
            return y to ratingAttr
        }
        return null to null
    }

    private fun buildExtraJson(
        attrs: Map<String, String>,
        type: String,
        xtreamId: Int?,
        trailer: String?,
        year: Int?,
        rating: Double?,
        imdb: String?,
        tmdb: String?,
        catchup: String?,
        timeshift: String?,
        audio: String?,
        aspect: String?,
        resolution: String?,
        provider: String?
    ): String? {
        val hasExtras = listOf(trailer, year?.toString(), rating?.toString(), imdb, tmdb, catchup, timeshift, audio, aspect, resolution, provider)
            .any { !it.isNullOrBlank() } || xtreamId != null || attrs.isNotEmpty()
        if (!hasExtras) return null

        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
        val sb = StringBuilder(256)
        sb.append('{')
        sb.append("\"m3u\":{\"attrs\":{")
        var first = true
        attrs.forEach { (k, v) ->
            if (v.isNotBlank()) {
                if (!first) sb.append(',')
                sb.append("\"").append(esc(k)).append("\":\"").append(esc(v)).append("\"")
                first = false
            }
        }
        sb.append("}")
        val hintPairs = listOf(
            "trailer" to trailer,
            "year" to year?.toString(),
            "rating" to rating?.toString(),
            "imdb" to imdb,
            "tmdb" to tmdb,
            "catchup" to catchup,
            "timeshift" to timeshift,
            "audio" to audio,
            "aspect" to aspect,
            "resolution" to resolution,
            "provider" to provider
        ).filter { it.second != null && it.second!!.isNotBlank() }
        if (hintPairs.isNotEmpty()) {
            sb.append(",\"hints\":{")
            hintPairs.forEachIndexed { idx, (k, v) ->
                if (idx > 0) sb.append(',')
                sb.append("\"").append(esc(k)).append("\":\"").append(esc(v!!)).append("\"")
            }
            sb.append('}')
        }
        sb.append('}') // end m3u
        if (xtreamId != null) {
            sb.append(",\"xtream\":{\"kind\":\"").append(type).append("\",\"id\":").append(xtreamId).append('}')
        }
        sb.append('}')
        return sb.toString()
    }

    // ---------- Regex/Const ----------

    private const val EXTINF = "#EXTINF"
    private val ATTR_REGEX = Regex("""(\\w[\\w\\-]*)=\\"([^\\"]*)\\"""")

    private data class PatternRule(val regex: Regex, val type: String)
    private val PROVIDER_PATTERNS = listOf(
        PatternRule(Regex("/(series)/", RegexOption.IGNORE_CASE), "series"),
        PatternRule(Regex("/(movies?|vod)/", RegexOption.IGNORE_CASE), "vod"),
        PatternRule(Regex("/(live|channel|stream)/[^\\s]+", RegexOption.IGNORE_CASE), "live"),
        PatternRule(Regex("index\\.m3u8(\\?.*)?$", RegexOption.IGNORE_CASE), "live"),
        // kompakter Xtream-Pfad: http(s)://host[:port]/user/pass/12345[.ext]
        PatternRule(Regex("^https?://[^/]+(?::\\d+)?/[^/]+/[^/]+/\\d+(?:\\.[a-z0-9]+)?$", RegexOption.IGNORE_CASE), "live")
    )

    private val LANG_MAP = mapOf(
        "de" to "DE","ger" to "DE","german" to "DE","deutsch" to "DE",
        "en" to "EN","eng" to "EN","english" to "EN",
        "us" to "US","uk" to "UK","gb" to "UK",
        "fr" to "FR","french" to "FR",
        "it" to "IT","italian" to "IT",
        "es" to "ES","spa" to "ES","spanish" to "ES",
        "tr" to "TR","turkish" to "TR",
        "ar" to "AR","arabic" to "AR",
        "ru" to "RU","russian" to "RU",
        "pl" to "PL","polish" to "PL",
        "pt" to "PT","pt-br" to "PT-BR","brazil" to "PT-BR",
        "nl" to "NL","dutch" to "NL",
        "se" to "SE","sv" to "SE","swedish" to "SE",
        "no" to "NO","norwegian" to "NO",
        "fi" to "FI","finnish" to "FI",
        "da" to "DA","dk" to "DA","danish" to "DA"
    )

    private val LEADING_COUNTRY_REGEX = Regex("^([A-Z]{2,3}):\\s*")
    private val BRACKET_LANG_REGEX = Regex("\\[([A-Za-z]{2,3}(?:-[A-Za-z]{2})?)\\]")
}
