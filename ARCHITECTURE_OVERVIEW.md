# FishIT-Player – Architecture Overview

> **This repository is in v2 rebuild mode.**
>
> - **V2 Architecture**: See [docs/v2/ARCHITECTURE_OVERVIEW_V2.md](docs/v2/ARCHITECTURE_OVERVIEW_V2.md)
> - **V1 Architecture (archived)**: See [legacy/docs/ARCHITECTURE_OVERVIEW_v1.md](legacy/docs/ARCHITECTURE_OVERVIEW_v1.md)

---

## Branch Status

- **Active branch**: `architecture/v2-bootstrap`
- **Default branch**: `architecture/v2-bootstrap`
- **V1 code location**: `legacy/v1-app/` (read-only, reference only)

---

## V2 Module Structure

The v2 rebuild uses the following module structure:

```text
/
├── app-v2/                   # V2 app shell, navigation, entry point
├── core/
│   ├── model/                # Canonical media models (RawMediaMetadata, MediaType, ImageRef)
│   ├── player-model/         # Player primitives (PlaybackContext, PlaybackState, SourceType)
│   ├── metadata-normalizer/  # Title parsing, TMDB resolution, SceneNameParser
│   ├── catalog-sync/         # Pipeline → Data sync orchestration
│   ├── feature-api/          # FeatureId, FeatureRegistry, FeatureProvider
│   ├── persistence/          # ObjectBox entities and repositories
│   ├── firebase/             # Firebase/Crashlytics integration
│   └── ui-imaging/           # Shared imaging utilities, Coil integration
├── infra/
│   ├── logging/              # UnifiedLog facade
│   ├── tooling/              # Build tooling and utilities
│   ├── transport-telegram/   # TelegramTransportClient, TdlibClientProvider
│   ├── transport-xtream/     # XtreamApiClient, XtreamUrlBuilder, XtreamDiscovery
│   ├── data-telegram/        # TelegramContentRepository, ObxTelegramContentRepository
│   └── data-xtream/          # XtreamCatalogRepository, XtreamLiveRepository
├── feature/
│   ├── home/                 # Home screen
│   ├── library/              # Library browsing
│   ├── live/                 # Live channels
│   ├── detail/               # Detail views
│   ├── telegram-media/       # Telegram media UI
│   ├── settings/             # Settings UI
│   └── audiobooks/           # Audiobook UI
├── player/
│   └── internal/             # Internal Player (SIP): InternalPlayerSession, PlaybackSourceResolver
├── playback/
│   ├── domain/               # Playback contracts: PlaybackSourceFactory, ResumeManager, KidsPlaybackGate
│   ├── telegram/             # TelegramPlaybackSourceFactoryImpl, TelegramFileDataSource
│   └── xtream/               # XtreamPlaybackSourceFactoryImpl
├── pipeline/
│   ├── telegram/             # TelegramCatalogPipeline, TelegramMessageCursor
│   ├── xtream/               # XtreamCatalogPipeline, XtreamCatalogMapper
│   ├── audiobook/            # Audiobook pipeline (stub)
│   └── io/                   # Local file pipeline (stub)
├── docs/
│   ├── v2/                   # V2 specifications and contracts
│   └── meta/                 # Build, quality, workspace docs
├── scripts/
│   ├── build/                # Build helpers
│   └── api-tests/            # API probe scripts
└── legacy/                   # V1 code and docs (read-only)
    ├── v1-app/               # Full v1 app module
    ├── docs/                 # V1 documentation
    └── gold/                 # Curated v1 patterns (36 patterns for v2 porting)
```

---

## Layer Architecture

The v2 architecture follows a strict layer hierarchy:

```
┌─────────────────────────────────────────────────────────────┐
│  UI Layer (feature/*, app-v2)                               │
│    - Compose screens, ViewModels, navigation                │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Domain Layer (playback/domain, core/catalog-sync)         │
│    - PlaybackSourceFactory, ResumeManager, KidsPlaybackGate │
│    - CatalogSyncService                                     │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Data Layer (infra/data-*, core/persistence)               │
│    - Repositories, ObjectBox entities                       │
│    - TelegramContentRepository, XtreamCatalogRepository     │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Pipeline Layer (pipeline/*)                                │
│    - TelegramCatalogPipeline, XtreamCatalogPipeline        │
│    - Produces RawMediaMetadata                             │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Transport Layer (infra/transport-*)                        │
│    - TelegramTransportClient, XtreamApiClient              │
│    - Raw TDLib/HTTP access                                  │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Model Layer (core/model, core/player-model)               │
│    - RawMediaMetadata, MediaType, ImageRef                 │
│    - PlaybackContext, PlaybackState, SourceType            │
└─────────────────────────────────────────────────────────────┘
```

---

## Key V2 Documents

| Document | Purpose |
|----------|---------|
| [V2_PORTAL.md](V2_PORTAL.md) | V2 entry point, links to all key docs |
| [AGENTS.md](AGENTS.md) | Agent rules for v2 development |
| [ROADMAP.md](ROADMAP.md) | V2 implementation phases |
| [CHANGELOG.md](CHANGELOG.md) | V2 changelog |
| [docs/v2/ARCHITECTURE_OVERVIEW_V2.md](docs/v2/ARCHITECTURE_OVERVIEW_V2.md) | Detailed v2 architecture |
| [docs/v2/CANONICAL_MEDIA_SYSTEM.md](docs/v2/CANONICAL_MEDIA_SYSTEM.md) | Canonical media model |
| [docs/v2/MEDIA_NORMALIZATION_CONTRACT.md](docs/v2/MEDIA_NORMALIZATION_CONTRACT.md) | Normalization rules |
| [docs/v2/LOGGING_CONTRACT_V2.md](docs/v2/LOGGING_CONTRACT_V2.md) | Logging contract |
| [docs/v2/internal-player/PLAYER_MIGRATION_STATUS.md](docs/v2/internal-player/PLAYER_MIGRATION_STATUS.md) | Player migration progress |

---

## Implementation Progress

| Layer | Module | Status |
|-------|--------|--------|
| **Core** | `core/model` | ✅ Complete |
| **Core** | `core/player-model` | ✅ Complete |
| **Core** | `core/feature-api` | ✅ Complete |
| **Core** | `core/metadata-normalizer` | ✅ Complete |
| **Core** | `core/catalog-sync` | ✅ Complete |
| **Core** | `core/persistence` | ✅ Complete |
| **Transport** | `infra/transport-telegram` | ✅ Complete |
| **Transport** | `infra/transport-xtream` | ✅ Complete |
| **Data** | `infra/data-telegram` | ✅ Complete |
| **Data** | `infra/data-xtream` | ✅ Complete |
| **Pipeline** | `pipeline/telegram` | ✅ Complete |
| **Pipeline** | `pipeline/xtream` | ✅ Complete |
| **Pipeline** | `pipeline/audiobook` | 🔲 Stub |
| **Pipeline** | `pipeline/io` | 🔲 Stub |
| **Playback** | `playback/domain` | ✅ Complete |
| **Playback** | `playback/telegram` | ✅ Complete |
| **Playback** | `playback/xtream` | ✅ Complete |
| **Player** | `player/internal` | 🚧 Phase 4/14 |
| **Feature** | `feature/*` | 🔲 Planned |

---

## Core Architecture Principles (V2)

1. **Canonical Media**: All pipelines produce `RawMediaMetadata`; normalization happens centrally.
2. **Feature System**: UI relies on `FeatureId` and `FeatureRegistry`, not hardcoded behavior.
3. **Internal Player (SIP)**: All playback goes through centralized player contracts.
4. **No Global Mutable Singletons**: Use DI and proper scoping.
5. **Unified Logging**: All logging through `UnifiedLog` facade.
6. **Legacy Isolation**: V1 code is read-only reference under `legacy/`.
7. **Layer Boundaries**: Strict separation - Pipeline may not import Data, Player may not import Pipeline.

---

## For Agents

See [AGENTS.md](AGENTS.md) for the complete v2 agent ruleset.

**Quick rules:**
- Modify only v2 paths (`app-v2/`, `core/`, `infra/`, `feature/`, `player/`, `playback/`, `pipeline/`, `docs/v2/`, `docs/meta/`, `scripts/`)
- Treat `legacy/**` as read-only
- No `com.chris.m3usuite` references outside `legacy/`
- Read module README.md before modifying any module

- Modify only v2 paths (`app-v2/`, `core/`, `infra/`, `feature/`, `player/`, `playback/`, `pipeline/`, `docs/v2/`, `docs/meta/`, `scripts/`)
- Treat `legacy/**` as read-only
- No `com.chris.m3usuite` references outside `legacy/`
- Read module README.md before modifying any module
