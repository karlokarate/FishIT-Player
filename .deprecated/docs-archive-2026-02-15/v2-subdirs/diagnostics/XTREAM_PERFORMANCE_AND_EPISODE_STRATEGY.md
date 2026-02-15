# Xtream Performance & Episode Strategy

> **Status:** Implemented  
> **Version:** 1.0  
> **Date:** 2025-01-17

## Overview

This document describes the performance optimization strategy for Xtream catalog ingestion and browsing in FishIT-Player v2. The strategy targets large libraries (30k+ movies, 4k+ series, 9k+ live channels) to ensure a fast, responsive user experience.

## Goals

### Part A: Catalog Sync Speed
- Live + Movies tiles appear **progressively within seconds** (no "all at the end")
- Series tiles appear without waiting for episode data
- No UI jank/OOM during sync

### Part B: Platinum Episode Handling  
- Opening a Series shows **seasons immediately**
- Opening a Season loads **episodes lazily (paged)** with predictive prefetch
- Playback is **always deterministic and race-proof**

---

## Architecture

### Layer Boundaries

```
┌─────────────────────────────────────────────────────────────┐
│  UI Layer (feature/detail, feature/library, feature/live)   │
│    - Observes Flows from repositories                       │
│    - Throttled updates during sync (400ms debounce)         │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Domain Layer (Use Cases in feature/*)                      │
│    - LoadSeriesSeasonsUseCase                               │
│    - LoadSeasonEpisodesUseCase                              │
│    - EnsureEpisodePlaybackReadyUseCase                      │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Data Layer (infra/data-xtream)                             │
│    - XtreamSeriesIndexRepository (seasons, episodes)        │
│    - XtreamCatalogRepository (VOD, Series, Live)            │
│    - ObxSeasonIndex, ObxEpisodeIndex entities               │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Pipeline Layer (pipeline/xtream)                           │
│    - XtreamCatalogPipeline (phase-ordered scan)             │
│    - Produces RawMediaMetadata only                         │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Transport Layer (infra/transport-xtream)                   │
│    - XtreamApiClient (rate-limited, cached)                 │
└─────────────────────────────────────────────────────────────┘
```

---

## Part A: Phase Ordering & Batching

### Phase Order

The scan order is optimized for perceived speed:

1. **Phase 1: Live Channels** (9k items, ~2-3 sec)
   - Fastest to sync, most visible on home screen
   - Batch size: **400 items**

2. **Phase 2: VOD/Movies** (30k items, ~15-20 sec)  
   - Second most visible category
   - Batch size: **250 items**

3. **Phase 3: Series Metadata** (4.6k items, ~3-5 sec)
   - Series headers only (no episodes)
   - Batch size: **150 items**

4. **Phase 4: Episodes** (SKIPPED during initial sync)
   - Loaded lazily when user opens a series

### Time-Based Flushing

Instead of fixed-count batches, we use **time-based flushing**:

```kotlin
// core/catalog-sync/SyncBatchManager.kt
private const val FLUSH_INTERVAL_MS = 1200L  // 1.2 seconds

// On each item discovered:
batchManager.add(phase, item)
if (batchManager.shouldFlush(phase)) {
    // Persist batch to ObjectBox
    repository.upsertBatch(batch)
}
```

**Benefits:**
- UI sees updates every ~1.2 seconds regardless of item count
- Prevents "all at the end" behavior
- Adapts to slow networks automatically

### Batch Sizes

| Phase | Batch Size | Rationale |
|-------|------------|-----------|
| Live | 400 | Small items, fast parsing |
| VOD | 250 | Larger items with images |
| Series | 150 | Complex metadata |
| Episodes | 100 | Per-season, lazy loaded |

---

## Part B: Episode Strategy

### Season Index

Seasons are stored in `ObxSeasonIndex`:

```kotlin
@Entity
data class ObxSeasonIndex(
    @Id var id: Long = 0,
    var seriesId: Int = 0,
    var seasonNumber: Int = 0,
    var episodeCount: Int? = null,
    var name: String? = null,
    var coverUrl: String? = null,
    var airDate: String? = null,
    var lastUpdatedMs: Long = 0,
)
```

**TTL:** 7 days

### Episode Index

Episodes are stored in `ObxEpisodeIndex` with playback hints:

```kotlin
@Entity  
data class ObxEpisodeIndex(
    @Id var id: Long = 0,
    var seriesId: Int = 0,
    var seasonNumber: Int = 0,
    var episodeNumber: Int = 0,
    var sourceKey: String = "",  // "xtream:episode:123:1:5"
    var episodeId: Int? = null,
    var title: String? = null,
    var thumbUrl: String? = null,
    var durationSecs: Int? = null,
    var plotBrief: String? = null,
    var rating: Double? = null,
    var airDate: String? = null,
    var playbackHintsJson: String? = null,  // JSON with stream_id, container_ext
    var lastUpdatedMs: Long = 0,
    var playbackHintsUpdatedMs: Long = 0,
)
```

**Index TTL:** 7 days  
**Playback Hints TTL:** 30 days

### Lazy Loading Flow

```
User Opens Series
        │
        ▼
┌───────────────────┐
│ Seasons cached?   │
│ (< 7 days old)    │
└─────────┬─────────┘
          │
     ┌────┴────┐
     │ No      │ Yes
     ▼         ▼
┌──────────┐  ┌──────────┐
│ Fetch    │  │ Return   │
│ from API │  │ cached   │
└────┬─────┘  └──────────┘
     │
     ▼
┌──────────┐
│ Persist  │
│ seasons  │
└────┬─────┘
     │
     ▼
User Sees Seasons (< 500ms)
```

### Paged Episodes

Episodes are loaded **per-page** (default: 30 per page):

```kotlin
fun observeEpisodes(
    seriesId: Int,
    seasonNumber: Int,
    page: Int = 0,
    pageSize: Int = 30,
): Flow<List<EpisodeIndexItem>>
```

**Prefetch:** When user scrolls to 80% of current page, prefetch next page.

### Deterministic Playback

**Problem:** User taps "Play" while episode data is still loading.

**Solution:** `EnsureEpisodePlaybackReadyUseCase`

```kotlin
val result = ensurePlaybackReady(sourceKey = "xtream:episode:123:1:5")

when (result) {
    is Ready -> {
        // hints.streamId, hints.containerExtension available
        val url = apiClient.buildSeriesUrl(hints.streamId, hints.containerExtension)
        player.play(url)
    }
    is Failed -> {
        showError("Episode not available")
    }
}
```

**Guarantees:**
1. Always checks repository first (no API call if hints fresh)
2. Fetches from API with 10-second timeout if needed
3. Returns stable `sourceKey` for playback URL construction
4. Race-proof: reads from repository after persist

---

## Use Cases

### LoadSeriesSeasonsUseCase

```kotlin
// feature/detail/series/SeriesEpisodeUseCases.kt

@Singleton
class LoadSeriesSeasonsUseCase @Inject constructor(
    private val repository: XtreamSeriesIndexRepository,
    private val apiClient: XtreamApiClient,
) {
    fun observeSeasons(seriesId: Int): Flow<List<SeasonIndexItem>>
    
    suspend fun ensureSeasonsLoaded(seriesId: Int, forceRefresh: Boolean = false): Boolean
}
```

### LoadSeasonEpisodesUseCase

```kotlin
@Singleton
class LoadSeasonEpisodesUseCase @Inject constructor(
    private val repository: XtreamSeriesIndexRepository,
    private val apiClient: XtreamApiClient,
) {
    fun observeEpisodes(seriesId: Int, seasonNumber: Int, page: Int, pageSize: Int): Flow<List<EpisodeIndexItem>>
    
    suspend fun ensureEpisodesLoaded(seriesId: Int, seasonNumber: Int, forceRefresh: Boolean = false): Boolean
}
```

### EnsureEpisodePlaybackReadyUseCase

```kotlin
@Singleton
class EnsureEpisodePlaybackReadyUseCase @Inject constructor(
    private val repository: XtreamSeriesIndexRepository,
    private val apiClient: XtreamApiClient,
) {
    sealed class Result {
        data class Ready(val sourceKey: String, val hints: EpisodePlaybackHints) : Result()
        data class Failed(val sourceKey: String, val reason: String) : Result()
    }
    
    suspend fun invoke(sourceKey: String, forceEnrich: Boolean = false): Result
}
```

---

## Performance Metrics (Debug Builds)

The `SyncPerfMetrics` collector tracks:

| Metric | Description |
|--------|-------------|
| `fetch_ms` | Time to fetch from API |
| `parse_ms` | Time to parse JSON |
| `persist_ms` | Time to persist batch |
| `items_discovered_per_sec` | Discovery rate |
| `items_persisted_per_sec` | Persistence rate |

Access via: `catalogSyncService.getLastSyncMetrics()`

---

## File Locations

| Component | Location |
|-----------|----------|
| Phase Config | `core/catalog-sync/.../SyncPhaseConfig.kt` |
| Batch Manager | `core/catalog-sync/.../SyncBatchManager.kt` |
| Perf Metrics | `core/catalog-sync/.../SyncPerfMetrics.kt` |
| Season/Episode Repository | `infra/data-xtream/.../XtreamSeriesIndexRepository.kt` |
| Repository Impl | `infra/data-xtream/.../ObxXtreamSeriesIndexRepository.kt` |
| Use Cases | `feature/detail/.../series/SeriesEpisodeUseCases.kt` |
| ObjectBox Entities | `core/persistence/.../ObxEntities.kt` |

---

## Testing

### Unit Tests

1. **Paging:** Verify correct offset/limit in episode queries
2. **TTL:** Verify stale entries are invalidated
3. **Race Conditions:** Verify playback hints are available after enrichment

### Regression Tests

1. **Tap play while enrichment running:** Should wait for enrichment, not fail
2. **Large library sync:** Should not OOM, tiles appear progressively
3. **Network failure during sync:** Should resume from last successful phase

---

## Future Improvements

1. **Flow Throttling:** Add 400ms debounce during active sync
2. **Transport Concurrency:** Limit list calls to 1-2 concurrent, detail calls to 6
3. **Predictive Prefetch:** Load next season's first page when user nears end of current
4. **Background Refresh:** WorkManager job to refresh stale indices overnight
