package com.fishit.player.core.metadata

import java.security.MessageDigest

/**
 * Utility for generating deterministic canonical IDs for normalized media.
 *
 * Canonical IDs enable cross-pipeline deduplication: the same movie from Telegram and Xtream will
 * share the same canonicalId, allowing the normalizer to merge them as variants of a single
 * NormalizedMedia.
 *
 * **IMPORTANT:**
 * - This util lives in :core:metadata-normalizer and MUST NOT be imported by pipeline modules.
 * - Pipelines MUST NOT call canonical ID generation - only the normalizer may.
 * - This util operates on normalized/canonical inputs only, never on raw titles.
 *
 * **Algorithm:**
 * 1. TMDB ID takes absolute priority - if present, hash on tmdbId + mediaType
 * 2. Otherwise, compose base string from canonicalTitle + year + season + episode
 * 3. SHA-256 hash the base string
 * 4. Return `"cm:" + first 16 hex chars` ("cm" = canonical media)
 *
 * **Examples:**
 * - TMDB ID present: `"Breaking Bad"` with tmdbId "1396" → `"cm:abc123..."`
 * - No TMDB ID: `"Breaking Bad"` (2008) S01E01 → `"cm:def456..."`
 *
 * **Contract Compliance:**
 * - Per MEDIA_NORMALIZATION_CONTRACT.md: Only normalizer may generate canonical IDs
 * - Per GLOSSARY_v2: GlobalIdUtil removed from core:model to prevent pipeline access
 * - Scene tag stripping removed - operates only on pre-normalized canonical titles
 */
object CanonicalIdUtil {

    /**
     * Generate a canonical ID for normalized media metadata.
     *
     * TMDB ID takes priority when available. Otherwise, uses canonical title + year + season/episode.
     *
     * @param canonicalTitle Normalized, cleaned title (NOT raw title)
     * @param year Release year if known, null otherwise
     * @param season Season number for episodes, null for movies
     * @param episode Episode number for episodes, null for movies
     * @param tmdbId TMDB ID if available (takes priority)
     * @return Canonical ID in format `"cm:<16-char-hex>"`
     */
    fun canonicalHashId(
        canonicalTitle: String,
        year: Int? = null,
        season: Int? = null,
        episode: Int? = null,
        tmdbId: String? = null,
    ): String {
        val baseString = if (tmdbId != null) {
            // TMDB ID is the strongest identifier - use it exclusively
            // Include season/episode for series to distinguish episodes of same show
            buildString {
                append("tmdb:")
                append(tmdbId)
                // Handle series-level (season only) and episode-level (season + episode)
                if (season != null) {
                    append("|S")
                    append(season.toString().padStart(2, '0'))
                    if (episode != null) {
                        append("E")
                        append(episode.toString().padStart(2, '0'))
                    }
                }
            }
        } else {
            // Fallback: canonical title + year + season/episode
            buildString {
                append(canonicalTitle)
                append("|")
                append(year?.toString() ?: "unknown")
                // Handle series-level (season only) and episode-level (season + episode)
                if (season != null) {
                    append("|S")
                    append(season.toString().padStart(2, '0'))
                    if (episode != null) {
                        append("E")
                        append(episode.toString().padStart(2, '0'))
                    }
                }
            }
        }

        val hash = sha256(baseString)
        return "cm:${hash.take(16)}"
    }

    /** Compute SHA-256 hash and return as hex string. */
    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
