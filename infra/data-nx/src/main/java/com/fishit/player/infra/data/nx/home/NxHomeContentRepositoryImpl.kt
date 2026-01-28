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
import com.fishit.player.core.model.ContentDisplayLimits
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
        
        // ==================== Shared Limits from core/model ====================
        // Large catalog limits REMOVED - use Paging for 40K+ catalogs.
        // This small limit is only for deprecated Flow-based methods that still exist
        // for backward compatibility but should NOT be used in production.
        private const val DEPRECATED_FALLBACK_LIMIT = 50
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
            profileKey = ContentDisplayLimits.DEFAULT_PROFILE_KEY,
            limit = ContentDisplayLimits.CONTINUE_WATCHING,
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
        return workRepository.observeRecentlyCreated(limit = ContentDisplayLimits.RECENTLY_ADDED)
            .mapLatest { works ->
                batchMapToHomeMediaItems(
                    works = works.filter { it.type != WorkType.EPISODE },
                    isNew = { work -> 
                        val now = System.currentTimeMillis()
                        (now - work.createdAtMs) < ContentDisplayLimits.NEW_BADGE_WINDOW_MS
                    }
                )
            }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe recently added" }
                emit(emptyList())
            }
    }

    // ==================== DEPRECATED Methods (use Paging instead) ====================
    // These methods are kept for backward compatibility but should NOT be used.
    // For large catalogs (40K+ items), use getMoviesPagingData() etc.

    @Suppress("DEPRECATION")
    @OptIn(ExperimentalCoroutinesApi::class)
    @Deprecated("Use getMoviesPagingData() instead")
    override fun observeMovies(): Flow<List<HomeMediaItem>> {
        UnifiedLog.i(TAG) { "observeMovies() CALLED - THIS SHOULD APPEAR IN LOGCAT!" }
        UnifiedLog.w(TAG, "observeMovies() is deprecated - use getMoviesPagingData() for large catalogs")
        return workRepository.observeByType(WorkType.MOVIE, limit = DEPRECATED_FALLBACK_LIMIT)
            .mapLatest { works -> batchMapToHomeMediaItems(works) }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe movies" }
                emit(emptyList())
            }
    }

    @Suppress("DEPRECATION")
    @OptIn(ExperimentalCoroutinesApi::class)
    @Deprecated("Use getSeriesPagingData() instead")
    override fun observeSeries(): Flow<List<HomeMediaItem>> {
        UnifiedLog.i(TAG) { "observeSeries() CALLED - THIS SHOULD APPEAR IN LOGCAT!" }
        UnifiedLog.w(TAG, "observeSeries() is deprecated - use getSeriesPagingData() for large catalogs")
        return workRepository.observeByType(WorkType.SERIES, limit = DEPRECATED_FALLBACK_LIMIT)
            .mapLatest { works -> 
                // Use cached lookup to avoid repeated DB queries on every emission
                val now = System.currentTimeMillis()
                val seriesWithNewEpisodes = if (now - newEpisodesCacheTimestamp > ContentDisplayLimits.NEW_EPISODES_CACHE_MS) {
                    // Cache expired, refresh
                    val newEpisodesCheckTimestamp = now - ContentDisplayLimits.NEW_EPISODES_WINDOW_MS
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

    @Suppress("DEPRECATION")
    @OptIn(ExperimentalCoroutinesApi::class)
    @Deprecated("Use getClipsPagingData() instead")
    override fun observeClips(): Flow<List<HomeMediaItem>> {
        UnifiedLog.w(TAG, "observeClips() is deprecated - use getClipsPagingData() for large catalogs")
        return workRepository.observeByType(WorkType.CLIP, limit = DEPRECATED_FALLBACK_LIMIT)
            .mapLatest { works -> batchMapToHomeMediaItems(works) }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe clips" }
                emit(emptyList())
            }
    }

    @Suppress("DEPRECATION")
    @OptIn(ExperimentalCoroutinesApi::class)
    @Deprecated("Use getLivePagingData() instead")
    override fun observeXtreamLive(): Flow<List<HomeMediaItem>> {
        UnifiedLog.w(TAG, "observeXtreamLive() is deprecated - use getLivePagingData() for large catalogs")
        return workRepository.observeByType(WorkType.LIVE_CHANNEL, limit = DEPRECATED_FALLBACK_LIMIT)
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
    
    private val homePagingConfig = PagingConfig(
        pageSize = ContentDisplayLimits.HomePaging.PAGE_SIZE,
        initialLoadSize = ContentDisplayLimits.HomePaging.INITIAL_LOAD_SIZE,
        prefetchDistance = ContentDisplayLimits.HomePaging.PREFETCH_DISTANCE,
        enablePlaceholders = false,
    )
    
    override fun getMoviesPagingData(): Flow<PagingData<HomeMediaItem>> {
        UnifiedLog.i(TAG) { "🎬 getMoviesPagingData() CALLED - Creating Movies PagingSource" }
        return Pager(
            config = homePagingConfig,
            pagingSourceFactory = {
                UnifiedLog.d(TAG) { "🎬 Movies PagingSource FACTORY invoked" }
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
        UnifiedLog.i(TAG) { "📺 getSeriesPagingData() CALLED - Creating Series PagingSource" }
        return Pager(
            config = homePagingConfig,
            pagingSourceFactory = {
                UnifiedLog.d(TAG) { "📺 Series PagingSource FACTORY invoked" }
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
            config = homePagingConfig,
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
            config = homePagingConfig,
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
            config = homePagingConfig,
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
