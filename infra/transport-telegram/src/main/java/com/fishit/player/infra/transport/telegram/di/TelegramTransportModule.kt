package com.fishit.player.infra.transport.telegram.di

// ============================================================================
// ARCHITECTURE NOTE:
// Telegram transport now uses the Telethon sidecar proxy via Chaquopy.
// All Telegram API dependencies have been removed.
// See Issue #703 for migration details.
// ============================================================================

import android.content.Context
import com.fishit.player.infra.transport.telegram.TelegramAuthClient
import com.fishit.player.infra.transport.telegram.TelegramClient
import com.fishit.player.infra.transport.telegram.TelegramFileClient
import com.fishit.player.infra.transport.telegram.TelegramHistoryClient
import com.fishit.player.infra.transport.telegram.TelegramRemoteResolver
import com.fishit.player.infra.transport.telegram.TelegramSessionConfig
import com.fishit.player.infra.transport.telegram.TelegramThumbFetcher
import com.fishit.player.infra.transport.telegram.imaging.TelegramThumbDownloader
import com.fishit.player.infra.transport.telegram.internal.DefaultTelegramClient
import com.fishit.player.infra.transport.telegram.internal.TelethonProxyClient
import com.fishit.player.infra.transport.telegram.internal.TelethonProxyLifecycle
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Telegram transport layer — Telethon Proxy Architecture.
 *
 * **SSOT Principle (Single Source of Truth):**
 * - Exactly ONE [TelegramClient] singleton instance owns all Telegram transport capabilities
 * - All typed interfaces resolve to THIS SAME INSTANCE
 * - One proxy lifecycle, one proxy client, one truth
 *
 * **Architecture:**
 * ```
 * Kotlin (OkHttp) → localhost:8089 → Python (Telethon) → Telegram MTProto
 * ```
 *
 * **Provided Interfaces (all backed by single DefaultTelegramClient):**
 * - [TelegramClient] — Unified facade
 * - [TelegramAuthClient] — Authentication operations
 * - [TelegramHistoryClient] — Chat/message browsing
 * - [TelegramFileClient] — File download operations (now HTTP streamed)
 * - [TelegramThumbFetcher] — Thumbnail fetching
 * - [TelegramRemoteResolver] — RemoteId-based media resolution
 */
@Module
@InstallIn(SingletonComponent::class)
object TelegramTransportModule {

    @Provides
    @Singleton
    fun provideTelethonProxyLifecycle(
        @ApplicationContext context: Context,
        config: TelegramSessionConfig,
    ): TelethonProxyLifecycle = TelethonProxyLifecycle(context, config)

    @Provides
    @Singleton
    fun provideTelethonProxyClient(
        config: TelegramSessionConfig,
    ): TelethonProxyClient = TelethonProxyClient(config)

    /**
     * Provides the SINGLE unified TelegramClient instance (SSOT).
     *
     * All other typed interface providers return THIS SAME INSTANCE.
     */
    @Provides
    @Singleton
    fun provideTelegramClient(
        proxyClient: TelethonProxyClient,
        proxyLifecycle: TelethonProxyLifecycle,
    ): TelegramClient = DefaultTelegramClient(proxyClient, proxyLifecycle)

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
    fun provideTelegramRemoteResolver(client: TelegramClient): TelegramRemoteResolver = client

    @Provides
    @Singleton
    fun provideTelegramThumbDownloader(
        remoteResolver: TelegramRemoteResolver,
        fileClient: TelegramFileClient,
    ): TelegramThumbDownloader = TelegramThumbDownloader(remoteResolver, fileClient)
}
