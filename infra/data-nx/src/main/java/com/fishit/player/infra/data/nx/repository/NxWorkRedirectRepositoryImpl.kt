package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxWorkRedirectRepository
import com.fishit.player.core.model.repository.NxWorkRedirectRepository.Redirect
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import com.fishit.player.core.persistence.obx.NX_WorkRedirect
import com.fishit.player.core.persistence.obx.NX_WorkRedirect_
import com.fishit.player.infra.data.nx.mapper.toDomain
import com.fishit.player.infra.data.nx.mapper.toEntity
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBox implementation of [NxWorkRedirectRepository].
 *
 * Manages work key redirects for canonical merge/dedupe scenarios.
 */
@Singleton
class NxWorkRedirectRepositoryImpl
    @Inject
    constructor(
        boxStore: BoxStore,
    ) : NxWorkRedirectRepository {
        private val box: Box<NX_WorkRedirect> = boxStore.boxFor(NX_WorkRedirect::class.java)

        // ──────────────────────────────────────────────────────────────────────────
        // Reads
        // ──────────────────────────────────────────────────────────────────────────

        /**
         * Resolves a work key to its canonical target, following redirect chains.
         * Returns the original key if no redirect exists.
         */
        override suspend fun resolve(workKey: String): String =
            withContext(Dispatchers.IO) {
                var currentKey = workKey
                var depth = 0
                val maxDepth = 10 // Prevent infinite loops

                while (depth < maxDepth) {
                    val redirect =
                        box
                            .query(NX_WorkRedirect_.oldWorkKey.equal(currentKey, StringOrder.CASE_SENSITIVE))
                            .build()
                            .findFirst()

                    if (redirect == null) {
                        return@withContext currentKey
                    }

                    currentKey = redirect.newWorkKey
                    depth++
                }

                // Max depth reached, return current (shouldn't happen in practice)
                currentKey
            }

        override fun observeAll(): Flow<List<Redirect>> =
            box
                .query()
                .order(NX_WorkRedirect_.createdAt)
                .build()
                .asFlow()
                .map { list -> list.map { it.toDomain() } }

        // ──────────────────────────────────────────────────────────────────────────
        // Writes
        // ──────────────────────────────────────────────────────────────────────────

        override suspend fun upsertRedirect(redirect: Redirect): Redirect =
            withContext(Dispatchers.IO) {
                val existing =
                    box
                        .query(NX_WorkRedirect_.oldWorkKey.equal(redirect.obsoleteWorkKey, StringOrder.CASE_SENSITIVE))
                        .build()
                        .findFirst()

                val entity =
                    if (existing != null) {
                        existing.apply {
                            newWorkKey = redirect.targetWorkKey
                        }
                    } else {
                        redirect.toEntity()
                    }

                box.put(entity)
                entity.toDomain()
            }

        override suspend fun delete(obsoleteWorkKey: String): Boolean =
            withContext(Dispatchers.IO) {
                val deleted =
                    box
                        .query(NX_WorkRedirect_.oldWorkKey.equal(obsoleteWorkKey, StringOrder.CASE_SENSITIVE))
                        .build()
                        .remove()
                deleted > 0
            }
    }
