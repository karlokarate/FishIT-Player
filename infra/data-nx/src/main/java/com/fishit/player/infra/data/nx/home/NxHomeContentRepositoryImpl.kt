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
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkRepository.Work
import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.model.repository.NxWorkUserStateRepository
import com.fishit.player.core.persistence.cache.HomeContentCache
import com.fishit.player.infra.data.nx.mapper.MediaTypeMapper
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NxHomeContentRepositoryImpl @Inject constructor(
    private val workRepository: NxWorkRepository,
    private val sourceRefRepository: NxWorkSourceRefRepository,
    private val userStateRepository: NxWorkUserStateRepository,
    private val homeContentCache: HomeContentCache,
) : HomeContentRepository {

    companion object {
        private const val TAG = "NxHomeContentRepo"
    }

    // Refresh trigger for paging data - emits when sync completes
    private val refreshTrigger = MutableSharedFlow<Unit>(replay = 1).apply {
        tryEmit(Unit) // Initial emission to start flow
    }

    // Coroutine scope for observing cache invalidations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Subscribe to cache invalidations and trigger paging refresh
        scope.launch {
            homeContentCache.observeInvalidations().collect {
                UnifiedLog.d(TAG) { "Cache invalidation detected: $it, triggering paging refresh" }
                refreshTrigger.emit(Unit)
            }
        }
    }

    // ==================== Primary Content Methods ====================

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

    // ==================== Paging Methods (Infinite Horizontal Scroll) ====================
    
    private val homePagingConfig = PagingConfig(
        pageSize = ContentDisplayLimits.HomePaging.PAGE_SIZE,
        initialLoadSize = ContentDisplayLimits.HomePaging.INITIAL_LOAD_SIZE,
        prefetchDistance = ContentDisplayLimits.HomePaging.PREFETCH_DISTANCE,
        enablePlaceholders = false,
    )
    
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getMoviesPagingData(): Flow<PagingData<HomeMediaItem>> {
        UnifiedLog.i(TAG) { "🎬 getMoviesPagingData() CALLED - Creating Movies PagingSource" }
        return refreshTrigger.flatMapLatest {
            Pager(
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
    }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getSeriesPagingData(): Flow<PagingData<HomeMediaItem>> {
        UnifiedLog.i(TAG) { "📺 getSeriesPagingData() CALLED - Creating Series PagingSource" }
        return refreshTrigger.flatMapLatest {
            Pager(
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
    }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getClipsPagingData(): Flow<PagingData<HomeMediaItem>> {
        return refreshTrigger.flatMapLatest {
            Pager(
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
    }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getLivePagingData(): Flow<PagingData<HomeMediaItem>> {
        return refreshTrigger.flatMapLatest {
            Pager(
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
    }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getRecentlyAddedPagingData(): Flow<PagingData<HomeMediaItem>> {
        return refreshTrigger.flatMapLatest {
            Pager(
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
            poster = poster,
            placeholderThumbnail = null,
            backdrop = backdrop,
            mediaType = MediaTypeMapper.toMediaType(type),
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

    // Note: mapWorkTypeToMediaType removed - use MediaTypeMapper.toMediaType() instead
    
    // ==================== Search ====================
    
    override suspend fun search(query: String, limit: Int): List<HomeMediaItem> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) return emptyList()
        
        return try {
            val works = workRepository.searchByTitle(normalizedQuery, limit.coerceAtMost(ContentDisplayLimits.SEARCH))
                .filter { it.type != WorkType.EPISODE } // Exclude episodes from search
            batchMapToHomeMediaItems(works)
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Search failed for query: $query" }
            emptyList()
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
    
    companion object {
        private const val TAG = "NxHomeContentRepo"
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, HomeMediaItem> {
        return try {
            val offset = params.key ?: 0
            val loadSize = params.loadSize
            
            UnifiedLog.d(TAG) { "🔍 HomePagingSource.load() START | workType=${workType?.name ?: "ALL"} offset=$offset loadSize=$loadSize" }

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
                    is LoadResult.Page -> {
                        UnifiedLog.d(TAG) { "🔍 HomePagingSource: DB returned ${result.data.size} works" }
                        result.data
                    }
                    else -> {
                        UnifiedLog.w(TAG) { "🔍 HomePagingSource: DB returned non-Page result: ${result::class.simpleName}" }
                        emptyList()
                    }
                }
            }.let { works ->
                if (excludeEpisodes) {
                    val filtered = works.filter { it.type != WorkType.EPISODE }
                    UnifiedLog.d(TAG) { "🔍 HomePagingSource: Filtered out episodes: ${works.size} → ${filtered.size}" }
                    filtered
                } else {
                    works
                }
            }
            
            UnifiedLog.d(TAG) { "🔍 HomePagingSource: Processing ${works.size} works for mapping" }

            // Batch load source refs for all works
            val workKeys = works.map { it.workKey }
            val sourceRefsMap = withContext(Dispatchers.IO) {
                sourceRefRepository.findByWorkKeysBatch(workKeys)
            }
            
            UnifiedLog.d(TAG) { "🔍 HomePagingSource: Loaded source refs for ${sourceRefsMap.size} works" }

            // Map to HomeMediaItem
            // Filter out items that fail mapping (e.g., corrupted data)
            val items = works.mapNotNull { work ->
                try {
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
                } catch (e: Exception) {
                    // Log and skip corrupted entries to prevent blocking pagination
                    UnifiedLog.w("HomePagingSource") {
                        "Failed to map work to HomeMediaItem: workKey=${work.workKey}, " +
                        "title=${work.displayTitle}, error=${e.message}"
                    }
                    null
                }
            }
            
            UnifiedLog.i(TAG) {
                "✅ HomePagingSource.load() RESULT | workType=${workType?.name ?: "ALL"} " +
                "offset=$offset count=${items.size} " +
                "hasNext=${items.size == loadSize} " +
                "titles=${items.take(3).joinToString { "\"${it.title}\"" }}"
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
            UnifiedLog.e(TAG, e) {
                "❌ HomePagingSource.load() ERROR | workType=${workType?.name ?: "ALL"} " +
                "offset=${params.key ?: 0} loadSize=${params.loadSize}"
            }
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
            poster = poster,
            placeholderThumbnail = null,
            backdrop = backdrop,
            mediaType = MediaTypeMapper.toMediaType(type),
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
    
    // Note: mapWorkTypeToMediaType removed - use MediaTypeMapper.toMediaType() instead
}
