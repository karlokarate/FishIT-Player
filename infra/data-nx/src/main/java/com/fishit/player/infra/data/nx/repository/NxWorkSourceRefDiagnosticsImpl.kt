/**
 * ObjectBox implementation of [NxWorkSourceRefDiagnostics].
 */
package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxWorkSourceRefDiagnostics
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceRef
import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.NX_WorkSourceRef
import com.fishit.player.core.persistence.obx.NX_WorkSourceRef_
import com.fishit.player.infra.data.nx.mapper.toDomain
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Diagnostics implementation for source references.
 */
@Singleton
class NxWorkSourceRefDiagnosticsImpl @Inject constructor(
    private val boxStore: BoxStore,
) : NxWorkSourceRefDiagnostics {
    private val box by lazy { boxStore.boxFor<NX_WorkSourceRef>() }
    private val workBox by lazy { boxStore.boxFor<NX_Work>() }

    override suspend fun countAll(): Long = withContext(Dispatchers.IO) {
        box.count()
    }

    override suspend fun findMissingAccountKey(limit: Int): List<SourceRef> = withContext(Dispatchers.IO) {
        box.query(NX_WorkSourceRef_.accountKey.equal("", StringOrder.CASE_SENSITIVE))
            .build()
            .find(0, limit.toLong())
            .map { it.toDomain() }
    }

    override suspend fun findInvalidSourceKeyFormat(limit: Int): List<SourceRef> = withContext(Dispatchers.IO) {
        // SSOT format: "src:<sourceType>:<accountKey>:<sourceItemKind>:<sourceItemKey>"
        // Find entries that don't start with "src:" or don't have enough parts
        box.all
            .filter { !isValidSourceKeyFormat(it.sourceKey) }
            .take(limit)
            .map { it.toDomain() }
    }

    override suspend fun findOrphaned(limit: Int): List<SourceRef> = withContext(Dispatchers.IO) {
        // Find source refs whose work relationship is null or points to non-existent work
        val validWorkIds = workBox.all.map { it.id }.toSet()

        box.all
            .filter { it.work.targetId == 0L || it.work.targetId !in validWorkIds }
            .take(limit)
            .map { it.toDomain() }
    }

    /**
     * Validates sourceKey follows SSOT format.
     * Expected: "src:<sourceType>:<accountKey>:<sourceItemKind>:<sourceItemKey>"
     * Minimum: at least has sourceType and accountKey parts
     */
    private fun isValidSourceKeyFormat(sourceKey: String): Boolean {
        if (sourceKey.isBlank()) return false
        val parts = sourceKey.split(":")
        // At minimum: src:type:accountKey (3 parts for legacy) or full 5-part format
        return parts.size >= 3
    }
}
