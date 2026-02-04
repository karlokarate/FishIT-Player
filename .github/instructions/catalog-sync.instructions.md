---
applyTo: '**/catalog-sync/**,**/work/**CatalogSync*,**/work/**Sync*,**/bootstrap/*Bootstrap*'
version: '1.0'
lastUpdated: '2026-02-02'
---

# Catalog Sync Architecture Instructions

**Version:** 1.0  
**Last Updated:** 2026-02-02  
**Status:** Active

> **BINDING CONTRACT** for all catalog synchronization code in v2.
> This file is automatically applied by VS Code Copilot when editing matching paths.

## 1. Sync Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         SYNC TRIGGER LAYER                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│  CatalogSyncBootstrap     │  SourceActivationObserver  │  UI (Settings/Debug)│
│  (App Start)              │  (Source Activation)       │  (Manual Buttons)   │
└──────────────┬────────────┴─────────────┬──────────────┴──────────┬─────────┘
               │                          │                         │
               ▼                          ▼                         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CatalogSyncWorkScheduler (SSOT)                          │
│  enqueueAutoSync() │ enqueueExpertSyncNow() │ enqueueForceRescan() │ ...    │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CatalogSyncOrchestratorWorker                            │
│  Checks: SourceActivationStore.getActiveSources()                           │
│  Builds: Parallel worker chains per active source                           │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
               ┌───────────────────────┼───────────────────────┐
               ▼                       ▼                       ▼
┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────────┐
│ XtreamCatalogScan    │  │ TelegramCatalogScan  │  │ IOCatalogScan        │
│ Worker               │  │ Worker               │  │ Worker               │
└──────────────────────┘  └──────────────────────┘  └──────────────────────┘
```

## 2. Sync Triggers (7 Total)

| # | Trigger | Location | SyncMode | ExistingWorkPolicy |
|---|---------|----------|----------|-------------------|
| 1 | App-Start | `CatalogSyncBootstrap.triggerSync()` | AUTO | KEEP |
| 2 | Source-Aktivierung | `SourceActivationObserver` | AUTO | KEEP |
| 3 | Settings "Sync Now" | `SettingsViewModel.syncNow()` | EXPERT_NOW | KEEP |
| 4 | Settings "Force Rescan" | `SettingsViewModel.forceRescan()` | FORCE_RESCAN | **REPLACE** |
| 5 | Debug "Sync Now" | `DebugViewModel` | EXPERT_NOW | KEEP |
| 6 | Debug "Force Rescan" | `DebugViewModel` | FORCE_RESCAN | **REPLACE** |
| 7 | Periodic Background | `schedulePeriodicSync()` | INCREMENTAL | UPDATE |

## 3. SyncMode Definitions

| Mode | Traffic | Use Case | Replaces Running? |
|------|---------|----------|-------------------|
| **AUTO** | ~2-5 MB | First launch, source activation | No (KEEP) |
| **EXPERT_NOW** | ~2-5 MB | User-triggered refresh | No (KEEP) |
| **FORCE_RESCAN** | ~2-5 MB | User-triggered full rescan | **Yes (REPLACE)** |
| **INCREMENTAL** | ~10-50 KB | Background periodic (every 2h) | No (KEEP) |

## 4. Source Activation (CRITICAL)

### 4.1 SourceActivationStore (SSOT)

The `SourceActivationStore` is the **Single Source of Truth** for which sources are active.
Workers check this before running any sync.

```kotlin
// CatalogSyncOrchestratorWorker.doWork()
val activeSources = sourceActivationStore.getActiveSources()
if (activeSources.isEmpty()) {
    return Result.success() // "no active sources, nothing to sync"
}
```

### 4.2 Xtream Activation Flow (Optimistic Pattern)

> **CRITICAL:** Xtream uses **optimistic activation** to enable immediate manual sync.

```kotlin
// XtreamSessionBootstrap.start()
val storedConfig = xtreamCredentialsStore.read()
if (storedConfig != null) {
    // 1. IMMEDIATELY activate (optimistic) - allows manual sync buttons to work
    sourceActivationStore.setXtreamActive()
    
    // 2. Delay for UI stability (2 seconds)
    delay(SESSION_INIT_DELAY_MS)
    
    // 3. Validate credentials in background
    val result = xtreamApiClient.initialize(storedConfig.toApiConfig())
    if (result.isFailure) {
        // 4. Deactivate on validation failure
        sourceActivationStore.setXtreamInactive(SourceErrorReason.INVALID_CREDENTIALS)
    }
}
```

**Why optimistic activation?**
- Without it, XTREAM isn't active until API validation completes (~2-3 seconds)
- Manual sync buttons would fail with "no active sources" during that window
- User expects immediate response when clicking "Sync Now"

### 4.3 Telegram Activation Flow (Reactive Pattern)

Telegram uses a **reactive pattern** via `TelegramActivationObserver`:

```kotlin
// TelegramActivationObserver observes TDLib auth state
telegramAuthState.collect { state ->
    when (state) {
        is Connected -> sourceActivationStore.setTelegramActive()
        is LoggedOut, Closed -> sourceActivationStore.setTelegramInactive()
    }
}
```

## 5. App Startup Timeline

```
T=0ms     │ App starts
          │ → XtreamSessionBootstrap.start()
          │   → Credentials found? → setXtreamActive() IMMEDIATELY ✅
          │ → CatalogSyncBootstrap.start()
          │   → Waits for activeSources.isNotEmpty()
          │ → SourceActivationObserver.start()
          │   → drop(1) - skips initial emission
──────────────────────────────────────────────────────────────────
T=0-2000ms│ User can open Settings → "Sync Now" button works ✅
          │ (because XTREAM is already active)
──────────────────────────────────────────────────────────────────
T=2000ms  │ XtreamSessionBootstrap → API validation (background)
──────────────────────────────────────────────────────────────────
T=~3000ms │ Validation result:
          │   Success → Already active, nothing to change
          │   Failure → setXtreamInactive() → SourceActivationObserver
          │             sees change → cancelSync()
──────────────────────────────────────────────────────────────────
T=5000ms  │ CatalogSyncBootstrap.triggerSync()
          │ → enqueueAutoSync()
          │ → schedulePeriodicSync(2 hours)
```

## 6. Hard Rules

### 6.1 SSOT Rule
> **All sync triggers MUST go through `CatalogSyncWorkScheduler`.**
> No UI/ViewModel may call `CatalogSyncService` directly.

### 6.2 Source Check Rule
> **`CatalogSyncOrchestratorWorker` MUST check `sourceActivationStore.getActiveSources()`
> before building worker chains.**

### 6.3 Optimistic Activation Rule
> **When stored credentials exist, `XtreamSessionBootstrap` MUST call
> `setXtreamActive()` BEFORE the delay/validation.**

### 6.4 Work Policy Rules
- `AUTO`, `EXPERT_NOW`, `INCREMENTAL` → `ExistingWorkPolicy.KEEP` (don't interrupt)
- `FORCE_RESCAN` → `ExistingWorkPolicy.REPLACE` (cancel and restart)

## 7. Key Files

| File | Purpose |
|------|---------|
| `core/catalog-sync/CatalogSyncWorkScheduler.kt` | Interface (SSOT) |
| `app-v2/work/CatalogSyncWorkScheduler.kt` | WorkManager implementation |
| `app-v2/work/CatalogSyncOrchestratorWorker.kt` | Main orchestrator |
| `app-v2/bootstrap/CatalogSyncBootstrap.kt` | App-start sync trigger |
| `app-v2/bootstrap/XtreamSessionBootstrap.kt` | Xtream session + activation |
| `infra/work/SourceActivationObserver.kt` | Reacts to source changes |
| `infra/work/DefaultSourceActivationStore.kt` | DataStore persistence |
| `core/source-activation-api/SourceActivationStore.kt` | Interface |

## 8. Debugging Sync Issues

### "no active sources, nothing to sync"
**Cause:** `SourceActivationStore.getActiveSources()` returns empty set.
**Check:**
1. Are credentials stored? → `XtreamCredentialsStore.read()`
2. Did `XtreamSessionBootstrap.start()` run?
3. Did `setXtreamActive()` get called?

### Manual sync button does nothing
**Cause:** Sync was already running with `KEEP` policy, OR no active sources.
**Check:**
1. Look for "Active sources check: [] (isEmpty=true)" in logs
2. Verify optimistic activation is working

### Sync runs but finds nothing
**Cause:** Workers completed but no items were persisted.
**Check:**
1. Channel sync enabled? → `BuildConfig.CHANNEL_SYNC_ENABLED`
2. API returning data? → Check network logs
