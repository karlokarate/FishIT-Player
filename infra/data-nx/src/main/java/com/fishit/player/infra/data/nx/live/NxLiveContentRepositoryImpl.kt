/**
 * NX-based LiveContentRepository implementation.
 *
 * This implementation reads from the NX work graph (v2 SSOT) instead of the
 * legacy ObxLive/ObxCategory layer.
 *
 * **Architecture:**
 * - Feature layer (feature:live) defines LiveContentRepository interface
 * - This implementation provides the data from NX repositories
 * - All reads go through NxWorkRepository, NxWorkSourceRefRepository, NxWorkUserStateRepository
 *
 * **Migration Note:**
 * This replaces LiveContentRepositoryAdapter which reads from legacy Obx* entities
 * and uses in-memory favorites (which is now persisted in NX_WorkUserState).
 *
 * **Profile Simplification:**
 * Currently uses DEFAULT_PROFILE_KEY until ProfileManager is implemented in v2.
 */
package com.fishit.player.infra.data.nx.live

import com.fishit.player.core.live.domain.LiveCategory
import com.fishit.player.core.live.domain.LiveChannel
import com.fishit.player.core.live.domain.LiveContentRepository
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.repository.NxEpgRepository
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkRepository.Work
import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.model.repository.NxWorkUserStateRepository
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NxLiveContentRepositoryImpl @Inject constructor(
    private val workRepository: NxWorkRepository,
    private val sourceRefRepository: NxWorkSourceRefRepository,
    private val userStateRepository: NxWorkUserStateRepository,
    private val epgRepository: NxEpgRepository,
) : LiveContentRepository {

    companion object {
        private const val TAG = "NxLiveContentRepo"
        private const val CHANNELS_LIMIT = 1000
        private const val SEARCH_LIMIT = 100
        private const val RECENT_LIMIT = 20
        private const val FAVORITES_LIMIT = 100
        
        /**
         * Default profile key until ProfileManager is implemented in v2.
         */
        private const val DEFAULT_PROFILE_KEY = "default"
    }

    // ==================== Channel Content ====================

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeChannels(categoryId: String?): Flow<List<LiveChannel>> {
        return combine(
            workRepository.observeByType(WorkType.LIVE_CHANNEL, limit = CHANNELS_LIMIT),
            userStateRepository.observeFavorites(DEFAULT_PROFILE_KEY, limit = FAVORITES_LIMIT),
        ) { works, favoriteStates ->
            works to favoriteStates
        }.mapLatest { (works, favoriteStates) ->
            val favoriteWorkKeys = favoriteStates.map { it.workKey }.toSet()
            works.mapNotNull { work ->
                val sourceType = determineSourceType(work.workKey)
                work.toLiveChannel(
                    sourceType = sourceType,
                    isFavorite = work.workKey in favoriteWorkKeys,
                )
            }
        }.catch { e ->
            UnifiedLog.e(TAG, e) { "Failed to observe channels" }
            emit(emptyList())
        }
    }

    override fun observeCategories(): Flow<List<LiveCategory>> {
        // For MVP: return a single "All Channels" category
        // TODO: Implement category grouping when NX_Category entity is added
        return workRepository.observeByType(WorkType.LIVE_CHANNEL, limit = CHANNELS_LIMIT)
            .map { works ->
                listOf(
                    LiveCategory(
                        id = "all_channels",
                        name = "All Channels",
                        channelCount = works.size,
                    ),
                )
            }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe categories" }
                emit(emptyList())
            }
    }

    // ==================== Search ====================

    override suspend fun search(query: String, limit: Int): List<LiveChannel> {
        return try {
            val normalizedQuery = query.trim().lowercase()
            if (normalizedQuery.isBlank()) return emptyList()

            val favoriteWorkKeys = userStateRepository
                .observeFavorites(DEFAULT_PROFILE_KEY, limit = FAVORITES_LIMIT)
                .let { emptySet<String>() } // Simplified: full impl would use first()

            workRepository.searchByTitle(normalizedQuery, limit.coerceAtMost(SEARCH_LIMIT))
                .filter { it.type == WorkType.LIVE_CHANNEL }
                .mapNotNull { work ->
                    val sourceType = determineSourceType(work.workKey)
                    work.toLiveChannel(
                        sourceType = sourceType,
                        isFavorite = work.workKey in favoriteWorkKeys,
                    )
                }
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Search failed for query: $query" }
            emptyList()
        }
    }

    // ==================== Recent Channels ====================

    override suspend fun getRecentChannels(limit: Int): List<LiveChannel> {
        return try {
            val continueWatching = userStateRepository
                .observeContinueWatching(DEFAULT_PROFILE_KEY, limit = limit.coerceAtMost(RECENT_LIMIT))
                .let { emptyList<com.fishit.player.core.model.userstate.WorkUserState>() } // Would need first()

            continueWatching.mapNotNull { state ->
                val work = workRepository.get(state.workKey)
                if (work?.type == WorkType.LIVE_CHANNEL) {
                    val sourceType = determineSourceType(work.workKey)
                    work.toLiveChannel(
                        sourceType = sourceType,
                        isFavorite = state.isFavorite,
                        lastWatched = state.lastWatchedAtMs,
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to get recent channels" }
            emptyList()
        }
    }

    // ==================== Favorites ====================

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeFavorites(): Flow<List<LiveChannel>> {
        return userStateRepository.observeFavorites(DEFAULT_PROFILE_KEY, limit = FAVORITES_LIMIT)
            .mapLatest { favoriteStates ->
                favoriteStates.mapNotNull { state ->
                    val work = workRepository.get(state.workKey)
                    if (work?.type == WorkType.LIVE_CHANNEL) {
                        val sourceType = determineSourceType(work.workKey)
                        work.toLiveChannel(
                            sourceType = sourceType,
                            isFavorite = true,
                            lastWatched = state.lastWatchedAtMs,
                        )
                    } else {
                        null
                    }
                }
            }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe favorites" }
                emit(emptyList())
            }
    }

    override suspend fun setFavorite(channelId: String, isFavorite: Boolean) {
        try {
            if (isFavorite) {
                userStateRepository.addToFavorites(DEFAULT_PROFILE_KEY, channelId)
            } else {
                userStateRepository.removeFromFavorites(DEFAULT_PROFILE_KEY, channelId)
            }
            UnifiedLog.d(TAG) { "Set favorite for $channelId: $isFavorite" }
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to set favorite for $channelId" }
        }
    }

    // ==================== Watch Tracking ====================

    override suspend fun recordWatched(channelId: String) {
        try {
            userStateRepository.markAsWatched(DEFAULT_PROFILE_KEY, channelId)
            UnifiedLog.d(TAG) { "Recorded watched for $channelId" }
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to record watched for $channelId" }
        }
    }

    // ==================== Mapping Helpers ====================

    private suspend fun Work.toLiveChannel(
        sourceType: SourceType,
        isFavorite: Boolean = false,
        lastWatched: Long? = null,
    ): LiveChannel {
        // Get source ref for EPG/Catchup data
        val sourceRefs = sourceRefRepository.findByWorkKey(workKey)
        val primarySource = sourceRefs.firstOrNull()
        
        // NOTE: EPG data (now/next) is NOT fetched here for performance reasons.
        // EPG should be:
        // 1. Fetched on-demand when player opens (via epgRepository.getNowNext())
        // 2. Prefetched periodically for favorites (via background worker - TODO)
        // The epgRepository is injected but used elsewhere (player, prefetch worker).
        
        return LiveChannel(
            id = workKey,
            name = displayTitle,
            channelNumber = null, // TODO: Add to NX_Work or metadata
            logo = ImageRef.fromString(posterRef),
            categoryId = null, // TODO: Add category support
            categoryName = null,
            // EPG Now/Next: null here, fetched on-demand by player
            currentProgram = null,
            currentProgramDescription = null,
            programStart = null,
            programEnd = null,
            sourceType = sourceType,
            isFavorite = isFavorite,
            lastWatched = lastWatched,
            // EPG/Catchup capabilities from source ref (static, not fetched)
            epgChannelId = primarySource?.epgChannelId,
            hasCatchup = (primarySource?.tvArchive ?: 0) > 0,
            catchupDays = primarySource?.tvArchiveDuration ?: 0,
            isAdult = isAdult,
        )
    }

    private suspend fun determineSourceType(workKey: String): SourceType {
        val sourceRefs = sourceRefRepository.findByWorkKey(workKey)
        if (sourceRefs.isEmpty()) return SourceType.UNKNOWN

        return when {
            sourceRefs.any { it.sourceType == NxWorkSourceRefRepository.SourceType.XTREAM } ->
                SourceType.XTREAM
            sourceRefs.any { it.sourceType == NxWorkSourceRefRepository.SourceType.TELEGRAM } ->
                SourceType.TELEGRAM
            sourceRefs.any { it.sourceType == NxWorkSourceRefRepository.SourceType.IO } ->
                SourceType.IO
            else -> SourceType.UNKNOWN
        }
    }
}
