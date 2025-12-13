package com.fishit.player.infra.data.telegram

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.feature.telegram.domain.TelegramMediaItem
import com.fishit.player.feature.telegram.domain.TelegramMediaRepository
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter that implements the feature's TelegramMediaRepository interface.
 *
 * **Architecture (Dependency Inversion):**
 * - Feature layer defines TelegramMediaRepository interface
 * - Data layer provides this implementation
 * - Feature depends on interface (not on infra module)
 * - Infra depends on feature (to implement interface)
 *
 * **Responsibility:**
 * - Maps RawMediaMetadata (from TelegramContentRepository) → TelegramMediaItem (domain model)
 * - Shields feature layer from pipeline concerns
 */
@Singleton
class TelegramMediaRepositoryAdapter @Inject constructor(
    private val contentRepository: TelegramContentRepository,
) : TelegramMediaRepository {

    override fun observeAll(): Flow<List<TelegramMediaItem>> {
        return contentRepository.observeAll()
            .map { rawItems -> rawItems.map { it.toTelegramMediaItem() } }
    }

    override fun observeByChat(chatId: Long): Flow<List<TelegramMediaItem>> {
        return contentRepository.observeByChat(chatId)
            .map { rawItems -> rawItems.map { it.toTelegramMediaItem() } }
    }

    override suspend fun getById(mediaId: String): TelegramMediaItem? {
        return contentRepository.getBySourceId(mediaId)?.toTelegramMediaItem()
    }

    override suspend fun search(query: String, limit: Int): List<TelegramMediaItem> {
        return contentRepository.search(query, limit).map { it.toTelegramMediaItem() }
    }

    companion object {
        private const val TAG = "TelegramMediaRepositoryAdapter"
    }
}

/**
 * Maps RawMediaMetadata to TelegramMediaItem (domain model).
 *
 * Extracts chatId and messageId from sourceId format: "msg:chatId:messageId"
 */
private fun RawMediaMetadata.toTelegramMediaItem(): TelegramMediaItem {
    // Extract chatId and messageId from sourceId
    val parts = sourceId.split(":")
    val chatId = parts.getOrNull(1)?.toLongOrNull()
    val messageId = parts.getOrNull(2)?.toLongOrNull()

    // Extract posterUrl from ImageRef
    val posterUrl = poster?.let { imageRef ->
        when (imageRef) {
            is ImageRef.Http -> imageRef.url
            is ImageRef.TelegramThumb -> "tg://thumb/${imageRef.fileId}/${imageRef.uniqueId}"
            is ImageRef.LocalFile -> "file://${imageRef.path}"
            else -> null
        }
    }

    return TelegramMediaItem(
        mediaId = sourceId,
        title = originalTitle,
        sourceLabel = sourceLabel,
        mediaType = mediaType,
        durationMinutes = durationMinutes,
        posterUrl = posterUrl,
        chatId = chatId,
        messageId = messageId,
    )
}
