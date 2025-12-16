package com.fishit.player.core.scenenameparser.impl

import com.fishit.player.core.scenenameparser.api.ParsedReleaseName
import com.fishit.player.core.scenenameparser.api.SceneNameInput
import com.fishit.player.core.scenenameparser.api.SceneNameParseResult
import com.fishit.player.core.scenenameparser.api.SceneNameParser
import com.fishit.player.core.scenenameparser.api.SourceHint
import com.fishit.player.core.scenenameparser.api.TmdbType

/**
 * Default implementation of SceneNameParser using Parsikle combinators.
 *
 * **Architecture:**
 * 1. Pre-normalization: Clean source-specific noise
 * 2. Tokenization: Split into meaningful tokens
 * 3. Pattern matching: Use Parsikle to match known patterns
 * 4. TMDB extraction: Extract TMDB IDs/URLs
 * 5. Best guess: Select most likely parse when ambiguous
 */
class DefaultSceneNameParser : SceneNameParser {
    override fun parse(input: SceneNameInput): SceneNameParseResult =
        try {
            // Step 1: Pre-normalize
            val normalized = preNormalize(input.raw, input.sourceHint)

            // Step 2: Extract TMDB info first (if present)
            val tmdbInfo = extractTmdbInfo(input.raw)

            // Step 3: Tokenize
            val tokens = tokenize(normalized)

            // Step 4: Parse patterns
            val parsed = parseTokens(tokens)

            // Step 5: Merge with TMDB info
            val result =
                parsed.copy(
                    tmdbId = tmdbInfo.id ?: parsed.tmdbId,
                    tmdbType = tmdbInfo.type ?: parsed.tmdbType,
                    tmdbUrl = tmdbInfo.url ?: parsed.tmdbUrl,
                )

            SceneNameParseResult.Parsed(result)
        } catch (e: Exception) {
            SceneNameParseResult.Unparsed("Failed to parse: ${e.message}")
        }

    /**
     * Pre-normalize raw string based on source hint.
     */
    private fun preNormalize(
        raw: String,
        sourceHint: SourceHint,
    ): String {
        var normalized = raw.trim()

        // Handle Telegram-specific noise
        if (sourceHint == SourceHint.TELEGRAM) {
            // Remove leading/trailing emojis (rough heuristic)
            normalized = normalized.replace(Regex("^[\\p{So}\\p{Sk}\\s]+"), "")
            normalized = normalized.replace(Regex("[\\p{So}\\p{Sk}\\s]+$"), "")

            // Remove @channel references
            normalized = normalized.replace(Regex("@\\w+"), "")

            // Remove t.me/... and Telegram: ... patterns
            normalized = normalized.replace(Regex("t\\.me/\\S+"), "")
            normalized = normalized.replace(Regex("Telegram:\\s*\\S+"), "")

            // Remove known non-semantic bracket tags (but keep meaningful ones)
            val nonSemanticTags = listOf("TGx", "rarbg", "eztv", "ettv")
            for (tag in nonSemanticTags) {
                normalized = normalized.replace("[$tag]", "", ignoreCase = true)
                normalized = normalized.replace("($tag)", "", ignoreCase = true)
            }
        }

        // Normalize separators
        normalized = normalized.replace('_', '.')
        normalized = normalized.replace(Regex("\\.+"), ".")
        normalized = normalized.replace(Regex("\\s+"), " ")
        normalized = normalized.trim()

        return normalized
    }

    /**
     * Extract TMDB info from raw string.
     */
    private fun extractTmdbInfo(raw: String): TmdbInfo {
        var id: Int? = null
        var type: TmdbType? = null
        var url: String? = null

        // Pattern 1: themoviedb.org/movie/<id>
        val movieUrlPattern = Regex("themoviedb\\.org/movie/(\\d+)")
        movieUrlPattern.find(raw)?.let { match ->
            id = match.groupValues[1].toIntOrNull()
            type = TmdbType.MOVIE
            url = "https://www.themoviedb.org/movie/$id"
        }

        // Pattern 2: themoviedb.org/tv/<id>
        val tvUrlPattern = Regex("themoviedb\\.org/tv/(\\d+)")
        tvUrlPattern.find(raw)?.let { match ->
            id = match.groupValues[1].toIntOrNull()
            type = TmdbType.TV
            url = "https://www.themoviedb.org/tv/$id"
        }

        // Pattern 3: tmdb:<id> or tmdb-<id>
        if (id == null) {
            val tmdbTagPattern = Regex("tmdb[:-](\\d+)", RegexOption.IGNORE_CASE)
            tmdbTagPattern.find(raw)?.let { match ->
                id = match.groupValues[1].toIntOrNull()
                // Type unknown when using tag format
            }
        }

        return TmdbInfo(id, type, url)
    }

    /**
     * Tokenize normalized string.
     */
    private fun tokenize(normalized: String): List<String> {
        // Split on common separators but keep structured tokens intact
        val tokens = mutableListOf<String>()
        val currentToken = StringBuilder()

        var i = 0
        while (i < normalized.length) {
            val char = normalized[i]

            when {
                // Separator characters
                char in listOf('.', ' ', '_', '[', ']', '(', ')', '-') -> {
                    if (currentToken.isNotEmpty()) {
                        tokens.add(currentToken.toString())
                        currentToken.clear()
                    }
                    // Keep '-' if it's likely part of a release group
                    if (char == '-' && i < normalized.length - 1 && normalized[i + 1].isLetter()) {
                        currentToken.append(char)
                    }
                    // Mark where parentheses were (for year detection)
                    if (char == '(' && i > 0) {
                        tokens.add("_PAREN_OPEN_")
                    } else if (char == ')' && i < normalized.length - 1) {
                        tokens.add("_PAREN_CLOSE_")
                    }
                    i++
                }
                else -> {
                    currentToken.append(char)
                    i++
                }
            }
        }

        if (currentToken.isNotEmpty()) {
            tokens.add(currentToken.toString())
        }

        return tokens.filter { it.isNotBlank() }
    }

    /**
     * Parse tokens into structured metadata.
     */
    private fun parseTokens(tokens: List<String>): ParsedReleaseName {
        val builder = ParsedReleaseBuilder()

        // First pass: identify technical tokens, season/episode, TMDB tags, and ALL years
        val technicalIndices = mutableSetOf<Int>()
        var seasonEpisodeIndex: Int? = null
        val yearIndices = mutableListOf<Int>()
        val yearsInParens = mutableSetOf<Int>()

        for (i in tokens.indices) {
            val token = tokens[i]

            // Skip paren markers
            if (token.startsWith("_PAREN_")) {
                technicalIndices.add(i)
                continue
            }

            // TMDB tags should be filtered out
            if (token.matches(Regex("tmdb[:-]\\d+", RegexOption.IGNORE_CASE)) ||
                token.matches(Regex("themoviedb")) ||
                token.matches(Regex("org/movie/\\d+")) ||
                token.matches(Regex("org/tv/\\d+"))
            ) {
                technicalIndices.add(i)
                continue
            }

            // Season/episode has high priority
            val seasonEpisode = tryParseSeasonEpisode(token)
            if (seasonEpisode != null && seasonEpisodeIndex == null) {
                builder.season = seasonEpisode.first
                builder.episode = seasonEpisode.second
                seasonEpisodeIndex = i
                technicalIndices.add(i)
                continue
            }

            // Check for technical metadata
            if (tryParseResolution(token) != null ||
                tryParseSource(token) != null ||
                tryParseVideoCodec(token) != null ||
                tryParseAudioCodec(token) != null ||
                tryParseLanguage(token) != null ||
                token.startsWith("-")
            ) {
                technicalIndices.add(i)
            }

            // Track ALL potential years and whether they're in parentheses
            if (tryParseYear(token) != null) {
                yearIndices.add(i)
                // Check if previous token was _PAREN_OPEN_
                if (i > 0 && tokens[i - 1] == "_PAREN_OPEN_") {
                    yearsInParens.add(i)
                }
            }
        }

        // Select the most likely year for cutoff:
        // 1. Prefer year in parentheses
        // 2. Otherwise, prefer the LAST year (rightmost)
        // 3. Unless there are technical tokens before it
        val selectedYearIndex =
            when {
                yearsInParens.isNotEmpty() -> yearsInParens.first()
                yearIndices.isNotEmpty() -> {
                    // Use the last year found, as earlier ones might be part of title
                    // But only if there are technical tokens after it
                    val lastYear = yearIndices.last()
                    val hasTechnicalAfter = technicalIndices.any { it > lastYear }
                    if (hasTechnicalAfter) lastYear else null
                }
                else -> null
            }

        // Determine title cutoff point
        val cutoffIndex =
            when {
                // If year is in parentheses, it's likely the release year, use it as cutoff
                selectedYearIndex != null && yearsInParens.contains(selectedYearIndex) -> selectedYearIndex - 1
                // Otherwise use season/episode or first technical token
                seasonEpisodeIndex != null -> seasonEpisodeIndex
                selectedYearIndex != null && technicalIndices.isNotEmpty() -> {
                    // If we have a year and technical tokens, use the earlier one
                    minOf(selectedYearIndex, technicalIndices.minOrNull() ?: selectedYearIndex)
                }
                selectedYearIndex != null -> selectedYearIndex
                technicalIndices.isNotEmpty() -> technicalIndices.minOrNull()!!
                else -> tokens.size
            }

        // Build title from tokens before cutoff, skipping technical tokens
        val titleTokens =
            tokens.subList(0, cutoffIndex).filterIndexed { index, token ->
                !technicalIndices.contains(index) && !token.startsWith("_PAREN_")
            }
        builder.title = titleTokens.joinToString(" ").ifBlank { "Unknown" }

        // Second pass: extract metadata from ALL tokens
        // For year, prefer one in parentheses, otherwise use LAST one found (rightmost)
        var lastYearFound: Int? = null
        var yearFromParens: Int? = null

        for (i in 0 until tokens.size) {
            val token = tokens[i]

            if (token.startsWith("_PAREN_")) continue
            if (tryParseSeasonEpisode(token) != null) continue // Already processed

            val year = tryParseYear(token)
            if (year != null) {
                // Track years in parentheses separately
                if (yearsInParens.contains(i)) {
                    yearFromParens = year
                } else {
                    // Always update to keep the rightmost year
                    lastYearFound = year
                }
                continue
            }

            val resolution = tryParseResolution(token)
            if (resolution != null && builder.resolution == null) {
                builder.resolution = resolution
                continue
            }

            val source = tryParseSource(token)
            if (source != null && builder.source == null) {
                builder.source = source
                continue
            }

            val videoCodec = tryParseVideoCodec(token)
            if (videoCodec != null && builder.videoCodec == null) {
                builder.videoCodec = videoCodec
                continue
            }

            val audioCodec = tryParseAudioCodec(token)
            if (audioCodec != null && builder.audioCodec == null) {
                builder.audioCodec = audioCodec
                continue
            }

            val language = tryParseLanguage(token)
            if (language != null && builder.language == null) {
                builder.language = language
                continue
            }

            if (token.startsWith("-") && token.length > 1) {
                builder.releaseGroup = token.substring(1)
            }
        }

        // Assign year: prefer parentheses, otherwise use last found
        builder.year = yearFromParens ?: lastYearFound

        return builder.build()
    }

    private fun tryParseSeasonEpisode(token: String): Pair<Int, Int>? {
        // Pattern: S01E02 or S1E2
        val pattern1 = Regex("S(\\d{1,2})E(\\d{1,3})", RegexOption.IGNORE_CASE)
        pattern1.find(token)?.let { match ->
            val season = match.groupValues[1].toIntOrNull()
            val episode = match.groupValues[2].toIntOrNull()
            if (season != null && episode != null) {
                return Pair(season, episode)
            }
        }

        // Pattern: 1x02
        val pattern2 = Regex("(\\d{1,2})x(\\d{1,3})", RegexOption.IGNORE_CASE)
        pattern2.find(token)?.let { match ->
            val season = match.groupValues[1].toIntOrNull()
            val episode = match.groupValues[2].toIntOrNull()
            if (season != null && episode != null) {
                return Pair(season, episode)
            }
        }

        return null
    }

    private fun tryParseYear(token: String): Int? {
        val yearPattern = Regex("(19\\d{2}|20\\d{2})")
        return yearPattern.find(token)?.value?.toIntOrNull()
    }

    private fun tryParseResolution(token: String): String? {
        val resolutions = listOf("2160p", "1080p", "720p", "480p", "360p")
        return resolutions.firstOrNull { token.contains(it, ignoreCase = true) }
    }

    private fun tryParseSource(token: String): String? {
        val sources =
            listOf(
                "WEB-DL",
                "WEBRip",
                "BluRay",
                "BRRip",
                "HDTV",
                "DVDRip",
                "REMUX",
                "AMZN",
                "NF",
                "DSNP",
                "ATVP",
            )
        return sources.firstOrNull { token.contains(it, ignoreCase = true) }?.let {
            // Return the actual matched token to preserve case
            sources.first { s -> token.contains(s, ignoreCase = true) }
        }
    }

    private fun tryParseVideoCodec(token: String): String? {
        val codecs = listOf("x264", "x265", "H.264", "H.265", "HEVC", "AVC")
        return codecs.firstOrNull { token.contains(it, ignoreCase = true) }
    }

    private fun tryParseAudioCodec(token: String): String? {
        val codecs =
            listOf(
                "AAC",
                "AC3",
                "DTS",
                "DDP5.1",
                "DDP",
                "TrueHD",
                "Atmos",
                "DTS-HD",
                "DTS-HD.MA",
            )
        return codecs.firstOrNull { token.contains(it, ignoreCase = true) }
    }

    private fun tryParseLanguage(token: String): String? {
        val languages = listOf("GERMAN", "ENGLISH", "MULTI", "DL", "DUAL", "KOREAN")
        return languages.firstOrNull { token.equals(it, ignoreCase = true) }
    }

    /**
     * TMDB information extracted from raw string.
     */
    private data class TmdbInfo(
        val id: Int?,
        val type: TmdbType?,
        val url: String?,
    )

    /**
     * Builder for ParsedReleaseName.
     */
    private class ParsedReleaseBuilder {
        var title: String = ""
        var year: Int? = null
        var season: Int? = null
        var episode: Int? = null
        var episodeTitle: String? = null
        var resolution: String? = null
        var source: String? = null
        var videoCodec: String? = null
        var audioCodec: String? = null
        var language: String? = null
        var releaseGroup: String? = null
        var tmdbId: Int? = null
        var tmdbType: TmdbType? = null
        var tmdbUrl: String? = null

        fun build() =
            ParsedReleaseName(
                title = title,
                year = year,
                season = season,
                episode = episode,
                episodeTitle = episodeTitle,
                resolution = resolution,
                source = source,
                videoCodec = videoCodec,
                audioCodec = audioCodec,
                language = language,
                releaseGroup = releaseGroup,
                tmdbId = tmdbId,
                tmdbType = tmdbType,
                tmdbUrl = tmdbUrl,
            )
    }
}
