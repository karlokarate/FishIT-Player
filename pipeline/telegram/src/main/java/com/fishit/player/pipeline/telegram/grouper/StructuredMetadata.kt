package com.fishit.player.pipeline.telegram.grouper

import com.fishit.player.pipeline.telegram.model.TelegramTmdbType

/**
 * Extracted structured metadata from a Telegram TEXT message.
 *
 * Per TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md Section 1.2:
 * These fields are extracted from TEXT messages in structured bundles.
 * All values are RAW extracted - validation happens via Schema Guards.
 *
 * Per Contract R4 Schema Guards, values outside these ranges are set to null:
 * - year: 1800..2100
 * - tmdbRating: 0.0..10.0
 * - fsk: 0..21
 * - lengthMinutes: 1..600
 *
 * @property tmdbId TMDB ID extracted from tmdbUrl (Integer, not the full URL)
 * @property tmdbType TMDB type extracted from tmdbUrl (MOVIE or TV). Per Gold Decision Dec 2025.
 * @property tmdbRating Rating from TMDB (0.0-10.0 scale)
 * @property year Release year
 * @property fsk FSK age rating (0, 6, 12, 16, 18, 21)
 * @property genres List of genre names
 * @property director Director name
 * @property originalTitle Original language title
 * @property lengthMinutes Runtime in minutes
 * @property productionCountry Production country code or name
 */
data class StructuredMetadata(
    val tmdbId: Int?,
    val tmdbType: TelegramTmdbType?,
    val tmdbRating: Double?,
    val year: Int?,
    val fsk: Int?,
    val genres: List<String>,
    val director: String?,
    val originalTitle: String?,
    val lengthMinutes: Int?,
    val productionCountry: String?,
) {
    /**
     * Whether this metadata has a TMDB ID for downstream unification.
     */
    val hasTmdbId: Boolean get() = tmdbId != null
    
    /**
     * Whether this metadata has a typed TMDB reference (ID + type).
     * Per Gold Decision Dec 2025: Both ID and type required for typed canonical ID.
     */
    val hasTypedTmdb: Boolean get() = tmdbId != null && tmdbType != null
    
    /**
     * Whether this metadata has any useful fields.
     */
    val hasAnyField: Boolean get() =
        tmdbId != null ||
            tmdbRating != null ||
            year != null ||
            fsk != null ||
            genres.isNotEmpty() ||
            director != null ||
            originalTitle != null ||
            lengthMinutes != null ||
            productionCountry != null
    
    companion object {
        /**
         * Empty metadata instance for when extraction fails.
         */
        val EMPTY = StructuredMetadata(
            tmdbId = null,
            tmdbType = null,
            tmdbRating = null,
            year = null,
            fsk = null,
            genres = emptyList(),
            director = null,
            originalTitle = null,
            lengthMinutes = null,
            productionCountry = null,
        )
    }
}
