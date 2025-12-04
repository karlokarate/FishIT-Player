# Telegram Full History Loading & Lazy Thumbnail Loading

## Overview

This implementation provides two major features for the Telegram integration:

1. **Unlimited Chat History Loading**: Remove artificial message limits and load entire chat histories
2. **Lazy, Viewport-Driven Thumbnail Loading**: On-demand thumbnail downloads with stable caching

## Part 1: Unlimited Chat History Loading

### Changes Made

#### TelegramHistoryScanner
- Changed `ScanConfig.maxPages` default from `10` to `Int.MAX_VALUE` (unlimited)
- Scanner now continues until TDLib returns empty batches
- Updated documentation to reflect unlimited scanning capability

#### TelegramContentRepository
- Added `syncFullChatHistory(chatId: Long, chatTitle: String?, serviceClient: T_TelegramServiceClient): Result<Int>` method
- Implements incremental batch persistence to avoid memory issues
- Uses parser pipeline (TelegramBlockGrouper + TelegramItemBuilder) to process messages
- Idempotent: repeated calls won't create duplicates (enforced by database-level uniqueness on chatId + anchorMessageId)
- Structured logging for pagination progress

### Usage Example

```kotlin
// In ViewModel
viewModelScope.launch {
    val repository = TelegramContentRepository(context, settingsStore)
    val serviceClient = T_TelegramServiceClient.getInstance(context)
    
    // Ensure service is started and auth is ready
    serviceClient.ensureStarted(context, settingsStore)
    if (!serviceClient.awaitAuthReady(timeoutMs = 30_000L)) {
        // Handle auth not ready
        return@launch
    }
    
    // Sync full chat history
    val result = repository.syncFullChatHistory(
        chatId = 123456789L,
        chatTitle = "My Chat", // Optional
        serviceClient = serviceClient
    )
    
    when {
        result.isSuccess -> {
            val itemsPersisted = result.getOrNull() ?: 0
            Log.i(TAG, "Synced $itemsPersisted items")
        }
        result.isFailure -> {
            val error = result.exceptionOrNull()
            Log.e(TAG, "Sync failed: ${error?.message}", error)
        }
    }
}
```

### Behavior

- **No artificial limits**: History loading continues until TDLib returns no more messages
- **Incremental persistence**: Messages are persisted in batches (100 messages at a time) to avoid memory issues
- **De-duplication**: Database enforces uniqueness via (chatId, anchorMessageId)
- **Resumable**: Can be called multiple times; existing messages won't be duplicated
- **Background-friendly**: Designed to run in WorkManager for large chats

### Integration Points

- **TelegramSyncWorker**: Can be updated to use `syncFullChatHistory()` instead of limited scans
- **TelegramIngestionCoordinator**: Can call `syncFullChatHistory()` when processing chats
- **UI**: Chat add/refresh actions can trigger full history sync

## Part 2: Lazy, Viewport-Driven Thumbnail Loading

### Changes Made

#### TelegramThumbKey Model
- New data class for stable cache keys: `TelegramThumbKey(remoteId, kind, sizeBucket)`
- `ThumbKind` enum: `POSTER`, `CHAT_MESSAGE`, `PREVIEW`
- Cache key format: `"tg_thumb_{kind}_{sizeBucket}_{remoteId}"`
- **Key stability**: Based on `remoteId` (stable across sessions), not `fileId` (unstable)

#### TelegramThumbFetcher
- Coil 3 Fetcher implementation for `TelegramThumbKey`
- On-demand thumbnail downloads via `T_TelegramFileDownloader`
- Concurrency control: `Semaphore(4)` limits concurrent TDLib downloads
- Fetches on IO dispatcher
- Resolves `remoteId → fileId` via `TelegramContentRepository.resolveThumbFileId()`

#### TelegramThumbKeyer
- Coil 3 Keyer for stable cache key generation
- Uses `TelegramThumbKey.toCacheKey()` method

#### AppImageLoader
- Updated to register:
  - `TelegramThumbFetcher.Factory(context)` for on-demand loading
  - `TelegramThumbKeyer()` for stable cache keys
  - `TelegramLocalFileFetcher.Factory()` for legacy file path support (backward compatibility)

#### TelegramContentRepository
- Added `resolveThumbFileId(remoteId: String, kind: ThumbKind, sizeBucket: Int): Int?` method
- Queries `ObxTelegramMessage` by `remoteId`
- Prefers `thumbFileId`, falls back to main `fileId`
- Returns `null` if no file reference exists

### Usage Example

```kotlin
@Composable
fun TelegramMessageRow(message: TelegramMessageUi) {
    val context = LocalContext.current
    
    if (message.hasThumbnail && message.remoteId != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(
                    TelegramThumbKey(
                        remoteId = message.remoteId,
                        kind = ThumbKind.CHAT_MESSAGE,
                        sizeBucket = 256 // 256px size bucket
                    )
                )
                .crossfade(true)
                .build(),
            contentDescription = "Thumbnail",
            imageLoader = AppImageLoader.get(context),
            modifier = Modifier.size(200.dp)
        )
    }
}
```

### Behavior

- **Lazy loading**: Thumbnails only download when rows enter or approach the viewport (Coil handles this automatically)
- **Stable caching**: Cache keys based on `remoteId` survive app restarts
- **Concurrency limits**: Semaphore(4) prevents TDLib overload during fast scrolling
- **Automatic caching**: Coil manages disk and memory caching
- **No bitmap storage in DB**: Only lightweight metadata (remoteId, thumbFileId, dimensions) is stored

### Cache Key Stability

**Why remoteId, not fileId?**
- `fileId` is TDLib's internal handle and can change between sessions
- `remoteId` (or `remote.unique_id`) is stable and persists across sessions
- Cache keys must be stable to allow cache hits after app restarts

**Cache Key Format:**
```
tg_thumb_CHAT_MESSAGE_256_AQADAgADyqoxG
tg_thumb_POSTER_512_AQADAwADzqoxG
tg_thumb_PREVIEW_0_AQADBAAD0qoxG
```

### Integration Points

- **LazyColumn/LazyVerticalGrid**: Use `AsyncImage` with `TelegramThumbKey` in item Composables
- **Existing Xtream image loading**: Continues to work via same `AppImageLoader`
- **ObxTelegramMessage**: Already has `remoteId` and `thumbFileId` fields

## Testing

### Unit Tests

#### TelegramThumbKeyTest
- Cache key stability across instances
- Differentiation by remoteId, kind, and sizeBucket
- Default values (sizeBucket = 0)
- Data class equality

#### TelegramHistoryScannerTest
- Updated to reflect `maxPages = Int.MAX_VALUE` default

### Manual Testing

To test the implementation:

1. **Full History Sync**:
   - Select a chat with many messages (hundreds or thousands)
   - Trigger `syncFullChatHistory()` in ViewModel
   - Monitor logs for batch progress
   - Verify all messages are persisted in DB
   - Check memory usage remains stable

2. **Lazy Thumbnail Loading**:
   - Open a chat with many thumbnails
   - Scroll through messages rapidly
   - Observe that thumbnails load only when entering viewport
   - Scroll back up to verify cached thumbnails load instantly
   - Restart app and scroll to verify cache hits from disk

## Performance Considerations

### Memory

- **History loading**: Batches of 100 messages processed at a time, then discarded
- **Thumbnail loading**: Only lightweight metadata in memory; bitmaps managed by Coil
- **Concurrency**: Semaphore(4) prevents excessive concurrent downloads

### Network

- **History loading**: TDLib handles network efficiency internally
- **Thumbnail loading**: Downloads only occur once per thumbnail; subsequent views use cache
- **Prefetching**: Coil handles prefetching slightly ahead of scroll position

### Disk

- **Database growth**: Full histories for large chats will increase DB size
- **Image cache**: Coil's disk cache (256MB-768MB depending on device) stores thumbnails
- **Cache eviction**: Coil handles LRU eviction automatically

## Migration from Existing Code

### For History Loading

**Before:**
```kotlin
val config = TelegramHistoryScanner.ScanConfig(maxPages = 10)
scanner.scan(chatId, config)
```

**After:**
```kotlin
// Defaults to unlimited
val config = TelegramHistoryScanner.ScanConfig()
scanner.scan(chatId, config)

// Or use high-level API
repository.syncFullChatHistory(chatId, serviceClient = serviceClient)
```

### For Thumbnail Loading

**Before (if using file paths directly):**
```kotlin
AsyncImage(
    model = "/path/to/thumbnail.jpg",
    ...
)
```

**After:**
```kotlin
AsyncImage(
    model = ImageRequest.Builder(context)
        .data(
            TelegramThumbKey(
                remoteId = message.remoteId,
                kind = ThumbKind.CHAT_MESSAGE,
                sizeBucket = 256
            )
        )
        .build(),
    imageLoader = AppImageLoader.get(context),
    ...
)
```

## Troubleshooting

### Full History Sync Issues

**Problem**: Sync fails with auth error
- **Solution**: Ensure `serviceClient.awaitAuthReady()` returns `true` before calling `syncFullChatHistory()`

**Problem**: Sync is too slow for very large chats
- **Solution**: Run sync in a background WorkManager worker; monitor progress via logs

**Problem**: Memory usage spikes during sync
- **Solution**: Verify batches are being persisted incrementally (check logs); ensure you're not holding all messages in memory

### Thumbnail Loading Issues

**Problem**: Thumbnails don't load
- **Solution**: Verify `TelegramServiceClient.isStarted` returns `true`; check logs for errors

**Problem**: Cache keys change between sessions
- **Solution**: Ensure you're using `remoteId`, not `fileId`, in `TelegramThumbKey`

**Problem**: Too many concurrent downloads during fast scrolling
- **Solution**: Semaphore(4) should handle this; if issues persist, reduce semaphore limit in `TelegramThumbFetcher`

## Future Enhancements

### History Loading
- Partial history refresh (only fetch new messages since last sync)
- Progress callbacks for UI progress bars
- Cancellation support for long-running syncs

### Thumbnail Loading
- Size-aware thumbnail selection (choose appropriate sizeBucket based on display size)
- Quality tiers (low/high quality based on network conditions)
- Batch prefetching for known scroll patterns

## References

- **Problem Statement**: See original task description
- **Coil 3 Documentation**: https://coil-kt.github.io/coil/
- **TDLib API**: https://core.telegram.org/tdlib/docs/
- **g00sha tdlib-coroutines**: https://github.com/g000sha256/tdl-coroutines

## Files Changed

### New Files
- `app/src/main/java/com/chris/m3usuite/telegram/image/TelegramThumbKey.kt`
- `app/src/test/java/com/chris/m3usuite/telegram/image/TelegramThumbKeyTest.kt`
- `app/src/main/java/com/chris/m3usuite/telegram/ui/examples/TelegramLazyLoadingExample.kt`
- `TELEGRAM_FULL_HISTORY_LAZY_THUMBS_IMPLEMENTATION.md` (this file)

### Modified Files
- `app/src/main/java/com/chris/m3usuite/telegram/ingestion/TelegramHistoryScanner.kt`
  - Changed `ScanConfig.maxPages` default to `Int.MAX_VALUE`
- `app/src/main/java/com/chris/m3usuite/data/repo/TelegramContentRepository.kt`
  - Added `syncFullChatHistory()` method
  - Added `resolveThumbFileId()` method
- `app/src/main/java/com/chris/m3usuite/telegram/image/TelegramThumbFetcher.kt`
  - Completely rewritten to support `TelegramThumbKey`
  - Added `TelegramThumbKeyer` for stable cache keys
  - Kept legacy `TelegramLocalFileFetcher` for backward compatibility
- `app/src/main/java/com/chris/m3usuite/ui/util/AppImageLoader.kt`
  - Updated to register new fetchers and keyer
- `app/src/test/java/com/chris/m3usuite/telegram/ingestion/TelegramHistoryScannerTest.kt`
  - Updated test to reflect new default
