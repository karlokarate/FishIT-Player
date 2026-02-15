package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxWorkUserStateRepository
import com.fishit.player.core.model.userstate.WorkUserState
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import com.fishit.player.core.persistence.ObjectBoxFlow.asSingleFlow
import com.fishit.player.core.persistence.obx.NX_WorkUserState
import com.fishit.player.core.persistence.obx.NX_WorkUserState_
import com.fishit.player.infra.data.nx.mapper.WorkUserStateMapper
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBox implementation of [NxWorkUserStateRepository].
 *
 * ## SSOT Contract Compliance
 * - Uses composite key (profileKey + workKey) as SSOT identity
 * - Sets `updatedAt` on every write
 * - Never exposes ObjectBox IDs as SSOT identifiers
 *
 * ## Implementation Notes
 * - profileKey is stored as profileId (Long) via simple parsing
 * - Future: profileKey resolution via ProfileRepository
 *
 * @see contracts/NX_SSOT_CONTRACT.md
 */
@Singleton
class NxWorkUserStateRepositoryImpl
    @Inject
    constructor(
        private val boxStore: BoxStore,
        private val mapper: WorkUserStateMapper,
    ) : NxWorkUserStateRepository {
        private val box by lazy { boxStore.boxFor<NX_WorkUserState>() }

        // =========================================================================
        // Read Operations
        // =========================================================================

        override suspend fun get(
            profileKey: String,
            workKey: String,
        ): WorkUserState? =
            withContext(Dispatchers.IO) {
                box
                    .query {
                        equal(NX_WorkUserState_.profileId, profileKey.toLongOrNull() ?: 0L)
                        equal(NX_WorkUserState_.workKey, workKey, StringOrder.CASE_SENSITIVE)
                    }.findFirst()
                    ?.let { mapper.toDomain(it) }
            }

        override fun observe(
            profileKey: String,
            workKey: String,
        ): Flow<WorkUserState?> {
            val query =
                box.query {
                    equal(NX_WorkUserState_.profileId, profileKey.toLongOrNull() ?: 0L)
                    equal(NX_WorkUserState_.workKey, workKey, StringOrder.CASE_SENSITIVE)
                }
            return query.asSingleFlow().map { entity ->
                entity?.let { mapper.toDomain(it) }
            }
        }

        override fun observeContinueWatching(
            profileKey: String,
            limit: Int,
        ): Flow<List<WorkUserState>> {
            val query =
                box.query {
                    equal(NX_WorkUserState_.profileId, profileKey.toLongOrNull() ?: 0L)
                    greater(NX_WorkUserState_.resumePositionMs, 0L)
                    equal(NX_WorkUserState_.isWatched, false)
                    orderDesc(NX_WorkUserState_.lastWatchedAt)
                }
            return query.asFlow().map { entities ->
                entities.take(limit).map { mapper.toDomain(it) }
            }
        }

        override fun observeFavorites(
            profileKey: String,
            limit: Int,
        ): Flow<List<WorkUserState>> {
            val query =
                box.query {
                    equal(NX_WorkUserState_.profileId, profileKey.toLongOrNull() ?: 0L)
                    equal(NX_WorkUserState_.isFavorite, true)
                    orderDesc(NX_WorkUserState_.updatedAt)
                }
            return query.asFlow().map { entities ->
                entities.take(limit).map { mapper.toDomain(it) }
            }
        }

        override fun observeWatchlist(
            profileKey: String,
            limit: Int,
        ): Flow<List<WorkUserState>> {
            val query =
                box.query {
                    equal(NX_WorkUserState_.profileId, profileKey.toLongOrNull() ?: 0L)
                    equal(NX_WorkUserState_.inWatchlist, true)
                    orderDesc(NX_WorkUserState_.updatedAt)
                }
            return query.asFlow().map { entities ->
                entities.take(limit).map { mapper.toDomain(it) }
            }
        }

        // =========================================================================
        // Resume Position Operations
        // =========================================================================

        override suspend fun updateResumePosition(
            profileKey: String,
            workKey: String,
            positionMs: Long,
            durationMs: Long,
        ): WorkUserState =
            withContext(Dispatchers.IO) {
                val profileId = profileKey.toLongOrNull() ?: 0L
                val existing = findByCompositeKey(profileId, workKey)
                val now = System.currentTimeMillis()

                val entity =
                    existing?.copy(
                        resumePositionMs = positionMs,
                        totalDurationMs = durationMs,
                        lastWatchedAt = now,
                        updatedAt = now,
                    ) ?: NX_WorkUserState(
                        profileId = profileId,
                        workKey = workKey,
                        resumePositionMs = positionMs,
                        totalDurationMs = durationMs,
                        lastWatchedAt = now,
                        createdAt = now,
                        updatedAt = now,
                    )

                box.put(entity)
                mapper.toDomain(entity)
            }

        override suspend fun clearResumePosition(
            profileKey: String,
            workKey: String,
        ): Boolean =
            withContext(Dispatchers.IO) {
                val profileId = profileKey.toLongOrNull() ?: 0L
                val existing = findByCompositeKey(profileId, workKey) ?: return@withContext false

                box.put(
                    existing.copy(
                        resumePositionMs = 0L,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                true
            }

        // =========================================================================
        // Watch Status Operations
        // =========================================================================

        override suspend fun markAsWatched(
            profileKey: String,
            workKey: String,
        ): WorkUserState =
            withContext(Dispatchers.IO) {
                val profileId = profileKey.toLongOrNull() ?: 0L
                val existing = findByCompositeKey(profileId, workKey)
                val now = System.currentTimeMillis()

                val entity =
                    existing?.copy(
                        isWatched = true,
                        watchCount = existing.watchCount + 1,
                        resumePositionMs = 0L, // Clear resume on mark watched
                        lastWatchedAt = now,
                        updatedAt = now,
                    ) ?: NX_WorkUserState(
                        profileId = profileId,
                        workKey = workKey,
                        isWatched = true,
                        watchCount = 1,
                        lastWatchedAt = now,
                        createdAt = now,
                        updatedAt = now,
                    )

                box.put(entity)
                mapper.toDomain(entity)
            }

        override suspend fun markAsUnwatched(
            profileKey: String,
            workKey: String,
        ): WorkUserState =
            withContext(Dispatchers.IO) {
                val profileId = profileKey.toLongOrNull() ?: 0L
                val existing = findByCompositeKey(profileId, workKey)
                val now = System.currentTimeMillis()

                val entity =
                    existing?.copy(
                        isWatched = false,
                        updatedAt = now,
                    ) ?: NX_WorkUserState(
                        profileId = profileId,
                        workKey = workKey,
                        isWatched = false,
                        createdAt = now,
                        updatedAt = now,
                    )

                box.put(entity)
                mapper.toDomain(entity)
            }

        // =========================================================================
        // Favorites Operations
        // =========================================================================

        override suspend fun addToFavorites(
            profileKey: String,
            workKey: String,
        ): WorkUserState =
            withContext(Dispatchers.IO) {
                val profileId = profileKey.toLongOrNull() ?: 0L
                val existing = findByCompositeKey(profileId, workKey)
                val now = System.currentTimeMillis()

                val entity =
                    existing?.copy(
                        isFavorite = true,
                        updatedAt = now,
                    ) ?: NX_WorkUserState(
                        profileId = profileId,
                        workKey = workKey,
                        isFavorite = true,
                        createdAt = now,
                        updatedAt = now,
                    )

                box.put(entity)
                mapper.toDomain(entity)
            }

        override suspend fun removeFromFavorites(
            profileKey: String,
            workKey: String,
        ): WorkUserState =
            withContext(Dispatchers.IO) {
                val profileId = profileKey.toLongOrNull() ?: 0L
                val existing =
                    findByCompositeKey(profileId, workKey) ?: run {
                        // Return a new empty state if none exists
                        return@withContext WorkUserState(profileKey = profileKey, workKey = workKey)
                    }
                val now = System.currentTimeMillis()

                val entity =
                    existing.copy(
                        isFavorite = false,
                        updatedAt = now,
                    )

                box.put(entity)
                mapper.toDomain(entity)
            }

        // =========================================================================
        // Watchlist Operations
        // =========================================================================

        override suspend fun addToWatchlist(
            profileKey: String,
            workKey: String,
        ): WorkUserState =
            withContext(Dispatchers.IO) {
                val profileId = profileKey.toLongOrNull() ?: 0L
                val existing = findByCompositeKey(profileId, workKey)
                val now = System.currentTimeMillis()

                val entity =
                    existing?.copy(
                        inWatchlist = true,
                        updatedAt = now,
                    ) ?: NX_WorkUserState(
                        profileId = profileId,
                        workKey = workKey,
                        inWatchlist = true,
                        createdAt = now,
                        updatedAt = now,
                    )

                box.put(entity)
                mapper.toDomain(entity)
            }

        override suspend fun removeFromWatchlist(
            profileKey: String,
            workKey: String,
        ): WorkUserState =
            withContext(Dispatchers.IO) {
                val profileId = profileKey.toLongOrNull() ?: 0L
                val existing =
                    findByCompositeKey(profileId, workKey) ?: run {
                        return@withContext WorkUserState(profileKey = profileKey, workKey = workKey)
                    }
                val now = System.currentTimeMillis()

                val entity =
                    existing.copy(
                        inWatchlist = false,
                        updatedAt = now,
                    )

                box.put(entity)
                mapper.toDomain(entity)
            }

        // =========================================================================
        // Hide/Show Operations
        // =========================================================================

        override suspend fun hideFromRecommendations(
            profileKey: String,
            workKey: String,
        ): WorkUserState =
            withContext(Dispatchers.IO) {
                val profileId = profileKey.toLongOrNull() ?: 0L
                val existing = findByCompositeKey(profileId, workKey)
                val now = System.currentTimeMillis()

                val entity =
                    existing?.copy(
                        isHidden = true,
                        updatedAt = now,
                    ) ?: NX_WorkUserState(
                        profileId = profileId,
                        workKey = workKey,
                        isHidden = true,
                        createdAt = now,
                        updatedAt = now,
                    )

                box.put(entity)
                mapper.toDomain(entity)
            }

        override suspend fun unhideFromRecommendations(
            profileKey: String,
            workKey: String,
        ): WorkUserState =
            withContext(Dispatchers.IO) {
                val profileId = profileKey.toLongOrNull() ?: 0L
                val existing =
                    findByCompositeKey(profileId, workKey) ?: run {
                        return@withContext WorkUserState(profileKey = profileKey, workKey = workKey)
                    }
                val now = System.currentTimeMillis()

                val entity =
                    existing.copy(
                        isHidden = false,
                        updatedAt = now,
                    )

                box.put(entity)
                mapper.toDomain(entity)
            }

        // =========================================================================
        // Rating Operations
        // =========================================================================

        override suspend fun setRating(
            profileKey: String,
            workKey: String,
            rating: Int,
        ): WorkUserState =
            withContext(Dispatchers.IO) {
                require(rating in 1..5) { "Rating must be between 1 and 5" }

                val profileId = profileKey.toLongOrNull() ?: 0L
                val existing = findByCompositeKey(profileId, workKey)
                val now = System.currentTimeMillis()

                val entity =
                    existing?.copy(
                        userRating = rating,
                        updatedAt = now,
                    ) ?: NX_WorkUserState(
                        profileId = profileId,
                        workKey = workKey,
                        userRating = rating,
                        createdAt = now,
                        updatedAt = now,
                    )

                box.put(entity)
                mapper.toDomain(entity)
            }

        override suspend fun clearRating(
            profileKey: String,
            workKey: String,
        ): WorkUserState =
            withContext(Dispatchers.IO) {
                val profileId = profileKey.toLongOrNull() ?: 0L
                val existing =
                    findByCompositeKey(profileId, workKey) ?: run {
                        return@withContext WorkUserState(profileKey = profileKey, workKey = workKey)
                    }
                val now = System.currentTimeMillis()

                val entity =
                    existing.copy(
                        userRating = null,
                        updatedAt = now,
                    )

                box.put(entity)
                mapper.toDomain(entity)
            }

        // =========================================================================
        // Private Helpers
        // =========================================================================

        private fun findByCompositeKey(
            profileId: Long,
            workKey: String,
        ): NX_WorkUserState? =
            box
                .query {
                    equal(NX_WorkUserState_.profileId, profileId)
                    equal(NX_WorkUserState_.workKey, workKey, StringOrder.CASE_SENSITIVE)
                }.findFirst()
    }
