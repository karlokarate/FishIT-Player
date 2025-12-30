package com.fishit.player.infra.data.xtream

import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import com.fishit.player.core.persistence.obx.ObxEpisodeIndex
import com.fishit.player.core.persistence.obx.ObxEpisodeIndex_
import com.fishit.player.core.persistence.obx.ObxSeasonIndex
import com.fishit.player.core.persistence.obx.ObxSeasonIndex_
import com.fishit.player.infra.logging.UnifiedLog
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBox-backed implementation of [XtreamSeriesIndexRepository].
 *
 * **Architecture:**
 * - Uses ObxSeasonIndex and ObxEpisodeIndex entities
 * - Provides reactive Flows for UI consumption
 * - Implements TTL-based invalidation
 * - Thread-safe via Dispatchers.IO
 *
 * **Performance:**
 * - Bulk operations use runInTx for single transaction
 * - Paging via ObjectBox find(offset, limit)
 * - Indexes on seriesId, seasonNumber, sourceKey for fast lookups
 */
@Singleton
class ObxXtreamSeriesIndexRepository @Inject constructor(
    private val boxStore: BoxStore,
) : XtreamSeriesIndexRepository {

    companion object {
        private const val TAG = "ObxSeriesIndexRepo"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val seasonBox by lazy { boxStore.boxFor<ObxSeasonIndex>() }
    private val episodeBox by lazy { boxStore.boxFor<ObxEpisodeIndex>() }

    // =========================================================================
    // Season Index
    // =========================================================================

    override fun observeSeasons(seriesId: Int): Flow<List<SeasonIndexItem>> {
        val query = seasonBox.query(ObxSeasonIndex_.seriesId.equal(seriesId.toLong()))
            .order(ObxSeasonIndex_.seasonNumber)
            .build()
        
        return query.asFlow().map { entities -> entities.map { it.toSeasonIndexItem() } }
    }

    override suspend fun getSeasons(seriesId: Int): List<SeasonIndexItem> = withContext(Dispatchers.IO) {
        seasonBox.query(ObxSeasonIndex_.seriesId.equal(seriesId.toLong()))
            .order(ObxSeasonIndex_.seasonNumber)
            .build()
            .find()
            .map { it.toSeasonIndexItem() }
    }

    override suspend fun hasFreshSeasons(seriesId: Int): Boolean = withContext(Dispatchers.IO) {
        val seasons = seasonBox.query(ObxSeasonIndex_.seriesId.equal(seriesId.toLong()))
            .build()
            .find()
        
        if (seasons.isEmpty()) return@withContext false
        
        // All seasons must be fresh
        val cutoff = System.currentTimeMillis() - SeasonIndexItem.SEASON_INDEX_TTL_MS
        seasons.all { it.lastUpdatedMs > cutoff }
    }

    override suspend fun upsertSeasons(seriesId: Int, seasons: List<SeasonIndexItem>) = withContext(Dispatchers.IO) {
        boxStore.runInTx {
            for (season in seasons) {
                // Find existing by seriesId + seasonNumber
                val existing = seasonBox.query(
                    ObxSeasonIndex_.seriesId.equal(seriesId.toLong())
                        .and(ObxSeasonIndex_.seasonNumber.equal(season.seasonNumber.toLong()))
                ).build().findFirst()

                val entity = if (existing != null) {
                    existing.copy(
                        episodeCount = season.episodeCount ?: existing.episodeCount,
                        name = season.name ?: existing.name,
                        coverUrl = season.coverUrl ?: existing.coverUrl,
                        airDate = season.airDate ?: existing.airDate,
                        lastUpdatedMs = System.currentTimeMillis(),
                    )
                } else {
                    ObxSeasonIndex(
                        seriesId = seriesId,
                        seasonNumber = season.seasonNumber,
                        episodeCount = season.episodeCount,
                        name = season.name,
                        coverUrl = season.coverUrl,
                        airDate = season.airDate,
                        lastUpdatedMs = System.currentTimeMillis(),
                    )
                }
                
                seasonBox.put(entity)
            }
        }
        
        UnifiedLog.d(TAG) { "Upserted ${seasons.size} seasons for series $seriesId" }
    }

    override suspend fun deleteSeasons(seriesId: Int) = withContext(Dispatchers.IO) {
        val removed = seasonBox.query(ObxSeasonIndex_.seriesId.equal(seriesId.toLong()))
            .build()
            .remove()
        
        UnifiedLog.d(TAG) { "Deleted $removed seasons for series $seriesId" }
    }

    // =========================================================================
    // Episode Index
    // =========================================================================

    override fun observeEpisodes(
        seriesId: Int,
        seasonNumber: Int,
        page: Int,
        pageSize: Int,
    ): Flow<List<EpisodeIndexItem>> {
        val query = episodeBox.query(
            ObxEpisodeIndex_.seriesId.equal(seriesId.toLong())
                .and(ObxEpisodeIndex_.seasonNumber.equal(seasonNumber.toLong()))
        )
            .order(ObxEpisodeIndex_.episodeNumber)
            .build()
        
        val offset = (page * pageSize).toLong()
        
        return query.asFlow().map { entities -> 
            // Apply paging manually in map (ObjectBox flow doesn't support offset)
            entities.drop(page * pageSize).take(pageSize).map { it.toEpisodeIndexItem() }
        }
    }

    override suspend fun getEpisodeCount(seriesId: Int, seasonNumber: Int): Int = withContext(Dispatchers.IO) {
        episodeBox.query(
            ObxEpisodeIndex_.seriesId.equal(seriesId.toLong())
                .and(ObxEpisodeIndex_.seasonNumber.equal(seasonNumber.toLong()))
        ).build().count().toInt()
    }

    override suspend fun getEpisodeBySourceKey(sourceKey: String): EpisodeIndexItem? = withContext(Dispatchers.IO) {
        episodeBox.query(ObxEpisodeIndex_.sourceKey.equal(sourceKey))
            .build()
            .findFirst()
            ?.toEpisodeIndexItem()
    }

    override suspend fun hasFreshEpisodes(seriesId: Int, seasonNumber: Int): Boolean = withContext(Dispatchers.IO) {
        val episodes = episodeBox.query(
            ObxEpisodeIndex_.seriesId.equal(seriesId.toLong())
                .and(ObxEpisodeIndex_.seasonNumber.equal(seasonNumber.toLong()))
        ).build().find()
        
        if (episodes.isEmpty()) return@withContext false
        
        val cutoff = System.currentTimeMillis() - EpisodeIndexItem.INDEX_TTL_MS
        episodes.any { it.lastUpdatedMs > cutoff }
    }

    override suspend fun upsertEpisodes(episodes: List<EpisodeIndexItem>) = withContext(Dispatchers.IO) {
        boxStore.runInTx {
            for (episode in episodes) {
                // Find existing by sourceKey
                val existing = episodeBox.query(ObxEpisodeIndex_.sourceKey.equal(episode.sourceKey))
                    .build()
                    .findFirst()

                val entity = if (existing != null) {
                    existing.copy(
                        episodeId = episode.episodeId ?: existing.episodeId,
                        title = episode.title ?: existing.title,
                        thumbUrl = episode.thumbUrl ?: existing.thumbUrl,
                        durationSecs = episode.durationSecs ?: existing.durationSecs,
                        plotBrief = episode.plotBrief ?: existing.plotBrief,
                        rating = episode.rating ?: existing.rating,
                        airDate = episode.airDate ?: existing.airDate,
                        playbackHintsJson = episode.playbackHintsJson ?: existing.playbackHintsJson,
                        lastUpdatedMs = System.currentTimeMillis(),
                        playbackHintsUpdatedMs = if (episode.playbackHintsJson != null) 
                            System.currentTimeMillis() else existing.playbackHintsUpdatedMs,
                    )
                } else {
                    ObxEpisodeIndex(
                        seriesId = episode.seriesId,
                        seasonNumber = episode.seasonNumber,
                        episodeNumber = episode.episodeNumber,
                        sourceKey = episode.sourceKey,
                        episodeId = episode.episodeId,
                        title = episode.title,
                        thumbUrl = episode.thumbUrl,
                        durationSecs = episode.durationSecs,
                        plotBrief = episode.plotBrief,
                        rating = episode.rating,
                        airDate = episode.airDate,
                        playbackHintsJson = episode.playbackHintsJson,
                        lastUpdatedMs = System.currentTimeMillis(),
                        playbackHintsUpdatedMs = if (episode.playbackHintsJson != null) 
                            System.currentTimeMillis() else 0L,
                    )
                }
                
                episodeBox.put(entity)
            }
        }
        
        UnifiedLog.d(TAG) { "Upserted ${episodes.size} episodes" }
    }

    override suspend fun updatePlaybackHints(sourceKey: String, hintsJson: String?) = withContext(Dispatchers.IO) {
        val existing = episodeBox.query(ObxEpisodeIndex_.sourceKey.equal(sourceKey))
            .build()
            .findFirst()
        
        if (existing != null) {
            val updated = existing.copy(
                playbackHintsJson = hintsJson ?: existing.playbackHintsJson,
                playbackHintsUpdatedMs = if (hintsJson != null) System.currentTimeMillis() else existing.playbackHintsUpdatedMs,
            )
            episodeBox.put(updated)
            UnifiedLog.d(TAG) { "Updated playback hints for $sourceKey" }
        } else {
            UnifiedLog.w(TAG) { "Episode not found for hints update: $sourceKey" }
        }
    }

    override suspend fun getPlaybackHints(sourceKey: String): EpisodePlaybackHints? = withContext(Dispatchers.IO) {
        val episode = episodeBox.query(ObxEpisodeIndex_.sourceKey.equal(sourceKey))
            .build()
            .findFirst() ?: return@withContext null
        
        val hintsJson = episode.playbackHintsJson ?: return@withContext EpisodePlaybackHints(
            episodeId = episode.episodeId,
            streamId = episode.episodeId, // Often same
            containerExtension = null,
            directUrl = null,
        )
        
        try {
            val jsonObj = json.parseToJsonElement(hintsJson).jsonObject
            EpisodePlaybackHints(
                episodeId = episode.episodeId,
                streamId = jsonObj["stream_id"]?.jsonPrimitive?.intOrNull 
                    ?: jsonObj["streamId"]?.jsonPrimitive?.intOrNull,
                containerExtension = jsonObj["container_extension"]?.jsonPrimitive?.contentOrNull
                    ?: jsonObj["containerExtension"]?.jsonPrimitive?.contentOrNull,
                directUrl = jsonObj["direct_url"]?.jsonPrimitive?.contentOrNull
                    ?: jsonObj["directUrl"]?.jsonPrimitive?.contentOrNull,
            )
        } catch (e: Exception) {
            UnifiedLog.w(TAG) { "Failed to parse playback hints for $sourceKey: ${e.message}" }
            EpisodePlaybackHints(
                episodeId = episode.episodeId,
                streamId = episode.episodeId,
                containerExtension = null,
                directUrl = null,
            )
        }
    }

    override suspend fun isPlaybackReady(sourceKey: String): Boolean = withContext(Dispatchers.IO) {
        val episode = episodeBox.query(ObxEpisodeIndex_.sourceKey.equal(sourceKey))
            .build()
            .findFirst() ?: return@withContext false
        
        episode.isPlaybackReady
    }

    override suspend fun deleteEpisodes(seriesId: Int, seasonNumber: Int) = withContext(Dispatchers.IO) {
        val removed = episodeBox.query(
            ObxEpisodeIndex_.seriesId.equal(seriesId.toLong())
                .and(ObxEpisodeIndex_.seasonNumber.equal(seasonNumber.toLong()))
        ).build().remove()
        
        UnifiedLog.d(TAG) { "Deleted $removed episodes for series $seriesId season $seasonNumber" }
    }

    override suspend fun deleteAllEpisodesForSeries(seriesId: Int) = withContext(Dispatchers.IO) {
        val removed = episodeBox.query(ObxEpisodeIndex_.seriesId.equal(seriesId.toLong()))
            .build()
            .remove()
        
        UnifiedLog.d(TAG) { "Deleted $removed episodes for series $seriesId" }
    }

    // =========================================================================
    // TTL Management
    // =========================================================================

    override suspend fun invalidateStaleEntries(): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val seasonCutoff = now - SeasonIndexItem.SEASON_INDEX_TTL_MS
        val episodeCutoff = now - EpisodeIndexItem.INDEX_TTL_MS
        
        val staleSeasons = seasonBox.query(ObxSeasonIndex_.lastUpdatedMs.less(seasonCutoff))
            .build()
            .remove()
        
        val staleEpisodes = episodeBox.query(ObxEpisodeIndex_.lastUpdatedMs.less(episodeCutoff))
            .build()
            .remove()
        
        val total = (staleSeasons + staleEpisodes).toInt()
        UnifiedLog.d(TAG) { "Invalidated $staleSeasons stale seasons, $staleEpisodes stale episodes" }
        total
    }

    override suspend fun invalidateAll() = withContext(Dispatchers.IO) {
        val seasons = seasonBox.count()
        val episodes = episodeBox.count()
        
        seasonBox.removeAll()
        episodeBox.removeAll()
        
        UnifiedLog.i(TAG) { "Invalidated all: $seasons seasons, $episodes episodes" }
    }

    // =========================================================================
    // Converters
    // =========================================================================

    private fun ObxSeasonIndex.toSeasonIndexItem() = SeasonIndexItem(
        seriesId = seriesId,
        seasonNumber = seasonNumber,
        episodeCount = episodeCount,
        name = name,
        coverUrl = coverUrl,
        airDate = airDate,
        lastUpdatedMs = lastUpdatedMs,
    )

    private fun ObxEpisodeIndex.toEpisodeIndexItem() = EpisodeIndexItem(
        seriesId = seriesId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        sourceKey = sourceKey,
        episodeId = episodeId,
        title = title,
        thumbUrl = thumbUrl,
        durationSecs = durationSecs,
        plotBrief = plotBrief,
        rating = rating,
        airDate = airDate,
        playbackHintsJson = playbackHintsJson,
        lastUpdatedMs = lastUpdatedMs,
        playbackHintsUpdatedMs = playbackHintsUpdatedMs,
    )
}

// =========================================================================
// Helper: Build Playback Hints JSON
// =========================================================================

/**
 * Build playback hints JSON from episode data.
 */
fun buildPlaybackHintsJson(
    streamId: Int?,
    containerExtension: String?,
    directUrl: String? = null,
): String = buildJsonObject {
    streamId?.let { put("stream_id", it) }
    containerExtension?.let { put("container_extension", it) }
    directUrl?.let { put("direct_url", it) }
}.toString()
