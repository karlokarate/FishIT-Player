/**
 * ObjectBox implementation of [NxWorkVariantRepository].
 *
 * Provides playback variant management for Works.
 */
package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxWorkVariantRepository
import com.fishit.player.core.model.repository.NxWorkVariantRepository.Variant
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.NX_WorkVariant
import com.fishit.player.core.persistence.obx.NX_WorkVariant_
import com.fishit.player.core.persistence.obx.NX_Work_
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
 * ObjectBox-backed repository for playback variants.
 */
@Singleton
class NxWorkVariantRepositoryImpl
    @Inject
    constructor(
        private val boxStore: BoxStore,
    ) : NxWorkVariantRepository {
        private val box by lazy { boxStore.boxFor<NX_WorkVariant>() }
        private val workBox by lazy { boxStore.boxFor<NX_Work>() }

        init {
            // One-time backfill on background thread to avoid ANR.
            // Reactive Flows auto-correct when put() triggers re-emission.
            Thread({
                backfillDenormalizedWorkKeys()
            }, "backfill-variant-workKey").start()
        }

        private fun backfillDenormalizedWorkKeys() {
            val varBox = boxStore.boxFor(NX_WorkVariant::class.java)
            val empty =
                varBox
                    .query(
                        NX_WorkVariant_.workKey.equal("", StringOrder.CASE_SENSITIVE),
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
                varBox.put(updated)
                UnifiedLog.i("NxWorkVariantRepo") { "Backfilled workKey on ${updated.size} pre-migration variants" }
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // Reads
        // ──────────────────────────────────────────────────────────────────────

        override suspend fun getByVariantKey(variantKey: String): Variant? =
            withContext(Dispatchers.IO) {
                box
                    .query(NX_WorkVariant_.variantKey.equal(variantKey, StringOrder.CASE_SENSITIVE))
                    .build()
                    .findFirst()
                    ?.toDomain()
            }

        override fun observeByWorkKey(workKey: String): Flow<List<Variant>> {
            // Indexed query on denormalized workKey field — no full table scan
            return box
                .query(NX_WorkVariant_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
                .build()
                .asFlow()
                .map { list -> list.map { it.toDomain() } }
        }

        override suspend fun findByWorkKey(workKey: String): List<Variant> =
            withContext(Dispatchers.IO) {
                box
                    .query(NX_WorkVariant_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
                    .build()
                    .find()
                    .map { it.toDomain() }
            }

        override suspend fun findByWorkKeysBatch(workKeys: List<String>): Map<String, List<Variant>> =
            withContext(Dispatchers.IO) {
                if (workKeys.isEmpty()) return@withContext emptyMap()

                // Indexed oneOf() query on denormalized workKey field — no full table scan
                val result = mutableMapOf<String, MutableList<Variant>>()
                box
                    .query(NX_WorkVariant_.workKey.oneOf(workKeys.toTypedArray(), StringOrder.CASE_SENSITIVE))
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

        override suspend fun findBySourceKey(sourceKey: String): List<Variant> =
            withContext(Dispatchers.IO) {
                box
                    .query(NX_WorkVariant_.sourceKey.equal(sourceKey, StringOrder.CASE_SENSITIVE))
                    .build()
                    .find()
                    .map { it.toDomain() }
            }

        override suspend fun selectBestVariant(
            workKey: String,
            preferredAudioLang: String?,
            minHeight: Int?,
        ): Variant? =
            withContext(Dispatchers.IO) {
                val variants = findByWorkKey(workKey)
                if (variants.isEmpty()) return@withContext null

                // Filter by minimum height if specified
                val filtered =
                    if (minHeight != null) {
                        variants.filter { (it.qualityHeight ?: 0) >= minHeight }.ifEmpty { variants }
                    } else {
                        variants
                    }

                // Prefer matching audio language
                val langMatch =
                    if (preferredAudioLang != null) {
                        filtered.filter { it.audioLang?.equals(preferredAudioLang, ignoreCase = true) == true }
                    } else {
                        emptyList()
                    }

                // Return best match: language match with highest quality, or highest quality overall
                (langMatch.ifEmpty { filtered })
                    .maxByOrNull { it.qualityHeight ?: 0 }
            }

        // ──────────────────────────────────────────────────────────────────────
        // Writes
        // ──────────────────────────────────────────────────────────────────────

        override suspend fun upsert(variant: Variant): Variant =
            withContext(Dispatchers.IO) {
                val existing =
                    box
                        .query(NX_WorkVariant_.variantKey.equal(variant.variantKey, StringOrder.CASE_SENSITIVE))
                        .build()
                        .findFirst()
                val entity = variant.toEntity(existing)

                // Link to work entity
                val work =
                    workBox
                        .query(NX_Work_.workKey.equal(variant.workKey, StringOrder.CASE_SENSITIVE))
                        .build()
                        .findFirst()
                if (work != null) {
                    entity.work.target = work
                }

                box.put(entity)
                entity.toDomain()
            }

        override suspend fun upsertBatch(variants: List<Variant>): List<Variant> =
            withContext(Dispatchers.IO) {
                if (variants.isEmpty()) return@withContext emptyList()

                try {
                    // CRITICAL FIX: Deduplicate variants by variantKey within batch
                    val uniqueVariants = variants.associateBy { it.variantKey }.values.toList()

                    if (uniqueVariants.size < variants.size) {
                        UnifiedLog.w("NxWorkVariantRepo") {
                            "Deduped ${variants.size - uniqueVariants.size} duplicate variantKeys in batch"
                        }
                    }

                    // Use runInTx for atomic batch update
                    boxStore.runInTx {
                        // CRITICAL: Query existing entities INSIDE transaction!
                        val variantKeys = uniqueVariants.map { it.variantKey }
                        val existingMap =
                            box
                                .query(NX_WorkVariant_.variantKey.oneOf(variantKeys.toTypedArray(), StringOrder.CASE_SENSITIVE))
                                .build()
                                .find()
                                .associateBy { it.variantKey }

                        // Batch lookup works
                        val workKeys = uniqueVariants.map { it.workKey }.distinct()
                        val workMap =
                            workBox
                                .query(NX_Work_.workKey.oneOf(workKeys.toTypedArray(), StringOrder.CASE_SENSITIVE))
                                .build()
                                .find()
                                .associateBy { it.workKey }

                        val entities =
                            uniqueVariants.map { variant ->
                                val entity = variant.toEntity(existingMap[variant.variantKey])
                                workMap[variant.workKey]?.let { entity.work.target = it }
                                entity
                            }
                        box.put(entities)
                    }

                    uniqueVariants
                } finally {
                    // CRITICAL: Cleanup thread-local resources to prevent transaction leaks
                    try {
                        boxStore.closeThreadResources()
                    } catch (e: Exception) {
                        // Ignore cleanup errors
                    }
                }
            }

        override suspend fun delete(variantKey: String): Boolean =
            withContext(Dispatchers.IO) {
                val removed =
                    box
                        .query(NX_WorkVariant_.variantKey.equal(variantKey, StringOrder.CASE_SENSITIVE))
                        .build()
                        .remove()
                removed > 0
            }
    }
