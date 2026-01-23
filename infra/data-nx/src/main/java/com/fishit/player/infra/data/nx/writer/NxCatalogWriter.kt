/**
 * NxCatalogWriter - Writes normalized media to the NX work graph.
 *
 * This is the ingest entry point for the NX system. It receives normalized
 * media metadata and creates/updates:
 * - NX_Work (canonical media work)
 * - NX_WorkSourceRef (pipeline source link)
 * - NX_WorkVariant (playback variant)
 *
 * **Usage:** Called by CatalogSyncService after normalization.
 *
 * **SSOT Contract:** docs/v2/NX_SSOT_CONTRACT.md
 */
package com.fishit.player.infra.data.nx.writer

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType as CoreSourceType
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceItemKind
import com.fishit.player.core.model.repository.NxWorkVariantRepository
import com.fishit.player.infra.logging.UnifiedLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes normalized media to the NX work graph.
 *
 * Orchestrates creation of NX_Work, NX_WorkSourceRef, and NX_WorkVariant entities.
 */
@Singleton
class NxCatalogWriter @Inject constructor(
    private val workRepository: NxWorkRepository,
    private val sourceRefRepository: NxWorkSourceRefRepository,
    private val variantRepository: NxWorkVariantRepository,
) {
    companion object {
        private const val TAG = "NxCatalogWriter"
    }

    /**
     * Ingest a normalized media item into the NX work graph.
     *
     * Creates or updates:
     * 1. NX_Work - canonical media identity
     * 2. NX_WorkSourceRef - links work to pipeline source
     * 3. NX_WorkVariant - playback variant (if playback hints available)
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

            // 1. Create/update the canonical work
            val workKey = buildWorkKey(normalized)
            val work = NxWorkRepository.Work(
                workKey = workKey,
                type = mapWorkType(normalized.mediaType),
                displayTitle = normalized.canonicalTitle,
                sortTitle = normalized.canonicalTitle,
                titleNormalized = normalized.canonicalTitle.lowercase(),
                year = normalized.year,
                runtimeMs = normalized.durationMs,
                posterRef = normalized.poster?.toSerializedString(),
                backdropRef = normalized.backdrop?.toSerializedString(),
                rating = normalized.rating,
                genres = normalized.genres,
                plot = normalized.plot,
                recognitionState = if (normalized.tmdb != null) {
                    NxWorkRepository.RecognitionState.CONFIRMED
                } else {
                    NxWorkRepository.RecognitionState.HEURISTIC
                },
                createdAtMs = now,
                updatedAtMs = now,
            )
            workRepository.upsert(work)

            // 2. Create/update source reference
            val sourceItemKind = mapSourceItemKind(normalized.mediaType)
            val sourceKey = buildSourceKey(raw.sourceType, accountKey, sourceItemKind, raw.sourceId)
            val sourceRef = NxWorkSourceRefRepository.SourceRef(
                sourceKey = sourceKey,
                workKey = workKey,
                sourceType = mapSourceType(raw.sourceType),
                accountKey = accountKey,
                sourceItemKind = sourceItemKind,
                sourceItemKey = raw.sourceId,
                sourceTitle = raw.originalTitle,
                firstSeenAtMs = now,
                lastSeenAtMs = now,
                availability = NxWorkSourceRefRepository.AvailabilityState.ACTIVE,
            )
            sourceRefRepository.upsert(sourceRef)

            // 3. Create playback variant if playback hints available
            if (raw.playbackHints.isNotEmpty()) {
                val variantKey = buildVariantKey(sourceKey)
                val variant = NxWorkVariantRepository.Variant(
                    variantKey = variantKey,
                    workKey = workKey,
                    sourceKey = sourceKey,
                    label = "Original",
                    isDefault = true,
                    container = extractContainerFromHints(raw.playbackHints),
                    durationMs = normalized.durationMs,
                    playbackHints = raw.playbackHints,
                    createdAtMs = now,
                    updatedAtMs = now,
                )
                variantRepository.upsert(variant)
            }

            UnifiedLog.d(TAG) { "Ingested: $workKey (source: $sourceKey)" }
            workKey
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to ingest: ${normalized.canonicalTitle}" }
            null
        }
    }

    /**
     * Batch ingest multiple items.
     *
     * @param items List of (raw, normalized, accountKey) tuples
     * @return Number of successfully ingested items
     */
    suspend fun ingestBatch(
        items: List<Triple<RawMediaMetadata, NormalizedMediaMetadata, String>>,
    ): Int {
        var success = 0
        for ((raw, normalized, accountKey) in items) {
            if (ingest(raw, normalized, accountKey) != null) {
                success++
            }
        }
        return success
    }

    // =========================================================================
    // Key Building (per NX_SSOT_CONTRACT.md)
    // =========================================================================

    /**
     * Build workKey: `<workType>:<canonicalSlug>:<year|LIVE>`
     */
    private fun buildWorkKey(normalized: NormalizedMediaMetadata): String {
        val type = normalized.mediaType.name.lowercase()
        val slug = normalized.canonicalTitle
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(50)
        val yearPart = when {
            normalized.mediaType == MediaType.LIVE -> "live"
            normalized.year != null -> normalized.year.toString()
            else -> "unknown"
        }
        return "$type:$slug:$yearPart"
    }

    /**
     * Build sourceKey: `src:<sourceType>:<accountKey>:<sourceItemKind>:<sourceItemKey>`
     */
    private fun buildSourceKey(
        sourceType: CoreSourceType,
        accountKey: String,
        itemKind: SourceItemKind,
        itemKey: String,
    ): String {
        return "src:${sourceType.name.lowercase()}:$accountKey:${itemKind.name.lowercase()}:$itemKey"
    }

    /**
     * Build variantKey: `v:<sourceKey>:default`
     */
    private fun buildVariantKey(sourceKey: String): String {
        return "v:$sourceKey:default"
    }

    // =========================================================================
    // Type Mappings
    // =========================================================================

    private fun mapWorkType(type: MediaType): NxWorkRepository.WorkType {
        return when (type) {
            MediaType.MOVIE -> NxWorkRepository.WorkType.MOVIE
            MediaType.SERIES -> NxWorkRepository.WorkType.SERIES
            MediaType.SERIES_EPISODE -> NxWorkRepository.WorkType.EPISODE
            MediaType.LIVE -> NxWorkRepository.WorkType.LIVE_CHANNEL
            MediaType.CLIP -> NxWorkRepository.WorkType.CLIP
            MediaType.AUDIOBOOK -> NxWorkRepository.WorkType.AUDIOBOOK
            MediaType.MUSIC -> NxWorkRepository.WorkType.MUSIC_TRACK
            else -> NxWorkRepository.WorkType.UNKNOWN
        }
    }

    private fun mapSourceType(type: CoreSourceType): NxWorkSourceRefRepository.SourceType {
        return when (type) {
            CoreSourceType.TELEGRAM -> NxWorkSourceRefRepository.SourceType.TELEGRAM
            CoreSourceType.XTREAM -> NxWorkSourceRefRepository.SourceType.XTREAM
            CoreSourceType.IO -> NxWorkSourceRefRepository.SourceType.IO
            else -> NxWorkSourceRefRepository.SourceType.UNKNOWN
        }
    }

    private fun mapSourceItemKind(mediaType: MediaType): SourceItemKind {
        return when (mediaType) {
            MediaType.MOVIE -> SourceItemKind.VOD
            MediaType.SERIES -> SourceItemKind.SERIES
            MediaType.SERIES_EPISODE -> SourceItemKind.EPISODE
            MediaType.LIVE -> SourceItemKind.LIVE
            MediaType.CLIP, MediaType.AUDIOBOOK, MediaType.MUSIC -> SourceItemKind.FILE
            else -> SourceItemKind.UNKNOWN
        }
    }

    private fun extractContainerFromHints(hints: Map<String, String>): String? {
        val ext = hints["containerExtension"] ?: hints["extension"]
        return when (ext?.lowercase()) {
            "mp4" -> "mp4"
            "mkv" -> "mkv"
            "avi" -> "avi"
            "webm" -> "webm"
            "ts" -> "ts"
            "m3u8" -> "hls"
            else -> null
        }
    }

    /**
     * Extension to serialize ImageRef to string format.
     */
    private fun ImageRef.toSerializedString(): String {
        return when (this) {
            is ImageRef.Http -> "http:$url"
            is ImageRef.TelegramThumb -> "tg:$remoteId"
            is ImageRef.LocalFile -> "file:$path"
            is ImageRef.InlineBytes -> "inline:${bytes.size}bytes"
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Source Clearing
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Clear all source refs for a specific source type.
     *
     * This removes all NX_WorkSourceRef entries for the given source type (e.g., Telegram, Xtream).
     * Works that are only referenced by deleted source refs may become orphaned.
     *
     * @param sourceType The source type to clear (TELEGRAM, XTREAM, etc.)
     * @return Number of source refs deleted
     */
    suspend fun clearSourceType(sourceType: NxWorkSourceRefRepository.SourceType): Int {
        val count = sourceRefRepository.deleteBySourceType(sourceType)
        UnifiedLog.i(TAG, "Cleared $count source refs for type: $sourceType")
        return count
    }

    /**
     * Clear all source refs for a specific account.
     *
     * @param accountKey The account key to clear (e.g., "telegram:123456" or "xtream:myserver")
     * @return Number of source refs deleted
     */
    suspend fun clearAccount(accountKey: String): Int {
        val count = sourceRefRepository.deleteByAccountKey(accountKey)
        UnifiedLog.i(TAG, "Cleared $count source refs for account: $accountKey")
        return count
    }
}
