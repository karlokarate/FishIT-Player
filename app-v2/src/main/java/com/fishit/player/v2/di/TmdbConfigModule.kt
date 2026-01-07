package com.fishit.player.v2.di

import com.fishit.player.core.metadata.tmdb.TmdbConfig
import com.fishit.player.core.metadata.tmdb.TmdbConfigProvider
import com.fishit.player.v2.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import javax.inject.Singleton

/**
 * Hilt module that provides TmdbConfigProvider from app BuildConfig.
 *
 * This module provides the BuildConfig-based implementation that reads
 * TMDB_API_KEY from the app's BuildConfig.
 *
 * **Why in app-v2?**
 * - core/metadata-normalizer has no access to app-v2's BuildConfig
 * - The DefaultTmdbConfigProvider in core returns DISABLED always
 * - This module provides the real implementation with API key access
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
@Module
@InstallIn(SingletonComponent::class)
object TmdbConfigModule {
    @Provides
    @Singleton
    fun provideTmdbConfigProvider(): TmdbConfigProvider =
        object : TmdbConfigProvider {
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
}
