# PLATIN Sync Continuation Strategy

> **Status:** BINDING CONTRACT  
> **Created:** 2026-01-30  
> **Module:** `infra/api-priority`

---

## Executive Summary

This document defines the **Platin-grade strategy** for how background sync should continue after yielding for high-priority user actions (detail enrichment, playback).

**Core Principle:** User-initiated actions ALWAYS have priority. Background sync is a "polite citizen" that yields immediately and resumes seamlessly.

---

## 1. Priority Hierarchy (Canonical)

```
CRITICAL_PLAYBACK (100)
    └─ Exclusive: ONE operation at a time
    └─ Blocks ALL other operations
    └─ Timeout: 30 seconds default
    └─ Use case: Pre-playback enrichment (ensureEnriched)

HIGH_USER_ACTION (50)
    └─ Concurrent: Multiple allowed
    └─ Pauses BACKGROUND operations
    └─ No timeout (completes normally)
    └─ Use case: Tile click → enrichImmediate()

BACKGROUND_SYNC (0)
    └─ Yields to HIGH and CRITICAL
    └─ Resumes via awaitHighPriorityComplete()
    └─ Use case: XtreamCatalogScanWorker
```

---

## 2. Sync Continuation Behavior

### 2.1. When Sync Yields

**Trigger:** `shouldYield()` returns `true` when:
- Any HIGH_USER_ACTION operation is active
- Any CRITICAL_PLAYBACK operation is active

**Where Sync Checks:**
- `collectEnhanced()` – after each category batch
- `syncSeriesBatch()` – after each series info fetch
- `syncVodInfoBatch()` – after each VOD info fetch  
- `syncChannelBatch()` – after each channel batch

### 2.2. Yield Protocol (Step-by-Step)

```kotlin
// In XtreamCatalogScanWorker
suspend fun processItemsWithYield(items: List<T>) {
    for ((index, item) in items.withIndex()) {
        // 1. Check if we should yield
        if (priorityDispatcher.shouldYield()) {
            UnifiedLog.d(TAG) { 
                "Yielding at index=$index for high-priority operation" 
            }
            
            // 2. Save checkpoint (optional but recommended)
            saveProgress(currentCategory, index)
            
            // 3. Wait for high-priority to complete
            priorityDispatcher.awaitHighPriorityComplete()
            
            UnifiedLog.d(TAG) { 
                "Resuming from index=$index after high-priority completed" 
            }
        }
        
        // 4. Process item
        processItem(item)
    }
}
```

### 2.3. Resume Behavior

**Immediate Resume:** Once `awaitHighPriorityComplete()` returns (all HIGH operations finished), sync continues **exactly where it left off**.

**No Restart Required:** Sync does NOT need to restart from the beginning. The checkpoint-based design allows seamless continuation.

**Debounce Handling:** Multiple rapid tile clicks (user browsing) are handled naturally:
- Each `enrichImmediate()` creates a HIGH operation
- Sync stays paused while ANY HIGH operation is active
- Only resumes when ALL HIGH operations complete

---

## 3. Edge Cases & Guarantees

### 3.1. Rapid Tile Navigation

**Scenario:** User rapidly clicks through 5 tiles in 2 seconds.

**Behavior:**
1. First click → `enrichImmediate()` starts, sync pauses
2. Clicks 2-5 → Each creates a HIGH operation
3. All 5 enrichments run concurrently (allowed for HIGH)
4. Sync resumes only after ALL 5 complete

**Result:** No API contention during browsing.

### 3.2. Playback During Sync

**Scenario:** User starts playback while sync is running.

**Behavior:**
1. Playback calls `ensureEnriched()` → CRITICAL priority
2. Sync immediately yields (checkpointed)
3. CRITICAL acquires exclusive lock
4. Playback starts with enriched metadata
5. After playback prep completes, sync resumes

**Result:** Playback never waits for sync.

### 3.3. Sync Cancellation

**Scenario:** User closes app during sync pause.

**Behavior:**
1. WorkManager cancels the Worker
2. `awaitHighPriorityComplete()` is interrupted
3. Worker exits cleanly (no crash)
4. Next sync resumes from last checkpoint

**Result:** No data loss, no corruption.

### 3.4. Network Failure During Yield

**Scenario:** Network fails while sync is paused.

**Behavior:**
1. High-priority operation fails/times out
2. `awaitHighPriorityComplete()` returns
3. Sync resumes but encounters network errors
4. Worker applies exponential backoff
5. WorkManager reschedules

**Result:** Graceful degradation.

---

## 4. Implementation Checklist

### 4.1. Already Implemented ✅

- [x] `ApiPriorityDispatcher.shouldYield()`
- [x] `ApiPriorityDispatcher.awaitHighPriorityComplete()`
- [x] `withHighPriority()` wrapper
- [x] `withCriticalPriority()` wrapper
- [x] Yield checkpoints in `XtreamCatalogScanWorker`
- [x] `enrichImmediate()` uses HIGH priority
- [x] `ensureEnriched()` uses CRITICAL priority

### 4.2. Series Enrichment (NOW IMPLEMENTED ✅)

- [x] `enrichSeriesFromXtream()` – calls `getSeriesInfo()`
- [x] `isSeriesSourceKey()` detection
- [x] `parseXtreamSeriesId()` parsing
- [x] Mapping from `XtreamSeriesInfoBlock` to `NormalizedMediaMetadata`

### 4.3. Recommended Enhancements (Future)

- [ ] **Checkpoint Persistence:** Save sync progress to DataStore so interrupted syncs resume from last batch
- [ ] **Yield Statistics:** Log how often sync yields, duration of pauses
- [ ] **Priority Metrics:** Track average enrichment latency by priority level
- [ ] **Max Pause Duration:** If sync is paused > 5 minutes, log warning and consider continuing anyway

---

## 5. Testing Scenarios

### 5.1. Unit Tests

```kotlin
@Test
fun `shouldYield returns true when HIGH operation active`()

@Test  
fun `awaitHighPriorityComplete suspends until HIGH completes`()

@Test
fun `CRITICAL blocks HIGH operations`()

@Test
fun `multiple HIGH operations all complete before resume`()
```

### 5.2. Integration Tests

```kotlin
@Test
fun `sync yields during enrichImmediate and resumes`()

@Test
fun `enrichImmediate completes within 500ms during sync`()

@Test
fun `rapid tile clicks debounce correctly`()
```

### 5.3. Manual Testing

1. Start sync → Open detail screen → Verify plot loads immediately
2. Start sync → Play item → Verify playback starts without delay
3. Browse 10 tiles rapidly → Verify no visible lag or API errors
4. Close app during sync → Reopen → Verify sync continues from checkpoint

---

## 6. Metrics & Observability

### 6.1. Key Metrics to Track

| Metric | Description | Target |
|--------|-------------|--------|
| `detail_enrich_latency_ms` | Time from tile click to plot displayed | < 500ms |
| `sync_yield_count` | Number of times sync yielded per session | Info only |
| `sync_pause_duration_ms` | Total time sync was paused | < 30s typical |
| `playback_ready_latency_ms` | Time from play click to video start | < 1000ms |

### 6.2. Log Tags

- `DetailEnrichment` – All enrichment operations
- `ApiPriority` – Priority acquisition/release
- `XtreamSync` – Sync progress and yield events

---

## 7. Summary

The Platin Sync Continuation Strategy ensures:

1. **User-First:** Detail fetch and playback ALWAYS get priority
2. **Seamless Resume:** Sync continues exactly where it paused
3. **No Contention:** API calls don't compete during user actions
4. **Graceful Degradation:** Handles all edge cases cleanly

This is the **gold standard** for balancing background work with user interactivity.
