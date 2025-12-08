# FishIT-Player v2 – Roadmap

This roadmap tracks the v2 rebuild starting from branch `architecture/v2-bootstrap`.

For v1 roadmap history, see `legacy/docs/ROADMAP_v1.md`.

---

## Overview

The v2 rebuild follows a phased approach:

| Phase | Name | Status | Target |
|-------|------|--------|--------|
| 0 | Legacy Cage & V2 Surface | ✅ COMPLETED | Dec 2025 |
| 0.5 | Agents, Portal, Branch Rules | ✅ COMPLETED | Dec 2025 |
| 1 | Feature System | 🚧 IN PROGRESS | Dec 2025 |
| 2 | Pipelines → Canonical Media | 🔲 PLANNED | Jan 2026 |
| 3 | SIP / Internal Player | 🔲 PLANNED | Jan 2026 |
| 4 | UI Feature Screens | 🔲 PLANNED | Feb 2026 |
| 5 | Quality & Performance | 🔲 PLANNED | Feb 2026 |

---

## Phase 0 – Legacy Cage & V2 Surface
**Status: ✅ COMPLETED**

### Goals
- Cage all v1 code under `legacy/` so v2 surface is clean
- Ensure only v2 modules are part of the Gradle build
- Split docs into v2/meta/legacy buckets

### Tasks (Done)
- [x] Move v1 app module to `legacy/v1-app/`
- [x] Remove `:app` from `settings.gradle.kts`
- [x] Reorganize scripts into `scripts/build/` and `scripts/api-tests/`
- [x] Split docs: v2 → `docs/v2/`, legacy → `legacy/docs/`, meta → `docs/meta/`
- [x] Remove stale files (`tools/tdlib neu.zip`)
- [x] Fix Kotlin serialization plugin in `core/persistence`

### Docs
- [docs/v2/cleanup.md](docs/v2/cleanup.md) – Phase 0 specification

---

## Phase 0.5 – Agents, V2 Portal, Branch Rules
**Status: ✅ COMPLETED**

### Goals
- Establish clear v2 entry point and agent rules
- Protect v2 branches from accidental modifications
- Set default branch to v2 rebuild branch

### Tasks (Done)
- [x] Create unified `AGENTS.md` with v2 rules
- [x] Create `V2_PORTAL.md` as v2 entry point
- [x] Change default branch to `architecture/v2-bootstrap`
- [x] Configure branch protection rulesets
- [x] Archive old agent files to `legacy/docs/agents/`
- [x] Fix all `v2-docs/` → `docs/v2/` path references

### Docs
- [AGENTS.md](AGENTS.md) – Agent rules
- [V2_PORTAL.md](V2_PORTAL.md) – V2 entry point

---

## Phase 1 – Feature System
**Status: 🚧 IN PROGRESS**

### Goals
- Implement core feature API (`FeatureId`, `FeatureRegistry`, `FeatureProvider`)
- Define feature catalog based on Zielbild.md
- Wire first features into `app-v2`

### Tasks
- [x] Create `core/feature-api` module with API types
- [x] Define `FeatureId`, `FeatureScope`, `FeatureOwner`, `FeatureProvider`, `FeatureRegistry`
- [x] Create `Features.kt` with grouped feature IDs
- [x] Implement `AppFeatureRegistry` in app-v2
- [x] Create Hilt DI module for feature system
- [x] Create first feature providers (`TelegramFullHistoryFeatureProvider`, `TelegramLazyThumbnailsFeatureProvider`)
- [x] Create feature contract documentation template
- [x] **Phase 1.5:** Wire FeatureRegistry into real code (`TelegramMediaViewModel`)
- [x] **Phase 1.5:** Add Hilt DI to `pipeline/telegram` and `feature/telegram-media`
- [x] **Phase 1.5:** Unit tests for `AppFeatureRegistry`
- [x] **Phase 1.5:** Unit tests for Telegram FeatureProviders
- [ ] Add more feature providers across modules
- [ ] Integrate feature checks in UI screens

### Modules Affected
- `core/feature-api` (new)
- `app-v2`
- `pipeline/telegram`
- Additional modules as providers are added

### Docs
- [docs/v2/architecture/FEATURE_SYSTEM_TARGET_MODEL.md](docs/v2/architecture/FEATURE_SYSTEM_TARGET_MODEL.md) – Feature system specification
- [docs/v2/features/telegram/FEATURE_telegram.full_history_streaming.md](docs/v2/features/telegram/FEATURE_telegram.full_history_streaming.md) – First feature contract
- [docs/v2/Zielbild.md](docs/v2/Zielbild.md) – Feature catalog vision
- [docs/v2/ARCHITECTURE_OVERVIEW_V2.md](docs/v2/ARCHITECTURE_OVERVIEW_V2.md) – V2 architecture

---

## Phase 2 – Pipelines → Canonical Media
**Status: 🔲 PLANNED**

### Goals
- Finalize canonical media model in `core/model`
- Implement pipeline stubs for all sources
- Wire central metadata normalizer

### Tasks
- [ ] Finalize `RawMediaMetadata` and `NormalizedMediaMetadata` in `core/model`
- [ ] Implement `MediaMetadataNormalizer` in `core/metadata-normalizer`
- [ ] Create pipeline stubs:
  - [ ] `pipeline/telegram`
  - [ ] `pipeline/xtream`
  - [ ] `pipeline/audiobook`
  - [ ] `pipeline/io`
- [ ] Wire pipelines to normalizer
- [ ] Add TMDB resolver integration point

### Modules Affected
- `core/model`
- `core/metadata-normalizer`
- `pipeline/*`

### Docs
- [docs/v2/CANONICAL_MEDIA_SYSTEM.md](docs/v2/CANONICAL_MEDIA_SYSTEM.md)
- [docs/v2/MEDIA_NORMALIZATION_CONTRACT.md](docs/v2/MEDIA_NORMALIZATION_CONTRACT.md)
- [docs/v2/MEDIA_NORMALIZER_DESIGN.md](docs/v2/MEDIA_NORMALIZER_DESIGN.md)

---

## Phase 3 – SIP / Internal Player
**Status: 🔲 PLANNED**

### Goals
- Integrate Internal Player (SIP) from v1
- Implement playback domain contracts
- Support VOD, live, resume, kids-mode

### Tasks
- [ ] Port SIP player core from v1 to `player/internal`
- [ ] Define playback domain contracts in `playback/domain`
- [ ] Implement VOD playback
- [ ] Implement live playback
- [ ] Implement resume functionality
- [ ] Implement kids-mode time limits
- [ ] Wire trickplay support

### Modules Affected
- `player/internal`
- `playback/domain`

### Docs
- [docs/v2/internal-player/](docs/v2/internal-player/) – SIP contracts and checklists

---

## Phase 4 – UI Feature Screens
**Status: 🔲 PLANNED**

### Goals
- Implement main UI screens using v2 architecture
- Wire screens to feature system and pipelines

### Tasks
- [ ] Home screen (`feature/home`)
- [ ] Library screen (`feature/library`)
- [ ] Live channels screen (`feature/live`)
- [ ] Detail screen (`feature/detail`)
- [ ] Telegram media screen (`feature/telegram-media`)
- [ ] Settings screen (`feature/settings`)
- [ ] Audiobooks screen (`feature/audiobooks`)

### Modules Affected
- `feature/*`
- `app-v2` (navigation)

### Docs
- [docs/v2/Zielbild.md](docs/v2/Zielbild.md) – UI feature structure

---

## Phase 5 – Quality & Performance
**Status: 🔲 PLANNED**

### Goals
- Add telemetry and diagnostics
- Implement cache management
- Profile and optimize performance

### Tasks
- [ ] Integrate telemetry for player, pipelines, UI
- [ ] Implement cache management UI
- [ ] Add log viewer feature
- [ ] Profile startup time
- [ ] Optimize memory usage
- [ ] Add quality gates (Detekt, Lint, tests)

### Modules Affected
- `core/telemetry`
- `infra/cache`
- `infra/logging`
- `feature/settings`

### Docs
- [docs/v2/LOGGING_CONTRACT_V2.md](docs/v2/LOGGING_CONTRACT_V2.md)

---

## Related Documents

- [Changelog](CHANGELOG.md) – v2 changelog
- [V2 Portal](V2_PORTAL.md) – Entry point for v2 architecture
- [Architecture Overview](docs/v2/ARCHITECTURE_OVERVIEW_V2.md) – Detailed v2 architecture
- [AGENTS.md](AGENTS.md) – Agent rules for v2 development
- [Zielbild](docs/v2/Zielbild.md) – Feature catalog vision
