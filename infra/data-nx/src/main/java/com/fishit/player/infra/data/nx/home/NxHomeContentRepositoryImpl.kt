/**
 * NX-based HomeContentRepository implementation.
 *
 * This implementation reads from the NX work graph (v2 SSOT) instead of the
 * legacy ObxCanonicalMedia layer.
 *
 * **Architecture:**
 * - Feature layer (feature:home) defines HomeContentRepository interface
 * - This implementation provides the data from NX repositories
 * - All reads go through NxWorkRepository, NxWorkUserStateRepository
 *
 * **Migration Note:**
 * This replaces HomeContentRepositoryAdapter which reads from legacy Obx* entities.
 * The old implementation is kept for comparison until NX data is fully populated.
 *
 * **Profile Simplification:**
 * Currently uses DEFAULT_PROFILE_KEY until ProfileManager is implemented in v2.
 * The NX repositories are profile-aware, but we use a single profile for now.
 */
package com.fishit.player.infra.data.nx.home

import com.fishit.player.core.home.domain.HomeContentRepository
import com.fishit.player.core.home.domain.HomeMediaItem
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkRepository.Work
import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.model.repository.NxWorkUserStateRepository
import com.fishit.player.core.model.userstate.WorkUserState
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
class NxHomeContentRepositoryImpl @Inject constructor(
    private val workRepository: NxWorkRepository,
    private val sourceRefRepository: NxWorkSourceRefRepository,
    private val userStateRepository: NxWorkUserStateRepository,
) : HomeContentRepository {

    companion object {
        private const val TAG = "NxHomeContentRepo"
        private const val CONTINUE_WATCHING_LIMIT = 20
        private const val RECENTLY_ADDED_LIMIT = 50
        private const val MOVIES_LIMIT = 200
        private const val SERIES_LIMIT = 200
        private const val CLIPS_LIMIT = 100
        private const val LIVE_LIMIT = 100
        private const val SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L
        
        /**
         * Default profile key until ProfileManager is implemented in v2.
         * All user states (resume marks, favorites, etc.) use this key.
         */
        private const val DEFAULT_PROFILE_KEY = "default"
    }

    // ==================== Primary Content Methods ====================

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeContinueWatching(): Flow<List<HomeMediaItem>> {
        return userStateRepository.observeContinueWatching(
            profileKey = DEFAULT_PROFILE_KEY,
            limit = CONTINUE_WATCHING_LIMIT,
        ).mapLatest { userStates ->
            if (userStates.isEmpty()) return@mapLatest emptyList()
            
            // Batch load works
            val workKeys = userStates.map { it.workKey }
            val worksMap = workKeys.mapNotNull { workRepository.get(it) }.associateBy { it.workKey }
            
            // Batch load source refs for all works
            val sourceRefsMap = sourceRefRepository.findByWorkKeysBatch(workKeys)
            
            userStates.mapNotNull { state ->
                val work = worksMap[state.workKey] ?: return@mapNotNull null
                val refs = sourceRefsMap[state.workKey] ?: emptyList()
                val allSourceTypes = refs.mapNotNull { ref ->
                    when (ref.sourceType) {
                        NxWorkSourceRefRepository.SourceType.XTREAM -> SourceType.XTREAM
                        NxWorkSourceRefRepository.SourceType.TELEGRAM -> SourceType.TELEGRAM
                        NxWorkSourceRefRepository.SourceType.IO -> SourceType.IO
                        else -> null
                    }
                }.distinct()
                val sourceType = determineSourceTypeFromRefs(refs)
                
                work.toHomeMediaItemFast(
                    sourceType = sourceType,
                    allSourceTypes = allSourceTypes.ifEmpty { listOf(sourceType) },
                    resumePosition = state.resumePositionMs,
                    duration = state.totalDurationMs.takeIf { it > 0 } ?: work.runtimeMs ?: 0L,
                )
            }
        }.catch { e ->
            UnifiedLog.e(TAG, e) { "Failed to observe continue watching" }
            emit(emptyList())
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRecentlyAdded(): Flow<List<HomeMediaItem>> {
        // Use createdAt sort for "Recently Added" - shows newly ingested content
        return workRepository.observeRecentlyCreated(limit = RECENTLY_ADDED_LIMIT)
            .mapLatest { works ->
                batchMapToHomeMediaItems(
                    works = works.filter { it.type != WorkType.EPISODE },
                    isNew = { work -> 
                        val now = System.currentTimeMillis()
                        (now - work.createdAtMs) < SEVEN_DAYS_MS
                    }
                )
            }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe recently added" }
                emit(emptyList())
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMovies(): Flow<List<HomeMediaItem>> {
        return workRepository.observeByType(WorkType.MOVIE, limit = MOVIES_LIMIT)
            .mapLatest { works -> batchMapToHomeMediaItems(works) }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe movies" }
                emit(emptyList())
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSeries(): Flow<List<HomeMediaItem>> {
        return workRepository.observeByType(WorkType.SERIES, limit = SERIES_LIMIT)
            .mapLatest { works -> batchMapToHomeMediaItems(works) }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe series" }
                emit(emptyList())
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeClips(): Flow<List<HomeMediaItem>> {
        return workRepository.observeByType(WorkType.CLIP, limit = CLIPS_LIMIT)
            .mapLatest { works -> batchMapToHomeMediaItems(works) }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe clips" }
                emit(emptyList())
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeXtreamLive(): Flow<List<HomeMediaItem>> {
        return workRepository.observeByType(WorkType.LIVE_CHANNEL, limit = LIVE_LIMIT)
            .mapLatest { works ->
                // Live channels default to Xtream source
                works.map { work ->
                    work.toHomeMediaItemFast(
                        sourceType = SourceType.XTREAM,
                        allSourceTypes = listOf(SourceType.XTREAM),
                    )
                }
            }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe Xtream live" }
                emit(emptyList())
            }
    }

    // ==================== Legacy Methods (backward compatibility) ====================

    override fun observeTelegramMedia(): Flow<List<HomeMediaItem>> {
        // Combine movies, series, clips - filtered for Telegram source
        return combine(
            observeMovies(),
            observeSeries(),
            observeClips(),
        ) { movies, series, clips ->
            (movies + series + clips)
                .filter { it.sourceType == SourceType.TELEGRAM }
                .take(200)
        }
    }

    override fun observeXtreamVod(): Flow<List<HomeMediaItem>> {
        return observeMovies().map { movies ->
            movies.filter { it.sourceType == SourceType.XTREAM }
        }
    }

    override fun observeXtreamSeries(): Flow<List<HomeMediaItem>> {
        return observeSeries().map { series ->
            series.filter { it.sourceType == SourceType.XTREAM }
        }
    }

    // ==================== Mapping Helpers ====================

    /**
     * Batch map works to HomeMediaItems with a single batch source lookup.
     *
     * **Performance:** Reduces N+1 queries to 2 queries total:
     * 1. One batch query for all source refs
     * 2. In-memory mapping
     *
     * @param works List of works to map
     * @param isNew Function to determine if a work is "new"
     * @return List of HomeMediaItems
     */
    private suspend fun batchMapToHomeMediaItems(
        works: List<Work>,
        isNew: (Work) -> Boolean = { false },
    ): List<HomeMediaItem> {
        if (works.isEmpty()) return emptyList()

        // Single batch lookup for all source refs
        val workKeys = works.map { it.workKey }
        val sourceRefsMap = sourceRefRepository.findByWorkKeysBatch(workKeys)

        return works.map { work ->
            val refs = sourceRefsMap[work.workKey] ?: emptyList()
            val allSourceTypes = refs.mapNotNull { ref ->
                when (ref.sourceType) {
                    NxWorkSourceRefRepository.SourceType.XTREAM -> SourceType.XTREAM
                    NxWorkSourceRefRepository.SourceType.TELEGRAM -> SourceType.TELEGRAM
                    NxWorkSourceRefRepository.SourceType.IO -> SourceType.IO
                    else -> null
                }
            }.distinct()
            val primarySourceType = determineSourceTypeFromRefs(refs)

            work.toHomeMediaItemFast(
                sourceType = primarySourceType,
                allSourceTypes = allSourceTypes.ifEmpty { listOf(primarySourceType) },
                isNew = isNew(work),
            )
        }
    }

    /**
     * Batch determine source types for multiple work keys.
     * Returns a map of workKey → primary SourceType.
     */
    private suspend fun batchDetermineSourceTypes(workKeys: List<String>): Map<String, SourceType> {
        if (workKeys.isEmpty()) return emptyMap()

        val sourceRefsMap = sourceRefRepository.findByWorkKeysBatch(workKeys)

        return workKeys.associateWith { workKey ->
            val refs = sourceRefsMap[workKey] ?: emptyList()
            determineSourceTypeFromRefs(refs)
        }
    }

    /**
     * Determine source type from pre-loaded source refs (no DB query).
     */
    private fun determineSourceTypeFromRefs(refs: List<NxWorkSourceRefRepository.SourceRef>): SourceType {
        if (refs.isEmpty()) return SourceType.UNKNOWN

        return when {
            refs.any { it.sourceType == NxWorkSourceRefRepository.SourceType.XTREAM } ->
                SourceType.XTREAM
            refs.any { it.sourceType == NxWorkSourceRefRepository.SourceType.TELEGRAM } ->
                SourceType.TELEGRAM
            refs.any { it.sourceType == NxWorkSourceRefRepository.SourceType.IO } ->
                SourceType.IO
            else -> SourceType.UNKNOWN
        }
    }

    /**
     * Fast mapping without additional DB queries.
     * Uses pre-loaded source type information.
     */
    private fun Work.toHomeMediaItemFast(
        sourceType: SourceType,
        allSourceTypes: List<SourceType>,
        resumePosition: Long = 0L,
        duration: Long = runtimeMs ?: 0L,
        isNew: Boolean = false,
    ): HomeMediaItem {
        return HomeMediaItem(
            id = workKey,
            title = displayTitle,
            poster = ImageRef.fromString(posterRef),
            placeholderThumbnail = null,
            backdrop = ImageRef.fromString(backdropRef),
            mediaType = mapWorkTypeToMediaType(type),
            sourceType = sourceType,
            sourceTypes = allSourceTypes,
            resumePosition = resumePosition,
            duration = duration,
            isNew = isNew,
            year = year,
            rating = rating?.toFloat(),
            genres = genres,
            navigationId = workKey,
            navigationSource = sourceType,
        )
    }

    private fun mapWorkTypeToMediaType(type: WorkType): MediaType {
        return when (type) {
            WorkType.MOVIE -> MediaType.MOVIE
            WorkType.SERIES -> MediaType.SERIES
            WorkType.EPISODE -> MediaType.SERIES_EPISODE
            WorkType.LIVE_CHANNEL -> MediaType.LIVE
            WorkType.CLIP -> MediaType.CLIP
            WorkType.AUDIOBOOK -> MediaType.AUDIOBOOK
            WorkType.MUSIC_TRACK -> MediaType.MUSIC
            WorkType.UNKNOWN -> MediaType.UNKNOWN
        }
    }
}
