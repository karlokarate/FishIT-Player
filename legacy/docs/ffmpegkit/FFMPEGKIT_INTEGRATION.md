> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

# Integrating Custom FFmpegKit AAR

This guide explains how to integrate a custom-built FFmpegKit AAR into the FishIT-Player project.

## Prerequisites

- Custom FFmpegKit AAR built via the `build-ffmpegkit.yml` workflow
- Development environment set up (JDK 21, Android SDK)

## Step 1: Download the AAR

### From GitHub Actions

1. Navigate to the workflow run in GitHub Actions
2. Scroll to the **Artifacts** section
3. Download `ffmpeg-kit-custom-aar.zip`
4. Extract the archive to get:
   - `ffmpeg-kit-custom.aar`
   - `checksums.txt`
   - `build-info.txt`

### From GitHub Release (if created)

1. Go to the Releases page
2. Find the FFmpegKit release
3. Download `ffmpeg-kit-custom.aar`

## Step 2: Add AAR to Project

Create a `libs` directory and copy the AAR:

```bash
cd /path/to/FishIT-Player
mkdir -p app/libs
cp /path/to/downloaded/ffmpeg-kit-custom.aar app/libs/
```

## Step 3: Update Gradle Configuration

### Option A: Use Custom AAR (Recommended)

Edit `app/build.gradle.kts` to replace the Jellyfin Media3 FFmpeg decoder:

```kotlin
dependencies {
    // ... existing dependencies ...
    
    // Media3 dependencies (keep these)
    val media3 = "1.8.0"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    
    // REPLACE THIS:
    // implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.8.0+1")
    
    // WITH THIS:
    implementation(files("libs/ffmpeg-kit-custom.aar"))
    
    // ... rest of dependencies ...
}
```

### Option B: Keep Both (Testing)

You can keep both for testing purposes:

```kotlin
dependencies {
    // ... existing dependencies ...
    
    // Original Jellyfin FFmpeg decoder
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.8.0+1")
    
    // Custom FFmpegKit (can be enabled/disabled via build variant)
    implementation(files("libs/ffmpeg-kit-custom.aar"))
    
    // ... rest of dependencies ...
}
```

## Step 4: Update Code (if needed)

### If Using FFmpegKit API Directly

If you're using FFmpegKit's API (not just as a Media3 decoder), you may need to import the classes:

```kotlin
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
```

### If Using as Media3 Decoder

The custom AAR should work as a drop-in replacement for the Jellyfin Media3 FFmpeg decoder if it's built with the same APIs.

## Step 5: Sync and Build

```bash
./gradlew clean
./gradlew :app:assembleDebug
```

## Step 6: Verify Integration

### Check APK Size

Compare APK size before and after:

```bash
# Before (with Jellyfin FFmpeg)
ls -lh app/build/outputs/apk/debug/app-debug.apk

# After (with custom FFmpegKit)
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

A custom minimal build should result in a smaller APK.

### Inspect APK Contents

```bash
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep libffmpeg
```

You should see the FFmpeg native libraries for your selected ABIs.

### Test Playback

1. Install the app on a test device or emulator
2. Test various media formats:
   - HLS streams
   - MP4 files
   - Other formats your app supports
3. Monitor logs for any FFmpeg-related errors

## Troubleshooting

### Build Error: "Duplicate files"

**Problem**: Conflicts with existing FFmpeg libraries.

**Solution**: Add packaging options in `app/build.gradle.kts`:

```kotlin
android {
    // ...
    packaging {
        jniLibs {
            pickFirsts += setOf(
                "**/libavcodec.so",
                "**/libavformat.so",
                "**/libavutil.so",
                "**/libswresample.so",
                "**/libswscale.so",
                "**/libffmpeg.so"
            )
        }
    }
}
```

### Runtime Error: "Library not found"

**Problem**: FFmpegKit native libraries not loaded.

**Solution**: Verify the AAR contains the correct ABIs:

```bash
unzip -l app/libs/ffmpeg-kit-custom.aar | grep "\.so$"
```

Ensure ABIs match your device/emulator architecture.

### Playback Fails for Certain Formats

**Problem**: Custom build missing required codecs.

**Solution**: Rebuild FFmpegKit with additional external libraries enabled:
1. Identify missing codec (check logs)
2. Re-run workflow with `enable_external_libs: true`
3. Or add specific library flags to workflow

### APK Too Large

**Problem**: Custom AAR is larger than expected.

**Solution**:
1. Rebuild with fewer ABIs (ARM64 only)
2. Disable GPL libraries if not needed
3. Disable external libraries
4. Check that x86/x86_64 ABIs are excluded

## Reverting to Jellyfin FFmpeg

If you need to revert:

1. Edit `app/build.gradle.kts`:
   ```kotlin
   // Comment out custom AAR
   // implementation(files("libs/ffmpeg-kit-custom.aar"))
   
   // Re-enable Jellyfin FFmpeg
   implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.8.0+1")
   ```

2. Sync and rebuild:
   ```bash
   ./gradlew clean
   ./gradlew :app:assembleDebug
   ```

## Version Control

### Committing the AAR

**Option 1: Commit AAR (Simple)**

```bash
git add app/libs/ffmpeg-kit-custom.aar
git commit -m "Add custom FFmpegKit AAR"
```

**Pros**: Easy for team members to build
**Cons**: Large binary file in Git history

**Option 2: Git LFS (Recommended)**

```bash
# Setup Git LFS (one time)
git lfs install

# Track AAR files
git lfs track "*.aar"
git add .gitattributes

# Add and commit
git add app/libs/ffmpeg-kit-custom.aar
git commit -m "Add custom FFmpegKit AAR via LFS"
```

**Pros**: Efficient for binary files
**Cons**: Requires Git LFS setup

**Option 3: Document Only**

Don't commit the AAR. Instead:

1. Add to `.gitignore`:
   ```
   app/libs/*.aar
   ```

2. Document in README:
   ```markdown
   ## Building

   Before building, download the custom FFmpegKit AAR:
   1. Go to Actions → Build Custom FFmpegKit AAR
   2. Download the latest artifact
   3. Extract to `app/libs/ffmpeg-kit-custom.aar`
   ```

**Pros**: No binary in repo
**Cons**: Extra step for developers

## Continuous Integration

### GitHub Actions Integration

To use custom FFmpegKit in CI builds:

```yaml
# In your CI workflow
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      # ... checkout and setup steps ...
      
      - name: Download FFmpegKit AAR
        uses: actions/download-artifact@v4
        with:
          name: ffmpeg-kit-custom-aar
          path: app/libs/
          # Or use a specific workflow run/release
      
      - name: Build app
        run: ./gradlew assembleRelease
```

### Caching Strategy

Cache the AAR to avoid rebuilding:

```yaml
- name: Cache FFmpegKit AAR
  uses: actions/cache@v4
  with:
    path: app/libs/ffmpeg-kit-custom.aar
    key: ffmpegkit-${{ hashFiles('.github/workflows/build-ffmpegkit.yml') }}
```

## Best Practices

1. **Version Tracking**: Tag AAR versions in release notes
2. **Testing**: Test thoroughly before using in production
3. **Documentation**: Note which features are enabled/disabled
4. **Checksums**: Verify AAR integrity using provided checksums
5. **Backups**: Keep working AAR versions for rollback

## Advanced: Multiple AAR Variants

To support multiple builds (e.g., lite vs full):

### Directory Structure

```
app/libs/
├── ffmpeg-kit-lite.aar      (minimal, ARM64 only)
├── ffmpeg-kit-standard.aar  (with external libs)
└── ffmpeg-kit-full.aar      (GPL, all features)
```

### Build Variants

```kotlin
android {
    flavorDimensions += "ffmpeg"
    productFlavors {
        create("lite") {
            dimension = "ffmpeg"
        }
        create("full") {
            dimension = "ffmpeg"
        }
    }
}

dependencies {
    "liteImplementation"(files("libs/ffmpeg-kit-lite.aar"))
    "fullImplementation"(files("libs/ffmpeg-kit-full.aar"))
}
```

## Support

For issues with:
- **FFmpegKit build**: See `docs/FFMPEGKIT_BUILD.md`
- **Integration**: Check this guide
- **Playback problems**: Review app logs and FFmpeg debug output
- **Workflow**: Check `.github/workflows/build-ffmpegkit.yml`

## Related Documentation

- [FFmpegKit Build Workflow](FFMPEGKIT_BUILD.md)
- [Developer Guide](../DEVELOPER_GUIDE.md)
- [Architecture Overview](../ARCHITECTURE_OVERVIEW.md)
