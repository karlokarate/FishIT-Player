package com.fishit.player.feature.live.feature.di

import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.feature.live.feature.LiveScreenFeatureProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Hilt module for Live TV screen feature providers.
 *
 * Binds the Live screen [FeatureProvider] into the multibinding set
 * that powers [AppFeatureRegistry].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LiveFeatureModule {

    @Binds
    @IntoSet
    abstract fun bindLiveScreenFeature(
        impl: LiveScreenFeatureProvider
    ): FeatureProvider
}
