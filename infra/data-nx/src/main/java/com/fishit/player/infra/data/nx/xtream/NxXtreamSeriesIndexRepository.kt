/**
 * NX-based implementation of XtreamSeriesIndexRepository.
 *
 * This implementation replaces ObxXtreamSeriesIndexRepository and uses
 * NX_Work + NX_WorkRelation entities instead of legacy ObxSeasonIndex/ObxEpisodeIndex.
 *
 * ## Architecture
 * - Series are stored as NX_Work with workType=SERIES
 * - Episodes are stored as NX_Work with workType=EPISODE
 * - Series↔Episode links via NX_WorkRelation (relationType=SERIES_EPISODE)
 * - seriesId is stored in NX_WorkSourceRef.sourceItemKey as part of sourceKey
 *
 * ## Key Mapping
 * - seriesId (Int) → sourceKey `src:xtream:<account>:series:<seriesId>`
 * - episodeId → sourceKey `src:xtream:<account>:episode:series:<seriesId>:s<season>:e<episode>` (XtreamIdCodec composite)
 *
 * @see com.fishit.player.core.detail.domain.XtreamSeriesIndexRepository
 */
package com.fishit.player.infra.data.nx.xtream

import com.fishit.player.core.detail.domain.EpisodeIndexItem
import com.fishit.player.core.detail.domain.EpisodePlaybackHints
import com.fishit.player.core.detail.domain.SeasonIndexItem
import com.fishit.player.core.detail.domain.XtreamSeriesIndexRepository
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.toUriString
import com.fishit.player.core.model.repository.NxWorkRelationRepository
import com.fishit.player.core.model.repository.NxWorkRelationRepository.RelationType
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceItemKind
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceType
import com.fishit.player.core.model.repository.NxWorkVariantRepository
import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.infra.data.nx.mapper.SourceKeyParser
import com.fishit.player.infra.data.nx.mapper.base.PlaybackHintsDecoder
import com.fishit.player.infra.data.nx.writer.NxEnrichmentWriter
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NxXtreamSeriesIndexRepository @Inject constructor(
    private val workRepository: NxWorkRepository,
    private val relationRepository: NxWorkRelationRepository,
    private val sourceRefRepository: NxWorkSourceRefRepository,
    private val variantRepository: NxWorkVariantRepository,
) : XtreamSeriesIndexRepository {

    // =========================================================================
    // Season Queries
    // =========================================================================

    override fun observeSeasons(seriesId: Int): Flow<List<SeasonIndexItem>> {
        // Find the series workKey by seriesId
        return flowOf(Unit).map {
            val seriesWorkKey = findSeriesWorkKeyBySeriesId(seriesId)
                ?: return@map emptyList()

            buildSeasonIndex(seriesId, seriesWorkKey)
        }
    }

    override suspend fun getSeasons(seriesId: Int): List<SeasonIndexItem> = withContext(Dispatchers.IO) {
        val seriesWorkKey = findSeriesWorkKeyBySeriesId(seriesId)
            ?: return@withContext emptyList()

        buildSeasonIndex(seriesId, seriesWorkKey)
    }

    override suspend fun hasFreshSeasons(seriesId: Int): Boolean = withContext(Dispatchers.IO) {
        val seriesWorkKey = findSeriesWorkKeyBySeriesId(seriesId)
            ?: return@withContext false

        val episodes = relationRepository.findEpisodesForSeries(seriesWorkKey)
        if (episodes.isEmpty()) return@withContext false

        // Check if any episode relation was updated recently
        val cutoff = System.currentTimeMillis() - SeasonIndexItem.SEASON_INDEX_TTL_MS
        // Batch-fetch all episode works (avoids N+1 individual get() calls)
        val workKeys = episodes.map { it.childWorkKey }
        val works = workRepository.getBatch(workKeys)
        works.values.any { it.updatedAtMs > cutoff }
    }

    override suspend fun upsertSeasons(seriesId: Int, seasons: List<SeasonIndexItem>) {
        // In NX architecture, seasons are derived from episode data via NX_WorkRelation.
        // This method is effectively a no-op because season metadata is computed
        // from the episode collection when queried.
        //
        // The legacy implementation stored seasons in ObxSeasonIndex, but NX
        // doesn't need this because:
        // 1. Season numbers come from NX_WorkRelation.season field
        // 2. Episode counts are computed from relation queries
        // 3. Season metadata (cover, airDate) can be stored on the series NX_Work
        //
        // If specific season metadata needs persistence, it should be added to
        // NX_WorkRelation or a new NX_SeasonMetadata entity.
        UnifiedLog.d(TAG) { "upsertSeasons: NX stores seasons via NX_WorkRelation - no separate entity needed" }
    }

    override suspend fun deleteSeasons(seriesId: Int) = withContext(Dispatchers.IO) {
        // In NX, deleting seasons means deleting all episode relations for this series
        val seriesWorkKey = findSeriesWorkKeyBySeriesId(seriesId)
            ?: return@withContext

        val episodes = relationRepository.findEpisodesForSeries(seriesWorkKey)
        for (relation in episodes) {
            relationRepository.delete(
                parentWorkKey = seriesWorkKey,
                childWorkKey = relation.childWorkKey,
                relationType = RelationType.SERIES_EPISODE,
            )
        }
        UnifiedLog.d(TAG) { "deleteSeasons: Removed ${episodes.size} episode relations for series $seriesId" }
    }

    // =========================================================================
    // Episode Queries
    // =========================================================================

    override fun observeEpisodes(
        seriesId: Int,
        seasonNumber: Int,
        page: Int,
        pageSize: Int,
    ): Flow<List<EpisodeIndexItem>> {
        return flowOf(Unit).map {
            val seriesWorkKey = findSeriesWorkKeyBySeriesId(seriesId)
                ?: return@map emptyList()

            val allEpisodes = buildEpisodeIndex(seriesId, seriesWorkKey, seasonNumber)

            // Apply pagination
            val startIndex = page * pageSize
            if (startIndex >= allEpisodes.size) {
                emptyList()
            } else {
                allEpisodes.subList(startIndex, minOf(startIndex + pageSize, allEpisodes.size))
            }
        }
    }

    override suspend fun getEpisodeCount(seriesId: Int, seasonNumber: Int): Int = withContext(Dispatchers.IO) {
        val seriesWorkKey = findSeriesWorkKeyBySeriesId(seriesId)
            ?: return@withContext 0

        relationRepository.findEpisodesForSeries(seriesWorkKey)
            .count { it.seasonNumber == seasonNumber }
    }

    override suspend fun getEpisodeBySourceKey(sourceKey: String): EpisodeIndexItem? = withContext(Dispatchers.IO) {
        // Get the source ref to find the work
        val sourceRef = sourceRefRepository.getBySourceKey(sourceKey)
            ?: return@withContext null

        // Get the episode work
        val episodeWork = workRepository.get(sourceRef.workKey)
            ?: return@withContext null

        // Extract seriesId, seasonNumber, episodeNumber from sourceKey
        // Format: src:xtream:<account>:episode:series:{seriesId}:s{season}:e{episode}
        //     or: src:xtream:<account>:episode:{seriesId}_{season}_{episode}
        val episodeInfo = SourceKeyParser.extractXtreamEpisodeInfo(sourceKey)
            ?: return@withContext null
        val seriesId = episodeInfo.seriesId
        val seasonNumber = episodeInfo.season
        val episodeNumber = episodeInfo.episodeNumber

        // Get variant for playback hints
        val variants = variantRepository.findByWorkKey(episodeWork.workKey)
        val defaultVariant = variants.firstOrNull()
        val playbackHintsJson = defaultVariant?.let { variant ->
            buildPlaybackHintsJsonFromVariant(variant)
        }

        EpisodeIndexItem(
            seriesId = seriesId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            sourceKey = sourceKey,
            episodeId = SourceKeyParser.extractXtreamEpisodeId(sourceKey),
            title = episodeWork.displayTitle,
            // --- Images (ImageRef → String URL for EpisodeIndexItem DTO) ---
            thumbUrl = episodeWork.poster?.toUriString(),
            coverUrl = episodeWork.backdrop?.toUriString(),
            thumbnailUrl = episodeWork.thumbnail?.toUriString(),
            // --- Core metadata ---
            durationSecs = episodeWork.runtimeMs?.let { (it / 1000).toInt() },
            durationString = defaultVariant?.playbackHints?.get(PlaybackHintKeys.DURATION_DISPLAY),
            plot = episodeWork.plot,
            rating = episodeWork.rating,
            airDate = episodeWork.releaseDate,
            // --- External IDs ---
            tmdbId = episodeWork.tmdbId?.toIntOrNull(),
            // --- Xtream-specific ---
            addedTimestamp = episodeWork.createdAtMs.takeIf { it > 0 }?.toString(),
            customSid = defaultVariant?.playbackHints?.get(PlaybackHintKeys.Xtream.CUSTOM_SID),
            // --- Technical stream metadata ---
            videoCodec = defaultVariant?.videoCodec,
            videoWidth = defaultVariant?.qualityWidth,
            videoHeight = defaultVariant?.qualityHeight,
            videoAspectRatio = defaultVariant?.playbackHints?.get(PlaybackHintKeys.VIDEO_ASPECT_RATIO),
            audioCodec = defaultVariant?.audioCodec,
            audioChannels = defaultVariant?.playbackHints?.get(PlaybackHintKeys.AUDIO_CHANNELS)?.toIntOrNull(),
            audioLanguage = defaultVariant?.audioLang,
            bitrateKbps = defaultVariant?.bitrateKbps,
            // --- Playback ---
            playbackHintsJson = playbackHintsJson,
            lastUpdatedMs = episodeWork.updatedAtMs,
            playbackHintsUpdatedMs = if (playbackHintsJson != null) episodeWork.updatedAtMs else 0L,
        )
    }

    override suspend fun hasFreshEpisodes(seriesId: Int, seasonNumber: Int): Boolean = withContext(Dispatchers.IO) {
        val seriesWorkKey = findSeriesWorkKeyBySeriesId(seriesId)
            ?: return@withContext false

        val episodes = relationRepository.findEpisodesForSeries(seriesWorkKey)
            .filter { it.seasonNumber == seasonNumber }

        if (episodes.isEmpty()) return@withContext false

        val cutoff = System.currentTimeMillis() - EpisodeIndexItem.INDEX_TTL_MS
        // Batch-fetch all episode works (avoids N+1 individual get() calls)
        val workKeys = episodes.map { it.childWorkKey }
        val works = workRepository.getBatch(workKeys)
        works.values.any { it.updatedAtMs > cutoff }
    }

    override suspend fun upsertEpisodes(episodes: List<EpisodeIndexItem>) {
        // No-op: Episode writes are handled by XtreamDetailSync via the
        // pipeline chain (toRawMediaMetadata → normalizer → NxCatalogWriter).
        // This repository is now read-only for episodes.
        UnifiedLog.d(TAG) {
            "upsertEpisodes: no-op — writes handled by XtreamDetailSync (${episodes.size} episodes)"
        }
    }

    override suspend fun updatePlaybackHints(sourceKey: String, hintsJson: String?) = withContext(Dispatchers.IO) {
        // Get the work key from source ref
        val sourceRef = sourceRefRepository.getBySourceKey(sourceKey)
            ?: run {
                UnifiedLog.w(TAG) { "updatePlaybackHints: Source ref not found for $sourceKey" }
                return@withContext
            }

        // SSOT variantKey format: {sourceKey}#original (consistent with NxCatalogWriter)
        val variantKey = NxEnrichmentWriter.buildVariantKey(sourceKey)
        val existingVariant = variantRepository.getByVariantKey(variantKey)

        // Parse hints if provided
        val playbackUrl = hintsJson?.let { extractPlaybackUrlFromHints(it) }
        val containerExt = hintsJson?.let { extractContainerFromHints(it) }

        // Build playbackHints map (merge with existing if available)
        val playbackHints = buildMap<String, String> {
            // Keep existing hints
            existingVariant?.playbackHints?.forEach { (k, v) -> put(k, v) }
            // Override with new values
            playbackUrl?.let { put(PlaybackHintKeys.Xtream.DIRECT_SOURCE, it) }
            containerExt?.let { put(PlaybackHintKeys.Xtream.CONTAINER_EXT, it) }
        }

        val variant = NxWorkVariantRepository.Variant(
            variantKey = variantKey,
            workKey = sourceRef.workKey,
            sourceKey = sourceKey,
            label = existingVariant?.label ?: "Original",
            isDefault = existingVariant?.isDefault ?: true,
            container = containerExt ?: existingVariant?.container,
            playbackHints = playbackHints,
            updatedAtMs = System.currentTimeMillis(),
        )
        variantRepository.upsert(variant)

        UnifiedLog.d(TAG) { "updatePlaybackHints: Updated hints for $sourceKey" }
    }

    override suspend fun getPlaybackHints(sourceKey: String): EpisodePlaybackHints? = withContext(Dispatchers.IO) {
        val sourceRef = sourceRefRepository.getBySourceKey(sourceKey)
            ?: return@withContext null

        val variants = variantRepository.findByWorkKey(sourceRef.workKey)
        val variant = variants.firstOrNull() ?: return@withContext null

        // Parse episodeId from sourceKey
        val episodeId = SourceKeyParser.extractXtreamEpisodeId(sourceKey)

        EpisodePlaybackHints(
            episodeId = episodeId,
            streamId = episodeId, // Often the same
            containerExtension = variant.container,
            directUrl = variant.playbackHints[PlaybackHintKeys.Xtream.DIRECT_SOURCE],
        )
    }

    override suspend fun isPlaybackReady(sourceKey: String): Boolean = withContext(Dispatchers.IO) {
        val sourceRef = sourceRefRepository.getBySourceKey(sourceKey)
            ?: return@withContext false

        val variants = variantRepository.findByWorkKey(sourceRef.workKey)
        variants.any { it.playbackHints.isNotEmpty() }
    }

    override suspend fun deleteEpisodes(seriesId: Int, seasonNumber: Int) = withContext(Dispatchers.IO) {
        val seriesWorkKey = findSeriesWorkKeyBySeriesId(seriesId)
            ?: return@withContext

        val episodes = relationRepository.findEpisodesForSeries(seriesWorkKey)
            .filter { it.seasonNumber == seasonNumber }

        for (relation in episodes) {
            // Delete the relation
            relationRepository.delete(
                parentWorkKey = seriesWorkKey,
                childWorkKey = relation.childWorkKey,
                relationType = RelationType.SERIES_EPISODE,
            )
            // Note: We don't delete the episode NX_Work itself - it might have other references
        }

        UnifiedLog.d(TAG) { "deleteEpisodes: Removed ${episodes.size} episode relations for series $seriesId season $seasonNumber" }
    }

    override suspend fun deleteAllEpisodesForSeries(seriesId: Int) = withContext(Dispatchers.IO) {
        val seriesWorkKey = findSeriesWorkKeyBySeriesId(seriesId)
            ?: return@withContext

        val episodes = relationRepository.findEpisodesForSeries(seriesWorkKey)
        for (relation in episodes) {
            relationRepository.delete(
                parentWorkKey = seriesWorkKey,
                childWorkKey = relation.childWorkKey,
                relationType = RelationType.SERIES_EPISODE,
            )
        }

        UnifiedLog.d(TAG) { "deleteAllEpisodesForSeries: Removed ${episodes.size} episode relations for series $seriesId" }
    }

    // =========================================================================
    // TTL Management
    // =========================================================================

    override suspend fun invalidateStaleEntries(): Int = withContext(Dispatchers.IO) {
        // In NX architecture, staleness is tracked via updatedAtMs on NX_Work
        // For now, we don't auto-delete stale entries - they're refreshed on access
        // This differs from legacy which deleted stale ObxSeasonIndex/ObxEpisodeIndex
        UnifiedLog.d(TAG) { "invalidateStaleEntries: NX uses refresh-on-access pattern" }
        0
    }

    override suspend fun invalidateAll() = withContext(Dispatchers.IO) {
        // This would require deleting all SERIES_EPISODE relations for Xtream sources
        // For safety, this is a no-op in NX - use clearSource() instead
        UnifiedLog.w(TAG) { "invalidateAll: Use NxCatalogWriter.clearSourceType() instead" }
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    /**
     * Find the series workKey by Xtream seriesId.
     *
     * Searches NX_WorkSourceRef for entries with:
     * - sourceType = XTREAM
     * - sourceItemKind = SERIES
     * - sourceItemKey contains seriesId
     */
    private suspend fun findSeriesWorkKeyBySeriesId(seriesId: Int): String? {
        val sourceRefs = sourceRefRepository.findBySourceTypeAndKind(
            sourceType = SourceType.XTREAM,
            itemKind = SourceItemKind.SERIES,
            itemKeyPrefix = seriesId.toString(),
        )

        // Find exact match (sourceItemKey = seriesId)
        return sourceRefs.find { ref ->
            ref.sourceItemKey == seriesId.toString()
        }?.workKey
    }

    /**
     * Build season index from episode relations.
     */
    private suspend fun buildSeasonIndex(seriesId: Int, seriesWorkKey: String): List<SeasonIndexItem> {
        val relations = relationRepository.findEpisodesForSeries(seriesWorkKey)
        if (relations.isEmpty()) return emptyList()

        // Batch-fetch all episode works in ONE query (avoids N+1)
        val allWorkKeys = relations.map { it.childWorkKey }
        val works = workRepository.getBatch(allWorkKeys)

        // Group by season number
        val seasonGroups = relations.groupBy { it.seasonNumber ?: 0 }

        return seasonGroups.map { (seasonNumber, episodes) ->
            // Get latest update time from pre-fetched works
            val latestUpdate = episodes.maxOfOrNull { relation ->
                works[relation.childWorkKey]?.updatedAtMs ?: 0L
            } ?: System.currentTimeMillis()

            SeasonIndexItem(
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeCount = episodes.size,
                name = "Season $seasonNumber",
                coverUrl = null, // Not stored per-season in NX
                airDate = null,
                lastUpdatedMs = latestUpdate,
            )
        }.sortedBy { it.seasonNumber }
    }

    /**
     * Build episode index from relations and works.
     *
     * Uses batch queries (3 total) instead of N+1 individual queries per episode.
     * For a series with 274 episodes, this reduces ~822 queries to 4.
     */
    private suspend fun buildEpisodeIndex(
        seriesId: Int,
        seriesWorkKey: String,
        seasonNumber: Int,
    ): List<EpisodeIndexItem> {
        val relations = relationRepository.findEpisodesForSeries(seriesWorkKey)
            .filter { it.seasonNumber == seasonNumber }
            .sortedBy { it.episodeNumber }

        if (relations.isEmpty()) return emptyList()

        // Batch-fetch ALL data in 3 queries (instead of 3*N)
        val childKeys = relations.map { it.childWorkKey }
        val works = workRepository.getBatch(childKeys)
        val sourceRefs = sourceRefRepository.findByWorkKeysBatch(childKeys)
        val variants = variantRepository.findByWorkKeysBatch(childKeys)

        return relations.mapNotNull { relation ->
            val episodeWork = works[relation.childWorkKey] ?: return@mapNotNull null
            val episodeSourceRefs = sourceRefs[relation.childWorkKey] ?: emptyList()
            val xtreamSourceRef = episodeSourceRefs.find { it.sourceType == SourceType.XTREAM }
            val episodeVariants = variants[relation.childWorkKey] ?: emptyList()

            val sourceKey = xtreamSourceRef?.sourceKey
                ?: SourceKeyParser.buildSourceKey(
                    com.fishit.player.core.model.SourceType.XTREAM,
                    "unknown",
                    "episode:series:${seriesId}:s${seasonNumber}:e${relation.episodeNumber}",
                )

            val defaultVariant = episodeVariants.firstOrNull()

            EpisodeIndexItem(
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeNumber = relation.episodeNumber ?: 0,
                sourceKey = sourceKey,
                episodeId = SourceKeyParser.extractXtreamEpisodeId(sourceKey),
                title = episodeWork.displayTitle,
                // --- Images (ImageRef → String URL for EpisodeIndexItem DTO) ---
                thumbUrl = episodeWork.poster?.toUriString(),
                coverUrl = episodeWork.backdrop?.toUriString(),
                thumbnailUrl = episodeWork.thumbnail?.toUriString(),
                // --- Core metadata ---
                durationSecs = episodeWork.runtimeMs?.let { (it / 1000).toInt() },
                durationString = defaultVariant?.playbackHints?.get(PlaybackHintKeys.DURATION_DISPLAY),
                plot = episodeWork.plot, // Full plot — never truncated
                rating = episodeWork.rating,
                airDate = episodeWork.releaseDate,
                // --- External IDs ---
                tmdbId = episodeWork.tmdbId?.toIntOrNull(),
                // --- Xtream-specific ---
                addedTimestamp = episodeWork.createdAtMs.takeIf { it > 0 }?.toString(),
                customSid = defaultVariant?.playbackHints?.get(PlaybackHintKeys.Xtream.CUSTOM_SID),
                // --- Technical stream metadata ---
                videoCodec = defaultVariant?.videoCodec,
                videoWidth = defaultVariant?.qualityWidth,
                videoHeight = defaultVariant?.qualityHeight,
                videoAspectRatio = defaultVariant?.playbackHints?.get(PlaybackHintKeys.VIDEO_ASPECT_RATIO),
                audioCodec = defaultVariant?.audioCodec,
                audioChannels = defaultVariant?.playbackHints?.get(PlaybackHintKeys.AUDIO_CHANNELS)?.toIntOrNull(),
                audioLanguage = defaultVariant?.audioLang,
                bitrateKbps = defaultVariant?.bitrateKbps,
                // --- Playback ---
                playbackHintsJson = defaultVariant?.let { buildPlaybackHintsJsonFromVariant(it) },
                lastUpdatedMs = episodeWork.updatedAtMs,
                playbackHintsUpdatedMs = defaultVariant?.let { episodeWork.updatedAtMs } ?: 0L,
            )
        }
    }

    private fun extractPlaybackUrlFromHints(hintsJson: String): String? {
        val hints = PlaybackHintsDecoder.decodeJson(hintsJson) ?: return null
        return hints[PlaybackHintKeys.Xtream.DIRECT_SOURCE]
    }

    private fun extractContainerFromHints(hintsJson: String): String? {
        val hints = PlaybackHintsDecoder.decodeJson(hintsJson) ?: return null
        return hints[PlaybackHintKeys.Xtream.CONTAINER_EXT]
    }

    private fun buildPlaybackHintsJsonFromVariant(variant: NxWorkVariantRepository.Variant): String =
        PlaybackHintsDecoder.encodeToJson(variant.playbackHints) ?: "{}"

    companion object {
        private const val TAG = "NxSeriesIndex"
    }
}
