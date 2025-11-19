package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.obx.ObxTelegramMessage
import com.chris.m3usuite.data.obx.ObxTelegramMessage_
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.browser.ChatBrowser
import com.chris.m3usuite.telegram.models.MediaInfo
import com.chris.m3usuite.telegram.models.MediaKind
import com.chris.m3usuite.telegram.parser.MediaParser
import com.chris.m3usuite.telegram.session.TelegramSession
import dev.g000sha256.tdl.dto.Message
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository for Telegram content.
 * Handles message indexing, metadata extraction, and content queries.
 */
class TelegramContentRepository(
    private val context: Context,
    private val store: SettingsStore
) {

    private val obxStore = ObxStore.getInstance(context)
    private val messageBox: Box<ObxTelegramMessage> = obxStore.boxFor()

    /**
     * Index messages from a specific chat.
     * Parses metadata and stores in ObjectBox with reusable keys.
     */
    suspend fun indexChatMessages(
        chatId: Long,
        chatTitle: String,
        messages: List<Message>
    ): Int = withContext(Dispatchers.IO) {
        var indexed = 0
        
        messages.forEach { message ->
            val parsed = MediaParser.parseMessage(
                chatId = chatId,
                chatTitle = chatTitle,
                message = message,
                recentMessages = emptyList()
            )

            when (parsed) {
                is com.chris.m3usuite.telegram.models.ParsedItem.Media -> {
                    val mediaInfo = parsed.info
                    val obxMessage = ObxTelegramMessage(
                        chatId = chatId,
                        messageId = message.id,
                        fileId = mediaInfo.fileId,
                        fileUniqueId = mediaInfo.fileUniqueId,
                        caption = mediaInfo.title,
                        captionLower = mediaInfo.title?.lowercase(),
                        date = message.date.toLong(),
                        fileName = mediaInfo.fileName,
                        durationSecs = mediaInfo.durationSeconds,
                        mimeType = mediaInfo.mimeType,
                        sizeBytes = mediaInfo.sizeBytes,
                        width = mediaInfo.width,
                        height = mediaInfo.height,
                        language = mediaInfo.language
                    )
                    
                    // Check if already exists
                    val existing = messageBox.query {
                        equal(ObxTelegramMessage_.chatId, chatId)
                        equal(ObxTelegramMessage_.messageId, message.id)
                    }.findFirst()
                    
                    if (existing == null) {
                        messageBox.put(obxMessage)
                        indexed++
                    } else {
                        // Update existing
                        obxMessage.id = existing.id
                        messageBox.put(obxMessage)
                    }
                }
                else -> {
                    // Skip non-media items
                }
            }
        }
        
        indexed
    }

    /**
     * Get all Telegram messages as MediaItem list.
     * Filters by selected chats from settings.
     */
    fun getAllTelegramContent(): Flow<List<MediaItem>> {
        return store.tgSelectedChatsCsv
            .map { csv ->
                val chatIds = csv.split(",").mapNotNull { it.trim().toLongOrNull() }
                if (chatIds.isEmpty()) {
                    emptyList()
                } else {
                    getTelegramContentByChatIds(chatIds)
                }
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Get Telegram content for specific chats.
     */
    private suspend fun getTelegramContentByChatIds(chatIds: List<Long>): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val messages = messageBox.query {
                `in`(ObxTelegramMessage_.chatId, chatIds.toLongArray())
                orderDesc(ObxTelegramMessage_.date)
            }.find()

            messages.map { obxMsg ->
                MediaItem(
                    id = encodeTelegramId(obxMsg.messageId),
                    name = obxMsg.caption ?: "Untitled",
                    type = "vod", // Default to VOD, can be refined based on metadata
                    url = "tg://file/${obxMsg.fileId}",
                    icon = null,
                    poster = obxMsg.thumbLocalPath,
                    plot = null,
                    rating = null,
                    year = null,
                    duration = obxMsg.durationSecs,
                    added = obxMsg.date,
                    categoryName = "Telegram",
                    providerName = "Telegram",
                    source = "telegram"
                )
            }
        }

    /**
     * Get Telegram content for a specific chat.
     */
    fun getTelegramContentByChat(chatId: Long): Flow<List<MediaItem>> {
        return kotlinx.coroutines.flow.flow {
            val messages = messageBox.query {
                equal(ObxTelegramMessage_.chatId, chatId)
                orderDesc(ObxTelegramMessage_.date)
            }.find()

            emit(messages.map { obxMsg ->
                MediaItem(
                    id = encodeTelegramId(obxMsg.messageId),
                    name = obxMsg.caption ?: "Untitled",
                    type = "vod",
                    url = "tg://file/${obxMsg.fileId}",
                    icon = null,
                    poster = obxMsg.thumbLocalPath,
                    plot = null,
                    rating = null,
                    year = null,
                    duration = obxMsg.durationSecs,
                    added = obxMsg.date,
                    categoryName = "Telegram - Chat $chatId",
                    providerName = "Telegram",
                    source = "telegram"
                )
            })
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Search Telegram content.
     */
    fun searchTelegramContent(query: String): Flow<List<MediaItem>> {
        return kotlinx.coroutines.flow.flow {
            val messages = messageBox.query {
                contains(ObxTelegramMessage_.captionLower, query.lowercase())
                orderDesc(ObxTelegramMessage_.date)
            }.find()

            emit(messages.map { obxMsg ->
                MediaItem(
                    id = encodeTelegramId(obxMsg.messageId),
                    name = obxMsg.caption ?: "Untitled",
                    type = "vod",
                    url = "tg://file/${obxMsg.fileId}",
                    icon = null,
                    poster = obxMsg.thumbLocalPath,
                    plot = null,
                    rating = null,
                    year = null,
                    duration = obxMsg.durationSecs,
                    added = obxMsg.date,
                    categoryName = "Telegram",
                    providerName = "Telegram",
                    source = "telegram"
                )
            })
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Get count of Telegram messages.
     */
    suspend fun getTelegramMessageCount(): Long = withContext(Dispatchers.IO) {
        messageBox.count()
    }

    /**
     * Clear all Telegram messages from ObjectBox.
     */
    suspend fun clearAllMessages() = withContext(Dispatchers.IO) {
        messageBox.removeAll()
    }

    /**
     * Encode Telegram message ID as a stable MediaItem ID.
     * Uses encoding scheme from architecture: telegram messages start at 4e12
     */
    private fun encodeTelegramId(messageId: Long): Long {
        return 4_000_000_000_000L + messageId
    }

    companion object {
        /**
         * Check if a MediaItem ID represents a Telegram item.
         */
        fun isTelegramItem(mediaId: Long): Boolean {
            return mediaId >= 4_000_000_000_000L && mediaId < 5_000_000_000_000L
        }
    }
}
