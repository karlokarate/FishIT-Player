---
applyTo: 
  - feature/**
---

# 🏆 PLATIN Instructions:  feature/* (Common Rules)

**Version:** 1.0  
**Last Updated:** 2026-02-04  
**Status:** Active

> **PLATIN STANDARD** - Feature Layer (UI + ViewModel) - Gemeinsame Regeln. 
>
> **Purpose:** Diese Rules gelten für ALLE feature-Module ohne Ausnahme. 
> Modul-spezifische Rules stehen in separaten Instructions (`feature-detail`, `feature-settings`).
>
> **Critical Principle:** Feature layer depends ONLY on interfaces (domain, playback).
> NEVER imports from infra, pipeline, or transport. 

---

## 🔴 ABSOLUTE HARD RULES (ALL FEATURES)

### 1. Feature Layer Isolation (CRITICAL)

```kotlin
// ✅ ALLOWED IN ALL FEATURE MODULES
import com.fishit.player.core.model.*                       // Core types
import com.fishit.player.core.*-domain.*                    // Domain interfaces
import com.fishit.player.playback.domain.*                  // Playback interfaces
import androidx.compose.*                                   // Compose UI
import androidx.lifecycle.*                                 // ViewModel
import androidx.hilt.navigation.compose.*                   // Hilt ViewModel
import androidx.navigation.*                                // Navigation

// ❌ FORBIDDEN IN ALL FEATURE MODULES
import com.fishit.player.pipeline.*                         // Pipeline
import com.fishit.player.infra.transport.*                  // Transport
import com.fishit.player.infra.data.*                       // Data repositories
import com.fishit.player.player.internal.*                  // Player internals
import org.drinkless.td.TdApi.*                             // TDLib
import okhttp3.*                                            // HTTP client
import io.objectbox.*                                       // ObjectBox
```

**Why This Matters:**
- Feature layer is the presentation layer, not business logic
- All business logic lives in domain use cases
- Feature modules depend ONLY on abstractions

---

### 2. ViewModel Responsibilities (ALL FEATURES)

```kotlin
// ✅ CORRECT: ViewModel coordinates domain use cases
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeContentRepository,  // Domain interface
    private val syncStateObserver: SyncStateObserver,          // Domain interface
) : ViewModel() {
    
    val content:  StateFlow<List<MediaItem>> = 
        repository
            .observeMovies()
            .map { movies -> movies.map { it.toUiModel() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

// ❌ WRONG: ViewModel calls transport/pipeline directly
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val telegramClient: TelegramHistoryClient,        // WRONG - transport!
    private val xtreamApi: XtreamApiClient,                   // WRONG - transport!
) : ViewModel() {
    fun loadContent() {
        val messages = telegramClient.fetchMessages(chatId, 100)  // WRONG! 
    }
}
```

**ViewModel Rules:**
- NO transport calls (use domain repositories)
- NO pipeline imports (use domain models)
- NO direct persistence (use repositories)
- NO business logic (delegate to use cases)
- ONLY UI state management + coordination

---

### 3. Reactive State Pattern (ALL FEATURES - MANDATORY)

```kotlin
// ✅ CORRECT: Reactive Flow with stateIn
val movies:  StateFlow<List<HomeMediaItem>> = 
    repository
        .observeMovies()
        .map { domain -> domain.map { it.toHomeMediaItem() } }
        .catch { e ->
            UnifiedLog.e(TAG, e) { "Error loading movies" }
            emit(emptyList())  // Graceful degradation
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

// ❌ WRONG: Manual state updates
private val _movies = MutableStateFlow<List<HomeMediaItem>>(emptyList())
val movies: StateFlow<List<HomeMediaItem>> = _movies.asStateFlow()

init {
    loadMovies()  // WRONG - manual loading
}

private fun loadMovies() {
    viewModelScope.launch {
        val items = repository.getMovies()  // WRONG - one-shot query
        _movies.value = items. map { it.toHomeMediaItem() }
    }
}
```

**Why Reactive is Better:**
- Automatic updates when data changes
- No manual refresh needed
- Memory-efficient (WhileSubscribed stops collection when inactive)
- Testable with fake repositories

---

### 4. Error Handling Pattern (ALL FEATURES)

```kotlin
// ✅ CORRECT: Catch errors and show UI state
val content: StateFlow<List<MediaItem>> = 
    repository
        .observeContent()
        .catch { e ->
            UnifiedLog.e(TAG, e) { "Error loading content:  ${e.message}" }
            emit(emptyList())  // Graceful degradation
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

// UI handles empty state
@Composable
fun ContentList(items: List<MediaItem>) {
    when {
        items.isEmpty() -> EmptyStateUI()
        else -> LazyColumn { items(items) { MediaCard(it) } }
    }
}

// ❌ WRONG:  Uncaught exceptions crash UI
val content: StateFlow<List<MediaItem>> = 
    repository
        .observeContent()  // May throw - not caught!
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

---

### 5. Navigation Pattern (ALL FEATURES)

```kotlin
// ✅ CORRECT: Events via SharedFlow, Screen handles navigation
sealed class HomeEvent {
    data class NavigateToDetail(val mediaId:  String) : HomeEvent()
    data class StartPlayback(val context: PlaybackContext) : HomeEvent()
}

@HiltViewModel
class HomeViewModel @Inject constructor(/* ... */) : ViewModel() {
    private val _events = MutableSharedFlow<HomeEvent>()
    val events = _events.asSharedFlow()
    
    fun onItemClick(item: HomeMediaItem) {
        viewModelScope.launch {
            _events.emit(HomeEvent.NavigateToDetail(item.id))
        }
    }
}

@Composable
fun HomeScreen(
    onNavigateToDetail: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.NavigateToDetail -> onNavigateToDetail(event. mediaId)
                is HomeEvent.StartPlayback -> { /* ... */ }
            }
        }
    }
    
    // UI code
}

// ❌ WRONG:  ViewModel depends on NavController
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val navController: NavController,  // WRONG - Android type in ViewModel!
) : ViewModel() {
    fun onItemClick(item: HomeMediaItem) {
        navController.navigate("detail/${item.id}")  // WRONG - navigation in ViewModel!
    }
}
```

---

### 6. UI State Models (ALL FEATURES)

```kotlin
// ✅ CORRECT: UI state in feature module
data class HomeUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val contentStreams: HomeContentStreams = HomeContentStreams(),
    val syncState: SyncUiState = SyncUiState. Idle,
)

data class HomeContentStreams(
    val continueWatching: List<HomeMediaItem> = emptyList(),
    val recentlyAdded: List<HomeMediaItem> = emptyList(),
    val movies: List<HomeMediaItem> = emptyList(),
)

// ❌ WRONG: Domain/Pipeline models leaking into UI state
data class HomeUiState(
    val rawMetadata: List<RawMediaMetadata> = emptyList(),  // WRONG - internal model!
    val telegramItems: List<TelegramMediaItem> = emptyList(),  // WRONG - pipeline model!
    val obxEntities: List<ObxCanonicalMedia> = emptyList(),  // WRONG - persistence model!
)
```

---

### 7. Use Case Pattern (Feature-Owned)

```kotlin
// ✅ CORRECT: Use case coordinates domain services
@Singleton
class PlayMediaUseCase @Inject constructor(
    private val playerEntry:  PlayerEntryPoint,  // Domain interface
) {
    suspend fun play(
        canonicalId: String,
        source: MediaSourceRef,
    ) {
        val context = buildPlaybackContext(canonicalId, source)
        playerEntry. start(context)
    }
    
    private fun buildPlaybackContext(
        canonicalId: String,
        source: MediaSourceRef,
    ): PlaybackContext {
        return PlaybackContext(
            canonicalId = canonicalId,
            sourceType = source.sourceType,
            sourceKey = source.sourceId. value,
            title = source.sourceLabel,
            extras = source.playbackHints,
        )
    }
}

// ❌ WRONG: Use case calls transport directly
@Singleton
class PlayMediaUseCase @Inject constructor(
    private val fileClient: TelegramFileClient,  // WRONG - transport!
) {
    suspend fun play(item: MediaItem) {
        val file = fileClient.downloadFile(item.fileId)  // WRONG!
    }
}
```

---

## 📐 Architecture Position

```
Feature Layer (UI + ViewModel) ← YOU ARE HERE
      ↓ depends on ↓
Domain Layer (Use Cases + Repositories)
      ↓ depends on ↓
Data Layer (Repository Implementations)
      ↓ depends on ↓
Transport Layer (External APIs)
```

**Critical Rules:**
- Feature → Domain:  ✅ Allowed (interfaces only)
- Feature → Data: ❌ Forbidden (breaks layer isolation)
- Feature → Transport: ❌ Forbidden (breaks layer isolation)
- Feature → Pipeline: ❌ Forbidden (breaks layer isolation)
- Feature → Player Internal: ❌ Forbidden (use PlayerEntryPoint)

---

## 🔍 Layer Boundary Enforcement

### CI Verification

```bash
# 1. No forbidden imports in ANY feature module
grep -rn "import.*pipeline\|import.*infra\.transport\|import.*infra\.data\|import.*player\.internal" feature/

# 2. No transport calls in ANY ViewModel
grep -rn "TelegramHistoryClient\|TelegramFileClient\|XtreamApiClient" feature/

# 3. No ObjectBox entity usage in ANY feature
grep -rn "import.*core\.persistence\.obx\." feature/

# 4. No direct player session access in ANY feature
grep -rn "InternalPlayerSession\|InternalPlayerState" feature/

# All should return empty! 
```

---

## ✅ PLATIN Checklist (ALL FEATURES)

### Common Rules
- [ ] ViewModels depend ONLY on domain interfaces
- [ ] NO transport imports (use domain repositories)
- [ ] NO pipeline imports (use domain models)
- [ ] NO data implementations (use interfaces)
- [ ] NO player internals (use PlayerEntryPoint)
- [ ] Reactive state via StateFlow + `stateIn`
- [ ] Error handling via `.catch { }` on Flows
- [ ] Navigation via SharedFlow events
- [ ] Uses UnifiedLog for all logging
- [ ] Hilt ViewModel injection via `@HiltViewModel`

### Composable Rules
- [ ] Composable entry point with `hiltViewModel()` default parameter
- [ ] Screen parameters via function arguments (not NavArgs)
- [ ] Navigation callbacks as lambdas (not NavController)
- [ ] Loading/Error/Empty states handled in UI
- [ ] Smart empty states (per STARTUP_TRIGGER_CONTRACT)

### Use Case Rules
- [ ] Use cases are `@Singleton` or `@ActivityScoped`
- [ ] Use cases depend ONLY on domain interfaces
- [ ] Use cases coordinate multiple domain services
- [ ] Use cases build domain models (PlaybackContext, etc.)
- [ ] Use cases handle async orchestration

---

## 📚 Reference Documents

1. `/AGENTS.md` - Feature layer architecture rules
2. `/docs/v2/STARTUP_TRIGGER_CONTRACT.md` - Smart empty states (AUTHORITATIVE)
3. `/contracts/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md` - Playback behavior
4. Jetpack Compose documentation
5. Hilt ViewModel documentation

---

## 🚨 Common Violations & Solutions

### Violation 1: Transport Calls in ViewModel

```kotlin
// ❌ WRONG
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val telegramClient: TelegramHistoryClient,  // WRONG! 
)

// ✅ CORRECT
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeContentRepository,  // Domain interface
)
```

---

### Violation 2: Manual State Updates

```kotlin
// ❌ WRONG
private val _movies = MutableStateFlow<List<Movie>>(emptyList())
init { loadMovies() }

// ✅ CORRECT
val movies = repository.observeMovies()
    .map { it.toUiModel() }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

---

### Violation 3: Navigation in ViewModel

```kotlin
// ❌ WRONG
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val navController: NavController,  // WRONG!
)

// ✅ CORRECT
sealed class HomeEvent {
    data class NavigateToDetail(val id: String) : HomeEvent()
}

private val _events = MutableSharedFlow<HomeEvent>()
val events = _events.asSharedFlow()
```

---

### Violation 4: Direct Player Access

```kotlin
// ❌ WRONG
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val playerSession: InternalPlayerSession,  // WRONG!
)

// ✅ CORRECT
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val playMediaUseCase: PlayMediaUseCase,  // Domain use case
)
```

---

**End of Common Feature Rules**

**Note:** For module-specific rules (e.g., Race-Free Source Selection in `feature/detail`),
see the module-specific instruction files: 
- `feature-detail.instructions.md`
- `feature-settings.instructions.md`