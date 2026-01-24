# Android App Signing Setup Guide

This document explains how to set up Android app signing for the FishIT-Player project using GitHub Actions workflows.

## Table of Contents

1. [Overview](#overview)
2. [Quick Start](#quick-start)
3. [Generate New Signing Key](#generate-new-signing-key)
4. [Configure GitHub Secrets](#configure-github-secrets)
5. [Build Workflows](#build-workflows)
6. [Security Best Practices](#security-best-practices)
7. [Troubleshooting](#troubleshooting)

## Overview

The project includes three build workflows:

1. **Debug Build** (`debug-build.yml`) - Unsigned APKs for development/testing
2. **Release Build** (`release-build.yml`) - Signed APKs for production/distribution
3. **Generate Signing Key** (`generate-signing-key.yml`) - Creates new signing keystores

### Signing Key Formats

The workflows support two methods for providing the signing keystore:

1. **Base64-encoded keystore** (Recommended for CI/CD)
   - Stored as a GitHub secret (`KEYSTORE_BASE64`)
   - Automatically decoded during build
   - No file management needed

2. **Keystore file path** (Legacy support)
   - Provide file path via `MYAPP_UPLOAD_STORE_FILE` environment variable
   - Useful for local builds

## Quick Start

### For Development (Unsigned Debug Builds)

No setup required! Just run the **Debug Build** workflow:

1. Go to: Actions → Debug Build – Unsigned APK (Development)
2. Click "Run workflow"
3. Fill in version information
4. Click "Run workflow"
5. Download the APK from artifacts

### For Production (Signed Release Builds)

Requires one-time signing key setup. Follow [Generate New Signing Key](#generate-new-signing-key).

## Generate New Signing Key

### Step 1: Run the Generator Workflow

1. Go to: **Actions** → **Generate Signing Key**
2. Click **"Run workflow"**
3. Configure the inputs:

   | Input | Description | Example |
   |-------|-------------|---------|
   | **key_alias** | Unique identifier for the key | `fishit-release-key` |
   | **key_validity_years** | How long the key is valid | `25` (recommended) |
   | **distinguished_name** | Your organization details | `CN=FishIT Player, OU=Development, O=FishIT, L=Berlin, ST=Berlin, C=DE` |
   | **keystore_password** | Password to protect the keystore | (strong password) |
   | **key_password** | Password to protect the signing key | (strong password) |

4. Click **"Run workflow"**
5. Wait for the workflow to complete (1-2 minutes)

### Step 2: Download the Artifact

1. Click on the completed workflow run
2. Scroll to **"Artifacts"** section at the bottom
3. Download `signing-keystore-{number}.zip`
4. Extract the zip file

The artifact contains:
- `fishit-release.keystore` - Binary keystore file (backup this!)
- `keystore.base64.txt` - Single-line base64 (for GitHub secrets)
- `keystore.base64.multiline.txt` - Multiline base64 (easier to read)
- `SETUP_INSTRUCTIONS.md` - Detailed instructions

### Step 3: Secure Backup

⚠️ **CRITICAL**: Back up the keystore file immediately!

- Store in password manager (recommended)
- Store in encrypted cloud storage
- Store in secure offline location
- **If you lose the keystore, you cannot update your app in Google Play Store**

## Configure GitHub Secrets

Set up the following repository secrets to enable signed builds:

### Navigate to Secrets

1. Go to your repository on GitHub
2. Click **Settings**
3. Click **Secrets and variables** → **Actions**
4. Click **New repository secret**

### Required Secrets

Create these four secrets:

#### 1. KEYSTORE_BASE64

- **Name**: `KEYSTORE_BASE64`
- **Value**: Copy the entire content of `keystore.base64.txt` (single line)
- This is the base64-encoded keystore that will be decoded during builds

#### 2. KEYSTORE_PASSWORD

- **Name**: `KEYSTORE_PASSWORD`
- **Value**: The keystore password you entered when generating the key
- This protects access to the keystore file

#### 3. KEY_PASSWORD

- **Name**: `KEY_PASSWORD`
- **Value**: The key password you entered when generating the key
- This protects the specific signing key within the keystore

#### 4. KEY_ALIAS

- **Name**: `KEY_ALIAS`
- **Value**: The key alias you entered when generating the key (e.g., `fishit-release-key`)
- This identifies which key to use from the keystore

### Alternative Secret Names

The release workflow also supports these legacy secret names:
- `ANDROID_SIGNING_KEYSTORE_BASE64` (alternative to `KEYSTORE_BASE64`)
- `ANDROID_SIGNING_KEYSTORE_PASSWORD` (alternative to `KEYSTORE_PASSWORD`)
- `ANDROID_SIGNING_KEY_ALIAS` (alternative to `KEY_ALIAS`)
- `ANDROID_SIGNING_KEY_PASSWORD` (alternative to `KEY_PASSWORD`)

## Build Workflows

### Debug Build (Unsigned)

**Purpose**: Development and testing

**When to use**:
- Internal testing
- Development builds
- QA testing
- Alpha/beta testing (if signing not required)

**Features**:
- ✅ No signing required
- ✅ Faster builds (no ProGuard/R8)
- ✅ All debug tools included (LeakCanary, Chucker)
- ✅ Detailed logging
- ❌ Cannot be published to Google Play Store
- ❌ Cannot be installed over signed builds

**How to run**:
1. Go to: Actions → Debug Build – Unsigned APK (Development)
2. Click "Run workflow"
3. Configure:
   - Version name (e.g., `2.0.0-debug`)
   - Version code (integer)
   - ABI target (arm64-v8a, armeabi-v7a, or both)
4. Click "Run workflow"
5. Download APK from artifacts

### Release Build (Signed)

**Purpose**: Production and distribution

**When to use**:
- Google Play Store submissions
- Production releases
- Official distribution channels

**Features**:
- ✅ Signed with your release key
- ✅ Code minification (ProGuard/R8)
- ✅ Resource shrinking
- ✅ Optimized for production
- ✅ Can be published to Play Store
- ❌ Larger APK size (includes multiple ABIs)

**How to run**:
1. Ensure [GitHub secrets are configured](#configure-github-secrets)
2. Go to: Actions → Release Build – APK (arm64-v8a & armeabi-v7a)
3. Click "Run workflow"
4. Configure:
   - Version name (e.g., `v1.0.0`)
   - Version code (integer)
5. Click "Run workflow"
6. Download signed APK from artifacts

### Workflow Comparison

| Feature | Debug Build | Release Build |
|---------|-------------|---------------|
| Signing | ❌ Unsigned | ✅ Signed |
| Code Minification | ❌ Disabled | ✅ Enabled (R8) |
| Resource Shrinking | ❌ Disabled | ✅ Enabled |
| Debug Tools | ✅ Included | ❌ Removed |
| Build Time | ~5-10 min | ~15-25 min |
| APK Size | Larger | Smaller |
| Play Store | ❌ No | ✅ Yes |

## Security Best Practices

### Keystore Management

1. **Never commit keystore files to git**
   ```bash
   # Add to .gitignore
   *.keystore
   *.jks
   release.keystore
   ```

2. **Keep backups in multiple secure locations**
   - Password manager (1Password, LastPass, etc.)
   - Encrypted cloud storage (Google Drive with encryption, etc.)
   - Offline backup (encrypted USB drive in safe)

3. **Use strong, unique passwords**
   - Minimum 12 characters
   - Mix of uppercase, lowercase, numbers, symbols
   - Different passwords for keystore and key
   - Never reuse passwords from other services

4. **Document the key information**
   - Key alias
   - Creation date
   - Expiration date
   - Distinguished name
   - Store this documentation with the backup

### GitHub Secrets Management

1. **Limit repository access**
   - Only give repository access to trusted team members
   - Use GitHub's organization settings to control access
   - Review access periodically

2. **Use environment secrets for extra protection**
   - Create separate environments (production, staging)
   - Require manual approval for production deployments
   - Set up environment-specific secrets

3. **Rotate secrets periodically**
   - Review secrets quarterly
   - Generate new keystores for major versions (if starting fresh)
   - Update secrets if team members leave

4. **Monitor secret usage**
   - Review workflow logs for unexpected secret access
   - Set up notifications for workflow failures
   - Audit repository access logs

### Build Security

1. **Verify workflow runs**
   - Check workflow logs for suspicious activity
   - Verify artifact checksums
   - Test built APKs before distribution

2. **Protect main branch**
   - Require pull request reviews
   - Enable branch protection rules
   - Restrict who can trigger workflows

3. **Use workflow concurrency controls**
   - Prevent multiple simultaneous release builds
   - Cancel outdated workflow runs
   - Review concurrency settings in workflow files

## Troubleshooting

### "Build will be unsigned" Warning

**Symptom**: Release workflow completes but APK is unsigned

**Causes**:
- GitHub secrets not configured
- Secret names incorrect
- Base64 encoding corrupted

**Solutions**:
1. Verify secrets exist: Settings → Secrets and variables → Actions
2. Check secret names match exactly (case-sensitive)
3. Regenerate base64 encoding if corrupted:
   ```bash
   base64 -w 0 your-keystore.keystore > keystore.base64.txt
   ```

### "Invalid Base64 in keystore secret"

**Symptom**: Workflow fails during keystore decode step

**Causes**:
- Whitespace in base64 string
- Incomplete base64 string
- Wrong secret used

**Solutions**:
1. Use the single-line version from `keystore.base64.txt`
2. Copy the ENTIRE content (check for truncation)
3. Verify no extra whitespace at start/end
4. Re-generate if necessary

### "Permission Denied" on Linux/Mac

**Symptom**: Cannot execute scripts or keytool commands

**Solution**:
```bash
chmod +x scripts/*.sh
```

### "Keystore was tampered with, or password was incorrect"

**Symptom**: Build fails during signing

**Causes**:
- Wrong keystore password
- Wrong key password
- Corrupted keystore file

**Solutions**:
1. Verify passwords match what you used during generation
2. Re-download keystore from artifact
3. Re-generate keystore if corrupted

### APK Size Too Large

**Symptom**: Release APK is larger than expected

**Possible causes**:
- Includes multiple ABIs (both arm64-v8a and armeabi-v7a)
- Resource shrinking not working
- Debug symbols included

**Solutions**:
- This is expected for universal APKs
- Google Play Store will automatically create optimized APKs per device
- Consider App Bundle (.aab) format for smaller downloads

### Cannot Update App in Play Store

**Symptom**: "Upload failed: You need to use a different package name"

**Cause**: Using a different signing key than the original upload

**Solutions**:
- Use the SAME keystore for all updates
- If keystore was lost, you must publish as a new app
- This is why backup is critical!

## Additional Resources

- [Android App Signing Documentation](https://developer.android.com/studio/publish/app-signing)
- [GitHub Encrypted Secrets](https://docs.github.com/en/actions/security-guides/encrypted-secrets)
- [Play Store Publishing Overview](https://developer.android.com/distribute/console)
- [Key and Keystore Management](https://developer.android.com/studio/publish/app-signing#secure-key)

## Support

For issues specific to the FishIT-Player build system:
1. Check workflow logs for detailed error messages
2. Review this documentation
3. Open an issue in the repository with:
   - Workflow run URL
   - Error messages
   - Steps to reproduce
   - What you expected to happen
