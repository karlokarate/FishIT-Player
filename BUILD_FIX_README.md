# Build Error Fix - Kotlin Binary Incompatibility

## Problem

The build was failing with errors like:
```
Module was compiled with an incompatible version of Kotlin. 
The binary version of its metadata is 2.2.0, expected version is 2.0.0.
```

Affected dependencies:
- Coil 3.3.0
- OkHttp 5.2.1
- Okio 3.16.1
- kotlinx-serialization 1.9.0
- kotlinx-coroutines 1.10.2
- TDLib 5.0.0

## Root Cause

These dependencies were compiled with Kotlin 2.2.0, but the project was using Kotlin 2.0.21. Kotlin enforces binary compatibility, so libraries compiled with a newer Kotlin version cannot be used with an older Kotlin compiler.

## Solution

Upgraded Kotlin to 2.1.0 for better forward compatibility:
- **Kotlin**: 2.0.21 → 2.1.0
- **KSP**: 2.0.21-1.0.28 → 2.1.0-1.0.29

### Why 2.1.0 instead of 2.2.0?

Kotlin 2.2.0 would be ideal, but KSP 2.2.0-1.0.x is not yet available in Maven repositories. Kotlin 2.1.0 provides good forward compatibility with 2.2.0-compiled libraries.

## Additional Tools Added

1. **Ben Manes Versions Plugin** - Check for dependency updates
   ```bash
   ./gradlew dependencyUpdates
   ```

2. **Dependency Checker Script** - Quick health check
   ```bash
   ./scripts/check_dependencies.sh
   ```

3. **Comprehensive Documentation** - `tools/README.md`

## Next Steps

When KSP 2.2.0-1.0.x becomes available:
1. Update to Kotlin 2.2.0 in `settings.gradle.kts`
2. Update to matching KSP version
3. This will provide perfect binary compatibility

## Testing

Build the release APK:
```bash
./gradlew clean assembleRelease -PabiFilters=arm64-v8a
```

## Files Changed

- `settings.gradle.kts` - Kotlin and KSP versions updated
- `build.gradle.kts` - Added Gradle Versions Plugin
- `scripts/check_dependencies.sh` - New dependency checker script
- `tools/README.md` - Dependency management documentation
- `DEPENDENCY_UPGRADE_NOTES.md` - Updated with fix details

## References

- [Kotlin 2.1.0 Release Notes](https://kotlinlang.org/docs/whatsnew21.html)
- [KSP Releases](https://github.com/google/ksp/releases)
- [Gradle Versions Plugin](https://github.com/ben-manes/gradle-versions-plugin)
