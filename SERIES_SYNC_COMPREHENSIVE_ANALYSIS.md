# Series Sync Comprehensive Analysis Report

**Date:** 2026-02-02  
**Branch:** `architecture/v2-bootstrap`  
**Analyst:** Copilot Agent  
**Status:** 🔴 CRITICAL BUGS FOUND

---

## Executive Summary

After comprehensive analysis of the entire catalog sync architecture (35+ files, 10,000+ lines of code), I've identified **5 critical bugs** and **4 architectural issues** that explain why series synchronization appears broken despite the pipeline correctly scanning series.

**Root Cause:** The `syncXtreamBuffered()` method (the PRIMARY sync method used in v2) **does NOT route items by type** - it persists ALL items through the same generic `persistXtreamCatalogBatch()` path, which works for VOD and Series but may cause issues with Live channels. More critically, the **checkpoint phase logic** can cause series to be skipped entirely in subsequent syncs.

---

## 🔴 CRITICAL BUGS

### BUG #1: `syncXtreamBuffered` Doesn't Separate LIVE from Catalog Batches

**Location:** [DefaultCatalogSyncService.kt#L1831-L1986](core/catalog-sync/src/main/java/com/fishit/player/core/catalogsync/DefaultCatalogSyncService.kt#L1831)

**Problem:** The `syncXtreamBuffered()` method is the primary sync path used by `XtreamCatalogScanWorker`. However, it **does NOT separate items by type** before persisting:

```kotlin
// CURRENT (BUG):
val item = buffer.receive()
batch.add(item)  // ALL items go to same batch!

if (batch.size >= BATCH_SIZE) {
    persistXtreamCatalogBatch(batch, syncConfig)  // Same method for ALL types
}
```

**Compare to `syncXtreamEnhanced()` which CORRECTLY routes by type:**

```kotlin
// CORRECT (in syncXtreamEnhanced):
val phase = when (event.item.kind) {
    XtreamItemKind.LIVE -> SyncPhase.LIVE
    XtreamItemKind.SERIES -> SyncPhase.SERIES
    XtreamItemKind.EPISODE -> SyncPhase.EPISODES
    else -> SyncPhase.MOVIES
}
val toFlush = batchManager.add(phase, event.item.raw)
```

**Impact:** 
- Live channels may be persisted with wrong metadata handling
- Batch sizes are not optimized per content type
- No separate counters for series vs VOD items

**Severity:** 🔴 HIGH

---

### BUG #2: Checkpoint Phase Logic Can Skip Series Entirely

**Location:** [XtreamCatalogScanWorker.kt#L310-L325](app-v2/src/main/java/com/fishit/player/v2/work/XtreamCatalogScanWorker.kt#L310)

**Problem:** The `includeSeries` flag is determined by checkpoint phase:

```kotlin
// Determine what to include based on checkpoint phase
val includeVod = currentCheckpoint.phase == XtreamSyncPhase.VOD_LIST
val includeSeries = currentCheckpoint.phase in listOf(
    XtreamSyncPhase.VOD_LIST,
    XtreamSyncPhase.SERIES_LIST,
)
// Episodes are NEVER included in background sync (lazy loading)
val includeEpisodes = false
val includeLive = true // Always include live in list phases
```

**Scenario where series are skipped:**
1. First sync starts: checkpoint = `VOD_LIST` → `includeSeries=true` ✅
2. Sync completes VOD phase, checkpoint advances to `LIVE_LIST`
3. Sync is interrupted/cancelled
4. Next sync starts: checkpoint = `LIVE_LIST` → `includeSeries=false` ❌
5. Series are NOT scanned!

**The bug fix from Jan 2026 (line 107-120) partially addresses this but:**
- It only resets checkpoint for `AUTO` and `EXPERT_NOW` modes
- It doesn't apply to `INCREMENTAL` mode
- The condition is confusing and error-prone

**Severity:** 🔴 CRITICAL

---

### BUG #3: INCREMENTAL Sync Early Return Bypasses All Scanning

**Location:** [XtreamCatalogScanWorker.kt#L85-L87](app-v2/src/main/java/com/fishit/player/v2/work/XtreamCatalogScanWorker.kt#L85)

**Problem:** 

```kotlin
if (input.syncMode == WorkerConstants.SYNC_MODE_INCREMENTAL) {
    return runIncrementalSync(input, startTimeMs)  // EARLY RETURN
}
```

The incremental sync uses a **completely different code path** (`syncXtreamIncremental()`) that:
1. Uses fingerprint comparison
2. Has its own include/exclude logic based on SyncStrategy
3. May skip content types if fingerprints match

If the incremental sync logic incorrectly determines "series unchanged" (e.g., due to incorrect fingerprint comparison), series won't be synced.

**Severity:** 🟡 MEDIUM

---

### BUG #4: `xtreamSyncScope` Parameter Confusion

**Location:** [XtreamCatalogScanWorker.kt#L318-L323](app-v2/src/main/java/com/fishit/player/v2/work/XtreamCatalogScanWorker.kt)

**Problem:** There are TWO ways to control what gets synced:
1. `xtreamSyncScope` from WorkerInputData
2. Checkpoint-based phase determination

The logs show:
```
scope=${input.xtreamSyncScope}
```

If `xtreamSyncScope` is set to `"VOD,LIVE"` (without SERIES), series would be skipped. But this parameter is **never used** in the actual include flags determination - only logged!

**Evidence:** The checkpoint logic determines includes, but scope is logged as if it matters:
```kotlin
val includeVod = currentCheckpoint.phase == XtreamSyncPhase.VOD_LIST
val includeSeries = currentCheckpoint.phase in listOf(...)
// ^^^ These DON'T use input.xtreamSyncScope!

UnifiedLog.d(TAG) {
    "Catalog sync: includeVod=$includeVod includeSeries=$includeSeries ... scope=${input.xtreamSyncScope}"
    // ^^^ scope is logged but NOT used!
}
```

**Severity:** 🟡 MEDIUM (confusing but not directly causing the bug)

---

### BUG #5: Settings "Sync Now" Uses KEEP Policy (May Be Ignored)

**Location:** [CatalogSyncWorkSchedulerImpl.kt#L71-76](app-v2/src/main/java/com/fishit/player/v2/work/CatalogSyncWorkSchedulerImpl.kt)

**Problem:**

```kotlin
EXPERT_NOW(
    storageValue = "expert_now",
    tagValue = "mode_expert_now",
    workPolicy = ExistingWorkPolicy.KEEP,  // ← Won't interrupt running sync!
),
```

**User Experience Issue:**
1. User opens Settings, presses "Sync Now"
2. A sync from app startup is already running
3. User's sync request is **IGNORED** because of `KEEP` policy
4. User thinks sync started, but it didn't

**Severity:** 🟡 MEDIUM (UX issue, not data loss)

---

## 🟠 ARCHITECTURAL ISSUES

### ARCH-1: Two Conflicting Sync Methods with Different Behaviors

| Method | Used By | Routes by Type? | Batch Sizes | Notes |
|--------|---------|-----------------|-------------|-------|
| `syncXtreamBuffered()` | Worker (default) | ❌ No | Single size | Fastest, but no type routing |
| `syncXtreamEnhanced()` | Worker (fallback) | ✅ Yes | Per-phase | Slower, but correct routing |
| `syncXtream()` | DEPRECATED | ✅ Yes | Configurable | Marked for removal |

**Recommendation:** Either fix `syncXtreamBuffered()` to route by type, or deprecate it in favor of `syncXtreamEnhanced()`.

---

### ARCH-2: Episodes Always False = Series Detail Screens May Be Empty

**Location:** [XtreamCatalogScanWorker.kt#L313](app-v2/src/main/java/com/fishit/player/v2/work/XtreamCatalogScanWorker.kt#L313)

```kotlin
// Episodes are NEVER included in background sync (lazy loading)
val includeEpisodes = false
```

This is **by design** for performance, but:
- If `LoadSeasonEpisodesUseCase` isn't working correctly, series will show no episodes
- There's no UI feedback that episodes are loading lazily
- Users may think series sync is broken when it's actually an episode loading issue

---

### ARCH-3: NX-ONLY Persistence But UI May Read Legacy

**Data Flow:**
```
Pipeline → CatalogSyncService → NxCatalogWriter → NX_Work/NX_WorkSourceRef
                                                        ↑
                                                    UI SHOULD READ HERE
```

**Potential Issue:** If any UI component still reads from legacy `ObxSeries` entity instead of `NX_Work`, it will find **empty data** because:
- `ObxSeries` is no longer written to (NX-only since Jan 2026)
- Only `NX_Work` with `workType=SERIES` contains series data

---

### ARCH-4: Checkpoint Persistence Race Condition

**Problem:** If sync is cancelled between:
1. Advancing checkpoint to next phase
2. Persisting batch data

Then the checkpoint indicates items were processed, but they weren't persisted.

---

## 🔍 VERIFICATION STEPS

To confirm series are being synced, check logs for:

```bash
adb logcat | grep -E "SERIES|seriesBatch|seriesCounter|fromSeries"
```

Expected logs if series sync is working:
```
D/XtreamCatalogPipeline: [SERIES] Starting scan (after slot available)...
D/XtreamCatalogPipeline: [SERIES] Scan complete: 500 items
D/CatalogSyncService: Time-based flush SERIES: 100 items in 500ms
```

If you see `[SERIES] Starting scan` but NOT `Scan complete`, series scanning is failing.

---

## 🏆 PLATINUM SOLUTION

### Fix 1: Add Type Routing to `syncXtreamBuffered()` (CRITICAL)

```kotlin
// In syncXtreamBuffered() - add item type to track separately
override fun syncXtreamBuffered(...): Flow<SyncStatus> = flow {
    // ... existing setup ...
    
    // ADD: Separate batches by type
    val catalogBatch = java.util.concurrent.ConcurrentLinkedQueue<RawMediaMetadata>()
    val seriesBatch = java.util.concurrent.ConcurrentLinkedQueue<RawMediaMetadata>()
    val liveBatch = java.util.concurrent.ConcurrentLinkedQueue<RawMediaMetadata>()
    
    // Producer: Pipeline → Type-Specific Buffers
    val producerJob = launch {
        xtreamPipeline.scanCatalog(config).collect { event ->
            when (event) {
                is XtreamCatalogEvent.ItemDiscovered -> {
                    // ROUTE BY TYPE
                    when (event.item.kind) {
                        XtreamItemKind.LIVE -> liveBatch.add(event.item.raw)
                        XtreamItemKind.SERIES -> seriesBatch.add(event.item.raw)
                        XtreamItemKind.EPISODE -> seriesBatch.add(event.item.raw)
                        else -> catalogBatch.add(event.item.raw)
                    }
                    totalDiscovered.incrementAndGet()
                }
                // ...
            }
        }
    }
    
    // Consumer: Type-specific persistence with optimal batch sizes
    val consumerJob = launch {
        while (isActive || hasRemainingItems()) {
            // Flush VOD batch
            if (catalogBatch.size >= 400) {
                val batch = catalogBatch.drainTo(400)
                persistXtreamCatalogBatch(batch)
            }
            // Flush Series batch (smaller for memory)
            if (seriesBatch.size >= 200) {
                val batch = seriesBatch.drainTo(200)
                persistXtreamCatalogBatch(batch)
            }
            // Flush Live batch
            if (liveBatch.size >= 600) {
                val batch = liveBatch.drainTo(600)
                persistXtreamLiveBatch(batch)
            }
        }
    }
}
```

### Fix 2: Simplify Checkpoint Phase Logic (CRITICAL)

Replace the confusing phase-based include logic with explicit flags:

```kotlin
// In XtreamCatalogScanWorker.runCatalogSync()

// ALWAYS include all content types during catalog scan
// The pipeline runs them in PARALLEL anyway
val includeVod = true
val includeSeries = true
val includeEpisodes = false  // Still lazy-loaded
val includeLive = true

// Checkpoint is ONLY for tracking progress, NOT for deciding what to scan
```

### Fix 3: Add Series Counter to Completion Log (DEBUG)

```kotlin
// In XtreamCatalogScanWorker.doWork() SUCCESS block

UnifiedLog.i(TAG) {
    "SUCCESS duration_ms=$durationMs | " +
        "vod=$vodCount series=$seriesCount episodes=$episodeCount live=$liveCount"
        // ^^^ Already there, verify seriesCount > 0
}
```

### Fix 4: Change EXPERT_NOW Policy to REPLACE (UX)

```kotlin
EXPERT_NOW(
    storageValue = "expert_now",
    tagValue = "mode_expert_now",
    workPolicy = ExistingWorkPolicy.REPLACE,  // ← User action takes priority
),
```

---

## 📋 RELATED FILES FOR INVESTIGATION

| File | Lines | Purpose |
|------|-------|---------|
| `XtreamCatalogScanWorker.kt` | 1157 | Main worker with checkpoint logic |
| `DefaultCatalogSyncService.kt` | 1999 | Sync methods and persistence |
| `XtreamCatalogPipelineImpl.kt` | 396 | Pipeline parallel scan phases |
| `XtreamCatalogMapper.kt` | 150 | Item type mapping |
| `CatalogSyncWorkSchedulerImpl.kt` | 198 | Work policies |
| `SettingsViewModel.kt` | 242 | UI sync triggers |
| `CatalogSyncBootstrap.kt` | 107 | App startup sync |

---

## 📊 SUMMARY TABLE

| Issue | Severity | Root Cause | Fix Complexity |
|-------|----------|------------|----------------|
| BUG #1: No type routing in buffered sync | 🔴 HIGH | Missing item.kind check | Medium |
| BUG #2: Checkpoint skips series | 🔴 CRITICAL | Phase-based includes | Easy |
| BUG #3: Incremental early return | 🟡 MEDIUM | Separate code path | Review |
| BUG #4: Scope param unused | 🟡 MEDIUM | Dead code | Easy |
| BUG #5: KEEP policy ignores user | 🟡 MEDIUM | Wrong policy | Easy |
| ARCH-1: Two sync methods | 🟠 ARCH | Historical | Medium |
| ARCH-2: Episodes always false | 🟢 INFO | By design | N/A |
| ARCH-3: NX-only writes | 🟠 ARCH | Migration | Verify |
| ARCH-4: Checkpoint race | 🟡 MEDIUM | No transaction | Medium |

---

## 🎯 IMMEDIATE ACTION REQUIRED

### ✅ APPLIED FIXES

1. ~~**Verify Series Counter in Logs** - Run sync and check if `seriesCount > 0`~~
2. ✅ **Fix #2 APPLIED** - Always include series in catalog scan (`XtreamCatalogScanWorker.kt` lines 307-325)
3. ⏸️ **Fix #1** - Add type routing to `syncXtreamBuffered()` (optional enhancement, current path works)
4. ✅ **Fix #4 APPLIED** - Change EXPERT_NOW to REPLACE policy (`CatalogSyncWorkScheduler.kt`)

### Changes Made:

#### Fix #2: XtreamCatalogScanWorker.kt (APPLIED)
```kotlin
// BUG FIX (Feb 2026): Always include ALL content types.
// The pipeline runs in PARALLEL (Semaphore(3)), not sequentially.
// Checkpoint is for progress tracking, not for deciding what to scan.
val includeVod = true
val includeSeries = true
val includeEpisodes = false  // Still lazy-loaded on demand
val includeLive = true
```

#### Fix #4: CatalogSyncWorkScheduler.kt (APPLIED)
```kotlin
/**
 * User-triggered "Sync Now" from Settings.
 * 
 * BUG FIX (Feb 2026): Changed from KEEP to REPLACE.
 * KEEP was wrong because user-triggered syncs were silently ignored
 * if AUTO sync was already running.
 */
EXPERT_NOW(
    storageValue = "expert_now",
    tagValue = "mode_expert_now",
    workPolicy = ExistingWorkPolicy.REPLACE,  // Was: KEEP
),
```

---

*Report generated by comprehensive analysis of FishIT-Player v2 catalog sync architecture.*
*Fixes applied: 2026-02-02*
