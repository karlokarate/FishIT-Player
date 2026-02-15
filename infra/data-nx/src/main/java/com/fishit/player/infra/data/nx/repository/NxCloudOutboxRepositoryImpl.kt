package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxCloudOutboxRepository
import com.fishit.player.core.model.repository.NxCloudOutboxRepository.OutboxEvent
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import com.fishit.player.core.persistence.obx.NX_CloudOutboxEvent
import com.fishit.player.core.persistence.obx.NX_CloudOutboxEvent_
import com.fishit.player.infra.data.nx.mapper.toDomain
import com.fishit.player.infra.data.nx.mapper.toEntity
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBox implementation of [NxCloudOutboxRepository].
 *
 * Manages cloud sync outbox events (pending uploads to cloud backend).
 */
@Singleton
class NxCloudOutboxRepositoryImpl
    @Inject
    constructor(
        boxStore: BoxStore,
    ) : NxCloudOutboxRepository {
        private val box: Box<NX_CloudOutboxEvent> = boxStore.boxFor(NX_CloudOutboxEvent::class.java)

        // ──────────────────────────────────────────────────────────────────────────
        // Writes
        // ──────────────────────────────────────────────────────────────────────────

        override suspend fun enqueue(event: OutboxEvent): OutboxEvent =
            withContext(Dispatchers.IO) {
                val entity = event.toEntity()
                box.put(entity)
                entity.toDomain()
            }

        override suspend fun dequeueBatch(
            nowMs: Long,
            limit: Int,
        ): List<OutboxEvent> =
            withContext(Dispatchers.IO) {
                box
                    .query()
                    .equal(NX_CloudOutboxEvent_.syncStatus, "PENDING", StringOrder.CASE_SENSITIVE)
                    .order(NX_CloudOutboxEvent_.createdAt)
                    .build()
                    .find(0, limit.toLong())
                    .map { it.toDomain() }
            }

        override suspend fun markSuccess(eventId: String): Boolean =
            withContext(Dispatchers.IO) {
                val id = eventId.toLongOrNull() ?: return@withContext false
                val entity = box.get(id) ?: return@withContext false

                entity.syncStatus = "SYNCED"
                entity.lastAttemptAt = System.currentTimeMillis()
                box.put(entity)
                true
            }

        override suspend fun markFailure(
            eventId: String,
            nowMs: Long,
            error: String,
        ): Boolean =
            withContext(Dispatchers.IO) {
                val id = eventId.toLongOrNull() ?: return@withContext false
                val entity = box.get(id) ?: return@withContext false

                entity.syncStatus = "FAILED"
                entity.retryCount++
                entity.lastError = error
                entity.lastAttemptAt = nowMs
                box.put(entity)
                true
            }

        // ──────────────────────────────────────────────────────────────────────────
        // Reads
        // ──────────────────────────────────────────────────────────────────────────

        override fun observePendingCount(): Flow<Long> =
            box
                .query()
                .equal(NX_CloudOutboxEvent_.syncStatus, "PENDING", StringOrder.CASE_SENSITIVE)
                .build()
                .asFlow()
                .map { list -> list.size.toLong() }
    }
