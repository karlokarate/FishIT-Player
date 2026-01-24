# Build Workflows

This directory contains GitHub Actions workflows for building the FishIT-Player Android application.

## Build Workflows

### 1. Debug Build (`debug-build.yml`)

**Purpose**: Build unsigned debug APKs for development and testing.

**When to use**:
- Development builds
- Internal testing
- QA testing
- Pre-release testing

**Features**:
- ✅ No signing required (unsigned)
- ✅ Fast builds (no minification)
- ✅ Includes all debug tools (LeakCanary, Chucker)
- ✅ Detailed logging enabled
- ✅ 2026 GitHub Actions optimizations
- ❌ Cannot publish to Play Store
- ❌ Cannot install over signed builds

**Manual trigger**: Actions → "Debug Build – Unsigned APK (Development)" → Run workflow

**Inputs**:
- `version_name`: Version string (e.g., "2.0.0-debug")
- `version_code`: Version code integer
- `abi_target`: Target ABI (arm64-v8a, armeabi-v7a, or both)

**Outputs**:
- Debug APK artifacts
- SHA256 checksums
- Build metrics

---

### 2. Release Build (`release-build.yml`)

**Purpose**: Build signed release APKs for production distribution.

**When to use**:
- Production releases
- Google Play Store submissions
- Official distribution channels

**Features**:
- ✅ Code signed with release key
- ✅ Code minification (R8)
- ✅ Resource shrinking
- ✅ Optimized for production
- ✅ Multiple ABI support
- ✅ Can publish to Play Store
- ⚠️ Requires signing key setup

**Manual trigger**: Actions → "Release Build – APK (arm64-v8a & armeabi-v7a)" → Run workflow

**Inputs**:
- `version_name`: Version string (e.g., "v1.0.0")
- `version_code`: Version code integer

**Prerequisites**:
- GitHub secrets configured (see [Signing Key Setup](#signing-key-setup))

**Outputs**:
- Signed release APK artifacts
- SHA256 checksums
- Build metrics
- R8 mapping files

---

### 3. Generate Signing Key (`generate-signing-key.yml`)

**Purpose**: Generate new Android signing keystores and convert to base64 for GitHub secrets storage.

**When to use**:
- First-time project setup
- Creating new signing keys
- Key rotation (advanced use case)

**Features**:
- ✅ Generates RSA 2048-bit keystore
- ✅ Converts to base64 for secrets storage
- ✅ Includes setup instructions
- ✅ Security best practices
- ⚠️ One-time setup per app

**Manual trigger**: Actions → "Generate Signing Key" → Run workflow

**Inputs**:
- `key_alias`: Key identifier (e.g., "fishit-release-key")
- `key_validity_years`: Years valid (default: 25)
- `distinguished_name`: Organization details
- `keystore_password`: Keystore password (masked)
- `key_password`: Key password (masked)

**Outputs**:
- Binary keystore file
- Base64-encoded keystore (single-line)
- Base64-encoded keystore (multiline)
- Setup instructions (SETUP_INSTRUCTIONS.md)

---

## Signing Key Setup

To build signed release APKs, you need to:

1. **Generate a signing key** (one-time setup)
2. **Configure GitHub secrets**
3. **Run release build workflow**

### Quick Start

#### Step 1: Generate Signing Key

1. Go to: **Actions** → **"Generate Signing Key"**
2. Click **"Run workflow"**
3. Fill in the required information:
   - Key alias (e.g., `fishit-release-key`)
   - Validity (25 years recommended)
   - Distinguished name (your organization info)
   - Keystore password (strong password)
   - Key password (strong password)
4. Wait for workflow to complete
5. Download the artifact
6. **IMPORTANT**: Backup the keystore file securely!

#### Step 2: Configure GitHub Secrets

Set up these repository secrets:

| Secret Name | Value | Description |
|-------------|-------|-------------|
| `KEYSTORE_BASE64` | Content of `keystore.base64.txt` | Base64-encoded keystore |
| `KEYSTORE_PASSWORD` | Your keystore password | Protects keystore access |
| `KEY_PASSWORD` | Your key password | Protects signing key |
| `KEY_ALIAS` | Your key alias | Identifies signing key |

**To set secrets**:
1. Go to: **Settings** → **Secrets and variables** → **Actions**
2. Click **"New repository secret"**
3. Add each secret with the exact name and value
4. Click **"Add secret"**

#### Step 3: Build Signed Release

1. Go to: **Actions** → **"Release Build – APK"**
2. Click **"Run workflow"**
3. Fill in version information
4. Click **"Run workflow"**
5. Download signed APK from artifacts

---

## Workflow Comparison

| Feature | Debug Build | Release Build | Generate Key |
|---------|-------------|---------------|--------------|
| **Purpose** | Development | Production | Setup |
| **Signing** | ❌ Unsigned | ✅ Signed | N/A |
| **Minification** | ❌ Disabled | ✅ R8 | N/A |
| **Debug Tools** | ✅ Included | ❌ Removed | N/A |
| **Build Time** | ~5-10 min | ~15-25 min | ~1-2 min |
| **APK Size** | Larger | Smaller | N/A |
| **Play Store** | ❌ No | ✅ Yes | N/A |
| **Prerequisites** | None | Signing key | None |

---

## Optimization Features (2026 Standards)

All build workflows use modern 2026 GitHub Actions optimizations:

### SDK Management
- ✅ Uses preinstalled Android SDK on ubuntu-24.04
- ✅ Only installs missing packages
- ✅ No redundant setup-android actions
- ✅ Faster SDK initialization

### Caching Strategy
- ✅ `gradle/actions/setup-gradle@v4` with intelligent caching
- ✅ `setup-java@v4` with built-in Gradle caching
- ✅ Better cache hit rates
- ✅ Reduced download times

### Build Performance
- ✅ Gradle configuration cache (`--configuration-cache`)
- ✅ No filesystem watching (`--no-watch-fs`)
- ✅ Build cache enabled (`--build-cache`)
- ✅ Parallel execution (`--parallel`)
- ✅ Gradle Build Scan for insights

### Action Updates
- ✅ `actions/checkout@v4`
- ✅ `actions/setup-java@v4`
- ✅ `gradle/actions/setup-gradle@v4`
- ✅ `actions/upload-artifact@v4`
- ✅ `actions/download-artifact@v4`

### Expected Performance
- **First run**: 20-30% faster than legacy workflows
- **Subsequent runs**: 40-60% faster with cache hits

---

## Security Best Practices

### Keystore Management

1. **Never commit keystore files**
   - Already in `.gitignore`
   - Use base64 encoding for GitHub secrets

2. **Backup keystores securely**
   - Store in password manager
   - Keep offline backup
   - Document key information

3. **Use strong passwords**
   - Minimum 12 characters
   - Mix of uppercase, lowercase, numbers, symbols
   - Unique passwords (don't reuse)

4. **Limit repository access**
   - Only trusted team members
   - Review access periodically
   - Use environment secrets for production

### GitHub Secrets

1. **Never log secret values**
   - All workflows mask sensitive data
   - Review logs before sharing

2. **Use environment secrets**
   - Add extra approval layer
   - Separate staging/production
   - Control deployment access

3. **Rotate secrets periodically**
   - Review quarterly
   - Update if team changes
   - Monitor for leaks

---

## Troubleshooting

### Build Failures

**Issue**: Workflow fails with signing errors

**Solution**: 
1. Verify GitHub secrets are set correctly
2. Check secret names match exactly (case-sensitive)
3. Ensure base64 encoding is complete (no truncation)
4. Re-generate keystore if corrupted

**Issue**: "BUILD FAILED: no matching variant found"

**Solution**:
1. Check Gradle sync errors
2. Verify module dependencies
3. Clean build cache: `./gradlew clean`

### Artifact Issues

**Issue**: Cannot download artifacts

**Solution**:
1. Check retention period (default: 7-30 days)
2. Verify workflow completed successfully
3. Check repository permissions

**Issue**: APK installation fails

**Solution**:
1. For debug APKs: Uninstall any signed versions first
2. For release APKs: Verify signing key matches
3. Check Android version compatibility (minSdk: 24)

### Performance Issues

**Issue**: Builds taking too long

**Solution**:
1. Check cache hit rates in logs
2. Review Gradle Build Scan for bottlenecks
3. Verify runner has sufficient resources
4. Consider upgrading runner type

---

## Complete Documentation

For detailed setup instructions, see:
- **[Android Signing Setup Guide](/docs/ANDROID_SIGNING_SETUP.md)** - Complete signing key setup
- **[Project README](/README.md)** - Project overview
- **[Architecture Docs](/docs/)** - Technical documentation

---

## Support

For issues with build workflows:
1. Check workflow logs for errors
2. Review troubleshooting section
3. Consult signing setup guide
4. Open issue with:
   - Workflow run URL
   - Error messages  
   - Steps to reproduce
   - Expected vs actual behavior
