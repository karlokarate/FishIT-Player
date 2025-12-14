package com.fishit.player.infra.transport.telegram.di

import com.fishit.player.core.imaging.fetcher.TelegramThumbFetcher
import com.fishit.player.infra.transport.telegram.TelegramTransportClient
import com.fishit.player.infra.transport.telegram.imaging.coil.CoilTelegramThumbFetcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Hilt module for Telegram imaging integration with Coil.
 *
 * **Purpose:**
 * - Provides TelegramThumbFetcher.Factory for core:ui-imaging
 * - Bridges Telegram transport layer with Coil image loading
 * - Enables Telegram thumbnail display in UI
 *
 * **Architecture (Phase B2):**
 * - Migrated from app-v2 to infra/transport-telegram
 * - Implements core:ui-imaging TelegramThumbFetcher.Factory
 * - Uses lazy Provider to avoid circular dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object TelegramImagingModule {
    /**
     * Provides the TelegramThumbFetcher.Factory implementation.
     *
     * Uses lazy Provider<TelegramTransportClient> to break potential circular
     * dependency between transport and imaging modules.
     *
     * Returns null if TelegramTransportClient is not available (graceful degradation).
     */
    @Provides
    @Singleton
    fun provideTelegramThumbFetcherFactory(
        telegramClientProvider: Provider<TelegramTransportClient>
    ): TelegramThumbFetcher.Factory? =
        runCatching {
            CoilTelegramThumbFetcher.Factory(telegramClientProvider.get())
        }.getOrNull()
}
