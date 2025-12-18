package com.fishit.player.internal.di

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import com.fishit.player.infra.transport.telegram.TelegramFileClient
import com.fishit.player.infra.transport.telegram.TelegramTransportClient
import com.fishit.player.playback.domain.DataSourceType
import com.fishit.player.playback.telegram.TelegramFileDataSourceFactory
import com.fishit.player.playback.telegram.config.TelegramFileReadyEnsurer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Singleton

/**
 * Hilt module providing DataSource.Factory instances for different source types.
 *
 * The player uses this map to select the appropriate DataSource.Factory
 * based on the [DataSourceType] returned by the [PlaybackSourceResolver].
 *
 * ## Binding Strategy
 * - [DataSourceType.TELEGRAM_FILE] → [TelegramFileDataSourceFactory] (zero-copy TDLib streaming)
 * - [DataSourceType.DEFAULT] → [DefaultDataSource.Factory] (standard HTTP/file sources)
 *
 * ## Layer Boundaries
 * This module bridges playback-layer factories into the player-internal layer.
 * It follows AGENTS.md Section 4.5 by keeping transport dependencies in their proper modules.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlayerDataSourceModule {

    /**
     * Provides the complete map of DataSource factories keyed by [DataSourceType].
     *
     * The player's [InternalPlayerSession] uses this map to configure ExoPlayer
     * with the appropriate MediaSourceFactory for each source type.
     */
    @Provides
    @Singleton
    fun provideDataSourceFactories(
        @ApplicationContext context: Context,
        telegramFactory: TelegramFileDataSourceFactory
    ): Map<DataSourceType, DataSource.Factory> {
        return mapOf(
            DataSourceType.TELEGRAM_FILE to telegramFactory,
            DataSourceType.DEFAULT to DefaultDataSource.Factory(context)
        )
    }

    /**
     * Provides the Telegram file DataSource factory.
     *
     * Uses [TelegramTransportClient] for zero-copy file streaming via TDLib.
     */
    @Provides
    @Singleton
    fun provideTelegramFileDataSourceFactory(
        transportClient: TelegramTransportClient,
        fileClient: TelegramFileClient,
        readyEnsurer: TelegramFileReadyEnsurer,
    ): TelegramFileDataSourceFactory {
        return TelegramFileDataSourceFactory(transportClient, fileClient, readyEnsurer)
    }
}
