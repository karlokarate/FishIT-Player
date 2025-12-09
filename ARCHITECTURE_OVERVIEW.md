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

```
/
├── app-v2/           # V2 app shell, navigation, entry point
├── core/
│   ├── model/        # Canonical media models
│   ├── metadata-normalizer/  # Title parsing, TMDB resolution
│   ├── persistence/  # ObjectBox entities and repositories
│   ├── firebase/     # Firebase/Crashlytics integration
│   └── ui-imaging/   # Shared imaging utilities
├── infra/
│   ├── logging/      # UnifiedLog facade
│   ├── cache/        # Cache management
│   ├── settings/     # DataStore preferences
│   └── imageloader/  # Coil 3 global ImageLoader
├── feature/
│   ├── home/         # Home screen
│   ├── library/      # Library browsing
│   ├── live/         # Live channels
│   ├── detail/       # Detail views
│   ├── telegram-media/  # Telegram media UI
│   ├── settings/     # Settings UI
│   └── audiobooks/   # Audiobook UI
├── player/
│   └── internal/     # Internal Player (SIP)
├── playback/
│   └── domain/       # Playback contracts
├── pipeline/
│   ├── telegram/     # Telegram pipeline
│   ├── xtream/       # Xtream pipeline
│   ├── audiobook/    # Audiobook pipeline
│   └── io/           # Local file pipeline
├── docs/
│   ├── v2/           # V2 specifications and contracts
│   └── meta/         # Build, quality, workspace docs
├── scripts/
│   ├── build/        # Build helpers
│   └── api-tests/    # API probe scripts
└── legacy/           # V1 code and docs (read-only)
    ├── v1-app/       # Full v1 app module
    ├── docs/         # V1 documentation
    └── gold/         # Curated v1 patterns (36 patterns for v2 porting)
```

---

## Key V2 Documents

| Document | Purpose |
|----------|---------|
| [V2_PORTAL.md](V2_PORTAL.md) | V2 entry point, links to all key docs |
| [AGENTS.md](AGENTS.md) | Agent rules for v2 development |
| [ROADMAP.md](ROADMAP.md) | V2 implementation phases and progress |
| [CHANGELOG.md](CHANGELOG.md) | V2 changelog |
| [docs/v2/ARCHITECTURE_OVERVIEW_V2.md](docs/v2/ARCHITECTURE_OVERVIEW_V2.md) | Detailed v2 architecture |
| [docs/v2/CANONICAL_MEDIA_SYSTEM.md](docs/v2/CANONICAL_MEDIA_SYSTEM.md) | Canonical media model |
| [docs/v2/MEDIA_NORMALIZATION_CONTRACT.md](docs/v2/MEDIA_NORMALIZATION_CONTRACT.md) | Normalization rules |
| [docs/v2/LOGGING_CONTRACT_V2.md](docs/v2/LOGGING_CONTRACT_V2.md) | Logging contract |
| [V2_RELEASE_READINESS_ASSESSMENT.md](V2_RELEASE_READINESS_ASSESSMENT.md) | Release progress analysis (Dec 2025) |
| [RELEASE_FORTSCHRITT_ZUSAMMENFASSUNG.md](RELEASE_FORTSCHRITT_ZUSAMMENFASSUNG.md) | Release summary (German) |

---

## Core Architecture Principles (V2)

1. **Canonical Media**: All pipelines produce `RawMediaMetadata`; normalization happens centrally.
2. **Feature System**: UI relies on `FeatureId` and `FeatureRegistry`, not hardcoded behavior.
3. **Internal Player (SIP)**: All playback goes through centralized player contracts.
4. **No Global Mutable Singletons**: Use DI and proper scoping.
5. **Unified Logging**: All logging through `UnifiedLog` facade.
6. **Legacy Isolation**: V1 code is read-only reference under `legacy/`.
7. **Layer Boundaries**: Strict enforcement via hard rules and audit checklists.

## Current Status (Dec 2025)

**Overall Progress: 25-30% to MVP Release**

- ✅ **Phase 0/0.5**: Complete - Architecture & governance (100%)
- 🚧 **Phase 1**: In Progress - Feature System (60%)
- 🟡 **Phase 2**: Partial - Pipelines & Metadata (50%)
- 🚧 **Phase 3**: Started - Internal Player (15%, Phase 3 core complete)
- 🔲 **Phase 4**: Planned - UI Feature Screens (10%)
- 🔲 **Phase 5**: Planned - Quality & Performance (5%)

**Critical Path:** Internal Player (SIP) migration - Phases 4-14 remaining (~6-8 weeks)

See [V2_RELEASE_READINESS_ASSESSMENT.md](V2_RELEASE_READINESS_ASSESSMENT.md) for detailed analysis.

---

## For Agents

See [AGENTS.md](AGENTS.md) for the complete v2 agent ruleset.

**Quick rules:**
- Modify only v2 paths (`app-v2/`, `core/`, `infra/`, `feature/`, `player/`, `playback/`, `pipeline/`, `docs/v2/`, `docs/meta/`, `scripts/`)
- Treat `legacy/**` as read-only
- No `com.chris.m3usuite` references outside `legacy/`
