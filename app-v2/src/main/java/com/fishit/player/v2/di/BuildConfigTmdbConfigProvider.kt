package com.fishit.player.v2.di

import com.fishit.player.core.metadata.tmdb.TmdbConfig
import com.fishit.player.core.metadata.tmdb.TmdbConfigProvider
import com.fishit.player.v2.BuildConfig
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BuildConfig-based TmdbConfigProvider.
 *
 * Reads TMDB API key from [BuildConfig.TMDB_API_KEY] (set via gradle.properties or environment).
 *
 * **API Key Configuration:**
 * - Set `TMDB_API_KEY` environment variable, OR
 * - Add `TMDB_API_KEY=your_v3_api_key` to gradle.properties or local.properties
 *
 * **Security:**
 * - API key is NEVER logged (see LOGGING_CONTRACT_V2.md)
 * - BuildConfig is not included in version control
 *
 * **Contract:** TMDB_ENRICHMENT_CONTRACT.md T-12
 * - If apiKey is blank → resolver is disabled (no crash, no API calls)
 */
@Singleton
class BuildConfigTmdbConfigProvider @Inject constructor() : TmdbConfigProvider {

    override fun getConfig(): TmdbConfig {
        val apiKey = BuildConfig.TMDB_API_KEY

        // Return disabled config if API key is not configured
        if (apiKey.isBlank()) {
            return TmdbConfig.DISABLED
        }

        // Use device locale for language preference
        val language = Locale.getDefault().language.ifBlank { "de" }

        return TmdbConfig(
            apiKey = apiKey,
            language = language,
            region = null,
        )
    }
}
