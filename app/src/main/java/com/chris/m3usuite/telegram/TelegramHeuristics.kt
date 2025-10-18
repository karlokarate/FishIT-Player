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

    // Erweiterte Muster:
    private val sxxexx = Regex("""\\bS(\\d{1,2})E(\\d{1,3})(?:\\s*[-–]\\s*(\\d{1,3}))?\\b""", RegexOption.IGNORE_CASE)
    private val sXeY   = Regex("""\\bS(\\d{1,2})\\s*[:x]\\s*E?(\\d{1,3})(?:\\s*[-–]\\s*(\\d{1,3}))?\\b""", RegexOption.IGNORE_CASE)
    private val nxm    = Regex("""\\b(\\d{1,2})x(\\d{1,3})(?:\\s*[-–]\\s*(\\d{1,3}))?\\b""", RegexOption.IGNORE_CASE)
    private val staffelFolge = Regex("""\\b(?:Staffel|Season)\\s*(\\d{1,2})\\s*(?:Folge|Ep\\.?|Episode)\\s*(\\d{1,3})(?:\\s*[-–]\\s*(\\d{1,3}))?\\b""", RegexOption.IGNORE_CASE)
    private val episodeOnly = Regex("""\\b(?:Ep\\.?|Episode)\\s*(\\d{1,3})\\b""", RegexOption.IGNORE_CASE)
    private val sColonE = Regex("""\\bS(\\d{1,2})\\s*[:]\\s*E?(\\d{1,3})\\b""", RegexOption.IGNORE_CASE)
    // Sprache/Tags: [GER], [DEU], GER/ENG, DE/EN, German/Deutsch
    private val langTag = Regex("""\\b(?:(DEU|GER|DE|GERMAN|DEUTSCH|EN|ENG|ENGLISH|VOSTFR|ITA|ES))\\b""", RegexOption.IGNORE_CASE)

    private fun detectLanguage(text: String): String? {
        val upper = text.uppercase()
        if (upper.contains("GER/ENG") || upper.contains("DE/EN")) return "de+en"
        val match = langTag.find(upper)?.groupValues?.getOrNull(1)?.uppercase() ?: return null
        return when (match) {
            "DEU", "GER", "DE", "GERMAN", "DEUTSCH" -> "de"
            "EN", "ENG", "ENGLISH" -> "en"
            "VOSTFR" -> "fr"
            "ITA" -> "it"
            "ES" -> "es"
            else -> null
        }
    }

    fun parse(caption: String?): ParseResult {
        if (caption.isNullOrBlank()) return ParseResult(false, title = null)
        val text = caption.trim()
        val lang = detectLanguage(text)
        // Ranges/Single Matches (erweitert)
        val m = sxxexx.find(text)
            ?: sXeY.find(text)
            ?: sColonE.find(text)
            ?: nxm.find(text)
            ?: staffelFolge.find(text)
        if (m != null) {
            val s = m.groupValues[1].toIntOrNull()
            val e1 = m.groupValues.getOrNull(2)?.toIntOrNull()
            val e2 = m.groupValues.getOrNull(3)?.toIntOrNull()
            val series = text.substring(0, m.range.first).trim(' ', '.', '-', '_', '[', '(', ')', ']').replace(Regex("[._]+"), " ")
            val rest = text.substring(m.range.last + 1).trim(' ', '.', '-', '_', '[', '(', ')', ']')
            return ParseResult(true, series.ifBlank { null }, s, e1, e2, rest.ifBlank { null }, lang)
        }
        // Fallback: nur Episoden-Zahl?
        episodeOnly.find(text)?.let { em ->
            val e = em.groupValues[1].toIntOrNull()
            val base = text.substring(0, em.range.first).trim(' ', '.', '-', '_', '[', '(', ')', ']')
            val rest = text.substring(em.range.last + 1).trim(' ', '.', '-', '_', '[', '(', ')', ']')
            return ParseResult(true, seriesTitle = base.ifBlank { null }, season = null, episode = e, episodeEnd = null, title = rest.ifBlank { null }, language = lang)
        }
        return ParseResult(false, title = text, language = lang)
    }
}
