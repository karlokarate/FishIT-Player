# Release Workflow Enhancements

## Overview

The `release-build.yml` workflow has been significantly enhanced with:

1. **Speed Through Coaching** - Multi-layer caching and build metrics
2. **7 Optional Quality Tools** - Selectable via workflow_dispatch inputs
3. **Fixed ABI Configuration** - Always builds both ARM ABIs
4. **Enhanced Reporting** - Speed coaching and quality summaries

## What Changed

### 1. Workflow Inputs

#### Added Quality Tool Toggles
All default to `false`, allowing selective enablement:
- `run_ktlint` - KTLint style checker
- `run_kover` - Kover code coverage analysis
- `run_semgrep` - Semgrep SAST security scanning
- `run_gradle_doctor` - Gradle build health check
- `run_android_lint` - Android Lint analysis
- `run_leakcanary` - LeakCanary memory leak detection (stub)
- `run_r8_analysis` - R8 code shrinker analysis

#### Removed ABI Inputs
- Removed: `enable_arm_v7a` and `enable_arm64_v8a` inputs
- Now: **Always builds both** `armeabi-v7a` AND `arm64-v8a`

### 2. Speed Improvements ("Speed Through Coaching")

#### Multi-Layer Caching
Three cache layers for maximum speed improvement:

1. **Gradle Wrapper Cache**
   ```yaml
   path: ~/.gradle/wrapper, ~/.gradle/caches
   key: ${{ runner.os }}-gradle-wrapper-${{ hashFiles('**/gradle-wrapper.properties') }}
   ```

2. **Gradle Dependencies Cache**
   ```yaml
   path: ~/.gradle/caches, ~/.m2/repository
   key: ${{ runner.os }}-gradle-deps-${{ hashFiles('**/build.gradle*', ...) }}
   ```

3. **Android Build Cache**
   ```yaml
   path: **/build/intermediates, **/build/generated
   key: ${{ runner.os }}-android-build-${{ github.sha }}
   ```

#### Build Speed Metrics
- Records workflow start time
- Measures build configuration and compilation time
- Outputs to `.github/metrics/build_speed.json`
- Uploaded as `build-speed-report` artifact

Example metrics JSON:
```json
{
  "workflow_run": "123",
  "total_time_seconds": 420,
  "build_time_seconds": 300,
  "timestamp": "2025-12-03T19:00:00Z",
  "commit": "abc123...",
  "runner": "Linux"
}
```

#### Speed Coaching Summary
Automatically appended to `$GITHUB_STEP_SUMMARY`:
- Total CI time (with breakdown)
- Build time
- Caching strategy notes
- Performance tips

### 3. Quality Tools Implementation

All tools run conditionally based on workflow inputs and use `continue-on-error: true` to prevent blocking the build.

#### Tool 1: KTLint
```bash
./gradlew ktlintCheck --no-daemon --stacktrace
```
- Reports style violations
- Output: `reports/ktlint/ktlint-output.txt`

#### Tool 2: Kover (Code Coverage)
```bash
./gradlew koverXmlReport --no-daemon --stacktrace
```
- Generates XML coverage report
- Output: `build/reports/kover/report.xml`
- Copied to: `reports/kover/`

#### Tool 3: Semgrep (SAST)
```bash
semgrep --config auto --json --output reports/semgrep.json .
```
- Installs semgrep via pip if needed
- Reports findings by severity (ERROR, WARNING, INFO)
- Output: `reports/semgrep.json`

#### Tool 4: Gradle Doctor
```bash
./gradlew help --scan --no-daemon
```
- Basic health check (plugin installation optional)
- Reports deprecation warnings
- Output: `reports/gradle-doctor.txt`

#### Tool 5: Android Lint
```bash
./gradlew lintVitalRelease --no-daemon --stacktrace
```
- Runs release-appropriate lint checks
- Summarizes errors, warnings, and info issues
- Output: `app/build/reports/lint-results-*.xml/html`

#### Tool 6: LeakCanary
```bash
# Stub implementation - requires emulator setup
```
- Currently a placeholder with TODO marker
- Future: Run instrumentation tests with emulator
- Would detect memory leaks in debug builds

#### Tool 7: R8 Analysis
- Analyzes R8 code shrinking after release build
- Collects ProGuard mapping files
- Reports:
  - `app/build/outputs/mapping/release/mapping.txt`
  - `app/build/outputs/mapping/release/usage.txt`
- Output: `reports/r8/`

### 4. Quality Reporting

#### quality-report.txt
A single aggregated text file containing:
- Build metadata (run number, commit, timestamp)
- Summary from each enabled quality tool
- Success/failure status with counts

Example:
```
# Quality Report - Build 123
Generated: 2025-12-03 19:00:00 UTC
Commit: abc123...

## KTLint Check
✅ KTLint: PASSED - No style violations found

## Kover Coverage Report
✅ Kover: Coverage report generated (Coverage: 75%)

## Semgrep SAST Analysis
🔍 Semgrep: Found 0 critical, 2 warnings, 5 info
```

#### Quality Artifacts Upload
Single artifact named `quality-artifacts` containing:
- `quality-report.txt`
- `reports/ktlint/**`
- `reports/kover/**`
- `reports/semgrep.json`
- `reports/gradle-doctor.txt`
- `app/build/reports/lint-results*`
- `reports/leakcanary/**`
- `reports/r8/**`

Only uploaded if at least one quality tool is enabled.

#### Quality Summary in GitHub UI
Appended to `$GITHUB_STEP_SUMMARY`:
- Lists which tools were enabled
- Shows quick totals (warnings, errors, coverage)
- Includes full quality-report.txt content

### 5. Build Configuration Changes

#### Removed Matrix Strategy
**Before:**
```yaml
strategy:
  matrix:
    abi: [arm64-v8a, armeabi-v7a]
```

**After:**
Single job that builds both ABIs sequentially:
```bash
# Build for arm64-v8a
./gradlew :app:assembleRelease -PabiFilters=arm64-v8a ...

# Build for armeabi-v7a
./gradlew :app:assembleRelease -PabiFilters=armeabi-v7a ...
```

#### Updated Artifact Naming
- **Before:** `app-release-arm64-v8a` and `app-release-armeabi-v7a` (separate)
- **After:** `app-release-apks` (single artifact with both APKs)

#### Updated Release Asset Preparation
The `create-release` job now:
1. Downloads single `app-release-apks` artifact
2. Extracts APKs by pattern matching filename
3. Renames based on detected ABI

### 6. What Was NOT Changed

✅ **Signing configuration** - Unchanged  
✅ **Keystore handling** - Unchanged  
✅ **Release creation logic** - Only artifact naming updated  
✅ **APK verification** - Unchanged  
✅ **Checksum calculation** - Unchanged  

## Usage

### Running with Quality Tools

1. Go to **Actions** tab in GitHub
2. Select **Release Build – APK** workflow
3. Click **Run workflow**
4. Fill in required inputs:
   - Version name: `v1.2.3`
   - Version code: `123`
   - Create release: `true/false`
   - Prerelease: `true/false`
5. **Select quality tools** to enable:
   - ✅ Enable only the tools you need
   - ⚠️ More tools = longer CI time
6. Click **Run workflow**

### Recommended Configurations

#### Quick Release (Default)
All quality tools OFF - fastest build
```
run_ktlint: false
run_kover: false
run_semgrep: false
run_gradle_doctor: false
run_android_lint: false
run_leakcanary: false
run_r8_analysis: false
```

#### Pre-Release Quality Check
Enable lightweight tools:
```
run_ktlint: true
run_android_lint: true
run_r8_analysis: true
```

#### Full Quality Audit
Enable all tools (slowest):
```
run_ktlint: true
run_kover: true
run_semgrep: true
run_gradle_doctor: true
run_android_lint: true
run_r8_analysis: true
# run_leakcanary: false (requires emulator)
```

## Performance Expectations

### First Run (Cold Cache)
- Build time: ~15-20 minutes
- Caches are populated

### Second Run (Warm Cache)
- Build time: ~8-12 minutes (**30-50% faster**)
- Most dependencies cached

### With Quality Tools Enabled
Each tool adds approximately:
- KTLint: +1-2 min
- Kover: +2-3 min
- Semgrep: +3-5 min
- Android Lint: +2-3 min
- Gradle Doctor: +1 min
- R8 Analysis: ~0 min (uses existing build)
- LeakCanary: N/A (not implemented)

## Artifacts Generated

### Always Generated
1. **app-release-apks** - Both ARM APKs + checksums (30 days)
2. **proguard-mapping** - ProGuard mapping file (90 days)
3. **build-speed-report** - Build metrics JSON (90 days)

### Conditionally Generated
4. **quality-artifacts** - All quality tool reports (30 days)
   - Only if at least one quality tool is enabled

## Future Enhancements

### LeakCanary Implementation
To fully implement LeakCanary testing:
1. Add emulator setup step
2. Run `./gradlew connectedDebugAndroidTest`
3. Pull reports from device: `/sdcard/Download/leakcanary-*.zip`
4. Parse and summarize leak reports

### Gradle Doctor Plugin
For better Gradle health checks:
1. Add plugin to `build.gradle.kts`:
   ```kotlin
   plugins {
       id("com.osacky.doctor") version "0.10.0"
   }
   ```
2. Run `./gradlew doctor` for detailed analysis

### Cache Optimization
- Monitor cache hit rates
- Adjust cache keys based on actual performance
- Consider separate caches for different workflows

## Troubleshooting

### Workflow Fails on Quality Tool
- Quality tools use `continue-on-error: true`
- Build should continue even if tool fails
- Check `quality-report.txt` for details

### Cache Not Working
- First run always populates cache
- Check cache keys match across runs
- Verify cache size limits not exceeded

### APKs Not Found in Release
- Check artifact upload succeeded
- Verify ABI pattern matching in prepare step
- Ensure both builds completed successfully

## Migration Notes

### For Existing Workflows
1. Remove any custom ABI selection logic
2. Update artifact download to use new names
3. Add quality tool selections to workflow dispatch UI

### For CI/CD Integration
- Artifact names changed: Update download scripts
- New artifacts available: Integrate metrics/quality reports
- Speed metrics can be tracked over time

## References

- Original workflow: `.github/workflows/release-build.yml` (before changes)
- Build metrics: `.github/metrics/build_speed.json`
- Quality reports: `quality-report.txt` in artifacts
- GitHub Actions: [Caching dependencies](https://docs.github.com/en/actions/using-workflows/caching-dependencies-to-speed-up-workflows)
