package com.fishit.player.pipeline.telegram.capability.di

import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.pipeline.telegram.capability.TelegramFullHistoryCapabilityProvider
import com.fishit.player.pipeline.telegram.capability.TelegramLazyThumbnailsCapabilityProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Hilt module for Telegram pipeline capability providers.
 *
 * Binds all Telegram-specific [FeatureProvider] implementations into the
 * multibinding set that powers [AppFeatureRegistry].
 *
 * Note: These are pipeline **capabilities**, not App Features. They represent
 * technical functionality provided by the Telegram pipeline.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelegramCapabilityModule {
    @Binds
    @IntoSet
    abstract fun bindFullHistoryCapability(impl: TelegramFullHistoryCapabilityProvider): FeatureProvider

    @Binds
    @IntoSet
    abstract fun bindLazyThumbnailsCapability(impl: TelegramLazyThumbnailsCapabilityProvider): FeatureProvider
}
