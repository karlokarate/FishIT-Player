/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - DOMAIN interface only.
 * - RuntimeState is ephemeral/ticking: keep it out of NxWork.
 * - Implementation maps to NX_WorkRuntimeState entity in infra/data-nx.
 * - Remove this header after implementation + verifier checks exist.
 */
package com.fishit.player.core.model.repository

import kotlinx.coroutines.flow.Flow

interface NxWorkRuntimeStateRepository {

    data class RuntimeState(
        val workKey: String,
        val isAvailable: Boolean = true,
        val lastProbeAtMs: Long? = null,
        val lastErrorCode: String? = null,
        val ttlUntilMs: Long? = null,

        val nowTitle: String? = null,
        val nowStartMs: Long? = null,
        val nowEndMs: Long? = null,

        val nextTitle: String? = null,
        val nextStartMs: Long? = null,
        val nextEndMs: Long? = null,
    )

    suspend fun get(workKey: String): RuntimeState?

    fun observe(workKey: String): Flow<RuntimeState?>

    fun observeForWorks(workKeys: List<String>): Flow<List<RuntimeState>>

    suspend fun upsert(state: RuntimeState): RuntimeState

    suspend fun upsertBatch(states: List<RuntimeState>): List<RuntimeState>

    suspend fun delete(workKey: String): Boolean
}

