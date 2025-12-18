package com.fishit.player.infra.transport.telegram.di

import com.fishit.player.infra.transport.telegram.DefaultTelegramTransportClient
import com.fishit.player.infra.transport.telegram.TelegramTransportClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.g000sha256.tdl.TdlClient
import javax.inject.Singleton

/**
 * Hilt module for Telegram transport layer.
 *
 * Provides TelegramTransportClient for use by higher layers (pipeline, data).
 *
 * **v2 Architecture:**
 * - Accepts TdlClient directly (not TdlibClientProvider which is v1 legacy)
 * - TdlClient must be provided by the app module (requires Android Context for initialization)
 * - App module handles TDLib parameter setup, logging installation, and lifecycle
 */
@Module
@InstallIn(SingletonComponent::class)
object TelegramTransportModule {

    /**
     * Provides the TelegramTransportClient singleton.
     *
     * The TdlClient is expected to be provided by the app module
     * via a separate @Provides method (created after TDLib parameter setup).
     */
    @Provides
    @Singleton
    fun provideTelegramTransportClient(
        tdlClient: TdlClient
    ): TelegramTransportClient {
        return DefaultTelegramTransportClient(tdlClient)
    }
}
