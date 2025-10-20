package com.chris.m3usuite.telegram

/** Simple parsing utilities for Telegram captions to distinguish VOD vs Series episodes.
 * Rules:
 * - Series pattern: SxxEyy, SxEy, or NxM (1x02) anywhere in the text.
 * - Series title: prefix before the pattern, trimmed of separators and brackets.
 * - Episode title: remainder after the pattern, with common separators removed.
 */
object TelegramHeuristics {
    private data class Normalized(
        val title: String,
        val year: Int?,
        val language: String?
    )

    private val qualities = Regex("(?i)\\b(720p|1080p|1440p|2160p|4k|8k|x264|x265|hevc|h265|webrip|web[- ]?dl|bluray|bdrip|remux|hdr|dolby|dv|ddp?\\d\\.\\d|atmos|aac|dts|cam|hdtc|bdremux|truehd|xvid)\\b")
    private val yearRegex = Regex("(?i)\\b(19|20)\\d{2}\\b")
    private val langRegex = Regex("(?i)\\b(GER/ENG|DE/EN|DEU|GER|DE|GERMAN|DEUTSCH|EN|ENG|ENGLISH|ITA|IT|ES|ESP|ESPAÑOL|SPANISH|VOSTFR|FR|FRENCH)\\b")
    private val codecNoise = Regex("(?i)\\b(h264|h265|hevc|avc|aac|dd|mp3|flac|opus)\\b")
    private val bracketNoise = Regex("[\u005B\u005D\u0028\u0029]")
    private val separatorNoise = Regex("[._]+")
    data class ParseResult(
        val isSeries: Boolean,
        val seriesTitle: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val episodeEnd: Int? = null, // for ranges E01-03
        val title: String? = null,
        val language: String? = null, // ISO-ish (de, en, es...)
        val year: Int? = null,
    )

    // Erweiterte Muster:
    private val sxxexx = Regex("""\\bS(\\d{1,2})E(\\d{1,3})(?:\\s*[-–]\\s*(\\d{1,3}))?\\b""", RegexOption.IGNORE_CASE)
    private val sXeY = Regex("""\\bS(\\d{1,2})\\s*[:x]\\s*E?(\\d{1,3})(?:\\s*[-–]\\s*(\\d{1,3}))?\\b""", RegexOption.IGNORE_CASE)
    private val nxm = Regex("""\\b(\\d{1,2})[x×](\\d{1,3})(?:\\s*[-–]\\s*(\\d{1,3}))?\\b""", RegexOption.IGNORE_CASE)
    private val staffelFolge = Regex("""\\b(?:Staffel|Season)\\s*(\\d{1,2})\\s*(?:Folge|Ep\\.?|Episode)\\s*(\\d{1,3})(?:\\s*[-–]\\s*(\\d{1,3}))?\\b""", RegexOption.IGNORE_CASE)
    private val episodeOnly = Regex("""\\b(?:Ep\\.?|Episode)\\s*(\\d{1,3})\\b""", RegexOption.IGNORE_CASE)
    private val sColonE = Regex("""\\bS(\\d{1,2})\\s*:\\s*E?(\\d{1,3})\\b""", RegexOption.IGNORE_CASE)

    private fun detectLanguage(text: String): String? {
        val upper = text.uppercase()
        return when {
            upper.contains("GER/ENG") || upper.contains("DE/EN") -> "de+en"
            upper.contains("GERMAN") || upper.contains("DEUTSCH") || upper.contains("DEU") || upper.contains(" GER ") || upper.contains(" DE ") -> "de"
            upper.contains("ENGLISH") || upper.contains("ENG") || upper.contains(" EN ") -> "en"
            upper.contains("VOSTFR") || upper.contains("FR ") || upper.contains("FRENCH") -> "fr"
            upper.contains("ITA") || upper.contains(" IT ") -> "it"
            upper.contains("ESPAÑOL") || upper.contains("SPANISH") || upper.contains("ESP") || upper.contains(" ES ") -> "es"
            else -> langRegex.find(upper)?.value?.lowercase()?.let {
                when (it) {
                    "ger", "de", "deu", "german", "deutsch" -> "de"
                    "en", "eng", "english" -> "en"
                    "ita", "it" -> "it"
                    "es", "esp", "español", "spanish" -> "es"
                    "vostfr", "fr", "french" -> "fr"
                    else -> null
                }
            }
        }
    }

    private fun normalize(raw: String): Normalized {
        val lang = detectLanguage(raw)
        val year = yearRegex.find(raw)?.value?.toIntOrNull()
        var working = raw
        working = bracketNoise.replace(working, " ")
        working = qualities.replace(working, " ")
        working = codecNoise.replace(working, " ")
        working = langRegex.replace(working, " ")
        working = yearRegex.replace(working, " ")
        working = separatorNoise.replace(working, " ")
        working = working.replace(Regex("[-–:]+"), " ")
        working = working.replace(Regex("\\s+"), " ")
        working = working.trim(' ', '-', '_', '.', '[', ']', '(', ')')
        return Normalized(working, year, lang)
    }

    fun parse(caption: String?): ParseResult {
        if (caption.isNullOrBlank()) return ParseResult(false, title = null)
        val text = caption.trim()
        val normalized = normalize(text)

        val match = sxxexx.find(text)
            ?: sXeY.find(text)
            ?: sColonE.find(text)
            ?: nxm.find(text)
            ?: staffelFolge.find(text)

        if (match != null) {
            val season = match.groupValues.getOrNull(1)?.toIntOrNull()
            val episode = match.groupValues.getOrNull(2)?.toIntOrNull()
            val episodeEnd = match.groupValues.getOrNull(3)?.toIntOrNull()
            val seriesRaw = text.substring(0, match.range.first)
            val episodeRaw = text.substring(match.range.last + 1)
            val seriesNorm = normalize(seriesRaw).title.ifBlank { null }
            val episodeNorm = normalize(episodeRaw).title.ifBlank { null }
            return ParseResult(
                isSeries = true,
                seriesTitle = seriesNorm,
                season = season,
                episode = episode,
                episodeEnd = episodeEnd,
                title = episodeNorm,
                language = normalized.language,
                year = normalized.year
            )
        }

        episodeOnly.find(text)?.let { only ->
            val episode = only.groupValues.getOrNull(1)?.toIntOrNull()
            val seriesRaw = text.substring(0, only.range.first)
            val episodeRaw = text.substring(only.range.last + 1)
            val seriesNorm = normalize(seriesRaw).title.ifBlank { null }
            val episodeNorm = normalize(episodeRaw).title.ifBlank { null }
            return ParseResult(
                isSeries = true,
                seriesTitle = seriesNorm,
                season = null,
                episode = episode,
                episodeEnd = null,
                title = episodeNorm,
                language = normalized.language,
                year = normalized.year
            )
        }

        return ParseResult(
            isSeries = false,
            title = normalized.title.ifBlank { null },
            language = normalized.language,
            year = normalized.year
        )
    }

    fun cleanMovieTitle(raw: String): String = normalize(raw).title
}
