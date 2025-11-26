# Internal Player Phase 5 Checklist – PlayerSurface, Aspect Ratio, Trickplay & Auto-Hide

**Version:** 1.0  
**Scope:** SIP-only PlayerSurface composable, aspect ratio modes, black-bar handling, trickplay behavior, and controls auto-hide  
**Contract Reference:** [INTERNAL_PLAYER_PLAYER_SURFACE_CONTRACT_PHASE5.md](INTERNAL_PLAYER_PLAYER_SURFACE_CONTRACT_PHASE5.md)

---

## Overview

Phase 5 implements the PlayerSurface composable as the visual rendering foundation for the modular SIP Internal Player. This includes aspect ratio modes, black-bar/background handling, trickplay (fast-forward/rewind with preview), and controls auto-hide behavior. All work is SIP-only and does not modify the legacy `InternalPlayerScreen.kt`.

**Key Principles:**
1. **SIP-Only**: No changes to legacy `InternalPlayerScreen.kt`
2. **Contract-Driven**: Behavior defined by `INTERNAL_PLAYER_PLAYER_SURFACE_CONTRACT_PHASE5.md`
3. **Black Bars Must Be Black**: All non-video areas must be pure black, never white or gray
4. **Correct Aspect Ratio**: FIT, FILL, ZOOM modes with predictable semantics
5. **Modern Trickplay**: Responsive fast-forward/rewind with visual feedback
6. **Non-Annoying Auto-Hide**: Controls hide after appropriate timeout without surprising the user

---

## Component Overview (Repository Scan Results)

### Current SIP PlayerSurface (`internal/ui/PlayerSurface.kt`) – **REUSE & EXTEND**
- **Current:** Hosts AndroidView(PlayerView), applies AspectRatioMode and subtitle style
- **Supports:** Tap gesture (no-op currently), horizontal swipe for LIVE channel zapping
- **Missing:** Black bar enforcement, trickplay gesture handling, vertical swipe, auto-hide coordination
- **Phase 5 Action:** Extend with black-bar handling, trickplay gestures, and auto-hide hooks

### Current AspectRatioMode (`internal/state/InternalPlayerState.kt`) – **REUSE**
- **Current:** FIT, FILL, ZOOM, STRETCH enum
- **Maps to:** `AspectRatioFrameLayout.RESIZE_MODE_*`
- **Phase 5 Action:** Ensure contract compliance (STRETCH may be removed or renamed to ZOOM variant)

### Current InternalPlayerControls (`internal/ui/InternalPlayerControls.kt`) – **EXTEND**
- **Current:** Main controls row, progress row, EPG overlay, CC menu
- **Missing:** Trickplay UI (speed indicator, seek preview), auto-hide timer coordination
- **Phase 5 Action:** Add trickplay state display, auto-hide timer management

### Legacy InternalPlayerScreen.kt – **REFERENCE ONLY**
- **Lines 1467-1487:** Trickplay speeds (2x, 3x, 5x), ffStage/rwStage state, stopTrickplay()
- **Lines 1489-1507:** showSeekPreview() with auto-hide after 900ms
- **Lines 1438-1451:** Auto-hide logic: TV 10s, phone/tablet 5s, blocks on popups
- **Lines 1365-1379:** resizeMode cycling (FIT → ZOOM → FILL)
- **Phase 5 Action:** Migrate behavior to SIP modules, do NOT copy code directly

### Layout compose_player_view.xml – **NEEDS BLACK BACKGROUND**
- **Current:** No background color specified
- **Issue:** May show white during loading (contract violation)
- **Phase 5 Action:** Set `android:background="@android:color/black"` or apply via code

---

## Task Group 1: PlayerSurface Foundation & Black Bars

**Goal:** Ensure PlayerSurface always renders non-video areas as black

### Task 1.1: PlayerView Background Configuration ✅ **DONE**
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/PlayerSurface.kt`
- `app/src/main/res/layout/compose_player_view.xml` (optional)

**Implementation:**
```kotlin
// In PlayerView factory block:
setShutterBackgroundColor(android.graphics.Color.BLACK)
setBackgroundColor(android.graphics.Color.BLACK)
```

**Contract Reference:** Section 3.1, Section 4.2

**Legacy Reference:**
- No explicit black background set in legacy (potential bug to fix)

**Tests Required:**
- PlayerView has black background on creation
- Shutter color is black
- No white/gray visible during initial buffering

**Completed:** 2025-11-26 - Added `setBackgroundColor(AndroidColor.BLACK)` and `setShutterBackgroundColor(AndroidColor.BLACK)` to PlayerView factory block in PlayerSurface.kt.

---

### Task 1.2: Compose Container Background ✅ **DONE**
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/PlayerSurface.kt`

**Implementation:**
```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)  // Add this
        // ... gesture modifiers
)
```

**Contract Reference:** Section 4.2 Rule 3

**Tests Required:**
- Box container has black background
- Background persists during aspect ratio changes
- Background visible during video loading

**Completed:** 2025-11-26 - Added `.background(Color.Black)` to PlayerSurface Box modifier.

---

### Task 1.3: XML Layout Black Background ✅ **DONE**
**Files to Modify:**
- `app/src/main/res/layout/compose_player_view.xml`

**Implementation:**
```xml
<androidx.media3.ui.PlayerView
    ...
    android:background="@android:color/black" />
```

**Contract Reference:** Section 4.2

**Tests Required:**
- XML-inflated PlayerView has black background
- Background survives orientation changes

**Completed:** 2025-11-26 - Added `android:background="@android:color/black"` to compose_player_view.xml.

---

## Task Group 2: Aspect Ratio Modes & Switching

**Goal:** Implement contract-compliant FIT/FILL/ZOOM modes with clean switching

### Task 2.1: AspectRatioMode Enum Cleanup ✅ **DONE**
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/state/InternalPlayerState.kt`

**Implementation:**
- Ensure `AspectRatioMode` aligns with contract:
  - `FIT` – entire video fits, black bars if needed
  - `FILL` – fills viewport, crops edges
  - `ZOOM` – aggressive crop (may merge with FILL or differentiate)
- STRETCH may be deprecated or kept for custom scaling

**Contract Reference:** Section 4.1

**Legacy Reference:**
- Lines 1374-1379: FIT → ZOOM → FILL cycle

**Tests Required:**
- Enum values match contract definitions
- toResizeMode() mapping is correct

---

### Task 2.2: Aspect Ratio Cycling Logic ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/state/InternalPlayerState.kt`

**Implementation:**
```kotlin
// Add helper function for cycling
fun AspectRatioMode.next(): AspectRatioMode = when (this) {
    FIT -> FILL
    FILL -> ZOOM
    ZOOM -> FIT
    STRETCH -> FIT  // fallback
}
```

**Contract Reference:** Section 4.1

**Tests Required:**
- Cycling behavior is deterministic
- All modes cycle correctly

---

### Task 2.3: Aspect Ratio Controller Integration ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/state/InternalPlayerState.kt`
- `app/src/main/java/com/chris/m3usuite/player/internal/session/InternalPlayerSession.kt`

**Implementation:**
- `InternalPlayerController.onCycleAspectRatio` updates state
- State changes propagate to PlayerSurface
- Aspect ratio persists in session (optional: persist to DataStore)

**Contract Reference:** Section 4.2 Rule 5

**Tests Required:**
- Controller callback updates state
- PlayerSurface receives new mode
- Black background maintained during switch

---

## Task Group 3: Trickplay Behavior & UI Hooks

**Goal:** Implement modern trickplay (FF/RW) with visual feedback

### Task 3.1: Trickplay State Model ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/state/InternalPlayerState.kt`

**Implementation:**
```kotlin
data class InternalPlayerUiState(
    // ... existing fields ...
    
    // Trickplay state (Phase 5)
    val trickplayActive: Boolean = false,
    val trickplaySpeed: Float = 1f,  // 1f = normal, 2f/3f/5f = FF, -2f/-3f/-5f = RW
    
    // Seek preview state (Phase 5)
    val seekPreviewVisible: Boolean = false,
    val seekPreviewTargetMs: Long? = null,
)
```

**Contract Reference:** Section 6.1, 6.2

**Legacy Reference:**
- Lines 1467-1470: trickplaySpeeds, ffStage, rwStage
- Lines 1456-1459: seekPreviewVisible, seekPreviewTargetMs

**Tests Required:**
- Default state is non-trickplay
- Speed values are valid
- Preview visibility is independent of trickplay

---

### Task 3.2: Trickplay Controller Methods ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/state/InternalPlayerState.kt`

**Implementation:**
```kotlin
data class InternalPlayerController(
    // ... existing callbacks ...
    
    // Trickplay callbacks (Phase 5)
    val onStartTrickplay: (direction: Int) -> Unit = {},  // +1 = FF, -1 = RW
    val onStopTrickplay: (resume: Boolean) -> Unit = {},
    val onCycleTrickplaySpeed: () -> Unit = {},
)
```

**Contract Reference:** Section 6.2

**Tests Required:**
- Start/stop callbacks work correctly
- Direction values are validated
- Resume parameter is respected

---

### Task 3.3: Trickplay Session Logic ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/session/InternalPlayerSession.kt`

**Implementation:**
- Implement trickplay speed cycling: 2x → 3x → 5x → normal
- Apply speed via `exoPlayer.setPlaybackParameters(PlaybackParameters(speed))`
- For rewind: Use seek-based approach (jump by -10s per tick) or negative speed if supported
- Update `InternalPlayerUiState.trickplayActive` and `trickplaySpeed`
- Stop trickplay on explicit action or playback end

**Contract Reference:** Section 6.2 Rules 1-4

**Legacy Reference:**
- Lines 1473-1487: stopTrickplay() implementation
- Lines 1599-1652: FF/RW key handling

**Tests Required:**
- Speed cycling works correctly
- Trickplay stops on user action
- Position is correct after trickplay exit
- Background remains black during trickplay

---

### Task 3.4: Seek Preview Logic ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/session/InternalPlayerSession.kt`

**Implementation:**
```kotlin
fun showSeekPreview(base: Long, target: Long, autoHide: Boolean = true) {
    _uiState.update { it.copy(
        seekPreviewVisible = true,
        seekPreviewTargetMs = target
    ) }
    
    if (autoHide) {
        scope.launch {
            delay(900)  // Match legacy 900ms timeout
            _uiState.update { it.copy(seekPreviewVisible = false, seekPreviewTargetMs = null) }
        }
    }
}
```

**Contract Reference:** Section 6.2 Rule 2

**Legacy Reference:**
- Lines 1489-1507: showSeekPreview() with 900ms auto-hide

**Tests Required:**
- Preview shows with correct target
- Auto-hide works after timeout
- Manual hide works

---

### Task 3.5: Trickplay UI in InternalPlayerControls ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/InternalPlayerControls.kt`

**Implementation:**
- Add trickplay speed indicator overlay (e.g., "2x ►►" or "◀◀ 3x")
- Add seek preview overlay showing target position
- Position overlays appropriately (center or top of screen)
- Use AnimatedVisibility for smooth transitions

**Contract Reference:** Section 6.2 Rule 2

**Tests Required:**
- Speed indicator shows during trickplay
- Preview overlay shows during seek
- Overlays don't block other controls

---

### Task 3.6: Trickplay Gesture Handling ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/PlayerSurface.kt`

**Implementation:**
- For VOD/SERIES: Horizontal swipe triggers trickplay or seek
- Options:
  - Quick swipe → seek ±10s
  - Long swipe / repeated → enter trickplay mode
  - Double-tap sides → seek ±10s (optional, contract Section 8.2)
- Ensure gestures don't conflict with LIVE channel zapping

**Contract Reference:** Section 8.2, 8.3

**Tests Required:**
- Horizontal swipe triggers trickplay for VOD/SERIES
- LIVE playback uses channel zapping, not trickplay
- Gesture threshold is appropriate

---

## Task Group 4: Controls Auto-Hide (TV vs Touch)

**Goal:** Implement non-annoying auto-hide with appropriate timeouts

### Task 4.1: Auto-Hide State Model ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/state/InternalPlayerState.kt`

**Implementation:**
```kotlin
data class InternalPlayerUiState(
    // ... existing fields ...
    
    // Controls visibility (Phase 5)
    val controlsVisible: Boolean = true,
    val controlsTick: Int = 0,  // Incremented on user activity to reset timer
)
```

**Contract Reference:** Section 7.1, 7.2

**Legacy Reference:**
- Lines 1347-1348: controlsVisible, controlsTick

**Tests Required:**
- Default state is controls visible
- Tick counter increments correctly

---

### Task 4.2: Auto-Hide Timer Logic ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/InternalPlayerControls.kt`
- (Optional) Create `internal/ui/AutoHideController.kt`

**Implementation:**
```kotlin
// In InternalPlayerContent or separate composable:
LaunchedEffect(controlsVisible, controlsTick, isTv) {
    if (!controlsVisible) return@LaunchedEffect
    
    // Don't auto-hide while modal overlays are open
    val blockingPopupOpen = state.showCcMenuDialog || state.showSettingsDialog || ...
    if (blockingPopupOpen) return@LaunchedEffect
    
    val timeoutMs = if (isTv) 7_000L else 4_000L  // Contract: TV 5-7s, phone 3-5s
    val startTick = controlsTick
    delay(timeoutMs)
    
    if (controlsVisible && controlsTick == startTick) {
        onHideControls()
    }
}
```

**Contract Reference:** Section 7.2

**Legacy Reference:**
- Lines 1438-1451: TV 10s, phone 5s, blocks on popups

**Tests Required:**
- TV timeout is longer than phone timeout
- Timer resets on user activity
- Auto-hide blocked when popups open

---

### Task 4.3: Activity Detection ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/PlayerSurface.kt`
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/InternalPlayerControls.kt`

**Implementation:**
- Define "activity" as (Contract Section 7.2):
  - DPAD navigation
  - Touch tap/swipe
  - Trickplay adjustments
  - CC menu open/close
  - Live EPG or menus interaction
- Any activity should:
  - Reset auto-hide timer (increment controlsTick)
  - Show controls if hidden

**Contract Reference:** Section 7.2

**Tests Required:**
- Each activity type resets timer
- Controls show on activity when hidden

---

### Task 4.4: Never-Hide Conditions ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/InternalPlayerControls.kt`

**Implementation:**
- Never hide controls when (Contract Section 7.3):
  - CC menu is open
  - Settings bottom sheet is open
  - Confirmation dialogs are open
  - Kid Mode is blocking playback with an overlay
  - Trickplay is actively being adjusted (Phase 5 addition)

**Contract Reference:** Section 7.3

**Legacy Reference:**
- Line 1442: `val blockingPopupOpen = showCcMenu || showAspectMenu || (type == "live" && quickActionsVisible)`

**Tests Required:**
- Controls never hide with CC menu open
- Controls never hide with settings open
- Controls never hide with kid block overlay

---

### Task 4.5: Tap-to-Toggle Controls ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/PlayerSurface.kt`
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/InternalPlayerControls.kt`

**Implementation:**
- Wire `onTap` callback in PlayerSurface to toggle controls visibility
- Update `InternalPlayerController` with `onToggleControlsVisibility` callback
- Increment `controlsTick` when controls are shown

**Contract Reference:** Section 8.1

**Legacy Reference:**
- Lines 1836-1837: Tap toggles controlsVisible

**Tests Required:**
- Tap when visible → hide
- Tap when hidden → show and reset timer
- Double-tap behavior (if implemented)

---

## Task Group 5: Tests & Validation

**Goal:** Comprehensive test coverage for all Phase 5 modules

### Task 5.1: PlayerSurface Black-Bar Tests ⬜
**Files to Create:**
- `app/src/test/java/com/chris/m3usuite/player/internal/ui/PlayerSurfacePhase5BlackBarTest.kt`

**Coverage:**
- PlayerView background is black on creation
- Compose container background is black
- Black bars visible for narrow video (pillarboxing)
- Black bars visible for wide video (letterboxing)
- Background black during pause at any position
- Background black during initial buffering

**Contract Reference:** Section 10.1

---

### Task 5.2: Aspect Ratio Tests ⬜
**Files to Create:**
- `app/src/test/java/com/chris/m3usuite/player/internal/ui/PlayerSurfacePhase5AspectRatioTest.kt`

**Coverage:**
- FIT mode shows entire video with bars
- FILL mode fills viewport, crops edges
- ZOOM mode applies aggressive crop
- Switching modes preserves black background
- No white/gray visible during mode switch

**Contract Reference:** Section 10.1

---

### Task 5.3: Trickplay Tests ⬜
**Files to Create:**
- `app/src/test/java/com/chris/m3usuite/player/internal/session/InternalPlayerSessionPhase5TrickplayTest.kt`

**Coverage:**
- Enter trickplay from multiple input patterns
- Speed cycling works correctly (2x → 3x → 5x → normal)
- Exit trickplay on explicit action
- Position correct after trickplay exit
- Aspect ratio unchanged during trickplay
- Background black during fast seeking

**Contract Reference:** Section 10.2

---

### Task 5.4: Auto-Hide Tests ⬜
**Files to Create:**
- `app/src/test/java/com/chris/m3usuite/player/internal/ui/InternalPlayerControlsPhase5AutoHideTest.kt`

**Coverage:**
- Controls hide after configured timeout (TV vs phone)
- Activity resets hide timer
- Controls stay visible when CC menu open
- Controls stay visible when settings open
- Controls stay visible when kid block overlay active
- Tap toggles visibility correctly

**Contract Reference:** Section 10.3

---

### Task 5.5: Kid Mode Interaction Tests ⬜
**Files to Create:**
- `app/src/test/java/com/chris/m3usuite/player/internal/ui/PlayerSurfacePhase5KidModeTest.kt`

**Coverage:**
- Kid Mode does not break PlayerSurface
- Background stays black in Kid Mode
- No aspect ratio changes in Kid Mode
- Kid blocking overlay doesn't affect video rendering

**Contract Reference:** Section 10.4

---

## Summary: Files Overview

### Files to Modify (SIP Only)
| File Path | Changes |
|-----------|---------|
| `internal/ui/PlayerSurface.kt` | Black bar enforcement, trickplay gestures, tap-to-toggle |
| `internal/state/InternalPlayerState.kt` | Add trickplay fields, controlsVisible, controlsTick |
| `internal/session/InternalPlayerSession.kt` | Trickplay logic, seek preview, auto-hide coordination |
| `internal/ui/InternalPlayerControls.kt` | Auto-hide timer, trickplay UI, activity detection |
| `res/layout/compose_player_view.xml` | Add black background attribute |

### Files to Create (SIP Only)
| File Path | Purpose |
|-----------|---------|
| `test/.../ui/PlayerSurfacePhase5BlackBarTest.kt` | Black bar visual tests |
| `test/.../ui/PlayerSurfacePhase5AspectRatioTest.kt` | Aspect ratio tests |
| `test/.../session/InternalPlayerSessionPhase5TrickplayTest.kt` | Trickplay behavior tests |
| `test/.../ui/InternalPlayerControlsPhase5AutoHideTest.kt` | Auto-hide tests |
| `test/.../ui/PlayerSurfacePhase5KidModeTest.kt` | Kid mode interaction tests |

### Files NOT Modified (Legacy)
- ❌ `player/InternalPlayerScreen.kt` - **UNTOUCHED** (legacy remains active)

---

## Contract Compliance Summary

| Contract Section | Requirement | SIP Implementation |
|-----------------|-------------|-------------------|
| 3.1 | Black bars must be black | PlayerView + Compose background |
| 3.2 | Legacy not authoritative | SIP may differ from legacy bugs |
| 3.3 | Separation of concerns | PlayerSurface owns video, gestures, background |
| 4.1 | FIT/FILL/ZOOM modes | AspectRatioMode enum + toResizeMode() |
| 4.2 | Background black in all modes | PlayerView + container background |
| 5.1 | PlayerSurface responsibilities | AndroidView host, gestures, auto-hide |
| 6.1 | Trickplay modes | Normal + trickplay state |
| 6.2 | Trickplay behavior | Speed cycling, visual feedback, exit |
| 7.1 | Auto-hide timing | TV 5-7s, phone 3-5s |
| 7.2 | Activity resets timer | DPAD, touch, trickplay, menus |
| 7.3 | Never hide with overlays | CC menu, settings, kid block |
| 8.1 | Single tap toggles controls | PlayerSurface onTap callback |
| 8.2 | Double tap seeks (optional) | ±10s seek on double-tap sides |
| 8.3 | Horizontal swipe trickplay | VOD/SERIES seek/trickplay |

---

## Legacy Behavior Mapping

| Legacy Code Location | Behavior | SIP Module | Status |
|---------------------|----------|------------|--------|
| L1347-1348 | controlsVisible, controlsTick | InternalPlayerUiState | ⬜ |
| L1365 | resizeMode state | AspectRatioMode | ⬜ |
| L1374-1379 | cycleResize() | AspectRatioMode.next() | ⬜ |
| L1438-1451 | Auto-hide logic | InternalPlayerControls | ⬜ |
| L1456-1459 | seekPreviewVisible, targetMs | InternalPlayerUiState | ⬜ |
| L1467-1470 | trickplaySpeeds, ffStage, rwStage | InternalPlayerUiState.trickplaySpeed | ⬜ |
| L1473-1487 | stopTrickplay() | InternalPlayerController callback | ⬜ |
| L1489-1507 | showSeekPreview() | InternalPlayerSession | ⬜ |
| L1517-1518 | TvKeyDebouncer | (Phase 6: TV remote) | ⬜ |
| L1836-1837 | Tap toggles controls | PlayerSurface onTap | ⬜ |

---

## Phase 5 Completion Criteria

- [ ] All Task Groups 1-5 complete
- [ ] All tests passing (unit + visual/behavioral)
- [ ] Black bars are black in all scenarios
- [ ] Aspect ratio modes work correctly
- [ ] Trickplay enters/exits cleanly with visual feedback
- [ ] Auto-hide works with correct timeouts
- [ ] Never auto-hide with blocking overlays
- [ ] Tap-to-toggle controls works
- [ ] No changes to legacy `InternalPlayerScreen.kt`
- [ ] Documentation updated (Roadmap, Status)

---

**Last Updated:** 2025-11-26
