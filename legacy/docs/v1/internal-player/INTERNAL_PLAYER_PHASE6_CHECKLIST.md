> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

# Internal Player Phase 6 Checklist – Global TV Input System & FocusKit-First Architecture

**Version:** 1.0  
**Scope:** SIP-only global TV input handling, FocusZones, Kids Mode filtering, and TvInputController  
**Contract Reference:** [INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md](INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md)

---

## Overview

Phase 6 implements a **global, unified TV Input + Focus system** for the entire FishIT Player. It replaces scattered DPAD-handling and screen-local hacks with a fully declarative, profile-aware, FocusKit-driven, testable architecture. All work is SIP-only and does not modify the legacy `InternalPlayerScreen.kt`.

**Key Principles:**
1. **SIP-Only**: No changes to legacy `InternalPlayerScreen.kt`
2. **Contract-Driven**: Behavior defined by `INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md`
3. **Global System**: TV input becomes app-wide, not player-local
4. **FocusKit-First**: FocusKit remains the central focus engine for all screens
5. **Kids Mode Global**: Kids profile input filtering applies BEFORE screen configs
6. **Declarative**: Screen configs define key→action mappings without code forks

---

## Repository Analysis Summary

### Current TV Input & Focus Handling

#### What Is Good / Reusable

| Module | Description | Phase 6 Reuse |
|--------|-------------|---------------|
| **FocusKit.kt** | Central focus facade with `tvClickable`, `tvFocusFrame`, `tvFocusableItem`, `focusGroup`, `focusBringIntoViewOnFocus`, DPAD adjust helpers | **RETAIN & EXTEND**: Add FocusZones abstraction on top |
| **TvKeyDebouncer.kt** | Prevents rapid key events on Fire TV remotes (300ms debounce) | **INTEGRATE**: Wire into global TvInputController |
| **FocusRowEngine.kt** | Media row focus handling with `tvFocusableItem` and prefetch | **RETAIN**: Rows become FocusZone consumers |
| **TvFocusRow.kt** | TV-specific lazy row with `focusGroup()` and auto-centering | **RETAIN**: Rows become FocusZone consumers |
| **TvButtons.kt** | TV-aware button variants (`TvButton`, `TvIconButton`) | **RETAIN**: Buttons consume focus via FocusKit |

#### What Becomes Global in Phase 6

| Concern | Current Location | Phase 6 Global |
|---------|------------------|----------------|
| KeyEvent → logical role mapping | Scattered in screens | **TvKeyRole mapper** (single source of truth) |
| Role → semantic action mapping | Inline in each screen | **TvScreenInputConfig DSL** (per-screen config) |
| DPAD navigation dispatch | `onPreviewKeyEvent` in many composables | **TvInputController** routes to FocusKit |
| Media key handling (FF/RW/Play) | InternalPlayerScreen.kt L1543-1698 | **TvInputController** dispatches TvActions |
| Kids Mode input blocking | Per-screen conditionals | **Global Kids Mode filter** (before screen config) |
| Overlay input restriction | Per-overlay `onPreviewKeyEvent` | **Global overlay blocking rules** |

#### What Becomes Screen-Specific in Phase 6

| Screen | Custom Mappings |
|--------|-----------------|
| **Player** | DPAD_LEFT/RIGHT → SEEK_10S, FF/RW → SEEK_30S, DPAD_UP → FOCUS_QUICK_ACTIONS |
| **Library** | FF/RW → PAGE_UP/DOWN, DPAD_CENTER → OPEN_DETAILS |
| **Settings** | Standard DPAD navigation only |
| **ProfileGate** | DPAD navigation within PIN keypad, BACK → dismiss |
| **LiveList Overlay** | DPAD_UP/DOWN → NAVIGATE, BACK → close |

#### What Must Be Replaced by Phase 6 Architecture

| Current Pattern | Problem | Phase 6 Replacement |
|-----------------|---------|---------------------|
| `onPreviewKeyEvent { ... }` in HomeChromeScaffold | Chrome expansion logic mixed with key handling | TvInputController with MENU action |
| `onKeyEvent` in AppIconButton | Button-local CENTER/ENTER handling | FocusKit tvClickable handles via TvInputController |
| Hardcoded KEYCODE checks in InternalPlayerScreen | Monolithic key handling (L1543-1735) | TvScreenInputConfig for Player + TvInputController |
| Manual `FocusManager.moveFocus()` calls | Scattered focus control | FocusKit.focusZone() with TvAction dispatch |
| `TvTextFieldFocusHelper` onPreviewKeyEvent | TextField-local escape logic | TvInputController respects textfield focus state |

---

## Task Group 1: TvKeyRole & Global KeyEvent→Role Mapping

**Goal:** Create the foundational abstraction for all TV key events

### Task 1.1: TvKeyRole Enum Definition ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/ui/tv/input/TvKeyRole.kt`

**Implementation Requirements:**
- Define all semantic key roles (Contract Section 3.1)
- DPAD Navigation: `DPAD_UP`, `DPAD_DOWN`, `DPAD_LEFT`, `DPAD_RIGHT`, `DPAD_CENTER`
- Playback: `PLAY_PAUSE`, `FAST_FORWARD`, `REWIND`
- Menu/System: `MENU`, `BACK`
- Channel: `CHANNEL_UP`, `CHANNEL_DOWN`
- Info: `INFO`, `GUIDE`
- Numbers: `NUM_0` through `NUM_9`

**Contract Reference:** Section 3.1

**Tests Required:**
- Enum completeness (all roles defined)
- Enum ordering stability
- No duplicates

---

### Task 1.2: KeyEvent to TvKeyRole Mapper ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/ui/tv/input/TvKeyRoleMapper.kt`

**Implementation Requirements:**
- Map Android `KeyEvent.KEYCODE_*` to `TvKeyRole`
- Handle all DPAD codes: `DPAD_UP`, `DPAD_DOWN`, `DPAD_LEFT`, `DPAD_RIGHT`, `DPAD_CENTER`
- Handle media keys: `MEDIA_PLAY_PAUSE`, `MEDIA_PLAY`, `MEDIA_PAUSE`, `MEDIA_FAST_FORWARD`, `MEDIA_REWIND`
- Handle menu keys: `MENU`, `BACK`, `ESCAPE`
- Handle channel keys: `CHANNEL_UP`, `CHANNEL_DOWN`, `PAGE_UP`, `PAGE_DOWN`
- Handle info keys: `INFO`, `GUIDE`, `TV_DATA_SERVICE`
- Handle number keys: `0`-`9`
- Return `null` for unsupported keycodes

**Contract Reference:** Section 3.1

**Tests Required:**
- All supported keycodes map correctly
- Unsupported keycodes return null
- Mapping is deterministic
- ACTION_DOWN vs ACTION_UP handling

---

### Task 1.3: Global Debounce Integration ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/ui/tv/input/TvKeyRoleMapper.kt`

**⚠️ IMPORTANT: TvKeyDebouncer Pipeline Position**

The existing `TvKeyDebouncer` (`player/TvKeyDebouncer.kt`) provides a 300ms debounce mechanism designed for Fire TV remotes. In the Phase 6 global pipeline:

```
KeyEvent → TvKeyDebouncer → TvKeyRole → TvInputController → ...
```

**TvKeyDebouncer Characteristics (from current implementation):**
- Default 300ms debounce threshold
- Handles `ACTION_DOWN` events only (ignores `ACTION_UP`)
- Per-key tracking via `lastKeyTime` map
- Rate-limited and fully-debounced modes available
- Requires `CoroutineScope` for job management

**Integration Approach:**
1. The `TvKeyDebouncer` should be instantiated at the `GlobalTvInputHost` level (Task 4.3)
2. The `TvKeyRoleMapper` receives **already-debounced** events
3. The mapper itself does NOT re-implement debouncing
4. Debounce configuration can be per-screen (via `TvScreenInputConfig`) for flexibility

**Implementation Requirements:**
- Integrate `TvKeyDebouncer` into the mapping pipeline at `GlobalTvInputHost`
- Apply debouncing to seek-related keys (FF, RW, DPAD_LEFT/RIGHT in Player)
- Configurable debounce threshold (default 300ms per existing debouncer)
- Skip debounce for navigation keys that must remain responsive

**Contract Reference:** Section 9.2

**Legacy Reference:**
- `TvKeyDebouncer.kt` existing implementation (146 lines, fully functional)
- InternalPlayerScreen.kt L1658-1679 debouncer usage

**Tests Required:**
- Debouncing applied to configured keys
- Non-debounced keys pass through immediately
- Debounce threshold is configurable

---

## Task Group 2: TvAction Definitions & ScreenConfig DSL

**Goal:** Define semantic actions and screen-specific key mappings

### Task 2.1: TvAction Enum Definition ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/ui/tv/input/TvAction.kt`

**Implementation Requirements:**
- Define all semantic actions (Contract Section 3.1 Level 2)
- Playback: `PLAY_PAUSE`, `SEEK_FORWARD_10S`, `SEEK_FORWARD_30S`, `SEEK_BACKWARD_10S`, `SEEK_BACKWARD_30S`
- Menu/Overlay: `OPEN_CC_MENU`, `OPEN_ASPECT_MENU`, `OPEN_QUICK_ACTIONS`, `OPEN_LIVE_LIST`
- Pagination: `PAGE_UP`, `PAGE_DOWN`
- Focus: `FOCUS_TIMELINE`, `FOCUS_QUICK_ACTIONS`
- Navigation: `NAVIGATE_UP`, `NAVIGATE_DOWN`, `NAVIGATE_LEFT`, `NAVIGATE_RIGHT`
- Channel: `CHANNEL_UP`, `CHANNEL_DOWN`
- System: `BACK`

**Contract Reference:** Section 3.1 Level 2

**Tests Required:**
- Enum completeness
- Enum ordering stability
- Category grouping (for inspector display)

---

### Task 2.2: TvScreenId Enum Definition ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/ui/tv/input/TvScreenId.kt`

**Implementation Requirements:**
- Define all screen identifiers (Contract Section 4.1)
- Screen IDs: `START`, `LIBRARY`, `PLAYER`, `SETTINGS`, `DETAIL`, `PROFILE_GATE`, `LIVE_LIST`, `CC_MENU`, `ASPECT_MENU`, `SEARCH`
- Each screen ID is a stable constant

**Contract Reference:** Section 4.1

**Tests Required:**
- Enum completeness
- No duplicates

---

### Task 2.3: TvScreenInputConfig Data Model ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/ui/tv/input/TvScreenInputConfig.kt`

**Implementation Requirements:**
- Define `TvScreenInputConfig` data class
- Contains mapping: `(TvKeyRole) → TvAction?`
- Supports `null` result (key ignored or delegated to FocusKit)
- Immutable after construction

**Contract Reference:** Section 4.2

**Tests Required:**
- Config resolution correctness
- Missing key returns null
- Immutability

---

### Task 2.4: ScreenConfig DSL Builder ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/ui/tv/input/ScreenConfigDsl.kt`

**Implementation Requirements:**
- Provide declarative DSL for screen config definition:
  ```kotlin
  screenConfig(PLAYER) {
      on(FAST_FORWARD) -> SEEK_FORWARD_30S
      on(REWIND) -> SEEK_BACKWARD_30S
      on(DPAD_UP) -> FOCUS_QUICK_ACTIONS
      on(MENU) -> OPEN_QUICK_ACTIONS
  }
  ```
- Type-safe at compile time
- Produces immutable `TvScreenInputConfig`

**Contract Reference:** Section 4.2

**Tests Required:**
- DSL produces correct mappings
- Multiple entries work
- Missing entries return null
- Type safety enforced

---

### Task 2.5: Default Screen Configs ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/ui/tv/input/DefaultScreenConfigs.kt`

**Implementation Requirements:**
- Define default configs for all screens using DSL
- **Player**: DPAD_LEFT/RIGHT → SEEK_10S, FF/RW → SEEK_30S, DPAD_UP → FOCUS_QUICK_ACTIONS, MENU → OPEN_QUICK_ACTIONS
- **Library**: FF/RW → PAGE_UP/DOWN, standard DPAD navigation
- **Settings**: Standard DPAD navigation only
- **ProfileGate**: Standard DPAD navigation
- **LiveList**: DPAD navigation, CHANNEL_UP/DOWN → PAGE_UP/DOWN
- Fallback config for unknown screens

**Contract Reference:** Section 4.2

**Tests Required:**
- Player config matches contract
- Library config matches contract
- All screens have configs
- Fallback works

---

## Task Group 3: TvScreenContext and Screen Input Routing

**Goal:** Provide per-screen context for input handling

### Task 3.1: TvScreenContext Interface ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/ui/tv/input/TvScreenContext.kt`

**Implementation Requirements:**
- Define interface with:
  - `screenId: TvScreenId`
  - `config: TvScreenInputConfig`
  - `onAction(action: TvAction): Boolean` callback
  - `currentFocusZone: FocusZone?` accessor
- Each screen provides its own context

**Contract Reference:** Section 5.1

**Tests Required:**
- Interface contract validation
- Action callback invocation
- Focus zone accessor

---

### Task 3.2: TvScreenContextProvider Composable ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/ui/tv/input/TvScreenContextProvider.kt`

**Implementation Requirements:**
- Composable that provides `TvScreenContext` to children via CompositionLocal
- `LocalTvScreenContext` composition local
- Auto-registers with global `TvInputController`

**Contract Reference:** Section 5.1

**Tests Required:**
- Context provided to children
- Context registration with controller
- Context cleanup on dispose

---

### Task 3.3: Screen Context Wiring for Player ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/InternalPlayerContent.kt`

**Implementation Requirements:**
- Wrap player content with `TvScreenContextProvider`
- Provide `TvScreenContext` with `PLAYER` screen ID
- Wire `onAction` callback to player controller methods:
  - `PLAY_PAUSE` → `controller.onTogglePlayPause()`
  - `SEEK_FORWARD_*` → `controller.onStepSeek(deltaMs)`
  - `SEEK_BACKWARD_*` → `controller.onStepSeek(-deltaMs)`
  - `OPEN_CC_MENU` → `controller.onToggleCcMenu()`
  - `FOCUS_QUICK_ACTIONS` → FocusKit zone navigation

**Contract Reference:** Section 8

**Tests Required:**
- Player actions dispatched correctly
- Focus actions route to FocusKit
- All Player TvActions handled

---

## Task Group 4: Global TvInputController Implementation

**Goal:** Create the central input handling orchestrator

### Task 4.1: TvInputController Interface ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/ui/tv/input/TvInputController.kt`

**Implementation Requirements:**
- Define interface (Contract Section 5.1):
  ```kotlin
  interface TvInputController {
      fun onKeyEvent(event: KeyEvent, context: TvScreenContext): Boolean
      val quickActionsVisible: State<Boolean>
      val focusedAction: State<TvAction?>
  }
  ```
- Central orchestrator for all TV input
- Never accessed from UI directly (only via context)

**Contract Reference:** Section 5.1, 5.2

**Tests Required:**
- Interface contract validation
- State exposure

---

### Task 4.2: DefaultTvInputController Implementation ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/ui/tv/input/DefaultTvInputController.kt`

**Implementation Requirements:**
- Implement `TvInputController` interface
- Pipeline: KeyEvent → TvKeyDebouncer → TvKeyRole → Kids Filter → Overlay Filter → TvAction → Dispatch
- Dispatch targets:
  - FocusKit for `NAVIGATE_*` and `FOCUS_*` actions
  - Screen context `onAction` for screen-specific actions
  - Overlay managers for overlay-specific actions
- Maintain `quickActionsVisible` and `focusedAction` state

**Contract Reference:** Section 5.2, Section 9.1

**Tests Required:**
- Full pipeline execution
- Kids Mode filtering
- Overlay blocking
- Action dispatch to correct target

---

### Task 4.3: GlobalTvInputHost Composable ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/ui/tv/input/GlobalTvInputHost.kt`

**Implementation Requirements:**
- Root-level composable that intercepts all KeyEvents
- Provides `LocalTvInputController` composition local
- Applies `onPreviewKeyEvent` at app root
- Delegates to `DefaultTvInputController`

**Contract Reference:** Section 9.1

**Tests Required:**
- KeyEvent interception at root
- Controller accessible via CompositionLocal
- No event leakage

---

### Task 4.4: Integration with MainActivity/App Root ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/MainActivity.kt` (or app root composable)

**Implementation Requirements:**
- Wrap app content with `GlobalTvInputHost`
- Ensure all screens receive input via global controller
- Maintain existing non-TV behavior unchanged

**Contract Reference:** Section 9.1

**Tests Required:**
- TV input handled globally
- Phone/tablet behavior unchanged
- All screens accessible

---

## Task Group 5: FocusZones Integration into FocusKit

**Goal:** Add named logical focus zones to FocusKit

**⚠️ IMPORTANT: FocusKit Integration Pattern**

FocusZones **MUST** integrate with the existing FocusKit infrastructure (`ui/focus/FocusKit.kt`) rather than creating a parallel focus system. The current FocusKit provides:

| Existing Feature | Location | Usage |
|------------------|----------|-------|
| `FocusRequester` management | Throughout FocusKit | Zone targets |
| `focusGroup()` modifier | `Modifier.focusGroup()` | Zone containers |
| `focusProperties` for neighbors | `FocusKit.focusNeighbors()` | Zone-to-zone transitions |
| `focusBringIntoViewOnFocus()` | Throughout | Auto-scroll on zone focus |
| `LocalForceTvFocus` | CompositionLocal | Force TV behavior for overlays |
| `tvFocusFrame()` / `tvClickable()` | Focus visuals | Zone item focus decoration |

**Integration Approach:**
1. `FocusZone` enum is a **labeling** mechanism, not a replacement for FocusRequester
2. `FocusZoneManager` wraps existing `FocusRequester` instances with zone metadata
3. `Modifier.focusZone(zone)` composes with existing `focusGroup()` and `focusRequester()`
4. Zone transitions use `FocusRequester.requestFocus()` (existing mechanism)
5. Navigation within zones uses `FocusManager.moveFocus()` (existing Compose API)

**Example Integration:**
```kotlin
// In InternalPlayerControls.kt (Phase 6)
Row(
    modifier = Modifier
        .focusGroup()                          // Existing FocusKit
        .focusZone(FocusZone.PLAYER_CONTROLS)  // New Phase 6 zone label
) {
    // Control buttons with existing tvClickable() modifiers
}
```

### Task 5.1: FocusZone Enum Definition ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/ui/focus/FocusZone.kt`

**Implementation Requirements:**
- Define all logical focus zones (Contract Section 6.1):
  - `PLAYER_CONTROLS` – Play/pause, seek bar, volume
  - `QUICK_ACTIONS` – CC, aspect ratio, speed, PiP buttons
  - `TIMELINE` – Seek bar / progress indicator
  - `CC_BUTTON` – Closed captions button
  - `ASPECT_BUTTON` – Aspect ratio button
  - `EPG_OVERLAY` – EPG program guide navigation
  - `LIVE_LIST` – Live channel selection overlay
  - `LIBRARY_ROW` – Content rows in library screens
  - `SETTINGS_LIST` – Settings items list
  - `PROFILE_GRID` – Profile selection grid

**Contract Reference:** Section 6.1

**Tests Required:**
- All zones defined
- No duplicates

---

### Task 5.2: FocusZoneManager Interface ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/ui/focus/FocusZoneManager.kt`

**Implementation Requirements:**
- Interface for managing focus zones:
  ```kotlin
  interface FocusZoneManager {
      fun registerZone(zone: FocusZone, requester: FocusRequester)
      fun unregisterZone(zone: FocusZone)
      fun focusZone(zone: FocusZone): Boolean
      fun currentZone(): FocusZone?
  }
  ```
- Each zone has a `FocusRequester`
- Zone transitions use FocusKit's `requestFocus()` mechanism

**Contract Reference:** Section 6.2

**Tests Required:**
- Zone registration
- Zone focus request
- Current zone tracking

---

### Task 5.3: FocusZone Composable Modifier ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/ui/focus/FocusKit.kt`

**Implementation Requirements:**
- Add `Modifier.focusZone(zone: FocusZone)` extension
- Auto-registers zone with `FocusZoneManager`
- Auto-unregisters on dispose
- Integrates with existing `focusGroup()` behavior

**Contract Reference:** Section 6.2

**Tests Required:**
- Zone registration on composition
- Zone unregistration on dispose
- Focus request works

---

### Task 5.4: TvAction → FocusZone Resolution ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/ui/tv/input/DefaultTvInputController.kt`

**Implementation Requirements:**
- Map focus-related TvActions to FocusZone operations:
  - `FOCUS_QUICK_ACTIONS` → `FocusZoneManager.focusZone(QUICK_ACTIONS)`
  - `FOCUS_TIMELINE` → `FocusZoneManager.focusZone(TIMELINE)`
  - `NAVIGATE_*` → `FocusKit.move*()` within current zone
- Focus moves to zone first, then action executes

**Contract Reference:** Section 6.2

**Tests Required:**
- Focus actions route to zones
- Navigate actions use FocusKit
- Zone transition works

---

## Task Group 6: Kids Mode Global Filtering

**Goal:** Enforce Kids Mode input restrictions globally BEFORE screen configs

### Task 6.1: KidsModeInputFilter Interface ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/ui/tv/input/KidsModeInputFilter.kt`

**Implementation Requirements:**
- Interface for filtering input based on Kids Mode:
  ```kotlin
  interface KidsModeInputFilter {
      fun filterAction(action: TvAction, isKidProfile: Boolean): TvAction?
  }
  ```
- Returns `null` to block action, or transformed action

**Contract Reference:** Section 7.1

**Tests Required:**
- Interface contract validation

---

### Task 6.2: DefaultKidsModeInputFilter Implementation ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/ui/tv/input/DefaultKidsModeInputFilter.kt`

**Implementation Requirements:**
- Implement Kids Mode blocking rules (Contract Section 7.1):
- **Blocked actions** (return `null`):
  - `FAST_FORWARD`, `REWIND`
  - `SEEK_FORWARD_10S`, `SEEK_FORWARD_30S`
  - `SEEK_BACKWARD_10S`, `SEEK_BACKWARD_30S`
  - `OPEN_CC_MENU`, `OPEN_ASPECT_MENU`, `OPEN_LIVE_LIST`
- **Allowed actions** (pass through):
  - `NAVIGATE_*` (all DPAD navigation)
  - `BACK`
  - `MENU` → transforms to Kids-approved menu only
  - `PLAY_PAUSE`
- Uses `SettingsStore.currentProfileId` + `ObxProfile.type == "kid"` for detection

**Contract Reference:** Section 7.1, 7.2

**Tests Required:**
- All blocked actions return null for kid profiles
- Allowed actions pass through
- Non-kid profiles unaffected
- Profile detection works

---

### Task 6.3: Integration into TvInputController Pipeline ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/ui/tv/input/DefaultTvInputController.kt`

**Implementation Requirements:**
- Insert Kids Mode filter BEFORE screen config resolution
- Pipeline order: KeyEvent → Debounce → TvKeyRole → **KidsModeFilter** → ScreenConfig → TvAction
- Log blocked actions to diagnostics
- Provide visual feedback when blocked action attempted (optional toast/overlay)

**Contract Reference:** Section 7.1

**Tests Required:**
- Filter applied before screen config
- Blocked actions logged
- Visual feedback (if implemented)

---

## Task Group 7: Overlay Blocking Rules

**Goal:** Restrict input when blocking overlays are active

### Task 7.1: OverlayState Tracker ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/ui/tv/input/OverlayStateTracker.kt`

**Implementation Requirements:**
- Track active blocking overlays (Contract Section 8.1):
  - CC Menu (`showCcMenuDialog`)
  - Aspect Ratio Menu (`showAspectMenuDialog`)
  - Live List Overlay (`showLiveList`)
  - Settings Dialog (`showSettingsDialog`)
  - Profile Gate (`profileGateActive`)
  - Error Dialogs (`showErrorDialog`)
- Expose `hasBlockingOverlay: Boolean` state
- State changes trigger input filter updates

**Contract Reference:** Section 8.1

**Tests Required:**
- All overlay types detected
- `hasBlockingOverlay` accurate
- State updates correctly

---

### Task 7.2: Overlay Input Filter ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/ui/tv/input/OverlayInputFilter.kt`

**Implementation Requirements:**
- Filter input when blocking overlay is active (Contract Section 8.1):
- **Allowed inside overlay**:
  - `NAVIGATE_UP`, `NAVIGATE_DOWN`, `NAVIGATE_LEFT`, `NAVIGATE_RIGHT`
  - `BACK` → closes overlay
- **Blocked** (return `null`):
  - All other actions including `FAST_FORWARD`, `REWIND`, `PLAY_PAUSE`, etc.

**Contract Reference:** Section 8.1

**Tests Required:**
- Navigation allowed inside overlay
- BACK closes overlay
- All other actions blocked
- No overlay = no blocking

---

### Task 7.3: Integration into TvInputController Pipeline ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/ui/tv/input/DefaultTvInputController.kt`

**Implementation Requirements:**
- Insert overlay filter AFTER Kids Mode filter
- Pipeline order: KeyEvent → Debounce → TvKeyRole → KidsModeFilter → **OverlayFilter** → ScreenConfig → TvAction
- BACK action triggers overlay dismiss

**Contract Reference:** Section 8.1

**Tests Required:**
- Overlay filter applied at correct position
- BACK dismisses overlay
- Navigation works inside overlay

---

## Task Group 8: TV Input Inspector (Debug Overlay)

**Goal:** Create debug overlay for TV input diagnostics

**⚠️ IMPORTANT: Use Existing Debug Infrastructure**

The TV Input Inspector **MUST** integrate with the existing debug/diagnostics system rather than creating a parallel logging mechanism:

| Existing Module | Location | Integration Point |
|-----------------|----------|-------------------|
| **GlobalDebug** | `core/debug/GlobalDebug.kt` | Use `logDpad()` for DPAD events, add `logTvInput()` method for TvAction events |
| **RouteTag** | `metrics/RouteTag.kt` | Use `RouteTag.current` for screen context instead of separate tracking |
| **DiagnosticsLogger** | `diagnostics/DiagnosticsLogger.kt` | Use `ComposeTV.logKeyEvent()` for structured event logging |
| **AppLog** | `core/logging/AppLog.kt` | All inspector logs flow through unified logging |

The inspector should **consume** events from GlobalDebug/DiagnosticsLogger, not create its own logging path.

### Task 8.1: TvInputInspectorState Data Model ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/ui/tv/debug/TvInputInspectorState.kt`

**Implementation Requirements:**
- Data model for inspector display:
  ```kotlin
  data class TvInputEvent(
      val timestamp: Long,
      val keyEvent: String,        // "KEYCODE_DPAD_LEFT (DOWN)"
      val keyRole: TvKeyRole?,     // DPAD_LEFT
      val action: TvAction?,       // SEEK_BACKWARD_10S
      val screenId: TvScreenId?,   // PLAYER
      val focusZone: FocusZone?,   // PLAYER_CONTROLS
      val handled: Boolean,        // true
  )
  ```
- Store last 5 events with timestamps
- **Integration:** Events logged via `GlobalDebug.logTvInput()` (new method to add)

**Contract Reference:** Section 7 (TV Input Debug Overlay)

**Tests Required:**
- Event model complete
- Event storage works
- Timestamp accuracy

---

### Task 8.2: TvInputInspector Composable ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/ui/tv/debug/TvInputInspector.kt`

**Implementation Requirements:**
- Semi-transparent overlay in bottom-right corner
- Displays last 5 key events with:
  - KeyEvent code and action
  - Resolved TvKeyRole
  - Resolved TvAction (or "null" if blocked)
  - Current ScreenId (use `RouteTag.current` for context)
  - Current FocusZone
  - Handled state
- Only visible when `GlobalDebug.tvInputInspectorEnabled`
- Only available in debug builds
- **Integration:** Use same pattern as existing debug overlays (e.g., log overlay in HomeChromeScaffold)

**Contract Reference:** Section 7 (TV Input Debug Overlay)

**Tests Required:**
- Overlay renders correctly
- Events display in order
- Toggle works
- Debug-only enforcement

---

### Task 8.3: Inspector Toggle in Developer Settings ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/ui/screens/SettingsScreen.kt`
- `app/src/main/java/com/chris/m3usuite/core/debug/GlobalDebug.kt`

**Implementation Requirements:**
- Add `tvInputInspectorEnabled` toggle to GlobalDebug (follows existing `setEnabled()` pattern)
- Add `logTvInput()` method to GlobalDebug for TvAction event logging
- Add corresponding toggle in Developer Settings section (follows existing debug toggle pattern)
- Default: disabled
- Persists across sessions (DataStore)

**Contract Reference:** Section 7 (TV Input Debug Overlay)

**Tests Required:**
- Toggle persists
- Inspector responds to toggle
- Default is disabled

---

## Task Group 9: Player, Library, Settings Integration as Consumers

**Goal:** Wire screens as consumers of the global TV input system

### Task 9.1: Player Screen Consumer Integration ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/InternalPlayerContent.kt`
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/InternalPlayerControls.kt`

**Implementation Requirements:**
- Remove local `onPreviewKeyEvent` handlers that duplicate global handling
- Use `TvScreenContextProvider` with Player config
- Wire TvAction callbacks to existing controller methods
- Add `focusZone(PLAYER_CONTROLS)`, `focusZone(QUICK_ACTIONS)`, `focusZone(TIMELINE)` modifiers to appropriate UI sections
- Retain existing FocusKit focus visuals

**Contract Reference:** Section 8

**Tests Required:**
- All player TvActions work via global system
- Focus zones registered correctly
- No duplicate key handling
- Existing behavior preserved

---

### Task 9.2: Library Screen Consumer Integration ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/ui/home/StartScreen.kt` (or LibraryScreen)

**Implementation Requirements:**
- Use `TvScreenContextProvider` with Library config
- Wire TvAction callbacks:
  - `PAGE_UP/PAGE_DOWN` → scroll by page
  - `NAVIGATE_*` → FocusKit handles
- Add `focusZone(LIBRARY_ROW)` to content rows
- Retain existing FocusKit focus visuals

**Contract Reference:** Section 4.2

**Tests Required:**
- Library TvActions work via global system
- FF/RW map to page navigation
- Rows focusable via FocusKit

---

### Task 9.3: Settings Screen Consumer Integration ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/ui/screens/SettingsScreen.kt`

**Implementation Requirements:**
- Use `TvScreenContextProvider` with Settings config
- Wire TvAction callbacks (standard DPAD navigation only)
- Add `focusZone(SETTINGS_LIST)` to settings list
- Retain existing focus handling for TextFields

**Contract Reference:** Section 4.2

**Tests Required:**
- Settings TvActions work via global system
- DPAD navigation works
- TextField focus escape still works

---

### Task 9.4: ProfileGate Consumer Integration ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/ui/auth/ProfileGate.kt`

**Implementation Requirements:**
- Use `TvScreenContextProvider` with ProfileGate config
- Wire TvAction callbacks (DPAD navigation)
- Add `focusZone(PROFILE_GRID)` to profile selection area
- Retain existing PIN keypad focus handling

**Contract Reference:** Section 4.2

**Tests Required:**
- ProfileGate TvActions work via global system
- PIN keypad navigation preserved
- Profile selection works

---

## Task Group 10: Testing & Validation Plan

**Goal:** Comprehensive test coverage for Phase 6

### Task 10.1: TvKeyRole Mapper Unit Tests ⬜
**Files to Create:**
- `app/src/test/java/com/chris/m3usuite/ui/tv/input/TvKeyRoleMapperTest.kt`

**Test Coverage:**
- All DPAD keycodes map correctly
- All media keycodes map correctly
- All menu keycodes map correctly
- Channel keycodes map correctly
- Number keycodes map correctly
- Unsupported keycodes return null
- Deterministic mapping

**Contract Reference:** Section 10.1

---

### Task 10.2: TvScreenInputConfig Unit Tests ⬜
**Files to Create:**
- `app/src/test/java/com/chris/m3usuite/ui/tv/input/TvScreenInputConfigTest.kt`

**Test Coverage:**
- Config resolution for all screen types
- Player config matches contract
- Library config matches contract
- Missing keys return null
- DSL produces correct configs

**Contract Reference:** Section 10.2

---

### Task 10.3: FocusZones Unit Tests ⬜
**Files to Create:**
- `app/src/test/java/com/chris/m3usuite/ui/focus/FocusZoneManagerTest.kt`

**Test Coverage:**
- Zone registration and unregistration
- Focus request to zone
- Zone-to-zone transitions
- Current zone tracking
- Focus persistence across zone changes

**Contract Reference:** Section 10.3

---

### Task 10.4: Kids Mode Filter Unit Tests ⬜
**Files to Create:**
- `app/src/test/java/com/chris/m3usuite/ui/tv/input/KidsModeInputFilterTest.kt`

**Test Coverage:**
- All blocked actions return null for kid profiles
- All allowed actions pass through for kid profiles
- Non-kid profiles unaffected
- Profile detection accuracy

**Contract Reference:** Section 10.4

---

### Task 10.5: Overlay Blocking Unit Tests ⬜
**Files to Create:**
- `app/src/test/java/com/chris/m3usuite/ui/tv/input/OverlayInputFilterTest.kt`

**Test Coverage:**
- Overlay detection accuracy (all overlay types)
- Navigation allowed inside overlay
- BACK closes overlay
- Non-navigation actions blocked
- No overlay = no blocking

**Contract Reference:** Section 10.4

---

### Task 10.6: Player-Specific Action Tests ⬜
**Files to Create:**
- `app/src/test/java/com/chris/m3usuite/player/internal/TvInputPlayerIntegrationTest.kt`

**Test Coverage:**
- `PLAY_PAUSE` toggles playback
- `SEEK_FORWARD_10S/30S` seeks correctly
- `SEEK_BACKWARD_10S/30S` seeks correctly
- `CHANNEL_UP/DOWN` works for live content
- `FOCUS_QUICK_ACTIONS` moves focus to quick actions zone
- Focus management via FocusKit

**Contract Reference:** Section 10.5

---

### Task 10.7: Global Regression Tests ⬜
**Files to Create:**
- `app/src/test/java/com/chris/m3usuite/ui/tv/input/TvInputGlobalRegressionTest.kt`

**Test Coverage:**
- ProfileGate navigation stable with DPAD
- LibraryScreen rows/columns navigable
- CC menu / Aspect menu no focus leakage
- Settings screen form navigation
- All screens remain accessible via global system

**Contract Reference:** Section 10.5

---

## Summary: Files Overview

### New Files to Create (SIP Only)
| File Path | Purpose |
|-----------|---------|
| `ui/tv/input/TvKeyRole.kt` | Key role enum |
| `ui/tv/input/TvKeyRoleMapper.kt` | KeyEvent → TvKeyRole mapper |
| `ui/tv/input/TvAction.kt` | Semantic action enum |
| `ui/tv/input/TvScreenId.kt` | Screen identifier enum |
| `ui/tv/input/TvScreenInputConfig.kt` | Per-screen config data model |
| `ui/tv/input/ScreenConfigDsl.kt` | DSL builder for configs |
| `ui/tv/input/DefaultScreenConfigs.kt` | Default configs for all screens |
| `ui/tv/input/TvScreenContext.kt` | Per-screen context interface |
| `ui/tv/input/TvScreenContextProvider.kt` | Context provider composable |
| `ui/tv/input/TvInputController.kt` | Controller interface |
| `ui/tv/input/DefaultTvInputController.kt` | Controller implementation |
| `ui/tv/input/GlobalTvInputHost.kt` | Root-level input host |
| `ui/tv/input/KidsModeInputFilter.kt` | Kids Mode filter interface |
| `ui/tv/input/DefaultKidsModeInputFilter.kt` | Kids Mode filter implementation |
| `ui/tv/input/OverlayStateTracker.kt` | Overlay state tracking |
| `ui/tv/input/OverlayInputFilter.kt` | Overlay input filter |
| `ui/focus/FocusZone.kt` | Focus zone enum |
| `ui/focus/FocusZoneManager.kt` | Zone manager interface |
| `ui/tv/debug/TvInputInspectorState.kt` | Inspector data model |
| `ui/tv/debug/TvInputInspector.kt` | Debug inspector composable |
| 10 test files | Unit and integration tests |

### Files to Modify (SIP Only)
| File Path | Changes |
|-----------|---------|
| `ui/focus/FocusKit.kt` | Add `focusZone()` modifier |
| `player/internal/ui/InternalPlayerContent.kt` | Wire TvScreenContext |
| `player/internal/ui/InternalPlayerControls.kt` | Add FocusZone modifiers |
| `ui/home/StartScreen.kt` | Wire TvScreenContext for Library |
| `ui/screens/SettingsScreen.kt` | Wire TvScreenContext, add inspector toggle |
| `ui/auth/ProfileGate.kt` | Wire TvScreenContext |
| `core/debug/GlobalDebug.kt` | Add inspector toggle |
| `MainActivity.kt` | Wrap with GlobalTvInputHost |

### Files NOT Modified (Legacy)
- ❌ `player/InternalPlayerScreen.kt` – **UNTOUCHED** (legacy remains active)

---

## Contract Compliance Summary

| Contract Section | Requirement | SIP Implementation |
|-----------------|-------------|-------------------|
| 3.1 | TvKeyRole abstraction | TvKeyRole enum + TvKeyRoleMapper |
| 3.1 | TvAction commands | TvAction enum |
| 4.1 | TvScreenId per screen | TvScreenId enum |
| 4.2 | TvScreenInputConfig mapping | Data class + DSL |
| 5.1 | TvInputController interface | Interface + DefaultTvInputController |
| 5.2 | Controller responsibilities | Pipeline implementation |
| 6.1 | FocusZones | FocusZone enum + FocusZoneManager |
| 6.2 | Resolution rules | TvAction → FocusZone mapping |
| 7.1 | Kids Mode allowed/blocked | KidsModeInputFilter |
| 7.2 | Per-screen adjustments | TvScreenInputConfig |
| 8.1 | Overlay blocking | OverlayInputFilter + OverlayStateTracker |
| 9.1 | Event flow | Full pipeline in DefaultTvInputController |
| 9.2 | TvKeyDebouncer | Integrated into pipeline |
| 10.* | Testing | Comprehensive test suite |

---

## Legacy Behavior Mapping

| Legacy Code Location | Behavior | SIP Module | Status |
|---------------------|----------|------------|--------|
| HomeChromeScaffold L224-267 | DPAD chrome expand | TvInputController MENU action | ⬜ |
| HomeChromeScaffold L322-368 | MENU/BACK handling | TvInputController | ⬜ |
| AppIconButton L80-89 | CENTER/ENTER → onClick | FocusKit tvClickable | ⬜ |
| InternalPlayerScreen L1543-1735 | All key handling | Player TvScreenContext | ⬜ |
| InternalPlayerScreen L1655-1697 | DPAD_LEFT/RIGHT seek with debounce | TvInputController + TvKeyDebouncer | ⬜ |
| ProfileGate PIN keypad | DPAD navigation in keypad | ProfileGate TvScreenContext | ⬜ |
| TvTextFieldFocusHelper | TextField escape | TvInputController respects focus state | ⬜ |

---

## Phase 6 Completion Criteria

- [ ] All Task Groups 1-10 complete
- [ ] All tests passing (unit + integration)
- [ ] TvKeyRole mapping works for all supported keys
- [ ] TvScreenInputConfig resolves correctly per screen
- [ ] FocusZones work with FocusKit
- [ ] Kids Mode blocks all required actions
- [ ] Overlay blocking works for all overlay types
- [ ] TV Input Inspector functional in debug builds
- [ ] Player, Library, Settings, ProfileGate integrated as consumers
- [ ] No direct KeyEvent handling in screens (all via TvInputController)
- [ ] No changes to legacy `InternalPlayerScreen.kt`
- [ ] Documentation updated (Roadmap, Status)

---

**Last Updated:** 2025-11-27
