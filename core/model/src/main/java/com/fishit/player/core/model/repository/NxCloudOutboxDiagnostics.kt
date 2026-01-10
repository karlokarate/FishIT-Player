/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - NOT for UI hot paths.
 */
package com.fishit.player.core.model.repository

interface NxCloudOutboxDiagnostics {
    suspend fun countAll(): Long
    suspend fun countPending(nowMs: Long): Long
    suspend fun findStuck(nowMs: Long, olderThanMs: Long, limit: Int = 200): List<NxCloudOutboxRepository.OutboxEvent>
}

