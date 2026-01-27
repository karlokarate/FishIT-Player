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
 * 
 * **Paging Support:**
 * All major content rows (Movies, Series, Clips, Live, Recently Added) support
 * horizontal paging via `get*PagingData()` methods for infinite scroll.
 */
package com.fishit.player.infra.data.nx.home

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.map
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
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
        private const val CONTINUE_WATCHING_LIMIT = 30
        
        /**
         * Cache duration for "New Episodes" badge lookup.
         * Avoids repeated DB queries on every Flow emission.
         */
        private const val NEW_EPISODES_CACHE_MS = 60_000L // 1 minute
        private const val RECENTLY_ADDED_LIMIT = 100
        
        /**
         * Content limits for Home screen.
         * 
         * **Design rationale:**
         * - Using asFlowWithLimit() these are now DB-level limits (efficient!)
         * - Limits are high enough to show meaningful content
         * - TV rows typically show 5-7 items visible, user scrolls for more
         * - If user has 60K movies, showing 2000 is reasonable for home browsing
         */
        private const val MOVIES_LIMIT = 2000
        private const val SERIES_LIMIT = 2000
        private const val CLIPS_LIMIT = 500
        private const val LIVE_LIMIT = 500
        private const val SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L
        
        /**
         * Default profile key until ProfileManager is implemented in v2.
         * All user states (resume marks, favorites, etc.) use this key.
         */
        private const val DEFAULT_PROFILE_KEY = "default"
        
        /**
         * Time window for "New Episodes" badge.
         * Episodes with sourceLastModifiedMs within this window are considered "new".
         * TODO: Replace with user preference for "last series check" timestamp.
         */
        private const val NEW_EPISODES_WINDOW_MS = 48 * 60 * 60 * 1000L // 48 hours
        
        /**
         * Paging configuration for horizontal rows.
         * 
         * **Design rationale:**
         * - PAGE_SIZE = 20: One "screenful" of tiles (TV shows ~5-7, phone ~3-4)
         * - INITIAL_LOAD = 40: Load 2 pages initially for smooth scroll start
         * - PREFETCH = 10: Start loading next page when 10 items from end
         */
        private const val HOME_PAGE_SIZE = 20
        private const val HOME_INITIAL_LOAD_SIZE = 40
        private const val HOME_PREFETCH_DISTANCE = 10
    }
    
    // Cache for new episodes badge (avoids repeated DB queries)
    // @Volatile ensures visibility across threads since this is a Singleton with Flow emissions
    @Volatile
    private var cachedNewEpisodesWorkKeys: Set<String> = emptySet()
    @Volatile
    private var newEpisodesCacheTimestamp: Long = 0L

    // ==================== Primary Content Methods ==

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
            .mapLatest { works -> 
                // Use cached lookup to avoid repeated DB queries on every emission
                val now = System.currentTimeMillis()
                val seriesWithNewEpisodes = if (now - newEpisodesCacheTimestamp > NEW_EPISODES_CACHE_MS) {
                    // Cache expired, refresh
                    val newEpisodesCheckTimestamp = now - NEW_EPISODES_WINDOW_MS
                    sourceRefRepository.findWorkKeysWithSeriesUpdates(
                        sinceMs = newEpisodesCheckTimestamp,
                        sourceType = null, // All sources
                    ).also {
                        cachedNewEpisodesWorkKeys = it
                        newEpisodesCacheTimestamp = now
                    }
                } else {
                    // Use cached value
                    cachedNewEpisodesWorkKeys
                }
                
                batchMapToHomeMediaItems(
                    works = works,
                    hasNewEpisodes = { work -> work.workKey in seriesWithNewEpisodes }
                )
            }
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
    
    // ==================== Paging Methods (Infinite Horizontal Scroll) ====================
    
    override fun getMoviesPagingData(): Flow<PagingData<HomeMediaItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = HOME_PAGE_SIZE,
                initialLoadSize = HOME_INITIAL_LOAD_SIZE,
                prefetchDistance = HOME_PREFETCH_DISTANCE,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                HomePagingSource(
                    workRepository = workRepository,
                    sourceRefRepository = sourceRefRepository,
                    workType = WorkType.MOVIE,
                    sortField = NxWorkRepository.SortField.TITLE,
                )
            }
        ).flow
    }
    
    override fun getSeriesPagingData(): Flow<PagingData<HomeMediaItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = HOME_PAGE_SIZE,
                initialLoadSize = HOME_INITIAL_LOAD_SIZE,
                prefetchDistance = HOME_PREFETCH_DISTANCE,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                HomePagingSource(
                    workRepository = workRepository,
                    sourceRefRepository = sourceRefRepository,
                    workType = WorkType.SERIES,
                    sortField = NxWorkRepository.SortField.TITLE,
                )
            }
        ).flow
    }
    
    override fun getClipsPagingData(): Flow<PagingData<HomeMediaItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = HOME_PAGE_SIZE,
                initialLoadSize = HOME_INITIAL_LOAD_SIZE,
                prefetchDistance = HOME_PREFETCH_DISTANCE,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                HomePagingSource(
                    workRepository = workRepository,
                    sourceRefRepository = sourceRefRepository,
                    workType = WorkType.CLIP,
                    sortField = NxWorkRepository.SortField.TITLE,
                )
            }
        ).flow
    }
    
    override fun getLivePagingData(): Flow<PagingData<HomeMediaItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = HOME_PAGE_SIZE,
                initialLoadSize = HOME_INITIAL_LOAD_SIZE,
                prefetchDistance = HOME_PREFETCH_DISTANCE,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                HomePagingSource(
                    workRepository = workRepository,
                    sourceRefRepository = sourceRefRepository,
                    workType = WorkType.LIVE_CHANNEL,
                    sortField = NxWorkRepository.SortField.TITLE,
                )
            }
        ).flow
    }
    
    override fun getRecentlyAddedPagingData(): Flow<PagingData<HomeMediaItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = HOME_PAGE_SIZE,
                initialLoadSize = HOME_INITIAL_LOAD_SIZE,
                prefetchDistance = HOME_PREFETCH_DISTANCE,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                HomePagingSource(
                    workRepository = workRepository,
                    sourceRefRepository = sourceRefRepository,
                    workType = null, // All types
                    sortField = NxWorkRepository.SortField.RECENTLY_ADDED,
                    excludeEpisodes = true,
                )
            }
        ).flow
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
     * @param isNew Function to determine if a work is "new" (recently added)
     * @param hasNewEpisodes Function to determine if a series has new episodes
     * @return List of HomeMediaItems
     */
    private suspend fun batchMapToHomeMediaItems(
        works: List<Work>,
        isNew: (Work) -> Boolean = { false },
        hasNewEpisodes: (Work) -> Boolean = { false },
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
                hasNewEpisodes = hasNewEpisodes(work),
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
        hasNewEpisodes: Boolean = false,
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
            hasNewEpisodes = hasNewEpisodes,
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

/**
 * PagingSource for Home screen horizontal rows.
 * 
 * Loads HomeMediaItem pages from NxWorkRepository with proper source type mapping.
 * Uses offset-based pagination for stable results across pages.
 */
private class HomePagingSource(
    private val workRepository: NxWorkRepository,
    private val sourceRefRepository: NxWorkSourceRefRepository,
    private val workType: WorkType?,
    private val sortField: NxWorkRepository.SortField,
    private val excludeEpisodes: Boolean = false,
) : PagingSource<Int, HomeMediaItem>() {
    
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, HomeMediaItem> {
        return try {
            val offset = params.key ?: 0
            val loadSize = params.loadSize
            
            // Build query options
            val options = NxWorkRepository.QueryOptions(
                type = workType,
                sortField = sortField,
                sortDirection = if (sortField == NxWorkRepository.SortField.RECENTLY_ADDED) {
                    NxWorkRepository.SortDirection.DESCENDING
                } else {
                    NxWorkRepository.SortDirection.ASCENDING
                },
                limit = loadSize,
            )
            
            // Get works using pagingSourceFactory
            val works = withContext(Dispatchers.IO) {
                val pagingSource = workRepository.pagingSourceFactory(options)()
                val result = pagingSource.load(
                    LoadParams.Refresh(
                        key = offset,
                        loadSize = loadSize,
                        placeholdersEnabled = false,
                    )
                )
                when (result) {
                    is LoadResult.Page -> result.data
                    else -> emptyList()
                }
            }.let { works ->
                if (excludeEpisodes) {
                    works.filter { it.type != WorkType.EPISODE }
                } else {
                    works
                }
            }
            
            // Batch load source refs for all works
            val workKeys = works.map { it.workKey }
            val sourceRefsMap = withContext(Dispatchers.IO) {
                sourceRefRepository.findByWorkKeysBatch(workKeys)
            }
            
            // Map to HomeMediaItem
            val items = works.map { work ->
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
                
                work.toHomeMediaItem(
                    sourceType = primarySourceType,
                    allSourceTypes = allSourceTypes.ifEmpty { listOf(primarySourceType) },
                )
            }
            
            val prevKey = if (offset > 0) maxOf(0, offset - loadSize) else null
            val nextKey = if (items.size == loadSize) offset + loadSize else null
            
            LoadResult.Page(
                data = items,
                prevKey = prevKey,
                nextKey = nextKey,
                itemsBefore = offset,
                itemsAfter = LoadResult.Page.COUNT_UNDEFINED,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
    
    override fun getRefreshKey(state: PagingState<Int, HomeMediaItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val closestPage = state.closestPageToPosition(anchorPosition)
            closestPage?.prevKey?.let { it + state.config.pageSize }
                ?: closestPage?.nextKey?.let { it - state.config.pageSize }
        }
    }
    
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
    
    private fun NxWorkRepository.Work.toHomeMediaItem(
        sourceType: SourceType,
        allSourceTypes: List<SourceType>,
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
            resumePosition = 0L,
            duration = runtimeMs ?: 0L,
            isNew = false,
            hasNewEpisodes = false,
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
