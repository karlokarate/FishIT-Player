package com.fishit.player.core.persistence.obx

/**
 * Generates stable canonical keys for media identity.
 *
 * Used by NxCanonicalMediaRepositoryImpl, CatalogSync, and other consumers to
 * produce deterministic identity keys for media works.
 *
 * Key formats:
 * - TMDB-based (high confidence): `tmdb:movie:<id>` or `tmdb:tv:<id>`
 * - Title-based movie: `movie:<normalized-title>:<year>`
 * - Title-based episode: `episode:<normalized-title>:S<NN>E<NN>`
 *
 * Extracted from deprecated ObxCanonicalEntities.kt during P3 cleanup.
 */
object CanonicalKeyGenerator {
    private const val TMDB_PREFIX = "tmdb:"
    private const val MOVIE_PREFIX = "movie:"
    private const val EPISODE_PREFIX = "episode:"

    /**
     * Generate canonical key from typed TmdbRef (preferred).
     * Per Gold Decision Dec 2025: Format is tmdb:{type}:{id}
     */
    fun fromTmdbId(tmdbRef: com.fishit.player.core.model.TmdbRef): String {
        val typePath =
            when (tmdbRef.type) {
                com.fishit.player.core.model.TmdbMediaType.MOVIE -> "movie"
                com.fishit.player.core.model.TmdbMediaType.TV -> "tv"
            }
        return "$TMDB_PREFIX$typePath:${tmdbRef.id}"
    }

    /** Generate canonical key for a movie without TMDB ID. Uses normalized title + year. */
    fun forMovie(
        canonicalTitle: String,
        year: Int?,
    ): String {
        val normalized = normalizeForKey(canonicalTitle)
        return if (year != null) {
            "$MOVIE_PREFIX$normalized:$year"
        } else {
            "$MOVIE_PREFIX$normalized"
        }
    }

    /**
     * Generate canonical key for an episode without TMDB ID. Uses normalized series title + season
     * + episode.
     */
    fun forEpisode(
        seriesTitle: String,
        season: Int,
        episode: Int,
    ): String {
        val normalized = normalizeForKey(seriesTitle)
        return "$EPISODE_PREFIX$normalized:S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
    }

    /**
     * Normalize title for use in canonical key.
     * - Lowercase
     * - Replace spaces with hyphens
     * - Remove special characters
     * - Collapse multiple hyphens
     */
    private fun normalizeForKey(title: String): String =
        title
            .lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')

    /** Parse canonical key to determine kind. */
    fun kindFromKey(key: String): String =
        when {
            key.startsWith(TMDB_PREFIX) -> "movie" // May be refined by TMDB lookup
            key.startsWith(MOVIE_PREFIX) -> "movie"
            key.startsWith(EPISODE_PREFIX) -> "episode"
            else -> "movie"
        }

    /** Check if key is TMDB-based (high confidence). */
    fun isTmdbBased(key: String): Boolean = key.startsWith(TMDB_PREFIX)
}
