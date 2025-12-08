package com.chris.m3usuite.telegram.parser

import com.chris.m3usuite.telegram.domain.TelegramMetadata

/**
 * Extracts metadata from Telegram messages.
 *
 * Per TELEGRAM_PARSER_CONTRACT.md Section 6.4:
 * - Primary metadata source: ExportText parsed fields
 * - Secondary source: filename + caption
 * - TMDb URL extraction: structured entities (if present) then regex fallback
 * - Adult detection delegated exclusively to AdultHeuristics
 */
object TelegramMetadataExtractor {
    /**
     * Regex to extract TMDb URLs from text.
     * Matches both movie and TV URLs.
     */
    private val TMDB_URL_REGEX =
        Regex("""https?://(?:www\.)?themoviedb\.org/(movie|tv)/\d+[^\s]*""", RegexOption.IGNORE_CASE)

    /**
     * Regex to extract year from filename (e.g., "Movie Name (2023).mp4" or "Movie.2023.mp4").
     */
    private val YEAR_FROM_FILENAME_REGEX =
        Regex("""\b(19|20)\d{2}\b""")

    /**
     * Regex to extract title from filename before year or extension.
     */
    private val TITLE_FROM_FILENAME_REGEX =
        Regex(
            """^(.+?)[\.\s_-]*(?:\(?\d{4}\)?)?[\.\s_-]*(?:720p|1080p|2160p|4k|hdr|bluray|webrip|web-dl|hdtv)?.*\.(?:mp4|mkv|avi|mov|wmv)$""",
            RegexOption.IGNORE_CASE,
        )

    /**
     * Extract metadata from an ExportText message.
     *
     * Uses pre-parsed fields when available, falling back to text parsing.
     *
     * @param text The ExportText message
     * @param chatTitle Chat title for adult detection
     * @return Extracted metadata
     */
    fun extractFromText(
        text: ExportText,
        chatTitle: String?,
    ): TelegramMetadata {
        // Extract TMDb URL from entities or text
        val tmdbUrl = extractTmdbUrl(text)

        // Determine adult status via AdultHeuristics only
        val isAdult = AdultHeuristics.isAdultContent(chatTitle, text.text)

        return TelegramMetadata(
            title = text.title,
            originalTitle = text.originalTitle,
            year = text.year,
            lengthMinutes = text.lengthMinutes,
            fsk = text.fsk,
            productionCountry = text.productionCountry,
            collection = text.collection,
            director = text.director,
            tmdbRating = text.tmdbRating,
            genres = text.genres,
            tmdbUrl = tmdbUrl ?: text.tmdbUrl,
            isAdult = isAdult,
        )
    }

    /**
     * Extract metadata from filename and caption as fallback.
     *
     * @param fileName Video filename
     * @param caption Optional caption text
     * @param chatTitle Chat title for adult detection
     * @return Extracted metadata with best-effort title and year
     */
    fun extractFromFilename(
        fileName: String?,
        caption: String?,
        chatTitle: String?,
    ): TelegramMetadata {
        val year = extractYearFromFilename(fileName)
        val title = extractTitleFromFilename(fileName) ?: caption?.take(100)

        // Determine adult status via AdultHeuristics only
        val isAdult = AdultHeuristics.isAdultContent(chatTitle, caption)

        // Try to extract TMDb URL from caption
        val tmdbUrl = caption?.let { extractTmdbUrlFromText(it) }

        return TelegramMetadata(
            title = title,
            originalTitle = null,
            year = year,
            lengthMinutes = null,
            fsk = null,
            productionCountry = null,
            collection = null,
            director = null,
            tmdbRating = null,
            genres = emptyList(),
            tmdbUrl = tmdbUrl,
            isAdult = isAdult,
        )
    }

    /**
     * Merge metadata from multiple sources, preferring non-null values.
     *
     * @param primary Primary metadata source (usually from text)
     * @param secondary Secondary metadata source (usually from filename)
     * @return Merged metadata with primary values taking precedence
     */
    fun merge(
        primary: TelegramMetadata,
        secondary: TelegramMetadata,
    ): TelegramMetadata =
        TelegramMetadata(
            title = primary.title ?: secondary.title,
            originalTitle = primary.originalTitle ?: secondary.originalTitle,
            year = primary.year ?: secondary.year,
            lengthMinutes = primary.lengthMinutes ?: secondary.lengthMinutes,
            fsk = primary.fsk ?: secondary.fsk,
            productionCountry = primary.productionCountry ?: secondary.productionCountry,
            collection = primary.collection ?: secondary.collection,
            director = primary.director ?: secondary.director,
            tmdbRating = primary.tmdbRating ?: secondary.tmdbRating,
            genres = primary.genres.ifEmpty { secondary.genres },
            tmdbUrl = primary.tmdbUrl ?: secondary.tmdbUrl,
            isAdult = primary.isAdult || secondary.isAdult,
        )

    /**
     * Create empty metadata with only adult status based on chat title.
     *
     * @param chatTitle Chat title for adult detection
     * @return Minimal metadata
     */
    fun emptyMetadata(chatTitle: String?): TelegramMetadata =
        TelegramMetadata(
            title = null,
            originalTitle = null,
            year = null,
            lengthMinutes = null,
            fsk = null,
            productionCountry = null,
            collection = null,
            director = null,
            tmdbRating = null,
            genres = emptyList(),
            tmdbUrl = null,
            isAdult = AdultHeuristics.isAdultChatTitle(chatTitle),
        )

    /**
     * Extract TMDb URL from ExportText, checking entities first then text.
     *
     * Per contract Section 6.4:
     * 1. Parse structured URL entities from TextEntityTypeTextUrl if available
     * 2. Fallback to regex extraction from raw text
     */
    private fun extractTmdbUrl(text: ExportText): String? {
        // First, check entities for structured URL
        for (entity in text.entities) {
            entity.type?.url?.let { url ->
                if (isTmdbUrl(url)) {
                    return url
                }
            }
        }

        // Fallback to regex extraction from text
        return extractTmdbUrlFromText(text.text)
    }

    /**
     * Extract TMDb URL from raw text using regex.
     */
    private fun extractTmdbUrlFromText(text: String): String? = TMDB_URL_REGEX.find(text)?.value

    /**
     * Check if a URL is a TMDb URL.
     */
    private fun isTmdbUrl(url: String): Boolean = url.contains("themoviedb.org/movie/") || url.contains("themoviedb.org/tv/")

    /**
     * Extract year from filename.
     */
    private fun extractYearFromFilename(fileName: String?): Int? {
        if (fileName == null) return null

        // Find all year candidates
        val matches = YEAR_FROM_FILENAME_REGEX.findAll(fileName).toList()
        if (matches.isEmpty()) return null

        // Prefer year that appears after a separator or in parentheses
        // Take the last reasonable year (usually the release year)
        val year = matches.lastOrNull()?.value?.toIntOrNull()

        // Validate year range (1900-current year + 1)
        return year?.takeIf { it in 1900..2030 }
    }

    /**
     * Extract title from filename.
     */
    private fun extractTitleFromFilename(fileName: String?): String? {
        if (fileName == null) return null

        // Try the complex regex first
        TITLE_FROM_FILENAME_REGEX.find(fileName)?.groupValues?.getOrNull(1)?.let { title ->
            return cleanTitle(title)
        }

        // Fallback: take everything before the last dot (extension)
        val withoutExtension = fileName.substringBeforeLast(".")
        if (withoutExtension.isBlank()) return null

        // Remove year if present
        val withoutYear = withoutExtension.replace(YEAR_FROM_FILENAME_REGEX, "")

        return cleanTitle(withoutYear)
    }

    /**
     * Clean up extracted title by replacing separators and trimming.
     */
    private fun cleanTitle(title: String): String =
        title
            .replace(".", " ")
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotBlank() }
            ?: title.trim()
}
