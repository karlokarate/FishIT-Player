package com.fishit.player.core.metadata.tmdb

/**
 * Configuration for TMDB API integration.
 *
 * Per TMDB_ENRICHMENT_CONTRACT.md:
 * - apiKey MUST come from non-committed source (BuildConfig, gradle.properties, environment)
 * - If apiKey is missing/blank: Resolver returns Disabled result (no crash)
 * - Logging MUST NEVER include apiKey
 *
 * @property apiKey TMDB API v3 key (NEVER log or commit this)
 * @property language Language for TMDB queries (ISO 639-1 code, default: en-US)
 * @property region Optional region for release dates (ISO 3166-1 code, e.g., US, DE)
 */
data class TmdbConfig(
    val apiKey: String,
    val language: String = "en-US",
    val region: String? = null,
) {
    /**
     * Check if TMDB is enabled (apiKey is not blank).
     */
    val isEnabled: Boolean get() = apiKey.isNotBlank()
}
