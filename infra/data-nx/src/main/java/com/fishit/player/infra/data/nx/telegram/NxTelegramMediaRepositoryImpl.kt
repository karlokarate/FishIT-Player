/**
 * NX-based TelegramMediaRepository implementation.
 *
 * This implementation reads from the NX work graph (v2 SSOT) instead of the
 * legacy TelegramContentRepository/RawMediaMetadata layer.
 *
 * **Architecture:**
 * - Feature layer (feature:telegram-media) defines TelegramMediaRepository interface
 * - This implementation provides the data from NX repositories
 * - All reads go through NxWorkRepository, NxWorkSourceRefRepository
 *
 * **Migration Note:**
 * This replaces TelegramMediaRepositoryAdapter which reads from TelegramContentRepository.
 *
 * **Source Filtering:**
 * Filters works to only include those with Telegram source refs.
 */
package com.fishit.player.infra.data.nx.telegram

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkRepository.Work
import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.telegrammedia.domain.TelegramMediaItem
import com.fishit.player.core.telegrammedia.domain.TelegramMediaRepository
import com.fishit.player.infra.data.nx.mapper.MediaTypeMapper
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NxTelegramMediaRepositoryImpl @Inject constructor(
    private val workRepository: NxWorkRepository,
    private val sourceRefRepository: NxWorkSourceRefRepository,
) : TelegramMediaRepository {

    companion object {
        private const val TAG = "NxTelegramMediaRepo"
        private const val ALL_LIMIT = 500
        private const val SEARCH_LIMIT = 100
    }

    // ==================== Observe All ====================

    override fun observeAll(): Flow<List<TelegramMediaItem>> {
        // Get all content and filter for Telegram source
        return workRepository.observeRecentlyUpdated(limit = ALL_LIMIT)
            .map { works ->
                works.filter { work ->
                    // Check if this work has a Telegram source
                    hasTelegramSource(work.workKey)
                }.mapNotNull { work ->
                    work.toTelegramMediaItem()
                }
            }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe all Telegram media" }
                emit(emptyList())
            }
    }

    // ==================== Observe By Chat ====================

    override fun observeByChat(chatId: Long): Flow<List<TelegramMediaItem>> {
        // Filter by chat ID embedded in sourceKey
        return workRepository.observeRecentlyUpdated(limit = ALL_LIMIT)
            .map { works ->
                works.filter { work ->
                    // Check if this work has a Telegram source from this specific chat
                    hasTelegramSourceForChat(work.workKey, chatId)
                }.mapNotNull { work ->
                    work.toTelegramMediaItem(chatId = chatId)
                }
            }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe Telegram media for chat $chatId" }
                emit(emptyList())
            }
    }

    // ==================== Get By ID ====================

    override suspend fun getById(mediaId: String): TelegramMediaItem? {
        return try {
            // The mediaId could be a workKey or a sourceKey
            val work = workRepository.get(mediaId)
            work?.toTelegramMediaItem()
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to get Telegram media by id: $mediaId" }
            null
        }
    }

    // ==================== Search ====================

    override suspend fun search(query: String, limit: Int): List<TelegramMediaItem> {
        return try {
            val normalizedQuery = query.trim().lowercase()
            if (normalizedQuery.isBlank()) return emptyList()

            workRepository.searchByTitle(normalizedQuery, limit.coerceAtMost(SEARCH_LIMIT))
                .filter { work -> hasTelegramSource(work.workKey) }
                .mapNotNull { work -> work.toTelegramMediaItem() }
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Search failed for query: $query" }
            emptyList()
        }
    }

    // ==================== Helpers ====================

    private suspend fun hasTelegramSource(workKey: String): Boolean {
        val sourceRefs = sourceRefRepository.findByWorkKey(workKey)
        return sourceRefs.any { it.sourceType == NxWorkSourceRefRepository.SourceType.TELEGRAM }
    }

    private suspend fun hasTelegramSourceForChat(workKey: String, chatId: Long): Boolean {
        val sourceRefs = sourceRefRepository.findByWorkKey(workKey)
        return sourceRefs.any { ref ->
            ref.sourceType == NxWorkSourceRefRepository.SourceType.TELEGRAM &&
                ref.sourceKey.contains(":$chatId:")
        }
    }

    private suspend fun Work.toTelegramMediaItem(chatId: Long? = null): TelegramMediaItem {
        // Extract Telegram-specific data from source refs
        val telegramSourceRef = sourceRefRepository.findByWorkKey(workKey)
            .firstOrNull { it.sourceType == NxWorkSourceRefRepository.SourceType.TELEGRAM }

        // Parse chatId and messageId from sourceKey (format: src:TELEGRAM:account:kind:chatId:messageId)
        val extractedChatId = chatId ?: extractChatIdFromSourceKey(telegramSourceRef?.sourceKey)
        val messageId = extractMessageIdFromSourceKey(telegramSourceRef?.sourceKey)

        return TelegramMediaItem(
            mediaId = workKey,
            title = displayTitle,
            sourceLabel = telegramSourceRef?.sourceTitle ?: "Telegram",
            mediaType = MediaTypeMapper.toMediaType(type),
            durationMs = runtimeMs,
            posterUrl = posterRef?.let { serializeImageRefToUrl(it) },
            chatId = extractedChatId,
            messageId = messageId,
            remoteId = null, // TODO: Store in NX metadata if needed
            mimeType = null, // TODO: Store in NX metadata if needed
        )
    }

    private fun extractChatIdFromSourceKey(sourceKey: String?): Long? {
        if (sourceKey == null) return null
        // Format: src:TELEGRAM:account:kind:chatId:messageId
        val parts = sourceKey.split(":")
        return parts.getOrNull(4)?.toLongOrNull()
    }

    private fun extractMessageIdFromSourceKey(sourceKey: String?): Long? {
        if (sourceKey == null) return null
        // Format: src:TELEGRAM:account:kind:chatId:messageId
        val parts = sourceKey.split(":")
        return parts.getOrNull(5)?.toLongOrNull()
    }

    // Note: mapWorkTypeToMediaType removed - use MediaTypeMapper.toMediaType() instead

    private fun serializeImageRefToUrl(serialized: String): String? {
        val colonIndex = serialized.indexOf(':')
        if (colonIndex < 0) return null

        val type = serialized.substring(0, colonIndex)
        val value = serialized.substring(colonIndex + 1)

        return when (type) {
            "http" -> value
            "tg" -> "tg:$value" // Keep tg: prefix for Telegram thumbnails
            "file" -> "file://$value"
            else -> null
        }
    }
}
