/**
 * ObjectBox implementation of [NxWorkVariantRepository].
 *
 * Provides playback variant management for Works.
 */
package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxWorkVariantRepository
import com.fishit.player.core.model.repository.NxWorkVariantRepository.Variant
import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.NX_WorkVariant
import com.fishit.player.core.persistence.obx.NX_WorkVariant_
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
 * ObjectBox-backed repository for playback variants.
 */
@Singleton
class NxWorkVariantRepositoryImpl @Inject constructor(
    private val boxStore: BoxStore,
) : NxWorkVariantRepository {
    private val box by lazy { boxStore.boxFor<NX_WorkVariant>() }
    private val workBox by lazy { boxStore.boxFor<NX_Work>() }

    // ──────────────────────────────────────────────────────────────────────
    // Reads
    // ──────────────────────────────────────────────────────────────────────

    override suspend fun getByVariantKey(variantKey: String): Variant? = withContext(Dispatchers.IO) {
        box.query(NX_WorkVariant_.variantKey.equal(variantKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()
            ?.toDomain()
    }

    override fun observeByWorkKey(workKey: String): Flow<List<Variant>> {
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

    override suspend fun findByWorkKey(workKey: String): List<Variant> = withContext(Dispatchers.IO) {
        val workId = workBox.query(NX_Work_.workKey.equal(workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()?.id ?: return@withContext emptyList()

        // Filter in memory by work.targetId
        box.all
            .filter { it.work.targetId == workId }
            .map { it.toDomain() }
    }

    override suspend fun findBySourceKey(sourceKey: String): List<Variant> = withContext(Dispatchers.IO) {
        box.query(NX_WorkVariant_.sourceKey.equal(sourceKey, StringOrder.CASE_SENSITIVE))
            .build()
            .find()
            .map { it.toDomain() }
    }

    override suspend fun selectBestVariant(
        workKey: String,
        preferredAudioLang: String?,
        minHeight: Int?,
    ): Variant? = withContext(Dispatchers.IO) {
        val variants = findByWorkKey(workKey)
        if (variants.isEmpty()) return@withContext null

        // Filter by minimum height if specified
        val filtered = if (minHeight != null) {
            variants.filter { (it.qualityHeight ?: 0) >= minHeight }.ifEmpty { variants }
        } else {
            variants
        }

        // Prefer matching audio language
        val langMatch = if (preferredAudioLang != null) {
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

    override suspend fun upsert(variant: Variant): Variant = withContext(Dispatchers.IO) {
        val existing = box.query(NX_WorkVariant_.variantKey.equal(variant.variantKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()
        val entity = variant.toEntity(existing)

        // Link to work entity
        val work = workBox.query(NX_Work_.workKey.equal(variant.workKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()
        if (work != null) {
            entity.work.target = work
        }

        box.put(entity)
        entity.toDomain()
    }

    override suspend fun upsertBatch(variants: List<Variant>): List<Variant> = withContext(Dispatchers.IO) {
        if (variants.isEmpty()) return@withContext emptyList()

        // Batch lookup existing
        val variantKeys = variants.map { it.variantKey }
        val existingMap = box.query(NX_WorkVariant_.variantKey.oneOf(variantKeys.toTypedArray(), StringOrder.CASE_SENSITIVE))
            .build()
            .find()
            .associateBy { it.variantKey }

        // Batch lookup works
        val workKeys = variants.map { it.workKey }.distinct()
        val workMap = workBox.query(NX_Work_.workKey.oneOf(workKeys.toTypedArray(), StringOrder.CASE_SENSITIVE))
            .build()
            .find()
            .associateBy { it.workKey }

        val entities = variants.map { variant ->
            val entity = variant.toEntity(existingMap[variant.variantKey])
            workMap[variant.workKey]?.let { entity.work.target = it }
            entity
        }

        box.put(entities)
        entities.map { it.toDomain() }
    }

    override suspend fun delete(variantKey: String): Boolean = withContext(Dispatchers.IO) {
        val removed = box.query(NX_WorkVariant_.variantKey.equal(variantKey, StringOrder.CASE_SENSITIVE))
            .build()
            .remove()
        removed > 0
    }
}
