package com.fishit.player.infra.transport.telegram.di

import com.fishit.player.infra.transport.telegram.DefaultTelegramTransportClient
import com.fishit.player.infra.transport.telegram.TdlibClientProvider
import com.fishit.player.infra.transport.telegram.TdlibClientProviderImpl
import com.fishit.player.infra.transport.telegram.TelegramTransportClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Telegram transport layer.
 *
 * Provides TelegramTransportClient for use by higher layers (pipeline, data).
 */
@Module
@InstallIn(SingletonComponent::class)
object TelegramTransportModule {

    /**
     * Provides the TelegramTransportClient singleton.
     */
    @Provides
    @Singleton
    fun provideTelegramTransportClient(
        clientProvider: TdlibClientProvider
    ): TelegramTransportClient {
        return DefaultTelegramTransportClient(clientProvider)
    }
}

/**
 * Binds module for Telegram transport dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelegramTransportBindsModule {
    
    @Binds
    @Singleton
    abstract fun bindTdlibClientProvider(
        impl: TdlibClientProviderImpl
    ): TdlibClientProvider
}
