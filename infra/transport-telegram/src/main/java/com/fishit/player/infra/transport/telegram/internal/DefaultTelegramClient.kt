package com.fishit.player.infra.transport.telegram.internal

import com.fishit.player.infra.transport.telegram.TelegramClient
import com.fishit.player.infra.transport.telegram.TelegramSessionConfig
import com.fishit.player.infra.transport.telegram.TgFileUpdate
import com.fishit.player.infra.transport.telegram.TgStorageStats
import com.fishit.player.infra.transport.telegram.TgThumbnailRef
import com.fishit.player.infra.transport.telegram.api.TdlibAuthState
import com.fishit.player.infra.transport.telegram.api.TelegramConnectionState
import com.fishit.player.infra.transport.telegram.api.TgChat
import com.fishit.player.infra.transport.telegram.api.TgFile
import com.fishit.player.infra.transport.telegram.api.TgMessage
import com.fishit.player.infra.transport.telegram.auth.TdlibAuthSession
import com.fishit.player.infra.transport.telegram.chat.TelegramChatBrowser
import com.fishit.player.infra.transport.telegram.file.TelegramFileDownloadManager
import com.fishit.player.infra.transport.telegram.imaging.TelegramThumbFetcherImpl
import dev.g000sha256.tdl.TdlClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Internal unified Telegram client that owns TDLib state and backs all typed interfaces.
 *
 * This is the single source of truth for Telegram transport. It composes existing concrete
 * implementations and exposes them through typed interfaces and the compatibility
 * [TelegramTransportClient] facade.
 *
 * **Architecture:**
 * - Single TdlClient instance shared across all operations
 * - Composes: TdlibAuthSession, TelegramChatBrowser, TelegramFileDownloadManager,
 * TelegramThumbFetcherImpl
 * - Implements all typed interfaces via delegation
 *
 * **Why unified?**
 * - Avoids multiple TdlClient wrappers competing for resources
 * - Single point of connection state management
 * - Consistent error handling across all operations
 *
 * **Visibility:** This class is `internal` - consumers MUST use the typed interfaces (
 * [TelegramAuthClient], [TelegramHistoryClient], [TelegramFileClient], [TelegramThumbFetcher]) or
 * the unified [TelegramClient] interface.
 */
internal class DefaultTelegramClient(
        tdlClient: TdlClient,
        sessionConfig: TelegramSessionConfig,
        authScope: CoroutineScope,
        fileScope: CoroutineScope,
) : TelegramClient {

    // Composed implementations
    private val authSession = TdlibAuthSession(tdlClient, sessionConfig, authScope)
    private val chatBrowser = TelegramChatBrowser(tdlClient)
    private val fileDownloadManager = TelegramFileDownloadManager(tdlClient, fileScope)
    private val thumbFetcher = TelegramThumbFetcherImpl(fileDownloadManager)

    // Connection state (internal, exposed via TelegramTransportClient if needed)
    private val _connectionState =
            MutableStateFlow<TelegramConnectionState>(TelegramConnectionState.Disconnected)
    val connectionState: Flow<TelegramConnectionState> = _connectionState.asStateFlow()

    // ========== TelegramAuthClient ==========

    override val authState: Flow<TdlibAuthState> = authSession.authState

    override suspend fun ensureAuthorized() {
        _connectionState.value = TelegramConnectionState.Connecting
        try {
            authSession.ensureAuthorized()
            _connectionState.value = TelegramConnectionState.Connected
        } catch (e: Exception) {
            _connectionState.value =
                    TelegramConnectionState.Error(e.message ?: "Authorization failed")
            throw e
        }
    }

    override suspend fun isAuthorized(): Boolean = authSession.isAuthorized()

    override suspend fun sendPhoneNumber(phoneNumber: String) {
        authSession.sendPhoneNumber(phoneNumber)
    }

    override suspend fun sendCode(code: String) {
        authSession.sendCode(code)
    }

    override suspend fun sendPassword(password: String) {
        authSession.sendPassword(password)
    }

    override suspend fun logout() {
        authSession.logout()
    }

    // ========== TelegramHistoryClient ==========

    override val messageUpdates: Flow<TgMessage> = chatBrowser.messageUpdates

    override suspend fun getChats(limit: Int): List<TgChat> = chatBrowser.getChats(limit)

    override suspend fun getChat(chatId: Long): TgChat? = chatBrowser.getChat(chatId)

    override suspend fun fetchMessages(
            chatId: Long,
            limit: Int,
            fromMessageId: Long,
            offset: Int,
    ): List<TgMessage> = chatBrowser.fetchMessages(chatId, limit, fromMessageId, offset)

    override suspend fun loadAllMessages(
            chatId: Long,
            pageSize: Int,
            maxMessages: Int,
            onProgress: ((loaded: Int) -> Unit)?,
    ): List<TgMessage> = chatBrowser.loadAllMessages(chatId, pageSize, maxMessages, onProgress)

    override suspend fun searchMessages(
            chatId: Long,
            query: String,
            limit: Int,
    ): List<TgMessage> = chatBrowser.searchMessages(chatId, query, limit)

    // ========== TelegramFileClient ==========

    override val fileUpdates: Flow<TgFileUpdate> = fileDownloadManager.fileUpdates

    override suspend fun startDownload(
            fileId: Int,
            priority: Int,
            offset: Long,
            limit: Long,
    ) {
        fileDownloadManager.startDownload(fileId, priority, offset, limit)
    }

    override suspend fun cancelDownload(fileId: Int, deleteLocalCopy: Boolean) {
        fileDownloadManager.cancelDownload(fileId, deleteLocalCopy)
    }

    override suspend fun getFile(fileId: Int): TgFile? = fileDownloadManager.getFile(fileId)

    override suspend fun resolveRemoteId(remoteId: String): TgFile? =
            fileDownloadManager.resolveRemoteId(remoteId)

    override suspend fun getDownloadedPrefixSize(fileId: Int): Long =
            fileDownloadManager.getDownloadedPrefixSize(fileId)

    override suspend fun getStorageStats(): TgStorageStats = fileDownloadManager.getStorageStats()

    override suspend fun optimizeStorage(maxSizeBytes: Long, maxAgeDays: Int): Long =
            fileDownloadManager.optimizeStorage(maxSizeBytes, maxAgeDays)

    // ========== TelegramThumbFetcher ==========

    override suspend fun fetchThumbnail(thumbRef: TgThumbnailRef): String? =
            thumbFetcher.fetchThumbnail(thumbRef)

    override suspend fun isCached(thumbRef: TgThumbnailRef): Boolean =
            thumbFetcher.isCached(thumbRef)

    override suspend fun prefetch(thumbRefs: List<TgThumbnailRef>) {
        thumbFetcher.prefetch(thumbRefs)
    }

    override suspend fun clearFailedCache() {
        thumbFetcher.clearFailedCache()
    }
}
