package com.fishit.player.core.metadata

import java.security.MessageDigest

/**
 * Utility for generating deterministic global IDs across pipelines.
 *
 * Global IDs enable cross-pipeline deduplication: the same movie from Telegram and Xtream will
 * share the same globalId, allowing the normalizer to merge them as variants of a single
 * NormalizedMedia.
 *
 * **Algorithm:**
 * 1. Normalize title (lowercase, collapse spaces, strip obvious scene tags)
 * 2. Compose base string: `<normalizedTitle>|<year-or-unknown>`
 * 3. SHA-256 hash the base string
 * 4. Return `"cm:" + first 16 hex chars` ("cm" = canonical media)
 *
 * **Examples:**
 * - `"Breaking Bad"` (2008) → `"cm:a1b2c3d4e5f6g7h8"`
 * - `"Breaking.Bad.2008.1080p.BluRay.x264-GROUP"` (2008) → same hash after normalization
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
     * @return Canonical ID in format `"cm:<16-char-hex>"`
     */
    fun generateCanonicalId(originalTitle: String, year: Int?): String {
        val normalizedTitle = normalizeTitle(originalTitle)
        val yearPart = year?.toString() ?: "unknown"
        val baseString = "$normalizedTitle|$yearPart"

        val hash = sha256(baseString)
        return "cm:${hash.take(16)}"
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

    /** Compute SHA-256 hash and return as hex string. */
    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
