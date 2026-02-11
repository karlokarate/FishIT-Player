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
 * - episodeId → sourceKey `src:xtream:<account>:episode:<seriesId>_<season>_<episode>`
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
import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceItemKind
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository.SourceType
import com.fishit.player.core.model.repository.NxWorkVariantRepository
import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.infra.data.nx.mapper.SourceKeyParser
import com.fishit.player.infra.data.nx.mapper.base.PlaybackHintsDecoder
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
        // For NX, we use the work's updatedAtMs as proxy for freshness
        episodes.any { relation ->
            val episodeWork = workRepository.get(relation.childWorkKey)
            episodeWork?.updatedAtMs?.let { it > cutoff } ?: false
        }
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
        // Format: src:xtream:<account>:episode:<seriesId>_<season>_<episode>
        val seriesId = SourceKeyParser.extractXtreamSeriesIdFromEpisode(sourceKey)
            ?: return@withContext null
        
        val itemKey = SourceKeyParser.extractItemKey(sourceKey)
        if (itemKey == null) return@withContext null
        
        val episodeParts = itemKey.split("_")
        if (episodeParts.size < 3) return@withContext null
        
        val seasonNumber = episodeParts[1].toIntOrNull() ?: return@withContext null
        val episodeNumber = episodeParts[2].toIntOrNull() ?: return@withContext null

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
            durationString = defaultVariant?.playbackHints?.get("duration_display"),
            plot = episodeWork.plot,
            rating = episodeWork.rating,
            airDate = episodeWork.releaseDate,
            // --- External IDs ---
            tmdbId = episodeWork.tmdbId?.toIntOrNull(),
            // --- Xtream-specific ---
            addedTimestamp = episodeWork.createdAtMs.takeIf { it > 0 }?.toString(),
            customSid = defaultVariant?.playbackHints?.get("custom_sid"),
            // --- Technical stream metadata ---
            videoCodec = defaultVariant?.videoCodec,
            videoWidth = defaultVariant?.playbackHints?.get("video_width")?.toIntOrNull(),
            videoHeight = defaultVariant?.qualityHeight,
            videoAspectRatio = defaultVariant?.playbackHints?.get("aspect_ratio"),
            audioCodec = defaultVariant?.audioCodec,
            audioChannels = defaultVariant?.playbackHints?.get("audio_channels")?.toIntOrNull(),
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
        episodes.any { relation ->
            val episodeWork = workRepository.get(relation.childWorkKey)
            episodeWork?.updatedAtMs?.let { it > cutoff } ?: false
        }
    }

    override suspend fun upsertEpisodes(episodes: List<EpisodeIndexItem>) = withContext(Dispatchers.IO) {
        if (episodes.isEmpty()) return@withContext

        val seriesId = episodes.first().seriesId
        val seriesWorkKey = findSeriesWorkKeyBySeriesId(seriesId)

        for (episode in episodes) {
            // Build episode workKey
            val episodeWorkKey = buildEpisodeWorkKey(episode)

            // Upsert episode as NX_Work — ALL metadata preserved
            val episodeWork = NxWorkRepository.Work(
                workKey = episodeWorkKey,
                type = WorkType.EPISODE,
                displayTitle = episode.title ?: "Episode ${episode.episodeNumber}",
                season = episode.seasonNumber,
                episode = episode.episodeNumber,
                year = null,
                runtimeMs = episode.durationSecs?.let { it.toLong() * 1000 },
                poster = ImageRef.fromString(episode.thumbUrl),
                backdrop = ImageRef.fromString(episode.coverUrl),
                thumbnail = ImageRef.fromString(episode.thumbnailUrl),
                rating = episode.rating,
                plot = episode.plot, // Full plot — never truncated
                releaseDate = episode.airDate,
                tmdbId = episode.tmdbId?.toString(),
                createdAtMs = episode.addedTimestamp?.toLongOrNull() ?: 0L,
                updatedAtMs = episode.lastUpdatedMs,
            )
            workRepository.upsert(episodeWork)

            // Upsert source ref
            val sourceRef = NxWorkSourceRefRepository.SourceRef(
                sourceKey = episode.sourceKey,
                workKey = episodeWorkKey,
                sourceType = SourceType.XTREAM,
                accountKey = SourceKeyParser.extractAccountKey(episode.sourceKey),
                sourceItemKind = SourceItemKind.EPISODE,
                sourceItemKey = "${episode.seriesId}_${episode.seasonNumber}_${episode.episodeNumber}",
                sourceTitle = episode.title,
                lastSeenAtMs = episode.lastUpdatedMs,
            )
            sourceRefRepository.upsert(sourceRef)

            // Link episode to series via NX_WorkRelation (if series exists)
            if (seriesWorkKey != null) {
                val relation = NxWorkRelationRepository.Relation(
                    parentWorkKey = seriesWorkKey,
                    childWorkKey = episodeWorkKey,
                    relationType = RelationType.SERIES_EPISODE,
                    seasonNumber = episode.seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    orderIndex = episode.seasonNumber * 1000 + episode.episodeNumber, // Composite sort order
                )
                relationRepository.upsert(relation)
            }

            // Upsert playback variant — ALL technical metadata preserved
            val hintsJson = episode.playbackHintsJson
            val hasVariantData = !hintsJson.isNullOrEmpty() ||
                episode.videoCodec != null || episode.audioCodec != null || episode.bitrateKbps != null

            if (hasVariantData) {
                val variantKey = "v:${episode.sourceKey}:default"
                val playbackUrl = hintsJson?.let { extractPlaybackUrlFromHints(it) }
                val containerExt = hintsJson?.let { extractContainerFromHints(it) }

                // Build playbackHints map — includes ALL source-specific data
                val playbackHints = buildMap<String, String> {
                    playbackUrl?.let { put(PlaybackHintKeys.Xtream.DIRECT_SOURCE, it) }
                    containerExt?.let { put(PlaybackHintKeys.Xtream.CONTAINER_EXT, it) }
                    episode.customSid?.let { put("custom_sid", it) }
                    episode.videoWidth?.let { put(PlaybackHintKeys.VIDEO_WIDTH, it.toString()) }
                    episode.videoAspectRatio?.let { put("aspect_ratio", it) }
                    episode.audioChannels?.let { put(PlaybackHintKeys.AUDIO_CHANNELS, it.toString()) }
                    episode.audioLanguage?.let { put("audio_language", it) }
                    episode.durationString?.let { put("duration_display", it) }
                }

                val variant = NxWorkVariantRepository.Variant(
                    variantKey = variantKey,
                    workKey = episodeWorkKey,
                    sourceKey = episode.sourceKey,
                    label = "source",
                    isDefault = true,
                    // Technical metadata in dedicated fields
                    qualityHeight = episode.videoHeight,
                    bitrateKbps = episode.bitrateKbps,
                    container = containerExt,
                    videoCodec = episode.videoCodec,
                    audioCodec = episode.audioCodec,
                    audioLang = episode.audioLanguage,
                    durationMs = episode.durationSecs?.let { it.toLong() * 1000 },
                    playbackHints = playbackHints,
                    updatedAtMs = episode.lastUpdatedMs,
                )
                variantRepository.upsert(variant)
            }
        }

        UnifiedLog.d(TAG) { "upsertEpisodes: Persisted ${episodes.size} episodes for series $seriesId" }
    }

    override suspend fun updatePlaybackHints(sourceKey: String, hintsJson: String?) = withContext(Dispatchers.IO) {
        // Get the work key from source ref
        val sourceRef = sourceRefRepository.getBySourceKey(sourceKey)
            ?: run {
                UnifiedLog.w(TAG) { "updatePlaybackHints: Source ref not found for $sourceKey" }
                return@withContext
            }

        // Update or create variant with playback hints
        val variantKey = "v:$sourceKey:default"
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
            label = existingVariant?.label ?: "source",
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
            directUrl = variant.playbackHints["direct_url"],
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

        // Group by season number
        val seasonGroups = relations.groupBy { it.seasonNumber ?: 0 }

        return seasonGroups.map { (seasonNumber, episodes) ->
            // Get latest update time from episodes
            val latestUpdate = episodes.maxOfOrNull { relation ->
                workRepository.get(relation.childWorkKey)?.updatedAtMs ?: 0L
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
     */
    private suspend fun buildEpisodeIndex(
        seriesId: Int,
        seriesWorkKey: String,
        seasonNumber: Int,
    ): List<EpisodeIndexItem> {
        val relations = relationRepository.findEpisodesForSeries(seriesWorkKey)
            .filter { it.seasonNumber == seasonNumber }
            .sortedBy { it.episodeNumber }

        return relations.mapNotNull { relation ->
            val episodeWork = workRepository.get(relation.childWorkKey) ?: return@mapNotNull null
            val sourceRefs = sourceRefRepository.findByWorkKey(relation.childWorkKey)
            val xtreamSourceRef = sourceRefs.find { it.sourceType == SourceType.XTREAM }
            val variants = variantRepository.findByWorkKey(relation.childWorkKey)

            val sourceKey = xtreamSourceRef?.sourceKey
                ?: "src:xtream:unknown:episode:${seriesId}_${seasonNumber}_${relation.episodeNumber}"

            val defaultVariant = variants.firstOrNull()

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
                durationString = defaultVariant?.playbackHints?.get("duration_display"),
                plot = episodeWork.plot, // Full plot — never truncated
                rating = episodeWork.rating,
                airDate = episodeWork.releaseDate,
                // --- External IDs ---
                tmdbId = episodeWork.tmdbId?.toIntOrNull(),
                // --- Xtream-specific ---
                addedTimestamp = episodeWork.createdAtMs.takeIf { it > 0 }?.toString(),
                customSid = defaultVariant?.playbackHints?.get("custom_sid"),
                // --- Technical stream metadata ---
                videoCodec = defaultVariant?.videoCodec,
                videoWidth = defaultVariant?.playbackHints?.get("video_width")?.toIntOrNull(),
                videoHeight = defaultVariant?.qualityHeight,
                videoAspectRatio = defaultVariant?.playbackHints?.get("aspect_ratio"),
                audioCodec = defaultVariant?.audioCodec,
                audioChannels = defaultVariant?.playbackHints?.get("audio_channels")?.toIntOrNull(),
                audioLanguage = defaultVariant?.audioLang,
                bitrateKbps = defaultVariant?.bitrateKbps,
                // --- Playback ---
                playbackHintsJson = defaultVariant?.let { buildPlaybackHintsJsonFromVariant(it) },
                lastUpdatedMs = episodeWork.updatedAtMs,
                playbackHintsUpdatedMs = defaultVariant?.let { episodeWork.updatedAtMs } ?: 0L,
            )
        }
    }

    private fun buildEpisodeWorkKey(episode: EpisodeIndexItem): String {
        val slug = (episode.title ?: "episode-${episode.episodeNumber}")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(30)
        return "episode:${episode.seriesId}-s${episode.seasonNumber}e${episode.episodeNumber}-$slug:unknown"
    }

    private fun extractPlaybackUrlFromHints(hintsJson: String): String? {
        val hints = PlaybackHintsDecoder.decodeJson(hintsJson) ?: return null
        return hints[PlaybackHintKeys.Xtream.DIRECT_SOURCE]
            ?: hints["direct_url"] // legacy fallback
            ?: hints["directUrl"]  // legacy fallback
    }

    private fun extractContainerFromHints(hintsJson: String): String? {
        val hints = PlaybackHintsDecoder.decodeJson(hintsJson) ?: return null
        return hints[PlaybackHintKeys.Xtream.CONTAINER_EXT]
            ?: hints["container_extension"] // legacy fallback
            ?: hints["containerExtension"]  // legacy fallback
    }

    private fun buildPlaybackHintsJsonFromVariant(variant: NxWorkVariantRepository.Variant): String =
        PlaybackHintsDecoder.encodeToJson(variant.playbackHints) ?: "{}"

    companion object {
        private const val TAG = "NxSeriesIndex"
    }
}
