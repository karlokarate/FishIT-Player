/**
 * ObjectBox implementation of [NxWorkSourceRefRepository].
 *
 * Links Works to pipeline source items (Telegram, Xtream, etc.).
 */
package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceRef
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceType
import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.NX_WorkSourceRef
import com.fishit.player.core.persistence.obx.NX_WorkSourceRef_
import com.fishit.player.core.persistence.obx.NX_Work_
import com.fishit.player.infra.data.nx.mapper.toDomain
import com.fishit.player.infra.data.nx.mapper.toEntity
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBox-backed repository for source references.
 */
@Singleton
class NxWorkSourceRefRepositoryImpl @Inject constructor(
    private val boxStore: BoxStore,
) : NxWorkSourceRefRepository {
    private val box by lazy { boxStore.boxFor<NX_WorkSourceRef>() }
    private val workBox by lazy { boxStore.boxFor<NX_Work>() }

    // ──────────────────────────────────────────────────────────────────────
    // Reads
    // ──────────────────────────────────────────────────────────────────────

    override suspend fun getBySourceKey(sourceKey: String): SourceRef? = withContext(Dispatchers.IO) {
        box.query(NX_WorkSourceRef_.sourceKey.equal(sourceKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()
            ?.toDomain()
    }

    override fun observeByWorkKey(workKey: String): Flow<List<SourceRef>> {
        // Get work entity to find its ID for filtering
        val workId = workBox.query(NX_Work_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()?.id ?: 0L

        // Use full table scan with in-memory filter for ToOne relation
        // TODO: Optimize with proper ObjectBox link query if needed for large datasets
        return box.query()
            .build()
            .asFlow()
            .map { list -> list.filter { it.work.targetId == workId }.map { it.toDomain() } }
    }

    override suspend fun findByWorkKey(workKey: String): List<SourceRef> = withContext(Dispatchers.IO) {
        val workId = workBox.query(NX_Work_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()?.id ?: return@withContext emptyList()

        // Filter in memory by work.targetId
        box.all
            .filter { it.work.targetId == workId }
            .map { it.toDomain() }
    }

    override suspend fun findByAccount(sourceType: SourceType, accountKey: String, limit: Int): List<SourceRef> = withContext(Dispatchers.IO) {
        val typeString = sourceType.toEntityString()
        box.query(
            NX_WorkSourceRef_.sourceType.equal(typeString, StringOrder.CASE_SENSITIVE)
                .and(NX_WorkSourceRef_.accountKey.equal(accountKey, StringOrder.CASE_SENSITIVE)),
        )
            .build()
            .find(0, limit.toLong())
            .map { it.toDomain() }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Writes
    // ──────────────────────────────────────────────────────────────────────

    override suspend fun upsert(sourceRef: SourceRef): SourceRef = withContext(Dispatchers.IO) {
        require(sourceRef.accountKey.isNotBlank()) { "accountKey cannot be blank" }

        val existing = box.query(NX_WorkSourceRef_.sourceKey.equal(sourceRef.sourceKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()
        val entity = sourceRef.toEntity(existing)

        // Link to work entity
        val work = workBox.query(NX_Work_.workKey.equal(sourceRef.workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()
        if (work != null) {
            entity.work.target = work
        }

        box.put(entity)
        entity.toDomain()
    }

    override suspend fun upsertBatch(sourceRefs: List<SourceRef>): List<SourceRef> = withContext(Dispatchers.IO) {
        if (sourceRefs.isEmpty()) return@withContext emptyList()

        // Validate all have accountKey
        sourceRefs.forEach { require(it.accountKey.isNotBlank()) { "accountKey cannot be blank for ${it.sourceKey}" } }

        // Batch lookup existing entities
        val sourceKeys = sourceRefs.map { it.sourceKey }
        val existingMap = box.query(NX_WorkSourceRef_.sourceKey.oneOf(sourceKeys.toTypedArray(), StringOrder.CASE_SENSITIVE))
            .build()
            .find()
            .associateBy { it.sourceKey }

        // Batch lookup work entities
        val workKeys = sourceRefs.map { it.workKey }.distinct()
        val workMap = workBox.query(NX_Work_.workKey.oneOf(workKeys.toTypedArray(), StringOrder.CASE_SENSITIVE))
            .build()
            .find()
            .associateBy { it.workKey }

        val entities = sourceRefs.map { sourceRef ->
            val entity = sourceRef.toEntity(existingMap[sourceRef.sourceKey])
            workMap[sourceRef.workKey]?.let { entity.work.target = it }
            entity
        }

        box.put(entities)
        entities.map { it.toDomain() }
    }

    override suspend fun updateLastSeen(sourceKey: String, lastSeenAtMs: Long): Boolean = withContext(Dispatchers.IO) {
        val entity = box.query(NX_WorkSourceRef_.sourceKey.equal(sourceKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst() ?: return@withContext false

        box.put(entity.copy(lastSeenAt = lastSeenAtMs))
        true
    }

    // ──────────────────────────────────────────────────────────────────────
    // Bulk Delete
    // ──────────────────────────────────────────────────────────────────────

    override suspend fun deleteBySourceType(sourceType: SourceType): Int = withContext(Dispatchers.IO) {
        val typeString = sourceType.toEntityString()
        val query = box.query(NX_WorkSourceRef_.sourceType.equal(typeString, StringOrder.CASE_SENSITIVE))
            .build()
        val count = query.count().toInt()
        query.remove()
        count
    }

    override suspend fun deleteByAccountKey(accountKey: String): Int = withContext(Dispatchers.IO) {
        val query = box.query(NX_WorkSourceRef_.accountKey.equal(accountKey, StringOrder.CASE_SENSITIVE))
            .build()
        val count = query.count().toInt()
        query.remove()
        count
    }

    // ──────────────────────────────────────────────────────────────────────
    // Pattern Search (for legacy ID lookups)
    // ──────────────────────────────────────────────────────────────────────

    override suspend fun findBySourceTypeAndKind(
        sourceType: SourceType,
        itemKind: NxWorkSourceRefRepository.SourceItemKind,
        itemKeyPrefix: String?,
    ): List<SourceRef> = withContext(Dispatchers.IO) {
        val typeString = sourceType.toEntityString()
        val kindString = itemKindToString(itemKind)

        val query = box.query(
            NX_WorkSourceRef_.sourceType.equal(typeString, StringOrder.CASE_SENSITIVE),
        ).build()

        // Filter in memory for item kind and optional prefix
        // (ObjectBox doesn't support nested field queries well)
        query.find()
            .filter { entity ->
                val sourceKeyParts = entity.sourceKey.split(":")
                // sourceKey format: src:sourceType:accountKey:itemKind:itemKey
                if (sourceKeyParts.size >= 5) {
                    val entityKind = sourceKeyParts[3]
                    val entityItemKey = sourceKeyParts[4]
                    entityKind.equals(kindString, ignoreCase = true) &&
                        (itemKeyPrefix == null || entityItemKey.startsWith(itemKeyPrefix))
                } else {
                    false
                }
            }
            .map { it.toDomain() }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────

    private fun SourceType.toEntityString(): String = when (this) {
        SourceType.TELEGRAM -> "telegram"
        SourceType.XTREAM -> "xtream"
        SourceType.IO -> "io"
        SourceType.LOCAL -> "local"
        SourceType.PLEX -> "plex"
        SourceType.UNKNOWN -> "unknown"
    }

    private fun itemKindToString(kind: NxWorkSourceRefRepository.SourceItemKind): String = when (kind) {
        NxWorkSourceRefRepository.SourceItemKind.VOD -> "vod"
        NxWorkSourceRefRepository.SourceItemKind.SERIES -> "series"
        NxWorkSourceRefRepository.SourceItemKind.EPISODE -> "episode"
        NxWorkSourceRefRepository.SourceItemKind.LIVE -> "live"
        NxWorkSourceRefRepository.SourceItemKind.FILE -> "file"
        NxWorkSourceRefRepository.SourceItemKind.UNKNOWN -> "unknown"
    }
}
