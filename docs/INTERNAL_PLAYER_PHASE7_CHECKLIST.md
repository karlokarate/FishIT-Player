# Internal Player Phase 7 Checklist – Unified PlaybackSession & In-App MiniPlayer

**Version:** 1.0  
**Scope:** SIP-only unified PlaybackSession, in-app MiniPlayer, PiP behavior refactor  
**Contract Reference:** [INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md](INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md)

---

## Overview

Phase 7 introduces a **unified PlaybackSession** that owns the ExoPlayer instance globally, and an **In-App MiniPlayer** overlay that allows video playback to continue seamlessly while navigating the app. All work is SIP-only and does not modify the legacy `InternalPlayerScreen.kt`.

**Key Principles:**
1. **SIP-Only**: No changes to legacy `InternalPlayerScreen.kt`
2. **Contract-Driven**: Behavior defined by `INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md`
3. **Single PlaybackSession**: One shared playback session across the entire app
4. **In-App MiniPlayer**: Floating overlay, not system PiP (for TV devices)
5. **System PiP for Phones/Tablets Only**: Native PiP only when backgrounding the app
6. **Fire TV**: UI PIP button → In-App MiniPlayer only, never `enterPictureInPictureMode()`

---

## Phase 7 Current State Analysis

### Current ExoPlayer Ownership

**Where is ExoPlayer created?**
- **SIP Path**: `InternalPlayerSession.kt` (`player/internal/session/`) creates its own `ExoPlayer` instance in the `rememberInternalPlayerSession()` composable via `ExoPlayer.Builder(context).build()`.
- **Legacy Path**: `InternalPlayerScreen.kt` (monolithic) creates its own `ExoPlayer` locally.
- **Existing PlaybackSession**: `PlaybackSession.kt` (in `playback/`) is a singleton holder that:
  - Uses `AtomicReference<ExoPlayer?>` to hold a shared player instance
  - Provides `acquire(context, builder)` to get/create a shared player
  - Tracks `currentSource` (URL) separately

**Current Issue**: InternalPlayerSession does NOT use `PlaybackSession.acquire()`. It creates its own player instance directly, defeating the purpose of having a global session.

### Current MiniPlayer/PiP State

**Existing MiniPlayer Components:**
- **`MiniPlayerState.kt`**: A singleton object tracking:
  - `visible: StateFlow<Boolean>` – whether mini player is shown
  - `descriptor: StateFlow<MiniPlayerDescriptor?>` – current media metadata
  - `focusRequests: SharedFlow<Unit>` – focus request events

- **`MiniPlayerDescriptor`**: Data class with type, url, mediaId, seriesId, season, episode, mimeType, origin, title, subtitle

- **`MiniPlayerHost.kt`**: A TV-only composable that:
  - Renders an `AndroidView(PlayerView)` with video
  - Uses `PlaybackSession.current()` to attach to the shared player
  - Shows "Zum Player" (resume) and "Stop" buttons
  - Only visible when `MiniPlayerState.visible == true`

**PiP Overlay Button Wiring (Legacy InternalPlayerScreen.kt):**
- `requestPictureInPicture()` function:
  - On TV: Calls `MiniPlayerState.show()` (in-app mini player)
  - On Phone/Tablet: Calls `activity.enterPictureInPictureMode()`
- PiP buttons in overlay controls call `requestPictureInPicture()`

**SIP InternalPlayerControls.kt:**
- `onPipClick` callback passed to `PlayerOverlayContent`
- `onPipClick: () -> Unit` parameter
- `IconButton(onClick = onPipClick)` – currently calls `requestPictureInPicture(activity)` which triggers native PiP on all devices

### Navigation Patterns

**Current Library/Detail → Player navigation:**
- Via NavController route: `player?url=...&type=...&mediaId=...`
- InternalPlayerEntry builds `PlaybackContext` from route params
- Delegates to legacy `InternalPlayerScreen`

**Current Player → back to library:**
- `onClose()` callback pops navigation
- MiniPlayer: `MiniPlayerDescriptor.buildRoute()` creates a route string for resume

**Return Route Storage:**
- `MiniPlayerDescriptor` stores enough context to rebuild the player route
- No explicit `returnRoute` storage for returning to the originating screen (e.g., which library position)

### FocusZone Integration

**Existing FocusZoneId enum includes:**
- `PLAYER_CONTROLS`, `QUICK_ACTIONS`, `TIMELINE`, etc.
- **Missing**: `MINI_PLAYER` FocusZoneId for MiniPlayer focus management

**TvScreenId enum includes:**
- `MINI_PLAYER` already defined for TV input config

**TvAction enum includes:**
- `PIP_SEEK_FORWARD`, `PIP_SEEK_BACKWARD`, `PIP_TOGGLE_PLAY_PAUSE`
- `PIP_ENTER_RESIZE_MODE`, `PIP_CONFIRM_RESIZE`, `PIP_MOVE_*`
- **Missing**: `TOGGLE_MINI_PLAYER_FOCUS` for long-press PLAY behavior

### What Must Be Preserved vs Replaced

**PRESERVE:**
- `PlaybackSession` singleton holder pattern (but extend it)
- `MiniPlayerState` global state management
- `MiniPlayerDescriptor` data model
- `MiniPlayerHost` composable (reuse and extend)
- FocusKit integration patterns
- All Phase 4-6 player behavior (subtitles, surface, TV input)

**REPLACE/REFACTOR:**
- SIP `InternalPlayerSession` must use `PlaybackSession` instead of creating local ExoPlayer
- PIP button in SIP controls must call `MiniPlayerManager.enterMiniPlayer()` not `requestPictureInPicture()`
- System PiP triggering must be moved to Activity lifecycle, not UI button

---

## Phase 7 Goals & Constraints (from contract)

### 1. Single Global PlaybackSession (Contract Section 3.1)

**Goal**: Exactly one shared playback session owns the ExoPlayer instance.

**What it owns:**
- The `ExoPlayer` instance
- Playback lifecycle (play, pause, stop, release)
- Position/duration state
- Track selection (audio, subtitle)
- Audio/subtitle state
- Video size state

**Session is NOT destroyed when:**
- Leaving the full player
- Opening the MiniPlayer
- Navigating between screens
- Entering/exiting in-app MiniPlayer mode
- Entering/exiting system PiP mode

**Session is ONLY released when:**
- The app is closed
- Playback is fully stopped by the user
- Errors require recreation

### 2. Two Presentation Layers (Contract Section 3.2)

**A) Full Player (SIP Player):**
- The existing SIP Player remains the full playback UI
- Consumes the shared PlaybackSession

**B) In-App MiniPlayer:**
- Floating overlay rendered inside the app UI
- NOT using system PiP (`enterPictureInPictureMode()`)
- Appears on top of all app screens
- Uses the same PlaybackSession
- Supports Play/Pause, Seek, Toggle, Resize/Move (Phase 8/9)
- Focusable via TV input
- Behaves like YouTube's in-app mini player

**C) System PiP (Phone/Tablet only):**
- Native Android PiP is used ONLY when the user leaves the app:
  - Home button
  - Recents
  - OS background transitions
- System PiP is NEVER triggered by the UI PIP button
- Fire TV: Never use system PiP from UI; OS may still auto-PiP

### 3. MiniPlayerState Requirements (Contract Section 4.1)

```
MiniPlayerState(
    visible: Boolean,
    mode: MiniPlayerMode = Normal | Resize,
    anchor: MiniPlayerAnchor = TopRight | TopLeft | BottomRight | BottomLeft,
    size: DpSize,
    position: Offset?,
    returnRoute: String?,
    returnListIndex: Int?,
    returnItemIndex: Int?,
)
```

### 4. Behavior Rules (Contract Section 4.2)

**Full Player → MiniPlayer (UI PIP Button):**
1. Must NOT call `enterPictureInPictureMode()`
2. Save `returnRoute`
3. Navigate back to underlying screen
4. Set `MiniPlayerState.visible = true`

**MiniPlayer → Full Player:**
1. Navigate back to SIP player route
2. Set `MiniPlayerState.visible = false`

**Inside MiniPlayer (Normal mode):**
- PLAY/PAUSE → toggle playback
- FF/RW → seek
- DPAD → behaves as background UI navigation unless MiniPlayer is focused
- Long-press PLAY → toggle Focus Zone (UI ↔ MiniPlayer)
- Row Fast-Scroll disabled when MiniPlayer visible

### 5. Platform Behavior (Contract Section 5)

**Phones/Tablets:**
- Home/Recents triggers system PiP if:
  - Playback active
  - MiniPlayer not visible
- UI PIP button = in-app MiniPlayer only

**Fire TV:**
- UI PIP button = in-app MiniPlayer only
- System PiP only if FireOS invokes it (never from app code)

### 6. TV Input Contract Extensions (Contract Section 6)

- Long-Press PLAY = `TvAction.TOGGLE_MINI_PLAYER_FOCUS`
- MiniPlayer visible → block:
  - `ROW_FAST_SCROLL_FORWARD`
  - `ROW_FAST_SCROLL_BACKWARD`
- Allowed when MiniPlayer visible:
  - PLAY/PAUSE, FF/RW, MENU (long) for resize
  - DPAD movement in resize mode
  - CENTER confirmation

**FocusZones:**
- `MINI_PLAYER`
- `PRIMARY_UI`

### 7. PlaybackSession Requirements (Contract Section 7)

**Functions:**
- `play()`, `pause()`, `togglePlayPause()`
- `seekTo()`, `seekBy()`
- `setSpeed()`, `enableTrickplay()`
- `stop()`, `release()`

**State (StateFlows):**
- `positionMs`, `durationMs`
- `isPlaying`, `buffering`
- `error`, `videoSize`
- `playbackState`

**Guarantees:**
- No re-init between MiniPlayer/full transitions
- Survives navigation
- Compatible with system PiP

### 8. Navigation Contract (Contract Section 8)

**Full → Mini:**
- From UI PIP button
- Save route
- Show MiniPlayer overlay

**Mini → Full:**
- Navigate to full player
- Hide MiniPlayer

**PiP → Full:**
- Restore PlaybackSession to full UI

### 9. Quality Requirements (Contract Section 9)

- Detekt (complexity < 10)
- Ktlint
- Android Lint (PiP warnings)
- Strict null-safety
- No direct ExoPlayer access outside PlaybackSession
- No blocking operations in MiniPlayer UI

### 10. Testing Requirements (Contract Section 10)

**Playback:**
- Full ↔ Mini transitions
- PiP entry/exit
- Seamless playback across navigation

**MiniPlayer:**
- Toggle focus
- Block fast scroll
- Resize mode (Phase 8)

**TV Input:**
- Correct actions when MiniPlayer visible
- Long-press PLAY toggle

**Navigation:**
- Resuming full player route
- returnRoute correctness

**Regression:**
- Phase 4 (subtitles)
- Phase 5 (surface)
- Phase 6 (input)
- Phase 3 (live)

---

## Task Group 1: PlaybackSession Core

**Goal:** Extend the existing `PlaybackSession` object into a fully featured unified session that owns ExoPlayer lifecycle and state.

**Status: ✅ DONE (Phase 7 Task 1)**

### Task 1.1: Define PlaybackSessionController Interface ✅
**Files Created:**
- `app/src/main/java/com/chris/m3usuite/playback/PlaybackSessionController.kt`

**Implementation:**
```kotlin
interface PlaybackSessionController {
    // State flows
    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val isPlaying: StateFlow<Boolean>
    val buffering: StateFlow<Boolean>
    val error: StateFlow<PlaybackException?>
    val videoSize: StateFlow<VideoSize?>
    val playbackState: StateFlow<Int>
    val isSessionActive: StateFlow<Boolean>
    
    // Commands
    fun play()
    fun pause()
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun seekBy(deltaMs: Long)
    fun setSpeed(speed: Float)
    fun enableTrickplay(speed: Float)
    fun stop()
    fun release()
}
```

**Contract Reference:** Section 7

**Tests:** `PlaybackSessionCoreTest.kt`

---

### Task 1.2: Extend PlaybackSession Singleton ✅
**Files Modified:**
- `app/src/main/java/com/chris/m3usuite/playback/PlaybackSession.kt`

**Implementation:**
- Added StateFlows for all session state (position, duration, isPlaying, etc.)
- Added command methods that forward to ExoPlayer
- Added Player.Listener to update StateFlows
- Ensured thread-safe state updates via MutableStateFlow
- Added `isSessionActive: StateFlow<Boolean>` property
- PlaybackSession now implements PlaybackSessionController interface

**Contract Reference:** Section 7

**Tests:** `PlaybackSessionCoreTest.kt`

---

### Task 1.3: Update InternalPlayerSession to Use PlaybackSession ✅
**Files Modified:**
- `app/src/main/java/com/chris/m3usuite/player/internal/session/InternalPlayerSession.kt`

**Implementation:**
- Replaced direct `ExoPlayer.Builder()` with `PlaybackSession.acquire(context, builder)`
- Updated DisposableEffect to NOT release player on dispose (shared ownership via PlaybackSession)
- Added `PlaybackSession.setSource(url)` call for MiniPlayer visibility checks
- Player survives MiniPlayer/full player transitions
- Cleanup only occurs when explicitly stopped (via PlaybackSession.release())

**Contract Reference:** Section 3.1

**Tests:** `InternalPlayerSessionPlaybackSessionTest.kt`

---

### Task 1.4: PlaybackSession State Flows Collection ✅
**Files Modified:**
- `app/src/main/java/com/chris/m3usuite/playback/PlaybackSession.kt`

**Implementation:**
- Added internal `MutableStateFlow` for each state property
- Updates flows in Player.Listener callbacks via `updateStateFromPlayer()`
- Exposed as read-only `StateFlow`
- Handles `C.TIME_UNSET` and null cases properly
- `resetStateFlows()` resets all state on release

**Tests:** `PlaybackSessionCoreTest.kt`

---

## Task Group 2: MiniPlayer Domain Model & Manager

**Goal:** Create a comprehensive MiniPlayer management layer with state persistence.

**Status: ✅ DONE (Phase 7 Task 1)**

### Task 2.1: Extend MiniPlayerState with Contract Fields ✅
**Files Created:**
- `app/src/main/java/com/chris/m3usuite/player/miniplayer/MiniPlayerState.kt`

**Implementation:**
```kotlin
data class MiniPlayerState(
    val visible: Boolean = false,
    val mode: MiniPlayerMode = MiniPlayerMode.NORMAL,
    val anchor: MiniPlayerAnchor = MiniPlayerAnchor.BOTTOM_RIGHT,
    val size: DpSize = DEFAULT_MINI_SIZE,
    val position: Offset? = null,
    val returnRoute: String? = null,
    val returnMediaId: Long? = null,
    val returnRowIndex: Int? = null,
    val returnItemIndex: Int? = null,
)

enum class MiniPlayerMode { NORMAL, RESIZE }
enum class MiniPlayerAnchor { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
```

**Contract Reference:** Section 4.1

**Tests:** `MiniPlayerStateTest.kt`

---

### Task 2.2: Create MiniPlayerManager ✅
**Files Created:**
- `app/src/main/java/com/chris/m3usuite/player/miniplayer/MiniPlayerManager.kt`

**Implementation:**
```kotlin
interface MiniPlayerManager {
    val state: StateFlow<MiniPlayerState>
    
    fun enterMiniPlayer(
        fromRoute: String,
        mediaId: Long? = null,
        rowIndex: Int? = null,
        itemIndex: Int? = null,
    )
    
    fun exitMiniPlayer(returnToFullPlayer: Boolean)
    fun updateMode(mode: MiniPlayerMode)
    fun updateAnchor(anchor: MiniPlayerAnchor)
    fun updateSize(size: DpSize)
    fun updatePosition(offset: Offset)
    fun reset()
}

object DefaultMiniPlayerManager : MiniPlayerManager { ... }
```

**Contract Reference:** Section 4.1, 4.2

**Tests:** `MiniPlayerManagerTest.kt`
```

**Contract Reference:** Section 4.1, 4.2

**Tests Required:**
- Enter/exit transitions
- State persistence across config changes
- Focus request handling

---

### Task 2.3: MiniPlayerManager Integration with PlaybackSession ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/miniplayer/MiniPlayerManager.kt`

**Note:** This task deferred to Task 2+ when UI wiring is implemented.

**Implementation:**
- `enterMiniPlayer` does NOT create a new player instance
- Uses `PlaybackSession.current()` to verify playback is active
- Updates `MiniPlayerDescriptor` with current media info
- `exitMiniPlayer` with `returnToFullPlayer=true` navigates back to player route

**Contract Reference:** Section 4.2

**Tests Required:**
- No second player instance created
- Seamless playback continuation
- State consistency

---

### Task 2.4: State Persistence Across Configuration Changes ⬜
**Note:** This task deferred to Task 2+ when UI wiring is implemented.

**Implementation:**
- MiniPlayerManager uses `MutableStateFlow` with initial state ✅
- SavedStateHandle integration for activity recreation (deferred)
- Persist `returnRoute` and indices ✅

**Tests Required:**
- State survives rotation
- State survives process death (if SavedStateHandle used)

---

## Task Group 2b: TV Input & FocusKit Primitives

**Goal:** Add missing TvAction and FocusZoneId entries for MiniPlayer support.

**Status: ✅ DONE (Phase 7 Task 1)**

### Task 2b.1: Add TOGGLE_MINI_PLAYER_FOCUS to TvAction ✅
**Files Modified:**
- `app/src/main/java/com/chris/m3usuite/tv/input/TvAction.kt`

**Implementation:**
- Added `TOGGLE_MINI_PLAYER_FOCUS` action
- Updated `isFocusAction()` helper to include new action
- Added KDoc documenting behavior (long-press PLAY trigger, focus toggle)

**Contract Reference:** Section 6

**Tests:** `TvActionEnumTest.kt` updated with Phase 7 tests

---

### Task 2b.2: Add FocusZoneId.MINI_PLAYER and PRIMARY_UI ✅
**Files Modified:**
- `app/src/main/java/com/chris/m3usuite/ui/focus/FocusKit.kt`

**Implementation:**
- Added `MINI_PLAYER` to `FocusZoneId` enum
- Added `PRIMARY_UI` to `FocusZoneId` enum
- Documented both as used for `TOGGLE_MINI_PLAYER_FOCUS` action

**Contract Reference:** Section 6

---

## Task Group 3: In-App MiniPlayer UI Skeleton

**Goal:** Create a basic MiniPlayer overlay composable for Phase 7.

**Status: ⬜ Deferred to Task 2+**

### Task 3.1: Extend MiniPlayerHost for Phase 7 ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/ui/home/MiniPlayerHost.kt`

**Implementation:**
- Add video rendering via `PlaybackSession.current()`
- Add Play/Pause toggle button
- Add seek buttons (±10s)
- Add "Expand to Full Player" button
- Add "Close" button
- Integrate with FocusKit via `FocusZoneId.MINI_PLAYER`

**Contract Reference:** Section 3.2, 4.2

**Tests Required:**
- Video renders correctly
- Controls respond to user input
- Focus management works

---

### Task 3.2: Add FocusZoneId.MINI_PLAYER ✅
**Completed in Task 2b.2**

---

### Task 3.3: Root Scaffold Overlay Integration ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/ui/home/HomeChromeScaffold.kt` (or equivalent)

**Implementation:**
- Ensure `MiniPlayerHost` is rendered as an overlay above all screens
- Position based on `MiniPlayerStateData.anchor`
- Apply size from `MiniPlayerStateData.size`

**Contract Reference:** Section 3.2

**Tests Required:**
- MiniPlayer appears above navigation content
- Positioning follows anchor setting

---

### Task 3.4: Prevent Flicker on Route Changes ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/ui/home/MiniPlayerHost.kt`

**Implementation:**
- Use `AnimatedVisibility` for smooth show/hide
- Maintain PlayerView attachment across route changes
- Use `key()` to preserve composable identity

**Tests Required:**
- No flicker during navigation
- Video continues seamlessly

---

## Task Group 4: PIP Button Refactor (UI → In-App MiniPlayer)

**Goal:** Wire the existing PIP button to use MiniPlayerManager instead of native PiP.

### Task 4.1: Locate and Modify SIP PIP Button ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/InternalPlayerControls.kt`

**Implementation:**
- Find `onPipClick` callback usage (line 386, 644)
- Replace `requestPictureInPicture(activity)` with `MiniPlayerManager.enterMiniPlayer(...)`

**Contract Reference:** Section 4.2

**Tests Required:**
- Button click triggers MiniPlayer, not native PiP
- Works on TV and phone

---

### Task 4.2: Remove enterPictureInPictureMode from SIP PIP Button ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/InternalPlayerControls.kt`
- `app/src/main/java/com/chris/m3usuite/player/internal/system/InternalPlayerSystemUi.kt`

**Implementation:**
- The SIP PIP button must NEVER call `activity.enterPictureInPictureMode()`
- Only `MiniPlayerManager.enterMiniPlayer()` is allowed from UI PIP button
- `InternalPlayerSystemUi.requestPictureInPicture()` should be renamed or deprecated

**Contract Reference:** Section 4.2, 5.2

**Tests Required:**
- No native PiP on TV from UI button
- No native PiP on phone from UI button

---

### Task 4.3: Build Return Route and Navigate Back ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/InternalPlayerControls.kt`

**Implementation:**
- On PIP button click:
  1. Capture current `PlaybackContext` and position
  2. Determine `returnRoute` from NavController current route
  3. Call `MiniPlayerManager.enterMiniPlayer(context, returnRoute, listIndex, itemIndex)`
  4. Navigate back (pop player from nav stack)

**Contract Reference:** Section 4.2

**Tests Required:**
- Return route is correctly captured
- Navigation correctly returns to originating screen

---

### Task 4.4: Ensure PlaybackSession Continues Without Re-init ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/session/InternalPlayerSession.kt`

**Implementation:**
- On dispose: Do NOT call `PlaybackSession.set(null)` or `player.release()`
- Only release when `MiniPlayerManager` signals full stop
- Add flag to track "keepAlive for MiniPlayer" state

**Contract Reference:** Section 3.1

**Tests Required:**
- Playback continues after closing full player
- No rebuffering when entering MiniPlayer

---

## Task Group 5: System PiP (Phones/Tablets Only)

**Goal:** Implement system PiP only when the Activity is backgrounded on phones/tablets.

### Task 5.1: Implement Activity Lifecycle PiP Entry ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/MainActivity.kt`

**Implementation:**
```kotlin
override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    if (!FocusKit.isTvDevice(this) &&
        PlaybackSession.current()?.isPlaying == true &&
        !MiniPlayerManager.state.value.visible
    ) {
        enterPictureInPictureIfSupported()
    }
}
```

**Alternative:** Use `setAutoEnterEnabled(true)` on API 31+ for automatic PiP entry.

**Contract Reference:** Section 5.1

**Tests Required:**
- PiP enters on Home button (phone)
- PiP enters on Recents (phone)
- PiP does NOT enter when MiniPlayer is visible

---

### Task 5.2: Block System PiP from UI Button ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/system/InternalPlayerSystemUi.kt`

**Implementation:**
- Remove or guard `requestPictureInPicture()` to never be called from UI buttons
- UI PIP button always goes to in-app MiniPlayer

**Contract Reference:** Section 5.1

**Tests Required:**
- UI button never triggers native PiP

---

### Task 5.3: Fire TV PiP Behavior ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/MainActivity.kt`

**Implementation:**
- On TV devices (`FocusKit.isTvDevice() == true`):
  - Do NOT call `enterPictureInPictureMode()` from app code
  - Allow OS-driven PiP behavior if FireOS chooses to do so

**Contract Reference:** Section 5.2

**Tests Required:**
- No native PiP call on Fire TV from app code
- App doesn't crash if FireOS triggers PiP

---

## Task Group 6: TV Input & MiniPlayer Behavior

**Goal:** Extend TV input handling for MiniPlayer-specific actions and blocking rules.

### Task 6.1: Add TvAction.TOGGLE_MINI_PLAYER_FOCUS ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/tv/input/TvAction.kt`

**Implementation:**
```kotlin
/**
 * Toggle focus between MiniPlayer and primary UI.
 * Triggered by long-press PLAY.
 * Contract: Section 6
 */
TOGGLE_MINI_PLAYER_FOCUS,
```

**Contract Reference:** Section 6

**Tests Required:**
- Enum value exists
- Category helper methods updated

---

### Task 6.2: Map Long-Press PLAY to TOGGLE_MINI_PLAYER_FOCUS ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/tv/input/GlobalTvInputHost.kt`
- `app/src/main/java/com/chris/m3usuite/tv/input/DefaultTvScreenConfigs.kt`

**Implementation:**
- Detect long-press PLAY (hold > 500ms)
- Dispatch `TvAction.TOGGLE_MINI_PLAYER_FOCUS` instead of `PLAY_PAUSE`
- In handler: Toggle focus between `MINI_PLAYER` and `PRIMARY_UI` FocusZones

**Contract Reference:** Section 6

**Tests Required:**
- Long-press detected correctly
- Focus toggles between zones

---

### Task 6.3: Block ROW_FAST_SCROLL When MiniPlayer Visible ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/tv/input/TvScreenInputConfig.kt`

**Implementation:**
- In `filterForMiniPlayerVisible()`:
  - Block `ROW_FAST_SCROLL_FORWARD`
  - Block `ROW_FAST_SCROLL_BACKWARD`
  - Allow `PLAY_PAUSE`, `PIP_SEEK_*`, navigation

**Contract Reference:** Section 6

**Tests Required:**
- Fast scroll blocked when MiniPlayer visible
- Other actions allowed

---

### Task 6.4: Route PIP_* Actions to MiniPlayerManager ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/tv/input/DefaultTvInputController.kt`

**Implementation:**
- When `TvScreenContext.screenId == MINI_PLAYER`:
  - `PIP_SEEK_FORWARD` → `PlaybackSession.seekBy(10_000)`
  - `PIP_SEEK_BACKWARD` → `PlaybackSession.seekBy(-10_000)`
  - `PIP_TOGGLE_PLAY_PAUSE` → `PlaybackSession.togglePlayPause()`

**Contract Reference:** Section 6

**Tests Required:**
- PIP actions correctly control playback
- Works when MiniPlayer is focused

---

### Task 6.5: Ensure DOUBLE_BACK Behavior with MiniPlayer ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/tv/input/GlobalTvInputHost.kt`

**Implementation:**
- Double BACK → `EXIT_TO_HOME` still works with MiniPlayer visible
- MiniPlayer remains visible on home/start route if playback continues

**Contract Reference:** Section 6

**Tests Required:**
- EXIT_TO_HOME navigates to home
- MiniPlayer stays visible

---

## Task Group 7: FocusZones & Focus Integration

**Goal:** Integrate MiniPlayer with FocusKit's zone system.

### Task 7.1: Add FocusZoneId.PRIMARY_UI ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/ui/focus/FocusKit.kt`

**Implementation:**
- Add `PRIMARY_UI` to `FocusZoneId` enum
- Document as "Main app UI area (non-MiniPlayer)"

**Contract Reference:** Section 6

**Tests Required:**
- Enum value exists

---

### Task 7.2: Implement Zone-Based Focus Toggle ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/ui/focus/FocusKit.kt`

**Implementation:**
- Add `toggleMiniPlayerFocus()` function:
  - If current zone is `MINI_PLAYER` → request focus on `PRIMARY_UI`
  - If current zone is NOT `MINI_PLAYER` → request focus on `MINI_PLAYER`

**Contract Reference:** Section 6

**Tests Required:**
- Focus toggles correctly
- Works with long-press PLAY

---

### Task 7.3: Prevent Implicit Focus Stealing ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/ui/home/MiniPlayerHost.kt`

**Implementation:**
- MiniPlayer receives focus ONLY via explicit `TvAction.TOGGLE_MINI_PLAYER_FOCUS`
- Normal DPAD navigation remains bound to primary UI
- Use `focusProperties { canFocus = focusEnabled }` pattern

**Contract Reference:** Section 6

**Tests Required:**
- DPAD navigation doesn't accidentally focus MiniPlayer
- Explicit action required

---

## Task Group 8: Navigation & Return Behavior

**Goal:** Implement correct navigation flow between Full Player and MiniPlayer.

### Task 8.1: Define ReturnRoute Storage ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/ui/home/MiniPlayerManager.kt`

**Implementation:**
- Store `returnRoute` when entering MiniPlayer
- Include `returnListIndex` and `returnItemIndex` for scroll position
- Clear on explicit MiniPlayer close

**Contract Reference:** Section 8

**Tests Required:**
- Route stored correctly from Library
- Route stored correctly from Detail
- Route stored correctly from other screens

---

### Task 8.2: Implement Full Player → MiniPlayer Flow ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/InternalPlayerControls.kt`

**Implementation:**
1. PIP button click
2. Save route: `navController.currentDestination?.route`
3. Close player: `navController.popBackStack()`
4. Show mini: `MiniPlayerManager.enterMiniPlayer(...)`

**Contract Reference:** Section 8

**Tests Required:**
- Player closes
- MiniPlayer shows
- Playback continues

---

### Task 8.3: Implement MiniPlayer → Full Player Flow ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/ui/home/MiniPlayerHost.kt`

**Implementation:**
1. "Expand" button click
2. Navigate to full player route: `navController.navigate(playerRoute)`
3. Hide mini: `MiniPlayerManager.exitMiniPlayer(returnToFullPlayer = true)`
4. Full player attaches to existing `PlaybackSession`

**Contract Reference:** Section 8

**Tests Required:**
- MiniPlayer hides
- Full player opens
- No rebuffering

---

### Task 8.4: Preserve Playback Position ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/session/InternalPlayerSession.kt`

**Implementation:**
- Full → Mini → Full must NOT reset playback position
- Use `PlaybackSession.current().currentPosition` for continuity
- Do not re-seek on full player reattach

**Contract Reference:** Section 8

**Tests Required:**
- Position preserved across transitions
- No seek jump

---

### Task 8.5: Handle EXIT_TO_HOME with MiniPlayer ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/tv/input/DefaultTvInputController.kt`

**Implementation:**
- EXIT_TO_HOME (double BACK) navigates to home/start route
- MiniPlayer remains visible if playback continues
- Do not auto-close MiniPlayer on EXIT_TO_HOME

**Contract Reference:** Section 8

**Tests Required:**
- Navigation goes to home
- MiniPlayer stays visible
- Playback continues

---

## Task Group 9: Testing & Quality

**Goal:** Comprehensive test coverage for all Phase 7 modules.

### Task 9.1: PlaybackSession Core Unit Tests ⬜
**Files to Create:**
- `app/src/test/java/com/chris/m3usuite/playback/PlaybackSessionTest.kt`

**Coverage:**
- State flow updates on player events
- play/pause/seek/position/cleanup
- Thread safety
- Null player handling

---

### Task 9.2: MiniPlayerManager State Transitions Tests ⬜
**Files to Create:**
- `app/src/test/java/com/chris/m3usuite/ui/home/MiniPlayerManagerTest.kt`

**Coverage:**
- Enter/exit transitions
- Mode changes
- Anchor/size/position updates
- ReturnRoute persistence

---

### Task 9.3: TvInputController MiniPlayer Actions Tests ⬜
**Files to Create:**
- `app/src/test/java/com/chris/m3usuite/tv/input/MiniPlayerTvInputTest.kt`

**Coverage:**
- PIP_* actions dispatch correctly
- TOGGLE_MINI_PLAYER_FOCUS behavior
- ROW_FAST_SCROLL blocking
- Long-press PLAY detection

---

### Task 9.4: Full Player → MiniPlayer → Full Player Integration Tests ⬜
**Files to Create:**
- `app/src/test/java/com/chris/m3usuite/integration/MiniPlayerIntegrationTest.kt`

**Coverage:**
- Complete transition flow
- Position preservation
- No rebuffering
- Focus management

---

### Task 9.5: Phone/Tablet System PiP Tests ⬜
**Files to Create:**
- `app/src/test/java/com/chris/m3usuite/integration/SystemPipTest.kt`

**Coverage:**
- Home button triggers PiP (phone)
- UI button does NOT trigger native PiP
- MiniPlayer visible blocks system PiP

---

### Task 9.6: Fire TV PIP Button Tests ⬜
**Files to Create:**
- `app/src/test/java/com/chris/m3usuite/integration/FireTvMiniPlayerTest.kt`

**Coverage:**
- UI PIP button triggers in-app MiniPlayer
- No native PiP call
- Focus toggle works

---

### Task 9.7: Phase 4-6 Regression Tests ⬜
**Files to Modify:**
- Existing Phase 4, 5, 6 test files

**Coverage:**
- Phase 4: Subtitles/CC still work in full player
- Phase 5: PlayerSurface, aspect ratio, trickplay, auto-hide unchanged
- Phase 6: TV input, Exit-to-Home, FocusZones unaffected
- Phase 3: Live/EPG overlays work with MiniPlayer visible

---

## Summary: Files Overview

### New Files to Create (SIP Only)
| File Path | Purpose |
|-----------|---------|
| `playback/PlaybackSessionController.kt` | Interface for PlaybackSession commands/state |
| `ui/home/MiniPlayerManager.kt` | Global MiniPlayer state manager |
| `test/.../playback/PlaybackSessionTest.kt` | PlaybackSession unit tests |
| `test/.../ui/home/MiniPlayerManagerTest.kt` | MiniPlayerManager unit tests |
| `test/.../tv/input/MiniPlayerTvInputTest.kt` | TV input tests for MiniPlayer |
| `test/.../integration/MiniPlayerIntegrationTest.kt` | Integration tests |
| `test/.../integration/SystemPipTest.kt` | System PiP tests |
| `test/.../integration/FireTvMiniPlayerTest.kt` | Fire TV tests |

### Files to Modify (SIP Only)
| File Path | Changes |
|-----------|---------|
| `playback/PlaybackSession.kt` | Add StateFlows, command methods, Player.Listener |
| `player/internal/session/InternalPlayerSession.kt` | Use PlaybackSession.acquire(), no local release |
| `player/internal/ui/InternalPlayerControls.kt` | Wire PIP button to MiniPlayerManager |
| `player/internal/system/InternalPlayerSystemUi.kt` | Remove/guard requestPictureInPicture() |
| `ui/home/MiniPlayerState.kt` | Add mode, anchor, returnRoute fields |
| `ui/home/MiniPlayerHost.kt` | Add controls, FocusZone integration |
| `ui/focus/FocusKit.kt` | Add MINI_PLAYER, PRIMARY_UI zones |
| `tv/input/TvAction.kt` | Add TOGGLE_MINI_PLAYER_FOCUS |
| `tv/input/DefaultTvScreenConfigs.kt` | Add MINI_PLAYER config |
| `tv/input/TvScreenInputConfig.kt` | Add MiniPlayer filter |
| `tv/input/GlobalTvInputHost.kt` | Add long-press PLAY detection |
| `tv/input/DefaultTvInputController.kt` | Route PIP_* to MiniPlayerManager |
| `MainActivity.kt` | Add onUserLeaveHint() for system PiP |

### Files NOT Modified (Legacy)
- ❌ `player/InternalPlayerScreen.kt` – **UNTOUCHED** (legacy remains active)

---

## Phase 7 Completion Criteria

- [ ] All Task Groups 1-9 complete
- [ ] All tests passing (unit + integration)
- [ ] PlaybackSession is truly global (single ExoPlayer instance)
- [ ] MiniPlayer UI functional with basic controls
- [ ] PIP button triggers in-app MiniPlayer (not native PiP)
- [ ] System PiP only on phone/tablet Activity background
- [ ] Fire TV never calls native PiP from app code
- [ ] TV input handles MiniPlayer actions correctly
- [ ] Focus toggle via long-press PLAY works
- [ ] ROW_FAST_SCROLL blocked when MiniPlayer visible
- [ ] Full ↔ Mini transitions preserve playback position
- [ ] No changes to legacy `InternalPlayerScreen.kt`
- [ ] Documentation updated (Roadmap, Status)
- [ ] Phase 4, 5, 6 regression tests pass

---

**Last Updated:** 2025-11-28
