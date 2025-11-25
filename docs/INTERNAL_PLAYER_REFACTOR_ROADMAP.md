# FishIT Internal Player – Refactoring Roadmap & Checklist

Status legend:
- ✅ = Done (implemented in current refactor)
- ⬜ = Open / not started
- 🔄 = In progress / partially implemented

---

## High-level Goal

Refactor the legacy `InternalPlayerScreen` into a modular, testable, and maintainable architecture that:

- Works flawlessly with **both**:
  - the existing **Xtream** pipeline, and
  - the new **Telegram** pipeline (using `tdlib-coroutines`),
- Keeps **all** existing features from the legacy screen (trickplay, TV remote support, PiP/mini-player, resume, kids mode, live/EPG, subtitles, debug),
- Centralizes domain logic (resume, kids, live, diagnostics, etc.) into dedicated modules,
- Leaves `InternalPlayerScreen` itself as a **thin orchestration layer**.

---

## Phase 1 – Introduce PlaybackContext and basic wiring

**Goal:** Provide a domain-level description of the playback session so that resume, kids, live, diagnostics, etc. can work without embedding logic in the screen.

### Checklist

- ✅ Define `PlaybackContext` and `PlaybackType`
  - ✅ Create `PlaybackContext` data class with:
    - `type: PlaybackType`
    - `mediaId`, `episodeId`, `seriesId`, `season`, `episodeNumber`
    - `liveCategoryHint`, `liveProviderHint`, `kidProfileId`
  - ✅ Create `PlaybackType` enum with `VOD`, `SERIES`, `LIVE`

- ✅ Thread PlaybackContext into new modules
  - ✅ Extend `InternalPlayerUiState` with `playbackType: PlaybackType`
  - ✅ Add convenience getters `isLive` and `isSeries` in `InternalPlayerUiState`
  - ✅ Update `rememberInternalPlayerSession(...)` to accept `playbackContext: PlaybackContext`
  - ✅ Initialize `InternalPlayerUiState` with `playbackType = playbackContext.type`

- ✅ Update InternalPlayerScreen API (core modules only)
  - ✅ `RememberInternalPlayerScreen(...)` now takes a `PlaybackContext`
  - ✅ `InternalPlayerScreen(...)` now takes a `PlaybackContext` and passes it to the session

- ✅ Update all call sites in the app
  - ✅ For VOD calls: construct `PlaybackContext(type = PlaybackType.VOD, mediaId = ..., ...)`
  - ✅ For series/episode calls: include `PlaybackType.SERIES` + `seriesId`, `season`, `episodeNumber`
  - ✅ For live calls: use `PlaybackType.LIVE` + `liveCategoryHint` / `liveProviderHint` where available
  - ✅ Created `InternalPlayerEntry` bridge that accepts PlaybackContext and delegates to legacy `InternalPlayerScreen`
  - ✅ Updated MainActivity navigation composable to build PlaybackContext from route parameters
  - ✅ Updated LiveDetailScreen direct call to use InternalPlayerEntry with PlaybackContext
  - ✅ Updated SeriesDetailScreen fallback to use InternalPlayerEntry with PlaybackContext
  - ✅ Verified all other call sites (VodDetailScreen, TelegramDetailScreen, LibraryScreen, StartScreen) route through MainActivity navigation
  - ✅ Confirmed no direct InternalPlayerScreen calls remain except in InternalPlayerEntry bridge

### Phase 1 Status: ✅ **FULLY COMPLETE**

All player call sites now use typed PlaybackContext and route through InternalPlayerEntry. The legacy monolithic InternalPlayerScreen remains the active runtime implementation. Runtime behavior is 100% preserved. The SIP modules are ready as reference implementations for future phases.

---

## Phase 2 – Resume handling and kids/screen-time gate

**Goal:** Move resume logic and kids/screen-time gating out of the legacy screen and into dedicated domain services, then integrate them into the player session.

### Checklist

- ✅ ResumeManager abstraction
  - ✅ Define `ResumeManager` interface:
    - `loadResumePositionMs(context: PlaybackContext): Long?`
    - `handlePeriodicTick(context: PlaybackContext, positionMs: Long, durationMs: Long)`
    - `handleEnded(context: PlaybackContext)`
  - ✅ Implement `DefaultResumeManager` using existing `ResumeRepository`:
    - ✅ Map VOD resume to `mediaId`
    - ✅ Map series resume to `seriesId`, `season`, `episodeNumber`
    - ✅ Apply same thresholds as legacy:
      - Only resume if position > 10s
      - Clear resume when remaining duration < 10s

- ✅ KidsPlaybackGate abstraction
  - ✅ Define `KidsPlaybackGate` interface:
    - `evaluateStart(): KidsGateState`
    - `onPlaybackTick(current: KidsGateState, deltaSecs: Int): KidsGateState`
  - ✅ Implement `DefaultKidsPlaybackGate` using:
    - ✅ `SettingsStore.currentProfileId`
    - ✅ `ObxStore` + `ObxProfile` to detect kid profiles
    - ✅ `ScreenTimeRepository` for:
      - `remainingMinutes(profileId)`
      - `tickUsageIfPlaying(profileId, deltaSecs)`

- ✅ Extend InternalPlayerUiState with kids information
  - ✅ Add `kidActive: Boolean`
  - ✅ Add `kidBlocked: Boolean`
  - ✅ Add `kidProfileId: Long?`

- ✅ Integrate ResumeManager and KidsPlaybackGate into the session
  - ✅ Instantiate `DefaultResumeManager` in `rememberInternalPlayerSession`
  - ✅ Instantiate `DefaultKidsPlaybackGate` in `rememberInternalPlayerSession`
  - ✅ Initial seek:
    - ✅ If `startMs` is provided, use it
    - ✅ Otherwise, call `loadResumePositionMs(playbackContext)` and seek if > 0
  - ✅ Periodic tick (every ~3 seconds):
    - ✅ Call `handlePeriodicTick(playbackContext, positionMs, durationMs)` for VOD/Series
    - ✅ Accumulate seconds and every ~60s:
      - ✅ Call `KidsPlaybackGate.onPlaybackTick(current, deltaSecs)`
      - ✅ Update `kidBlocked` and `kidActive` in `InternalPlayerUiState`
      - ✅ Pause player if `kidBlocked` becomes true
  - ✅ On `STATE_ENDED`:
    - ✅ Call `resumeManager.handleEnded(playbackContext)` to clear resume markers

- ⬜ UI feedback for kids block
  - ✅ Show a blocking overlay when `kidBlocked == true`
  - ✅ Provide a clear message and optional navigation back
  - ✅ Log a diagnostics event on kid block (for the internal debug screen)

---

## Phase 3 – Live-TV and EPG controller

**Goal:** Move Live-TV specific behaviour (channel navigation, EPG, overlays, live lists) out of the legacy screen into a dedicated `LivePlaybackController`.

### Checklist

- ✅ Design LivePlaybackController
  - ✅ Define `LivePlaybackController` interface:
    - ✅ `suspend fun initFromPlaybackContext(ctx: PlaybackContext)`
    - ✅ `fun jumpChannel(delta: Int)`
    - ✅ `fun selectChannel(channelId: Long)`
    - ✅ `fun onPlaybackPositionChanged(positionMs: Long)`
    - ✅ Expose `StateFlow<LiveChannel?>` and `StateFlow<EpgOverlayState>`
  - ✅ Create `LiveChannel` data class
  - ✅ Create `EpgOverlayState` data class
  - ✅ Create `DefaultLivePlaybackController` stub implementation
  - ✅ Create repository interfaces (`LiveChannelRepository`, `LiveEpgRepository`)
  - ✅ Create `TimeProvider` abstraction for testable time operations
  - ✅ Create `LivePlaybackControllerTest` with test skeleton

- ⬜ Migrate legacy Live-TV logic
  - ⬜ Extract live lists (`libraryLive`, favorites) from legacy screen into controller
  - ⬜ Move `switchToLive(...)` into controller
  - ⬜ Move `jumpLive(delta: Int)` into controller
  - ⬜ Move EPG resolution (`EpgRepository` queries) into controller
  - ⬜ Implement auto-hide of EPG overlay in controller or a dedicated timer helper

- ✅ Integrate with UI (SIP UI path only)
  - ✅ Extend `InternalPlayerUiState` with:
    - ✅ `liveChannelName`
    - ✅ `liveNowTitle`
    - ✅ `liveNextTitle`
    - ✅ `epgOverlayVisible`
    - ✅ `liveListVisible`
  - ✅ Update `InternalPlayerContent` to:
    - ✅ Show EPG overlay when controller marks it visible
    - ✅ Render live channel title and EPG snippet
    - ✅ Hide progress row for LIVE content
  - ✅ Create `PlayerSurface` with gesture handling:
    - ✅ Horizontal swipe ⇒ `jumpChannel(+/-1)` for Live, seek for VOD
    - ✅ Vertical swipe ⇒ toggle live list (stub with TODO for Phase 3 extended)
  - ✅ Add live controller callbacks to `InternalPlayerController`:
    - ✅ `onJumpLiveChannel(delta: Int)`
    - ✅ `onToggleLiveList()`
  - ✅ Wire `DefaultLivePlaybackController` in `InternalPlayerSession` (SIP path)
  - ✅ Add unit tests for live UI state mapping

**Note:** Legacy `InternalPlayerScreen` still owns runtime Live UI until the final migration phase.
The SIP-based UI integration is complete but not activated at runtime.

---

## Phase 4 – Subtitle style & CC menu centralization

**Goal:** Move subtitle style (scale, foreground/background colors, opacity) and the CC menu out of the legacy screen into `SubtitleStyleManager` + UI controls.

### Checklist

- ⬜ SubtitleStyleManager
  - ⬜ Define `SubtitleStyle` data class (scale, fgColor, bgColor, fgOpacity, bgOpacity)
  - ⬜ Define `SubtitleStyleManager` interface with:
    - ⬜ `val currentStyle: StateFlow<SubtitleStyle>`
    - ⬜ `suspend fun update(style: SubtitleStyle)`
  - ⬜ Implement default manager using `SettingsStore` keys for subtitle style

- ⬜ Wire subtitle style into player
  - ⬜ Add `subtitleStyle: SubtitleStyle` to `InternalPlayerUiState`
  - ⬜ Pass the style into `PlayerSurface` / PlayerView creation
  - ⬜ Apply styles to `subtitleView`:
    - ⬜ `setFractionalTextSize(scale)`
    - ⬜ `setApplyEmbeddedStyles(true)` / `setApplyEmbeddedFontSizes(true)`
    - ⬜ `setStyle(CaptionStyleCompat(...))` with colors + alpha

- ⬜ CC menu in InternalPlayerControls
  - ⬜ Implement `CcSettingsDialog` with:
    - ⬜ Scale control
    - ⬜ FG/BG color selection
    - ⬜ Opacity sliders
  - ⬜ On confirm:
    - ⬜ Call `SubtitleStyleManager.update(...)`
    - ⬜ Update `InternalPlayerUiState.subtitleStyle`

---

## Phase 5 – PlayerSurface, aspect ratio, trickplay & auto-hide

**Goal:** Encapsulate PlayerView, aspect ratio behaviour, trickplay (fast-forward/rewind with preview), and auto-hide logic in a dedicated composable and state.

### Checklist

- ⬜ PlayerSurface composable
  - ⬜ Implement `PlayerSurface(...)` in `InternalPlayerControls` that:
    - ⬜ Hosts the `AndroidView(PlayerView)`
    - ⬜ Configures `resizeMode` based on `state.aspectRatioMode`
    - ⬜ Connects subtitle style (from `InternalPlayerUiState`) to `subtitleView`
    - ⬜ Handles gestures:
      - ⬜ Tap: toggles control visibility
      - ⬜ Horizontal swipe: seek/trickplay or Live-channel swap
      - ⬜ Vertical swipe: open live list/quick actions

- ⬜ Trickplay (FF/RW) and seek preview
  - ⬜ Extend `InternalPlayerUiState` with:
    - ⬜ `trickplayActive`, `trickplaySpeed`
    - ⬜ `seekPreviewVisible`, `seekPreviewTargetMs`
  - ⬜ Implement helpers in `InternalPlayerControls`:
    - ⬜ `startTrickplay(direction)`
    - ⬜ `stopTrickplay(resume: Boolean)`
    - ⬜ `showSeekPreview(...)`
  - ⬜ Port existing legacy trickplay behaviour (speeds, preview overlay, DPAD integration)

- ⬜ Auto-hide controls
  - ⬜ Add `controlsVisible` and `controlsTick` to state (or internal state in controls module)
  - ⬜ Use `LaunchedEffect(controlsVisible, controlsTick, isTv)` to auto-hide:
    - ⬜ Different timeouts for TV vs phone
    - ⬜ No auto-hide while any menus/overlays are open (CC, tracks, settings, live EPG)

---

## Phase 6 – TV remote (DPAD) and focus handling

**Goal:** Extract DPAD/focus logic and QuickActions into a dedicated TV input controller, so phone/tablet behaviour remains clean.

### Checklist

- ⬜ TvInputController definition
  - ⬜ Define `TvInputController` with:
    - ⬜ `fun onKeyEvent(event: KeyEvent): Boolean`
    - ⬜ `val quickActionsVisible: State<Boolean>`
    - ⬜ `val focusedAction: State<TvAction?>`
  - ⬜ Define `TvAction` enum (e.g. PLAY_PAUSE, PIP, CC, ASPECT, LIVE_LIST, etc.)

- ⬜ Migrate focus and DPAD logic
  - ⬜ Move all `FocusRequester` usage from legacy screen into this controller
  - ⬜ Move `focusScaleOnTv` setup to a TV-specific layer
  - ⬜ Migrate DPAD behaviour:
    - ⬜ Center: toggle controls and play/pause
    - ⬜ Left/Right: seek or trickplay, jump live
    - ⬜ Up/Down: quick actions / live list / overlays
    - ⬜ Back: close menus/overlays, then controls, then exit screen

- ⬜ Integrate into UI
  - ⬜ In `InternalPlayerControls`, detect `isTv` and:
    - ⬜ Wire key events through `TvInputController`
    - ⬜ Show quick actions when `quickActionsVisible` is true
    - ⬜ Use `focusedAction` to direct focus to correct button(s)

---

## Phase 7 – PlaybackSession & MiniPlayer integration

**Goal:** Make the player instance sharable across screens (mini-player / PiP behaviour & TV mini-player compatibility) using existing session/mini components.

### Checklist

- ⬜ PlaybackSession integration
  - ⬜ Replace direct `ExoPlayer.Builder(...)` in `InternalPlayerSession` with:
    - ⬜ `PlaybackSession.acquire(context)` (or equivalent existing helper)
  - ⬜ Let `PlaybackSession` take ownership of:
    - ⬜ the `ExoPlayer` lifecycle
    - ⬜ the `currentSource` (final URL / resolved source)

- ⬜ MiniPlayerOrchestrator
  - ⬜ Create `MiniPlayerOrchestrator` that wraps:
    - ⬜ `MiniPlayerState`
    - ⬜ `MiniPlayerDescriptor`
  - ⬜ Provide methods:
    - ⬜ `onEnterPipOrMini(...)`
    - ⬜ `onLeaveScreen(...)`
  - ⬜ Move logic for:
    - ⬜ TV mini-player vs Android system PiP
    - ⬜ updating descriptors on exit
    - ⬜ keeping the player alive for mini mode

- ⬜ SystemUi integration
  - ⬜ Update `requestPictureInPicture(...)`:
    - ⬜ On TVs: activate mini-player flow via `MiniPlayerOrchestrator`
    - ⬜ On phones/tablets: retain current system PiP behaviour

---

## Phase 8 – Lifecycle, rotation, and Xtream worker pause

**Goal:** Centralize lifecycle handling (pause/resume/destroy), rotation lock/unlock, and Xtream worker pausing/resuming into a dedicated lifecycle composable.

### Checklist

- ⬜ InternalPlayerLifecycle composable
  - ⬜ Create `InternalPlayerLifecycle(...)` that:
    - ⬜ Listens to `ON_RESUME`, `ON_PAUSE`, `ON_DESTROY`
    - ⬜ Coordinates with:
      - ⬜ `ResumeManager` (final save/clear on destroy)
      - ⬜ `KidsPlaybackGate` (optional) for resume/resume gating
    - ⬜ Manages rotation:
      - ⬜ Reads `settings.rotationLocked`
      - ⬜ Locks orientation on entry, restores on exit
    - ⬜ Manages Xtream workers:
      - ⬜ Reads initial `settings.m3uWorkersEnabled`
      - ⬜ Disables workers while player is active
      - ⬜ Restores previous state on exit

- ⬜ Screen integration
  - ⬜ Add `InternalPlayerLifecycle(...)` into `InternalPlayerScreen`
  - ⬜ Ensure that the lifecycle composable does not directly depend on UI types

---

## Phase 9 – Diagnostics & internal debug screen

**Goal:** Provide a central diagnostics service that both the in-player debug overlay and a standalone debug screen can consume.

### Checklist

- ⬜ PlayerDiagnostics service
  - ⬜ Define `PlayerDiagnosticsSnapshot` (url, finalUrl, mime, type, pos, duration, buffering, tracks, etc.)
  - ⬜ Define `PlayerDiagnostics` interface with:
    - ⬜ `val snapshots: StateFlow<PlayerDiagnosticsSnapshot>`
    - ⬜ `fun logEvent(name: String, meta: Map<String, String> = emptyMap())`
  - ⬜ Implement using:
    - ⬜ `TelegramLogRepository`
    - ⬜ `DiagnosticsLogger` (existing Media3 diagnostics)

- ⬜ Session integration
  - ⬜ On each `onEvents` / state change:
    - ⬜ Update `PlayerDiagnosticsSnapshot`
  - ⬜ On errors:
    - ⬜ `logEvent("error", meta = ...)`

- ⬜ Debug overlay & screen
  - ⬜ Update `DebugInfoOverlay` to read from `PlayerDiagnostics.snapshots`
  - ⬜ Add an internal debug screen (separate route) that:
    - ⬜ Shows recent snapshots
    - ⬜ Links to logs and HTTP/Telegram details

---

## Phase 10 – Tooling, testing, and quality

**Goal:** Ensure long-term maintainability and correctness with the help of existing tools and high-quality external libraries.

### Checklist

- ⬜ Static analysis & style
  - ⬜ Ensure `ktlint` is enabled and configured for the project
  - ⬜ Ensure `detekt` is enabled and run in CI
  - ⬜ Address warnings in new modules as part of the refactor

- ⬜ Memory & performance
  - ⬜ Integrate `LeakCanary` to detect leaks involving PlayerView / Activities
  - ⬜ Optionally enable `StrictMode` (debug builds) for main-thread I/O and network
  - ⬜ Use Android Studio Profiler / Network Profiler for Xtream and Telegram streams

- ⬜ Architecture checks
  - ⬜ Consider adding Gradle module-level boundaries (e.g. dedicated `player-internal` module)
  - ⬜ Optionally use `ArchUnit` or custom checks to:
    - ⬜ Disallow new code referencing `legacy_InternalPlayerScreen`
    - ⬜ Enforce that UI modules do not depend on TDLib or ObjectBox

- ⬜ Automated tests
  - ⬜ Add unit tests for:
    - ⬜ `PlaybackSourceResolver` (HTTP, tg://, rar://, series, live)
    - ⬜ `DefaultResumeManager`
    - ⬜ `DefaultKidsPlaybackGate`
  - ⬜ Add Robolectric / instrumentation tests for:
    - ⬜ `LivePlaybackController`
    - ⬜ `TvInputController`
    - ⬜ `SubtitleStyleManager`

---

## Final target module layout (repository tree)

> Note: In the project folder, all modules currently live at the same level for fast iteration.  
> In the **repository**, they must be placed according to their package structure.

```text
app/
  src/
    main/
      java/
        com/
          chris/
            m3usuite/
              player/
                InternalPlayerScreen.kt

                internal/
                  state/
                    InternalPlayerState.kt

                  session/
                    InternalPlayerSession.kt

                  source/
                    InternalPlaybackSourceResolver.kt
                    // (and any future helpers for Telegram/Xtream URL handling)

                  system/
                    InternalPlayerSystemUi.kt
                    InternalPlayerLifecycle.kt

                  ui/
                    InternalPlayerControls.kt
                    // PlayerSurface, dialogs, overlays, TV quick actions

                  domain/
                    PlaybackContext.kt
                    ResumeManager.kt
                    KidsPlaybackGate.kt
                    // future: SubtitleStyleManager.kt, etc.

                  live/
                    LivePlaybackController.kt
                    DefaultLivePlaybackController.kt
                    LiveChannel.kt
                    EpgOverlayState.kt

                  tv/
                    TvInputController.kt
                    TvAction.kt

                  mini/
                    MiniPlayerOrchestrator.kt

                  subtitles/
                    SubtitleStyleManager.kt
                    SubtitleStyle.kt

                  debug/
                    PlayerDiagnostics.kt
                    PlayerDiagnosticsSnapshot.kt
                    // any internal debug helpers used by the debug screen
```

---

## Notes on professionalism, reuse, and external tools

- Prefer **reuse of existing modules**:
  - `DelegatingDataSourceFactory` for data-source routing (Xtream/Telegram/RAR)
  - `TelegramFileDataSource`, `RarDataSource`, `StreamingConfig`, and `T_TelegramFileDownloader` for Telegram integration
  - `ResumeRepository`, `ScreenTimeRepository`, `ObxStore`, `ObxProfile` for domain logic

- Prefer **official and maintained libraries**:
  - Media and playback:
    - AndroidX Media3 / ExoPlayer (already in use; keep on latest stable)
  - Debugging & profiling:
    - LeakCanary (memory leaks)
    - StrictMode (optional, debug-only)
  - Static analysis:
    - ktlint, detekt (already used in repo; keep them updated)
  - Optional:
    - ArchUnit or similar for architecture rules (package/module boundaries)

- TDLib / Telegram:
  - Always respect the official `tdlib` / `tdlib-coroutines` documentation when interacting with Telegram.
  - Zero deviations from official semantics unless strictly necessary for best-effort integration (e.g. dealing with device-specific edge cases).
