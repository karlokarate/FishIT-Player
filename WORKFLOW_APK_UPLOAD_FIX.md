# GitHub Actions APK Upload Path Fix

## Issue
GitHub Actions workflow run #21340960318 failed with error at the "Upload APK artifacts" step.

**Error Message:**
```
if-no-files-found: error
```

**Affected Workflow:** 
- `release-build.yml` - Build APK – V2 App (Selectable ABIs: arm64-v8a | armeabi-v7a | both)

## Root Cause Analysis

### ABI Split Directory Structure
When workflows use `-PuseSplits=true` (line 708 in release-build.yml), the Android Gradle Plugin creates APK files in ABI-specific subdirectories:

```
app-v2/build/outputs/apk/
└── release/
    ├── arm64-v8a/
    │   └── app-v2-release-arm64-v8a.apk
    └── armeabi-v7a/
        └── app-v2-release-armeabi-v7a.apk
```

### Previous Upload Pattern (Broken)
```yaml
path: |
  app-v2/build/outputs/apk/${{ github.event.inputs.build_variant }}/*.apk
```

This pattern expands to: `app-v2/build/outputs/apk/release/*.apk`

**Problem:** The single `*` wildcard only matches files in the immediate directory, not in subdirectories. Since APKs are in `arm64-v8a/` and `armeabi-v7a/` subdirectories, they weren't found, causing the upload step to fail.

## Solution

### Updated Upload Pattern (Fixed)
```yaml
path: |
  app-v2/build/outputs/apk/${{ github.event.inputs.build_variant }}/**/*.apk
```

This pattern expands to: `app-v2/build/outputs/apk/release/**/*.apk`

**Fix:** The `**` glob pattern is a recursive wildcard that matches files in any subdirectory level:
- ✅ Matches `app-v2/build/outputs/apk/release/arm64-v8a/*.apk`
- ✅ Matches `app-v2/build/outputs/apk/release/armeabi-v7a/*.apk`
- ✅ Also matches `app-v2/build/outputs/apk/release/*.apk` (backward compatible with non-split builds)

## Applied Changes

### 1. release-build.yml (Main Release Workflow)
**Location:** Line 1088-1095

**Change:**
```diff
- path: |
-   app-v2/build/outputs/apk/${{ github.event.inputs.build_variant }}/*.apk
+ path: |
+   app-v2/build/outputs/apk/${{ github.event.inputs.build_variant }}/**/*.apk
```

**Added Documentation:**
```yaml
# 2026 FIX: Upload APK artifacts with ABI split support
# When useSplits=true, APKs are in ABI-specific subdirectories:
#   - app-v2/build/outputs/apk/release/arm64-v8a/*.apk
#   - app-v2/build/outputs/apk/release/armeabi-v7a/*.apk
# The /** pattern ensures we capture APKs in subdirectories
```

### 2. v2-release-build.yml (V2 Release Workflow)
**Locations:** Lines 215 and 321

**Changes:**
```diff
- path: |
-   app-v2/build/outputs/apk/${{ github.event.inputs.build_type }}/*.apk
+ path: |
+   app-v2/build/outputs/apk/${{ github.event.inputs.build_type }}/**/*.apk
```

```diff
- path: app-v2/build/outputs/apk/debug/*.apk
+ path: app-v2/build/outputs/apk/debug/**/*.apk
```

### 3. codec-env-smoke.yml (Codec Smoke Test)
**Location:** Line 59

**Change:**
```diff
- path: app-v2/build/outputs/apk/debug/*.apk
+ path: app-v2/build/outputs/apk/debug/**/*.apk
```

### 4. debug-tools-gating.yml (Debug Tools Verification)
**Location:** Line 170

**Change:**
```diff
- path: app-v2/build/outputs/apk/release/*.apk
+ path: app-v2/build/outputs/apk/release/**/*.apk
```

## Workflows NOT Modified (And Why)

### debug-build.yml
**Reason:** Uses `-PuseSplits=false` (line 166), which creates APKs directly in the variant folder without subdirectories. Additionally, it uses `find` command to copy APKs to a flat `artifacts/` directory before upload, so the path pattern is already correct.

## Verification

### Why This Fix Works
1. **Recursive Matching:** The `**` pattern is a standard glob feature supported by GitHub Actions' `actions/upload-artifact@v4`
2. **Backward Compatible:** The pattern also matches files in the immediate directory, so non-split builds continue to work
3. **ABI Split Builds:** Now correctly finds APKs in subdirectories like `arm64-v8a/` and `armeabi-v7a/`

### Expected Behavior After Fix
When the workflow runs with ABI splits enabled:
1. Build step creates APKs in ABI-specific subdirectories
2. Upload artifact step finds all APKs using `**/*.apk` pattern
3. All APKs are successfully uploaded to the artifact
4. Workflow completes successfully

## Related Configuration

### app-v2/build.gradle.kts (Lines ~580-600)
```kotlin
val useSplits = project.findProperty("useSplits")?.toString()?.toBoolean() ?: false
val abiFilters = project.findProperty("abiFilters")?.toString()

if (useSplits) {
    splits {
        abi {
            isEnable = true
            reset()
            if (abiFilters != null) {
                abiFilters.split(",").forEach { abi ->
                    include(abi.trim())
                }
            } else {
                include("arm64-v8a", "armeabi-v7a")
            }
            isUniversalApk = project.findProperty("universalApk")?.toString()?.toBoolean() ?: false
        }
    }
}
```

This configuration creates separate APKs for each ABI in dedicated subdirectories when `useSplits=true` is set.

## Testing Notes

### Manual Testing
To test this fix manually:
1. Trigger the "Build APK – V2 App" workflow from GitHub Actions
2. Select any ABI target (arm64-v8a, armeabi-v7a, or both)
3. Verify the workflow completes successfully
4. Download the artifact and verify all expected APKs are present

### Expected Artifact Contents
For `abi_target: both`, the artifact should contain:
- `app-v2-release-arm64-v8a.apk`
- `app-v2-release-armeabi-v7a.apk`

## Commit Details
- **Commit:** 94e0dfc
- **Date:** 2026-01-25
- **Branch:** copilot/add-fishit-player-actions
- **Fixes:** GitHub Actions run #21340960318

## References
- GitHub Actions upload-artifact documentation: https://github.com/actions/upload-artifact
- Glob pattern documentation: https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions#patterns-to-match-file-paths
- Android Gradle Plugin ABI splits: https://developer.android.com/build/configure-apk-splits
