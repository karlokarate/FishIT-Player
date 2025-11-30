# Internal Player Phase 8 Checklist – Performance, Lifecycle & Stability

**Version:** 1.0  
**Scope:** Unified PlaybackSession lifecycle, MiniPlayer lifecycle hygiene, playback-aware workers, memory management, Compose performance  
**Contract Reference:** [INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md](INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md)

---

## Overview

Phase 8 ensures that the unified PlaybackSession + In-App MiniPlayer behave robustly and efficiently under real-world conditions:

- App background/foreground
- Rotation & configuration changes
- Process death / Activity recreation
- Background workers (Xtream, Telegram, DB, EPG)
- Memory pressure & resource reuse

The goal is to make playback **stutter-free, leak-free, and resilient**, while keeping performance predictable and testable.

**Key Principles:**
1. **One Playback Session per app** – No second ExoPlayer for SIP or MiniPlayer
2. **Warm Resume, Cold Start only when necessary** – Lifecycle events must not unnecessarily rebuild PlaybackSession/ExoPlayer
3. **Playback-Aware Resource Usage** – Workers throttle when playback is active
4. **No UI Jank** – No main-thread blocking, no unnecessary recomposition bursts
5. **Full Observability** – Errors and performance symptoms are testable and debuggable

---

## Phase 8 Current State Analysis

### PlaybackSession Lifecycle

**Where is ExoPlayer created and released?**

| Component | Creation | Release |
|-----------|----------|---------|
| `PlaybackSession` singleton (`playback/PlaybackSession.kt`) | Via `acquire(context) { builder() }` – creates if null | Via `release()` – removes listener, releases player, resets state |
| `InternalPlayerSession` SIP (`player/internal/session/`) | Now uses `PlaybackSession.acquire()` (Phase 7 change) | Does NOT release on dispose – shared ownership via PlaybackSession |
| Legacy `InternalPlayerScreen` | Creates own ExoPlayer in `rememberExoPlayer()` | Releases on `DisposableEffect` `onDispose` |

**How does PlaybackSession react to Activity lifecycle?**

- **Current State**: PlaybackSession is an `object` singleton with no lifecycle observer
- **onPause**: No automatic response – player continues
- **onStop**: No automatic response – player continues
- **onResume**: No automatic rebinding logic
- **onDestroy**: No automatic release

**Lifecycle Gap**: There is currently no `SessionLifecycleState` enum or state machine in PlaybackSession. The session simply holds a player reference without tracking lifecycle transitions.

**ExoPlayer.Builder usage outside PlaybackSession:**
- ❌ Legacy `InternalPlayerScreen.kt` still creates its own ExoPlayer (L525-558)
- ✅ SIP `InternalPlayerSession` now uses `PlaybackSession.acquire()` (Phase 7)

### UI / Navigation

**Library/Detail → Player navigation:**
- Via NavController route `player?url=...&type=...&mediaId=...`
- InternalPlayerEntry builds PlaybackContext
- Delegates to legacy InternalPlayerScreen (or SIP in future)

**Player → MiniPlayer → Library:**
- PIP button: Now calls `onEnterMiniPlayer` callback (Phase 7)
- MiniPlayerManager.enterMiniPlayer() stores returnRoute
- Navigation: popBackStack() removes full player from stack
- MiniPlayer overlay appears via HomeChromeScaffold

**Player/MiniPlayer → Home (EXIT_TO_HOME):**
- Double-BACK triggers `TvAction.EXIT_TO_HOME` (Phase 6)
- MiniPlayer remains visible when going to home (per contract)

**UI Recreation Risks:**
- Config changes cause Activity recreation → Compose recomposition
- Without SavedState, playback position could be lost
- MiniPlayerState is a singleton, so it survives recomposition

### System PiP & Rotation

**System PiP on phone/tablet (Phase 7):**
- `MainActivity.onUserLeaveHint()` triggers PiP entry
- Conditions: NOT TV, isPlaying, MiniPlayer not visible
- `buildPictureInPictureParams()` uses 16:9 ratio

**Rotation/Config changes:**
- No explicit rotation lock in SIP code
- `configChanges` in AndroidManifest may prevent Activity recreation
- No SavedStateHandle in PlaybackSession
- PlaybackSession singleton survives config changes if Activity isn't destroyed

**Missing:**
- No explicit position/track restoration after config changes
- No ViewModel backing for SIP session state

### Workers & Background Jobs

**Existing Workers:**

| Worker | Purpose | Playback Awareness |
|--------|---------|-------------------|
| `XtreamDeltaImportWorker` | Delta import of Xtream content | Checks `m3uWorkersEnabled` pref, no playback check |
| `XtreamDetailsWorker` | Enriches VOD/Series details | No playback awareness |
| `TelegramSyncWorker` | Syncs Telegram chat content | Uses `Dispatchers.IO`, no playback check |
| `ScreenTimeResetWorker` | Resets daily screen time | No playback awareness |
| `ObxKeyBackfillWorker` | Backfills ObjectBox keys | No playback awareness |

**Current Worker Behavior:**
- Workers use `Dispatchers.IO` for heavy operations ✅
- No worker checks `PlaybackSession.isPlaying` before heavy work
- No throttling mechanism when playback is active
- `SchedulingGateway` coordinates scheduling but not playback-aware pausing

### Memory & Resources

**Potential Issues:**

1. **Multiple ExoPlayer instances**: Legacy InternalPlayerScreen creates its own player separate from PlaybackSession
2. **Bitmap Loading**: Uses Coil/AsyncImage with caching ✅
3. **FocusKit Effects**: Multiple `graphicsLayer`/`drawWithContent` chains on focusable items could accumulate
4. **Static References**: No obvious static Activity/Context references in session/manager layers

**LeakCanary**: Not currently integrated in debug builds

---

## Phase 8 – Goals & Constraints (from contract)

### SessionLifecycleState State Machine (Contract Section 4.2)

PlaybackSession must maintain an internal state machine:

```kotlin
enum class SessionLifecycleState {
    IDLE,        // no media loaded
    PREPARED,    // media loaded, ready to play
    PLAYING,     // actively playing
    PAUSED,      // paused but retained
    BACKGROUND,  // app in background, still allowed to play
    STOPPED,     // playback stopped, resources mostly freed
    RELEASED     // ExoPlayer released, session not usable
}
```

### Lifecycle Rules (Contract Section 4.3)

| Event | Behavior |
|-------|----------|
| onResume (foreground) | Re-bind UI surfaces, do NOT recreate ExoPlayer |
| onPause | If video + no background audio: pause; else stay in PREPARED/PAUSED |
| onStop | Do NOT immediately release ExoPlayer; go to BACKGROUND if still active |
| onDestroy | Only release ExoPlayer if no route wants the session |

### Rotation & Config Changes (Contract Section 4.4)

Rotation/config changes MUST NOT:
- Reset playback position
- Reset aspect ratio
- Reset subtitle/audio track selection

UI components must re-bind to existing PlaybackSession, not recreate it.

### Navigation & Backstack (Contract Section 5)

**Full → Mini:**
- PIP Button → `MiniPlayerManager.enterMiniPlayer()`
- Pop SIP player from backstack
- PlaybackSession unchanged

**Mini → Full:**
- Expand/Maximize → navigate to full player route
- `MiniPlayerState.visible = false`

**EXIT_TO_HOME (Double BACK):**
- MiniPlayer: remains visible if playback is running

### System PiP vs In-App MiniPlayer (Contract Section 6)

**In-App MiniPlayer:**
- NOT coupled with `enterPictureInPictureMode()`
- Controlled only by UI/TV Input actions

**System PiP (Phone/Tablet Only):**
- Triggered by: Home button, Recents, OS events
- NEVER triggered by PIP UI button
- NEVER activated when MiniPlayer is visible

### PlaybackPriority (Contract Section 7)

Introduce:
```kotlin
object PlaybackPriority {
    val isPlaybackActive: StateFlow<Boolean> // derived from PlaybackSession
}
```

Workers must throttle when `isPlaybackActive == true`:
- Add delay between heavy network calls
- No large DB migrations during playback
- All DB/network work on `Dispatchers.IO`

### Memory & Leak Hygiene (Contract Section 8)

- ExoPlayer created/released ONLY through PlaybackSession
- LeakCanary in debug builds for PlaybackSession/MiniPlayer
- No static refs to Activity/Context in session/manager layers
- Images via AsyncImage/Coil with caching

### Compose & FocusKit Performance (Contract Section 9)

**Recomposition Hygiene:**
- Split hot vs cold state in InternalPlayerUiState
- Hot paths (position, buffering, isPlaying) in isolated small Composables

**FocusKit:**
- Consolidate focus effects to reduce graphicsLayer/drawWithContent overhead

**MiniPlayerOverlay:**
- Short, state-driven animations
- No excessive layout jumps on show/hide

### Error Handling & Recovery (Contract Section 10)

**Streaming Errors:**
- PlaybackSession signals via `error: StateFlow<PlayerError?>`
- UI shows soft error overlay, does NOT crash

**Worker Errors:**
- Workers NEVER kill PlaybackSession
- Heavy errors displayed after playback ends

---

## Implementation Checklist

### Group 1 – PlaybackSession Lifecycle & Ownership ✅ DONE

- [x] **1.1** Define `SessionLifecycleState` enum in `playback/` package
  - States: IDLE, PREPARED, PLAYING, PAUSED, BACKGROUND, STOPPED, RELEASED
  - Add `_lifecycleState: MutableStateFlow<SessionLifecycleState>` to PlaybackSession
  - Expose as `val lifecycleState: StateFlow<SessionLifecycleState>`

- [x] **1.2** Implement lifecycle state transitions in PlaybackSession
  - IDLE → PREPARED: on `player.prepare()` with media item
  - PREPARED → PLAYING: on `player.play()` when ready
  - PLAYING → PAUSED: on `player.pause()` or lifecycle pause
  - PLAYING/PAUSED → BACKGROUND: on app background with active playback
  - BACKGROUND → PLAYING/PAUSED: on app foreground
  - Any → STOPPED: on `stop()` call
  - Any → RELEASED: on `release()` call

- [x] **1.3** Add lifecycle observer to PlaybackSession
  - Create `PlaybackLifecycleController` composable
  - Observe `ON_RESUME`, `ON_PAUSE`, `ON_STOP`, `ON_DESTROY`
  - Wire to PlaybackSession state transitions via `onAppForeground()` / `onAppBackground()`
  - Place in `MainActivity` or `HomeChromeScaffold` composition tree

- [x] **1.4** Ensure ExoPlayer is created/released ONLY inside PlaybackSession
  - Audit all `ExoPlayer.Builder` usages in codebase
  - Document legacy InternalPlayerScreen as exception (do not modify)
  - Verify SIP InternalPlayerSession uses `PlaybackSession.acquire()` ✅

- [x] **1.5** Ensure onPause/onStop/onResume do NOT recreate ExoPlayer unnecessarily
  - `onAppForeground()` / `onAppBackground()` methods do not recreate player
  - Warm resume: Player instance is preserved, only lifecycle state changes
  - Test: Mini ↔ Full transition → no rebuffering (via existing shared PlaybackSession)

- [x] **1.6** Add unit tests: `PlaybackSessionLifecycleTest`
  - Test state transitions: foreground/background
  - Test initial state is IDLE
  - Test stop() → STOPPED, release() → RELEASED
  - Test helper properties: isSessionActiveByLifecycle, canResume

### Group 2 – UI Rebinding & Rotation ✅ DONE

- [x] **2.1** Ensure PlayerSurface rebinds to existing PlaybackSession on config changes
  - `AndroidView(PlayerView)` attaches to player on recomposition
  - Checks `PlaybackSession.lifecycleState` for rebinding behavior
  - Phase 8 comments document warm resume vs cold start states
  - No new player creation on rotation

- [x] **2.2** Ensure MiniPlayerOverlay rebinds to existing PlaybackSession on config changes
  - Uses `PlaybackSession.current()` in update block
  - MiniPlayerState preserved via singleton DefaultMiniPlayerManager
  - visible/mode/anchor/size/position all survive config changes
  - Black background set for consistent surface swap appearance

- [x] **2.3** Prevent playback resets on rotation
  - Position persists via PlaybackSession (player instance survives)
  - AspectRatioMode persists in InternalPlayerUiState (session-level state)
  - SubtitleStyle persists in InternalPlayerUiState (session-level state)
  - Track selections persist at ExoPlayer level (player instance survives)

- [x] **2.4** TV vs Phone/Tablet orientation handling
  - TV: Generally fixed landscape, Phase 8 changes do not affect
  - Phone/Tablet: Rotation allowed, player continues without restart
  - Existing `settings.rotationLocked` respected where used

- [x] **2.5** Add tests: `RotationResilienceTest`
  - RotationDoesNotRecreateExoPlayer test (session singleton preserved)
  - MiniPlayerSurvivesRotation tests (visible/mode/anchor/size/position)
  - AspectAndSubtitlesPreservedOnRotation tests (enum stability, copy preservation)
  - Full rotation scenario test (combined state preservation)
  - TV lifecycle state enum completeness verified

### Group 3 – Navigation & Backstack Stability ✅ DONE

- [x] **3.1** Validate Full → Mini → Full flows
  - Full player → PIP button → Mini visible → Expand → Full player restored
  - PlaybackSession unchanged throughout
  - returnRoute correctly stored and used
  - Created `PlayerNavigationHelper` with single-top navigation pattern

- [x] **3.2** Validate EXIT_TO_HOME (double BACK) with MiniPlayer
  - MiniPlayer visible + double BACK → navigate to home
  - MiniPlayer remains visible if playback active (contract decision)
  - PlaybackSession unchanged
  - Updated `DoubleBackNavigator` with MiniPlayer awareness

- [x] **3.3** Ensure no "ghost" SIP players remain on backstack
  - After Mini → Full → back, old entries cleared via `launchSingleTop = true`
  - `PlayerNavigationHelper.navigateToPlayer()` prevents duplicate player routes
  - Route identification via `PLAYER_ROUTE_PREFIX` constant

- [x] **3.4** Add tests: `NavigationBackstackTest`
  - Full/Mini/Home combinations
  - Backstack integrity after multiple transitions
  - EXIT_TO_HOME behavior in `GlobalDoubleBackExitTest`
  - Extended `MiniPlayerNavigationTest` with session continuity tests

### Group 4 – System PiP vs In-App MiniPlayer

- [ ] **4.1** Verify PIP UI button NEVER calls `enterPictureInPictureMode()`
  - Audit SIP InternalPlayerControls.kt
  - Button must only call `controller.onEnterMiniPlayer`

- [ ] **4.2** Verify system PiP ONLY when leaving app on phone/tablet
  - Conditions: playback active, MiniPlayer NOT visible, NOT TV device
  - `MainActivity.onUserLeaveHint()` triggers correctly

- [ ] **4.3** Ensure safe restore from system PiP
  - Returning from system PiP → PlaybackSession still valid
  - Position/tracks preserved
  - No new player instance created

- [ ] **4.4** Verify TV devices NEVER trigger system PiP from app code
  - `isTvDevice()` check in `tryEnterSystemPip()`

- [ ] **4.5** Add tests: `SystemPiPIntegrationTest`
  - Phone/tablet: PiP entry on Home button
  - Fire TV: no PiP from app code
  - Restore from PiP without player recreation

### Group 5 – Playback-Aware Worker Scheduling ✅ DONE

- [x] **5.1** Introduce `PlaybackPriority` object
  - `val isPlaybackActive: StateFlow<Boolean>` derived from PlaybackSession.isPlaying && lifecycleState in {PLAYING, PAUSED, BACKGROUND}
  - Place in `playback/` package
  - Added `PLAYBACK_THROTTLE_MS = 500L` constant
  - Added `shouldThrottle()` convenience method

- [x] **5.2** Update `XtreamDeltaImportWorker` with playback awareness
  - Check `PlaybackPriority.isPlaybackActive` at start
  - If active: add `delay(PLAYBACK_THROTTLE_MS)` between heavy operations
  - Throttle points: before seed, before delta, before details, before EPG prefetch

- [x] **5.3** Update `XtreamDetailsWorker` with playback awareness
  - Same throttling pattern as XtreamDeltaImportWorker
  - Throttle before details chunk and before EPG prefetch

- [x] **5.3b** Update `ObxKeyBackfillWorker` with playback awareness
  - Throttle at start and between Live/VOD/Series entity types
  - Already uses `withContext(Dispatchers.IO)` for main thread hygiene

- [x] **5.4** Ensure no heavy CPU/IO on main thread
  - All workers already use `CoroutineWorker` (runs on Dispatchers.Default by default)
  - ObxKeyBackfillWorker explicitly uses `withContext(Dispatchers.IO)`
  - No ObjectBox operations on main thread in workers

- [x] **5.5** Add tests: `WorkerThrottleTest`
  - Tests verify throttle not applied when playback inactive
  - Tests verify throttle delay constant is 500ms
  - Tests verify shouldThrottle() convenience method

- [x] **5.6** Add tests: `PlaybackPriorityStateTest`
  - Tests verify isPlaybackActive reflects lifecycle state correctly
  - Tests verify STOPPED/IDLE/RELEASED → isPlaybackActive false
  - Tests verify PLAYBACK_THROTTLE_MS constant

**NOTE:** TelegramSyncWorker NOT modified per task constraint (no Telegram module changes).

### Group 6 – Memory & Leak Hygiene ✅ PARTIAL DONE

- [x] **6.1** Integrate LeakCanary in debug builds
  - Already present: `debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")`
  - Focus on PlaybackSession, MiniPlayerManager, Activity leaks

- [x] **6.2** Remove/avoid static references to Context/Activity/View
  - Audit PlaybackSession: no static Context storage ✓
  - Audit MiniPlayerManager: no static Activity refs ✓
  - FocusKit: No retained references (Composable-only) ✓

- [x] **6.3** Ensure images loaded via AsyncImage/Coil with caching
  - Verify poster/thumbnail loading uses Coil ✓
  - No manual Bitmap creation in player UI ✓

- [ ] **6.4** Add tests: `LeakSimulationTest` (or manual QA checklist)
  - Open player → close → verify GC clears player
  - MiniPlayer show/hide cycles don't accumulate views
  - Rotation doesn't leak Activities

### Group 7 – Compose & FocusKit Performance ✅ DONE

- [x] **7.1** Audit InternalPlayerUiState usage
  - Identified hot paths: positionMs, durationMs, isPlaying, isBuffering, trickplayActive, trickplaySpeed, controlsTick, controlsVisible
  - Identified cold paths: playbackType, aspectRatioMode, subtitleStyle, kidActive, kidBlocked, dialog visibility, live TV metadata
  - Created `PlayerHotState` and `PlayerColdState` data classes for state separation

- [x] **7.2** Isolate hot paths into small Composables
  - Created `PlayerHotState` with computed properties (formattedPosition, formattedDuration, progressFraction)
  - Created `PlayerColdState` with computed properties (isLive, isSeries, hasBlockingOverlay)
  - Both state classes have `fromFullState()` factory methods for extraction from InternalPlayerUiState
  - Documentation guides UI to collect HOT state only in small focused composables

- [x] **7.3** Consolidate FocusKit visual effects
  - Created `FocusDecorationConfig` data class with presets (Clickable, IconButton, Card, None)
  - Created `Modifier.focusDecorations()` that consolidates scale, shadow, border, halo, and content tint
  - Single graphicsLayer + single drawWithContent per element (no stacking)
  - Added to FocusKit facade for consistent access

- [x] **7.4** Add tests: `FocusKitPerformanceTest` and `PlayerHotColdStateTest`
  - FocusDecorationConfig presets and defaults verified
  - Hot/Cold state extraction and computed properties tested
  - State immutability and separation verified

### Group 8 – Error Handling & Recovery

- [ ] **8.1** Verify soft error reporting for streaming errors
  - PlaybackSession exposes `error: StateFlow<PlaybackException?>`
  - UI shows error overlay, does NOT crash app
  - Error overlay has "Retry" and "Close" options

- [ ] **8.2** Ensure PlaybackSession errors do not crash UI
  - `try-catch` around player operations
  - Error state propagates via StateFlow

- [ ] **8.3** Ensure worker failures do not kill PlaybackSession
  - Workers isolated via WorkManager
  - Failed workers logged but do not affect active playback
  - Heavy errors shown after playback ends (not during)

- [ ] **8.4** Add tests: `PlaybackErrorRecoveryTest`
  - Simulate network error → UI shows message → retry works
  - Simulate 401/404 → appropriate error message

- [ ] **8.5** Add tests: `WorkerErrorIsolationTest`
  - Worker failure during playback → playback unaffected
  - Error logged to diagnostics

### Group 9 – Regression Suite

- [ ] **9.1** Verify Phase 4 behavior unchanged (Subtitles/CC)
  - CC menu opens/closes correctly
  - Style changes apply
  - Track selection works for VOD/Series/Live

- [ ] **9.2** Verify Phase 5 behavior unchanged (Black bars, aspect ratio, trickplay)
  - Black bars maintained
  - Aspect ratio cycling works
  - Trickplay/seek gestures work
  - Auto-hide controls work

- [ ] **9.3** Verify Phase 6 behavior unchanged (TV input, FocusZones, EXIT_TO_HOME)
  - DPAD navigation works
  - Focus zones receive focus correctly
  - Kids mode filtering active
  - EXIT_TO_HOME via double-BACK works

- [ ] **9.4** Verify Phase 7 behavior unchanged (MiniPlayer, system PiP)
  - MiniPlayer shows/hides correctly
  - Resize mode works (FF/RW size, DPAD move, OK confirm, BACK cancel)
  - Focus toggle via long-press PLAY works
  - System PiP on phones/tablets only

- [ ] **9.5** Create regression test suite manifest
  - Document all manual QA scenarios
  - Mark automated vs manual tests

---

## Quality Gates

### Build/Lint

- [ ] `./gradlew ktlintCheck` passes
- [ ] `./gradlew detekt` passes with complexity ≤ 10 per function
- [ ] `./gradlew lintDebug` passes with no new PiP/lifecycle warnings

### Tests

- [ ] All Phase 8 unit tests pass
- [ ] All Phase 4-7 regression tests pass
- [ ] LeakCanary reports no leaks in debug testing

### Performance

- [ ] Compose recomposition count acceptable during playback
- [ ] No main-thread ANRs during worker execution + playback
- [ ] Memory stable during extended playback sessions

---

## Files to Create

| File | Purpose | Status |
|------|---------|--------|
| `playback/SessionLifecycleState.kt` | Lifecycle state enum | ✅ Created (Task 1) |
| `playback/PlaybackPriority.kt` | Playback-aware scheduling helper | ✅ Created (Task 3) |
| `playback/PlaybackLifecycleController.kt` | Lifecycle observer composable | ✅ Created (Task 1) |
| `test/.../PlaybackSessionLifecycleTest.kt` | Lifecycle transition tests | ✅ Created (Task 1) |
| `test/.../RotationResilienceTest.kt` | Config change tests | ✅ Created (Task 2) |
| `test/.../PlaybackPriorityStateTest.kt` | Playback priority state tests | ✅ Created (Task 3) |
| `test/.../WorkerThrottleTest.kt` | Worker throttling tests | ✅ Created (Task 3) |
| `test/.../NavigationBackstackTest.kt` | Navigation integrity tests | ⬜ Pending |
| `test/.../SystemPiPIntegrationTest.kt` | System PiP behavior tests | ⬜ Pending |
| `test/.../PlaybackErrorRecoveryTest.kt` | Error handling tests | ⬜ Pending |
| `test/.../WorkerErrorIsolationTest.kt` | Worker isolation tests | ⬜ Pending |
| `test/.../ComposePerfSmokeTest.kt` | Compose performance tests | ⬜ Pending |

## Files to Modify

| File | Changes | Status |
|------|---------|--------|
| `playback/PlaybackSession.kt` | Add lifecycleState, transitions, priority exposure | ✅ Done (Task 1) |
| `playback/PlaybackSessionController.kt` | Add lifecycleState to interface | ✅ Done (Task 1) |
| `work/XtreamDeltaImportWorker.kt` | Add playback-aware throttling | ✅ Done (Task 3) |
| `work/XtreamDetailsWorker.kt` | Add playback-aware throttling | ✅ Done (Task 3) |
| `work/ObxKeyBackfillWorker.kt` | Add playback-aware throttling | ✅ Done (Task 3) |
| `player/internal/ui/PlayerSurface.kt` | Add lifecycle-aware rebinding | ✅ Done (Task 2) |
| `player/miniplayer/MiniPlayerOverlay.kt` | Verify rebinding on config change | ✅ Done (Task 2) |
| `app/build.gradle.kts` | LeakCanary debug dependency | ✅ Already present |

## Files NOT Modified (Per Task 3 Constraints)

- ❌ `telegram/work/TelegramSyncWorker.kt` – Telegram modules untouched per constraint
- ❌ `telegram/**/*.kt` – All Telegram modules untouched per constraint
- ❌ `player/InternalPlayerScreen.kt` – Legacy remains untouched
- ❌ `tv/input/*` – Phase 6 TV input layer stable
- ❌ Phase 4-7 core implementations (unless fixing bugs)

---

## Contract Reference Summary

All implementations must align with:
- **Section 4**: PlaybackSession Lifecycle Contract
- **Section 5**: Navigation & Backstack Stability
- **Section 6**: System PiP vs In-App MiniPlayer
- **Section 7**: Background Workers & Playback Priority
- **Section 8**: Memory & Resource Management
- **Section 9**: Compose & FocusKit Performance
- **Section 10**: Error Handling & Recovery
- **Section 11**: Quality-Tooling & Test Requirements
- **Section 12**: Ownership

---

**Last Updated:** 2025-11-29
