# INTERNAL PLAYER – PLAYER SURFACE & INTERACTION CONTRACT (PHASE 5, SIP-ONLY)

**Version:** 1.0  
**Scope:** Visual rendering, aspect ratio behavior, black-bar handling, trickplay and controls auto-hide for the SIP Internal Player.  
**Status:** Authoritative contract for Phase 5 implementation. Legacy InternalPlayerScreen is informative only, not a source of truth.

---

## 1. Design Goals

1. **Correct, standard-compliant video framing**  
   All non-video areas (letterboxing/pillarboxing) must be black, never white or transparent.

2. **Predictable aspect ratio behavior**  
   Clearly defined modes (FIT / FILL / ZOOM) with intuitive, TV-first semantics.

3. **Clean PlayerSurface abstraction**  
   Single composable responsible for video rendering, gestures and basic visual overlays (not domain logic).

4. **Modern trickplay behavior**  
   Fast-forward/rewind that feels responsive and understandable.

5. **Non-annoying auto-hide behavior**  
   Controls hide themselves when appropriate, without surprising the user.

6. **SIP-only refactor**  
   This contract governs only the modular SIP pipeline.  
   Legacy InternalPlayerScreen is not to be modified, only used as a historical reference when needed.

---

## 2. Terminology

- **PlayerSurface:** SIP composable that hosts the video rendering surface (PlayerView via AndroidView) plus gesture interactions.

- **Aspect ratio mode:**
  - **FIT** – show the entire video, maintain aspect ratio, may produce black bars.
  - **FILL** – fill the viewport, may crop content.
  - **ZOOM** – like FILL, but tuned for aggressive crop (optional).

- **Black bars:** areas outside the active video frame, must be visually black.

- **Controls:** on-screen UI (transport, CC button, info overlays).

- **Trickplay:** continuous fast-forward/rewind mode, including visual feedback (speed, target position).

- **SIP path:** new modular Internal Player path under refactor.

- **Legacy player:** old InternalPlayerScreen, not to be changed in Phase 5.

---

## 3. Global Rules (SIP-only)

### 3.1 Black Bars Must Be Black

1. Any region where no video pixels are rendered (letterbox, pillarbox, before first frame, after end) must appear as pure/near-pure black.

2. No white or gray areas are allowed as background in the video region.

3. This includes:
   - the PlayerView background,
   - the composable container background (Boxes),
   - any "shutter" state before/after playback.

### 3.2 Legacy is not authoritative

1. The legacy player's behavior on aspect ratio, trickplay, and background is not a specification.

2. If legacy behavior contradicts this contract, SIP implementation is allowed (and expected) to differ.

3. Shadow or debug comparison may log differences, but cannot force SIP to mimic legacy bugs.

### 3.3 Separation of concerns

1. **PlayerSurface owns:**
   - video surface,
   - aspect ratio & resize decisions,
   - background color / black bars,
   - gestures for play/pause, seek, trickplay,
   - control auto-hide timers (in cooperation with the controls layer).

2. **It does not own:**
   - domain logic (resume, kids, live controller, subtitles),
   - persistence,
   - navigation.

   Those are handled by other modules and passed in via state/callbacks.

---

## 4. Aspect Ratio & Black Bars Contract

### 4.1 Aspect Ratio Modes

The SIP player must support at least:

- `AspectRatioMode.FIT` – default
- `AspectRatioMode.FILL`
- `AspectRatioMode.ZOOM` (optional but recommended)

**FIT:**
- Entire video fits inside the available viewport.
- Aspect ratio is preserved.
- Any extra horizontal or vertical space is filled with black.

**FILL:**
- Video fills the viewport entirely.
- Aspect ratio is preserved; parts of the video may be cropped at edges.
- No background is visible (no bars).

**ZOOM:**
- More aggressive crop than FILL (used to simulate "zoom into content").
- Like FILL but may assume a bit more crop is acceptable.
- Still no non-black background visible.

### 4.2 Implementation Rules

1. PlayerView (or its parent container) must have `Color.Black` as background.

2. Shutter/background color for PlayerView must be explicitly set to black:
   ```kotlin
   playerView.setShutterBackgroundColor(Color.BLACK)
   ```

3. Any Compose container around `AndroidView(PlayerView)` must explicitly set `.background(Color.Black)` and not rely on theme defaults.

4. When the video is not yet rendered (initial buffering, no frame):
   - viewport must still appear black, not white.

5. When the user changes aspect ratio mode:
   - resize behavior changes, but the non-video background remains black in all modes.

---

## 5. PlayerSurface Contract

### 5.1 Responsibilities

1. Host the PlayerView via AndroidView.

2. Apply the current aspect ratio mode (e.g. `resizeMode`) and background color.

3. Handle user gestures:
   - single tap
   - long press (optional)
   - horizontal swipe
   - vertical swipe (if defined)

4. Drive control visibility / auto-hide:
   - respond to user activity by showing/hiding controls.

5. Provide hooks for:
   - trickplay start/stop,
   - scrubbing gestures,
   - live channel jumping (for LIVE content, if applicable).

### 5.2 Visibility & Layout

1. PlayerSurface must fill the available space in the Internal Player layout.

2. Overlays (controls, EPG, subtitles, CC menu overlays) are drawn above the video, without breaking aspect ratio behavior.

3. **Subtitles:**
   - are handled by Subtitle/CC subsystem, but are visually aligned with the video frame area (not with the black bars/background).

---

## 6. Trickplay Contract

### 6.1 Modes

- **Normal playback** – no trickplay active.
- **Trickplay** – e.g. 2x/4x/8x fast forward or rewind, or keyframe skipping.

### 6.2 Behavior Rules

1. **Entering trickplay:**
   - Must be intentional: e.g. long-press right/left, repeated press, or dedicated button.
   - UI should clearly indicate trickplay is active (icon/speed label).

2. **While in trickplay:**
   - The player should:
     - either use ExoPlayer's internal `setPlaybackSpeed`,
     - or jump by consistent seek steps (e.g. ±10s, ±30s) with clear visual feedback.
   - Controls must not auto-hide while the user is actively in trickplay adjustment.

3. **Exiting trickplay:**
   - On explicit user action (play/pause, confirm, or timeout).
   - Playback returns to normal speed.
   - Tracked position must be consistent: the player resumes at the expected point.

4. **Trickplay vs aspect ratio:**
   - Trickplay must not change aspect ratio mode.
   - Background remains black even during fast seeking.

---

## 7. Auto-Hide Contract

### 7.1 General

1. Controls (transport bar, buttons) must auto-hide after a period of user inactivity.

2. Timing and behavior may differ between TV and handheld:
   - **TV:** longer timeout (e.g. 5–7 seconds)
   - **Phone/Tablet:** shorter timeout (e.g. 3–5 seconds)

### 7.2 What counts as "activity"

1. Any of:
   - DPAD navigation,
   - touch tap/swipe,
   - trickplay adjustments,
   - CC menu open/close,
   - live EPG or menus interaction.

2. Activity must:
   - reset the auto-hide timer,
   - keep controls visible as long as the user is interacting.

### 7.3 Never hide controls when:

1. Critical overlays/dialogs are open:
   - CC menu,
   - settings bottom sheet,
   - confirmation dialogs.

2. Kid Mode is blocking playback with an overlay.

### 7.4 Implementation Rules

1. Auto-hide logic lives in the SIP UI layer (PlayerSurface + controls), not in the legacy screen.

2. Time-based logic must be deterministic and testable (e.g. using a `TimeProvider` or injection), not `System.currentTimeMillis()` hard-coded everywhere.

3. Tests must verify:
   - controls visible on user interaction,
   - hide triggers after correct timeout,
   - no hide when overlays are active.

---

## 8. TV Remote & Gestures

### 8.1 TV Remote (DPAD)

1. DPAD must be supported:
   - **Center:** toggle controls and/or play/pause.
   - **Left/Right:** seek or step trickplay.
   - **Up/Down:** open quick actions, live list, or other overlays (subject to existing TV input controller in later phases).

2. PlayerSurface must cooperate with a TV input controller rather than implement all mapping inline (Phase 6 may move this further).

### 8.2 Touch Gestures

1. **Single tap:**
   - toggles controls visibility (show/hide).

2. **Double tap (optional):**
   - may seek ±10s (like common mobile players).

3. **Horizontal swipe:**
   - may control trickplay or precise seek (depending on design).

4. **Vertical swipe:**
   - may open quick actions or live list (for LIVE).

These gestures must not change background behavior (black bars remain black at all times).

---

## 9. Error Handling & Edge Cases

1. If PlayerView fails to initialize:
   - fallback to a black screen with a UI error message (no white background).

2. If aspect ratio mode is unknown:
   - default to FIT.

3. If the device is rotated / resized:
   - PlayerSurface recomputes layout; black background remains consistent.

---

## 10. Testing Requirements

### 10.1 Visual Tests (Behavioral)

- Play a video with:
  - aspect ratio narrower than device → vertical bars are black.
  - aspect ratio wider than device → horizontal bars are black.

- Switch between FIT/FILL/ZOOM:
  - no white or gray space visible.

- Pause at various positions:
  - background remains black when frame is not covering entire area.

### 10.2 Trickplay Tests

- Enter/exit trickplay from multiple input patterns.
- Confirm playback resumes at the correct position.
- Ensure aspect ratio and background do not change with trickplay.

### 10.3 Auto-Hide Tests

- Simulate inactivity:
  - controls hide after configured timeout.

- Simulate activity (DPAD/touch):
  - controls stay visible or reappear.

- Ensure controls never hide while CC menu or other dialogs are open.

### 10.4 Kid Mode Interaction

- Ensure Kid Mode does not break PlayerSurface:
  - background stays black,
  - no subtle changes to aspect ratio.

---

## 11. Evolution & Ownership

1. Any change to aspect ratio, black-bar behavior, trickplay or auto-hide must first update this contract before implementation.

2. SIP PlayerSurface is the source of truth. Legacy behavior may be observed but not enforced.

3. Phase 5 implementation should:
   - actively fix legacy issues (e.g. white bars),
   - avoid copying snapshot logic from the legacy screen without evaluating it against this contract.
