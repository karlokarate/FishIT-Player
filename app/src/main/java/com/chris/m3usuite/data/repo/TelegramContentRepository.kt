package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.obx.ObxTelegramItem
import com.chris.m3usuite.data.obx.ObxTelegramItem_
import com.chris.m3usuite.data.obx.ObxTelegramMessage
import com.chris.m3usuite.data.obx.ObxTelegramMessage_
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.domain.TelegramImageRef
import com.chris.m3usuite.telegram.domain.TelegramItem
import com.chris.m3usuite.telegram.domain.TelegramItemType
import com.chris.m3usuite.telegram.domain.toDomain
import com.chris.m3usuite.telegram.domain.toObx
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
 *
 * **Phase B Domain Integration:**
 * - New upsertItems/observeItems* methods operate on TelegramItem domain objects
 * - Uses ObxTelegramItem for new parser pipeline persistence
 * - Legacy methods using ObxTelegramMessage are marked @Deprecated
 */
class TelegramContentRepository(
    private val context: Context,
    private val store: SettingsStore,
) {
    private val obxStore = ObxStore.get(context)
    private val messageBox: Box<ObxTelegramMessage> = obxStore.boxFor()
    private val itemBox: Box<ObxTelegramItem> = obxStore.boxFor()

    // =========================================================================
    // New Domain-Oriented APIs (Phase B)
    // =========================================================================

    /**
     * Upsert TelegramItem domain objects to ObjectBox.
     *
     * Upsert logic: items are identified by (chatId, anchorMessageId).
     * Existing items with the same logical key are updated; new items are inserted.
     *
     * @param items List of TelegramItem domain objects to upsert
     */
    suspend fun upsertItems(items: List<TelegramItem>) =
        withContext(Dispatchers.IO) {
            if (items.isEmpty()) return@withContext

            // Batch query: Get all existing items for the given (chatId, anchorMessageId) pairs
            // Group items by chatId for efficient querying
            val itemsByChat = items.groupBy { it.chatId }
            val existingByKey = mutableMapOf<Pair<Long, Long>, Long>() // (chatId, anchorMessageId) -> id

            for ((chatId, chatItems) in itemsByChat) {
                val anchorIds = chatItems.map { it.anchorMessageId }.toLongArray()
                val existingItems =
                    itemBox
                        .query {
                            equal(ObxTelegramItem_.chatId, chatId)
                            `in`(ObxTelegramItem_.anchorMessageId, anchorIds)
                        }.find()

                for (existing in existingItems) {
                    existingByKey[Pair(existing.chatId, existing.anchorMessageId)] = existing.id
                }
            }

            // Convert to ObxTelegramItem and set id if existing
            val obxItems =
                items.map { item ->
                    val obx = item.toObx()
                    val key = Pair(item.chatId, item.anchorMessageId)
                    existingByKey[key]?.let { existingId ->
                        obx.id = existingId
                    }
                    obx
                }

            // Bulk put
            itemBox.put(obxItems)
        }

    /**
     * Observe TelegramItem domain objects for a specific chat.
     *
     * @param chatId Chat ID to filter by
     * @return Flow emitting list of TelegramItem objects for the chat
     */
    fun observeItemsByChat(chatId: Long): Flow<List<TelegramItem>> =
        flow {
            val items =
                itemBox
                    .query {
                        equal(ObxTelegramItem_.chatId, chatId)
                        orderDesc(ObxTelegramItem_.createdAtUtc)
                    }.find()
                    .map { it.toDomain() }
            emit(items)
        }.flowOn(Dispatchers.IO)

    /**
     * Observe all TelegramItem domain objects.
     *
     * @return Flow emitting list of all TelegramItem objects
     */
    fun observeAllItems(): Flow<List<TelegramItem>> =
        flow {
            val items =
                itemBox
                    .query {
                        orderDesc(ObxTelegramItem_.createdAtUtc)
                    }.find()
                    .map { it.toDomain() }
            // DEBUG: Log ObxTelegramItem count for UI wiring diagnostics
            android.util.Log.d(
                LOG_TAG_UI_WIRING,
                "TelegramContentRepository.observeAllItems(): ${items.size} items in ObxTelegramItem (new table)",
            )
            emit(items)
        }.flowOn(Dispatchers.IO)

    /**
     * Get TelegramItem by logical key.
     *
     * @param chatId Chat ID
     * @param anchorMessageId Anchor message ID
     * @return TelegramItem or null if not found
     */
    suspend fun getItem(
        chatId: Long,
        anchorMessageId: Long,
    ): TelegramItem? =
        withContext(Dispatchers.IO) {
            itemBox
                .query {
                    equal(ObxTelegramItem_.chatId, chatId)
                    equal(ObxTelegramItem_.anchorMessageId, anchorMessageId)
                }.findFirst()
                ?.toDomain()
        }

    /**
     * Delete TelegramItem by logical key.
     *
     * @param chatId Chat ID
     * @param anchorMessageId Anchor message ID
     */
    suspend fun deleteItem(
        chatId: Long,
        anchorMessageId: Long,
    ) = withContext(Dispatchers.IO) {
        val existing =
            itemBox
                .query {
                    equal(ObxTelegramItem_.chatId, chatId)
                    equal(ObxTelegramItem_.anchorMessageId, anchorMessageId)
                }.findFirst()
        existing?.let { itemBox.remove(it) }
    }

    /**
     * Clear all TelegramItem objects from ObjectBox.
     */
    suspend fun clearAllItems() =
        withContext(Dispatchers.IO) {
            itemBox.removeAll()
        }

    /**
     * Get count of TelegramItem objects.
     */
    suspend fun getTelegramItemCount(): Long =
        withContext(Dispatchers.IO) {
            itemBox.count()
        }

    // =========================================================================
    // Phase D UI Wiring: ObxTelegramItem-based Query APIs
    // =========================================================================

    /**
     * Summary of Telegram content for a single chat.
     *
     * Used by StartScreen/LibraryScreen to display Telegram rows.
     *
     * @property chatId Telegram chat ID
     * @property chatTitle Human-readable chat title (resolved via TDLib)
     * @property vodCount Number of VOD items in this chat
     * @property posterRef Reference to a representative poster image (for row thumbnail)
     */
    data class TelegramChatSummary(
        val chatId: Long,
        val chatTitle: String,
        val vodCount: Int,
        val posterRef: TelegramImageRef?,
    )

    /**
     * Observe all Telegram VOD items grouped by chat.
     *
     * Filters to VOD types only (MOVIE, SERIES_EPISODE, CLIP), excluding
     * non-playable types (AUDIOBOOK, RAR_ITEM, POSTER_ONLY).
     *
     * Phase D: This replaces getTelegramVodByChat() for the new pipeline.
     *
     * @return Flow of Map<chatId, List<TelegramItem>>
     */
    fun observeVodItemsByChat(): Flow<Map<Long, List<TelegramItem>>> =
        store.tgSelectedVodChatsCsv
            .map { csv ->
                val chatIds = parseChatIdsCsv(csv)
                if (chatIds.isEmpty()) {
                    emptyMap()
                } else {
                    buildVodItemsByChatMap(chatIds)
                }
            }.flowOn(Dispatchers.IO)

    /**
     * Build map of VOD items by chat from ObxTelegramItem.
     */
    private suspend fun buildVodItemsByChatMap(chatIds: List<Long>): Map<Long, List<TelegramItem>> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<Long, List<TelegramItem>>()

            for (chatId in chatIds) {
                val items =
                    itemBox
                        .query {
                            equal(ObxTelegramItem_.chatId, chatId)
                            orderDesc(ObxTelegramItem_.createdAtUtc)
                        }.find()
                        .map { it.toDomain() }
                        .filter { it.isVodType() }

                if (items.isNotEmpty()) {
                    result[chatId] = items
                }
            }

            result
        }

    /**
     * Observe a flat list of all Telegram VOD items.
     *
     * Filters to VOD types only (MOVIE, SERIES_EPISODE, CLIP).
     *
     * @return Flow emitting list of TelegramItem objects
     */
    fun observeAllVodItems(): Flow<List<TelegramItem>> =
        store.tgSelectedVodChatsCsv
            .map { csv ->
                val chatIds = parseChatIdsCsv(csv)
                if (chatIds.isEmpty()) {
                    emptyList()
                } else {
                    withContext(Dispatchers.IO) {
                        itemBox
                            .query {
                                `in`(ObxTelegramItem_.chatId, chatIds.toLongArray())
                                orderDesc(ObxTelegramItem_.createdAtUtc)
                            }.find()
                            .map { it.toDomain() }
                            .filter { it.isVodType() }
                    }
                }
            }.flowOn(Dispatchers.IO)

    /**
     * Observe chat summaries for UI rows.
     *
     * Phase D: This provides summary data for StartScreen/LibraryScreen rows.
     * Filters to VOD types and resolves chat titles via TDLib.
     *
     * @return Flow of list of TelegramChatSummary
     */
    fun observeVodChatSummaries(): Flow<List<TelegramChatSummary>> =
        store.tgSelectedVodChatsCsv
            .map { csv ->
                val chatIds = parseChatIdsCsv(csv)
                if (chatIds.isEmpty()) {
                    emptyList()
                } else {
                    buildVodChatSummaries(chatIds)
                }
            }.flowOn(Dispatchers.IO)

    /**
     * Build chat summaries from ObxTelegramItem.
     */
    private suspend fun buildVodChatSummaries(chatIds: List<Long>): List<TelegramChatSummary> =
        withContext(Dispatchers.IO) {
            val summaries = mutableListOf<TelegramChatSummary>()

            for (chatId in chatIds) {
                val items =
                    itemBox
                        .query {
                            equal(ObxTelegramItem_.chatId, chatId)
                            orderDesc(ObxTelegramItem_.createdAtUtc)
                        }.find()
                        .map { it.toDomain() }
                        .filter { it.isVodType() }

                if (items.isEmpty()) continue

                // Resolve chat title via TDLib
                val chatTitle = resolveChatTitle(chatId)

                // Find a representative poster (from the first item with a poster)
                val posterRef = items.firstOrNull { it.posterRef != null }?.posterRef

                summaries.add(
                    TelegramChatSummary(
                        chatId = chatId,
                        chatTitle = chatTitle,
                        vodCount = items.size,
                        posterRef = posterRef,
                    ),
                )
            }

            // DEBUG: Log summary count for UI wiring diagnostics
            android.util.Log.d(
                LOG_TAG_UI_WIRING,
                "UI summaries: ${summaries.size} chats, totalVod=${summaries.sumOf { it.vodCount }}",
            )

            summaries
        }

    /**
     * Helper extension to check if a TelegramItem is a VOD type.
     *
     * VOD types are playable video content: MOVIE, SERIES_EPISODE, CLIP.
     * Excludes: AUDIOBOOK, RAR_ITEM, POSTER_ONLY (not directly playable as video).
     */
    private fun TelegramItem.isVodType(): Boolean =
        when (type) {
            TelegramItemType.MOVIE,
            TelegramItemType.SERIES_EPISODE,
            TelegramItemType.CLIP,
            -> true
            else -> false
        }

    // =========================================================================
    // Legacy APIs (Pre-Phase B, using ObxTelegramMessage)
    // =========================================================================

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
     *
     * @deprecated Use upsertItems with the new parser pipeline instead.
     * This method uses the legacy ObxTelegramMessage entity.
     */
    @Deprecated(
        message = "Use upsertItems with new parser pipeline",
        replaceWith = ReplaceWith("upsertItems(items)"),
    )
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

        // Extract file metadata from TDLib message content (Requirement 3, 6)
        val fileId: Int?
        val fileUniqueId: String?
        val durationSecs: Int?
        val width: Int?
        val height: Int?
        val supportsStreaming: Boolean?
        val thumbFileId: Int?
        val thumbLocalPath: String?
        val localPath: String? // Zero-copy playback path (Requirement 6)

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
                // Local file path for zero-copy playback (Requirement 6)
                localPath = localPath,
                // Thumbnail fields (video/document thumbnail) (Requirement 3)
                thumbFileId = thumbFileId,
                thumbLocalPath = thumbLocalPath,
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

            messages.map { obxMsg -> toMediaItem(obxMsg, contentType) }
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
     * Single source of truth for mapping ObxTelegramMessage -> MediaItem
     * for single Telegram messages.
     *
     * All single-message queries must use this function rather than
     * constructing MediaItem instances manually.
     *
     * Includes thumbnail support (Requirement 3) and zero-copy paths (Requirement 6).
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

        // Use posterLocalPath as primary, fallback to thumbLocalPath (video thumbnail)
        val poster = obxMsg.posterLocalPath ?: obxMsg.thumbLocalPath

        return MediaItem(
            id = encodeTelegramId(obxMsg.messageId),
            name = obxMsg.caption ?: obxMsg.fileName ?: "Untitled",
            type = type,
            url = url,
            poster = poster,
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
            // Thumbnail support (Requirement 3)
            posterId = obxMsg.posterFileId ?: obxMsg.thumbFileId,
            localPosterPath = obxMsg.posterLocalPath ?: obxMsg.thumbLocalPath,
            // Zero-copy paths (Requirement 6) - use localPath for video/document
            localVideoPath = if (obxMsg.durationSecs != null) obxMsg.localPath else null,
            localPhotoPath = if (obxMsg.durationSecs == null && obxMsg.width != null) obxMsg.localPath else null,
            localDocumentPath = if (obxMsg.durationSecs == null && obxMsg.width == null) obxMsg.localPath else null,
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
                    messages.map { obxMsg -> toMediaItem(obxMsg, "vod") },
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
                    messages.map { obxMsg -> toMediaItem(obxMsg, "vod") },
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
     * Get Telegram content grouped by chat ID for per-chat rows (Requirement 1, 2).
     * Returns a map of chatId to (chatTitle, List<MediaItem>).
     *
     * This enables rendering separate rows for each enabled chat in Home/Library screens.
     * Chat titles are resolved via TDLib for proper display (not numeric IDs).
     *
     * @return Flow of Map<Long, Pair<String, List<MediaItem>>> where:
     *         - Key: chatId
     *         - Value: Pair of (chatTitle, list of MediaItems for that chat)
     */
    fun getTelegramContentByChat(): Flow<Map<Long, Pair<String, List<MediaItem>>>> =
        store.tgSelectedChatsCsv
            .map { csv ->
                val chatIds = parseChatIdsCsv(csv)
                if (chatIds.isEmpty()) {
                    emptyMap()
                } else {
                    buildChatContentMap(chatIds)
                }
            }.flowOn(Dispatchers.IO)

    /**
     * Build map of chat content with resolved titles.
     * Internal helper for getTelegramContentByChat().
     */
    private suspend fun buildChatContentMap(chatIds: List<Long>): Map<Long, Pair<String, List<MediaItem>>> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<Long, Pair<String, List<MediaItem>>>()

            for (chatId in chatIds) {
                // Get messages for this chat
                val messages =
                    messageBox
                        .query {
                            equal(ObxTelegramMessage_.chatId, chatId)
                            orderDesc(ObxTelegramMessage_.date)
                        }.find()

                if (messages.isEmpty()) continue

                // Resolve chat title via TDLib (Requirement 2)
                val chatTitle = resolveChatTitle(chatId)

                // Convert to MediaItems
                val items = messages.map { obxMsg -> toMediaItem(obxMsg, null) }

                result[chatId] = Pair(chatTitle, items)
            }

            result
        }

    /**
     * Resolve chat title from TDLib (Requirement 2).
     * Returns chat title or falls back to "Chat {chatId}" if unavailable.
     */
    private suspend fun resolveChatTitle(chatId: Long): String =
        withContext(Dispatchers.IO) {
            try {
                // Access TDLib service client to get chat info
                val serviceClient =
                    com.chris.m3usuite.telegram.core
                        .T_TelegramServiceClient
                        .getInstance(context)

                // Use the browser to resolve chat title
                serviceClient.resolveChatTitle(chatId)
            } catch (e: Exception) {
                "Chat $chatId"
            }
        }

    /**
     * Get VOD content grouped by chat (Requirement 1, 2).
     *
     * @deprecated Uses legacy ObxTelegramMessage. Use observeVodItemsByChat() instead.
     */
    @Deprecated(
        message = "Uses legacy ObxTelegramMessage. Use observeVodItemsByChat() instead.",
        replaceWith = ReplaceWith("observeVodItemsByChat()"),
    )
    fun getTelegramVodByChat(): Flow<Map<Long, Pair<String, List<MediaItem>>>> =
        store.tgSelectedVodChatsCsv
            .map { csv ->
                val chatIds = parseChatIdsCsv(csv)
                // DEBUG: Log query for UI wiring diagnostics
                android.util.Log.d(
                    LOG_TAG_UI_WIRING,
                    "TelegramContentRepository.getTelegramVodByChat(): querying ${chatIds.size} chatIds from ObxTelegramMessage (legacy)",
                )
                if (chatIds.isEmpty()) {
                    emptyMap()
                } else {
                    buildChatMoviesMap(chatIds)
                }
            }.flowOn(Dispatchers.IO)

    /**
     * Build map of chat movie content with resolved titles.
     */
    private suspend fun buildChatMoviesMap(chatIds: List<Long>): Map<Long, Pair<String, List<MediaItem>>> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<Long, Pair<String, List<MediaItem>>>()

            // DEBUG: Log total ObxTelegramMessage count for diagnostics
            val legacyMessageCount = messageBox.count()
            android.util.Log.d(
                LOG_TAG_UI_WIRING,
                "TelegramContentRepository: Total ObxTelegramMessage count = $legacyMessageCount (legacy table)",
            )

            for (chatId in chatIds) {
                // Get non-series messages for this chat
                val messages =
                    messageBox
                        .query {
                            equal(ObxTelegramMessage_.chatId, chatId)
                            equal(ObxTelegramMessage_.isSeries, false)
                            orderDesc(ObxTelegramMessage_.date)
                        }.find()

                if (messages.isEmpty()) continue

                val chatTitle = resolveChatTitle(chatId)
                val items = messages.map { obxMsg -> toMediaItem(obxMsg, "vod") }

                result[chatId] = Pair(chatTitle, items)
            }

            // DEBUG: Log result
            android.util.Log.d(
                LOG_TAG_UI_WIRING,
                "TelegramContentRepository.buildChatMoviesMap(): returning ${result.size} chats from ObxTelegramMessage (legacy)",
            )

            result
        }

    /**
     * Get Series content grouped by chat (Requirement 1, 2).
     *
     * @deprecated Uses legacy ObxTelegramMessage. Use observeVodItemsByChat() instead.
     */
    @Deprecated(
        message = "Uses legacy ObxTelegramMessage. Use observeVodItemsByChat() instead.",
        replaceWith = ReplaceWith("observeVodItemsByChat()"),
    )
    fun getTelegramSeriesByChat(): Flow<Map<Long, Pair<String, List<MediaItem>>>> =
        store.tgSelectedSeriesChatsCsv
            .map { csv ->
                val chatIds = parseChatIdsCsv(csv)
                if (chatIds.isEmpty()) {
                    emptyMap()
                } else {
                    buildChatSeriesMap(chatIds)
                }
            }.flowOn(Dispatchers.IO)

    /**
     * Build map of chat series content with resolved titles.
     */
    private suspend fun buildChatSeriesMap(chatIds: List<Long>): Map<Long, Pair<String, List<MediaItem>>> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<Long, Pair<String, List<MediaItem>>>()

            for (chatId in chatIds) {
                // Get series grouped by normalized name
                val episodes =
                    messageBox
                        .query {
                            equal(ObxTelegramMessage_.chatId, chatId)
                            equal(ObxTelegramMessage_.isSeries, true)
                            orderDesc(ObxTelegramMessage_.date)
                        }.find()

                if (episodes.isEmpty()) continue

                val chatTitle = resolveChatTitle(chatId)

                // Group by series and create representative items
                val seriesItems =
                    episodes
                        .filter { !it.seriesNameNormalized.isNullOrBlank() }
                        .groupBy { it.seriesNameNormalized!! }
                        .map { (_, episodeList) ->
                            // Use first episode as representative
                            val representative = episodeList.first()
                            val encodedSeriesName = java.net.URLEncoder.encode(representative.seriesNameNormalized, "UTF-8")
                            MediaItem(
                                id = encodeTelegramId(representative.messageId),
                                name = representative.seriesName ?: "Untitled Series",
                                type = "series",
                                url = "tg://series/${representative.chatId}/$encodedSeriesName",
                                poster = representative.posterLocalPath ?: representative.thumbLocalPath,
                                plot = representative.description,
                                rating = null,
                                year = representative.year,
                                durationSecs = null,
                                categoryName = "Telegram Series - $chatTitle",
                                source = "telegram",
                                providerKey = "Telegram",
                                posterId = representative.posterFileId ?: representative.thumbFileId,
                                localPosterPath = representative.posterLocalPath ?: representative.thumbLocalPath,
                                tgChatId = representative.chatId,
                                tgMessageId = representative.messageId,
                                tgFileId = representative.fileId,
                                localVideoPath = representative.localPath,
                                localPhotoPath = null,
                                localDocumentPath = representative.localPath,
                            )
                        }

                if (seriesItems.isNotEmpty()) {
                    result[chatId] = Pair(chatTitle, seriesItems)
                }
            }

            result
        }

    /**
     * Encode Telegram message ID as a stable MediaItem ID.
     * Uses encoding scheme from architecture: telegram messages start at 4e12
     */
    private fun encodeTelegramId(messageId: Long): Long = 4_000_000_000_000L + messageId

    companion object {
        /** Debug log tag for UI wiring diagnostics */
        private const val LOG_TAG_UI_WIRING = "telegram-ui"

        /**
         * Check if a MediaItem ID represents a Telegram item.
         */
        fun isTelegramItem(mediaId: Long): Boolean = mediaId >= 4_000_000_000_000L && mediaId < 5_000_000_000_000L
    }
}
