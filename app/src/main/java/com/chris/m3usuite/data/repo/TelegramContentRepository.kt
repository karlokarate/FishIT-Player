package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.obx.ObxTelegramMessage
import com.chris.m3usuite.data.obx.ObxTelegramMessage_
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.parser.MediaParser
import com.chris.m3usuite.telegram.parser.TgContentHeuristics
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessageVideo
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
     * Index messages from a specific chat with enhanced metadata extraction.
     * Parses metadata using MediaParser + TgContentHeuristics and stores in ObjectBox.
     * Extracts file metadata directly from TDLib message content.
     *
     * @param chatId Chat ID
     * @param chatTitle Chat title for context-based classification
     * @param messages List of messages to index
     * @param chatType Type of chat for filtering: "vod", "series", or "feed"
     * @return Number of messages indexed
     */
    suspend fun indexChatMessages(
        chatId: Long,
        chatTitle: String,
        messages: List<Message>,
        chatType: String = "vod",
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

                        // Apply heuristics for better classification
                        val heuristic = TgContentHeuristics.classify(mediaInfo, chatTitle)

                        // Extract file metadata from TDLib message content
                        val fileId: Int?
                        val fileUniqueId: String?
                        val durationSecs: Int?
                        val width: Int?
                        val height: Int?
                        val supportsStreaming: Boolean?

                        when (val content = message.content) {
                            is MessageVideo -> {
                                fileId = content.video.video?.id
                                fileUniqueId =
                                    content.video.video
                                        ?.remote
                                        ?.uniqueId
                                durationSecs = content.video.duration
                                width = content.video.width
                                height = content.video.height
                                supportsStreaming = content.video.supportsStreaming
                            }
                            is MessageDocument -> {
                                fileId = content.document.document?.id
                                fileUniqueId =
                                    content.document.document
                                        ?.remote
                                        ?.uniqueId
                                durationSecs = mediaInfo.durationMinutes?.times(60)
                                width = null
                                height = null
                                supportsStreaming = null
                            }
                            else -> {
                                fileId = null
                                fileUniqueId = null
                                durationSecs = mediaInfo.durationMinutes?.times(60)
                                width = null
                                height = null
                                supportsStreaming = null
                            }
                        }

                        // Detect language from heuristics
                        val language = heuristic.detectedLanguages.firstOrNull()

                        val obxMessage =
                            ObxTelegramMessage(
                                chatId = chatId,
                                messageId = message.id,
                                fileId = fileId,
                                fileUniqueId = fileUniqueId,
                                supportsStreaming = supportsStreaming,
                                caption = mediaInfo.title ?: mediaInfo.fileName,
                                captionLower = (mediaInfo.title ?: mediaInfo.fileName)?.lowercase(),
                                date = message.date.toLong(),
                                fileName = mediaInfo.fileName,
                                durationSecs = durationSecs,
                                mimeType = mediaInfo.mimeType,
                                sizeBytes = mediaInfo.sizeBytes,
                                width = width,
                                height = height,
                                language = language,
                            )

                        // Upsert logic: check if already exists
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
                            // Update existing with new metadata
                            obxMessage.id = existing.id
                            messageBox.put(obxMessage)
                        }
                    }
                    else -> {
                        // Skip non-media items (SubChat, Invite, None)
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
     * Get Telegram VOD content (movies).
     * Filters by VOD-specific chat selection from settings.
     */
    fun getTelegramVod(): Flow<List<MediaItem>> =
        store.tgSelectedVodChatsCsv
            .map { csv ->
                val chatIds = parseChatIdsCsv(csv)
                if (chatIds.isEmpty()) {
                    emptyList()
                } else {
                    getTelegramContentByChatIds(chatIds, "vod")
                }
            }.flowOn(Dispatchers.IO)

    /**
     * Get Telegram Series content (TV shows/series).
     * Filters by Series-specific chat selection from settings.
     */
    fun getTelegramSeries(): Flow<List<MediaItem>> =
        store.tgSelectedSeriesChatsCsv
            .map { csv ->
                val chatIds = parseChatIdsCsv(csv)
                if (chatIds.isEmpty()) {
                    emptyList()
                } else {
                    getTelegramContentByChatIds(chatIds, "series")
                }
            }.flowOn(Dispatchers.IO)

    /**
     * Get Telegram Feed items (latest activity).
     * Returns recent items from all selected chats, sorted by date.
     */
    fun getTelegramFeedItems(): Flow<List<MediaItem>> =
        store.tgSelectedChatsCsv
            .map { csv ->
                val chatIds = parseChatIdsCsv(csv)
                if (chatIds.isEmpty()) {
                    emptyList()
                } else {
                    // Get recent items (last 100) for activity feed
                    getTelegramRecentContent(chatIds, limit = 100)
                }
            }.flowOn(Dispatchers.IO)

    /**
     * Parse comma-separated chat IDs from settings string.
     */
    private fun parseChatIdsCsv(csv: String): List<Long> = csv.split(",").mapNotNull { it.trim().toLongOrNull() }

    /**
     * Get Telegram content for specific chats.
     *
     * @param chatIds List of chat IDs to query
     * @param contentType Optional content type filter ("vod", "series", "feed")
     */
    private suspend fun getTelegramContentByChatIds(
        chatIds: List<Long>,
        contentType: String? = null,
    ): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val messages =
                messageBox
                    .query {
                        `in`(ObxTelegramMessage_.chatId, chatIds.toLongArray())
                        orderDesc(ObxTelegramMessage_.date)
                    }.find()

            messages.map { obxMsg ->
                toMediaItem(obxMsg, contentType)
            }
        }

    /**
     * Get recent Telegram content (for activity feed).
     */
    private suspend fun getTelegramRecentContent(
        chatIds: List<Long>,
        limit: Int = 100,
    ): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val messages =
                messageBox
                    .query {
                        `in`(ObxTelegramMessage_.chatId, chatIds.toLongArray())
                        orderDesc(ObxTelegramMessage_.date)
                    }.find(0, limit.toLong())

            messages.map { obxMsg ->
                toMediaItem(obxMsg, "feed")
            }
        }

    /**
     * Convert ObxTelegramMessage to MediaItem with proper URL format.
     */
    private fun toMediaItem(
        obxMsg: ObxTelegramMessage,
        contentType: String? = null,
    ): MediaItem {
        // Generate proper tg:// URL with chatId and messageId
        val url = buildTelegramUrl(obxMsg.fileId, obxMsg.chatId, obxMsg.messageId)

        // Determine type based on content
        val type =
            when (contentType) {
                "series" -> "series"
                "vod" -> "vod"
                else -> "vod" // Default to VOD
            }

        return MediaItem(
            id = encodeTelegramId(obxMsg.messageId),
            name = obxMsg.caption ?: obxMsg.fileName ?: "Untitled",
            type = type,
            url = url,
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

    /**
     * Build Telegram URL with proper format: tg://file/<fileId>?chatId=...&messageId=...
     */
    private fun buildTelegramUrl(
        fileId: Int?,
        chatId: Long,
        messageId: Long,
    ): String {
        val baseUrl = "tg://file/${fileId ?: 0}"
        return "$baseUrl?chatId=$chatId&messageId=$messageId"
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
                        toMediaItem(obxMsg).copy(
                            categoryName = "Telegram - Chat $chatId",
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
                        toMediaItem(obxMsg)
                    },
                )
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
