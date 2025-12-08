# INTERNAL PLAYER SUBTITLE & CC CONTRACT – PHASE 4 (SIP-ONLY)

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
