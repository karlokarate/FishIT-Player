# TV-UX Integration Guide

This document provides step-by-step instructions for integrating the new TV-optimized components into existing screens.

## Overview

The following components have been created to address TV-specific UX issues:

1. **TvKeyDebouncer** - ✅ Integrated into InternalPlayerScreen
2. **TvTextFieldFocusHelper** - Ready for integration
3. **TvEmptyState** - Ready for integration
4. **DiagnosticsLogger** - ✅ Integrated into InternalPlayerScreen
5. **PerformanceMonitor** - Ready for integration

## 1. Integrating TvTextFieldFocusHelper into Settings Screens

### Problem
In Settings screens, when a TextField is focused and the TV keyboard is open, users cannot navigate away using DPAD keys on the remote control.

### Solution
Apply `TvTextFieldFocusHelper` to all TextFields in Settings screens.

### Implementation

#### Option A: Using the Modifier Extension (Recommended)
```kotlin
import com.chris.m3usuite.ui.tv.rememberTvTextFieldFocusHelper
import com.chris.m3usuite.ui.tv.tvTextFieldFocusable

@Composable
fun SettingsScreen() {
    val focusHelper = rememberTvTextFieldFocusHelper()
    
    // Apply to each TextField
    OutlinedTextField(
        value = m3uUrl,
        onValueChange = { /* ... */ },
        label = { Text("M3U URL") },
        modifier = Modifier
            .fillMaxWidth()
            .tvTextFieldFocusable(focusHelper)  // Add this
    )
}
```

#### Option B: Using TvSettingsFocusConfig (For complex forms)
```kotlin
import com.chris.m3usuite.ui.tv.TvSettingsFocusConfig

@Composable
fun SettingsScreen() {
    Column {
        // Each field gets a modifier that enables DPAD navigation
        OutlinedTextField(
            value = field1,
            onValueChange = { /* ... */ },
            modifier = Modifier
                .fillMaxWidth()
                .then(TvSettingsFocusConfig.rememberTextFieldModifier(index = 0, totalFields = 5))
        )
        
        OutlinedTextField(
            value = field2,
            onValueChange = { /* ... */ },
            modifier = Modifier
                .fillMaxWidth()
                .then(TvSettingsFocusConfig.rememberTextFieldModifier(index = 1, totalFields = 5))
        )
        
        // ... more fields
    }
}
```

### Files to Modify
- `app/src/main/java/com/chris/m3usuite/ui/screens/SettingsScreen.kt`

### Testing
1. Open Settings screen on Android TV emulator or device
2. Navigate to a TextField using DPAD
3. Press OK/Enter to focus the field (keyboard opens)
4. Press DPAD DOWN or DPAD UP
5. Verify: Focus moves to the next/previous field instead of staying trapped

## 2. Integrating TvEmptyState into List Screens

### Problem
When screens show no content, users have no clear way to navigate back, making the screen feel broken.

### Solution
Replace empty list handling with `TvEmptyState` components.

### Implementation

#### For Library/Category Screens
```kotlin
import com.chris.m3usuite.ui.common.TvEmptyListState
import androidx.compose.runtime.collectAsState

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit
) {
    val items by viewModel.items.collectAsState()
    
    when {
        items.isEmpty() -> {
            TvEmptyListState(
                emptyMessage = "Keine Kanäle verfügbar",
                onBack = onBack,
                showRefreshAction = true,
                onRefresh = { viewModel.refresh() }
            )
        }
        else -> {
            // Show list
            LazyColumn {
                items(items) { item ->
                    // Item content
                }
            }
        }
    }
}
```

#### For Detail Screens with Loading
```kotlin
import com.chris.m3usuite.ui.common.TvLoadingState
import com.chris.m3usuite.ui.common.TvErrorState

@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    
    when (state) {
        is DetailState.Loading -> {
            TvLoadingState(
                message = "Lade Details...",
                onBack = onBack,
                showBackButton = true
            )
        }
        is DetailState.Error -> {
            TvErrorState(
                errorMessage = state.error,
                onBack = onBack,
                onRetry = { viewModel.retry() }
            )
        }
        is DetailState.Success -> {
            // Show content
        }
    }
}
```

### Files to Modify
Screens that show lists or can be empty:
- `app/src/main/java/com/chris/m3usuite/ui/screens/*Screen.kt`
- Especially: Library, Categories, Live channels, VOD, Series

### Testing
1. Navigate to a screen with no content
2. Verify: Empty state message is shown
3. Verify: Back button is focusable and highlighted
4. Press Back button
5. Verify: Navigation works correctly

## 3. Adding Diagnostics to Xtream Operations

### Implementation

#### In XtreamSeeder or XtreamClient
```kotlin
import com.chris.m3usuite.diagnostics.DiagnosticsLogger
import com.chris.m3usuite.diagnostics.PerformanceMonitor

suspend fun loadLiveChannels(): List<LiveChannel> {
    val timer = PerformanceMonitor.startTimer(
        category = "xtream",
        operation = "load_live_channels",
        screen = "home"
    )
    
    DiagnosticsLogger.Xtream.logLoadStart("live", "home")
    
    return try {
        timer.checkpoint("fetch_started")
        val response = api.getLiveCategories()
        
        timer.checkpoint("fetch_completed")
        val channels = response.map { parseChannel(it) }
        
        timer.checkpoint("parsing_completed")
        saveToDatabase(channels)
        
        timer.finish(mapOf("channel_count" to channels.size.toString()))
        
        DiagnosticsLogger.Xtream.logLoadComplete(
            type = "live",
            count = channels.size,
            durationMs = timer.totalMs,
            screen = "home"
        )
        
        channels
    } catch (e: Exception) {
        DiagnosticsLogger.Xtream.logLoadError(
            type = "live",
            error = e.message ?: "unknown",
            screen = "home"
        )
        throw e
    }
}
```

### Files to Modify
- `app/src/main/java/com/chris/m3usuite/core/xtream/XtreamSeeder.kt`
- `app/src/main/java/com/chris/m3usuite/core/xtream/XtreamClient.kt`
- `app/src/main/java/com/chris/m3usuite/work/XtreamDetailsWorker.kt`

## 4. Adding Diagnostics to Telegram Operations

### Implementation

#### In TelegramClient
```kotlin
import com.chris.m3usuite.diagnostics.DiagnosticsLogger

suspend fun processUpdate(update: Update) {
    DiagnosticsLogger.Telegram.logUpdateReceived(
        updateType = update::class.simpleName ?: "unknown"
    )
    
    when (update) {
        is UpdateNewMessage -> {
            val startTime = System.currentTimeMillis()
            processMessage(update.message)
            val duration = System.currentTimeMillis() - startTime
            
            DiagnosticsLogger.Telegram.logMediaResolve(
                messageId = update.message.id.toString(),
                durationMs = duration
            )
        }
        // ... other updates
    }
}
```

### Files to Modify
- `app/src/main/java/com/chris/m3usuite/telegram/TelegramClient.kt`
- `app/src/main/java/com/chris/m3usuite/telegram/TelegramMessageHandler.kt`

## 5. Adding Diagnostics to Compose Screens

### Implementation

#### Track Screen Load Time
```kotlin
import com.chris.m3usuite.diagnostics.DiagnosticsLogger
import androidx.compose.runtime.LaunchedEffect

@Composable
fun HomeScreen() {
    val startTime = remember { System.currentTimeMillis() }
    
    LaunchedEffect(Unit) {
        val loadTime = System.currentTimeMillis() - startTime
        DiagnosticsLogger.ComposeTV.logScreenLoad(
            screen = "home",
            durationMs = loadTime
        )
    }
    
    // Screen content
}
```

#### Track Focus Changes
```kotlin
import androidx.compose.ui.focus.onFocusChanged

@Composable
fun SettingsField(name: String) {
    var hasFocus by remember { mutableStateOf(false) }
    
    OutlinedTextField(
        value = value,
        onValueChange = { /* ... */ },
        modifier = Modifier
            .onFocusChanged { focusState ->
                if (focusState.isFocused != hasFocus) {
                    DiagnosticsLogger.ComposeTV.logFocusChange(
                        screen = "settings",
                        from = if (!focusState.isFocused) name else null,
                        to = if (focusState.isFocused) name else null
                    )
                    hasFocus = focusState.isFocused
                }
            }
    )
}
```

## Testing Strategy

### Manual Testing on TV
1. **Fire TV Device/Emulator**: Test scrubbing fix
   - Play VOD content
   - Rapidly press left/right on remote
   - Verify: No endless scrubbing
   
2. **Android TV Device/Emulator**: Test focus navigation
   - Open Settings
   - Navigate to TextField
   - Try to navigate away with DPAD
   - Verify: Focus moves correctly
   
3. **Empty States**: Test navigation
   - Navigate to empty screen
   - Verify: Back button is focusable
   - Press Back
   - Verify: Navigates correctly

### Unit Testing
Run existing tests:
```bash
./gradlew testDebugUnitTest
```

### Instrumented Testing (Requires Device/Emulator)
```bash
./gradlew connectedDebugAndroidTest
```

## Monitoring and Debugging

### View Diagnostic Logs
Logs are structured JSON for easy parsing:
```bash
adb logcat -s DiagnosticsLogger
```

### Export Diagnostic Events
Add a debug screen to export events:
```kotlin
val json = DiagnosticsLogger.exportEventsAsJson(limit = 100)
// Save to file or share
```

### Performance Analysis
Look for patterns in timing data:
- Operations > 1000ms get WARN level
- Track trends over time
- Identify bottlenecks

## Integration Checklist

- [ ] Apply TvTextFieldFocusHelper to SettingsScreen
- [ ] Apply TvTextFieldFocusHelper to XtreamSettingsViewModel screens
- [ ] Add TvEmptyState to Library screen
- [ ] Add TvEmptyState to Category screens
- [ ] Add TvEmptyState to Search results
- [ ] Add DiagnosticsLogger to XtreamSeeder
- [ ] Add DiagnosticsLogger to XtreamClient
- [ ] Add DiagnosticsLogger to TelegramClient
- [ ] Add PerformanceMonitor to XtreamDetailsWorker
- [ ] Add screen load tracking to main screens
- [ ] Test on Fire TV device
- [ ] Test on Android TV device
- [ ] Review diagnostic logs
- [ ] Update CHANGELOG.md

## Resources

- [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) - Complete developer documentation
- [Android TV Guidelines](https://developer.android.com/design/ui/tv)
- [Compose for TV](https://developer.android.com/jetpack/androidx/releases/tv)

## Support

For questions or issues with integration:
1. Review test files for usage examples
2. Check DEVELOPER_GUIDE.md for detailed API documentation
3. Run unit tests to validate behavior
4. Use DiagnosticsLogger to debug issues
