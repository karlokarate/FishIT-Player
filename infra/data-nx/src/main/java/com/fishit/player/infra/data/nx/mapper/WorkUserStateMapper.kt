package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.userstate.CloudSyncState
import com.fishit.player.core.model.userstate.WorkUserState
import com.fishit.player.core.persistence.obx.NX_WorkUserState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper between NX_WorkUserState entity and WorkUserState domain model.
 *
 * ## Key Transformations
 * - Entity uses `profileId: Long` → Domain uses `profileKey: String`
 * - Entity uses `lastWatchedAt: Long` (0 = null) → Domain uses `lastWatchedAtMs: Long?`
 *
 * ## PLATIN Architecture
 * - This mapper lives in `infra/data-nx` (implementation layer)
 * - Maps between persistence entity and domain model
 * - Domain model is source of truth for business logic
 */
@Singleton
class WorkUserStateMapper
    @Inject
    constructor() {
        /**
         * Maps NX_WorkUserState entity to WorkUserState domain model.
         *
         * @param entity The ObjectBox entity to convert
         * @param profileKeyResolver Function to resolve profileId → profileKey
         * @return WorkUserState domain model
         */
        fun toDomain(
            entity: NX_WorkUserState,
            profileKeyResolver: (Long) -> String,
        ): WorkUserState =
            WorkUserState(
                profileKey = profileKeyResolver(entity.profileId),
                workKey = entity.workKey,
                resumePositionMs = entity.resumePositionMs,
                totalDurationMs = entity.totalDurationMs,
                isWatched = entity.isWatched,
                watchCount = entity.watchCount,
                isFavorite = entity.isFavorite,
                userRating = entity.userRating?.takeIf { it > 0 },
                inWatchlist = entity.inWatchlist,
                isHidden = entity.isHidden,
                lastWatchedAtMs = entity.lastWatchedAt?.takeIf { it > 0L },
                createdAtMs = entity.createdAt,
                updatedAtMs = entity.updatedAt,
                // Cloud sync fields not yet in entity - default to LOCAL_ONLY
                lastUpdatedByDeviceId = null,
                cloudSyncState = CloudSyncState.LOCAL_ONLY,
            )

        /**
         * Maps NX_WorkUserState entity to WorkUserState domain model using default profile key.
         *
         * @param entity The ObjectBox entity to convert
         * @return WorkUserState domain model with profileId as string profileKey
         */
        fun toDomain(entity: NX_WorkUserState): WorkUserState = toDomain(entity) { profileId -> profileId.toString() }

        /**
         * Maps WorkUserState domain model to NX_WorkUserState entity.
         *
         * @param domain The domain model to convert
         * @param profileIdResolver Function to resolve profileKey → profileId
         * @param existingId Optional existing entity ID for updates (0 for new entities)
         * @return NX_WorkUserState entity ready for persistence
         */
        fun toEntity(
            domain: WorkUserState,
            profileIdResolver: (String) -> Long,
            existingId: Long = 0L,
        ): NX_WorkUserState =
            NX_WorkUserState(
                id = existingId,
                profileId = profileIdResolver(domain.profileKey),
                workKey = domain.workKey,
                resumePositionMs = domain.resumePositionMs,
                totalDurationMs = domain.totalDurationMs,
                isWatched = domain.isWatched,
                watchCount = domain.watchCount,
                isFavorite = domain.isFavorite,
                userRating = domain.userRating ?: 0,
                inWatchlist = domain.inWatchlist,
                isHidden = domain.isHidden,
                lastWatchedAt = domain.lastWatchedAtMs ?: 0L,
                createdAt = domain.createdAtMs,
                updatedAt = domain.updatedAtMs,
            )

        /**
         * Maps WorkUserState domain model to NX_WorkUserState entity using default profile ID resolver.
         *
         * @param domain The domain model to convert
         * @param existingId Optional existing entity ID for updates (0 for new entities)
         * @return NX_WorkUserState entity ready for persistence
         */
        fun toEntity(
            domain: WorkUserState,
            existingId: Long = 0L,
        ): NX_WorkUserState = toEntity(domain, { key -> key.toLongOrNull() ?: 0L }, existingId)
    }
