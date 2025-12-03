# FFmpegKit Custom Build Workflow

## Overview

This document describes the custom FFmpegKit build workflow for FishIT-Player. The workflow builds a slim, optimized FFmpegKit Android Archive (AAR) from source using the official FFmpegKit build scripts.

## Why Custom Build?

Building FFmpegKit from source provides several advantages:

1. **Size Optimization**: Include only the codecs and features needed for streaming
2. **ABI Selection**: Build only for target architectures (ARM64, ARM v7a)
3. **Custom Configuration**: Enable/disable specific features and libraries
4. **Clean Build**: No binary merging; everything built from verified source
5. **License Compliance**: Clear control over GPL vs LGPL components

## Workflow Location

The workflow is defined in `.github/workflows/build-ffmpegkit.yml`

## How to Use

### Triggering the Workflow

The workflow is manually triggered via GitHub Actions:

1. Go to **Actions** tab in GitHub
2. Select **Build Custom FFmpegKit AAR** workflow
3. Click **Run workflow**
4. Configure build options:
   - **FFmpegKit version**: Git tag or branch (default: `main`)
   - **Enable GPL**: Build with GPL libraries (x264, x265)
   - **Enable ARM v7a**: Build for 32-bit ARM devices
   - **Enable ARM64 v8a**: Build for 64-bit ARM devices (recommended)
   - **Enable external libs**: Include OpenSSL, Opus, VP9, AV1
   - **Create release**: Automatically create a GitHub release

### Build Configuration Examples

#### Minimal Build (LGPL, ARM64 only)
```
ffmpeg_kit_version: main
enable_gpl: false
enable_arm_v7a: false
enable_arm64_v8a: true
enable_external_libs: false
```

**Result**: Smallest AAR, suitable for streaming HLS/DASH content with basic codecs.

#### Standard Build (LGPL, ARM64 + ARM v7a)
```
ffmpeg_kit_version: main
enable_gpl: false
enable_arm_v7a: true
enable_arm64_v8a: true
enable_external_libs: true
```

**Result**: Includes modern codecs (VP9, AV1, Opus) and HTTPS support via OpenSSL.

#### Full Build (GPL, all features)
```
ffmpeg_kit_version: main
enable_gpl: true
enable_arm_v7a: true
enable_arm64_v8a: true
enable_external_libs: true
```

**Result**: Includes x264/x265 encoders. **Note**: GPL license applies to the entire app.

## Build Architecture

### Build Process

1. **Environment Setup**
   - Ubuntu 24.04 runner
   - Java 21 (Temurin)
   - Android SDK 36
   - Android NDK 25.2.9519653 (NDK r25c, required for FFmpegKit v6.0 compatibility)

2. **FFmpegKit Clone**
   - Clones from `https://github.com/arthenica/ffmpeg-kit`
   - Uses specified version/tag or main branch

3. **Build Script Invocation**
   - Calls `android.sh` with configured flags
   - Disables unused architectures (x86, x86_64)
   - Enables only selected external libraries
   - Optimizes for speed (`--speed`)

4. **AAR Output**
   - AAR generated in `prebuilt/` directory
   - Contains compiled native libraries (.so files)
   - Includes Java wrapper classes

5. **Artifact Upload**
   - AAR uploaded as GitHub Actions artifact
   - Includes checksums and build info
   - Optional GitHub release creation

### Build Flags

The workflow uses the following FFmpegKit build flags:

**Always Applied:**
- `--disable-x86`: Skip x86 architecture
- `--disable-x86-64`: Skip x86_64 architecture
- `--enable-android-zlib`: Use built-in Android zlib
- `--enable-android-media-codec`: Enable MediaCodec support
- `--speed`: Optimize for speed over size

**Conditional:**
- `--disable-arm-v7a`: Skip 32-bit ARM (if disabled)
- `--disable-arm-v7a-neon`: Skip ARM NEON (if disabled)
- `--disable-arm64-v8a`: Skip 64-bit ARM (if disabled)
- `--enable-gpl`: Enable GPL libraries
- `--enable-x264`: H.264 encoder (GPL)
- `--enable-x265`: HEVC encoder (GPL)
- `--enable-openssl`: HTTPS support
- `--enable-opus`: Opus audio codec
- `--enable-libvpx`: VP8/VP9 video codec
- `--enable-dav1d`: AV1 video decoder

### Build Time

Expected build times (GitHub Actions, 4-core VM):

- **Minimal build** (ARM64 only): ~45-60 minutes
- **Standard build** (ARM64 + ARM v7a): ~90-120 minutes
- **Full build** (GPL + all libs): ~150-180 minutes

## Using the Built AAR

### 1. Download the AAR

After the workflow completes:

1. Go to the workflow run page
2. Download the `ffmpeg-kit-custom-aar` artifact
3. Extract `ffmpeg-kit-custom.aar`

### 2. Add to Project

Copy the AAR to your project:

```bash
mkdir -p app/libs
cp ffmpeg-kit-custom.aar app/libs/
```

### 3. Update Dependencies

Edit `app/build.gradle.kts`:

```kotlin
dependencies {
    // Replace Jellyfin Media3 FFmpeg decoder
    // implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.8.0+1")
    
    // Use custom FFmpegKit AAR
    implementation(files("libs/ffmpeg-kit-custom.aar"))
    
    // Keep other Media3 dependencies
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    // ...
}
```

### 4. Sync and Build

```bash
./gradlew :app:assembleDebug
```

## AAR Contents

The built AAR contains:

- **Native libraries** (.so files) for selected ABIs:
  - `lib/arm64-v8a/libffmpegkit.so` (if ARM64 enabled)
  - `lib/armeabi-v7a/libffmpegkit.so` (if ARM v7a enabled)
  - Additional codec/format libraries

- **Java classes**:
  - `com.arthenica.ffmpegkit.*` - Main API
  - JNI wrapper classes

- **AndroidManifest.xml**: Basic manifest stub

- **proguard.txt**: ProGuard rules (if any)

## Verifying the Build

### Check AAR Size

A minimal build should be:
- ARM64 only: ~15-25 MB
- ARM64 + ARM v7a: ~30-40 MB

Full builds with GPL libraries can be 50-80 MB or larger.

### Inspect AAR Contents

```bash
unzip -l ffmpeg-kit-custom.aar
```

Look for `.so` files in `jni/` or `lib/` directories.

### Verify SHA256

Compare the SHA256 in `checksums.txt` with:

```bash
sha256sum ffmpeg-kit-custom.aar
```

## Troubleshooting

### Build Fails with NDK Errors

**Problem**: NDK version mismatch or missing.

**Solution**: The workflow uses NDK 25.2.9519653 (r25c), which is compatible with FFmpegKit v6.0. If build fails, check:
1. FFmpegKit compatibility with this NDK version
2. Note: NDK r27 is NOT compatible with FFmpegKit v6.0 due to build system changes
3. Update `ANDROID_NDK_VERSION` in workflow only if using a different FFmpegKit version

### AAR Not Found After Build

**Problem**: `android.sh` completed but no AAR in `prebuilt/`.

**Solution**: Check build logs for errors. The script may have:
1. Skipped AAR creation due to errors
2. Changed output location in newer versions

### Build Times Out (240 minutes)

**Problem**: Full builds can exceed timeout.

**Solution**:
1. Disable unnecessary external libraries
2. Build only one ABI (ARM64 recommended)
3. Use `--lts` flag for faster builds (API 16+ support)

### GPL License Concerns

**Problem**: Unsure about GPL implications.

**Solution**:
- Without `--enable-gpl`: LGPL 2.1+ applies
- With `--enable-gpl`: GPL 3.0+ applies to entire app
- **Recommendation**: Start with LGPL build; add GPL only if x264/x265 encoding is required

## FFmpegKit Documentation

For detailed FFmpegKit documentation, see:

- **Main Repository**: https://github.com/arthenica/ffmpeg-kit
- **Wiki**: https://github.com/arthenica/ffmpeg-kit/wiki
- **Building Guide**: https://github.com/arthenica/ffmpeg-kit/wiki/Building
- **Android Guide**: https://github.com/arthenica/ffmpeg-kit/wiki/Android

## Maintenance

### Updating FFmpegKit Version

To use a newer FFmpegKit release:

1. Check available tags: https://github.com/arthenica/ffmpeg-kit/tags
2. Run workflow with desired tag (e.g., `v6.0`)
3. Test the built AAR in a development build
4. Update documentation if API changes

### Modifying Build Configuration

To add/remove features:

1. Edit `.github/workflows/build-ffmpegkit.yml`
2. Update the "Prepare build configuration" step
3. Add new `BUILD_FLAGS` entries
4. Test the modified workflow

## Best Practices

1. **Version Control**: Tag working AAR builds
2. **Size Monitoring**: Track AAR size over time
3. **Testing**: Test playback with custom AAR before production use
4. **Documentation**: Update build info when changing configuration
5. **Caching**: Save working AARs to avoid rebuilding unnecessarily

## Known Limitations

1. **Build Time**: Builds can take 1-4 hours depending on configuration
2. **Runner Resources**: GitHub Actions runners have 7GB RAM, 14GB disk
3. **No Caching**: Each build compiles from scratch (by design)
4. **Single Job**: No parallel architecture builds (simpler, more reliable)

## Future Enhancements

Possible improvements:

- [ ] Add presets (minimal, standard, full)
- [ ] Cache intermediate build artifacts
- [ ] Split build by architecture (parallel jobs)
- [ ] Automated testing of built AAR
- [ ] Integration with release workflow
- [ ] Support for custom FFmpeg patches
