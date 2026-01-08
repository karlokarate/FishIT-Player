# core/persistence

This module provides ObjectBox persistence infrastructure for the FishIT-Player application.

## Overview

- **ObjectBox Entities**: Persistent data models (ObxCanonicalMedia, ObxVod, ObxSeries, etc.)
- **Reactive Flows**: `ObjectBoxFlow.kt` - Lifecycle-safe Flow extensions for ObjectBox queries
- **Write Configuration**: `ObxWriteConfig` - SSOT for all batch sizes and write operations
- **Property Converters**: Custom converters for complex types (ImageRef, MediaType, etc.)

## ObxWriteConfig - Batch Size SSOT

`ObxWriteConfig` is the **Single Source of Truth (SSOT)** for all ObjectBox write configurations.

### Key Features

1. **Device-Aware Batch Sizing**: Automatically adjusts batch sizes based on device class
   - FireTV/Low-RAM: Conservative values (35 items for sync, 500 for backfill)
   - Phone/Tablet: Optimized values (100-4000 items based on operation)

2. **Phase-Specific Optimization**: Different batch sizes for different content types
   - Live Channels: 600 items (smaller entities)
   - Movies/VOD: 400 items (medium-sized entities)
   - Series: 200 items (complex entities with relations)
   - Episodes: 200 items (lazy-loaded content)

3. **Extension Functions**: Convenient `Box<T>.putChunked()` for automatic chunking

### Usage Examples

#### Device-Aware Batch Sizing

```kotlin
import com.fishit.player.core.persistence.config.ObxWriteConfig

// Get batch size for current device
val batchSize = ObxWriteConfig.getBatchSize(context)

// Get phase-specific batch size
val liveBatchSize = ObxWriteConfig.getSyncLiveBatchSize(context)
val moviesBatchSize = ObxWriteConfig.getSyncMoviesBatchSize(context)
```

#### Chunked Writes (Device-Aware)

```kotlin
import com.fishit.player.core.persistence.config.ObxWriteConfig
import com.fishit.player.core.persistence.config.ObxWriteConfig.putChunked

val items: List<ObxVod> = // ... your items
val box = boxStore.boxFor(ObxVod::class.java)

// Device-aware chunking (recommended)
box.putChunked(items, context)

// Explicit chunk size
box.putChunked(items, chunkSize = 1000)
```

#### Backfill Operations

```kotlin
val chunkSize = ObxWriteConfig.getBackfillChunkSize(context)
val pageSize = ObxWriteConfig.getPageSize(context)

// Use for paged backfill operations
query.find(offset, pageSize)
```

### Migration from Old Constants

The following constants are now deprecated in favor of `ObxWriteConfig`:

| Old Location | Deprecated Constant | New Location |
|--------------|---------------------|--------------|
| `WorkerConstants` | `FIRETV_BATCH_SIZE` | `ObxWriteConfig.FIRETV_BATCH_CAP` |
| `WorkerConstants` | `NORMAL_BATCH_SIZE` | `ObxWriteConfig.NORMAL_BATCH_SIZE` |
| `SyncPhaseConfig` | Hardcoded `35` in FIRETV_SAFE | `ObxWriteConfig.FIRETV_BATCH_CAP` |

### Device Detection

`ObxWriteConfig` uses `DeviceClassProvider` from `core:device-api` as the SSOT for device detection.
The Android implementation is provided by `AndroidDeviceClassProvider` in `infra:device-android`.

Detection criteria:
- **TV_LOW_RAM**: Android TV devices with < 2GB RAM OR `isLowRamDevice == true`
- **TV**: Android TV devices with >= 2GB RAM (not low RAM)
- **PHONE_TABLET**: All other devices

### Contract Compliance

- **CATALOG_SYNC_WORKERS_CONTRACT_V2 W-17**: FireTV Safety - all batch sizes capped at 35 items
- **PLATIN Guidelines**: Phase-specific batch sizes for optimal performance
- **ObjectBox Best Practices**: Chunked writes for progressive UI updates and memory efficiency

## ObjectBoxFlow

Provides lifecycle-safe reactive Flow extensions for ObjectBox queries.

### Key Pattern

ObjectBox `DataObserver` is a **change trigger**, not a data source. Always re-query on changes:

```kotlin
// ✅ CORRECT: Re-query on change
val items: Flow<List<ObxVod>> = query.asFlow().map { entities ->
    entities.map { it.toRawMediaMetadata() }
}

// ❌ WRONG: Expecting data in observer
query.subscribe().observer { data ->
    // 'data' is NOT the updated list!
}
```

## Property Converters

Custom converters for complex types:

- `ImageRefConverter`: Serializes `ImageRef` sealed interface to JSON
- `MediaTypeConverter`: Converts `MediaType` enum to string
- `PipelineIdTagConverter`: Converts `PipelineIdTag` enum to string

## SSOT Eager Plans

`ObxEagerPlans` is the **Single Source of Truth (SSOT)** for all ObjectBox eager loading patterns.

### Overview

Instead of scattered `.eager()` calls across repositories, all eager loading configurations are centralized in `ObxEagerPlans.kt`. This eliminates N+1 query problems and provides consistent, auditable query optimization.

### Why Centralized Eager Plans?

**Problem without centralization:**
- `.eager()` calls scattered across multiple repositories
- Inconsistent eager loading (some repos forget it)
- N+1 query problems difficult to track
- No single place to optimize query patterns

**Solution with ObxEagerPlans:**
- All eager loading patterns in one place
- Named plans document intent (e.g., `applyHomeContinueWatchingEager`, `applyLibraryVodGridEager`)
- Easy to audit and optimize
- Prevents N+1 regressions via centralized control

### Usage in Repositories

```kotlin
import com.fishit.player.core.persistence.obx.ObxEagerPlans.applyHomeMoviesRowEager

// In repository method:
val query = canonicalMediaBox.query(...)
    .applyHomeMoviesRowEager()  // Apply centralized eager plan
    .build()
```

### Available Plans

#### Home Use-Cases
- `applyHomeContinueWatchingEager()` - Continue Watching row (no-op, uses batch-fetch)
- `applyHomeRecentlyAddedEager()` - Recently Added row
- `applyHomeMoviesRowEager()` - Movies row
- `applyHomeSeriesRowEager()` - Series row
- `applyHomeClipsRowEager()` - Clips row

#### Library Use-Cases
- `applyLibraryVodGridEager()` - VOD grid items (no-op, flat entity)
- `applyLibrarySeriesGridEager()` - Series grid items (no-op, flat entity)

#### Detail Use-Cases
- `applyVodDetailsEager()` - VOD detail screen
- `applySeriesDetailsEager()` - Series detail screen
- `applyEpisodeDetailsEager()` - Episode detail screen

#### Playback Use-Cases
- `applyPlaybackResolveDefaultSourceEager()` - Source resolution for playback

#### Search Use-Cases
- `applySearchResultsEager()` - Cross-repository search results

### Performance Impact

**Before (N+1 Problem):**
- Query 1: Load 100 canonical media items
- Query 2-101: Load sources for each item (100 separate queries)
- Total: **101 queries**

**After (Eager Loading):**
- Query 1: Load 100 canonical media items WITH sources
- Total: **1 query**

**Result:** 50-100x faster for large result sets

### Batch-Fetch Alternative

Some repositories (like `HomeContentRepositoryAdapter`) use **batch-fetch pattern** instead of `.eager()`:

1. Load entity IDs in first query
2. Load all related entities in second query with IN clause
3. Join in-memory

**When to use each:**
- **Eager Loading:** Simple 1-level relations, small result sets
- **Batch-Fetch:** Nested relations, large result sets, complex joins

Both approaches are valid and eliminate N+1 problems. ObxEagerPlans documents the eager alternative.

### Adding New Plans

When you need a new eager loading pattern:

1. Add extension function to `ObxEagerPlans.kt` (not inline in repository)
2. Name it descriptively: `apply<UseCase><Entity>Eager()`
3. Document:
   - Relations loaded
   - Consumer repositories
   - Performance impact

Example:

```kotlin
/**
 * Eager plan for **User Favorites** list.
 *
 * **Relations Loaded:**
 * - ObxFavorite.canonicalMedia → ObxCanonicalMedia
 *
 * **Consumer:**
 * - FavoritesRepositoryAdapter.observeFavorites()
 */
fun QueryBuilder<ObxFavorite>.applyUserFavoritesEager(): QueryBuilder<ObxFavorite> {
    eager(ObxFavorite_.canonicalMedia)
    return this
}
```

### Contract Compliance

Per `HOME_PHASE1_IMPLEMENTATION.md`:
- Eliminates N+1 query problems for Home + Library Grid
- Reduces database lock contention
- Single query instead of N+1 queries

## References

- Contract: `docs/CATALOG_SYNC_WORKERS_CONTRACT_V2.md` (W-17)
- Guidelines: `.github/instructions/app-work.instructions.md`
- Device Detection: `infra/transport-xtream/XtreamTransportConfig.kt`
- Eager Plans: `core/persistence/src/main/java/.../obx/ObxEagerPlans.kt`
- Issue: #609 (Centralized Eager Loading Patterns)
