package com.fishit.player.pipeline.telegram.feature.di

import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.pipeline.telegram.feature.TelegramFullHistoryFeatureProvider
import com.fishit.player.pipeline.telegram.feature.TelegramLazyThumbnailsFeatureProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Hilt module for Telegram pipeline feature providers.
 *
 * Binds all Telegram-specific [FeatureProvider] implementations into the
 * multibinding set that powers [AppFeatureRegistry].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelegramFeatureModule {

    @Binds
    @IntoSet
    abstract fun bindFullHistoryFeature(
        impl: TelegramFullHistoryFeatureProvider
    ): FeatureProvider

    @Binds
    @IntoSet
    abstract fun bindLazyThumbnailsFeature(
        impl: TelegramLazyThumbnailsFeatureProvider
    ): FeatureProvider
}
