package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.obx.ObxTelegramMessage
import com.chris.m3usuite.data.obx.ObxTelegramMessage_
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.models.MediaInfo
import com.chris.m3usuite.telegram.models.MediaKind
import com.chris.m3usuite.telegram.parser.MediaParser
import com.chris.m3usuite.telegram.parser.TgContentHeuristics
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * Repository for Telegram content.
 * Handles message indexing, metadata extraction, and content queries.
 *
 * **Windowed Zero-Copy Streaming Support:**
 * - Generates tg://file/<fileId>?chatId=...&messageId=... URLs for all Telegram media
 * - These URLs are handled by TelegramDataSource with windowed streaming
 * - Windowing applies to direct media files: MOVIE, EPISODE, CLIP, AUDIO
 * - RAR_ARCHIVE and other archives are NOT streamed via TelegramDataSource
 *   (they require full download and extraction, separate handling)
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

            // Detect if this is a structured movie chat by analyzing message patterns
            val isStructuredMovieChat = detectStructuredMovieChat(messages)

            // If structured, parse all messages at once using structured parser
            if (isStructuredMovieChat) {
                val chatContext =
                    com.chris.m3usuite.telegram.models.ChatContext(
                        chatId = chatId,
                        chatTitle = chatTitle,
                        isStructuredMovieChat = true,
                    )

                // Sort messages by ascending message ID (chronological order) as expected by parser
                val sortedMessages = messages.sortedBy { it.id }
                val parsedItems = MediaParser.parseStructuredMovieChat(chatContext, sortedMessages)

                // Process each parsed item
                parsedItems.forEach { parsed ->
                    when (parsed) {
                        is com.chris.m3usuite.telegram.models.ParsedItem.Media -> {
                            val mediaInfo = parsed.info

                            // Find the original message to extract additional metadata
                            val message = messages.find { it.id == mediaInfo.messageId }
                            if (message != null) {
                                indexed += indexMediaInfo(mediaInfo, message, chatTitle)
                            }
                        }
                        else -> {
                            // Skip non-media items
                        }
                    }
                }
            } else {
                // Non-structured chat: use traditional per-message parsing
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

                            // Skip consumed messages (from structured patterns)
                            if (mediaInfo.isConsumed) {
                                return@forEach
                            }

                            // Skip items without valid fileId (cannot be played)
                            if (mediaInfo.fileId == null || mediaInfo.fileId <= 0) {
                                return@forEach
                            }

                            indexed += indexMediaInfo(mediaInfo, message, chatTitle)
                        }
                        else -> {
                            // Skip non-media items (SubChat, Invite, None)
                        }
                    }
                }
            }

            indexed
        }

    /**
     * Detect if a chat follows the structured movie pattern by analyzing message sequences.
     * A structured chat typically has repeating patterns of: Video -> Text (metadata) -> Photo (poster)
     */
    private fun detectStructuredMovieChat(messages: List<Message>): Boolean {
        // Need at least 3 messages for pattern detection
        if (messages.size < 3) return false

        // Sort by message ID ascending (chronological order as sent)
        val sorted = messages.sortedBy { it.id }
        var patternMatches = 0

        // Look for at least 2 occurrences of the pattern
        for (i in 0..sorted.size - 3) {
            val msg0 = sorted[i]
            val msg1 = sorted[i + 1]
            val msg2 = sorted[i + 2]

            val isPattern =
                msg0.content is MessageVideo &&
                    msg1.content is MessageText &&
                    msg2.content is MessagePhoto

            if (isPattern) {
                patternMatches++
                if (patternMatches >= 2) return true
            }
        }

        return false
    }

    /**
     * Index a single MediaInfo object extracted from a message.
     * This is shared logic between structured and non-structured parsing.
     */
    private suspend fun indexMediaInfo(
        mediaInfo: MediaInfo,
        message: Message,
        chatTitle: String,
    ): Int {
        // Skip items without valid fileId (cannot be played)
        if (mediaInfo.fileId == null || mediaInfo.fileId <= 0) {
            return 0
        }

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

        // Determine if this is a series episode
        // Note: The parser (MediaParser) may detect series patterns even without extracting a name.
        // However, for storage and grouping, we require a valid series name. Episodes without
        // a series name cannot be grouped and will be treated as standalone content.
        // Only mark as series if we have a valid series name AND season/episode info
        val isSeries =
            (
                heuristic.suggestedKind == MediaKind.EPISODE ||
                    mediaInfo.seasonNumber != null ||
                    mediaInfo.episodeNumber != null
            ) &&
                !mediaInfo.seriesName.isNullOrBlank()

        // Normalize series name for grouping
        // Note: This normalization may cause collisions for series that differ only in casing,
        // separators, or spacing. This is intentional for grouping variations of the same series.
        // Examples: "Star.Trek" and "Star-Trek" both become "star trek"
        val seriesNameNormalized =
            mediaInfo.seriesName
                ?.lowercase()
                ?.replace(Regex("""[._-]+"""), " ")
                ?.trim()

        val obxMessage =
            ObxTelegramMessage(
                chatId = mediaInfo.chatId,
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
                // Movie metadata
                title = mediaInfo.title,
                year = mediaInfo.year,
                genres = mediaInfo.genres.joinToString(", "),
                fsk = mediaInfo.fsk,
                description = mediaInfo.extraInfo,
                posterFileId = mediaInfo.posterFileId,
                // Series metadata
                isSeries = isSeries,
                seriesName = mediaInfo.seriesName,
                seriesNameNormalized = seriesNameNormalized,
                seasonNumber = heuristic.seasonNumber ?: mediaInfo.seasonNumber,
                episodeNumber = heuristic.episodeNumber ?: mediaInfo.episodeNumber,
                episodeTitle = mediaInfo.episodeTitle,
            )

        // Upsert logic: check if already exists
        val existing =
            messageBox
                .query {
                    equal(ObxTelegramMessage_.chatId, mediaInfo.chatId)
                    equal(ObxTelegramMessage_.messageId, message.id)
                }.findFirst()

        if (existing == null) {
            messageBox.put(obxMessage)
            return 1
        } else {
            // Update existing with new metadata
            obxMessage.id = existing.id
            messageBox.put(obxMessage)
            return 0
        }
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
                    getTelegramMoviesByChatIds(chatIds)
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
                    getTelegramSeriesByChatIds(chatIds)
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
                MediaItem(
                    id = encodeTelegramId(obxMsg.messageId),
                    name = obxMsg.caption ?: "Untitled",
                    type = "vod", // Default to VOD, can be refined based on metadata
                    url = "tg://file/${obxMsg.fileId}?chatId=${obxMsg.chatId}&messageId=${obxMsg.messageId}",
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
        requireNotNull(fileId) { "fileId must not be null when building Telegram URL" }
        val baseUrl = "tg://file/$fileId"
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
                        MediaItem(
                            id = encodeTelegramId(obxMsg.messageId),
                            name = obxMsg.caption ?: "Untitled",
                            type = "vod",
                            url = "tg://file/${obxMsg.fileId}?chatId=${obxMsg.chatId}&messageId=${obxMsg.messageId}",
                            poster = obxMsg.thumbLocalPath,
                            plot = null,
                            rating = null,
                            year = null,
                            durationSecs = obxMsg.durationSecs,
                            categoryName = "Telegram - Chat $chatId",
                        )
                    },
                )
            }.flowOn(Dispatchers.IO)

    /**
     * Get grouped series data for a specific chat.
     * Groups episodes by series name and organizes by season.
     *
     * @param chatId Chat ID to query
     * @return Map of series name to list of episodes
     */
    suspend fun getSeriesGroupedByChat(chatId: Long): Map<String, List<ObxTelegramMessage>> =
        withContext(Dispatchers.IO) {
            val episodes =
                messageBox
                    .query {
                        equal(ObxTelegramMessage_.chatId, chatId)
                        equal(ObxTelegramMessage_.isSeries, true)
                        orderDesc(ObxTelegramMessage_.date)
                    }.find()

            // Group by normalized series name
            episodes
                .filter { !it.seriesNameNormalized.isNullOrBlank() }
                .groupBy { it.seriesNameNormalized!! }
        }

    /**
     * Get all episodes for a specific series in a chat.
     *
     * @param chatId Chat ID
     * @param seriesNameNormalized Normalized series name for grouping
     * @return List of episodes sorted by season and episode number
     */
    suspend fun getSeriesEpisodes(
        chatId: Long,
        seriesNameNormalized: String,
    ): List<ObxTelegramMessage> =
        withContext(Dispatchers.IO) {
            messageBox
                .query {
                    equal(ObxTelegramMessage_.chatId, chatId)
                    equal(ObxTelegramMessage_.isSeries, true)
                    // Use exact match for normalized series name
                    equal(ObxTelegramMessage_.seriesNameNormalized, seriesNameNormalized, StringOrder.CASE_SENSITIVE)
                }.find()
                .sortedWith(
                    compareBy(
                        { it.seasonNumber ?: 0 },
                        { it.episodeNumber ?: 0 },
                    ),
                )
        }

    /**
     * Get movies (non-series content) for specific chats.
     *
     * @param chatIds List of chat IDs
     * @return List of movie MediaItems
     */
    private suspend fun getTelegramMoviesByChatIds(chatIds: List<Long>): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val messages =
                messageBox
                    .query {
                        `in`(ObxTelegramMessage_.chatId, chatIds.toLongArray())
                        equal(ObxTelegramMessage_.isSeries, false)
                        orderDesc(ObxTelegramMessage_.date)
                    }.find()

            messages.map { obxMsg ->
                toMediaItem(obxMsg, "vod")
            }
        }

    /**
     * Get unique series (one entry per series) for specific chats.
     *
     * @param chatIds List of chat IDs
     * @return List of series MediaItems (one per series)
     */
    private suspend fun getTelegramSeriesByChatIds(chatIds: List<Long>): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val episodes =
                messageBox
                    .query {
                        `in`(ObxTelegramMessage_.chatId, chatIds.toLongArray())
                        equal(ObxTelegramMessage_.isSeries, true)
                        orderDesc(ObxTelegramMessage_.date)
                    }.find()

            // Group by (chatId, seriesNameNormalized) and take first episode as representative
            episodes
                .filter { !it.seriesNameNormalized.isNullOrBlank() }
                .groupBy { Pair(it.chatId, it.seriesNameNormalized!!) }
                .map { (_, episodeList) ->
                    // Use first episode as representative, but modify fields for series view
                    val representative = episodeList.first()
                    val encodedSeriesName = URLEncoder.encode(representative.seriesNameNormalized, "UTF-8")
                    MediaItem(
                        id = encodeTelegramId(representative.messageId),
                        name = representative.seriesName ?: "Untitled Series",
                        type = "series",
                        url = "tg://series/${representative.chatId}/$encodedSeriesName",
                        poster = representative.posterLocalPath ?: representative.thumbLocalPath,
                        plot = representative.description,
                        rating = null,
                        year = representative.year,
                        durationSecs = null, // Series don't have a single duration
                        categoryName = "Telegram Series",
                        source = "telegram",
                        providerKey = "Telegram",
                    )
                }
        }

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
                            url = "tg://file/${obxMsg.fileId}?chatId=${obxMsg.chatId}&messageId=${obxMsg.messageId}",
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
