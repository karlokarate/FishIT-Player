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
 * In the Telethon proxy architecture, playback uses standard HTTP URLs
 * (http://127.0.0.1:PORT/file?chat=X&id=Y) instead of custom tg:// URIs.
 * This means no custom DataSource is needed — DefaultDataSource handles HTTP.
 *
 * Only provides:
 * - [TelegramPlaybackSourceFactoryImpl] bound into the set of [PlaybackSourceFactory]
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelegramPlaybackModule {

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindTelegramPlaybackSourceFactory(
        impl: TelegramPlaybackSourceFactoryImpl,
    ): PlaybackSourceFactory
}
