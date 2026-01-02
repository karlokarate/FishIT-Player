package com.fishit.player.playback.xtream.di

import com.fishit.player.playback.domain.PlaybackSourceFactory
import com.fishit.player.playback.xtream.DefaultXtreamDataSourceFactoryProvider
import com.fishit.player.playback.xtream.XtreamDataSourceFactoryProvider
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
 * - [XtreamDataSourceFactoryProvider] for creating DataSource factories with per-request headers
 *
 * **Usage:**
 * The [PlaybackSourceFactory] set is injected into [PlaybackSourceResolver]
 * which uses it to resolve playback sources based on [SourceType].
 *
 * **DataSource Provider:**
 * The [XtreamDataSourceFactoryProvider] creates DataSource.Factory instances
 * with per-request configuration (headers, debug mode). This maintains player layer
 * source-agnosticism - the player depends on the provider interface, not the concrete implementation.
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
    abstract fun bindXtreamPlaybackSourceFactory(impl: XtreamPlaybackSourceFactoryImpl): PlaybackSourceFactory

    /**
     * Provides the XtreamDataSourceFactoryProvider for player layer integration.
     *
     * This provider creates DataSource.Factory instances with per-request headers,
     * maintaining source-agnosticism in the player layer.
     */
    @Binds
    @Singleton
    abstract fun bindXtreamDataSourceFactoryProvider(impl: DefaultXtreamDataSourceFactoryProvider): XtreamDataSourceFactoryProvider
}
