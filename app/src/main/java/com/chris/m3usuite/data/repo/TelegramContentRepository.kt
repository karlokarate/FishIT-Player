package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.obx.ObxTelegramMessage
import com.chris.m3usuite.data.obx.ObxTelegramMessage_
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.parser.MediaParser
import dev.g000sha256.tdl.dto.Message
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository for Telegram content.
 * Handles message indexing, metadata extraction, and content queries.
 */
class TelegramContentRepository(
    private val context: Context,
    private val store: SettingsStore,
) {
    private val obxStore = ObxStore.get(context)
    private val messageBox: Box<ObxTelegramMessage> = obxStore.boxFor()

    /**
     * Index messages from a specific chat.
     * Parses metadata and stores in ObjectBox with reusable keys.
     */
    suspend fun indexChatMessages(
        chatId: Long,
        chatTitle: String,
        messages: List<Message>,
    ): Int =
        withContext(Dispatchers.IO) {
            var indexed = 0

            messages.forEach { message ->
                val parsed =
                    MediaParser.parseMessage(
                        chatId = chatId,
                        chatTitle = chatTitle,
                        message = message,
                        recentMessages = emptyList(),
                    )

                when (parsed) {
                    is com.chris.m3usuite.telegram.models.ParsedItem.Media -> {
                        val mediaInfo = parsed.info

                        // TODO: Extract file metadata from TDLib message content
                        // These fields are not available in MediaInfo and require direct TDLib access
                        val fileId: Int? = null
                        val fileUniqueId: String? = null
                        val durationSecs: Int? = mediaInfo.durationMinutes?.times(60)
                        val width: Int? = null
                        val height: Int? = null
                        val language: String? = null

                        val obxMessage =
                            ObxTelegramMessage(
                                chatId = chatId,
                                messageId = message.id,
                                fileId = fileId,
                                fileUniqueId = fileUniqueId,
                                caption = mediaInfo.title,
                                captionLower = mediaInfo.title?.lowercase(),
                                date = message.date.toLong(),
                                fileName = mediaInfo.fileName,
                                durationSecs = durationSecs,
                                mimeType = mediaInfo.mimeType,
                                sizeBytes = mediaInfo.sizeBytes,
                                width = width,
                                height = height,
                                language = language,
                            )

                        // Check if already exists
                        val existing =
                            messageBox
                                .query {
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
    fun getAllTelegramContent(): Flow<List<MediaItem>> =
        store.tgSelectedChatsCsv
            .map { csv ->
                val chatIds = csv.split(",").mapNotNull { it.trim().toLongOrNull() }
                if (chatIds.isEmpty()) {
                    emptyList()
                } else {
                    getTelegramContentByChatIds(chatIds)
                }
            }.flowOn(Dispatchers.IO)

    /**
     * Get Telegram content for specific chats.
     */
    private suspend fun getTelegramContentByChatIds(chatIds: List<Long>): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val messages =
                messageBox
                    .query {
                        `in`(ObxTelegramMessage_.chatId, chatIds.toLongArray())
                        orderDesc(ObxTelegramMessage_.date)
                    }.find()

            messages.map { obxMsg ->
                MediaItem(
                    id = encodeTelegramId(obxMsg.messageId),
                    name = obxMsg.caption ?: "Untitled",
                    type = "vod", // Default to VOD, can be refined based on metadata
                    url = "tg://file/${obxMsg.fileId}",
                    poster = obxMsg.thumbLocalPath,
                    plot = null,
                    rating = null,
                    year = null,
                    durationSecs = obxMsg.durationSecs,
                    categoryName = "Telegram",
                    source = "telegram",
                    providerKey = "Telegram",
                )
            }
        }

    /**
     * Get Telegram content for a specific chat.
     */
    fun getTelegramContentByChat(chatId: Long): Flow<List<MediaItem>> =
        kotlinx.coroutines.flow
            .flow {
                val messages =
                    messageBox
                        .query {
                            equal(ObxTelegramMessage_.chatId, chatId)
                            orderDesc(ObxTelegramMessage_.date)
                        }.find()

                emit(
                    messages.map { obxMsg ->
                        MediaItem(
                            id = encodeTelegramId(obxMsg.messageId),
                            name = obxMsg.caption ?: "Untitled",
                            type = "vod",
                            url = "tg://file/${obxMsg.fileId}",
                            poster = obxMsg.thumbLocalPath,
                            plot = null,
                            rating = null,
                            year = null,
                            durationSecs = obxMsg.durationSecs,
                            categoryName = "Telegram - Chat $chatId",
                            source = "telegram",
                            providerKey = "Telegram",
                        )
                    },
                )
            }.flowOn(Dispatchers.IO)

    /**
     * Search Telegram content.
     */
    fun searchTelegramContent(query: String): Flow<List<MediaItem>> =
        kotlinx.coroutines.flow
            .flow {
                val messages =
                    messageBox
                        .query {
                            contains(ObxTelegramMessage_.captionLower, query.lowercase(), StringOrder.CASE_INSENSITIVE)
                            orderDesc(ObxTelegramMessage_.date)
                        }.find()

                emit(
                    messages.map { obxMsg ->
                        MediaItem(
                            id = encodeTelegramId(obxMsg.messageId),
                            name = obxMsg.caption ?: "Untitled",
                            type = "vod",
                            url = "tg://file/${obxMsg.fileId}",
                            poster = obxMsg.thumbLocalPath,
                            plot = null,
                            rating = null,
                            year = null,
                            durationSecs = obxMsg.durationSecs,
                            categoryName = "Telegram",
                            source = "telegram",
                            providerKey = "Telegram",
                        )
                    },
                )
            }.flowOn(Dispatchers.IO)

    /**
     * Get Telegram VOD content.
     * Filters by selected VOD chats from settings.
     */
    fun getTelegramVod(): Flow<List<MediaItem>> =
        flow {
            store.tgSelectedVodChatsCsv.collect { csv ->
                val chatIds = csv.split(",").mapNotNull { it.trim().toLongOrNull() }
                if (chatIds.isEmpty()) {
                    // Fall back to general selected chats if VOD-specific list is empty
                    val generalCsv = store.tgSelectedChatsCsv.first()
                    val generalChatIds = generalCsv.split(",").mapNotNull { it.trim().toLongOrNull() }
                    if (generalChatIds.isEmpty()) {
                        emit(emptyList())
                    } else {
                        emit(getTelegramContentByChatIds(generalChatIds))
                    }
                } else {
                    emit(getTelegramContentByChatIds(chatIds))
                }
            }
        }.flowOn(Dispatchers.IO)

    /**
     * Get Telegram Series content.
     * Filters by selected Series chats from settings.
     */
    fun getTelegramSeries(): Flow<List<MediaItem>> =
        flow {
            store.tgSelectedSeriesChatsCsv.collect { csv ->
                val chatIds = csv.split(",").mapNotNull { it.trim().toLongOrNull() }
                if (chatIds.isEmpty()) {
                    emit(emptyList())
                } else {
                    val items = getTelegramContentByChatIds(chatIds)
                    // Mark items as series type
                    emit(items.map { it.copy(type = "series") })
                }
            }
        }.flowOn(Dispatchers.IO)

    /**
     * Get Telegram Feed items (recent activity).
     * Returns the most recent items from all selected chats.
     * Limited to last 100 items for feed display.
     */
    fun getTelegramFeedItems(): Flow<List<MediaItem>> =
        store.tgSelectedChatsCsv
            .map { csv ->
                val chatIds = csv.split(",").mapNotNull { it.trim().toLongOrNull() }
                if (chatIds.isEmpty()) {
                    emptyList()
                } else {
                    val messages =
                        messageBox
                            .query {
                                `in`(ObxTelegramMessage_.chatId, chatIds.toLongArray())
                                orderDesc(ObxTelegramMessage_.date)
                            }.find(0, 100) // Limit to 100 most recent items

                    messages.map { obxMsg ->
                        MediaItem(
                            id = encodeTelegramId(obxMsg.messageId),
                            name = obxMsg.caption ?: "Untitled",
                            type = "vod",
                            url = "tg://file/${obxMsg.fileId}",
                            poster = obxMsg.thumbLocalPath,
                            plot = null,
                            rating = null,
                            year = null,
                            durationSecs = obxMsg.durationSecs,
                            categoryName = "Telegram - New",
                            source = "telegram",
                            providerKey = "Telegram",
                        )
                    }
                }
            }.flowOn(Dispatchers.IO)

    /**
     * Get count of Telegram messages.
     */
    suspend fun getTelegramMessageCount(): Long =
        withContext(Dispatchers.IO) {
            messageBox.count()
        }

    /**
     * Clear all Telegram messages from ObjectBox.
     */
    suspend fun clearAllMessages() =
        withContext(Dispatchers.IO) {
            messageBox.removeAll()
        }

    /**
     * Encode Telegram message ID as a stable MediaItem ID.
     * Uses encoding scheme from architecture: telegram messages start at 4e12
     */
    private fun encodeTelegramId(messageId: Long): Long = 4_000_000_000_000L + messageId

    companion object {
        /**
         * Check if a MediaItem ID represents a Telegram item.
         */
        fun isTelegramItem(mediaId: Long): Boolean = mediaId >= 4_000_000_000_000L && mediaId < 5_000_000_000_000L
    }
}
