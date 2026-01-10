/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - DOMAIN interface only (no ObjectBox entities / no BoxStore).
 * - Implementation lives in infra/data-nx and maps to NX_WorkUserState entity.
 * - SSOT identity: (profileKey, workKey) — NEVER use local DB ids as SSOT.
 * - Every write MUST set:
 *     lastUpdatedAtMs = nowEpochMillis
 *     lastUpdatedByDeviceId = deviceInstallId
 *     cloudSyncState = DIRTY
 * - Keep MVP small; diagnostics/stats go to NxWorkUserStateDiagnostics.
 * - Remove this header after infra/data-nx implementation + integration tests are green.
 */
package com.fishit.player.core.model.repository

import com.fishit.player.core.model.userstate.WorkUserState
import kotlinx.coroutines.flow.Flow

interface NxWorkUserStateRepository {
    suspend fun get(
        profileKey: String,
        workKey: String,
    ): WorkUserState?

    fun observe(
        profileKey: String,
        workKey: String,
    ): Flow<WorkUserState?>

    fun observeContinueWatching(
        profileKey: String,
        limit: Int = 20,
    ): Flow<List<WorkUserState>>

    fun observeFavorites(
        profileKey: String,
        limit: Int = 100,
    ): Flow<List<WorkUserState>>

    fun observeWatchlist(
        profileKey: String,
        limit: Int = 100,
    ): Flow<List<WorkUserState>>

    suspend fun updateResumePosition(
        profileKey: String,
        workKey: String,
        positionMs: Long,
        durationMs: Long,
    ): WorkUserState

    suspend fun clearResumePosition(
        profileKey: String,
        workKey: String,
    ): Boolean

    suspend fun markAsWatched(
        profileKey: String,
        workKey: String,
    ): WorkUserState

    suspend fun markAsUnwatched(
        profileKey: String,
        workKey: String,
    ): WorkUserState

    suspend fun addToFavorites(
        profileKey: String,
        workKey: String,
    ): WorkUserState

    suspend fun removeFromFavorites(
        profileKey: String,
        workKey: String,
    ): WorkUserState

    suspend fun addToWatchlist(
        profileKey: String,
        workKey: String,
    ): WorkUserState

    suspend fun removeFromWatchlist(
        profileKey: String,
        workKey: String,
    ): WorkUserState

    suspend fun hideFromRecommendations(
        profileKey: String,
        workKey: String,
    ): WorkUserState

    suspend fun unhideFromRecommendations(
        profileKey: String,
        workKey: String,
    ): WorkUserState

    suspend fun setRating(
        profileKey: String,
        workKey: String,
        rating: Int,
    ): WorkUserState

    suspend fun clearRating(
        profileKey: String,
        workKey: String,
    ): WorkUserState
}
