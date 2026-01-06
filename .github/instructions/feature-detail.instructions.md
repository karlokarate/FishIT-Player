---
applyTo: 
  - feature/detail/**
---

# 🏆 PLATIN Instructions:  feature/detail

> **PLATIN STANDARD** - Detail Screen (Unified Detail for All Media Types).
>
> **Purpose:** Unified detail screen with race-free source selection and async enrichment.
> **Inherits:** All rules from `feature-common. instructions.md` apply here. 

---

## 🔴 MODULE-SPECIFIC HARD RULES

### 1. Race-Free Source Selection (CRITICAL)

```kotlin
// ✅ CORRECT: Derive activeSource from SSOT at moment of use
fun play() {
    viewModelScope.launch {
        val activeSource = SourceSelection.resolveActiveSource(
            media = state.value.media,  // Current media from StateFlow
            selectedSourceKey = state.value.selectedSourceKey,
            resume = state.value.resume,
        )
        
        playMediaUseCase.play(
            canonicalId = state.value.media.canonicalId,
            source = activeSource,
        )
    }
}

// ❌ WRONG:  Stale snapshot (race condition!)
private var selectedSource: MediaSourceRef? = null  // Cached, may be stale!

fun selectSource(source: MediaSourceRef) {
    selectedSource = source  // WRONG - snapshot may become stale!
}

fun play() {
    viewModelScope.launch {
        playMediaUseCase.play(
            canonicalId = media.canonicalId,
            source = selectedSource ?: media.sources.first(),  // WRONG - stale! 
        )
    }
}
```

**Why This Matters:**
- Sources may become unavailable between selection and playback
- Resume position may change between selection and playback
- Media may be refreshed/enriched between selection and playback
- ALWAYS derive from current StateFlow state, never cache

---

### 2. Async Enrichment Pattern

```kotlin
// ✅ CORRECT: Use DetailEnrichmentService (domain)
@HiltViewModel
class UnifiedDetailViewModel @Inject constructor(
    private val detailUseCases: UnifiedDetailUseCases,
    private val enrichmentService: DetailEnrichmentService,  // Domain service
) : ViewModel() {
    
    private val _state = MutableStateFlow(UnifiedDetailUiState())
    val state = _state.asStateFlow()
    
    fun loadDetail(canonicalId: String) {
        viewModelScope.launch {
            // 1. Load existing data immediately
            val media = detailUseCases.getMediaDetail(canonicalId)
            _state.update { it.copy(media = media, isLoading = false) }
            
            // 2. Trigger async enrichment (TMDB, etc.)
            enrichmentService.enrich(canonicalId)
            // StateFlow will update automatically when enrichment completes
        }
    }
}

// ❌ WRONG: ViewModel does enrichment itself
@HiltViewModel
class UnifiedDetailViewModel @Inject constructor(
    private val tmdbClient: TmdbApiClient,  // WRONG - transport!
) : ViewModel() {
    fun loadDetail(canonicalId: String) {
        viewModelScope.launch {
            val tmdbData = tmdbClient.search(title)  // WRONG - transport in ViewModel!
        }
    }
}
```

---

### 3. PlayMediaUseCase Pattern

```kotlin
// ✅ CORRECT: Use case builds PlaybackContext from domain models
@Singleton
class PlayMediaUseCase @Inject constructor(
    private val playerEntry: PlayerEntryPoint,
) {
    suspend fun play(
        canonicalId: String,
        source: MediaSourceRef,
    ) {
        val context = PlaybackContext(
            canonicalId = canonicalId,
            sourceType = source.sourceType,
            sourceKey = source.sourceId.value,
            title = source.sourceLabel,
            extras = source.playbackHints,  // Pass-through for playback layer
        )
        
        playerEntry.start(context)
    }
}

// ❌ WRONG: ViewModel builds PlaybackContext
@HiltViewModel
class UnifiedDetailViewModel @Inject constructor(
    private val playerEntry: PlayerEntryPoint,
) : ViewModel() {
    fun play() {
        val context = PlaybackContext(/* ... */)  // WRONG - ViewModel knows too much!
        playerEntry.start(context)
    }
}
```

---

### 4. Source Selection State Management

```kotlin
// ✅ CORRECT: selectedSourceKey in UI state
data class UnifiedDetailUiState(
    val media: NormalizedMedia? = null,
    val selectedSourceKey: SourceKey? = null,  // UI selection state
    val resume: ResumeInfo? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

// User selects source
fun selectSource(sourceKey: SourceKey) {
    _state.update { it.copy(selectedSourceKey = sourceKey) }
}

// Resolve active source at moment of use
fun play() {
    viewModelScope. launch {
        val currentState = state.value
        val activeSource = SourceSelection.resolveActiveSource(
            media = currentState. media ?: return@launch,
            selectedSourceKey = currentState.selectedSourceKey,
            resume = currentState.resume,
        )
        
        playMediaUseCase. play(currentState.media. canonicalId, activeSource)
    }
}
```

---

### 5. Episode Selection (Series)

```kotlin
// ✅ CORRECT: Navigate to new detail screen for episode
fun selectEpisode(episodeId:  String) {
    viewModelScope.launch {
        _events.emit(UnifiedDetailEvent.NavigateToDetail(episodeId))
    }
}

// ❌ WRONG: Load episode in same ViewModel
fun selectEpisode(episodeId: String) {
    loadDetail(episodeId)  // WRONG - state leakage, back button breaks
}
```

---

## 📋 Module Responsibilities

| Responsibility | Allowed | Forbidden |
|----------------|---------|-----------|
| Display media details | ✅ | Direct repository access |
| Season/episode selection | ✅ | Transport calls |
| Source selection | ✅ | URL building |
| Playback initiation | ✅ | Direct player access |
| Async enrichment trigger | ✅ | TMDB/IMDB lookups |

---

## 📐 Architecture Position

```
UnifiedDetailScreen (Composable)
    ↓
UnifiedDetailViewModel (StateFlow)
    ↓
    ├── UnifiedDetailUseCases (domain)
    │   ├── GetMediaDetailUseCase
    │   ├── GetEpisodesUseCase
    │   └── GetRelatedMediaUseCase
    │
    ├── DetailEnrichmentService (domain)
    │   └── Async TMDB/IMDB enrichment
    │
    └── PlayMediaUseCase (feature use case)
        └── PlayerEntryPoint (domain)
```

---

## ✅ PLATIN Checklist

### Inherited from feature-common
- [ ] All common feature rules apply (see `feature-common.instructions.md`)

### Detail-Specific
- [ ] Source selection uses `SourceSelection.resolveActiveSource()` at play-time
- [ ] NO cached/stale `MediaSourceRef` snapshots
- [ ] Enrichment via `DetailEnrichmentService`, not in ViewModel
- [ ] `PlayMediaUseCase` builds `PlaybackContext` from domain models
- [ ] Episode selection navigates to new detail screen
- [ ] `selectedSourceKey` stored in UI state, derived on use

---

## 📚 Reference Documents

1. `/feature/detail/README.md` - Detail screen architecture
2. `/contracts/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md` - Playback behavior
3. `/feature-common.instructions.md` - Common rules (INHERITED)
4. `/docs/v2/DETAIL_SCREEN_ARCHITECTURE.md` - Race-free source selection

---

## 🚨 Common Violations & Solutions

### Violation 1: Stale Source Snapshot

```kotlin
// ❌ WRONG
private var selectedSource: MediaSourceRef? = null
fun play() {
    playMediaUseCase.play(media.canonicalId, selectedSource! !)  // Stale!
}

// ✅ CORRECT
fun play() {
    val activeSource = SourceSelection.resolveActiveSource(
        media = state.value.media,
        selectedSourceKey = state.value.selectedSourceKey,
        resume = state.value.resume,
    )
    playMediaUseCase.play(media.canonicalId, activeSource)
}
```

---

### Violation 2: ViewModel Does Enrichment

```kotlin
// ❌ WRONG
@HiltViewModel
class UnifiedDetailViewModel @Inject constructor(
    private val tmdbClient: TmdbApiClient,  // WRONG!
)

// ✅ CORRECT
@HiltViewModel
class UnifiedDetailViewModel @Inject constructor(
    private val enrichmentService: DetailEnrichmentService,  // Domain service
)
```

---

**End of feature/detail Instructions**