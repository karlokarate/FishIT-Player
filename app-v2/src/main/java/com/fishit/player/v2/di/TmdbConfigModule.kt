package com.fishit.player.v2.di

import com.fishit.player.core.metadata.tmdb.TmdbConfigProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that overrides TmdbConfigProvider from core/metadata-normalizer.
 *
 * This module provides the BuildConfig-based implementation that reads
 * TMDB_API_KEY from the app's BuildConfig.
 *
 * **Why override?**
 * - core/metadata-normalizer has no access to app-v2's BuildConfig
 * - The DefaultTmdbConfigProvider in core returns DISABLED always
 * - This module provides the real implementation with API key access
 *
 * **Hilt Priority:**
 * Modules in the app module take precedence over library modules.
 *
 * @see BuildConfigTmdbConfigProvider
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TmdbConfigModule {

    @Binds
    @Singleton
    abstract fun bindTmdbConfigProvider(
        impl: BuildConfigTmdbConfigProvider,
    ): TmdbConfigProvider
}
