# v2 Naming Glossary & Module Taxonomy

**Version:** 2.0  
**Date:** 2025-12-11  
**Status:** Authoritative Reference (Binding Contract)

> **⚠️ This document is authoritative.** All code, documentation, and agent behavior must conform to the vocabulary and naming conventions defined here. Violations must be flagged and corrected.

---

## 1. Vocabulary Definitions

This section defines the canonical terms used in the v2 codebase. Use these definitions consistently in code, documentation, and discussions.

### 1.1 Core Terms

| Term | Definition | Scope | Examples |
|------|------------|-------|----------|
| **AppFeature** | A user-visible product capability exposed in the app UI. Lives in `feature/*` modules only. | User-facing | Home, Library, Settings, Detail, TelegramMedia, Audiobooks |
| **AppShell** | The application shell: entry point, main activity, navigation host and global theme setup for v2. Lives in `app-v2` (excluding feature-specific code). | AppShell | `FishItV2Application`, `MainActivity`, `AppNavHost`, `Theme`, `Type`, `AppFeatureRegistry` |
| **FeatureId** | A unique identifier in the feature system. Used for both AppFeatures and PipelineCapabilities. Defined as `@JvmInline value class FeatureId(val id: String)` in `core/feature-api`. | CoreModel | `FeatureId("ui.screen.home")`, `FeatureId("telegram.full_history")`, `FeatureId("xtream.live")` |
| **FeatureProvider** | Interface that registers features with the system. Both AppFeatures and PipelineCapabilities implement this interface. | CoreModel | `TelegramFullHistoryCapabilityProvider`, `HomeFeatureProvider` |
| **FeatureRegistry** | Central registry that collects all `FeatureProvider` implementations (both AppFeatures and PipelineCapabilities), provides lookup for `FeatureId` → provider instances. Interface defined in `:core:feature-api`, implementation `AppFeatureRegistry` in `app-v2`. | CoreModel | `AppFeatureRegistry` in `app-v2` |
| **FeatureScope** | Enum defining feature lifecycle scope: `APP`, `PIPELINE`, `PLAYER_SESSION`, `UI_SCREEN`, `REQUEST`. | CoreModel | `FeatureScope.PIPELINE` for capabilities |
| **FeatureOwner** | Data class holding module name and optional team for ownership attribution. | CoreModel | `FeatureOwner(moduleName = "pipeline:telegram")` |

### 1.2 Pipeline Terms

| Term | Definition | Scope | Examples |
|------|------------|-------|----------|
| **Pipeline** | A module that ingests content from an external source and produces `RawMediaMetadata`. Lives in `pipeline/*`. | Pipeline | `pipeline/telegram`, `pipeline/xtream`, `pipeline/io`, `pipeline/audiobook` |
| **PipelineCapability** | A technical capability provided by a pipeline. Named `*CapabilityProvider` and lives in `pipeline/*/capability/`. | PipelineCapability | `TelegramFullHistoryCapabilityProvider`, `TelegramLazyThumbnailsCapabilityProvider` |
| **PipelineIdTag** | Enum identifying the pipeline source: `TELEGRAM`, `XTREAM`, `IO`, `AUDIOBOOK`. | CoreModel | Used in `SourceKey`, `RawMediaMetadata`, etc. |
| **RawMediaMetadata** | The canonical data class produced by all pipelines. Contains unprocessed metadata before normalization. | CoreModel | Defined in `core/model` |

### 1.3 Metadata Terms

| Term | Definition | Scope | Examples |
|------|------------|-------|----------|
| **NormalizedMediaMetadata** | Normalized, pipeline-agnostic metadata describing a media item (canonicalTitle, year, season, episode, tmdbId, externalIds, images) after enrichment. Produced by the normalizer from `RawMediaMetadata`. | CoreModel | `NormalizedMediaMetadata` in `core/model` |
| **NormalizedMedia** | Aggregated normalized media object that contains title, year, mediaType, globalId, plus multiple playback `variants` from different sources. Represents a single logical media item with cross-pipeline deduplication. | CoreModel | `NormalizedMedia` in `core/model` |
| **MetadataNormalizer** | Service that transforms `RawMediaMetadata` into normalized representations (`NormalizedMediaMetadata` and/or `NormalizedMedia`) using TMDB and other heuristics. Lives in `core/metadata-normalizer`. | MetadataNormalizer | `MediaMetadataNormalizer`, `TmdbMetadataResolver` |
| **SceneNameParser** | Parses scene release names to extract title, year, quality, etc. | MetadataNormalizer | `RegexSceneNameParser` |

### 1.4 Infrastructure Terms

| Term | Definition | Scope | Examples |
|------|------------|-------|----------|
| **Transport** | Network/file access layer. Lives in `infra/transport-*`. | InfraTransport | `TelegramTransportClient`, `XtreamApiClient` |
| **Data** | Persistence and repository layer. Lives in `infra/data-*`. | InfraData | `TelegramContentRepository`, `XtreamCatalogRepository` |
| **Tooling** | Development and debugging utilities. Lives in `infra/tooling`. | Tooling | Debug services, test utilities |
| **Logging** | Unified logging infrastructure. Lives in `infra/logging`. | Logging | `UnifiedLog`, `UnifiedLogInitializer` |

### 1.5 Player Terms

| Term | Definition | Scope | Examples |
|------|------------|-------|----------|
| **Player** | The internal player engine (SIP). Lives in `player/*`. | Player | `InternalPlayerSession`, `InternalPlayerState` |
| **Playback** | Playback domain logic and source factories. Lives in `playback/*`. | Playback | `PlaybackSourceFactory`, `TelegramFileDataSource` |
| **PlaybackContext** | The data class passed to the player containing all playback information. | Player | Defined in `core/player-model` |

---

## 2. Module Taxonomy

### 2.1 Module Hierarchy

```text
app-v2/                          # Application entry point
├── feature/                     # Feature registration and DI
├── navigation/                  # NavHost
└── ui/                          # Theme, debug screens

core/                            # Shared core modules (pure Kotlin/Android)
├── app-startup/                 # Startup configuration
├── catalog-sync/                # Catalog sync contracts
├── feature-api/                 # FeatureId, FeatureProvider, FeatureRegistry
├── firebase/                    # Firebase integration
├── metadata-normalizer/         # Normalizer, TMDB resolver
├── model/                       # Canonical data classes
├── persistence/                 # ObjectBox entities and setup
├── player-model/                # PlaybackContext, PlaybackState, PlaybackError
└── ui-imaging/                  # Coil image loading

feature/                         # User-facing feature modules
├── audiobooks/                  # Audiobooks feature
├── detail/                      # Detail screen
├── home/                        # Home screen
├── library/                     # Library screen
├── live/                        # Live TV screen
├── settings/                    # Settings
└── telegram-media/              # Telegram media browser

pipeline/                        # Content ingestion pipelines
├── audiobook/                   # Audiobook pipeline
├── io/                          # Local file/IO pipeline
├── telegram/                    # Telegram pipeline
│   ├── adapter/
│   ├── capability/              # <-- PipelineCapability providers
│   ├── catalog/
│   ├── debug/
│   ├── mapper/
│   └── model/
└── xtream/                      # Xtream pipeline
    ├── adapter/
    ├── catalog/
    ├── debug/
    ├── mapper/                  # <-- Mapping DTOs to RawMediaMetadata
    └── model/

# Note: Xtream uses the same structural pattern as Telegram:
# - catalog/ for catalog orchestration and IO
# - mapper/ for mapping DTOs into RawMediaMetadata

infra/                           # Infrastructure modules
├── data-telegram/               # Telegram persistence
├── data-xtream/                 # Xtream persistence
├── logging/                     # Unified logging
├── tooling/                     # Dev tools
├── transport-telegram/          # TDLib transport
└── transport-xtream/            # Xtream HTTP transport

playback/                        # Playback domain
├── domain/                      # Interfaces and policies
├── telegram/                    # Telegram DataSource
└── xtream/                      # Xtream source factory

player/                          # Internal player (SIP)
├── internal/                    # InternalPlayerSession, controls, UI
└── miniplayer/                  # MiniPlayer manager and overlay
```

### 2.2 Module Dependencies

```text
              ┌────────────────┐
              │    app-v2      │
              └───────┬────────┘
                      │
         ┌────────────┼────────────┐
         │            │            │
         ▼            ▼            ▼
    ┌─────────┐  ┌─────────┐  ┌─────────┐
    │feature/*│  │ player/*│  │playback/*│
    └────┬────┘  └────┬────┘  └────┬────┘
         │            │            │
         └────────────┼────────────┘
                      │
         ┌────────────┼────────────┐
         │            │            │
         ▼            ▼            ▼
    ┌─────────┐  ┌─────────┐  ┌─────────┐
    │pipeline/*│ │ infra/*  │ │  core/*  │
    └─────────┘  └─────────┘  └─────────┘
```

---

## 3. Pipeline Capability Overview

### 3.1 Telegram Pipeline Capabilities

| Capability | FeatureId | Description | Status |
|------------|-----------|-------------|--------|
| Full History | `telegram.full_history` | Download complete chat history | ✅ Implemented |
| Lazy Thumbnails | `telegram.lazy_thumbnails` | Load thumbnails on demand | ✅ Implemented |

### 3.2 Xtream Pipeline Capabilities

| Capability | FeatureId | Description | Status |
|------------|-----------|-------------|--------|
| *TBD* | | Xtream capabilities to be migrated | 🚧 Planned |

### 3.3 IO Pipeline Capabilities

| Capability | FeatureId | Description | Status |
|------------|-----------|-------------|--------|
| *TBD* | | IO capabilities to be migrated | 🚧 Planned |

---

## 4. App Feature Overview

### 4.1 Registered App Features

| Feature | Module | Description | Status |
|---------|--------|-------------|--------|
| Home | `feature/home` | Main landing screen | 🚧 Stub |
| Library | `feature/library` | User library | 🚧 Stub |
| Live | `feature/live` | Live TV | 🚧 Stub |
| Detail | `feature/detail` | Media detail screen | ✅ Active |
| Settings | `feature/settings` | App settings | 🚧 Stub |
| Telegram Media | `feature/telegram-media` | Telegram media browser | 🚧 Stub |
| Audiobooks | `feature/audiobooks` | Audiobook player | 🚧 Stub |

---

## 5. Naming Conventions

### 5.1 Package Naming

| Module Type | Package Pattern | Example |
|-------------|-----------------|---------|
| Core | `com.fishit.player.core.<module>` | `com.fishit.player.core.model` |
| Feature (App) | `com.fishit.player.feature.<name>` | `com.fishit.player.feature.detail` |
| Pipeline | `com.fishit.player.pipeline.<name>` | `com.fishit.player.pipeline.telegram` |
| Pipeline Capability | `com.fishit.player.pipeline.<name>.capability` | `com.fishit.player.pipeline.telegram.capability` |
| Infrastructure | `com.fishit.player.infra.<layer>.<name>` | `com.fishit.player.infra.transport.telegram` |
| Playback | `com.fishit.player.playback.<name>` | `com.fishit.player.playback.telegram` |
| Player | `com.fishit.player.player.<name>` | `com.fishit.player.player.internal` |

### 5.2 Class Naming

| Type | Pattern | Example |
|------|---------|---------|
| Pipeline Capability Provider | `<Pipeline><Capability>CapabilityProvider` | `TelegramFullHistoryCapabilityProvider` |
| App Feature Provider | `<Feature>FeatureProvider` | `HomeFeatureProvider` |
| Repository Interface | `<Entity>Repository` | `TelegramContentRepository` |
| Repository Implementation | `<Backing><Entity>Repository` | `ObxTelegramContentRepository` |
| Use Case | `<Action><Entity>UseCase` | `PlayItemUseCase` |
| ViewModel | `<Screen>ViewModel` | `UnifiedDetailViewModel` |
| DataSource | `<Source>DataSource` | `TelegramFileDataSource` |

### 5.3 Forbidden Patterns

| Pattern | Location | Reason |
|---------|----------|--------|
| `*FeatureProvider` | `pipeline/*` | Use `*CapabilityProvider` for pipeline modules |
| `feature/` package | `pipeline/*` | Use `capability/` package for pipeline modules |
| `com.chris.m3usuite` | anywhere except `legacy/` | v1 namespace forbidden in v2 |

---

## 6. Quality Gates & Enforcement

The naming conventions from this glossary are enforced via static analysis tools:

| Tool | Purpose | Configuration |
|------|---------|---------------|
| **Detekt** | Kotlin static analysis, naming pattern enforcement | `detekt-config.yml` |
| **ktlint** | Code style and import organization | Gradle plugin |
| **CI/GitHub Actions** | Automated checks on PRs | `.github/workflows/` |

### 6.1 Enforced Rules

1. **ForbiddenImport**: `com.chris.m3usuite.*` outside `legacy/`
2. **Naming Pattern**: `*FeatureProvider` forbidden in `pipeline/*` packages
3. **Package Pattern**: `feature/` package forbidden in `pipeline/*` modules
4. **Category Consistency**: All categories in NAMING_INVENTORY must match glossary terms

### 6.2 Violation Handling

When a naming violation is detected:

1. Agent must **immediately flag** the violation to the user
2. Agent must **not proceed** with changes that would worsen the violation
3. Agent must **propose a fix** aligned with this glossary

---

## 7. Reference Documents

- [NAMING_INVENTORY_v2.md](NAMING_INVENTORY_v2.md) - Complete file-to-vocabulary mapping
- [FEATURE_SYSTEM_TARGET_MODEL.md](architecture/FEATURE_SYSTEM_TARGET_MODEL.md) - Feature system architecture
- [Zielbild.md](Zielbild.md) - v2 architecture target state
- [AGENTS.md](../../AGENTS.md) - Agent rules and constraints (includes binding naming contract)
