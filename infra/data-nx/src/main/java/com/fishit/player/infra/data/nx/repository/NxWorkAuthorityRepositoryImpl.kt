package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxWorkAuthorityRepository
import com.fishit.player.core.model.repository.NxWorkAuthorityRepository.AuthorityRef
import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.NX_Work_
import com.fishit.player.infra.data.nx.mapper.buildAuthorityKey
import com.fishit.player.infra.data.nx.mapper.toAuthorityRef
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
 * ObjectBox implementation of [NxWorkAuthorityRepository].
 *
 * Authority refs (TMDB, IMDB, TVDB) are stored directly on NX_Work entity
 * rather than in a separate table. This implementation maps to/from NX_Work.authorityKey.
 */
@Singleton
class NxWorkAuthorityRepositoryImpl @Inject constructor(
    boxStore: BoxStore,
) : NxWorkAuthorityRepository {

    private val workBox: Box<NX_Work> = boxStore.boxFor(NX_Work::class.java)

    // ──────────────────────────────────────────────────────────────────────────
    // Reads
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun findWorkKeyByAuthorityKey(authorityKey: String): String? = withContext(Dispatchers.IO) {
        workBox.query(NX_Work_.authorityKey.equal(authorityKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()
            ?.workKey
    }

    override fun observeForWork(workKey: String): Flow<List<AuthorityRef>> =
        workBox.query(NX_Work_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .flow()
            .map { works ->
                works.firstOrNull()?.toAuthorityRef()?.let { listOf(it) } ?: emptyList()
            }

    // ──────────────────────────────────────────────────────────────────────────
    // Writes
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun upsert(ref: AuthorityRef): AuthorityRef = withContext(Dispatchers.IO) {
        val work = workBox.query(NX_Work_.workKey.equal(ref.workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst() ?: error("Work not found: ${ref.workKey}")

        val authorityKey = buildAuthorityKey(ref.authorityType, ref.namespace, ref.authorityId)

        work.authorityKey = authorityKey
        work.updatedAt = System.currentTimeMillis()

        // Also set specific ID fields if applicable
        when (ref.authorityType) {
            NxWorkAuthorityRepository.AuthorityType.TMDB -> work.tmdbId = ref.authorityId
            NxWorkAuthorityRepository.AuthorityType.IMDB -> work.imdbId = ref.authorityId
            NxWorkAuthorityRepository.AuthorityType.TVDB -> work.tvdbId = ref.authorityId
            else -> { /* No specific field */ }
        }

        workBox.put(work)
        ref.copy(matchedAtMs = work.updatedAt)
    }

    override suspend fun delete(authorityKey: String): Boolean = withContext(Dispatchers.IO) {
        val work = workBox.query(NX_Work_.authorityKey.equal(authorityKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst() ?: return@withContext false

        work.authorityKey = null
        work.tmdbId = null
        work.imdbId = null
        work.tvdbId = null
        work.updatedAt = System.currentTimeMillis()

        workBox.put(work)
        true
    }
}
