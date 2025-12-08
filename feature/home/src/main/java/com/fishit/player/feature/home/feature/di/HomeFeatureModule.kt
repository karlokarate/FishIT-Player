package com.fishit.player.feature.home.feature.di

import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.feature.home.feature.HomeScreenFeatureProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Hilt module for Home screen feature providers.
 *
 * Binds the Home screen [FeatureProvider] into the multibinding set
 * that powers [AppFeatureRegistry].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HomeFeatureModule {

    @Binds
    @IntoSet
    abstract fun bindHomeScreenFeature(
        impl: HomeScreenFeatureProvider
    ): FeatureProvider
}
