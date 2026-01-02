# FishIT Player v2 – Implementation Phases

> This document defines the **build order** for FishIT Player v2.  
> It is designed to be both human-readable and machine-usable so that agents can implement v2 without inventing new architecture.

We follow the Strangler pattern:

- v2 lives in a **new branch** in the existing repo.
- Legacy app remains intact as a reference.
- v2 eventually becomes the primary app.

---

## ⚡ CRITICAL: v1 Quality Reference

> **Before starting any phase**, agents MUST consult **`V1_VS_V2_ANALYSIS_REPORT.md`**.
>
> This report documents:
> - **Tier 1 systems** (~5000+ lines each): Port directly without rewriting
> - **Tier 2 systems**: Port with minor adaptation
> - **Appendix A**: Complete v1→v2 file mapping (~17,000 lines)
> - **Appendix C**: Phase-specific contract documents (Phase 4-8)
>
> **Do NOT reinvent existing v1 abstractions.** The analysis report identifies
> production-quality components that have been refined over months.

---

## Global rule – contracts before code

Behavior and architecture MUST NEVER be changed only in code.
Any change to behavior or architecture MUST be reflected in the relevant markdown contract first,
and only then implemented in code.

---

## 0. Branching Strategy

### 0.1 v2 Architecture Branch

- The v2 work happens on a dedicated branch:

  - Branch name: `architecture/v2-bootstrap`

- All v2-focused tasks must be based on this branch until v2 is ready to be merged into `main`.

### 0.2 Legacy App Behavior

- Legacy app module (`:app` and related code) remains intact.
- No new v2 features are added to legacy modules.
- Legacy code is used only as a:
  - behavioral reference,
  - source of stable implementations that can be ported into v2 modules.

---

## Phase 0 – Module Skeleton & Minimal AppShell

**Goal:** Set up the v2 module structure so that the project compiles and `:app-v2` launches a minimal Compose screen.

### Allowed modules to modify in Phase 0

- `settings.gradle`
- `:app-v2`
- `:core:model`
- `:core:persistence`
- `:core:metadata-normalizer`
- `:core:firebase`
- `:playback:domain`
- `:player:internal`
- `:pipeline:telegram`
- `:pipeline:xtream`
- `:pipeline:io`
- `:pipeline:audiobook`
- `:feature:home`
- `:feature:library`
- `:feature:live`
- `:feature:telegram-media`
- `:feature:audiobooks`
- `:feature:settings`
- `:infra:logging`
- `:infra:tooling`

Legacy modules MUST NOT be modified.

### Checklist

- [ ] Add v2 modules to `settings.gradle` as defined in the architecture overview.
- [ ] Create minimal `build.gradle.kts` (or Gradle files) for each new module with:
  - [ ] Correct plugin setup.
  - [ ] Basic dependencies consistent with `ARCHITECTURE_OVERVIEW_V2.md`.
- [ ] In `:app-v2`:
  - [ ] Add a minimal `MainActivity` using Jetpack Compose.
  - [ ] Use Hilt for DI setup (Application class, Hilt annotations).
  - [ ] Set up Compose Navigation with a single route: `DebugSkeletonScreen`.
  - [ ] Display text from string resources: `"FishIT Player v2 – Skeleton"`.
  - [ ] Provide English and German translations for this text.
- [ ] In each new module:
  - [ ] Add minimal package structure (empty or with simple marker interfaces/classes).
  - [ ] Add a small `README.md` or package-level KDoc if helpful (not mandatory).

**Notes:**

- No real business logic is ported in Phase 0.
- Focus solely on module structure, build configuration, DI shell, and a compiling app.

---

## Phase 1 – Playback Core & Internal Player Bootstrap

**Goal:** Get the v2 internal player (SIP) running in isolation using a simple HTTP test stream, without pipeline integration.

### Allowed modules to modify in Phase 1

- `:core:model`
- `:playback:domain`
- `:player:internal`
- `:feature:home`
- `:infra:logging`

### Checklist

> **Note (2025-12-16):** `PlaybackContext` is defined in `:core:player-model`, NOT `:core:model`.
> This doc has outdated module references that will be corrected during implementation.

- [x] In `:core:player-model` (was `:core:model`):
  - [x] Define `enum class SourceType { TELEGRAM, XTREAM, AUDIOBOOK, IO, UNKNOWN }`.
  - [x] Define `data class PlaybackContext(...)` with fields for playback.
- [x] In `:playback:domain`:
  - [x] Declare interfaces:
    - `PlaybackSourceFactory`
    - `ResumeManager` (interface)
    - `KidsPlaybackGate` (interface)
  - [x] Provide Hilt multibindings setup.
- [x] In `:player:internal`:
  - [x] Port and adapt the v2 SIP code:
    - `InternalPlayerState` and related state models.
    - `InternalPlayerSession` wired to use `:playback:domain` interfaces.
    - `PlaybackSourceResolver` with `Set<PlaybackSourceFactory>` injection.
  - [x] Expose `DebugPlaybackScreen` for testing.
- [x] In `:feature:home`:
  - [x] Add `DebugPlaybackScreen` with test stream (Big Buck Bunny).
- [x] In `:app-v2`:
  - [x] Navigation to `DebugPlaybackScreen` available.

**Result:**  
At the end of Phase 1, `:app-v2` launches, and the SIP internal player can play a test HTTP stream via `DebugPlaybackScreen`.

---

## Phase 2 – Telegram Pipeline Integration

**Goal:** Integrate Telegram as the first real pipeline into v2, using tdlib-coroutines and the new pipeline/feature structure.

### Allowed modules to modify in Phase 2

- `:pipeline:telegram`
- `:feature:telegram-media`
- `:player:internal`
- `:playback:domain` (only if strictly necessary)
- `:feature:home`
- `:infra:logging`
- `:core:persistence` (for ObjectBox entity reuse)
- `:core:metadata-normalizer` (only for `RawMediaMetadata` type definition, NOT implementation)

Legacy Telegram code is read-only; logic is ported, not reused directly.

### v1 Component Reuse (MUST port)

The following v1 components must be ported directly to `:pipeline:telegram`:
- `T_TelegramServiceClient` - Core TDLib integration (auth, connection, sync states)
- `T_TelegramFileDownloader` - Download queue with priority system
- `TelegramFileDataSource` - Zero-copy Media3 DataSource for `tg://` URLs
- `TelegramStreamingSettingsProvider` - Streaming configuration
- `Mp4HeaderParser` - MP4 validation before playback
- `RarDataSource` + `RarEntryRandomAccessSource` - RAR archive entry streaming

### ObjectBox Reuse

- Port `ObxStore` singleton pattern to `:core:persistence`
- Reuse v1 entity definitions (`ObxEntities.kt`) directly - specifically `ObxTelegramMessage`
- Implement/maintain v2 `ResumeManager` backed by canonical media storage (`CanonicalMediaRepository`)

### Checklist

- [ ] In `:core:metadata-normalizer`:
  - [ ] Define `RawMediaMetadata` data class (see `MEDIA_NORMALIZATION_CONTRACT.md` Section 1.1).
  - [ ] This type will be used by pipelines in Phase 2+.
  - [ ] No normalization logic is implemented yet—only the type definition.
- [ ] In `:pipeline:telegram`:
  - [ ] Define Telegram domain models:
    - `TelegramMediaItem`
    - IDs / metadata types as needed.
  - [ ] Define interfaces:
    - `TelegramContentRepository`:
      - List media items by filters (e.g. recents, chats, saved).
      - Paging and refresh operations.
    - `TelegramDownloadManager`:
      - Start/stop download.
      - Query download state (progress, completed, failed).
    - `TelegramStreamingSettingsProvider`.
    - `TelegramPlaybackSourceFactory`:
      - Converts `TelegramMediaItem` and `PlaybackContext` into a telegram-specific source descriptor usable by `InternalPlaybackSourceResolver`.
  - [ ] Port tdlib-coroutines integration from legacy code:
    - `T_TelegramServiceClient`
    - `T_TelegramFileDownloader`
    - `TelegramFileDataSource`
  - [ ] Provide default implementations for `TelegramContentRepository` and `TelegramDownloadManager`.
  - [ ] Add helper:
    - `fun TelegramMediaItem.toPlaybackContext(...): PlaybackContext`
    - This MUST live in `:pipeline:telegram`, not in `:playback:domain`.
  - [ ] **Prepare for normalization (stub only for now):**
    - Add stub: `fun TelegramMediaItem.toRawMediaMetadata(): RawMediaMetadata`
    - This function extracts raw metadata as-is from the Telegram item (no cleaning, no heuristics).
    - See `MEDIA_NORMALIZATION_CONTRACT.md` Section 2.1 for pipeline responsibilities.
    - Full implementation will be validated in Phase 3+.

- [ ] In `:player:internal`:
  - [ ] Extend `InternalPlaybackSourceResolver` so that:
    - For `PlaybackType.TELEGRAM`, it uses `TelegramPlaybackSourceFactory` injected via DI/config.
    - It uses `TelegramFileDataSource` behind the scenes, not legacy direct Telelgram logic.
- [ ] In `:feature:telegram-media`:
  - [ ] Implement `TelegramShellState` and a ViewModel that:
    - Uses `TelegramContentRepository` to load media sections.
    - Emits a UI state suitable for list/grid rendering.
  - [ ] Implement UI:
    - List/grid of `TelegramMediaItem`s.
    - On click: build `PlaybackContext` via `TelegramMediaItem.toPlaybackContext(...)` and navigate to `InternalPlayerEntry`.
  - [ ] All UI strings must be in English and German string resources.

- [ ] In `:feature:home`:
  - [ ] Add a “Telegram” entry tile that navigates to the Telegram media shell.

**Result:**  
At the end of Phase 2, users can browse Telegram media in v2 and play it with the internal player, using tdlib-coroutines behind the scenes.

---

## Phase 3 – Xtream Pipeline Integration

**Goal:** Integrate Xtream / IPTV as a second real pipeline in v2, reusing stable logic from v1 where appropriate.

### Allowed modules to modify in Phase 3

- `:pipeline:xtream`
- `:feature:library`
- `:feature:live`
- `:player:internal`
- `:playback:domain` (if needed for Live behavior)
- `:feature:home`
- `:infra:logging`
- `:core:metadata-normalizer` (implementation begins here)

### Checklist

- [x] **Phase 3A: Metadata Normalization Core (Skeleton)**
  - [x] In `:core:metadata-normalizer`:
    - [x] **Module created** with Gradle configuration (Kotlin + coroutines)
    - [x] **Dependency on `:core:model`** added
    - [x] **Dependency on `tmdb-java`** added (version 2.11.0, no usage yet)
    - [x] Define `NormalizedMediaMetadata` data class in `:core:model` (see `MEDIA_NORMALIZATION_CONTRACT.md` Section 1.2)
    - [x] Define `CanonicalMediaId` and `MediaKind` in `:core:model` (see `MEDIA_NORMALIZATION_CONTRACT.md` Section 1.3)
    - [x] Define `MediaMetadataNormalizer` interface in `:core:metadata-normalizer`
    - [x] Define `TmdbMetadataResolver` interface in `:core:metadata-normalizer`
    - [x] Implement `DefaultMediaMetadataNormalizer` (no-op: passes through input as-is)
    - [x] Implement `DefaultTmdbMetadataResolver` (no-op: returns input unmodified)
    - [x] Add unit tests:
      - [x] Test default normalizer does not modify data (3 tests)
      - [x] Test default resolver does not modify data (3 tests)
  - [ ] **Full implementation (deferred to later):**
    - [ ] Implement `MediaMetadataNormalizer` with actual normalization logic:
      - Title normalization (strip technical tags, normalize whitespace/case/punctuation)
      - Structural parsing (extract year, season, episode from scene-style naming)
      - Deterministic output (same input → same output)
    - [ ] Implement scene/title parser (custom Kotlin regex engine):
      - Inspired by Sonarr, Radarr, GuessIt, FileBot patterns (behavior only, no direct code porting)
      - Extract: cleaned title, year, season/episode, edition tags, resolution, media source
    - [ ] Implement `TmdbMetadataResolver` with TMDB integration:
      - TMDB search using `tmdb-java`
      - Enrich `NormalizedMediaMetadata` with TMDB ID, official titles, years
      - Handle ambiguous matches (log and/or skip setting TMDB ID)
    - [ ] Add comprehensive unit tests:
      - Scene-naming parser tests (various formats)
      - Normalization determinism tests
      - TMDB resolver tests (mock TMDB API)
  - [x] Documentation:
    - [x] Update `ARCHITECTURE_OVERVIEW_V2.md` to confirm module exists with skeleton
    - [x] Update `IMPLEMENTATION_PHASES_V2.md` to mark Phase 3A skeleton as complete
    - All changes align with `MEDIA_NORMALIZATION_AND_UNIFICATION.md` and `MEDIA_NORMALIZATION_CONTRACT.md`

- [ ] **Phase 3B: Xtream Pipeline Integration**
  - [ ] In `:pipeline:xtream`:
    - [ ] Define Xtream domain models:
      - `XtreamVodItem`, `XtreamSeriesItem`, `XtreamEpisode`, `XtreamChannel`, `XtreamEpgEntry`, etc.
    - [ ] Define interfaces:
      - `XtreamCatalogRepository` (VOD/Series).
      - `XtreamLiveRepository` (channels + EPG).
      - `XtreamPlaybackSourceFactory`.
    - [ ] Port stable v1 code:
      - HTTP URL building.
      - `DelegatingDataSourceFactory`.
      - `RarDataSource`.
    - [ ] Implement default repository implementations based on v1 HTTP/Xtream logic.
    - [ ] **Implement `toRawMediaMetadata()` for all Xtream media items:**
      - `fun XtreamVodItem.toRawMediaMetadata(): RawMediaMetadata`
      - `fun XtreamSeriesItem.toRawMediaMetadata(): RawMediaMetadata`
      - `fun XtreamEpisode.toRawMediaMetadata(): RawMediaMetadata`
      - Extract metadata exactly as provided by Xtream API (no cleaning, no heuristics)
      - Pass through TMDB IDs if Xtream provides them
      - See `MEDIA_NORMALIZATION_CONTRACT.md` Section 2.1

- [ ] In `:player:internal`:
  - [ ] Extend `InternalPlaybackSourceResolver` so that:
    - For `PlaybackType.VOD`, `SERIES`, `LIVE` (Xtream-based), it uses `XtreamPlaybackSourceFactory`.
- [ ] In `:feature:library`:
  - [ ] Implement a basic Xtream-aware library UI:
    - Browse VOD and series via `XtreamCatalogRepository`.
    - On item click: create `PlaybackContext` and navigate to internal player.
- [ ] In `:feature:live`:
  - [ ] Implement channel list UI using `XtreamLiveRepository`.
  - [ ] Implement simple EPG UI (can be minimal initially).
  - [ ] Integrate with `LivePlaybackController` (from `:playback:domain`) to:
    - Switch channels.
    - Display live metadata/EPG inside the player.
- [ ] In `:feature:home`:
  - [ ] Add Xtream-specific entry tiles (e.g. “Live TV”, “Xtream Library”).

All new UI text must be localized (English and German).  
No direct ExoPlayer or tdlib usage in feature modules.

---

## Phase 4 – IO & Audiobook Foundations

**Goal:** Reserve and lightly implement IO and Audiobook pipelines and shells so that future work will not require refactoring the top-level architecture.

### Allowed modules to modify in Phase 4

- `:pipeline:io`
- `:pipeline:audiobook`
- `:feature:audiobooks`
- `:feature:library` (if needed)
- `:player:internal` (only to add IO/Audiobook cases in source resolver)
- `:core:persistence` (for canonical media storage)

### Checklist

- [ ] **Phase 4A: IO Pipeline Foundations**
  - [ ] In `:pipeline:io`:
    - [ ] Define `IoMediaItem`.
    - [ ] Define `IoContentRepository` interface.
    - [ ] Provide a minimal implementation that:
      - Lists local files from one or two fixed locations or an OS picker.
    - [ ] Define `IoPlaybackSourceFactory`.
    - [ ] Implement `fun IoMediaItem.toRawMediaMetadata(): RawMediaMetadata`
      - Extract filename, path, duration if available
      - No cleaning or heuristics (see `MEDIA_NORMALIZATION_CONTRACT.md`)
  - [ ] In `:player:internal`:
    - [ ] Extend `InternalPlaybackSourceResolver` to:
      - Support `PlaybackType.IO` using `IoPlaybackSourceFactory`.

- [ ] **Phase 4B: Audiobook Pipeline Foundations**
  - [ ] In `:pipeline:audiobook`:
    - [ ] Define `AudiobookItem` and `AudiobookRepository` interface.
    - [ ] Define `AudiobookPlaybackSourceFactory`.
    - [ ] Provide a stub or minimal implementation that:
      - Returns mocked data or simple local files for early v2.
    - [ ] Implement `fun AudiobookItem.toRawMediaMetadata(): RawMediaMetadata`
  - [ ] In `:player:internal`:
    - [ ] Extend `InternalPlaybackSourceResolver` to:
      - Support `PlaybackType.AUDIOBOOK` using `AudiobookPlaybackSourceFactory`.
  - [ ] In `:feature:audiobooks`:
    - [ ] Implement a minimal placeholder shell:
      - List a few `AudiobookItem`s (real or mocked).
      - On click: build `PlaybackContext` and navigate to internal player.

- [ ] **Phase 4C: Canonical Media Storage**
  - [ ] In `:core:persistence`:
    - [ ] Define `CanonicalMediaRepository` interface:
      - `suspend fun upsertCanonicalMedia(normalized: NormalizedMediaMetadata): CanonicalMediaId`
      - `suspend fun addOrUpdateSourceRef(canonicalId: CanonicalMediaId, source: MediaSourceRef)`
      - `suspend fun findByCanonicalId(id: CanonicalMediaId): CanonicalMediaWithSources?`
    - [ ] Implement ObjectBox-backed repository
    - [ ] Add ObjectBox entities for canonical media and source references
    - [ ] Wire into DI
  - [ ] Documentation:
    - Reference `MEDIA_NORMALIZATION_CONTRACT.md` Section 2.4 for persistence responsibilities

**Note:**  
These features may remain minimal for the first v2 release, but their presence in the architecture prevents future structural refactors.

---

## Phase 5 – AppShell, Profiles, Entitlements & Firebase Integration

**Goal:** Implement AppShell behavior, profiles, entitlements, and introduce Firebase-backed feature flags without breaking offline-first behavior.

### Allowed modules to modify in Phase 5

- `:app-v2`
- `:core:model`
- `:core:persistence`
- `:core:firebase`
- `:feature:settings`
- `:feature:home`
- `:infra:logging`

### Checklist

- [ ] In `:core:model`:
  - [ ] Define:
    - `data class FeatureId(val value: String)`
    - `data class FeatureDescriptor(...)`
    - `interface FeatureRegistry`
  - [ ] Define interfaces:
    - `FeatureFlagProvider`
    - `RemoteProfileStore`
- [ ] In `:core:persistence`:
  - [ ] Implement `ProfileRepository`:
    - Create/edit/delete profiles (adult + kids).
    - Get/set active profile.
  - [ ] Implement `EntitlementRepository`:
    - Stores trial info and purchased features locally.
    - Provides `EntitlementState`.
  - [ ] Implement `LocalFeatureFlagProvider`:
    - Implements `FeatureFlagProvider`.
    - Reads defaults from local storage / hardcoded config.
    - Works entirely without Firebase.
- [ ] In `:core:firebase`:
  - [ ] Add Gradle dependencies for Firebase (Remote Config, Firestore, Crashlytics, as needed).
  - [ ] Implement:
    - `FirebaseFeatureFlagProvider` (implements `FeatureFlagProvider`).
    - `FirebaseRemoteProfileStore` (implements `RemoteProfileStore`).
  - [ ] Handle failures gracefully:
    - Provide last-known-good values or fall back to `LocalFeatureFlagProvider`.
- [ ] In `:app-v2` (AppShell):
  - [ ] Implement startup sequence:
    - Determine `DeviceProfile` (phone/tablet/tv).
    - Load local profiles and entitlements.
    - Initialize DI graph with Hilt:
      - Bind `FeatureFlagProvider` to:
        - Firebase-backed impl if configured, else
        - `LocalFeatureFlagProvider`.
      - Bind `RemoteProfileStore` to Firebase impl if available, else a no-op/local impl.
    - Initialize pipelines in background (non-blocking).
  - [ ] Implement `FeatureRegistry`:
    - Collect `FeatureDescriptor` objects from `:feature:*` modules (via Hilt or static providers).
    - Provide a list of active features based on `FeatureFlagProvider` and `EntitlementRepository`.
  - [ ] Wire Compose Navigation:
    - Routes for all feature shells (Home, Telegram, Library, Live, Audiobooks, Settings).
- [ ] In `:feature:home`:
  - [ ] Use `FeatureRegistry` to decide which tiles to show.
  - [ ] Respect entitlements and kids restrictions when rendering navigation options.
- [ ] In `:feature:settings`:
  - [ ] Implement UI for:
    - Managing profiles (including kids).
    - Displaying trial/entitlement state.
    - Configuring subtitle styles (wired to `SubtitleStyleManager` in `:playback:domain`).

**Important:**  
The app MUST continue to work **without Firebase** by using only local providers.  
Firebase enhances flags/profiles/analytics but is not required for app startup or local playback.

---

## Phase 6 – Playback Behavior Contracts Integration

**Goal:** Wire the full playback behavior (Resume, Kids, Subtitles, Live, TV) into the internal player and ensure it matches the existing behavior contracts.

### Allowed modules to modify in Phase 6

- `:playback:domain`
- `:player:internal`
- `:core:persistence`
- `:feature:settings`
- `:infra:logging`

### Checklist

- [ ] In `:playback:domain`:
  - [ ] Implement `DefaultResumeManager` according to the existing behavior contract:
    - Save resume points per `PlaybackContext`.
    - Clear resume near the end as specified.
  - [ ] Implement `DefaultKidsPlaybackGate`:
    - Tick-based screen-time tracking.
    - Blocking behavior when limits are reached.
  - [ ] Implement `DefaultSubtitleStyleManager`:
    - Hold subtitle style state (likely via StateFlow).
    - Use `SubtitleStyleStore` from `:core:persistence` for persistence.
  - [ ] Implement `DefaultSubtitleSelectionPolicy`:
    - Rules for auto-selecting tracks.
    - Handling of kids profiles (disabling subtitles/CC if required).
  - [ ] Implement `DefaultLivePlaybackController`:
    - Pure domain logic using pipeline-agnostic interfaces (not pipeline-specific types).
  - [ ] Implement `DefaultTvInputController`:
    - DPAD/focus behavior for TV devices, based on `DeviceProfile`.

- [ ] In `:player:internal`:
  - [ ] Integrate `ResumeManager` into `InternalPlayerSession`:
    - Seek to stored resume position when starting playback if appropriate.
    - Update resume position periodically during playback.
    - Clear resume when the content is considered “finished”.
  - [ ] Integrate `KidsPlaybackGate`:
    - Periodically call tick method with playback context and elapsed time.
    - If the gate indicates blocking, pause playback and show an overlay.
  - [ ] Integrate `SubtitleStyleManager` and `SubtitleSelectionPolicy`:
    - Apply styles to subtitle rendering.
    - Use selection policy when tracks become available.
  - [ ] Integrate `LivePlaybackController`:
    - Expose live-specific controls and EPG overlay data to UI.
  - [ ] Integrate `TvInputController`:
    - Map DPAD and TV-specific input events to player actions.

- [ ] In `:feature:settings`:
  - [ ] Wire UI for configuring subtitle styles to `SubtitleStyleManager`.

- [ ] In `:infra:logging`:
  - [ ] Ensure that errors and special cases (e.g. kids blocking, resume resets) are logged via `UnifiedLog`.

---

## Phase 7 – Diagnostics, Debug & Tooling

**Goal:** Make the player and pipelines observable and enforce architectural quality via tooling.

### Allowed modules to modify in Phase 7

- `:player:internal`
- `:infra:logging`
- `:infra:tooling`
- CI / build configuration files

### Checklist

- [ ] In `:player:internal`:
  - [ ] Implement `PlayerDiagnostics` and `PlayerDiagnosticsSnapshot`:
    - Capture URL, resolved source, track info, buffering state, errors.
  - [ ] Add a debug overlay Composable:
    - Triggered by a developer-only flag.
    - Displays key diagnostics data.
- [ ] In `:infra:logging`:
  - [ ] Integrate diagnostics into logging:
    - Provide a way to send snapshots to logs and/or Crashlytics.
- [ ] In `:infra:tooling`:
  - [ ] Configure:
    - ktlint
    - detekt
  - [ ] Add basic architecture tests (e.g. via ArchUnit or alternative) to enforce:
    - No `:feature:*` → `:pipeline:*` cross-dependency violations beyond allowed ones.
    - No UI modules depending on tdlib or ExoPlayer directly.
  - [ ] Set up LeakCanary in debug builds.
  - [ ] Enable StrictMode policies in debug builds.
- [ ] CI:
  - [ ] Update CI pipeline to:
    - Build and test `:app-v2`.
    - Run formatting and static analysis (ktlint, detekt).
    - Run architecture checks.

---

## Phase 8 – v2 Promotion & Legacy Decommissioning

**Goal:** Make v2 (`:app-v2`) the primary app, freeze v1, and ensure the repo remains clean and future-proof.

### Allowed modules to modify in Phase 8

- CI / build configuration
- Repo-level documentation
- High-level Gradle configuration
- Legacy modules (for archival only)

### Checklist

- [ ] Verify that:
  - [ ] `:app-v2` builds and passes all tests.
  - [ ] Core v2 features are stable enough for a release.
- [ ] CI:
  - [ ] Update CI workflows to treat `:app-v2` as the primary build target.
  - [ ] Optionally remove legacy `:app` from the main CI build to speed up pipelines.
- [ ] Git:
  - [ ] Merge `architecture/v2-bootstrap` into `main`.
  - [ ] Tag the first v2 release.
  - [ ] Move v1 code to:
    - `legacy/v1` branch and/or
    - A `:app-legacy` module clearly marked as non-active.
- [ ] Clean-up:
  - [ ] Remove unused legacy modules that are no longer referenced.
  - [ ] Keep only what is useful as documentation / reference.

---

## Instructions for AI / Agents

When working on FishIT Player v2, agents MUST follow these rules:

1. **Read the contracts first**
   - Always read:
     - `APP_VISION_AND_SCOPE.md`
     - `ARCHITECTURE_OVERVIEW_V2.md`
     - `IMPLEMENTATION_PHASES_V2.md`
     - `MEDIA_NORMALIZATION_CONTRACT.md` (when working on pipelines, metadata, or canonical identity)
   - before starting any v2 task.

2. **Respect phase boundaries**
   - Only work on items in the current phase unless explicitly instructed otherwise.
   - Do not implement later-phase features early.

3. **Scope changes to allowed modules**
   - For each phase, restrict modifications to the modules listed as “allowed to modify”.
   - Do not touch legacy modules (`:app` and friends) for v2 work.

4. **No architecture options in code**
   - Do not implement “Option A / Option B” in code.
   - If architecture seems underspecified:
     - Propose a concrete update to the relevant markdown.
     - Wait for the contract to reflect that decision.
     - Only then implement.

5. **i18n compliance**
   - No new hardcoded user-facing text.
   - Use string resources with English and German entries.
   - Developer-only text may be English-only but must still use resources.

6. **Dependency versions**
   - Use the library versions defined in `DEPENDENCY_POLICY.md` (once it exists).
   - Propose dependency upgrades only in dedicated tasks (not while implementing features).

- Do not deviate from `DEPENDENCY_POLICY.md` versions inside feature or refactor tasks.
    Any deviation MUST be done in a dedicated dependency update task.

7. **Parallel work**
   - When multiple agents are active:
     - Each task should focus on one module or a small group of modules.
     - Cross-module changes or refactors must be handled in dedicated architecture tasks.

8. **Pipeline Metadata Contract**
   - When implementing or modifying pipeline modules:
     - Always implement `toRawMediaMetadata()` according to `MEDIA_NORMALIZATION_CONTRACT.md` Section 2.1.
     - Never perform title cleaning, normalization, or heuristics in pipelines.
     - Never conduct TMDB or external metadata searches in pipelines.
     - Tests must validate `RawMediaMetadata` production, not normalization behavior.

This document, together with the vision and architecture overview, forms the **executable plan** from an empty v2 skeleton to a production-ready APK.
