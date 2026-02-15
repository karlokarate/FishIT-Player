# INTERNAL PLAYER CONTRACT (Consolidated)

**Consolidated from phase-specific contracts on 2026-02-15**

> This document merges the former INTERNAL_PLAYER_BEHAVIOR_CONTRACT (Resume/Kids),
> Phase 4 (Subtitles/CC), Phase 5 (Player Surface), Phase 6 (TV Input),
> Phase 7 (Playback Session), and Phase 8 (Performance/Lifecycle).

---

# Part 1: Resume & Kids Mode Behavior


Version: 1.0  
Scope: Resume behavior & kids/screen-time enforcement for FishIT Player’s internal player (legacy + SIP).  
Status: Drafted during Phase 3 (shadow mode). This document is the **functional specification**; legacy behavior is informative, not authoritative.

---

## 1. Design Goals

1. Provide a **clear, testable contract** for resume and kids/screen-time behavior.
2. Allow the **modular SIP implementation** to intentionally fix legacy bugs without “locking in” incorrect legacy behavior.
3. Enable **shadow-mode comparison** (legacy vs SIP) to classify differences as:
   - **Spec-compliant SIP vs legacy bug**
   - **Spec violation in SIP**
   - **Irrelevant / tolerated divergence**
4. Keep behavior intuitive and aligned with common video UX and parental-control patterns.

This contract applies to:
- `DefaultResumeManager`
- `DefaultKidsPlaybackGate`
- `Phase2Integration`
- Shadow/diagnostic tools that interpret resume/kids behavior.

Legacy `InternalPlayerScreen` is treated as a reference implementation, not as the source of truth.

---

## 2. Terminology

- **VOD**: Movie or single, non-episodic video.
- **Series episode**: Video that belongs to a series (seriesId + season + episodeNumber).
- **LIVE**: Live TV channel or stream.
- **Position**: Current playback position in milliseconds.
- **Duration**: Known media duration in milliseconds.
- **Remaining time**: `remainingMs = max(0, durationMs - positionMs)`.
- **Resume entry**: Persisted data describing where to continue playback.

---

## 3. Resume Behavior Contract

### 3.1. Global Rules

1. Resume behavior is defined **only** for:
   - VOD
   - Series episodes
2. LIVE content **never** resumes from a saved position.
3. Resume logic must be **idempotent** and **safe**:
   - No crashes on missing data, negative duration, or malformed IDs.
   - Failing repositories must result in "no resume", not in a crash.

> **Note (LIVE Playback & Contract Enforcer):**
> LIVE playback is excluded from resume rules and is not affected by Contract Enforcer
> beyond diagnostics. The `LivePlaybackController` handles channel navigation, EPG overlay,
> and auto-hide timing, but does NOT integrate with `ResumeManager` or `BehaviorContractEnforcer`.
> Shadow diagnostics may observe LIVE sessions for validation purposes without affecting runtime behavior.

### 3.2. Identification Rules

#### 3.2.1. VOD

- VOD resume entries are keyed by:
  - `mediaId: Long`
- A VOD resume entry must be independent of URL, provider, or chat origin.

#### 3.2.2. Series Episodes

- Series episodes may be identified by:
  - Preferred key: `(seriesId: Int, season: Int?, episodeNumber: Int?)`
  - Fallback key: `episodeId: Int?` (for legacy sources)
- If both composite key and episodeId are available, composite key should be used as **primary**, episodeId as **migration fallback**.

### 3.3. Resume Load (On Start)

When a playback session is created, `DefaultResumeManager.loadResumePositionMs(playbackContext)` is used to load a resume point.

**Contract:**

1. If no entry exists → return `null` (no resume).
2. If the stored position is **less than or equal to 10 seconds** → return `null` (treat as “never started”).
3. If the stored position is **greater than 10 seconds** and **less than duration - nearEndThreshold** (see 3.4) → return the stored position.
4. If a stored position exists, but the duration is unknown or implausible:
   - Use the stored position if it is > 10 seconds and < some upper bound (e.g. 24h).
   - If position is implausible (e.g. > 48h), return `null` and log a diagnostic.

This ensures that trivial “zaps” do not create resume entries.

### 3.4. Near-End Behavior

To avoid resuming at a point where the content is practically finished, define:

- `nearEndThresholdSeconds = 10`
- `nearEndThresholdMs = 10_000`

**Contract:**

- If `remainingMs <= nearEndThresholdMs` at the time the media is considered ended or last saved:
  - The resume entry must be **cleared**.
  - Future sessions must **start from 0**.

This mirrors common media player behavior where files are considered “watched” near the end, but uses a **fixed time threshold** instead of percentages for determinism.

### 3.5. Periodic Tick (During Playback)

The player periodically calls:  
`DefaultResumeManager.handlePeriodicTick(playbackContext, positionMs, durationMs)`.

**Contract:**

1. The caller is responsible for invoking this method approximately every **3 seconds** for VOD/Series playback.
2. `handlePeriodicTick` must:
   - Ignore calls with invalid data (negative duration, position > duration).
   - Store the current position if:
     - `positionMs > 10_000` (10 seconds)
     - and `remainingMs > nearEndThresholdMs`
   - If `remainingMs <= nearEndThresholdMs`:
     - Clear any existing resume entry.
3. LIVE playback:
   - `handlePeriodicTick` must be a **no-op** (except for optional diagnostics).

### 3.6. End of Playback

When playback reaches a true `STATE_ENDED` event, `handleEnded(playbackContext)` is invoked.

**Contract:**

- For VOD and Series:
  - If the final position satisfies `remainingMs <= nearEndThresholdMs` → clear resume entry.
- For LIVE:
  - No resume entry should exist, but if one does (due to bugs), it must be cleared.

This guarantees that finishing a video does not leave a stale resume marker.

---

## 4. Kids / Screen-Time Contract

### 4.1. Goals

1. Provide a simple, predictable **daily screen-time quota** per kid profile.
2. Ensure the logic is **defensible** and aligns with typical parental control patterns.
3. Make failure behavior **explicit** (error vs fail-open vs fail-closed).

### 4.2. Identification

- Kids logic is only active when:
  - A current profile is set, and
  - That profile is marked as a “kid” profile.
- A profile is considered a “kid” if:
  - `profile.type == "kid"` (or equivalent semantics in ObxProfile).

### 4.3. Daily Quota

- Each kid profile has a **remaining daily quota** in minutes.
- The source of truth is typically a persistent store (e.g. `ScreenTimeRepository`).

**Contract:**

1. Quota is decremented **only** when:
   - Playback is active (VOD, Series, or LIVE), and
   - A kid profile is active.
2. Quota is not decremented when:
   - Playback is paused for a “long enough” period (exact definition may be handled by caller and spec’d later).
3. If the remaining quota is `<= 0`:
   - The kid state must be considered **blocked**.

### 4.4. Tick Mechanics

`DefaultKidsPlaybackGate.onPlaybackTick(currentState, deltaSecs)` is called periodically.

**Contract:**

1. `deltaSecs` is the amount of time since the last tick and should normally be **3 seconds**.
2. The gate implementation must accumulate seconds internally and only deduct from the daily quota when it reaches (or exceeds) **60 seconds**.
   - Example:
     - Accumulate: `accumulator += deltaSecs`
     - If `accumulator >= 60`:
       - Decrement quota by 1 minute.
       - Subtract 60 from accumulator (carry over remainder).
3. When the last remaining minute is consumed (quota transitions from 1 → 0):
   - The state must transition to “blocked”.
4. If the quota is already `<= 0` at tick time:
   - The state remains blocked.

### 4.5. Start Evaluation

`DefaultKidsPlaybackGate.evaluateStart()` is called when playback begins (or when profile changes).

**Contract:**

1. It must:

   - Evaluate whether a current profile is a kid.
   - Fetch the remaining daily quota for that profile.
   - Return a state that indicates:
     - `kidActive: Boolean`
     - `kidBlocked: Boolean`
     - `remainingMinutes: Int?`

2. If any error occurs during evaluation (repository failures, I/O, etc.):
   - The implementation must **not crash**.
   - Fail-open vs fail-closed behavior must be selectable:
     - Default: **fail-open** (allow playback, but report diagnostics).
     - Future: A config flag may allow a stricter fail-closed mode.

This avoids bricking playback due to temporary data issues.

### 4.6. Blocked State Semantics

When kids’ gate decides the user is blocked:

1. `kidBlocked == true` must remain true until:
   - The current playback session stops, or
   - A profile change occurs, or
   - The quota is explicitly reset (new day, manual override).
2. It is the **responsibility of the caller** (player UI/session) to:
   - Pause playback as soon as `kidBlocked` becomes true.
   - Surface an appropriate UI (overlay, dialog, navigation) to inform the user.
3. `DefaultKidsPlaybackGate` must not attempt to control the player directly.
   - It is a **pure policy component**.

This matches the separation of concerns: KidsGate decides “allowed vs blocked”, the session/UI decides “what to do when blocked”.

---

## 5. Spec vs Legacy vs SIP – Classification

Because the legacy `InternalPlayerScreen` may contain historical bugs or inconsistent behavior, this contract uses a classification model for differences.

For any observed behavior (legacy vs SIP):

1. **Spec-compliant SIP**  
   - SIP behavior matches this contract.  
   - Legacy may differ.  
   - Classification: `SpecPreferredSIP`.  
   - Action: Keep SIP behavior. Document legacy behavior as a historical bug.

2. **Spec violation in SIP**  
   - SIP does not follow the contract.  
   - Legacy may or may not be correct.  
   - Classification: `SpecPreferredLegacy` or `SpecViolation`.  
   - Action: Fix SIP implementation to match the spec (or adjust spec if spec is wrong).

3. **Both match the spec**  
   - Classification: `ExactMatch`.  
   - Action: No change needed.

4. **Spec says “don’t care” or “tolerated variance”**  
   - For example: minor drift in reported remaining minutes, minor time offset within an allowed tolerance window.  
   - Classification: `DontCare`.  
   - Action: No change required; difference is accepted.

Shadow-mode tooling (e.g. `ShadowComparisonService`) should use this classification model when reporting mismatches, so that known legacy bugs can be explicitly documented and SIP behavior can intentionally improve on legacy.

---

## 6. Testing Requirements

### 6.1. ResumeManager Tests

Tests must cover at least:

- No resume when stored position `<= 10s`.
- Resume when stored position `> 10s` and not near end.
- Clear resume when remaining time `< 10s` (both on tick and on end).
- VOD vs Series ID mapping (mediaId vs composite key vs episodeId fallback).
- Robustness against invalid data (negative duration, position > duration).

### 6.2. KidsPlaybackGate Tests

Tests must cover at least:

- Kid profile detection vs adult profile.
- 60-second accumulation logic with various delta patterns (3s, 5s, etc.).
- Transition from allowed → blocked when quota hits 0.
- Behavior when repositories fail (errors): fail-open by default, with correct diagnostics.
- Block state persistence until end-of-session or profile change.

### 6.3. Integration / Shadow Tests

- Combined behavior with `Phase2Integration` must demonstrate that SIP can adhere to this contract in isolation.
- Differences against legacy must be logged and classified, not blindly treated as errors.

---

## 7. Evolution and Ownership

- This contract is the **living source of truth** for resume and kids behavior.
- Any change in behavior must be reflected here **before** being implemented in SIP or legacy code.
- Legacy behavior may diverge from this specification; such divergences must be:
  - Documented
  - Classified
  - Eventually removed when the modular player becomes the active implementation.

Once the modular InternalPlayerScreen becomes the runtime default, this contract governs actual user-facing behavior.

---

# Part 2: Subtitle & CC (Phase 4)


Version: 1.0  
Scope: Subtitle track selection, subtitle styling, CC/Subtitle UI, and central subtitle management **exclusively for the modular SIP Internal Player**.  
Legacy InternalPlayerScreen remains untouched and serves *only* as a behavioral reference during the refactor.

---

## 1. Design Goals

1. **Centralized Subtitle/CC Logic**  
   All subtitle behavior—track selection, styling, preview, and persistence—must flow through a single domain module (`SubtitleStyleManager` + `SubtitleSelectionPolicy`).

2. **Clear Rule Set (Contract-Driven)**  
   Legacy behavior is *not authoritative*. The contract defines how subtitles must behave in the SIP Player.

3. **Kid Mode Priority**  
   Subtitles and CC UI must be completely disabled for kid profiles.

4. **Modern, Clean, TV-Optimized UX**  
   Subtitle styling must look contemporary, readable, and intuitive—especially for TV users.

5. **Robust, Predictable Behavior**  
   Subtitle handling must never crash, flicker, or mismatch tracks/styles.

---

## 2. Terminology

- **Subtitle Track**: Any Media3 text track (caption/subtitle).
- **SubtitleStyle**: Data model for visual subtitle styling.
- **SubtitleStyleManager**: Domain controller for subtitle style persistence and updates.
- **SubtitleSelectionPolicy**: Logic for choosing a subtitle track at playback start.
- **CC Menu**: In-player UI to adjust subtitle tracks and style.
- **SIP Path**: Modular Internal Player pipeline under refactor.
- **Legacy Player**: Old InternalPlayerScreen. NOT modified. Used only as behavioral reference.
- **Kid Mode**: Profile type that disables subtitles completely.

---

## 3. Global Rules (SIP-Only)

### 3.1 Kid Mode – Hard Restriction
If the active profile is marked as kid:

- No subtitles are rendered.
- No subtitle track is selected.
- No CC/Subtitle button is shown in the player.
- No subtitle settings are interactable in the SettingsScreen.
- SubtitleStyleManager still stores styles, but they are never applied.

### 3.2 SIP Is the Only Target for Phase 4
- This contract governs **only** the modular SIP implementation.  
- The legacy InternalPlayerScreen **must not** be modified or extended.  
- ShadowDiagnostics may still observe legacy behavior but must not enforce it.

---

## 4. SubtitleStyle (Domain Model)

### 4.1 Required Fields

```kotlin
data class SubtitleStyle(
    val textScale: Float,         // 0.5f..2.0f
    val foregroundColor: Int,     // ARGB
    val backgroundColor: Int,     // ARGB
    val foregroundOpacity: Float, // 0f..1f
    val backgroundOpacity: Float, // 0f..1f
    val edgeStyle: EdgeStyle      // NONE, OUTLINE, SHADOW, GLOW
)
```

### 4.2 Defaults
- textScale = 1.0  
- fgColor = White (100% opacity)  
- bgColor = Black (~60% opacity)  
- edgeStyle = Outline

### 4.3 Allowed Ranges
- textScale: 0.5–2.0  
- foregroundOpacity: 0.5–1.0  
- backgroundOpacity: 0.0–1.0  

---

## 5. SubtitleStyleManager (Domain)

### 5.1 Interface Definition

```kotlin
interface SubtitleStyleManager {
    val currentStyle: StateFlow<SubtitleStyle>
    val currentPreset: StateFlow<SubtitlePreset>

    suspend fun updateStyle(style: SubtitleStyle)
    suspend fun applyPreset(preset: SubtitlePreset)
    suspend fun resetToDefault()
}
```

### 5.2 Contract Rules
- Styles persist **per profile**, not globally.
- Updates must propagate immediately through StateFlow.
- Both the SIP Player and SettingsScreen use the same manager instance.
- In Kid Mode, the manager’s values remain valid but ignored.

---

## 6. SubtitleSelectionPolicy

### 6.1 Inputs
- List of available Media3 subtitle tracks.
- Preferred languages (profile setting).
- System captioning preferences (if accessible).
- Content type (VOD, SERIES, LIVE).

### 6.2 Selection Rules
1. If Kid Mode → always “no subtitles”.
2. Preferred order:
   - system language  
   - primary profile language  
   - secondary profile language  
   - track with `default` flag  
   - fallback: first usable track if “Always show subtitles” is enabled  
   - otherwise: no subtitles  
3. User manual selection → becomes new preference.

### 6.3 Persistence
- Per-profile subtitle language preference.
- Optional: separate preferences for VOD vs LIVE.

---

## 7. Player Integration (SIP Path)

### 7.1 Applying SubtitleStyle
At playback time, SIP Player must map SubtitleStyle to:

- `CaptionStyleCompat`
- `subtitleView.setFractionalTextSize(textScale)`
- background & foreground ARGB with opacity applied
- edgeStyle mapping to available outline/shadow/glow settings

### 7.2 Live Style Updates
When SubtitleStyleManager emits a new style:

- UI updates preview immediately.
- Player applies style on next rendering pass.

### 7.3 Error Handling
- If no tracks are available:
  - CC button may be hidden OR show “No subtitles available”.
- If style application fails:
  - revert to default style safely.
- Never crash.

---

## 8. CC / Subtitle UI (SIP Only)

### 8.1 CC Button Visibility
- Visible only for non-kid profiles.
- Visible only if at least one subtitle track exists.

### 8.2 Radial CC Menu (TV/DPAD Primary UI)
A modern radial menu with segments:
- **Track/Language**
- **Text Size**
- **Color**
- **Background**
- **Opacity**
- **Presets (Default, High Contrast, TV Large, Minimal)**

### 8.3 DPAD Behavior
- Left/Right = navigate segments  
- Up/Down = change option inside segment  
- Center/OK = apply selection  
- Back = cancel without changes

### 8.4 Touch UI Variant
On phones/tablets: BottomSheet with identical controls.

### 8.5 Live Preview
- Subtitle preview immediately reflects pending style changes.
- Preview does not affect active playback until applied.

---

## 9. SettingsScreen Integration

### 9.1 Global Subtitle Settings (per profile)
- SubtitlePreset selection
- Text scale slider
- FG/BG color pickers
- FG/BG opacity sliders
- EdgeStyle picker
- Reset-to-default

### 9.2 Preview Box
Always show a small preview label identical to the SIP preview.

### 9.3 Kid Mode Behavior
- Settings are hidden or read-only.
- SubtitleStyleManager still stores data, but SIP Player ignores subtitles.

---

## 10. Additional Advanced Behaviors (Phase 4 Optional)

### 10.1 Adaptive Subtitle Mode
Automatically adjust FG/BG contrast based on scene brightness.

### 10.2 Quick-Action Toggles
- “TV Large”
- “High Contrast”
- “Cinema Mode”

### 10.3 Info Panel Preparation
Introduce a non-visual `SubtitleInfoState` for later expansion.

---

## 11. Testing Requirements

### 11.1 SubtitleStyleManager Tests
- All ranges validated  
- Presets apply correct settings  
- Persistence per profile  
- Reset-to-default restores baseline

### 11.2 SubtitleSelectionPolicy Tests
- Track selection priority  
- No subtitles when Kid Mode  
- Manual override persistence  
- VOD vs LIVE preferences

### 11.3 CC Menu UI Tests (SIP Only)
- CC button visibility rules  
- Radial navigation correctness  
- Preview accuracy  
- Cancel/Applying behavior  

### 11.4 Integration Tests
- Style updates propagate correctly to subtitleView  
- Player never crashes on invalid data  
- SettingsScreen ↔ SIP Player synchronization

---

## 12. Evolution & Ownership

- Any future subtitle behavior changes must update this contract first.
- SIP Player is the only implementation target.
- Legacy player stays unmodified and will eventually be removed.

---

# Part 3: Player Surface & Interaction (Phase 5)


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

---

# Part 4: TV Input & Focus (Phase 6)

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

---

# Part 5: Playback Session & MiniPlayer (Phase 7)

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

---

# Part 6: Performance & Lifecycle (Phase 8)


INTERNAL PLAYER – PHASE 8 CONTRACT

Performance, Lifecycle & Stability for Unified PlaybackSession + MiniPlayer

Applies To: SIP Player, MiniPlayer, Global TV Input, Background Workers

Legacy InternalPlayerScreen: Reference-Only (MUST remain untouched)


---

1. Purpose

Phase 8 ensures that the unified PlaybackSession + In-App MiniPlayer behave robustly and efficiently under real-world conditions:

App background/foreground

Rotation & configuration changes

Process death / Activity recreation

Background workers (Xtream, Telegram, DB, EPG)

Memory pressure & resource reuse


The goal is to make playback stutter-free, leak-free, and resilient,
while keeping performance predictable and testbar.


---

2. Non-Goals

Phase 8 does not:

Introduce new user-facing features (no new buttons, no new flows)

Change the Phase 7 UX semantics (MiniPlayer, Resize, Focus Toggle etc.)

Extend TV input or FocusKit beyond what Phase 6/7 defined

Replace or modify the legacy InternalPlayerScreen


Phase 8 is strictly about stability, performance and lifecycle hygiene.


---

3. Core Principles

1. One Playback Session per app

No second ExoPlayer / second player instance for SIP or MiniPlayer.



2. Warm Resume, Cold Start only when necessary

Lifecycle events dürfen PlaybackSession/ExoPlayer nicht unnötig neu bauen.



3. Playback-Aware Resource Usage

Worker/Network/DB/CPU-Intensive Tasks drosseln, wenn Playback aktiv ist.



4. No UI Jank

Keine Blockierungen auf dem Main-Thread, keine unnötigen Recomposition-Bursts.



5. Full Observability

Fehler und Performance-Symptome sind test- und debugbar, nicht „magisch“.





---

4. PlaybackSession Lifecycle Contract

4.1 Ownership & Lifetime

PlaybackSession hält die einzige ExoPlayer-Instanz für die SIP-Welt:

Full Player

MiniPlayer

Optional: System PiP Binding


PlaybackSession darf nicht direkt von Activity/Fragment/View gebaut oder destroyed werden.


4.2 Lifecycle States

PlaybackSession must maintain an internal state machine, z. B.:

enum class SessionLifecycleState {
    IDLE,        // no media loaded
    PREPARED,    // media loaded, ready to play
    PLAYING,     // actively playing
    PAUSED,      // paused but retained
    BACKGROUND,  // app in background, still allowed to play
    STOPPED,     // playback stopped, resources mostly freed
    RELEASED     // ExoPlayer released, session not usable
}

4.3 Lifecycle Rules

onResume() (App foreground)

If SessionLifecycleState in {PREPARED, PLAYING, PAUSED, BACKGROUND}:

Re-bind UI surfaces (PlayerSurface, MiniPlayerOverlay)

Do not recreate ExoPlayer



onPause()

If playing video and device allows background audio:

Optional: keep playing (BACKGROUND)


Else:

Pause playback; stay in PREPARED or PAUSED



onStop()

Should not immediately release ExoPlayer

Session goes to BACKGROUND only when:

No UI is bound

Playback is still active (e.g. audio)



onDestroy()

Only release ExoPlayer when:

No route wants the session anymore (no full player, no mini, no PiP)

SessionLifecycleState == STOPPED




4.4 Rotation / Configuration Changes

Rotation / configuration change (e.g. locale, UI mode) MUST NOT:

Reset playback position

Reset aspect ratio

Reset subtitle/audio track selection


UI components must re-bind to the existing PlaybackSession, not recreate it.



---

5. Navigation & Backstack Stability

5.1 Full ↔ Mini ↔ Home

Full → Mini

PIP Button (UI) → MiniPlayerManager.enterMiniPlayer(fromRoute, mediaId, indices)

SIP Player screen removed (popBackStack)

PlaybackSession unchanged


Mini → Full

Expand/Maximize → navigate to full player route derived from returnRoute/mediaId

MiniPlayerState.visible = false


EXIT_TO_HOME (Double BACK)

From any screen:

Single BACK: normal overlay/stack navigation

Double BACK within threshold: TvAction.EXIT_TO_HOME → Start/Home route

MiniPlayer: bleibt sichtbar, wenn Playback läuft (je nach Contract).




5.2 Process Death & State Rehydration

Auf Process-Death muss der Player (optional) so rehydratisierbar sein, dass:

Zuletzt geschaute MediaId + Position persistiert sind

Optional: Nutzer erhält „Continue watching from X:XX?“


PlaybackSession darf nie „half-initialized“ übrig bleiben (keine Zombie-Player).



---

6. System PiP vs In-App MiniPlayer

6.1 In-App MiniPlayer

Vollständig in Phase 7 definiert.

NICHT mit enterPictureInPictureMode() gekoppelt.

Wird nur durch Actions in deiner UI/TV Input Pipeline gesteuert.


6.2 System PiP (Phone/Tablet Only)

May be triggered by:

Home button

Recents

OS events (auto-enter PiP for video activities)


Phase-8 Zusatzregeln:


1. System PiP darf nie vom PIP-Button im UI ausgelöst werden.


2. System PiP darf nicht aktiviert werden, wenn:

MiniPlayer sichtbar ist

Kids Mode diese Funktion verhindern soll (optional)



3. Beim Rückkehren aus System PiP:

PlaybackSession muss re-ge-bound werden (Full oder Mini)

Position, Tracks, Aspect bleiben erhalten





---

7. Background Workers & Playback Priority

7.1 PlaybackPriority

Introduce:

object PlaybackPriority {
    val isPlaybackActive: StateFlow<Boolean> // derived from PlaybackSession.isPlaying && SessionLifecycleState
}

7.2 Worker Behavior

Xtream, Telegram, EPG, DB-Prozesse, Log-Upload:

Wenn isPlaybackActive == true:

Worker müssen:

ihre Task-Rate drosseln

keine langen CPU/IO-Bursts erzeugen


Praktisches Beispiel:

delay(BACKGROUND_THROTTLE_MS) zwischen heavy network calls

keine großvolumigen DB-Migrationen während Playback





7.3 Threading & Main-Thread Rules

DB & Netzwerk-Arbeit immer in Dispatchers.IO / geeigneten Worker-Scope.

Kein Netzwerk/IO-Aufruf im Compose-Composable selbst.

Kein schwerer Code in onDraw, drawWithContent, graphicsLayer.



---

8. Memory & Resource Management

8.1 ExoPlayer

ExoPlayer darf nur durch PlaybackSession erstellt und released werden.

Activity/Composables dürfen nie ExoPlayer.Builder aufrufen.


8.2 Leak-Schutz

In Debug-Builds:

LeakCanary aktivieren (oder vergleichbare Lösung)

PlaybackSession + MiniPlayer + FocusKit-Row/Zone intensiv beobachten


Keine static-Refs auf Activity/Context/etc. aus Session/Manager-Schichten.


8.3 Cache & Bitmaps

Bild-Loading (Cover/Thumbnails/Posters) nur über:

AsyncImage/Coil/Glide mit Cache


Keine manuellen Bitmaps im UI, wenn vermeidbar.

Player-intern: CacheDataSource für HTTP/Xtream, um Re-Requests zu vermeiden.



---

9. Compose & FocusKit Performance

9.1 Recomposition Hygiene

InternalPlayerUiState darf nicht alles in einem Objekt packen, wenn nur Teile sich ändern.

Hot-Paths (Progress, isPlaying, Buffering) in separate, kleine Composables, die kaum Layout kosten.


9.2 FocusKit

Focus-Effekte (tvFocusFrame, tvClickable, tvFocusGlow) so konsolidieren, dass:

maximal eine graphicsLayer + drawWithContent Kette pro UI-Element läuft

unnötige doppelte Effekte vermieden werden



9.3 MiniPlayerOverlay

Animationen kurz, state-driven, testfreundlich (abschaltbar in Tests).

Keine übermäßige Layout-Sprünge beim Ein-/Ausblenden.



---

10. Error Handling & Recovery

10.1 Netz- / Streaming-Fehler

PlaybackSession muss Fehler signalisieren via:

error: StateFlow<PlayerError?>


UI (Full Player/MiniPlayer) muss:

den Fehler „soft“ anzeigen (Overlay, Message)

nicht hart crashen


Optional:

Auto-Retry bei transienten Fehlern (Connection Reset, Timeout)

Logging an Diagnostics / TV Input Inspector



10.2 Worker-Fehler

Worker dürfen niemals die PlaybackSession killen.

Schwere Fehler (z. B. DB defekt) müssen:

UI-kompatibel (Fehlermeldung),

aber Playback-bewusst sein (z. B. erst nach Ende der Session anzeigen).




---

11. Quality-Tooling & Test Requirements

11.1 Tools

Detekt:

Max Complexity pro Funktion ≤ 10

Keine „God-Classes“ im Session/Manager-Bereich


Ktlint:

Einheitlicher Stil – wichtig für Review/Langlebigkeit


Android Lint:

Lifecycle, Threading, Context-Leaks, PiP-Warnings ernst nehmen


LeakCanary (Debug):

Fokus auf PlaybackSession/MiniPlayer



11.2 Tests

Unit-Tests:

PlaybackSessionLifecycleTest:

onPause/onStop/onResume Simulation

keine unnötige Player-Recreation


MiniPlayerLifecycleTest:

Full↔Mini↔Home mit Lifecycle-Wechseln


WorkerThrottleTest:

Worker-Rate reduziert bei isPlaybackActive == true



Integration-/Robolectric-Tests:

App in Background/Foreground mit laufendem Playback (TV & Phone-Konfiguration)

Rotation mit laufendem Player + MiniPlayer

System PiP (Phone): Home → PiP → zurück zur App


Regression-Tests:

Prüfen, dass Phasen 4–7-Verhalten unverändert bleibt:

Subtitles/CC

PlayerSurface & Aspect Ratio

TV Input & Focus (inkl. EXIT_TO_HOME, MiniPlayer-Inputs)

Live TV/EPG




---

12. Ownership

PlaybackSessionController:

alleiniger Owner von ExoPlayer + PlaybackLifecycle


MiniPlayerManager:

alleiniger Owner von MiniPlayerState


TvInputController:

Owner der Input-Routing-Logik, aber NICHT des Lifecycle


FocusKit:

Owner aller Fokus-/Zonen-Entscheidungen


Workers:

an PlaybackPriority gebunden


UI-Screens (Library, Player, Settings, ProfileGate, Start):

reine Konsumenten dieser Systeme, ohne eigene Lifecycle-/Playback-Logik




---

13. Evolution / Amendments

Alle Änderungen an:

PlaybackSession-Lifecycle

MiniPlayer-Lifecycle

System PiP Verhalten

Worker/Playback-Interaktion


müssen zuerst diesen Contract aktualisieren und danach in:

INTERNAL_PLAYER_PHASE8_CHECKLIST.md

INTERNAL_PLAYER_REFACTOR_ROADMAP.md


übernommen werden, bevor Implementierung erfolgt.


---

Filename:
docs/INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md