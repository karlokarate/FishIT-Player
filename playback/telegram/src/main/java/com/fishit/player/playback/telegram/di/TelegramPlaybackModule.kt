package com.fishit.player.playback.telegram.di

import com.fishit.player.infra.transport.telegram.TelegramFileClient
import com.fishit.player.playback.domain.PlaybackSourceFactory
import com.fishit.player.playback.telegram.TelegramFileDataSourceFactory
import com.fishit.player.playback.telegram.TelegramPlaybackSourceFactoryImpl
import com.fishit.player.playback.telegram.config.TelegramFileReadyEnsurer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Hilt module for Telegram playback components.
 *
 * Provides:
 * - [TelegramPlaybackSourceFactoryImpl] bound into the set of [PlaybackSourceFactory]
 * - [TelegramFileReadyEnsurer] for streaming readiness validation
 * - [TelegramFileDataSourceFactory] for Media3 DataSource creation
 *
 * **Usage:** The [PlaybackSourceFactory] set is injected into [PlaybackSourceResolver] which uses
 * it to resolve playback sources based on [SourceType].
 *
 * **Dependencies:**
 * - [TelegramFileClient] from `:infra:transport-telegram`
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelegramPlaybackModule {
    /**
     * Binds the Telegram factory into the set of PlaybackSourceFactory.
     *
     * The @IntoSet annotation allows multiple factories to be collected and injected as
     * Set<PlaybackSourceFactory>.
     */
    @Binds
    @IntoSet
    @Singleton
    abstract fun bindTelegramPlaybackSourceFactory(impl: TelegramPlaybackSourceFactoryImpl): PlaybackSourceFactory

    companion object {
        /**
         * Provides the TelegramFileReadyEnsurer for streaming readiness checks.
         *
         * This component:
         * - Validates MP4 moov atom completeness
         * - Polls download progress until streaming-ready
         * - Used by TelegramFileDataSource
         */
        @Provides
        @Singleton
        fun provideTelegramFileReadyEnsurer(fileClient: TelegramFileClient): TelegramFileReadyEnsurer = TelegramFileReadyEnsurer(fileClient)

        /**
         * Provides the TelegramFileDataSourceFactory for Media3 integration.
         *
         * This factory creates TelegramFileDataSource instances for handling tg:// URIs during
         * playback.
         */
        @Provides
        @Singleton
        fun provideTelegramFileDataSourceFactory(
            fileClient: TelegramFileClient,
            readyEnsurer: TelegramFileReadyEnsurer,
        ): TelegramFileDataSourceFactory = TelegramFileDataSourceFactory(fileClient, readyEnsurer)
    }
}
