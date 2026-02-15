/**
 * ObjectBox implementation of [NxWorkDiagnostics].
 *
 * Provides diagnostic queries for Works - NOT for UI hot paths.
 * Used in debug screens and verifier workers.
 */
package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxWorkDiagnostics
import com.fishit.player.core.model.repository.NxWorkRepository.Work
import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.NX_WorkSourceRef
import com.fishit.player.core.persistence.obx.NX_WorkVariant
import com.fishit.player.core.persistence.obx.NX_Work_
import com.fishit.player.infra.data.nx.mapper.WorkTypeMapper
import com.fishit.player.infra.data.nx.mapper.toDomain
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Diagnostics implementation for canonical Works.
 *
 * All methods run on IO dispatcher.
 */
@Singleton
class NxWorkDiagnosticsImpl
    @Inject
    constructor(
        private val boxStore: BoxStore,
    ) : NxWorkDiagnostics {
        private val workBox by lazy { boxStore.boxFor<NX_Work>() }
        private val sourceRefBox by lazy { boxStore.boxFor<NX_WorkSourceRef>() }
        private val variantBox by lazy { boxStore.boxFor<NX_WorkVariant>() }

        override suspend fun countAll(): Long =
            withContext(Dispatchers.IO) {
                workBox.count()
            }

        override suspend fun countByType(type: WorkType): Long =
            withContext(Dispatchers.IO) {
                val typeString = WorkTypeMapper.toEntityString(type)
                workBox
                    .query(NX_Work_.workType.equal(typeString, StringOrder.CASE_SENSITIVE))
                    .build()
                    .count()
            }

        override suspend fun findWorksMissingSources(limit: Int): List<Work> =
            withContext(Dispatchers.IO) {
                // Find all work IDs that have at least one source ref
                val workIdsWithSources =
                    sourceRefBox.all
                        .mapNotNull { it.work?.target?.id }
                        .toSet()

                // Return works that don't have any source refs
                workBox.all
                    .filter { it.id !in workIdsWithSources }
                    .take(limit)
                    .map { it.toDomain() }
            }

        override suspend fun findWorksMissingVariants(limit: Int): List<Work> =
            withContext(Dispatchers.IO) {
                // Find all work IDs that have at least one variant
                val workIdsWithVariants =
                    variantBox.all
                        .mapNotNull { it.work?.target?.id }
                        .toSet()

                // Return works that don't have any variants
                workBox.all
                    .filter { it.id !in workIdsWithVariants }
                    .take(limit)
                    .map { it.toDomain() }
            }

        override suspend fun countNeedsReview(): Long =
            withContext(Dispatchers.IO) {
                workBox
                    .query(NX_Work_.needsReview.equal(true))
                    .build()
                    .count()
            }
    }
