package com.fishit.player.core.model

/**
 * TMDB media type classification - the SSOT for typed TMDB references.
 *
 * Per Gold Decision (Dec 2025):
 * - TMDB only knows MOVIE and TV as root types
 * - Episodes are NOT a separate TMDB type
 * - Episodes are represented as: TmdbRef(TV, seriesId) + season/episode from RawMediaMetadata
 *
 * This enables direct TMDB API calls without conversion:
 * - MOVIE → GET /movie/{id}
 * - TV → GET /tv/{id}
 * - Episode details: GET /tv/{id}/season/{s}/episode/{e} (using season/episode from NormalizedMedia)
 *
 * @see TmdbRef for the typed reference combining type and ID
 */
enum class TmdbMediaType {
    /**
     * TMDB Movie type.
     *
     * API: GET /movie/{id}
     * CanonicalId format: tmdb:movie:{id}
     */
    MOVIE,

    /**
     * TMDB TV Show type (includes series/episodes).
     *
     * Episodes use this type with the series ID, combined with season/episode fields.
     *
     * API for show: GET /tv/{id}
     * API for episode: GET /tv/{id}/season/{season}/episode/{episode}
     * CanonicalId format: tmdb:tv:{id}
     */
    TV,
}

/**
 * Typed TMDB reference - combines TMDB ID with its media type.
 *
 * This is the canonical way to reference TMDB entities in the v2 architecture.
 *
 * **Episode Handling:**
 * Episodes are represented as `TmdbRef(TV, seriesId)` plus the `season` and `episode`
 * fields from RawMediaMetadata/NormalizedMedia. Never create episode-specific TMDB refs.
 *
 * **Example Usage:**
 * ```kotlin
 * // Movie: Inception (TMDB ID 27205)
 * val movieRef = TmdbRef(TmdbMediaType.MOVIE, 27205)
 * // API: GET /movie/27205
 *
 * // TV Show: Breaking Bad (TMDB ID 1396)
 * val showRef = TmdbRef(TmdbMediaType.TV, 1396)
 * // API for show: GET /tv/1396
 * // API for S05E16: GET /tv/1396/season/5/episode/16
 * ```
 *
 * **CanonicalId Generation:**
 * - MOVIE → "tmdb:movie:27205"
 * - TV → "tmdb:tv:1396"
 *
 * @property type The TMDB media type (MOVIE or TV)
 * @property id The TMDB numeric ID
 */
data class TmdbRef(
    val type: TmdbMediaType,
    val id: Int,
) {
    init {
        require(id > 0) { "TMDB ID must be positive, got: $id" }
    }

    /**
     * Generate the canonical ID string for this TMDB reference.
     *
     * Format:
     * - MOVIE → "tmdb:movie:{id}"
     * - TV → "tmdb:tv:{id}"
     *
     * @return The canonical ID string suitable for deduplication
     */
    fun toCanonicalIdString(): String = when (type) {
        TmdbMediaType.MOVIE -> "tmdb:movie:$id"
        TmdbMediaType.TV -> "tmdb:tv:$id"
    }

    override fun toString(): String = toCanonicalIdString()

    companion object {
        /**
         * Parse a canonical ID string back to TmdbRef.
         *
         * Supports:
         * - "tmdb:movie:123" → TmdbRef(MOVIE, 123)
         * - "tmdb:tv:456" → TmdbRef(TV, 456)
         *
         * @return TmdbRef or null if format doesn't match
         */
        fun fromCanonicalIdString(canonicalId: String): TmdbRef? {
            val movieMatch = TMDB_MOVIE_PATTERN.matchEntire(canonicalId)
            if (movieMatch != null) {
                val id = movieMatch.groupValues[1].toIntOrNull() ?: return null
                return TmdbRef(TmdbMediaType.MOVIE, id)
            }

            val tvMatch = TMDB_TV_PATTERN.matchEntire(canonicalId)
            if (tvMatch != null) {
                val id = tvMatch.groupValues[1].toIntOrNull() ?: return null
                return TmdbRef(TmdbMediaType.TV, id)
            }

            return null
        }

        /**
         * Check if a canonical ID is an untyped legacy format.
         *
         * Legacy format: "tmdb:123" (without movie/tv qualifier)
         *
         * @return true if this is a legacy untyped TMDB canonical ID
         */
        fun isLegacyUntypedCanonicalId(canonicalId: String): Boolean =
            LEGACY_TMDB_PATTERN.matches(canonicalId)

        /**
         * Parse a legacy untyped canonical ID and determine type from MediaType context.
         *
         * Used for migration of existing stored canonical IDs.
         *
         * @param canonicalId Legacy format "tmdb:123"
         * @param mediaType MediaType to infer TMDB type from
         * @return Typed TmdbRef or null if cannot be determined
         */
        fun migrateLegacyCanonicalId(canonicalId: String, mediaType: MediaType): TmdbRef? {
            val match = LEGACY_TMDB_PATTERN.matchEntire(canonicalId) ?: return null
            val id = match.groupValues[1].toIntOrNull() ?: return null

            val type = when (mediaType) {
                MediaType.MOVIE -> TmdbMediaType.MOVIE
                MediaType.SERIES, MediaType.SERIES_EPISODE -> TmdbMediaType.TV
                else -> return null // Cannot determine type for LIVE, UNKNOWN, etc.
            }

            return TmdbRef(type, id)
        }

        private val TMDB_MOVIE_PATTERN = Regex("^tmdb:movie:(\\d+)$")
        private val TMDB_TV_PATTERN = Regex("^tmdb:tv:(\\d+)$")
        private val LEGACY_TMDB_PATTERN = Regex("^tmdb:(\\d+)$")
    }
}

/**
 * Extension to create TmdbRef for a movie.
 */
fun Int.asTmdbMovieRef(): TmdbRef = TmdbRef(TmdbMediaType.MOVIE, this)

/**
 * Extension to create TmdbRef for a TV show.
 */
fun Int.asTmdbTvRef(): TmdbRef = TmdbRef(TmdbMediaType.TV, this)
