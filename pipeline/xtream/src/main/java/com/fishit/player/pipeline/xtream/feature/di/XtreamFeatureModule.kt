package com.fishit.player.pipeline.xtream.feature.di

import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.pipeline.xtream.feature.XtreamLiveStreamingFeatureProvider
import com.fishit.player.pipeline.xtream.feature.XtreamSeriesMetadataFeatureProvider
import com.fishit.player.pipeline.xtream.feature.XtreamVodPlaybackFeatureProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Hilt module for Xtream pipeline feature providers.
 *
 * Binds all Xtream-specific [FeatureProvider] implementations into the
 * multibinding set that powers [AppFeatureRegistry].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class XtreamFeatureModule {

    @Binds
    @IntoSet
    abstract fun bindLiveStreamingFeature(
        impl: XtreamLiveStreamingFeatureProvider
    ): FeatureProvider

    @Binds
    @IntoSet
    abstract fun bindVodPlaybackFeature(
        impl: XtreamVodPlaybackFeatureProvider
    ): FeatureProvider

    @Binds
    @IntoSet
    abstract fun bindSeriesMetadataFeature(
        impl: XtreamSeriesMetadataFeatureProvider
    ): FeatureProvider
}
