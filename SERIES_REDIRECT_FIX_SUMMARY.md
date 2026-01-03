# Series Playback 302 Redirect Fix - Summary

## Problem Description

Series episodes were failing to play while movies worked correctly. The issue was traced to URL format differences in how the application constructed playback URLs for series content.

### Observed Behavior

**Working (Movies):**
```
Request 1: GET /movie/Christoph10/JQ2rKsQ744/787926.mkv
Response: 302 Found (redirect with token)
Request 2: GET /movie/Christoph10/JQ2rKsQ744/787926.mkv?token=<TOKEN>
Result: ✅ Playback starts successfully
```

**Failing (Series):**
```
Request 1: GET /series/Christoph10/JQ2rKsQ744/12663/1/1.mkv
Result: ❌ No 302 redirect, playback fails
```

## Root Cause Analysis

The application has two URL formats for series episodes:

1. **Direct Path (Modern):** `/series/{user}/{pass}/{episodeId}.ext`
   - Uses the episode's stream ID directly
   - Works with 302 redirects and tokenized CDN URLs
   - ✅ This is the correct format

2. **Legacy Path:** `/series/{user}/{pass}/{seriesId}/{season}/{episode}.ext`
   - Uses series ID + season/episode numbers
   - Does NOT work reliably with 302 redirects on modern Xtream panels
   - ❌ This is the problematic format

### Why Was Legacy Path Being Used?

In `XtreamPipelineAdapter.toEpisodes()`, episodes were being created with a fallback:

```kotlin
// OLD CODE
id = ep.resolvedEpisodeId ?: 0,  // Defaults to 0 if missing
```

Then in `XtreamUrlBuilder.seriesEpisodeUrl()`:

```kotlin
if (episodeId != null && episodeId > 0) {
    // Use direct path: /series/user/pass/episodeId.ext
} else {
    // Fall back to legacy path: /series/user/pass/seriesId/season/episode.ext
}
```

When `episodeId` was `0`, the legacy path was used, which doesn't work with modern panel redirect mechanisms.

## Solution

**Filter out episodes without valid episodeId during pipeline conversion:**

```kotlin
// NEW CODE in XtreamPipelineAdapter.toEpisodes()
val episodeId = ep.resolvedEpisodeId
if (episodeId == null || episodeId <= 0) {
    // Skip this episode - it can't be played reliably
    return@forEach
}

result.add(
    XtreamEpisode(
        id = episodeId,  // Now guaranteed to be valid
        // ... other fields
    )
)
```

### Why This Works

1. Only episodes with valid stream IDs are created in the pipeline
2. All valid episodes will use the direct path format: `/series/{user}/{pass}/{episodeId}.ext`
3. The direct path format works correctly with 302 redirects and tokenized CDN URLs
4. Episodes without IDs are filtered out (better than showing unplayable content)

## API Model Support

The `XtreamEpisodeInfo` model already handles both field names that providers might use:

```kotlin
data class XtreamEpisodeInfo(
    val id: Int? = null,
    @SerialName("episode_id") val episodeId: Int? = null,
    // ...
) {
    val resolvedEpisodeId: Int?
        get() = episodeId ?: id  // Prefers episode_id over id
}
```

This accommodates different Xtream panel implementations (including KönigTV which uses `episode_id`).

## Impact

### Before Fix
- Series episodes without valid `episode_id` would default to `id=0`
- This triggered legacy path usage
- Legacy path doesn't work with 302 redirects → playback fails
- User sees broken/unplayable episodes

### After Fix
- Episodes without valid IDs are filtered out during pipeline conversion
- Only episodes with valid stream IDs appear in the UI
- All visible episodes use the direct path format
- Direct path works correctly with 302 redirects → playback succeeds
- User only sees playable episodes

## Testing

### Unit Tests
- ✅ All pipeline tests pass (`./gradlew :pipeline:xtream:testDebugUnitTest`)
- ✅ All playback tests pass (`./gradlew :playback:xtream:testDebugUnitTest`)

### Manual Verification
To verify the fix:
1. Build debug version of the app
2. Navigate to a series with episodes
3. Select and play an episode
4. Open Chucker HTTP Inspector (debug builds only)
5. Verify the request uses format: `/series/{user}/{pass}/{episodeId}.mkv`
6. Verify 302 redirect is followed with token
7. Verify playback starts successfully

## Related Files

### Changed
- `pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/adapter/XtreamPipelineAdapter.kt`
  - Added episodeId validation and filtering in `toEpisodes()`

### Also Fixed
- `pipeline/xtream/src/test/java/com/fishit/player/pipeline/xtream/model/XtreamRawMetadataExtensionsTest.kt`
  - Updated `tmdbId` → `tmdb` references (API model change)

### Related (No Changes Needed)
- `infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamApiModels.kt`
  - Already handles both `id` and `episode_id` fields correctly
- `infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamUrlBuilder.kt`
  - Already uses direct path when `episodeId > 0`
- `playback/xtream/src/main/java/com/fishit/player/playback/xtream/XtreamHttpDataSourceFactory.kt`
  - Already configured to follow 302 redirects with `followRedirects(true)`

## Future Considerations

1. **Logging:** Consider adding debug logging when episodes are filtered out to help diagnose provider issues
2. **User Feedback:** Could show a toast/warning if many episodes are filtered (indicates provider data quality issue)
3. **Provider Support:** Monitor for providers that genuinely don't provide episode IDs and may need special handling

## References

- Problem statement: User report comparing working movie vs failing series requests
- Xtream API spec: Series episode playback requires `/series/{user}/{pass}/{episodeId}.ext` format for modern panels
- Related documentation: `XTREAM_SERIES_PLAYBACK_FIX_VERIFICATION.md`
