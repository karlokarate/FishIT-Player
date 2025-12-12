# Telegram Legacy Module Migration Summary - Part 3B: Chat Browser + Thumb Fetcher

**Migration Date:** 2025-01-16  
**Commit:** `52709299`

---

## 3. TelegramChatBrowser.kt (chat/)

**Path:** `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/chat/TelegramChatBrowser.kt`

**Lines:** 409

```kotlin
package com.fishit.player.infra.transport.telegram.chat

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramHistoryClient
import com.fishit.player.infra.transport.telegram.TgChat
import com.fishit.player.infra.transport.telegram.TgMessage
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
import dev.g000sha256.tdl.dto.SearchMessagesFilterEmpty
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import java.util.concurrent.ConcurrentHashMap

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
 * **TDLib Paging Rule (CRITICAL):**
 * Per TDLib semantics, `getChatHistory` requires:
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
    private val client: TdlClient
) : TelegramHistoryClient {

    companion object {
        private const val TAG = "TelegramChatBrowser"
        private const val DEFAULT_RETRIES = 3
    }

    // Cache for chat metadata to reduce API calls - thread-safe
    private val chatCache = ConcurrentHashMap<Long, Chat>()

    // ========== TelegramHistoryClient Implementation ==========

    override val messageUpdates: Flow<TgMessage>
        get() = client.newMessageUpdates
            .mapNotNull { update ->
                val message = update.message ?: return@mapNotNull null
                mapMessage(message)
            }

    override suspend fun getChats(limit: Int): List<TgChat> {
        UnifiedLog.d(TAG, "getChats(limit=$limit)")

        return executeWithRetry("getChats", DEFAULT_RETRIES) {
            val chatsResult = client.getChats(ChatListMain(), limit)
            val chatIds: LongArray = when (chatsResult) {
                is TdlResult.Success -> chatsResult.result.chatIds
                is TdlResult.Failure -> throw RuntimeException(
                    "getChats failed: ${chatsResult.code} - ${chatsResult.message}"
                )
            }

            val result = mutableListOf<TgChat>()
            for (chatId in chatIds) {
                try {
                    val chatResult = client.getChat(chatId)
                    if (chatResult is TdlResult.Success) {
                        val chat = chatResult.result
                        chatCache[chatId] = chat // Update cache
                        result.add(mapChat(chat))
                    }
                } catch (e: Exception) {
                    UnifiedLog.w(TAG, "Error loading chat $chatId: ${e.message}")
                }
            }

            UnifiedLog.d(TAG, "Loaded ${result.size} chats")
            result
        }
    }

    override suspend fun getChat(chatId: Long): TgChat? {
        // Check cache first
        chatCache[chatId]?.let { return mapChat(it) }

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
        offset: Int
    ): List<TgMessage> {
        UnifiedLog.d(TAG, "fetchMessages(chatId=$chatId, limit=$limit, from=$fromMessageId, offset=$offset)")

        return executeWithRetry("fetchMessages", DEFAULT_RETRIES) {
            val historyResult = client.getChatHistory(
                chatId = chatId,
                fromMessageId = fromMessageId,
                offset = offset,
                limit = limit,
                onlyLocal = false
            )

            when (historyResult) {
                is TdlResult.Success -> {
                    val messages = historyResult.result.messages
                        ?.filterNotNull()
                        ?.map { mapMessage(it) }
                        ?: emptyList()

                    UnifiedLog.d(TAG, "Fetched ${messages.size} messages")
                    messages
                }
                is TdlResult.Failure -> {
                    throw RuntimeException("getChatHistory failed: ${historyResult.code} - ${historyResult.message}")
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
     * Also handles TDLib's async loading behavior where the first call may return
     * only 1 message while TDLib loads more from the server in the background.
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
        limit: Int
    ): List<TgMessage> {
        UnifiedLog.d(TAG, "searchMessages(chatId=$chatId, query='$query', limit=$limit)")

        return executeWithRetry("searchMessages", DEFAULT_RETRIES) {
            val searchResult = client.searchChatMessages(
                chatId = chatId,
                query = query,
                senderId = null,
                fromMessageId = 0,
                offset = 0,
                limit = limit,
                filter = SearchMessagesFilterEmpty(),
                messageThreadId = 0,
                savedMessagesTopicId = 0
            )

            when (searchResult) {
                is TdlResult.Success -> {
                    val messages = searchResult.result.messages
                        ?.filterNotNull()
                        ?.map { mapMessage(it) }
                        ?: emptyList()

                    UnifiedLog.d(TAG, "Search found ${messages.size} messages")
                    messages
                }
                is TdlResult.Failure -> {
                    throw RuntimeException("searchChatMessages failed: ${searchResult.code} - ${searchResult.message}")
                }
            }
        }
    }

    // ========== Mapping Functions ==========

    private fun mapChat(chat: Chat): TgChat {
        return TgChat(
            id = chat.id,
            title = chat.title,
            type = mapChatType(chat.type),
            memberCount = getMemberCount(chat),
            lastMessageId = chat.lastMessage?.id,
            lastMessageDate = chat.lastMessage?.date
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

    private fun getMemberCount(chat: Chat): Int {
        return when (val type = chat.type) {
            is ChatTypeBasicGroup -> type.basicGroupId.toInt() // Will need separate call
            is ChatTypeSupergroup -> 0 // Will need separate call
            else -> 0
        }
    }

    private fun mapMessage(message: Message): TgMessage {
        return TgMessage(
            id = message.id,
            chatId = message.chatId,
            senderId = message.senderId,
            date = message.date,
            content = mapContent(message.content),
            replyToMessageId = message.replyTo?.let { 
                // Extract message ID from reply info
                null // Simplified - full implementation would extract ID
            },
            forwardInfo = message.forwardInfo?.let { "forwarded" }
        )
    }

    private fun mapContent(content: MessageContent): com.fishit.player.infra.transport.telegram.TgContent? {
        return when (content) {
            is MessageVideo -> com.fishit.player.infra.transport.telegram.TgContent.Video(
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
            is MessageAudio -> com.fishit.player.infra.transport.telegram.TgContent.Audio(
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
            is MessageDocument -> com.fishit.player.infra.transport.telegram.TgContent.Document(
                fileId = content.document.document.id,
                remoteId = content.document.document.remote?.id ?: "",
                fileName = content.document.fileName,
                mimeType = content.document.mimeType,
                fileSize = content.document.document.size,
                caption = content.caption.text
            )
            is MessagePhoto -> com.fishit.player.infra.transport.telegram.TgContent.Photo(
                sizes = content.photo.sizes.map { size ->
                    com.fishit.player.infra.transport.telegram.TgPhotoSize(
                        fileId = size.photo.id,
                        remoteId = size.photo.remote?.id ?: "",
                        width = size.width,
                        height = size.height,
                        fileSize = size.photo.size
                    )
                },
                caption = content.caption.text
            )
            is MessageAnimation -> com.fishit.player.infra.transport.telegram.TgContent.Animation(
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
            is MessageVideoNote -> com.fishit.player.infra.transport.telegram.TgContent.VideoNote(
                fileId = content.videoNote.video.id,
                remoteId = content.videoNote.video.remote?.id ?: "",
                duration = content.videoNote.duration,
                length = content.videoNote.length,
                fileSize = content.videoNote.video.size
            )
            is MessageVoiceNote -> com.fishit.player.infra.transport.telegram.TgContent.VoiceNote(
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

    // ========== Utility ==========

    private suspend inline fun <T> executeWithRetry(
        operation: String,
        retries: Int,
        block: () -> T
    ): T {
        var lastError: Exception? = null
        repeat(retries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastError = e
                UnifiedLog.w(TAG, "$operation failed (attempt ${attempt + 1}/$retries): ${e.message}")
                if (attempt < retries - 1) {
                    delay(500L * (attempt + 1)) // Exponential backoff
                }
            }
        }
        throw lastError ?: RuntimeException("$operation failed after $retries attempts")
    }
}
```

---

## 4. TelegramThumbFetcherImpl.kt (imaging/)

**Path:** `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/imaging/TelegramThumbFetcherImpl.kt`

**Lines:** 161

```kotlin
package com.fishit.player.infra.transport.telegram.imaging

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramFileClient
import com.fishit.player.infra.transport.telegram.TelegramThumbFetcher
import com.fishit.player.infra.transport.telegram.TgFileUpdate
import com.fishit.player.infra.transport.telegram.TgThumbnailRef
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import kotlin.time.Duration.Companion.seconds

/**
 * Telegram Thumbnail Fetcher Implementation (v2 Architecture).
 *
 * Fetches Telegram thumbnails for Coil image loading integration.
 * Uses transport-layer file downloads with remoteId-first fallback.
 *
 * **Key Behaviors (from legacy TelegramFileLoader):**
 * - Download thumbnails with medium priority
 * - RemoteId fallback for stale fileIds
 * - Bounded LRU set for failed remoteIds (prevents log spam)
 * - Prefetch support for scroll-ahead
 *
 * **v2 Compliance:**
 * - Uses UnifiedLog for all logging
 * - No UI references
 * - Consumes TelegramFileClient interface
 *
 * @param fileClient The transport-layer file client (injected via DI)
 *
 * @see TelegramThumbFetcher interface this implements
 * @see contracts/TELEGRAM_LEGACY_MODULE_MIGRATION_CONTRACT.md
 */
class TelegramThumbFetcherImpl(
    private val fileClient: TelegramFileClient
) : TelegramThumbFetcher {

    companion object {
        private const val TAG = "TelegramThumbFetcher"
        private const val DOWNLOAD_PRIORITY = 16 // Medium priority
        private const val FETCH_TIMEOUT_SECONDS = 10L
        private const val MAX_FAILED_CACHE_SIZE = 500
    }

    // Bounded LRU set of failed remoteIds to prevent repeated fetch attempts
    private val failedRemoteIds: MutableSet<String> = Collections.newSetFromMap(
        object : LinkedHashMap<String, Boolean>(MAX_FAILED_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > MAX_FAILED_CACHE_SIZE
            }
        }
    )

    // ========== TelegramThumbFetcher Implementation ==========

    override suspend fun fetchThumbnail(thumbRef: TgThumbnailRef): String? {
        // Skip if already known to fail
        if (thumbRef.remoteId in failedRemoteIds) {
            return null
        }

        // Check if already cached
        val cached = isCachedInternal(thumbRef.fileId)
        if (cached != null) {
            return cached
        }

        // Try download with current fileId
        var localPath = tryDownload(thumbRef.fileId)
        if (localPath != null) {
            return localPath
        }

        // FileId might be stale - try resolving via remoteId
        if (thumbRef.remoteId.isNotEmpty()) {
            val resolved = fileClient.resolveRemoteId(thumbRef.remoteId)
            if (resolved != null && resolved.id != thumbRef.fileId) {
                UnifiedLog.d(TAG, "Resolved stale fileId ${thumbRef.fileId} → ${resolved.id}")
                localPath = tryDownload(resolved.id)
                if (localPath != null) {
                    return localPath
                }
            }
        }

        // All attempts failed - add to failed cache
        if (thumbRef.remoteId.isNotEmpty()) {
            synchronized(failedRemoteIds) {
                failedRemoteIds.add(thumbRef.remoteId)
            }
        }

        return null
    }

    override suspend fun isCached(thumbRef: TgThumbnailRef): Boolean {
        return isCachedInternal(thumbRef.fileId) != null
    }

    override suspend fun prefetch(thumbRefs: List<TgThumbnailRef>) {
        for (ref in thumbRefs) {
            // Skip known failures
            if (ref.remoteId in failedRemoteIds) continue

            // Skip already cached
            if (isCachedInternal(ref.fileId) != null) continue

            // Start low-priority download (don't wait for completion)
            try {
                fileClient.startDownload(
                    fileId = ref.fileId,
                    priority = DOWNLOAD_PRIORITY / 2, // Lower priority for prefetch
                    offset = 0,
                    limit = 0
                )
            } catch (e: Exception) {
                // Ignore prefetch errors
            }
        }
    }

    override fun clearFailedCache() {
        synchronized(failedRemoteIds) {
            failedRemoteIds.clear()
        }
        UnifiedLog.d(TAG, "Failed cache cleared")
    }

    // ========== Internal Methods ==========

    private suspend fun isCachedInternal(fileId: Int): String? {
        val file = fileClient.getFile(fileId) ?: return null
        return if (file.isDownloadingCompleted && file.localPath != null) {
            file.localPath
        } else {
            null
        }
    }

    private suspend fun tryDownload(fileId: Int): String? {
        return try {
            // Start download
            fileClient.startDownload(
                fileId = fileId,
                priority = DOWNLOAD_PRIORITY,
                offset = 0,
                limit = 0
            )

            // Wait for completion
            withTimeoutOrNull(FETCH_TIMEOUT_SECONDS * 1000) {
                waitForCompletion(fileId)
            }
        } catch (e: Exception) {
            UnifiedLog.w(TAG, "Download failed for fileId=$fileId: ${e.message}")
            null
        }
    }

    private suspend fun waitForCompletion(fileId: Int): String? {
        // Poll for completion
        repeat(100) { // Max 10 seconds with 100ms intervals
            val file = fileClient.getFile(fileId)
            if (file?.isDownloadingCompleted == true && file.localPath != null) {
                return file.localPath
            }
            delay(100)
        }
        return null
    }
}
```

---

**See also:**
- [Part 3A: Auth + Download Manager](TELEGRAM_MIGRATION_SUMMARY_PART3A_IMPLEMENTATIONS.md)
- [Part 3C: Playback Layer](TELEGRAM_MIGRATION_SUMMARY_PART3C_PLAYBACK.md)
