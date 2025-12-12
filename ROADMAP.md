# FishIT-Player v2 – Roadmap

<!-- markdownlint-disable MD024 -->

This roadmap tracks the v2 rebuild starting from branch `architecture/v2-bootstrap`.

For v1 roadmap history, see `legacy/docs/ROADMAP_v1.md`.

---

## Overview

The v2 rebuild follows a phased approach:

| Phase | Name | Status | Target |
|-------|------|--------|--------|
| 0 | Legacy Cage & V2 Surface | ✅ COMPLETED | Dec 2025 |
| 0.5 | Agents, Portal, Branch Rules | ✅ COMPLETED | Dec 2025 |
| 1 | Feature System | ✅ COMPLETED | Dec 2025 |
| 1.5-1.9 | Catalog Pipelines (Telegram/Xtream) | ✅ COMPLETED | Dec 2025 |
| 2 | Pipelines → Canonical Media | ✅ COMPLETED | Dec 2025 |
| 2.1 | Transport Layer (Telegram/Xtream) | ✅ COMPLETED | Dec 2025 |
| 2.2 | Data Layer (Telegram/Xtream) | ✅ COMPLETED | Dec 2025 |
| 2.3 | Metadata Normalizer | ✅ COMPLETED | Dec 2025 |
| 3 | SIP / Internal Player (Phase 0-5) | 🚧 IN PROGRESS | Dec 2025 |
| 4 | UI Feature Screens | 🔲 PLANNED | Jan 2026 |
| 5 | Quality & Performance | 🔲 PLANNED | Jan 2026 |

---

## Phase 0 – Legacy Cage & V2 Surface

Status: ✅ COMPLETED

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

Status: ✅ COMPLETED

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

Status: ✅ COMPLETED

### Goals

- Implement core feature API (`FeatureId`, `FeatureRegistry`, `FeatureProvider`)
- Define feature catalog based on Zielbild.md
- Wire first features into `app-v2`

### Tasks (Done)

- [x] Create `core/feature-api` module with API types
- [x] Define `FeatureId`, `FeatureScope`, `FeatureOwner`, `FeatureProvider`, `FeatureRegistry`
- [x] Create `Features.kt` with grouped feature IDs
- [x] Implement `AppFeatureRegistry` in app-v2
- [x] Create Hilt DI module for feature system
- [x] Create first capability providers (`TelegramFullHistoryCapabilityProvider`, `TelegramLazyThumbnailsCapabilityProvider`)
- [x] Create feature contract documentation template
- [x] **Phase 1.5:** Wire FeatureRegistry into real code (`TelegramMediaViewModel`)
- [x] **Phase 1.5:** Add Hilt DI to `pipeline/telegram` and `feature/telegram-media`
- [x] **Phase 1.5:** Unit tests for `AppFeatureRegistry`
- [x] **Phase 1.5:** Unit tests for Telegram FeatureProviders
- [x] **Phase 1.6:** Build stabilization (TDLib API, OkHttp 5.x fixes)
- [x] **Phase 1.7:** Test stabilization (MockK fixes for g000sha256 TDLib)
- [x] **Phase 1.8:** Telegram Catalog Pipeline complete
- [x] **Phase 1.9:** Xtream Catalog Pipeline complete

### Modules Affected

- `core/feature-api`
- `app-v2`
- `pipeline/telegram`
- `pipeline/xtream`
- `feature/telegram-media`

### Docs

- [docs/v2/architecture/FEATURE_SYSTEM_TARGET_MODEL.md](docs/v2/architecture/FEATURE_SYSTEM_TARGET_MODEL.md) – Feature system specification
- [docs/v2/features/telegram/FEATURE_telegram.full_history_streaming.md](docs/v2/features/telegram/FEATURE_telegram.full_history_streaming.md) – First feature contract
- [docs/v2/Zielbild.md](docs/v2/Zielbild.md) – Feature catalog vision

---

## Phase 2 – Pipelines → Canonical Media

Status: ✅ COMPLETED

### Goals

- Finalize canonical media model in `core/model`
- Implement pipeline stubs for all sources
- Wire central metadata normalizer
- Create Transport and Data layers for clean architecture

### Tasks (Done)

- [x] Finalize `RawMediaMetadata` and `NormalizedMediaMetadata` in `core/model`
- [x] Implement `MediaMetadataNormalizer` in `core/metadata-normalizer`
- [x] Create pipeline implementations:
  - [x] `pipeline/telegram` - TelegramCatalogPipeline
  - [x] `pipeline/xtream` - XtreamCatalogPipeline
  - [x] `pipeline/audiobook` - stub
  - [x] `pipeline/io` - stub
- [x] Wire pipelines to normalizer
- [x] Add TMDB resolver integration point
- [x] **Phase 2.1:** Transport Layer
  - [x] `infra/transport-telegram` - Typed interfaces (TelegramAuthClient, TelegramHistoryClient, TelegramFileClient, TelegramThumbFetcher)
  - [x] `infra/transport-xtream` - XtreamApiClient, XtreamUrlBuilder, XtreamDiscovery
- [x] **Phase 2.2:** Data Layer
  - [x] `infra/data-telegram` - TelegramContentRepository, ObxTelegramContentRepository
  - [x] `infra/data-xtream` - XtreamCatalogRepository, XtreamLiveRepository
  - [x] `core/catalog-sync` - CatalogSyncService, SyncRequest/Result models
- [x] **Phase 2.3:** Metadata Normalizer
  - [x] `core/metadata-normalizer` - MediaMetadataNormalizer, TmdbMetadataResolver
  - [x] SceneNameParser with regex patterns

### Modules Created

- `core/model` (enhanced)
- `core/metadata-normalizer` (new)
- `core/catalog-sync` (new)
- `infra/transport-telegram` (new)
- `infra/transport-xtream` (new)
- `infra/data-telegram` (new)
- `infra/data-xtream` (new)
- `pipeline/telegram` (enhanced)
- `pipeline/xtream` (enhanced)

### Docs

- [docs/v2/CANONICAL_MEDIA_SYSTEM.md](docs/v2/CANONICAL_MEDIA_SYSTEM.md)
- [docs/v2/MEDIA_NORMALIZATION_CONTRACT.md](docs/v2/MEDIA_NORMALIZATION_CONTRACT.md)
- [docs/v2/MEDIA_NORMALIZER_DESIGN.md](docs/v2/MEDIA_NORMALIZER_DESIGN.md)

---

## Phase 3 – SIP / Internal Player

Status: 🚧 IN PROGRESS (Player Migration Phase 5 of 14 Complete)

### Goals

- Integrate Internal Player (SIP) from v1
- Implement playback domain contracts
- Support VOD, live, resume, kids-mode

### Player Migration Progress

See [docs/v2/internal-player/PLAYER_MIGRATION_STATUS.md](docs/v2/internal-player/PLAYER_MIGRATION_STATUS.md) for detailed status.

| Migration Phase | Status | Description |
|-----------------|--------|-------------|
| Phase 0 | ✅ COMPLETE | Guardrails & Architecture |
| Phase 1 | ✅ COMPLETE | IST-Analyse |
| Phase 2 | ✅ COMPLETE | Player-Modell finalisieren (`core:player-model`) |
| Phase 3 | ✅ COMPLETE | SIP-Kern portieren (`player:internal` refactor) |
| Phase 4 | ✅ COMPLETE | Telegram & Xtream PlaybackFactories |
| Phase 5 | ✅ COMPLETE | MiniPlayer (`player:miniplayer` module) |
| Phase 6 | ✅ COMPLETE | Subtitles/CC (`SubtitleTrackManager`) |
| Phase 7 | ✅ COMPLETE | Audio-Spur (`AudioTrackManager`) |
| Phase 8 | ⏳ PENDING | Serienmodus & TMDB |
| Phase 9 | ⏳ PENDING | Kids/Guest Policy |
| Phase 10 | ⏳ PENDING | Fehler-Handling |
| Phase 11 | ⏳ PENDING | Download & Offline |
| Phase 12 | ⏳ PENDING | Live-TV |
| Phase 13 | ⏳ PENDING | Input & Casting |
| Phase 14 | ⏳ PENDING | Tests & Doku |

### Completed Tasks

- [x] Create `core:player-model` with `SourceType`, `PlaybackContext`, `PlaybackState`, `PlaybackError`
- [x] Create `playback:domain` with `PlaybackSourceFactory`, `PlaybackSource`, contracts
- [x] Implement `TelegramPlaybackSourceFactoryImpl` in `playback:telegram`
- [x] Implement `XtreamPlaybackSourceFactoryImpl` in `playback:xtream`
- [x] Refactor `player:internal` to use new types
- [x] Create `PlaybackSourceResolver` with factory injection pattern
- [x] Clean layer violations (remove pipeline deps from player)
- [x] Move `TelegramFileDataSource` to `playback:telegram`
- [x] Implement `player:miniplayer` module with MiniPlayerManager, MiniPlayerState, MiniPlayerOverlay
- [x] Implement `SubtitleTrack` model and `SubtitleTrackManager` (Phase 6)
- [x] Implement `AudioTrack` model and `AudioTrackManager` (Phase 7)
- [x] **Player is test-ready**: Debug playback via Big Buck Bunny stream in `DebugPlaybackScreen`

### Player Test Status

> ✅ **The player is test-ready without Telegram/Xtream transport.**
>
> - Debug playback available via `DebugPlaybackScreen` using Big Buck Bunny test stream
> - Player uses `PlaybackSourceResolver` + `Set<PlaybackSourceFactory>` (injected via `@Multibinds`)
> - Empty factory set is valid – player falls back to test stream
> - Telegram/Xtream `PlaybackSourceFactory` implementations can be enabled later without changing player code

### Pending Tasks

- [ ] Series mode / binge watching (Phase 8)
- [ ] Kids-mode time limits & Guest restrictions (Phase 9)
- [ ] Enhanced error handling and recovery (Phase 10)
- [ ] Download / Offline playback (Phase 11)
- [ ] Live TV with EPG (Phase 12)
- [ ] Input handling & Casting (Phase 13)
- [ ] Tests and documentation (Phase 14)

### Telegram Transport Status

The next step for real Telegram content is implementing `DefaultTelegramClient` in `transport-telegram`:

| Task | Priority | Description |
|------|----------|-------------|
| `DefaultTelegramClient` | P0 | Wrap g00sha TDLib, implement `TelegramAuthClient`, `TelegramHistoryClient`, `TelegramFileClient` |
| `TelegramThumbFetcherImpl` | P1 | Provide `TelegramThumbFetcher` to imaging layer |
| Reactivate `TelegramPlaybackModule` | P1 | Bind `TelegramPlaybackSourceFactoryImpl` into factory set after transport is stable |

**Note:** `TdlibClientProvider` is a v1 pattern and must NOT be reintroduced. Use typed interfaces instead.

### Modules Affected

- `core/player-model` (new)
- `playback/domain` (enhanced)
- `playback/telegram` (new factory impl)
- `playback/xtream` (new factory impl)
- `player/internal` (refactored)

### Docs

- [docs/v2/internal-player/](docs/v2/internal-player/) – SIP contracts and checklists
- [docs/v2/internal-player/PLAYER_MIGRATION_STATUS.md](docs/v2/internal-player/PLAYER_MIGRATION_STATUS.md) – Detailed migration status
- [docs/v2/ARCHITECTURE_OVERVIEW_V2.md](docs/v2/ARCHITECTURE_OVERVIEW_V2.md) – V2 architecture

---

## Phase 4 – UI Feature Screens

Status: 🔲 PLANNED

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

Status: 🔲 PLANNED

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
- [Player Migration Status](docs/v2/internal-player/PLAYER_MIGRATION_STATUS.md) – Detailed player migration progress
