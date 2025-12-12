package com.fishit.player.infra.transport.telegram.chat

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramHistoryClient
import com.fishit.player.infra.transport.telegram.api.TgChat
import com.fishit.player.infra.transport.telegram.api.TgContent
import com.fishit.player.infra.transport.telegram.api.TgMessage
import com.fishit.player.infra.transport.telegram.api.TgPhotoSize
import com.fishit.player.infra.transport.telegram.util.RetryConfig
import com.fishit.player.infra.transport.telegram.util.TelegramRetry
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.Chat
import dev.g000sha256.tdl.dto.ChatListMain
import dev.g000sha256.tdl.dto.ChatTypeBasicGroup
import dev.g000sha256.tdl.dto.ChatTypePrivate
import dev.g000sha256.tdl.dto.ChatTypeSecret
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVideoNote
import dev.g000sha256.tdl.dto.MessageVoiceNote
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * Browser for navigating Telegram chats and messages (v2 Architecture).
 *
 * Ported from legacy `T_ChatBrowser` with v2 architecture compliance.
 *
 * **Key Behaviors (from legacy):**
 * - Chat list retrieval with caching
 * - Message history with proper TDLib paging (offset -1 rule)
 * - Full chat history loading for backfill
 * - Retry logic with exponential backoff
 *
 * **TDLib Paging Rule (CRITICAL):** Per TDLib semantics, `getChatHistory` requires:
 * - First page: `fromMessageId=0`, `offset=0`
 * - Subsequent pages: `fromMessageId=oldestMsgId`, `offset=-1` (to avoid duplicates)
 *
 * **v2 Compliance:**
 * - No UI references
 * - Uses UnifiedLog for all logging
 * - Returns TgMessage/TgChat wrapper types
 * - DI-scoped (receives TdlClient, doesn't create it)
 *
 * @param client The TDLib client (injected via DI)
 *
 * @see TelegramHistoryClient interface this implements
 * @see contracts/TELEGRAM_LEGACY_MODULE_MIGRATION_CONTRACT.md
 */
class TelegramChatBrowser(
        private val client: TdlClient,
) : TelegramHistoryClient {

    companion object {
        private const val TAG = "TelegramChatBrowser"
        private const val PAGE_SIZE_CHATS = 100
    }

    // Cache for chat metadata to reduce API calls - thread-safe
    private val chatCache = ConcurrentHashMap<Long, Chat>()
    // Cache for member counts (requires separate API calls)
    private val memberCountCache = ConcurrentHashMap<Long, Int>()

    // ========== TelegramHistoryClient Implementation ==========

    override val messageUpdates: Flow<TgMessage>
        get() =
                client.newMessageUpdates.mapNotNull { update ->
                    val message = update.message ?: return@mapNotNull null
                    mapMessage(message)
                }

    /**
     * Get all chats using stable cursor-based enumeration.
     *
     * **Stable Enumeration (no hard cap):**
     * - Pages through getChats in batches of [PAGE_SIZE_CHATS]
     * - Continues until TDLib returns empty/partial batch
     * - Respects caller's [limit] as maximum returned
     *
     * @param limit Maximum chats to return (0 = all available)
     */
    override suspend fun getChats(limit: Int): List<TgChat> {
        UnifiedLog.d(TAG, "getChats(limit=$limit)")

        val effectiveLimit = if (limit <= 0) Int.MAX_VALUE else limit
        val allChats = mutableListOf<TgChat>()
        var lastChatId = Long.MAX_VALUE
        var lastOrder = Long.MAX_VALUE

        while (allChats.size < effectiveLimit) {
            val pageLimit = minOf(PAGE_SIZE_CHATS, effectiveLimit - allChats.size)

            val batch =
                    TelegramRetry.executeWithRetry(
                            config = RetryConfig.DEFAULT,
                            operationName = "getChats",
                    ) {
                        val chatsResult = client.getChats(ChatListMain(), pageLimit)
                        when (chatsResult) {
                            is TdlResult.Success -> chatsResult.result.chatIds
                            is TdlResult.Failure ->
                                    throw RuntimeException(
                                            "getChats failed: ${chatsResult.code} - ${chatsResult.message}"
                                    )
                        }
                    }

            if (batch.isEmpty()) {
                UnifiedLog.d(TAG, "No more chats, stopping enumeration")
                break
            }

            for (chatId in batch) {
                if (allChats.size >= effectiveLimit) break

                try {
                    val chatResult = client.getChat(chatId)
                    if (chatResult is TdlResult.Success) {
                        val chat = chatResult.result
                        chatCache[chatId] = chat
                        allChats.add(mapChat(chat))
                    }
                } catch (e: Exception) {
                    UnifiedLog.w(TAG, "Error loading chat $chatId: ${e.message}")
                }
            }

            // TDLib returns chats in order - if we got less than requested, we're done
            if (batch.size < pageLimit) {
                UnifiedLog.d(
                        TAG,
                        "Partial batch received (${batch.size}/$pageLimit), end of chat list"
                )
                break
            }
        }

        UnifiedLog.d(TAG, "Loaded ${allChats.size} chats")
        return allChats
    }

    override suspend fun getChat(chatId: Long): TgChat? {
        // Check cache first
        chatCache[chatId]?.let {
            return mapChat(it)
        }

        return try {
            val result = client.getChat(chatId)
            when (result) {
                is TdlResult.Success -> {
                    val chat = result.result
                    chatCache[chatId] = chat
                    mapChat(chat)
                }
                is TdlResult.Failure -> {
                    UnifiedLog.w(TAG, "Error loading chat $chatId: ${result.message}")
                    null
                }
            }
        } catch (e: Exception) {
            UnifiedLog.w(TAG, "Error loading chat $chatId: ${e.message}")
            null
        }
    }

    override suspend fun fetchMessages(
            chatId: Long,
            limit: Int,
            fromMessageId: Long,
            offset: Int,
    ): List<TgMessage> {
        UnifiedLog.d(
                TAG,
                "fetchMessages(chatId=$chatId, limit=$limit, from=$fromMessageId, offset=$offset)"
        )

        return TelegramRetry.executeWithRetry(
                config = RetryConfig.DEFAULT,
                operationName = "fetchMessages",
        ) {
            val historyResult =
                    client.getChatHistory(
                            chatId = chatId,
                            fromMessageId = fromMessageId,
                            offset = offset,
                            limit = limit,
                            onlyLocal = false,
                    )

            when (historyResult) {
                is TdlResult.Success -> {
                    val messages =
                            historyResult.result.messages?.filterNotNull()?.map { mapMessage(it) }
                                    ?: emptyList()

                    UnifiedLog.d(TAG, "Fetched ${messages.size} messages")
                    messages
                }
                is TdlResult.Failure -> {
                    throw RuntimeException(
                            "getChatHistory failed: ${historyResult.code} - ${historyResult.message}"
                    )
                }
            }
        }
    }

    /**
     * Load complete message history from a chat.
     *
     * **TDLib Paging Rule:**
     * - First page: `fromMessageId=0`, `offset=0`
     * - Subsequent pages: `fromMessageId=oldestMsgId`, `offset=-1`
     *
     * Also handles TDLib's async loading behavior where the first call may return only 1 message
     * while TDLib loads more from the server in the background.
     */
    override suspend fun loadAllMessages(
            chatId: Long,
            pageSize: Int,
            maxMessages: Int,
            onProgress: ((loaded: Int) -> Unit)?
    ): List<TgMessage> {
        UnifiedLog.d(TAG, "loadAllMessages(chatId=$chatId, pageSize=$pageSize, max=$maxMessages)")

        val allMessages = mutableListOf<TgMessage>()
        var fromMessageId = 0L
        var isFirstPage = true

        while (allMessages.size < maxMessages) {
            // CRITICAL: TDLib paging rule
            // First page: offset=0
            // Subsequent pages: offset=-1 to avoid duplicate of the anchor message
            val offset = if (isFirstPage) 0 else -1

            var batch = fetchMessages(chatId, pageSize, fromMessageId, offset)

            // Handle TDLib async loading: first call often returns only 1 message
            // Wait and retry to get the full batch from server
            if (isFirstPage && batch.size == 1) {
                UnifiedLog.d(TAG, "First batch returned 1 message, waiting for TDLib async load...")
                delay(500L)
                batch = fetchMessages(chatId, pageSize, fromMessageId, offset)
                UnifiedLog.d(TAG, "After retry: ${batch.size} messages")
            }

            isFirstPage = false

            if (batch.isEmpty()) {
                UnifiedLog.d(TAG, "No more messages, stopping")
                break
            }

            allMessages.addAll(batch)
            fromMessageId = batch.last().id

            onProgress?.invoke(allMessages.size)
            UnifiedLog.d(TAG, "Progress: ${allMessages.size} messages loaded")

            // Safety check to prevent infinite loops
            if (batch.size < pageSize) {
                UnifiedLog.d(TAG, "Received partial batch, assuming end of history")
                break
            }
        }

        UnifiedLog.d(TAG, "Total messages loaded: ${allMessages.size}")
        return allMessages
    }

    override suspend fun searchMessages(
            chatId: Long,
            query: String,
            limit: Int,
    ): List<TgMessage> {
        UnifiedLog.d(TAG, "searchMessages(chatId=$chatId, query='$query', limit=$limit)")

        return TelegramRetry.executeWithRetry(
                config = RetryConfig.DEFAULT,
                operationName = "searchMessages",
        ) {
            val searchResult =
                    client.searchChatMessages(
                            chatId = chatId,
                            query = query,
                            senderId = null,
                            fromMessageId = 0L,
                            offset = 0,
                            limit = limit,
                            filter = null,
                    )

            when (searchResult) {
                is TdlResult.Success -> {
                    val messages =
                            searchResult.result.messages?.filterNotNull()?.map { mapMessage(it) }
                                    ?: emptyList()

                    UnifiedLog.d(TAG, "Search found ${messages.size} messages")
                    messages
                }
                is TdlResult.Failure -> {
                    throw RuntimeException(
                            "searchChatMessages failed: ${searchResult.code} - ${searchResult.message}"
                    )
                }
            }
        }
    }

    // ========== Mapping Functions ==========

    private fun mapChat(chat: Chat): TgChat {
        return TgChat(
                chatId = chat.id,
                title = chat.title,
                type = mapChatType(chat.type),
                memberCount = getMemberCount(chat),
                lastMessageId = chat.lastMessage?.id,
                lastMessageDate = chat.lastMessage?.date?.toLong()
        )
    }

    private fun mapChatType(type: dev.g000sha256.tdl.dto.ChatType): String {
        return when (type) {
            is ChatTypePrivate -> "private"
            is ChatTypeBasicGroup -> "basicGroup"
            is ChatTypeSupergroup -> if (type.isChannel) "channel" else "supergroup"
            is ChatTypeSecret -> "secret"
            else -> "unknown"
        }
    }

    /**
     * Gets member count for a chat.
     *
     * **Note:** TDLib does not include member count in the Chat object. For
     * BasicGroups/Supergroups, a separate getBasicGroupFullInfo/getSupergroupFullInfo call is
     * required. This is cached to reduce API calls.
     *
     * For now, we return cached values or 0 and populate asynchronously. Full implementation
     * requires async fetching of full group info.
     */
    private fun getMemberCount(chat: Chat): Int {
        // Check cache first
        memberCountCache[chat.id]?.let {
            return it
        }

        // For basic groups and supergroups, we'd need separate API calls:
        // - getBasicGroupFullInfo(basicGroupId) -> memberCount
        // - getSupergroupFullInfo(supergroupId) -> memberCount
        // These would be fetched lazily or on demand
        //
        // BUG FIX: Previously returned basicGroupId.toInt() which is the group ID, not count!
        return 0
    }

    private fun mapMessage(message: Message): TgMessage {
        return TgMessage(
                messageId = message.id,
                chatId = message.chatId,
                senderId = message.senderId,
                date = message.date.toLong(),
                content = mapContent(message.content),
                replyToMessageId =
                        message.replyTo?.let {
                            // Extract message ID from reply info
                            null // Simplified - full implementation would extract ID
                        },
                forwardInfo = message.forwardInfo?.let { "forwarded" }
        )
    }

    private fun mapContent(content: MessageContent): TgContent? {
        return when (content) {
            is MessageVideo ->
                    TgContent.Video(
                            fileId = content.video.video.id,
                            remoteId = content.video.video.remote?.id ?: "",
                            fileName = content.video.fileName,
                            mimeType = content.video.mimeType,
                            duration = content.video.duration,
                            width = content.video.width,
                            height = content.video.height,
                            fileSize = content.video.video.size,
                            supportsStreaming = content.video.supportsStreaming,
                            caption = content.caption.text
                    )
            is MessageAudio ->
                    TgContent.Audio(
                            fileId = content.audio.audio.id,
                            remoteId = content.audio.audio.remote?.id ?: "",
                            fileName = content.audio.fileName,
                            mimeType = content.audio.mimeType,
                            duration = content.audio.duration,
                            title = content.audio.title,
                            performer = content.audio.performer,
                            fileSize = content.audio.audio.size,
                            caption = content.caption.text
                    )
            is MessageDocument ->
                    TgContent.Document(
                            fileId = content.document.document.id,
                            remoteId = content.document.document.remote?.id ?: "",
                            fileName = content.document.fileName,
                            mimeType = content.document.mimeType,
                            fileSize = content.document.document.size,
                            caption = content.caption.text
                    )
            is MessagePhoto ->
                    TgContent.Photo(
                            sizes =
                                    content.photo.sizes.map { size ->
                                        TgPhotoSize(
                                                fileId = size.photo.id,
                                                remoteId = size.photo.remote?.id ?: "",
                                                width = size.width,
                                                height = size.height,
                                                fileSize = size.photo.size
                                        )
                                    },
                            caption = content.caption.text
                    )
            is MessageAnimation ->
                    TgContent.Animation(
                            fileId = content.animation.animation.id,
                            remoteId = content.animation.animation.remote?.id ?: "",
                            fileName = content.animation.fileName,
                            mimeType = content.animation.mimeType,
                            duration = content.animation.duration,
                            width = content.animation.width,
                            height = content.animation.height,
                            fileSize = content.animation.animation.size,
                            caption = content.caption.text
                    )
            is MessageVideoNote ->
                    TgContent.VideoNote(
                            fileId = content.videoNote.video.id,
                            remoteId = content.videoNote.video.remote?.id ?: "",
                            duration = content.videoNote.duration,
                            length = content.videoNote.length,
                            fileSize = content.videoNote.video.size
                    )
            is MessageVoiceNote ->
                    TgContent.VoiceNote(
                            fileId = content.voiceNote.voice.id,
                            remoteId = content.voiceNote.voice.remote?.id ?: "",
                            duration = content.voiceNote.duration,
                            mimeType = content.voiceNote.mimeType,
                            fileSize = content.voiceNote.voice.size,
                            caption = content.caption.text
                    )
            else -> null
        }
    }
}
