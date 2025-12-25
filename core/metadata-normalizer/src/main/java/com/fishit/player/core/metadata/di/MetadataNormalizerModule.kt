package com.fishit.player.core.metadata.di

import com.fishit.player.core.metadata.MediaMetadataNormalizer
import com.fishit.player.core.metadata.RegexMediaMetadataNormalizer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing MediaMetadataNormalizer bindings.
 *
 * Provides the RegexMediaMetadataNormalizer as the default implementation.
 * This normalizer:
 * - Uses SceneNameParser to extract metadata from filenames
 * - Cleans titles by removing technical tags
 * - Extracts year, season, episode from scene-style naming
 * - Is deterministic: same input → same output
 *
 * For testing or special scenarios, you can override this binding.
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
}
