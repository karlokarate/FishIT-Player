package com.fishit.player.core.metadata

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.ids.CanonicalId

/**
 * Generates contract-aligned fallback canonical keys when TMDB identity is unavailable.
 *
 * Fallbacks are only generated for episodes with both season and episode numbers, or for
 * movies. All other media (including Live) remain unlinked.
 */
object FallbackCanonicalKeyGenerator {

    fun generateFallbackCanonicalId(
            originalTitle: String,
            year: Int?,
            season: Int?,
            episode: Int?,
            mediaType: MediaType,
    ): CanonicalId? {
        if (mediaType == MediaType.LIVE) return null

        val slug = originalTitle.toSlug()

        return when {
            season != null && episode != null ->
                    CanonicalId(
                            "episode:${slug}:S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
                    )
            mediaType == MediaType.MOVIE -> CanonicalId("movie:$slug${year?.let { ":$it" } ?: ""}")
            else -> null
        }
    }

    private fun String.toSlug(): String =
            lowercase()
                    .replace(Regex("[^a-z0-9\\s-]"), "")
                    .replace(Regex("\\s+"), "-")
                    .replace(Regex("-+"), "-")
                    .trim('-')
}
