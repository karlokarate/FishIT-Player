# Dependency Management Tools

This directory contains tools and scripts for managing dependencies and checking compatibility.

## Available Tools

### 1. Dependency Version Checker Script

**Location**: `scripts/check_dependencies.sh`

**Purpose**: Quickly check current dependency versions and identify potential compatibility issues.

**Usage**:
```bash
./scripts/check_dependencies.sh
```

**What it does**:
- Reports current Kotlin and KSP versions
- Checks Kotlin/KSP compatibility
- Lists all major dependency versions
- Identifies known compatibility issues
- Suggests compatibility checks to perform

### 2. Gradle Versions Plugin

**Purpose**: Automated dependency update checking using the Ben Manes Versions Plugin.

**Usage**:
```bash
# Check for dependency updates
./gradlew dependencyUpdates

# View report
open build/dependencyUpdates/report.html
```

**Configuration**: The plugin is configured in `build.gradle.kts` to:
- Reject unstable versions (alpha, beta, rc, etc.)
- Generate reports in JSON, HTML, and TXT formats
- Save reports to `build/dependencyUpdates/`

**Reports Generated**:
- `report.json` - Machine-readable format
- `report.html` - Human-readable browser view
- `report.txt` - Console-friendly format

### 3. Dependency Analysis

**Gradle Task**: `./gradlew buildEnvironment`

Shows the dependency tree and build configuration.

**Gradle Task**: `./gradlew dependencies`

Shows all dependencies in the project (can be scoped to specific configurations).

Example:
```bash
# Show all dependencies
./gradlew :app:dependencies

# Show only runtime dependencies
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

## Checking for Compatibility Issues

### Manual Compatibility Checks

1. **Kotlin Version Compatibility**
   - Check that KSP version matches Kotlin base version (e.g., Kotlin 2.2.0 → KSP 2.2.0-x.x.x)
   - Verify all Kotlin libraries (coroutines, serialization) are compatible

2. **Dependency Binary Compatibility**
   - Libraries compiled with newer Kotlin may not work with older Kotlin versions
   - If you see errors like "Module was compiled with an incompatible version of Kotlin", upgrade Kotlin

3. **AGP and Gradle Compatibility**
   - Check [AGP Release Notes](https://developer.android.com/build/releases/gradle-plugin) for supported Gradle versions
   - Current: AGP 8.6.1 requires Gradle 8.9+

4. **CompileSdk Compatibility**
   - Ensure AGP version officially supports your compileSdk level
   - Check `gradle.properties` for suppressions

### Automated Checks

Run the dependency checker script for a quick health check:
```bash
./scripts/check_dependencies.sh
```

## Common Compatibility Issues and Solutions

### Issue: Kotlin Binary Incompatibility

**Error**: "Module was compiled with an incompatible version of Kotlin. The binary version of its metadata is X.X.X, expected version is Y.Y.Y"

**Solution**: 
1. Check which Kotlin version your dependencies require
2. Update Kotlin in `settings.gradle.kts` to match or exceed the required version
3. Update KSP to match the new Kotlin version

### Issue: KSP Processor Not Found

**Error**: "No providers found in processor classpath"

**Solution**:
1. Verify KSP version matches Kotlin version (base should match)
2. Check that annotation processors (e.g., ObjectBox) support your Kotlin/KSP version
3. Try cleaning: `./gradlew clean`

### Issue: AGP Compatibility

**Error**: Related to Android Gradle Plugin version

**Solution**:
1. Check AGP compatibility matrix
2. Upgrade Gradle if needed: `./gradlew wrapper --gradle-version X.X`
3. Update AGP in `settings.gradle.kts`

## Updating Dependencies

### Safe Update Process

1. **Check for updates**:
   ```bash
   ./gradlew dependencyUpdates
   ```

2. **Review the report**:
   - Open `build/dependencyUpdates/report.html`
   - Identify updates you want to apply

3. **Update one category at a time**:
   - Start with build tools (AGP, Gradle, Kotlin)
   - Then update AndroidX libraries
   - Then third-party libraries

4. **Test after each update**:
   ```bash
   ./gradlew clean
   ./gradlew assembleDebug
   ./gradlew test
   ```

5. **Check for deprecations**:
   ```bash
   ./gradlew lint
   ```

### Update Priority Order

1. **Critical Security Updates** - Always apply immediately
2. **Kotlin & KSP** - Keep aligned, update together
3. **AGP & Gradle** - Update to latest stable
4. **AndroidX Libraries** - Can often be updated together
5. **Third-party Libraries** - Update as needed, test thoroughly

## Version Constraints

### Kotlin and KSP Alignment

**Rule**: KSP version must match Kotlin base version.

Example:
- Kotlin 2.2.0 → KSP 2.2.0-1.0.x ✅
- Kotlin 2.2.0 → KSP 2.0.21-1.0.x ❌

### AGP and Gradle Minimum Versions

| AGP Version | Minimum Gradle | Recommended Gradle |
|-------------|----------------|-------------------|
| 8.6.x       | 8.9            | 8.13              |
| 8.7.x       | 8.9            | 8.13+             |

### Compose Compiler and Kotlin

The Compose Compiler is now part of Kotlin (as of Kotlin 2.0). The `org.jetbrains.kotlin.plugin.compose` plugin handles compatibility automatically.

## Additional Resources

- [Android Developers - Dependency Management](https://developer.android.com/build/dependencies)
- [Kotlin Releases](https://kotlinlang.org/docs/releases.html)
- [KSP Releases](https://github.com/google/ksp/releases)
- [AGP Release Notes](https://developer.android.com/build/releases/gradle-plugin)
- [Gradle Versions Plugin](https://github.com/ben-manes/gradle-versions-plugin)

## Troubleshooting

### Can't download dependencies

**Error**: Network/connection errors

**Solution**:
1. Check internet connection
2. Verify repository URLs in `settings.gradle.kts`
3. Check proxy settings if behind corporate firewall
4. Try: `./gradlew --refresh-dependencies`

### Build fails after dependency update

**Solution**:
1. Clean build: `./gradlew clean`
2. Invalidate caches: `rm -rf .gradle build app/build`
3. Re-sync: `./gradlew --refresh-dependencies`
4. Check for breaking changes in updated library's changelog

### Dependency resolution conflicts

**Error**: Multiple versions of the same library

**Solution**:
1. Use `./gradlew :app:dependencies` to see conflict
2. Force a version if needed:
   ```kotlin
   configurations.all {
       resolutionStrategy {
           force("com.example:library:1.0.0")
       }
   }
   ```
3. Exclude transitive dependencies if necessary

## Maintenance

Run dependency checks regularly:
- **Weekly**: Check for security updates
- **Monthly**: Run `dependencyUpdates` and review
- **Quarterly**: Major dependency updates (Kotlin, AGP, AndroidX)

Keep tools up to date:
- Update the versions plugin: `com.github.ben-manes.versions`
- Update the dependency checker script as project evolves
