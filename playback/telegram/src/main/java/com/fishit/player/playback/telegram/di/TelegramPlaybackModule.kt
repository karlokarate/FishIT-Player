package com.fishit.player.playback.telegram.di

import com.fishit.player.playback.domain.PlaybackSourceFactory
import com.fishit.player.playback.telegram.TelegramPlaybackSourceFactoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Hilt module for Telegram playback components.
 *
 * ⚠️ **CURRENTLY DISABLED** - Uncomment @Module when TdlibClientProvider is implemented.
 *
 * Provides:
 * - [TelegramPlaybackSourceFactoryImpl] bound into the set of [PlaybackSourceFactory]
 *
 * **Usage:**
 * The [PlaybackSourceFactory] set is injected into [PlaybackSourceResolver]
 * which uses it to resolve playback sources based on [SourceType].
 *
 * **Dependencies:**
 * - [TelegramTransportClient] from `:infra:transport-telegram`
 *   (requires TdlibClientProvider which is not yet implemented in v2)
 *
 * **Activation Steps:**
 * When TdlibClientProvider is implemented:
 * 1. Create TdlibClientProvider implementation in app-v2
 * 2. Uncomment @Module annotation below
 * 3. Telegram sources will then use TelegramPlaybackSourceFactoryImpl
 *
 * **Fallback Behavior:**
 * While disabled, PlaybackSourceResolver uses fallback resolution:
 * - If context has HTTP URI → uses that directly
 * - Otherwise → uses test stream (Big Buck Bunny)
 */
// @Module  // <-- Uncomment when TdlibClientProvider is available
// @InstallIn(SingletonComponent::class)  // <-- Uncomment when TdlibClientProvider is available
abstract class TelegramPlaybackModule {

    /**
     * Binds the Telegram factory into the set of PlaybackSourceFactory.
     *
     * The @IntoSet annotation allows multiple factories to be collected
     * and injected as Set<PlaybackSourceFactory>.
     */
    // @Binds  // <-- Uncomment when TdlibClientProvider is available
    // @IntoSet
    // @Singleton
    abstract fun bindTelegramPlaybackSourceFactory(
        impl: TelegramPlaybackSourceFactoryImpl
    ): PlaybackSourceFactory
}
