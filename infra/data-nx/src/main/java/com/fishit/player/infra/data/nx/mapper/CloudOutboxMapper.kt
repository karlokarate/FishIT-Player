package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.repository.NxCloudOutboxRepository
import com.fishit.player.core.persistence.obx.NX_CloudOutboxEvent

/**
 * Mapper between NX_CloudOutboxEvent entity and NxCloudOutboxRepository.OutboxEvent domain model.
 */

internal fun NX_CloudOutboxEvent.toDomain(): NxCloudOutboxRepository.OutboxEvent =
    NxCloudOutboxRepository.OutboxEvent(
        eventId = id.toString(), // Use entity ID as event ID
        eventType =
            when (eventType) {
                "UPSERT_USER_STATE" -> NxCloudOutboxRepository.EventType.UPSERT_USER_STATE
                "UPSERT_PROFILE" -> NxCloudOutboxRepository.EventType.UPSERT_PROFILE
                "UPSERT_PROFILE_RULE" -> NxCloudOutboxRepository.EventType.UPSERT_PROFILE_RULE
                "UPSERT_PROFILE_USAGE" -> NxCloudOutboxRepository.EventType.UPSERT_PROFILE_USAGE
                else -> NxCloudOutboxRepository.EventType.UNKNOWN
            },
        entityKey = entityKey,
        payloadJson = payload,
        createdAtMs = createdAt,
        attemptCount = retryCount,
        nextAttemptAtMs = lastAttemptAt ?: createdAt, // Use last attempt as next attempt base
        lastError = lastError,
    )

internal fun NxCloudOutboxRepository.OutboxEvent.toEntity(): NX_CloudOutboxEvent =
    NX_CloudOutboxEvent(
        eventType = eventType.name,
        entityType = eventType.name.substringAfter("UPSERT_").lowercase(),
        entityKey = entityKey,
        payload = payloadJson,
        syncStatus = "PENDING",
        retryCount = attemptCount,
        lastError = lastError,
        createdAt = if (createdAtMs > 0) createdAtMs else System.currentTimeMillis(),
        lastAttemptAt = nextAttemptAtMs.takeIf { it > 0 },
    )
