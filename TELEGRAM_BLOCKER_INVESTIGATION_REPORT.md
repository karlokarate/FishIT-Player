# Telegram Pipeline Blocker Investigation Report

**Date:** 2025-12-22  
**Branch:** `copilot/investigate-telegram-blockers`  
**Status:** Investigation Complete - Diagnostic Logging Added

---

## Executive Summary

This investigation traced the complete Telegram integration pipeline from UI onboarding through catalog synchronization to identify blockers preventing content from appearing after authentication. The investigation confirmed the architecture is correctly implemented, verified no JSON parsing issues exist, and added comprehensive diagnostic logging to identify runtime blockers.

**Key Finding:** The architecture and data flow are correct. The blocker is likely in one of the state transitions (auth → Ready, connection → Connected, or activation → ACTIVE) that can now be diagnosed with the enhanced logging.

---

## 1. Architecture Verification

### 1.1 Complete Data Flow (Verified)

```
┌─────────────────────────────────────────────────────────────────────┐
│ UI Layer (Onboarding/StartScreen)                                   │
│   - User initiates Telegram connect                                 │
└────────────────────┬────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────────┐
│ TelegramAuthRepository (domain interface)                           │
│   Location: core/feature-api/...TelegramAuthRepository.kt           │
│   Exposes: StateFlow<TelegramAuthState>                             │
└────────────────────┬────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────────┐
│ TelegramAuthRepositoryImpl (data-telegram)                          │
│   Location: infra/data-telegram/.../TelegramAuthRepositoryImpl.kt   │
│   Maps: TdlibAuthState → DomainAuthState                            │
│   - Ready → Connected                                                │
│   - Connecting/Idle → Idle                                           │
│   - LoggingOut/Closed/LoggedOut → Disconnected                      │
└────────────────────┬────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────────┐
│ TelegramAuthClient (transport interface)                            │
│   Location: infra/transport-telegram/.../TelegramAuthClient.kt      │
│   Implemented by: DefaultTelegramTransportClient                    │
└────────────────────┬────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────────┐
│ DefaultTelegramTransportClient (transport impl)                     │
│   Wraps TDLib client, manages internal state                        │
└─────────────────────────────────────────────────────────────────────┘

                     │ Auth State Changes
                     ↓
┌─────────────────────────────────────────────────────────────────────┐
│ TelegramActivationObserver (app-v2)                                 │
│   Location: app-v2/.../TelegramActivationObserver.kt                │
│   Started in: FishItV2Application.onCreate() (line 83)              │
│   Observes: TelegramAuthRepository.authState                        │
│   Maps: Connected → setTelegramActive()                             │
└────────────────────┬────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────────┐
│ SourceActivationStore (core/catalog-sync)                           │
│   Interface: SourceActivationStore                                  │
│   Impl: DefaultSourceActivationStore (infra/work)                   │
│   Persists: Telegram ACTIVE/INACTIVE state                          │
└────────────────────┬────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────────┐
│ SourceActivationObserver (infra/work)                               │
│   Started in: FishItV2Application.onCreate() (line 82)              │
│   Triggers: Worker scheduling when sources become ACTIVE            │
└────────────────────┬────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────────┐
│ CatalogSyncOrchestratorWorker (app-v2/work)                         │
│   Builds worker chain for active sources                            │
│   Order: Xtream → Telegram → IO                                     │
└────────────────────┬────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────────┐
│ Telegram Worker Chain                                               │
│   1. TelegramAuthPreflightWorker                                    │
│      - Verifies auth state is Connected                             │
│   2. TelegramFullHistoryScanWorker / TelegramIncrementalScanWorker  │
│      - Calls CatalogSyncService.syncTelegram()                      │
└────────────────────┬────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────────┐
│ CatalogSyncService (core/catalog-sync)                              │
│   Calls: TelegramCatalogPipeline.scanCatalog()                      │
└────────────────────┬────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────────┐
│ TelegramCatalogPipelineImpl (pipeline/telegram)                     │
│   Pre-flight checks:                                                │
│     1. authState.first() == TdlibAuthState.Ready                    │
│     2. connectionState.first() == TelegramConnectionState.Connected │
│   Uses: TelegramPipelineAdapter                                     │
└────────────────────┬────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────────────┐
│ TelegramPipelineAdapter (pipeline/telegram)                         │
│   Wraps: TelegramTransportClient                                    │
│   Fetches: Chats and messages                                       │
│   Converts: TgMessage → TelegramMediaItem → RawMediaMetadata        │
└─────────────────────────────────────────────────────────────────────┘
```

**✅ Verification Status:** Complete architecture trace confirmed. All components correctly wired.

---

## 2. DTO Chain Verification (No JSON)

### 2.1 Data Transformation Path

```
TDLib Native (C++)
  TdApi.UpdateNewMessage
  TdApi.Message
  TdApi.MessageVideo
  TdApi.File
    ↓
Transport Layer (infra/transport-telegram/api)
  TgMessage
  TgContent.Video / TgContent.Document
  TgFile
  TgPhotoSize
    ↓
Pipeline Layer (pipeline/telegram/model)
  TelegramMediaItem (internal DTO)
    - remoteId: String (stable)
    - fileName: String
    - mimeType: String
    - fileSize: Long
    - duration: Int
    ↓
Core Model (via toRawMediaMetadata())
  RawMediaMetadata
    - sourceKey: SourceKey
    - title: String
    - streamingUrl: String (tg://media/...)
    - imageRefs: List<ImageRef>
    - mediaType: MediaType
    - externalIds: Map<String, String>
```

### 2.2 JSON Usage Analysis

**Search Results:**
```bash
# No JSON decoders in transport-telegram
grep -rn "JSON\|json\|JsonDecoder" infra/transport-telegram --include="*.kt"
# Result: No matches

# No JSON decoders in pipeline/telegram
grep -rn "JSON\|json\|JsonDecoder" pipeline/telegram --include="*.kt"
# Result: Only references to JSON exports used for test data
```

**✅ Confirmation:** No JSON parsing in the Telegram data path. All conversions are direct Kotlin type mappings.

---

## 3. Potential Blockers Identified

### 3.1 Auth State Transition Blocker

**Location:** `TelegramCatalogPipelineImpl.scanCatalog()` (line 52)

**Check:**
```kotlin
val auth = adapter.authState.first()
if (auth !is TdlibAuthState.Ready) {
    // BLOCKER: Cannot proceed
}
```

**Possible Causes:**
- TDLib client not fully initialized
- Auth state stuck in Connecting/Idle
- Auth flow interrupted or incomplete
- TdlibAuthState.Ready never emitted

**Diagnostic Logging Added:**
```kotlin
UnifiedLog.i(TAG) { "scanCatalog called - checking auth state..." }
val auth = adapter.authState.first()
UnifiedLog.i(TAG) { "Auth state: $auth (isReady=${auth is TdlibAuthState.Ready})" }
```

### 3.2 Connection State Blocker

**Location:** `TelegramCatalogPipelineImpl.scanCatalog()` (line 65)

**Check:**
```kotlin
val conn = adapter.connectionState.first()
if (conn !is TelegramConnectionState.Connected) {
    // BLOCKER: Cannot proceed
}
```

**Possible Causes:**
- Network connectivity issues
- TDLib connection not established
- Connection state stuck in Connecting
- TelegramConnectionState.Connected never emitted

**Diagnostic Logging Added:**
```kotlin
UnifiedLog.i(TAG) { "Auth OK - checking connection state..." }
val conn = adapter.connectionState.first()
UnifiedLog.i(TAG) { "Connection state: $conn (isConnected=${conn is TelegramConnectionState.Connected})" }
```

### 3.3 Source Activation Blocker

**Location:** `CatalogSyncOrchestratorWorker.doWork()` (line 65)

**Check:**
```kotlin
val activeSources = sourceActivationStore.getActiveSources()
if (SourceId.TELEGRAM !in activeSources) {
    // BLOCKER: Telegram workers not enqueued
}
```

**Possible Causes:**
- TelegramActivationObserver not started
- Auth state Connected not triggering setTelegramActive()
- SourceActivationStore not persisting state
- Race condition in observer startup

**Verification:**
- ✅ TelegramActivationObserver IS started (FishItV2Application.kt line 83)
- ✅ SourceActivationObserver IS started (FishItV2Application.kt line 82)
- ✅ State mapping logic exists (TelegramActivationObserver.kt line 64)

**Diagnostic Logging Added:**
```kotlin
UnifiedLog.i(TAG) { "Active sources check: $activeSources" }
UnifiedLog.i(TAG) { "✅ Telegram is ACTIVE - building Telegram worker chain" }
// OR
UnifiedLog.w(TAG) { "⚠️ Telegram is NOT ACTIVE - skipping Telegram workers" }
```

### 3.4 Stub URL Blocker

**Location:** `TelegramMediaItem.toTelegramUri()` (line 219)

**Issue:**
```kotlin
// OLD CODE (allows stubs in production)
fun toTelegramUri(): String =
    if (remoteId != null) {
        "tg://media/$encodedRemoteId?chatId=$chatId&messageId=$messageId"
    } else {
        "tg://stub/$id?chatId=$chatId&messageId=$messageId"  // ❌ BLOCKER
    }
```

**Fix Applied:**
```kotlin
fun toTelegramUri(): String {
    if (remoteId != null) {
        val encodedRemoteId = java.net.URLEncoder.encode(remoteId, "UTF-8")
        return "tg://media/$encodedRemoteId?chatId=$chatId&messageId=$messageId"
    }
    
    // Production guard
    if (ALLOW_STUB_URLS) {
        return "tg://stub/$id?chatId=$chatId&messageId=$messageId"
    }
    
    error("Production BLOCKER: remoteId is null for message $messageId in chat $chatId")
}

companion object {
    @JvmField
    var ALLOW_STUB_URLS: Boolean = false  // Default: production-safe
}
```

**✅ Status:** Production guard added. Stub URLs now blocked by default.

---

## 4. Changes Made

### 4.1 Enhanced Diagnostic Logging

**File:** `pipeline/telegram/.../TelegramCatalogPipelineImpl.kt`

**Changes:**
- Added INFO-level logging before auth/connection checks
- Shows exact state values and boolean evaluation
- Added "BLOCKER:" prefix to error messages
- Added "Pre-flight checks passed" confirmation

**File:** `app-v2/.../TelegramActivationObserver.kt`

**Changes:**
- Added state mapping debug logging
- Enhanced activation handling with ✅/⚠️/❌ indicators
- Logs when `setTelegramActive()` is called
- Confirms workers should be scheduled

**File:** `app-v2/work/CatalogSyncOrchestratorWorker.kt`

**Changes:**
- Shows all active sources with boolean flags
- Logs when Telegram is ACTIVE vs NOT ACTIVE
- Shows number of workers being enqueued
- Clear separation between sources

**File:** `app-v2/work/TelegramAuthPreflightWorker.kt`

**Changes:**
- Shows auth state with `isConnected` boolean
- Enhanced failure messages with ✅/❌ indicators
- Clear differentiation between retry and failure cases

### 4.2 Production Safety Improvements

**File:** `pipeline/telegram/model/TelegramMediaItem.kt`

**Changes:**
- Added `ALLOW_STUB_URLS` flag (default: `false`)
- Production guard throws error when `remoteId` is null
- Prevents stub URLs from leaking into production
- Clear error message explains the blocker

---

## 5. Diagnostic Log Points

### 5.1 Expected Log Sequence (Success Path)

```
[TelegramActivationObserver] TelegramActivationObserver starting
[TelegramActivationObserver] Auth state mapped: Connected → Active
[TelegramActivationObserver] ✅ Telegram auth ready → calling sourceActivationStore.setTelegramActive()
[TelegramActivationObserver] ✅ Telegram source marked ACTIVE - workers should be scheduled

[CatalogSyncOrchestratorWorker] START sync_run_id=... mode=...
[CatalogSyncOrchestratorWorker] Active sources check: [TELEGRAM] (isEmpty=false)
[CatalogSyncOrchestratorWorker] Active sources: [TELEGRAM] (TELEGRAM=true, XTREAM=false, IO=false)
[CatalogSyncOrchestratorWorker] ✅ Telegram is ACTIVE - building Telegram worker chain
[CatalogSyncOrchestratorWorker] ✅ Enqueued 2 Telegram workers (preflight + scan)
[CatalogSyncOrchestratorWorker] SUCCESS duration_ms=... (enqueued workers for sources: [TELEGRAM])

[TelegramAuthPreflightWorker] START sync_run_id=... mode=... source=TELEGRAM
[TelegramAuthPreflightWorker] Checking auth state: Connected (isConnected=true)
[TelegramAuthPreflightWorker] ✅ SUCCESS duration_ms=... (TDLib authorized)

[TelegramFullHistoryScanWorker] START sync_run_id=... mode=... source=TELEGRAM kind=FULL_HISTORY
[TelegramFullHistoryScanWorker] Sync started for source: TELEGRAM

[TelegramCatalogPipelineImpl] scanCatalog called - checking auth state...
[TelegramCatalogPipelineImpl] Auth state: Ready (isReady=true)
[TelegramCatalogPipelineImpl] Auth OK - checking connection state...
[TelegramCatalogPipelineImpl] Connection state: Connected (isConnected=true)
[TelegramCatalogPipelineImpl] Pre-flight checks passed - starting catalog scan
[TelegramCatalogPipelineImpl] Starting catalog scan for N chats
```

### 5.2 Blocker Detection Patterns

**Blocker 1: Auth State Not Ready**
```
[TelegramCatalogPipelineImpl] scanCatalog called - checking auth state...
[TelegramCatalogPipelineImpl] Auth state: Connecting (isReady=false)
[TelegramCatalogPipelineImpl] BLOCKER: Cannot scan - auth state is Connecting (expected TdlibAuthState.Ready)
```

**Blocker 2: Connection State Not Connected**
```
[TelegramCatalogPipelineImpl] Auth state: Ready (isReady=true)
[TelegramCatalogPipelineImpl] Auth OK - checking connection state...
[TelegramCatalogPipelineImpl] Connection state: WaitingForNetwork (isConnected=false)
[TelegramCatalogPipelineImpl] BLOCKER: Cannot scan - connection state is WaitingForNetwork (expected TelegramConnectionState.Connected)
```

**Blocker 3: Telegram Not Activated**
```
[CatalogSyncOrchestratorWorker] Active sources check: [] (isEmpty=true)
[CatalogSyncOrchestratorWorker] SUCCESS duration_ms=... (no active sources, nothing to sync)
```

Or:
```
[CatalogSyncOrchestratorWorker] Active sources check: [XTREAM] (isEmpty=false)
[CatalogSyncOrchestratorWorker] Active sources: [XTREAM] (TELEGRAM=false, XTREAM=true, IO=false)
[CatalogSyncOrchestratorWorker] ⚠️ Telegram is NOT ACTIVE - skipping Telegram workers
```

**Blocker 4: Stub URL in Production**
```
Exception: Production BLOCKER: remoteId is null for message 123 in chat 456. 
This indicates incomplete message parsing from TDLib. 
Stub URLs are not allowed in production builds.
```

---

## 6. Verification Steps

### 6.1 Build Verification

```bash
./gradlew :pipeline:telegram:compileDebugKotlin :app-v2:compileDebugKotlin --no-daemon
```

**Result:** ✅ BUILD SUCCESSFUL in 59s

### 6.2 Test Verification

```bash
./gradlew :pipeline:telegram:testDebugUnitTest --no-daemon
```

**Result:** ✅ BUILD SUCCESSFUL in 45s

---

## 7. Next Steps for User

### 7.1 Enable Logging

1. Launch the app
2. Navigate to Settings
3. Enable "Debug Logging" or "Global Debug" toggle
4. Logs will be captured via UnifiedLog

### 7.2 Reproduce the Issue

1. Fresh app install or logged-out state
2. Go through Telegram onboarding
3. Complete auth flow (phone, code, password if needed)
4. Wait for catalog sync to trigger
5. Check if content appears

### 7.3 Collect Logs

**Via ADB:**
```bash
adb logcat -s TelegramActivationObserver TelegramCatalogPipelineImpl CatalogSyncOrchestratorWorker TelegramAuthPreflightWorker
```

**Via In-App Log Viewer:**
- Navigate to Settings → Diagnostics → View Logs
- Filter by "Telegram" or "Catalog"
- Export logs for analysis

### 7.4 Analyze Logs

Look for:
1. **Auth State Progression:**
   - Does `TelegramAuthState` reach `Connected`?
   - Does `TdlibAuthState` reach `Ready`?

2. **Connection State:**
   - Does `TelegramConnectionState` reach `Connected`?

3. **Activation:**
   - Is `setTelegramActive()` called?
   - Do logs show "Telegram is ACTIVE"?

4. **Worker Execution:**
   - Are Telegram workers enqueued?
   - Does `TelegramAuthPreflightWorker` succeed?
   - Does `TelegramFullHistoryScanWorker` start?

5. **Pipeline Execution:**
   - Does `scanCatalog` get called?
   - Do pre-flight checks pass?
   - Are chats discovered?

---

## 8. Recommendations

### 8.1 Immediate Actions

1. **Test with enhanced logging** to identify exact blocker
2. **Verify TDLib initialization** completes successfully
3. **Check network connectivity** during auth and sync
4. **Monitor state transitions** from Idle → Connecting → Ready → Connected

### 8.2 Future Improvements

1. **Health Check Endpoint:**
   - Add debug screen showing current state of all components
   - Display: Auth state, Connection state, Active sources, Last sync time

2. **Retry Logic:**
   - Add automatic retry for transient failures
   - Exponential backoff for connection issues

3. **User Feedback:**
   - Show progress indicator during auth
   - Display state (Connecting, Authorizing, Syncing)
   - Show number of items discovered

4. **Error Reporting:**
   - Surface specific blockers to user
   - Provide actionable error messages
   - Link to troubleshooting guide

---

## 9. Summary

### ✅ Verified Working

- Architecture layer boundaries correct
- DTO chain has no JSON parsing
- All observers started in Application.onCreate()
- Worker chain correctly configured
- State mapping logic implemented
- Production guard added for stub URLs

### ⚠️ Potential Blockers (Now Diagnosable)

1. Auth state may not reach `TdlibAuthState.Ready`
2. Connection state may not reach `TelegramConnectionState.Connected`
3. Source activation may not persist or trigger workers
4. remoteId may be null causing failed URL generation

### 🔍 Next Investigation

The enhanced logging will pinpoint the exact blocker. User should:
1. Run the app with logging enabled
2. Complete Telegram auth flow
3. Check logs against patterns in Section 5.2
4. Report which blocker pattern appears

---

## Appendix A: File Modifications

| File | Lines Changed | Type |
|------|---------------|------|
| `pipeline/telegram/.../TelegramCatalogPipelineImpl.kt` | 45-76 | Enhanced logging |
| `app-v2/.../TelegramActivationObserver.kt` | 64-96 | State logging + activation logging |
| `app-v2/work/CatalogSyncOrchestratorWorker.kt` | 65-108 | Active sources logging |
| `app-v2/work/TelegramAuthPreflightWorker.kt` | 70-123 | Auth check logging |
| `pipeline/telegram/model/TelegramMediaItem.kt` | 213-244 | Production guard for stubs |

**Total:** 5 files modified, ~80 lines changed/added

---

## Appendix B: Contract Compliance

All changes comply with:
- ✅ `AGENTS.md` Section 4 (Layer Boundaries)
- ✅ `AGENTS.md` Section 11 (Pre/Post-Change Checklists)
- ✅ `GLOSSARY_v2_naming_and_modules.md` (Naming conventions)
- ✅ `LOGGING_CONTRACT_V2.md` (UnifiedLog usage)
- ✅ `MEDIA_NORMALIZATION_CONTRACT.md` (No pipeline normalization)

No architecture violations introduced.

---

**End of Report**
