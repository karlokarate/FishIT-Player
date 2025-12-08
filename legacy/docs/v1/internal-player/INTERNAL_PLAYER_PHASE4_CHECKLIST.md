> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

# Internal Player Phase 4 Checklist – Subtitle & CC Centralization

**Version:** 1.0  
**Scope:** SIP-only subtitle style management, CC menu, and subtitle track selection  
**Contract Reference:** [INTERNAL_PLAYER_SUBTITLE_CC_CONTRACT_PHASE4.md](INTERNAL_PLAYER_SUBTITLE_CC_CONTRACT_PHASE4.md)

---

## Overview

Phase 4 implements centralized subtitle styling, CC menu controls, and subtitle track selection for the modular SIP Internal Player. All work is SIP-only and does not modify the legacy `InternalPlayerScreen.kt`.

**Key Principles:**
1. **SIP-Only**: No changes to legacy `InternalPlayerScreen.kt`
2. **Contract-Driven**: Behavior defined by `INTERNAL_PLAYER_SUBTITLE_CC_CONTRACT_PHASE4.md`
3. **Kid Mode First**: Subtitles completely disabled for kid profiles
4. **Centralized**: All subtitle logic flows through domain modules

---

## Task Group 1: SubtitleStyle Domain Model & Manager

**Goal:** Create domain model and centralized manager for subtitle styling

### Task 1.1: SubtitleStyle Data Model ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/player/internal/subtitles/SubtitleStyle.kt`

**Implementation:**
```kotlin
data class SubtitleStyle(
    val textScale: Float = 1.0f,           // 0.5f..2.0f
    val foregroundColor: Int = 0xFFFFFFFF.toInt(),  // ARGB
    val backgroundColor: Int = 0x99000000.toInt(),  // ARGB
    val foregroundOpacity: Float = 1.0f,   // 0f..1f
    val backgroundOpacity: Float = 0.6f,   // 0f..1f
    val edgeStyle: EdgeStyle = EdgeStyle.OUTLINE
)

enum class EdgeStyle {
    NONE,
    OUTLINE,
    SHADOW,
    GLOW
}
```

**Legacy Reference:**
- Legacy L208-212: `subScale`, `subFg`, `subBg`, `subFgOpacity`, `subBgOpacity`
- Legacy L1748-1766: `subtitleView` configuration with `CaptionStyleCompat`

**Tests Required:**
- Default values match contract specification
- Range validation (textScale, opacity)
- Data class copy operations
- EdgeStyle enum values

---

### Task 1.2: SubtitlePreset Enum ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/player/internal/subtitles/SubtitlePreset.kt`

**Implementation:**
```kotlin
enum class SubtitlePreset {
    DEFAULT,
    HIGH_CONTRAST,
    TV_LARGE,
    MINIMAL;

    fun toStyle(): SubtitleStyle = when (this) {
        DEFAULT -> SubtitleStyle()
        HIGH_CONTRAST -> SubtitleStyle(
            foregroundColor = 0xFFFFFF00.toInt(),  // Yellow
            backgroundColor = 0xFF000000.toInt(),   // Black
            foregroundOpacity = 1.0f,
            backgroundOpacity = 1.0f
        )
        TV_LARGE -> SubtitleStyle(
            textScale = 1.5f,
            edgeStyle = EdgeStyle.OUTLINE
        )
        MINIMAL -> SubtitleStyle(
            textScale = 0.8f,
            backgroundOpacity = 0.3f
        )
    }
}
```

**Legacy Reference:**
- Legacy L2374-2382: Quick preset buttons (Klein, Standard, Groß)
- Contract Section 8.2: Preset requirements

**Tests Required:**
- All presets produce valid SubtitleStyle
- Preset to style conversion
- Enum completeness

---

### Task 1.3: SubtitleStyleManager Interface ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/player/internal/subtitles/SubtitleStyleManager.kt`

**Implementation:**
```kotlin
interface SubtitleStyleManager {
    val currentStyle: StateFlow<SubtitleStyle>
    val currentPreset: StateFlow<SubtitlePreset>

    suspend fun updateStyle(style: SubtitleStyle)
    suspend fun applyPreset(preset: SubtitlePreset)
    suspend fun resetToDefault()
}
```

**Contract Reference:** Section 5

**Tests Required:**
- Interface contract validation
- StateFlow behavior
- Thread safety

---

### Task 1.4: DefaultSubtitleStyleManager Implementation ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/player/internal/subtitles/DefaultSubtitleStyleManager.kt`

**Implementation:**
- Uses `SettingsStore` for persistence (DataStore)
- Reads from keys: `SUB_SCALE`, `SUB_FG`, `SUB_BG`, `SUB_FG_OPACITY_PCT`, `SUB_BG_OPACITY_PCT`
- Writes updates to DataStore
- Emits changes through StateFlow
- Per-profile persistence (uses `currentProfileId`)

**Legacy Reference:**
- Legacy L207-212: SettingsStore subtitle flows
- `SettingsStore.kt` L207-211: Subtitle persistence keys

**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/prefs/SettingsStore.kt` - Add write methods if missing

**Tests Required:**
- Persistence to DataStore
- StateFlow emission on updates
- Per-profile isolation
- Reset to defaults
- Concurrent access safety

---

## Task Group 2: SubtitleSelectionPolicy

**Goal:** Centralize subtitle track selection logic

### Task 2.1: SubtitleSelectionPolicy Interface ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/player/internal/subtitles/SubtitleSelectionPolicy.kt`

**Implementation:**
```kotlin
data class SubtitleTrack(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String?,
    val label: String,
    val isDefault: Boolean
)

interface SubtitleSelectionPolicy {
    fun selectInitialTrack(
        availableTracks: List<SubtitleTrack>,
        preferredLanguages: List<String>,
        playbackType: PlaybackType,
        isKidMode: Boolean
    ): SubtitleTrack?

    fun persistSelection(
        track: SubtitleTrack?,
        playbackType: PlaybackType
    )
}
```

**Contract Reference:** Section 6

**Tests Required:**
- Kid mode always returns null
- Preferred language matching
- Default flag handling
- Persistence

---

### Task 2.2: DefaultSubtitleSelectionPolicy Implementation ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/player/internal/subtitles/DefaultSubtitleSelectionPolicy.kt`

**Implementation:**
- Kid mode: Always return null (Contract Section 3.1)
- Priority order (Contract Section 6.2):
  1. System language
  2. Primary profile language
  3. Secondary profile language
  4. Track with default flag
  5. First track if "always show subtitles" enabled
  6. Otherwise null
- Separate preferences for VOD/LIVE if configured

**Legacy Reference:**
- Legacy L1284-1304: `refreshSubtitleOptions()` - track enumeration logic
- Legacy L2304-2340: Track selection via `TrackSelectionOverride`

**Tests Required:**
- Kid mode blocking
- Language priority order
- Default flag handling
- VOD vs LIVE preferences
- Null handling

---

## Task Group 3: Player Integration (SIP Session)

**Goal:** Wire subtitle style and track selection into SIP session

### Task 3.1: Apply SubtitleStyle to PlayerView ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/session/InternalPlayerSession.kt`
- `app/src/main/java/com/chris/m3usuite/player/internal/state/InternalPlayerState.kt`

**Implementation:**
1. **Extend InternalPlayerUiState:**
   ```kotlin
   data class InternalPlayerUiState(
       // ... existing fields ...
       val subtitleStyle: SubtitleStyle = SubtitleStyle(),
       val selectedSubtitleTrack: SubtitleTrack? = null,
   )
   ```

2. **In InternalPlayerSession:**
   - Instantiate `DefaultSubtitleStyleManager` using `SettingsStore`
   - Collect `currentStyle` StateFlow
   - Update `InternalPlayerUiState.subtitleStyle` when style changes
   - Pass `subtitleStyle` to PlayerView configuration

3. **PlayerView Configuration:**
   - Apply via `subtitleView?.apply { ... }`
   - `setFractionalTextSize(style.textScale)`
   - `setApplyEmbeddedStyles(true)`
   - `setApplyEmbeddedFontSizes(true)`
   - Map `SubtitleStyle` to `CaptionStyleCompat`
   - Apply foreground/background colors with opacity

**Legacy Reference:**
- Legacy L1748-1766: PlayerView subtitle configuration
- Legacy L2476-2484: `withOpacity()` helper function

**Tests Required:**
- Style updates propagate to UiState
- CaptionStyleCompat mapping
- Opacity calculation
- EdgeStyle mapping
- Kid mode: Style stored but not applied

---

### Task 3.2: Subtitle Track Selection Integration ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/session/InternalPlayerSession.kt`

**Implementation:**
1. Instantiate `DefaultSubtitleSelectionPolicy`
2. On `Player.Listener.onTracksChanged`:
   - Enumerate available subtitle tracks from `exoPlayer.currentTracks`
   - Call `policy.selectInitialTrack()` with kid mode status
   - Apply track selection via `TrackSelectionOverride`
   - Update `InternalPlayerUiState.selectedSubtitleTrack`
3. Kid mode: Skip all track selection
4. Manual selection: Call `policy.persistSelection()`

**Legacy Reference:**
- Legacy L1284-1304: Track enumeration from `exoPlayer.currentTracks`
- Legacy L2304-2312: Track selection via `TrackSelectionOverride`

**Tests Required:**
- Initial track selection
- Kid mode blocking
- Manual selection persistence
- Track change handling
- Null track handling

---

## Task Group 4: CC Menu UI (SIP InternalPlayerControls)

**Goal:** Create modern CC menu for subtitle controls

### Task 4.1: CC Button in InternalPlayerControls ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/InternalPlayerControls.kt`

**Implementation:**
- Add CC button to control bar
- Visibility rules (Contract Section 8.1):
  - Visible only for non-kid profiles
  - Visible only if at least one subtitle track exists
- Opens CC menu on click
- Uses Material Icons: `Icons.Default.Subtitles` or similar

**Legacy Reference:**
- Legacy L2194-2210: CC button in overlay controls
- Legacy L2253-2267: CC button in quick actions

**Tests Required:**
- Visibility rules (kid mode, track availability)
- Button click handler
- Focus handling

---

### Task 4.2: CcMenuDialog Composable ⬜
**Files to Create:**
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/CcMenuDialog.kt`

**Implementation:**
- Full-screen dialog with segments:
  1. **Track/Language Selection** (subtitle tracks)
  2. **Text Size** (slider 0.5–2.0)
  3. **Foreground Color** (color picker)
  4. **Background Color** (color picker)
  5. **Foreground Opacity** (slider 0.5–1.0)
  6. **Background Opacity** (slider 0.0–1.0)
  7. **Edge Style** (NONE, OUTLINE, SHADOW, GLOW)
  8. **Presets** (DEFAULT, HIGH_CONTRAST, TV_LARGE, MINIMAL)

- DPAD behavior (Contract Section 8.3):
  - Left/Right: Navigate segments
  - Up/Down: Change option within segment
  - Center/OK: Apply selection
  - Back: Cancel without changes

- Touch UI: BottomSheet with identical controls (Contract Section 8.4)

**Legacy Reference:**
- Legacy L2290-2390: CC menu dialog structure
- Legacy L2347-2370: Scale and opacity sliders
- Legacy L2374-2382: Preset buttons

**Tests Required:**
- DPAD navigation
- Segment switching
- Option selection
- Apply/cancel behavior
- Touch UI variant

---

### Task 4.3: Live Preview in CC Menu ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/CcMenuDialog.kt`

**Implementation:**
- Preview label at top of dialog
- Shows "Example Subtitle Text"
- Reflects pending style changes immediately
- Does not affect active playback until applied
- Uses same rendering as player subtitle view

**Contract Reference:** Section 8.5

**Tests Required:**
- Preview updates on style change
- Preview isolation from playback
- Preview text rendering

---

## Task Group 5: SettingsScreen Integration

**Goal:** Add global subtitle settings to SettingsScreen

### Task 5.1: Subtitle Settings Section ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/ui/screens/SettingsScreen.kt`

**Implementation:**
- Add "Subtitles" settings category
- Controls:
  1. **Preset Selection** (dropdown)
  2. **Text Scale** (slider with preview)
  3. **Foreground Color** (color picker)
  4. **Background Color** (color picker)
  5. **Foreground Opacity** (slider)
  6. **Background Opacity** (slider)
  7. **Edge Style** (dropdown)
  8. **Reset to Default** (button)

- Kid Mode Behavior (Contract Section 9.3):
  - Settings hidden or read-only for kid profiles
  - SubtitleStyleManager still stores data

**Legacy Reference:**
- No existing SettingsScreen subtitle section (new feature)
- Legacy subtitle settings only in player CC menu

**Tests Required:**
- Setting updates persist
- Preview box updates
- Kid mode read-only behavior
- Reset to defaults

---

### Task 5.2: Subtitle Preview Box ⬜
**Files to Modify:**
- `app/src/main/java/com/chris/m3usuite/ui/screens/SettingsScreen.kt`

**Implementation:**
- Small preview label in settings
- Shows "Example Text"
- Matches SIP player subtitle rendering
- Updates in real-time as settings change

**Contract Reference:** Section 9.2

**Tests Required:**
- Preview matches player rendering
- Real-time updates
- Style accuracy

---

## Task Group 6: Testing & Validation

**Goal:** Comprehensive test coverage for all Phase 4 modules

### Task 6.1: SubtitleStyleManager Tests ⬜
**Files to Create:**
- `app/src/test/java/com/chris/m3usuite/player/internal/subtitles/SubtitleStyleManagerTest.kt`

**Coverage:**
- All ranges validated (textScale, opacity)
- Presets apply correct settings
- Persistence per profile
- Reset-to-default restores baseline
- Thread safety
- StateFlow emission

---

### Task 6.2: SubtitleSelectionPolicy Tests ⬜
**Files to Create:**
- `app/src/test/java/com/chris/m3usuite/player/internal/subtitles/SubtitleSelectionPolicyTest.kt`

**Coverage:**
- Track selection priority
- No subtitles when Kid Mode
- Manual override persistence
- VOD vs LIVE preferences
- Language matching
- Default flag handling

---

### Task 6.3: CC Menu UI Tests ⬜
**Files to Create:**
- `app/src/test/java/com/chris/m3usuite/player/internal/ui/CcMenuDialogTest.kt`

**Coverage:**
- CC button visibility rules
- DPAD navigation correctness
- Preview accuracy
- Cancel/Apply behavior
- Touch UI variant

---

### Task 6.4: Integration Tests ⬜
**Files to Create:**
- `app/src/test/java/com/chris/m3usuite/player/internal/session/InternalPlayerSessionPhase4SubtitleTest.kt`

**Coverage:**
- Style updates propagate to subtitleView
- Player never crashes on invalid data
- SettingsScreen ↔ SIP Player synchronization
- Kid mode blocking
- Track selection integration

---

## Summary: Files Overview

### New Files to Create (SIP Only)
| File Path | Purpose |
|-----------|---------|
| `internal/subtitles/SubtitleStyle.kt` | Domain model for subtitle styling |
| `internal/subtitles/SubtitlePreset.kt` | Preset enum |
| `internal/subtitles/SubtitleStyleManager.kt` | Interface for style management |
| `internal/subtitles/DefaultSubtitleStyleManager.kt` | Implementation with DataStore |
| `internal/subtitles/SubtitleSelectionPolicy.kt` | Interface for track selection |
| `internal/subtitles/DefaultSubtitleSelectionPolicy.kt` | Implementation with language priority |
| `internal/ui/CcMenuDialog.kt` | CC menu composable |
| `test/.../subtitles/SubtitleStyleManagerTest.kt` | Manager tests |
| `test/.../subtitles/SubtitleSelectionPolicyTest.kt` | Selection tests |
| `test/.../ui/CcMenuDialogTest.kt` | UI tests |
| `test/.../session/InternalPlayerSessionPhase4SubtitleTest.kt` | Integration tests |

### Files to Modify (SIP Only)
| File Path | Changes |
|-----------|---------|
| `internal/state/InternalPlayerState.kt` | Add `subtitleStyle`, `selectedSubtitleTrack` fields |
| `internal/session/InternalPlayerSession.kt` | Wire manager, apply style, handle selection |
| `internal/ui/InternalPlayerControls.kt` | Add CC button |
| `ui/screens/SettingsScreen.kt` | Add subtitle settings section |
| `prefs/SettingsStore.kt` | Add write methods if missing |

### Files NOT Modified (Legacy)
- ❌ `player/InternalPlayerScreen.kt` - **UNTOUCHED** (legacy remains active)

---

## Behavior Mapping: Legacy vs SIP

| Legacy Code Location | Behavior | SIP Module | Status |
|---------------------|----------|------------|--------|
| L208-212 | Subtitle style preferences | `DefaultSubtitleStyleManager` | ⬜ |
| L1258-1266 | Effective style helpers | `SubtitleStyle` data model | ⬜ |
| L1284-1304 | Track enumeration | `SubtitleSelectionPolicy` | ⬜ |
| L1748-1766 | PlayerView subtitle config | `InternalPlayerSession` | ⬜ |
| L2194-2210, L2253-2267 | CC button | `InternalPlayerControls` | ⬜ |
| L2290-2390 | CC menu dialog | `CcMenuDialog` | ⬜ |
| L2304-2312, L2328-2339 | Track selection | `SubtitleSelectionPolicy` | ⬜ |
| L2476-2484 | `withOpacity()` helper | Style application logic | ⬜ |

---

## Contract Compliance Summary

| Contract Section | Requirement | SIP Implementation |
|-----------------|-------------|-------------------|
| 3.1 | Kid Mode: No subtitles rendered | `SubtitleSelectionPolicy` blocks |
| 3.1 | Kid Mode: No subtitle track selected | Policy returns null |
| 3.1 | Kid Mode: No CC button shown | CC button visibility check |
| 3.1 | Kid Mode: Settings hidden/read-only | SettingsScreen conditional |
| 4.1 | SubtitleStyle fields | `SubtitleStyle.kt` data class |
| 4.2 | Default values | Data class defaults |
| 5.1 | SubtitleStyleManager interface | Interface definition |
| 5.2 | Per-profile persistence | Uses `currentProfileId` |
| 6.2 | Track selection priority | Policy implementation |
| 7.1 | Apply to PlayerView | Session integration |
| 8.1 | CC button visibility | UI conditional logic |
| 8.2 | Radial CC menu | `CcMenuDialog` |
| 8.3 | DPAD behavior | Navigation logic |
| 9.1 | SettingsScreen controls | Settings integration |
| 9.2 | Preview box | Preview composable |

---

## Phase 4 Completion Criteria

- [ ] All Task Groups 1-6 complete
- [ ] All tests passing (unit + integration)
- [ ] SIP subtitle style working in isolation
- [ ] CC menu functional with DPAD
- [ ] SettingsScreen integration complete
- [ ] Kid mode completely blocks subtitles
- [ ] No changes to legacy `InternalPlayerScreen.kt`
- [ ] Documentation updated (Roadmap, Status)

---

**Last Updated:** 2025-11-26
