# Debug Tools Compile-Time Gating Strategy

**Issue:** #564  
**Phase 3 Implementation:** PR #568  
**Status:** ✅ Complete

## Overview

This document describes the complete strategy for compile-time isolation of debug tools (LeakCanary and Chucker) in FishIT-Player v2. The goal is to ensure debug tools are **completely removed** from release builds—not just disabled, but entirely absent from compiled classes.

## Architecture

### Three-Layer Isolation

The debug tools isolation uses a three-layer approach:

```
┌─────────────────────────────────────────────────────────────┐
│ Layer 1: BuildConfig Flags (Compile-Time Constants)        │
├─────────────────────────────────────────────────────────────┤
│ • INCLUDE_LEAKCANARY: Boolean                               │
│ • INCLUDE_CHUCKER: Boolean                                  │
│                                                             │
│ Set to true in debug builds, false in release              │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ Layer 2: SourceSet Separation (Module Isolation)           │
├─────────────────────────────────────────────────────────────┤
│ :core:debug-settings module → debugImplementation only     │
│                                                             │
│ Contains:                                                   │
│ • DebugToolsSettingsRepository                             │
│ • DebugFlagsHolder                                         │
│ • DebugToolsInitializer                                    │
│ • GatedChuckerInterceptor                                  │
│                                                             │
│ Release builds: Module not compiled or linked              │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ Layer 3: UI Gating (Runtime Visibility Control)            │
├─────────────────────────────────────────────────────────────┤
│ DebugScreen.kt:                                             │
│ • if (state.isChuckerAvailable) { /* UI */ }               │
│ • if (state.isLeakCanaryAvailable) { /* UI */ }            │
│                                                             │
│ Release builds: Debug tool UI completely hidden            │
└─────────────────────────────────────────────────────────────┘
```

## Implementation Details

### 1. BuildConfig Flags

**Location:** `app-v2/build.gradle.kts`

```kotlin
android {
    defaultConfig {
        // Default values for all builds
        buildConfigField("boolean", "INCLUDE_LEAKCANARY", "true")
        buildConfigField("boolean", "INCLUDE_CHUCKER", "true")
    }

    buildTypes {
        release {
            // Override: Debug tools MUST be disabled in release
            buildConfigField("boolean", "INCLUDE_LEAKCANARY", "false")
            buildConfigField("boolean", "INCLUDE_CHUCKER", "false")
        }
        debug {
            // Explicit: Debug tools enabled
            buildConfigField("boolean", "INCLUDE_LEAKCANARY", "true")
            buildConfigField("boolean", "INCLUDE_CHUCKER", "true")
        }
    }
}
```

**Usage in Code:**

```kotlin
// Phase 2: DebugToolsController abstraction
val controller = if (BuildConfig.INCLUDE_LEAKCANARY) {
    LeakCanaryControllerImpl()
} else {
    NoOpDebugToolsController()
}
```

**Benefits:**
- Compile-time dead code elimination (Kotlin/Proguard removes false branches)
- Zero runtime overhead in release builds
- Type-safe flag access

### 2. SourceSet Separation

**Module:** `:core:debug-settings`

**Dependencies (debugImplementation only):**

```kotlin
// app-v2/build.gradle.kts
dependencies {
    debugImplementation(project(":core:debug-settings"))
}

// feature/settings/build.gradle.kts
dependencies {
    debugImplementation(project(":core:debug-settings"))
}

// infra/transport-xtream/build.gradle.kts
dependencies {
    debugImplementation(project(":core:debug-settings"))
}
```

**Module Structure:**

```
core/debug-settings/
├── build.gradle.kts
├── src/
│   ├── main/           # ⚠️ All classes here (shared infrastructure)
│   │   └── java/
│   │       └── com/fishit/player/core/debugsettings/
│   │           ├── DebugToolsSettingsRepository.kt
│   │           ├── DataStoreDebugToolsSettingsRepository.kt
│   │           ├── DebugFlagsHolder.kt
│   │           ├── DebugToolsInitializer.kt
│   │           └── GatedChuckerInterceptor.kt
│   └── test/
│       └── java/...
```

**Why `src/main/`?**
- The module itself contains shared infrastructure needed by debug builds
- The module is **not included** in release builds via `debugImplementation`
- No need for `src/debug/` within the module—isolation happens at dependency level

**Benefits:**
- Complete module exclusion from release builds
- No stub implementations needed
- Clean separation of concerns
- Easy to add new debug-only modules

### 3. UI Gating

**Location:** `feature/settings/src/main/java/com/fishit/player/feature/settings/DebugScreen.kt`

**Pattern:**

```kotlin
// Chucker Section - Only show if available
if (state.isChuckerAvailable) {
    item {
        DebugSection(title = "HTTP Inspector", icon = Icons.Default.Cloud) {
            // ... Chucker UI components
        }
    }
}

// LeakCanary Section - Only show if available
if (state.isLeakCanaryAvailable) {
    item {
        LeakCanaryDiagnosticsSection(
            state = state,
            onOpenLeakCanary = { viewModel.openLeakCanaryUi() },
            // ... other callbacks
        )
    }
}
```

**State Properties:**

```kotlin
data class DebugState(
    val isLeakCanaryAvailable: Boolean = false,
    val isChuckerAvailable: Boolean = false,
    // ...
)
```

**ViewModel Logic:**

```kotlin
@HiltViewModel
class DebugViewModel @Inject constructor(
    private val debugToolsController: DebugToolsController,
    // ...
) : ViewModel() {
    private val _state = MutableStateFlow(
        DebugState(
            isLeakCanaryAvailable = debugToolsController.isLeakCanaryAvailable,
            isChuckerAvailable = debugToolsController.isChuckerAvailable,
            // ...
        )
    )
}
```

**Benefits:**
- Zero UI footprint in release builds
- Clean user experience (no disabled/grayed-out buttons)
- Consistent with BuildConfig flag state

## CI Validation

### Automated Verification Task

**Location:** `app-v2/build.gradle.kts`

**Task:** `verifyNoDebugToolsInRelease`

```kotlin
tasks.register("verifyNoDebugToolsInRelease") {
    group = "verification"
    description = "Verifies that no LeakCanary/Chucker references exist in release builds"

    doLast {
        val releaseClasses = layout.buildDirectory
            .dir("intermediates/javac/release/classes")
            .get()
            .asFile

        if (!releaseClasses.exists()) {
            logger.warn("Release classes not found, skipping verification")
            return@doLast
        }

        val forbiddenStrings = listOf(
            "LeakCanary",
            "leakcanary",
            "Chucker",
            "chucker",
            "DebugToolsSettingsRepository",
            "DebugFlagsHolder",
            "DebugToolsInitializer",
            "GatedChuckerInterceptor"
        )

        val violations = mutableListOf<String>()

        releaseClasses.walkTopDown().forEach { file ->
            if (file.extension == "class") {
                val content = file.readBytes().toString(Charsets.ISO_8859_1)
                forbiddenStrings.forEach { forbidden ->
                    if (content.contains(forbidden)) {
                        violations.add("Found '$forbidden' in ${file.relativeTo(releaseClasses)}")
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                """
                ❌ DEBUG TOOL LEAKAGE DETECTED IN RELEASE BUILD!
                
                The following debug tool references were found:
                ${violations.joinToString("\n")}
                
                This violates Issue #564 compile-time gating requirements.
                """.trimIndent()
            )
        } else {
            logger.lifecycle("✅ Release build is clean - no debug tool references found")
        }
    }
}

// Hook into release build
tasks.named("assembleRelease") {
    finalizedBy("verifyNoDebugToolsInRelease")
}
```

**How It Works:**

1. **Automatic Execution:** Runs after `assembleRelease` completes
2. **Class Scanning:** Walks through all compiled `.class` files in release build
3. **String Detection:** Searches for forbidden strings in bytecode
4. **Fail-Fast:** Throws `GradleException` if violations found
5. **CI Integration:** Task failure breaks CI/CD pipeline

**Manual Execution:**

```bash
# Build release and verify
./gradlew assembleRelease

# Or verify existing build
./gradlew verifyNoDebugToolsInRelease
```

## Adding New Build Variants

### Scenario: Custom Build with No Debug Tools

**Goal:** Create a `staging` build type without debug tools.

**Steps:**

1. **Add Build Type:**

```kotlin
// app-v2/build.gradle.kts
android {
    buildTypes {
        create("staging") {
            initWith(getByName("release"))
            isDebuggable = true
            applicationIdSuffix = ".staging"
            
            // Disable debug tools
            buildConfigField("boolean", "INCLUDE_LEAKCANARY", "false")
            buildConfigField("boolean", "INCLUDE_CHUCKER", "false")
        }
    }
}
```

2. **Dependencies (Optional):**

If you want debug tools in staging:

```kotlin
dependencies {
    stagingImplementation(project(":core:debug-settings"))
}
```

3. **Build:**

```bash
./gradlew assembleStagingRelease
```

### Scenario: QA Build with Debug Tools

**Goal:** QA build with debug tools enabled.

```kotlin
android {
    buildTypes {
        create("qa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".qa"
            
            // Enable debug tools
            buildConfigField("boolean", "INCLUDE_LEAKCANARY", "true")
            buildConfigField("boolean", "INCLUDE_CHUCKER", "true")
        }
    }
}

dependencies {
    qaImplementation(project(":core:debug-settings"))
}
```

## Testing Strategy

### Debug Build Testing

**Verify debug tools are present and functional:**

```bash
# Build debug APK
./gradlew assembleDebug

# Install on device
adb install -r app-v2/build/outputs/apk/debug/app-v2-debug.apk

# Manual verification:
# 1. Navigate to Settings → Debug Screen
# 2. Verify "HTTP Inspector" section is visible
# 3. Verify "Memory / LeakCanary" section is visible
# 4. Tap "Open HTTP Inspector" → Chucker UI should open
# 5. Tap "Open" in LeakCanary section → LeakCanary UI should open
```

### Release Build Testing

**Verify debug tools are completely removed:**

```bash
# Build release APK
./gradlew assembleRelease

# ✅ Automatic verification runs (verifyNoDebugToolsInRelease)

# Manual verification (optional):
adb install -r app-v2/build/outputs/apk/release/app-v2-release.apk

# Check Debug Screen:
# 1. Navigate to Settings → Debug Screen
# 2. Verify "HTTP Inspector" section is NOT visible
# 3. Verify "Memory / LeakCanary" section is NOT visible
```

### APK Size Comparison

**Expected size reduction in release builds:**

```bash
# Compare APK sizes
ls -lh app-v2/build/outputs/apk/debug/app-v2-debug.apk
ls -lh app-v2/build/outputs/apk/release/app-v2-release.apk

# Expected difference: ~2-3 MB smaller (LeakCanary + Chucker removed)
```

## Troubleshooting

### Issue: Release Build Contains Debug Tool References

**Symptoms:**
- `verifyNoDebugToolsInRelease` task fails
- Error message shows forbidden strings in release classes

**Possible Causes:**

1. **Direct Import in Production Code:**
   ```kotlin
   // ❌ BAD: Direct import in main sourceset
   import com.squareup.leakcanary.LeakCanary
   
   // ✅ GOOD: Use DebugToolsController abstraction
   @Inject lateinit var debugToolsController: DebugToolsController
   ```

2. **Module Not Properly Gated:**
   ```kotlin
   // ❌ BAD: Module visible in all builds
   implementation(project(":core:debug-settings"))
   
   // ✅ GOOD: Module only in debug builds
   debugImplementation(project(":core:debug-settings"))
   ```

3. **BuildConfig Flag Ignored:**
   ```kotlin
   // ❌ BAD: Always using debug implementation
   val controller = LeakCanaryControllerImpl()
   
   // ✅ GOOD: Conditional instantiation
   val controller = if (BuildConfig.INCLUDE_LEAKCANARY) {
       LeakCanaryControllerImpl()
   } else {
       NoOpDebugToolsController()
   }
   ```

**Resolution:**

1. Check the violation output for specific class names
2. Find the source file importing debug tools
3. Refactor to use `DebugToolsController` abstraction
4. Rebuild and verify: `./gradlew clean assembleRelease`

### Issue: UI Shows Debug Tools in Release

**Symptoms:**
- Debug Screen shows Chucker/LeakCanary sections in release build
- Buttons are visible but disabled

**Cause:**
- UI gating not applied or incorrectly implemented

**Resolution:**

Ensure sections are wrapped with availability checks:

```kotlin
// ✅ CORRECT: Entire section gated
if (state.isChuckerAvailable) {
    item {
        DebugSection(title = "HTTP Inspector", ...) {
            // ... UI components
        }
    }
}

// ❌ INCORRECT: Section always visible, content conditional
item {
    DebugSection(title = "HTTP Inspector", ...) {
        if (state.isChuckerAvailable) {
            // ... UI components
        } else {
            Text("Not available")
        }
    }
}
```

### Issue: Task Runs on Fresh Checkout

**Symptoms:**
- `verifyNoDebugToolsInRelease` runs but no classes exist
- Warning: "Release classes not found"

**Behavior:**
- Task skips verification with warning (expected)
- No build failure

**Resolution:**
- Run `./gradlew assembleRelease` first
- Task will then verify compiled classes

## Related Documentation

- **Issue #564:** Parent issue for compile-time gating strategy
- **PR #566 (Phase 1):** BuildConfig flags implementation
- **PR #567 (Phase 2):** DebugToolsController abstraction layer
- **PR #568 (Phase 3):** UI gating and CI validation (this phase)

## Success Criteria

- ✅ Release builds contain **zero** references to LeakCanary or Chucker
- ✅ Release builds show **zero** debug tool UI elements
- ✅ CI automatically fails on debug tool leakage
- ✅ Developers can create custom build variants with/without tools
- ✅ Debug builds remain fully functional with all tools

## Future Improvements

Potential enhancements for future phases:

1. **APK Analyzer Integration:** Add automated APK size comparison
2. **Method Count Tracking:** Monitor method count impact of debug tools
3. **Dex Scanning:** Validate at DEX level in addition to .class files
4. **Proguard Rules:** Additional verification of R8/Proguard mapping
5. **Symbol Validation:** Check native library symbols (if applicable)

---

**Maintained by:** Architecture Team  
**Last Updated:** Phase 3 (PR #568)  
**Next Review:** After Phase 4 (if additional debug tools added)
