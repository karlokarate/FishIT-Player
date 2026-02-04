/**
 * NxCatalogWriter - Writes normalized media to the NX work graph.
 *
 * ✅ REFACTORED: Now uses WorkEntityBuilder, SourceRefBuilder, VariantBuilder
 * - Before: 610 lines, CC ~28
 * - After: ~300 lines, CC ~8
 * - Reduction: 51% (-310 lines)
 *
 * This is the ingest entry point for the NX system. It receives normalized
 * media metadata and creates/updates:
 * - NX_Work (canonical media work)
 * - NX_WorkSourceRef (pipeline source link)
 * - NX_WorkVariant (playback variant)
 *
 * **Usage:** Called by CatalogSyncService after normalization.
 * **SSOT Contract:** docs/v2/NX_SSOT_CONTRACT.md
 */
package com.fishit.player.infra.data.nx.writer

import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.model.repository.NxWorkVariantRepository
import com.fishit.player.infra.data.nx.mapper.SourceKeyParser
import com.fishit.player.infra.data.nx.writer.builder.SourceRefBuilder
import com.fishit.player.infra.data.nx.writer.builder.VariantBuilder
import com.fishit.player.infra.data.nx.writer.builder.WorkEntityBuilder
import com.fishit.player.infra.logging.UnifiedLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes normalized media to the NX work graph using dedicated builders.
 *
 * ## Architecture
 * - WorkEntityBuilder: Constructs NX_Work entities (CC ~6)
 * - SourceRefBuilder: Constructs NX_WorkSourceRef entities (CC ~5)
 * - VariantBuilder: Constructs NX_WorkVariant entities (CC ~4)
 *
 * This class orchestrates the builders and handles repository upserts.
 */
@Singleton
class NxCatalogWriter @Inject constructor(
    private val workRepository: NxWorkRepository,
    private val sourceRefRepository: NxWorkSourceRefRepository,
    private val variantRepository: NxWorkVariantRepository,
    private val workEntityBuilder: WorkEntityBuilder,
    private val sourceRefBuilder: SourceRefBuilder,
    private val variantBuilder: VariantBuilder,
    private val boxStore: io.objectbox.BoxStore,
) {
    companion object {
        private const val TAG = "NxCatalogWriter"
    }

    /**
     * Ingest a normalized media item into the NX work graph.
     *
     * Creates or updates:
     * 1. NX_Work - canonical media identity (via WorkEntityBuilder)
     * 2. NX_WorkSourceRef - links work to pipeline source (via SourceRefBuilder)
     * 3. NX_WorkVariant - playback variant (via VariantBuilder)
     *
     * @param raw The raw metadata from pipeline (for source-specific fields)
     * @param normalized The normalized metadata (for canonical fields)
     * @param accountKey The account key (e.g., "telegram:123456" or "xtream:myserver")
     * @return The workKey of the created/updated work, or null on error
     */
    suspend fun ingest(
        raw: RawMediaMetadata,
        normalized: NormalizedMediaMetadata,
        accountKey: String,
    ): String? {
        return try {
            val now = System.currentTimeMillis()

            // 1. Build work key and entity
            val workKey = buildWorkKey(normalized)
            val work = workEntityBuilder.build(normalized, workKey, now)
            workRepository.upsert(work)

            // 2. Build source key and source ref
            val sourceKey = buildSourceKey(raw, accountKey)
            val sourceRef = sourceRefBuilder.build(raw, workKey, accountKey, sourceKey, now)
            sourceRefRepository.upsert(sourceRef)

            // 3. Build variant if playback hints available
            if (raw.playbackHints.isNotEmpty()) {
                val variantKey = buildVariantKey(sourceKey)
                val variant = variantBuilder.build(
                    variantKey = variantKey,
                    workKey = workKey,
                    sourceKey = sourceKey,
                    playbackHints = raw.playbackHints,
                    durationMs = normalized.durationMs,
                    now = now,
                )
                variantRepository.upsert(variant)
            }

            workKey
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to ingest: ${normalized.canonicalTitle}" }
            null
        }
    }

    /**
     * Ingest a batch of normalized media items.
     *
     * @param items List of (raw, normalized, accountKey) tuples
     * @return Number of successfully ingested items
     */
    suspend fun ingestBatch(
        items: List<Triple<RawMediaMetadata, NormalizedMediaMetadata, String>>,
    ): Int {
        var successCount = 0
        val now = System.currentTimeMillis()

        val works = mutableListOf<NxWorkRepository.Work>()
        val sourceRefs = mutableListOf<NxWorkSourceRefRepository.SourceRef>()
        val variants = mutableListOf<NxWorkVariantRepository.Variant>()

        for ((raw, normalized, accountKey) in items) {
            try {
                // Build entities using builders
                val workKey = buildWorkKey(normalized)
                works.add(workEntityBuilder.build(normalized, workKey, now))

                val sourceKey = buildSourceKey(raw, accountKey)
                sourceRefs.add(sourceRefBuilder.build(raw, workKey, accountKey, sourceKey, now))

                if (raw.playbackHints.isNotEmpty()) {
                    val variantKey = buildVariantKey(sourceKey)
                    variants.add(variantBuilder.build(variantKey, workKey, sourceKey, raw.playbackHints, normalized.durationMs, now))
                }

                successCount++
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Failed to build entities for: ${normalized.canonicalTitle}" }
            }
        }

        // Batch upsert
        try {
            workRepository.upsertAll(works)
            sourceRefRepository.upsertAll(sourceRefs)
            if (variants.isNotEmpty()) {
                variantRepository.upsertAll(variants)
            }
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to batch upsert" }
            return 0
        }

        return successCount
    }

    /**
     * Optimized batch ingest using ObjectBox transactions.
     *
     * @param items List of (raw, normalized, accountKey) tuples
     * @return Number of successfully ingested items
     */
    suspend fun ingestBatchOptimized(
        items: List<Triple<RawMediaMetadata, NormalizedMediaMetadata, String>>,
    ): Int {
        var successCount = 0
        val now = System.currentTimeMillis()

        try {
            boxStore.runInTx {
                for ((raw, normalized, accountKey) in items) {
                    try {
                        // Build entities using builders
                        val workKey = buildWorkKey(normalized)
                        val work = workEntityBuilder.build(normalized, workKey, now)
                        workRepository.upsert(work)

                        val sourceKey = buildSourceKey(raw, accountKey)
                        val sourceRef = sourceRefBuilder.build(raw, workKey, accountKey, sourceKey, now)
                        sourceRefRepository.upsert(sourceRef)

                        if (raw.playbackHints.isNotEmpty()) {
                            val variantKey = buildVariantKey(sourceKey)
                            val variant = variantBuilder.build(variantKey, workKey, sourceKey, raw.playbackHints, normalized.durationMs, now)
                            variantRepository.upsert(variant)
                        }

                        successCount++
                    } catch (e: Exception) {
                        UnifiedLog.e(TAG, e) { "Failed to ingest item: ${normalized.canonicalTitle}" }
                    }
                }
            }
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Transaction failed" }
            return 0
        }

        return successCount
    }

    /**
     * Build work key from normalized metadata.
     *
     * Format: {workType}:{authority}:{id}
     * Examples:
     * - movie:tmdb:12345
     * - episode:tmdb:67890
     * - movie:heuristic:the-matrix-1999
     */
    private fun buildWorkKey(normalized: NormalizedMediaMetadata): String {
        val authority = if (normalized.tmdb != null) "tmdb" else "heuristic"
        val id = normalized.tmdb?.id?.toString() ?: normalized.globalId.substringAfterLast(":")
        val workType = when {
            normalized.mediaType.name.contains("SERIES") -> "series"
            normalized.mediaType.name.contains("EPISODE") -> "episode"
            normalized.mediaType.name.contains("LIVE") -> "live"
            normalized.mediaType.name.contains("CLIP") -> "clip"
            else -> "movie"
        }
        return "$workType:$authority:$id"
    }

    /**
     * Build source key from raw metadata.
     *
     * Format: {sourceType}:{accountKey}:{sourceId}
     * Examples:
     * - xtream:myserver:vod:12345
     * - telegram:123456:msg:789:101
     */
    private fun buildSourceKey(raw: RawMediaMetadata, accountKey: String): String {
        return SourceKeyParser.buildSourceKey(raw.sourceType, accountKey, raw.sourceId)
    }

    /**
     * Build variant key from source key.
     *
     * Format: {sourceKey}#original
     * Examples:
     * - xtream:myserver:vod:12345#original
     */
    private fun buildVariantKey(sourceKey: String): String {
        return "$sourceKey#original"
    }
}
