# TDLib BoringSSL AAR Build Guide

This document explains how to build the TDLib AAR with BoringSSL for Android.

## Overview

The build process consists of two GitHub Actions workflows:

1. **Standart.yml** - Builds TDLib with BoringSSL
2. **tdlib-boringssl-aar.yml** - Packages the AAR and creates a release

## Prerequisites

All builds happen in GitHub Actions. You only need:
- GitHub account with access to this repository
- Ability to trigger workflows

## Build Process

### Step 1: Build TDLib with BoringSSL

1. Go to **Actions** tab in GitHub
2. Select workflow: **"TDLib - Android Java (prebuilt BoringSSL, validated, doc-conform)"**
3. Click **"Run workflow"**
4. Wait for completion (~60-90 minutes)

This workflow will:
- Download TDLib from official repository (master branch)
- Download/build BoringSSL (pinned commit for reproducibility)
- Pre-generate TDLib auto-sources (MIME types, TL API)
- Build TDLib for **arm64-v8a** and **armeabi-v7a** with JNI enabled
- Generate Java bindings (TdApi.java)
- Validate libraries (ELF headers, static linking)
- Upload artifacts:
  - `tdlib-java-bindings` - Contains TdApi.java
  - `tdlib-android-arm64-v8a-boringssl` - Contains libtdjni.so for arm64
  - `tdlib-android-armeabi-v7a-boringssl` - Contains libtdjni.so for v7a
  - `logs-*` - Build logs for debugging

### Step 2: Package as AAR

1. Go to **Actions** tab in GitHub
2. Select workflow: **"TDLib BoringSSL AAR Release"**
3. Click **"Run workflow"**
4. Configure inputs:
   - **release_tag** (optional): Custom release tag (e.g., `v1.0.0`)
     - Leave empty to auto-generate: `tdlib-boringssl-{commit}-{run_id}`
   - **standart_run_id** (optional): Specific Standart.yml run ID to use
     - Leave empty to use the latest successful run
5. Click **"Run workflow"** button
6. Wait for completion (~5-10 minutes)

This workflow will:
- Download artifacts from Standart.yml workflow
- Fetch Client.java and Log.java from TDLib repository
- Populate the `libtd` module structure
- Build the AAR using Gradle
- Create a GitHub Release with:
  - `tdlib-android-boringssl.aar` - The main AAR file
  - `README.md` - Integration guide
  - `BUILD_INFO.txt` - Build metadata

### Step 3: Download and Use

1. Go to **Releases** page
2. Find your release (sorted by date)
3. Download `tdlib-android-boringssl.aar`
4. Use in your Android project (see `docs/tdlib-integration-example.md`)

## Advanced: Custom Builds

### Building Different TDLib Versions

To build a specific TDLib version/commit:

1. Modify `.github/workflows/Standart.yml`
2. Find the "Clone TDLib" step
3. Change from:
   ```yaml
   git clone --depth=1 https://github.com/tdlib/td.git "${TD_SRC_DIR}"
   ```
   To:
   ```yaml
   git clone https://github.com/tdlib/td.git "${TD_SRC_DIR}"
   cd "${TD_SRC_DIR}"
   git checkout YOUR_COMMIT_HASH
   ```

### Building Additional ABIs

To add x86/x86_64 support:

1. Edit `.github/workflows/Standart.yml`
2. Add to the matrix in the `tdlib` job:
   ```yaml
   matrix:
     include:
       # ... existing arm64-v8a and armeabi-v7a
       - abi: x86_64
         asset: boringssl-...-x86_64.tar.zst
         sha: boringssl-...-x86_64.sha256
         expect_machine: "X86-64"
         expect_class: "ELF64"
       - abi: x86
         asset: boringssl-...-x86.tar.zst
         sha: boringssl-...-x86.sha256
         expect_machine: "Intel 80386"
         expect_class: "ELF32"
   ```
3. Update BoringSSL prebuilt releases to include x86/x86_64
4. Update `tdlib-boringssl-aar.yml` to download additional ABIs

### Customizing Build Flags

TDLib build flags in `Standart.yml`:

```yaml
cmake -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$NDK_PATH/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI="${ABI}" \
  -DANDROID_PLATFORM="${ANDROID_PLATFORM}" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
  -DTD_ENABLE_JNI=ON \          # Enable JNI bindings
  -DTD_ENABLE_JAVA=ON \          # Enable Java API generation
  -DOPENSSL_ROOT_DIR="$SSL_DIR" \
  -DOPENSSL_USE_STATIC_LIBS=ON \ # Static linking (BoringSSL)
  ...
```

Common customizations:
- **Disable JSON API**: Remove `-DTD_ENABLE_JSON=ON` (not used for JNI)
- **Enable LTO**: Add `-DCMAKE_INTERPROCEDURAL_OPTIMIZATION=ON`
- **Debug build**: Change `-DCMAKE_BUILD_TYPE=Release` to `Debug`

## Local Development

### Testing AAR Build Locally

After running Standart.yml and downloading artifacts:

```bash
# 1. Download artifacts from GitHub Actions
# 2. Extract to .artifacts/ directory
# 3. Run the preparation steps manually:

mkdir -p libtd/src/main/java/org/drinkless/tdlib
mkdir -p libtd/src/main/jniLibs/{arm64-v8a,armeabi-v7a}

# Copy files
cp .artifacts/tdlib-java-bindings/TdApi.java \
   libtd/src/main/java/org/drinkless/tdlib/

find .artifacts -name 'libtdjni.so' -path '*arm64*' -exec \
   cp {} libtd/src/main/jniLibs/arm64-v8a/ \;

find .artifacts -name 'libtdjni.so' -path '*v7a*' -exec \
   cp {} libtd/src/main/jniLibs/armeabi-v7a/ \;

# Fetch Client.java and Log.java
git clone --depth=1 https://github.com/tdlib/td.git /tmp/td
cp /tmp/td/example/java/org/drinkless/tdlib/{Client,Log}.java \
   libtd/src/main/java/org/drinkless/tdlib/

# Build AAR
./gradlew :libtd:assembleRelease
```

Or use the helper script:

```bash
./scripts/test-aar-build.sh
```

### Validating the AAR

```bash
# Extract AAR contents
unzip -l libtd/build/outputs/aar/libtd-release.aar

# Should contain:
# - AndroidManifest.xml
# - classes.jar (with TdApi, Client, Log)
# - jni/arm64-v8a/libtdjni.so
# - jni/armeabi-v7a/libtdjni.so

# Check native library
unzip -p libtd/build/outputs/aar/libtd-release.aar \
  jni/arm64-v8a/libtdjni.so | file -

# Should show: ELF 64-bit LSB shared object, ARM aarch64

# Verify no dynamic OpenSSL deps
unzip -p libtd/build/outputs/aar/libtd-release.aar \
  jni/arm64-v8a/libtdjni.so > /tmp/libtdjni.so
readelf -d /tmp/libtdjni.so | grep NEEDED

# Should NOT show libssl.so or libcrypto.so
```

## Troubleshooting

### Workflow Fails: "No successful Standart.yml run found"

**Solution**: Run Standart.yml first, or provide a specific `standart_run_id`

### Workflow Fails: "libtdjni.so not found"

**Causes**:
1. Standart.yml build failed
2. Artifacts expired (14-day retention)
3. Artifact names changed

**Solution**: Check Standart.yml artifacts and ensure they uploaded successfully

### Workflow Fails: "TdApi.java not created"

**Cause**: Java generation step in Standart.yml failed

**Solution**: 
1. Check Standart.yml logs in the "Build generator" step
2. Ensure gperf and php-cli are installed in the runner
3. Check that td_api.tl schema file exists

### AAR Size Too Large

TDLib native libraries can be 20-30 MB per ABI. This is normal.

To reduce size:
- Strip symbols (already done in workflow)
- Use only required ABIs in your app
- Enable R8 shrinking in app build

### "BORINGSSL_DIR is unset"

**Cause**: BoringSSL prebuilt download or fallback build failed

**Solution**:
1. Check if prebuilt release exists with correct tag
2. Check fallback build logs
3. Verify NDK version matches

## Build Times

Typical workflow execution times:

- **Standart.yml**: 60-90 minutes
  - BoringSSL build: 10-15 min (or instant if cached)
  - TDLib configure: 5 min
  - TDLib build (per ABI): 20-30 min
  - Validation: 2-3 min

- **tdlib-boringssl-aar.yml**: 5-10 minutes
  - Artifact download: 1-2 min
  - Gradle build: 2-3 min
  - Release creation: 1-2 min

## CI/CD Integration

### Automating Releases

To automatically trigger AAR build after TDLib build:

1. Uncomment the `workflow_run` trigger in `tdlib-boringssl-aar.yml`
2. This will auto-trigger AAR packaging when Standart.yml succeeds
3. Useful for nightly builds or continuous deployment

### Version Tagging

Use semantic versioning for releases:

```bash
# Trigger with specific tag
# In GitHub Actions UI:
release_tag: v1.2.3

# Or via GitHub CLI:
gh workflow run tdlib-boringssl-aar.yml \
  -f release_tag=v1.2.3
```

## Performance Optimization

### Caching

The workflows use GitHub Actions cache for:
- NDK download (restored across runs)
- BoringSSL builds (per ABI, restored if same version)
- Gradle dependencies (restored for AAR build)

### Parallelization

Standart.yml builds both ABIs in parallel using matrix strategy:
- arm64-v8a and armeabi-v7a build simultaneously
- Reduces total build time by ~50%

## Security

### BoringSSL Pinning

The workflow pins a specific BoringSSL commit for reproducibility:

```yaml
BORINGSSL_TAG: boringssl-28efc83e86dc-android24-ndk-r27c
```

This ensures:
- Reproducible builds
- Known security characteristics
- Compatibility with TDLib

To update:
1. Test new BoringSSL commit locally
2. Build prebuilt releases for all ABIs
3. Update `BORINGSSL_TAG` in workflow

### Static Linking

BoringSSL is statically linked into libtdjni.so:
- No runtime dependencies
- Self-contained AAR
- No OpenSSL version conflicts

Verify with:
```bash
readelf -d libtdjni.so | grep NEEDED
# Should not show libssl.so or libcrypto.so
```

## Resources

- [TDLib Build Instructions](https://tdlib.github.io/td/build.html)
- [Android NDK Documentation](https://developer.android.com/ndk)
- [BoringSSL Repository](https://boringssl.googlesource.com/boringssl/)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)

## Support

For issues:
1. Check workflow logs in GitHub Actions
2. Review this guide's troubleshooting section
3. Open an issue with logs attached
4. Include workflow run URL for faster debugging
