/**
 * NxEnrichmentWriter — Enriches EXISTING NX entities with detail API metadata.
 *
 * ## 2-Writer Architecture
 * | Writer | When | What |
 * |--------|------|------|
 * | [NxCatalogWriter] | Catalog Sync + Detail Open | Creates NX_Work + SourceRef + Variant |
 * | **NxEnrichmentWriter** | Detail Open | Enriches EXISTING works with info_call metadata |
 *
 * ## Responsibilities
 * Enriches existing NX_Work entities that were created by [NxCatalogWriter] during sync,
 * with richer metadata from the detail info_call APIs:
 *
 * - **Series enrichment:** `get_series_info` provides plot, poster, backdrop, rating,
 *   genres, director, cast, trailer, tmdbId, imdbId — fields often absent in listing API.
 * - **VOD enrichment:** `get_vod_info` provides the same metadata fields,
 *   plus containerExtension and directSource for playback variant updates.
 * - **Live:** No enrichment possible — Xtream has no `get_live_info` endpoint.
 *
 * ## Enrichment Semantics
 * Uses [NxWorkRepository.enrichIfAbsent] with defined field update policies:
 * - **ENRICH_ONLY:** plot, poster, backdrop, rating, genres, etc. → set only if currently null
 * - **ALWAYS_UPDATE:** tmdbId, imdbId, tvdbId → always overwrite with non-null value
 * - **MONOTONIC_UP:** recognitionState → only upgrade, never downgrade
 * - **IMMUTABLE:** workKey, workType, canonicalTitle → never changed
 *
 * ## Call Order
 * NxEnrichmentWriter runs BEFORE [NxCatalogWriter] for series episode creation,
 * so that the parent series work is fully enriched before episode works are created.
 *
 * @see NxCatalogWriter for sync-time + detail-time entity creation (shared by both orchestrators)
 */
package com.fishit.player.infra.data.nx.writer

import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkVariantRepository
import com.fishit.player.core.model.repository.toEnrichment
import com.fishit.player.infra.data.nx.writer.builder.WorkEntityBuilder
import com.fishit.player.infra.logging.UnifiedLog
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NxEnrichmentWriter @Inject constructor(
    private val workRepository: NxWorkRepository,
    private val variantRepository: NxWorkVariantRepository,
    private val workEntityBuilder: WorkEntityBuilder,
) {
    companion object {
        private const val TAG = "NxEnrichmentWriter"

        /**
         * Build the SSOT variantKey for a given sourceKey.
         *
         * Format: `{sourceKey}#original` — consistent with [NxCatalogWriter].
         */
        fun buildVariantKey(sourceKey: String): String = "$sourceKey#original"
    }

    /**
     * Enrich an existing NX_Work with metadata from a detail info_call.
     *
     * Works for any content type (Series, VOD). The [NormalizedMediaMetadata]
     * is constructed by the caller from the API response.
     *
     * Uses [NxWorkRepository.enrichIfAbsent] semantics:
     * - Fields like plot, poster, rating → set only if currently null on entity
     * - tmdbId, imdbId → always overwrite (detail API is more authoritative)
     * - workType, canonicalTitle → never changed
     *
     * @param workKey The workKey of the existing NX_Work to enrich
     * @param metadata The normalized metadata from the detail info_call
     * @return The enriched work, or null if the work doesn't exist
     */
    suspend fun enrichWork(
        workKey: String,
        metadata: NormalizedMediaMetadata,
    ): NxWorkRepository.Work? {
        val now = System.currentTimeMillis()
        val work = workEntityBuilder.build(metadata, workKey, now)
        val enriched = workRepository.enrichIfAbsent(workKey, work.toEnrichment())

        if (enriched != null) {
            UnifiedLog.d(TAG) {
                "enrichWork: Enriched NX_Work($workKey) " +
                    "tmdbId=${enriched.tmdbId}, plot=${enriched.plot?.take(30)}"
            }
        } else {
            UnifiedLog.w(TAG) { "enrichWork: NX_Work($workKey) not found — skipping enrichment" }
        }

        return enriched
    }

    /**
     * Update playback hints on an existing variant.
     *
     * Used for VOD detail enrichment: `get_vod_info` returns `containerExtension`,
     * `directSource`, and other playback-relevant fields that aren't available
     * in the listing API.
     *
     * Merges new hints with existing hints (existing hints preserved if not overwritten).
     *
     * @param sourceKey The sourceKey identifying the variant's source
     * @param workKey The workKey of the NX_Work owning this variant
     * @param hintsUpdate Map of hint keys to update/add
     */
    suspend fun updateVariantPlaybackHints(
        sourceKey: String,
        workKey: String,
        hintsUpdate: Map<String, String>,
    ) {
        val variantKey = buildVariantKey(sourceKey)
        val existing = variantRepository.getByVariantKey(variantKey)

        // Merge: existing hints + new hints (new wins on collision)
        val mergedHints = buildMap {
            existing?.playbackHints?.forEach { (k, v) -> put(k, v) }
            putAll(hintsUpdate)
        }

        val variant = NxWorkVariantRepository.Variant(
            variantKey = variantKey,
            workKey = workKey,
            sourceKey = sourceKey,
            label = existing?.label ?: "Original",
            isDefault = existing?.isDefault ?: true,
            // Preserve existing technical metadata to avoid silent wipe
            qualityHeight = existing?.qualityHeight,
            qualityWidth = existing?.qualityWidth,
            bitrateKbps = existing?.bitrateKbps,
            videoCodec = existing?.videoCodec,
            audioCodec = existing?.audioCodec,
            audioLang = existing?.audioLang,
            durationMs = existing?.durationMs,
            // Update container from hints if provided, otherwise preserve existing
            container = hintsUpdate[PlaybackHintKeys.Xtream.CONTAINER_EXT] ?: existing?.container,
            playbackHints = mergedHints,
            updatedAtMs = System.currentTimeMillis(),
        )
        variantRepository.upsert(variant)

        UnifiedLog.d(TAG) {
            "updateVariantPlaybackHints: Updated variant for $sourceKey " +
                "(${hintsUpdate.size} hints merged, technical fields preserved)"
        }
    }
}
