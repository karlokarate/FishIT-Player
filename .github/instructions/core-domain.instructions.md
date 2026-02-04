---
applyTo:
  - core/home-domain/**
  - core/library-domain/**
  - core/live-domain/**
  - core/detail-domain/**
  - core/telegrammedia-domain/**
  - core/onboarding-domain/**
---

# 🏆 PLATIN Instructions: core/*-domain Modules

**Version:** 1.0  
**Last Updated:** 2026-02-04  
**Status:** Active

> **PLATIN STANDARD** - Domain Use Cases and Business Logic.
>
> **Purpose:** Domain layer containing use cases that orchestrate repositories and services.
> These modules contain application-level business logic following Clean Architecture principles.
>
> **Binding Contract:** `contracts/GLOSSARY_v2_naming_and_modules.md` (Version 2.0)
>
> **Critical Principle:** Domain modules are pure business logic. They depend ONLY on
> `core/model` interfaces and `playback/domain`. NO infrastructure, transport, or UI dependencies.

---

## 🔴 ABSOLUTE HARD RULES

### 1. Use Cases ONLY - No Implementations

```kotlin
// ✅ ALLOWED: Use cases that orchestrate repositories
class GetHomeContentUseCase @Inject constructor(
    private val homeRepository: HomeContentRepository,
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(profileId: Long): HomeContent {
        val profile = profileRepository.getProfile(profileId)
        return homeRepository.getHomeContent(
            isKidsMode = profile.isKid,
            limit = if (profile.isKid) 10 else 20,
        )
    }
}

class PlayMediaUseCase @Inject constructor(
    private val playerEntry: PlayerEntryPoint,
) {
    suspend operator fun invoke(canonicalId: String, source: MediaSourceRef) {
        val context = buildPlaybackContext(canonicalId, source)
        playerEntry.start(context)
    }
}

// ❌ FORBIDDEN: Repository implementations
class HomeContentRepositoryImpl : HomeContentRepository { ... }
// ↑ Repository implementations belong in infra/data-* layer!

// ❌ FORBIDDEN: Direct persistence access
class GetMediaUseCase @Inject constructor(
    private val boxStore: BoxStore,  // WRONG - ObjectBox in domain!
) { ... }
```

**Per Glossary Section 5.2:**
> Use Case naming pattern: `<Action><Entity>UseCase` (e.g., `PlayItemUseCase`)

---

### 2. No Transport/Pipeline Dependencies

```kotlin
// ✅ ALLOWED imports in domain modules
import com.fishit.player.core.model.*                    // Core types
import com.fishit.player.core.model.repository.*         // Repository INTERFACES
import com.fishit.player.playback.domain.*               // Playback interfaces
import kotlinx.coroutines.flow.Flow                      // Coroutines
import javax.inject.Inject                               // DI annotations

// ❌ FORBIDDEN imports
import com.fishit.player.pipeline.*                      // Pipeline DTOs
import com.fishit.player.pipeline.telegram.*             // TelegramMediaItem
import com.fishit.player.pipeline.xtream.*               // XtreamVodItem
import com.fishit.player.infra.transport.*               // Transport layer
import com.fishit.player.infra.transport.telegram.*      // TelegramAuthClient etc.
import com.fishit.player.infra.data.*                    // Data layer implementations
import org.drinkless.td.*                                // TDLib
import okhttp3.*                                         // HTTP
import io.objectbox.*                                    // ObjectBox
```

**Per AGENTS.md Section 4.5 (Layer Boundary Enforcement):**
> Each layer may only import from layers directly below it in the hierarchy.
> Domain layer depends on: `core/model`, `playback/domain`
> Domain layer MUST NOT depend on: Pipeline, Transport, Data, UI

---

### 3. No UI Dependencies

```kotlin
// ❌ FORBIDDEN: UI frameworks in domain
import androidx.compose.*                                // Compose UI
import android.view.*                                    // Android Views
import androidx.lifecycle.ViewModel                      // ViewModels belong in feature/*

// ❌ FORBIDDEN: Navigation in domain
import androidx.navigation.*                             // Navigation is feature layer

// ✅ CORRECT: ViewModels consume use cases in feature layer
// feature/home/HomeViewModel.kt
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHomeContentUseCase: GetHomeContentUseCase,  // Use case injection
) : ViewModel() { ... }
```

---

### 4. Repository Interfaces ONLY

```kotlin
// ✅ CORRECT: Domain defines repository INTERFACES in core/model
// core/model/repository/HomeContentRepository.kt
interface HomeContentRepository {
    fun observeHomeContent(isKidsMode: Boolean): Flow<HomeContent>
    suspend fun getHomeContent(isKidsMode: Boolean, limit: Int): HomeContent
}

// ✅ CORRECT: Domain USE CASE uses the interface
// core/home-domain/GetHomeContentUseCase.kt
class GetHomeContentUseCase @Inject constructor(
    private val homeRepository: HomeContentRepository,  // Interface!
) { ... }

// ❌ WRONG: Domain implementing the repository
// core/home-domain/HomeContentRepositoryImpl.kt  // WRONG LOCATION!
class HomeContentRepositoryImpl @Inject constructor(
    private val boxStore: BoxStore,  // WRONG - implementation detail!
) : HomeContentRepository { ... }
// ↑ This belongs in infra/data-home/!
```

---

## 📋 Use Case Patterns

### Standard Use Case (Suspend)

```kotlin
/**
 * Use case for fetching home content based on profile.
 * 
 * @see HomeContentRepository for data source
 */
class GetHomeContentUseCase @Inject constructor(
    private val homeRepository: HomeContentRepository,
    private val profileRepository: ProfileRepository,
) {
    /**
     * @param profileId The active profile ID
     * @return HomeContent with rows appropriate for the profile type
     */
    suspend operator fun invoke(profileId: Long): HomeContent {
        val profile = profileRepository.getProfile(profileId)
        return homeRepository.getHomeContent(
            isKidsMode = profile.isKid,
            limit = if (profile.isKid) 10 else 20,
        )
    }
}
```

### Flow-based Use Case (Reactive)

```kotlin
/**
 * Observes library content with filtering.
 * Returns a Flow that updates when underlying data changes.
 */
class ObserveLibraryContentUseCase @Inject constructor(
    private val libraryRepository: LibraryContentRepository,
) {
    operator fun invoke(filter: LibraryFilter): Flow<List<MediaItem>> {
        return libraryRepository.observeContent(filter)
    }
}
```

### Orchestration Use Case (Combines Multiple Use Cases)

```kotlin
/**
 * Orchestrates sync and playback in a single operation.
 */
class SyncAndPlayUseCase @Inject constructor(
    private val syncUseCase: SyncCatalogUseCase,
    private val playUseCase: PlayMediaUseCase,
) {
    suspend operator fun invoke(mediaId: Long) {
        syncUseCase.syncIfStale()
        playUseCase(mediaId)
    }
}
```

### Playback Use Case (Per Glossary Section 1.6)

```kotlin
/**
 * Starts playback for a media item.
 * Uses PlayerEntryPoint abstraction - never accesses player internals.
 */
class PlayMediaUseCase @Inject constructor(
    private val playerEntry: PlayerEntryPoint,
) {
    suspend operator fun invoke(
        canonicalId: String,
        source: MediaSourceRef,
    ) {
        val context = PlaybackContext(
            canonicalId = canonicalId,
            sourceType = source.sourceType,
            sourceKey = source.sourceId.value,
            title = source.sourceLabel,
            extras = source.playbackHints,
        )
        playerEntry.start(context)
    }
}
```

---

## 📋 Module-Specific Responsibilities

| Module | Use Cases | Repository Interfaces Used |
|--------|-----------|---------------------------|
| `home-domain` | `GetHomeContentUseCase`, `GetContinueWatchingUseCase`, `GetRecentlyAddedUseCase` | `HomeContentRepository`, `ProfileRepository` |
| `library-domain` | `GetLibraryContentUseCase`, `SearchLibraryUseCase`, `FilterByGenreUseCase` | `LibraryContentRepository` |
| `live-domain` | `GetLiveChannelsUseCase`, `GetEpgNowUseCase`, `GetEpgScheduleUseCase` | `LiveContentRepository`, `EpgRepository` |
| `detail-domain` | `GetMediaDetailUseCase`, `GetRelatedMediaUseCase`, `GetEpisodesUseCase` | `DetailContentRepository`, `CanonicalMediaRepository` |
| `telegrammedia-domain` | `GetTelegramChatsUseCase`, `GetTelegramMediaUseCase`, `SyncTelegramChatUseCase` | `TelegramContentRepository` |
| `onboarding-domain` | `ValidateXtreamCredentialsUseCase`, `SetupSourceUseCase`, `CompleteOnboardingUseCase` | `SourceActivationStore` |

---

## 📐 Architecture Position

```
feature/* (ViewModels consume use cases)
      ↓
core/*-domain (use cases) ← YOU ARE HERE
      ↓
      ├── reads from: core/model/repository/* (interfaces ONLY)
      ├── uses: playback/domain (PlayerEntryPoint)
      └── uses: core/source-activation-api (SourceActivationStore)
      
      ↑ IMPLEMENTATIONS provided by:
      └── infra/data-* (repository implementations)
```

**Per Glossary Section 2.2 (Module Dependencies):**
```text
     ┌─────────┐
     │feature/*│  ← ViewModels
     └────┬────┘
          │
          ▼
     ┌─────────┐
     │core/*   │  ← Domain Use Cases
     │-domain  │
     └────┬────┘
          │
          ▼
     ┌─────────┐
     │core/    │  ← Repository Interfaces
     │model    │
     └─────────┘
```

---

## 🔍 Pre-Change Verification

```bash
# 1. No forbidden imports (pipeline, transport, data, UI)
grep -rn "import.*pipeline\|import.*infra\.transport\|import.*infra\.data\|import.*androidx\.compose\|import.*io\.objectbox" core/*-domain/

# 2. No repository implementations in domain
find core/*-domain/ -name "*RepositoryImpl*.kt" -o -name "*Impl.kt"

# 3. No ViewModels in domain
grep -rn "class.*ViewModel" core/*-domain/

# 4. No ObjectBox entities
grep -rn "import.*objectbox\|BoxStore\|@Entity" core/*-domain/

# All should return empty!
```

---

## ✅ PLATIN Checklist

### For ALL Domain Modules

- [ ] Only use cases and domain services (no implementations)
- [ ] No repository implementations (→ infra/data-*)
- [ ] No pipeline imports (`TelegramMediaItem`, `XtreamVodItem`)
- [ ] No transport imports (`TelegramAuthClient`, `TDLib`, `OkHttp`)
- [ ] No data layer imports (`BoxStore`, `ObjectBox`)
- [ ] No UI imports (`Compose`, `View`, `ViewModel`)
- [ ] Repository dependencies are INTERFACES from `core/model/repository`
- [ ] Use cases are injectable via `@Inject constructor`
- [ ] Use cases use `operator fun invoke()` pattern
- [ ] Flow return types for reactive/observable data
- [ ] Suspend functions for one-shot operations
- [ ] Proper error handling (`Result<T>` or sealed classes)
- [ ] Uses `PlayerEntryPoint` for playback (not player internals)
- [ ] Uses `UnifiedLog` for logging (per `LOGGING_CONTRACT_V2.md`)

### Naming Conventions (Per Glossary Section 5.2)

- [ ] Use cases: `<Action><Entity>UseCase` (e.g., `GetHomeContentUseCase`)
- [ ] Package: `com.fishit.player.core.<module>` (e.g., `core.home-domain`)
- [ ] No `*Impl` classes in domain modules

---

## 📚 Reference Documents (Priority Order)

1. **`/contracts/GLOSSARY_v2_naming_and_modules.md`** - AUTHORITATIVE naming contract (v2.0)
2. **`/AGENTS.md`** - Section 4.5 (Layer Boundary Enforcement)
3. **`/contracts/MEDIA_NORMALIZATION_CONTRACT.md`** - RawMediaMetadata contract (AUTHORITATIVE)
4. **`/contracts/LOGGING_CONTRACT_V2.md`** - Logging rules
5. Clean Architecture principles (Robert C. Martin)

---

## 🚨 Common Violations & Solutions

### Violation 1: Repository Implementation in Domain

```kotlin
// ❌ WRONG (in core/home-domain/)
class HomeContentRepositoryImpl @Inject constructor(
    private val boxStore: BoxStore,
) : HomeContentRepository { ... }

// ✅ CORRECT: Move to infra/data-home/
// infra/data-home/ObxHomeContentRepository.kt
class ObxHomeContentRepository @Inject constructor(
    private val boxStore: BoxStore,
) : HomeContentRepository { ... }
```

### Violation 2: Pipeline DTO in Domain

```kotlin
// ❌ WRONG
import com.fishit.player.pipeline.telegram.TelegramMediaItem

class GetTelegramMediaUseCase @Inject constructor(...) {
    fun invoke(): List<TelegramMediaItem>  // WRONG - pipeline DTO!
}

// ✅ CORRECT: Use domain models
class GetTelegramMediaUseCase @Inject constructor(
    private val telegramRepo: TelegramContentRepository,
) {
    fun invoke(): Flow<List<RawMediaMetadata>>  // Core model type
}
```

### Violation 3: Direct Player Access

```kotlin
// ❌ WRONG
import com.fishit.player.player.internal.InternalPlayerSession

class PlayMediaUseCase @Inject constructor(
    private val playerSession: InternalPlayerSession,  // WRONG - player internal!
) { ... }

// ✅ CORRECT: Use PlayerEntryPoint abstraction
import com.fishit.player.playback.domain.PlayerEntryPoint

class PlayMediaUseCase @Inject constructor(
    private val playerEntry: PlayerEntryPoint,  // Domain interface!
) { ... }
```

### Violation 4: ViewModel in Domain

```kotlin
// ❌ WRONG (in core/home-domain/)
@HiltViewModel
class HomeViewModel : ViewModel() { ... }

// ✅ CORRECT: ViewModels belong in feature/*
// feature/home/HomeViewModel.kt
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHomeContentUseCase: GetHomeContentUseCase,
) : ViewModel() { ... }
```

---

**End of PLATIN Instructions for core/*-domain**
