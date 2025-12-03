# Build Custom FFmpegKit AAR Workflow

Quick reference for building custom FFmpegKit AARs from source.

## Usage

1. **Go to Actions**: Navigate to the Actions tab in GitHub
2. **Select Workflow**: Click "Build Custom FFmpegKit AAR"
3. **Run Workflow**: Configure options and run
4. **Download Artifact**: Get the AAR from workflow artifacts

## Presets

| Preset | ARM64 | ARM v7a | External Libs | GPL | Size | Time |
|--------|-------|---------|---------------|-----|------|------|
| Minimal | ✓ | ✗ | ✗ | ✗ | ~15-20 MB | ~45-60 min |
| Standard | ✓ | ✓ | ✓ | ✗ | ~35-45 MB | ~90-120 min |
| Full GPL | ✓ | ✓ | ✓ | ✓ | ~60-80 MB | ~150-180 min |

## Configuration Options

- **ffmpeg_kit_version**: Git tag or branch (default: `main`)
- **enable_gpl**: Include GPL libraries (x264, x265)
- **enable_arm_v7a**: Build for 32-bit ARM devices
- **enable_arm64_v8a**: Build for 64-bit ARM devices
- **enable_external_libs**: Include OpenSSL, Opus, VP9, AV1
- **create_release**: Automatically create GitHub release

## Documentation

- **Complete Guide**: `/docs/FFMPEGKIT_BUILD.md`
- **Integration**: `/docs/FFMPEGKIT_INTEGRATION.md`
- **Presets**: `/docs/FFMPEGKIT_PRESETS.md`

## Build Process

1. Clones FFmpegKit from `arthenica/ffmpeg-kit`
2. Sets up Android SDK & NDK 27.2
3. Runs `android.sh` with configured flags
4. Produces AAR in `prebuilt/` directory
5. Uploads artifact with checksums and build info

## Output

- `ffmpeg-kit-custom.aar` - The compiled AAR
- `checksums.txt` - SHA256 checksums
- `build-info.txt` - Build configuration details

## Integration

Copy AAR to your project:
```bash
cp ffmpeg-kit-custom.aar app/libs/
```

Update `app/build.gradle.kts`:
```kotlin
dependencies {
    implementation(files("libs/ffmpeg-kit-custom.aar"))
}
```

## Support

For issues or questions, see the complete documentation in `/docs/`.
