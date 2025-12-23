package com.fishit.player.infra.transport.telegram.di

import com.fishit.player.infra.transport.telegram.DefaultTelegramTransportClient
import com.fishit.player.infra.transport.telegram.TelegramFileClient
import com.fishit.player.infra.transport.telegram.TelegramThumbFetcher
import com.fishit.player.infra.transport.telegram.TelegramTransportClient
import com.fishit.player.infra.transport.telegram.file.TelegramFileDownloadManager
import com.fishit.player.infra.transport.telegram.imaging.TelegramThumbFetcherImpl
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

    private const val TELEGRAM_FILE_SCOPE = "TelegramFileScope"

    /**
     * Provides a dedicated coroutine scope for Telegram file operations.
     */
    @Provides
    @Singleton
    @Named(TELEGRAM_FILE_SCOPE)
    fun provideTelegramFileScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    /**
     * Provides the TelegramFileClient for file download operations.
     *
     * Features:
     * - Priority-based download queue (streaming priority=32)
     * - Bounded concurrency (max 4 concurrent downloads)
     * - RemoteId resolution for stale file recovery
     * - Storage statistics and optimization
     */
    @Provides
    @Singleton
    fun provideTelegramFileClient(
        tdlClient: TdlClient,
        @Named(TELEGRAM_FILE_SCOPE) scope: CoroutineScope
    ): TelegramFileClient {
        return TelegramFileDownloadManager(tdlClient, scope)
    }

    /**
     * Provides the TelegramThumbFetcher for thumbnail loading.
     *
     * Features:
     * - RemoteId-first design (no fileId stored)
     * - Bounded failed cache (prevents retry spam)
     * - Prefetch support for scroll-ahead
     */
    @Provides
    @Singleton
    fun provideTelegramThumbFetcher(
        fileClient: TelegramFileClient
    ): TelegramThumbFetcher {
        return TelegramThumbFetcherImpl(fileClient)
    }
}
