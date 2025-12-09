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
| 2 | Pipelines → Canonical Media | 🟡 PARTIAL (50%) | Jan 2026 |
| 3 | SIP / Internal Player | 🚧 STARTED (Phase 3 Core Complete) | Jan-Feb 2026 |
| 4 | UI Feature Screens | 🔲 PLANNED | Feb-Mar 2026 |
| 5 | Quality & Performance | 🔲 PLANNED | Mar 2026 |

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
**Status: 🚧 STARTED (Phase 3 Core Complete - Dec 2025)**

### Goals
- Integrate Internal Player (SIP) from v1
- Implement playback domain contracts
- Support VOD, live, resume, kids-mode

### Tasks
- [x] **Phase 3 Complete (Dec 2025):** Create `core:player-model` module
- [x] **Phase 3 Complete:** Implement `PlaybackSourceResolver` with Factory pattern
- [x] **Phase 3 Complete:** Update `InternalPlayerSession` for new architecture
- [x] **Phase 3 Complete:** Update `InternalPlayerState` to use new types
- [x] **Phase 3 Complete:** Update playback domain interfaces (`ResumeManager`, `KidsPlaybackGate`)
- [x] **Phase 3 Complete:** Remove layer violations (pipeline deps from player)
- [x] **Phase 3 Complete:** Move `TelegramFileDataSource` to `playback:telegram`
- [ ] **Phase 4-14 (Remaining):** Port remaining SIP components from v1
  - [ ] Complete VOD playback implementation
  - [ ] Complete live playback implementation
  - [ ] Implement resume functionality (beyond stub)
  - [ ] Implement kids-mode time limits (beyond stub)
  - [ ] Wire trickplay support
  - [ ] Subtitle system integration
  - [ ] Mini-player and PiP
  - [ ] TV input handling
  - [ ] Performance optimizations

### Modules Affected
- `core:player-model` (new - Phase 3)
- `player/internal` (Phase 3 started)
- `playback/domain` (Phase 3 updated)
- `playback/telegram` (Phase 3 updated)
- `playback/xtream` (future)

### Current Progress
- **Phase 3 of 14 Complete** (see `docs/v2/player migrationsplan.md`)
- Player now uses clean `core:player-model` types
- Factory pattern for source resolution implemented
- Layer violations fixed
- **Remaining:** Phases 4-14 (full SIP port, UI chrome, advanced features)

### Docs
- [docs/v2/internal-player/](docs/v2/internal-player/) – SIP contracts and checklists
- [docs/v2/internal-player/PLAYER_MIGRATION_STATUS.md](docs/v2/internal-player/PLAYER_MIGRATION_STATUS.md) – Phase tracking
- [docs/v2/player migrationsplan.md](docs/v2/player migrationsplan.md) – 14-phase migration plan

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

## Release Readiness Assessment (Dec 2025)

**Status:** Comprehensive assessment completed (Dec 9, 2025)

### Overall Progress: 25-30% to MVP

| Layer | Progress | Status |
|-------|----------|--------|
| Architecture & Docs | 90% | ✅ Excellent |
| Core | 40% | 🟡 Partial |
| Pipeline | 55% | 🟡 Good foundation |
| Playback | 15% | 🔴 Critical blocker |
| Player | 15% | 🔴 Critical blocker |
| UI | 10% | 🔴 Critical blocker |
| Infra | 35% | 🟡 OK |

### Critical Blockers (16-20 weeks remaining to MVP)

1. **Internal Player (SIP)** - 85% remaining (~6-8 weeks)
   - Phase 3 complete, Phases 4-14 remaining
   - ~5000 LOC from v1 to port
2. **UI Feature Screens** - 90% missing (~4-6 weeks)
   - All feature modules exist but mostly skeletal
3. **Metadata Normalizer** - 80% missing (~2-3 weeks)
   - Scene parser, TMDB integration needed
4. **Playback Domain** - 70% missing (~2 weeks)
   - Full implementations of managers and gates
5. **Data Repositories** - 80% missing (~3-4 weeks)
   - Real implementations connecting to APIs

### Timeline Estimates

- **MVP (Minimal Viable Product):** 16-20 weeks → **May 2026**
- **Feature-Complete:** +8-12 weeks → **August 2026**
- **Production-Ready:** +4-6 weeks → **October 2026**

### Code Metrics

- Production files: 261 Kotlin files
- Test files: 100 files
- Active modules: 37 v2 modules
- LOC: ~34,000 actual / ~88,000 target (39% completion)

### Assessment Documents

- [V2_RELEASE_READINESS_ASSESSMENT.md](V2_RELEASE_READINESS_ASSESSMENT.md) - Full analysis (English)
- [RELEASE_FORTSCHRITT_ZUSAMMENFASSUNG.md](RELEASE_FORTSCHRITT_ZUSAMMENFASSUNG.md) - Summary (German)

---

## Related Documents

- [Changelog](CHANGELOG.md) – v2 changelog
- [V2 Portal](V2_PORTAL.md) – Entry point for v2 architecture
- [Architecture Overview](docs/v2/ARCHITECTURE_OVERVIEW_V2.md) – Detailed v2 architecture
- [AGENTS.md](AGENTS.md) – Agent rules for v2 development
- [Zielbild](docs/v2/Zielbild.md) – Feature catalog vision
