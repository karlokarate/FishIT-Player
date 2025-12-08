# Removed Telegram Streaming Configuration Sliders

## Summary
Removed two configurable settings from Telegram Advanced Settings that were artificially limiting the streaming behavior:

1. **Initial Prefix Size** (formerly 64-2048 KB configurable)
2. **Seek Margin / Range Window** (formerly 256-8192 KB configurable)

## Rationale
These settings were originally introduced to manage windowed streaming but have been superseded by TDLib-optimized streaming using `StreamingConfigRefactor` constants. The artificial boundaries were:
- Not necessary for modern TDLib streaming architecture
- Added complexity without meaningful benefit
- Potentially caused playback issues if misconfigured

## New Architecture
The system now uses fixed, optimized constants from `StreamingConfigRefactor`:
- **MIN_PREFIX_FOR_VALIDATION_BYTES**: 64 KB (hardcoded, sufficient for MP4 header validation)
- **MAX_PREFIX_SCAN_BYTES**: 2 MB (safety limit for moov atom detection)
- **MIN_READ_AHEAD_BYTES**: 1 MB (read-ahead for seek operations)
- **ENSURE_READY_TIMEOUT_MS**: 30 seconds (timeout from settings, still configurable)

## Files Modified
1. `app/src/main/java/com/chris/m3usuite/ui/screens/SettingsScreen.kt`
   - Removed two slider UI components

2. `app/src/main/java/com/chris/m3usuite/ui/screens/TelegramAdvancedSettingsViewModel.kt`
   - Removed `initialPrefixKb` and `seekMarginKb` state properties
   - Removed setter methods `setInitialPrefixKb()` and `setSeekMarginKb()`
   - Updated value array indexing in combine() flow

3. `app/src/main/java/com/chris/m3usuite/prefs/SettingsStore.kt`
   - Removed DataStore keys: `TG_INITIAL_PREFIX_BYTES` and `TG_SEEK_MARGIN_BYTES`
   - Removed Flow properties and setter methods
   - Removed validation logic in setters

4. `app/src/main/java/com/chris/m3usuite/data/repo/SettingsRepository.kt`
   - Removed Flow properties and pass-through setters

5. `app/src/main/java/com/chris/m3usuite/telegram/domain/TelegramStreamingSettings.kt`
   - Removed `initialMinPrefixBytes` and `seekMarginBytes` properties

6. `app/src/main/java/com/chris/m3usuite/telegram/domain/TelegramStreamingSettingsProvider.kt`
   - Removed mapping from DataStore flows
   - Removed default values from initialValue
   - Updated value array indexing in combine() flow

7. `app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt`
   - Updated deprecation messages for legacy constants

## Migration Path
Existing installations with saved values in DataStore will simply ignore those keys. The default behavior uses the optimized `StreamingConfigRefactor` constants which provide better performance.

## Verification
- ✅ Build successful (assembleDebug)
- ✅ ktlint format check passes
- ✅ No test failures related to removed settings
- ✅ TelegramFileDataSource uses StreamingConfigRefactor constants
- ✅ No remaining references to removed settings in codebase
