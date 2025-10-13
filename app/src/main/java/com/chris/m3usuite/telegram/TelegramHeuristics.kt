package com.chris.m3usuite.telegram

/** Simple parsing utilities for Telegram captions to distinguish VOD vs Series episodes.
 * Rules:
 * - Series pattern: SxxEyy, SxEy, or NxM (1x02) anywhere in the text.
 * - Series title: prefix before the pattern, trimmed of separators and brackets.
 * - Episode title: remainder after the pattern, with common separators removed.
 */
object TelegramHeuristics {
    data class ParseResult(
        val isSeries: Boolean,
        val seriesTitle: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val episodeEnd: Int? = null, // for ranges E01-03
        val title: String? = null,
        val language: String? = null // ISO-ish (de, en, es...)
    )

    // Single and ranged patterns
    private val sxxexx = Regex("""(?i)(?:^|[\s._\-\[\(])S(\d{1,2})[Eex](\d{1,2})(?:$|[\s._\-\]\)])""")
    private val sxxexxRange = Regex("""(?i)(?:^|[\s._\-\[\(])S(\d{1,2})E(\d{1,2})[-–](?:E)?(\d{1,2})(?:$|[\s._\-\]\)])""")
    private val nxm   = Regex("""(?i)(?:^|[\s._\-\[\(])(\d{1,2})x(\d{1,2})(?:$|[\s._\-\]\)])""")
    private val nxmRange = Regex("""(?i)(?:^|[\s._\-\[\(])(\d{1,2})x(\d{1,2})[-–](\d{1,2})(?:$|[\s._\-\]\)])""")
    private val sXeY  = Regex("""(?i)(?:^|[\s._\-\[\(])S(\d{1,2})[\s._\-]*E(\d{1,2})(?:$|[\s._\-\]\)])""")
    // German verbose forms: "Staffel 6 Folge 10"
    private val staffelFolge = Regex("""(?i)Staffel\s*(\d{1,2})\D{0,6}Folge\s*(\d{1,3})""")

    private val langTags = listOf(
        "de" to listOf("[DE]","[GER]","[DEU]","GERMAN","DEUTSCH","(DE)","(GER)","(DEU)"),
        "en" to listOf("[EN]","[ENG]","ENGLISH","(EN)","(ENG)"),
        "es" to listOf("[ES]","ESP","SPANISH","(ES)"),
        "tr" to listOf("[TR]","TURKISH","(TR)"),
        "ru" to listOf("[RU]","RUS","RUSSIAN","(RU)")
    )

    private fun detectLanguage(text: String): String? {
        val upper = text.uppercase()
        for ((code, markers) in langTags) {
            if (markers.any { upper.contains(it) }) return code
        }
        return null
    }

    fun parse(caption: String?): ParseResult {
        if (caption.isNullOrBlank()) return ParseResult(false, title = null)
        val text = caption.trim()
        val lang = detectLanguage(text)
        // Ranges first
        sxxexxRange.find(text)?.let { m ->
            val s = m.groupValues[1].toIntOrNull()
            val e1 = m.groupValues[2].toIntOrNull()
            val e2 = m.groupValues[3].toIntOrNull()
            val series = text.substring(0, m.range.first).trim(' ', '.', '-', '_', '[', '(', ')', ']').replace(Regex("[._]+"), " ")
            val rest = text.substring(m.range.last + 1).trim(' ', '.', '-', '_', '[', '(', ')', ']')
            return ParseResult(true, series.ifBlank { null }, s, e1, e2, rest.ifBlank { null }, lang)
        }
        nxmRange.find(text)?.let { m ->
            val s = m.groupValues[1].toIntOrNull()
            val e1 = m.groupValues[2].toIntOrNull()
            val e2 = m.groupValues[3].toIntOrNull()
            val series = text.substring(0, m.range.first).trim(' ', '.', '-', '_', '[', '(', ')', ']').replace(Regex("[._]+"), " ")
            val rest = text.substring(m.range.last + 1).trim(' ', '.', '-', '_', '[', '(', ')', ']')
            return ParseResult(true, series.ifBlank { null }, s, e1, e2, rest.ifBlank { null }, lang)
        }
        // Singles
        val m = sxxexx.find(text)
            ?: sXeY.find(text)
            ?: nxm.find(text)
            ?: staffelFolge.find(text)
        if (m != null) {
            val s = m.groupValues[1].toIntOrNull()
            val e = m.groupValues[2].toIntOrNull()
            val series = text.substring(0, m.range.first).trim(' ', '.', '-', '_', '[', '(', ')', ']').replace(Regex("[._]+"), " ")
            val rest = text.substring(m.range.last + 1).trim(' ', '.', '-', '_', '[', '(', ')', ']')
            return ParseResult(true, series.ifBlank { null }, s, e, null, rest.ifBlank { null }, lang)
        }
        return ParseResult(false, title = text, language = lang)
    }
}
