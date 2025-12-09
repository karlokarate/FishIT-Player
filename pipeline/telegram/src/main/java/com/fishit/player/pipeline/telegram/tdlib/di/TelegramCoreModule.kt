package com.fishit.player.pipeline.telegram.tdlib.di

import com.fishit.player.pipeline.telegram.tdlib.DefaultTelegramClient
import com.fishit.player.pipeline.telegram.tdlib.TdlibClientProvider
import com.fishit.player.pipeline.telegram.tdlib.TelegramClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Telegram core client bindings.
 *
 * Provides:
 * - TelegramClient: Main client interface for TDLib operations
 *
 * **Dependencies (must be provided by app module):**
 * - TdlibClientProvider: Provides TDLib client with Android Context
 *
 * **Architecture:**
 * - Pipeline module defines TdlibClientProvider interface
 * - App module provides implementation with Context
 * - This module wires DefaultTelegramClient using the provider
 */
@Module
@InstallIn(SingletonComponent::class)
object TelegramCoreModule {

    @Provides
    @Singleton
    fun provideTelegramClient(
        clientProvider: TdlibClientProvider,
    ): TelegramClient = DefaultTelegramClient(clientProvider)
}
