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

## References

- Contract: `docs/CATALOG_SYNC_WORKERS_CONTRACT_V2.md` (W-17)
- Guidelines: `.github/instructions/app-work.instructions.md`
- Device Detection: `infra/transport-xtream/XtreamTransportConfig.kt`
- Issue: #XXX (ObjectBox Performance Optimization)
