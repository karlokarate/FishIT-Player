package com.chris.m3usuite.telegram

import java.util.Locale

object TelegramHeuristics {

    data class Parsed(
        val title: String,
        val year: Int? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val quality: String? = null,
        val languages: Set<String> = emptySet()
    ) {
        val isSeries: Boolean get() = season != null && episode != null
    }

    @JvmStatic
    fun parse(raw: String?): Parsed {
        val safe = (raw ?: "").trim()
        if (safe.isEmpty()) {
            return Parsed(title = "")
        }

        val primary = pickPrimaryLine(safe)
        val noExt = stripExtension(primary)
        val normalized = normalizeSeparators(noExt)
        val tokens = tokenizeUpper(normalized)

        val (season, episode) = detectSeasonEpisode(normalized)
        val year = detectYear(normalized)
        val languages = detectLanguages(tokens)
        val quality = detectQuality(normalized, tokens)
        val title = normalizeTitle(normalized, season, episode, year)

        return Parsed(
            title = title,
            year = year,
            season = season,
            episode = episode,
            quality = quality,
            languages = languages
        )
    }

    private fun pickPrimaryLine(text: String): String {
        val line = text.lineSequence().firstOrNull { it.isNotBlank() } ?: text
        val slashCut = line.substringAfterLast('/', line)
        val bslashCut = slashCut.substringAfterLast('\\', slashCut)
        return bslashCut.trim()
    }

    private fun stripExtension(input: String): String {
        return input.replace(Regex("""(?i)\.[a-z0-9]{2,5}$"""), "")
    }

    private fun normalizeSeparators(input: String): String {
        var s = input
        s = s.replace('_', ' ')
        s = s.replace('.', ' ')
        s = s.replace(Regex("""[|]+"""), " ")
        s = s.replace(Regex("""\s+"""), " ").trim()
        return s
    }

    private fun tokenizeUpper(input: String): List<String> {
        return input
            .split(Regex("""[ \t\p{Zs}\[\]\(\)\{\}/\\\-]+"""))
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.uppercase(Locale.ROOT) }
            .toList()
    }

    private val sxePattern = Regex("""(?i)\bS(\d{1,2})\s*[.\-_ ]*E(\d{1,3})\b""")
    private val xPattern = Regex("""(?i)\b(\d{1,2})\s*[xX]\s*(\d{1,3})\b""")
    private val deStaffelFolgePattern =
        Regex("""(?i)\bStaffel\s*(\d{1,2})\b.*?\b(Folge|Ep(?:isode)?)\s*(\d{1,3})\b""")

    private fun detectSeasonEpisode(input: String): Pair<Int?, Int?> {
        sxePattern.find(input)?.let {
            val s = it.groupValues.getOrNull(1)?.toIntOrNull()
            val e = it.groupValues.getOrNull(2)?.toIntOrNull()
            if (s != null && e != null) return s to e
        }
        xPattern.find(input)?.let {
            val s = it.groupValues.getOrNull(1)?.toIntOrNull()
            val e = it.groupValues.getOrNull(2)?.toIntOrNull()
            if (s != null && e != null) return s to e
        }
        deStaffelFolgePattern.find(input)?.let {
            val s = it.groups[1]?.value?.toIntOrNull()
            val e = it.groups[3]?.value?.toIntOrNull()
            if (s != null && e != null) return s to e
        }
        return null to null
    }

    private val yearPattern = Regex("""(?<!\d)(19\d{2}|20\d{2})(?!\d)""")

    private fun detectYear(input: String): Int? {
        val m = yearPattern.find(input) ?: return null
        val y = m.groupValues[1].toIntOrNull() ?: return null
        return if (y in 1900..2099) y else null
    }

    private fun detectLanguages(tokensUpper: List<String>): Set<String> {
        val langs = mutableSetOf<String>()
        fun has(vararg keys: String): Boolean = tokensUpper.any { t ->
            keys.any { k -> t == k }
        }
        fun hasLike(vararg keys: String): Boolean = tokensUpper.any { t ->
            keys.any { k -> t.contains(k) }
        }

        if (has("MULTI") || hasLike("MULTI-")) {
            langs.add("multi")
        }
        if (has("DUAL")) {
            langs.add("dual")
        }
        if (has("GERMAN", "DEUTSCH") || has("DE")) {
            langs.add("de")
        }
        if (has("ENGLISH") || has("EN")) {
            langs.add("en")
        }
        if (has("FRENCH") || has("FR")) {
            langs.add("fr")
        }
        if (has("SPANISH", "ESPANOL") || has("ES")) {
            langs.add("es")
        }
        if (has("ITALIAN") || has("IT")) {
            langs.add("it")
        }
        if (has("HINDI") || has("HI")) {
            langs.add("hi")
        }
        return langs
    }

    private fun detectQuality(input: String, tokensUpper: List<String>): String? {
        val up = input.uppercase(Locale.ROOT)

        fun containsWord(word: String): Boolean {
            val rx = Regex("""(?i)(^|[^A-Z0-9])${Regex.escape(word)}([^A-Z0-9]|$)""")
            return rx.containsMatchIn(up)
        }

        fun hasAny(vararg words: String) = words.any { containsWord(it) }

        val parts = mutableListOf<String>()

        when {
            hasAny("2160P", "4K", "UHD") -> parts += "2160p"
            hasAny("1080P") -> parts += "1080p"
            hasAny("720P") -> parts += "720p"
            hasAny("480P") -> parts += "480p"
        }

        when {
            hasAny("WEB-DL", "WEBDL", "WEBRIP", "WEB") -> parts.add(0, "WEB-DL")
            hasAny("BLURAY", "BDRIP", "BRRIP", "BDREMUX", "REMUX") -> parts.add(0, "BluRay")
            hasAny("HDTV") -> parts.add(0, "HDTV")
        }

        if (hasAny("HDR", "DOVI", "DV")) {
            parts += "HDR"
        }

        return if (parts.isEmpty()) null else parts.distinct().joinToString(" ")
    }

    private val metaTokens = setOf(
        "WEB-DL", "WEBDL", "WEBRIP", "WEB",
        "BLURAY", "BDRIP", "BRRIP", "REMUX", "HDTS", "HDTV", "DVDRIP",
        "2160P", "4K", "UHD", "1080P", "720P", "480P",
        "HDR", "DV", "DOVI",
        "X264", "X265", "H264", "H265", "HEVC", "AV1", "AAC", "AC3", "DTS", "TRUEHD", "ATMOS", "DDP5.1", "DDP5", "DDP",
        "PROPER", "REPACK", "READNFO", "EXTENDED", "UNCUT", "COMPLETE", "REMASTER", "REMASTERED",
        "GERMAN", "DEUTSCH", "ENGLISH", "MULTI", "DUAL", "DE", "EN",
        "MKV", "MP4", "M4V", "AVI", "TS", "M2TS", "WEBM"
    )

    private fun normalizeTitle(input: String, season: Int?, episode: Int?, year: Int?): String {
        var t = cutBeforeDividers(input)
            .let { sxePattern.replace(it, " ") }
            .let { xPattern.replace(it, " ") }
            .let { deStaffelFolgePattern.replace(it, " ") }

        if (year != null) {
            t = t.replace(Regex("""(?<!\d)${year}(?!\d)"""), " ")
        }

        t = t.replace(Regex("""(?i)\b(Season|Staffel|Folge|Episode|Ep)\b\.?\s*\d{1,3}"""), " ")
        t = removeMetaTokens(t)
        t = t.replace(Regex("""[\[\]\(\)\{\}]"""), " ")
        t = t.replace(Regex("""[|]+"""), " ")
        t = t.replace('_', ' ')
        t = t.replace('.', ' ')
        t = t.replace(Regex("""\s+"""), " ").trim()

        if (t.isEmpty()) {
            t = rebuildTitleFromTokens(input)
        }

        if (t.isBlank()) {
            val fallback2 = stripExtension(input).replace(Regex("""[._\[\]\(\)\{\}|]+"""), " ").trim()
            if (fallback2.isNotBlank()) {
                t = fallback2
            }
        }

        if (t.isBlank()) {
            t = ""
        } else {
            t = toNiceCase(t)
        }

        return t
    }

    private fun cutBeforeDividers(s: String): String {
        var idx = s.length
        fun update(delim: String) {
            val p = s.indexOf(delim)
            if (p >= 0 && p < idx) idx = p
        }
        update(" | ")
        update(" - ")
        val p1 = s.indexOf('[').takeIf { it >= 0 } ?: Int.MAX_VALUE
        val p2 = s.indexOf('(').takeIf { it >= 0 } ?: Int.MAX_VALUE
        val p3 = s.indexOf('{').takeIf { it >= 0 } ?: Int.MAX_VALUE
        idx = minOf(idx, p1, p2, p3)
        val cut = s.substring(0, idx).trim()
        return cut.ifEmpty { s }
    }

    private fun removeMetaTokens(text: String): String {
        var t = text
        val rx = Regex(
            """(?i)\b(2160p|4k|uhd|1080p|720p|480p|hdr|dv|dovi|web-?dl|webrip|bluray|bdrip|brrip|bdremux|remux|hdtv|dvdrip|x264|x265|h\.?264|h\.?265|hevc|av1|aac|ac3|dts|truehd|atmos|ddp5?\.?1|proper|repack|readnfo|extended|uncut|complete|remaster(?:ed)?|german|deutsch|english|multi|dual|mkv|mp4|m4v|avi|ts|m2ts|webm)\b"""
        )
        t = rx.replace(t, " ")
        t = t.replace(Regex("""(?i)\b(2160|1080|720|480)\b"""), " ")
        t = t.replace(Regex("""[._\-]+"""), " ")
        return t
    }

    private fun rebuildTitleFromTokens(input: String): String {
        val tokens = tokenizeUpper(input)
        val keep = tokens.filter { tok ->
            when {
                metaTokens.contains(tok) -> false
                tok.matches(Regex("""\d{4}""")) && tok.startsWith("19").not() && tok.startsWith("20").not() -> false
                tok.matches(Regex("""(2160|1080|720|480)P?""")) -> false
                tok.matches(Regex("""S\d{1,2}E\d{1,3}""")) -> false
                tok.matches(Regex("""\d{1,2}X\d{1,3}""")) -> false
                else -> true
            }
        }
        val title = keep.joinToString(" ") {
            it.lowercase(Locale.ROOT).replaceFirstChar { c -> c.titlecase(Locale.ROOT) }
        }
        return title.trim()
    }

    private fun toNiceCase(input: String): String {
        val words = input.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val preserved = setOf("I", "II", "III", "IV", "VI", "VII", "VIII", "IX", "X", "TV", "UHD", "HDR", "DV")
        return words.joinToString(" ") { w ->
            when {
                preserved.contains(w.uppercase(Locale.ROOT)) -> w.uppercase(Locale.ROOT)
                w.length <= 2 && w.all { it.isLetter() } -> w.uppercase(Locale.ROOT)
                else -> w.lowercase(Locale.ROOT).replaceFirstChar { c -> c.titlecase(Locale.ROOT) }
            }
        }.trim()
    }

    @JvmStatic
    fun seTag(season: Int?, episode: Int?): String? {
        if (season == null || episode == null) return null
        return "S" + season.toString().padStart(2, '0') + "E" + episode.toString().padStart(2, '0')
    }
}