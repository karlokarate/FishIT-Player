package com.fishit.player.core.metadata.tmdb

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configuration for TMDB API access.
 *
 * Per TMDB_ENRICHMENT_CONTRACT.md T-12:
 * If [apiKey] is blank, resolver is disabled (no crash, no API calls).
 *
 * @property apiKey TMDB API v3 key (read access token). Blank = disabled.
 * @property language ISO 639-1 language code for results (default: "de")
 * @property region ISO 3166-1 region code for results (optional)
 */
data class TmdbConfig(
    val apiKey: String,
    val language: String = "de",
    val region: String? = null,
) {
    /**
     * Whether TMDB resolver is enabled.
     * Disabled when apiKey is blank.
     */
    val isEnabled: Boolean
        get() = apiKey.isNotBlank()

    companion object {
        /**
         * Disabled configuration (no API key).
         * Resolver will pass-through unchanged.
         */
        val DISABLED = TmdbConfig(apiKey = "", language = "de", region = null)
    }
}

/**
 * Provider for [TmdbConfig].
 *
 * Implementations should retrieve the API key from secure storage
 * (BuildConfig, encrypted preferences, etc.).
 */
interface TmdbConfigProvider {
    /**
     * Get current TMDB configuration.
     * Returns [TmdbConfig.DISABLED] if no API key is available.
     */
    fun getConfig(): TmdbConfig
}

/**
 * Default provider that reads TMDB API key from BuildConfig.
 *
 * The API key should be set via gradle.properties or local.properties:
 * ```
 * TMDB_API_KEY=your_v3_api_key
 * ```
 *
 * And configured in build.gradle.kts:
 * ```kotlin
 * buildConfigField("String", "TMDB_API_KEY", "\"${findProperty("TMDB_API_KEY") ?: ""}\"")
 * ```
 *
 * If no key is provided, returns [TmdbConfig.DISABLED].
 */
@Singleton
class DefaultTmdbConfigProvider
    @Inject
    constructor() : TmdbConfigProvider {
        override fun getConfig(): TmdbConfig {
            // TODO: Read from BuildConfig.TMDB_API_KEY when configured
            // For now, return disabled config until API key is configured
            return TmdbConfig.DISABLED
        }
    }
