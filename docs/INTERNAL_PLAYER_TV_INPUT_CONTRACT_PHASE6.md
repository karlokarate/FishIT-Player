# INTERNAL PLAYER – PHASE 6 : GLOBAL TV INPUT & FOCUS CONTRACT
### Version 1.0 — Authoritative Behavior Specification (SIP‑Only)
### Applies to: All TV devices (FireTV, Android TV), All Screens
### Legacy InternalPlayerScreen: Reference Only / Never Modified

---

# 1. PURPOSE OF PHASE 6
Phase 6 defines a **global, unified TV Input + Focus system** for the entire FishIT Player.  
It replaces scattered DPAD-handling and screen-local hacks with a fully declarative,
profile‑aware, FocusKit‑driven, testable architecture.

This contract ensures:
- Every TV button (DPAD, FF/RW, Play/Pause, Menu, Back, Center) is globally available  
- Each screen can map these keys differently, without code forks  
- FocusKit becomes the **single source of truth** for focus navigation  
- Player becomes a *consumer*, not owner, of DPAD logic  
- Kids Mode and ProfileGate influence DPAD behavior globally  
- TV input becomes predictable, testable and extensible

---

# 2. NON‑GOALS
Phase 6 does **not**:
- Implement new UI components  
- Modify Legacy InternalPlayerScreen  
- Replace FocusKit  
- Add player features (done in Phase 5)

---

# 3. GLOBAL TV INPUT MODEL

## 3.1 Physical Key → Logical KeyRole → Semantic TvAction Pipeline

### Level 1 — `TvKeyRole` (logical abstraction of KeyEvents)
All TV keys are normalized into stable roles:

```
DPAD_UP  
DPAD_DOWN  
DPAD_LEFT  
DPAD_RIGHT  
DPAD_CENTER  
PLAY_PAUSE  
FAST_FORWARD  
REWIND  
MENU  
BACK
```

A **global, shared** mapper must convert `KeyEvent` → `TvKeyRole`.

### Level 2 — `TvAction` (semantic application-level commands)
`TvAction` represents the real intent, example:

```
PLAY_PAUSE
SEEK_FORWARD_10S
SEEK_FORWARD_30S
SEEK_BACKWARD_10S
SEEK_BACKWARD_30S
OPEN_CC_MENU
OPEN_ASPECT_MENU
OPEN_QUICK_ACTIONS
OPEN_LIVE_LIST
PAGE_UP
PAGE_DOWN
FOCUS_TIMELINE
FOCUS_QUICK_ACTIONS
NAVIGATE_UP
NAVIGATE_DOWN
NAVIGATE_LEFT
NAVIGATE_RIGHT
BACK
```

### Level 3 — Execution Path
A resolved `TvAction` is handled by:
- **FocusKit** (for focus-based actions)
- **Screen-specific controller** (e.g., playerController)
- **Navigation layer**
- **Overlay controllers** (CC, AspectRatio, LiveList, etc.)

The Player *does not interpret KeyEvents* directly.

---

# 4. SCREEN CONFIGURATION MODEL

## 4.1 `TvScreenId`
Every screen receives a unique stable ID:
```
START, LIBRARY, PLAYER, SETTINGS, DETAIL, PROFILE_GATE, etc.
```

## 4.2 `TvScreenInputConfig`
Each screen provides a declarative mapping:

```
(screenId, TvKeyRole) → TvAction?
```

Examples:

### Player Screen
```
FAST_FORWARD → SEEK_FORWARD_30S
REWIND → SEEK_BACKWARD_30S
DPAD_UP → FOCUS_QUICK_ACTIONS
DPAD_DOWN → FOCUS_TIMELINE
MENU → OPEN_QUICK_ACTIONS
```

### Library Screen
```
FAST_FORWARD → PAGE_DOWN
REWIND → PAGE_UP
DPAD_CENTER → OPEN_DETAILS
MENU → OPEN_FILTERS
```

A missing entry = key ignored on this screen.

This config is data-driven, not embedded in Composables.

---

# 5. GLOBAL TV INPUT CONTROLLER

## 5.1 Interface
```
interface TvInputController {
    fun onKeyEvent(event: KeyEvent, context: TvScreenContext): Boolean
    val quickActionsVisible: State<Boolean>
    val focusedAction: State<TvAction?>
}
```

## 5.2 Responsibilities
- Normalize all KeyEvents → TvKeyRole  
- Resolve TvAction per screen (via TvScreenInputConfig)  
- Dispatch TvAction:
  - to FocusKit if focus-navigation  
  - to SIP PlayerController if playback-specific  
  - to overlay managers if applicable  
- Maintain:
  - quickActionsVisible  
  - focusedAction (for focus redirection)  

InternalPlayerComponents **never** decide DPAD actions; they react to TvInputController.

---

# 6. FOCUSKIT INTEGRATION

## 6.1 FocusZones
FocusKit must define named logical focus areas:

```
"player_controls"
"quick_actions"
"timeline"
"cc_button"
"aspect_button"
"live_list"
"library_row"
"settings_list"
"profile_grid"
```

## 6.2 Resolution Rules

- `TvAction.FOCUS_QUICK_ACTIONS`  
  → FocusKit.focusZone("quick_actions")

- `TvAction.NAVIGATE_LEFT`  
  → FocusKit.moveLeft()

- `TvAction.NAVIGATE_DOWN`  
  → FocusKit.moveDown()

- `TvAction.OPEN_CC_MENU`  
  → focus moves to CC button *first*, then menu opens

- `TvAction.OPEN_ASPECT_MENU`  
  → focus moves to aspect ratio control

The Player or any other screen does NOT manually manage DPAD focus.

---

# 7. PROFILE & KIDS-MODE RULES

## 7.1 Global Policy
Kids Mode overrides all screen actions:

### Allowed Actions:
```
DPAD_UP/DOWN/LEFT/RIGHT  
BACK  
MENU → open KidsApprovedMenu only
```

### Blocked or transformed:
```
FAST_FORWARD → null
REWIND → null
SEEK_FORWARD_XX → null
SEEK_BACKWARD_XX → null
OPEN_CC_MENU → null
OPEN_LIVE_LIST → null
OPEN_ASPECT_MENU → null
```

## 7.2 Per-Screen Adjustments
Each screen may override actions via TvScreenInputConfig,  
but Kids Mode remains **globally enforced**.

---

# 8. BLOCKING & OVERLAYS

## 8.1 If ANY overlay is active:
- CC Menu  
- Aspect Ratio menu  
- Subtitle menu  
- Live List  
- Sleep Timer  
- Settings Dialog  
- ProfileGate  
- Error Dialog  

→ FocusKit + TvInputController restrict keys to:

```
NAVIGATE within overlay
BACK closes overlay
```

FAST_FORWARD/REWIND → **no-op**.

---

# 9. DPAD EVENT FLOW

## 9.1 Full Flow
```
KeyEvent → TvKeyDebouncer → TvKeyRole → TvInputController
→ TvAction → (FocusKit / ScreenManager / OverlayManager / SIP player)
→ focus updates / playback actions
```

No direct KeyEvent handling in:
- PlayerSurface  
- InternalPlayerControls  
- LibraryScreen  
- SettingsScreen  
- FocusKit components  

## 9.2 Debouncing
`TvKeyDebouncer` MUST be used globally to avoid double-events on FireTV.

---

# 10. TESTING REQUIREMENTS

## 10.1 Key → Role Mapping
- deterministic mapping  
- unsupported keys → null safely  

## 10.2 Action Resolution
- correct per-screen mappings  
- Kids Mode enforced globally  
- MENU/BACK behavior predictable  
- DPAD only moves focus via FocusKit

## 10.3 Focus Behavior
- FocusZones respond correctly  
- no focus traps  
- no manual DPAD logic in Composables  

## 10.4 Player Integration
- TvAction.PLAY_PAUSE toggles playback  
- Seek actions affect trickplay/seek behavior correctly  
- Focus-based actions route via FocusKit, not PlayerSurface  

## 10.5 Global Regression
- ProfileGate navigation stable with DPAD  
- LibraryScreen rows/columns still navigable  
- CC menu / Aspect menu do not allow focus leakage  

---

# 11. EVOLUTION & OWNERSHIP

All changes to TV input or focus must:
1. Update this contract  
2. Update the Phase 6 checklist  
3. Only then be implemented  

Legacy InternalPlayerScreen remains reference-only.

---

# END OF CONTRACT
