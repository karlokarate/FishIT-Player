# Telegram Full History + Lazy Thumbnails UI Integration Summary

## Overview

This document summarizes the changes made to integrate the new "full Telegram chat history + lazy thumbnails" implementation into the UI, align advanced Telegram-related settings with the new engine behavior, and fix the multi-DataStore error.

## Part 1: Multi-DataStore Error Fix ✅

### Problem
Multiple DataStore instances were being created for the same file (`settings.preferences_pb`), causing the error:
"There are multiple DataStores active for the same file"

### Solution
1. **Created `SettingsDataStoreProvider` singleton** (`prefs/SettingsDataStoreProvider.kt`)
   - Provides a single DataStore<Preferences> instance using double-checked locking
   - Thread-safe initialization
   - Uses `PreferenceDataStoreFactory.create()` with `preferencesDataStoreFile()`

2. **Updated `SettingsStore`** (`prefs/SettingsStore.kt`)
   - Removed `private val Context.dataStore by preferencesDataStore("settings")`
   - Uses `SettingsDataStoreProvider.getInstance(context)` instead
   - Replaced all 134+ references to `context.dataStore` with `dataStore` member variable

3. **Updated `CacheManager`** (`core/cache/CacheManager.kt`)
   - Removed duplicate `preferencesDataStore("settings")` declaration
   - Uses `SettingsDataStoreProvider.getInstance(context)` for settings access
   - `clearDataStore()` now uses `dataStore.edit { clear() }` instead of file deletion

4. **Added safe reset method**
   - `SettingsStore.resetAllSettings()` uses `dataStore.edit { clear() }`
   - No longer deletes files on disk, preventing multi-DataStore errors

## Part 2: Advanced Settings Alignment ✅

### Updated Clamping Ranges

**Telegram Engine Settings:**
- `Max Global Downloads`: 1-10 (was 1-20)
- `Max Video Downloads`: 1-5 (was 1-10)  
- `Max Thumb Downloads`: 1-8 (was 1-10)
- `File Ready Timeout`: 5-60 seconds (was 2-60)
- `Prefetch Batch Size`: 1-16 (was 1-50)
- `Max Parallel Thumbnail Downloads`: 1-8 (fixed internally at 4 in TelegramThumbFetcher)

**ExoPlayer Buffer Settings:**
- Added invariant enforcement: `BufferForPlayback <= MinBuffer <= MaxBuffer`
- `MinBuffer`: 1-300 seconds (was 5-300)
- `MaxBuffer`: 1-300 seconds (was 5-300)
- `BufferForPlayback`: 0.5-10 seconds (was 0.5-30)
- `BufferForPlaybackAfterRebuffer`: 0.5-10 seconds (was 0.5-30)

### Auto-Adjustment Logic
- When setting MinBuffer, if it exceeds MaxBuffer, MaxBuffer is adjusted upward
- When setting MaxBuffer, if it's less than MinBuffer, MinBuffer is adjusted downward
- When setting BufferForPlayback, if it exceeds MinBuffer, MinBuffer is adjusted upward
- When setting BufferForPlaybackAfterRebuffer, if it exceeds MaxBuffer, MaxBuffer is adjusted upward

### Preference Migration
- Added `SettingsStore.migrateAdvancedSettings()` method
- Automatically called on app startup in `App.onCreate()`
- Clamps existing out-of-range values to new safe limits
- Enforces buffer invariants on existing settings

### Deprecated Settings
- `Full Download for Thumbnails`: Marked as deprecated (should be phased out)
  - New architecture expects thumbnail-sized files only
  - Setting is kept for backward compatibility but noted for removal

## Part 3: Telegram UI Integration ✅

### Full Chat History Sync

**Added to `TelegramSettingsViewModel`:**
- `onSyncFullChatHistory(chatId: Long, chatTitle: String?)` method
- Uses `TelegramContentRepository.syncFullChatHistory()` 
- Streaming pagination with unlimited history (maxPages = Int.MAX_VALUE)
- Progress tracking via `isSyncingHistory` and `syncProgress` state fields

**State Updates:**
- Added `isSyncingHistory: Boolean` to track sync in progress
- Added `syncProgress: String?` to display sync status (e.g., "Synced 1234 items")

### Lazy Thumbnail Implementation

**Already Implemented:**
- `TelegramThumbKey` data class with remoteId, ThumbKind, sizeBucket
- `TelegramThumbFetcher` with Semaphore(4) for concurrency control
- `TelegramThumbKeyer` for stable cache keys
- `AppImageLoader` registered with Coil 3 components
- `TelegramContentRepository.resolveThumbFileId()` for remoteId → fileId mapping

**Usage Pattern:**
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
    imageLoader = AppImageLoader.get(context)
)
```

## Part 4: Testing & Validation

### Build Status
- Code compiles successfully
- ktlint has pre-existing warnings (not introduced by these changes)
- Changes are minimal and surgical

### What to Test Manually
1. **DataStore Error:** 
   - Settings persistence works correctly
   - "Reset Settings" operation completes without multi-DataStore error
   - App restart maintains settings

2. **Advanced Settings:**
   - Sliders respect new min/max ranges
   - Out-of-range values are clamped on app startup
   - ExoPlayer buffer invariants are maintained

3. **Telegram Sync:**
   - Full chat history sync can be triggered
   - Progress indicator shows during sync
   - Sync completes successfully for large chats

4. **Thumbnail Loading:**
   - Thumbnails load lazily as user scrolls
   - Cache keys are stable across app restarts
   - Concurrency is limited (no TDLib overload)

## Files Modified

### Created
- `app/src/main/java/com/chris/m3usuite/prefs/SettingsDataStoreProvider.kt`

### Modified
- `app/src/main/java/com/chris/m3usuite/prefs/SettingsStore.kt`
- `app/src/main/java/com/chris/m3usuite/core/cache/CacheManager.kt`
- `app/src/main/java/com/chris/m3usuite/telegram/ui/TelegramSettingsViewModel.kt`
- `app/src/main/java/com/chris/m3usuite/App.kt`

## References

- **Implementation Doc:** `TELEGRAM_FULL_HISTORY_LAZY_THUMBS_IMPLEMENTATION.md`
- **TDLib Agent Doc:** `.github/tdlibAgent.md`
- **Example Usage:** `telegram/ui/examples/TelegramLazyLoadingExample.kt` (if exists)

## Next Steps

1. **UI Testing:**
   - Test settings screens for correct min/max slider behavior
   - Verify sync progress indicator displays correctly
   - Test thumbnail loading in chat lists

2. **Integration Testing:**
   - Test with real Telegram chats with large histories
   - Verify OOM-safe behavior with thousands of messages
   - Test cache stability across app restarts

3. **Documentation:**
   - Update user-facing docs about advanced settings
   - Document sync behavior for large chats
   - Add troubleshooting guide for common issues

## Known Limitations

1. **Thumbnail Prefetch:** 
   - Setting "Max Parallel Thumbnail Downloads" is informational only
   - TelegramThumbFetcher uses fixed Semaphore(4) internally
   - Consider removing UI control or mapping it to prefetch behavior

2. **Full Download for Thumbnails:**
   - Setting is deprecated but still exposed
   - Should be hidden or removed in future update

3. **Sync Progress:**
   - Currently shows total items persisted
   - Could be enhanced with:
     - Pages processed count
     - Estimated time remaining
     - Cancelable operation support
