package com.fishit.player.internal.di

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import com.fishit.player.playback.domain.DataSourceType
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
 * ## Telethon Proxy Architecture
 * Telegram files are now streamed via HTTP from the Telethon localhost proxy.
 * This means Telegram uses [DataSourceType.DEFAULT] (standard HTTP) instead of
 * a custom DataSource. No TELEGRAM_FILE mapping is needed.
 *
 * ## Binding Strategy
 * - [DataSourceType.DEFAULT] → [DefaultDataSource.Factory] (HTTP/file sources, including Telegram)
 */
@Module
@InstallIn(SingletonComponent::class)
object PlayerDataSourceModule {

    @Provides
    @Singleton
    fun provideDataSourceFactories(
        @ApplicationContext context: Context,
    ): Map<DataSourceType, DataSource.Factory> =
        mapOf(
            DataSourceType.DEFAULT to DefaultDataSource.Factory(context),
        )
}
