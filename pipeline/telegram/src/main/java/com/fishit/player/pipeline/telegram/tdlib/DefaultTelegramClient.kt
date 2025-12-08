package com.fishit.player.pipeline.telegram.tdlib

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.pipeline.telegram.mapper.toTelegramMediaItems
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.AuthorizationState
import dev.g000sha256.tdl.dto.AuthorizationStateClosed
import dev.g000sha256.tdl.dto.AuthorizationStateClosing
import dev.g000sha256.tdl.dto.AuthorizationStateLoggingOut
import dev.g000sha256.tdl.dto.AuthorizationStateReady
import dev.g000sha256.tdl.dto.AuthorizationStateWaitCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPassword
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPhoneNumber
import dev.g000sha256.tdl.dto.ChatListMain
import dev.g000sha256.tdl.dto.ChatType
import dev.g000sha256.tdl.dto.ChatTypeBasicGroup
import dev.g000sha256.tdl.dto.ChatTypePrivate
import dev.g000sha256.tdl.dto.ChatTypeSecret
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.LocalFile
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.RemoteFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Default TelegramClient Implementation
 *
 * Wraps g00sha tdlib-coroutines for Telegram media access in v2 architecture.
 *
 * **Naming Convention:**
 * - `TelegramClient` = Interface (analog to `XtreamApiClient`)
 * - `DefaultTelegramClient` = Implementation (analog to `DefaultXtreamApiClient`)
 *
 * **g00sha tdlib-coroutines Integration:** Uses `dev.g000sha256:tdl-coroutines-android:5.0.0` AAR
 * which provides:
 * - `TdlClient` - Main client wrapper with coroutine support
 * - `TdlResult<T>` - Result wrapper (Success/Failure)
 * - `dev.g000sha256.tdl.dto.*` - All TDLib DTOs (Chat, Message, File, etc.)
 *
 * **v1 Component Mapping:**
 * - Adapted from v1 `T_TelegramServiceClient` (singleton, client lifecycle)
 * - Integrated v1 `T_TelegramSession` (auth flow management)
 * - Integrated v1 `T_ChatBrowser` (media message fetching)
 * - Uses v1 patterns but with v2 boundaries (no UI, no player logic)
 *
 * **ARCHITECTURE NOTE:** This pipeline client does NOT take Android Context directly. TDLib
 * initialization requiring Context is handled via [TdlibClientProvider].
 *
 * @param clientProvider Provider for underlying TdlClient (injected via Hilt)
 * @param scope Coroutine scope for background operations
 */
class DefaultTelegramClient(
        private val clientProvider: TdlibClientProvider,
        private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : TelegramClient {

    companion object {
        private const val TAG = "DefaultTelegramClient"
        private const val DEFAULT_RETRIES = 3
        private const val RETRY_DELAY_MS = 500L
    }

    /**
     * Lazy access to the underlying TdlClient. Throws if provider not initialized - callers must
     * ensure [ensureAuthorized] is called first.
     */
    private val tdlClient: TdlClient
        get() = clientProvider.getClient()

    // State flows
    private val _authState = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Idle)
    override val authState: Flow<TelegramAuthState> = _authState.asStateFlow()

    private val _connectionState =
            MutableStateFlow<TelegramConnectionState>(TelegramConnectionState.Disconnected)
    override val connectionState: Flow<TelegramConnectionState> = _connectionState.asStateFlow()

    // ========== Authorization ==========

    override suspend fun ensureAuthorized() {
        UnifiedLog.d(TAG, "ensureAuthorized() - checking auth state")

        // Initialize provider if needed
        if (!clientProvider.isInitialized) {
            _authState.value = TelegramAuthState.Connecting
            clientProvider.initialize()
        }

        // Query current auth state from TDLib
        val authResult = tdlClient.getAuthorizationState()
        when (authResult) {
            is TdlResult.Success -> {
                val state = mapAuthorizationState(authResult.result)
                _authState.value = state

                if (state != TelegramAuthState.Ready) {
                    UnifiedLog.w(TAG, "Not authorized: $state")
                    throw TelegramAuthException("Not authorized: $state")
                }

                _connectionState.value = TelegramConnectionState.Connected
                UnifiedLog.i(TAG, "Authorization confirmed: Ready")
            }
            is TdlResult.Failure -> {
                val error = "Auth check failed: ${authResult.code} - ${authResult.message}"
                _authState.value = TelegramAuthState.Error(error)
                UnifiedLog.e(TAG, error)
                throw TelegramAuthException(error)
            }
        }
    }

    // ========== Chat Operations ==========

    override suspend fun getChats(limit: Int): List<TelegramChatInfo> {
        UnifiedLog.d(TAG, "getChats(limit=$limit)")

        return withRetry("getChats") {
            // Load chat list from TDLib
            val chatsResult = tdlClient.getChats(ChatListMain(), limit)
            val chatIds: LongArray = chatsResult.getOrThrow().chatIds

            // Fetch individual chat details
            val result = mutableListOf<TelegramChatInfo>()
            for (chatId in chatIds) {
                try {
                    val chat = tdlClient.getChat(chatId).getOrThrow()
                    result.add(
                            TelegramChatInfo(
                                    chatId = chat.id,
                                    title = chat.title,
                                    type = mapChatType(chat.type),
                                    photoPath =
                                            chat.photo?.small?.local?.let { local ->
                                                local.path.takeIf { local.isDownloadingCompleted }
                                            }
                            )
                    )
                } catch (e: Exception) {
                    UnifiedLog.w(TAG, "Failed to load chat $chatId: ${e.message}")
                }
            }
            result.toList()
        }
    }

    // ========== Media Message Operations ==========

    override suspend fun fetchMediaMessages(
            chatId: Long,
            limit: Int,
            offsetMessageId: Long
    ): List<TelegramMediaItem> {
        UnifiedLog.d(
                TAG,
                "fetchMediaMessages(chatId=$chatId, limit=$limit, offset=$offsetMessageId)"
        )

        return withRetry("fetchMediaMessages") {
            val messages = loadMessageHistory(chatId, offsetMessageId, limit)
            messages.toTelegramMediaItems().also {
                UnifiedLog.d(TAG, "Fetched ${it.size} media items from chat $chatId")
            }
        }
    }

    override suspend fun fetchAllMediaMessages(
            chatIds: List<Long>,
            limit: Int
    ): List<TelegramMediaItem> {
        UnifiedLog.d(TAG, "fetchAllMediaMessages(chatIds=${chatIds.size}, limit=$limit)")

        return chatIds.flatMap { chatId ->
            try {
                fetchMediaMessages(chatId, limit, 0)
            } catch (e: Exception) {
                UnifiedLog.w(TAG, "Failed to fetch from chat $chatId: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * Load all messages from a chat by paging through the entire history.
     *
     * Ported from v1 T_ChatBrowser.loadAllMessages() with proven patterns:
     * - Proper offset handling (0 for first page, -1 for subsequent to avoid duplicates)
     * - TDLib async loading detection (first call may return only 1 message)
     * - Retry logic for async loads
     * - Safety limits to prevent infinite loops
     * - Batch size optimization
     *
     * Per TDLib documentation (legacy docs/telegram/tdlibsetup.md):
     * - getChatHistory requires special offset handling
     * - First call may return incomplete batch while loading from server
     *
     * @param chatId Chat ID to load all messages from
     * @param pageSize Number of messages per page (default 100, TDLib max)
     * @param maxMessages Maximum total messages as safety limit (default 10000)
     * @return Complete list of all messages from the chat
     */
    suspend fun loadAllMessages(
            chatId: Long,
            pageSize: Int = 100,
            maxMessages: Int = 10000
    ): List<Message> {
        UnifiedLog.d(TAG, "loadAllMessages(chatId=$chatId, pageSize=$pageSize, max=$maxMessages)")

        val allMessages = mutableListOf<Message>()
        var fromMessageId = 0L
        var isFirstPage = true

        while (allMessages.size < maxMessages) {
            // Per legacy pattern:
            // - First page: offset=0
            // - Subsequent pages: offset=-1 to avoid duplicate of the anchor message
            val offset = if (isFirstPage) 0 else -1

            var batch =
                    loadMessageHistory(
                            chatId = chatId,
                            fromMessageId = fromMessageId,
                            limit = pageSize.coerceAtMost(100) // TDLib max is 100
                    )

            // Handle TDLib async loading: first call often returns only 1 message
            // Wait and retry to get the full batch from server (proven pattern from v1)
            if (isFirstPage && batch.size == 1) {
                UnifiedLog.d(
                        TAG,
                        "First batch returned ${batch.size} message, waiting for TDLib async load..."
                )
                delay(500L) // Same as v1
                batch =
                        loadMessageHistory(
                                chatId = chatId,
                                fromMessageId = fromMessageId,
                                limit = pageSize.coerceAtMost(100)
                        )
                UnifiedLog.d(TAG, "After retry: ${batch.size} messages")
            }

            isFirstPage = false

            if (batch.isEmpty()) {
                UnifiedLog.d(TAG, "No more messages, stopping at ${allMessages.size} total")
                break
            }

            allMessages.addAll(batch)
            fromMessageId = batch.last().id

            UnifiedLog.d(TAG, "Progress: ${allMessages.size} messages loaded")

            // Safety check: partial batch indicates end of history
            if (batch.size < pageSize) {
                UnifiedLog.d(
                        TAG,
                        "Received partial batch (${batch.size}), assuming end of history"
                )
                break
            }
        }

        UnifiedLog.d(TAG, "Total messages loaded: ${allMessages.size}")
        return allMessages
    }

    // ========== File Operations ==========

    override suspend fun resolveFileLocation(fileId: Int): TelegramFileLocation {
        UnifiedLog.d(TAG, "resolveFileLocation(fileId=$fileId)")

        return withRetry("resolveFileLocation") {
            val fileResult = tdlClient.getFile(fileId)
            val file = fileResult.getOrThrow()
            mapFileLocation(file)
        }
    }

    override suspend fun resolveFileByRemoteId(remoteId: String): Int {
        UnifiedLog.d(TAG, "resolveFileByRemoteId(remoteId=$remoteId)")

        return withRetry("resolveFileByRemoteId") {
            val fileResult = tdlClient.getRemoteFile(remoteId, null)
            fileResult.getOrThrow().id
        }
    }

    override suspend fun requestFileDownload(fileId: Int, priority: Int): TelegramFileLocation {
        UnifiedLog.d(TAG, "requestFileDownload(fileId=$fileId, priority=$priority)")

        return withRetry("requestFileDownload") {
            // Request download from TDLib (this only starts the download)
            val downloadResult =
                    tdlClient.downloadFile(
                            fileId = fileId,
                            priority = priority,
                            offset = 0,
                            limit = 0, // 0 = entire file
                            synchronous = false
                    )
            val file = downloadResult.getOrThrow()
            mapFileLocation(file)
        }
    }

    override suspend fun requestThumbnailDownload(
            remoteId: String,
            priority: Int
    ): TelegramFileLocation {
        UnifiedLog.d(TAG, "requestThumbnailDownload(remoteId=$remoteId, priority=$priority)")

        return withRetry("requestThumbnailDownload") {
            // Step 1: Resolve remoteId to current session's fileId
            val fileResult = tdlClient.getRemoteFile(remoteId, null)
            val fileId = fileResult.getOrThrow().id

            UnifiedLog.d(TAG, "Resolved remoteId=$remoteId to fileId=$fileId")

            // Step 2: Request download with thumbnail priority (default 8, lower than video 16)
            val downloadResult =
                    tdlClient.downloadFile(
                            fileId = fileId,
                            priority = priority,
                            offset = 0,
                            limit = 0, // Full download for thumbnails
                            synchronous = false
                    )
            val file = downloadResult.getOrThrow()
            mapFileLocation(file)
        }
    }

    // ========== Lifecycle ==========

    override suspend fun close() {
        UnifiedLog.d(TAG, "close()")
        _authState.value = TelegramAuthState.Idle
        _connectionState.value = TelegramConnectionState.Disconnected
        // Note: TdlClient lifecycle is managed by TdlibClientProvider
    }

    // ========== Private Helpers ==========

    /**
     * Load message history with TDLib offset handling.
     *
     * Per TDLib docs:
     * - First page: fromMessageId=0, offset=0
     * - Subsequent pages: fromMessageId=oldest message ID, offset=-1 (to avoid duplicates)
     */
    private suspend fun loadMessageHistory(
            chatId: Long,
            fromMessageId: Long,
            limit: Int
    ): List<Message> {
        val offset = if (fromMessageId == 0L) 0 else -1

        val historyResult =
                tdlClient.getChatHistory(
                        chatId = chatId,
                        fromMessageId = fromMessageId,
                        offset = offset,
                        limit = limit.coerceAtMost(100), // TDLib max is 100
                        onlyLocal = false
                )

        return when (historyResult) {
            is TdlResult.Success -> {
                historyResult.result.messages.filterNotNull()
            }
            is TdlResult.Failure -> {
                throw TelegramFileException(
                        "getChatHistory failed: ${historyResult.code} - ${historyResult.message}"
                )
            }
        }
    }

    /** Retry wrapper with exponential backoff. */
    private suspend fun <T> withRetry(
            operation: String,
            retries: Int = DEFAULT_RETRIES,
            block: suspend () -> T
    ): T {
        var lastError: Exception? = null

        repeat(retries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastError = e
                UnifiedLog.w(
                        TAG,
                        "$operation failed (attempt ${attempt + 1}/$retries): ${e.message}"
                )

                if (attempt < retries - 1) {
                    delay(RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }

        throw lastError ?: TelegramFileException("$operation failed after $retries attempts")
    }

    /** Map TDLib AuthorizationState to TelegramAuthState. */
    private fun mapAuthorizationState(state: AuthorizationState): TelegramAuthState =
            when (state) {
                is AuthorizationStateReady -> TelegramAuthState.Ready
                is AuthorizationStateWaitPhoneNumber -> TelegramAuthState.WaitingForPhone
                is AuthorizationStateWaitCode -> TelegramAuthState.WaitingForCode
                is AuthorizationStateWaitPassword -> TelegramAuthState.WaitingForPassword
                is AuthorizationStateClosing,
                is AuthorizationStateClosed,
                is AuthorizationStateLoggingOut -> TelegramAuthState.Idle
                else -> TelegramAuthState.Connecting
            }

    /** Map TDLib ChatType to string label. */
    private fun mapChatType(type: ChatType?): String =
            when (type) {
                is ChatTypePrivate -> "private"
                is ChatTypeBasicGroup -> "group"
                is ChatTypeSupergroup -> if (type.isChannel) "channel" else "supergroup"
                is ChatTypeSecret -> "secret"
                else -> "unknown"
            }

    /** Map TDLib File to TelegramFileLocation. */
    private fun mapFileLocation(file: File): TelegramFileLocation {
        val local: LocalFile = file.local
        val remote: RemoteFile = file.remote

        return TelegramFileLocation(
                fileId = file.id,
                remoteId = remote.id,
                uniqueId = remote.uniqueId,
                localPath = local.path.takeIf { local.isDownloadingCompleted },
                size = file.size.toLong(),
                downloadedSize = local.downloadedSize.toLong(),
                isDownloadingActive = local.isDownloadingActive,
                isDownloadingCompleted = local.isDownloadingCompleted
        )
    }
}

// ========== Extension: TdlResult.getOrThrow() ==========

/**
 * Extension function to convert TdlResult to value or throw exception.
 *
 * Copied from v1 T_TelegramSession.kt for consistency.
 */
@JvmSynthetic
private fun <T> TdlResult<T>.getOrThrow(): T =
        when (this) {
            is TdlResult.Success -> result
            is TdlResult.Failure -> throw RuntimeException("TDLib error $code: $message")
        }
