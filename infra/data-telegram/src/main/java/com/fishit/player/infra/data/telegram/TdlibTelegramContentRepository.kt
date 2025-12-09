package com.fishit.player.infra.data.telegram

import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory TelegramContentRepository implementation.
 *
 * **Architecture Compliance:**
 * - Works only with RawMediaMetadata (no pipeline DTOs)
 * - Receives data from catalog pipeline via upsertAll()
 * - Provides data to Domain/UI layers
 *
 * **Phase 2 Status:** Stub implementation with in-memory storage.
 * Production will use ObjectBox persistence.
 *
 * **Layer Boundaries:**
 * - Transport → Pipeline → Data → Domain → UI
 * - This repository sits in Data layer
 * - Consumes RawMediaMetadata from Pipeline (via CatalogSync)
 * - Serves RawMediaMetadata to Domain/UI
 */
class TdlibTelegramContentRepository : TelegramContentRepository {

    companion object {
        private const val TAG = "TdlibTelegramContentRepository"
    }

    // In-memory storage (Phase 2 stub - will be ObjectBox in production)
    private val storage = MutableStateFlow<Map<String, RawMediaMetadata>>(emptyMap())

    override fun observeAll(): Flow<List<RawMediaMetadata>> =
        storage.map { it.values.toList() }

    override fun observeByChat(chatId: Long): Flow<List<RawMediaMetadata>> =
        storage.map { map ->
            map.values.filter { it.sourceId.contains(":$chatId:") }
        }

    override suspend fun getAll(limit: Int, offset: Int): List<RawMediaMetadata> {
        UnifiedLog.d(TAG, "getAll(limit=$limit, offset=$offset)")
        return storage.value.values.toList().drop(offset).take(limit)
    }

    override suspend fun getBySourceId(sourceId: String): RawMediaMetadata? {
        return storage.value[sourceId]
    }

    override suspend fun search(query: String, limit: Int): List<RawMediaMetadata> {
        val lowerQuery = query.lowercase()
        return storage.value.values
            .filter { it.originalTitle.lowercase().contains(lowerQuery) }
            .take(limit)
    }

    override suspend fun upsertAll(items: List<RawMediaMetadata>) {
        UnifiedLog.d(TAG, "upsertAll(${items.size} items)")
        val current = storage.value.toMutableMap()
        items.forEach { item ->
            current[item.sourceId] = item
        }
        storage.value = current
    }

    override suspend fun upsert(item: RawMediaMetadata) {
        val current = storage.value.toMutableMap()
        current[item.sourceId] = item
        storage.value = current
    }

    override suspend fun getAllChatIds(): List<Long> {
        return storage.value.values
            .mapNotNull { extractChatId(it.sourceId) }
            .distinct()
    }

    override suspend fun count(): Long = storage.value.size.toLong()

    override suspend fun deleteAll() {
        UnifiedLog.d(TAG, "deleteAll()")
        storage.value = emptyMap()
    }

    /**
     * Extract chat ID from sourceId format "msg:chatId:messageId"
     */
    private fun extractChatId(sourceId: String): Long? {
        val parts = sourceId.split(":")
        return if (parts.size >= 2 && parts[0] == "msg") {
            parts[1].toLongOrNull()
        } else {
            null
        }
    }
}
