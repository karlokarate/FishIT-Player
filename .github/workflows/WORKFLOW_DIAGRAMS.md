# Build System Workflow Diagram

## Overview of Build Workflows

```
┌─────────────────────────────────────────────────────────────────┐
│                     FishIT-Player Build System                  │
│                                                                 │
│  Three workflows for different use cases:                      │
│  1. Debug Build (Development)                                  │
│  2. Release Build (Production)                                 │
│  3. Generate Signing Key (One-time Setup)                      │
└─────────────────────────────────────────────────────────────────┘
```

## Workflow 1: Debug Build (Unsigned)

```
┌──────────────┐
│  Developer   │
│  triggers    │
│  workflow    │
└──────┬───────┘
       │
       ▼
┌─────────────────────────────────────────────┐
│     GitHub Actions: Debug Build             │
│  ┌───────────────────────────────────────┐  │
│  │ 1. Checkout code                      │  │
│  │ 2. Setup Java 21 + Gradle             │  │
│  │ 3. Setup Android SDK                  │  │
│  │ 4. Build debug APK (unsigned)         │  │
│  │    - No minification                  │  │
│  │    - Debug tools included             │  │
│  │    - Fast build                       │  │
│  │ 5. Generate checksums                 │  │
│  │ 6. Upload artifacts                   │  │
│  └───────────────────────────────────────┘  │
└──────────────┬──────────────────────────────┘
               │
               ▼
       ┌───────────────┐
       │   Artifacts   │
       │  ✓ APK file   │
       │  ✓ SHA256     │
       └───────────────┘
```

**Use case**: Development, testing, QA  
**Time**: ~5-10 minutes  
**Requirements**: None

---

## Workflow 2: Release Build (Signed)

```
┌─────────────────┐
│  Prerequisites  │
│  (one-time)     │
│                 │
│  ✓ Keystore     │
│  ✓ Secrets      │
└────────┬────────┘
         │
         ▼
┌──────────────┐
│  Developer   │
│  triggers    │
│  workflow    │
└──────┬───────┘
       │
       ▼
┌──────────────────────────────────────────────┐
│     GitHub Actions: Release Build            │
│  ┌────────────────────────────────────────┐  │
│  │ 1. Checkout code                       │  │
│  │ 2. Setup Java 21 + Gradle              │  │
│  │ 3. Setup Android SDK                   │  │
│  │ 4. Decode keystore from secrets        │  │
│  │    - KEYSTORE_BASE64 → release.keystore│  │
│  │ 5. Build release APK (signed)          │  │
│  │    - R8 minification                   │  │
│  │    - Resource shrinking                │  │
│  │    - Code obfuscation                  │  │
│  │    - Signed with release key           │  │
│  │ 6. Generate checksums                  │  │
│  │ 7. Upload artifacts + mappings         │  │
│  └────────────────────────────────────────┘  │
└──────────────┬───────────────────────────────┘
               │
               ▼
       ┌───────────────────┐
       │    Artifacts      │
       │  ✓ Signed APK     │
       │  ✓ SHA256         │
       │  ✓ R8 mappings    │
       └───────────────────┘
```

**Use case**: Production, Play Store  
**Time**: ~15-25 minutes  
**Requirements**: Signing key + GitHub secrets configured

---

## Workflow 3: Generate Signing Key (One-time Setup)

```
┌──────────────┐
│  Developer   │
│  triggers    │
│  workflow    │
│  (one-time)  │
└──────┬───────┘
       │
       ▼
┌─────────────────────────────────────────────┐
│  GitHub Actions: Generate Signing Key       │
│  ┌───────────────────────────────────────┐  │
│  │ 1. Setup Java (for keytool)          │  │
│  │ 2. Generate keystore                  │  │
│  │    - keytool -genkeypair              │  │
│  │    - RSA 2048-bit                     │  │
│  │    - 25 years validity                │  │
│  │ 3. Convert to base64                  │  │
│  │    - Single-line (for secrets)        │  │
│  │    - Multiline (for readability)      │  │
│  │ 4. Create setup instructions          │  │
│  │ 5. Upload artifacts                   │  │
│  └───────────────────────────────────────┘  │
└──────────────┬──────────────────────────────┘
               │
               ▼
       ┌────────────────────────────┐
       │      Artifacts             │
       │  ✓ Binary keystore         │
       │  ✓ Base64 (single-line)    │
       │  ✓ Base64 (multiline)      │
       │  ✓ Setup instructions      │
       └────────────┬───────────────┘
                    │
                    ▼
       ┌────────────────────────────┐
       │  Developer Actions:        │
       │  1. Download artifact      │
       │  2. Backup keystore file   │
       │  3. Configure secrets:     │
       │     - KEYSTORE_BASE64      │
       │     - KEYSTORE_PASSWORD    │
       │     - KEY_PASSWORD         │
       │     - KEY_ALIAS            │
       └────────────────────────────┘
```

**Use case**: Initial setup, key generation  
**Time**: ~1-2 minutes  
**Requirements**: None (creates the requirements!)

---

## Complete Setup Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    First-Time Setup Process                     │
└─────────────────────────────────────────────────────────────────┘

Step 1: Generate Key           Step 2: Configure Secrets
┌──────────────────┐           ┌────────────────────────┐
│ Run workflow:    │           │ GitHub Settings →      │
│ Generate         │  ───────► │ Secrets →              │
│ Signing Key      │           │ Add 4 secrets          │
└──────────────────┘           └────────┬───────────────┘
                                        │
                                        ▼
                               ┌────────────────────────┐
                               │ ✓ KEYSTORE_BASE64      │
                               │ ✓ KEYSTORE_PASSWORD    │
                               │ ✓ KEY_PASSWORD         │
                               │ ✓ KEY_ALIAS            │
                               └────────┬───────────────┘
                                        │
                                        ▼
                               ┌────────────────────────┐
                               │ Ready for             │
                               │ Release Builds!       │
                               └───────────────────────┘

After setup complete, you can run release builds anytime!
```

---

## Workflow Comparison

```
┌─────────────────┬──────────────────┬──────────────────┬──────────────┐
│ Feature         │ Debug Build      │ Release Build    │ Generate Key │
├─────────────────┼──────────────────┼──────────────────┼──────────────┤
│ Purpose         │ Development      │ Production       │ Setup        │
│ Signing         │ ❌ Unsigned      │ ✅ Signed        │ N/A          │
│ Minification    │ ❌ No            │ ✅ R8            │ N/A          │
│ Debug Tools     │ ✅ Included      │ ❌ Removed       │ N/A          │
│ Build Time      │ ~5-10 min        │ ~15-25 min       │ ~1-2 min     │
│ Prerequisites   │ None             │ Key + Secrets    │ None         │
│ Play Store      │ ❌ No            │ ✅ Yes           │ N/A          │
└─────────────────┴──────────────────┴──────────────────┴──────────────┘
```

---

## Security Flow

```
┌──────────────────────────────────────────────────────────────┐
│                   Security Best Practices                    │
└──────────────────────────────────────────────────────────────┘

Keystore Generation
        │
        ▼
    ┌────────────────┐
    │ Strong         │
    │ Passwords      │
    │ (12+ chars)    │
    └────┬───────────┘
         │
         ▼
    ┌────────────────┐        ┌─────────────────────┐
    │ Binary         │ ────►  │ Base64 Encoding     │
    │ Keystore       │        │ (for GitHub Secret) │
    └────┬───────────┘        └──────┬──────────────┘
         │                           │
         ▼                           ▼
    ┌────────────────┐        ┌─────────────────────┐
    │ Secure Backup  │        │ GitHub Secret       │
    │ - Password Mgr │        │ - KEYSTORE_BASE64   │
    │ - Encrypted    │        │ - Never in logs     │
    │ - Offline copy │        │ - Masked in UI      │
    └────────────────┘        └─────────────────────┘
         │                           │
         │                           │
         └───────────┬───────────────┘
                     │
                     ▼
            ┌─────────────────┐
            │ Release Builds  │
            │ (Secure)        │
            └─────────────────┘
```

---

## References

- [BUILD_GUIDE.md](../BUILD_GUIDE.md) - Quick reference
- [ANDROID_SIGNING_SETUP.md](../docs/ANDROID_SIGNING_SETUP.md) - Complete guide
- [README-BUILD.md](README-BUILD.md) - Workflow documentation
