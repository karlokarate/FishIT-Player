package com.chris.m3usuite.telegram

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/** Simple parsing utilities for Telegram captions to distinguish VOD vs Series episodes. */
object TelegramHeuristics {
    private const val TAG = "TelegramHeuristics"
    private val NO_MATCH_REGEX = Regex("(?!.)")

    private fun safeRegex(pattern: String, vararg options: RegexOption): Regex {
        val optionSet = if (options.isEmpty()) emptySet() else options.toSet()
        return runCatching { Regex(pattern, optionSet) }
            .getOrElse { err ->
                Log.e(TAG, "Invalid regex pattern: $pattern", err)
                NO_MATCH_REGEX
            }
    }

    private val qualityTokens = safeRegex(
        """\b(?:720p|1080p|1440p|2160p|4k|8k|x264|x265|hevc|webrip|web[- ]?dl|bluray|blu[- ]?ray|bdremux|remux|hdr|dolby(?: vision)?|dv|ddp?\d\.\d|atmos|aac|dts|cam|hdtc|truehd)\b""",
        RegexOption.IGNORE_CASE
    )
    private val bracketLanguageRegex = safeRegex(
        """[\(\[\{]\s*(?:deu|ger|de|eng|en|ita|es|vostfr|fr|vo|subfrench)(?:\s*/\s*(?:deu|ger|de|eng|en|ita|es|vostfr|fr|vo|subfrench))*\s*[\)\]\}]""",
        RegexOption.IGNORE_CASE
    )
    private val languagePairRegex = safeRegex(
        """\b(?:deu|ger|de)\s*/\s*(?:eng|en|english)\b""",
        RegexOption.IGNORE_CASE
    )
    private val languageTokenRegex = safeRegex(
        """\b(DEU|GER|DE|GERMAN|DEUTSCH|ENG|EN|ENGLISH|ITA|ES|VOSTFR|FR|VO|SUBFRENCH)\b""",
        RegexOption.IGNORE_CASE
    )
    private val cleanupSeparatorsRegex = Regex("[._]+")
    private val punctuationRegex = Regex("[\\[\\]\\(\\)\\{\\}]")
    private val whitespaceRegex = Regex("\\s+")
    private val yearRegex = safeRegex("""\b(19\d{2}|20\d{2})\b""", RegexOption.IGNORE_CASE)
    private val sxxexxRegex = safeRegex(
        """\bS(?<season>\d{1,2})E(?<episode>\d{1,3})(?:\s*[-–]\s*(?<episodeEnd>\d{1,3}))?\b""",
        RegexOption.IGNORE_CASE
    )
    private val nxmRegex = safeRegex(
        """\b(?<season>\d{1,2})[x×](?<episode>\d{1,3})(?:\s*[-–]\s*(?<episodeEnd>\d{1,3}))?\b""",
        RegexOption.IGNORE_CASE
    )
    private val colonRegex = safeRegex(
        """\bS(?<season>\d{1,2})\s*:\s*E?(?<episode>\d{1,3})(?:\s*[-–]\s*(?<episodeEnd>\d{1,3}))?\b""",
        RegexOption.IGNORE_CASE
    )
    private val labeledSeasonEpisodeRegex = safeRegex(
        """\b(?:Staffel|Season)\s*(?<season>\d{1,2})\s*(?:Folge|Ep\.?|Episode)\s*(?<episode>\d{1,3})(?:\s*[-–]\s*(?<episodeEnd>\d{1,3}))?\b""",
        RegexOption.IGNORE_CASE
    )
    private val episodeOnlyRegex = safeRegex(
        """\b(?:Ep\.?|Episode)\s*(?<episode>\d{1,3})\b""",
        RegexOption.IGNORE_CASE
    )
    private val seriesTrimChars = charArrayOf(' ', '.', '-', '_', '[', ']', '(', ')', ':', '–')

    data class ParseResult(
        val isSeries: Boolean,
        val seriesTitle: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val episodeEnd: Int? = null,
        val title: String? = null,
        val language: String? = null,
        val year: Int? = null,
    )

    private data class NormalizedCaption(
        val cleaned: String,
        val language: String?,
        val year: Int?
    )

    private fun detectLanguage(text: String): String? {
        if (languagePairRegex.containsMatchIn(text)) return "de+en"
        val match = languageTokenRegex.find(text)?.groupValues?.getOrNull(1)?.uppercase() ?: return null
        return when (match) {
            "DEU", "GER", "DE", "GERMAN", "DEUTSCH" -> "de"
            "ENG", "EN", "ENGLISH" -> "en"
            "ITA" -> "it"
            "ES" -> "es"
            "VOSTFR", "FR", "SUBFRENCH" -> "fr"
            "VO" -> "vo"
            else -> null
        }
    }

    private fun normalize(raw: String): NormalizedCaption {
        val language = detectLanguage(raw)
        var working = raw
        val year = yearRegex.find(working)?.groupValues?.getOrNull(1)?.toIntOrNull()
        working = qualityTokens.replace(working, " ")
        working = bracketLanguageRegex.replace(working, " ")
        working = languagePairRegex.replace(working, " ")
        working = languageTokenRegex.replace(working, " ")
        working = yearRegex.replace(working, " ")
        working = punctuationRegex.replace(working, " ")
        working = cleanupSeparatorsRegex.replace(working, " ")
        val collapsed = whitespaceRegex.replace(working, " ").trim(*seriesTrimChars)
        return NormalizedCaption(collapsed, language, year)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun parse(caption: String?): ParseResult {
        if (caption.isNullOrBlank()) return ParseResult(false, title = null)
        val raw = caption.trim()
        val normalized = normalize(raw)
        val text = normalized.cleaned
        val lang = normalized.language
        val year = normalized.year ?: extractYear(raw)

        val episodeMatch = sequenceOf(
            sxxexxRegex.find(text),
            nxmRegex.find(text),
            colonRegex.find(text),
            labeledSeasonEpisodeRegex.find(text)
        ).firstOrNull { it != null }

        if (episodeMatch != null) {
            val seasonValue = episodeMatch.groups["season"]?.value?.toIntOrNull()
            val episodeValue = episodeMatch.groups["episode"]?.value?.toIntOrNull()
            val episodeEndValue = episodeMatch.groups["episodeEnd"]?.value?.toIntOrNull()
            val prefix = text.substring(0, episodeMatch.range.first)
            val suffix = text.substring(episodeMatch.range.last + 1)
            val seriesTitle = cleanSegment(prefix).first
            val episodeTitle = cleanSegment(suffix).first
            return ParseResult(
                isSeries = true,
                seriesTitle = seriesTitle,
                season = seasonValue,
                episode = episodeValue,
                episodeEnd = episodeEndValue,
                title = episodeTitle,
                language = lang,
                year = year
            )
        }

        val episodeOnlyMatch = episodeOnlyRegex.find(text)
        if (episodeOnlyMatch != null) {
            val episode = episodeOnlyMatch.groups["episode"]?.value?.toIntOrNull()
            val prefix = text.substring(0, episodeOnlyMatch.range.first)
            val suffix = text.substring(episodeOnlyMatch.range.last + 1)
            val seriesTitle = cleanSegment(prefix).first
            val episodeTitle = cleanSegment(suffix).first
            return ParseResult(
                isSeries = true,
                seriesTitle = seriesTitle,
                season = null,
                episode = episode,
                episodeEnd = null,
                title = episodeTitle,
                language = lang,
                year = year
            )
        }

        val movieTitle = normalized.cleaned
        return ParseResult(
            isSeries = false,
            title = movieTitle.ifBlank { raw },
            language = lang,
            year = year
        )
    }

    fun fallbackParse(caption: String?): ParseResult {
        if (caption.isNullOrBlank()) return ParseResult(isSeries = false)
        val normalized = normalize(caption.trim())
        val title = normalized.cleaned
        return ParseResult(
            isSeries = false,
            title = title.ifBlank { caption.trim() },
            language = normalized.language,
            year = normalized.year ?: extractYear(caption)
        )
    }

    fun cleanMovieTitle(raw: String): String = normalize(raw).cleaned

    private fun cleanSegment(segment: String): Pair<String?, Int?> {
        val normalized = normalize(segment)
        val title = normalized.cleaned.trim(*seriesTrimChars)
        return title.ifBlank { null } to normalized.year
    }

    private fun extractYear(text: String): Int? =
        yearRegex.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
}
