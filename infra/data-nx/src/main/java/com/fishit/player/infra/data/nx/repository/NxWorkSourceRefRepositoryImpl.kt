/**
 * ObjectBox implementation of [NxWorkSourceRefRepository].
 *
 * Links Works to pipeline source items (Telegram, Xtream, etc.).
 */
package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceRef
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceType
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.NX_WorkSourceRef
import com.fishit.player.core.persistence.obx.NX_WorkSourceRef_
import com.fishit.player.core.persistence.obx.NX_Work_
import com.fishit.player.infra.data.nx.mapper.SourceKeyParser
import com.fishit.player.infra.data.nx.mapper.toDomain
import com.fishit.player.infra.data.nx.mapper.toEntity
import com.fishit.player.infra.logging.UnifiedLog
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
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
class NxWorkSourceRefRepositoryImpl
    @Inject
    constructor(
        private val boxStore: BoxStore,
    ) : NxWorkSourceRefRepository {
        private val box by lazy { boxStore.boxFor<NX_WorkSourceRef>() }
        private val workBox by lazy { boxStore.boxFor<NX_Work>() }

        init {
            // One-time backfill on background thread to avoid ANR.
            // Reactive Flows auto-correct when put() triggers re-emission.
            Thread({
                backfillDenormalizedWorkKeys()
            }, "backfill-srcref-workKey").start()
        }

        private fun backfillDenormalizedWorkKeys() {
            val srcBox = boxStore.boxFor(NX_WorkSourceRef::class.java)
            val empty =
                srcBox
                    .query(
                        NX_WorkSourceRef_.workKey.equal("", StringOrder.CASE_SENSITIVE),
                    ).build()
                    .find()
            if (empty.isEmpty()) return
            val updated =
                empty.mapNotNull { entity ->
                    val wk = entity.work.target?.workKey
                    if (!wk.isNullOrBlank()) {
                        entity.workKey = wk
                        entity
                    } else {
                        null
                    }
                }
            if (updated.isNotEmpty()) {
                srcBox.put(updated)
                UnifiedLog.i("NxWorkSourceRefRepo") { "Backfilled workKey on ${updated.size} pre-migration source refs" }
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // Reads
        // ──────────────────────────────────────────────────────────────────────

        override suspend fun getBySourceKey(sourceKey: String): SourceRef? =
            withContext(Dispatchers.IO) {
                box
                    .query(NX_WorkSourceRef_.sourceKey.equal(sourceKey, StringOrder.CASE_SENSITIVE))
                    .build()
                    .findFirst()
                    ?.toDomain()
            }

        override fun observeByWorkKey(workKey: String): Flow<List<SourceRef>> {
            // Indexed query on denormalized workKey field — no full table scan
            return box
                .query(NX_WorkSourceRef_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
                .build()
                .asFlow()
                .map { list -> list.map { it.toDomain() } }
        }

        override suspend fun findByWorkKey(workKey: String): List<SourceRef> =
            withContext(Dispatchers.IO) {
                // Indexed query on denormalized workKey field — no full table scan
                box
                    .query(NX_WorkSourceRef_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
                    .build()
                    .find()
                    .map { it.toDomain() }
            }

        /**
         * Batch lookup source refs for multiple work keys.
         *
         * **Performance Critical:** Uses single indexed `oneOf()` query on
         * denormalized `workKey` field — O(k) on requested keys, not O(n) on table size.
         */
        override suspend fun findByWorkKeysBatch(workKeys: List<String>): Map<String, List<SourceRef>> =
            withContext(Dispatchers.IO) {
                if (workKeys.isEmpty()) return@withContext emptyMap()

                // Indexed oneOf() query on denormalized workKey field — no full table scan
                val result = mutableMapOf<String, MutableList<SourceRef>>()
                box
                    .query(NX_WorkSourceRef_.workKey.oneOf(workKeys.toTypedArray(), StringOrder.CASE_SENSITIVE))
                    .build()
                    .find()
                    .forEach { entity ->
                        val wk = entity.workKey
                        if (wk.isNotEmpty()) {
                            result.getOrPut(wk) { mutableListOf() }.add(entity.toDomain())
                        }
                    }

                result
            }

        override suspend fun findByAccount(
            sourceType: SourceType,
            accountKey: String,
            limit: Int,
        ): List<SourceRef> =
            withContext(Dispatchers.IO) {
                val typeString = sourceType.toEntityString()
                box
                    .query(
                        NX_WorkSourceRef_.sourceType
                            .equal(typeString, StringOrder.CASE_SENSITIVE)
                            .and(NX_WorkSourceRef_.accountKey.equal(accountKey, StringOrder.CASE_SENSITIVE)),
                    ).build()
                    .find(0, limit.toLong())
                    .map { it.toDomain() }
            }

        // ──────────────────────────────────────────────────────────────────────
        // Writes
        // ──────────────────────────────────────────────────────────────────────

        override suspend fun upsert(sourceRef: SourceRef): SourceRef =
            withContext(Dispatchers.IO) {
                require(sourceRef.accountKey.isNotBlank()) { "accountKey cannot be blank" }

                val existing =
                    box
                        .query(NX_WorkSourceRef_.sourceKey.equal(sourceRef.sourceKey, StringOrder.CASE_SENSITIVE))
                        .build()
                        .findFirst()
                val entity = sourceRef.toEntity(existing)

                // Link to work entity
                val work =
                    workBox
                        .query(NX_Work_.workKey.equal(sourceRef.workKey, StringOrder.CASE_SENSITIVE))
                        .build()
                        .findFirst()
                if (work != null) {
                    entity.work.target = work
                }

                box.put(entity)
                entity.toDomain()
            }

        override suspend fun upsertBatch(sourceRefs: List<SourceRef>): List<SourceRef> =
            withContext(Dispatchers.IO) {
                if (sourceRefs.isEmpty()) return@withContext emptyList()

                try {
                    // Validate all have accountKey
                    sourceRefs.forEach { require(it.accountKey.isNotBlank()) { "accountKey cannot be blank for ${it.sourceKey}" } }

                    // CRITICAL FIX: Deduplicate sourceRefs by sourceKey within batch
                    val uniqueRefs = sourceRefs.associateBy { it.sourceKey }.values.toList()

                    if (uniqueRefs.size < sourceRefs.size) {
                        UnifiedLog.w("NxWorkSourceRefRepo") {
                            "Deduped ${sourceRefs.size - uniqueRefs.size} duplicate sourceKeys in batch"
                        }
                    }

                    // Use runInTx for atomic batch update
                    boxStore.runInTx {
                        // CRITICAL: Query existing entities INSIDE transaction!
                        val sourceKeys = uniqueRefs.map { it.sourceKey }
                        val existingMap =
                            box
                                .query(NX_WorkSourceRef_.sourceKey.oneOf(sourceKeys.toTypedArray(), StringOrder.CASE_SENSITIVE))
                                .build()
                                .find()
                                .associateBy { it.sourceKey }

                        // Batch lookup work entities
                        val workKeys = uniqueRefs.map { it.workKey }.distinct()
                        val workMap =
                            workBox
                                .query(NX_Work_.workKey.oneOf(workKeys.toTypedArray(), StringOrder.CASE_SENSITIVE))
                                .build()
                                .find()
                                .associateBy { it.workKey }

                        val entities =
                            uniqueRefs.map { sourceRef ->
                                val entity = sourceRef.toEntity(existingMap[sourceRef.sourceKey])
                                workMap[sourceRef.workKey]?.let { entity.work.target = it }
                                entity
                            }
                        box.put(entities)
                    }

                    uniqueRefs
                } finally {
                    // CRITICAL: Cleanup thread-local resources to prevent transaction leaks
                    try {
                        boxStore.closeThreadResources()
                    } catch (e: Exception) {
                        // Ignore cleanup errors
                    }
                }
            }

        override suspend fun updateLastSeen(
            sourceKey: String,
            lastSeenAtMs: Long,
        ): Boolean =
            withContext(Dispatchers.IO) {
                val entity =
                    box
                        .query(NX_WorkSourceRef_.sourceKey.equal(sourceKey, StringOrder.CASE_SENSITIVE))
                        .build()
                        .findFirst() ?: return@withContext false

                box.put(entity.copy(lastSeenAt = lastSeenAtMs))
                true
            }

        // ──────────────────────────────────────────────────────────────────────
        // Bulk Delete
        // ──────────────────────────────────────────────────────────────────────

        override suspend fun deleteBySourceType(sourceType: SourceType): Int =
            withContext(Dispatchers.IO) {
                val typeString = sourceType.toEntityString()
                val query =
                    box
                        .query(NX_WorkSourceRef_.sourceType.equal(typeString, StringOrder.CASE_SENSITIVE))
                        .build()
                val count = query.count().toInt()
                query.remove()
                count
            }

        override suspend fun deleteByAccountKey(accountKey: String): Int =
            withContext(Dispatchers.IO) {
                val query =
                    box
                        .query(NX_WorkSourceRef_.accountKey.equal(accountKey, StringOrder.CASE_SENSITIVE))
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
        ): List<SourceRef> =
            withContext(Dispatchers.IO) {
                val typeString = sourceType.toEntityString()
                val kindString = itemKindToString(itemKind)

                val query =
                    box
                        .query(
                            NX_WorkSourceRef_.sourceType.equal(typeString, StringOrder.CASE_SENSITIVE),
                        ).build()

                // Filter in memory for item kind and optional prefix
                // (ObjectBox doesn't support nested field queries well)
                query
                    .find()
                    .filter { entity ->
                        val parsed = SourceKeyParser.parse(entity.sourceKey)
                        if (parsed != null) {
                            val entityKind = parsed.itemKind
                            val entityItemKey = parsed.itemKey
                            entityKind.equals(kindString, ignoreCase = true) &&
                                (itemKeyPrefix == null || entityItemKey.startsWith(itemKeyPrefix))
                        } else {
                            false
                        }
                    }.map { it.toDomain() }
            }

        // ──────────────────────────────────────────────────────────────────────
        // Private helpers
        // ──────────────────────────────────────────────────────────────────────

        private fun SourceType.toEntityString(): String =
            when (this) {
                SourceType.TELEGRAM -> "telegram"
                SourceType.XTREAM -> "xtream"
                SourceType.IO -> "io"
                SourceType.LOCAL -> "local"
                SourceType.PLEX -> "plex"
                SourceType.UNKNOWN -> "unknown"
            }

        private fun itemKindToString(kind: NxWorkSourceRefRepository.SourceItemKind): String =
            when (kind) {
                NxWorkSourceRefRepository.SourceItemKind.VOD -> "vod"
                NxWorkSourceRefRepository.SourceItemKind.SERIES -> "series"
                NxWorkSourceRefRepository.SourceItemKind.EPISODE -> "episode"
                NxWorkSourceRefRepository.SourceItemKind.LIVE -> "live"
                NxWorkSourceRefRepository.SourceItemKind.FILE -> "file"
                NxWorkSourceRefRepository.SourceItemKind.UNKNOWN -> "unknown"
            }

        // ──────────────────────────────────────────────────────────────────────
        // Incremental Sync & New Episodes Detection
        // ──────────────────────────────────────────────────────────────────────

        /**
         * Finds series episodes that were modified after [sinceMs].
         *
         * Used for:
         * - "New Episodes" badge detection
         * - Incremental sync optimization (only process changed content)
         *
         * @param sinceMs Unix timestamp in milliseconds (e.g., user's last check time)
         * @param sourceType Optional filter by source type
         * @param limit Maximum results (default 100)
         * @return Source refs with episodes modified since the given timestamp
         */
        override suspend fun findSeriesUpdatedSince(
            sinceMs: Long,
            sourceType: SourceType?,
            limit: Int,
        ): List<SourceRef> =
            withContext(Dispatchers.IO) {
                // Build query with sourceLastModifiedMs filter
                val baseCondition = NX_WorkSourceRef_.sourceLastModifiedMs.greater(sinceMs)
                val query =
                    if (sourceType != null) {
                        val typeString = sourceType.toEntityString()
                        box
                            .query(
                                baseCondition.and(
                                    NX_WorkSourceRef_.sourceType.equal(typeString, StringOrder.CASE_SENSITIVE),
                                ),
                            ).build()
                    } else {
                        box.query(baseCondition).build()
                    }

                // Filter for EPISODE kind (series episodes) and order by lastModified desc
                // Note: We use contains(":episode:") pattern to detect episode refs
                // This avoids sourceKey split parsing issues when accountKey contains colons
                query
                    .find()
                    .filter { entity ->
                        // Episode refs identified by ":episode:" pattern in sourceKey
                        // Format: src:xtream:{accountKey}:episode:{id} or xtream:episode:{seriesId}:{s}:{e}
                        entity.sourceKey.contains(":episode:", ignoreCase = true)
                    }.sortedByDescending { it.sourceLastModifiedMs ?: 0L }
                    .take(limit)
                    .map { it.toDomain() }
            }

        /**
         * Finds work keys (series) that have episodes with updates since [sinceMs].
         *
         * This is the primary method for "New Episodes" badge:
         * - Gets the set of workKeys (series) that have new/updated episodes
         * - UI can then show badge on those series cards
         *
         * @param sinceMs Unix timestamp in milliseconds
         * @param sourceType Optional filter by source type
         * @return Set of workKeys (series globalIds) that have new episodes
         */
        override suspend fun findWorkKeysWithSeriesUpdates(
            sinceMs: Long,
            sourceType: SourceType?,
        ): Set<String> =
            withContext(Dispatchers.IO) {
                // Build query with sourceLastModifiedMs filter
                val baseCondition = NX_WorkSourceRef_.sourceLastModifiedMs.greater(sinceMs)
                val query =
                    if (sourceType != null) {
                        val typeString = sourceType.toEntityString()
                        box
                            .query(
                                baseCondition.and(
                                    NX_WorkSourceRef_.sourceType.equal(typeString, StringOrder.CASE_SENSITIVE),
                                ),
                            ).build()
                    } else {
                        box.query(baseCondition).build()
                    }

                // Collect unique workKeys from episodes (via work ToOne relation)
                // Note: We use contains(":episode:") pattern to detect episode refs
                // This avoids sourceKey split parsing issues when accountKey contains colons
                query
                    .find()
                    .filter { entity ->
                        // Episode refs identified by ":episode:" pattern in sourceKey
                        entity.sourceKey.contains(":episode:", ignoreCase = true)
                    }.mapNotNull { entity ->
                        // Use denormalized workKey (avoids N ToOne lazy loads)
                        entity.workKey.ifEmpty { null }
                    }.toSet()
            }
    }
