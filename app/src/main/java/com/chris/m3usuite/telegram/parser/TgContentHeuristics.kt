package com.chris.m3usuite.telegram.parser

import com.chris.m3usuite.telegram.models.MediaInfo
import com.chris.m3usuite.telegram.models.MediaKind

/**
 * Advanced heuristics for Telegram content classification and metadata extraction.
 * Provides intelligent classification beyond basic parsing to improve content detection.
 *
 * Key responsibilities:
 * - Refine classification from MediaParser (Movie vs Series vs Episode)
 * - Extract season/episode information from various patterns
 * - Detect content types based on chat context
 * - Score content confidence for filtering
 */
object TgContentHeuristics {
    // Season/Episode patterns (broader than MediaParser)
    private val seasonEpisodePatterns =
        listOf(
            Regex("""[Ss](\d{1,2})[Ee](\d{1,3})"""), // S01E02, s1e2
            Regex("""[Ss]eason\s*(\d{1,2})\s*[Ee]pisode\s*(\d{1,3})""", RegexOption.IGNORE_CASE), // Season 1 Episode 2
            Regex("""(\d{1,2})x(\d{1,3})"""), // 1x02
            Regex("""[Ee]pisode\s*(\d{1,3})""", RegexOption.IGNORE_CASE), // Episode 4 (no season)
            Regex("""[Ee]p\s*(\d{1,3})""", RegexOption.IGNORE_CASE), // Ep 4
            Regex("""[Ff]olge\s*(\d{1,3})""", RegexOption.IGNORE_CASE), // Folge 4 (German)
            Regex("""[Ss]taffel\s*(\d{1,2})""", RegexOption.IGNORE_CASE), // Staffel 2 (German)
        )

    // Language tags for better content classification
    private val languageTags =
        listOf(
            Regex("""\b(GERMAN|DEUTSCH|DE)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(ENGLISH|ENG|EN)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(MULTI|DUAL)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(SUBBED|SUB)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(DUBBED|DUB)\b""", RegexOption.IGNORE_CASE),
        )

    // Quality indicators
    private val qualityTags =
        listOf(
            Regex("""\b(4K|UHD|2160p)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(1080p|FHD)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(720p|HD)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(480p|SD)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(BLURAY|BRRIP|BDRIP)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(WEBRIP|WEBDL|WEB-DL)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(HDTV|PDTV)\b""", RegexOption.IGNORE_CASE),
        )

    // Series indicators in chat titles
    private val seriesIndicators =
        listOf(
            Regex("""\b(series|serien|show|staffel|season)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(episodes?|folgen?)\b""", RegexOption.IGNORE_CASE),
        )

    // Movie indicators
    private val movieIndicators =
        listOf(
            Regex("""\b(film|movie|kino|cinema)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(vod|video)\b""", RegexOption.IGNORE_CASE),
        )

    /**
     * Result of heuristic classification with confidence score.
     */
    data class HeuristicResult(
        val suggestedKind: MediaKind,
        val confidence: Double, // 0.0 to 1.0
        val seasonNumber: Int? = null,
        val episodeNumber: Int? = null,
        val detectedLanguages: List<String> = emptyList(),
        val detectedQuality: String? = null,
        val reasoning: String = "",
    )

    /**
     * Season/Episode extraction result.
     */
    data class SeasonEpisode(
        val season: Int?,
        val episode: Int?,
        val pattern: String, // Which pattern matched
    )

    /**
     * Classify content based on parsed info and chat context.
     * Provides a confidence score to help filter ambiguous content.
     *
     * @param parsed MediaInfo from MediaParser
     * @param chatTitle Title of the chat where content was found
     * @return HeuristicResult with suggested classification and confidence
     */
    fun classify(
        parsed: MediaInfo,
        chatTitle: String?,
    ): HeuristicResult {
        val allText =
            buildString {
                append(chatTitle.orEmpty())
                append(" ")
                append(parsed.fileName.orEmpty())
                append(" ")
                append(parsed.title.orEmpty())
            }

        // Extract season/episode from all available text
        val seasonEp = guessSeasonEpisode(allText)

        // Detect languages
        val languages = detectLanguages(allText)

        // Detect quality
        val quality = detectQuality(allText)

        // Check chat context for series/movie hints
        val chatHintsSeries = chatTitle?.let { hasSeriesIndicators(it) } ?: false
        val chatHintsMovie = chatTitle?.let { hasMovieIndicators(it) } ?: false

        // Build classification logic
        var suggestedKind = parsed.kind
        var confidence = 0.5 // Base confidence
        val reasons = mutableListOf<String>()

        // Strong episode indicators
        if (seasonEp != null && (seasonEp.season != null || seasonEp.episode != null)) {
            suggestedKind = MediaKind.EPISODE
            confidence += 0.3
            reasons.add("Detected S${seasonEp.season ?: "?"}E${seasonEp.episode ?: "?"} pattern")
        }

        // Series metadata from parser
        if (parsed.totalEpisodes != null || parsed.totalSeasons != null) {
            suggestedKind = MediaKind.SERIES
            confidence += 0.2
            reasons.add("Metadata indicates series (${parsed.totalEpisodes} eps)")
        }

        // Existing episode classification from parser
        if (parsed.seasonNumber != null || parsed.episodeNumber != null) {
            if (suggestedKind != MediaKind.EPISODE) {
                suggestedKind = MediaKind.EPISODE
                confidence += 0.25
                reasons.add("Parser detected episode markers")
            }
        }

        // Chat context hints
        if (chatHintsSeries) {
            if (suggestedKind == MediaKind.MOVIE) {
                suggestedKind = MediaKind.EPISODE
                confidence += 0.15
                reasons.add("Chat context suggests series")
            } else if (suggestedKind == MediaKind.EPISODE) {
                confidence += 0.1
                reasons.add("Chat context confirms series")
            }
        }

        if (chatHintsMovie && suggestedKind == MediaKind.MOVIE) {
            confidence += 0.1
            reasons.add("Chat context confirms movie")
        }

        // Quality/Language tags add confidence
        if (languages.isNotEmpty()) {
            confidence += 0.05
            reasons.add("Language tags: ${languages.joinToString()}")
        }

        if (quality != null) {
            confidence += 0.05
            reasons.add("Quality: $quality")
        }

        // Cap confidence at 1.0
        confidence = confidence.coerceAtMost(1.0)

        return HeuristicResult(
            suggestedKind = suggestedKind,
            confidence = confidence,
            seasonNumber = seasonEp?.season ?: parsed.seasonNumber,
            episodeNumber = seasonEp?.episode ?: parsed.episodeNumber,
            detectedLanguages = languages,
            detectedQuality = quality,
            reasoning = reasons.joinToString("; "),
        )
    }

    /**
     * Extract season and episode numbers from text using various patterns.
     *
     * @param text Text to analyze (filename, caption, chat title, etc.)
     * @return SeasonEpisode if pattern matched, null otherwise
     */
    fun guessSeasonEpisode(text: String): SeasonEpisode? {
        for (pattern in seasonEpisodePatterns) {
            val match = pattern.find(text) ?: continue

            return when (pattern.pattern) {
                // S01E02, s1e2
                """[Ss](\d{1,2})[Ee](\d{1,3})""" -> {
                    SeasonEpisode(
                        season = match.groupValues.getOrNull(1)?.toIntOrNull(),
                        episode = match.groupValues.getOrNull(2)?.toIntOrNull(),
                        pattern = "SxxEyy",
                    )
                }
                // Season 1 Episode 2
                """[Ss]eason\s*(\d{1,2})\s*[Ee]pisode\s*(\d{1,3})""" -> {
                    SeasonEpisode(
                        season = match.groupValues.getOrNull(1)?.toIntOrNull(),
                        episode = match.groupValues.getOrNull(2)?.toIntOrNull(),
                        pattern = "Season X Episode Y",
                    )
                }
                // 1x02
                """(\d{1,2})x(\d{1,3})""" -> {
                    SeasonEpisode(
                        season = match.groupValues.getOrNull(1)?.toIntOrNull(),
                        episode = match.groupValues.getOrNull(2)?.toIntOrNull(),
                        pattern = "XxY",
                    )
                }
                // Episode 4 (no season)
                """[Ee]pisode\s*(\d{1,3})""" -> {
                    SeasonEpisode(
                        season = null,
                        episode = match.groupValues.getOrNull(1)?.toIntOrNull(),
                        pattern = "Episode X",
                    )
                }
                // Ep 4
                """[Ee]p\s*(\d{1,3})""" -> {
                    SeasonEpisode(
                        season = null,
                        episode = match.groupValues.getOrNull(1)?.toIntOrNull(),
                        pattern = "Ep X",
                    )
                }
                // Folge 4 (German)
                """[Ff]olge\s*(\d{1,3})""" -> {
                    SeasonEpisode(
                        season = null,
                        episode = match.groupValues.getOrNull(1)?.toIntOrNull(),
                        pattern = "Folge X",
                    )
                }
                // Staffel 2 (German season)
                """[Ss]taffel\s*(\d{1,2})""" -> {
                    SeasonEpisode(
                        season = match.groupValues.getOrNull(1)?.toIntOrNull(),
                        episode = null,
                        pattern = "Staffel X",
                    )
                }
                else -> continue
            }
        }

        return null
    }

    /**
     * Detect language tags in text.
     */
    fun detectLanguages(text: String): List<String> {
        val detected = mutableListOf<String>()

        for (pattern in languageTags) {
            val match = pattern.find(text)
            if (match != null) {
                detected.add(match.value.uppercase())
            }
        }

        return detected.distinct()
    }

    /**
     * Detect quality indicators in text.
     */
    fun detectQuality(text: String): String? {
        for (pattern in qualityTags) {
            val match = pattern.find(text)
            if (match != null) {
                return match.value.uppercase()
            }
        }
        return null
    }

    /**
     * Check if chat title contains series indicators.
     */
    fun hasSeriesIndicators(chatTitle: String): Boolean = seriesIndicators.any { it.containsMatchIn(chatTitle) }

    /**
     * Check if chat title contains movie indicators.
     */
    fun hasMovieIndicators(chatTitle: String): Boolean = movieIndicators.any { it.containsMatchIn(chatTitle) }

    /**
     * Score content quality based on multiple factors.
     * Higher score = better quality/more complete metadata.
     *
     * @return Score from 0.0 to 1.0
     */
    fun scoreContent(parsed: MediaInfo): Double {
        var score = 0.0

        // Has title
        if (!parsed.title.isNullOrBlank()) score += 0.2

        // Has year
        if (parsed.year != null) score += 0.15

        // Has duration
        if (parsed.durationMinutes != null && parsed.durationMinutes > 0) score += 0.1

        // Has genres
        if (parsed.genres.isNotEmpty()) score += 0.1

        // Has rating
        if (parsed.tmdbRating != null) score += 0.15

        // Has file metadata
        if (parsed.sizeBytes != null && parsed.sizeBytes > 0) score += 0.1

        // Series-specific
        if (parsed.kind == MediaKind.SERIES) {
            if (parsed.totalEpisodes != null) score += 0.1
            if (parsed.totalSeasons != null) score += 0.1
        }

        // Episode-specific
        if (parsed.kind == MediaKind.EPISODE) {
            if (parsed.seasonNumber != null) score += 0.05
            if (parsed.episodeNumber != null) score += 0.05
        }

        return score.coerceIn(0.0, 1.0)
    }
}
