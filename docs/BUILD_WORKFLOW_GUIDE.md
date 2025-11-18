# Build APK Workflow Guide

## Overview

The new `build-apk.yml` workflow provides a simple, robust way to build FishIT Player APKs without the complexity of custom TDLib compilation.

## How to Use

### Trigger the Workflow

1. Go to GitHub Actions tab
2. Select "Build APK" workflow
3. Click "Run workflow"
4. Configure your build:

### Build Options

#### Build Type
- **debug**: Development build (faster, includes debug symbols)
- **release**: Production build (optimized, minified)

#### Target ABIs
- **both**: Builds separate APKs for arm64-v8a AND armeabi-v7a (recommended)
- **arm64-v8a**: Only for modern 64-bit devices
- **armeabi-v7a**: Only for 32-bit devices (older devices)

#### Sign APK
- **true**: Sign the APK with your release keystore (requires secrets)
- **false**: Build unsigned APK (for testing only)

#### Mirror Mode
- **true**: Disable ObjectBox (pure Telegram mirror mode)
- **false**: Enable ObjectBox (full features)

### Required Secrets

For signed builds, ensure these secrets are configured:
- `ANDROID_SIGNING_KEYSTORE_BASE64` - Base64-encoded keystore file
- `ANDROID_SIGNING_KEYSTORE_PASSWORD` - Keystore password
- `ANDROID_SIGN_KEY_ALIAS` - Key alias
- `ANDROID_SIGNING_KEY_PASSWORD` - Key password

### Build Artifacts

The workflow produces:
- **APK files** (1 or 2 depending on ABI selection)
- **mapping.txt** (ProGuard mappings for release builds)
- **checksums.txt** (SHA256 hashes for verification)

Artifacts are retained for 14 days.

### Example Builds

#### Production Build (Both ABIs, Signed)
```
build_type: release
abis: both
sign_apk: true
mirror_only: false
```
**Result**: Two signed production APKs (arm64 + v7a)

#### Debug Build (arm64 only, Unsigned)
```
build_type: debug
abis: arm64-v8a
sign_apk: false
mirror_only: true
```
**Result**: One unsigned debug APK for testing

#### Release Build (Universal, Signed, Mirror Mode)
```
build_type: release
abis: both
sign_apk: true
mirror_only: true
```
**Result**: Two signed APKs in mirror mode

## Build Time

Typical build times:
- **Debug**: ~10-15 minutes
- **Release**: ~15-25 minutes

Much faster than the old TDLib build workflows (which took 1-2 hours)!

## Workflow Features

✅ **Simple**: No complex configuration needed
✅ **Fast**: Uses pre-built TDLib from Maven Central
✅ **Flexible**: Choose your ABI targets
✅ **Reliable**: Proper error handling and verification
✅ **Informative**: Build summary with checksums
✅ **Efficient**: Parallel gradle execution
✅ **Cached**: Uses Gradle and Java caching

## Migration from Old Workflows

### Old Way (12 workflows)
- `android-build.yml` - Complex with TDLib picker
- `android-build-multiabi.yml` - Old multi-ABI builds
- `tdlib_android.yml` - TDLib build
- `tdlib_official.yml` - Official TDLib
- ... 8 more TDLib variants

### New Way (1 workflow)
- `build-apk.yml` - Simple, does everything

### What Changed
- ❌ No more TDLib compilation
- ❌ No more NDK/CMake complexity
- ❌ No more build caching issues
- ✅ Direct dependency from Maven Central
- ✅ Faster builds
- ✅ Simpler maintenance

## Troubleshooting

### Build Fails
1. Check the build summary in GitHub Actions
2. Look for Gradle errors in the logs
3. Verify secrets are configured (for signed builds)

### APK Not Found
- Ensure the build completed successfully
- Check the "Upload APK Artifacts" step
- Verify the artifact retention hasn't expired (14 days)

### Signature Verification Failed
- Verify keystore secrets are correct
- Check that keystore password and alias match
- Ensure keystore file is properly base64-encoded

## Technical Details

### Dependencies
- **tdl-coroutines**: 5.0.0 (includes TDLib v1.8.56)
- **AGP**: 8.5.2
- **Kotlin**: 2.0.21
- **Gradle**: 8.13

### Build Environment
- **Runner**: ubuntu-24.04
- **Java**: Temurin 21
- **Android SDK**: API 36
- **Build Tools**: 35.0.0

### Gradle Configuration
Uses existing `app/build.gradle.kts` settings:
- ABI splits via `abiFilters`
- ProGuard for release builds
- Signing configuration from secrets
- ObjectBox integration toggle

## Support

For issues or questions:
1. Check GitHub Actions logs
2. Review this guide
3. Consult CHANGELOG.md for recent changes
4. Open an issue on GitHub

---

**Updated**: 2025-11-18
**Workflow**: `.github/workflows/build-apk.yml`
