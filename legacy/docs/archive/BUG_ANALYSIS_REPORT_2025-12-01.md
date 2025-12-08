> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

# Bug Analysis Report – SIP Internal Player + Telegram/Xtream Integration

**Date:** 2025-12-01  
**Scope:** Analysis of 5 runtime bugs in the refactored InternalPlayer  
**Status:** Analysis Complete – Ready for Fix-It Task

---

## Overview

This document contains analysis findings for 5 runtime bugs in the FishIT-Player refactored SIP (Simplified Internal Player) architecture. Each bug is analyzed with:
- What is happening (observed behavior)
- Where it happens (file + function)
- Why it happens (root cause)
- What to change (fix plan)

---

## BUG 1 – Live TV Format Detection & Debug Screen Visibility

### Observed Behavior

- The refactored InternalPlayer sometimes detects LIVE correctly, sometimes not.
- The debug screen does not show the current playback URL, extension, MIME type, or whether SIP thinks this is LIVE vs VOD.

### Where It Happens

**Classification Logic:**
- `app/src/main/java/com/chris/m3usuite/player/internal/domain/PlaybackContext.kt` – `PlaybackType` enum (VOD, SERIES, LIVE)
- `app/src/main/java/com/chris/m3usuite/MainActivity.kt` lines 524-548 – Builds `PlaybackContext` from route parameters
- `app/src/main/java/com/chris/m3usuite/player/internal/source/InternalPlaybackSourceResolver.kt` – MIME type inference

**Debug Screen:**
- `app/src/main/java/com/chris/m3usuite/player/internal/state/InternalPlayerState.kt` – `showDebugInfo: Boolean` flag exists but no diagnostic fields
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/InternalPlayerControls.kt` – Debug overlay rendering

### Root Cause

1. **LIVE Classification Depends Solely on Route Parameter:**
   - The `type` parameter is passed from navigation routes (e.g., `player?...&type=live`)
   - If a LIVE stream is launched without the `type=live` parameter (e.g., from Telegram or a misrouted path), it defaults to VOD
   - There is **no URL/path inspection** to infer LIVE from URL patterns like `/live/`, `.ts` extension, or HLS live playlist markers

2. **MIME Type Inference Incomplete:**
   - `InternalPlaybackSourceResolver.resolve()` uses `PlayUrlHelper.guessMimeType()` and file extension heuristics
   - No inspection of URL path segments (`/live/`, `/movie/`, `/series/`) for Xtream streams
   - No live-specific HLS detection (missing `#EXT-X-PLAYLIST-TYPE` inspection)

3. **Debug Diagnostics Missing:**
   - `InternalPlayerUiState` has no fields for:
     - `currentPlaybackUrl: String?`
     - `inferredExtension: String?`
     - `resolvedMimeType: String?`
     - `isLiveFromUrl: Boolean?` (URL-inferred live detection)
   - The debug overlay (`showDebugInfo = true`) only toggles visibility but doesn't render diagnostic data

### Fix Plan

1. **Add URL-based LIVE detection heuristics in `InternalPlaybackSourceResolver`:**
   ```kotlin
   fun isLikelyLive(url: String): Boolean {
       return url.contains("/live/") ||
              url.endsWith(".ts") ||
              url.contains("stream_type=live") ||
              // Add more Xtream-specific patterns
   }
   ```

2. **Extend `InternalPlayerUiState` with diagnostic fields:**
   ```kotlin
   val currentPlaybackUrl: String? = null,
   val inferredExtension: String? = null,
   val resolvedMimeType: String? = null,
   val isLiveFromUrl: Boolean = false,
   ```

3. **Populate diagnostic fields in `rememberInternalPlayerSession`:**
   - After calling `PlaybackSourceResolver.resolve()`, extract and store URL, extension, MIME type
   - Add `isLiveFromUrl` based on URL pattern inspection

4. **Update debug overlay in `InternalPlayerControls` to display:**
   - URL (truncated)
   - Extension
   - MIME type
   - PlaybackType (VOD/SERIES/LIVE)
   - isLiveFromUrl

---

## BUG 2 – LIVE Channel Swipe Left/Right Not Working

### Observed Behavior

- In InternalPlayer LIVE mode, swiping left/right (or DPAD left/right on TV) does not switch channels as expected.

### Where It Happens

**Gesture Handling:**
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/PlayerSurface.kt` lines 148-190 – Drag gesture detection
- `app/src/main/java/com/chris/m3usuite/player/internal/ui/PlayerSurface.kt` lines 163-167 – LIVE channel zapping logic

**Controller Callback:**
- `app/src/main/java/com/chris/m3usuite/player/InternalPlayerEntry.kt` line 223 – `onJumpLiveChannel` callback is empty placeholder

**Live Controller:**
- `app/src/main/java/com/chris/m3usuite/player/internal/live/DefaultLivePlaybackController.kt` – `jumpChannel()` is fully implemented
- `app/src/main/java/com/chris/m3usuite/player/internal/session/InternalPlayerSession.kt` lines 150-162 – LiveController created for LIVE type

### Root Cause

**The wiring is incomplete:**

1. **PlayerSurface correctly detects LIVE gestures** (line 164-167):
   ```kotlin
   if (playbackType == PlaybackType.LIVE) {
       val delta = if (dragDeltaX < 0) +1 else -1
       onJumpLiveChannel(delta)
   }
   ```

2. **InternalPlayerEntry creates an empty callback** (line 223):
   ```kotlin
   onJumpLiveChannel = { /* Handled by LivePlaybackController in session */ },
   ```
   - The comment says "Handled by LivePlaybackController in session" but there is **no actual wiring**

3. **LivePlaybackController is created in `rememberInternalPlayerSession`** but the controller reference is NOT exposed to `InternalPlayerEntry`:
   - The session creates `liveController` (line 150-162) but doesn't return it or expose it
   - The `createSipController()` function in `InternalPlayerEntry` has no access to `liveController`

4. **Missing channel URL propagation:**
   - Even if `jumpChannel()` were called, it only updates `_currentChannel.value`
   - There is no code that takes the new channel URL and sets it on the player via `PlaybackSession.setSource()`

### Fix Plan

1. **Expose `liveController` from `rememberInternalPlayerSession`:**
   - Change return type to include both player and live controller
   - Or expose via a state holder

2. **Wire `onJumpLiveChannel` in `createSipController()`:**
   ```kotlin
   onJumpLiveChannel = { delta ->
       scope.launch {
           liveController?.jumpChannel(delta)
           val newChannel = liveController?.currentChannel?.value
           if (newChannel?.url != null) {
               // Update player source with new channel URL
               PlaybackSession.setSource(newChannel.url)
           }
       }
   },
   ```

3. **Add URL change listener for LivePlaybackController:**
   - Observe `liveController.currentChannel` StateFlow
   - When channel changes, update the player's MediaItem source

4. **Implement DPAD left/right handling in TvInputController:**
   - Map DPAD_LEFT → `CHANNEL_DOWN` for LIVE type
   - Map DPAD_RIGHT → `CHANNEL_UP` for LIVE type

---

## BUG 3 – Debug Log Screen Only Accessible When Telegram Is Connected

### Observed Behavior

- The debug output screen (Settings/Debug) is only usable if Telegram is enabled/authorized.
- Debug features should be available even without Telegram.

### Where It Happens

**Settings Screen:**
- `app/src/main/java/com/chris/m3usuite/ui/screens/SettingsScreen.kt` lines 363-628 – `TelegramSettingsSection`

**Debug Navigation:**
- `app/src/main/java/com/chris/m3usuite/ui/screens/SettingsScreen.kt` lines 571-605 – "Telegram Tools" section only shown when `state.authState == TelegramAuthState.READY`

**Routes:**
- `app/src/main/java/com/chris/m3usuite/MainActivity.kt` lines 659-686 – `telegram_log`, `telegram_feed`, `log_viewer` routes exist

### Root Cause

The "Telegram Tools" section (containing Log and LogViewer buttons) is **nested inside the `TelegramAuthState.READY` branch:**

```kotlin
TelegramAuthState.READY -> {
    // ... other READY-only UI ...
    
    // Navigation to Telegram screens (lines 571-605)
    HorizontalDivider()
    Text("Telegram Tools", style = MaterialTheme.typography.titleSmall)
    Row(...) {
        onOpenFeed?.let { /* Activity Feed button */ }
        onOpenLog?.let { /* Logs button */ }
    }
    onOpenLogViewer?.let { /* Log Viewer button */ }
}
```

This means:
- If Telegram is DISCONNECTED, WAITING_FOR_PHONE, WAITING_FOR_CODE, or WAITING_FOR_PASSWORD, the debug buttons are not shown
- Even the generic `Log Viewer` (which shows AppLog entries, not just Telegram logs) is hidden

### Fix Plan

1. **Move debug navigation outside the auth state branch:**
   - The "Log Viewer" button should always be visible (it's a general debug feature)
   - The "Telegram Logs" button can remain conditional on `state.enabled` but should not require `READY` state

2. **Create a separate "Debug & Diagnostics" section:**
   ```kotlin
   // After TelegramSettingsSection
   SettingsCard(title = "Debug & Diagnostics") {
       onOpenLogViewer?.let { handler ->
           Button(onClick = handler, modifier = Modifier.fillMaxWidth()) {
               Text("App Log Viewer")
           }
       }
       if (telegramState.enabled) {
           onOpenTelegramLog?.let { handler ->
               Button(onClick = handler, modifier = Modifier.fillMaxWidth()) {
                   Text("Telegram Logs")
               }
           }
       }
   }
   ```

3. **Alternative: Add debug toggle at the top of TelegramSettingsSection:**
   - Show "Logs" and "Log Viewer" buttons even when `state.enabled = false`
   - Only hide Telegram-specific actions (Activity Feed) when Telegram is disabled

---

## BUG 4 – First-Time Xtream URL Entry Crash on Back Navigation

### Observed Behavior

- When entering the Xtream URL for the first time in Settings and then navigating back, the app crashes.
- After reopening the app and manually starting Delta Import, things work.

### Where It Happens

**Settings Save:**
- `app/src/main/java/com/chris/m3usuite/ui/screens/XtreamSettingsViewModel.kt` lines 60-83 – `onChange()` saves immediately on every keystroke

**Auto-Import Trigger:**
- `app/src/main/java/com/chris/m3usuite/MainActivity.kt` lines 225-245 – `LaunchedEffect(xtHost, xtUser, xtPass, xtPort)` triggers auto-import when credentials change

**Repository Access:**
- `app/src/main/java/com/chris/m3usuite/MainActivity.kt` line 233 – Creates `XtreamObxRepository` and calls `importDelta()`

### Root Cause

The crash likely occurs due to a **race condition** between:

1. **Settings change triggers auto-import (MainActivity line 225-245):**
   ```kotlin
   LaunchedEffect(xtHost, xtUser, xtPass, xtPort) {
       if (xtHost.isBlank() || xtUser.isBlank() || xtPass.isBlank()) return@LaunchedEffect
       // ... auto-import code
   }
   ```

2. **XtreamSettingsViewModel saves on every keystroke (line 60-83):**
   - Each character typed triggers `save()` which writes to DataStore
   - This causes `xtHost`, `xtUser`, etc. StateFlows to emit rapidly
   - Each emission potentially triggers the `LaunchedEffect`

3. **Back navigation pops the Settings screen:**
   - If the user presses BACK while a save is in-flight or auto-import is running
   - The ViewModel scope is cancelled
   - But the `LaunchedEffect` in MainActivity continues with the import
   - The import may access resources that were expected to be initialized

4. **Potential NPE scenarios:**
   - `XtreamObxRepository` created with incomplete config (partial credentials)
   - `importDelta()` called before ObjectBox is fully ready
   - Network calls fail and throw exceptions that aren't caught

5. **Missing null guards:**
   - `store.hasXtream()` check exists for M3U-derived config but not for first-time manual entry
   - The code assumes `xtPort > 0` is valid but it might be 80 (default) even when host is empty

### Fix Plan

1. **Add validation before triggering auto-import:**
   ```kotlin
   LaunchedEffect(xtHost, xtUser, xtPass, xtPort) {
       if (xtHost.isBlank() || xtUser.isBlank() || xtPass.isBlank()) return@LaunchedEffect
       if (xtPort <= 0) return@LaunchedEffect
       
       // Add debounce to avoid triggering on every keystroke
       delay(500) // Wait 500ms after last change
       
       // Verify config is valid before import
       val isValid = XtreamConfig.isValid(xtHost, xtPort, xtUser, xtPass)
       if (!isValid) return@LaunchedEffect
       
       // ... proceed with import
   }
   ```

2. **Debounce the save in XtreamSettingsViewModel:**
   ```kotlin
   private var saveJob: Job? = null
   
   fun onChange(...) = viewModelScope.launch {
       saveJob?.cancel()
       saveJob = launch {
           delay(300) // Debounce
           save(...)
       }
   }
   ```

3. **Wrap import in try-catch with proper error handling:**
   ```kotlin
   withContext(Dispatchers.IO) {
       try {
           XtreamObxRepository(ctx, store).importDelta(...)
       } catch (e: Exception) {
           AppLog.log(
               category = "xtream",
               level = AppLog.Level.ERROR,
               message = "Delta import failed: ${e.message}"
           )
       }
   }
   ```

4. **Add lifecycle-aware guard:**
   - Check if the calling coroutine is still active before accessing UI-related resources
   - Use `ensureActive()` before heavy operations

---

## BUG 5 – PiP on Phone/Tablet: Audio Only, No Visible Window

### Observed Behavior

- When minimizing the app (HOME or RECENTS) while video is playing:
  - Audio continues (player stays alive)
  - PiP should show a mini-player window, but none is visible on phone/tablet (only sound)

### Where It Happens

**Activity PiP Methods:**
- `app/src/main/java/com/chris/m3usuite/MainActivity.kt` lines 726-732 – `onUserLeaveHint()` triggers PiP
- `app/src/main/java/com/chris/m3usuite/MainActivity.kt` lines 743-775 – `tryEnterSystemPip()`
- `app/src/main/java/com/chris/m3usuite/MainActivity.kt` lines 783-796 – `buildPictureInPictureParams()`

**Manifest Configuration:**
- `AndroidManifest.xml` lines 31-32:
  ```xml
  android:resizeableActivity="true"
  android:supportsPictureInPicture="true"
  ```

**Playback Session:**
- `app/src/main/java/com/chris/m3usuite/playback/PlaybackSession.kt` – Singleton ExoPlayer holder

### Root Cause

Several issues prevent visible PiP window:

1. **PiP Only Triggered on API < 31 (line 730-732):**
   ```kotlin
   override fun onUserLeaveHint() {
       super.onUserLeaveHint()
       if (Build.VERSION.SDK_INT < 31) {
           tryEnterSystemPip()
       }
   }
   ```
   - On API 31+, the code relies on `setAutoEnterEnabled(true)` via `buildPictureInPictureParams()`
   - But `setPictureInPictureParams()` is only called in `updatePipParams()` which is **never called from anywhere**

2. **Missing PiP Params Update:**
   - `updatePipParams()` (lines 851-861) exists but is documented as needing to be called "when playback state changes"
   - There is **no actual caller** for this method
   - Without calling `setPictureInPictureParams()`, auto-enter PiP won't work on API 31+

3. **PlayerView Not Attached During PiP:**
   - The `PlayerSurface` composable is only rendered when the player screen is in the composition
   - When leaving the app, the composable is disposed (or may be)
   - PiP needs a PlayerView surface to remain attached

4. **isPlaying Check May Be Stale:**
   ```kotlin
   val isPlaying = PlaybackSession.isPlaying.value
   ```
   - This reads from `StateFlow` but doesn't observe it reactively
   - By the time `onUserLeaveHint` runs, the state might not reflect actual playback

5. **Compose Navigation Pops Player:**
   - If BACK is pressed before HOME, the player composable is removed from composition
   - The ExoPlayer continues playing via PlaybackSession, but there's no surface to render to

### Fix Plan

1. **Call `updatePipParams()` reactively in Compose:**
   ```kotlin
   // In MainActivity's setContent block or InternalPlayerEntry
   LaunchedEffect(isPlaying, miniPlayerVisible) {
       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
           (context as? MainActivity)?.updatePipParams()
       }
   }
   ```

2. **For API 31+, set auto-enter immediately when playback starts:**
   ```kotlin
   // In InternalPlayerSession or PlaybackSession
   LaunchedEffect(player?.isPlaying) {
       if (Build.VERSION.SDK_INT >= 31) {
           activity.updatePipParams()
       }
   }
   ```

3. **Ensure PlayerView survives composition for PiP:**
   - Move PlayerView to Activity-level or use `AndroidView` with explicit lifecycle handling
   - Or set `player.setVideoSurfaceView()` to a persistent SurfaceView during PiP

4. **Handle `onPictureInPictureModeChanged`:**
   - Add callback to detect when PiP mode is entered/exited
   - Re-bind player surface when returning from PiP

5. **Alternative: Use PlaybackSession's surface management:**
   - PlaybackSession could maintain its own SurfaceView for PiP scenarios
   - When entering PiP, detach from Compose PlayerView and attach to Activity-level surface

6. **Fix API < 31 path:**
   - The `tryEnterSystemPip()` call happens correctly
   - Verify `enterPictureInPictureMode(params)` is called with valid params
   - Check logs for "Failed to enter system PiP" messages

---

## Summary Table

| Bug ID | Root Cause | Key Files | Complexity | Status |
|--------|------------|-----------|------------|--------|
| BUG 1 | Missing URL-based LIVE detection + missing diagnostic fields | InternalPlaybackSourceResolver, InternalPlayerState | Medium | ✅ FIXED |
| BUG 2 | Live channel controller not wired to gesture callbacks | InternalPlayerEntry, InternalPlayerSession | Medium | ✅ FIXED |
| BUG 3 | Debug buttons nested inside Telegram READY state | SettingsScreen | Low | ✅ FIXED |
| BUG 4 | Race condition + missing debounce on Xtream config save | XtreamSettingsViewModel, MainActivity | Medium | ✅ FIXED |
| BUG 5 | `updatePipParams()` never called + surface detachment | MainActivity, InternalPlayerEntry | High | ✅ FIXED |

---

## Fix Summary (Phase 9 Sammel-Patch)

### BUG 1 – Live/VOD Detection & Debug Info ✅
- Added `isLikelyLiveUrl()` heuristic in `PlaybackSourceResolver`
- Added diagnostic fields to `ResolvedPlaybackSource`: `isLiveFromUrl`, `inferredExtension`
- Added debug fields to `InternalPlayerUiState`: `debugPlaybackUrl`, `debugResolvedMimeType`, `debugInferredExtension`, `debugIsLiveFromUrl`
- Session now populates debug fields after source resolution

### BUG 2 – Live-TV Channel Zapping ✅
- Created `InternalPlayerSessionResult` to expose both player and `liveController`
- Modified `rememberInternalPlayerSession` to return result with `liveController`
- Updated `createSipController` to accept `liveController` and `playbackType`
- Implemented `onJumpLiveChannel` callback to call `liveController.jumpChannel(delta)`
- Added automatic player source update when `currentChannel` changes

### BUG 3 – Debug Log Viewer Accessibility ✅
- Created "Debug & Diagnostics" settings section that is always visible
- Moved Log Viewer button from Telegram READY state to global section
- Telegram Logs button still requires Telegram to be enabled

### BUG 4 – Xtream First-Time Config Crash ✅
- Added 500ms debounce in `XtreamSettingsViewModel.onChange()`
- Added 750ms debounce in MainActivity auto-import `LaunchedEffect`
- Added port validation (`port > 0`)
- Wrapped import in try/catch with proper `AppLog` error handling

### BUG 5 – System PiP on Phone/Tablet ✅
- Added `LaunchedEffect` in `InternalPlayerEntry` to call `updatePipParams()` when playback state changes
- Added `onPictureInPictureModeChanged` callback in `MainActivity`
- PiP params now update reactively for API 31+ auto-enter support

---

## Recommended Fix Order

1. **BUG 3** (Low complexity) – Quick win, UI-only change
2. **BUG 4** (Medium) – Improves first-time user experience
3. **BUG 2** (Medium) – Critical for LIVE TV functionality
4. **BUG 1** (Medium) – Debugging capability + format detection
5. **BUG 5** (High) – Requires careful lifecycle management

---

## References

- `docs/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md` – Resume & Kids mode behavior
- `docs/INTERNAL_PLAYER_REFACTOR_ROADMAP.md` – SIP architecture phases
- `docs/INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md` – Lifecycle rules
- `docs/TELEGRAM_SIP_PLAYER_INTEGRATION.md` – Telegram integration
- `docs/TELEGRAM_UI_WIRING_ANALYSIS.md` – UI wiring gaps

---

**Author:** GitHub Copilot Agent  
**Analysis Status:** ✅ COMPLETE – All bugs fixed in Phase 9 Sammel-Patch
