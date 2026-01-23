package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxWorkUserStateDiagnostics
import com.fishit.player.core.model.userstate.WorkUserState
import com.fishit.player.core.persistence.obx.NX_WorkUserState
import com.fishit.player.core.persistence.obx.NX_WorkUserState_
import com.fishit.player.infra.data.nx.mapper.WorkUserStateMapper
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBox implementation of [NxWorkUserStateDiagnostics].
 *
 * ## Purpose
 * - NOT for UI hot paths (feature layer)
 * - Intended for verifier workers and debug tooling
 * - Used by Settings → Debug → NX Data Inspector
 *
 * ## Implementation Notes
 * - All operations run on IO dispatcher
 * - validateState() is pure (no IO)
 */
@Singleton
class NxWorkUserStateDiagnosticsImpl @Inject constructor(
    private val boxStore: BoxStore,
    private val mapper: WorkUserStateMapper,
) : NxWorkUserStateDiagnostics {

    private val box by lazy { boxStore.boxFor<NX_WorkUserState>() }

    override suspend fun countAll(): Long = withContext(Dispatchers.IO) {
        box.count()
    }

    override suspend fun countByProfile(profileKey: String): Long = withContext(Dispatchers.IO) {
        val profileId = profileKey.toLongOrNull() ?: 0L
        box.query {
            equal(NX_WorkUserState_.profileId, profileId)
        }.count()
    }

    /**
     * Finds orphaned states where workKey references a non-existent work.
     *
     * NOTE: This requires cross-referencing with NX_Work table.
     * For now, returns states with empty or malformed workKeys.
     */
    override suspend fun findOrphanedStates(limit: Int): List<WorkUserState> =
        withContext(Dispatchers.IO) {
            // Find states with empty or invalid workKeys
            box.query {
                equal(NX_WorkUserState_.workKey, "", StringOrder.CASE_SENSITIVE)
            }.find().take(limit).map { mapper.toDomain(it) }
        }

    /**
     * Finds states with invalid ratings (outside 1-5 range).
     */
    override suspend fun findInvalidRatings(limit: Int): List<WorkUserState> =
        withContext(Dispatchers.IO) {
            // Find all states and filter for invalid ratings
            box.all.filter { entity ->
                val rating = entity.userRating
                rating != null && (rating < 1 || rating > 5)
            }.take(limit).map { mapper.toDomain(it) }
        }

    /**
     * Finds duplicate states (same profileKey + workKey combination).
     *
     * This should never happen if the composite key constraint is enforced,
     * but this method helps detect data corruption.
     */
    override suspend fun findDuplicateStates(limit: Int): List<WorkUserState> =
        withContext(Dispatchers.IO) {
            val all = box.all
            val seen = mutableSetOf<String>()
            val duplicates = mutableListOf<NX_WorkUserState>()

            for (entity in all) {
                val key = "${entity.profileId}:${entity.workKey}"
                if (key in seen) {
                    duplicates.add(entity)
                    if (duplicates.size >= limit) break
                } else {
                    seen.add(key)
                }
            }

            duplicates.map { mapper.toDomain(it) }
        }

    /**
     * Validates a WorkUserState and returns a list of validation errors.
     *
     * This is a pure function (no IO).
     *
     * @param state The state to validate
     * @return List of validation error messages (empty if valid)
     */
    override fun validateState(state: WorkUserState): List<String> {
        val errors = mutableListOf<String>()

        // Validate profileKey
        if (state.profileKey.isBlank()) {
            errors.add("profileKey cannot be blank")
        }

        // Validate workKey
        if (state.workKey.isBlank()) {
            errors.add("workKey cannot be blank")
        }

        // Validate resumePositionMs
        if (state.resumePositionMs < 0) {
            errors.add("resumePositionMs cannot be negative: ${state.resumePositionMs}")
        }

        // Validate totalDurationMs
        if (state.totalDurationMs < 0) {
            errors.add("totalDurationMs cannot be negative: ${state.totalDurationMs}")
        }

        // Validate resume vs duration
        if (state.resumePositionMs > state.totalDurationMs && state.totalDurationMs > 0) {
            errors.add(
                "resumePositionMs (${state.resumePositionMs}) exceeds " +
                    "totalDurationMs (${state.totalDurationMs})",
            )
        }

        // Validate watchCount
        if (state.watchCount < 0) {
            errors.add("watchCount cannot be negative: ${state.watchCount}")
        }

        // Validate userRating
        state.userRating?.let { rating ->
            if (rating !in 1..5) {
                errors.add("userRating must be between 1 and 5: $rating")
            }
        }

        // Validate timestamps
        if (state.createdAtMs <= 0) {
            errors.add("createdAtMs must be positive: ${state.createdAtMs}")
        }

        if (state.updatedAtMs <= 0) {
            errors.add("updatedAtMs must be positive: ${state.updatedAtMs}")
        }

        if (state.updatedAtMs < state.createdAtMs) {
            errors.add(
                "updatedAtMs (${state.updatedAtMs}) cannot be before " +
                    "createdAtMs (${state.createdAtMs})",
            )
        }

        state.lastWatchedAtMs?.let { lastWatched ->
            if (lastWatched <= 0) {
                errors.add("lastWatchedAtMs must be positive when set: $lastWatched")
            }
        }

        return errors
    }
}
