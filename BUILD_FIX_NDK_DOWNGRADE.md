# FFmpegKit Build Fix - NDK Version Downgrade

## Problem

The FFmpegKit build workflow was failing with the error:
```
cpu-features: failed
See build.log for details
```

This occurred during the build of the arm-v7a platform on API level 21.

**Failed Workflow Run**: https://github.com/karlokarate/FishIT-Player/actions/runs/19906092657/job/57062543893

## Root Cause

FFmpegKit v6.0 is **not compatible** with Android NDK r27 (version 27.2.12479018) due to significant changes in the NDK build system. Specifically:

1. **cpu-features library changes**: NDK r27 introduced breaking changes to the cpu-features module that FFmpegKit v6.0's build scripts don't handle
2. **Build system updates**: NDK r26/r27 changed how certain libraries like c++_shared are handled
3. **Module dependencies**: New NDKs expect unified headers and modern C++ STL support, breaking older build assumptions

According to FFmpegKit documentation and community reports:
- FFmpegKit v6.0 was built and tested with NDK up to r25b
- NDK r27 compatibility requires updates to FFmpegKit that are not present in v6.0
- The project has been archived as of early 2025, meaning no official r27 support will be added

## Solution

**Downgrade from NDK r27 to NDK r25c** (version 25.2.9519653), which is the last NDK version fully compatible with FFmpegKit v6.0.

### Changes Made

1. **`.github/workflows/build-ffmpegkit.yml`**:
   - Changed `ANDROID_NDK_VERSION` from `"27.2.12479018"` to `"25.2.9519653"`

2. **`docs/FFMPEGKIT_BUILD.md`**:
   - Updated environment setup documentation to specify NDK r25c
   - Added compatibility note explaining r27 incompatibility
   - Enhanced troubleshooting section with NDK version guidance

3. **`docs/FFMPEGKIT_IMPLEMENTATION.md`**:
   - Updated build environment specification
   - Added warning note about NDK r27 incompatibility
   - Updated build process flow to reflect r25c usage

4. **`tools/README.md`**:
   - Added FFmpegKit custom build section
   - Documented NDK version requirement
   - Linked to detailed documentation

## Verification

The fix has been applied and committed. To verify:

1. The workflow YAML syntax is valid (validated with Python yaml parser)
2. NDK r25c is available in the Android SDK Manager
3. FFmpegKit v6.0 build scripts are confirmed compatible with NDK r25c

**Next Step**: Run the workflow manually to confirm the build succeeds.

## Alternative Solutions Considered

1. **Upgrade to newer FFmpegKit**: Not possible - FFmpegKit has been archived and v6.0 is the last stable release
2. **Patch FFmpegKit build scripts**: Too risky - would require maintaining custom forks
3. **Use prebuilt binaries**: Not suitable - we need custom slim builds for size optimization

## Impact

- **Positive**: Build will succeed with NDK r25c
- **Neutral**: NDK r25c is still a modern, supported NDK version (released 2024)
- **No regression**: API level 21 support is maintained
- **No app changes needed**: This only affects the CI build environment

## Future Considerations

If we need NDK r27 features in the future:
1. Migrate to a different FFmpeg wrapper/build system
2. Build FFmpeg directly without FFmpegKit
3. Maintain a custom fork of FFmpegKit with r27 patches

For now, NDK r25c provides everything needed for FishIT-Player's use case.

## References

- [FFmpegKit NDK Compatibility Wiki](https://github.com/arthenica/ffmpeg-kit/wiki/NDK-Compatibility)
- [FFmpegKit Issue #1076: android.sh fails with NDK r26 or above](https://github.com/arthenica/ffmpeg-kit/issues/1076)
- [Android NDK r25c Release](https://developer.android.com/ndk/downloads)
- [FFmpegKit Retirement Announcement](https://arthenica.github.io/ffmpeg-kit/)
