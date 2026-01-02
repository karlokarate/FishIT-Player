# Xtream SERIES Playback Fix - Verification Guide

## What Was Fixed

**Core Issue:** Series episodes were using `/movie/` or `/vod/` path instead of `/series/` path, causing playback failures.

**Solution:** Changed URL construction in two places:
1. `XtreamUrlBuilder.seriesEpisodeUrl()` - Now uses `/series/` path
2. `DefaultXtreamApiClient.buildSeriesEpisodeUrl()` - Now uses `/series/` path

## Manual Verification Steps

### Prerequisites
- Debug build of FishIT-Player v2
- Active Xtream account with series content
- Chucker HTTP inspector enabled (automatically included in debug builds)

### Test Procedure

1. **Start a Series Episode**
   - Navigate to a series in the Xtream catalog
   - Select any episode
   - Tap "Play"

2. **Observe in Chucker HTTP Inspector**
   - Open Chucker from notification or app menu
   - Look for the playback request

3. **Verify Request #1 (Initial Request)**
   ```
   Method: GET
   URL: http://<server>:<port>/series/<username>/<password>/<episodeId>.mp4
   Headers:
     - User-Agent: FishIT-Player/2.x (Android)
     - Accept: */*
     - Accept-Encoding: identity
   ```
   
   Expected: `302 Found` response with `Location` header pointing to CDN

4. **Verify Request #2 (Redirected Request)**
   ```
   Method: GET
   URL: http://<cdn-ip>:<port>/path?token=<token>
   Headers:
     - User-Agent: FishIT-Player/2.x (Android)
     - Accept: */*
     - Accept-Encoding: identity
   ```
   
   Expected: `200 OK` or `206 Partial Content` with `Content-Type: video/*`

5. **Verify Playback**
   - Episode starts playing within 3-5 seconds
   - Video quality is correct
   - Audio plays correctly
   - Seeking (forward/backward) works
   - No buffering issues

### Success Criteria

✅ **URL Path:** Request uses `/series/` NOT `/movie/` or `/vod/`  
✅ **Extension:** Container extension preserved (e.g., `.mkv` stays `.mkv`)  
✅ **Redirect:** 302 redirect followed automatically  
✅ **Headers:** Correct headers on both requests  
✅ **Playback:** Video starts and plays smoothly  
✅ **Seeking:** Forward/backward seeking works  

### Failure Indicators

❌ Request uses `/movie/` or `/vod/` path → Code not deployed correctly  
❌ 404 Not Found response → Server doesn't support `/series/` path (report to provider)  
❌ Redirect not followed → OkHttp configuration issue (should not happen)  
❌ Wrong Content-Type → Extension mismatch or server issue  

## Troubleshooting

### If `/movie/` path is still used:
1. Verify you're running the latest debug build
2. Check that `episodeId` hint is present in PlaybackContext
3. Review XtreamPlaybackSourceFactoryImpl logs for series URL construction

### If 404 error occurs:
1. Check if provider supports standard Xtream `/series/` endpoint
2. Verify episodeId is correct (should be episode's stream ID from get_series_info)
3. Test with different episodes to rule out episode-specific issues

### If redirect fails:
1. Check OkHttp configuration in XtreamHttpDataSourceFactory
2. Verify `followRedirects(true)` and `followSslRedirects(true)` are set
3. Check for network interceptors that might block redirects

## Logs to Collect (If Issues Occur)

Enable debug logging and collect:
```
adb logcat -s XtreamPlaybackFactory:V XtreamHttpDataSource:V
```

Look for:
- "Built stream URL for series content with extension: ..."
- "→ Xtream HTTP request: ..."
- "← Xtream HTTP success: ..."

**Note:** Do NOT share logs publicly without redacting credentials and tokens!

## Rollback Plan

If series playback fails after this fix:
1. Revert to previous version
2. Report issue with:
   - Xtream provider name
   - Episode that fails
   - Chucker screenshot (redacted)
   - Logcat output (redacted)

## Contact

For issues or questions, open a GitHub issue with:
- Version: `copilot/fix-xtream-series-playback` (commit: d0bc2ea)
- Device: [Your device model]
- Android version: [Your Android version]
- Provider: [Redacted if sensitive]
- Description: [What happened vs. what you expected]
