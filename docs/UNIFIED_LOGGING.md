# Unified Logging Facade

## Overview

The `:infra:logging` module provides a unified logging facade for the entire FishIT-Player application. This facade hides the internal logging backend (currently Timber) and provides a stable API that all modules use for logging.

**For v2 architecture-specific requirements, see [v2-docs/LOGGING_CONTRACT_V2.md](../v2-docs/LOGGING_CONTRACT_V2.md), which is the authoritative contract for all v2 modules.**

## Design Principles

1. **Single Source of Truth**: All logging must go through `UnifiedLog` - direct `android.util.Log` or Timber usage is forbidden outside the `:infra:logging` module
2. **Backend Independence**: The facade API is stable and independent of the backend implementation
3. **Future-Proof**: The backend can be swapped (e.g., migrating to Kermit for Kotlin Multiplatform) without affecting call sites
4. **Production-Ready**: Built-in support for crash reporting integration (Crashlytics/Sentry)

## Architecture

```
┌─────────────────────────────────────────┐
│         Application Modules             │
│  (pipelines, player, features, etc.)    │
└──────────────┬──────────────────────────┘
               │ uses
               ▼
┌─────────────────────────────────────────┐
│          UnifiedLog Facade              │
│      (Stable Public API)                │
└──────────────┬──────────────────────────┘
               │ delegates to
               ▼
┌─────────────────────────────────────────┐
│          Timber Backend                 │
│   (DebugTree / ProductionReportingTree) │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│    Android Log / Crash Reporting        │
└─────────────────────────────────────────┘
```

## Current Backend: Timber 5.0.1

Timber is used as the internal backend because:
- Mature, stable library with wide adoption
- Excellent API for structured logging
- Easy integration with crash reporting services
- Lightweight with minimal overhead

## Usage

### Initialization

Initialize the logging system in `Application.onCreate()`:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize logging FIRST before any other code
        UnifiedLogInitializer.init(isDebug = BuildConfig.DEBUG)
        
        // Rest of initialization...
    }
}
```

### Logging Messages

```kotlin
import com.fishit.player.infra.logging.UnifiedLog

class MyClass {
    companion object {
        private const val TAG = "MyClass"
    }
    
    fun doWork() {
        // Debug logging
        UnifiedLog.d(TAG, "Starting work")
        
        // Info logging
        UnifiedLog.i(TAG, "Work in progress")
        
        // Warning with exception
        try {
            riskyOperation()
        } catch (e: Exception) {
            UnifiedLog.w(TAG, "Operation failed, retrying", e)
        }
        
        // Error logging
        UnifiedLog.e(TAG, "Critical failure", exception)
    }
}
```

### Log Levels

The facade supports five log levels (in order of severity):

1. **VERBOSE** - Detailed diagnostic information (rarely needed)
2. **DEBUG** - Diagnostic information useful during development
3. **INFO** - General informational messages about app flow
4. **WARN** - Recoverable errors or unexpected situations
5. **ERROR** - Serious errors that require attention

### Filtering by Level

You can control log verbosity at runtime:

```kotlin
// Only log WARN and ERROR in production scenarios
UnifiedLog.setMinLevel(UnifiedLog.Level.WARN)

// Enable verbose logging for debugging
UnifiedLog.setMinLevel(UnifiedLog.Level.VERBOSE)
```

## Static Analysis Enforcement

### Detekt Rule

The project has Detekt rules configured to enforce the logging contract:

```yaml
ForbiddenImport:
  active: true
  imports:
    - value: 'android.util.Log'
      reason: 'Use UnifiedLog from :infra:logging instead. Direct android.util.Log usage is forbidden outside the logging module.'
    - value: 'timber.log.Timber'
      reason: 'Use UnifiedLog from :infra:logging instead. Direct Timber usage is forbidden outside the logging module.'
  excludes:
    - '**/infra/logging/**'
```

**Status**: ✅ **Enabled** - This rule is now active for all v2 modules and will fail the build if any module (except `:infra:logging`) imports `android.util.Log` or `Timber`.

### Running Checks

```bash
# Run detekt to check for violations
./gradlew detekt

# Run on a specific module
./gradlew :pipeline:telegram:detekt
```

## Future Enhancements

### Crashlytics/Sentry Integration

The `ProductionReportingTree` includes TODO markers for integrating with crash reporting services:

```kotlin
// In ProductionReportingTree.log()
if (priority == Log.ERROR) {
    FirebaseCrashlytics.getInstance().apply {
        log(message)
        t?.let { recordException(it) }
    }
}
```

### Kermit Migration Path

For Kotlin Multiplatform support, we can migrate to [Kermit](https://github.com/touchlab/Kermit):

1. Keep `UnifiedLog` API signatures unchanged
2. Replace internal Timber calls with Kermit `Logger` instance
3. All call sites remain untouched
4. Update initialization logic in `UnifiedLogInitializer`

The facade pattern ensures this migration has zero impact on application code.

## Testing

The module includes comprehensive unit tests:

```bash
./gradlew :infra:logging:testDebugUnitTest
```

Tests cover:
- Minimum level filtering
- Timber integration
- Throwable handling
- Backward compatibility
- Initialization behavior

## Migration from Direct android.util.Log

If you have existing code using `android.util.Log`, migrate as follows:

### Before:
```kotlin
import android.util.Log

Log.d("TAG", "message")
Log.e("TAG", "error", exception)
```

### After:
```kotlin
import com.fishit.player.infra.logging.UnifiedLog

UnifiedLog.d("TAG", "message")
UnifiedLog.e("TAG", "error", exception)
```

## FAQ

### Q: Can I use Timber directly in my module?
**A:** No. Only the `:infra:logging` module should import Timber. All other modules must use `UnifiedLog`. This is enforced by Detekt.

### Q: Why not use android.util.Log directly?
**A:** Direct android.util.Log usage:
- Cannot be easily redirected to crash reporting services
- Lacks structured logging capabilities
- Makes it difficult to control log verbosity centrally
- Cannot be easily mocked or tested

### Q: What happens if I forget to initialize UnifiedLog?
**A:** Timber will not be configured, and logs may not appear in logcat or crash reports. Always initialize in `Application.onCreate()`.

### Q: Can I change the backend from Timber to something else?
**A:** Yes! That's the whole point of the facade. Simply update the internal implementation in `UnifiedLog` and `UnifiedLogInitializer` without touching any call sites.

### Q: What about v2 modules?
**A:** V2 modules MUST follow the contract defined in [v2-docs/LOGGING_CONTRACT_V2.md](../v2-docs/LOGGING_CONTRACT_V2.md). All v2 modules use `UnifiedLog`, and static enforcement is enabled via Detekt.

## References

- [V2 Logging Contract](../v2-docs/LOGGING_CONTRACT_V2.md) - Authoritative contract for v2 modules
- [Timber Documentation](https://github.com/JakeWharton/timber)
- [Android Logging Best Practices](https://developer.android.com/studio/debug/am-logcat)
- [Firebase Crashlytics](https://firebase.google.com/docs/crashlytics)
