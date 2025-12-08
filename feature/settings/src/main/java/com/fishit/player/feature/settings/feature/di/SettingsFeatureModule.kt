package com.fishit.player.feature.settings.feature.di

import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.feature.settings.feature.SettingsScreenFeatureProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Hilt module for Settings screen feature providers.
 *
 * Binds the Settings screen [FeatureProvider] into the multibinding set
 * that powers [AppFeatureRegistry].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsFeatureModule {

    @Binds
    @IntoSet
    abstract fun bindSettingsScreenFeature(
        impl: SettingsScreenFeatureProvider
    ): FeatureProvider
}
