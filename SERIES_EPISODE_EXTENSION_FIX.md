# Series Episode URI Extension Fix

## Problem Statement

In the Xtream pipeline for series episodes, URIs were being built with `.mp4` extension even though:
1. The `containerExtension` is stored as `.mkv` in ObjectBox
2. The fallback was recently changed from `mp4` to `mkv` in `DefaultXtreamApiClient.kt`
3. URIs should always be built from the `containerExtension` field (SSOT principle)

## Data Flow Analysis

### Expected Flow
```
API Response (container_extension: "mkv")
  ↓
XtreamEpisodeInfo.containerExtension = "mkv"
  ↓
XtreamEpisode.containerExtension = "mkv"
  ↓
RawMediaMetadata.playbackHints[CONTAINER_EXT] = "mkv"
  ↓
PlaybackContext.extras[CONTAINER_EXT] = "mkv"
  ↓
XtreamPlaybackSourceFactoryImpl.resolveOutputExtension() → "mkv"
  ↓
buildSeriesEpisodeUrl(containerExtension: "mkv") → .../episode.mkv
```

### Issue: Fallback Inconsistency

When `containerExtension` is missing (null or blank), fallback values were inconsistent across layers:

| Layer | Component | Fallback Value | Status |
|-------|-----------|----------------|---------|
| Transport | DefaultXtreamApiClient.kt:785 | `"mkv"` | ✅ Correct |
| Transport | XtreamUrlBuilder.kt:210 | `"mp4"` | ❌ Wrong |
| Transport | XtreamApiModels.kt:42 | `["mp4", "mkv", ...]` | ❌ Wrong |
| Playback | XtreamPlaybackSourceFactoryImpl.kt:545 | `"mp4"` | ❌ Wrong |

## Root Cause

The issue was in THREE places:

### 1. Config Default (XtreamApiModels.kt)
```kotlin
// OLD - Wrong
val seriesExtPrefs: List<String> = listOf("mp4", "mkv", "avi")

// NEW - Correct
val seriesExtPrefs: List<String> = listOf("mkv", "mp4", "avi")
```

The config's `seriesExtPrefs.firstOrNull()` was returning "mp4" as the first preference.

### 2. URL Builder Fallback (XtreamUrlBuilder.kt)
```kotlin
// OLD - Wrong
config.seriesExtPrefs.firstOrNull()?.let { sanitizeSeriesExtension(it) }
    ?: "mp4" // First fallback: mp4

// NEW - Correct
config.seriesExtPrefs.firstOrNull()?.let { sanitizeSeriesExtension(it) }
    ?: "mkv" // First fallback: mkv
```

### 3. Playback Layer Fallback (XtreamPlaybackSourceFactoryImpl.kt)
```kotlin
// OLD - Wrong
if (contentType == CONTENT_TYPE_SERIES) {
    UnifiedLog.w(TAG) {
        "$contentType: No containerExtension provided. Using fallback: mp4"
    }
    return "mp4"
}

// NEW - Correct
if (contentType == CONTENT_TYPE_SERIES) {
    UnifiedLog.w(TAG) {
        "$contentType: No containerExtension provided. Using fallback: mkv"
    }
    return "mkv"
}
```

## Solution

Fixed all three layers to use consistent `"mkv"` fallback:

1. **XtreamApiModels.kt**: Changed seriesExtPrefs default order
   - From: `["mp4", "mkv", "avi"]`
   - To: `["mkv", "mp4", "avi"]`

2. **XtreamUrlBuilder.kt**: Changed hard-coded fallback
   - From: `?: "mp4"`
   - To: `?: "mkv"`

3. **XtreamPlaybackSourceFactoryImpl.kt**: Changed fallback
   - From: `return "mp4"`
   - To: `return "mkv"`

4. **Enhanced Logging**: Added trace logging in `resolveOutputExtension()`:
   ```kotlin
   UnifiedLog.d(TAG) {
       "resolveOutputExtension: contentType=$contentType, containerExt=$containerExt, " +
           "hasNewKey=${context.extras.containsKey(PlaybackHintKeys.Xtream.CONTAINER_EXT)}, " +
           "hasLegacyKey=${context.extras.containsKey(EXTRA_CONTAINER_EXT)}"
   }
   ```

## Test Updates

Updated tests in both modules to reflect correct behavior:

### playback/xtream/XtreamSeriesPlaybackTest.kt
- Changed test to expect `mkv` fallback instead of `mp4`
- Updated test header documentation

### infra/transport-xtream/XtreamSeriesEpisodeUrlTest.kt
- Fixed tests that were checking for OLD incorrect behavior (`/movie/` path)
- Updated to check for CORRECT behavior (`/series/` path)
- Changed fallback expectations from `mp4` to `mkv`

## Impact

### Before Fix
- Series episodes without containerExtension: Used `.mp4` fallback
- Inconsistent behavior across layers
- Potential playback failures if server doesn't support mp4 for that content

### After Fix
- Series episodes without containerExtension: Use `.mkv` fallback
- Consistent behavior across ALL layers (transport, URL builder, playback)
- Proper SSOT: `containerExtension` from ObjectBox is always used when available
- Fallback only used when containerExtension is genuinely missing

## Verification

1. **Build**: ✅ All modules compile successfully
2. **Tests**: ✅ All 88 tests pass (playback + transport)
3. **ktlint**: ✅ Code formatting passes
4. **Logic**: ✅ Data flow traced from API → Storage → Playback

## Files Changed

1. `playback/xtream/src/main/java/.../XtreamPlaybackSourceFactoryImpl.kt`
   - Changed fallback: mp4 → mkv
   - Added enhanced logging

2. `infra/transport-xtream/src/main/java/.../XtreamUrlBuilder.kt`
   - Changed fallback: mp4 → mkv
   - Updated documentation

3. `infra/transport-xtream/src/main/java/.../XtreamApiModels.kt`
   - Changed seriesExtPrefs default order: [mp4, mkv, avi] → [mkv, mp4, avi]

4. `playback/xtream/src/test/java/.../XtreamSeriesPlaybackTest.kt`
   - Updated test expectations: mp4 → mkv fallback

5. `infra/transport-xtream/src/test/java/.../XtreamSeriesEpisodeUrlTest.kt`
   - Fixed tests to check for correct /series/ path
   - Updated fallback expectations: mp4 → mkv

## Future Considerations

1. The `containerExtension` field should always be populated from the Xtream API response
2. If missing, consider fetching series info detail to get the correct extension
3. Monitor logs with the new trace logging to identify cases where fallback is used
4. Consider making mkv vs mp4 configurable per-provider if needed

## Related Documentation

- **Transport Layer**: `infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/DefaultXtreamApiClient.kt`
- **Pipeline Layer**: `pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/adapter/XtreamPipelineAdapter.kt`
- **Playback Layer**: `playback/xtream/src/main/java/com/fishit/player/playback/xtream/XtreamPlaybackSourceFactoryImpl.kt`
- **Data Models**: `pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/model/XtreamEpisode.kt`
