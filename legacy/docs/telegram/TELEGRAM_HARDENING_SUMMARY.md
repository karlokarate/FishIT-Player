> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

# Telegram Playback Hardening - Implementation Summary

## Overview
This implementation hardens Telegram playback startup and settings state management to ensure Telegram VOD never stays on a black screen without a clear reason, and the "Telegram enabled" setting is only changed by explicit user actions.

## Completed Changes

### 1. Telegram Playback Path Logging
**File:** `app/src/main/java/com/chris/m3usuite/player/internal/session/InternalPlayerSession.kt`

Added comprehensive logging for all Telegram playback operations:

- **setMediaItem()**: Logs URL, playbackType, mimeType, chatId, messageId, fileId
- **prepare()**: Logs when prepare is called for Telegram VOD
- **play()**: Logs playWhenReady state and kids gate status
- **onPlaybackStateChanged**: Logs state transitions (IDLE/BUFFERING/READY/ENDED) with playWhenReady and isPlaying
- **onPlayWhenReadyChanged**: Logs playWhenReady changes with reason (USER_REQUEST, AUDIO_FOCUS_LOSS, etc.)

All logs include confirmation that Telegram VOD reaches playable state (STATE_READY or STATE_PLAYING with playWhenReady=true).

### 2. Buffering Watchdog
**File:** `app/src/main/java/com/chris/m3usuite/player/internal/session/InternalPlayerSession.kt`

Implemented a buffering watchdog that monitors BUFFERING state duration:

- Tracks when player enters BUFFERING state
- After 15 seconds in BUFFERING, logs diagnostic event with:
  - positionMs, durationMs
  - fileId, downloadedPrefixSize, expectedSize
  - playWhenReady, playbackState
- Throttled to log at most once per 30 seconds to avoid spam
- Resets on exit from BUFFERING state

### 3. Telegram Settings State Refactor
**File:** `app/src/main/java/com/chris/m3usuite/telegram/ui/TelegramSettingsViewModel.kt`

Refactored `TelegramSettingsState` to separate user decisions from engine health:

```kotlin
data class TelegramSettingsState(
    // User decision - only changed by explicit user actions
    val enabled: Boolean = false,
    
    // Engine health - changed by engine failures/recoveries
    val isEngineHealthy: Boolean = true,
    
    // Recent error for display (does not affect enabled toggle)
    val recentError: String? = null,
    
    // ... other fields
)
```

**Key Changes:**
- `enabled`: Only changed by `onToggleEnabled()` (user action)
- `isEngineHealthy`: Updated by all error handlers when engine fails/recovers
- `recentError`: Stores recent error messages for display
- Stack trace logging in `onToggleEnabled()` (DEBUG builds only) to audit callers
- All error handlers now update `isEngineHealthy` and `recentError` without touching `enabled`

**Updated Error Handlers:**
- `onStartConnection()` - marks engine unhealthy on failure
- `onConnectWithPhone()` - marks engine healthy on success, unhealthy on failure
- `onSendCode()` - marks engine healthy on success, unhealthy on failure
- `onSendPassword()` - marks engine healthy on success, unhealthy on failure
- `onLoadChats()` - marks engine unhealthy on failure
- `onDisconnect()` - marks engine unhealthy on failure

## UI Integration (To Be Implemented)

The Settings UI needs to be updated to reflect the new state model. Here's how:

### Current Behavior
The Telegram settings toggle currently binds to `enabled` state and shows errors via `errorMessage`.

### Required Changes

1. **Toggle Binding** (Already Correct)
   - Keep the toggle bound to `state.enabled`
   - Toggle should ONLY respond to user clicks
   - Never programmatically change the toggle state

2. **Add Engine Health Status Display**
   
   Add a status indicator below the toggle that shows engine health:

   ```kotlin
   // Example UI code (adapt to your actual UI framework)
   
   // Telegram Enabled Toggle
   Switch(
       checked = state.enabled,
       onCheckedChange = { viewModel.onToggleEnabled(it) }
   )
   
   // NEW: Engine Health Status Display
   if (state.enabled) {
       Row(
           modifier = Modifier.padding(start = 16.dp),
           verticalAlignment = Alignment.CenterVertically
       ) {
           // Health indicator icon
           Icon(
               imageVector = if (state.isEngineHealthy) {
                   Icons.Default.CheckCircle
               } else {
                   Icons.Default.Warning
               },
               tint = if (state.isEngineHealthy) {
                   Color.Green
               } else {
                   Color(0xFFFFA500) // Orange
               },
               contentDescription = null
           )
           
           Spacer(modifier = Modifier.width(8.dp))
           
           // Health status text
           Text(
               text = if (state.isEngineHealthy) {
                   "Engine Running"
               } else {
                   "Engine Unhealthy"
               },
               style = MaterialTheme.typography.bodySmall,
               color = if (state.isEngineHealthy) {
                   Color.Green
               } else {
                   Color(0xFFFFA500)
               }
           )
       }
       
       // Show recent error if present
       state.recentError?.let { error ->
           Text(
               text = error,
               style = MaterialTheme.typography.bodySmall,
               color = Color(0xFFFFA500),
               modifier = Modifier.padding(start = 16.dp, top = 4.dp)
           )
       }
   }
   ```

3. **Auth State Display** (Keep Existing)
   - Keep showing auth state (DISCONNECTED, WAITING_FOR_PHONE, etc.)
   - This is independent of engine health

### Visual Example

```
┌─────────────────────────────────────┐
│ Telegram                            │
│ ○━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ ON │ ← Toggle (bound to enabled)
│                                     │
│ ✓ Engine Running                    │ ← NEW: Health indicator (green when healthy)
│                                     │
│ or                                  │
│                                     │
│ ⚠ Engine Unhealthy                 │ ← NEW: Health warning (orange when unhealthy)
│ Connection failed: Network timeout  │ ← NEW: Recent error message
│                                     │
│ Auth Status: Ready                  │ ← Existing: Auth state
│                                     │
│ [Load Chats]                        │
└─────────────────────────────────────┘
```

## Benefits

1. **Never Lose User Intent**: User's decision to enable/disable Telegram is preserved even when engine has errors
2. **Clear Error Visibility**: Users see engine health status without the toggle mysteriously changing
3. **Diagnostic Logging**: Comprehensive logging helps diagnose black screen issues
4. **Buffering Insights**: Watchdog provides detailed diagnostics when playback stalls
5. **Audit Trail**: Stack traces (in DEBUG) help identify unexpected callers of setTelegramEnabled

## Testing Recommendations

1. **Enable Telegram → Cause Network Error**
   - Expected: Toggle stays ON, health indicator shows unhealthy, error message displayed

2. **Start Playback → Monitor Logs**
   - Expected: See setMediaItem, prepare, play logs with all details

3. **Playback Stalls in Buffering**
   - Expected: After 15 seconds, see buffering watchdog log with diagnostics

4. **Recovery from Error**
   - Expected: After successful operation (e.g., load chats), health indicator turns green

5. **Toggle OFF → Toggle ON**
   - Expected: Stack trace logged (DEBUG builds), engine starts, health turns green

## Security Summary

No security vulnerabilities introduced. All changes are logging and state management improvements that:
- Add observability to existing operations
- Separate concerns (user intent vs runtime health)
- Use proper error handling and throttling
- Only collect stack traces in DEBUG builds

## Future Improvements (Optional)

1. **Auto-Recovery**: When engine becomes unhealthy, could add automatic retry logic
2. **Health Metrics**: Track engine health over time for reliability monitoring
3. **User Notifications**: Show toast/snackbar when engine health changes
4. **Diagnostic Export**: Allow users to export logs when reporting issues
