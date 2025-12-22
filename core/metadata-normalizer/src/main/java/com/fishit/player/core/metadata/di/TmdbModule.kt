package com.fishit.player.core.metadata.di

import com.fishit.player.core.metadata.DefaultTmdbMetadataResolver
import com.fishit.player.core.metadata.TmdbMetadataResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module for TMDB enrichment.
 *
 * Per TMDB_ENRICHMENT_CONTRACT.md:
 * - TMDB dependency ONLY in :core:metadata-normalizer
 * - Resolver is Singleton-scoped (caches are bounded, FireTV-safe)
 *
 * **Note:** TmdbConfigProvider binding is provided by app-v2 (BuildConfigTmdbConfigProvider)
 * because the config provider needs access to app-level BuildConfig.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TmdbModule {

    /**
     * Binds the default TMDB metadata resolver.
     *
     * The resolver uses:
     * - app.moviebase:tmdb-api for TMDB API calls
     * - TmdbScoring for deterministic match scoring
     * - TmdbLruCache for bounded in-memory caching
     */
    @Binds
    @Singleton
    abstract fun bindTmdbMetadataResolver(
        impl: DefaultTmdbMetadataResolver,
    ): TmdbMetadataResolver
}
