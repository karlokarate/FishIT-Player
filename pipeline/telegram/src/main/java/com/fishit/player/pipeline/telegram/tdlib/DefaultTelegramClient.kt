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
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
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
                                },
                        ),
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
        offsetMessageId: Long,
    ): List<TelegramMediaItem> {
        UnifiedLog.d(
            TAG,
            "fetchMediaMessages(chatId=$chatId, limit=$limit, offset=$offsetMessageId)",
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
        limit: Int,
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

    override suspend fun requestFileDownload(
        fileId: Int,
        priority: Int,
    ): TelegramFileLocation {
        UnifiedLog.d(TAG, "requestFileDownload(fileId=$fileId, priority=$priority)")

        return withRetry("requestFileDownload") {
            // Request download from TDLib (this only starts the download)
            val downloadResult =
                tdlClient.downloadFile(
                    fileId = fileId,
                    priority = priority,
                    offset = 0,
                    limit = 0, // 0 = entire file
                    synchronous = false,
                )
            val file = downloadResult.getOrThrow()
            mapFileLocation(file)
        }
    }

    override suspend fun getMessagesPage(
        chatId: Long,
        fromMessageId: Long,
        limit: Int,
    ): List<Message> {
        UnifiedLog.d(
            TAG,
            "getMessagesPage(chatId=$chatId, fromMessageId=$fromMessageId, limit=$limit)",
        )

        return withRetry("getMessagesPage") {
            loadMessageHistory(chatId, fromMessageId, limit)
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
        limit: Int,
    ): List<Message> {
        val offset = if (fromMessageId == 0L) 0 else -1

        val historyResult =
            tdlClient.getChatHistory(
                chatId = chatId,
                fromMessageId = fromMessageId,
                offset = offset,
                limit = limit.coerceAtMost(100), // TDLib max is 100
                onlyLocal = false,
            )

        return when (historyResult) {
            is TdlResult.Success -> {
                historyResult.result.messages.filterNotNull()
            }
            is TdlResult.Failure -> {
                throw TelegramFileException(
                    "getChatHistory failed: ${historyResult.code} - ${historyResult.message}",
                )
            }
        }
    }

    /** Retry wrapper with exponential backoff. */
    private suspend fun <T> withRetry(
        operation: String,
        retries: Int = DEFAULT_RETRIES,
        block: suspend () -> T,
    ): T {
        var lastError: Exception? = null

        repeat(retries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastError = e
                UnifiedLog.w(
                    TAG,
                    "$operation failed (attempt ${attempt + 1}/$retries): ${e.message}",
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
            is AuthorizationStateLoggingOut,
            -> TelegramAuthState.Idle
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
            isDownloadingCompleted = local.isDownloadingCompleted,
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
