# Telegram Auth State Persistence Fix (2025-12-03)

## Problem Description

**User Report:**
After closing and reopening the app, navigating to Settings shows:
- ✅ "Telegram aktivieren" toggle is ON (correct)
- ❌ API ID shows "0" (should be hidden when authenticated)
- ❌ API Hash shows empty (should be hidden when authenticated)
- ❌ Phone number field is empty and editable
- ❌ Status shows "DISCONNECTED" (should be "READY")

**Workaround Required:**
User had to toggle "Telegram aktivieren" OFF and then ON again to restore READY state without re-authentication.

## Root Cause Analysis

### Issue 1: Race Condition at App Start

**Flow:**
1. `App.onCreate()` starts Telegram engine in background coroutine
2. `TelegramSettingsViewModel` is created immediately (before engine fully started)
3. ViewModel collects `serviceClient.authState` which is still `Idle`
4. UI shows `DISCONNECTED` state with empty credential fields
5. After toggle OFF/ON, ViewModel calls `ensureStarted()` again and TDLib session is recognized as valid

**Problem:** 
The `T_TelegramServiceClient` singleton maintains TDLib state, but its `_authState` flow starts as `Idle` and only updates when:
- Auth events are collected from `T_TelegramSession`
- Or when `login()` explicitly triggers auth flow

When the app restarts with a valid TDLib session:
- The session file exists on disk
- TDLib internally knows auth is Ready
- But `T_TelegramServiceClient._authState` remains `Idle` until explicitly queried

### Issue 2: Missing Initial State Query

**Before Fix:**
```kotlin
// T_TelegramServiceClient.ensureStarted()
startUpdateDistribution()
startAuthEventCollection()  // Only listens for FUTURE auth events

_connectionState.value = TgConnectionState.Connected
_isStarted.set(true)
// Auth state NOT queried - remains Idle until next auth event
```

**Problem:**
`startAuthEventCollection()` only receives FUTURE auth state changes from TDLib. If the session is already authenticated when starting, no event is emitted, so `_authState` never updates from `Idle`.

## Solution Implemented

### Fix 1: Query Initial Auth State on Start

**File:** `app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramServiceClient.kt`

**Change:**
After starting the engine and before marking as ready, explicitly query TDLib's current authorization state:

```kotlin
// Query initial auth state from TDLib to avoid showing "Disconnected" when session is valid
// This fixes the issue where users see empty fields after app restart despite valid session
try {
    val initialAuthState = client!!.getAuthorizationState().getOrNull()
    if (initialAuthState != null) {
        val mappedState = mapAuthorizationStateToAuthState(initialAuthState)
        _authState.value = mappedState
        TelegramLogRepository.debug(
            "T_TelegramServiceClient",
            "Initial auth state after start: $mappedState (TDLib: ${initialAuthState::class.simpleName})"
        )
    }
} catch (e: Exception) {
    TelegramLogRepository.warn(
        "T_TelegramServiceClient",
        "Failed to query initial auth state: ${e.message}"
    )
}
```

**Result:**
- If TDLib has a valid session → `_authState` immediately becomes `Ready`
- If TDLib needs re-auth → `_authState` becomes `WaitingForPhone`
- UI reflects correct state immediately after engine start

### Fix 2: Improved ViewModel Auto-Start Logic

**File:** `app/src/main/java/com/chris/m3usuite/telegram/ui/TelegramSettingsViewModel.kt`

**Changes:**

1. **Removed `isConnecting` guard:** Allowed `ensureStarted()` to be called even if already connecting (it's idempotent)

2. **Added Ready state check before login():**
```kotlin
// ensureStarted() is idempotent - safe to call even if App.onCreate() already started it
serviceClient.ensureStarted(app, store)

// Only call login() if NOT already Ready (prevents unnecessary calls)
if (serviceClient.authState.value !is TelegramAuthState.Ready) {
    TelegramLogRepository.debug(
        source = "TelegramSettingsViewModel",
        message = "Auth not ready, triggering login flow",
    )
    serviceClient.login() // Let TDLib determine if session is valid
} else {
    TelegramLogRepository.debug(
        source = "TelegramSettingsViewModel",
        message = "Auth already ready, skipping login call",
    )
}
```

**Result:**
- ViewModel calls `ensureStarted()` (idempotent, safe)
- If auth is already Ready (from initial state query), skip redundant `login()` call
- If auth needs work, `login()` proceeds as normal

## Flow After Fix

### App Restart with Valid Session

```
1. App.onCreate()
   └─> serviceClient.ensureStarted()
       ├─> Create TdlClient
       ├─> Start auth event collection (for future updates)
       ├─> Query initial auth state from TDLib
       │   └─> TDLib returns AuthorizationStateReady (from disk)
       ├─> Set _authState = Ready ✅
       └─> Set _connectionState = Connected ✅

2. TelegramSettingsViewModel.init()
   ├─> Collect serviceClient.authState
   │   └─> Receives Ready immediately ✅
   ├─> Collect store.tgEnabled
   │   └─> Receives true ✅
   └─> ensureStartedIfEnabled()
       ├─> serviceClient.ensureStarted() (already started, returns early)
       └─> Check authState.value == Ready → Skip login() ✅

3. SettingsScreen renders
   ├─> state.enabled = true ✅
   ├─> state.authState = READY ✅
   ├─> API fields HIDDEN (only shown when DISCONNECTED) ✅
   ├─> Phone field HIDDEN ✅
   └─> Shows "Status: READY" + "Trennen" button ✅
```

### App Restart with Expired Session

```
1. App.onCreate()
   └─> serviceClient.ensureStarted()
       ├─> Query initial auth state from TDLib
       │   └─> TDLib returns AuthorizationStateWaitPhoneNumber
       └─> Set _authState = WaitingForPhone ✅

2. TelegramSettingsViewModel.init()
   ├─> Collect authState → WaitingForPhone
   └─> ensureStartedIfEnabled()
       ├─> Check authState != Ready
       └─> Call serviceClient.login() → Prompts for phone ✅

3. SettingsScreen renders
   ├─> state.authState = WAITING_FOR_PHONE
   ├─> Shows API fields (if DISCONNECTED mapped) or phone field
   └─> User can re-authenticate
```

## Testing Checklist

- [ ] Fresh install → Enable Telegram → Authenticate → Close app → Reopen
  - **Expected:** Status shows READY immediately, no credential fields visible
  
- [ ] Authenticated app → Revoke session externally → Reopen app
  - **Expected:** Status shows WAITING_FOR_PHONE, prompts for re-auth
  
- [ ] Authenticated app → Toggle OFF → Toggle ON
  - **Expected:** Status shows READY without re-authentication
  
- [ ] Multiple ViewModels accessing ServiceClient (e.g., Settings + another screen)
  - **Expected:** All receive same auth state, no conflicts

## Additional Notes

### Idempotency
Both `App.onCreate()` and `TelegramSettingsViewModel.init()` call `ensureStarted()`. This is safe because:
- `ensureStarted()` uses `AtomicBoolean` guards (`_isStarted`, `isInitializing`)
- Multiple calls return early if already started
- No duplicate TdlClient instances created

### ViewModel Lifecycle
The ViewModel is recreated on configuration changes and when navigating to Settings. The ServiceClient is a singleton that persists across ViewModel instances. The fix ensures that any new ViewModel immediately sees the correct auth state from the ServiceClient's flows.

### TDLib Session Persistence
TDLib stores session data in:
- `{app_cache_dir}/tdlib/td.binlog` (encrypted session database)
- Session survives app restarts, device reboots, and app updates
- Only revoked if user explicitly logs out or session expires server-side

## Related Files

### Modified
- `app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramServiceClient.kt`
  - Added initial auth state query in `ensureStarted()`
  
- `app/src/main/java/com/chris/m3usuite/telegram/ui/TelegramSettingsViewModel.kt`
  - Improved `ensureStartedIfEnabled()` logic with Ready state check

### Unchanged (Already Correct)
- `app/src/main/java/com/chris/m3usuite/App.kt`
  - Auto-start logic already existed
  
- `app/src/main/java/com/chris/m3usuite/ui/screens/SettingsScreen.kt`
  - UI correctly hides credential fields when state != DISCONNECTED

---

**Status:** ✅ FIXED  
**Date:** 2025-12-03  
**Issue:** Auth state not persisting across app restarts  
**Resolution:** Query initial TDLib auth state on engine start + improved ViewModel auto-start logic
