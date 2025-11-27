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

- ✅ UI feedback for kids block
  - ✅ Show a blocking overlay when `kidBlocked == true`
  - ✅ Provide a clear message and optional navigation back
  - ✅ Log a diagnostics event on kid block (for the internal debug screen)

### Phase 2 Status: ✅ **FULLY COMPLETE**

All Resume and Kids/Screen-Time gate modules are fully implemented, tested, and integrated into the SIP session. The modular implementations mirror legacy behavior with full parity. All unit tests pass. The legacy InternalPlayerScreen remains the active runtime implementation.

**Completion Date:** 2025-11-25

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
    - ✅ Expose `StateFlow<LiveMetrics>` for shadow diagnostics
  - ✅ Create `LiveChannel` data class
  - ✅ Create `EpgOverlayState` data class
  - ✅ Create `LiveMetrics` data class
  - ✅ Create `DefaultLivePlaybackController` stub implementation
  - ✅ Create repository interfaces (`LiveChannelRepository`, `LiveEpgRepository`)
  - ✅ Create `TimeProvider` abstraction for testable time operations
  - ✅ Create `LivePlaybackControllerTest` with test skeleton

- ✅ **Phase 3 Task 1: Live-TV Robustness & Data Integrity** ✅ **COMPLETE**
  - ✅ EPG stale detection with configurable threshold (default: 3 minutes)
  - ✅ EPG fallback and caching for error recovery
  - ✅ Smart channel zapping (filter null/empty URLs, remove duplicates)
  - ✅ Controller sanity guards (never crash on empty/invalid lists)
  - ✅ Live metrics exposure for shadow diagnostics
  - ✅ Comprehensive test suite (32 new tests in LiveControllerRobustnessTest)

- ✅ **Phase 3 Task 2: SIP Live-TV Interaction & UX Polish** ✅ **COMPLETE**
  - ✅ Deterministic 200ms jump throttle using injected TimeProvider
  - ✅ EPG overlay hides immediately on channel change (hideAtRealtimeMs = now)
  - ✅ LiveEpgInfoState StateFlow populated when EPG overlay updates
  - ✅ AnimatedVisibility uses epgOverlay.visible directly (~200ms fade animations)
  - ✅ Comprehensive test suite (12 controller + 15 UI tests, all existing tests pass)

- ✅ Migrate legacy Live-TV logic (SIP Implementation)
  - ✅ Live channel lists integrated via DefaultLiveChannelRepository → ObxLive
  - ✅ `jumpChannel(delta: Int)` implemented in DefaultLivePlaybackController
  - ✅ `selectChannel(channelId: Long)` implemented in DefaultLivePlaybackController
  - ✅ EPG resolution integrated via DefaultLiveEpgRepository → EpgRepository
  - ✅ Auto-hide of EPG overlay implemented with configurable timing
  - ✅ EPG stale detection, caching, and fallback implemented
  - ✅ Smart channel filtering (null/empty URLs, duplicates)
  - ✅ 200ms deterministic jump throttle using TimeProvider
  - ✅ Comprehensive test coverage (68+ tests)
  - **Note:** SIP implementation complete; legacy InternalPlayerScreen remains active runtime

- ✅ Integrate with UI (SIP Implementation Complete)
  - ✅ Extend `InternalPlayerUiState` with:
    - ✅ `liveChannelName` (Step 3.A)
    - ✅ `liveNowTitle` (Step 3.A)
    - ✅ `liveNextTitle` (Step 3.A)
    - ✅ `epgOverlayVisible` (Step 3.A)
  - ✅ Wire LivePlaybackController in SIP session (Step 3.B):
    - ✅ Create `DefaultLiveChannelRepository` bridging to ObxLive
    - ✅ Create `DefaultLiveEpgRepository` bridging to EpgRepository
    - ✅ Instantiate controller for LIVE playback type
    - ✅ Initialize controller from PlaybackContext
    - ✅ Collect currentChannel StateFlow → map to liveChannelName
    - ✅ Collect epgOverlay StateFlow → map to liveNowTitle, liveNextTitle, epgOverlayVisible
    - ✅ Add comprehensive tests (InternalPlayerSessionPhase3LiveMappingTest)
  - ✅ Update `InternalPlayerContent` to (Step 3.C - **SIP PATH COMPLETE**):
    - ✅ Show EPG overlay when controller marks it visible
    - ✅ Render live channel title and EPG snippet
    - ✅ Add comprehensive UI tests (InternalPlayerContentPhase3LiveUiTest)
    - ✅ AnimatedVisibility with 200ms fade animations (Task 2)
  - ✅ Map gestures in `PlayerSurface` (Step 3.D - **SIP PATH COMPLETE**):
    - ✅ Horizontal swipe ⇒ `jumpChannel(+/-1)` for Live (SIP only)
    - ✅ 200ms deterministic throttle using TimeProvider (Task 2)
    - ✅ Created PlayerSurface.kt with gesture handling
    - ✅ Wired callback through InternalPlayerContent
    - ✅ Added PlayerSurfacePhase3LiveGestureTest (19 tests)
    - ⬜ Vertical swipe ⇒ open live list sheet or quick actions (future phase)
    - ⬜ VOD/SERIES: seek/trickplay gestures (future phase)

### Phase 3 Status: ✅ **FULLY COMPLETE (SIP Implementation)**

All Live-TV controller modules are fully implemented, tested, and integrated into the SIP UI path. The DefaultLivePlaybackController contains complete legacy behavior migration including:
- Channel navigation with smart filtering and deduplication
- EPG overlay management with stale detection, caching, and fallback
- 200ms deterministic jump throttle
- LiveMetrics exposure for diagnostics
- Comprehensive test coverage (68+ controller tests, 19 UI tests, 19 gesture tests)

The **legacy InternalPlayerScreen remains the active runtime implementation**. The SIP Live-TV implementation is complete and ready for activation in future phases.

**Completion Date:** 2025-11-26

---

## Phase 4 – Subtitle style & CC menu centralization

**Goal:** Move subtitle style, CC menu, and subtitle track selection out of the legacy screen into centralized domain modules (`SubtitleStyleManager` + `SubtitleSelectionPolicy`).

**Status:** ✅ **SIP IMPLEMENTATION COMPLETE** (2025-11-26)

**Full Specification:** See [INTERNAL_PLAYER_PHASE4_CHECKLIST.md](INTERNAL_PLAYER_PHASE4_CHECKLIST.md) and [INTERNAL_PLAYER_SUBTITLE_CC_CONTRACT_PHASE4.md](INTERNAL_PLAYER_SUBTITLE_CC_CONTRACT_PHASE4.md)

**Key Principles:**
- SIP-Only: No modifications to legacy `InternalPlayerScreen.kt`
- Contract-Driven: Behavior defined by subtitle/CC contract
- Kid Mode First: Subtitles completely disabled for kid profiles
- Centralized: All subtitle logic flows through domain modules

### Task Group 1: SubtitleStyle Domain Model & Manager ✅

- ✅ Task 1.1: SubtitleStyle Data Model
  - ✅ Created `internal/subtitles/SubtitleStyle.kt`
  - ✅ Data class with contract-compliant defaults and range validation
  - ✅ `EdgeStyle` enum: NONE, OUTLINE, SHADOW, GLOW
  - ✅ Legacy Reference: L208-212, L1748-1766

- ✅ Task 1.2: SubtitlePreset Enum
  - ✅ Created `internal/subtitles/SubtitlePreset.kt`
  - ✅ 4 presets: DEFAULT, HIGH_CONTRAST, TV_LARGE, MINIMAL
  - ✅ `toStyle()` conversion implemented
  - ✅ Legacy Reference: L2374-2382

- ✅ Task 1.3: SubtitleStyleManager Interface
  - ✅ Created `internal/subtitles/SubtitleStyleManager.kt`
  - ✅ StateFlow-based API with update/preset/reset methods
  - ✅ Contract Reference: Section 5

- ✅ Task 1.4: DefaultSubtitleStyleManager Implementation
  - ✅ Created `internal/subtitles/DefaultSubtitleStyleManager.kt`
  - ✅ DataStore persistence via SettingsStore
  - ✅ Per-profile persistence using currentProfileId
  - ✅ Scale normalization (legacy 0.04-0.12 ↔ new 0.5-2.0)
  - ✅ Legacy Reference: SettingsStore.kt L207-211

### Task Group 2: SubtitleSelectionPolicy ✅

- ✅ Task 2.1: SubtitleSelectionPolicy Interface
  - ✅ Created `internal/subtitles/SubtitleSelectionPolicy.kt`
  - ✅ `SubtitleTrack` data class defined
  - ✅ Interface with `selectInitialTrack()` and `persistSelection()`
  - ✅ Contract Reference: Section 6

- ✅ Task 2.2: DefaultSubtitleSelectionPolicy Implementation
  - ✅ Created `internal/subtitles/DefaultSubtitleSelectionPolicy.kt`
  - ✅ Kid mode: Always returns null
  - ✅ Language priority: System → Primary → Secondary → Default flag → null
  - ✅ Persistence hooks prepared
  - ✅ Legacy Reference: L1284-1304, L2304-2340

### Task Group 3: Player Integration (SIP Session) ✅

- ✅ Task 3.1: Apply SubtitleStyle to PlayerView
  - ✅ Extended `InternalPlayerUiState` with `subtitleStyle: SubtitleStyle`
  - ✅ Instantiated `DefaultSubtitleStyleManager` in `InternalPlayerSession`
  - ✅ Collected `currentStyle` StateFlow and updated UiState
  - ✅ Applied to PlayerView via `CaptionStyleCompat` in `PlayerSurface`
  - ✅ Mapped `SubtitleStyle` to `CaptionStyleCompat` with opacity
  - ✅ Legacy Reference: L1748-1766, L2476-2484

- ✅ Task 3.2: Subtitle Track Selection Integration
  - ✅ Extended `InternalPlayerUiState` with `selectedSubtitleTrack: SubtitleTrack?`
  - ✅ Extended `InternalPlayerUiState` with `availableSubtitleTracks: List<SubtitleTrack>`
  - ✅ Extended `InternalPlayerController` with CC callbacks (`onToggleCcMenu`, `onSelectSubtitleTrack`, `onUpdateSubtitleStyle`, `onApplySubtitlePreset`)
  - ✅ Instantiated `DefaultSubtitleSelectionPolicy` in `InternalPlayerSession`
  - ✅ On `Player.Listener.onTracksChanged`: Enumerated tracks and called `selectInitialTrack()`
  - ✅ Applied selection via `TrackSelectionOverride`
  - ✅ Kid mode: Skipped all track selection
  - ✅ Legacy Reference: L1284-1304, L2304-2312

### Task Group 4: CC Menu UI (SIP InternalPlayerControls) ✅

- ✅ Task 4.1: CC Button in InternalPlayerControls
  - ✅ Added CC button to control bar
  - ✅ Visibility: Non-kid profiles AND at least one subtitle track
  - ✅ Opens CC menu on click via `controller.onToggleCcMenu`
  - ✅ Legacy Reference: L2194-2210, L2253-2267

- ✅ Task 4.2: CcMenuDialog Composable
  - ✅ Created `internal/ui/CcMenuDialog.kt`
  - ✅ Segments: Track Selection, Text Size, FG Opacity, BG Opacity, Edge Style, Presets
  - ✅ Wired callbacks: `onApplyStyle`, `onApplyPreset`, `onSelectTrack`
  - ✅ All TODO markers resolved
  - ✅ Legacy Reference: L2290-2390

- ✅ Task 4.3: Live Preview in CC Menu
  - ✅ `SubtitlePreview` composable showing "Example Subtitle Text"
  - ✅ Reflects pending style changes immediately
  - ✅ Does not affect active playback until applied
  - ✅ Contract Reference: Section 8.5

### Task Group 5: SettingsScreen Integration ✅

- ✅ Task 5.1: Subtitle Settings Section
  - ✅ Created `SubtitleSettingsViewModel` backed by `SubtitleStyleManager`
  - ✅ Created `SubtitleSettingsSection` composable
  - ✅ Controls: Preset buttons, Scale slider, FG Opacity slider, BG Opacity slider, Reset button
  - ✅ Kid mode: Section hidden with message
  - ✅ Removed duplicate subtitle settings from Player card
  - ✅ Contract Reference: Section 9.1

- ✅ Task 5.2: Subtitle Preview Box
  - ✅ `SubtitlePreviewBox` composable in settings
  - ✅ Shows "Beispiel Untertitel" with current style
  - ✅ Real-time updates reflecting style changes
  - ✅ Contract Reference: Section 9.2

### Task Group 6: Testing & Validation ✅ **COMPLETE (2025-11-26)**

- ✅ Task 6.1: SubtitleStyleManager Tests
  - ✅ 11 tests in `SubtitleStyleTest.kt`
  - ✅ 18 tests in `SubtitleStyleManagerRobustnessTest.kt` (NEW)
  - ✅ Coverage: Range validation, presets, defaults, edge styles, robustness

- ✅ Task 6.2: SubtitleSelectionPolicy Tests
  - ✅ 7 tests in `SubtitleSelectionPolicyTest.kt`
  - ✅ 22 tests in `InternalPlayerSessionSubtitleIntegrationTest.kt` (NEW)
  - ✅ Coverage: Priority order, kid mode, default flag, language matching, VOD/SERIES/LIVE

- ✅ Task 6.3: CC Menu UI Tests
  - ✅ 19 tests in `CcMenuPhase4UiTest.kt`
  - ✅ 18 tests in `CcMenuKidModeAndEdgeCasesTest.kt` (NEW)
  - ✅ Coverage: Visibility rules, dialog conditions, state initialization, track selection, Kid Mode, edge cases

**Phase 4 Group 6 Validation Summary:**
- ✅ 95 total subtitle/CC tests (37 existing + 58 new)
- ✅ All VOD/SERIES/LIVE subtitle selection scenarios validated
- ✅ Kid Mode blocking verified end-to-end
- ✅ Edge cases handled: zero tracks, invalid styles, track list changes
- ✅ Contract compliance verified via comprehensive test suite
- ✅ No changes to legacy InternalPlayerScreen
- ✅ SIP subtitle/CC behavior fully validated and stabilized

### Files Overview

**New SIP Files:**
- ✅ `internal/subtitles/SubtitleStyle.kt`
- ✅ `internal/subtitles/SubtitlePreset.kt`
- ✅ `internal/subtitles/SubtitleStyleManager.kt`
- ✅ `internal/subtitles/DefaultSubtitleStyleManager.kt`
- ✅ `internal/subtitles/SubtitleSelectionPolicy.kt`
- ✅ `internal/subtitles/DefaultSubtitleSelectionPolicy.kt`
- ✅ `internal/ui/CcMenuDialog.kt`
- ✅ `ui/screens/SubtitleSettingsViewModel.kt`
- ✅ 6 test files:
  - `SubtitleStyleTest.kt` (11 tests)
  - `SubtitleSelectionPolicyTest.kt` (7 tests)
  - `CcMenuPhase4UiTest.kt` (19 tests)
  - `SubtitleStyleManagerRobustnessTest.kt` (18 tests) - Group 6
  - `InternalPlayerSessionSubtitleIntegrationTest.kt` (22 tests) - Group 6
  - `CcMenuKidModeAndEdgeCasesTest.kt` (18 tests) - Group 6

**Modified SIP Files:**
- ✅ `internal/state/InternalPlayerState.kt` - Added subtitle fields and controller callbacks
- ✅ `internal/session/InternalPlayerSession.kt` - Wired managers and track selection
- ✅ `internal/ui/InternalPlayerControls.kt` - Added CC button and dialog
- ✅ `internal/ui/PlayerSurface.kt` - Applied subtitle style to PlayerView
- ✅ `ui/screens/SettingsScreen.kt` - Added SubtitleSettingsSection

**Legacy Files NOT Modified:**
- ❌ `player/InternalPlayerScreen.kt` - Untouched (remains active runtime)

### Legacy Behavior Mapping

| Legacy Code | Behavior | SIP Module |
|-------------|----------|------------|
| L208-212 | Subtitle preferences | DefaultSubtitleStyleManager |
| L1258-1266 | Effective style helpers | SubtitleStyle data model |
| L1284-1304 | Track enumeration | SubtitleSelectionPolicy |
| L1748-1766 | PlayerView config | InternalPlayerSession |
| L2194-2210, L2253-2267 | CC button | InternalPlayerControls |
| L2290-2390 | CC menu | CcMenuDialog |
| L2304-2312, L2328-2339 | Track selection | SubtitleSelectionPolicy |
| L2476-2484 | withOpacity() | Style application |

---

## Phase 5 – PlayerSurface, aspect ratio, trickplay & auto-hide

**Goal:** Encapsulate PlayerView, aspect ratio behaviour, trickplay (fast-forward/rewind with preview), and auto-hide logic in a dedicated composable and state.

**Status:** ✅ **FULLY VALIDATED & COMPLETE** (2025-11-27) – All Phase 5 implementations hardened and verified for SIP

**Full Specification:** See [INTERNAL_PLAYER_PHASE5_CHECKLIST.md](INTERNAL_PLAYER_PHASE5_CHECKLIST.md) and [INTERNAL_PLAYER_PLAYER_SURFACE_CONTRACT_PHASE5.md](INTERNAL_PLAYER_PLAYER_SURFACE_CONTRACT_PHASE5.md)

**Key Principles:**
- SIP-Only: No modifications to legacy `InternalPlayerScreen.kt`
- Contract-Driven: Behavior defined by Phase 5 contract
- Black Bars Must Be Black: All non-video areas must be pure black
- Modern Trickplay: Responsive FF/RW with visual feedback
- Non-Annoying Auto-Hide: Appropriate timeouts for TV vs phone

**Validation Summary (2025-11-27):**
- ✅ Contract compliance verified for all requirements
- ✅ Code quality improved: Magic numbers replaced with named constants
  - `PlayerSurfaceConstants`: SWIPE_THRESHOLD_PX, LARGE_SWIPE_THRESHOLD_PX, SMALL_SEEK_DELTA_MS, LARGE_SEEK_DELTA_MS
  - `ControlsConstants`: AUTO_HIDE_TIMEOUT_TV_MS, AUTO_HIDE_TIMEOUT_TOUCH_MS, OVERLAY_BACKGROUND_OPACITY, FADE_ANIMATION_DURATION_MS
- ✅ Integration tests added covering combined scenarios
- ✅ All 87+ tests passing (black bars, aspect ratio, trickplay, auto-hide, integration)
- ✅ No regressions in Phase 1-4 behavior
- ✅ Legacy InternalPlayerScreen remains unchanged

**Note:** SIP is now the reference implementation for PlayerSurface behavior.

### Task Group 1: PlayerSurface Foundation & Black Bars ✅ COMPLETE

- ✅ Task 1.1: PlayerView Background Configuration
  - ✅ Set `setShutterBackgroundColor(Color.BLACK)` in PlayerView factory
  - ✅ Set `setBackgroundColor(Color.BLACK)` in PlayerView factory
  - ✅ Legacy Reference: No explicit black background in legacy (bug fix)
  
- ✅ Task 1.2: Compose Container Background
  - ✅ Add `.background(Color.Black)` to PlayerSurface Box container
  - ✅ Ensure background persists during aspect ratio changes

- ✅ Task 1.3: XML Layout Black Background
  - ✅ Add `android:background="@android:color/black"` to compose_player_view.xml

### Task Group 2: Aspect Ratio Modes & Switching ✅ COMPLETE

- ✅ Task 2.1: AspectRatioMode Enum Cleanup
  - ✅ Verify FIT/FILL/ZOOM align with contract definitions
  - ✅ STRETCH kept for legacy compatibility
  
- ✅ Task 2.2: Aspect Ratio Cycling Logic
  - ✅ Add `AspectRatioMode.next()` helper function
  - ✅ Cycle: FIT → FILL → ZOOM → FIT
  - ✅ Legacy Reference: L1374-1379

- ✅ Task 2.3: Aspect Ratio Controller Integration
  - ✅ Wire `onCycleAspectRatio` to state updates
  - ✅ Ensure black background maintained during switch

### Task Group 3: Trickplay Behavior & UI Hooks ✅ COMPLETE

- ✅ Task 3.1: Trickplay State Model
  - ✅ Add `trickplayActive: Boolean` to InternalPlayerUiState
  - ✅ Add `trickplaySpeed: Float` to InternalPlayerUiState
  - ✅ Add `seekPreviewVisible` and `seekPreviewTargetMs` fields
  - ✅ Legacy Reference: L1467-1470 (trickplaySpeeds, ffStage, rwStage)

- ✅ Task 3.2: Trickplay Controller Methods
  - ✅ Add `onStartTrickplay(direction: Int)` callback
  - ✅ Add `onStopTrickplay(applyPosition: Boolean)` callback
  - ✅ Add `onCycleTrickplaySpeed()` callback
  - ✅ Add `onStepSeek(deltaMs: Long)` callback
  - ✅ Add `TrickplayDirection` enum

- ✅ Task 3.3: Trickplay Session Logic (Foundation)
  - ✅ State model and controller callbacks defined
  - ✅ ExoPlayer speed manipulation deferred to session wiring (future activation)
  - ✅ Legacy Reference: L1473-1487 (stopTrickplay())

- ✅ Task 3.4: Seek Preview Logic
  - ✅ Add `seekPreviewVisible: Boolean` to InternalPlayerUiState
  - ✅ Add `seekPreviewTargetMs: Long?` to InternalPlayerUiState
  - ✅ UI rendering implemented
  - ✅ Legacy Reference: L1489-1507

- ✅ Task 3.5: Trickplay UI in InternalPlayerControls
  - ✅ Add `TrickplayIndicator` composable (e.g., "2x ►►" or "◀◀ 3x")
  - ✅ Add `SeekPreviewOverlay` showing target position and delta
  - ✅ Use AnimatedVisibility for smooth transitions (150ms fade)

- ✅ Task 3.6: Trickplay Gesture Handling
  - ✅ VOD/SERIES: Horizontal swipe triggers step seek (±10s/±30s)
  - ✅ Swipe magnitude determines seek delta (small=10s, large=30s)
  - ✅ No conflict with LIVE channel zapping

### Task Group 4: Controls Auto-Hide (TV vs Touch) ✅ COMPLETE

- ✅ Task 4.1: Auto-Hide State Model
  - ✅ Add `controlsVisible: Boolean` to InternalPlayerUiState
  - ✅ Add `controlsTick: Int` to InternalPlayerUiState
  - ✅ Add `hasBlockingOverlay` computed property
  - ✅ Legacy Reference: L1347-1348

- ✅ Task 4.2: Auto-Hide Timer Logic
  - ✅ TV timeout: 7 seconds (contract: 5-7s)
  - ✅ Phone/tablet timeout: 4 seconds (contract: 3-5s)
  - ✅ Block auto-hide when modal overlays open or trickplay active
  - ✅ Legacy Reference: L1438-1451

- ✅ Task 4.3: Activity Detection
  - ✅ Add `onUserInteraction()` callback
  - ✅ controlsTick mechanism resets timer
  - ✅ Touch tap/swipe resets timer

- ✅ Task 4.4: Never-Hide Conditions
  - ✅ Never hide with CC menu open
  - ✅ Never hide with settings open
  - ✅ Never hide with kid block overlay
  - ✅ Never hide during active trickplay

- ✅ Task 4.5: Tap-to-Toggle Controls
  - ✅ Wire PlayerSurface `onTap` to `onToggleControlsVisibility()`
  - ✅ Controls wrapped in AnimatedVisibility (200ms fade)
  - ✅ Legacy Reference: L1836-1837

### Task Group 5: Tests & Validation ✅ COMPLETE

- ✅ Task 5.1: PlayerSurface Black-Bar Tests (16 tests)
  - ✅ PlayerView background is black
  - ✅ Compose container background is black
  - ✅ AspectRatioMode mapping and cycling verified

- ✅ Task 5.2: Aspect Ratio Tests (included in 5.1)
  - ✅ FIT/FILL/ZOOM modes work correctly
  - ✅ Mode switching preserves black background

- ✅ Task 5.3: Trickplay Tests (24 tests)
  - ✅ Enter/exit trickplay correctly
  - ✅ Speed values and TrickplayDirection enum
  - ✅ Aspect ratio unchanged during trickplay

- ✅ Task 5.4: Auto-Hide Tests (33 tests)
  - ✅ Correct timeouts (TV 7s, phone 4s)
  - ✅ hasBlockingOverlay computed property
  - ✅ Never hide with overlays open

- ✅ Task 5.5: Integration Tests (16 tests in Phase5IntegrationTest.kt)
  - ✅ Trickplay + Aspect Ratio interactions
  - ✅ CC Menu + Auto-Hide interactions
  - ✅ Multi-feature state consistency
  - ✅ Rapid interaction sequences

### Files Overview

**Files Modified:**
- `internal/ui/PlayerSurface.kt` – Black bars, step seek gestures, tap-to-toggle
- `internal/state/InternalPlayerState.kt` – Trickplay fields, controls visibility, TrickplayDirection
- `internal/ui/InternalPlayerControls.kt` – Auto-hide LaunchedEffect, TrickplayIndicator, SeekPreviewOverlay
- `res/layout/compose_player_view.xml` – Black background

**Files NOT Modified:**
- ❌ `player/InternalPlayerScreen.kt` – Untouched (legacy active runtime)

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
