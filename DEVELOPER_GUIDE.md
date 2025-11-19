# FishIT Player - Developer Guide

## Development Environment Setup

### Prerequisites
- JDK 21 (recommended for AGP 8.5+)
- Android SDK with API 24-36
- Android NDK r27c (for TDLib native builds)
- Gradle 8.13+

### Initial Setup
The project includes a GitHub Actions workflow for environment validation:

```bash
# Verify toolchain locally
./gradlew --version
java -version

# Warm up Gradle dependencies
./gradlew help
```

## Quality & Build Tools

### Gradle Tasks Overview

#### Linting & Code Quality
```bash
# Android Lint (includes TV-specific checks)
./gradlew lintDebug
./gradlew lintRelease

# Kotlin code style (ktlint)
./gradlew ktlintCheck        # Check code style
./gradlew ktlintFormat       # Auto-format code

# Kotlin code smells (Detekt)
./gradlew detekt             # Run static analysis
```

#### Building
```bash
# Debug builds
./gradlew assembleDebug
./gradlew :app:assembleDebug

# Release builds (requires keystore configuration)
./gradlew assembleRelease

# Build all variants
./gradlew assemble
```

#### Testing
```bash
# Unit tests
./gradlew testDebugUnitTest
./gradlew test

# Instrumented tests (requires device/emulator)
./gradlew connectedDebugAndroidTest
./gradlew :app:connectedAndroidTest

# Compile test sources only
./gradlew :app:compileDebugAndroidTestSources
```

### Code Quality Configuration

#### ktlint
Code style enforcement for Kotlin. Configuration in `build.gradle.kts`.

**Rules:**
- Android code style conventions
- Automatic formatting available via `ktlintFormat`
- Excludes generated code and build artifacts

#### Detekt
Static code analysis for Kotlin. Configuration in `detekt-config.yml`.

**Focus Areas:**
- Complexity metrics (ComplexMethod, LongParameterList)
- Potential bugs (NullableToStringCall, UnsafeCast)
- Performance issues (ArrayPrimitive, UnnecessaryTemporaryInstantiation)
- Code smells (UnusedPrivateMember, DataClassShouldBeImmutable)

**TV-Specific Considerations:**
- Coroutines usage patterns
- Focus handling complexity
- Performance-critical paths

#### Android Lint
Built-in Android linting with TV-specific checks enabled.

**Key Checks:**
- TV UI components (Leanback, Compose TV)
- Focus handling
- Remote control compatibility
- Resource optimization

## Diagnostics & Performance Monitoring

### DiagnosticsLogger

Structured event logging for debugging and performance analysis.

#### Basic Usage
```kotlin
import com.chris.m3usuite.diagnostics.DiagnosticsLogger

// Log a simple event
DiagnosticsLogger.logEvent(
    category = "xtream",
    event = "load_live_list",
    screen = "home",
    metadata = mapOf("count" to "150")
)

// Log an error
DiagnosticsLogger.logError(
    category = "media3",
    event = "playback_failed",
    throwable = exception,
    screen = "player"
)
```

#### Category-Specific Helpers
```kotlin
// XTREAM operations
DiagnosticsLogger.Xtream.logLoadStart("live", screen = "home")
DiagnosticsLogger.Xtream.logLoadComplete("live", count = 150, durationMs = 234)
DiagnosticsLogger.Xtream.logLoadError("live", error = "timeout")

// Telegram/TDLib operations
DiagnosticsLogger.Telegram.logUpdateReceived("newMessage")
DiagnosticsLogger.Telegram.logMediaResolve(messageId = "123", durationMs = 456)

// Media3/ExoPlayer events
DiagnosticsLogger.Media3.logPlaybackStart(screen = "player", mediaType = "vod")
DiagnosticsLogger.Media3.logSeekOperation(screen = "player", fromMs = 1000, toMs = 2000)
DiagnosticsLogger.Media3.logBufferEvent(screen = "player", bufferMs = 500, isBuffering = true)

// Compose/TV UI events
DiagnosticsLogger.ComposeTV.logScreenLoad(screen = "settings", durationMs = 123)
DiagnosticsLogger.ComposeTV.logFocusChange(screen = "settings", from = "field1", to = "field2")
DiagnosticsLogger.ComposeTV.logKeyEvent(screen = "player", keyCode = "DPAD_LEFT", action = "DOWN")
```

#### Configuration
```kotlin
// Enable/disable logging
DiagnosticsLogger.isEnabled = true

// Set log level
DiagnosticsLogger.logLevel = DiagnosticsLogger.LogLevel.DEBUG

// Enable console output
DiagnosticsLogger.enableConsoleOutput = true
```

#### Data Export
```kotlin
// Get recent events
val events = DiagnosticsLogger.getRecentEvents(limit = 100)

// Export as JSON
val json = DiagnosticsLogger.exportEventsAsJson(limit = 100)

// Clear history
DiagnosticsLogger.clearEvents()
```

**Security Note:** DiagnosticsLogger automatically sanitizes metadata to prevent logging of sensitive data (tokens, passwords, auth headers).

### PerformanceMonitor

Timing and performance measurement utilities.

#### Basic Measurement
```kotlin
import com.chris.m3usuite.diagnostics.PerformanceMonitor

// Measure a block
val (result, duration) = PerformanceMonitor.measure("load_data") {
    // expensive operation
    loadDataFromDatabase()
}
println("Operation took ${duration}ms")
```

#### Measure and Log
```kotlin
// Automatically log if exceeds threshold
val result = PerformanceMonitor.measureAndLog(
    category = "xtream",
    operation = "load_live_list",
    screen = "home",
    threshold = 100L, // Only log if > 100ms
    additionalMetadata = mapOf("filter" to "favorites")
) {
    loadLiveChannels()
}
```

#### Checkpoint Timer
```kotlin
// Track multiple stages of an operation
val timer = PerformanceMonitor.startTimer(
    category = "xtream",
    operation = "full_sync",
    screen = "settings"
)

// Fetch data
fetchCategories()
timer.checkpoint("categories_loaded")

// Process data
processChannels()
timer.checkpoint("channels_processed")

// Save to database
saveToDatabase()
timer.checkpoint("saved_to_db")

// Finish and log all checkpoints
timer.finish(mapOf("total_items" to "500"))
```

## TV-Specific Components

### TvKeyDebouncer

Prevents endless scrubbing issues on Fire TV/Android TV remotes.

#### Problem
Fire TV remotes can generate rapid, unstoppable seek/scrubbing events causing the player to enter an endless scrubbing state.

#### Solution
```kotlin
import com.chris.m3usuite.player.TvKeyDebouncer

val debouncer = TvKeyDebouncer(
    scope = coroutineScope,
    debounceMs = 300L // Adjust based on testing
)

view.setOnKeyListener { _, keyCode, event ->
    debouncer.handleKeyEventRateLimited(keyCode, event) { code, isDown ->
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                handleSeekBackward()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                handleSeekForward()
                true
            }
            else -> false
        }
    }
}
```

### TvTextFieldFocusHelper

Prevents focus traps in Settings screens when TV keyboard is open.

#### Problem
When a TextField is focused and the TV keyboard is displayed, DPAD navigation gets trapped - users cannot navigate away using the remote control.

#### Solution
```kotlin
import com.chris.m3usuite.ui.tv.rememberTvTextFieldFocusHelper
import com.chris.m3usuite.ui.tv.tvTextFieldFocusable

@Composable
fun SettingsScreen() {
    val focusHelper = rememberTvTextFieldFocusHelper()
    
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("M3U URL") },
        modifier = Modifier
            .fillMaxWidth()
            .tvTextFieldFocusable(focusHelper)
    )
}
```

### TvEmptyState

Provides proper navigation for screens with no content.

#### Problem
Screens with no content can become "dead ends" where users cannot navigate back.

#### Solution
```kotlin
import com.chris.m3usuite.ui.common.TvEmptyState
import com.chris.m3usuite.ui.common.TvEmptyListState
import com.chris.m3usuite.ui.common.TvLoadingState
import com.chris.m3usuite.ui.common.TvErrorState

// Empty list
TvEmptyListState(
    emptyMessage = "Keine Kanäle verfügbar",
    onBack = { navController.popBackStack() },
    showRefreshAction = true,
    onRefresh = { viewModel.refresh() }
)

// Loading with cancel option
TvLoadingState(
    message = "Lade Kanäle...",
    onBack = { navController.popBackStack() }
)

// Error with retry
TvErrorState(
    errorMessage = "Netzwerkfehler beim Laden",
    onBack = { navController.popBackStack() },
    onRetry = { viewModel.retry() }
)
```

## Testing

### Unit Tests
Location: `app/src/test/`

Focus areas:
- Business logic
- ViewModels
- Repository implementations
- Data transformations

### Instrumented Tests
Location: `app/src/androidTest/`

Focus areas:
- UI components
- TV focus navigation
- Player controls
- Database operations

### TV-Specific Test Scenarios

#### Focus Navigation Tests
```kotlin
@Test
fun testSettingsScreenFocusNavigation() {
    // Verify TextField focus can be escaped with DPAD
    // Verify focus order is logical
    // Verify back button clears focus properly
}
```

#### Player Seek Tests
```kotlin
@Test
fun testPlayerSeekDebouncing() {
    // Verify rapid DPAD presses don't cause endless scrubbing
    // Verify single seek operations work correctly
    // Verify trickplay state resets properly
}
```

#### Empty State Tests
```kotlin
@Test
fun testEmptyStateNavigation() {
    // Verify back button is focusable
    // Verify BackHandler is registered
    // Verify focus requests on empty screens
}
```

## CI/CD Integration

### GitHub Actions Workflows

#### copilot-setup-steps.yml
Validates development environment setup:
- JDK installation
- Android SDK configuration
- NDK r27c setup
- Gradle wrapper validation
- Dependency caching

#### android-quality.yml (Recommended)
Run quality checks on PRs:
```yaml
jobs:
  quality:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          java-version: '21'
      - name: Run ktlint
        run: ./gradlew ktlintCheck
      - name: Run Detekt
        run: ./gradlew detekt
      - name: Run Android Lint
        run: ./gradlew lintDebug
      - name: Run Unit Tests
        run: ./gradlew testDebugUnitTest
```

## Dependencies

### Core Libraries
- Kotlin 2.0.21
- Compose 1.9.3
- Material3 1.3.1
- Media3 (ExoPlayer) 1.8.0
- AndroidX Navigation 2.9.5
- AndroidX Lifecycle 2.9.4

### Data & Storage
- ObjectBox 5.0.1
- DataStore Preferences 1.1.7
- Room (legacy, being phased out)

### Network & Media
- OkHttp 5.2.1
- Coil 3.3.0 (image loading)
- Jellyfin Media3 FFmpeg Decoder 1.8.0+1

### TV-Specific
- AndroidX TV Material 1.0.1
- Custom focus handling utilities

### Quality Tools
- Detekt 1.23.7
- ktlint 1.0.1 (via Gradle plugin 12.1.2)

## Architecture

### Key Modules
- **core/** - Core business logic, Xtream API, utilities
- **data/** - Repositories, data sources, ObjectBox entities
- **ui/** - Compose UI, screens, components
- **player/** - Media3 player, TV controls
- **telegram/** - TDLib integration
- **diagnostics/** - Logging and performance monitoring (new)

### Data Flow
1. **Xtream**: XtreamClient → XtreamSeeder → ObjectBox → UI
2. **Telegram**: TDLib → TelegramClient → ObjectBox → UI
3. **Playback**: UI → PlaybackLauncher → Media3 Player → UI

## Best Practices

### Performance
- Use `PerformanceMonitor` for operations > 100ms
- Profile with Android Studio Profiler
- Monitor memory with LeakCanary (optional)
- Use Baseline Profiles for critical paths (optional)

### TV UX
- Always provide focusable back navigation
- Use `TvKeyDebouncer` for seek operations
- Test with Fire TV and Android TV devices
- Handle empty states with `TvEmptyState`
- Use `TvTextFieldFocusHelper` for forms

### Code Quality
- Run ktlint before committing: `./gradlew ktlintFormat`
- Address Detekt warnings in new code
- Write tests for bug fixes and new features
- Use DiagnosticsLogger for debugging

### Security
- Never log tokens, passwords, or sensitive data
- Use ProGuard/R8 for release builds
- Keep dependencies updated
- Follow Android security best practices

## Troubleshooting

### Build Issues
```bash
# Clean and rebuild
./gradlew clean assembleDebug

# Refresh dependencies
./gradlew --refresh-dependencies

# Check for dependency conflicts
./gradlew :app:dependencies
```

### TV Remote Issues
- Use `DiagnosticsLogger.ComposeTV.logKeyEvent()` to debug
- Check key event handling with `TvKeyDebouncer`
- Test on real devices, not just emulators

### Focus Issues
- Use Focus Debugger: `com.chris.m3usuite.ui.debug.FocusDebug`
- Log focus changes with `DiagnosticsLogger.ComposeTV.logFocusChange()`
- Verify focus requesters are properly initialized

## Optional Integrations

### Firebase Performance Monitoring
Add to `DiagnosticsLogger.processEvent()`:
```kotlin
val trace = Firebase.performance.newTrace(event.category + "_" + event.event)
trace.start()
// ... operation ...
trace.stop()
```

### Sentry Performance
Add to `build.gradle.kts`:
```kotlin
dependencies {
    implementation("io.sentry:sentry-android:...")
}
```

Configure in `DiagnosticsLogger`:
```kotlin
Sentry.captureMessage(jsonString)
```

### LeakCanary
Add to debug builds:
```kotlin
debugImplementation("com.squareup.leakcanary:leakcanary-android:...")
```

## Contributing

1. Create a feature branch from `master`
2. Make focused, well-tested changes
3. Run quality checks: `./gradlew ktlintCheck detekt lintDebug test`
4. Write clear commit messages
5. Create a PR with description of changes
6. Address review feedback

## Resources

- [Android TV Design Guidelines](https://developer.android.com/design/ui/tv)
- [Compose for TV](https://developer.android.com/jetpack/androidx/releases/tv)
- [Media3 Documentation](https://developer.android.com/media/media3)
- [ObjectBox Documentation](https://docs.objectbox.io/)
- [Detekt Rules](https://detekt.dev/docs/rules/complexity)
