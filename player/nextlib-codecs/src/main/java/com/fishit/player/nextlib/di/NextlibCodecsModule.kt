package com.fishit.player.nextlib.di

import com.fishit.player.nextlib.DefaultNextlibCodecConfigurator
import com.fishit.player.nextlib.NextlibCodecConfigurator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for NextLib codec integration.
 *
 * Provides [NextlibCodecConfigurator] as a singleton for consistent
 * renderer factory creation across player sessions.
 */
@Module
@InstallIn(SingletonComponent::class)
object NextlibCodecsModule {
    
    /**
     * Provides the NextLib codec configurator.
     *
     * Returns [DefaultNextlibCodecConfigurator] which uses NextLib's
     * FFmpeg-based renderers for extended codec support.
     */
    @Provides
    @Singleton
    fun provideNextlibCodecConfigurator(): NextlibCodecConfigurator {
        return DefaultNextlibCodecConfigurator()
    }
}
