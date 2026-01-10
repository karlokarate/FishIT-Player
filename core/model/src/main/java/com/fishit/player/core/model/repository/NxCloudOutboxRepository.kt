/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - DOMAIN interface only.
 * - Outbox is for future cloud sync; do NOT implement cloud writes here.
 * - Worker will dequeue and upload later.
 * - Implementation maps to NX_CloudOutboxEvent entity in infra/data-nx.
 */
package com.fishit.player.core.model.repository

import kotlinx.coroutines.flow.Flow

interface NxCloudOutboxRepository {

    enum class EventType {
        UPSERT_USER_STATE,
        UPSERT_PROFILE,
        UPSERT_PROFILE_RULE,
        UPSERT_PROFILE_USAGE,
        UNKNOWN,
    }

    data class OutboxEvent(
        val eventId: String, // deterministic or random; impl decides
        val eventType: EventType,
        val entityKey: String,
        val payloadJson: String,
        val createdAtMs: Long = 0L,
        val attemptCount: Int = 0,
        val nextAttemptAtMs: Long = 0L,
        val lastError: String? = null,
    )

    suspend fun enqueue(event: OutboxEvent): OutboxEvent

    suspend fun dequeueBatch(nowMs: Long, limit: Int = 100): List<OutboxEvent>

    suspend fun markSuccess(eventId: String): Boolean

    suspend fun markFailure(eventId: String, nowMs: Long, error: String): Boolean

    fun observePendingCount(): Flow<Long>
}

