# Build System Setup Checklist

Quick verification checklist to ensure the FishIT-Player build system is properly configured.

## ✅ Pre-flight Checklist

### For Debug Builds (No Setup Required)

- [x] Workflow file exists: `.github/workflows/debug-build.yml`
- [x] Workflow appears in Actions tab
- [ ] Test run: Can trigger workflow manually
- [ ] Test run: Workflow completes successfully
- [ ] Test run: Can download debug APK from artifacts

**Expected results:**
- Build time: 5-10 minutes
- Artifacts: Debug APK + SHA256 checksum
- APK is unsigned (can install for testing)

---

### For Release Builds (Requires Setup)

#### Step 1: Keystore Generation

- [x] Workflow file exists: `.github/workflows/generate-signing-key.yml`
- [ ] Run workflow with required inputs:
  - [ ] Key alias (e.g., `fishit-release-key`)
  - [ ] Validity (25 years recommended)
  - [ ] Distinguished name
  - [ ] Strong keystore password
  - [ ] Strong key password
- [ ] Download artifact
- [ ] Extract files from artifact
- [ ] Verify files exist:
  - [ ] `fishit-release.keystore` (binary file)
  - [ ] `keystore.base64.txt` (single-line text)
  - [ ] `keystore.base64.multiline.txt` (readable text)
  - [ ] `SETUP_INSTRUCTIONS.md` (guide)

**Expected results:**
- Workflow completes in 1-2 minutes
- Artifact size: ~2-5 KB
- Keystore valid for 25 years

#### Step 2: Secure Backup

- [ ] Store keystore file in password manager
- [ ] Create encrypted offline backup
- [ ] Document key information:
  - [ ] Key alias
  - [ ] Creation date
  - [ ] Keystore password (in password manager)
  - [ ] Key password (in password manager)
- [ ] Verify backup is accessible

**Important:** If keystore is lost, app cannot be updated in Play Store!

#### Step 3: Configure GitHub Secrets

- [ ] Navigate to: Repository Settings → Secrets and variables → Actions
- [ ] Add secret: `KEYSTORE_BASE64`
  - [ ] Name: Exactly `KEYSTORE_BASE64`
  - [ ] Value: Copy entire content of `keystore.base64.txt`
  - [ ] No extra whitespace or line breaks
- [ ] Add secret: `KEYSTORE_PASSWORD`
  - [ ] Name: Exactly `KEYSTORE_PASSWORD`
  - [ ] Value: Your keystore password
- [ ] Add secret: `KEY_PASSWORD`
  - [ ] Name: Exactly `KEY_PASSWORD`
  - [ ] Value: Your key password
- [ ] Add secret: `KEY_ALIAS`
  - [ ] Name: Exactly `KEY_ALIAS`
  - [ ] Value: Your key alias (e.g., `fishit-release-key`)
- [ ] Verify all 4 secrets are listed
- [ ] Verify secret names are case-sensitive matches

**Expected results:**
- All 4 secrets appear in secrets list
- Values are masked (not visible)

#### Step 4: Test Release Build

- [x] Workflow file exists: `.github/workflows/release-build.yml`
- [ ] Trigger release build workflow
- [ ] Provide version name (e.g., `v1.0.0`)
- [ ] Provide version code (e.g., `1`)
- [ ] Workflow completes successfully
- [ ] No "unsigned" warnings in logs
- [ ] Download signed APK from artifacts
- [ ] Verify APK is signed:
  ```bash
  # Run locally:
  keytool -list -printcert -jarfile app-release.apk
  # Should show certificate info
  ```

**Expected results:**
- Build time: 15-25 minutes
- Artifacts: Signed APK + SHA256 + R8 mappings
- APK certificate matches your keystore

---

## 🔧 Troubleshooting Checklist

### Issue: "Build will be unsigned" warning

**Check:**
- [ ] All 4 secrets are configured
- [ ] Secret names match exactly (case-sensitive)
- [ ] `KEYSTORE_BASE64` contains full base64 string (no truncation)
- [ ] No extra whitespace in secrets

**Fix:**
- Delete and re-add secrets
- Use single-line base64 from `keystore.base64.txt`
- Copy entire content (use "Download raw" button if viewing on GitHub)

---

### Issue: "Invalid Base64" error

**Check:**
- [ ] Used `keystore.base64.txt` (not multiline version)
- [ ] Copied complete content
- [ ] No whitespace at start/end

**Fix:**
- Re-download artifact
- Re-copy base64 string
- Use text editor to verify no extra characters

---

### Issue: Cannot install debug APK

**Check:**
- [ ] Uninstalled any signed versions first
- [ ] Device allows installation from unknown sources
- [ ] APK is for correct architecture (arm64-v8a or armeabi-v7a)

**Fix:**
```bash
# Uninstall existing app
adb uninstall com.fishit.player

# Install debug APK
adb install app-debug.apk
```

---

### Issue: APK won't install over existing app

**Cause:** Cannot install unsigned APK over signed APK (or vice versa)

**Fix:**
- Uninstall existing app first
- Use same signing key for all updates

---

### Issue: Lost keystore file

**⚠️ CRITICAL:**
- Cannot update app in Google Play Store
- Must publish as new app
- All existing users must download new app

**Prevention:**
- Maintain secure backups
- Store in password manager
- Keep offline encrypted copy
- Document all key information

---

## 📝 Verification Commands

### Verify keystore file

```bash
# List keystore contents
keytool -list -v -keystore fishit-release.keystore -storepass YOUR_PASSWORD

# Expected output:
# - Keystore type: PKCS12
# - Your alias: fishit-release-key
# - Certificate fingerprints
# - Valid from/until dates
```

### Verify base64 encoding

```bash
# Decode and compare
base64 -d keystore.base64.txt > test.keystore
diff fishit-release.keystore test.keystore

# Expected output:
# (no output = files are identical)
```

### Verify APK signature

```bash
# Check if APK is signed
jarsigner -verify -verbose -certs app-release.apk

# Expected output:
# - "jar verified"
# - Certificate chain
# - Signature algorithm: SHA256withRSA
```

### Verify APK certificate

```bash
# Extract certificate info
keytool -list -printcert -jarfile app-release.apk

# Expected output:
# - Signer #1 (your certificate)
# - Distinguished name matches your keystore
# - Valid from/until dates
```

---

## 🎯 Success Criteria

### Debug Builds

- ✅ Can trigger workflow manually
- ✅ Build completes in 5-10 minutes
- ✅ Debug APK can be downloaded
- ✅ APK installs on test devices
- ✅ Debug tools work (LeakCanary, Chucker)

### Release Builds

- ✅ All 4 secrets configured
- ✅ Can trigger workflow manually
- ✅ Build completes in 15-25 minutes
- ✅ No "unsigned" warnings in logs
- ✅ Signed APK can be downloaded
- ✅ Certificate matches keystore
- ✅ APK can be uploaded to Play Store

### Security

- ✅ Keystore backed up securely
- ✅ Passwords stored in password manager
- ✅ Keystore file NOT in git repository
- ✅ GitHub secrets configured
- ✅ Repository access limited to trusted team members

---

## 📞 Next Steps

### After successful setup:

1. **Test debug builds** - Verify workflow works
2. **Test release builds** - Verify signing works
3. **Document key details** - Save for future reference
4. **Share with team** - Only trusted members
5. **Set up CI/CD** - Automate builds on commits (optional)

### For production deployment:

1. Follow [Google Play Console setup](https://play.google.com/console)
2. Upload signed APK
3. Configure store listing
4. Set up Play App Signing (optional)
5. Release to internal testing first
6. Promote to production when ready

---

## 📚 Additional Resources

- [BUILD_GUIDE.md](BUILD_GUIDE.md) - Quick reference
- [ANDROID_SIGNING_SETUP.md](docs/ANDROID_SIGNING_SETUP.md) - Complete guide
- [WORKFLOW_DIAGRAMS.md](.github/workflows/WORKFLOW_DIAGRAMS.md) - Visual flow
- [README-BUILD.md](.github/workflows/README-BUILD.md) - Workflow docs

---

**Last Updated:** 2026-01-24  
**Version:** 1.0
