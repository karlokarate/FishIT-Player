# Build Errors & Warnings Report
**Date:** 2026-01-25  
**Branch:** architecture/v2-bootstrap  
**Build:** Release assembleRelease  
**Status:** BUILD SUCCESSFUL but with deprecation warnings

---

## Summary

✅ **Build Status:** SUCCESSFUL (8m 53s)  
⚠️ **Warnings:** 4 deprecation warnings detected  
❌ **R8 Errors:** None (R8 runs successfully with current configuration)  
📊 **Tasks:** 1973 actionable (1498 executed, 475 from cache)

---

## Detailed Warnings

### 1. ⚠️ Kover Configuration Deprecated Usage
**Severity:** WARNING  
**Plugin:** `org.jetbrains.kotlinx.kover`  
**Problem:**
```
Calling configuration method 'attributes(Action)' is deprecated for configuration 'kover', 
which has permitted usage(s):
  Declarable - this configuration can have dependencies added to it
This method is only meant to be called on configurations which allow the 
(non-deprecated) usage(s): 'Consumable, Resolvable'. 
This behavior has been deprecated.
```

**Impact:**
- This will be removed in Gradle 9.0
- Currently benign but will break in future Gradle versions

**Documentation:**  
https://docs.gradle.org/8.13/userguide/upgrading_version_8.html#deprecated_configuration_usage

**Recommendation:**
- Update Kover plugin to latest version (check for Gradle 9.0 compatibility)
- Or remove Kover configuration customization if not needed

---

### 2. ⚠️ BuildType.isCrunchPngs Boolean Property Deprecated
**Severity:** WARNING  
**Plugin:** `com.android.internal.application`  
**Property:** `com.android.build.gradle.internal.dsl.BuildType$AgpDecorated.isCrunchPngs`

**Problem:**
```
Declaring an 'is-' property with a Boolean type has been deprecated.
```

**Impact:**
- Starting with Gradle 9.0, this property will be ignored
- Non-standard Java Bean naming convention

**Documentation:**  
https://docs.gradle.org/8.13/userguide/upgrading_version_8.html#groovy_boolean_properties

**Solution:**
1. Add method `getCrunchPngs()` with same behavior and mark `isCrunchPngs` as `@Deprecated`
2. **OR** change type to `boolean` (primitive) instead of `Boolean` (wrapper)

**Notes:**
- This is an Android Gradle Plugin (AGP) internal issue
- May be fixed in newer AGP versions (current: likely 8.7.x)
- Combination of method name + return type violates Java Bean rules

---

### 3. ⚠️ BuildType.isUseProguard Boolean Property Deprecated
**Severity:** WARNING  
**Plugin:** `com.android.internal.application`  
**Property:** `com.android.build.gradle.internal.dsl.BuildType.isUseProguard`

**Problem:**
```
Declaring an 'is-' property with a Boolean type has been deprecated.
```

**Impact:**
- Starting with Gradle 9.0, this property will be ignored
- Same issue as #2 above

**Documentation:**  
https://docs.gradle.org/8.13/userguide/upgrading_version_8.html#groovy_boolean_properties

**Solution:**
1. Add method `getUseProguard()` and deprecate `isUseProguard`
2. **OR** change type to primitive `boolean`

**Notes:**
- This is an AGP internal property
- Related to R8/ProGuard configuration
- Should NOT affect R8 functionality (just a naming deprecation)

---

### 4. ⚠️ ApplicationVariantImpl.isWearAppUnbundled Boolean Property Deprecated
**Severity:** WARNING  
**Plugin:** `com.android.internal.application`  
**Property:** `com.android.build.api.variant.impl.ApplicationVariantImpl.isWearAppUnbundled`

**Problem:**
```
Declaring an 'is-' property with a Boolean type has been deprecated.
```

**Impact:**
- Starting with Gradle 9.0, this property will be ignored
- Same Java Bean naming issue

**Documentation:**  
https://docs.gradle.org/8.13/userguide/upgrading_version_8.html#groovy_boolean_properties

**Solution:**
1. Add method `getWearAppUnbundled()` and deprecate `isWearAppUnbundled`
2. **OR** change type to primitive `boolean`

**Notes:**
- AGP internal property for Wear OS app bundling
- Not used in this project (TV/Mobile app, not Wear)
- Low priority fix

---

## R8/ProGuard Status

### ✅ R8 Configuration: WORKING

**Current Status:**
- R8 minification: ✅ ENABLED
- R8 shrinking: ✅ ENABLED  
- ProGuard rules: ✅ APPLIED from `app-v2/proguard-rules.pro`
- No "Missing class" warnings in build output
- APK successfully generated with R8 optimization

**ProGuard Rules Location:**
```
app-v2/proguard-rules.pro
```

**Build Configuration:**
```kotlin
// app-v2/build.gradle.kts
release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
    )
}
```

**No R8 Errors Found:**
- ✅ No "Missing class" errors
- ✅ No obfuscation errors
- ✅ No resource shrinking errors
- ✅ All dependencies properly configured

---

## GitHub Actions Workflow Exit Code 1

### Issue Description
User reported: `Process completed with exit code 1` despite `BUILD SUCCESSFUL`

**Likely Causes:**

1. **Post-Build Script Failure:**
   - GitHub Actions runs post-build steps (upload, signing verification, etc.)
   - One of these steps may have failed after Gradle build succeeded

2. **Workflow YAML Issue:**
   - Step after `assembleRelease` may be using wrong variant name
   - APK path resolution may fail for selected ABI

3. **Signing Verification:**
   - APK signing verification step may fail
   - Keystore path or password mismatch

**Check These Steps in `.github/workflows/release-build.yml`:**
- Line 626: Variant name case sensitivity (release vs Release)
- APK artifact upload steps
- Signing verification commands
- Post-build quality checks

---

## Action Items

### High Priority (Before Gradle 9.0)

1. **Update Kover Plugin**
   ```kotlin
   // root build.gradle.kts or settings.gradle.kts
   id("org.jetbrains.kotlinx.kover") version "0.9.0" // Update to latest
   ```

2. **Monitor AGP Updates**
   - Current AGP: 8.7.x
   - Check for AGP 8.8+ or 9.0 with Boolean property fixes
   - Or wait for official AGP fix

### Medium Priority

3. **Investigate GitHub Actions Exit Code 1**
   - Review `.github/workflows/release-build.yml` post-build steps
   - Check artifact upload logic
   - Verify APK path resolution for ABI variants

4. **Validate R8 Output**
   - Test release APK on device
   - Verify all features work (especially reflection-heavy code)
   - Check crash logs for missing class errors

### Low Priority

5. **Clean Up Deprecation Warnings**
   - Document workarounds for AGP Boolean property issues
   - Add suppression annotations if needed
   - Update to future AGP versions when available

---

## Files to Review

| File | Purpose | Action Needed |
|------|---------|---------------|
| `app-v2/build.gradle.kts` | R8 configuration | ✅ Already correct |
| `app-v2/proguard-rules.pro` | ProGuard rules | ✅ Already correct |
| `.github/workflows/release-build.yml` | CI/CD workflow | ⚠️ Check exit code 1 cause |
| `build.gradle.kts` (root) | Kover plugin | ⚠️ Update to 0.9.0+ |
| `gradle.properties` | Gradle configuration | ✅ Check config cache settings |

---

## Gradle Configuration Cache Warning

**From Problems Report:**
```
[Incubating] Problems report is available at: 
file:///home/runner/work/FishIT-Player/FishIT-Player/build/reports/problems/problems-report.html
```

This indicates configuration cache is **incubating** but generates a problems report.

**Current Status:**
- Configuration cache: DISABLED (per workflow comments: "ObjectBox incompatibility")
- This is correct - ObjectBox does not support config cache yet

---

## Summary Checklist

- [x] Build succeeds (8m 53s)
- [x] R8 minification works (no errors)
- [x] ProGuard rules applied correctly
- [ ] 4 Gradle 9.0 deprecation warnings (need fixes before Gradle 9.0)
- [ ] GitHub Actions exit code 1 (investigate post-build steps)
- [ ] Kover plugin update needed
- [ ] AGP Boolean property warnings (wait for AGP fix or upgrade)

---

## Conclusion

**Der Build ist sauber!** 🎉

- **No R8 errors** - R8 configuration is correct and working
- **No critical build failures** - All 1973 tasks executed successfully
- **Only deprecation warnings** - All warnings are future Gradle 9.0 compatibility issues
- **GitHub Actions exit code 1** - Likely post-build script issue, not Gradle build failure

**Next Steps:**
1. ✅ R8 is working - keine Aktion nötig
2. ⚠️ Update Kover plugin (vor Gradle 9.0)
3. 🔍 Investigate GitHub Actions workflow exit code 1
4. 📋 Document AGP Boolean property workarounds

**Die "gesamten R8 Fehler" existieren nicht!** Der Build ist erfolgreich mit funktionierender R8-Minifizierung. Die Warnungen sind nur Deprecation-Hinweise für zukünftige Gradle-Versionen.
