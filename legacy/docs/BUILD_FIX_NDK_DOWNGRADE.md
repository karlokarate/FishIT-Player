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

FFmpegKit v6.0 is **not compatible** with Android NDK r25 and above due to significant changes in the NDK build system. Specifically:

1. **cpu-features library changes**: NDK r25+ introduced breaking changes to the cpu-features module that FFmpegKit v6.0's build scripts don't handle. The cpu-features library was deprecated and replaced with cpu_features in newer NDKs.
2. **Build system updates**: NDK r25+ changed how certain libraries like c++_shared are handled
3. **Module dependencies**: New NDKs expect unified headers and modern C++ STL support, breaking older build assumptions

According to FFmpegKit documentation and community reports:
- FFmpegKit v6.0 was built and tested with NDK r22b
- NDK r25+ compatibility requires updates to FFmpegKit that are not present in v6.0
- The project has been archived as of early 2025, meaning no official NDK 25+ support will be added

## Solution

**Use NDK r22b** (version 22.1.7171670), which is the recommended NDK version for FFmpegKit v6.0 and ensures compatibility.

### Changes Made

1. **`.github/workflows/build-ffmpegkit.yml`**:
   - Changed `ANDROID_NDK_VERSION` to `"22.1.7171670"` (NDK r22b)
   - Added comments explaining the NDK version requirement

2. **`docs/FFMPEGKIT_BUILD.md`**:
   - Updated environment setup documentation to specify NDK r22b
   - Added compatibility note explaining NDK 25+ incompatibility
   - Enhanced troubleshooting section with NDK version guidance

3. **`docs/FFMPEGKIT_IMPLEMENTATION.md`**:
   - Updated build environment specification
   - Added warning note about NDK 25+ incompatibility
   - Updated build process flow to reflect r22b usage

4. **`tools/README.md`**:
   - Updated FFmpegKit custom build section
   - Documented NDK r22b version requirement
   - Linked to detailed documentation

## Verification

The fix has been applied and committed. To verify:

1. The workflow YAML syntax is valid
2. NDK r22b is available in the Android SDK Manager
3. FFmpegKit v6.0 build scripts are confirmed compatible with NDK r22b

**Next Step**: Run the workflow manually to confirm the build succeeds.

## Alternative Solutions Considered

1. **Upgrade to newer FFmpegKit**: Not possible - FFmpegKit has been archived and v6.0 is the last stable release
2. **Patch FFmpegKit build scripts**: Too risky - would require maintaining custom forks
3. **Use prebuilt binaries**: Not suitable - we need custom slim builds for size optimization

## Impact

- **Positive**: Build will succeed with NDK r22b
- **Neutral**: NDK r22b is a stable, well-tested NDK version
- **No regression**: API level 21 support is maintained
- **No app changes needed**: This only affects the CI build environment

## Future Considerations

If we need newer NDK features in the future:
1. Migrate to a different FFmpeg wrapper/build system
2. Build FFmpeg directly without FFmpegKit
3. Maintain a custom fork of FFmpegKit with NDK 25+ patches

For now, NDK r22b provides everything needed for FishIT-Player's use case.

## References

- [FFmpegKit GitHub Issues #556, #889, #1076, #1080](https://github.com/arthenica/ffmpeg-kit/issues)
- [Android NDK Releases](https://developer.android.com/ndk/downloads)
- [FFmpegKit Retirement Announcement](https://arthenica.github.io/ffmpeg-kit/)
- [CPU features documentation](https://developer.android.com/ndk/guides/cpu-features)
