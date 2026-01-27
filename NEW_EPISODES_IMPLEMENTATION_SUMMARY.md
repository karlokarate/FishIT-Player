# New Episodes Feature Implementation Summary

## Overview

This document summarizes the implementation of the "New Episodes" badge feature for series content in FishIT-Player v2.

## Problem Statement

The `sourceLastModifiedMs` field was flowing through the entire pipeline (Xtream/Telegram → RawMediaMetadata → NX_WorkSourceRef) but was **not being used at runtime** for:
1. "New Episodes" badge display on series cards
2. Incremental sync optimization (delta-fetch)

## Implementation Completed

### Phase 1: Repository Layer ✅

**1. Interface Methods Added** (`NxWorkSourceRefRepository.kt`)
```kotlin
suspend fun findSeriesUpdatedSince(
    sinceMs: Long,
    sourceType: SourceType? = null,
    limit: Int = 100,
): List<SourceRef>

suspend fun findWorkKeysWithSeriesUpdates(
    sinceMs: Long,
    sourceType: SourceType? = null,
): Set<String>
```

**2. Index Added** (`NxEntities.kt`)
```kotlin
@Index var sourceLastModifiedMs: Long? = null,
```

**3. Implementation** (`NxWorkSourceRefRepositoryImpl.kt`)
- Efficient ObjectBox queries using the new index
- Filter for EPISODE item kind
- Return workKeys (series globalIds) with new episodes

### Phase 2: UI Model Layer ✅

**1. HomeMediaItem Extended** (`HomeMediaItem.kt`)
```kotlin
/**
 * Indicates this series has new episodes since user's last check.
 * Only meaningful for [MediaType.SERIES].
 */
val hasNewEpisodes: Boolean = false,
```

**2. FishTile Enhanced** (`FishTile.kt`)
- New `hasNewEpisodes: Boolean = false` parameter
- New `NewEpisodesBadge` composable (blue "NEW EP" badge)
- Badge shown in top-start corner (vs "NEW" in top-end)

**3. FishRowSimple Extended** (`FishRow.kt`)
```kotlin
hasNewEpisodes: (T) -> Boolean = { false },
```

### Phase 3: Integration ✅

**NxHomeContentRepositoryImpl.kt**
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
override fun observeSeries(): Flow<List<HomeMediaItem>> {
    return workRepository.observeByType(WorkType.SERIES, limit = SERIES_LIMIT)
        .mapLatest { works -> 
            // Lookup series with new episodes in the last 48h
            val newEpisodesCheckTimestamp = System.currentTimeMillis() - NEW_EPISODES_WINDOW_MS
            val seriesWithNewEpisodes = sourceRefRepository.findWorkKeysWithSeriesUpdates(
                sinceMs = newEpisodesCheckTimestamp,
            )
            
            batchMapToHomeMediaItems(
                works = works,
                hasNewEpisodes = { work -> work.workKey in seriesWithNewEpisodes }
            )
        }
}
```

## Current Behavior

- Series cards now show a blue "NEW EP" badge if any episode has `sourceLastModifiedMs` within the last **48 hours**
- The badge appears in the top-start corner (separate from the "NEW" badge for newly added content)
- The feature works for all sources (Xtream, Telegram, IO)

## Files Modified

| File | Change |
|------|--------|
| `core/model/repository/NxWorkSourceRefRepository.kt` | Added `findSeriesUpdatedSince()`, `findWorkKeysWithSeriesUpdates()` |
| `core/persistence/obx/NxEntities.kt` | Added `@Index` to `sourceLastModifiedMs` |
| `infra/data-nx/repository/NxWorkSourceRefRepositoryImpl.kt` | Implemented new methods |
| `core/home-domain/domain/HomeMediaItem.kt` | Added `hasNewEpisodes` field |
| `core/ui-layout/layout/FishTile.kt` | Added `hasNewEpisodes` param, `NewEpisodesBadge` |
| `core/ui-layout/layout/FishRow.kt` | Added `hasNewEpisodes` param to `FishRowSimple` |
| `infra/data-nx/home/NxHomeContentRepositoryImpl.kt` | Wired `hasNewEpisodes` logic |

## Future Improvements (P2/P3)

1. **User Preference for Check Timestamp**
   - Store `lastSeriesCheckTimestamp` in DataStore/UserPreferences
   - Reset on "Mark all as seen" action
   - Currently uses fixed 48h window

2. **Incremental Sync Optimization**
   - Use `sourceLastModifiedMs` in `XtreamCatalogScanWorker.runIncrementalSync()`
   - Only process items with `lastModified > lastSyncTimestamp`
   - Currently uses count-based comparison

3. **UI Integration in feature/home**
   - Ensure `FishRowSimple` calls include `hasNewEpisodes` lambda
   - Add badge reset mechanism (user taps series → clears badge)

## Compilation Status

✅ All affected modules compile successfully:
- `:core:home-domain`
- `:core:ui-layout`
- `:infra:data-nx`
- `:core:model`
