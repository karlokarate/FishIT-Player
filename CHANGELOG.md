# FishIT-Player v2 – Changelog

This changelog tracks all changes starting from the v2 rebuild on branch `architecture/v2-bootstrap`.

For v1 history prior to the rebuild, see `legacy/docs/CHANGELOG_v1.md`.

---

## [Unreleased]

### Phase 1.7 – Test Stabilization

**Status: COMPLETED**

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

**Status: COMPLETED**

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

**Status: COMPLETED**

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
