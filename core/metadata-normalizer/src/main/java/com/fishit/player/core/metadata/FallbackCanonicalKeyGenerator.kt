package com.fishit.player.core.metadata

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.ids.CanonicalId

/**
 * Fallback generator for canonical IDs when TMDB data is unavailable.
 *
 * Per Media Normalization Contract (docs/v2/MEDIA_NORMALIZATION_CONTRACT.md):
 * - Episodes with season/episode → `episode:<canonicalTitle>:S<season>E<episode>`
 * - Movies with year → `movie:<canonicalTitle>:<year>`
 * - Items lacking minimum metadata → cannot be canonically identified (returns null)
 *
 * This is exclusively for use by `:core:metadata-normalizer`. Pipelines MUST NOT use this.
 *
 * **Examples:**
 * - `"Breaking Bad"` (S05E16) → `"episode:breaking-bad:S05E16"`
 * - `"Inception"` (2010) → `"movie:inception:2010"`
 * - `"Unknown Title"` (no year, no S/E) → `null`
 */
object FallbackCanonicalKeyGenerator {

    // Basic scene tags to strip (resolution, codec, group, source, season/episode patterns)
    private val sceneTagPattern =
            Regex(
                    """[\.\s]*(720p|1080p|2160p|4k|uhd|hdr|bluray|bdrip|webrip|web-dl|hdtv|dvdrip|x264|x265|h264|h265|aac|dts|ac3|atmos|remux|s\d{1,2}e\d{1,2}|\[.*?]|-.{1,15}$)""",
                    RegexOption.IGNORE_CASE,
            )

    /**
     * Generate a fallback canonical ID for a media item when TMDB data is unavailable.
     *
     * **Contract-aligned rules:**
     * 1. Episodes with season + episode → `episode:<title>:SxxExx`
     * 2. Movies/other with year → `movie:<title>:<year>`
     * 3. Items without sufficient metadata → return null (unlinked)
     *
     * @param originalTitle The raw title from the source (will be normalized)
     * @param year Release year if known, null otherwise
     * @param season Season number for episodes when available
     * @param episode Episode number for episodes when available
     * @param mediaType Media type hint (LIVE always returns null per contract)
     * @return Canonical ID string or null if item cannot be canonically identified
     */
    fun generateFallbackCanonicalId(
            originalTitle: String,
            year: Int?,
            season: Int?,
            episode: Int?,
            mediaType: MediaType,
    ): CanonicalId? {
        // LIVE content is explicitly unlinked per contract
        if (mediaType == MediaType.LIVE) {
            return null
        }

        val normalizedTitle = normalizeTitle(originalTitle)
        val titleForKey = normalizeForKey(normalizedTitle)

        // Episode with full season/episode metadata
        if (season != null && episode != null) {
            return CanonicalId(
                    "episode:${titleForKey}:S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
            )
        }

        // Movie or fallback: require year for stable canonical identity
        if (year != null) {
            return CanonicalId("movie:${titleForKey}:${year}")
        }

        // Insufficient metadata: cannot assign canonical identity
        return null
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
