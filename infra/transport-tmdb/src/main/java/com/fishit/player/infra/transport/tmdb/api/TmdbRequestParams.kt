package com.fishit.player.infra.transport.tmdb.api

/**
 * Request parameters for TMDB API calls.
 *
 * @property language ISO 639-1 language code (e.g., "en-US", "de-DE")
 * @property region ISO 3166-1 alpha-2 country code for region-specific results (e.g., "US", "DE")
 */
data class TmdbRequestParams(
    val language: String,
    val region: String? = null,
)
