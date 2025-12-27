package com.fishit.player.infra.data.telegram

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.TmdbRef
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import com.fishit.player.core.persistence.obx.ObxTelegramMessage
import com.fishit.player.core.persistence.obx.ObxTelegramMessage_
import com.fishit.player.infra.logging.UnifiedLog
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBox-backed implementation of [TelegramContentRepository].
 *
 * **Architecture Compliance:**
 * - Works only with RawMediaMetadata (no pipeline DTOs)
 * - Uses ObjectBox entities internally (ObxTelegramMessage)
 * - Provides reactive Flows for UI consumption
 *
 * **Layer Boundaries:**
 * - Transport → Pipeline → Data → Domain → UI
 * - This repository sits in Data layer
 * - Consumes RawMediaMetadata from Pipeline (via CatalogSync)
 * - Serves RawMediaMetadata to Domain/UI
 *
 * **Conversion Strategy:**
 * - RawMediaMetadata ↔ ObxTelegramMessage
 * - sourceId format: "msg:{chatId}:{messageId}"
 */
@Singleton
class ObxTelegramContentRepository @Inject constructor(
    private val boxStore: BoxStore
) : TelegramContentRepository {

    companion object {
        private const val TAG = "ObxTelegramContentRepo"
    }

    private val box by lazy { boxStore.boxFor<ObxTelegramMessage>() }

    override fun observeAll(): Flow<List<RawMediaMetadata>> {
        val query = box.query()
            .order(ObxTelegramMessage_.date, QueryBuilder.DESCENDING)
            .build()
        return query.asFlow().map { entities -> entities.map { it.toRawMediaMetadata() } }
    }

    override fun observeByChat(chatId: Long): Flow<List<RawMediaMetadata>> {
        val query = box.query(ObxTelegramMessage_.chatId.equal(chatId))
            .order(ObxTelegramMessage_.date, QueryBuilder.DESCENDING)
            .build()
        return query.asFlow().map { entities -> entities.map { it.toRawMediaMetadata() } }
    }

    override suspend fun getAll(limit: Int, offset: Int): List<RawMediaMetadata> =
        withContext(Dispatchers.IO) {
            UnifiedLog.d(TAG, "getAll(limit=$limit, offset=$offset)")
            box.query()
                .order(ObxTelegramMessage_.date, QueryBuilder.DESCENDING)
                .build()
                .find(offset.toLong(), limit.toLong())
                .map { it.toRawMediaMetadata() }
        }

    override suspend fun getBySourceId(sourceId: String): RawMediaMetadata? =
        withContext(Dispatchers.IO) {
            val (chatId, messageId) = parseSourceId(sourceId) ?: return@withContext null
            box.query(
                ObxTelegramMessage_.chatId.equal(chatId)
                    .and(ObxTelegramMessage_.messageId.equal(messageId))
            )
                .build()
                .findFirst()
                ?.toRawMediaMetadata()
        }

    override suspend fun search(query: String, limit: Int): List<RawMediaMetadata> =
        withContext(Dispatchers.IO) {
            val lowerQuery = query.lowercase()
            box.query(
                ObxTelegramMessage_.captionLower.contains(lowerQuery)
                    .or(ObxTelegramMessage_.title.contains(lowerQuery))
                    .or(ObxTelegramMessage_.fileName.contains(lowerQuery))
            )
                .order(ObxTelegramMessage_.date, QueryBuilder.DESCENDING)
                .build()
                .find(0, limit.toLong())
                .map { it.toRawMediaMetadata() }
        }

    override suspend fun upsertAll(items: List<RawMediaMetadata>) =
        withContext(Dispatchers.IO) {
            UnifiedLog.d(TAG, "upsertAll(${items.size} items)")
            val entities = items.mapNotNull { it.toObxEntity() }
            if (entities.isEmpty()) return@withContext
            
            // Batch-optimized upsert: load all potential matches in fewer queries
            // Group by chatId for efficient querying
            val chatIds = entities.map { it.chatId }.distinct().toLongArray()
            val existingEntities = box.query(ObxTelegramMessage_.chatId.oneOf(chatIds))
                .build()
                .find()
            
            // Build lookup map with composite key "chatId:messageId"
            val existingIdMap = existingEntities.associateBy(
                { "${it.chatId}:${it.messageId}" },
                { it.id }
            )
            
            val toUpsert = entities.map { entity ->
                val key = "${entity.chatId}:${entity.messageId}"
                val existingId = existingIdMap[key]
                if (existingId != null && existingId > 0) {
                    entity.copy(id = existingId)
                } else {
                    entity
                }
            }
            
            box.put(toUpsert)
            UnifiedLog.d(TAG, "upsertAll: ${entities.size} items (${existingEntities.size} existing in chats)")
        }

    override suspend fun upsert(item: RawMediaMetadata) =
        withContext(Dispatchers.IO) {
            val entity = item.toObxEntity() ?: return@withContext
            
            val existing = box.query(
                ObxTelegramMessage_.chatId.equal(entity.chatId)
                    .and(ObxTelegramMessage_.messageId.equal(entity.messageId))
            ).build().findFirst()
            
            if (existing != null) {
                box.put(entity.copy(id = existing.id))
            } else {
                box.put(entity)
            }
        }

    override suspend fun getAllChatIds(): List<Long> =
        withContext(Dispatchers.IO) {
            box.query()
                .build()
                .property(ObxTelegramMessage_.chatId)
                .distinct()
                .findLongs()
                .toList()
        }

    override suspend fun count(): Long =
        withContext(Dispatchers.IO) {
            box.count()
        }

    override suspend fun deleteAll() =
        withContext(Dispatchers.IO) {
            UnifiedLog.d(TAG, "deleteAll()")
            box.removeAll()
        }

    // ========================================================================
    // Mapping: ObxTelegramMessage ↔ RawMediaMetadata
    // ========================================================================

    /**
     * Convert ObjectBox entity to RawMediaMetadata.
     *
     * ## v2 remoteId-First Architecture
     *
     * Per `contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`:
     * - Uses `posterRemoteId` or `thumbRemoteId` to build ImageRef.TelegramThumb
     * - Includes chatId/messageId for resolution fallback
     * - `fileId` resolved at runtime via `getRemoteFile(remoteId)`
     */
    private fun ObxTelegramMessage.toRawMediaMetadata(): RawMediaMetadata {
        val derivedMediaType = when {
            isSeries -> MediaType.SERIES_EPISODE
            mimeType?.startsWith("audio/") == true -> MediaType.UNKNOWN
            else -> MediaType.MOVIE
        }

        // Build thumbnail ImageRef from remoteId (v2 architecture)
        val thumbnailRef = when {
            posterRemoteId != null -> ImageRef.TelegramThumb(
                remoteId = posterRemoteId!!,
                chatId = chatId,
                messageId = messageId
            )
            thumbRemoteId != null -> ImageRef.TelegramThumb(
                remoteId = thumbRemoteId!!,
                chatId = chatId,
                messageId = messageId
            )
            else -> null
        }

        // Build ExternalIds from TMDB/IMDB if available
        // Per MEDIA_NORMALIZATION_CONTRACT.md: TMDB type is derived from mediaType
        val externalIds = tmdbId?.toIntOrNull()?.let { id ->
            val tmdbType = when (derivedMediaType) {
                MediaType.SERIES_EPISODE -> TmdbMediaType.TV
                MediaType.MOVIE -> TmdbMediaType.MOVIE
                else -> TmdbMediaType.MOVIE // Default to MOVIE for unknown
            }
            ExternalIds(
                tmdb = TmdbRef(tmdbType, id),
                imdbId = imdbId
            )
        } ?: imdbId?.let {
            ExternalIds(imdbId = it)
        } ?: ExternalIds()

        return RawMediaMetadata(
            originalTitle = title ?: caption ?: fileName ?: "Unknown",
            year = year,
            season = seasonNumber,
            episode = episodeNumber,
            durationMs = durationSecs?.let { it * 1000L },
            sourceType = SourceType.TELEGRAM,
            sourceLabel = "Telegram Chat: $chatId",
            sourceId = "msg:$chatId:$messageId",
            mediaType = derivedMediaType,
            thumbnail = thumbnailRef,
            poster = thumbnailRef,
            externalIds = externalIds
        )
    }

    /**
     * Convert RawMediaMetadata to ObxTelegramMessage entity.
     *
     * Extracts remoteId from ImageRef.TelegramThumb to persist thumbnail references.
     * Per TELEGRAM_ID_ARCHITECTURE_CONTRACT.md: Only remoteId is stored; fileId resolved at runtime.
     */
    private fun RawMediaMetadata.toObxEntity(): ObxTelegramMessage? {
        val (chatId, messageId) = parseSourceId(sourceId) ?: return null

        // Extract remoteId from ImageRef.TelegramThumb for poster/thumbnail
        val posterRemoteId = (poster as? ImageRef.TelegramThumb)?.remoteId
        val thumbRemoteId = (thumbnail as? ImageRef.TelegramThumb)?.remoteId
            ?: posterRemoteId // Fallback: use poster as thumbnail if no separate thumbnail

        return ObxTelegramMessage(
            chatId = chatId,
            messageId = messageId,
            caption = originalTitle,
            captionLower = originalTitle.lowercase(),
            title = originalTitle,
            year = year,
            seasonNumber = season,
            episodeNumber = episode,
            durationSecs = durationMs?.let { (it / 1000).toInt() },
            isSeries = mediaType == MediaType.SERIES_EPISODE,
            date = System.currentTimeMillis() / 1000,
            // Persist thumbnail references for UI display
            posterRemoteId = posterRemoteId,
            thumbRemoteId = thumbRemoteId,
            // Persist external IDs for canonical identity
            tmdbId = externalIds.tmdb?.id?.toString(),
            imdbId = externalIds.imdbId
        )
    }

    private fun parseSourceId(sourceId: String): Pair<Long, Long>? {
        val parts = sourceId.split(":")
        if (parts.size < 3 || parts[0] != "msg") return null
        val chatId = parts[1].toLongOrNull() ?: return null
        val messageId = parts[2].toLongOrNull() ?: return null
        return chatId to messageId
    }
}
