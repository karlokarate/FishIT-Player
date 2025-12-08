# Feature: telegram.lazy_thumbnails

## Metadata

| Property | Value |
|----------|-------|
| **ID** | `telegram.lazy_thumbnails` |
| **Scope** | `PIPELINE` |
| **Owner** | `pipeline:telegram` |
| **Status** | Active |
| **Since** | Phase 1.7 (Dec 2025) |

---

## Description

This feature enables on-demand, lazy loading of Telegram media thumbnails using stable remoteId-based resolution. Thumbnails are only downloaded when needed (e.g., when scrolling into view), reducing bandwidth and improving initial load times.

**Key Innovation:** Uses TDLib's remoteId (stable across sessions) instead of fileId (session-specific) for thumbnail resolution, ensuring thumbnails remain accessible after app restart or TDLib session rotation.

---

## Dependencies

| FeatureId | Required | Description |
|-----------|----------|-------------|
| `infra.logging.unified` | Yes | For structured logging of thumbnail operations |
| `infra.imageloader.coil` | Yes | For image loading and caching (future) |

---

## Guarantees

### Functional Guarantees

1. **RemoteId-First Resolution**
   - Thumbnails are identified by stable remoteId (not volatile fileId)
   - Resolution works across app restarts and TDLib session changes
   - Fallback to fileId when remoteId is unavailable

2. **Lazy Loading**
   - Thumbnails are NOT downloaded during initial sync
   - Download triggered only when item scrolls into view
   - Already-downloaded thumbnails are served from TDLib cache

3. **Priority Management**
   - Thumbnail downloads use lower priority (8) than video files (16)
   - Videos take precedence over thumbnails during bandwidth contention
   - Active playback always prioritized over thumbnail loads

4. **Concurrency Control**
   - Configurable max parallel thumbnail downloads (future: via settings)
   - FIFO queue for pending requests when limit exceeded
   - Automatic cancellation of off-screen thumbnail requests (future)

### Performance Guarantees

| Metric | Target |
|--------|--------|
| Max concurrent thumbnail downloads | 3-5 (configurable) |
| Thumbnail resolution latency | < 500ms (cached) |
| Thumbnail download latency | < 2s (uncached, typical) |
| Memory overhead per thumbnail request | < 10 KB |

---

## Failure Modes

### F1: RemoteId Resolution Failed

- **Trigger:** remoteId cannot be resolved to current session fileId
- **Behavior:** Log warning, return null thumbnail path
- **Error Code:** `TELEGRAM_THUMBNAIL_RESOLVE_FAILED`
- **Fallback:** Show placeholder image in UI

### F2: Download Timeout

- **Trigger:** Thumbnail download exceeds timeout (10s)
- **Behavior:** Cancel download, mark as failed
- **Error Code:** `TELEGRAM_THUMBNAIL_TIMEOUT`
- **Fallback:** Retry on next visibility (max 3 retries)

### F3: Network Unavailable

- **Trigger:** No network during thumbnail request
- **Behavior:** Queue request, retry when network returns
- **Error Code:** `TELEGRAM_THUMBNAIL_NETWORK_ERROR`
- **Fallback:** Show placeholder, auto-retry when online

### F4: Concurrent Limit Exceeded

- **Trigger:** More thumbnail requests than allowed concurrency
- **Behavior:** Enqueue in FIFO, process when slot available
- **Error Code:** N/A (normal behavior)
- **Fallback:** User sees staggered thumbnail appearance

---

## Logging & Telemetry

### Log Tags

| Tag | Level | Description |
|-----|-------|-------------|
| `TelegramThumbnails` | DEBUG | General thumbnail operations |
| `TelegramThumbnails.Resolve` | DEBUG | RemoteId → fileId resolution |
| `TelegramThumbnails.Download` | DEBUG | Download progress |
| `TelegramThumbnails.Error` | WARN | Thumbnail failures |

### Telemetry Events

| Event | Properties | When |
|-------|------------|------|
| `telegram_thumbnail_requested` | `remoteId`, `priority` | Thumbnail load requested |
| `telegram_thumbnail_resolved` | `remoteId`, `fileId`, `durationMs` | RemoteId resolved to fileId |
| `telegram_thumbnail_downloaded` | `remoteId`, `sizeBytes`, `durationMs` | Download completed |
| `telegram_thumbnail_failed` | `remoteId`, `errorCode`, `errorMessage` | Download failed |
| `telegram_thumbnail_cached` | `remoteId` | Served from cache (no download) |

---

## Test Requirements

### Unit Tests

- [x] `requestThumbnailDownload()` resolves remoteId to fileId
- [x] Retry logic with exponential backoff
- [ ] Thumbnail requests use priority 8 (lower than video 16)
- [ ] Failed requests don't block other thumbnails

### Integration Tests

- [ ] Load 50+ thumbnails, verify concurrency limit respected
- [ ] Verify thumbnails persist across app restart (TDLib cache)
- [ ] Verify remoteId resolution works after TDLib session rotation

### Manual Tests

- [ ] Scroll through Telegram media grid, verify lazy loading
- [ ] Verify videos load faster than thumbnails during contention
- [ ] Verify thumbnails remain accessible after app restart

---

## Implementation Notes

### Key Classes

| Class | Role |
|-------|------|
| `TelegramClient` | Interface with `requestThumbnailDownload()` method |
| `DefaultTelegramClient` | Implements remoteId → fileId resolution + download |
| `TelegramMediaViewModel` | Triggers thumbnail loads based on feature availability |
| `TelegramThumbPrefetcher` | Future: Background prefetch service |

### RemoteId Resolution Flow

```
1. UI requests thumbnail by remoteId
2. Check feature: featureRegistry.isSupported(TelegramFeatures.LAZY_THUMBNAILS)
3. If supported:
   a. Call telegramClient.requestThumbnailDownload(remoteId, priority=8)
   b. Client resolves: tdlClient.getRemoteFile(remoteId) → fileId
   c. Client downloads: tdlClient.downloadFile(fileId, priority=8, limit=0)
   d. Return TelegramFileLocation with localPath
4. UI loads image from localPath via imageloader
```

### Priority Hierarchy

| Content Type | Priority | Notes |
|--------------|----------|-------|
| Active video playback | 32 | Highest |
| Video prefetch | 16 | Default video priority |
| Thumbnail downloads | 8 | Lower than videos |
| Background sync | 1 | Lowest |

### Concurrency Management (Future Enhancement)

- **Phase 2:** Add `maxThumbnailConcurrency` setting (default 3)
- **Phase 3:** Implement FIFO queue with automatic slot management
- **Phase 4:** Add visibility-based cancellation (off-screen cancels)

---

## API Reference

### TelegramClient.requestThumbnailDownload()

```kotlin
/**
 * Request thumbnail download by remoteId (cross-session stable).
 *
 * @param remoteId Remote file ID (stable across sessions)
 * @param priority Download priority hint (1-32, default 8 for thumbnails)
 * @return File location metadata once download is initiated
 * @throws TelegramFileException if thumbnail cannot be resolved or downloaded
 */
suspend fun requestThumbnailDownload(
    remoteId: String,
    priority: Int = 8
): TelegramFileLocation
```

### TelegramMediaViewModel.loadThumbnails()

```kotlin
/**
 * Load thumbnails for visible items.
 *
 * Only executes when LAZY_THUMBNAILS feature is supported.
 *
 * @param remoteIds List of remoteIds for thumbnails to load
 */
fun loadThumbnails(remoteIds: List<String>)
```

---

## Migration from v1

### v1 Implementation (T_TelegramFileDownloader)

- Used fileId-first resolution (broken after session rotation)
- Hardcoded priority (16, same as videos)
- No explicit concurrency control
- Thumbnail prefetch logic mixed with playback logic

### v2 Improvements

- ✅ RemoteId-first resolution (stable across sessions)
- ✅ Lower priority for thumbnails (8 vs 16)
- ✅ Clear separation: pipeline downloads, player streams
- ✅ Feature-gated (can be disabled per user/region)
- 🚧 Concurrency limits (deferred to Phase 2)
- 🚧 Visibility-based cancellation (deferred to Phase 4)

---

## Related Documents

| Document | Purpose |
|----------|---------|
| [FEATURE_SYSTEM_TARGET_MODEL.md](../../architecture/FEATURE_SYSTEM_TARGET_MODEL.md) | Feature system overview |
| [TELEGRAM_TDLIB_V2_INTEGRATION.md](../../TELEGRAM_TDLIB_V2_INTEGRATION.md) | TDLib integration guide |
| [TDLIB_STREAMING_THUMBNAILS_SSOT.md](../TDLIB_STREAMING_THUMBNAILS_SSOT.md) | Legacy thumbnail patterns (v1) |
| [TELEGRAM_V2_IMPLEMENTATION_STATUS.md](../TELEGRAM_V2_IMPLEMENTATION_STATUS.md) | Implementation status |

---

## Future Enhancements

### Phase 2: Runtime Settings Integration
- Add `TelegramStreamingSettingsProvider` with thumbnail settings
- Configurable `maxThumbnailConcurrency` (default 3)
- Configurable `thumbnailDownloadTimeout` (default 10s)
- Toggle for thumbnail prefetch on/off

### Phase 3: Smart Prefetch
- Predict next visible items based on scroll velocity
- Prefetch thumbnails 2-3 rows ahead
- Cancel prefetch for items scrolled past

### Phase 4: Advanced Optimization
- WebP thumbnail conversion for bandwidth savings
- Adaptive quality based on connection speed
- Thumbnail sprite sheets for grid views
