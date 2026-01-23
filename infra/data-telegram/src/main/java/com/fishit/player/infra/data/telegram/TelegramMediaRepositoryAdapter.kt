package com.fishit.player.infra.data.telegram

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.toUriString
import com.fishit.player.core.telegrammedia.domain.TelegramMediaItem
import com.fishit.player.core.telegrammedia.domain.TelegramMediaRepository
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
 *
 * @deprecated Use [NxTelegramMediaRepositoryImpl] instead. This adapter uses legacy
 * TelegramContentRepository which is being phased out in favor of NX_* entities.
 */
@Deprecated(
    message = "Use NxTelegramMediaRepositoryImpl instead. Legacy OBX adapter scheduled for removal.",
    replaceWith = ReplaceWith(
        "NxTelegramMediaRepositoryImpl",
        "com.fishit.player.infra.data.telegram.NxTelegramMediaRepositoryImpl"
    ),
    level = DeprecationLevel.WARNING
)
@Singleton
class TelegramMediaRepositoryAdapter
    @Inject
    constructor(
        private val contentRepository: TelegramContentRepository,
    ) : TelegramMediaRepository {
        override fun observeAll(): Flow<List<TelegramMediaItem>> =
            contentRepository
                .observeAll()
                .map { rawItems -> rawItems.map { it.toTelegramMediaItem() } }

        override fun observeByChat(chatId: Long): Flow<List<TelegramMediaItem>> =
            contentRepository
                .observeByChat(chatId)
                .map { rawItems -> rawItems.map { it.toTelegramMediaItem() } }

        override suspend fun getById(mediaId: String): TelegramMediaItem? = contentRepository.getBySourceId(mediaId)?.toTelegramMediaItem()

        override suspend fun search(
            query: String,
            limit: Int,
        ): List<TelegramMediaItem> =
            contentRepository.search(query, limit).map {
                it.toTelegramMediaItem()
            }

        companion object {
            private const val TAG = "TelegramMediaRepositoryAdapter"
        }
    }

/**
 * Maps RawMediaMetadata to TelegramMediaItem (domain model).
 *
 * Extracts chatId and messageId from sourceId format: "msg:chatId:messageId"
 *
 * ## v2 remoteId-First Architecture
 *
 * Per `contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`:
 * - Uses only `remoteId` for Telegram thumbnails
 * - `fileId` resolved at runtime via `getRemoteFile(remoteId)`
 */
private fun RawMediaMetadata.toTelegramMediaItem(): TelegramMediaItem {
    // Extract chatId and messageId from sourceId
    val parts = sourceId.split(":")
    val chatId = parts.getOrNull(1)?.toLongOrNull()
    val messageId = parts.getOrNull(2)?.toLongOrNull()

    // Prefer Telegram video thumbnails if poster is missing (videos usually have thumbnail, not poster)
    val primaryImage = thumbnail ?: poster

    // Extract posterUrl from ImageRef using remoteId-first URI format
    val posterUrl =
        primaryImage?.let { imageRef ->
            when (imageRef) {
                is ImageRef.Http -> imageRef.url
                is ImageRef.TelegramThumb -> imageRef.toUriString()
                is ImageRef.LocalFile -> "file://${imageRef.path}"
                else -> null
            }
        }

    // Playback-critical Telegram identifiers (v2): carried via playbackHints
    val remoteId = playbackHints[PlaybackHintKeys.Telegram.REMOTE_ID]
    val mimeType = playbackHints[PlaybackHintKeys.Telegram.MIME_TYPE]

    return TelegramMediaItem(
        mediaId = sourceId,
        title = originalTitle,
        sourceLabel = sourceLabel,
        mediaType = mediaType,
        durationMs = durationMs,
        posterUrl = posterUrl,
        chatId = chatId,
        messageId = messageId,
        remoteId = remoteId,
        mimeType = mimeType,
    )
}
