package com.fishit.player.playback.xtream.di

import com.fishit.player.playback.domain.PlaybackSourceFactory
import com.fishit.player.playback.xtream.XtreamPlaybackSourceFactoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Hilt module for Xtream playback components.
 *
 * Provides:
 * - [XtreamPlaybackSourceFactoryImpl] bound into the set of [PlaybackSourceFactory]
 *
 * **Usage:**
 * The [PlaybackSourceFactory] set is injected into [PlaybackSourceResolver]
 * which uses it to resolve playback sources based on [SourceType].
 *
 * **Architecture Note:**
 * The factory is stateless - Xtream server configuration is passed via
 * [PlaybackContext.extras] for each playback request.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class XtreamPlaybackModule {

    /**
     * Binds the Xtream factory into the set of PlaybackSourceFactory.
     *
     * The @IntoSet annotation allows multiple factories to be collected
     * and injected as Set<PlaybackSourceFactory>.
     */
    @Binds
    @IntoSet
    @Singleton
    abstract fun bindXtreamPlaybackSourceFactory(
        impl: XtreamPlaybackSourceFactoryImpl
    ): PlaybackSourceFactory
}
