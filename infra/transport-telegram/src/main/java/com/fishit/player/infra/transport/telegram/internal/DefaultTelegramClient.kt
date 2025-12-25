package com.fishit.player.infra.transport.telegram.internal

import com.fishit.player.infra.transport.telegram.TelegramClient
import com.fishit.player.infra.transport.telegram.TelegramSessionConfig
import com.fishit.player.infra.transport.telegram.TgFileUpdate
import com.fishit.player.infra.transport.telegram.TgStorageStats
import com.fishit.player.infra.transport.telegram.TgThumbnailRef
import com.fishit.player.infra.transport.telegram.api.TdlibAuthState
import com.fishit.player.infra.transport.telegram.api.TelegramConnectionState
import com.fishit.player.infra.transport.telegram.api.TelegramFileException
import com.fishit.player.infra.transport.telegram.api.TgChat
import com.fishit.player.infra.transport.telegram.api.TgContent
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
import kotlinx.coroutines.flow.filter

/**
 * Internal Telegram client that owns TDLib state and backs all typed interfaces.
 *
 * This is the single source of truth for Telegram transport. It composes existing
 * concrete implementations and exposes them through typed interfaces and the
 * compatibility [TelegramTransportClient] facade.
 */
class DefaultTelegramClient(
    tdlClient: TdlClient,
    sessionConfig: TelegramSessionConfig,
    authScope: CoroutineScope,
    fileScope: CoroutineScope,
) : TelegramClient {

    private val authSession = TdlibAuthSession(tdlClient, sessionConfig, authScope)
    private val chatBrowser = TelegramChatBrowser(tdlClient)
    private val fileDownloadManager = TelegramFileDownloadManager(tdlClient, fileScope)
    private val thumbFetcher = TelegramThumbFetcherImpl(fileDownloadManager)

    private val _connectionState =
        MutableStateFlow<TelegramConnectionState>(TelegramConnectionState.Disconnected)
    override val connectionState: Flow<TelegramConnectionState> = _connectionState.asStateFlow()

    override val authState: Flow<TdlibAuthState> = authSession.authState

    override val messageUpdates: Flow<TgMessage> = chatBrowser.messageUpdates

    override val mediaUpdates: Flow<TgMessage> =
        messageUpdates.filter { message ->
            val content = message.content
            content != null && content !is TgContent.Text
        }

    override val fileUpdates: Flow<TgFileUpdate> = fileDownloadManager.fileUpdates

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

    override suspend fun getChats(limit: Int): List<TgChat> = chatBrowser.getChats(limit)

    override suspend fun getChat(chatId: Long): TgChat? = chatBrowser.getChat(chatId)

    override suspend fun fetchMessages(
        chatId: Long,
        limit: Int,
        fromMessageId: Long,
        offset: Int,
    ): List<TgMessage> = chatBrowser.fetchMessages(chatId, limit, fromMessageId, offset)

    override suspend fun fetchMessages(
        chatId: Long,
        limit: Int,
        offsetMessageId: Long,
    ): List<TgMessage> {
        return if (offsetMessageId == 0L) {
            chatBrowser.fetchMessages(chatId, limit, fromMessageId = 0, offset = 0)
        } else {
            chatBrowser.fetchMessages(chatId, limit, fromMessageId = offsetMessageId, offset = -1)
        }
    }

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

    override suspend fun fetchThumbnail(thumbRef: TgThumbnailRef): String? =
        thumbFetcher.fetchThumbnail(thumbRef)

    override suspend fun isCached(thumbRef: TgThumbnailRef): Boolean = thumbFetcher.isCached(thumbRef)

    override suspend fun prefetch(thumbRefs: List<TgThumbnailRef>) {
        thumbFetcher.prefetch(thumbRefs)
    }

    override suspend fun clearFailedCache() {
        thumbFetcher.clearFailedCache()
    }

    override suspend fun resolveFile(fileId: Int): TgFile {
        return fileDownloadManager.getFile(fileId)
            ?: throw TelegramFileException("File not found for fileId=$fileId")
    }

    override suspend fun resolveFileByRemoteId(remoteId: String): TgFile {
        return fileDownloadManager.resolveRemoteId(remoteId)
            ?: throw TelegramFileException("File not found for remoteId=$remoteId")
    }

    override suspend fun requestFileDownload(fileId: Int, priority: Int): TgFile {
        fileDownloadManager.startDownload(fileId, priority)
        return fileDownloadManager.getFile(fileId)
            ?: throw TelegramFileException("File not found after download request: fileId=$fileId")
    }

    override suspend fun close() {
        _connectionState.value = TelegramConnectionState.Disconnected
    }
}
