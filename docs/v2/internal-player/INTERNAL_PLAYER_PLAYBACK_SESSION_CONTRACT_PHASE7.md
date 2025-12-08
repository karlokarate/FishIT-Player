# INTERNAL PLAYER – PHASE 7 CONTRACT  
## Unified PlaybackSession & In‑App MiniPlayer Architecture  
### Applies To: SIP Player, Global TV Input Layer, MiniPlayer Overlay  
### Legacy InternalPlayerScreen: Reference Only (Untouched)

---

# 1. Purpose

Phase 7 introduces a unified **PlaybackSession** and an **In‑App MiniPlayer** that allow video playback to continue seamlessly while navigating the app.  
This contract defines the complete behavior, state machines, navigation rules, input semantics, platform distinctions (TV vs Phone/Tablet), testing requirements, and quality‑assurance expectations.

The goal:  
A playback model that behaves like a hybrid of Netflix, YouTube and Prime Video — but entirely under your architectural control.

---

# 2. Non‑Goals

Phase 7 does **not**:

- Replace or modify any part of the legacy InternalPlayerScreen  
- Introduce new UI beyond the minimal MiniPlayer overlay  
- Implement the full resize/move UI (reserved for Phase 8/9)  
- Change subtitle/CC behavior (Phase 4) or PlayerSurface behavior (Phase 5)  
- Redesign TV input (Phase 6 already delivered this)

---

# 3. Core Principles

## 3.1 Single PlaybackSession
There is **exactly one** shared playback session across the entire app.  
It owns:

- The `ExoPlayer` instance  
- Playback lifecycle  
- Position/duration state  
- Track selection  
- Audio/subtitle state  
- Video size state  

The session must not be destroyed when:

- Leaving the full player  
- Opening the MiniPlayer  
- Navigating between screens  
- Entering/exiting in‑app MiniPlayer mode  
- Entering/exiting system PiP mode  

The session is only released when:

- The app is closed  
- Playback is fully stopped by the user  
- Errors require recreation  

---

## 3.2 Two Presentation Layers

### A) Full Player (SIP Player)
The existing SIP Player remains the full playback UI.

### B) In‑App MiniPlayer (Phase 7)
A floating overlay rendered **inside the app UI**, not using system PiP.

Characteristics:

- Appears on top of all app screens  
- Uses the same PlaybackSession  
- Supports Play/Pause, Seek, Toggle, Resize/Move (Phase 8/9)  
- Focusable via TV input  
- Behaves like YouTube’s in‑app mini player

### C) System PiP (Phone/Tablet only)
Native Android PiP is used **only** when the user leaves the app:
- Home button  
- Recents  
- OS background transitions  

System PiP is **never** triggered by the UI PIP button.

Fire TV:
- Never use system PiP from UI
- System may still auto‑PiP based on OS

---

# 4. MiniPlayer Contract

## 4.1 MiniPlayerState

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

---

## 4.2 Behavior Rules

### Full Player → MiniPlayer
Triggered by UI PIP Button:
- Must **NOT** call `enterPictureInPictureMode()`
- Instead:
  1. Save returnRoute
  2. Navigate back to underlying screen
  3. Set `MiniPlayerState.visible = true`

### MiniPlayer → Full Player
- Navigate back to SIP player route
- Set `MiniPlayerState.visible = false`

### Inside MiniPlayer
Normal mode:
- PLAY/PAUSE → toggle  
- FF/RW → seek  
- DPAD → behaves as background UI navigation unless focused  
- Long‑press PLAY → toggle Focus Zone (UI <-> MiniPlayer)  
- Row Fast‑Scroll disabled

Resize mode (Phase 8):
- FF/RW → resize  
- DPAD → move  
- CENTER → confirm  
- BACK → cancel  

---

# 5. Platform Behavior

## 5.1 Phones/Tablets
- Home/Recents triggers system PiP if:
  - Playback active  
  - MiniPlayer not visible  
- UI PIP button = in‑app MiniPlayer only  

## 5.2 Fire TV
- UI PIP button = in‑app MiniPlayer only  
- System PiP only if FireOS invokes it  

---

# 6. TV Input Contract Extensions

- Long‑Press PLAY = `TvAction.TOGGLE_MINI_PLAYER_FOCUS`
- MiniPlayer visible → block:
  - `ROW_FAST_SCROLL_FORWARD`
  - `ROW_FAST_SCROLL_BACKWARD`
- Allowed:
  - PLAY/PAUSE
  - FF/RW
  - MENU (long) for resize
  - DPAD movement in resize mode
  - CENTER confirmation  

FocusZones:
- `MINI_PLAYER`
- `PRIMARY_UI`

---

# 7. PlaybackSession Requirements

### Functions:
```
play(), pause(), togglePlayPause(),
seekTo(), seekBy(),
setSpeed(), enableTrickplay(),
stop(), release()
```

### State:
```
positionMs, durationMs
isPlaying, buffering
error, videoSize
playbackState
```

### Guarantees:
- No re‑init between MiniPlayer/full transitions  
- Survives navigation  
- Compatible with system PiP  

---

# 8. Navigation Contract

### Full → Mini:
- From UI PIP button
- Save route
- Show MiniPlayer overlay  

### Mini → Full:
- Navigate to full player
- Hide MiniPlayer  

### PiP → Full:
- Restore PlaybackSession to full UI  

---

# 9. Quality Requirements

Use:
- Detekt (complexity < 10)
- Ktlint  
- Android Lint (PiP warnings)
- Strict null‑safety
- No direct ExoPlayer access outside PlaybackSession  
- No blocking operations in MiniPlayer UI  

---

# 10. Testing Requirements

## Playback:
- Full ↔ Mini transitions
- PiP entry/exit  
- Seamless playback across navigation  

## MiniPlayer:
- Toggle focus  
- Block fast scroll  
- Resize mode (Phase 8)  

## TV Input:
- Correct actions when MiniPlayer visible  
- Long‑press PLAY toggle  

## Navigation:
- Resuming full player route  
- returnRoute correctness  

## Regression:
- Phase 4 (subtitles)
- Phase 5 (surface)
- Phase 6 (input)
- Phase 3 (live)  

---

# 11. Ownership

- MiniPlayerManager manages MiniPlayerState  
- PlaybackSessionController manages ExoPlayer  
- TvInputController adjusts behavior based on MiniPlayerState  
- FocusKit is authoritative for focus  
- SIP Player screens are consumers only  

---

# END OF CONTRACT  
Filename: `INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md`
