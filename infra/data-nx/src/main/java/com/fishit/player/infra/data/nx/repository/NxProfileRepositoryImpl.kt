package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxProfileRepository
import com.fishit.player.core.model.repository.NxProfileRepository.Profile
import com.fishit.player.core.persistence.obx.NX_Profile
import com.fishit.player.core.persistence.obx.NX_Profile_
import com.fishit.player.infra.data.nx.mapper.toDomain
import com.fishit.player.infra.data.nx.mapper.toEntity
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.kotlin.flow
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBox implementation of [NxProfileRepository].
 *
 * Manages user profiles (main, kids, guest).
 */
@Singleton
class NxProfileRepositoryImpl @Inject constructor(
    boxStore: BoxStore,
) : NxProfileRepository {

    private val box: Box<NX_Profile> = boxStore.boxFor(NX_Profile::class.java)

    // ──────────────────────────────────────────────────────────────────────────
    // Reads
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun get(profileKey: String): Profile? = withContext(Dispatchers.IO) {
        box.query(NX_Profile_.profileKey.equal(profileKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()
            ?.toDomain()
    }

    override fun observeAll(): Flow<List<Profile>> =
        box.query()
            .order(NX_Profile_.createdAt)
            .build()
            .flow()
            .map { list -> list.map { it.toDomain() } }

    // ──────────────────────────────────────────────────────────────────────────
    // Writes
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun upsert(profile: Profile): Profile = withContext(Dispatchers.IO) {
        val existing = box.query(NX_Profile_.profileKey.equal(profile.profileKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()

        val entity = if (existing != null) {
            existing.apply {
                name = profile.displayName
                avatarUrl = profile.avatarKey
                profileType = if (profile.isKids) "KIDS" else "MAIN"
                lastUsedAt = System.currentTimeMillis()
            }
        } else {
            profile.toEntity()
        }

        box.put(entity)
        entity.toDomain()
    }

    override suspend fun softDelete(profileKey: String): Boolean = withContext(Dispatchers.IO) {
        // Since entity doesn't have soft delete, we actually remove it
        // In production, consider adding isDeleted flag to entity
        val deleted = box.query(NX_Profile_.profileKey.equal(profileKey, StringOrder.CASE_SENSITIVE))
            .build()
            .remove()
        deleted > 0
    }
}
