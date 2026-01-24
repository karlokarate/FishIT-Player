# Build System Quick Reference

Quick guide for building FishIT-Player APKs using GitHub Actions workflows.

## 🚀 Quick Start

### For Development (Unsigned Debug Build)

**No setup required!** Just run the workflow:

1. Go to: [Actions](../../actions) → **Debug Build – Unsigned APK (Development)**
2. Click **"Run workflow"**
3. Fill in:
   - Version name: `2.0.0-debug`
   - Version code: `1`
   - ABI: `arm64-v8a` (or `both` for multiple)
4. Click **"Run workflow"**
5. Wait 5-10 minutes
6. Download APK from artifacts

✅ **Use for**: Testing, QA, internal distribution  
❌ **Cannot**: Publish to Play Store, install over signed builds

---

### For Production (Signed Release Build)

**Requires one-time setup.** See [Full Setup Guide](#full-setup-guide) below.

1. Complete signing key setup (one-time)
2. Go to: [Actions](../../actions) → **Release Build – APK**
3. Click **"Run workflow"**
4. Fill in version info
5. Download signed APK

✅ **Use for**: Play Store, production distribution  
⚠️ **Requires**: Signing key + GitHub secrets configured

---

## 🔑 One-Time Signing Key Setup

### Step 1: Generate Key (5 minutes)

1. [Actions](../../actions) → **Generate Signing Key** → Run workflow
2. Fill in:
   - Key alias: `fishit-release-key`
   - Validity: `25` years
   - Distinguished name: `CN=FishIT Player, OU=Dev, O=FishIT, L=Berlin, ST=Berlin, C=DE`
   - Keystore password: (create strong password)
   - Key password: (create strong password)
3. Download artifact when complete
4. **BACKUP THE KEYSTORE FILE** 🔒

### Step 2: Configure Secrets (3 minutes)

1. [Settings](../../settings) → **Secrets and variables** → **Actions**
2. Add these secrets:

| Secret Name | Get Value From |
|-------------|----------------|
| `KEYSTORE_BASE64` | Copy content of `keystore.base64.txt` |
| `KEYSTORE_PASSWORD` | Your keystore password |
| `KEY_PASSWORD` | Your key password |
| `KEY_ALIAS` | `fishit-release-key` (or your alias) |

### Step 3: Build Release (15 minutes)

Now you can run signed release builds! See [For Production](#for-production-signed-release-build) above.

---

## 📚 Full Documentation

| Document | Description |
|----------|-------------|
| [Android Signing Setup](docs/ANDROID_SIGNING_SETUP.md) | Complete signing key guide |
| [Workflow README](.github/workflows/README-BUILD.md) | Detailed workflow documentation |
| [Troubleshooting](docs/ANDROID_SIGNING_SETUP.md#troubleshooting) | Common issues and solutions |

---

## ⚡ Workflow Comparison

| | Debug | Release |
|-|-------|---------|
| **Signing** | No | Yes |
| **Setup Required** | No | Yes (one-time) |
| **Build Time** | ~5-10 min | ~15-25 min |
| **Debug Tools** | Yes | No |
| **Play Store** | No | Yes |

---

## 🆘 Common Issues

### "Build will be unsigned" Warning

**Fix**: Configure GitHub secrets (see [Step 2](#step-2-configure-secrets-3-minutes))

### Cannot Install Debug APK

**Fix**: Uninstall any signed versions first, then install debug APK

### Lost Keystore File

**⚠️ CRITICAL**: If you lose the keystore:
- You cannot update your app in Play Store
- You must publish as a new app
- **Always keep secure backups!**

---

## 🔒 Security Reminders

- ✅ Keystore files already in `.gitignore` (never commit)
- ✅ Passwords masked in workflow logs
- ✅ Base64 encoding for secure secret storage
- ⚠️ Backup keystore in password manager
- ⚠️ Use strong, unique passwords
- ⚠️ Limit repository access to trusted team

---

## 📞 Need Help?

1. Check [Full Documentation](#full-documentation)
2. Review workflow logs for error messages
3. Open issue with details

---

**Last Updated**: 2026-01-24  
**Workflows**: debug-build.yml, release-build.yml, generate-signing-key.yml
