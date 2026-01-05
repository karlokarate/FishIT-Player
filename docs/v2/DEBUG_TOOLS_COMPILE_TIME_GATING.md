# Debug Tools Compile-Time Gating Strategy

**Issue:** #564  
**Phase 3 Implementation:** PR #569  
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
        // Define all possible class output directories
        val classPathsToScan = listOf(
            // Kotlin compiler output (primary for Kotlin projects)
            layout.buildDirectory.dir("tmp/kotlin-classes/release").get().asFile,
            // Java compiler output (for Java sources)
            layout.buildDirectory.dir("intermediates/javac/release/classes").get().asFile,
            // Compiled library classes (merged from dependencies)
            layout.buildDirectory.dir("intermediates/compile_library_classes_jar/release").get().asFile,
            // Final DEX output (most reliable, but only available after dexing)
            layout.buildDirectory.dir("intermediates/dex/release").get().asFile,
        )

        // Filter to existing directories
        val existingPaths = classPathsToScan.filter { it.exists() }

        if (existingPaths.isEmpty()) {
            logger.warn("No release class output found in any of:")
            classPathsToScan.forEach { path ->
                logger.warn("  - ${path.absolutePath}")
            }
            logger.warn("Skipping verification. Run 'assembleRelease' first.")
            return@doLast
        }

        logger.lifecycle("🔍 Scanning ${existingPaths.size} output directories:")
        existingPaths.forEach { path ->
            logger.lifecycle("    - ${path.name}/")
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
        var scannedFilesCount = 0

        existingPaths.forEach { rootDir ->
            rootDir.walkTopDown().forEach { file ->
                // Scan .class files (Java/Kotlin bytecode)
                if (file.extension == "class") {
                    scannedFilesCount++
                    val content = file.readBytes().toString(Charsets.ISO_8859_1)
                    forbiddenStrings.forEach { forbidden ->
                        if (content.contains(forbidden)) {
                            violations.add(
                                "Found '$forbidden' in ${file.relativeTo(rootDir)} " +
                                "(from ${rootDir.name})"
                            )
                        }
                    }
                }
                
                // Scan .jar files (library dependencies)
                if (file.extension == "jar") {
                    scannedFilesCount++
                    val jarContent = java.util.zip.ZipFile(file).use { zip ->
                        zip.entries().asSequence()
                            .filter { it.name.endsWith(".class") }
                            .map { entry ->
                                zip.getInputStream(entry).readBytes().toString(Charsets.ISO_8859_1)
                            }
                            .joinToString("")
                    }
                    forbiddenStrings.forEach { forbidden ->
                        if (jarContent.contains(forbidden)) {
                            violations.add(
                                "Found '$forbidden' in JAR ${file.name} " +
                                "(from ${rootDir.name})"
                            )
                        }
                    }
                }
                
                // Scan .dex files (final Android bytecode)
                if (file.extension == "dex") {
                    scannedFilesCount++
                    val dexContent = file.readBytes().toString(Charsets.ISO_8859_1)
                    forbiddenStrings.forEach { forbidden ->
                        if (dexContent.contains(forbidden)) {
                            violations.add(
                                "Found '$forbidden' in DEX ${file.name} " +
                                "(from ${rootDir.name})"
                            )
                        }
                    }
                }
            }
        }

        logger.lifecycle("📊 Scanned $scannedFilesCount files")

        if (violations.isNotEmpty()) {
            throw GradleException(
                """
                ❌ DEBUG TOOL LEAKAGE DETECTED IN RELEASE BUILD!
                
                The following debug tool references were found:
                ${violations.joinToString("\n")}
                
                Scanned locations:
                ${existingPaths.joinToString("\n") { "  - ${it.name}/" }}
                
                This violates Issue #564 compile-time gating requirements.
                """.trimIndent()
            )
        } else {
            logger.lifecycle("✅ Release build is clean - no debug tool references found")
            logger.lifecycle("   ($scannedFilesCount files scanned)")
        }
    }
}

// Hook into release build
tasks.whenTaskAdded {
    if (name == "assembleRelease") {
        finalizedBy("verifyNoDebugToolsInRelease")
    }
}
```

**How It Works:**

1. **Multi-Path Scanning:** Checks multiple output directories to support both Java and Kotlin:
   - `tmp/kotlin-classes/release/` - Kotlin compiler output (primary for Kotlin projects)
   - `intermediates/javac/release/classes/` - Java compiler output
   - `intermediates/compile_library_classes_jar/release/` - Library JARs
   - `intermediates/dex/release/` - Final DEX files

2. **Flexible Scanning:** Scans existing directories only, adapts to project structure
3. **File Type Support:** Handles `.class`, `.jar`, and `.dex` files
4. **String Detection:** Searches for forbidden strings in bytecode
5. **Fail-Fast:** Throws `GradleException` if violations found
6. **CI Integration:** Task failure breaks CI/CD pipeline

**Manual Execution:**

```bash
# Build release and verify
./gradlew assembleRelease

# Expected output:
# 🔍 Scanning 2 output directories:
#     - kotlin-classes/
#     - dex/
# 📊 Scanned 1234 files
# ✅ Release build is clean - no debug tool references found
#    (1234 files scanned)

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
- **PR #569 (Phase 3):** UI gating and CI validation (this phase)

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
**Last Updated:** Phase 3 (PR #569)  
**Next Review:** After Phase 4 (if additional debug tools added)
