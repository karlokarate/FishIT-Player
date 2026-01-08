# ObjectBox Performance Baseline

**Document Version:** 1.0  
**Created:** January 2026  
**Status:** Active  
**Related Issues:** #609, #608 (ObxWriteConfig)

---

## Overview

This document establishes performance baselines for ObjectBox operations in FishIT Player v2, providing regression guardrails and optimization targets.

## Baseline Philosophy

Baselines are derived from:
1. **Real-world testing** on representative devices (Phone/Tablet, FireTV Low-RAM)
2. **ObxWriteConfig optimization** (PR #608) batch sizes
3. **User experience targets** (60fps smooth scrolling, instant search feedback)

---

## Device Classes

Per `ObxWriteConfig` and `CATALOG_SYNC_WORKERS_CONTRACT_V2` (W-17):

| Device Class | Memory Profile | Batch Size Strategy | Example Devices |
|--------------|----------------|---------------------|-----------------|
| **TV_LOW_RAM** | ≤ 1.5GB RAM | Conservative (35-500 items) | FireTV Stick (2nd gen), Mi Box S |
| **PHONE_TABLET** | > 1.5GB RAM | Optimized (200-4000 items) | Modern phones, tablets |
| **TV** | ≥ 2GB RAM | Optimized (same as Phone/Tablet) | Shield TV, FireTV Cube |

---

## 1. Library Load Time

### Metric Definition
Time from navigation trigger to first visible row displayed.

**Measurement Points:**
- Start: User taps "Library" navigation item
- End: First `FishRow` with ≥3 tiles visible on screen

### Baseline Targets

| Device Class | Target | Acceptable Max | Critical Threshold |
|--------------|--------|----------------|-------------------|
| TV_LOW_RAM | 800ms | 1500ms | 2500ms |
| PHONE_TABLET | 400ms | 800ms | 1500ms |
| TV | 500ms | 1000ms | 2000ms |

### Optimization Notes
- **Cold Start:** First load after app launch (cache miss)
- **Warm Start:** Subsequent loads (ObjectBox page cache hit)
- **Large Catalogs:** 10,000+ items require pagination (per `OBJECTBOX_REACTIVE_PATTERNS.md`)

### Related Code
- `infra/data-library/LibraryContentRepositoryAdapter.kt` - Repository query
- `ObxWriteConfig.getPageSize()` - Query pagination size
- `feature/library/LibraryViewModel.kt` - StateFlow observation

---

## 2. Scroll-Jank Metrics

### Metric Definition
Frame drops during continuous scrolling in Library or Home screens.

**Measurement Method:**
- Automated: Android Benchmark with Macrobenchmark
- Manual: Developer Options "Profile GPU Rendering" histogram

### Baseline Targets

| Metric | Target | Acceptable | Critical |
|--------|--------|-----------|----------|
| **Jank Rate** | < 1% frames dropped | < 3% | > 5% |
| **Frame Time (P95)** | < 12ms (80fps) | < 16ms (60fps) | > 20ms |
| **Main Thread Work** | < 10ms per scroll event | < 14ms | > 16ms |

### Common Jank Causes
1. **Main Thread DB Access** → Use reactive Flows + `Dispatchers.IO`
2. **Large Image Decoding** → Coil handles off-thread (compliant)
3. **N+1 Queries** → Detected by `RepoBoundaryChecker` (optional component)
4. **Eager Loading** → Use `query.find()` with pagination

### Related Code
- `OBJECTBOX_REACTIVE_PATTERNS.md` - Reactive flow patterns
- `core/persistence/config/ObxWriteConfig.kt` - Page sizes
- `RepoBoundaryChecker.kt` - N+1 detection (optional)

---

## 3. Home Rows Load Time

### Metric Definition
Time to populate all content rows on Home screen.

**Measurement Points:**
- Start: Navigation to Home screen
- End: All rows visible (Continue Watching, Recently Added, Movies, Series, Live)

### Baseline Targets

| Device Class | 6-Stream Load | Notes |
|--------------|---------------|-------|
| TV_LOW_RAM | 1200ms | Conservative batch sizes (35 items) |
| PHONE_TABLET | 600ms | Optimized batch sizes (200-600 items) |
| TV | 800ms | Optimized batch sizes |

### Implementation Status
Per `HOME_PLATINUM_PERFORMANCE_PLAN.md` (Phase 2 Complete):
- ✅ 6-stream type-safe `combine()` with `stateIn`
- ✅ Reactive Flows with `debounce(300ms)` during sync
- ✅ Device-aware batch sizing via `ObxWriteConfig`

### Related Code
- `feature/home/HomeViewModel.kt` - 6-stream combine
- `infra/data-home/HomeContentRepositoryAdapter.kt` - Multi-source aggregation
- `ObxWriteConfig.getSyncLiveBatchSize()` - Phase-specific batching

---

## 4. Search Latency

### Metric Definition
Time from keystroke to search results displayed.

**User Expectation:** < 300ms for "instant search" feel

### Baseline Targets

| Catalog Size | TV_LOW_RAM | PHONE_TABLET | TV |
|--------------|-----------|--------------|-----|
| < 1,000 items | 150ms | 80ms | 100ms |
| 1,000-10,000 | 300ms | 150ms | 200ms |
| > 10,000 | 500ms | 250ms | 300ms |

### Implementation Requirements
1. **Query Optimization**
   - Use `.startsWith()` for prefix search (indexed)
   - Use `.contains()` for full-text (slower, limit results)
   - Limit: 100 results initially, paginate on scroll

2. **Reactive Debouncing**
   ```kotlin
   searchQuery
       .debounce(300.milliseconds)
       .flatMapLatest { query ->
           repository.searchByTitle(query, limit = 100)
       }
   ```

3. **Index Requirements**
   ```kotlin
   @Entity
   data class ObxVod(
       @Index var nameLower: String = "",  // Essential for search!
   )
   ```

### Related Code
- `infra/data-library/LibraryContentRepositoryAdapter.kt` - Search queries
- `OBJECTBOX_REACTIVE_PATTERNS.md` - Debouncing patterns

---

## 5. ObjectBox Operation Benchmarks

### 5.1 Box.put() - Batch Write Performance

**Test Scenario:** Insert 1,000 VOD items via `putChunked()`

| Device Class | Chunk Size | Target Time | Acceptable Max |
|--------------|-----------|-------------|----------------|
| TV_LOW_RAM | 35 items | 2500ms | 4000ms |
| PHONE_TABLET | 400 items | 800ms | 1500ms |
| TV | 400 items | 1000ms | 1800ms |

**Calculation:**
- TV_LOW_RAM: 1,000 items ÷ 35/chunk × 80ms/chunk = ~2500ms
- PHONE_TABLET: 1,000 items ÷ 400/chunk × 300ms/chunk = ~800ms

### 5.2 Box.query() - Read Performance

**Test Scenario:** Query 500 items with `.order()` and `.find()`

| Device Class | Target Time | Acceptable Max |
|--------------|-------------|----------------|
| TV_LOW_RAM | 40ms | 80ms |
| PHONE_TABLET | 20ms | 40ms |
| TV | 25ms | 50ms |

**Notes:**
- Cold query: ObjectBox page cache miss
- Warm query: 50% faster (cached pages)
- Add `.limit(100)` for UI lists (no need to query all 500)

### 5.3 Eager Loading - ToMany Relations

**Test Scenario:** Load 100 Series with eager-loaded Episodes

| Device Class | Target Time | Acceptable Max |
|--------------|-------------|----------------|
| TV_LOW_RAM | 150ms | 300ms |
| PHONE_TABLET | 80ms | 150ms |
| TV | 100ms | 200ms |

**Anti-Pattern to Avoid:**
```kotlin
// ❌ N+1 Query Pattern (detected by RepoBoundaryChecker)
series.forEach { s ->
    val episodes = episodeBox.query(ObxEpisode_.seriesId.equal(s.id)).build().find()
    // This executes 100 separate queries!
}
```

**Correct Pattern:**
```kotlin
// ✅ Eager Loading with @Backlink
@Entity
data class ObxSeries(
    @Id var id: Long = 0,
    @Backlink(to = "series")
    lateinit var episodes: ToMany<ObxEpisode>,
)
// ObjectBox loads all episodes in 1 query per series
```

---

## 6. Regression Guards

### 6.1 Automated Guards (Build-Time)

1. **Benchmark Tests** (`ObxPerformanceBenchmark.kt`)
   - Fail PR if regression > 20% vs baseline
   - Run on CI with consistent device (GitHub Actions emulator)

2. **StrictMode** (`StrictModeConfig.kt`)
   - Detect main thread disk reads/writes
   - Crash debug builds on violation
   - Custom penalty for ObjectBox calls on main thread

3. **Repository Boundary Checker** (`RepoBoundaryChecker.kt` - optional)
   - Detect N+1 queries via call stack analysis
   - Log warning + crash in debug builds

### 6.2 Manual Validation

1. **Before Release:**
   - Profile GPU Rendering histogram check
   - Manual scroll test (Library with 10k+ items)
   - Search latency test (type "Action" - should be instant)

2. **Performance Regression Checklist:**
   - [ ] Library loads in < target time
   - [ ] No scroll jank (< 3% frames dropped)
   - [ ] Home screen loads 6 streams in < target time
   - [ ] Search results appear < 300ms
   - [ ] No StrictMode violations in debug logs

---

## 7. Optimization History

| Date | Change | Performance Impact | Issue/PR |
|------|--------|-------------------|----------|
| Dec 2024 | ObxWriteConfig device-aware batching | +40% sync speed on Phone/Tablet | #608 |
| Dec 2024 | Home 6-stream reactive combine | -200ms initial load | Phase 2 |
| Jan 2026 | Performance baseline documentation | Regression protection | #609 |

---

## 8. Future Optimization Targets

### High Priority
1. **Library Pagination** - Reduce initial query from 4000 → 500 items (lazy load on scroll)
2. **Search Index Optimization** - Add full-text search index for better large-catalog performance
3. **Prefetch on Scroll** - Predictive query for next page

### Medium Priority
1. **Background Preloading** - Load Library content during Home screen idle time
2. **Query Result Caching** - In-memory LRU cache for frequent searches
3. **Incremental Updates** - Use ObjectBox observers to update only changed rows

---

## References

- `OBJECTBOX_REACTIVE_PATTERNS.md` - Flow patterns and best practices
- `ObxWriteConfig.kt` - Device-aware batch sizing SSOT
- `CATALOG_SYNC_WORKERS_CONTRACT_V2.md` - W-17 (FireTV Safety)
- `HOME_PLATINUM_PERFORMANCE_PLAN.md` - Phase 2 implementation
- Android Benchmark documentation: https://developer.android.com/topic/performance/benchmarking

---

**Audit Status:**  
✅ Baseline targets defined  
✅ Measurement methodology documented  
✅ Regression guards specified  
⏳ Automated benchmarks pending (this PR)
