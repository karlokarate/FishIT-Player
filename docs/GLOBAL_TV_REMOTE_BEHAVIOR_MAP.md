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

- [ ] FF/RW → Seek in mini-player  
- [ ] PLAY/PAUSE → Toggle playback  
- [ ] DPAD → Navigate background app  
- [ ] MENU (long press) → Enter Resize Mode  
- [ ] FF/RW → Resize  
- [ ] DPAD → Move  
- [ ] CENTER → Confirm  

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
- [ ] 10. Global double BACK = Exit to Home  
