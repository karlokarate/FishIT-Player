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

> **PLATIN STANDARD** - Domain use cases and business logic.
>
> **Purpose:** Domain layer containing use cases that orchestrate repositories and services.
> These modules contain application-level business logic.

---

## 🔴 ABSOLUTE HARD RULES

### 1. Use Cases Only
```kotlin
// ✅ ALLOWED
class GetHomeContentUseCase @Inject constructor(
    private val homeRepository: HomeContentRepository,
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(profileId: Long): HomeContent
}

class PlayMediaUseCase @Inject constructor(...)
class SyncCatalogUseCase @Inject constructor(...)
class SearchMediaUseCase @Inject constructor(...)

// ❌ FORBIDDEN
class HomeContentRepositoryImpl : HomeContentRepository { ... }
// Repository implementations → infra/data-* layer!
```

### 2. No Transport/Pipeline Dependencies
```kotlin
// ✅ ALLOWED
import com.fishit.player.core.model.*
import com.fishit.player.core.model.repository.*
import com.fishit.player.playback.domain.*
import kotlinx.coroutines.flow.Flow

// ❌ FORBIDDEN
import com.fishit.player.pipeline.*              // Pipeline DTOs
import com.fishit.player.infra.transport.*       // Transport
import org.drinkless.td.*                         // TDLib
import okhttp3.*                                  // HTTP
import io.objectbox.*                             // ObjectBox
```

### 3. No UI Dependencies
```kotlin
// ❌ FORBIDDEN
import androidx.compose.*
import android.view.*
import androidx.lifecycle.ViewModel  // ViewModels live in feature/*
```

---

## 📋 Use Case Patterns

### Standard Use Case
```kotlin
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
```

### Flow-based Use Case
```kotlin
class ObserveLibraryContentUseCase @Inject constructor(
    private val libraryRepository: LibraryContentRepository,
) {
    operator fun invoke(filter: LibraryFilter): Flow<List<MediaItem>> {
        return libraryRepository.observeContent(filter)
    }
}
```

### Orchestration Use Case
```kotlin
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

---

## 📋 Module-Specific Responsibilities

| Module | Use Cases |
|--------|-----------|
| `home-domain` | GetHomeContent, GetContinueWatching, GetRecentlyAdded |
| `library-domain` | GetLibraryContent, SearchLibrary, FilterByGenre |
| `live-domain` | GetLiveChannels, GetEpgNow, GetEpgSchedule |
| `detail-domain` | GetMediaDetail, GetRelatedMedia, GetEpisodes |
| `telegrammedia-domain` | GetTelegramChats, GetTelegramMedia, SyncTelegramChat |
| `onboarding-domain` | ValidateXtreamCredentials, SetupSource, CompleteOnboarding |

---

## 📐 Architecture Position

```
core/model (types)
      ↓
core/*-domain (use cases) ← YOU ARE HERE
      ↓
      ├── reads from: core/model/repository interfaces
      ├── uses: playback/domain
      └── consumed by: feature/* ViewModels
```

---

## ✅ PLATIN Checklist

- [ ] Only use cases and domain services
- [ ] No repository implementations
- [ ] No pipeline imports (TelegramMediaItem, XtreamVodItem)
- [ ] No transport imports (TDLib, OkHttp)
- [ ] No UI imports (Compose, View, ViewModel)
- [ ] No ObjectBox imports (entity details)
- [ ] Use cases are injectable via Hilt
- [ ] Flow return types for reactive data
- [ ] Proper error handling (Result<T> or sealed classes)

---

## 📚 Reference Documents

1. `/contracts/GLOSSARY_v2_naming_and_modules.md` - Domain terminology
2. `/AGENTS.md` - Section 4.5 (Layer Boundaries)
3. Clean Architecture principles
