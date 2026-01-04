package com.fishit.player.pipeline.telegram.grouper

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.api.TgContent
import com.fishit.player.infra.transport.telegram.api.TgMessage
import com.fishit.player.pipeline.telegram.model.TelegramTmdbType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts structured metadata from Telegram TEXT messages.
 *
 * Per TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md Section 1.2:
 * - Parses JSON-like fields from TEXT message captions
 * - Extracts TMDB URL and converts to typed reference (ID + type)
 * - Applies Schema Guards (Contract R4) for range validation
 *
 * **Supported Fields:**
 * - tmdbUrl → tmdbId + tmdbType (via TelegramTmdbType.parseFromUrl())
 * - tmdbRating, year, fsk, genres, director, originalTitle
 * - lengthMinutes, productionCountry
 *
 * Per Gold Decision Dec 2025:
 * - TMDB type (MOVIE or TV) is extracted from URL path
 * - Both ID and type are required for typed canonical IDs
 *
 * Per MEDIA_NORMALIZATION_CONTRACT: All values RAW extracted, no cleaning.
 *
 * **Schema Guards (Contract R4):**
 * - year: 1800..2100 else null
 * - tmdbRating: 0.0..10.0 else null
 * - fsk: 0..21 else null
 * - lengthMinutes: 1..600 else null
 */
@Singleton
class TelegramStructuredMetadataExtractor
    @Inject
    constructor() {
        companion object {
            private const val TAG = "TelegramStructuredMetadataExtractor"

            // Schema Guard ranges (Contract R4)
            private val YEAR_RANGE = 1800..2100
            private val RATING_RANGE = 0.0..10.0
            private val FSK_RANGE = 0..21
            private val LENGTH_RANGE = 1..600

            // Field extraction patterns for JSON-like text
            private val TMDB_URL_PATTERN =
                Regex(""""?tmdbUrl"?\s*[:=]\s*"?([^"'\s,}]+)"?""", RegexOption.IGNORE_CASE)
            private val TMDB_RATING_PATTERN =
                Regex(""""?tmdbRating"?\s*[:=]\s*(\d+\.?\d*)""", RegexOption.IGNORE_CASE)
            private val YEAR_PATTERN = Regex(""""?year"?\s*[:=]\s*(\d{4})""", RegexOption.IGNORE_CASE)
            private val FSK_PATTERN = Regex(""""?fsk"?\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
            private val DIRECTOR_PATTERN =
                Regex(""""?director"?\s*[:=]\s*"?([^"'\n,}]+)"?""", RegexOption.IGNORE_CASE)
            private val ORIGINAL_TITLE_PATTERN =
                Regex(""""?originalTitle"?\s*[:=]\s*"?([^"'\n,}]+)"?""", RegexOption.IGNORE_CASE)
            private val LENGTH_PATTERN =
                Regex(""""?lengthMinutes"?\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
            private val COUNTRY_PATTERN =
                Regex(
                    """"?productionCountry"?\s*[:=]\s*"?([^"'\n,}]+)"?""",
                    RegexOption.IGNORE_CASE,
                )
            private val GENRES_PATTERN =
                Regex(""""?genres"?\s*[:=]\s*\[([^\]]+)\]""", RegexOption.IGNORE_CASE)
        }

        /**
         * Checks if a TEXT message contains structured fields.
         *
         * A message is considered structured if it has any of:
         * - tmdbUrl
         * - tmdbRating
         * - year field (not just in title)
         * - fsk
         *
         * @param textMessage The TEXT message to check
         * @return true if structured fields are detected
         */
        fun hasStructuredFields(textMessage: TgMessage): Boolean {
            val text = extractTextContent(textMessage) ?: return false

            // FIX: Year alone qualifies as structured per contract R6
            // No need to also require director field
            return TMDB_URL_PATTERN.containsMatchIn(text) ||
                TMDB_RATING_PATTERN.containsMatchIn(text) ||
                FSK_PATTERN.containsMatchIn(text) ||
                YEAR_PATTERN.containsMatchIn(text)
        }

        /**
         * Extracts all structured fields from a TEXT message.
         *
         * Per Gold Decision Dec 2025: Uses TelegramTmdbType.parseFromUrl() to extract
         * both TMDB ID and type from the URL in a single pass.
         *
         * @param textMessage The TEXT message to extract from
         * @return StructuredMetadata or null if no structured fields
         */
        fun extractStructuredMetadata(textMessage: TgMessage): StructuredMetadata? {
            val text = extractTextContent(textMessage) ?: return null

            if (!hasStructuredFields(textMessage)) {
                return null
            }

            // Extract typed TMDB reference (ID + type) using TelegramTmdbType.parseFromUrl()
            val tmdbUrl = extractField(text, TMDB_URL_PATTERN)
            val tmdbParsed = tmdbUrl?.let { TelegramTmdbType.parseFromUrl(it) }
            val tmdbType = tmdbParsed?.first
            val tmdbId = tmdbParsed?.second

            val tmdbRating = extractDouble(text, TMDB_RATING_PATTERN)?.let { applyRatingGuard(it) }
            val year = extractInt(text, YEAR_PATTERN)?.let { applyYearGuard(it) }
            val fsk = extractInt(text, FSK_PATTERN)?.let { applyFskGuard(it) }
            val director = extractField(text, DIRECTOR_PATTERN)?.trim()
            val originalTitle = extractField(text, ORIGINAL_TITLE_PATTERN)?.trim()
            val lengthMinutes = extractInt(text, LENGTH_PATTERN)?.let { applyLengthGuard(it) }
            val productionCountry = extractField(text, COUNTRY_PATTERN)?.trim()
            val genres = extractGenres(text)

            val metadata =
                StructuredMetadata(
                    tmdbId = tmdbId,
                    tmdbType = tmdbType,
                    tmdbRating = tmdbRating,
                    year = year,
                    fsk = fsk,
                    genres = genres,
                    director = director,
                    originalTitle = originalTitle,
                    lengthMinutes = lengthMinutes,
                    productionCountry = productionCountry,
                )

            if (metadata.hasAnyField) {
                val chatId = textMessage.chatId
                UnifiedLog.d(TAG) { "Extracted: chatId=$chatId, tmdbId=$tmdbId, tmdbType=$tmdbType, year=$year, fsk=$fsk" }
                return metadata
            }

            return null
        }

        /**
         * Extracts TMDB ID from URL (legacy, kept for backward compatibility).
         *
         * **Prefer using TelegramTmdbType.parseFromUrl() for typed extraction.**
         *
         * Supports formats:
         * - "https://www.themoviedb.org/movie/12345-movie-name" → 12345
         * - "https://www.themoviedb.org/tv/98765-show" → 98765
         * - "themoviedb.org/movie/12345" → 12345
         *
         * @param tmdbUrl The TMDB URL to parse
         * @return TMDB ID as Int or null if parse fails
         */
        @Deprecated(
            "Use TelegramTmdbType.parseFromUrl() for typed extraction",
            ReplaceWith("TelegramTmdbType.parseFromUrl(tmdbUrl)?.second"),
        )
        fun extractTmdbIdFromUrl(tmdbUrl: String?): Int? {
            if (tmdbUrl.isNullOrBlank()) return null
            return TelegramTmdbType.parseFromUrl(tmdbUrl)?.second
        }

        // ========== Schema Guards (Contract R4) ==========

        private fun applyYearGuard(year: Int): Int? =
            if (year in YEAR_RANGE) {
                year
            } else {
                UnifiedLog.d(TAG) { "Schema Guard: year=$year outside $YEAR_RANGE" }
                null
            }

        private fun applyRatingGuard(rating: Double): Double? =
            if (rating in RATING_RANGE) {
                rating
            } else {
                UnifiedLog.d(TAG) { "Schema Guard: rating=$rating outside $RATING_RANGE" }
                null
            }

        private fun applyFskGuard(fsk: Int): Int? =
            if (fsk in FSK_RANGE) {
                fsk
            } else {
                UnifiedLog.d(TAG) { "Schema Guard: fsk=$fsk outside $FSK_RANGE" }
                null
            }

        private fun applyLengthGuard(length: Int): Int? =
            if (length in LENGTH_RANGE) {
                length
            } else {
                UnifiedLog.d(TAG) { "Schema Guard: lengthMinutes=$length outside $LENGTH_RANGE" }
                null
            }

        // ========== Helper Methods ==========

        private fun extractTextContent(message: TgMessage): String? {
            // TEXT messages may have caption in content or be plain text
            return when (val content = message.content) {
                is TgContent.Text -> content.text
                is TgContent.Photo -> content.caption
                is TgContent.Video -> content.caption
                is TgContent.Document -> content.caption
                is TgContent.Audio -> content.caption
                is TgContent.Unknown -> null // Could parse kind for text
                null -> null // Pure text message - need to access via different mechanism
                else -> null
            }
        }

        private fun extractField(
            text: String,
            pattern: Regex,
        ): String? = pattern.find(text)?.groupValues?.getOrNull(1)

        private fun extractInt(
            text: String,
            pattern: Regex,
        ): Int? = extractField(text, pattern)?.toIntOrNull()

        private fun extractDouble(
            text: String,
            pattern: Regex,
        ): Double? = extractField(text, pattern)?.toDoubleOrNull()

        private fun extractGenres(text: String): List<String> {
            val match = GENRES_PATTERN.find(text) ?: return emptyList()
            val genresString = match.groupValues.getOrNull(1) ?: return emptyList()

            return genresString
                .split(",")
                .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                .filter { it.isNotBlank() }
        }
    }
