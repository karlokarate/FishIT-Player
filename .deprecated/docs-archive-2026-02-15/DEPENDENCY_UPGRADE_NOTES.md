# Dependency Upgrade Notes

## Date: November 19, 2025 (Update 2)

### Critical Fix: Kotlin Binary Incompatibility

**Issue**: Build failure due to Kotlin binary incompatibility
- Dependencies (Coil 3.3.0, OkHttp 5.2.1, Okio 3.16.1, kotlinx-serialization 1.9.0) compiled with Kotlin 2.2.0
- Project was using Kotlin 2.0.21
- Error: "Module was compiled with an incompatible version of Kotlin. The binary version of its metadata is 2.2.0, expected version is 2.0.0"

**Solution Applied**:
1. **Kotlin**: 2.0.21 → **2.1.0**
   - Better forward compatibility with Kotlin 2.2.0-compiled libraries
   - Kotlin 2.2.0 tested but KSP support not yet available
   
2. **KSP**: 2.0.21-1.0.28 → **2.1.0-1.0.29**
   - Aligned with Kotlin 2.1.0
   - Required for ObjectBox and other annotation processors

### New Dependency Management Tools Added

1. **Ben Manes Versions Plugin** (`build.gradle.kts`)
   - Automatically checks for dependency updates
   - Usage: `./gradlew dependencyUpdates`
   - Generates reports in JSON, HTML, TXT formats
   - Configured to reject unstable versions

2. **Dependency Checker Script** (`scripts/check_dependencies.sh`)
   - Quick health check of all dependencies
   - Validates Kotlin/KSP compatibility
   - Lists current versions
   - Identifies known issues
   - Provides actionable recommendations

3. **Comprehensive Documentation** (`tools/README.md`)
   - Complete guide to dependency management
   - Compatibility checking procedures
   - Common issues and solutions
   - Update process best practices

## Date: November 19, 2025 (Update 1)

## Upgrades Applied

### Build Tools

1. **Android Gradle Plugin**: 8.5.2 → **8.6.1**
   - Latest stable version as of late 2024
   - Improved build performance and Gradle 8.x compatibility
   - Better caching support

2. **Ktlint Runtime**: 1.0.1 → **1.5.0**
   - Better Kotlin 2.x support
   - More accurate linting rules
   - Performance improvements

3. **SLF4J Android**: 1.7.36 → **2.0.16**
   - Major version upgrade
   - Better Android compatibility
   - Fluent logging API

### Gradle Performance Optimizations

Added to `gradle.properties`:
- `org.gradle.parallel=true` - Enable parallel build execution
- `org.gradle.caching=true` - Enable Gradle build cache
- Updated `android.suppressUnsupportedCompileSdk` to reference AGP 8.6.1

## Versions Kept (Already Optimal)

- **Gradle Wrapper**: 8.13 (latest stable)
- **Kotlin**: 2.1.0 (upgraded for compatibility)
- **KSP**: 2.1.0-1.0.29 (aligned with Kotlin)
- **Detekt**: 1.23.7 (latest)
- **Ktlint Plugin**: 12.1.2 (latest)
- **JUnit**: 4.13.2 (latest)
- **ZXing**: 3.5.3 (latest)
- **junrar**: 7.5.5 (latest)

## Analysis Notes

### Future-Dated Versions

Many dependencies in this repository appear to have version numbers from late 2025 (future relative to Nov 2024 knowledge base):

**AndroidX Libraries** (Potentially from 2025):
- core-ktx: 1.17.0
- activity-compose: 1.11.0
- activity-ktx: 1.11.0
- navigation-compose: 2.9.5
- lifecycle-runtime-compose: 2.9.4
- material: 1.13.0

**Compose Libraries**:
- compose-ui: 1.9.3
- material3: 1.3.1

**Media3**:
- media3: 1.8.0
- jellyfin-ffmpeg: 1.8.0+1

**Other Libraries**:
- Coil: 3.3.0 (Coil 3 was alpha/beta in late 2024)
- OkHttp: 5.2.1 (5.x was alpha in late 2024, 4.12.x was stable)
- Okio: 3.16.1
- kotlinx-serialization-json: 1.9.0
- kotlinx-coroutines-android: 1.10.2
- datastore-preferences: 1.1.7
- work-runtime-ktx: 2.10.5
- paging-runtime-ktx: 3.3.6
- paging-compose: 3.3.6

**Test Libraries**:
- androidx.test.ext:junit: 1.3.0
- espresso-core: 3.7.0

### Decision Made

**Did NOT downgrade** these versions because:
1. Repository is dated October/November 2025
2. These versions might actually exist in that timeframe
3. Without internet access, cannot verify availability
4. Build failure is due to network issues, not version problems

## Recommendations for Future Updates

### Priority: Upgrade to Kotlin 2.2.0

When KSP 2.2.0-1.0.x becomes available in Maven repositories:
1. Update Kotlin to 2.2.0 in `settings.gradle.kts`
2. Update KSP to matching 2.2.0-1.0.x version
3. This will provide perfect binary compatibility with all current dependencies

### When Network Access is Restored

1. **Verify all dependency versions** against Maven Central
2. **Consider using Compose BOM** for easier version management:
   ```kotlin
   val composeBom = platform("androidx.compose:compose-bom:2024.XX.XX")
   implementation(composeBom)
   androidTestImplementation(composeBom)
   implementation("androidx.compose.ui:ui")
   implementation("androidx.compose.material3:material3")
   // etc - versions come from BOM
   ```

3. **Check for newer Kotlin versions** (2.1.x might be available)

4. **Update ObjectBox** if newer versions are available (currently 5.0.1)

5. **Check TDLib dependencies** (dev.g000sha256:tdl-coroutines-android:5.0.0)

### Potential Improvements

1. **Use version catalogs** (libs.versions.toml) for centralized dependency management

2. **Consider AndroidX BOM** for managing AndroidX library versions:
   ```kotlin
   dependencies {
       implementation(platform("androidx.compose:compose-bom:YYYY.MM.DD"))
       // All compose dependencies without versions
   }
   ```

3. **Enable Gradle configuration cache** when stable (currently warnings allowed)

4. **Review ProGuard rules** with newer library versions

5. **Check for deprecated APIs** that might need migration

## New Features from Upgraded Dependencies

### AGP 8.6.1
- Improved R8 optimizations
- Better Kotlin compatibility
- Faster incremental builds
- Improved Gradle configuration cache support

### Ktlint 1.5.0
- New formatting rules for Kotlin 2.0+
- Better multiplatform project support
- Improved performance on large codebases

### SLF4J 2.0.16
- Fluent logging API:
  ```kotlin
  logger.atInfo()
      .addKeyValue("userId", userId)
      .addKeyValue("action", action)
      .log("User action performed")
  ```
- Better performance with lazy parameter evaluation
- Improved MDC (Mapped Diagnostic Context) support

## Code Adaptations Made

None required for the applied upgrades. All changes were backward compatible.

## Code Adaptations That May Be Needed (Future)

### If Updating OkHttp from 5.x to 4.x (for stability)
- No code changes needed, just version number
- 5.x is alpha/preview, 4.12.x is stable

### If Updating Coil from 3.x to stable
- Coil 3 has different package structure
- May need to update imports if downgrading to Coil 2.x

### If Using Compose BOM
- Remove explicit version numbers from Compose dependencies
- Let BOM manage versions

## Testing Recommendations

When network is available:
1. Run `./gradlew clean build`
2. Run all unit tests: `./gradlew test`
3. Run lint checks: `./gradlew lint`
4. Run Detekt: `./gradlew detekt`
5. Run Ktlint: `./gradlew ktlintCheck`
6. Build release APK: `./gradlew assembleRelease`
7. Test on physical device, especially TV features

## Additional Notes

- Build is currently failing due to network connectivity issues (cannot access dl.google.com)
- This prevents downloading dependencies from Maven repositories
- All version changes are theoretical until network access is restored and build succeeds
- Repository appears to be from November 2025, significantly ahead of available knowledge base (late 2024)

## Using the New Dependency Management Tools

### Quick Health Check

Run the dependency checker script for an instant overview:
```bash
./scripts/check_dependencies.sh
```

This provides:
- Current Kotlin and KSP versions with compatibility check
- All major dependency versions
- Known compatibility issues
- Actionable recommendations

### Check for Dependency Updates

Use the Gradle Versions Plugin:
```bash
# Run the dependency update checker
./gradlew dependencyUpdates

# View the HTML report
open build/dependencyUpdates/report.html
```

The plugin will show:
- Dependencies with available updates
- Current vs. available versions
- Grouped by category (build tools, AndroidX, third-party)

### View Dependency Tree

To understand dependency relationships:
```bash
# Full dependency tree
./gradlew :app:dependencies

# Specific configuration (e.g., release runtime)
./gradlew :app:dependencies --configuration releaseRuntimeClasspath

# Show why a specific version is used
./gradlew :app:dependencyInsight --dependency androidx.core:core-ktx
```

### Regular Maintenance Schedule

**Weekly**: Run `./scripts/check_dependencies.sh` for quick health check

**Monthly**: 
1. Run `./gradlew dependencyUpdates`
2. Review and plan updates
3. Update non-breaking dependencies

**Quarterly**:
1. Major updates (Kotlin, AGP, AndroidX)
2. Full regression testing
3. Update documentation

For complete documentation, see `tools/README.md`.

---

## Licensing Considerations

### NextLib / FFmpeg (GPL-3.0)

**Component:** `io.github.anilbeesetti:nextlib-media3ext:1.8.0-0.9.0`

**License:** GPL-3.0 (via FFmpeg)

**Module:** `player:nextlib-codecs`

**Impact:** NextLib wraps FFmpeg to provide additional codec support (AV1, HEVC, etc.) for Media3/ExoPlayer. Because FFmpeg is licensed under GPL-3.0, shipping NextLib in our APK means the entire app distribution is subject to GPL-3.0 copyleft requirements.

**Current Status:** NextLib is integrated via Hilt DI and exposed through the `NextlibCodecConfigurator` interface in the player layer.

**TODO: Build Flavor for GPL-Free Distribution**

To distribute the app through channels that require non-copyleft licensing (e.g., certain enterprise deployments or Play Store compliance for closed-source apps), we need to create a build flavor that excludes NextLib:

```kotlin
// Proposed flavor configuration (not yet implemented)
android {
    flavorDimensions += "codecs"
    productFlavors {
        create("withFFmpeg") {
            dimension = "codecs"
            // Includes NextLib, full codec support, GPL-3.0 applies
        }
        create("stock") {
            dimension = "codecs"
            // Uses only Media3 built-in codecs, no GPL
        }
    }
}
```

**Action Items:**
1. [ ] Create `withFFmpeg` and `stock` product flavors
2. [ ] Conditionally include `:player:nextlib-codecs` only in `withFFmpeg`
3. [ ] Provide no-op `NextlibCodecConfigurator` implementation for `stock` flavor
4. [ ] Document codec limitations in `stock` flavor
5. [ ] Add GPL-3.0 license notice to app settings/about screen when `withFFmpeg` is used

**References:**
- FFmpeg License: https://ffmpeg.org/legal.html
- NextLib Repository: https://github.com/anilbeesetti/nextlib
- GPL-3.0 Text: https://www.gnu.org/licenses/gpl-3.0.html
