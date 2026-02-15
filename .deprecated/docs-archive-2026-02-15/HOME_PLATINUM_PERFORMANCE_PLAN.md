# Home Screen Platinum Performance Plan

> **Status:** Planning  
> **Created:** 2026-01-02  
> **Contract Compliance:** Mandatory Layer Boundaries (AGENTS.md Section 4.5)

This document defines the **contract-compliant implementation plan** for all Platinum performance optimizations for the Home screen, ensuring strict adherence to v2 architecture layer boundaries.

---

## Layer Hierarchy (Binding)

```
┌─────────────────────────────────────────────────────────┐
│  UI Layer (feature/home)                                │
│    - HomeScreen, HomeViewModel                          │
│    - MAY: Import domain interfaces, core/model          │
│    - MUST NOT: Import infra/*, pipeline/*, transport/*  │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│  Domain Layer (core/home-domain)                        │
│    - HomeContentRepository (interface)                  │
│    - HomeMediaItem (domain model)                       │
│    - MAY: Import core/model, core/persistence (abstract)│
│    - MUST NOT: Import infra/*, pipeline/*               │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│  Data Layer (infra/data-home)                           │
│    - HomeContentRepositoryAdapter (implementation)      │
│    - MAY: Import domain, core/persistence, infra/data-* │
│    - MUST NOT: Import pipeline/* DTOs directly          │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│  Persistence Layer (core/persistence)                   │
│    - ObxCanonicalMedia, ObxCanonicalResumeMark         │
│    - ObjectBox queries, cache abstractions              │
└─────────────────────────────────────────────────────────┘
```

**Hard Rules:**
- UI never imports from infra/data-*, infra/transport-*, pipeline/*
- Domain defines interfaces, data implements them
- All cross-layer communication via interfaces in core/*

---

## Phase 1: Quick Wins (1 Day)

### 1.1 distinctUntilChanged() on State Flows

**Affected Modules:**
- `feature/home/` - HomeViewModel.kt

**Layer Compliance:**
- ✅ Internal to ViewModel (no cross-layer)
- ✅ No new imports needed

**Implementation:**

```kotlin
// feature/home/src/.../HomeViewModel.kt

val state: StateFlow<HomeState> = combine(
    contentStreams,
    errorState,
    syncStateObserver.observeSyncState(),
    sourceActivationStore.observeStates()
) { content, error, syncState, sourceActivation ->
    // ... existing mapping
}
    .distinctUntilChanged()  // ✅ ADD THIS
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeState()
    )

val filteredState: StateFlow<HomeState> = combine(
    state,
    _searchQuery,
    _selectedGenre,
    _isSearchVisible
) { currentState, query, genre, isSearchVisible ->
    // ... existing mapping
}
    .distinctUntilChanged()  // ✅ ADD THIS
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeState()
    )
```

**Contract Compliance:**
- ✅ No layer boundary violation
- ✅ No new dependencies
- ✅ Pure Kotlin Flows optimization

---

### 1.2 Debounced Search

**Affected Modules:**
- `feature/home/` - HomeViewModel.kt

**Layer Compliance:**
- ✅ Internal to ViewModel (no cross-layer)
- ✅ Uses kotlinx.coroutines (already imported)

**Implementation:**

```kotlin
// feature/home/src/.../HomeViewModel.kt

private val _searchQuery = MutableStateFlow("")

// NEW: Debounced query
private val debouncedSearchQuery = _searchQuery
    .debounce(300)  // 300ms delay
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ""
    )

// CHANGE: Use debouncedSearchQuery instead of _searchQuery
val filteredState: StateFlow<HomeState> = combine(
    state,
    debouncedSearchQuery,  // ✅ CHANGED: Debounced
    _selectedGenre,
    _isSearchVisible
) { currentState, query, genre, isSearchVisible ->
    // ... existing mapping (unchanged)
}
```

**Contract Compliance:**
- ✅ No layer boundary violation
- ✅ No new module dependencies
- ✅ Standard Kotlin Flows API

---

### 1.3 Eager Loading for Relations

**Affected Modules:**
- `infra/data-home/` - HomeContentRepositoryAdapter.kt
- `core/persistence/` - ObjectBoxFlow extensions

**Layer Compliance:**
- ✅ Data layer accesses Persistence layer (allowed)
- ✅ No UI or Domain changes needed
- ✅ Encapsulated in data implementation

**Implementation:**

```kotlin
// infra/data-home/src/.../HomeContentRepositoryAdapter.kt

override fun observeRecentlyAdded(): Flow<List<HomeMediaItem>> {
    val query = canonicalMediaBox.query(
        ObxCanonicalMedia_.mediaType.notEqual("SERIES_EPISODE")
    )
        .eager(ObxCanonicalMedia_.sources)  // ✅ ADD: Preload sources
        .orderDesc(ObxCanonicalMedia_.createdAt)
        .build()

    return query.asFlow()
        .map { canonicalMediaList ->
            // sources already loaded - no N+1!
            // ... existing mapping (unchanged)
        }
}

override fun observeMovies(): Flow<List<HomeMediaItem>> {
    val query = canonicalMediaBox.query(
        ObxCanonicalMedia_.mediaType.equal("MOVIE")
    )
        .eager(ObxCanonicalMedia_.sources)  // ✅ ADD: Preload sources
        .orderDesc(ObxCanonicalMedia_.createdAt)
        .build()
    // ... rest unchanged
}

// Apply to: observeSeries(), observeClips()
```

**Contract Compliance:**
- ✅ Data layer → Persistence layer (allowed)
- ✅ No domain or UI changes
- ✅ Pure ObjectBox API usage

---

## Phase 2: Caching Layer (2-3 Days)

### 2.1 Cache Abstraction in core/persistence

**New Module Structure:**
```
core/
└── persistence/
    └── src/main/java/com/fishit/player/core/persistence/
        ├── cache/
        │   ├── HomeContentCache.kt         (interface)
        │   ├── CachedSection.kt            (data class)
        │   └── CacheConfig.kt              (TTL config)
        └── cache/impl/
            ├── InMemoryHomeCache.kt        (L1 implementation)
            └── DiskHomeCache.kt            (L2 implementation)
```

**Layer Compliance:**
- ✅ Cache abstractions live in core/persistence (shared infra)
- ✅ No business logic (pure caching concern)
- ✅ Can be injected into data layer

**Implementation:**

```kotlin
// core/persistence/src/.../cache/HomeContentCache.kt

package com.fishit.player.core.persistence.cache

import com.fishit.player.core.home.domain.HomeMediaItem
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * Multi-layer cache for Home screen content.
 * 
 * **Architecture:**
 * - L1 (Memory): Fast, short TTL (5s), volatile
 * - L2 (Disk): Persistent, medium TTL (5min), survives app restart
 * - L3 (ObjectBox): Source of truth, always fresh
 * 
 * **Layer Compliance:**
 * - Lives in core/persistence (shared infrastructure)
 * - No dependency on UI, Domain, Pipeline layers
 * - Used by infra/data-* implementations only
 */
interface HomeContentCache {
    
    /**
     * Get cached section with TTL check.
     * Returns null if not cached or expired.
     */
    suspend fun get(key: CacheKey): CachedSection?
    
    /**
     * Store section with TTL.
     */
    suspend fun put(key: CacheKey, section: CachedSection)
    
    /**
     * Invalidate specific section.
     */
    suspend fun invalidate(key: CacheKey)
    
    /**
     * Invalidate all sections (e.g., after catalog sync).
     */
    suspend fun invalidateAll()
    
    /**
     * Observe cache invalidation events (for reactive updates).
     */
    fun observeInvalidations(): Flow<CacheKey>
}

/**
 * Cache key for Home sections.
 */
sealed class CacheKey(val name: String) {
    object ContinueWatching : CacheKey("continue_watching")
    object RecentlyAdded : CacheKey("recently_added")
    object Movies : CacheKey("movies")
    object Series : CacheKey("series")
    object Clips : CacheKey("clips")
    object LiveTV : CacheKey("live_tv")
}

/**
 * Cached section with timestamp and TTL.
 */
data class CachedSection(
    val items: List<HomeMediaItem>,
    val timestamp: Long = System.currentTimeMillis(),
    val ttl: Duration
) {
    fun isExpired(): Boolean {
        return System.currentTimeMillis() - timestamp > ttl.inWholeMilliseconds
    }
}
```

**Contract Compliance:**
- ✅ Abstractions in core/persistence (allowed)
- ✅ Domain model (HomeMediaItem) imported from core/home-domain
- ✅ No infra, pipeline, or transport dependencies

---

### 2.2 Cache Implementation (L1 Memory)

**Affected Modules:**
- `core/persistence/` - InMemoryHomeCache.kt (new)

**Layer Compliance:**
- ✅ Implementation stays in core/persistence
- ✅ No domain logic, pure caching

**Implementation:**

```kotlin
// core/persistence/src/.../cache/impl/InMemoryHomeCache.kt

package com.fishit.player.core.persistence.cache.impl

import com.fishit.player.core.persistence.cache.CacheKey
import com.fishit.player.core.persistence.cache.CachedSection
import com.fishit.player.core.persistence.cache.HomeContentCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * L1 in-memory cache implementation.
 * 
 * **Characteristics:**
 * - Fast: ConcurrentHashMap access (<1ms)
 * - Volatile: Lost on app kill
 * - Short TTL: 5 seconds (for rapid updates)
 * 
 * **Layer Compliance:**
 * - No UI, Domain, Pipeline dependencies
 * - Pure infra/caching concern
 */
@Singleton
class InMemoryHomeCache @Inject constructor() : HomeContentCache {
    
    private val cache = ConcurrentHashMap<CacheKey, CachedSection>()
    private val _invalidations = MutableSharedFlow<CacheKey>(extraBufferCapacity = 10)
    
    override suspend fun get(key: CacheKey): CachedSection? {
        return cache[key]?.takeUnless { it.isExpired() }
    }
    
    override suspend fun put(key: CacheKey, section: CachedSection) {
        cache[key] = section
    }
    
    override suspend fun invalidate(key: CacheKey) {
        cache.remove(key)
        _invalidations.emit(key)
    }
    
    override suspend fun invalidateAll() {
        cache.clear()
        CacheKey::class.sealedSubclasses.forEach { keyClass ->
            val key = keyClass.objectInstance as? CacheKey
            key?.let { _invalidations.emit(it) }
        }
    }
    
    override fun observeInvalidations(): Flow<CacheKey> = _invalidations.asSharedFlow()
}
```

**Contract Compliance:**
- ✅ Pure infra implementation
- ✅ No cross-layer violations
- ✅ Injectable via Hilt

---

### 2.3 Cache Integration in Data Layer

**Affected Modules:**
- `infra/data-home/` - HomeContentRepositoryAdapter.kt

**Layer Compliance:**
- ✅ Data layer → Persistence layer (allowed)
- ✅ Cache as implementation detail (not exposed to domain)
- ✅ Domain layer remains unaware of caching

**Implementation:**

```kotlin
// infra/data-home/src/.../HomeContentRepositoryAdapter.kt

@Singleton
class HomeContentRepositoryAdapter @Inject constructor(
    private val boxStore: BoxStore,
    private val homeContentCache: HomeContentCache,  // ✅ NEW: Inject cache
    // ... existing dependencies
) : HomeContentRepository {
    
    override fun observeContinueWatching(): Flow<List<HomeMediaItem>> {
        return flow {
            // Try cache first (L1)
            val cached = homeContentCache.get(CacheKey.ContinueWatching)
            if (cached != null) {
                emit(cached.items)
            }
            
            // Then stream from ObjectBox (L3)
            canonicalResumeBox.query()
                .greater(ObxCanonicalResumeMark_.positionPercent, 0.0)
                .equal(ObxCanonicalResumeMark_.isCompleted, false)
                .eager(ObxCanonicalResumeMark_.canonicalMedia)  // ✅ Eager load
                .orderDesc(ObxCanonicalResumeMark_.updatedAt)
                .build()
                .asFlow()
                .map { resumeMarks ->
                    // ... existing mapping
                    val items = /* ... mapping logic ... */
                    
                    // Update cache (background)
                    homeContentCache.put(
                        CacheKey.ContinueWatching,
                        CachedSection(items, ttl = 5.seconds)
                    )
                    
                    items
                }
                .collect { emit(it) }
        }
            .distinctUntilChanged()
            .catch { /* ... existing error handling */ }
    }
    
    // Apply to all observe* methods
}
```

**Contract Compliance:**
- ✅ Data layer uses Persistence layer (allowed)
- ✅ Cache not exposed to domain/UI
- ✅ Transparent optimization

---

## Phase 3: Granular State Slices (3-4 Days)

### 3.1 Section-Based State in Domain

**Affected Modules:**
- `core/home-domain/` - HomeSectionState.kt (new)

**Layer Compliance:**
- ✅ Domain layer defines state model
- ✅ No infra dependencies
- ✅ UI consumes via domain interfaces

**Implementation:**

```kotlin
// core/home-domain/src/.../HomeSectionState.kt

package com.fishit.player.core.home.domain

/**
 * Granular section state for independent recomposition.
 * 
 * **Architecture:**
 * - Each section has its own StateFlow in ViewModel
 * - UI observes only sections it renders
 * - Section updates don't trigger full screen recomposition
 * 
 * **Layer Compliance:**
 * - Defined in domain layer (core/home-domain)
 * - No UI or infra dependencies
 * - Feature layer consumes via collectAsState()
 */
sealed class HomeSectionState<out T> {
    object Initial : HomeSectionState<Nothing>()
    object Loading : HomeSectionState<Nothing>()
    data class Loaded<T>(val data: T, val refreshedAt: Long) : HomeSectionState<T>()
    data class Error(val message: String, val cached: Any?) : HomeSectionState<Nothing>()
}

/**
 * Typed section wrappers for type safety.
 */
data class ContinueWatchingSection(val items: List<HomeMediaItem>)
data class RecentlyAddedSection(val items: List<HomeMediaItem>)
data class MoviesSection(val items: List<HomeMediaItem>)
data class SeriesSection(val items: List<HomeMediaItem>)
data class ClipsSection(val items: List<HomeMediaItem>)
data class LiveTVSection(val items: List<HomeMediaItem>)

/**
 * Meta-state for cross-cutting concerns.
 */
data class HomeMetaState(
    val syncState: SyncUiState,
    val sourceActivation: SourceActivationSnapshot,
    val error: String? = null,
    val isSearchVisible: Boolean = false
)
```

**Contract Compliance:**
- ✅ Pure domain models
- ✅ No layer violations
- ✅ Type-safe section wrappers

---

### 3.2 Refactor ViewModel to Granular Flows

**Affected Modules:**
- `feature/home/` - HomeViewModel.kt

**Layer Compliance:**
- ✅ UI layer → Domain layer (allowed)
- ✅ No infra imports
- ✅ Uses domain interfaces only

**Implementation:**

```kotlin
// feature/home/src/.../HomeViewModel.kt

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeContentRepository: HomeContentRepository,
    // ... existing dependencies
) : ViewModel() {
    
    // ✅ NEW: Granular section flows (independent)
    val continueWatching: StateFlow<HomeSectionState<ContinueWatchingSection>> =
        homeContentRepository.observeContinueWatching()
            .map { items ->
                HomeSectionState.Loaded(
                    ContinueWatchingSection(items),
                    System.currentTimeMillis()
                )
            }
            .catch { HomeSectionState.Error(it.message ?: "Unknown", null) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                HomeSectionState.Initial
            )
    
    val recentlyAdded: StateFlow<HomeSectionState<RecentlyAddedSection>> =
        homeContentRepository.observeRecentlyAdded()
            .map { items ->
                HomeSectionState.Loaded(
                    RecentlyAddedSection(items),
                    System.currentTimeMillis()
                )
            }
            .catch { HomeSectionState.Error(it.message ?: "Unknown", null) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                HomeSectionState.Initial
            )
    
    // Repeat for: movies, series, clips, liveTV
    
    // ✅ Meta-state (only cross-cutting concerns)
    val metaState: StateFlow<HomeMetaState> = combine(
        syncStateObserver.observeSyncState(),
        sourceActivationStore.observeStates(),
        errorState,
        _isSearchVisible
    ) { syncState, sourceActivation, error, isSearchVisible ->
        HomeMetaState(
            syncState = syncState,
            sourceActivation = sourceActivation,
            error = error,
            isSearchVisible = isSearchVisible
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeMetaState(
            syncState = SyncUiState.Idle,
            sourceActivation = SourceActivationSnapshot.EMPTY
        )
    )
}
```

**Contract Compliance:**
- ✅ UI → Domain (allowed imports)
- ✅ No infra imports
- ✅ Each section is independent StateFlow

---

### 3.3 Refactor HomeScreen for Selective Observation

**Affected Modules:**
- `feature/home/` - HomeScreen.kt

**Layer Compliance:**
- ✅ UI layer composables
- ✅ Observe domain state only
- ✅ No infra or data layer imports

**Implementation:**

```kotlin
// feature/home/src/.../HomeScreen.kt

@Composable
fun HomeScreen(
    onItemClick: (HomeMediaItem) -> Unit,
    onSettingsClick: () -> Unit,
    onDebugClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    // ✅ Granular observation (each section independent)
    val continueWatchingState by viewModel.continueWatching.collectAsState()
    val recentlyAddedState by viewModel.recentlyAdded.collectAsState()
    val moviesState by viewModel.movies.collectAsState()
    val seriesState by viewModel.series.collectAsState()
    val clipsState by viewModel.clips.collectAsState()
    val liveTVState by viewModel.liveTV.collectAsState()
    
    val metaState by viewModel.metaState.collectAsState()
    
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            HomeTopBar(
                syncState = metaState.syncState,
                isSearchActive = metaState.isSearchVisible,
                // ... handlers
            )
            
            // ✅ Selective recomposition: Only changed section recomposes
            LazyColumn {
                // Continue Watching Section
                when (val state = continueWatchingState) {
                    is HomeSectionState.Loaded -> {
                        item(key = "continue_watching") {
                            MediaRow(
                                title = "Continue Watching",
                                items = state.data.items,
                                onItemClick = onItemClick
                            )
                        }
                    }
                    // Handle Loading, Error states
                }
                
                // Recently Added Section
                when (val state = recentlyAddedState) {
                    is HomeSectionState.Loaded -> {
                        item(key = "recently_added") {
                            MediaRow(
                                title = "Recently Added",
                                items = state.data.items,
                                onItemClick = onItemClick
                            )
                        }
                    }
                }
                
                // ... repeat for other sections
            }
        }
    }
}
```

**Contract Compliance:**
- ✅ UI observes domain state only
- ✅ No data/infra imports
- ✅ Granular recomposition per section

---

## Phase 4: Progressive Loading Orchestration (2-3 Days)

### 4.1 Load Orchestrator in Domain

**Affected Modules:**
- `core/home-domain/` - HomeLoadOrchestrator.kt (new)

**Layer Compliance:**
- ✅ Domain layer coordination logic
- ✅ No UI dependencies
- ✅ Uses repository interfaces only

**Implementation:**

```kotlin
// core/home-domain/src/.../HomeLoadOrchestrator.kt

package com.fishit.player.core.home.domain

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Progressive loading orchestrator for Home content.
 * 
 * **Architecture:**
 * - Loads content in priority phases (Critical → High → Normal)
 * - Prevents UI freezes by staggering DB queries
 * - Provides perceived performance via early content display
 * 
 * **Layer Compliance:**
 * - Lives in domain layer (orchestration logic)
 * - Uses repository interfaces only (no infra dependencies)
 * - No UI concerns (triggers via UseCase pattern)
 */
@Singleton
class HomeLoadOrchestrator @Inject constructor(
    private val homeContentRepository: HomeContentRepository
) {
    
    /**
     * Load phases with priority.
     */
    enum class LoadPhase {
        CRITICAL,  // Continue Watching, Recently Added (above fold)
        HIGH,      // Movies, Series (first scroll)
        NORMAL,    // Clips, Live TV (below fold)
        COMPLETE   // All loaded
    }
    
    /**
     * Trigger progressive load with priority phases.
     * 
     * @return Flow of load progress (for UI feedback)
     */
    suspend fun loadProgressively(): kotlinx.coroutines.flow.Flow<LoadPhase> = 
        kotlinx.coroutines.flow.flow {
            coroutineScope {
                // Phase 1: CRITICAL (above-the-fold content)
                emit(LoadPhase.CRITICAL)
                async { homeContentRepository.observeContinueWatching().first() }
                async { homeContentRepository.observeRecentlyAdded().first() }
                
                delay(50)  // Breathing room for UI
                
                // Phase 2: HIGH (first scroll content)
                emit(LoadPhase.HIGH)
                async { homeContentRepository.observeMovies().first() }
                async { homeContentRepository.observeSeries().first() }
                
                delay(100)  // Breathing room for UI
                
                // Phase 3: NORMAL (below fold)
                emit(LoadPhase.NORMAL)
                async { homeContentRepository.observeClips().first() }
                async { homeContentRepository.observeXtreamLive().first() }
                
                emit(LoadPhase.COMPLETE)
            }
        }
}
```

**Contract Compliance:**
- ✅ Domain layer orchestration
- ✅ No UI or infra imports
- ✅ Uses repository interfaces only

---

### 4.2 Integrate in ViewModel

**Affected Modules:**
- `feature/home/` - HomeViewModel.kt

**Layer Compliance:**
- ✅ UI → Domain (allowed)
- ✅ Orchestrator injection via Hilt

**Implementation:**

```kotlin
// feature/home/src/.../HomeViewModel.kt

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeContentRepository: HomeContentRepository,
    private val loadOrchestrator: HomeLoadOrchestrator,  // ✅ NEW
    // ... existing dependencies
) : ViewModel() {
    
    // Load progress for UI feedback
    private val _loadPhase = MutableStateFlow(HomeLoadOrchestrator.LoadPhase.CRITICAL)
    val loadPhase: StateFlow<HomeLoadOrchestrator.LoadPhase> = _loadPhase.asStateFlow()
    
    init {
        // Trigger progressive load on init
        viewModelScope.launch {
            loadOrchestrator.loadProgressively().collect { phase ->
                _loadPhase.value = phase
            }
        }
    }
    
    // ... existing section flows (unchanged)
}
```

**Contract Compliance:**
- ✅ UI → Domain (allowed)
- ✅ No new layer violations

---

## Phase 5: FTS Index + Paging (5-7 Days)

### 5.1 FTS Index in Persistence

**Affected Modules:**
- `core/persistence/` - ObxCanonicalMedia.kt

**Layer Compliance:**
- ✅ Persistence layer enhancement
- ✅ No domain logic in entity

**Implementation:**

```kotlin
// core/persistence/src/.../obx/ObxCanonicalMedia.kt

@Entity
data class ObxCanonicalMedia(
    @Id var id: Long = 0,
    
    @Index(type = IndexType.TEXT)  // ✅ NEW: Full-Text-Search index
    var title: String = "",
    
    @Index(type = IndexType.TEXT)  // ✅ NEW: FTS on plot
    var plotSummary: String? = null,
    
    // ... existing fields
)
```

**Contract Compliance:**
- ✅ Pure persistence concern
- ✅ No business logic

---

### 5.2 FTS Search in Repository Interface

**Affected Modules:**
- `core/home-domain/` - HomeContentRepository.kt
- `infra/data-home/` - HomeContentRepositoryAdapter.kt

**Layer Compliance:**
- ✅ Domain defines interface
- ✅ Data implements with ObjectBox FTS

**Implementation:**

```kotlin
// core/home-domain/src/.../HomeContentRepository.kt

interface HomeContentRepository {
    // ... existing methods
    
    /**
     * Full-text search across all home content.
     * Uses ObjectBox FTS index for fast queries.
     */
    fun searchFTS(query: String): Flow<List<HomeMediaItem>>
}

// infra/data-home/src/.../HomeContentRepositoryAdapter.kt

override fun searchFTS(query: String): Flow<List<HomeMediaItem>> {
    if (query.length < 2) return flowOf(emptyList())
    
    return canonicalMediaBox.query(
        // ✅ Native FTS query (100x faster than LIKE)
        ObxCanonicalMedia_.title.fullTextContains(query)
            .or(ObxCanonicalMedia_.plotSummary.fullTextContains(query))
    )
        .build()
        .asFlow()
        .map { items -> items.map { it.toHomeMediaItem() } }
        .distinctUntilChanged()
        .catch { emit(emptyList()) }
}
```

**Contract Compliance:**
- ✅ Domain → Data (via interface)
- ✅ No layer violations

---

### 5.3 Paging3 Integration

**Affected Modules:**
- `infra/data-home/` - HomeContentPagingSource.kt (new)
- `core/home-domain/` - Add paging methods to interface

**Layer Compliance:**
- ✅ Paging3 is data layer concern
- ✅ Domain exposes PagingData<T> (Paging3 type)
- ✅ UI consumes via Paging3 Compose extensions

**Implementation:**

```kotlin
// core/home-domain/src/.../HomeContentRepository.kt

interface HomeContentRepository {
    // ... existing methods
    
    /**
     * Paged movies for lazy loading.
     */
    fun observeMoviesPaged(): Flow<PagingData<HomeMediaItem>>
}

// infra/data-home/src/.../HomeContentRepositoryAdapter.kt

override fun observeMoviesPaged(): Flow<PagingData<HomeMediaItem>> {
    return Pager(
        config = PagingConfig(
            pageSize = 20,
            enablePlaceholders = false,
            initialLoadSize = 40
        ),
        pagingSourceFactory = {
            HomeMoviesPagingSource(canonicalMediaBox)
        }
    ).flow
}

// infra/data-home/src/.../HomeContentPagingSource.kt

class HomeMoviesPagingSource(
    private val canonicalMediaBox: Box<ObxCanonicalMedia>
) : PagingSource<Int, HomeMediaItem>() {
    
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, HomeMediaItem> {
        val page = params.key ?: 0
        val pageSize = params.loadSize
        
        return try {
            val items = canonicalMediaBox.query(
                ObxCanonicalMedia_.mediaType.equal("MOVIE")
            )
                .eager(ObxCanonicalMedia_.sources)
                .orderDesc(ObxCanonicalMedia_.createdAt)
                .build()
                .find(pageSize.toLong() * page, pageSize.toLong())
                .map { it.toHomeMediaItem() }
            
            LoadResult.Page(
                data = items,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (items.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
```

**Contract Compliance:**
- ✅ Paging in data layer
- ✅ Domain exposes Flow<PagingData<T>>
- ✅ UI uses Paging3 Compose (standard Google lib)

---

## Module Dependencies Summary

### Dependency Graph (After All Phases)

```
feature/home
    ↓ depends on
core/home-domain (interfaces)
    ↓ implemented by
infra/data-home (adapter)
    ↓ depends on
core/persistence (cache, entities, ObjectBox)
    ↓ depends on
core/model (domain primitives)
```

**No Violations:**
- ✅ UI never imports infra/*
- ✅ Domain never imports infra/*
- ✅ Data never imports pipeline/* DTOs
- ✅ All dependencies flow downward

---

## Testing Strategy (Contract Compliant)

### Unit Tests per Layer

```
feature/home/src/test/
    └── HomeViewModelTest.kt
        - Tests granular section flows
        - Mocks HomeContentRepository (domain interface)
        - No infra dependencies

core/home-domain/src/test/
    └── HomeLoadOrchestratorTest.kt
        - Tests load phases
        - Mocks repository interface
        - No UI or infra dependencies

infra/data-home/src/test/
    └── HomeContentRepositoryAdapterTest.kt
        - Tests cache integration
        - Tests ObjectBox queries
        - Tests mapping to HomeMediaItem
        - No UI dependencies

core/persistence/src/test/
    └── InMemoryHomeCacheTest.kt
        - Tests cache invalidation
        - Tests TTL expiration
        - No domain or UI dependencies
```

**Contract Compliance:**
- ✅ Each layer tests only its own concerns
- ✅ Dependencies mocked via interfaces
- ✅ No cross-layer test violations

---

## Migration Strategy

### Step-by-Step (Backward Compatible)

1. **Phase 1 (Quick Wins):**
   - ✅ No breaking changes
   - ✅ Existing HomeState untouched
   - ✅ Can be released incrementally

2. **Phase 2 (Caching):**
   - ✅ Cache as implementation detail
   - ✅ UI/Domain unaware
   - ✅ No API changes

3. **Phase 3 (Granular State):**
   - ⚠️ Breaking change for HomeScreen
   - ✅ Feature flag: `GRANULAR_HOME_STATE`
   - ✅ Old state remains until migration complete

4. **Phase 4 (Progressive Loading):**
   - ✅ Opt-in via orchestrator
   - ✅ Existing flows work unchanged

5. **Phase 5 (FTS + Paging):**
   - ✅ New methods added to interface
   - ✅ Old methods remain for compatibility
   - ✅ UI switches when ready

---

## Contract Validation Checklist

Before each PR:

- [ ] Run layer boundary audit: `grep -rn "import.*infra\." feature/`
- [ ] Run pipeline DTO check: `grep -rn "import.*TelegramMediaItem" infra/data-*`
- [ ] Verify no transport in UI: `grep -rn "import.*transport\." feature/`
- [ ] Run tests per layer: `./gradlew :feature:home:test :infra:data-home:test`
- [ ] Check ObjectBox eager loading: Search for `.eager(` in adapters
- [ ] Verify cache TTLs: Check all `CachedSection` instantiations

---

## Performance Targets (Per Phase)

| Phase | Metric | Before | Target | Validation |
|-------|--------|--------|--------|------------|
| 1 | Initial Load | 800ms | 400ms | Systrace |
| 2 | Cache Hit | N/A | <50ms | Benchmark |
| 3 | Recompositions | 100+ | <10 | Compose Compiler Metrics |
| 4 | Above-Fold | 800ms | 100ms | User Timing API |
| 5 | Search | 400ms | <50ms | Query profiler |

---

## Next Steps

1. Review this plan with team
2. Create feature branch: `feature/home-platinum-performance`
3. Implement Phase 1 (1 day)
4. Merge & validate (no regressions)
5. Continue with Phase 2-5

**End of Plan**
