# GLOBAL TV REMOTE BEHAVIOR MAP



### Phase 6 Input Model — Screen Contexts & Key Semantics  

*(Based on Netflix / Prime / YouTube standards + custom FishIT Player upgrades)*

---

# 🏠 GLOBAL RULES (apply everywhere)

- [ ] **CENTER**  
      → Activates the focused item (Enter/OK)  
      → **Exception:** Player in playback mode → Play/Pause  

- [ ] **BACK**  
      → Closes overlays/dialogs  
      → Then exits current screen  

- [ ] **LONG PRESS BACK**  
      → Reserved for “Return to Home” (future feature)  

- [ ] **MENU (short press)**  
      → Context actions (Filters, QuickActions, Aspect, etc.)  

- [ ] **MENU (long press)**  
      → **Global Search**  

- [ ] **DPAD**  
      → Normal FocusKit navigation unless overridden  

- [ ] **FF / RW**  
      → Seek or fast scroll depending on screen  

- [ ] **PLAY/PAUSE**  
      → Starts playback **only if focused item is playable**  
      → Movie: resume point  
      → Series: resume correct episode  

---

# 🎬 PLAYER SCREEN

## Context A: Playback Mode (controls hidden)

- [ ] CENTER → **Play/Pause toggle**  
- [ ] PLAY/PAUSE → Play/Pause  
- [ ] DPAD_LEFT/RIGHT → Seek ±10s  
- [ ] DPAD_UP → Open QuickActions  
- [ ] DPAD_DOWN → Reveal controls/timeline  
- [ ] FF → Seek +30s  
- [ ] RW → Seek −30s  
- [ ] MENU → Player options  
- [ ] BACK → Reveal controls → (second BACK) exit player  

✔ **NO Row Fast Scroll in Player**  

---

## Context B: UI Navigation Mode (controls visible)

- [ ] CENTER → Activate focused button  
- [ ] DPAD_LEFT/RIGHT/UP/DOWN → Navigate FocusZones  
- [ ] FF/RW → **Disabled** (Player has no rows)  
- [ ] MENU → QuickActions / Aspect / Settings  
- [ ] BACK → Close UI → return to Playback mode  

---

## Mini-Context: Player + PIP Enabled

- [ ] FF/RW → Seek in PIP  
- [ ] PLAY/PAUSE → Toggle PIP playback  
- [ ] DPAD → Navigate app behind PIP  
- [ ] MENU (long press) → Enter PIP Resize Mode  
- [ ] FF/RW (resize mode) → Resize  
- [ ] DPAD (resize mode) → Move PIP  
- [ ] CENTER (resize mode) → Confirm size/position  

---

# 🏠 HOME / BROWSE / LIBRARY SCREENS

## Standard Browsing

- [ ] CENTER → Open details  
- [ ] DPAD_LEFT/RIGHT → Move tile-by-tile  
- [ ] DPAD_UP/DOWN → Switch rows  
- [ ] FF → Enter **Row Fast Scroll Mode**  
- [ ] RW → Row Fast Scroll backwards  
- [ ] PLAY/PAUSE → Start focused item (resume logic)  
- [ ] MENU (short press) → Filters/Sort  
- [ ] MENU (long press) → Global Search  
- [ ] BACK → Close overlays / go back  

---

## Row Fast Scroll Mode (Fullscreen Row View)

- [ ] **Triggered by FF/RW**  
- [ ] Row expands fullscreen  
- [ ] DPAD_LEFT/RIGHT → slow tile-by-tile  
- [ ] FF/RW → accelerated row scroll  
- [ ] DPAD_UP/DOWN → switch rows  
- [ ] CENTER → open selected item  
- [ ] BACK → exit fast-scroll mode  

---

# 📺 DETAIL SCREEN

- [ ] CENTER → Play/resume  
- [ ] PLAY/PAUSE → Play/resume  
- [ ] FF/RW → Next/previous episode  
- [ ] MENU → Detail actions (Trailer, Add to list, etc.)  
- [ ] DPAD → Navigate episode list, buttons, metadata  

---

# ⚙️ SETTINGS SCREEN

- [ ] CENTER → Activate option  
- [ ] DPAD → Navigate list  
- [ ] BACK → Exit settings  
- [ ] PLAY/PAUSE → no-op  
- [ ] FF/RW → Switch settings tabs (future)  
- [ ] MENU → Advanced Settings (Xtream, Telegram login, etc.)  

---

# 🧒 PROFILE GATE SCREEN

- [ ] CENTER → Select profile  
- [ ] DPAD → Navigate profiles  
- [ ] MENU → Profile options  
- [ ] BACK → Exit app / previous  

---

# 🪟 GLOBAL PIP / MINIPLAYER MODE


## Normal Mode (default)

- [x] FF/RW → Seek ±10s in mini-player  
- [x] PLAY/PAUSE → Toggle playback  
- [x] DPAD → Navigate background app (unless MiniPlayer is focused)  
- [x] Long-press PLAY → Toggle focus between MiniPlayer and background UI  

- [x] MENU (long press) → Enter Resize Mode  

## Resize Mode

- [x] FF/RW → Resize (coarse: ±40dp width, ±22.5dp height)  
- [x] DPAD → Move position (fine: ±20px per press)  

- [x] CENTER/OK → Confirm size/position and exit resize mode  
- [x] BACK → Cancel and revert to previous size/position  

## Visual Feedback (Phase 7 Polish)

- [x] Drop shadow (12dp) and rounded corners (16dp)  
- [x] Translucent control background (40% black overlay)  

- [x] Scale-up (1.03x) in resize mode with primary-colored border  
- [x] Animated size transitions (200ms tween)  
- [x] Slide-in/fade-in when showing, slide-out/fade-out when hiding  

## Snapping Behavior


- [x] 6 snap anchors: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER_TOP, CENTER_BOTTOM  
- [x] Snap to nearest anchor on resize confirm or drag end  
- [x] Center snap threshold: 80dp from horizontal center  
- [x] Safe margins: 16dp from screen edges  


## Touch Gestures (Phones/Tablets only)

- [x] Drag to move (auto-enters resize mode)  
- [x] Snap to nearest anchor on drag end  
- [x] Gestures disabled on TV devices  

## Hints & Discoverability

- [x] First-time hint: "Press Menu to resize and move the mini player" (TV only, auto-dismiss 4s)  
- [x] Resize mode hint: "FF/RW: Size • DPAD: Move • OK: Confirm • Back: Cancel"  
- [x] All hints use string resources (internationalizable)  

---

# 💡 EXTRA PREMIUM FEATURES

- [ ] 1. DPAD long-press acceleration  
- [ ] 2. Smart Skip Intro/Recap (double FF/RW)  
- [ ] 3. Row Jump to Middle/End  
- [ ] 4. Center long press = Global Info Overlay  
- [ ] 5. Live TV: Channel Surf Mode  
- [ ] 6. Quick Settings Panel (MENU + UP)  
- [ ] 7. Library Zoom Mode (row fullscreen w/ metadata)  
- [ ] 8. Adaptive Trickplay Speeds  

- [ ] 9. DPAD_DOWN hold = continuous scrub  
- [x] 10. Global double BACK = Exit to Home  

---

# 🚀 EXIT_TO_HOME BEHAVIOR (Phase 8)


## Double BACK → Exit to Home

- [x] Single BACK: Normal behavior (close overlay, navigate up)
- [x] Double BACK within 500ms: Triggers `EXIT_TO_HOME` action
- [x] Navigation: Navigates to Start/Home route (library)

- [x] Backstack: Clears with `popUpTo` + `launchSingleTop`

## MiniPlayer Behavior on EXIT_TO_HOME

- [x] MiniPlayer **REMAINS VISIBLE** if playback is active
- [x] Playback continues uninterrupted in MiniPlayer
- [x] User can keep watching while at home screen
- [x] No "ghost" player routes on backstack after navigation

## Contract Reference

- INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md Section 5.1
- INTERNAL_PLAYER_PHASE8_CHECKLIST.md Group 3.2
