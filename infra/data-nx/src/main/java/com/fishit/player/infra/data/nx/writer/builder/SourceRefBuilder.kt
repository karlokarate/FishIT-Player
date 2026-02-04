/**
 * SourceRefBuilder - Constructs NX_WorkSourceRef entities.
 *
 * Extracts source reference construction logic from NxCatalogWriter to reduce CC.
 * Handles:
 * - Source key construction
 * - Clean item key extraction (numeric ID vs full format)
 * - Live-specific fields (EPG, catchup)
 *
 * CC: ~5 (well below target of 15)
 */
package com.fishit.player.infra.data.nx.writer.builder

import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType as CoreSourceType
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceItemKind
import com.fishit.player.infra.data.nx.mapper.SourceItemKindMapper
import com.fishit.player.infra.data.nx.mapper.SourceKeyParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds NX_WorkSourceRef entities.
 */
@Singleton
class SourceRefBuilder @Inject constructor() {

    /**
     * Build a SourceRef entity from raw metadata.
     *
     * @param raw The raw metadata from pipeline
     * @param workKey The work key (foreign key to NX_Work)
     * @param accountKey The account key (e.g., "telegram:123456" or "xtream:myserver")
     * @param sourceKey The computed source key
     * @param now Current timestamp
     * @return SourceRef entity ready for upsert
     */
    fun build(
        raw: RawMediaMetadata,
        workKey: String,
        accountKey: String,
        sourceKey: String,
        now: Long = System.currentTimeMillis(),
    ): NxWorkSourceRefRepository.SourceRef {
        val sourceItemKind = SourceItemKindMapper.fromMediaType(raw.mediaType)
        
        // CRITICAL: Store just the numeric ID, not the full xtream:type:id format
        val cleanSourceItemKey = extractCleanItemKey(raw.sourceId)

        return NxWorkSourceRefRepository.SourceRef(
            sourceKey = sourceKey,
            workKey = workKey,
            sourceType = mapSourceType(raw.sourceType),
            accountKey = accountKey,
            sourceItemKind = sourceItemKind,
            sourceItemKey = cleanSourceItemKey,
            sourceTitle = raw.originalTitle,
            firstSeenAtMs = now,
            lastSeenAtMs = now,
            sourceLastModifiedMs = raw.lastModifiedTimestamp,
            availability = NxWorkSourceRefRepository.AvailabilityState.ACTIVE,
            // Live channel specific (EPG/Catchup)
            epgChannelId = raw.epgChannelId,
            tvArchive = raw.tvArchive,
            tvArchiveDuration = raw.tvArchiveDuration,
        )
    }

    /**
     * Extract clean item key - just the numeric ID.
     *
     * Examples:
     * - "xtream:vod:12345" → "12345"
     * - "12345" → "12345"
     * - "msg:123:456" → "msg:123:456" (Telegram format preserved)
     */
    private fun extractCleanItemKey(sourceId: String): String {
        return SourceKeyParser.extractNumericItemKey(sourceId)?.toString()
            ?: SourceKeyParser.extractItemKey(sourceId)
            ?: sourceId
    }

    /**
     * Map core SourceType to repository SourceType.
     */
    private fun mapSourceType(coreType: CoreSourceType): NxWorkSourceRefRepository.SourceType {
        return when (coreType) {
            CoreSourceType.TELEGRAM -> NxWorkSourceRefRepository.SourceType.TELEGRAM
            CoreSourceType.XTREAM -> NxWorkSourceRefRepository.SourceType.XTREAM
            CoreSourceType.IO -> NxWorkSourceRefRepository.SourceType.IO
            CoreSourceType.AUDIOBOOK -> NxWorkSourceRefRepository.SourceType.AUDIOBOOK
            CoreSourceType.UNKNOWN -> NxWorkSourceRefRepository.SourceType.UNKNOWN
        }
    }
}
