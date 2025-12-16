package com.fishit.player.core.metadata

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.ids.CanonicalId

/**
 * Utility for generating deterministic global IDs across pipelines.
 *
 * Global IDs enable cross-pipeline deduplication: the same movie from Telegram and Xtream will
 * share the same globalId, allowing the normalizer to merge them as variants of a single
 * NormalizedMedia.
 *
 * **Algorithm (contract-aligned fallback):**
 * 1. Normalize title (lowercase, collapse spaces, strip obvious scene tags)
 * 2. Normalize for key (letters/digits only, spaces → hyphen, collapse hyphens)
 * 3. Return canonical key in contract format:
 *    - Episode fallback: `"episode:<title>:S<season>E<episode>"`
 *    - Movie fallback: `"movie:<title>[:<year>]"`
 *
 * **Examples:**
 * - `"Breaking Bad"` (2008, S05E16) → `"episode:breaking-bad:S05E16"`
 * - `"Inception"` (2010) → `"movie:inception:2010"`
 */
object GlobalIdUtil {

    // Basic scene tags to strip (resolution, codec, group, source)
    private val sceneTagPattern =
            Regex(
                    """[\.\s]*(720p|1080p|2160p|4k|uhd|hdr|bluray|bdrip|webrip|web-dl|hdtv|dvdrip|x264|x265|h264|h265|aac|dts|ac3|atmos|remux|\[.*?]|-.{1,15}$)""",
                    RegexOption.IGNORE_CASE,
            )

    /**
     * Generate a canonical global ID for a media item.
     *
     * @param originalTitle The raw title from the source (will be normalized)
     * @param year Release year if known, null otherwise
     * @param season Season number for episodes when available
     * @param episode Episode number for episodes when available
     * @param mediaType Media type hint to disambiguate fallback formatting
     * @return Contract canonical ID (tmdb:<id> or movie:/episode: fallback)
     */
    fun generateCanonicalId(
            originalTitle: String,
            year: Int?,
            season: Int? = null,
            episode: Int? = null,
            mediaType: MediaType = MediaType.UNKNOWN,
    ): CanonicalId {
        val normalizedTitle = normalizeTitle(originalTitle)
        val titleForKey = normalizeForKey(normalizedTitle)

        return if (season != null && episode != null) {
            // Episode with season/episode numbers: episode:<title>:S<season>E<episode>
            CanonicalId(
                    "episode:${titleForKey}:S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
            )
        } else {
            // Fall back to movie-style format for episodes without season/episode metadata
            // or for actual movies. Per contract, episodes without S/E cannot be canonically
            // identified, so we treat them like movies for identification purposes.
            val yearPart = year?.let { ":$it" } ?: ""
            CanonicalId("movie:${titleForKey}$yearPart")
        }
    }

    /**
     * Normalize a title for comparison and hashing.
     *
     * - Trim whitespace
     * - Convert to lowercase
     * - Replace dots/underscores with spaces
     * - Strip obvious scene tags
     * - Collapse multiple spaces
     */
    internal fun normalizeTitle(title: String): String {
        return title.trim()
                .lowercase()
                .replace('.', ' ')
                .replace('_', ' ')
                .replace(sceneTagPattern, "")
                .replace(Regex("""\s+"""), " ")
                .trim()
    }

    /**
     * Normalize a title string for canonical key composition.
     * - Lowercase
     * - Replace non-alphanumeric characters with hyphens
     * - Collapse multiple hyphens
     */
    internal fun normalizeForKey(title: String): String =
            title.lowercase()
                    .replace(Regex("[^a-z0-9\\s-]"), "")
                    .replace(Regex("\\s+"), "-")
                    .replace(Regex("-+"), "-")
                    .trim('-')
}
