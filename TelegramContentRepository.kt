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
 * Repository für Telegram-Content.
 *
 * Responsibilities:
 * - Messages eines Chats parsen und in ObxTelegramMessage indexieren
 * - aus ObxTelegramMessage → MediaItem bauen
 * - Filter/Flows für Movies/Series/Kids/Search etc.
 *
 * TDLib-Philosophie:
 * - TDLib hält Files, Paths, Caches.
 * - Wir speichern nur Identität + Semantik:
 *   - chatId, messageId, fileId, fileUniqueId
 *   - Titel, Jahr, Genres, FSK, Beschreibung
 *   - Serien-Metadaten, Kids/Flags
 *   - Poster-/Thumb-FileIDs (keine Pfade!)
 *
 * Streaming/Zero-Copy:
 * - Playback benutzt tg:// URLs, die von TelegramPlayUrl gebaut werden.
 * - ExoPlayer liest aus TDLib-Cache per eigener DataSource.
 * - Keine lokalen Pfade (localPath/posterLocalPath/thumbLocalPath) mehr in OBX/MediaItem.
 */
class TelegramContentRepository(
    private val context: Context,
    private val store: SettingsStore,
) {
    private val obxStore = ObxStore.get(context)
    private val messageBox: Box<ObxTelegramMessage> = obxStore.boxFor()

    /**
     * Index messages from a specific chat with enhanced metadata extraction.
     *
     * This method will:
     * - Parse structured 3-message patterns (photo + text + document/video)
     * - Extract movie/series metadata (title, year, genres, fsk, description)
     * - Store semantic metadata in ObxTelegramMessage
     *
     * It is designed to be idempotent and can be re-run when new messages arrive.
     */
    suspend fun indexChatMessages(
        chatId: Long,
        chatTitle: String?,
        messages: List<Message>,
    ): Int =
        withContext(Dispatchers.IO) {
            if (messages.isEmpty()) return@withContext 0

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
                                if (indexSingleMessage(chatId, chatTitle, message, mediaInfo)) {
                                    indexed++
                                }
                            }
                        }

                        else -> {
                            // Non-media parsed items are ignored for indexing purposes
                        }
                    }
                }
            } else {
                // Fallback: Process messages individually with simple heuristics
                messages.forEach { message ->
                    val mediaInfo = MediaParser.parseMediaFromMessage(chatId, chatTitle, message)
                    if (mediaInfo != null) {
                        if (indexSingleMessage(chatId, chatTitle, message, mediaInfo)) {
                            indexed++
                        }
                    }
                }
            }

            indexed
        }

    /**
     * Detect if a chat follows the structured movie chat pattern.
     *
     * Heuristics:
     * - Repeated sequences of PHOTO -> TEXT -> DOCUMENT/VIDEO
     * - High ratio of media messages
     */
    private fun detectStructuredMovieChat(messages: List<Message>): Boolean {
        if (messages.size < 3) return false

        var structuredTriples = 0
        var i = 0

        while (i + 2 < messages.size) {
            val m1 = messages[i]
            val m2 = messages[i + 1]
            val m3 = messages[i + 2]

            val isPhoto = m1.content is MessagePhoto
            val isText = m2.content is MessageText
            val isDocOrVideo = m3.content is MessageDocument || m3.content is MessageVideo

            if (isPhoto && isText && isDocOrVideo) {
                structuredTriples++
                i += 3
            } else {
                i++
            }
        }

        // Consider it structured if at least 2 such triples exist
        return structuredTriples >= 2
    }

    /**
     * Index a single message and store semantic metadata in ObjectBox.
     *
     * This method avoids persisting TDLib paths. Instead, it stores:
     * - identity: chatId, messageId, fileId, fileUniqueId
     * - semantic metadata: title, year, genres, fsk, description
     * - telegram poster/thumbnail IDs
     */
    private fun indexSingleMessage(
        chatId: Long,
        chatTitle: String?,
        message: Message,
        mediaInfo: MediaInfo,
    ): Boolean {
        // Use heuristics to classify content and extract additional data
        val heuristic = TgContentHeuristics.classify(mediaInfo, chatTitle)

        // Extract file metadata from TDLib message content (Requirement 3, 6)
        val fileId: Int?
        val fileUniqueId: String?
        val durationSecs: Int?
        val width: Int?
        val height: Int?
        val supportsStreaming: Boolean?
        val thumbFileId: Int?
        val thumbLocalPath: String? // local thumb path (not persisted anymore)
        val localPath: String? // local media path (not persisted anymore)

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
                // Extract video thumbnail (Requirement 3)
                thumbFileId =
                    content.video.thumbnail
                        ?.file
                        ?.id
                thumbLocalPath =
                    content.video.thumbnail
                        ?.file
                        ?.local
                        ?.path
                // Extract local video file path (Requirement 6)
                localPath =
                    content.video.video
                        ?.local
                        ?.path
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
                // Extract document thumbnail (Requirement 3)
                thumbFileId =
                    content.document.thumbnail
                        ?.file
                        ?.id
                thumbLocalPath =
                    content.document.thumbnail
                        ?.file
                        ?.local
                        ?.path
                // Extract local document file path (Requirement 6)
                localPath =
                    content.document.document
                        ?.local
                        ?.path
            }

            is MessagePhoto -> {
                // For photos, use the largest size
                val largestPhoto = content.photo.sizes.maxByOrNull { it.width * it.height }
                fileId = largestPhoto?.photo?.id
                fileUniqueId = largestPhoto?.photo?.remote?.uniqueId
                durationSecs = null
                width = largestPhoto?.width
                height = largestPhoto?.height
                supportsStreaming = null
                // Photos use different sizes, not separate thumbnail files
                // Use the largest size directly as the poster
                thumbFileId = null
                thumbLocalPath = null
                // Extract local photo path (Requirement 6)
                localPath = largestPhoto?.photo?.local?.path
            }

            else -> {
                fileId = null
                fileUniqueId = null
                durationSecs = mediaInfo.durationMinutes?.times(60)
                width = null
                height = null
                supportsStreaming = null
                thumbFileId = null
                thumbLocalPath = null
                localPath = null
            }
        }

        // Detect language from heuristics
        val language = heuristic.detectedLanguages.firstOrNull()

        // Determine if this is a series episode
        val isSeries =
            (
                heuristic.suggestedKind == MediaKind.EPISODE ||
                    mediaInfo.seasonNumber != null ||
                    mediaInfo.episodeNumber != null
            ) && !mediaInfo.seriesNameNormalized.isNullOrBlank()

        val seriesNameNormalized =
            mediaInfo.seriesNameNormalized
                ?: heuristic.seriesNameNormalized
                ?: mediaInfo.title?.lowercase()

        // Build ObxTelegramMessage with semantic metadata only (no persisted paths)
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
                // Thumbnail identifiers (NOT paths; TDLib owns paths)
                thumbFileId = thumbFileId,
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

        return if (existing == null) {
            messageBox.put(obxMessage)
            true
        } else {
            // Update existing with new metadata
            obxMessage.id = existing.id
            messageBox.put(obxMessage)
            false
        }
    }

    /**
     * Get Telegram Movies (VOD).
     * Filters by Movie-specific chat selection from settings.
     */
    fun getTelegramMovies(): Flow<List<MediaItem>> =
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
     * Get Telegram Kids content (subset of Movies/Series).
     */
    fun getTelegramKids(): Flow<List<MediaItem>> =
        store.tgSelectedKidsChatsCsv
            .map { csv ->
                val chatIds = parseChatIdsCsv(csv)
                if (chatIds.isEmpty()) {
                    emptyList()
                } else {
                    getTelegramKidsByChatIds(chatIds)
                }
            }.flowOn(Dispatchers.IO)

    /**
     * Helper to parse CSV chat IDs from settings.
     */
    private fun parseChatIdsCsv(csv: String?): List<Long> =
        csv
            ?.split(",")
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filter { it != 0L }
            ?: emptyList()

    /**
     * Internal: get Telegram Movies for a set of chat IDs.
     */
    private fun getTelegramMoviesByChatIds(chatIds: List<Long>): List<MediaItem> {
        if (chatIds.isEmpty()) return emptyList()

        val result = mutableListOf<MediaItem>()

        chatIds.forEach { chatId ->
            val query =
                messageBox
                    .query {
                        equal(ObxTelegramMessage_.chatId, chatId)
                        // Movies: not marked as series
                        equal(ObxTelegramMessage_.isSeries, false)
                        // Must have a title or fileName
                        condition(
                            ObxTelegramMessage_.title.notEquals("").or(
                                ObxTelegramMessage_.fileName.notEquals(""),
                            ),
                        )
                        // Order by date descending (newest first)
                        orderDesc(ObxTelegramMessage_.date)
                    }.build()

            val messages = query.find()
            query.close()

            messages
                .map { toMediaItem(it, contentType = "vod") }
                .let { result.addAll(it) }
        }

        return result
    }

    /**
     * Internal: get Telegram Series for a set of chat IDs.
     */
    private fun getTelegramSeriesByChatIds(chatIds: List<Long>): List<MediaItem> {
        if (chatIds.isEmpty()) return emptyList()

        val result = mutableListOf<MediaItem>()

        chatIds.forEach { chatId ->
            val query =
                messageBox
                    .query {
                        equal(ObxTelegramMessage_.chatId, chatId)
                        equal(ObxTelegramMessage_.isSeries, true)
                        order(ObxTelegramMessage_.seriesName, StringOrder.CASE_INSENSITIVE)
                        order(ObxTelegramMessage_.seasonNumber)
                        order(ObxTelegramMessage_.episodeNumber)
                    }.build()

            val messages = query.find()
            query.close()

            // Group by series name and build representative MediaItem per series
            val grouped =
                messages
                    .filter { !it.seriesNameNormalized.isNullOrBlank() }
                    .groupBy { it.seriesNameNormalized!!.lowercase() }

            grouped.forEach { (_, episodeList) ->
                val representative = episodeList.first()
                val encodedSeriesName = URLEncoder.encode(representative.seriesNameNormalized, "UTF-8")

                // Simple aggregate series entry; posters werden dynamisch per posterId geladen
                val item =
                    MediaItem(
                        id = encodeTelegramId(representative.messageId),
                        name = representative.seriesName ?: "Untitled Series",
                        type = "series",
                        url = "tg://series/${representative.chatId}/$encodedSeriesName",
                        poster = null,
                        plot = representative.description,
                        rating = null,
                        year = representative.year,
                        durationSecs = null, // Series don't have a single duration
                        categoryName = "Telegram Series",
                        source = "telegram",
                        providerKey = "Telegram",
                    )

                result.add(item)
            }
        }

        return result
    }

    /**
     * Internal: get Telegram Kids content for a set of chat IDs.
     *
     * Kids-Inhalte sind aktuell:
     * - alle ObxTelegramMessage-Einträge, die in Kids-Chats liegen
     * - oder per heuristics/flag als Kids markiert werden (optional)
     */
    private fun getTelegramKidsByChatIds(chatIds: List<Long>): List<MediaItem> {
        if (chatIds.isEmpty()) return emptyList()

        val result = mutableListOf<MediaItem>()

        chatIds.forEach { chatId ->
            val query =
                messageBox
                    .query {
                        equal(ObxTelegramMessage_.chatId, chatId)
                        // Option: Kids-Flag könnte später in ObxTelegramMessage landen
                        // For now: einfach alle(Messages) dieses Chats, die kein adult-Flag haben
                        orderDesc(ObxTelegramMessage_.date)
                    }.build()

            val messages = query.find()
            query.close()

            messages
                .map { toMediaItem(it, contentType = if (it.isSeries) "series" else "vod") }
                .let { result.addAll(it) }
        }

        return result
    }

    /**
     * Get Telegram content for a specific chat.
     */
    fun getTelegramContentByChat(chatId: Long): Flow<List<MediaItem>> =
        flow {
            val messages =
                messageBox
                    .query {
                        equal(ObxTelegramMessage_.chatId, chatId)
                        orderDesc(ObxTelegramMessage_.date)
                    }.find()

            val items =
                messages.map { msg ->
                    val type = if (msg.isSeries) "series" else "vod"
                    toMediaItem(msg, contentType = type)
                }

            emit(items)
        }.flowOn(Dispatchers.IO)

    /**
     * Search Telegram content.
     */
    fun searchTelegramContent(query: String): Flow<List<MediaItem>> =
        flow {
            val q = query.trim().lowercase()
            if (q.isBlank()) {
                emit(emptyList())
                return@flow
            }

            val messages =
                messageBox
                    .query {
                        condition(
                            ObxTelegramMessage_.captionLower.contains(q)
                                .or(ObxTelegramMessage_.fileName.contains(q)),
                        )
                        orderDesc(ObxTelegramMessage_.date)
                    }.find()

            val items =
                messages.map { msg ->
                    val type = if (msg.isSeries) "series" else "vod"
                    toMediaItem(msg, contentType = type)
                }

            emit(items)
        }.flowOn(Dispatchers.IO)

    /**
     * Build MediaItem from ObxTelegramMessage.
     *
     * Wichtig:
     * - Keine Pfade mehr aus OBX verwenden.
     * - Poster kommt über posterId/Thumb + TelegramFileLoader.
     * - Playback über tg:// URL + TDLib.
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
            poster = null, // Telegram posters are resolved via posterId + TelegramFileLoader
            plot = obxMsg.description,
            rating = null,
            year = obxMsg.year,
            durationSecs = obxMsg.durationSecs,
            categoryName = "Telegram",
            source = "telegram",
            providerKey = "Telegram",
            tgChatId = obxMsg.chatId,
            tgMessageId = obxMsg.messageId,
            tgFileId = obxMsg.fileId,
            // Thumbnail support (Requirement 3) – only IDs, no paths
            posterId = obxMsg.posterFileId ?: obxMsg.thumbFileId,
            localPosterPath = null,
            // Zero-copy paths – Telegram playback uses tg:// URLs backed by TDLib cache
            localVideoPath = null,
            localPhotoPath = null,
            localDocumentPath = null,
        )
    }

    /**
     * Build Telegram URL with proper format: tg://file/<fileId>?chatId=...&messageId=...
     */
    private fun buildTelegramUrl(
        fileId: Int?,
        chatId: Long,
        messageId: Long,
    ): String =
        com.chris.m3usuite.telegram.util.TelegramPlayUrl
            .buildFileUrl(fileId, chatId, messageId)

    /**
     * Build aggregated series list across multiple chats.
     * Used by library screen to show series as top-level items.
     */
    suspend fun getAggregatedSeriesWithContext(
        chatIdToTitle: Map<Long, String?>,
    ): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val allMessages =
                messageBox
                    .query {
                        equal(ObxTelegramMessage_.isSeries, true)
                        order(ObxTelegramMessage_.seriesName, StringOrder.CASE_INSENSITIVE)
                        order(ObxTelegramMessage_.seasonNumber)
                        order(ObxTelegramMessage_.episodeNumber)
                    }.find()

            if (allMessages.isEmpty()) {
                return@withContext emptyList()
            }

            // Group by chatId first, then by seriesNameNormalized
            val groupedByChat =
                allMessages
                    .filter { !it.seriesNameNormalized.isNullOrBlank() }
                    .groupBy { it.chatId }

            val seriesItems = mutableListOf<MediaItem>()

            groupedByChat.forEach { (chatId, messagesInChat) ->
                val chatTitle = chatIdToTitle[chatId] ?: "Telegram"

                val groupedBySeries =
                    messagesInChat
                        .filter { !it.seriesNameNormalized.isNullOrBlank() }
                        .groupBy { it.seriesNameNormalized!!.lowercase() }

                groupedBySeries.forEach { (_, episodeList) ->
                    val representative = episodeList.first()
                    val encodedSeriesName = URLEncoder.encode(representative.seriesNameNormalized, "UTF-8")

                    val item =
                        MediaItem(
                            id = encodeTelegramId(representative.messageId),
                            name = representative.seriesName ?: "Untitled Series",
                            type = "series",
                            url = "tg://series/${representative.chatId}/$encodedSeriesName",
                            poster = null,
                            plot = representative.description,
                            rating = null,
                            year = representative.year,
                            durationSecs = null,
                            categoryName = "Telegram Series - $chatTitle",
                            source = "telegram",
                            providerKey = "Telegram",
                            posterId = representative.posterFileId ?: representative.thumbFileId,
                            localPosterPath = null,
                            tgChatId = representative.chatId,
                            tgMessageId = representative.messageId,
                            tgFileId = representative.fileId,
                            localVideoPath = null,
                            localPhotoPath = null,
                            localDocumentPath = null,
                        )

                    seriesItems.add(item)
                }
            }

            seriesItems
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
        fun isTelegramItem(mediaId: Long): Boolean =
            mediaId >= 4_000_000_000_000L && mediaId < 5_000_000_000_000L
    }
}