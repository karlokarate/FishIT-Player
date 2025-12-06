package com.fishit.player.core.model

/**
 * Canonical media identity used for cross-pipeline unification.
 *
 * This represents a globally unique identifier for a media work
 * across all pipelines (Telegram, Xtream, IO, etc.).
 *
 * Per MEDIA_NORMALIZATION_CONTRACT.md identity rules (ordered by priority):
 * 1. If tmdbId exists: key = "tmdb:<tmdbId>"
 * 2. If no tmdbId, but normalized title + year + (S/E for episodes):
 *    - Movies: key = "movie:<canonicalTitle>:<year>"
 *    - Episodes: key = "episode:<canonicalTitle>:S<season>E<episode>"
 * 3. If neither available: item cannot be assigned a stable CanonicalMediaId
 *
 * @property kind Type of media (MOVIE or EPISODE)
 * @property key Unique key string representing the canonical identity
 */
data class CanonicalMediaId(
    val kind: MediaKind,
    val key: String,
)

/**
 * Media kind for canonical identity.
 */
enum class MediaKind {
    /** Feature film or VOD movie */
    MOVIE,
    
    /** TV episode or series episode */
    EPISODE,
}
