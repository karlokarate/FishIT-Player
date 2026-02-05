# Incremental Sync Contract V1

**Status:** BINDING  
**Created:** 2025-01-XX  
**Scope:** Xtream Catalog Sync  
**Lifecycle:** DELETE AFTER IMPLEMENTATION  

---

## 1. Executive Summary

This contract defines the implementation requirements to enable **true incremental sync** for the Xtream catalog pipeline. The core bug is:

> **`IncrementalSyncDecider` returns a `SyncStrategy`, but `DefaultXtreamSyncService.executePhase()` ignores it and processes ALL items.**

### Key Finding: Infrastructure EXISTS

| Component | Status | Location |
|-----------|--------|----------|
| `NX_SyncCheckpoint` entity | ✅ EXISTS | `SyncTrackingEntities.kt:62-152` |
| `NX_ItemFingerprint` entity | ✅ EXISTS | `SyncTrackingEntities.kt:187-262` |
| `SyncCheckpointRepository` | ✅ EXISTS | `SyncCheckpointRepository.kt` |
| `FingerprintRepository` | ✅ EXISTS | `FingerprintRepository.kt` |
| `IncrementalSyncDecider` | ✅ EXISTS | `IncrementalSyncDecider.kt` |
| Tier 3/4 filtering in executePhase | ❌ MISSING | **This is the bug** |

**Effort:** Wiring, not building. ~200 LoC changes.

---

## 2. Architecture Overview

### 2.1 TiviMate-Inspired 4-Tier Decision Tree

```
┌─────────────────────────────────────────────────────────────┐
│                     SYNC REQUEST                            │
│                 (accountKey, contentType)                   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ TIER 1: ETag Check (Server-side change detection)          │
│                                                             │
│   GET /api/categories HTTP/1.1                              │
│   If-None-Match: "abc123"                                   │
│                                                             │
│   → 304 Not Modified → SKIP_SYNC (instant, no data fetch)  │
│   → 200 OK (new ETag) → Continue to Tier 2                 │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ TIER 2: Count Check (Quick sanity check)                    │
│                                                             │
│   storedCount = NX_SyncCheckpoint.itemCount                 │
│   serverCount = /api/categories → count                     │
│                                                             │
│   → counts match → INCREMENTAL_SYNC (only changed items)   │
│   → counts differ → FULL_SYNC (structural change)          │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ TIER 3: Timestamp Filter (During INCREMENTAL_SYNC)          │
│                                                             │
│   lastSyncTime = NX_SyncCheckpoint.lastSyncCompleteMs       │
│   item.timestamp = XtreamVodItem.added / XtreamChannel.added│
│                                                             │
│   → item.timestamp < lastSyncTime → SKIP ITEM              │
│   → item.timestamp >= lastSyncTime → Continue to Tier 4    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ TIER 4: Fingerprint Check (Detect true changes)             │
│                                                             │
│   stored = NX_ItemFingerprint[sourceKey].fingerprint        │
│   current = computeFingerprint(item.raw)                    │
│                                                             │
│   → stored == current → SKIP (no change)                   │
│   → stored != current → PROCESS (update needed)            │
│   → stored == null → PROCESS (new item)                    │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Current Flow (BUG)

```kotlin
// DefaultXtreamSyncService.syncCatalog()
val syncStrategy = checkSyncStrategy(config)  // ← DECIDES correctly
when (syncStrategy) {
    is SyncStrategy.SkipSync -> { emit(Skipped); return }
    is SyncStrategy.IncrementalSync -> { /* IGNORED! */ }
    is SyncStrategy.FullSync -> { /* IGNORED! */ }
}
// ...
executeVodPhase(config, buffer, scope)  // ← No strategy passed!

// DefaultXtreamSyncService.executePhase()
pipeline.scanCatalog(pipelineConfig).collect { event ->
    when (event) {
        is ItemDiscovered -> {
            buffer.send(item)  // ← ALL items sent, no filtering!
            count++
        }
    }
}
```

### 2.3 Target Flow (FIXED)

```kotlin
// DefaultXtreamSyncService.syncCatalog()
val syncStrategy = checkSyncStrategy(config)
val lastSyncTime = syncCheckpointRepository.getLastSyncTime(config.accountKey)
when (syncStrategy) {
    is SyncStrategy.SkipSync -> { emit(Skipped); return }
    // Continue with sync...
}
// ...
executeVodPhase(config, buffer, scope, syncStrategy, lastSyncTime)

// DefaultXtreamSyncService.executePhase()
val fingerprintMap = if (syncStrategy is IncrementalSync) {
    fingerprintRepository.getFingerprintsAsMap("xtream", accountId, contentType)
} else {
    emptyMap()
}

pipeline.scanCatalog(pipelineConfig).collect { event ->
    when (event) {
        is ItemDiscovered -> {
            val raw = event.item.raw
            
            // Tier 3: Timestamp filter
            if (syncStrategy is IncrementalSync && lastSyncTime != null) {
                val itemTimestamp = raw.fields["added"]?.toLongOrNull()
                if (itemTimestamp != null && itemTimestamp < lastSyncTime) {
                    skippedCount++
                    return@collect  // Skip old item
                }
            }
            
            // Tier 4: Fingerprint check
            if (syncStrategy is IncrementalSync) {
                val currentFingerprint = computeFingerprint(raw)
                val storedFingerprint = fingerprintMap[raw.sourceId]
                if (storedFingerprint == currentFingerprint) {
                    skippedCount++
                    return@collect  // Skip unchanged item
                }
            }
            
            buffer.send(XtreamSyncItem(raw))
            processedCount++
        }
    }
}

// After persist: Update fingerprints
fingerprintRepository.putFingerprints(newFingerprints)
```

---

## 3. Entity Definitions (ALREADY EXIST)

### 3.1 NX_SyncCheckpoint

**Location:** `core/persistence/src/.../obx/SyncTrackingEntities.kt:62-152`

```kotlin
@Entity
data class NX_SyncCheckpoint(
    @Id var id: Long = 0,
    @Unique @Index val checkpointKey: String = "",     // "xtream:account123:vod"
    val sourceType: String = "",                        // "xtream"
    val accountId: String = "",                         // "account123"
    val contentType: String = "",                       // "vod" | "series" | "live"
    var lastSyncStartMs: Long = 0,
    var lastSyncCompleteMs: Long = 0,                   // ← Tier 3 timestamp
    var etag: String? = null,                           // ← Tier 1 ETag
    var itemCount: Int = 0,                             // ← Tier 2 count
    var newItemCount: Int = 0,
    var updatedItemCount: Int = 0,
    var deletedItemCount: Int = 0,
    var wasIncrementalSync: Boolean = false,
    var syncGeneration: Long = 0,
    // ...
)
```

### 3.2 NX_ItemFingerprint

**Location:** `core/persistence/src/.../obx/SyncTrackingEntities.kt:187-262`

```kotlin
@Entity
data class NX_ItemFingerprint(
    @Id var id: Long = 0,
    @Unique @Index val sourceKey: String = "",          // "xtream:account123:vod:12345"
    var fingerprint: Int = 0,                           // ← Tier 4 hash
    var lastSeenMs: Long = 0,
    var syncGeneration: Long = 0,
    val sourceType: String = "",
    val accountId: String = "",
    val contentType: String = "",
)
```

---

## 4. Gaps to Fix ("Baustellen")

### 4.1 HIGH Priority (Core Bug Fix)

| ID | Gap | File | Lines | Effort |
|----|-----|------|-------|--------|
| G-01 | Pass `syncStrategy` to `executeXxxPhase()` methods | `DefaultXtreamSyncService.kt` | 356-420 | S |
| G-02 | Pass `lastSyncTime` to `executeXxxPhase()` methods | `DefaultXtreamSyncService.kt` | 356-420 | S |
| G-03 | Add Tier 3 timestamp filter in `executePhase()` | `DefaultXtreamSyncService.kt` | 425-460 | M |
| G-04 | Add Tier 4 fingerprint check in `executePhase()` | `DefaultXtreamSyncService.kt` | 425-460 | M |
| G-05 | Inject `FingerprintRepository` in constructor | `DefaultXtreamSyncService.kt` | 40-60 | S |
| G-06 | Update fingerprints after persist | `DefaultXtreamSyncService.kt` | 455-465 | M |

### 4.2 MEDIUM Priority (Correctness)

| ID | Gap | File | Lines | Effort |
|----|-----|------|-------|--------|
| G-07 | Extract `contentType` from phase (`"vod"`, `"series"`, `"live"`) | `DefaultXtreamSyncService.kt` | 356-420 | S |
| G-08 | Record checkpoint per-contentType (not just global) | `DefaultXtreamSyncService.kt` | 340-355 | M |
| G-09 | Compute stable fingerprint from `RawMediaMetadata` | New file or extension | - | M |

### 4.3 LOW Priority (Polish)

| ID | Gap | File | Effort |
|----|-----|------|--------|
| G-10 | Add metrics for skipped vs processed items | `DefaultXtreamSyncService.kt` | S |
| G-11 | Log incremental sync effectiveness | `DefaultXtreamSyncService.kt` | S |
| G-12 | Clean stale fingerprints (syncGeneration) | `FingerprintRepository.kt` | M |

---

## 5. Implementation Plan

### Phase 1: Wire SyncStrategy (Gaps G-01, G-02, G-05)

```kotlin
// 1. Add FingerprintRepository to constructor
class DefaultXtreamSyncService @Inject constructor(
    private val pipeline: XtreamCatalogPipeline,
    private val checkpointStore: SyncCheckpointStore,
    private val incrementalSyncDecider: IncrementalSyncDecider,
    private val fingerprintRepository: FingerprintRepository,  // ← ADD
    // ...
)

// 2. Change signature of executeXxxPhase methods
private suspend fun executeVodPhase(
    config: XtreamSyncConfig,
    buffer: ChannelSyncBuffer<XtreamSyncItem>,
    scope: CoroutineScope,
    syncStrategy: SyncStrategy,       // ← ADD
    lastSyncTimeMs: Long?,            // ← ADD
): Int

// 3. Pass from syncCatalog()
val lastSyncTimeMs = syncCheckpointRepository
    .getCheckpoint("xtream", config.accountKey, "vod")
    ?.lastSyncCompleteMs
val vodCount = executeVodPhase(config, buffer, scope, syncStrategy, lastSyncTimeMs)
```

### Phase 2: Implement Tier 3/4 Filtering (Gaps G-03, G-04, G-07)

```kotlin
private suspend fun executePhase(
    pipelineConfig: XtreamCatalogConfig,
    buffer: ChannelSyncBuffer<XtreamSyncItem>,
    scope: CoroutineScope,
    phaseName: String,
    syncStrategy: SyncStrategy,
    lastSyncTimeMs: Long?,
    contentType: String,  // "vod" | "series" | "live"
): PhaseResult {
    var processedCount = 0
    var skippedByTimestamp = 0
    var skippedByFingerprint = 0
    
    // Load fingerprints for incremental sync
    val fingerprintMap: Map<String, Int> = if (syncStrategy is SyncStrategy.IncrementalSync) {
        fingerprintRepository.getFingerprintsAsMap(
            sourceType = "xtream",
            accountId = pipelineConfig.accountName,
            contentType = contentType,
        )
    } else {
        emptyMap()
    }
    
    val newFingerprints = mutableListOf<FingerprintUpdate>()
    
    val producerJob = scope.launch {
        pipeline.scanCatalog(pipelineConfig).collect { event ->
            when (event) {
                is XtreamCatalogEvent.ItemDiscovered -> {
                    val raw = event.item.raw
                    
                    // Tier 3: Timestamp filter (only for incremental)
                    if (syncStrategy is SyncStrategy.IncrementalSync && lastSyncTimeMs != null) {
                        val itemAdded = raw.fields["added"]?.toLongOrNull()
                        if (itemAdded != null && itemAdded < lastSyncTimeMs) {
                            skippedByTimestamp++
                            return@collect  // Skip old item
                        }
                    }
                    
                    // Tier 4: Fingerprint check (only for incremental)
                    if (syncStrategy is SyncStrategy.IncrementalSync) {
                        val currentFingerprint = computeFingerprint(raw)
                        val storedFingerprint = fingerprintMap[raw.sourceId]
                        
                        if (storedFingerprint != null && storedFingerprint == currentFingerprint) {
                            skippedByFingerprint++
                            return@collect  // Skip unchanged item
                        }
                        
                        // Track for later update
                        newFingerprints.add(FingerprintUpdate(raw.sourceId, currentFingerprint))
                    }
                    
                    buffer.send(XtreamSyncItem(raw))
                    processedCount++
                }
                // ...
            }
        }
    }
    
    // ... consumer job ...
    
    // Update fingerprints after successful persist
    if (newFingerprints.isNotEmpty()) {
        fingerprintRepository.putFingerprints(
            sourceType = "xtream",
            accountId = pipelineConfig.accountName,
            contentType = contentType,
            fingerprints = newFingerprints,
        )
    }
    
    return PhaseResult(
        processed = processedCount,
        skippedByTimestamp = skippedByTimestamp,
        skippedByFingerprint = skippedByFingerprint,
    )
}
```

### Phase 3: Fingerprint Computation (Gap G-09)

```kotlin
// Extension function in same file or new FingerprintUtils.kt
private fun computeFingerprint(raw: RawMediaMetadata): Int {
    // Stable hash based on immutable fields
    return Objects.hash(
        raw.title,
        raw.originalTitle,
        raw.year,
        raw.duration,
        raw.fields["stream_url"],
        raw.fields["container_extension"],
    )
}
```

---

## 6. Binding Constraints

### 6.1 Live Channels MUST Continue Working

- Live channel sync uses same flow: `executeLivePhase()` → `executePhase()`
- Fingerprint check applies equally to live channels
- Category filtering via `liveCategoryIds` is preserved

### 6.2 Category Filtering MUST Be Preserved

- Category IDs are passed to pipeline config: `vodCategoryIds`, `seriesCategoryIds`, `liveCategoryIds`
- Pipeline applies filtering BEFORE returning items
- Incremental sync filtering happens AFTER pipeline filtering (complementary)

### 6.3 Entity Naming MUST Use NX_* Prefix

- ✅ `NX_SyncCheckpoint` - already exists
- ✅ `NX_ItemFingerprint` - already exists
- ❌ DO NOT create new `Obx*` entities for this feature

### 6.4 Layer Boundaries

- ✅ `FingerprintRepository` is in `core/persistence` - allowed to use in `core/catalog-sync`
- ❌ DO NOT access `FingerprintRepository` from `pipeline/*` modules
- ❌ DO NOT import `NX_ItemFingerprint` entity in `pipeline/*` modules

---

## 7. Validation Checklist

After implementation, verify:

- [ ] `SyncStrategy.IncrementalSync` actually skips items in logs
- [ ] `skippedByTimestamp` count > 0 for re-sync of unchanged catalog
- [ ] `skippedByFingerprint` count > 0 for re-sync of unchanged catalog
- [ ] Category filtering still works (`vodCategoryIds` etc.)
- [ ] Live channel sync still works
- [ ] `wasIncremental = true` in `SyncStatus.Completed` when incremental

### Test Scenario 1: No Changes

1. Full sync account A (first time)
2. Wait 5 seconds
3. Sync account A again
4. **Expected:** `SyncStrategy.SkipSync` (Tier 1/2) OR minimal items processed (Tier 3/4)

### Test Scenario 2: New Items Only

1. Full sync account A
2. Mock: Add 5 new VOD items with `added > lastSyncTime`
3. Sync account A again
4. **Expected:** Only 5 items processed, rest skipped

### Test Scenario 3: Category Filter + Incremental

1. Sync account A with `vodCategoryIds = [10, 20]`
2. Sync account A again
3. **Expected:** Category filtering applies first, then incremental filtering

---

## 8. Acceptance Criteria

This contract is fulfilled when:

1. **Tier 3 active:** Items with `added < lastSyncTime` are skipped
2. **Tier 4 active:** Items with unchanged fingerprint are skipped
3. **Logging:** `skippedByTimestamp`, `skippedByFingerprint` logged
4. **Metrics:** `SyncStatus.Completed.wasIncremental` is accurate
5. **No regressions:** Category filtering, live channels work as before
6. **Build passes:** No compilation errors, no test failures

---

## 9. Post-Implementation Actions

After all acceptance criteria met:

1. **DELETE THIS DOCUMENT** (it's temporary)
2. Update `INCREMENTAL_SYNC_DESIGN.md` if needed
3. Remove any TODO comments referencing this contract
4. Update `ROADMAP.md` to mark incremental sync as complete

---

## 10. Multi-Agent Audit Verification (2025-01-XX)

> **Audit Methodology:** 5 parallel subagents analyzed repo-specific patterns.
> Sequential thinking used for consolidation and gap identification.

### 10.1 Audit Results Summary

| Audit Area | Files Analyzed | Key Findings |
|------------|----------------|--------------|
| Catalog-Sync Module | 27 files, ~5,400 LOC | Infrastructure EXISTS, wiring MISSING |
| Persistence Layer | 20 NX_* entities | Fingerprint computation NOT IMPLEMENTED |
| Xtream Pipeline | 26 files, ~3,500 LOC | Timestamps AVAILABLE and mapped |
| Contracts/Docs | 18+ documents | SSOT rules documented |
| Test Infrastructure | 4 test files, 995 LOC | **CRITICAL: 0 tests for affected code** |

### 10.2 Critical Gaps Identified

#### G-13: Fingerprint Computation Missing (CRITICAL)

**Status:** Spec exists, implementation DOES NOT exist

**Evidence:**
```kotlin
// SyncTrackingEntities.kt contains ONLY spec:
/**
 * Fingerprint computation formula (from INCREMENTAL_SYNC_DESIGN.md):
 * fingerprint = hash(title + year + seasonNum + episodeNum + duration + streamUrl)
 */
```

**Required Implementation:**
```kotlin
// NEW FILE NEEDED: core/catalog-sync/.../mapper/FingerprintComputation.kt
fun RawMediaMetadata.computeFingerprint(): String {
    val input = buildString {
        append(title.orEmpty().lowercase())
        append("|")
        append(year?.toString().orEmpty())
        append("|")
        append(seasonNumber?.toString().orEmpty())
        append("|")
        append(episodeNumber?.toString().orEmpty())
        append("|")
        append(durationMs?.toString().orEmpty())
        append("|")
        append(streamUrl.orEmpty())
    }
    return input.hashCode().toString(16) // Or use proper hash function
}
```

#### G-14: Zero Test Coverage for Affected Components (CRITICAL)

**Components with 0 tests:**
| Component | LOC | Risk |
|-----------|-----|------|
| `IncrementalSyncDecider` | 241 | HIGH - Core decision logic |
| `DefaultXtreamSyncService` | 493 | HIGH - Main service being modified |
| `FingerprintRepository` | 320 | MEDIUM - CRUD operations |

**Required Test Files:**
1. `IncrementalSyncDeciderTest.kt` - Test all 4 tier decisions
2. `DefaultXtreamSyncServiceTest.kt` - Test sync flow with mocked dependencies
3. `FingerprintRepositoryTest.kt` - Test bulk operations

### 10.3 Timestamp Availability Verification

✅ **CONFIRMED:** Xtream API provides usable timestamps:

| Content Type | API Field | Mapped To |
|--------------|-----------|-----------|
| VOD | `added` (Unix epoch) | `RawMediaMetadata.addedTimestamp` |
| Series | `last_modified` (Unix epoch) | `RawMediaMetadata.lastModifiedTimestamp` |
| Episodes | (derived from series) | Inherited |
| Live | `added` (Unix epoch) | `RawMediaMetadata.addedTimestamp` |

**Source File:** `pipeline/xtream/.../mapper/XtreamVodMapper.kt`

### 10.4 DI Wiring Verification

**Current DefaultXtreamSyncService injections:**
```kotlin
class DefaultXtreamSyncService @Inject constructor(
    private val xtreamPipeline: XtreamCatalogPipeline,
    private val syncCheckpointStore: SyncCheckpointStore,
    private val incrementalSyncDecider: IncrementalSyncDecider,
    private val deviceProfileDetector: DeviceProfileDetector,
    private val syncPerfMetrics: SyncPerfMetrics,
    // ❌ MISSING: private val fingerprintRepository: FingerprintRepository,
)
```

**Module providing FingerprintRepository:** `EnhancedSyncModule.kt` in `core/catalog-sync/di/`

### 10.5 Consumer Inventory (Who calls affected code)

| Consumer | File | Call Pattern |
|----------|------|--------------|
| XtreamCatalogSyncWorker | `app-v2/.../work/` | `syncService.syncCatalog()` |
| XtreamPeriodicSyncWorker | `app-v2/.../work/` | `syncService.syncCatalog()` |
| TelegramCatalogSyncWorker | `app-v2/.../work/` | Similar pattern |
| SourceActivationBootstrap | `app-v2/.../bootstrap/` | Triggers initial sync |
| SettingsSourceViewModel | `feature/settings/` | Manual sync trigger |

### 10.6 Related Contracts (Must Not Violate)

| Contract | Key Rules |
|----------|-----------|
| `CATALOG_SYNC_WORKERS_CONTRACT_V2.md` | W-2, W-3: Workers MUST use Scheduler |
| `NX_SSOT_CONTRACT.md` | INV-6: UI uses NX_* entities only |
| `catalog-sync.instructions.md` | SSOT for sync orchestration |
| `AGENTS.md` Section 4.8 | `core/catalog-sync` is sync SSOT |

### 10.7 Updated Gap Analysis

Original gaps G-01 to G-12 remain valid. Adding:

| Gap ID | Description | Severity | Action |
|--------|-------------|----------|--------|
| G-13 | Fingerprint computation not implemented | HIGH | Create function |
| G-14 | Zero test coverage for affected code | HIGH | Write tests first |
| G-15 | FingerprintRepository not injected | MEDIUM | Add to constructor |

### 10.8 Revised File Inventory

#### Files to MODIFY

| File | Location | Changes | LOC Est. |
|------|----------|---------|----------|
| `DefaultXtreamSyncService.kt` | `core/catalog-sync/.../xtream/` | Inject + wire fingerprint | ~100 |
| `XtreamSyncModule.kt` | `core/catalog-sync/di/` | Provide FingerprintRepository | ~5 |

#### Files to CREATE

| File | Location | Purpose | LOC Est. |
|------|----------|---------|----------|
| `FingerprintComputation.kt` | `core/catalog-sync/.../mapper/` | Compute fingerprints | ~50 |
| `IncrementalSyncDeciderTest.kt` | `core/catalog-sync/src/test/` | Test 4-tier logic | ~200 |
| `DefaultXtreamSyncServiceTest.kt` | `core/catalog-sync/src/test/` | Test sync flow | ~300 |
| `FingerprintRepositoryTest.kt` | `core/persistence/src/test/` | Test bulk ops | ~150 |

#### Files UNCHANGED (verified)

| File | Reason |
|------|--------|
| `SyncTrackingEntities.kt` | Entities complete |
| `FingerprintRepository.kt` | CRUD complete |
| `SyncCheckpointRepository.kt` | CRUD complete |
| `IncrementalSyncDecider.kt` | Logic complete |
| Pipeline mappers | Already map timestamps |

---

## Appendix A: File Inventory (Updated)

| File | Purpose | Lines Changed |
|------|---------|---------------|
| `DefaultXtreamSyncService.kt` | Main changes + DI | ~100 |
| `XtreamSyncModule.kt` | Bind FingerprintRepository | ~5 |
| `FingerprintComputation.kt` | **NEW** - Compute fingerprints | ~50 |
| `IncrementalSyncDeciderTest.kt` | **NEW** - Test 4-tier logic | ~200 |
| `DefaultXtreamSyncServiceTest.kt` | **NEW** - Test sync flow | ~300 |
| `FingerprintRepositoryTest.kt` | **NEW** - Test bulk ops | ~150 |
| `FingerprintRepository.kt` | Maybe minor additions | ~10 |
| `SyncCheckpointRepository.kt` | No changes needed | 0 |
| `IncrementalSyncDecider.kt` | No changes needed | 0 |
| `SyncTrackingEntities.kt` | No changes needed | 0 |

## Appendix B: Class Relationships

```
┌─────────────────────────────────────────────────────────────────────────┐
│                       DefaultXtreamSyncService                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Dependencies (Constructor Injection):                            │  │
│  │   - XtreamCatalogPipeline                                        │  │
│  │   - SyncCheckpointStore                                          │  │
│  │   - IncrementalSyncDecider                                       │  │
│  │   + FingerprintRepository  ← ADD                                 │  │
│  │   - SyncPerfMetrics                                              │  │
│  │   - DeviceProfileDetector                                        │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Modified Methods:                                                │  │
│  │   - syncCatalog() → pass syncStrategy, lastSyncTime              │  │
│  │   - executeVodPhase() → accept new params                        │  │
│  │   - executeSeriesPhase() → accept new params                     │  │
│  │   - executeEpisodesPhase() → accept new params                   │  │
│  │   - executeLivePhase() → accept new params                       │  │
│  │   - executePhase() → implement Tier 3/4 filtering                │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                    Uses            │           Uses
           ┌────────────────────────┼────────────────────────┐
           │                        │                        │
           ▼                        ▼                        ▼
┌──────────────────────┐ ┌──────────────────────┐ ┌──────────────────────┐
│  IncrementalSync-    │ │  FingerprintRepo-    │ │  SyncCheckpoint-     │
│  Decider             │ │  sitory              │ │  Repository          │
│                      │ │                      │ │                      │
│ - decideSyncStrategy │ │ - getFingerprintMap  │ │ - getCheckpoint      │
│   → SyncStrategy     │ │ - putFingerprints    │ │ - recordSyncStart    │
│                      │ │ - markStaleItems     │ │ - recordSyncComplete │
└──────────────────────┘ └──────────────────────┘ └──────────────────────┘
           │                        │                        │
           │                        │                        │
           ▼                        ▼                        ▼
┌──────────────────────────────────────────────────────────────────────┐
│                        ObjectBox Database                            │
│  ┌────────────────────┐          ┌─────────────────────────────┐    │
│  │ NX_SyncCheckpoint  │          │ NX_ItemFingerprint          │    │
│  │ - checkpointKey    │          │ - sourceKey                 │    │
│  │ - lastSyncComplete │          │ - fingerprint               │    │
│  │ - itemCount        │          │ - lastSeenMs                │    │
│  │ - etag             │          │ - syncGeneration            │    │
│  └────────────────────┘          └─────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────┘
```

---

**END OF CONTRACT**
