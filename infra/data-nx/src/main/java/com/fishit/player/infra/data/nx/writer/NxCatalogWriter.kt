/**
 * NxCatalogWriter - Writes normalized media to the NX work graph.
 *
 * ✅ REFACTORED: Now uses WorkEntityBuilder, SourceRefBuilder, VariantBuilder
 * - Before: 610 lines, CC ~28
 * - After: ~220 lines, CC ~8
 * - Reduction: ~64% (-390 lines)
 *
 * This is the ingest entry point for the NX system. It receives normalized
 * media metadata and creates/updates:
 * - NX_Work (canonical media work)
 * - NX_WorkSourceRef (pipeline source link)
 * - NX_WorkVariant (playback variant)
 *
 * **Usage:** Called by XtreamSyncService / sync orchestration after normalization.
 * **SSOT Contract:** contracts/NX_SSOT_CONTRACT.md
 */
package com.fishit.player.infra.data.nx.writer

import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.model.repository.NxWorkVariantRepository
import com.fishit.player.core.persistence.obx.NxKeyGenerator
import com.fishit.player.infra.data.nx.mapper.MediaTypeMapper
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
class NxCatalogWriter
    @Inject
    constructor(
        private val workRepository: NxWorkRepository,
        private val sourceRefRepository: NxWorkSourceRefRepository,
        private val variantRepository: NxWorkVariantRepository,
        private val workEntityBuilder: WorkEntityBuilder,
        private val sourceRefBuilder: SourceRefBuilder,
        private val variantBuilder: VariantBuilder,
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
        ): String? =
            try {
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
                    val variantKey = NxKeyGenerator.defaultVariantKey(sourceKey)
                    val variant =
                        variantBuilder.build(
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

        /**
         * Batch ingest normalized media items into the NX work graph.
         *
         * Builds all entities first, then batch-upserts for efficiency.
         * Used by [XtreamCatalogSync] consumer to reduce per-item transaction overhead.
         *
         * @param items List of (raw, normalized, accountKey) tuples
         * @return Number of successfully ingested items
         */
        suspend fun ingestBatch(items: List<Triple<RawMediaMetadata, NormalizedMediaMetadata, String>>): Int {
            if (items.isEmpty()) return 0

            var successCount = 0
            val now = System.currentTimeMillis()

            val works = mutableListOf<NxWorkRepository.Work>()
            val sourceRefs = mutableListOf<NxWorkSourceRefRepository.SourceRef>()
            val variants = mutableListOf<NxWorkVariantRepository.Variant>()

            for ((raw, normalized, accountKey) in items) {
                try {
                    val workKey = buildWorkKey(normalized)
                    works.add(workEntityBuilder.build(normalized, workKey, now))

                    val sourceKey = buildSourceKey(raw, accountKey)
                    sourceRefs.add(sourceRefBuilder.build(raw, workKey, accountKey, sourceKey, now))

                    if (raw.playbackHints.isNotEmpty()) {
                        val variantKey = NxKeyGenerator.defaultVariantKey(sourceKey)
                        variants.add(variantBuilder.build(variantKey, workKey, sourceKey, raw.playbackHints, normalized.durationMs, now))
                    }

                    successCount++
                } catch (e: Exception) {
                    UnifiedLog.e(TAG, e) { "Failed to build entities for: ${normalized.canonicalTitle}" }
                }
            }

            try {
                workRepository.upsertBatch(works)
                sourceRefRepository.upsertBatch(sourceRefs)
                if (variants.isNotEmpty()) {
                    variantRepository.upsertBatch(variants)
                }
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Failed to batch upsert" }
                return 0
            }

            return successCount
        }

        /**
         * Build work key from normalized metadata.
         *
         * Delegates to NxKeyGenerator.workKey() — the single source of truth for
         * work key format: `{workType}:{authority}:{id}`
         *
         * Uses canonical MediaTypeMapper.toWorkType() for correct type mapping.
         */
        private fun buildWorkKey(normalized: NormalizedMediaMetadata): String {
            val workType = MediaTypeMapper.toWorkType(normalized.mediaType)
            return NxKeyGenerator.workKey(
                workType = workType,
                title = normalized.canonicalTitle,
                year = normalized.year,
                // tmdbId intentionally NOT passed — workKey is always heuristic.
                // tmdbId is stored separately on NX_Work and used via Dual-Lookup.
                season = normalized.season,
                episode = normalized.episode,
            )
        }

        /**
         * Build source key from raw metadata.
         *
         * [accountKey] uses the NxKeyGenerator format `{sourceType}:{accountKey}` (e.g.,
         * `xtream:user@server.com`). [SourceKeyParser.buildSourceKey] already prepends sourceType,
         * so we extract only the accountKey portion to avoid a duplicate prefix like
         * `src:xtream:xtream:user@server.com:…`.
         *
         * Format: src:{sourceType}:{accountKey}:{itemKind}:{itemKey}
         * Examples:
         * - src:xtream:user@server.com:vod:12345
         * - src:telegram:+491234567890:file:789:101
         */
        private fun buildSourceKey(
            raw: RawMediaMetadata,
            accountKey: String,
        ): String {
            val identifier = accountKey.removePrefix("${raw.sourceType.name.lowercase()}:")
            // FIX: Strip sourceType prefix from sourceId too.
            // XtreamIdCodec produces "xtream:series:1418", but SourceKeyParser.buildSourceKey
            // already prepends "src:xtream:", resulting in "src:xtream:account:xtream:series:1418".
            // We need just the kind:key part, e.g. "series:1418".
            val cleanSourceId = raw.sourceId.removePrefix("${raw.sourceType.name.lowercase()}:")
            return SourceKeyParser.buildSourceKey(raw.sourceType, identifier, cleanSourceId)
        }

        // =========================================================================
        // Bulk Operations
        // =========================================================================

        /**
         * Clear all source refs for a given source type.
         *
         * Used for clearing entire pipelines (e.g., "clear all Telegram sources").
         *
         * @param sourceType Source type to clear (TELEGRAM, XTREAM, etc.)
         * @return Number of source refs deleted
         */
        suspend fun clearSourceType(sourceType: NxWorkSourceRefRepository.SourceType): Int =
            sourceRefRepository.deleteBySourceType(sourceType)
    }
