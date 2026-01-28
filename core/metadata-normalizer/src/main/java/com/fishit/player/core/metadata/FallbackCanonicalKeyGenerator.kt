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

        val cleanedTitle = stripSceneTags(originalTitle)
        val slug = toSlug(cleanedTitle)

        return when {
            season != null && episode != null ->
                CanonicalId(
                    "episode:$slug:S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}",
                )
            mediaType == MediaType.MOVIE ->
                CanonicalId("movie:$slug${year?.let { ":$it" } ?: ":unknown"}")
            mediaType == MediaType.SERIES ->
                // BUG FIX: Add fallback for series (was missing, returned null)
                // Format: "series:<slug>:<year>" or "series:<slug>:unknown"
                // This allows series detail screens to work even without year
                CanonicalId("series:$slug${year?.let { ":$it" } ?: ":unknown"}")
            else -> null
        }
    }

    private val sceneTagPattern =
        Regex(
            """[\.\s]*(720p|1080p|2160p|4k|uhd|hdr|bluray|bdrip|webrip|web-dl|hdtv|dvdrip|x264|x265|h264|h265|aac|dts|ac3|atmos|remux|\[.*?]|-.{1,15}$)""",
            RegexOption.IGNORE_CASE,
        )

    private fun stripSceneTags(title: String): String =
        title
            .replace('.', ' ')
            .replace('_', ' ')
            .replace(sceneTagPattern, "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun toSlug(input: String): String =
        input
            .lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
}
