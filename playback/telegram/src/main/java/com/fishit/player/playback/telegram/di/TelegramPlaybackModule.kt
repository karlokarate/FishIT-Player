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
 * Provides:
 * - [TelegramPlaybackSourceFactoryImpl] bound into the set of [PlaybackSourceFactory]
 *
 * **Usage:**
 * The [PlaybackSourceFactory] set is injected into [PlaybackSourceResolver]
 * which uses it to resolve playback sources based on [SourceType].
 *
 * **Dependencies:**
 * - [TelegramTransportClient] from `:infra:transport-telegram`
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelegramPlaybackModule {

    /**
     * Binds the Telegram factory into the set of PlaybackSourceFactory.
     *
     * The @IntoSet annotation allows multiple factories to be collected
     * and injected as Set<PlaybackSourceFactory>.
     */
    @Binds
    @IntoSet
    @Singleton
    abstract fun bindTelegramPlaybackSourceFactory(
        impl: TelegramPlaybackSourceFactoryImpl
    ): PlaybackSourceFactory
}
