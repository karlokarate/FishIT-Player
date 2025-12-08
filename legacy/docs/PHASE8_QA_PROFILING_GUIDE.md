# Phase 8 QA & Profiling Guide

## Overview

This document provides guidance for manual QA testing and profiling scenarios for the Phase 8 implementation (Performance, Lifecycle & Stability).

**Scope:** SIP-only verification. Do NOT test or modify Telegram modules.

**Contract References:**
- `INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md`
- `INTERNAL_PLAYER_PHASE8_CHECKLIST.md`
- `GLOBAL_TV_REMOTE_BEHAVIOR_MAP.md`

---

## Test Devices

### Recommended Devices

| Category | Device | Use Case |
|----------|--------|----------|
| Low-end Android TV | Fire TV Stick (2nd gen), basic Android TV box | Performance validation |
| Mid-range Android TV | Fire TV Stick 4K, Chromecast with Google TV | Standard testing |
| High-end Android TV | Nvidia Shield TV | Performance baseline |
| Phone | Any Android phone (Android 8+) | Touch/system PiP testing |
| Tablet | Any Android tablet (Android 8+) | Rotation/PiP testing |

---

## QA Scenarios

### 1. PlaybackSession Lifecycle

**Objective:** Verify PlaybackSession lifecycle state transitions

| Scenario | Steps | Expected Result |
|----------|-------|-----------------|
| Foreground → Background → Foreground | 1. Start playback<br>2. Press Home<br>3. Return to app | Playback resumes without rebuffering |
| Rotation during playback | 1. Start playback<br>2. Rotate device | Position, aspect, subtitles preserved |
| Full → Mini → Full | 1. Start playback<br>2. Tap PIP button<br>3. Tap MiniPlayer expand | No rebuffering, position preserved |
| Exit to Home (double BACK) | 1. Start playback<br>2. Press BACK twice quickly | Navigate to home, MiniPlayer remains if playing |

### 2. MiniPlayer UX

**Objective:** Verify In-App MiniPlayer behavior

| Scenario | Steps | Expected Result |
|----------|-------|-----------------|
| Enter MiniPlayer | Tap PIP button in player | MiniPlayer overlay appears, playback continues |
| Resize Mode (TV) | In MiniPlayer, long-press MENU | Enter resize mode, border appears |
| Resize MiniPlayer | In resize mode, press FF/RW | MiniPlayer size changes |
| Move MiniPlayer | In resize mode, use DPAD | MiniPlayer position changes |
| Confirm resize | Press OK/CENTER in resize mode | Changes confirmed, mode exits |
| Cancel resize | Press BACK in resize mode | Changes reverted, mode exits |
| Touch drag (phone) | Drag MiniPlayer with finger | Position updates, snaps to anchor |
| Focus toggle | Long-press PLAY with MiniPlayer visible | Focus toggles between UI and MiniPlayer |

### 3. System PiP (Phones/Tablets Only)

**Objective:** Verify system PiP triggers correctly

| Scenario | Device | Steps | Expected Result |
|----------|--------|-------|-----------------|
| Home triggers PiP | Phone | 1. Start playback<br>2. Press Home | System PiP activates |
| MiniPlayer blocks PiP | Phone | 1. Start playback<br>2. Enter MiniPlayer<br>3. Press Home | NO system PiP (MiniPlayer visible) |
| PIP button → MiniPlayer | Any | Tap PIP button in player | In-app MiniPlayer (NOT system PiP) |
| TV never system PiP | Fire TV | Press Home during playback | NO system PiP (TV device) |

### 4. TV Input & DPAD Navigation

**Objective:** Verify DPAD behavior matches GLOBAL_TV_REMOTE_BEHAVIOR_MAP

| Screen | Key | Expected Action |
|--------|-----|-----------------|
| PLAYER | DPAD_LEFT/RIGHT | Seek ±10s |
| PLAYER | FF/RW | Seek ±30s |
| PLAYER | DPAD_UP | Focus Quick Actions |
| PLAYER | DPAD_DOWN | Focus Timeline |
| PLAYER | CENTER | Play/Pause |
| LIBRARY | FF/RW | Row Fast Scroll |
| LIBRARY | CENTER | Open Details |
| MINI_PLAYER | FF/RW | Seek in MiniPlayer |
| MINI_PLAYER (resize) | FF/RW | Resize MiniPlayer |
| MINI_PLAYER (resize) | DPAD | Move MiniPlayer |

### 5. Kids Mode Filtering

**Objective:** Verify Kids Mode blocks restricted actions

| Scenario | Steps | Expected Result |
|----------|-------|-----------------|
| Seek blocked for kids | 1. Switch to kid profile<br>2. Start playback<br>3. Press DPAD LEFT/RIGHT | No seek (action blocked) |
| CC menu blocked for kids | 1. Switch to kid profile<br>2. Open player | CC button not visible |
| Play/Pause allowed | 1. Kid profile<br>2. Press PLAY | Playback toggles |
| Navigation allowed | 1. Kid profile<br>2. Navigate with DPAD | Normal navigation |

### 6. Error Handling

**Objective:** Verify error overlays and recovery

| Scenario | Steps | Expected Result |
|----------|-------|-----------------|
| Network error | 1. Start playback<br>2. Disable network | PlaybackErrorOverlay appears with retry option |
| Error retry | Click "Retry" on error overlay | Playback attempts to resume |
| Error dismiss | Click "Close" on error overlay | Returns to library, player releases |
| MiniPlayer error badge | Error occurs with MiniPlayer visible | Compact error badge in MiniPlayer |
| Kids error message | Error in kid profile | Generic "Cannot play" message |

### 7. Worker Throttling

**Objective:** Verify workers respect PlaybackPriority

| Scenario | Steps | Expected Result |
|----------|-------|-----------------|
| Worker during playback | 1. Trigger Xtream sync<br>2. Start playback | Workers throttle (visible in logs) |
| Worker without playback | 1. Trigger Xtream sync<br>2. No playback | Workers run at full speed |

---

## Profiling Guide

### Tools

- **Android Studio Profiler:** CPU, memory, network monitoring
- **Log Viewer (in-app):** AppLog events (enable via Settings > Developer)
- **TvInputInspector (debug):** TV input events (enable via Settings > Developer)
- **LeakCanary (debug):** Memory leak detection (auto-enabled in debug)

### Profiling Scenarios

#### A. Playback with Frequent Seeks

**Setup:**
1. Start playback of any VOD content
2. Open Android Studio Profiler (CPU)

**Actions:**
1. Seek rapidly using DPAD LEFT/RIGHT
2. Use FF/RW for trickplay
3. Enable/disable subtitles

**Observe:**
- CPU usage should not spike above 50% during seeks
- No GC pressure (memory graph stable)
- Frame rate maintained at 60fps

#### B. Playback + Worker Activity

**Setup:**
1. Start playback
2. Trigger Xtream refresh (or wait for scheduled sync)
3. Open Android Studio Profiler (CPU + Memory)

**Observe:**
- Playback frame rate unaffected
- Worker throttling visible in logs (`PlaybackPriority.shouldThrottle`)
- Memory stable (no accumulation)

#### C. MiniPlayer Show/Hide + Resize

**Setup:**
1. Start playback
2. Enter MiniPlayer mode

**Actions:**
1. Toggle MiniPlayer visibility 10+ times
2. Enter/exit resize mode
3. Navigate library with MiniPlayer visible

**Observe:**
- No animation jank
- No memory accumulation (LeakCanary silent)
- Focus transitions smooth

#### D. Rotation Stress Test

**Setup:**
1. Start playback on phone/tablet
2. Open Android Studio Profiler

**Actions:**
1. Rotate device 10+ times rapidly
2. Check position, aspect ratio, subtitle selection after each rotation

**Observe:**
- PlaybackSession not recreated (single instance)
- Position preserved within 1 second accuracy
- Aspect ratio and subtitles unchanged
- No Activity leaks

### Key Metrics

| Metric | Target | Location |
|--------|--------|----------|
| CPU during playback | < 30% baseline | Android Studio Profiler |
| CPU during seeks | < 50% peak | Android Studio Profiler |
| Memory baseline | < 200MB | Android Studio Profiler |
| GC events | < 1 per minute | Android Studio Profiler |
| Frame rate | 60fps sustained | GPU Profiler |
| Recomposition count | Minimal during playback | Layout Inspector |

---

## Automated Test Coverage

### Unit Tests (app/src/test/)

| Test File | Coverage |
|-----------|----------|
| `Phase4SubtitleRegressionTest.kt` | Subtitle style, presets, kids blocking |
| `Phase5PlayerSurfaceRegressionTest.kt` | Black bars, aspect ratio, trickplay |
| `Phase6TvInputRegressionTest.kt` | TV input resolution, kids/overlay filtering |
| `Phase7MiniPlayerRegressionTest.kt` | MiniPlayer state, transitions, resize |
| `Phase8CrossCheckRegressionTest.kt` | Lifecycle state, PlaybackPriority, errors |
| `PlaybackSessionLifecycleTest.kt` | Lifecycle state transitions |
| `RotationResilienceTest.kt` | Config change survival |
| `MiniPlayerTransitionTest.kt` | Full↔Mini transitions |
| `GlobalTvInputBehaviorTest.kt` | Screen-specific key mappings |

### Running Tests

```bash
# All Phase 4-8 regression tests
./gradlew :app:testDebugUnitTest --tests "*Phase4*" --tests "*Phase5*" --tests "*Phase6*" --tests "*Phase7*" --tests "*Phase8*"

# Full test suite (may include Telegram tests that are known broken)
./gradlew :app:testDebugUnitTest --continue
```

---

## Known Limitations

1. **Telegram tests may fail:** Per task constraints, Telegram modules are NOT modified. Some Telegram tests may fail due to existing issues.

2. **System PiP visual testing:** Requires physical device; emulator support varies.

3. **Fire TV auto-PiP:** FireOS may still trigger PiP on its own (not blocked at OS level).

4. **LeakCanary false positives:** Some false positives may occur with Compose; focus on PlaybackSession/MiniPlayerManager leaks.

---

## Appendix: Log Categories

| Category | Description | Use |
|----------|-------------|-----|
| `PLAYER_ERROR` | Playback errors | Error diagnosis |
| `WORKER_ERROR` | Worker failures | Background job issues |
| `TV_INPUT` | TV input events | DPAD/remote debugging |
| `LIFECYCLE` | Session lifecycle | State transitions |
| `MINIPLAYER` | MiniPlayer state | PIP debugging |

Enable logging via Settings > Developer > Debug Logging.

---

**Last Updated:** 2025-11-30
