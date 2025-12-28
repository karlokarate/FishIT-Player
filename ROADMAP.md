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
| 2.4 | Structured Bundles (Telegram) | ✅ COMPLETED | Dec 2025 |
| 2.5 | SSOT Catalog Sync Scheduler | ✅ COMPLETED | Dec 2025 |
| 2.6 | SSOT Ingest Worker Bodies | ✅ COMPLETED | Dec 2025 |
| 3 | SIP / Internal Player (Phase 0-7) | ✅ COMPLETED | Dec 2025 |
| 3.1 | SIP / Internal Player (Phase 8-14) | 🚧 IN PROGRESS | Jan 2026 |
| 4 | UI Feature Screens | 🚧 IN PROGRESS | Jan 2026 |
| 5 | Quality & Performance | 🔲 PLANNED | Feb 2026 |

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

## Phase 2.4 – Telegram Structured Bundles

Status: 📋 PLANNED (Dec 2025)

### Goals

- Implement structured bundle recognition for Telegram chats
- Enable zero-parsing path for chats with pre-structured metadata
- Pass through TMDB-IDs, FSK ratings from TEXT messages

### Background

Analysis of 398 Telegram chat exports revealed that **8 chats** contain structured metadata in TEXT messages (tmdbUrl, year, fsk, genres, etc.). These can be grouped with VIDEO/PHOTO messages by identical timestamp into "Structured Bundles".

This enables:
- **Zero-Parsing Path:** Direct TMDB-ID pass-through from source
- **Zero-API-Call Path:** Skip TMDB search for structured chats
- **Kids-Filter:** FSK values available without TMDB lookup

### Tasks

- [x] **Phase 2.4.1:** Core Model Extensions
  - [x] Add `ageRating: Int?` to `RawMediaMetadata`
  - [x] Add `rating: Double?` to `RawMediaMetadata`
  - [x] Unit tests for model extensions

- [x] **Phase 2.4.2:** TelegramMediaItem Extensions
  - [x] Add structured bundle fields (structuredTmdbId, structuredYear, structuredFsk, etc.)
  - [x] Add `TelegramBundleType` enum (FULL_3ER, COMPACT_2ER, SINGLE)
  - [x] Update `toRawMediaMetadata()` for new fields
  - [x] Unit tests

- [x] **Phase 2.4.3:** Message Bundler
  - [x] Implement `TelegramMessageBundler` (timestamp grouping)
  - [x] Implement `TelegramMessageBundle` data class
  - [x] Implement bundle classification (3er, 2er, single)
  - [x] Unit tests with JSON fixtures

- [x] **Phase 2.4.4:** Metadata Extractor
  - [x] Implement `TelegramStructuredMetadataExtractor`
  - [x] TMDB-URL to ID parsing (`/movie/(\d+)`)
  - [x] Structured field extraction
  - [x] Unit tests

- [x] **Phase 2.4.5:** Bundle-to-Item Mapper
  - [x] Implement `TelegramBundleToMediaItemMapper`
  - [x] Lossless multi-video emission (Contract R7)
  - [x] Poster selection (max pixel area, Contract R9)
  - [x] Unit tests

- [x] **Phase 2.4.6:** Pipeline Integration
  - [x] Wire bundler into `TelegramPipelineAdapter`
  - [x] Update for bundle-aware processing
  - [x] Unified TgContent DTOs (resolved bridge duplicates)

- [x] **Phase 2.4.7:** Normalizer Optimization
  - [x] Document TMDB shortcut requirement in DefaultTmdbMetadataResolver
  - [ ] Implement actual TMDB search with shortcut (future phase)

### Modules Affected

- `core/model` (RawMediaMetadata extensions)
- `pipeline/telegram/grouper` (new package)
- `pipeline/telegram/mapper` (bundle mapper)
- `pipeline/telegram/model` (TelegramMediaItem extensions)
- `core/metadata-normalizer` (TMDB-ID shortcut)

### Docs

- [docs/v2/TELEGRAM_STRUCTURED_BUNDLES_MASTERPLAN.md](docs/v2/TELEGRAM_STRUCTURED_BUNDLES_MASTERPLAN.md) – Full design
- [contracts/TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md](contracts/TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md) – Binding contract

---

## Phase 2.5 – SSOT Catalog Sync Scheduler

Status: ✅ COMPLETED (Dec 2025)

### Goals

- Implement single-source-of-truth (SSOT) sync scheduling
- Wire all UI sync triggers to WorkManager only
- Remove all direct CatalogSyncService.sync() calls from UI

### Background

Contract W-6 (CATALOG_SYNC_WORKERS_CONTRACT_V2) mandates a single global sync queue with `uniqueWorkName = "catalog_sync_global"`. This phase implements the scheduling infrastructure and wires the UI to use it exclusively.

### Tasks (Done)

- [x] **Task 1: SSOT Worker Scheduling**
  - [x] Extend `CatalogSyncWorkScheduler` interface with `enqueueForceRescan()`, `cancelSync()`
  - [x] Rename app-v2 scheduler to `CatalogSyncWorkSchedulerImpl`
  - [x] Remove `DefaultCatalogSyncWorkScheduler` placeholder
  - [x] Update DI modules (`AppWorkModule`)
  - [x] Wire scheduler into `DebugViewModel`

- [x] **Task 2: SSOT Sync Trigger Wiring**
  - [x] Create `SyncUiState` sealed interface (Idle/Running/Success/Failed)
  - [x] Create `SyncFailureReason` enum
  - [x] Create `SyncStateObserver` interface
  - [x] Create `CatalogSyncUiBridge` (WorkManager → SyncUiState mapping)
  - [x] Update `DebugViewModel` with `SyncStateObserver` injection
  - [x] Replace Telegram/Xtream sync buttons with SSOT controls
  - [x] Add `SyncStatusRow` composable for state display

### Modules Affected

- `core/catalog-sync` (SyncUiState, SyncStateObserver)
- `app-v2/work` (CatalogSyncWorkSchedulerImpl, CatalogSyncUiBridge)
- `app-v2/di` (AppWorkModule)
- `feature/settings` (DebugViewModel, DebugScreen)

### Docs

- [docs/CATALOG_SYNC_WORKERS_CONTRACT_V2.md](docs/CATALOG_SYNC_WORKERS_CONTRACT_V2.md) – Updated to v2.1

---

## Phase 2.6 – SSOT Ingest Worker Bodies

Status: ✅ COMPLETED (Dec 2025)

### Goals

- Implement worker bodies for `catalog_sync_global` chain
- All workers call CatalogSyncService only (W-2 compliant)
- FireTV-safe: bounded batches + frequent checkpoints
- Non-retryable failure handling (W-20)

### Background

Phase 2.5 implemented the SSOT scheduler and UI wiring. This phase implements the actual worker bodies that execute catalog synchronization:
- **Xtream workers:** Preflight validation + catalog scan
- **Telegram workers:** Auth preflight + full/incremental scan
- **IO workers:** Permission check + quick scan

### Tasks (Done)

- [x] **Task 1: Worker Infrastructure**
  - [x] Create `WorkerConstants.kt` (tags, keys, device classes, sync modes)
  - [x] Create `WorkerInputData.kt` (input parsing, RuntimeGuards)
  - [x] Create `WorkerOutputData` builder functions

- [x] **Task 2: Orchestrator Worker**
  - [x] Update `CatalogSyncOrchestratorWorker` with full chain building
  - [x] Source order: Xtream → Telegram → IO (W-7)
  - [x] No-source behavior: Return success if no sources active (W-8)
  - [x] Pass input data to all child workers

- [x] **Task 3: Xtream Workers**
  - [x] `XtreamPreflightWorker` (auth state validation)
  - [x] `XtreamCatalogScanWorker` (via CatalogSyncService.syncXtream)
  - [x] Non-retryable failures: Invalid credentials, Expired account

- [x] **Task 4: Telegram Workers**
  - [x] `TelegramAuthPreflightWorker` (TDLib auth check)
  - [x] `TelegramFullHistoryScanWorker` (full scan)
  - [x] `TelegramIncrementalScanWorker` (incremental sync)
  - [x] Non-retryable failure: Login required

- [x] **Task 5: IO Workers**
  - [x] `IoQuickScanWorker` (permission check + stub)
  - [x] Non-retryable failure: Storage permission denied

### Modules Affected

- `app-v2/work` (All workers)

### Docs

- [docs/CATALOG_SYNC_WORKERS_CONTRACT_V2.md](docs/CATALOG_SYNC_WORKERS_CONTRACT_V2.md) – Updated to v2.2

---

## Phase 3 – SIP / Internal Player

Status: 🚧 IN PROGRESS (Player Migration Phase 7 of 14 Complete)

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
| Phase 8 | ⏳ TODO | Serienmodus & TMDB |
| Phase 9 | ⏳ TODO | Kids/Guest Policy |
| Phase 10 | ⏳ TODO | Fehler-Handling |
| Phase 11 | ⏳ TODO | Download & Offline |
| Phase 12 | ⏳ TODO | Live-TV |
| Phase 13 | ⏳ TODO | Input & Casting |
| Phase 14 | ⏳ TODO | Tests & Doku |

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

Status: � IN PROGRESS

### Goals

- Implement main UI screens using v2 architecture
- Wire screens to feature system and pipelines

### Completed

- [x] Onboarding screen (`feature/onboarding`) – Telegram & Xtream auth flows
- [x] Home screen shell (`feature/home`) – Basic structure
- [x] Debug playback screen – Test player integration

### TODO (Priority Order)

1. [ ] **Home Content Integration** (`feature/home`)
   - Wire catalog data from repositories
   - Add content rows (Continue Watching, Recent, Categories)
   - Implement TV focus navigation (FocusKit)

2. [ ] **Telegram Media Browser** (`feature/telegram-media`)
   - Chat list with media preview
   - Media grid per chat
   - Wire to TelegramContentRepository

3. [ ] **Library Screen** (`feature/library`)
   - Category-based VOD browsing
   - Series/Movie separation
   - Search functionality

4. [ ] **Detail Screen** (`feature/detail`)
   - VOD/Series detail view
   - Episode list for series
   - Playback launch via MediaActionBar

5. [ ] **Live Channels** (`feature/live`)
   - EPG grid view
   - Channel list with categories
   - Live stream launch

6. [ ] **Settings** (`feature/settings`)
   - Profile management
   - Playback preferences
   - Cache management
   - Debug/diagnostic options

7. [ ] **Audiobooks** (`feature/audiobooks`) – Future

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

### TODO (Priority Order)

1. [ ] **Telemetry Foundation**
   - Extend UnifiedLog for structured telemetry events
   - Player stats (buffer, playback time, errors)
   - Pipeline metrics (scan duration, item count)

2. [ ] **Cache Management UI** (`feature/settings`)
   - TDLib cache size display and clear action
   - Image cache (Coil) management
   - HTTP cache statistics

3. [ ] **Log Viewer** (`feature/settings`)
   - In-app log browser for diagnostics
   - Filter by source/level
   - Export logs functionality

4. [ ] **Performance Profiling**
   - Startup time measurement
   - Memory usage monitoring
   - UI jank detection (dropped frames)

5. [ ] **Quality Gates**
   - Ensure Detekt rules enforced
   - Lint clean builds
   - Minimum test coverage targets

### Modules Affected

- `infra/logging` – Telemetry via UnifiedLog
- `infra/imaging` – Image cache management
- `infra/work` – Background cleanup jobs
- `feature/settings` – Cache/Log UI

### Docs

- [docs/v2/LOGGING_CONTRACT_V2.md](docs/v2/LOGGING_CONTRACT_V2.md)

---

## Known Issues / Technical Debt

Status: 🔴 BLOCKING or ⚠️ NON-BLOCKING

This section tracks known bugs, missing implementations, and technical debt that must be resolved before release.

### 🔴 BLOCKING – Must Fix Before Feature Complete

| Issue | Module | Description | Created |
|-------|--------|-------------|---------|
| Missing TdlibClientProvider binding | `app-v2` | Dagger cannot find `@Provides` for `TdlibClientProvider`. v1 legacy pattern – should NOT be reintroduced. Telegram playback module is disabled until proper typed interfaces are available. | Dec 2025 |
| Missing XtreamAuthRepository binding | `app-v2` | Dagger cannot find `@Provides` for `XtreamAuthRepository`. Need to implement proper binding in data-xtream module. | Dec 2025 |

### ⚠️ NON-BLOCKING – Can Ship But Should Fix

| Issue | Module | Description | Created |
|-------|--------|-------------|---------|
| pipeline-cli API outdated | `tools/pipeline-cli` | CLI tool uses old TgContent signatures (e.g., `content.minithumbnail` instead of `content.thumbnail?.minithumbnail`). Not critical – CLI is dev-only tool. | Dec 2025 |
| TgChat/TgChatType still inline | `transport-telegram` | `TgChat` and `TgChatType` are still defined inline in `TelegramTransportClient.kt`. Should be moved to `api/` package for consistency. Low priority. | Dec 2025 |

### 📝 TODO – Planned Cleanup Tasks

| Task | Module | Description | Priority |
|------|--------|-------------|----------|
| Implement TelegramTransportModule | `infra/transport-telegram` | Create proper Hilt module with typed interface bindings (`TelegramAuthClient`, `TelegramHistoryClient`, `TelegramFileClient`). | HIGH |
| Re-enable TelegramPlaybackModule | `playback/telegram` | Currently disabled via `@Multibinds` empty set. Enable once transport typed interfaces are ready. | HIGH |
| Move TgChat to api/ package | `infra/transport-telegram` | Extract `TgChat`, `TgChatType` from interface file to `api/TgChat.kt`. | LOW |
| Update pipeline-cli | `tools/pipeline-cli` | Update CLI to use new TgContent API with nested thumbnail access. | LOW |

### Resolution Protocol

When fixing items from this list:

1. Create a branch from `architecture/v2-bootstrap`
2. Fix the issue following AGENTS.md guidelines
3. Update this section: move item to "Resolved" or delete it
4. Update CHANGELOG.md with the fix
5. Commit with message referencing this roadmap section

### Resolved Issues

| Issue | Module | Resolution | Date |
|-------|--------|------------|------|
| Duplicate TgContent definitions | `transport-telegram`, `pipeline-telegram` | Unified on `api/TgContent.kt`, removed bridge functions | Dec 2025 |

---

## Release QA Checklist – catalog_fullsync_playback_telegram_images.patch

This checklist validates the fixes from the catalog_fullsync_playback_telegram_images.patch:

### A. Telegram Thumbnail Rendering

- [ ] Telegram media items display thumbnails (not just 🎬 emoji placeholder)
- [ ] FishImage correctly loads `tg://thumb/...` URIs via TelegramThumbFetcher
- [ ] Fallback to poster image when thumbnail is not available
- [ ] Log ImageRef type for Telegram items to verify thumbnail vs poster selection

### B. Xtream VOD/Series Backfill Detection

- [ ] Force Rescan detects VOD/Series items with blank metadata (not just null)
- [ ] Backfill queue shows realistic counts for VOD and Series needing enrichment
- [ ] After enrichment, metadata fields (plot, cast, director, genre, trailer) are populated
- [ ] Blank strings are normalized to null in the database

### C. Xtream VOD Playback URL Fix

- [ ] Xtream VOD playback URLs use `/movie/...` path (not `/vod/...`)
- [ ] Log one Xtream VOD playback URL and verify HTTP 200 response
- [ ] Verify stream_id is prioritized over vod_id/movie_id for VOD item resolution

### D. Catalog Full Sync (No Silent Drops)

- [ ] Telegram scan: `discovered` count matches `persisted` count (no drops)
- [ ] Xtream scan: VOD/Series/Episode/Live counts match server expectations
- [ ] Pipeline events use `send()` instead of `trySend()` (backpressure, no data loss)
- [ ] Large catalogs (>1000 items) sync completely

### E. Instrumentation & Debug Counters

- [ ] Debug dashboard displays pipeline discovered/persisted totals per source type
- [ ] Playback URL logging shows resolved Xtream URLs
- [ ] Telegram URI resolution logs include remoteId and mimeType
- [ ] TelegramThumbFetcher execution is logged when loading thumbnails

### Validation Commands

```bash
# Force Rescan and check logs
adb logcat -s UnifiedLog:D | grep -E "backfill|discovered|persisted|playback"

# Check Xtream VOD URL
adb logcat -s XtreamPlayback:D | grep "buildVodUrl"

# Verify Telegram thumbnails
adb logcat -s TelegramThumbFetcher:D FishImage:D

# Monitor pipeline events
adb logcat -s TelegramCatalogPipelineImpl:D XtreamCatalogPipelineImpl:D
```

---

## Related Documents

- [Changelog](CHANGELOG.md) – v2 changelog
- [V2 Portal](V2_PORTAL.md) – Entry point for v2 architecture
- [Architecture Overview](docs/v2/ARCHITECTURE_OVERVIEW_V2.md) – Detailed v2 architecture
- [AGENTS.md](AGENTS.md) – Agent rules for v2 development
- [Zielbild](docs/v2/Zielbild.md) – Feature catalog vision
- [Player Migration Status](docs/v2/internal-player/PLAYER_MIGRATION_STATUS.md) – Detailed player migration progress
