package com.fishit.player.internal.di

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import com.fishit.player.playback.domain.DataSourceType
import com.fishit.player.playback.telegram.TelegramFileDataSourceFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing DataSource.Factory instances for different source types.
 *
 * The player uses this map to select the appropriate DataSource.Factory based on the
 * [DataSourceType] returned by the [PlaybackSourceResolver].
 *
 * ## Binding Strategy
 * - [DataSourceType.TELEGRAM_FILE] → [TelegramFileDataSourceFactory] (zero-copy TDLib streaming)
 * - [DataSourceType.DEFAULT] → [DefaultDataSource.Factory] (standard HTTP/file sources)
 *
 * ## Layer Boundaries This module bridges playback-layer factories into the player-internal layer.
 * It follows AGENTS.md Section 4.5 by keeping transport dependencies in their proper modules.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlayerDataSourceModule {
    /**
     * Provides the complete map of DataSource factories keyed by [DataSourceType].
     *
     * The player's [InternalPlayerSession] uses this map to configure ExoPlayer with the
     * appropriate MediaSourceFactory for each source type.
     *
     * Note: [TelegramFileDataSourceFactory] is provided by [TelegramPlaybackModule] in
     * :playback:telegram. This module only wires it into the DataSource map.
     */
    @Provides
    @Singleton
    fun provideDataSourceFactories(
        @ApplicationContext context: Context,
        telegramFactory: TelegramFileDataSourceFactory,
    ): Map<DataSourceType, DataSource.Factory> =
        mapOf(
            DataSourceType.TELEGRAM_FILE to telegramFactory,
            DataSourceType.DEFAULT to DefaultDataSource.Factory(context),
        )

    // Note: TelegramFileDataSourceFactory is provided by TelegramPlaybackModule.
    // DO NOT add a duplicate @Provides here - it causes Hilt duplicate binding errors.
}
