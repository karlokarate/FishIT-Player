# FishIT-Player v2 – Changelog

This changelog tracks all changes starting from the v2 rebuild on branch `architecture/v2-bootstrap`.

For v1 history prior to the rebuild, see `legacy/docs/CHANGELOG_v1.md`.

---

## [Unreleased]

### Logging System Update (2025-12-11)

- **feat(logging)**: Lambda-based lazy logging as primary API
  - `UnifiedLog` now supports inline lambda-based overloads: `UnifiedLog.d(TAG) { "message" }`
  - Lambda message is only evaluated if the log level is enabled (lazy evaluation)
  - String-based overloads remain for convenience with constant messages
  - Added `isEnabled(level)` method for expensive log preparations
- **docs(contracts)**: Bumped LOGGING_CONTRACT_V2 to version 1.1
  - Lambda-based API is now the **primary** logging API for v2
  - Added Section 5.1 "Lazy Logging" with performance rules
  - Updated all code examples to use lambda-based syntax
  - Added agent-specific rules for hot paths (player, transport, pipelines)
- **docs(logging)**: Created `docs/v2/logging/UNIFIED_LOGGING.md`
  - Comprehensive logging guide with API examples
  - Performance guidelines for hot paths
  - Log level usage and tag conventions
- **test(logging)**: Added 10 new unit tests for lazy logging behavior
  - Tests verify lambda is not evaluated when log level is filtered
  - Tests verify `isEnabled()` correctness

### FFmpeg / NextLib Integration (2025-12-11)

- **feat(player)**: Added FFmpeg-based software decoders via NextLib
  - New module `player:nextlib-codecs` for FFmpeg codec integration
  - Uses `io.github.anilbeesetti:nextlib-media3ext:1.8.0-0.9.0`
  - `NextlibCodecConfigurator` interface for RenderersFactory abstraction
  - `DefaultNextlibCodecConfigurator` creates `NextRenderersFactory` for ExoPlayer
  - Hilt DI module `NextlibCodecsModule` for dependency injection
- **feat(player)**: Integrated NextLib into SIP (Internal Player)
  - `InternalPlayerSession` now uses `NextRenderersFactory` via codecConfigurator
  - Extended codec support: Vorbis, Opus, FLAC, ALAC, AAC, AC3, EAC3, DTS, TrueHD (audio), H.264, HEVC, VP8, VP9 (video)
  - Added track logging on STATE_READY to verify codec integration
- **docs**: Updated PLAYER_MIGRATION_STATUS.md with FFmpeg/NextLib section
- **note**: NextLib is GPL-3.0 licensed

### Phase 7 – Audio Track Selection (2025-12-11)

- **feat(player-model)**: Created `AudioTrack.kt` with source-agnostic audio models
  - `AudioTrackId` value class for stable track identification
  - `AudioChannelLayout` enum: MONO, STEREO, SURROUND_5_1, SURROUND_7_1, ATMOS, UNKNOWN
  - `AudioCodecType` enum: AAC, AC3, EAC3, DTS, TRUEHD, FLAC, OPUS, VORBIS, MP3, PCM, UNKNOWN
  - `AudioSourceType` enum: EMBEDDED, EXTERNAL, MANIFEST
  - `AudioTrack` data class with `fromMedia3()` factory method and `buildLabel()` helper
- **feat(player-model)**: Created `AudioSelectionState.kt` with selection policy
  - Observable state with `availableTracks`, `selectedTrackId`, `preferredLanguage`, `preferSurroundSound`
  - `selectBestTrack()` deterministic selection: language preference → surround preference → first available
  - `tracksForLanguage()` filtering helper
  - `hasMultipleTracks`, `trackCount` computed properties
- **feat(player)**: Created `AudioTrackManager` in `player:internal/audio/`
  - Media3 track discovery and mapping via `Player.Listener`
  - Selection API: `selectTrack()`, `selectTrackByLanguage()`, `selectNextTrack()`
  - Preference updates: `updatePreferences()` for language and surround preferences
  - Track cycling for TV remote quick switching
  - Logging via UnifiedLog per LOGGING_CONTRACT_V2
- **feat(player)**: Extended `InternalPlayerSession` with audio APIs
  - `audioState: StateFlow<AudioSelectionState>` for UI observation
  - `selectAudioTrack(trackId)`, `selectAudioByLanguage(languageCode)` selection methods
  - `cycleAudioTrack()` for TV remote quick switching
  - `updateAudioPreferences()` for preference management
  - AudioTrackManager attach/detach in initialize/release lifecycle
- **test(player)**: Created `AudioModelsTest.kt` with 31 unit tests
  - Tests for AudioTrack, AudioChannelLayout, AudioCodecType, AudioSourceType
  - Tests for AudioSelectionState and selection policy behavior
- **fix(player-model)**: Fixed EAC3/AC3 MIME type detection order
  - EAC3 must be checked before AC3 since "eac3" contains "ac3"
- **docs**: Updated PLAYER_MIGRATION_STATUS.md to Phase 7 Complete

### Phase 6 – Subtitles/CC (2025-12-11)

- **feat(player-model)**: Created `SubtitleTrack.kt` with subtitle models
  - `SubtitleTrackId` value class, `SubtitleSourceType` enum
  - `SubtitleTrack` data class with `fromMedia3()` factory
- **feat(player-model)**: Created `SubtitleSelectionState.kt`
- **feat(player)**: Created `SubtitleTrackManager` in `player:internal/subtitle/`
- **feat(player)**: Extended `InternalPlayerSession` with subtitle APIs
  - `subtitleState`, `selectSubtitleTrack()`, `disableSubtitles()`, `selectSubtitleByLanguage()`
- **test(player)**: Created `SubtitleModelsTest.kt` with unit tests
- **docs**: Updated PLAYER_MIGRATION_STATUS.md to Phase 6 Complete

### Documentation Sync (2025-12-11)

- **docs**: Synchronized all Phase 1-6 documentation with actual IST-Zustand
  - **ROADMAP.md**: Phase 5 MiniPlayer marked COMPLETE, removed duplicate Phase 2/3 sections
  - **ARCHITECTURE_OVERVIEW_V2.md**: 
    - Added `playback:telegram` and `playback:xtream` module documentation
    - Updated `player:internal` to reference `PlaybackSourceResolver` (not legacy `InternalPlaybackSourceResolver`)
    - Added `player:miniplayer` as separate module (not `internal.mini` package)
    - Added `PlayerDataSourceModule` DI documentation
    - Corrected pipeline documentation (playback components moved to `:playback:*`)
  - **PLAYER_ARCHITECTURE_V2.md**: Updated Phase 3-5 status to COMPLETE
  - **PLAYER_MIGRATION_STATUS.md**: Added note distinguishing v2 migration docs from v1 refactoring docs

### Phase 1-6 Review Fixes (2025-12-11)

- **fix(player)**: Media3/ExoPlayer version corrected from 1.9.0 to 1.8.0 (stable)
- **feat(player)**: Added `getPlayer(): Player?` method to `InternalPlayerSession` for UI attachment
- **feat(player)**: DataSource.Factory integration for custom sources
  - `InternalPlayerSession` now accepts `Map<DataSourceType, DataSource.Factory>` via constructor
  - ExoPlayer configured with appropriate `MediaSourceFactory` based on `PlaybackSource.dataSourceType`
  - `TelegramFileDataSource` now properly integrated for `tg://` URIs
- **feat(player)**: Created `PlayerDataSourceModule.kt` Hilt DI module
  - Provides `TelegramFileDataSourceFactory` for Telegram streaming
  - Provides `DefaultDataSource.Factory` for standard HTTP/file sources
- **deps**: Added `infra:transport-telegram` dependency to `player:internal` for Hilt wiring
- **docs**: Updated `PLAYER_MIGRATION_STATUS.md` with review fixes section

### Phase 5 – MiniPlayer Migration (2025-12-11)

- **feat(player)**: Created `player:miniplayer` module
  - `MiniPlayerState` immutable state model (visibility, mode, anchor, size, position)
  - `MiniPlayerMode` enum (NORMAL, RESIZE)
  - `MiniPlayerAnchor` enum (6 positions including center variants)
  - `MiniPlayerManager` interface for state transitions
  - `DefaultMiniPlayerManager` thread-safe implementation with StateFlow
  - `MiniPlayerCoordinator` for high-level fullscreen ↔ MiniPlayer orchestration
  - `PlayerWithMiniPlayerState` combined state for UI consumption
  - `MiniPlayerOverlay` Compose UI component with drag, resize, animations
  - Hilt DI module with `@Singleton` scoped manager
  - Unit tests for state machine logic

- **docs**: Updated PLAYER_MIGRATION_STATUS.md to Phase 5 Complete

---

### Pipeline Finalization (Phases 0-7 Complete)

#### Post-review hardening

- **Telegram chat classification**: added warm-up callback to trigger ingestion when COLD chats become WARM/HOT, unsuppresses suppressed chats.
- **Xtream globalId disambiguation**: avoid title collisions without year by seeding canonicalId with source identifiers.
- **Manual variant overrides**: playback orchestrator now honors an explicit SourceKey override before preference sorting.
- **Dead variant filtering**: Normalizer drops permanently dead variants via VariantHealthStore and skips empty groups.
- **Language detection**: unknown language now yields null (no device-language bias in VariantSelector).

Status: COMPLETED

#### Phase 0 – Global Data Model Extensions

- **feat(model)**: Added cross-pipeline identification types
  - `PipelineIdTag` enum: TELEGRAM, XTREAM, IO, AUDIOBOOK, UNKNOWN with short codes
  - `SourceKey` data class combining PipelineIdTag + sourceId for unique variant identification
  - `GlobalIdUtil` SHA-256 based canonical ID generator with title normalization
  - Extended `RawMediaMetadata` with `pipelineIdTag` and `globalId` fields

#### Phase 1 – Variant System

- **feat(model)**: Implemented variant selection system for cross-pipeline playback
  - `MediaVariant` data class with sourceKey, qualityTag, resolution, language, OmU flag, availability
  - `NormalizedMedia` data class for cross-pipeline merged media with variants list
  - `VariantSelector` score-based sorting algorithm (availability → language → quality → pipeline)
  - `MimeDecider` MIME/extension-based media type detection with `MimeMediaKind` enum

#### Phase 2 – Normalizer Enhancement

- **feat(normalizer)**: Enhanced `Normalizer.kt` in core:metadata-normalizer
  - Groups `RawMediaMetadata` by globalId for cross-pipeline deduplication
  - Creates `NormalizedMedia` with sorted variants per media item
  - Handles multi-source content unification

#### Phase 3 – Telegram Pipeline Enhancement

- **feat(telegram)**: Enhanced Telegram catalog pipeline
  - Updated `TelegramRawMetadataExtensions.kt` to set `pipelineIdTag = TELEGRAM`
  - Generates `globalId` via `GlobalIdUtil.generate()` for each item
  - `TelegramChatMediaProfile` for tracking media density per chat
  - `TelegramChatMediaClassifier` with Hot/Warm/Cold classification thresholds

#### Phase 4 – Xtream Pipeline Enhancement

- **feat(xtream)**: Enhanced Xtream catalog pipeline
  - Updated `XtreamRawMetadataExtensions.kt` for all content types
  - VOD, Series, Episode, Channel all set `pipelineIdTag = XTREAM`
  - All types generate `globalId` via `GlobalIdUtil.generate()`

#### Phase 5 – Playback Integration

- **feat(playback)**: Integrated variant system with playback layer
  - `TelegramMp4Validator` for MP4 moov atom validation (progressive downloads)
  - `VariantPlaybackOrchestrator` for variant-based playback with automatic fallback
  - Uses `VariantSelector.sorted()` for playback priority ordering

#### Phase 6 – Dead Media Detection

- **feat(model)**: Implemented variant health tracking
  - `VariantHealthStore` for tracking variant failures
  - Dead variant detection: ≥3 failures + 24h = permanently dead
  - `markFailed()`, `isAvailable()`, `isDeadPermanently()` APIs

#### Phase 7 – UI Settings Integration

- **feat(settings)**: Added playback settings persistence
  - `PlaybackSettingsRepository` DataStore-based persistence for `VariantPreferences`
  - Stores preferred language, OmU preference, auto-fallback toggle
- **feat(detail)**: Added manual variant selection support
  - `ManualVariantSelectionStore` in-memory store for user's variant choices
  - Supports per-media manual variant override

### Phase 3 – Player Migration (Phases 0-4 Complete)

Status: IN PROGRESS (Phase 4 Complete, Phases 5-14 Pending)

- **feat(player-model)**: Created `core:player-model` module with source-agnostic types
  - `SourceType` enum: TELEGRAM, XTREAM, AUDIOBOOK, IO, UNKNOWN
  - `PlaybackContext` with title, thumbnail, sourceType, extras map
  - `PlaybackState` enum: IDLE, BUFFERING, READY, PLAYING, PAUSED, ENDED, ERROR
  - `PlaybackError` sealed class with Playback, Source, Network, Unknown subtypes
- **feat(playback-domain)**: Enhanced playback contracts in `playback:domain`
  - `PlaybackSourceFactory` interface with `supports(SourceType)` + `createSource(PlaybackContext)`
  - `PlaybackSource` data class with URI + `DataSourceType`
  - `PlaybackSourceException` for factory error handling
  - Hilt DI module with `@IntoSet` binding pattern
- **feat(playback-telegram)**: Implemented Telegram playback factory
  - `TelegramPlaybackSourceFactoryImpl` builds `tg://file/<id>` URIs
  - `TelegramFileDataSource` for ExoPlayer integration (moved from player:internal)
  - `TelegramPlaybackModule` Hilt DI binding
- **feat(playback-xtream)**: Implemented Xtream playback factory
  - `XtreamPlaybackSourceFactoryImpl` builds authenticated stream URLs
  - Supports content types: `live`, `vod`, `series`
  - Uses `XtreamUrlBuilder` from transport layer
  - `XtreamPlaybackModule` Hilt DI binding
- **refactor(player-internal)**: Updated SIP core to use new types
  - `PlaybackSourceResolver` with factory injection via `Set<PlaybackSourceFactory>`
  - `InternalPlayerSession` uses `core:player-model.PlaybackContext`
  - `InternalPlayerState` uses `core:player-model.PlaybackState`
  - Removed layer violations: no more `pipeline:telegram` or TDLib dependencies
- **fix(layer-violations)**: Cleaned up forbidden cross-layer imports
  - Player no longer imports Pipeline DTOs
  - DataSources moved to `playback/*` modules
  - Transport layer used via interfaces only

### Phase 2.3 – Metadata Normalizer

Status: COMPLETED

- **feat(normalizer)**: Created `core:metadata-normalizer` module
  - `MediaMetadataNormalizer` interface for RawMediaMetadata → NormalizedMediaMetadata
  - `DefaultMediaMetadataNormalizer` with regex + TMDB integration
  - `TmdbMetadataResolver` interface with async lookup
  - `DefaultTmdbMetadataResolver` stub implementation
  - `SceneNameParser` / `RegexSceneNameParser` for title parsing
  - `ParsedSceneInfo` for extracted scene metadata (title, year, season, episode)
  - `MetadataNormalizerModule` Hilt DI binding
- **test(normalizer)**: Comprehensive test suite
  - `RegexMediaMetadataNormalizerTest` - title parsing edge cases
  - `DefaultTmdbMetadataResolverTest` - resolver integration
  - `DefaultMediaMetadataNormalizerTest` - end-to-end flow
  - `NormalizerHardeningTest` - robustness tests

### Phase 2.2 – Data Layer (Telegram & Xtream)

Status: COMPLETED

- **feat(data-telegram)**: Created `infra:data-telegram` module
  - `TelegramContentRepository` interface for content access
  - `ObxTelegramContentRepository` ObjectBox implementation
  - `TdlibTelegramContentRepository` TDLib-backed implementation
  - `TelegramDataModule` Hilt DI binding
- **feat(data-xtream)**: Created `infra:data-xtream` module
  - `XtreamCatalogRepository` interface for VOD/Series access
  - `ObxXtreamCatalogRepository` ObjectBox implementation
  - `XtreamLiveRepository` interface for live channels
  - `ObxXtreamLiveRepository` ObjectBox implementation
  - `XtreamDataModule` Hilt DI binding
- **feat(catalog-sync)**: Created `core:catalog-sync` module
  - `CatalogSyncContract` with `CatalogSyncService` interface
  - `SyncRequest`, `SyncResult`, `SyncStatus`, `SyncProgress` models
  - `DefaultCatalogSyncService` orchestrates pipeline → data flow
  - `CatalogSyncModule` Hilt DI binding

### Phase 2.1 – Transport Layer (Telegram & Xtream)

Status: COMPLETED

- **feat(transport-telegram)**: Created `infra:transport-telegram` module
  - `TelegramTransportClient` interface for TDLib operations
  - `DefaultTelegramTransportClient` TDLib adapter implementation
  - `TdlibClientProvider` for TDLib client lifecycle
  - `TelegramTransportModule` Hilt DI binding
- **feat(transport-xtream)**: Created `infra:transport-xtream` module
  - `XtreamApiClient` interface for Xtream API access
  - `DefaultXtreamApiClient` OkHttp implementation
  - `XtreamUrlBuilder` for URL construction with auth
  - `XtreamDiscovery` for port and capability detection
  - `XtreamApiModels` for API DTOs (XtreamLiveStream, XtreamVodStream, etc.)
  - `XtreamTransportModule` Hilt DI binding

### Phase 1.9 – Xtream Catalog Pipeline

Status: COMPLETED

- **feat(xtream)**: Implemented event-based catalog pipeline layer (analogous to Telegram)
  - New `XtreamCatalogPipeline` interface for stateless media scanning
  - `XtreamCatalogPipelineImpl` with 4-phase scanning (VOD → Series → Episodes → Live)
  - `XtreamCatalogEvent` sealed interface with:
    - `ScanStarted`, `ScanProgress`, `ScanCompleted`, `ScanCancelled`, `ScanError`
    - `ItemDiscovered` emitting `XtreamCatalogItem` with `RawMediaMetadata`
  - `XtreamCatalogConfig` with presets: `DEFAULT`, `VOD_ONLY`, `SERIES_ONLY`, `LIVE_ONLY`
  - `XtreamCatalogSource` interface for data source abstraction
  - `XtreamCatalogMapper` for mapping Xtream models to catalog items
  - `XtreamItemKind` enum: VOD, SERIES, EPISODE, LIVE
  - `XtreamScanPhase` enum for progress tracking
- **feat(di)**: Added Hilt DI support to pipeline/xtream module
  - `XtreamCatalogModule` binds pipeline and mapper
  - Added ksp and dagger.hilt.android plugins to build.gradle.kts
- **fix(xtream)**: Added missing `mediaType` field to `XtreamNormalizationExtensions`
  - Root cause: Competing extension functions without `mediaType` caused `UNKNOWN` values
  - Added `MediaType.MOVIE`, `MediaType.SERIES_EPISODE`, `MediaType.LIVE` as appropriate
  - Harmonized `sourceLabel` values across extension files
- **test(xtream)**: Added comprehensive catalog pipeline tests
  - `XtreamCatalogPipelineTest` (7 tests) - event flow, config filtering, error handling
  - `XtreamCatalogMapperTest` (7 tests) - all media type mappings with ImageRef
  - All catalog and model tests passing (33 tests)

### Phase 1.8 – Telegram Catalog Pipeline

Status: COMPLETED

- **feat(telegram)**: Implemented event-based catalog pipeline layer
  - New `TelegramCatalogPipeline` interface for stateless media scanning
  - `TelegramCatalogPipelineImpl` with pre-flight auth/connection checks
  - `TelegramCatalogEvent` sealed interface with:
    - `ScanStarted`, `ScanProgress`, `ScanCompleted`, `ScanCancelled`, `ScanError`
    - `ItemDiscovered` emitting `TelegramCatalogItem` with `RawMediaMetadata`
  - `TelegramCatalogConfig` for scan configuration (limits, filters, chat selection)
  - `TelegramMessageCursor` for cursor-based pagination with quota enforcement
- **feat(di)**: Added Hilt modules for catalog layer
  - `TelegramCatalogModule` binds `TelegramCatalogPipeline`
  - `TelegramCoreModule` provides `TelegramClient` via `TdlibClientProvider`
- **test(telegram)**: Added comprehensive catalog pipeline tests
  - `TelegramCatalogPipelineTest` for event flow verification
  - `TelegramMessageCursorTest` for pagination and quota tests
- **arch**: Integrated with existing v2 pipeline structure
  - Uses existing `TelegramClient` interface (not raw TDLib)
  - Uses existing `TelegramMediaItem.toRawMediaMetadata()` extension
  - Follows stateless producer pattern per v2 architecture

### Phase 1.7 – Test Stabilization

Status: COMPLETED

- **fix(tests)**: Fixed MockK runtime errors for g000sha256 TDLib DTOs
  - Root cause: g000sha256 TDLib DTOs are final data classes that MockK cannot mock
  - Solution: Created `TdlibTestFixtures` with real DTO factory methods
  - Updated `DefaultTelegramClientTest` to use `TdlibTestFixtures` instead of mocks
  - All 123 Telegram pipeline tests now passing
- **docs(agents)**: Added Pipeline Migration Philosophy section to `AGENTS.md`
  - New Section 12: "Pipeline Migration Philosophy (Telegram & Xtream)"
  - Documents that v1 pipelines are functionally proven and battle-tested
  - Establishes migration mindset: port good behavior, don't redesign from scratch
  - References legacy artifacts (JSON exports, CLI, contracts) as source of truth

### Phase 1.6 – Build Stabilization

Status: COMPLETED

- **fix(player)**: Updated `TelegramFileDataSource` to use v2 `TelegramClient` API
  - Replaced obsolete `TelegramTdlibClient` reference with `TelegramClient`
  - Updated `ensureFileReady()` → `requestFileDownload()` method call
  - Fixed all TDLib API property accesses for v2 compatibility
- **fix(xtream)**: Updated `XtreamUrlBuilder` to modern OkHttp 5.x API
  - Replaced deprecated `HttpUrl.parse()` with `toHttpUrlOrNull()` extension
  - Replaced deprecated function calls with property accesses:
    - `pathSegments()` → `pathSegments`
    - `host()` → `host`
    - `port()` → `port`
    - `scheme()` → `scheme`
- **fix(tests)**: Fixed TDLib test type mismatches in `DefaultTelegramClientTest`
  - Added helper functions `successResult()` and `failureResult()` for TdlResult mocking
  - Fixed `Long` vs `Int` type mismatch for `size` and `downloadedSize` properties
  - Fixed `ChatTypePrivate` constructor to use single-argument form
- **fix(tests)**: Fixed `TdlibMessageMapperTest` size property types
  - Removed `.toInt()` conversions to match TDLib v5.x `Long` types

### Phase 1 – Feature System (PLANNED)

- Core feature API: `FeatureId`, `FeatureRegistry`, `FeatureProvider`
- Feature contracts and wiring into `app-v2`
- First feature modules scaffolded

### Phase 2 – Pipelines → Canonical Media (PLANNED)

- Canonical media model finalized in `core/model`
- Pipeline stubs for Telegram, Xtream, Audiobook, IO
- Central metadata normalizer wired

### Phase 3 – SIP / Internal Player (PLANNED)

- Internal Player (SIP) integration from v1
- Playback domain contracts
- Resume, kids-mode, live/VOD support

### Phase 4 – UI Feature Screens (PLANNED)

- Home, Library, Live, Detail screens
- Telegram media UI
- Settings, Audiobooks

### Phase 5 – Quality & Performance (PLANNED)

- Telemetry integration
- Cache controls
- Performance profiling and optimizations

---

## 2025-12-08

### Phase 0.5 – Agents, V2 Portal, Branch Rules

Status: COMPLETED

- **docs(agents)**: Created unified `AGENTS.md` as single v2 agents ruleset
  - Defines allowed vs forbidden paths for all agents
  - Establishes legacy read-only policy
  - Documents pre- and post-change checklists
- **docs(portal)**: Created `V2_PORTAL.md` as v2 entry point
  - Links to all v2 architecture docs under `docs/v2/`
  - Describes v2 module structure and ownership
  - References key contracts for pipelines, player, and infra
- **chore(branch)**: Changed default branch to `architecture/v2-bootstrap`
- **chore(rulesets)**: Configured branch protection rules
  - No force pushes allowed
  - No branch deletion for protected branches
  - Main branch protected from premature merges
- **fix(docs)**: Corrected all `v2-docs/` → `docs/v2/` path references
  - Updated `AGENTS_V2.md` (now archived)
  - Updated `docs/v2/*.md` internal references
  - Updated `pipeline/**/*.kt` KDoc paths
  - Fixed `legacy/docs/UNIFIED_LOGGING.md` relative links
- **docs(reorg)**: Moved old agent files to `legacy/docs/agents/`
  - `AGENTS.md` (old v1 version)
  - `AGENTS_V2.md` (previous v2 version)

---

### Phase 0 – Legacy Cage & V2 Surface Cleanup

**Status: COMPLETED**

- **chore(cage)**: Moved entire v1 app module to `legacy/v1-app/`
  - All source code under `app/src/main/java/com/chris/m3usuite/`
  - All tests under `app/src/test/`
  - All resources and assets
- **chore(gradle)**: Cleaned `settings.gradle.kts`
  - Removed `:app` module include
  - Kept only v2 modules: `:app-v2`, `:core:*`, `:infra:*`, `:feature:*`, `:player:*`, `:playback:*`, `:pipeline:*`
- **chore(scripts)**: Reorganized scripts
  - Build helpers moved to `scripts/build/` (safe-build.sh, wrappers)
  - API probe scripts moved to `scripts/api-tests/` (konigtv, xtream tests)
- **chore(docs)**: Split documentation
  - V2 docs placed under `docs/v2/`
  - Legacy docs placed under `legacy/docs/`
  - Meta docs (build, quality, workspace) under `docs/meta/`
- **chore(cleanup)**: Removed stale files
  - Deleted `tools/tdlib neu.zip` archive
  - Removed duplicate root scripts
- **fix(build)**: Added Kotlin serialization plugin to `core/persistence`
  - Added `org.jetbrains.kotlin.plugin.serialization`
  - Added `kotlinx-serialization-json:1.7.3` dependency
  - Fixes Kapt stub generation errors for ObjectBox entities

---

## Version History

| Version | Date | Phase | Status |
|---------|------|-------|--------|
| v2.0.0-alpha | TBD | Phase 1+ | PLANNED |
| v2-bootstrap | 2025-12-08 | Phase 0/0.5 | COMPLETED |

---

## Related Documents

- [V2 Portal](V2_PORTAL.md) – Entry point for v2 architecture
- [Roadmap](ROADMAP.md) – v2 implementation phases
- [Architecture Overview](docs/v2/ARCHITECTURE_OVERVIEW_V2.md) – Detailed v2 architecture
- [AGENTS.md](AGENTS.md) – Agent rules for v2 development
