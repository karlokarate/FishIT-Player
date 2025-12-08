package com.fishit.player.feature.telegram.media.feature.di

import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.feature.telegram.media.feature.TelegramScreenFeatureProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Hilt module for Telegram Media screen feature providers.
 *
 * Binds the Telegram screen [FeatureProvider] into the multibinding set
 * that powers [AppFeatureRegistry].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelegramMediaFeatureModule {

    @Binds
    @IntoSet
    abstract fun bindTelegramScreenFeature(
        impl: TelegramScreenFeatureProvider
    ): FeatureProvider
}
