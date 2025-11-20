package com.chris.m3usuite.telegram.parser

import com.chris.m3usuite.telegram.models.*

/**
 * Heuristic classification and metadata enrichment for Telegram content.
 * Provides additional intelligence beyond basic MediaParser pattern matching.
 *
 * Key responsibilities:
 * - Classify content based on chat title, filename, caption
 * - Extract season/episode information from various formats
 * - Detect adult content
 * - Score content quality/relevance
 * - Detect sub-chat references
 */
object TgContentHeuristics {
    
    /**
     * Result of heuristic classification.
     */
    data class HeuristicResult(
        val kind: MediaKind,
        val confidence: Float, // 0.0 to 1.0
        val seasonEpisode: SeasonEpisode? = null,
        val isAdult: Boolean = false,
        val tags: List<String> = emptyList()
    )
    
    /**
     * Season and episode information.
     */
    data class SeasonEpisode(
        val season: Int,
        val episode: Int
    )
    
    // Patterns for various season/episode formats
    private val seasonEpisodePatterns = listOf(
        Regex("""[Ss](\d{1,2})[Ee](\d{1,2})"""),                    // S01E02
        Regex("""Season\s*(\d{1,2})\s*Episode\s*(\d{1,2})""", RegexOption.IGNORE_CASE),  // Season 1 Episode 2
        Regex("""Staffel\s*(\d{1,2})\s*Folge\s*(\d{1,2})""", RegexOption.IGNORE_CASE),   // Staffel 1 Folge 2
        Regex("""Episode\s*(\d{1,2})""", RegexOption.IGNORE_CASE),  // Episode 4 (assume season 1)
        Regex("""Folge\s*(\d{1,2})""", RegexOption.IGNORE_CASE),    // Folge 4 (assume season 1)
        Regex("""(\d{1,2})x(\d{1,2})""")                             // 1x02
    )
    
    // Adult content indicators
    private val adultKeywords = setOf(
        "porn", "sex", "xxx", "18+", "adult", "nsfw", "creampie", 
        "anal", "bdsm", "hentai", "erotic", "🔞"
    )
    
    // Movie indicators in chat title
    private val movieIndicators = setOf(
        "film", "movie", "kino", "cinema", "filme"
    )
    
    // Series indicators in chat title
    private val seriesIndicators = setOf(
        "serie", "series", "show", "tv", "serien", "season", "staffel"
    )
    
    /**
     * Classify content using heuristics based on parsed item, chat title, and filename.
     */
    fun classify(
        parsed: ParsedItem,
        chatTitle: String,
        fileName: String? = null
    ): HeuristicResult {
        if (parsed !is ParsedItem.Media) {
            return HeuristicResult(
                kind = MediaKind.OTHER,
                confidence = 0.0f
            )
        }
        
        val media = parsed.info
        val text = buildString {
            append(chatTitle)
            append(" ")
            append(fileName ?: "")
            append(" ")
            append(media.title ?: "")
            append(" ")
            append(media.originalTitle ?: "")
        }.lowercase()
        
        // Check for adult content
        val isAdult = adultKeywords.any { text.contains(it) }
        
        // Extract season/episode if present
        val seasonEp = guessSeasonEpisode(text)
        
        // Determine kind and confidence
        val (kind, confidence) = when {
            // If MediaParser already classified as RAR_ARCHIVE, keep it
            media.kind == MediaKind.RAR_ARCHIVE -> MediaKind.RAR_ARCHIVE to 1.0f
            
            // If we found season/episode info, likely a series episode
            seasonEp != null -> MediaKind.EPISODE to 0.9f
            
            // Check chat title for series indicators
            seriesIndicators.any { chatTitle.lowercase().contains(it) } -> {
                if (media.kind == MediaKind.EPISODE) {
                    MediaKind.EPISODE to 0.95f
                } else {
                    MediaKind.SERIES to 0.8f
                }
            }
            
            // Check chat title for movie indicators
            movieIndicators.any { chatTitle.lowercase().contains(it) } -> 
                MediaKind.MOVIE to 0.85f
            
            // If MediaParser suggested a kind, use it with lower confidence
            media.kind != MediaKind.OTHER -> media.kind to 0.7f
            
            // Default fallback
            else -> MediaKind.MOVIE to 0.5f
        }
        
        // Extract tags
        val tags = mutableListOf<String>()
        if (isAdult) tags.add("adult")
        if (seasonEp != null) tags.add("episode")
        tags.addAll(media.genres)
        
        return HeuristicResult(
            kind = kind,
            confidence = confidence,
            seasonEpisode = seasonEp,
            isAdult = isAdult,
            tags = tags
        )
    }
    
    /**
     * Try to extract season and episode numbers from text.
     * Returns null if no valid season/episode pattern found.
     */
    fun guessSeasonEpisode(text: String): SeasonEpisode? {
        for (pattern in seasonEpisodePatterns) {
            val match = pattern.find(text) ?: continue
            
            return when {
                match.groupValues.size >= 3 -> {
                    // Has both season and episode
                    val season = match.groupValues[1].toIntOrNull() ?: continue
                    val episode = match.groupValues[2].toIntOrNull() ?: continue
                    SeasonEpisode(season, episode)
                }
                match.groupValues.size >= 2 -> {
                    // Only episode number (assume season 1)
                    val episode = match.groupValues[1].toIntOrNull() ?: continue
                    SeasonEpisode(1, episode)
                }
                else -> continue
            }
        }
        return null
    }
    
    /**
     * Detect if a message is likely a sub-chat reference (link to another channel/group).
     */
    fun isSubChatReference(messageText: String): Boolean {
        // Telegram channel/group references: t.me/username or @username (username: 5-32 chars, letters/numbers/_)
        val telegramAtPattern = Regex("""(?<!\w)@([a-zA-Z0-9_]{5,32})""")
        return messageText.contains("t.me/") || telegramAtPattern.containsMatchIn(messageText)
    }
    
    /**
     * Calculate quality score based on available metadata.
     * Higher score = better quality/more complete metadata.
     */
    fun calculateQualityScore(media: MediaInfo): Float {
        var score = 0f
        var maxScore = 0f
        
        // Title is essential
        maxScore += 3f
        if (media.title?.isNotBlank() == true) score += 3f
        
        // Year is very important
        maxScore += 2f
        if (media.year != null && media.year > 1900) score += 2f
        
        // Genres add value
        maxScore += 1f
        if (media.genres.isNotEmpty()) score += 1f
        
        // Runtime/length is useful
        maxScore += 1f
        if (media.durationMinutes != null && media.durationMinutes > 0) score += 1f
        
        // TMDb rating indicates curated content
        maxScore += 1f
        if (media.tmdbRating != null && media.tmdbRating > 0) score += 1f
        
        // Country/FSK are nice-to-have
        maxScore += 0.5f
        if (media.country?.isNotBlank() == true) score += 0.25f
        if (media.fsk != null) score += 0.25f
        
        return if (maxScore > 0) score / maxScore else 0f
    }
}
