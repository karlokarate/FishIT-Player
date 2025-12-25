package com.fishit.player.infra.transport.telegram.di

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramAuthClient
import com.fishit.player.infra.transport.telegram.TelegramClient
import com.fishit.player.infra.transport.telegram.TelegramFileClient
import com.fishit.player.infra.transport.telegram.TelegramHistoryClient
import com.fishit.player.infra.transport.telegram.TelegramSessionConfig
import com.fishit.player.infra.transport.telegram.TelegramThumbFetcher
import com.fishit.player.infra.transport.telegram.TelegramTransportClient
import com.fishit.player.infra.transport.telegram.internal.DefaultTelegramClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.g000sha256.tdl.TdlClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module for Telegram transport layer.
 *
 * Provides transport clients for use by higher layers (pipeline, playback, imaging).
 *
 * **v2 Architecture:**
 * - Accepts TdlClient directly (not TdlibClientProvider which is v1 legacy)
 * - TdlClient must be provided by the app module (requires Android Context for initialization)
 * - App module handles TDLib parameter setup, logging installation, and lifecycle
 *
 * **Provided Interfaces:**
 * - [TelegramTransportClient] - Main transport client (auth, chats, messages, files)
 * - [TelegramFileClient] - File download operations with priority queue
 * - [TelegramThumbFetcher] - Thumbnail fetching for Coil integration
 */
@Module
@InstallIn(SingletonComponent::class)
object TelegramTransportModule {
    private const val TAG = "TelegramTransportModule"

    private const val TELEGRAM_AUTH_SCOPE = "TelegramAuthScope"
    private const val TELEGRAM_FILE_SCOPE = "TelegramFileScope"

    /**
     * Provides a dedicated coroutine scope for Telegram auth operations.
     */
    @Provides
    @Singleton
    @Named(TELEGRAM_AUTH_SCOPE)
    fun provideTelegramAuthScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Provides a dedicated coroutine scope for Telegram file operations.
     */
    @Provides
    @Singleton
    @Named(TELEGRAM_FILE_SCOPE)
    fun provideTelegramFileScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Provides the singleton Telegram client facade.
     *
     * The TdlClient is expected to be provided by the app module
     * via a separate @Provides method (created after TDLib parameter setup).
     */
    @Provides
    @Singleton
    fun provideTelegramClient(
        tdlClient: TdlClient,
        sessionConfig: TelegramSessionConfig,
        @Named(TELEGRAM_AUTH_SCOPE) authScope: CoroutineScope,
        @Named(TELEGRAM_FILE_SCOPE) fileScope: CoroutineScope,
    ): TelegramClient {
        UnifiedLog.d(TAG) { "Initializing Telegram transport client" }
        return DefaultTelegramClient(tdlClient, sessionConfig, authScope, fileScope)
    }

    @Provides
    @Singleton
    fun provideTelegramAuthClient(client: TelegramClient): TelegramAuthClient = client

    @Provides
    @Singleton
    fun provideTelegramHistoryClient(client: TelegramClient): TelegramHistoryClient = client

    @Provides
    @Singleton
    fun provideTelegramFileClient(client: TelegramClient): TelegramFileClient = client

    @Provides
    @Singleton
    fun provideTelegramThumbFetcher(client: TelegramClient): TelegramThumbFetcher = client

    @Provides
    @Singleton
    fun provideTelegramTransportClient(client: TelegramClient): TelegramTransportClient = client
}
