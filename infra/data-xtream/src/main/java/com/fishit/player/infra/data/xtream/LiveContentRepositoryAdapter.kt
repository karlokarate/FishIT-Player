package com.fishit.player.infra.data.xtream

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import com.fishit.player.core.persistence.obx.ObxCategory
import com.fishit.player.core.persistence.obx.ObxCategory_
import com.fishit.player.core.persistence.obx.ObxEpgNowNext
import com.fishit.player.core.persistence.obx.ObxEpgNowNext_
import com.fishit.player.core.persistence.obx.ObxLive
import com.fishit.player.core.persistence.obx.ObxLive_
import com.fishit.player.feature.live.domain.LiveCategory
import com.fishit.player.feature.live.domain.LiveChannel
import com.fishit.player.feature.live.domain.LiveContentRepository
import com.fishit.player.infra.logging.UnifiedLog
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter implementation of [LiveContentRepository] for the Live TV feature.
 *
 * Maps ObjectBox entities directly to LiveChannel domain models, providing
 * full access to entity fields like categoryId, EPG data, and channel metadata.
 *
 * **Architecture:**
 * - Feature layer defines LiveContentRepository interface
 * - Data layer provides this implementation
 * - Converts ObxLive → LiveChannel (direct entity mapping)
 *
 * **v2 Compliance (MEDIA_NORMALIZATION_CONTRACT.md):**
 * - Data layer maps entities to feature domain models
 * - No pipeline or transport imports (entities are in core:persistence)
 * - EPG data fetched from ObxEpgNowNext table
 * - Categories fetched from ObxCategory table with cached lookup
 *
 * **Why direct entity mapping?**
 * RawMediaMetadata is a normalized, source-agnostic model without provider-specific
 * fields like categoryId, channelNumber, or EPG data. The adapter needs these fields
 * for the LiveChannel domain model, so it reads directly from ObjectBox entities.
 *
 * **Note on Favorites/Recent:** Currently favorites and recent channels are stored
 * in-memory. Future: Should be persisted in dedicated ObjectBox entities.
 */
@Singleton
class LiveContentRepositoryAdapter
    @Inject
    constructor(
        private val boxStore: BoxStore,
    ) : LiveContentRepository {
        companion object {
            private const val TAG = "LiveContentRepoAdapter"
        }

        private val liveBox by lazy { boxStore.boxFor<ObxLive>() }
        private val categoryBox by lazy { boxStore.boxFor<ObxCategory>() }
        private val epgBox by lazy { boxStore.boxFor<ObxEpgNowNext>() }

        // Category name cache to avoid repeated DB lookups
        private val categoryNameCache = mutableMapOf<String, String?>()

        // In-memory storage for favorites and recent (should be persisted later)
        private val favoriteIds = mutableSetOf<String>()
        private val recentChannelIds = mutableListOf<String>()
        private val recentChannelCache = mutableMapOf<String, LiveChannel>()

        override fun observeChannels(categoryId: String?): Flow<List<LiveChannel>> {
            val query =
                if (categoryId != null) {
                    liveBox.query(ObxLive_.categoryId.equal(categoryId)).order(ObxLive_.nameLower).build()
                } else {
                    liveBox.query().order(ObxLive_.nameLower).build()
                }
            return query
                .asFlow()
                .map { entities -> entities.map { it.toLiveChannel() } }
                .flowOn(Dispatchers.IO)
        }

        override fun observeCategories(): Flow<List<LiveCategory>> =
            categoryBox
                .query(ObxCategory_.kind.equal("live"))
                .build()
                .asFlow()
                .map { categories ->
                    categories
                        .map { category ->
                            LiveCategory(
                                id = category.categoryId,
                                name = category.categoryName ?: category.categoryId,
                                channelCount =
                                    liveBox
                                        .query(ObxLive_.categoryId.equal(category.categoryId))
                                        .build()
                                        .count()
                                        .toInt(),
                            )
                        }.sortedBy { it.name }
                }.flowOn(Dispatchers.IO)

        override suspend fun search(
            query: String,
            limit: Int,
        ): List<LiveChannel> =
            withContext(Dispatchers.IO) {
                try {
                    val lowerQuery = query.lowercase()
                    liveBox
                        .query(ObxLive_.nameLower.contains(lowerQuery))
                        .build()
                        .find(0, limit.toLong())
                        .map { it.toLiveChannel() }
                } catch (e: Exception) {
                    UnifiedLog.e(TAG) { "Search error: ${e.message}" }
                    emptyList()
                }
            }

        override suspend fun getRecentChannels(limit: Int): List<LiveChannel> =
            recentChannelIds.take(limit).mapNotNull { recentChannelCache[it] }

        override fun observeFavorites(): Flow<List<LiveChannel>> =
            liveBox
                .query()
                .order(ObxLive_.nameLower)
                .build()
                .asFlow()
                .map { entities ->
                    entities
                        .filter { favoriteIds.contains("xtream:live:${it.streamId}") }
                        .map { it.toLiveChannel() }
                }.flowOn(Dispatchers.IO)

        override suspend fun setFavorite(
            channelId: String,
            isFavorite: Boolean,
        ) {
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
                withContext(Dispatchers.IO) {
                    val streamId = channelId.removePrefix("xtream:live:").toIntOrNull()
                    if (streamId != null) {
                        liveBox
                            .query(ObxLive_.streamId.equal(streamId.toLong()))
                            .build()
                            .findFirst()
                            ?.let { entity ->
                                recentChannelCache[channelId] = entity.toLiveChannel()
                            }
                    }
                }
            } catch (e: Exception) {
                UnifiedLog.e(TAG) { "Failed to cache recent channel: ${e.message}" }
            }
        }

        // ========================================================================
        // Mapping: ObxLive → LiveChannel
        // ========================================================================

        /**
         * Maps ObxLive entity to LiveChannel domain model.
         *
         * Direct entity mapping provides full access to:
         * - categoryId (for filtering)
         * - epgChannelId (for EPG lookup)
         * - All other entity fields
         *
         * EPG data (current program) is fetched from ObxEpgNowNext table.
         */
        private fun ObxLive.toLiveChannel(): LiveChannel {
            val sourceId = "xtream:live:$streamId"
            val epg = epgChannelId?.let { lookupEpg(streamId, it) }

            return LiveChannel(
                id = sourceId,
                name = name,
                channelNumber = streamId, // Use streamId as channel number
                logo = logo?.let { ImageRef.Http(it) },
                categoryId = categoryId,
                categoryName = categoryId?.let { lookupCategoryName(it) },
                currentProgram = epg?.nowTitle,
                currentProgramDescription = null, // ObxEpgNowNext doesn't store description
                programStart = epg?.nowStartMs,
                programEnd = epg?.nowEndMs,
                sourceType = SourceType.XTREAM,
                isFavorite = favoriteIds.contains(sourceId),
                lastWatched =
                    if (recentChannelIds.contains(sourceId)) {
                        System.currentTimeMillis()
                    } else {
                        null
                    },
            )
        }

        /**
         * Lookup EPG now/next data for a channel.
         */
        private fun lookupEpg(
            streamId: Int,
            epgChannelId: String,
        ): ObxEpgNowNext? {
            // Try by streamId first, fall back to epgChannelId
            return epgBox
                .query(ObxEpgNowNext_.streamId.equal(streamId.toLong()))
                .build()
                .findFirst()
                ?: epgBox
                    .query(ObxEpgNowNext_.channelId.equal(epgChannelId))
                    .build()
                    .findFirst()
        }

        /**
         * Lookup category name from ObxCategory table with caching.
         */
        private fun lookupCategoryName(categoryId: String): String? =
            categoryNameCache.getOrPut(categoryId) {
                categoryBox
                    .query(ObxCategory_.categoryId.equal(categoryId))
                    .build()
                    .findFirst()
                    ?.categoryName
            }
    }
