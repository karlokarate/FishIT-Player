# FishIT Player v2 – Architecture Overview

> This document defines the **top-level architecture** for FishIT Player v2.  
> It describes layers, modules, dependencies, and responsibilities.

---

## 📊 Current Status (Dec 2025)

**Overall Progress: 25-30% to MVP Release**

| Phase | Status | Progress | Target |
|-------|--------|----------|--------|
| Phase 0/0.5 | ✅ Complete | 100% | Dec 2025 |
| Phase 1 | 🚧 In Progress | 60% | Dec 2025 |
| Phase 2 | 🟡 Partial | 50% | Jan 2026 |
| Phase 3 | 🚧 Started | 15% (Phase 3 core done) | Jan-Feb 2026 |
| Phase 4 | 🔲 Planned | 10% | Feb-Mar 2026 |
| Phase 5 | 🔲 Planned | 5% | Mar 2026 |

**Latest Milestone:** Phase 3 SIP-Kern Migration completed (Dec 9, 2025)
- New `core:player-model` module with clean types
- `PlaybackSourceResolver` with Factory pattern
- Layer violations fixed

See [V2_RELEASE_READINESS_ASSESSMENT.md](../../V2_RELEASE_READINESS_ASSESSMENT.md) for detailed analysis.

---

## ⚡ v1 Porting Reference

> **IMPORTANT:** Before implementing any module, consult
> **`V1_VS_V2_ANALYSIS_REPORT.md`** for:
>
> - **Tier 1 systems** (port directly): SIP Player, UnifiedLog, FocusKit, Fish* Layout, Xtream Pipeline, AppImageLoader
> - **Tier 2 systems** (minor adapt): PlaybackSession, DetailScaffold, MediaActionBar, TvButtons, MiniPlayer
> - **Complete file mapping**: Appendix A lists ~17,000 lines with exact v1→v2 module targets
>
> Many v2 modules already have production-tested v1 implementations ready to port.

---

The v2 architecture is built **inside the existing repository**, but as a new generation alongside the legacy app.

We use a **Strangler pattern**:

- Legacy app (v1) remains as `:app` and related modules.
- New app generation lives in:
  - `:app-v2` and associated v2 modules.
- Over time, v2 fully replaces v1 as the main entrypoint.

---

## 1. Layers

From top (closest to user) to bottom (infrastructure):

1. **AppShell**
2. **Feature Shells (UI)**
3. **Pipelines**
4. **PlaybackDomain**
5. **Internal Player (SIP)**
6. **Core & Infrastructure**

Each layer has a clear responsibility and must only depend on allowed layers below or beside it as defined in this document.

---

## 2. Modules (Gradle) – v2 Generation

All v2 modules live in the existing repo but are separate from the legacy app.

### 2.1 App & Core

- `:app-v2`
  - New entry app module for FishIT Player v2.
  - Contains:
    - **AppShell** (Compose Navigation host, DI wiring, top-level state).
    - Minimal startup UI.
  - Must **not** contain:
    - Pipeline logic
    - Player logic
    - Telegram/Xtream SDK usage

- `:core:model`
  - Cross-cutting domain models and types:
    - `PlaybackContext`, `PlaybackType`
    - `Profile`, `KidsProfileInfo`
    - `FeatureId`, `FeatureDescriptor`
    - `DeviceProfile` (phone/tablet/tv)
    - Generic error/result types
  - Pure Kotlin, no Android UI dependencies.

- `:core:persistence`
  - Persistence abstractions and implementations:
    - DataStore wrappers
    - Local DB (ObjectBox - reused from v1)
    - File storage abstractions
  - Repository interfaces and/or implementations for:
    - `ProfileRepository` (backed by `ObxProfile`, `ObxProfilePermissions`)
    - `EntitlementRepository`
    - `LocalMediaRepository` (for generic IO content)
    - `SubtitleStyleStore` (persistence for subtitle style preferences)
    - `ResumeRepository` (backed by `ObxResumeMark`)
    - Cached feature flags / entitlements
    - `CanonicalMediaRepository` (stores canonical media and source references - see `MEDIA_NORMALIZATION_CONTRACT.md`)
  - ObjectBox entities ported from v1:
    - `ObxCategory`, `ObxLive`, `ObxVod`, `ObxSeries`, `ObxEpisode`
    - `ObxEpgNowNext`, `ObxProfile`, `ObxProfilePermissions`
    - `ObxResumeMark`, `ObxTelegramMessage`

- `:core:metadata-normalizer`
  - **Status: Phase 3 skeleton complete** – Module exists with interfaces and no-op implementations
  - **Cross-pipeline media normalization and identity** (see `MEDIA_NORMALIZATION_AND_UNIFICATION.md` and `MEDIA_NORMALIZATION_CONTRACT.md`)
  - Types (defined in `:core:model`):
    - `RawMediaMetadata` - Raw metadata from pipelines ✅
    - `NormalizedMediaMetadata` - Normalized, canonical metadata ✅
    - `CanonicalMediaId` - Global media identity ✅
  - Services (defined in `:core:metadata-normalizer`):
    - `MediaMetadataNormalizer` - Title cleaning, scene-naming parser, structural extraction ✅ (interface + default no-op)
    - `TmdbMetadataResolver` - TMDB search and enrichment via `tmdb-java` ✅ (interface + default no-op)
  - Processing flow:
    ```
    Pipeline → RawMediaMetadata → MediaMetadataNormalizer → 
    NormalizedMediaMetadata → TmdbMetadataResolver → 
    Enriched Metadata → CanonicalMediaId → Storage
    ```
  - Dependencies:
    - `:core:model` ✅
    - `tmdb-java` (Apache 2.0) for TMDB API integration ✅ (dependency added, no usage yet)
    - Scene-naming regex engine (custom Kotlin, inspired by Sonarr/Radarr/GuessIt/FileBot) - Future implementation
  - MUST NOT depend on:
    - Any `:pipeline:*` modules
    - Any UI framework

- `:core:firebase`
  - Firebase facade module.
  - Interfaces live in `core:model` / `core:persistence`, implementations here:
    - `FirebaseFeatureFlagProvider` (implements `FeatureFlagProvider`)
    - `FirebaseRemoteProfileStore` (implements `RemoteProfileStore`)
  - Integrates only in **Phase 5+** of implementation.
  - v2 must be able to run without active Firebase configuration by using local fallback implementations.

---

### 2.2 Playback Domain & Internal Player

- `:playback:domain`
  - Pipeline-agnostic playback logic.
  - Contains **interfaces and default implementations** for:
    - `ResumeManager`
    - `KidsPlaybackGate`
    - `SubtitleStyleManager`
    - `SubtitleSelectionPolicy`
    - `LivePlaybackController`
    - `TvInputController`
  - May depend on:
    - `:core:model`
    - `:core:persistence`
    - `:infra:logging`
  - Must **not** depend on:
    - Any `:pipeline:*` module
    - Any UI framework (Compose, Views)
    - ExoPlayer / Media3 directly
  - Pipelines integrate into this layer via:
    - Repositories or data providers that implement interfaces defined in `core:model` or `playback:domain`.

- `:player:internal`
  - The v2 **Structured Internal Player** (SIP).
  - Uses ExoPlayer / Media3 and the services from `:playback:domain`.
  - Structure (package-level):

    - `internal.state`
      - Internal player state models (`InternalPlayerState`, buffering flags, track info etc.)
    - `internal.session`
      - `InternalPlayerSession` orchestrating:
        - ExoPlayer instance(s)
        - `ResumeManager`
        - `KidsPlaybackGate`
        - `SubtitleStyleManager`
        - `LivePlaybackController`
        - `TvInputController`
    - `internal.source`
      - `InternalPlaybackSourceResolver`
      - MediaItem and DataSource factory logic for each pipeline type via pipeline-agnostic `PlaybackContext`.
    - `internal.ui`
      - `InternalPlayerControls` (Compose)
      - `PlayerSurface`
      - Error overlays, minimal HUD, CC menu dialog
    - `internal.subtitles`
      - Subtitle view integration
      - Application of `SubtitleStyleManager` and `SubtitleSelectionPolicy`
    - `internal.live`
      - Live-TV in-player controls and EPG overlay state (using `LivePlaybackController` from `playback:domain`)
    - `internal.tv`
      - `TvInputController` integration with focus & DPAD actions
    - `internal.mini`
      - Mini-player and PiP orchestration
    - `internal.system`
      - System UI behavior, rotation, audio focus
    - `internal.debug`
      - `PlayerDiagnostics`
      - Player debug overlay & debug screen

  - Public entrypoint:
    - `InternalPlayerEntry(playbackContext: PlaybackContext, ...)` Composable
  - Must **not** depend on:
    - `:feature:*` modules
    - `:pipeline:*` modules directly  
    Instead, it resolves media via `InternalPlaybackSourceResolver` configured with pipeline-provided factories.

---

### 2.3 Pipelines

Each pipeline module is responsible for **one content source** and contains **no UI**.

- `:pipeline:telegram`
  - Responsible for Telegram media integration.
  - Contains:
    - Telegram domain models (e.g. `TelegramMediaItem`, `TelegramChatId`)
    - `TelegramContentRepository` interface + implementation
    - `TelegramDownloadManager` interface + implementation
    - `TelegramStreamingSettingsProvider`
    - `TelegramPlaybackSourceFactory`:
      - Converts a `TelegramMediaItem` + `PlaybackContext` into a data structure the `InternalPlaybackSourceResolver` can use (e.g. wrapping `TelegramFileDataSource`).
    - tdlib-coroutines integration:
      - `T_TelegramServiceClient`
      - `T_TelegramFileDownloader`
      - `TelegramFileDataSource`
  - **Ported from v1** (MUST reuse):
    - `T_TelegramServiceClient` - Core TDLib integration (auth, connection, sync states)
    - `T_TelegramFileDownloader` - Download queue with priority system
    - `TelegramFileDataSource` - Zero-copy Media3 DataSource for `tg://` URLs
    - `TelegramStreamingSettingsProvider` - Streaming configuration
    - `Mp4HeaderParser` - MP4 validation before playback
    - `RarDataSource` + `RarEntryRandomAccessSource` - RAR archive entry streaming
  - Contains helper extensions like:
    - `fun TelegramMediaItem.toPlaybackContext(...): PlaybackContext`
    - `fun TelegramMediaItem.toRawMediaMetadata(): RawMediaMetadata` (for normalization - see `MEDIA_NORMALIZATION_CONTRACT.md`)
  - Must **not**:
    - Use Compose or any UI elements.
    - Depend on `:feature:*` modules.
    - Depend on `:player:internal`.
    - Perform title normalization or TMDB lookups (handled by `:core:metadata-normalizer`)

- `:pipeline:xtream`
  - Xtream / IPTV integration.
  - Contains:
    - Xtream domain models (VOD, Series, LiveChannel, EPG entries).
    - `XtreamCatalogRepository` (VOD/Series)
    - `XtreamLiveRepository` (channels + EPG)
    - `XtreamPlaybackSourceFactory`
    - HTTP/URL-building code
    - DataSource implementations reused from v1:
      - `DelegatingDataSourceFactory`
      - `RarDataSource`
  - **Ported from v1** (MUST reuse):
    - `DelegatingDataSourceFactory` - Routes URLs to correct DataSource by scheme
    - `XtreamObxRepository` - ObjectBox-backed content queries (adapted to v2 interfaces)
  - Contains helper extensions:
    - `fun XtreamVodItem.toRawMediaMetadata(): RawMediaMetadata` (for normalization)
    - `fun XtreamSeriesItem.toRawMediaMetadata(): RawMediaMetadata`
    - `fun XtreamEpisode.toRawMediaMetadata(): RawMediaMetadata`
  - Must **not**:
    - Contain UI.
    - Depend on `:feature:*` or `:player:internal`.
    - Perform title normalization or TMDB lookups

- `:pipeline:io`
  - Local / IO content integration.
  - Contains:
    - `IoMediaItem`
    - `IoContentRepository` (local storage, SAF, future network shares)
    - `IoPlaybackSourceFactory`
    - `fun IoMediaItem.toRawMediaMetadata(): RawMediaMetadata`
  - Initial implementation may be minimal, but the module and APIs exist from day one.

- `:pipeline:audiobook`
  - Audiobook integration.
  - Contains:
    - `AudiobookItem`
    - `AudiobookRepository`
    - `AudiobookPlaybackSourceFactory`
    - `fun AudiobookItem.toRawMediaMetadata(): RawMediaMetadata`
  - Can start as a minimal skeleton; real behavior is introduced in later phases.

Pipelines use interfaces defined in `core:model` / `playback:domain`, not the other way around.

**Pipeline Contract for Metadata (binding):**

All pipelines MUST comply with `MEDIA_NORMALIZATION_CONTRACT.md`:
- Provide `toRawMediaMetadata()` for all media items
- Never perform title normalization or cleaning
- Never conduct TMDB or external database searches
- Never attempt cross-pipeline media matching or identity decisions
- Pass through external IDs (like TMDB IDs) only if provided by the source

---

### 2.4 Feature Shells (UI Modules)

Feature modules represent user-facing entry points. They contain only UI and ViewModel logic.

- `:feature:home`
  - Global start screen.
  - Responsibilities:
    - Show “Continue Watching” across pipelines.
    - Show entry tiles for active pipelines and features.
    - Optionally show locally computed recommendations.
  - Depends on:
    - `:core:model`, `:core:persistence`
    - `:playback:domain`
    - `:player:internal`
    - Pipeline repositories (for aggregated views)
    - `:infra:logging`

- `:feature:library`
  - Unified library of VOD/Series.
  - Responsibilities:
    - Browse, filter, search content across pipelines.
  - Depends on:
    - `:core:model`
    - `:pipeline:xtream`, `:pipeline:telegram`, `:pipeline:io` (via interfaces)
    - `:playback:domain`, `:player:internal`

- `:feature:live`
  - Live-TV feature shell.
  - Responsibilities:
    - Channel list UI.
    - EPG UI.
    - Integration with `LivePlaybackController` and pipelines.
  - Depends on:
    - `:playback:domain`
    - `:pipeline:xtream`
    - `:player:internal`

- `:feature:telegram-media`
  - Telegram-specific media shell.
  - Responsibilities:
    - Show Telegram media organized into sections (e.g., saved, channels, chats).
    - Search, filter, and open playback.
  - Depends on:
    - `:pipeline:telegram`
    - `:playback:domain`
    - `:player:internal`

- `:feature:audiobooks`
  - Audiobook shell.
  - Responsibilities:
    - Audiobook browsing and playback controls.
    - May start with simple placeholder UI in early phases.
  - Depends on:
    - `:pipeline:audiobook`
    - `:playback:domain`
    - `:player:internal`

- `:feature:settings`
  - Settings shell.
  - Responsibilities:
    - Profile management (including kids profiles).
    - Subtitle style configuration.
    - Playback preferences.
    - Feature visibility based on entitlements and Firebase flags.
  - Depends on:
    - `:core:model`, `:core:persistence`
    - `:core:firebase` (for reading flags/entitlements via interfaces)
    - `:playback:domain`

All feature modules must:

- Use string resources for UI text.
- Provide both English and German string entries for user-facing text from the beginning.

---

### 2.5 Infrastructure

- `:infra:logging`
  - Unified logging abstraction:
    - `UnifiedLog`
    - Integration points for:
      - Android logcat
      - Firebase Crashlytics
    - `TelegramLogRepository` bridge if needed
    - Player diagnostics integration from `:player:internal`
  - No app logic.

- `:infra:tooling`
  - Developer & QA tooling:
    - Debug menu(s)
    - Dev flags (local only)
    - Static analysis configuration (detekt, ktlint)
    - Optional ArchUnit tests for enforcing layering rules
  - No app logic.

---

## 3. Legacy v1 Coexistence

The existing app (v1) continues to exist as:

- `:app` and its associated modules (legacy pipelines, legacy InternalPlayerScreen, etc.).

Rules:

- No new features are added to v1.
- v1 code is used only as:
  - Behavioral reference
  - Source for porting selected implementations into v2 modules
- v2 modules must build and run independently of v1.

Once v2 reaches parity:

- v2 (`:app-v2`) becomes the primary app target.
- v1 may be:
  - Archived into a `legacy/v1` branch and/or
  - Kept as `:app-legacy` or similar, for historical reference only.

---

## 4. Dependency Rules

### 4.1 Allowed Dependencies

- `:app-v2` **may depend on**:
  - `:core:*`
  - `:playback:domain`
  - `:player:internal`
  - `:pipeline:*`
  - `:feature:*`
  - `:infra:*`

- `:feature:*` **may depend on**:
  - `:core:*`
  - `:playback:domain`
  - `:player:internal` (for `InternalPlayerEntry`)
  - Relevant `:pipeline:*` modules
  - `:infra:logging`

- `:pipeline:*` **may depend on**:
  - `:core:model`
  - `:core:persistence`
  - `:core:metadata-normalizer` (only for accessing `RawMediaMetadata` type, NOT for calling normalizer services)
  - `:infra:logging`
  - External SDKs relevant to that pipeline (tdlib, HTTP clients, etc.)

- `:playback:domain` **may depend on**:
  - `:core:model`
  - `:core:persistence`
  - `:infra:logging`

- `:player:internal` **may depend on**:
  - `:core:model`
  - `:playback:domain`
  - `:infra:logging`
  - Media3 / ExoPlayer

- `:core:*` **may depend on**:
  - Kotlin stdlib
  - Minimal AndroidX/core libs as needed
  - External libraries for their specific purpose (e.g., `:core:metadata-normalizer` may depend on `tmdb-java`)
  - No feature, pipeline or player modules

- `:infra:*` **may depend on**:
  - `:core:*`
  - External tooling libraries (Firebase Crashlytics, LeakCanary, etc.)

### 4.2 Forbidden Dependencies

- `:feature:*` MUST NOT:
  - Depend directly on tdlib classes or Telegram SDK.
  - Depend directly on ExoPlayer / Media3.
  - Depend on other `:feature:*` modules unless explicitly defined (e.g. home may know about feature descriptors, not internals).

- `:pipeline:*` MUST NOT:
  - Depend on any `:feature:*` modules.
  - Depend on `:player:internal`.
  - Use Compose or any UI framework.
  - Perform title normalization, cleaning, or heuristics (violation of `MEDIA_NORMALIZATION_CONTRACT.md`).
  - Conduct TMDB, IMDB, TVDB or any external metadata searches.
  - Compute or decide `CanonicalMediaId` or cross-pipeline identity.

- `:player:internal` MUST NOT:
  - Depend on `:feature:*`.
  - Depend directly on `:pipeline:*` modules.
    - All pipeline-specific logic must be expressed via factories/repositories passed in from higher layers/config.

- `:playback:domain` MUST NOT:
  - Depend on any `:pipeline:*`.
  - Depend on Compose or Views.
  - Depend on Firebase directly.

Violations of these rules must be treated as architecture errors.  
Static analysis (detekt, ArchUnit or equivalent) will be configured to detect and prevent such violations.

---

## 5. AppShell Responsibilities

`AppShell` lives in `:app-v2` and is responsible for:

- **Startup sequence**:
  - Determine `DeviceProfile` (phone/tablet/tv).
  - Load local profiles & entitlements via `ProfileRepository` and `EntitlementRepository`.
  - Initialize DI graph (Hilt).
  - Initialize pipelines lazily or in background (never block initial UI).
- **Navigation**:
  - Provide a Compose Navigation host.
  - Define routes for all feature shells.
  - Use a `FeatureRegistry` to know which features exist and are enabled.

- **FeatureRegistry**:
  - Interface in `:core:model`.
  - Implementation in `:app-v2` (or a core helper), aggregating descriptors from `:feature:*` modules.
  - Each feature module exposes a `FeatureDescriptor` and registration hook (via DI or a static provider).

AppShell **does not**:

- Implement pipeline logic.
- Talk directly to Telegram / Xtream / IO SDKs.
- Embed ExoPlayer or playback logic.

---

## 6. Internationalization Rules (Architecture-Level)

- All `:feature:*` and `:player:internal` UI modules must:
  - Use Android string resources for user-facing text.
  - Provide English and German resource entries for each user-facing string from day one.
- Developer-only debug UI:
  - May be English-only,
  - But must still use string resources defined in `values/` (and optionally mirrored in `values-de/`).

Architecture tasks and code generators must treat “hardcoded user-facing text” as a violation.

---

## 7. Multi-Agent / Parallel Work

To safely allow multiple agents or developers to work in parallel:

- Tasks must be scoped narrowly:
  - Prefer tasks that focus on a single module or a very small set of modules.
  - Cross-module refactors require dedicated architectural tasks.
- Feature tasks:
  - Must not modify pipeline or player internals unless explicitly stated.
- Pipeline tasks:
  - Must not modify feature modules unless explicitly stated.
- `:app-v2` and `:core:*`:
  - Should change infrequently and only in dedicated tasks (e.g. adding a new feature route, adding a new shared model).

This minimizes merge conflicts and keeps responsibilities clear.

---

## 8. Instructions for AI / Agents

When AI assistants work with this architecture:

1. **Always read**:
   - `APP_VISION_AND_SCOPE.md`
   - `ARCHITECTURE_OVERVIEW_V2.md`
   - `IMPLEMENTATION_PHASES_V2.md`
   - `MEDIA_NORMALIZATION_CONTRACT.md` (when working on pipelines, metadata, or canonical identity)
   - before touching v2 modules.

2. **Respect dependency rules**:
   - Do not introduce new module dependencies that violate Section 4.
   - If a required dependency seems to break the rules, update this document first with a clear justification.

3. **No “Option A / B” architecture proposals in code**:
   - For architecture-level decisions, pick a single best-practice direction that is consistent with this document.
   - If a choice is truly unclear, propose a concrete amendment to this document before implementing.

4. **i18n compliance**:
   - Never hardcode user-facing strings.
   - Add new strings to both `values/strings.xml` and `values-de/strings.xml`.

5. **Scope of tasks**:
   - Restrict code changes to the modules explicitly mentioned in the task description.
   - Do not “drive-by” modify other modules.

This architecture overview is the **structural contract** of v2.  
The implementation phases document describes the order in which these structures are realized.
