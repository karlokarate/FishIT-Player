package com.fishit.player.infra.transport.telegram.di

import com.fishit.player.infra.transport.telegram.DefaultTelegramTransportClient
import com.fishit.player.infra.transport.telegram.TdlibClientProvider
import com.fishit.player.infra.transport.telegram.TelegramTransportClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Telegram transport layer.
 *
 * Provides TelegramTransportClient for use by higher layers (pipeline, data).
 *
 * **Note:** TdlibClientProvider must be provided by the app module
 * since it requires Android Context for TDLib initialization.
 */
@Module
@InstallIn(SingletonComponent::class)
object TelegramTransportModule {

    /**
     * Provides the TelegramTransportClient singleton.
     *
     * The TdlibClientProvider is expected to be provided by the app module
     * via a separate @Binds or @Provides method.
     */
    @Provides
    @Singleton
    fun provideTelegramTransportClient(
        clientProvider: TdlibClientProvider
    ): TelegramTransportClient {
        return DefaultTelegramTransportClient(clientProvider)
    }
}
