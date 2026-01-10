/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - DOMAIN interface only.
 * - Usage is aggregate per profileKey + epochDay (UTC).
 * - Implementation maps to NX_ProfileUsageDay entity in infra/data-nx.
 */
package com.fishit.player.core.model.repository

import kotlinx.coroutines.flow.Flow

interface NxProfileUsageRepository {

    data class UsageDay(
        val profileKey: String,
        val epochDay: Int,
        val watchedMs: Long,
        val sessionsCount: Int,
        val lastUpdatedAtMs: Long = 0L,
    )

    suspend fun get(profileKey: String, epochDay: Int): UsageDay?

    fun observeRange(profileKey: String, fromEpochDay: Int, toEpochDay: Int): Flow<List<UsageDay>>

    suspend fun upsert(day: UsageDay): UsageDay
}


