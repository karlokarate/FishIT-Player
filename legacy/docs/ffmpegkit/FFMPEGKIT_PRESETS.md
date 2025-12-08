> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

# FFmpegKit Build Presets - Quick Reference

This document provides ready-to-use presets for building custom FFmpegKit AARs for different use cases.

## Quick Start

1. Go to **Actions** → **Build Custom FFmpegKit AAR**
2. Click **Run workflow**
3. Select a preset below
4. Click **Run workflow** button

---

## Preset 1: Minimal Streaming (Recommended)

**Best for**: HLS/DASH streaming with basic codecs

**Configuration:**
```
ffmpeg_kit_version: main
enable_gpl: false
enable_arm_v7a: false
enable_arm64_v8a: true
enable_external_libs: false
create_release: false
```

**Features:**
- ✅ ARM64 only (modern devices)
- ✅ Built-in codecs (H.264, AAC)
- ✅ Android MediaCodec support
- ✅ Smallest size (~15-20 MB)
- ❌ No external libraries
- ❌ No x264/x265 encoders

**License**: LGPL 2.1+

**Build Time**: ~45-60 minutes

**Use Case**: Most streaming apps, Fire TV, Android TV

---

## Preset 2: Standard Modern Codecs

**Best for**: Apps supporting modern video formats (VP9, AV1, Opus)

**Configuration:**
```
ffmpeg_kit_version: main
enable_gpl: false
enable_arm_v7a: true
enable_arm64_v8a: true
enable_external_libs: true
create_release: false
```

**Features:**
- ✅ ARM64 + ARM v7a (wide device support)
- ✅ VP9 video codec (libvpx)
- ✅ AV1 video decoder (dav1d)
- ✅ Opus audio codec
- ✅ HTTPS support (OpenSSL)
- ✅ Medium size (~35-45 MB)
- ❌ No x264/x265 encoders

**License**: LGPL 2.1+

**Build Time**: ~90-120 minutes

**Use Case**: Modern streaming with WebRTC, VP9, AV1 support

---

## Preset 3: Full GPL (Encoding)

**Best for**: Apps that need video encoding (recording, transcoding)

**Configuration:**
```
ffmpeg_kit_version: main
enable_gpl: true
enable_arm_v7a: true
enable_arm64_v8a: true
enable_external_libs: true
create_release: false
```

**Features:**
- ✅ ARM64 + ARM v7a
- ✅ x264 H.264 encoder
- ✅ x265 HEVC encoder
- ✅ All external libraries
- ✅ Full codec support
- ❌ Large size (~60-80 MB)
- ⚠️ GPL license applies to entire app

**License**: GPL 3.0+ (entire app must be GPL!)

**Build Time**: ~150-180 minutes

**Use Case**: Video editing, transcoding, recording apps

**⚠️ Warning**: GPL license has implications for proprietary code. Consult legal before using.

---

## Preset 4: ARM64 + External Libs

**Best for**: Balance between size and features for modern devices only

**Configuration:**
```
ffmpeg_kit_version: main
enable_gpl: false
enable_arm_v7a: false
enable_arm64_v8a: true
enable_external_libs: true
create_release: false
```

**Features:**
- ✅ ARM64 only
- ✅ Modern codecs (VP9, AV1, Opus)
- ✅ HTTPS support
- ✅ Small-medium size (~25-30 MB)
- ❌ No ARM v7a (drops older devices)

**License**: LGPL 2.1+

**Build Time**: ~60-75 minutes

**Use Case**: Modern streaming apps targeting newer devices

---

## Preset 5: Release Build with GitHub Release

**Best for**: Creating a tagged release with AAR artifact

**Configuration:**
```
ffmpeg_kit_version: main (or specific tag like v6.0)
enable_gpl: false
enable_arm_v7a: true
enable_arm64_v8a: true
enable_external_libs: true
create_release: true
```

**Features:**
- ✅ Creates GitHub Release automatically
- ✅ Includes build info and checksums
- ✅ Easy to share and version
- ✅ Standard configuration (ARM64 + ARM v7a)

**Build Time**: ~90-120 minutes

**Use Case**: Stable builds for distribution

---

## Comparison Table

| Preset | Size | ABIs | External Libs | GPL | Build Time | Use Case |
|--------|------|------|---------------|-----|------------|----------|
| **Minimal** | ~15-20 MB | ARM64 | No | No | 45-60 min | Basic streaming |
| **Standard** | ~35-45 MB | ARM64, ARM v7a | Yes | No | 90-120 min | Modern codecs |
| **Full GPL** | ~60-80 MB | ARM64, ARM v7a | Yes | Yes | 150-180 min | Encoding/transcoding |
| **ARM64 + Libs** | ~25-30 MB | ARM64 | Yes | No | 60-75 min | Modern devices only |
| **Release** | ~35-45 MB | ARM64, ARM v7a | Yes | No | 90-120 min | Stable release |

---

## Custom Configuration

For advanced users, you can customize any preset:

### Common Modifications

#### Add specific FFmpegKit version
```
ffmpeg_kit_version: v6.0
```

#### Remove ARM v7a for smaller size
```
enable_arm_v7a: false
```

#### Enable GPL for encoding
```
enable_gpl: true
```

### Build Flags Reference

The workflow translates your inputs to these FFmpegKit flags:

**Always Applied:**
- `--disable-x86` (not needed for mobile)
- `--disable-x86-64` (not needed for mobile)
- `--enable-android-zlib` (built-in Android lib)
- `--enable-android-media-codec` (hardware acceleration)
- `--speed` (optimize for speed)

**When `enable_external_libs: true`:**
- `--enable-openssl` (HTTPS support)
- `--enable-opus` (Opus audio codec)
- `--enable-libvpx` (VP8/VP9 video)
- `--enable-dav1d` (AV1 decoder)

**When `enable_gpl: true`:**
- `--enable-gpl` (enable GPL license)
- `--enable-x264` (H.264 encoder)
- `--enable-x265` (HEVC encoder)

---

## Decision Tree

```
┌─ Need video encoding (x264/x265)?
│
├─ YES → Use Preset 3 (Full GPL)
│       ⚠️  Entire app becomes GPL!
│
└─ NO ─┬─ Need modern codecs (VP9/AV1)?
       │
       ├─ YES ─┬─ Support older devices?
       │       │
       │       ├─ YES → Preset 2 (Standard Modern)
       │       └─ NO  → Preset 4 (ARM64 + Libs)
       │
       └─ NO ──┬─ Want smallest size?
               │
               ├─ YES → Preset 1 (Minimal)
               └─ NO  → Preset 2 (Standard)
```

---

## Testing Your Build

After building:

1. Download the artifact
2. Check `build-info.txt` for configuration
3. Verify size in `checksums.txt`
4. Test in development build:
   ```bash
   cp ffmpeg-kit-custom.aar app/libs/
   ./gradlew assembleDebug
   ```
5. Install and test playback

---

## Troubleshooting

### Build Failed
- Check workflow logs for specific errors
- Verify NDK version compatibility
- Try stable FFmpegKit tag (e.g., `v6.0`) instead of `main`

### AAR Too Large
- Use Preset 1 (Minimal) or Preset 4 (ARM64 + Libs)
- Disable GPL libraries
- Build ARM64 only

### Missing Codecs
- Enable external libraries
- Check FFmpegKit documentation for specific codec requirements
- May need to modify workflow to add custom flags

### Playback Issues
- Test with standard presets first
- Check device architecture matches AAR ABIs
- Review app logs for FFmpeg errors

---

## FAQ

**Q: Which preset should I use for FishIT-Player?**  
A: Start with **Preset 2 (Standard Modern)** for full device support with modern codecs.

**Q: Do I need GPL for streaming?**  
A: No. GPL is only needed for x264/x265 encoding. Streaming doesn't need it.

**Q: Can I enable more external libraries?**  
A: Yes, but you'll need to modify the workflow. See `docs/FFMPEGKIT_BUILD.md`.

**Q: How often should I rebuild?**  
A: Only when:
- FFmpegKit releases a new version
- You need different features
- Security updates are released

**Q: Can I build multiple presets?**  
A: Yes, run the workflow multiple times with different configurations. Each run is independent.

---

## Next Steps

1. Choose a preset from this guide
2. Run the workflow in GitHub Actions
3. Download the AAR artifact
4. Follow `docs/FFMPEGKIT_INTEGRATION.md` to integrate
5. Test thoroughly before production use

---

## Additional Resources

- **Detailed Build Guide**: `docs/FFMPEGKIT_BUILD.md`
- **Integration Guide**: `docs/FFMPEGKIT_INTEGRATION.md`
- **FFmpegKit Wiki**: https://github.com/arthenica/ffmpeg-kit/wiki
- **Workflow File**: `.github/workflows/build-ffmpegkit.yml`
