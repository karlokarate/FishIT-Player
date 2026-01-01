# WorkManager Parallel Sources Fix

**Date:** 2026-01-01  
**Branch:** `copilot/fix-telegram-sync-blocking`  
**Issue:** Telegram workers stay BLOCKED when activated after Xtream

---

## Problem

When Xtream is active first and Telegram becomes active later, Telegram workers remain permanently BLOCKED. WorkManager snapshot showed:
- `TelegramAuthPreflightWorker`: BLOCKED
- `TelegramIncrementalScanWorker`: BLOCKED
- `XtreamCatalogScanWorker`: BLOCKED (waiting on retrying XtreamPreflightWorker)
- Orchestrator reports SUCCESS anyway

## Root Causes

### 1. Sequential Chaining
The orchestrator used sequential chaining:
```kotlin
// OLD CODE - SEQUENTIAL
var workContinuation: WorkContinuation? = null

if (XTREAM in activeSources) {
    workContinuation = workManager.beginUniqueWork(...).then(xtreamChain)
}

if (TELEGRAM in activeSources) {
    workContinuation = workContinuation.then(telegramChain)  // ❌ BLOCKS on Xtream
}
```

This made Telegram wait for Xtream to complete, including all Xtream preflight retries.

### 2. XtreamPreflight Infinite Retry Loop
When Xtream was not configured, `XtreamAuthState.Idle` caused infinite retries:
```kotlin
// OLD CODE
is XtreamAuthState.Idle -> {
    UnifiedLog.w(TAG) { "Auth state idle, retrying" }
    Result.retry()  // ❌ INFINITE LOOP when not configured
}
```

### 3. Single Global Unique Work Name
All sources shared `catalog_sync_global`, causing cross-source blocking.

---

## Solution

### 1. Parallel Chains with Per-Source Unique Names

**New Architecture:**
```kotlin
// Each source enqueues independently
if (XTREAM in activeSources) {
    workManager
        .beginUniqueWork("catalog_sync_global_xtream", REPLACE, xtreamChain[0])
        .then(xtreamChain.drop(1))
        .enqueue()
}

if (TELEGRAM in activeSources) {
    workManager
        .beginUniqueWork("catalog_sync_global_telegram", REPLACE, telegramChain[0])
        .then(telegramChain.drop(1))
        .enqueue()
}
```

**Benefits:**
- Each source runs in its own independent chain
- Late activation (Telegram after Xtream) starts immediately
- REPLACE policy ensures fresh chain on re-activation
- No cross-source blocking

### 2. XtreamPreflight Fail-Fast on Idle

**New Semantics:**
```kotlin
is XtreamAuthState.Idle -> {
    UnifiedLog.e(TAG) {
        "FAILURE reason=not_configured state=Idle retry=false"
    }
    Result.failure(...)  // ✅ FAIL FAST, don't block other sources
}
```

**Idle now means:** Not configured or credentials missing (not "initializing")

**Transient errors still retry:** Connection errors use `Result.retry()` with bounded backoff

### 3. Clear Preflight Semantics

| Worker | State | Result | Notes |
|--------|-------|--------|-------|
| **XtreamPreflight** | `Authenticated` | `success` | Ready to scan |
| | `Failed/Expired` | `failure` | Invalid credentials |
| | `Idle` | `failure` | Not configured |
| | Connection error | `retry` | Transient, bounded backoff |
| **TelegramAuthPreflight** | `Connected` | `success` | Ready to scan |
| | `WaitingFor*` | `failure` | User action required |
| | `Disconnected/Error` | `failure` | Not authorized |
| | `Idle` | `retry` | Still initializing |

---

## Flow After Fix

### Scenario: Xtream Active First, Telegram Activates Later

**t=0**: App starts, Xtream active, Telegram inactive
```
SourceActivationStore: [XTREAM]
→ SourceActivationObserver triggers enqueueAutoSync()
→ Orchestrator enqueues catalog_sync_global_xtream
→ Xtream chain: XtreamPreflight → XtreamCatalogScan
```

**t=30s**: User completes Telegram auth
```
TelegramAuthState: Idle → Connected
→ TelegramActivationObserver calls setTelegramActive()
→ SourceActivationStore: [XTREAM, TELEGRAM]
→ SourceActivationObserver triggers enqueueAutoSync()
→ Orchestrator enqueues:
    - catalog_sync_global_xtream (REPLACE - already running)
    - catalog_sync_global_telegram (REPLACE - NEW!)
→ Telegram chain starts IMMEDIATELY: TelegramAuthPreflight → TelegramIncrementalScan
```

**Result:** Both sources run in parallel, independent of each other.

---

## Files Modified

| File | Change |
|------|--------|
| `CatalogSyncOrchestratorWorker.kt` | Parallel chains, per-source unique names, enhanced logging |
| `XtreamPreflightWorker.kt` | Idle → failure (fail-fast), updated contract docs |
| `TelegramAuthPreflightWorker.kt` | Updated contract docs (no functional change) |

---

## Testing

### Manual Verification

1. **Start with Xtream Active:**
   - Verify WorkManager shows `catalog_sync_global_xtream` chain
   - Verify Xtream workers are ENQUEUED/RUNNING

2. **Activate Telegram Later:**
   - Complete Telegram auth flow
   - Verify WorkManager shows NEW `catalog_sync_global_telegram` chain
   - Verify Telegram workers are ENQUEUED/RUNNING (NOT BLOCKED)
   - Verify both chains run in parallel

3. **Check Logs:**
   ```
   [CatalogSyncOrchestratorWorker] Enqueued Xtream chain: work_name=catalog_sync_global_xtream workers=2
   [CatalogSyncOrchestratorWorker] ✅ Enqueued Telegram chain: work_name=catalog_sync_global_telegram workers=2
   [CatalogSyncOrchestratorWorker] SUCCESS (enqueued 2 parallel chains: XTREAM, TELEGRAM)
   ```

### Expected WorkManager Snapshot

**Before Telegram activation:**
```
catalog_sync_global_xtream: RUNNING
  - XtreamPreflightWorker: RUNNING
  - XtreamCatalogScanWorker: ENQUEUED
```

**After Telegram activation:**
```
catalog_sync_global_xtream: RUNNING
  - XtreamPreflightWorker: RUNNING
  - XtreamCatalogScanWorker: ENQUEUED

catalog_sync_global_telegram: RUNNING  ← NEW!
  - TelegramAuthPreflightWorker: RUNNING  ← NOT BLOCKED
  - TelegramIncrementalScanWorker: ENQUEUED
```

---

## Architecture Compliance

- ✅ All changes in `app-v2/work` (allowed v2 path)
- ✅ No layer boundary violations
- ✅ Following `LOGGING_CONTRACT_V2.md`
- ✅ No changes to contracts or core modules
- ✅ Build successful

---

## Related Documents

- `TELEGRAM_BLOCKER_INVESTIGATION_REPORT.md` - Original investigation
- `CATALOG_SYNC_WORKERS_CONTRACT_V2` - Worker contracts (referenced in code)
- `AGENTS.md` Section 11 - Pre/Post-Change Checklists

---

**Status:** ✅ FIXED - Parallel execution with per-source unique work names
