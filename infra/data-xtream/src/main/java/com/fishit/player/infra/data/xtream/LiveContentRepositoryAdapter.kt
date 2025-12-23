package com.fishit.player.infra.data.xtream

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.feature.live.domain.LiveCategory
import com.fishit.player.feature.live.domain.LiveChannel
import com.fishit.player.feature.live.domain.LiveContentRepository
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter implementation of [LiveContentRepository] for the Live TV feature.
 *
 * Maps RawMediaMetadata from [XtreamLiveRepository] to LiveChannel domain models.
 *
 * **Architecture:**
 * - Feature layer defines LiveContentRepository interface
 * - Data layer provides this implementation
 * - Converts RawMediaMetadata → LiveChannel
 *
 * **Note on Favorites/Recent:**
 * Currently favorites and recent channels are not persisted.
 * These features require additional ObjectBox entities.
 */
@Singleton
class LiveContentRepositoryAdapter @Inject constructor(
    private val liveRepository: XtreamLiveRepository
) : LiveContentRepository {

    companion object {
        private const val TAG = "LiveContentRepoAdapter"
    }

    // In-memory storage for favorites and recent (should be persisted later)
    private val favoriteIds = mutableSetOf<String>()
    private val recentChannelIds = mutableListOf<String>()
    private val recentChannelCache = mutableMapOf<String, LiveChannel>()

    override fun observeChannels(categoryId: String?): Flow<List<LiveChannel>> {
        return liveRepository.observeChannels(categoryId)
            .map { items ->
                items.map { it.toLiveChannel() }
            }
    }

    override fun observeCategories(): Flow<List<LiveCategory>> {
        return liveRepository.observeChannels()
            .map { items ->
                items
                    .mapNotNull { item ->
                        val categoryId = item.extras["categoryId"]
                        val categoryName = item.extras["categoryName"]
                        if (categoryId != null && categoryName != null) {
                            categoryId to categoryName
                        } else null
                    }
                    .groupBy { it.first }
                    .map { (id, pairs) ->
                        LiveCategory(
                            id = id,
                            name = pairs.first().second,
                            channelCount = pairs.size
                        )
                    }
                    .sortedBy { it.name }
            }
    }

    override suspend fun search(query: String, limit: Int): List<LiveChannel> {
        return try {
            liveRepository.search(query, limit)
                .map { it.toLiveChannel() }
        } catch (e: Exception) {
            UnifiedLog.e(TAG) { "Search error: ${e.message}" }
            emptyList()
        }
    }

    override suspend fun getRecentChannels(limit: Int): List<LiveChannel> {
        return recentChannelIds.take(limit).mapNotNull { recentChannelCache[it] }
    }

    override fun observeFavorites(): Flow<List<LiveChannel>> {
        return liveRepository.observeChannels()
            .map { items ->
                items
                    .filter { favoriteIds.contains(it.sourceId) }
                    .map { it.toLiveChannel() }
            }
    }

    override suspend fun setFavorite(channelId: String, isFavorite: Boolean) {
        if (isFavorite) {
            favoriteIds.add(channelId)
        } else {
            favoriteIds.remove(channelId)
        }
        UnifiedLog.d(TAG) { "Set favorite $channelId = $isFavorite" }
    }

    override suspend fun recordWatched(channelId: String) {
        // Move to front of recent list
        recentChannelIds.remove(channelId)
        recentChannelIds.add(0, channelId)
        
        // Keep only last 20
        while (recentChannelIds.size > 20) {
            val removed = recentChannelIds.removeLast()
            recentChannelCache.remove(removed)
        }
        
        // Cache the channel data
        try {
            liveRepository.getBySourceId(channelId)?.let { metadata ->
                recentChannelCache[channelId] = metadata.toLiveChannel()
            }
        } catch (e: Exception) {
            UnifiedLog.e(TAG) { "Failed to cache recent channel: ${e.message}" }
        }
    }

    /**
     * Map RawMediaMetadata to LiveChannel domain model.
     */
    private fun RawMediaMetadata.toLiveChannel(): LiveChannel {
        return LiveChannel(
            id = sourceId,
            name = title,
            channelNumber = extras["channelNumber"]?.toIntOrNull(),
            logo = posterUrl?.let { ImageRef.Remote(it) },
            categoryId = extras["categoryId"],
            categoryName = extras["categoryName"],
            currentProgram = extras["currentProgram"],
            currentProgramDescription = extras["currentProgramDescription"],
            programStart = extras["programStart"]?.toLongOrNull(),
            programEnd = extras["programEnd"]?.toLongOrNull(),
            sourceType = SourceType.XTREAM,
            isFavorite = favoriteIds.contains(sourceId),
            lastWatched = if (recentChannelIds.contains(sourceId)) {
                System.currentTimeMillis()
            } else null
        )
    }
}
