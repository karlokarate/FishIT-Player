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
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for the Telegram proxy OkHttpClient (30s connect, 120s read for file streaming).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TelegramProxyHttpClient

/**
 * Qualifier for the Telegram health-check OkHttpClient (2s connect/read for fast polling).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TelegramHealthHttpClient

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
 * **OkHttpClient Management (follows Xtream pattern):**
 * - [TelegramProxyHttpClient]: Qualified client for proxy API calls (long read timeout for file streaming)
 * - [TelegramHealthHttpClient]: Qualified client for health polling (short timeouts)
 * - Both are DI-managed singletons, enabling Chucker interception and testability
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

    // ── OkHttpClient providers ──────────────────────────────────────────────

    /**
     * Provides OkHttpClient for Telethon proxy API calls.
     *
     * Timeouts are tuned for localhost proxy communication:
     * - connectTimeout: 30s (allows Chaquopy startup time)
     * - readTimeout: 120s (file downloads via /file endpoint can be slow)
     * - writeTimeout: 10s (small JSON payloads only)
     */
    @Provides
    @Singleton
    @TelegramProxyHttpClient
    fun provideProxyOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

    /**
     * Provides lightweight OkHttpClient for health-check polling.
     *
     * Deliberately short timeouts — health checks must respond fast
     * or be treated as "not ready yet".
     */
    @Provides
    @Singleton
    @TelegramHealthHttpClient
    fun provideHealthOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()

    // ── Proxy infrastructure ────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideTelethonProxyLifecycle(
        @ApplicationContext context: Context,
        config: TelegramSessionConfig,
        @TelegramHealthHttpClient healthClient: OkHttpClient,
    ): TelethonProxyLifecycle = TelethonProxyLifecycle(context, config, healthClient)

    @Provides
    @Singleton
    fun provideTelethonProxyClient(
        config: TelegramSessionConfig,
        @TelegramProxyHttpClient client: OkHttpClient,
    ): TelethonProxyClient = TelethonProxyClient(config, client)

    // ── TelegramClient SSOT ─────────────────────────────────────────────────

    /**
     * Provides the SINGLE unified TelegramClient instance (SSOT).
     *
     * All other typed interface providers return THIS SAME INSTANCE.
     */
    @Provides
    @Singleton
    fun provideTelegramClient(
        @ApplicationContext context: Context,
        proxyClient: TelethonProxyClient,
        proxyLifecycle: TelethonProxyLifecycle,
    ): TelegramClient = DefaultTelegramClient(proxyClient, proxyLifecycle, context.cacheDir)

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
