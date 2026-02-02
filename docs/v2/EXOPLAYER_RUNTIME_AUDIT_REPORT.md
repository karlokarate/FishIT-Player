# ExoPlayer Runtime Audit Report

**Date:** 2025-01-31  
**Branch:** `architecture/v2-bootstrap`  
**Author:** Copilot Agent

---

## 1. Executive Summary

This document provides a comprehensive audit of the current ExoPlayer implementation in FishIT-Player v2, comparing it against:
1. The battle-tested v1 legacy implementation
2. Native ExoPlayer capabilities
3. Best-in-class market implementations (VLC, MX Player, Just Player)

### 🔴 Critical Issues Found

| Issue | Severity | Status |
|-------|----------|--------|
| **Screen Rotation Breaks Playback** | 🔴 CRITICAL | Missing `configChanges` in AndroidManifest |
| **No Resize Mode UI** (FIT/FILL/ZOOM) | 🟠 HIGH | Missing entirely in v2 |
| **No Audio Track Selection UI** | 🟠 HIGH | Backend exists, no UI |
| **No Subtitle Track Selection UI** | 🟠 HIGH | Backend exists, no UI |
| **No Playback Speed Control UI** | 🟡 MEDIUM | Backend exists, no UI |
| **No PiP (Picture-in-Picture)** | 🟡 MEDIUM | Not implemented in v2 |
| **No MiniPlayer** | 🟡 MEDIUM | Not integrated in v2 |

---

## 2. Current v2 Implementation Status

### 2.1. What Works ✅

| Feature | Location | Status |
|---------|----------|--------|
| Basic playback (HTTP streams) | `player/internal` | ✅ Works |
| Play/Pause controls | `InternalPlayerControls.kt` | ✅ Works |
| Seek ±10s buttons | `InternalPlayerControls.kt` | ✅ Works |
| Progress slider (VOD) | `InternalPlayerControls.kt` | ✅ Works |
| Mute toggle | `InternalPlayerControls.kt` | ✅ Works |
| Live indicator (pulsing dot) | `InternalPlayerControls.kt` | ✅ Works |
| TV focus support (DPAD) | `FocusableIconButton` | ✅ Works |
| Buffering indicator | `PlayerSurface.kt` | ✅ Works |
| Error display | `PlayerSurface.kt` | ✅ Works |
| Subtitle track discovery | `SubtitleTrackManager.kt` | ✅ Backend works |
| Audio track discovery | `AudioTrackManager.kt` | ✅ Backend works |
| NextLib codecs (FFmpeg) | `NextlibCodecConfigurator` | ✅ Works |
| Telegram DataSource | `playback/telegram` | ✅ Works |
| Xtream DataSource | `playback/xtream` | ✅ Works |

### 2.2. What's Missing ❌

| Feature | v1 Status | v2 Status | Priority |
|---------|-----------|-----------|----------|
| **configChanges in Manifest** | ✅ Has it | ❌ MISSING | 🔴 CRITICAL |
| **AspectRatioMode (FIT/FILL/ZOOM)** | ✅ Full UI | ❌ No UI | 🟠 HIGH |
| **Subtitle Selection UI** | ✅ Full menu | ❌ No UI | 🟠 HIGH |
| **Audio Track Selection UI** | ✅ Full menu | ❌ No UI | 🟠 HIGH |
| **Playback Speed Control** | ✅ UI button | ❌ No UI | 🟡 MEDIUM |
| **PiP Support** | ✅ Phone only | ❌ Not implemented | 🟡 MEDIUM |
| **MiniPlayer Overlay** | ✅ Works | ❌ Not integrated | 🟡 MEDIUM |
| **Rotation Lock Setting** | ✅ Works | ❌ Not implemented | 🟡 MEDIUM |
| **CC/Subtitle Styling** | ✅ Scale/Color | ❌ Not implemented | 🟢 LOW |
| **Trickplay Gestures** | ✅ Swipe seek | ❌ Not implemented | 🟢 LOW |
| **Live TV Channel Zapping** | ✅ Swipe | ❌ Not implemented | 🟢 LOW |

---

## 3. Critical Issue #1: Screen Rotation

### 3.1. Problem

**User Report:** "Stream stops on screen rotation"

**Root Cause Analysis:**

The v2 AndroidManifest (`app-v2/src/main/AndroidManifest.xml`) is missing the `android:configChanges` attribute on MainActivity:

```xml
<!-- CURRENT (v2) - BROKEN -->
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:theme="@style/Theme.FishITPlayerV2">
```

Compare to v1 which correctly handles config changes:

```xml
<!-- LEGACY (v1) - CORRECT -->
<activity
    android:name=".MainActivity"
    android:configChanges="keyboardHidden|orientation|screenSize|smallestScreenSize|screenLayout|uiMode">
```

### 3.2. Impact

Without `configChanges`, Android will:
1. **Destroy** the Activity on rotation
2. **Recreate** the Activity from scratch
3. **Lose** all player state (position, playback, buffers)
4. **Restart** the stream from the beginning (or fail)

### 3.3. Fix Required

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:configChanges="keyboardHidden|orientation|screenSize|smallestScreenSize|screenLayout|uiMode"
    android:theme="@style/Theme.FishITPlayerV2">
```

### 3.4. Phase 8 Rotation Resilience (v1 Reference)

The v1 implementation has extensive Phase 8 work for rotation resilience:

1. **PlaybackSession Singleton:** Survives config changes
2. **ExoPlayer Instance:** Never recreated on rotation
3. **State Preservation:** Position, aspect ratio, tracks all preserved
4. **UI Rebinding:** PlayerView re-attaches to existing player
5. **Tests:** `RotationResilienceTest.kt` validates behavior

**v2 has NONE of this yet.** The `InternalPlayerSession` is created fresh each time.

---

## 4. Missing UI Controls

### 4.1. Aspect Ratio / Resize Mode

**What v1 has:**
- FIT, FILL, ZOOM modes
- Button in controls to cycle through modes
- State persists across rotation
- `AspectRatioFrameLayout.RESIZE_MODE_*` integration

**v2 Status:**
- `InternalPlayerState` has NO `aspectRatioMode` field
- `PlayerSurface` does NOT apply resize mode to PlayerView
- No UI button to change modes

**Implementation Needed:**
```kotlin
// InternalPlayerState.kt
data class InternalPlayerState(
    // ... existing fields
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
)

enum class AspectRatioMode {
    FIT, FILL, ZOOM;
    
    fun next(): AspectRatioMode = when (this) {
        FIT -> FILL
        FILL -> ZOOM
        ZOOM -> FIT
    }
    
    fun toResizeMode(): Int = when (this) {
        FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }
}
```

### 4.2. Audio Track Selection

**Backend Status:**
- `AudioTrackManager.kt` exists and works (399 lines)
- Discovers tracks, maps to `AudioTrack` models
- `selectTrack(AudioTrackId)` API available
- State exposed via `StateFlow<AudioSelectionState>`

**Missing:**
- No UI picker/dialog to show available tracks
- No button in `InternalPlayerControls` to open picker
- User cannot change audio language

### 4.3. Subtitle Track Selection

**Backend Status:**
- `SubtitleTrackManager.kt` exists and works (312 lines)
- Discovers tracks, maps to `SubtitleTrack` models
- `selectTrack(SubtitleTrackId)` and `disableSubtitles()` APIs
- State exposed via `StateFlow<SubtitleSelectionState>`
- Kid mode integration (disables subtitles in kid profiles)

**Missing:**
- No UI picker/dialog to show available tracks
- No button in `InternalPlayerControls` to open picker
- User cannot enable/disable/change subtitles

### 4.4. Playback Speed Control

**Backend Status:**
- `InternalPlayerSession` can set speed via `player.setPlaybackSpeed()`
- `InternalPlayerState.playbackSpeed` field exists

**Missing:**
- No UI button/selector to change speed
- No speed options (0.5x, 0.75x, 1x, 1.25x, 1.5x, 2x)

---

## 5. Native ExoPlayer Features Not Utilized

### 5.1. Features We SHOULD Enable

| Feature | ExoPlayer API | Status |
|---------|---------------|--------|
| **Adaptive Bitrate (ABR)** | `DefaultTrackSelector` | ✅ Using |
| **HDR Support** | Automatic for HLG/PQ | ❓ Untested |
| **Dolby Vision** | Requires NextLib | ❓ Needs verification |
| **Surface Type Selection** | `SurfaceView` vs `TextureView` | ⚠️ Using default |
| **Decoder Priority** | Software vs Hardware | ⚠️ NextLib configures |
| **Tunneled Playback** | For Android TV | ❌ Not enabled |
| **Audio Offload** | Battery saving | ❌ Not enabled |
| **Media3 MediaSession** | For system integration | ❌ Not implemented |
| **SeekParameters** | EXACT vs CLOSEST_SYNC | ⚠️ Default used |

### 5.2. Tunneled Playback (Android TV)

For Android TV, tunneled playback provides:
- Lower latency
- Better A/V sync
- Reduced battery usage

```kotlin
// Enable tunneled playback
DefaultTrackSelector(context).apply {
    parameters = parameters.buildUpon()
        .setTunnelingEnabled(true)
        .build()
}
```

### 5.3. Audio Offload

For background audio playback:
```kotlin
ExoPlayer.Builder(context)
    .setAudioOffloadPreferences(
        AudioOffloadPreferences.DEFAULT
            .buildUpon()
            .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
            .build()
    )
    .build()
```

---

## 6. Comparison with Market Leaders

### 6.1. VLC for Android

**Features FishIT Should Adopt:**
- ✅ All aspect ratios (Fit, Fill, Center, 16:9, 4:3, etc.)
- ✅ Audio delay adjustment
- ✅ Subtitle delay adjustment
- ✅ Gesture controls (brightness/volume swipe)
- ✅ Lock screen controls
- ✅ Background audio playback
- ✅ Hardware acceleration toggle
- ✅ Audio boost (volume beyond 100%)

### 6.2. MX Player

**Features FishIT Should Adopt:**
- ✅ SW/HW decoder selection per-video
- ✅ Multi-core decoding
- ✅ Network stream (with subtitle download)
- ✅ Zoom with pinch gesture
- ✅ Kids lock (prevent touch)
- ✅ Headphone unplug pause

### 6.3. Just (Video) Player

**Features FishIT Should Adopt:**
- ✅ Modern Material 3 UI
- ✅ Swipe gestures for seek/brightness/volume
- ✅ Picture-in-Picture
- ✅ Audio focus handling
- ✅ Playback speed (0.25x - 4x)
- ✅ Skip intro/outro buttons

---

## 7. Recommended Implementation Roadmap

### Phase 1: Critical Fixes (Immediate)

1. **🔴 Fix Rotation** - Add `configChanges` to AndroidManifest
2. **🔴 Singleton Session** - Implement `PlaybackSession` for player reuse

### Phase 2: Essential Controls (1-2 weeks)

1. **Aspect Ratio Button** - FIT/FILL/ZOOM cycling
2. **Audio Track Picker** - Bottom sheet with available tracks
3. **Subtitle Track Picker** - Bottom sheet with available tracks + OFF
4. **Playback Speed Selector** - 0.5x, 0.75x, 1x, 1.25x, 1.5x, 2x

### Phase 3: Quality Improvements (2-4 weeks)

1. **Tunneled Playback** - Enable for Android TV
2. **MediaSession Integration** - For notification controls
3. **PiP Support** - Phone/tablet only
4. **MiniPlayer** - In-app overlay

### Phase 4: Advanced Features (Future)

1. **Gesture Controls** - Brightness/volume swipe
2. **Subtitle Styling** - Scale, color, background
3. **Audio Delay** - ±5 seconds adjustment
4. **Network Stats Overlay** - Debug mode

---

## 8. Files Requiring Changes

### 8.1. AndroidManifest (CRITICAL)

**File:** `app-v2/src/main/AndroidManifest.xml`

```diff
 <activity
     android:name=".MainActivity"
     android:exported="true"
+    android:configChanges="keyboardHidden|orientation|screenSize|smallestScreenSize|screenLayout|uiMode"
     android:theme="@style/Theme.FishITPlayerV2">
```

### 8.2. InternalPlayerState (AspectRatio)

**File:** `player/internal/src/main/java/com/fishit/player/internal/state/InternalPlayerState.kt`

Add:
- `aspectRatioMode: AspectRatioMode` field
- `AspectRatioMode` enum with `next()` and `toResizeMode()`

### 8.3. InternalPlayerControls (New Buttons)

**File:** `player/internal/src/main/java/com/fishit/player/internal/ui/InternalPlayerControls.kt`

Add:
- Aspect ratio button (top-right)
- Audio track button (bottom bar)
- Subtitle button (bottom bar)
- Speed button (bottom bar)

### 8.4. PlayerSurface (Apply Resize Mode)

**File:** `player/internal/src/main/java/com/fishit/player/internal/ui/PlayerSurface.kt`

Change PlayerView creation to apply resize mode:
```kotlin
AndroidView(
    factory = { ctx ->
        PlayerView(ctx).apply {
            useController = false
            resizeMode = state.aspectRatioMode.toResizeMode()
        }
    },
    update = { playerView ->
        playerView.resizeMode = state.aspectRatioMode.toResizeMode()
    },
)
```

### 8.5. New Files Needed

| File | Purpose |
|------|---------|
| `TrackSelectionSheet.kt` | Bottom sheet for audio/subtitle selection |
| `SpeedSelectionDialog.kt` | Dialog for playback speed |
| `PlaybackSession.kt` | Singleton for player lifecycle (v2) |
| `AspectRatioMode.kt` | Enum in `core/player-model` |

---

## 9. Quality Improvement Recommendations

### 9.1. Video Quality

1. **Enable Tunneled Playback** on Android TV
2. **Add HDR Support** detection and toggle
3. **Implement Adaptive Resolution** for mobile data

### 9.2. Audio Quality

1. **Enable Audio Offload** for battery savings
2. **Add Audio Boost** option (max 200%)
3. **Implement Spatial Audio** detection

### 9.3. Stream Stability

1. **Add Retry Logic** with exponential backoff
2. **Implement Buffer Monitoring** UI (debug mode)
3. **Add Network Speed Indicator**

---

## 10. Conclusion

The v2 player has a solid foundation but is missing critical features that were fully implemented in v1 (Phase 3-8). The immediate priority is:

1. **Fix rotation** (manifest change)
2. **Port PlaybackSession** from v1 for player lifecycle
3. **Add UI controls** for track selection and aspect ratio

The backend for track selection already exists; only UI is needed.

---

## Appendix A: v1 Phase Reference

| Phase | Focus | v2 Status |
|-------|-------|-----------|
| Phase 1 | Basic ExoPlayer setup | ✅ Done |
| Phase 2 | Source resolution | ✅ Done |
| Phase 3 | Live TV support | ✅ Partial |
| Phase 4 | Subtitles | ✅ Backend only |
| Phase 5 | Aspect Ratio | ❌ Not done |
| Phase 6 | Audio Tracks | ✅ Backend only |
| Phase 7 | PlaybackSession | ❌ Not done |
| Phase 8 | Rotation/Lifecycle | ❌ Not done |

---

## Appendix B: Test Commands

```bash
# Build and test player module
./gradlew :player:internal:testDebugUnitTest

# Check for rotation handling
grep -rn "configChanges" app-v2/

# Find aspect ratio code
grep -rn "RESIZE_MODE\|AspectRatio" player/
```
