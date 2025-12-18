package com.fishit.player.pipeline.telegram.model

import com.fishit.player.core.model.TmdbMediaType

/**
 * TMDB type indicator extracted from Telegram structured bundle URLs.
 *
 * Per Gold Decision (Dec 2025):
 * - TMDB only knows MOVIE and TV as root types
 * - Episodes use TV type with season/episode from other fields
 * - Never use EPISODE type
 *
 * Extracted from TMDB URLs in structured TEXT messages:
 * - "https://www.themoviedb.org/movie/12345" → MOVIE
 * - "https://www.themoviedb.org/tv/67890" → TV
 *
 * @see TelegramMediaItem.structuredTmdbType
 */
enum class TelegramTmdbType {
    /** TMDB Movie - /movie/{id} path */
    MOVIE,

    /** TMDB TV Show - /tv/{id} path (episodes use this with season/episode) */
    TV;

    /**
     * Convert to core model TmdbMediaType.
     */
    fun toTmdbMediaType(): TmdbMediaType = when (this) {
        MOVIE -> TmdbMediaType.MOVIE
        TV -> TmdbMediaType.TV
    }

    companion object {
        /**
         * Parse TMDB URL to extract type and ID.
         *
         * Supported URL formats:
         * - https://www.themoviedb.org/movie/12345
         * - https://themoviedb.org/movie/12345
         * - https://www.themoviedb.org/tv/67890
         * - https://themoviedb.org/tv/67890-slug
         *
         * @param url TMDB URL string
         * @return Pair of (type, id) or null if URL doesn't match
         */
        fun parseFromUrl(url: String): Pair<TelegramTmdbType, Int>? {
            val movieMatch = MOVIE_URL_PATTERN.find(url)
            if (movieMatch != null) {
                val id = movieMatch.groupValues[1].toIntOrNull() ?: return null
                return MOVIE to id
            }

            val tvMatch = TV_URL_PATTERN.find(url)
            if (tvMatch != null) {
                val id = tvMatch.groupValues[1].toIntOrNull() ?: return null
                return TV to id
            }

            return null
        }

        // Match /movie/123 or /movie/123-slug
        private val MOVIE_URL_PATTERN = Regex("""/movie/(\d+)""")
        
        // Match /tv/123 or /tv/123-slug
        private val TV_URL_PATTERN = Regex("""/tv/(\d+)""")
    }
}
