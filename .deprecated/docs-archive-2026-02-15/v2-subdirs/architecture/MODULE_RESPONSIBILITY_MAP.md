# Module Responsibility Map – FishIT-Player v2

> **Branch:** `architecture/v2-bootstrap`
> **Generated:** 2024-12-30
> **Authority:** This document derives from module READMEs and `/contracts/GLOSSARY_v2_naming_and_modules.md`

---

## Table of Contents

1. [Layer Overview](#layer-overview)
2. [App Layer](#app-layer)
3. [Feature Layer](#feature-layer)
4. [Core Layer](#core-layer)
5. [Playback Layer](#playback-layer)
6. [Player Layer](#player-layer)
7. [Pipeline Layer](#pipeline-layer)
8. [Infrastructure Layer](#infrastructure-layer)
9. [Cross-Cutting Concerns](#cross-cutting-concerns)
10. [Quick Reference Matrix](#quick-reference-matrix)

---

## Layer Overview

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│  UI Layer                                                                   │
│  ┌─────────┐ ┌─────────┐ ┌────────┐ ┌────────┐ ┌────────────────┐           │
│  │  home   │ │ library │ │  live  │ │ detail │ │ telegram-media │ ...       │
│  └─────────┘ └─────────┘ └────────┘ └────────┘ └────────────────┘           │
└─────────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│  Domain Layer (playback:domain, core:catalog-sync)                          │
│    - Use Cases: Play, Sync, Search                                          │
│    - Catalog orchestration                                                  │
│    - Business rules                                                         │
└─────────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│  Data Layer (infra:data-*)                                                  │
│    - Repositories consume RawMediaMetadata                                  │
│    - ObjectBox persistence                                                  │
│    - Flow-based observation                                                 │
└─────────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│  Pipeline Layer (pipeline:*)                                                │
│    - Transform transport DTOs → RawMediaMetadata                            │
│    - Catalog events (add/update/remove)                                     │
│    - No network, no persistence                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│  Transport Layer (infra:transport-*)                                        │
│    - Raw API access (TDLib, HTTP)                                           │
│    - Connection, auth, streaming                                            │
│    - Hides wire-level details                                               │
└─────────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│  Core Layer (core:model, core:player-model)                                 │
│    - Canonical DTOs: RawMediaMetadata, PlaybackContext                      │
│    - Enums: MediaType, SourceType, PlaybackState                            │
│    - Zero dependencies                                                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## App Layer

### `app-v2`

| Aspect | Details |
|--------|---------|
| **Purpose** | Application entry point, DI root, navigation host |
| **Package** | `com.fishit.player` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| Main Activity / Shell | Business logic |
| Hilt Application setup | Transport implementations |
| Navigation graph | Database definitions |
| Global theme application | Pipeline processing |
| Startup initialization | Playback state management |
| Module wiring via DI | Source-specific code |

**Key Classes:** `FishItPlayerApp`, `MainActivity`, `NavGraph`, DI modules

---

## Feature Layer

### `feature:home`

| Aspect | Details |
|--------|---------|
| **Purpose** | Home screen with aggregated content rows |
| **Package** | `com.fishit.player.feature.home` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| Home screen composables | Raw API calls |
| ViewModel for home state | Pipeline processing |
| Hero carousel logic | Database entities |
| Content row aggregation | Playback engine control |
| Navigation to detail | Transport layer access |

### `feature:library`

| Aspect | Details |
|--------|---------|
| **Purpose** | VOD/Series library browsing |
| **Package** | `com.fishit.player.feature.library` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| Library grid/list UI | Transport clients |
| Category filtering UI | Database access (use repositories) |
| Search UI | Pipeline DTOs |
| ViewModel for library | Raw TDLib/Xtream calls |
| Navigation to detail | Media playback |

### `feature:live`

| Aspect | Details |
|--------|---------|
| **Purpose** | Live TV channel browsing and EPG |
| **Package** | `com.fishit.player.feature.live` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| Live TV grid UI | Transport implementations |
| EPG timeline UI | Xtream API calls |
| Channel filtering | Database entities |
| ViewModel for live content | Playback session management |
| Navigation to player | Pipeline processing |

### `feature:detail`

| Aspect | Details |
|--------|---------|
| **Purpose** | Media detail screen with metadata, actions |
| **Package** | `com.fishit.player.feature.detail` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| Detail scaffold UI | Transport layer |
| MediaActionBar | Pipeline processing |
| Series episode list | Raw database access |
| MetaChips display | Player internal state |
| Cast/crew display | TMDB API calls (use normalizer) |

### `feature:telegram-media`

| Aspect | Details |
|--------|---------|
| **Purpose** | Telegram chat/media browsing UI |
| **Package** | `com.fishit.player.feature.telegrammedia` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| Chat list UI | TDLib direct access |
| Media grid/list UI | Transport internals |
| Telegram navigation | Pipeline processing |
| ViewModel for Telegram content | Auth state management |
| Folder/category display | File download logic |

### `feature:audiobooks`

| Aspect | Details |
|--------|---------|
| **Purpose** | Audiobook browsing and playback UI |
| **Package** | `com.fishit.player.feature.audiobooks` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| Audiobook library UI | File system scanning |
| Chapter navigation UI | Audio processing |
| Sleep timer UI | Pipeline internals |
| ViewModel for audiobooks | Transport layer |
| Playback position display | Database entities |

### `feature:settings`

| Aspect | Details |
|--------|---------|
| **Purpose** | App settings, account management, diagnostics |
| **Package** | `com.fishit.player.feature.settings` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| Settings screens | Transport implementations |
| Account linking UI | Pipeline processing |
| Theme selection | Database migrations |
| Log viewer UI | Raw DataStore access |
| Cache management UI | Transport auth flows |
| Source activation UI | Worker implementations |

### `feature:onboarding`

| Aspect | Details |
|--------|---------|
| **Purpose** | First-run setup, source onboarding |
| **Package** | `com.fishit.player.feature.onboarding` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| Onboarding flow screens | Transport auth logic |
| Source selection UI | Pipeline processing |
| Tutorial screens | Database setup |
| ViewModel for onboarding | Raw API calls |
| Xtream login form | Auth state persistence |
| Telegram connect UI | File system access |

---

## Core Layer

### `core:model`

| Aspect | Details |
|--------|---------|
| **Purpose** | Central, source-agnostic data models |
| **Package** | `com.fishit.player.core.model` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `RawMediaMetadata` data class | Source-specific logic |
| `MediaType` enum | Network calls |
| `SourceType` enum | Database code |
| `RawMediaKind` enum | Playback logic |
| `ImageRef` for images | UI imports |
| `ExternalIds` (TMDB/IMDB) | Business heuristics |

**Hard Rule:** Kotlin stdlib only – zero external dependencies.

### `core:player-model`

| Aspect | Details |
|--------|---------|
| **Purpose** | Player primitives (source-agnostic) |
| **Package** | `com.fishit.player.core.playermodel` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `PlaybackContext` | Media3 imports |
| `PlaybackState` enum | Source-specific logic |
| `PlaybackError` | UI framework imports |
| `SourceType` (playback) | Network/persistence |
| Pure Kotlin types | Android SDK (except basics) |

**Hard Rule:** Bottom of player stack. All other player modules depend on this.

### `core:feature-api`

| Aspect | Details |
|--------|---------|
| **Purpose** | Feature/capability registration contracts |
| **Package** | `com.fishit.player.core.featureapi` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `FeatureId` identifiers | Feature implementations |
| `FeatureRegistry` interface | UI code |
| `CapabilityProvider` contracts | Network calls |
| Feature metadata types | Database access |
| Registration extensions | Source-specific logic |

### `core:source-activation-api`

| Aspect | Details |
|--------|---------|
| **Purpose** | Source activation state contracts |
| **Package** | `com.fishit.player.core.sourceactivation` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `SourceId` enum | Implementation classes |
| `SourceActivationState` sealed | Transport dependencies |
| `SourceActivationStore` interface | UI/Android framework |
| `SourceActivationSnapshot` | Persistence code |
| `SourceErrorReason` enum | Business logic |

**Architectural Purpose:** Breaks `catalog-sync ↔ data-xtream` circular dependency.

### `core:persistence`

| Aspect | Details |
|--------|---------|
| **Purpose** | ObjectBox setup, BoxStore, DataStore |
| **Package** | `com.fishit.player.core.persistence` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `BoxStore` provider | Business logic |
| ObjectBox converters | Transport code |
| DataStore definitions | Pipeline processing |
| Migration helpers | UI code |
| Transaction utilities | Source-specific logic |

### `core:metadata-normalizer`

| Aspect | Details |
|--------|---------|
| **Purpose** | Normalize raw metadata → enriched domain models |
| **Package** | `com.fishit.player.core.metadatanormalizer` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `MetadataNormalizer` interface | Transport imports |
| Title cleanup heuristics | Pipeline imports |
| Season/episode extraction | Repository access |
| TMDB/IMDB lookup (via provider) | Playback logic |
| Adult/family content detection | UI imports |
| Language/version detection | Source-specific logic |
| `DomainMediaItem` output | Raw HTTP calls |

### `core:catalog-sync`

| Aspect | Details |
|--------|---------|
| **Purpose** | Orchestrate pipeline → data persistence |
| **Package** | `com.fishit.player.core.catalogsync` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `CatalogSyncService` | Direct network calls |
| Event consumption from pipelines | TDLib/Xtream API calls |
| Batch upsert orchestration | UI updates |
| Sync status emission | ObjectBox entity definitions |
| Progress tracking | Transport layer access |
| Optional normalization pass | Pipeline internals |

### `core:firebase`

| Aspect | Details |
|--------|---------|
| **Purpose** | Firebase/Crashlytics integration |
| **Package** | `com.fishit.player.core.firebase` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| Crashlytics setup | Business logic |
| Analytics events | Transport code |
| Remote Config | Pipeline processing |
| Cloud Messaging setup | UI code |
| Performance monitoring | Database access |

### `core:ui-imaging`

| Aspect | Details |
|--------|---------|
| **Purpose** | Image loading abstractions |
| **Package** | `com.fishit.player.core.uiimaging` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `ImageRef` consumers | Coil/Glide implementation |
| Image composables | Transport code |
| Placeholder handling | Database access |
| Error image display | Pipeline processing |
| Image URL building | Business logic |

### `core:ui-theme`

| Aspect | Details |
|--------|---------|
| **Purpose** | Theme tokens, typography, colors |
| **Package** | `com.fishit.player.core.uitheme` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `FishTheme` composable | Screen implementations |
| Color tokens | Business logic |
| Typography scale | Transport code |
| Shape definitions | Database access |
| Dark/light variants | Any non-UI code |

### `core:ui-layout`

| Aspect | Details |
|--------|---------|
| **Purpose** | Reusable UI components (FishTile, FishRow, etc.) |
| **Package** | `com.fishit.player.core.uilayout` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `FishTile` composable | Business logic |
| `FishRow` composable | Transport code |
| `FishHeader` composable | Database access |
| TV focus handling components | Pipeline processing |
| Grid/list layouts | Source-specific UI |

### `core:app-startup`

| Aspect | Details |
|--------|---------|
| **Purpose** | App initialization, Hilt entry points |
| **Package** | `com.fishit.player.core.appstartup` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| Startup Initializers | Business logic |
| Hilt entry point setup | Feature UI |
| Early transport init | Pipeline processing |
| Logging bootstrap | Database migrations |
| WorkManager init | Sync orchestration |

---

## Playback Layer

### `playback:domain`

| Aspect | Details |
|--------|---------|
| **Purpose** | App-level playback use cases |
| **Package** | `com.fishit.player.playback.domain` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `PlayUseCase` | Transport imports |
| `SyncCatalogUseCase` | Pipeline DTOs |
| `SearchUseCase` | UI framework imports |
| `GetMediaUseCase` | Direct network calls |
| Catalog sync orchestration | Direct persistence access |
| Playback orchestration | TDLib/Xtream specifics |

### `playback:telegram`

| Aspect | Details |
|--------|---------|
| **Purpose** | Telegram media playback source |
| **Package** | `com.fishit.player.playback.telegram` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `TelegramPlaybackSourceFactory` | Pipeline DTOs |
| `TelegramFileDataSource` | Repository access |
| `PlaybackContext` creation | Domain heuristics |
| File download primitives (via Transport) | UI imports |
| Media3 DataSource impl | Direct TDLib calls |

### `playback:xtream`

| Aspect | Details |
|--------|---------|
| **Purpose** | Xtream media playback source |
| **Package** | `com.fishit.player.playback.xtream` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `XtreamPlaybackSourceFactory` | Pipeline DTOs |
| `XtreamStreamDataSource` | Repository access |
| URL building for streams | Domain heuristics |
| `PlaybackContext` creation | UI imports |
| Media3 DataSource impl | Direct HTTP calls |

---

## Player Layer

### `player:internal`

| Aspect | Details |
|--------|---------|
| **Purpose** | Internal Player (SIP) core engine |
| **Package** | `com.fishit.player.player.internal` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `InternalPlayerSession` | Source-specific logic |
| `InternalPlayerState` | Pipeline imports |
| `PlaybackSourceResolver` | Data layer access |
| Subtitle selection policy | UI composables |
| Live playback handling | Transport implementations |
| Media3 ExoPlayer integration | Business rules |

**Hard Rule:** Player is source-agnostic. Uses `PlaybackSourceFactory` sets via `@Multibinds`.

### `player:ui`

| Aspect | Details |
|--------|---------|
| **Purpose** | Player chrome, controls, overlays |
| **Package** | `com.fishit.player.player.ui` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| Player controls UI | Playback engine logic |
| Seek bar composables | Source resolution |
| Subtitle display | Transport access |
| Settings dialogs | Pipeline processing |
| Gesture handlers | Database access |
| Full-screen chrome | Business rules |

### `player:ui-api`

| Aspect | Details |
|--------|---------|
| **Purpose** | Player UI contracts/interfaces |
| **Package** | `com.fishit.player.player.uiapi` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| Player UI interfaces | Implementations |
| State observation contracts | Transport code |
| Command definitions | Pipeline code |
| Navigation contracts | Database code |
| Error display contracts | Source-specific logic |

### `player:miniplayer`

| Aspect | Details |
|--------|---------|
| **Purpose** | Mini-player / PiP functionality |
| **Package** | `com.fishit.player.player.miniplayer` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| Mini-player UI | Source-specific logic |
| PiP transition handling | Transport access |
| Background playback UI | Pipeline processing |
| Mini-player state | Database access |
| Expand/collapse logic | Business rules |

### `player:nextlib-codecs`

| Aspect | Details |
|--------|---------|
| **Purpose** | FFmpeg/codec extensions for Media3 |
| **Package** | `com.fishit.player.player.nextlibcodecs` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| FFmpeg integration | Any app logic |
| Codec factories | UI code |
| Decoder selection | Transport code |
| Native library loading | Database access |
| Format support checks | Pipeline processing |

**Hard Rule:** Pure codec functionality. No business logic.

---

## Pipeline Layer

### `pipeline:telegram`

| Aspect | Details |
|--------|---------|
| **Purpose** | Transform `TgMessage` → `RawMediaMetadata` |
| **Package** | `com.fishit.player.pipeline.telegram` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `TelegramCatalogPipeline` | Direct TDLib calls |
| `TelegramCatalogEvent` emission | Network/file downloads |
| `TelegramMediaItem` (internal) | Repository access |
| `toRawMediaMetadata()` mapping | Playback logic |
| Structured Bundle grouping | UI imports |
| `TelegramMessageCursor` | Normalization heuristics |
| Pass-through of structured TMDB-IDs | Persistence entities |

**Forbidden:** TMDB lookups (only pass-through allowed)

### `pipeline:xtream`

| Aspect | Details |
|--------|---------|
| **Purpose** | Transform Xtream API responses → `RawMediaMetadata` |
| **Package** | `com.fishit.player.pipeline.xtream` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `XtreamCatalogPipeline` | Direct HTTP calls |
| `XtreamCatalogEvent` emission | `XtreamApiClient` usage |
| `XtreamVodItem` (internal) | Repository access |
| `XtreamSeriesItem` (internal) | Playback logic |
| `XtreamChannel` (internal) | UI imports |
| `toRawMediaMetadata()` mapping | Normalization heuristics |
| `XtreamCatalogSource` | Persistence entities |

### `pipeline:io`

| Aspect | Details |
|--------|---------|
| **Purpose** | Local file discovery → `RawMediaMetadata` |
| **Package** | `com.fishit.player.pipeline.io` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| File scanner interface | Direct SAF access |
| Local media discovery | UI code |
| `toRawMediaMetadata()` mapping | Network calls |
| File metadata extraction | Database access |
| Directory watching | Playback logic |

### `pipeline:audiobook`

| Aspect | Details |
|--------|---------|
| **Purpose** | Audiobook file processing → `RawMediaMetadata` |
| **Package** | `com.fishit.player.pipeline.audiobook` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| Audiobook scanner | UI code |
| Chapter extraction | Network calls |
| `toRawMediaMetadata()` mapping | Database access |
| M4B/MP3 metadata parsing | Playback logic |
| Series/book grouping | Transport layer |

---

## Infrastructure Layer

### `infra:logging`

| Aspect | Details |
|--------|---------|
| **Purpose** | Unified logging facade |
| **Package** | `com.fishit.player.infra.logging` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `UnifiedLog` facade | Any app logic |
| Log categories | UI code |
| Ring buffer storage | Transport specifics |
| Structured event logging | Pipeline processing |
| Performance monitoring | Database entities |
| Async processing | Business rules |

**Hard Rule:** Zero leaf dependency – imported by all modules.

### `infra:cache`

| Aspect | Details |
|--------|---------|
| **Purpose** | Cache directory management |
| **Package** | `com.fishit.player.infra.cache` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| Cache directory paths | Business logic |
| Cache cleanup actions | UI code |
| Size calculations | Transport specifics |
| Clear operations | Pipeline processing |
| Cache policy | Database access |

### `infra:tooling`

| Aspect | Details |
|--------|---------|
| **Purpose** | Debug/dev tools, test utilities |
| **Package** | `com.fishit.player.infra.tooling` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| Debug overlays | Production logic |
| Test fixtures | UI screens |
| Mock providers | Transport implementations |
| Dev-only features | Pipeline processing |
| Inspection tools | Database migrations |

### `infra:transport-telegram`

| Aspect | Details |
|--------|---------|
| **Purpose** | TDLib wrapper, Telegram transport |
| **Package** | `com.fishit.player.infra.transport.telegram` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `DefaultTelegramClient` impl | Pipeline processing |
| `TelegramAuthClient` | UI code |
| `TelegramHistoryClient` | Repository access |
| `TelegramFileClient` | Business rules |
| `TelegramThumbFetcher` | Playback logic |
| `TgMessage`, `TgContent` DTOs | Player imports |
| Auth state machine | Normalization |

**SSOT:** Single `TdlClient` instance per process.

### `infra:transport-xtream`

| Aspect | Details |
|--------|---------|
| **Purpose** | Xtream Codes API client |
| **Package** | `com.fishit.player.infra.transport.xtream` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `XtreamApiClient` | Pipeline processing |
| HTTP client setup | UI code |
| Auth/session handling | Repository access |
| URL building | Business rules |
| Rate limiting | Playback logic |
| Connection pooling | Player imports |
| API response DTOs | Normalization |

### `infra:data-telegram`

| Aspect | Details |
|--------|---------|
| **Purpose** | Telegram data persistence |
| **Package** | `com.fishit.player.infra.data.telegram` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `TelegramContentRepository` | Pipeline DTOs |
| ObjectBox Telegram entities | Transport internals |
| Flow-based queries | UI code |
| Upsert/delete operations | Playback logic |
| Sync status tracking | Business rules |

### `infra:data-xtream`

| Aspect | Details |
|--------|---------|
| **Purpose** | Xtream data persistence |
| **Package** | `com.fishit.player.infra.data.xtream` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `XtreamCatalogRepository` | Pipeline DTOs |
| `XtreamLiveRepository` | Transport internals |
| ObjectBox Xtream entities | UI code |
| Flow-based queries | Playback logic |
| Credential storage | Business rules |
| Account management | Pipeline processing |

### `infra:data-home`

| Aspect | Details |
|--------|---------|
| **Purpose** | Home screen data aggregation |
| **Package** | `com.fishit.player.infra.data.home` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `HomeContentRepository` | Pipeline DTOs |
| Cross-source aggregation | Transport internals |
| Recent/continue watching | UI code |
| Recommended content | Playback logic |
| Flow-based queries | Business rules |

### `infra:imaging`

| Aspect | Details |
|--------|---------|
| **Purpose** | Coil ImageLoader setup |
| **Package** | `com.fishit.player.infra.imaging` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `ImageLoader` singleton | Business logic |
| Coil fetchers | Pipeline processing |
| Cache configuration | UI composables |
| Telegram thumb fetcher | Transport implementations |
| Placeholder setup | Database access |

### `infra:work`

| Aspect | Details |
|--------|---------|
| **Purpose** | WorkManager workers, background sync |
| **Package** | `com.fishit.player.infra.work` |

| ✅ Owns | ❌ Must Not Own |
|---------|-----------------|
| `SourceActivationStore` impl | UI code |
| Sync workers | Transport implementations |
| Worker scheduling | Pipeline internals |
| Periodic sync setup | Database entities |
| Constraint handling | Business rules |

---

## Cross-Cutting Concerns

### Logging

All modules import `infra:logging` for structured logging via `UnifiedLog`:

```kotlin
// Correct
UnifiedLog.d("MyModule", "Event happened", mapOf("key" to "value"))

// Forbidden
Log.d("MyModule", "Event happened")  // Raw Android Log
println("Event happened")            // No structured logging
```

### Dependency Injection

All modules use Hilt with proper scoping:

| Scope | Usage |
|-------|-------|
| `@Singleton` | App-wide singletons (BoxStore, TdlClient) |
| `@ViewModelScoped` | ViewModel-lifetime objects |
| `@ActivityScoped` | Activity-lifetime objects |

### Error Handling

All modules emit errors via sealed classes:

```kotlin
sealed interface SyncResult {
    data class Success(val count: Int) : SyncResult
    data class Error(val message: String, val cause: Throwable?) : SyncResult
}
```

---

## Quick Reference Matrix

| Module | May Import | Must Not Import |
|--------|------------|-----------------|
| `core:model` | Kotlin stdlib only | Everything else |
| `core:player-model` | Kotlin stdlib only | Everything else |
| `pipeline:*` | `core:model`, `transport-*` | `data/*`, `playback/*`, `feature/*` |
| `playback:*` | `core:model`, `transport-*` | `pipeline/*` DTOs, `data/*` |
| `player:internal` | `core:*`, `playback:*` | `pipeline/*`, `transport/*` directly |
| `feature:*` | `core:*`, `playback:domain`, `player:*` | `pipeline/*` DTOs, `transport/*` |
| `infra:data-*` | `core:model`, `core:persistence` | `pipeline/*` DTOs |

---

## Related Documents

- [DEPENDENCY_GRAPH.md](DEPENDENCY_GRAPH.md) – Visual dependency map
- [DEPENDENCY_GRAPH.dot](DEPENDENCY_GRAPH.dot) – Graphviz source
- [DEPENDENCY_GRAPH.mmd](DEPENDENCY_GRAPH.mmd) – Mermaid source
- [FEATURE_SYSTEM_TARGET_MODEL.md](FEATURE_SYSTEM_TARGET_MODEL.md) – Feature system design
- [TELEGRAM_TRANSPORT_SSOT.md](TELEGRAM_TRANSPORT_SSOT.md) – Telegram transport contracts
- `/contracts/GLOSSARY_v2_naming_and_modules.md` – Naming conventions
- `/contracts/MEDIA_NORMALIZATION_CONTRACT.md` – Normalization rules

---

*Generated from module READMEs and AGENTS.md contracts*
