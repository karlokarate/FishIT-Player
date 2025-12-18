package com.fishit.player.core.metadata.di

import com.fishit.player.core.metadata.DefaultTmdbMetadataResolver
import com.fishit.player.core.metadata.MediaMetadataNormalizer
import com.fishit.player.core.metadata.RegexMediaMetadataNormalizer
import com.fishit.player.core.metadata.TmdbMetadataResolver
import com.fishit.player.core.metadata.tmdb.TmdbConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing metadata normalizer bindings.
 *
 * Provides:
 * - MediaMetadataNormalizer (regex-based implementation)
 * - TmdbMetadataResolver (TMDB API integration)
 * - TmdbConfig (API key from gradle.properties or environment)
 */
@Module
@InstallIn(SingletonComponent::class)
object MetadataNormalizerModule {
    /**
     * Provides the production MediaMetadataNormalizer implementation.
     *
     * Uses [RegexMediaMetadataNormalizer] which:
     * - Parses scene-style filenames
     * - Cleans titles (removes tags, normalizes whitespace)
     * - Extracts structural metadata (year, season, episode)
     *
     * @return MediaMetadataNormalizer instance
     */
    @Provides
    @Singleton
    fun provideMediaMetadataNormalizer(): MediaMetadataNormalizer = RegexMediaMetadataNormalizer()

    /**
     * Provides TMDB configuration.
     *
     * Per TMDB_ENRICHMENT_CONTRACT.md Section 3.3:
     * - API key MUST come from non-committed source
     * - If apiKey is missing/blank: Resolver returns Disabled result (no crash)
     * - Logging MUST NEVER include apiKey
     *
     * @return TmdbConfig instance
     */
    @Provides
    @Singleton
    fun provideTmdbConfig(): TmdbConfig {
        // Try to get API key from system environment first (for CI/production)
        val apiKey =
            System.getenv("TMDB_API_KEY")
                ?: System.getProperty("tmdb.api.key")
                ?: "" // Empty = disabled (no crash)

        return TmdbConfig(
            apiKey = apiKey,
            language = "en-US",
            region = null,
        )
    }

    /**
     * Provides TMDB metadata resolver.
     *
     * @param config TMDB configuration
     * @return TmdbMetadataResolver instance
     */
    @Provides
    @Singleton
    fun provideTmdbMetadataResolver(config: TmdbConfig): TmdbMetadataResolver = DefaultTmdbMetadataResolver(config)
}
