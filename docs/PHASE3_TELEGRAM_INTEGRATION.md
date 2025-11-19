# Phase 3: Telegram Integration - Implementation Guide

## Overview
This document describes the Phase 3 implementation for wiring FishTelegramRow into StartScreen and LibraryScreen with proper TDLib-based video playback.

## Architecture

### UI Layer
#### StartScreen
- **Location**: `ui/home/StartScreen.kt`
- **Changes**: Added Telegram content row after VOD row
- **Visibility**: Shown when `tgEnabled && telegramContent.isNotEmpty()`
- **Interaction**: Click handler launches playback via PlaybackLauncher

#### LibraryScreen
- **Location**: `ui/screens/LibraryScreen.kt`
- **Changes**: Added Telegram content in VOD tab
- **Loading**: LaunchedEffect observes `tgEnabled` and loads content via TelegramContentRepository
- **Display**: Shows up to 120 items using FishTelegramRow component

#### StartViewModel
- **Added**: TelegramContentRepository dependency
- **Added**: `telegramContent` StateFlow for UI
- **Added**: `observeTelegramContent()` method to load and observe Telegram items
- **Integration**: Uses existing tgEnabled flow from SettingsStore

### Playback Layer

#### TelegramDataSource
- **Location**: `player/datasource/TelegramDataSource.kt`
- **Purpose**: Media3 DataSource implementation for Telegram file streaming
- **URL Format**: `tg://file/<fileId>`
- **Features**:
  - Implements Media3 DataSource interface
  - Supports seek/resume via position parameter
  - Delegates to TelegramFileDownloader for TDLib operations
  - Proper error handling and resource cleanup

#### TelegramFileDownloader
- **Location**: `telegram/downloader/TelegramFileDownloader.kt`
- **Purpose**: Handles TDLib file downloads following official documentation
- **Key Features**:
  - **Chunk-based streaming**: Downloads only what's needed for playback
  - **File info caching**: Reduces repeated TDLib API calls
  - **Priority support**: Uses priority 16 for streaming (high)
  - **Offset support**: Enables seek operations
  - **Cache management**: Automatic cleanup via TDLib's optimizeStorage

#### Cache Management
- **Max Size**: 500MB (configurable)
- **TTL**: Files older than 7 days are removed
- **Immunity**: Files from last 24 hours are kept
- **API**: Uses TDLib's `optimizeStorage` method
- **Trigger**: Called via `cleanupCache()` method

### Data Layer

#### Resume Points
- **Location**: `data/repo/ResumeRepository.kt`
- **Methods**:
  - `setTelegramResume(mediaId, positionSecs)`
  - `getTelegramResume(mediaId)`
- **Storage**: Uses existing ObjectBox ObxResumeMark
- **Implementation**: Reuses VOD resume logic (Telegram IDs: 4e12-5e12 range)

#### Content Loading
- **Repository**: TelegramContentRepository
- **Method**: `getAllTelegramContent()`
- **Flow**: Returns Flow<List<MediaItem>> based on selected chats
- **Filtering**: Uses tgSelectedChatsCsv from SettingsStore

## TDLib Integration Best Practices

### Coroutines Usage
All TDLib calls are properly wrapped:
```kotlin
suspend fun operation() = withContext(Dispatchers.IO) {
    val result = session.client.someMethod(...)
    when (result) {
        is TdlResult.Success -> // handle success
        is TdlResult.Failure -> // handle error
    }
}
```

### Download API
Following official TDLib documentation:
```kotlin
val result = session.client.downloadFile(
    fileId = fileIdInt,
    priority = 16,        // High priority for streaming
    offset = position,    // Support seek operations
    limit = 0,           // 0 = download from offset to end
    synchronous = true   // Wait for completion
)
```

### Cache Optimization
Using TDLib's built-in optimization:
```kotlin
session.client.optimizeStorage(
    size = bytesToFree,
    ttl = 7 * 24 * 60 * 60,      // 7 days
    count = Int.MAX_VALUE,
    immunityDelay = 24 * 60 * 60, // Keep recent 24h
    fileTypes = emptyArray(),     // All types
    chatIds = longArrayOf(),      // All chats
    // ...
)
```

## Integration Status

### ✅ Completed
1. UI wiring in StartScreen and LibraryScreen
2. TelegramDataSource for Media3 integration
3. TelegramFileDownloader with proper TDLib usage
4. Resume point tracking
5. Cache management implementation
6. DelegatingDataSourceFactory recognition of tg:// scheme

### ⏳ Pending
1. **TelegramSession Injection**: Need to provide TelegramSession to DelegatingDataSourceFactory
   - Option A: Add to PlayerComponents/PlayerChooser
   - Option B: Global singleton (less preferred)
   - Option C: Dependency injection framework

2. **Testing**: 
   - Verify playback with internal player
   - Test with external players (VLC, MX Player)
   - Confirm seek/resume functionality
   - Validate cache cleanup

3. **Error Handling**:
   - User-friendly messages for TDLib errors
   - Fallback behavior when TelegramSession unavailable
   - Network error recovery

4. **Performance Optimization**:
   - Add periodic cache cleanup trigger
   - Optimize download chunk sizes
   - Consider pre-caching for better UX

## File Structure
```
app/src/main/java/com/chris/m3usuite/
├── ui/
│   ├── home/
│   │   ├── StartScreen.kt         (Modified)
│   │   └── StartViewModel.kt      (Modified)
│   └── screens/
│       └── LibraryScreen.kt       (Modified)
├── player/
│   └── datasource/
│       ├── DelegatingDataSourceFactory.kt  (Modified)
│       └── TelegramDataSource.kt          (New)
├── telegram/
│   └── downloader/
│       └── TelegramFileDownloader.kt      (New)
└── data/
    └── repo/
        └── ResumeRepository.kt    (Modified)
```

## Usage Example

### For Developers
When a user clicks on a Telegram video in StartScreen:

1. **UI Layer**: `onItemClick` handler creates PlayRequest with `tg://file/<fileId>` URL
2. **Playback Layer**: PlaybackLauncher forwards to PlayerChooser
3. **Player**: Internal player uses DelegatingDataSourceFactory
4. **DataSource**: TelegramDataSource is created for tg:// scheme
5. **Download**: TelegramFileDownloader handles TDLib operations
6. **Streaming**: Video chunks downloaded on-demand with high priority
7. **Resume**: Position saved via ResumeRepository on pause/stop
8. **Cleanup**: Old files removed automatically when cache > 500MB

### For Users
1. Enable Telegram in Settings
2. Select chats to sync
3. Browse Telegram content in Start screen or Library
4. Click to play - videos stream instantly
5. Progress is saved - resume from where you left off
6. Cache is managed automatically - no bloat

## References
- TDLib Documentation: https://core.telegram.org/tdlib/docs/
- tdl-coroutines: https://github.com/g000sha256/tdl-coroutines
- Media3 DataSource: https://developer.android.com/guide/topics/media/media3/datasources
- FishIT Player Architecture: See ARCHITECTURE_OVERVIEW.md
