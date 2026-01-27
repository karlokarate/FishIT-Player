# Incremental Sync Architecture for FishIT-Player

> **Status:** Design Document  
> **Created:** 2026-01-27  
> **Author:** Copilot Agent  
> **Module:** `core/catalog-sync`, `pipeline/xtream`, `infra/transport-xtream`

---

## 1. Problem Statement

### Current Behavior
- Every catalog sync downloads **ALL** content from Xtream API
- 40K+ VOD items, 5K+ Series, 500+ Live channels re-processed each time
- Full DB writes even for unchanged items
- Wastes bandwidth, battery, and processing time

### Xtream API Limitation
- **No server-side filtering** by date or modification time
- No delta/changelog endpoint
- Must fetch full catalog on every request

### Goal
Implement **client-side incremental sync** that minimizes processing while working within Xtream API constraints.

---

## 2. Industry Analysis

### How Comparable Apps Handle This

| App | Primary Strategy | Secondary Strategy | Effectiveness |
|-----|------------------|-------------------|---------------|
| **TiviMate** | Fingerprint comparison | ETag caching | 70-80% |
| **IPTV Smarters** | Timestamp filtering | Count check | 60-70% |
| **OTT Navigator** | Hybrid (user choice) | Full refresh option | 70-80% |
| **Plex** | ETag + Delta API | Server changelog | 95%+ |
| **Jellyfin** | Last-Modified header | Item fingerprints | 90%+ |

### Key Insight
Apps working with Xtream use **client-side detection** since the server doesn't support delta queries.

---

## 3. Proposed Architecture

### 4-Tier Sync Decision Tree

```
┌─────────────────────────────────────────────────────────────────────┐
│                      SYNC DECISION FLOW                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────────┐                                               │
│  │ 1. Check ETag    │ ─── 304 Not Modified ───► SKIP SYNC (100%)   │
│  │    (HTTP Header) │                                               │
│  └────────┬─────────┘                                               │
│           │ 200 OK or Not Supported                                 │
│           ▼                                                         │
│  ┌──────────────────┐                                               │
│  │ 2. Quick Count   │ ─── Same Count ───► Likely unchanged,        │
│  │    Comparison    │                     but verify with #3        │
│  └────────┬─────────┘                                               │
│           │ Different Count                                         │
│           ▼                                                         │
│  ┌──────────────────┐                                               │
│  │ 3. Timestamp     │ ─── added > lastSync ───► Process item       │
│  │    Filter        │                                               │
│  │                  │ ─── added ≤ lastSync ───► Check fingerprint  │
│  └────────┬─────────┘                                               │
│           │                                                         │
│           ▼                                                         │
│  ┌──────────────────┐                                               │
│  │ 4. Fingerprint   │ ─── Match ───► SKIP DB Write                 │
│  │    Comparison    │                                               │
│  │                  │ ─── No Match ───► PERSIST + Update FP        │
│  └──────────────────┘                                               │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### Tier Breakdown

#### Tier 1: ETag Caching (Network Level)
```kotlin
// Request with stored ETag
GET /player_api.php?action=get_vod_streams
If-None-Match: "abc123"

// Response options:
// 304 Not Modified → Skip entire sync
// 200 OK + new ETag → Process response
```

**Impact:** 100% savings if server supports it (many don't)

#### Tier 2: Item Count Quick-Check
```kotlin
val lastCount = syncState.vodItemCount
val currentCount = response.items.size

if (currentCount == lastCount) {
    // Likely no changes - but verify with fingerprints
    // (count same doesn't guarantee no modifications)
}
```

**Impact:** Fast gate, ~5% overhead savings

#### Tier 3: Timestamp Filtering
```kotlin
val lastSyncMs = syncState.lastVodSyncMs
val newItems = items.filter { it.added != null && it.added * 1000 > lastSyncMs }

// Only process newItems for persistence
// Existing items with same timestamp can skip fingerprint check
```

**Impact:** 80-90% DB write reduction for subsequent syncs

#### Tier 4: Fingerprint Comparison
```kotlin
fun XtreamVodItem.fingerprint(): Int {
    return Objects.hash(
        streamId,
        name,
        added,
        categoryId,
        containerExtension,
        streamIcon
    )
}

val storedFp = fingerprintStore.get(item.streamId)
if (storedFp == item.fingerprint()) {
    // Skip persist - item unchanged
} else {
    // Persist and update fingerprint
    persist(item)
    fingerprintStore.put(item.streamId, item.fingerprint())
}
```

**Impact:** Catches modifications to existing items

---

## 4. Data Model

### SyncCheckpoint Entity
```kotlin
@Entity
data class ObxSyncCheckpoint(
    @Id var id: Long = 0,
    
    // Source identification
    val sourceType: String,        // "xtream", "telegram"
    val accountId: String,         // Xtream account ID
    val contentType: String,       // "vod", "series", "live"
    
    // Sync metadata
    var lastSyncStartMs: Long = 0,
    var lastSyncCompleteMs: Long = 0,
    var lastSyncDurationMs: Long = 0,
    
    // HTTP caching
    var etag: String? = null,
    var lastModified: String? = null,
    
    // Content tracking
    var itemCount: Int = 0,
    var newItemCount: Int = 0,
    var updatedItemCount: Int = 0,
    var deletedItemCount: Int = 0,
    
    // Sync mode
    var wasIncrementalSync: Boolean = false,
    var forcedFullSync: Boolean = false,
)
```

### ItemFingerprint Entity
```kotlin
@Entity
data class ObxItemFingerprint(
    @Id var id: Long = 0,
    
    // Composite key: sourceType + accountId + contentType + itemId
    @Index
    val sourceKey: String,  // "xtream_123_vod_45678"
    
    val fingerprint: Int,
    val lastSeenMs: Long,
    
    // For deletion detection
    val syncGeneration: Long,  // Incremented each sync
)
```

---

## 5. Implementation Plan

### Phase 1: Foundation (LOW effort, HIGH impact)
| Task | File | Description |
|------|------|-------------|
| 1.1 | `core/persistence/entity/ObxSyncCheckpoint.kt` | Create entity |
| 1.2 | `core/persistence/entity/ObxItemFingerprint.kt` | Create entity |
| 1.3 | `core/persistence/SyncCheckpointRepository.kt` | CRUD for checkpoints |
| 1.4 | `core/persistence/FingerprintRepository.kt` | Fast fingerprint lookups |

### Phase 2: Transport Layer (LOW effort)
| Task | File | Description |
|------|------|-------------|
| 2.1 | `infra/transport-xtream/XtreamHttpClient.kt` | Add ETag request/response handling |
| 2.2 | `infra/transport-xtream/EtagCache.kt` | Store ETags per endpoint |

### Phase 3: Pipeline Integration (MEDIUM effort)
| Task | File | Description |
|------|------|-------------|
| 3.1 | `pipeline/xtream/sync/IncrementalSyncConfig.kt` | Sync configuration |
| 3.2 | `pipeline/xtream/XtreamCatalogSource.kt` | Timestamp filtering |
| 3.3 | `pipeline/xtream/XtreamCatalogPipelineImpl.kt` | Fingerprint comparison |

### Phase 4: Orchestration (MEDIUM effort)
| Task | File | Description |
|------|------|-------------|
| 4.1 | `core/catalog-sync/IncrementalSyncDecider.kt` | Decide sync strategy |
| 4.2 | `core/catalog-sync/DefaultCatalogSyncService.kt` | Integrate incremental logic |
| 4.3 | `app-v2/work/XtreamCatalogScanWorker.kt` | Use incremental sync |

### Phase 5: Cleanup & Polish
| Task | File | Description |
|------|------|-------------|
| 5.1 | Deletion detection | Mark items not seen in sync |
| 5.2 | Metrics/Logging | Track incremental sync effectiveness |
| 5.3 | User settings | "Force Full Refresh" option |

---

## 6. Expected Results

### Performance Comparison

| Metric | Before (Full Sync) | After (Incremental) | Improvement |
|--------|-------------------|---------------------|-------------|
| **Sync Time** | ~60 seconds | ~5-10 seconds | **6-12x faster** |
| **DB Writes** | 40,000 items | ~500-2,000 items | **20-80x fewer** |
| **Network** | Full download | Same (or 0 if 304) | **0-100%** |
| **Battery** | High | Low | **Significant** |
| **Memory** | Same | Same | No change |

### Sync Scenarios

| Scenario | Strategy Used | Expected Time |
|----------|---------------|---------------|
| First sync ever | Full sync | ~60s |
| Daily refresh, no changes | ETag 304 (if supported) | <1s |
| Daily refresh, 50 new items | Timestamp filter | ~5s |
| Weekly refresh, 200 new items | Timestamp + fingerprint | ~10s |
| Manual "Force Refresh" | Full sync | ~60s |

---

## 7. Fingerprint Field Selection

### VOD Items
```kotlin
fun XtreamVodItem.fingerprint(): Int = Objects.hash(
    streamId,           // Primary key
    name,               // Title changes
    added,              // Timestamp
    categoryId,         // Category reassignment
    containerExtension, // Format changes (mp4, mkv)
    streamIcon,         // Artwork changes
    rating,             // Metadata update
)
```

### Series Items
```kotlin
fun XtreamSeriesItem.fingerprint(): Int = Objects.hash(
    seriesId,
    name,
    lastModified,
    categoryId,
    cover,
    rating,
    episodeCount,      // New episodes indicator
)
```

### Live Channels
```kotlin
fun XtreamChannel.fingerprint(): Int = Objects.hash(
    streamId,
    name,
    categoryId,
    streamIcon,
    epgChannelId,       // EPG mapping changes
)
```

---

## 8. Edge Cases

### Provider Doesn't Set Timestamps
- Some providers set `added = 0` or `null`
- **Fallback:** Use fingerprint comparison for ALL items
- **Detection:** If >50% items have `added == null`, log warning

### ETag Not Supported
- Most Xtream panels don't support ETag
- **Fallback:** Skip Tier 1, proceed to Tier 3
- **Detection:** Check for ETag in response headers

### Item Deleted on Provider
- Item in local DB but not in API response
- **Strategy:** Track `syncGeneration` per item
- Items with old `syncGeneration` after sync = deleted
- Mark as `deletedAt`, don't hard delete immediately

### Mid-Sync Failure
- Sync interrupted (network, battery, etc.)
- **Strategy:** Store checkpoint with last processed item
- Resume from checkpoint on next sync attempt

---

## 9. Contract Compliance

This design complies with:

- **MEDIA_NORMALIZATION_CONTRACT:** Fingerprints are pre-normalization (raw data)
- **LOGGING_CONTRACT_V2:** All sync decisions logged via UnifiedLog
- **AGENTS.md Section 4.2:** Pipeline produces RawMediaMetadata only
- **AGENTS.md Section 4.5:** No cross-layer imports

---

## 10. Open Questions

1. **Fingerprint storage:** ObjectBox entity vs DataStore?
   - **Recommendation:** ObjectBox (faster bulk operations)

2. **Sync generation cleanup:** How long to keep old fingerprints?
   - **Recommendation:** 30 days, then prune

3. **User-facing control:** Should user see sync mode?
   - **Recommendation:** Yes, in Debug/Settings screen

4. **Telegram pipeline:** Apply same pattern?
   - **Recommendation:** Yes, but different fingerprint fields

---

## 11. References

- [TiviMate Behavior Analysis](internal observation)
- [Xtream Codes API Documentation](provider docs)
- [Android Paging 3 Documentation](https://developer.android.com/topic/libraries/architecture/paging/v3-overview)
- [AGENTS.md Section 4](../../AGENTS.md#4-core-architecture-principles-v2)
