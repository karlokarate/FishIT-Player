package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxWorkRuntimeStateRepository
import com.fishit.player.core.model.repository.NxWorkRuntimeStateRepository.RuntimeState
import com.fishit.player.core.persistence.obx.NX_WorkRuntimeState
import com.fishit.player.core.persistence.obx.NX_WorkRuntimeState_
import com.fishit.player.infra.data.nx.mapper.toDomain
import com.fishit.player.infra.data.nx.mapper.toEntity
import io.objectbox.Box
import io.objectbox.BoxStore
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBox implementation of [NxWorkRuntimeStateRepository].
 *
 * Manages transient runtime state for works (availability, errors, EPG now/next).
 */
@Singleton
class NxWorkRuntimeStateRepositoryImpl @Inject constructor(
    boxStore: BoxStore,
) : NxWorkRuntimeStateRepository {

    private val box: Box<NX_WorkRuntimeState> = boxStore.boxFor(NX_WorkRuntimeState::class.java)

    // ──────────────────────────────────────────────────────────────────────────
    // Reads
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun get(workKey: String): RuntimeState? = withContext(Dispatchers.IO) {
        box.query(NX_WorkRuntimeState_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()
            ?.toDomain()
    }

    override fun observe(workKey: String): Flow<RuntimeState?> =
        box.query(NX_WorkRuntimeState_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .asFlow()
            .map { list -> list.firstOrNull()?.toDomain() }

    override fun observeForWorks(workKeys: List<String>): Flow<List<RuntimeState>> =
        box.query(NX_WorkRuntimeState_.workKey.oneOf(workKeys.toTypedArray(), StringOrder.CASE_SENSITIVE))
            .build()
            .asFlow()
            .map { list -> list.map { it.toDomain() } }

    // ──────────────────────────────────────────────────────────────────────────
    // Writes
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun upsert(state: RuntimeState): RuntimeState = withContext(Dispatchers.IO) {
        val existing = box.query(NX_WorkRuntimeState_.workKey.equal(state.workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()

        val entity = if (existing != null) {
            // Update existing entity
            existing.apply {
                stateType = when {
                    state.lastErrorCode != null -> "ERROR"
                    !state.isAvailable -> "UNAVAILABLE"
                    else -> "AVAILABLE"
                }
                errorCode = state.lastErrorCode
                errorMessage = state.lastErrorCode?.let { "Error: $it" }
                updatedAt = state.lastProbeAtMs ?: System.currentTimeMillis()
            }
        } else {
            state.toEntity()
        }

        box.put(entity)
        entity.toDomain()
    }

    override suspend fun upsertBatch(states: List<RuntimeState>): List<RuntimeState> = withContext(Dispatchers.IO) {
        states.map { state ->
            val existing = box.query(NX_WorkRuntimeState_.workKey.equal(state.workKey, StringOrder.CASE_SENSITIVE))
                .build()
                .findFirst()

            if (existing != null) {
                existing.apply {
                    stateType = when {
                        state.lastErrorCode != null -> "ERROR"
                        !state.isAvailable -> "UNAVAILABLE"
                        else -> "AVAILABLE"
                    }
                    errorCode = state.lastErrorCode
                    errorMessage = state.lastErrorCode?.let { "Error: $it" }
                    updatedAt = state.lastProbeAtMs ?: System.currentTimeMillis()
                }
            } else {
                state.toEntity()
            }
        }.also { entities ->
            box.put(entities)
        }.map { it.toDomain() }
    }

    override suspend fun delete(workKey: String): Boolean = withContext(Dispatchers.IO) {
        val deleted = box.query(NX_WorkRuntimeState_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .remove()
        deleted > 0
    }
}
