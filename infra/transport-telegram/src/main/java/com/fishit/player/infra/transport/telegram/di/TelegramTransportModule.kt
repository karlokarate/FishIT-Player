package com.fishit.player.infra.transport.telegram.di

// ============================================================================
// ARCHITECTURE NOTE:
// Telegram transport is SSOT-finalized (December 2025).
// Do NOT add additional Telegram transport implementations.
// Do NOT create parallel TdlClient instances.
// See docs/v2/architecture/TELEGRAM_TRANSPORT_SSOT.md
// ============================================================================

import com.fishit.player.infra.transport.telegram.TelegramAuthClient
import com.fishit.player.infra.transport.telegram.TelegramClient
import com.fishit.player.infra.transport.telegram.TelegramFileClient
import com.fishit.player.infra.transport.telegram.TelegramHistoryClient
import com.fishit.player.infra.transport.telegram.TelegramRemoteResolver
import com.fishit.player.infra.transport.telegram.TelegramSessionConfig
import com.fishit.player.infra.transport.telegram.TelegramThumbFetcher
import com.fishit.player.infra.transport.telegram.imaging.TelegramThumbDownloader
import com.fishit.player.infra.transport.telegram.internal.DefaultTelegramClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.g000sha256.tdl.TdlClient
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hilt module for Telegram transport layer - SSOT Implementation.
 *
 * **SSOT Principle (Single Source of Truth):**
 * - Exactly ONE [TelegramClient] singleton instance owns all Telegram transport capabilities
 * - All typed interfaces resolve to THIS SAME INSTANCE
 * - One TdlClient, one orchestrating wrapper, one truth
 *
 * **v2 Architecture:**
 * - Accepts TdlClient directly (not TdlibClientProvider which is v1 legacy)
 * - TdlClient must be provided by the app module (requires Android Context for initialization)
 * - App module handles TDLib parameter setup, logging installation, and lifecycle
 *
 * **Provided Interfaces (all backed by single TelegramClient instance):**
 * - [TelegramClient]
 * - Unified facade (for consumers that need multiple capabilities)
 * - [TelegramAuthClient]
 * - Authentication operations
 * - [TelegramHistoryClient]
 * - Chat/message browsing operations
 * - [TelegramFileClient]
 * - File download operations with priority queue
 * - [TelegramThumbFetcher]
 * - Thumbnail fetching for Coil integration
 * - [TelegramRemoteResolver]
 * - RemoteId-based media resolution (SSOT for file references)
 *
 * **Why SSOT matters:**
 * - Shared state/retry/caching logic in one place
 * - Clear ownership ("who is responsible?")
 * - No duplicate Flows/Listeners
 * - One truth, not four "truths"
 */
@Module
@InstallIn(SingletonComponent::class)
object TelegramTransportModule {

    private const val TELEGRAM_AUTH_SCOPE = "TelegramAuthScope"
    private const val TELEGRAM_FILE_SCOPE = "TelegramFileScope"

    /** Provides a dedicated coroutine scope for Telegram auth operations. */
    @Provides
    @Singleton
    @Named(TELEGRAM_AUTH_SCOPE)
    fun provideTelegramAuthScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Provides a dedicated coroutine scope for Telegram file operations. */
    @Provides
    @Singleton
    @Named(TELEGRAM_FILE_SCOPE)
    fun provideTelegramFileScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Provides the SINGLE unified TelegramClient instance (SSOT).
     *
     * This is the ONE instance that backs ALL typed interfaces. Internal implementation (
     * [DefaultTelegramClient]) composes TdlibAuthSession, TelegramChatBrowser,
     * TelegramFileDownloadManager, and TelegramThumbFetcherImpl.
     *
     * **All other providers in this module return THIS SAME INSTANCE.**
     */
    @Provides
    @Singleton
    fun provideTelegramClient(
            tdlClient: TdlClient,
            sessionConfig: TelegramSessionConfig,
            @Named(TELEGRAM_AUTH_SCOPE) authScope: CoroutineScope,
            @Named(TELEGRAM_FILE_SCOPE) fileScope: CoroutineScope,
    ): TelegramClient {
        return DefaultTelegramClient(
                tdlClient = tdlClient,
                sessionConfig = sessionConfig,
                authScope = authScope,
                fileScope = fileScope,
        )
    }

    /**
     * Provides TelegramAuthClient - resolves to the SAME TelegramClient instance.
     *
     * Consumers should inject this interface when they only need auth capabilities.
     */
    @Provides
    @Singleton
    fun provideTelegramAuthClient(telegramClient: TelegramClient): TelegramAuthClient =
            telegramClient

    /**
     * Provides TelegramHistoryClient - resolves to the SAME TelegramClient instance.
     *
     * Features:
     * - Chat list retrieval with member counts
     * - Paginated message history loading
     * - Full chat loading for catalog building
     * - Message search within chats
     * - Real-time message updates via Flow
     */
    @Provides
    @Singleton
    fun provideTelegramHistoryClient(telegramClient: TelegramClient): TelegramHistoryClient =
            telegramClient

    /**
     * Provides TelegramFileClient - resolves to the SAME TelegramClient instance.
     *
     * Features:
     * - Priority-based download queue (streaming priority=32)
     * - Bounded concurrency (max 4 concurrent downloads)
     * - RemoteId resolution for stale file recovery
     * - Storage statistics and optimization
     */
    @Provides
    @Singleton
    fun provideTelegramFileClient(telegramClient: TelegramClient): TelegramFileClient =
            telegramClient

    /**
     * Provides TelegramThumbFetcher - resolves to the SAME TelegramClient instance.
     *
     * Features:
     * - RemoteId-first design (no fileId stored)
     * - Bounded failed cache (prevents retry spam)
     * - Prefetch support for scroll-ahead
     */
    @Provides
    @Singleton
    fun provideTelegramThumbFetcher(telegramClient: TelegramClient): TelegramThumbFetcher =
            telegramClient

    /**
     * Provides TelegramRemoteResolver - resolves to the SAME TelegramClient instance.
     *
     * Features:
     * - Resolves (chatId, messageId) to current TDLib fileIds
     * - Supports media and thumbnail file resolution
     * - Returns metadata (MIME type, duration, dimensions, local paths)
     * - Implements RemoteId-First Architecture (SSOT)
     */
    @Provides
    @Singleton
    fun provideTelegramRemoteResolver(telegramClient: TelegramClient): TelegramRemoteResolver =
            telegramClient

    /**
     * Provides TelegramThumbDownloader for thumbnail upgrade strategy.
     *
     * Features:
     * - Ensures thumbnails are downloaded via TDLib cache (no secondary cache)
     * - Non-blocking: triggers download, returns path when ready
     * - Low priority downloads (background)
     * - Idempotent and app-scope (not UI-scope)
     *
     * Note: TelegramThumbDownloader is intentionally an @Singleton even though it composes
     * resolver + fileClient. It centralizes thumbnail download coordination across multiple
     * UI surfaces, reusing a shared TDLib-backed download queue and avoiding duplicate work,
     * while remaining UI-agnostic (no Context, no ViewModel state).
     */
    @Provides
    @Singleton
    fun provideTelegramThumbDownloader(
        remoteResolver: TelegramRemoteResolver,
        fileClient: TelegramFileClient
    ): TelegramThumbDownloader = TelegramThumbDownloader(remoteResolver, fileClient)
}
