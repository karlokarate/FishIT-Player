package com.fishit.player.infra.transport.telegram

import com.fishit.player.infra.logging.UnifiedLog
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
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVideoNote
import dev.g000sha256.tdl.dto.MessageVoiceNote
import dev.g000sha256.tdl.dto.RemoteFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Default TelegramTransportClient Implementation.
 *
 * Wraps g00sha tdlib-coroutines for low-level Telegram access in v2 architecture.
 *
 * **Module Boundary:**
 * - Returns raw wrapper types (TgMessage, TgFile, TgChat)
 * - No knowledge of RawMediaMetadata or pipeline models
 * - Pipeline layer handles conversion to catalog items
 *
 * **g00sha tdlib-coroutines Integration:**
 * Uses `dev.g000sha256:tdl-coroutines-android:5.0.0` AAR.
 *
 * @param clientProvider Provider for underlying TdlClient (injected via Hilt)
 * @param scope Coroutine scope for background operations
 */
class DefaultTelegramTransportClient(
    private val clientProvider: TdlibClientProvider,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : TelegramTransportClient {

    companion object {
        private const val TAG = "TelegramTransport"
        private const val DEFAULT_RETRIES = 3
        private const val RETRY_DELAY_MS = 500L
    }

    private val tdlClient: TdlClient
        get() = clientProvider.getClient()

    private val _authState = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Idle)
    override val authState: Flow<TelegramAuthState> = _authState.asStateFlow()

    private val _connectionState = MutableStateFlow<TelegramConnectionState>(TelegramConnectionState.Disconnected)
    override val connectionState: Flow<TelegramConnectionState> = _connectionState.asStateFlow()

    // ========== Authorization ==========

    override suspend fun ensureAuthorized() {
        UnifiedLog.d(TAG, "ensureAuthorized()")

        if (!clientProvider.isInitialized) {
            _authState.value = TelegramAuthState.Connecting
            clientProvider.initialize()
        }

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

    override suspend fun isAuthorized(): Boolean {
        if (!clientProvider.isInitialized) return false

        return try {
            val result = tdlClient.getAuthorizationState()
            result is TdlResult.Success && result.result is AuthorizationStateReady
        } catch (e: Exception) {
            false
        }
    }

    // ========== Chat Operations ==========

    override suspend fun getChats(limit: Int): List<TgChat> {
        UnifiedLog.d(TAG, "getChats(limit=$limit)")

        return withRetry("getChats") {
            val chatsResult = tdlClient.getChats(ChatListMain(), limit)
            val chatIds: LongArray = chatsResult.getOrThrow().chatIds

            val result = mutableListOf<TgChat>()
            for (chatId in chatIds) {
                try {
                    val chat = tdlClient.getChat(chatId).getOrThrow()
                    result.add(
                        TgChat(
                            id = chat.id,
                            title = chat.title,
                            type = mapChatType(chat.type),
                            photoSmallFileId = chat.photo?.small?.id,
                            photoBigFileId = chat.photo?.big?.id,
                            memberCount = null // Requires separate API call
                        )
                    )
                } catch (e: Exception) {
                    UnifiedLog.w(TAG, "Failed to load chat $chatId: ${e.message}")
                }
            }
            result.toList()
        }
    }

    // ========== Message Operations ==========

    override suspend fun fetchMessages(
        chatId: Long,
        limit: Int,
        offsetMessageId: Long
    ): List<TgMessage> {
        UnifiedLog.d(TAG, "fetchMessages(chatId=$chatId, limit=$limit, offset=$offsetMessageId)")

        return withRetry("fetchMessages") {
            val messages = loadMessageHistory(chatId, offsetMessageId, limit)
            messages.map { mapMessage(it) }.also {
                UnifiedLog.d(TAG, "Fetched ${it.size} messages from chat $chatId")
            }
        }
    }

    // ========== File Operations ==========

    override suspend fun resolveFile(fileId: Int): TgFile {
        UnifiedLog.d(TAG, "resolveFile(fileId=$fileId)")

        return withRetry("resolveFile") {
            val fileResult = tdlClient.getFile(fileId)
            val file = fileResult.getOrThrow()
            mapFile(file)
        }
    }

    override suspend fun resolveFileByRemoteId(remoteId: String): TgFile {
        UnifiedLog.d(TAG, "resolveFileByRemoteId(remoteId=$remoteId)")

        return withRetry("resolveFileByRemoteId") {
            val fileResult = tdlClient.getRemoteFile(remoteId, null)
            val file = fileResult.getOrThrow()
            // Need to get full file info
            val fullFile = tdlClient.getFile(file.id).getOrThrow()
            mapFile(fullFile)
        }
    }

    override suspend fun requestFileDownload(fileId: Int, priority: Int): TgFile {
        UnifiedLog.d(TAG, "requestFileDownload(fileId=$fileId, priority=$priority)")

        return withRetry("requestFileDownload") {
            val downloadResult = tdlClient.downloadFile(
                fileId = fileId,
                priority = priority,
                offset = 0,
                limit = 0,
                synchronous = false
            )
            val file = downloadResult.getOrThrow()
            mapFile(file)
        }
    }

    // ========== Lifecycle ==========

    override suspend fun close() {
        UnifiedLog.d(TAG, "close()")
        _authState.value = TelegramAuthState.Idle
        _connectionState.value = TelegramConnectionState.Disconnected
    }

    // ========== Private Helpers ==========

    private suspend fun loadMessageHistory(
        chatId: Long,
        fromMessageId: Long,
        limit: Int
    ): List<Message> {
        val offset = if (fromMessageId == 0L) 0 else -1

        val historyResult = tdlClient.getChatHistory(
            chatId = chatId,
            fromMessageId = fromMessageId,
            offset = offset,
            limit = limit.coerceAtMost(100),
            onlyLocal = false
        )

        return when (historyResult) {
            is TdlResult.Success -> historyResult.result.messages.filterNotNull()
            is TdlResult.Failure -> throw TelegramFileException(
                "getChatHistory failed: ${historyResult.code} - ${historyResult.message}"
            )
        }
    }

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
                UnifiedLog.w(TAG, "$operation failed (attempt ${attempt + 1}/$retries): ${e.message}")
                if (attempt < retries - 1) {
                    delay(RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }

        throw lastError ?: TelegramFileException("$operation failed after $retries attempts")
    }

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

    private fun mapChatType(type: ChatType?): TgChatType =
        when (type) {
            is ChatTypePrivate -> TgChatType.PRIVATE
            is ChatTypeBasicGroup -> TgChatType.BASIC_GROUP
            is ChatTypeSupergroup -> if (type.isChannel) TgChatType.CHANNEL else TgChatType.SUPERGROUP
            is ChatTypeSecret -> TgChatType.SECRET
            else -> TgChatType.UNKNOWN
        }

    private fun mapMessage(msg: Message): TgMessage =
        TgMessage(
            id = msg.id,
            chatId = msg.chatId,
            senderId = extractSenderId(msg),
            date = msg.date,
            content = mapContent(msg.content),
            replyToMessageId = msg.replyTo?.let { 
                // Extract message ID from ReplyTo
                null // Simplified - would need proper handling
            }
        )

    private fun extractSenderId(msg: Message): Long {
        // MessageSender can be MessageSenderUser or MessageSenderChat
        return when (val sender = msg.senderId) {
            is dev.g000sha256.tdl.dto.MessageSenderUser -> sender.userId
            is dev.g000sha256.tdl.dto.MessageSenderChat -> sender.chatId
            else -> 0L
        }
    }

    private fun mapContent(content: MessageContent): TgContent =
        when (content) {
            is MessageVideo -> TgContent.Video(
                fileId = content.video.video.id,
                remoteId = content.video.video.remote.id,
                uniqueId = content.video.video.remote.uniqueId,
                duration = content.video.duration,
                width = content.video.width,
                height = content.video.height,
                fileName = content.video.fileName,
                mimeType = content.video.mimeType,
                fileSize = content.video.video.size.toLong(),
                caption = content.caption.text.takeIf { it.isNotEmpty() },
                thumbnail = content.video.thumbnail?.let { mapThumbnail(it) }
            )
            is MessageDocument -> TgContent.Document(
                fileId = content.document.document.id,
                remoteId = content.document.document.remote.id,
                uniqueId = content.document.document.remote.uniqueId,
                fileName = content.document.fileName,
                mimeType = content.document.mimeType,
                fileSize = content.document.document.size.toLong(),
                caption = content.caption.text.takeIf { it.isNotEmpty() },
                thumbnail = content.document.thumbnail?.let { mapThumbnail(it) }
            )
            is MessageAudio -> TgContent.Audio(
                fileId = content.audio.audio.id,
                remoteId = content.audio.audio.remote.id,
                uniqueId = content.audio.audio.remote.uniqueId,
                duration = content.audio.duration,
                title = content.audio.title.takeIf { it.isNotEmpty() },
                performer = content.audio.performer.takeIf { it.isNotEmpty() },
                fileName = content.audio.fileName,
                mimeType = content.audio.mimeType,
                fileSize = content.audio.audio.size.toLong(),
                caption = content.caption.text.takeIf { it.isNotEmpty() },
                albumCoverThumbnail = content.audio.albumCoverThumbnail?.let { mapThumbnail(it) }
            )
            is MessagePhoto -> TgContent.Photo(
                sizes = content.photo.sizes.map { size ->
                    TgPhotoSize(
                        type = size.type,
                        fileId = size.photo.id,
                        remoteId = size.photo.remote.id,
                        uniqueId = size.photo.remote.uniqueId,
                        width = size.width,
                        height = size.height,
                        fileSize = size.photo.size
                    )
                },
                caption = content.caption.text.takeIf { it.isNotEmpty() }
            )
            is MessageAnimation -> TgContent.Animation(
                fileId = content.animation.animation.id,
                remoteId = content.animation.animation.remote.id,
                uniqueId = content.animation.animation.remote.uniqueId,
                duration = content.animation.duration,
                width = content.animation.width,
                height = content.animation.height,
                fileName = content.animation.fileName,
                mimeType = content.animation.mimeType,
                fileSize = content.animation.animation.size.toLong(),
                caption = content.caption.text.takeIf { it.isNotEmpty() },
                thumbnail = content.animation.thumbnail?.let { mapThumbnail(it) }
            )
            is MessageVideoNote -> TgContent.VideoNote(
                fileId = content.videoNote.video.id,
                remoteId = content.videoNote.video.remote.id,
                uniqueId = content.videoNote.video.remote.uniqueId,
                duration = content.videoNote.duration,
                length = content.videoNote.length,
                fileSize = content.videoNote.video.size.toLong(),
                thumbnail = content.videoNote.thumbnail?.let { mapThumbnail(it) }
            )
            is MessageVoiceNote -> TgContent.VoiceNote(
                fileId = content.voiceNote.voice.id,
                remoteId = content.voiceNote.voice.remote.id,
                uniqueId = content.voiceNote.voice.remote.uniqueId,
                duration = content.voiceNote.duration,
                mimeType = content.voiceNote.mimeType,
                fileSize = content.voiceNote.voice.size.toLong(),
                caption = content.caption.text.takeIf { it.isNotEmpty() }
            )
            is MessageText -> TgContent.Text(
                text = content.text.text
            )
            else -> TgContent.Unsupported(
                typeName = content::class.simpleName ?: "Unknown"
            )
        }

    private fun mapThumbnail(thumb: dev.g000sha256.tdl.dto.Thumbnail): TgThumbnail =
        TgThumbnail(
            fileId = thumb.file.id,
            remoteId = thumb.file.remote.id,
            uniqueId = thumb.file.remote.uniqueId,
            width = thumb.width,
            height = thumb.height,
            fileSize = thumb.file.size
        )

    private fun mapFile(file: File): TgFile {
        val local: LocalFile = file.local
        val remote: RemoteFile = file.remote

        return TgFile(
            id = file.id,
            remoteId = remote.id,
            uniqueId = remote.uniqueId,
            size = file.size.toLong(),
            expectedSize = file.expectedSize.toLong(),
            localPath = local.path.takeIf { local.isDownloadingCompleted },
            downloadedPrefixSize = local.downloadedPrefixSize.toLong(),
            isDownloadingActive = local.isDownloadingActive,
            isDownloadingCompleted = local.isDownloadingCompleted
        )
    }
}

// ========== Extension: TdlResult.getOrThrow() ==========

@JvmSynthetic
private fun <T> TdlResult<T>.getOrThrow(): T =
    when (this) {
        is TdlResult.Success -> result
        is TdlResult.Failure -> throw RuntimeException("TDLib error $code: $message")
    }
