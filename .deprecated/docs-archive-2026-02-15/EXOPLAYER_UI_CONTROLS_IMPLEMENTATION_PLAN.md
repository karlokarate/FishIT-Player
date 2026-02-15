# ExoPlayer UI Controls Implementation Plan

**Date:** 2025-01-31  
**Status:** PLANNING  
**Priority:** 🟠 HIGH

---

## Overview

This document outlines the implementation plan for missing player UI controls identified in the [ExoPlayer Runtime Audit Report](EXOPLAYER_RUNTIME_AUDIT_REPORT.md).

---

## 1. Aspect Ratio Mode (FIT/FILL/ZOOM)

### 1.1. New Enum: `AspectRatioMode`

**Location:** `core/player-model/src/main/java/com/fishit/player/core/playermodel/AspectRatioMode.kt`

```kotlin
package com.fishit.player.core.playermodel

import androidx.annotation.Keep
import androidx.media3.ui.AspectRatioFrameLayout

/**
 * Video aspect ratio display modes.
 *
 * Maps to Media3 [AspectRatioFrameLayout] resize modes.
 */
@Keep
enum class AspectRatioMode {
    /** Fit video within bounds, preserving aspect ratio (letterbox/pillarbox) */
    FIT,
    
    /** Fill bounds, preserving aspect ratio (may crop) */
    FILL,
    
    /** Zoom to fill, cropping as needed */
    ZOOM;
    
    /** Cycle to next mode: FIT → FILL → ZOOM → FIT */
    fun next(): AspectRatioMode = when (this) {
        FIT -> FILL
        FILL -> ZOOM
        ZOOM -> FIT
    }
    
    /** Convert to Media3 resize mode constant */
    fun toResizeMode(): Int = when (this) {
        FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }
    
    /** Display name for UI */
    val displayName: String
        get() = when (this) {
            FIT -> "Fit"
            FILL -> "Fill"
            ZOOM -> "Zoom"
        }
    
    /** Icon resource for UI */
    val iconRes: Int
        get() = when (this) {
            FIT -> R.drawable.ic_aspect_ratio_fit
            FILL -> R.drawable.ic_aspect_ratio_fill
            ZOOM -> R.drawable.ic_aspect_ratio_zoom
        }
}
```

### 1.2. State Update: `InternalPlayerState`

**File:** `player/internal/src/main/java/com/fishit/player/internal/state/InternalPlayerState.kt`

```kotlin
data class InternalPlayerState(
    // ... existing fields
    
    /** Current aspect ratio display mode */
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
)
```

### 1.3. UI Button: `InternalPlayerControls`

Add to top-right corner of controls overlay:

```kotlin
@Composable
private fun TopBar(
    title: String,
    subtitle: String?,
    aspectRatioMode: AspectRatioMode,
    onCycleAspectRatio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Title on left
        Column { ... }
        
        // Aspect ratio button on right
        FocusableIconButton(
            onClick = onCycleAspectRatio,
            contentDescription = "Aspect ratio: ${aspectRatioMode.displayName}",
        ) {
            Icon(
                painter = painterResource(aspectRatioMode.iconRes),
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}
```

### 1.4. PlayerSurface Update

Apply resize mode to PlayerView:

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
    modifier = Modifier.fillMaxSize(),
)
```

---

## 2. Audio Track Selection

### 2.1. UI Component: `AudioTrackSheet`

**Location:** `player/internal/src/main/java/com/fishit/player/internal/ui/AudioTrackSheet.kt`

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrackSheet(
    state: AudioSelectionState,
    onSelectTrack: (AudioTrackId) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Black.copy(alpha = 0.95f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Audio",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            state.availableTracks.forEach { track ->
                AudioTrackItem(
                    track = track,
                    isSelected = track.id == state.selectedTrackId,
                    onClick = { onSelectTrack(track.id) },
                )
            }
        }
    }
}

@Composable
private fun AudioTrackItem(
    track: AudioTrack,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column {
            Text(
                text = track.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
            
            Text(
                text = track.formatDescription, // e.g., "5.1 Surround • AAC"
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}
```

### 2.2. Button in BottomBar

```kotlin
// Audio track button
FocusableIconButton(
    onClick = { showAudioSheet = true },
    contentDescription = "Audio tracks",
) {
    Icon(
        imageVector = Icons.Default.Audiotrack,
        contentDescription = null,
        tint = Color.White,
    )
}
```

---

## 3. Subtitle Track Selection

### 3.1. UI Component: `SubtitleTrackSheet`

**Location:** `player/internal/src/main/java/com/fishit/player/internal/ui/SubtitleTrackSheet.kt`

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleTrackSheet(
    state: SubtitleSelectionState,
    onSelectTrack: (SubtitleTrackId) -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Black.copy(alpha = 0.95f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Subtitles",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // "Off" option first
            SubtitleTrackItem(
                name = "Off",
                isSelected = !state.isEnabled,
                onClick = onDisable,
            )
            
            Divider(color = Color.White.copy(alpha = 0.2f))
            
            state.availableTracks.forEach { track ->
                SubtitleTrackItem(
                    name = track.displayName,
                    language = track.language,
                    isForced = track.isForced,
                    isSelected = track.id == state.selectedTrackId && state.isEnabled,
                    onClick = { onSelectTrack(track.id) },
                )
            }
        }
    }
}
```

---

## 4. Playback Speed Selection

### 4.1. UI Component: `SpeedSelectionSheet`

**Location:** `player/internal/src/main/java/com/fishit/player/internal/ui/SpeedSelectionSheet.kt`

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSelectionSheet(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Black.copy(alpha = 0.95f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Playback Speed",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            speeds.forEach { speed ->
                SpeedItem(
                    speed = speed,
                    isSelected = currentSpeed == speed,
                    onClick = { onSelectSpeed(speed) },
                )
            }
        }
    }
}

@Composable
private fun SpeedItem(
    speed: Float,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = when (speed) {
                1.0f -> "Normal"
                else -> "${speed}x"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
    }
}
```

---

## 5. Updated InternalPlayerControls Layout

```
┌──────────────────────────────────────────────────────────────┐
│  [← Back]              Title              [Aspect Ratio] 🔲  │
│                       Subtitle                                │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│                                                               │
│                  [ ⏪10 ]  [ ▶️ ]  [ ⏩10 ]                    │
│                                                               │
│                                                               │
├──────────────────────────────────────────────────────────────┤
│  ═══════════════════════●═══════════════════════════════     │
│                                                               │
│  00:32:15 / 01:45:30    [🔊] [🎵] [💬] [⚡]                    │
│                          Mute Audio Sub Speed                 │
└──────────────────────────────────────────────────────────────┘
```

---

## 6. Implementation Checklist

### Phase 1: Aspect Ratio (Immediate)
- [ ] Create `AspectRatioMode.kt` in `core/player-model`
- [ ] Add `aspectRatioMode` to `InternalPlayerState`
- [ ] Add `cycleAspectRatio()` to `InternalPlayerSession`
- [ ] Update `PlayerSurface` to apply resize mode
- [ ] Add aspect ratio button to `InternalPlayerControls`
- [ ] Add drawable icons for aspect modes

### Phase 2: Track Selection (Week 1)
- [ ] Create `AudioTrackSheet.kt`
- [ ] Create `SubtitleTrackSheet.kt`
- [ ] Add audio button to bottom bar
- [ ] Add subtitle button to bottom bar
- [ ] Wire up sheet display logic
- [ ] Test with multi-audio content

### Phase 3: Speed Control (Week 1-2)
- [ ] Create `SpeedSelectionSheet.kt`
- [ ] Add `setPlaybackSpeed()` to session
- [ ] Add speed button to bottom bar
- [ ] Wire up sheet display logic

### Phase 4: Polish (Week 2)
- [ ] Add TV-specific focus handling for sheets
- [ ] Add animations for sheet transitions
- [ ] Add haptic feedback on selections
- [ ] Test on real Fire TV device
- [ ] Add string resources for i18n

---

## 7. Files to Create/Modify

| Action | File | Priority |
|--------|------|----------|
| CREATE | `core/player-model/.../AspectRatioMode.kt` | 🔴 HIGH |
| MODIFY | `player/internal/.../InternalPlayerState.kt` | 🔴 HIGH |
| MODIFY | `player/internal/.../InternalPlayerSession.kt` | 🔴 HIGH |
| MODIFY | `player/internal/.../PlayerSurface.kt` | 🔴 HIGH |
| MODIFY | `player/internal/.../InternalPlayerControls.kt` | 🔴 HIGH |
| CREATE | `player/internal/.../ui/AudioTrackSheet.kt` | 🟠 MEDIUM |
| CREATE | `player/internal/.../ui/SubtitleTrackSheet.kt` | 🟠 MEDIUM |
| CREATE | `player/internal/.../ui/SpeedSelectionSheet.kt` | 🟠 MEDIUM |
| CREATE | `player/internal/res/drawable/ic_aspect_*` | 🟢 LOW |

---

## 8. Dependencies

No new dependencies required. All features use existing Media3 and Compose APIs.

---

## 9. Testing Plan

1. **Unit Tests:**
   - `AspectRatioMode.next()` cycling
   - `AspectRatioMode.toResizeMode()` mapping

2. **Integration Tests:**
   - Aspect ratio persists across seek
   - Track selection actually changes audio/subtitles
   - Speed changes apply immediately

3. **Manual Testing:**
   - Test on phone (portrait → landscape)
   - Test on Fire TV Stick (DPAD navigation)
   - Test with multi-audio content (e.g., dual audio anime)
   - Test with embedded subtitles

---

**Next Steps:**
1. Review and approve this plan
2. Create tracking issue for implementation
3. Start with Phase 1 (Aspect Ratio) as immediate priority
