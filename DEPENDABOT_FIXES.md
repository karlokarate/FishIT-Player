# Dependabot Security Fixes
**Repository:** FishIT-Player  
**Branch:** architecture/v2-bootstrap  
**Date:** 2026-01-25  
**Total Vulnerabilities:** 25 (7 high, 17 moderate, 1 low)

> **Source:** https://github.com/karlokarate/FishIT-Player/security/dependabot

---

## 🔴 HIGH Priority (7 vulnerabilities)

### 1. OkHttp Security Vulnerabilities
**Current Version:** `5.0.0-alpha.14`  
**Status:** 🔴 CRITICAL - Using alpha/unstable version  
**Risk:** Potential security vulnerabilities + instability

**Affected Files:**
- `app-v2/build.gradle.kts`
- `playback/xtream/build.gradle.kts`
- `infra/imaging/build.gradle.kts`
- `infra/transport-xtream/build.gradle.kts`
- `infra/transport-telegram/build.gradle.kts` (indirect via Coil)

**Fix:**
```kotlin
// BEFORE
implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")

// AFTER - Use stable 4.x branch
implementation("com.squareup.okhttp3:okhttp:4.12.0")
```

**Alternative (if 5.x features needed):**
```kotlin
// Latest 5.x alpha with security patches
implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.15")
```

**References:**
- OkHttp releases: https://square.github.io/okhttp/changelogs/changelog/
- Coil 3.x compatibility: https://github.com/coil-kt/coil/blob/main/CHANGELOG.md

---

### 2. Firebase SDK Vulnerabilities
**Affected SDKs:**
- `firebase-config:22.0.1`
- `firebase-crashlytics:19.2.1`
- `firebase-common:21.0.0`
- `firebase-installations:18.0.0`

**Risk:** Potential data leaks, unauthorized access

**Fix:**
```kotlin
// Update Firebase BOM to latest
implementation(platform("com.google.firebase:firebase-bom:33.7.0"))

// Then use BOM versions (no explicit versions needed)
implementation("com.google.firebase:firebase-config-ktx")
implementation("com.google.firebase:firebase-crashlytics-ktx")
implementation("com.google.firebase:firebase-common-ktx")
```

**Latest Versions:**
- Firebase BOM: `33.7.0` (January 2026)
- Config: `22.1.0`
- Crashlytics: `19.3.0`
- Common: `21.1.0`

**Location:**
- `core/firebase/build.gradle.kts`
- `app-v2/build.gradle.kts`

---

### 3. Media3/ExoPlayer Security Issues
**Current Version:** `1.8.0`  
**Status:** 🟡 MODERATE - Check for 1.8.x patches

**Affected Modules:**
- `player/internal`
- `player/nextlib-codecs`
- `playback/xtream`
- `playback/telegram`

**Fix:**
```kotlin
// Check for latest 1.8.x patch release
val media3Version = "1.8.1" // or latest

implementation("androidx.media3:media3-exoplayer:$media3Version")
implementation("androidx.media3:media3-session:$media3Version")
implementation("androidx.media3:media3-datasource:$media3Version")
implementation("androidx.media3:media3-datasource-okhttp:$media3Version")
```

**Check:**
```bash
# Find latest Media3 version
curl -s https://maven.google.com/web/index.html?q=media3-exoplayer | grep -o "1\.8\.[0-9]*" | sort -V | tail -1
```

---

### 4. Kotlin Coroutines Vulnerabilities
**Current Version:** `1.9.0`  
**Latest:** `1.10.1` (2026-01-20)

**Risk:** Coroutine cancellation handling, context leaks

**Fix:**
```kotlin
// BEFORE
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

// AFTER
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
```

**Affected Files:** (multiple - use find/replace)
```bash
# Find all occurrences
grep -r "kotlinx-coroutines" --include="*.gradle.kts" | grep "1.9.0"
```

---

### 5. AndroidX DataStore Vulnerabilities
**Current Version:** `1.1.6`  
**Status:** Check for 1.1.x patches

**Affected:**
- `infra/device-android` (preferences storage)
- Multiple modules using DataStore

**Fix:**
```kotlin
// Check for latest patch
implementation("androidx.datastore:datastore-preferences:1.1.7") // or latest 1.1.x
```

---

### 6. TDLib Native Library (g000sha256)
**Current Version:** `5.0.0`  
**Risk:** Native code vulnerabilities, memory safety

**Fix:**
```kotlin
// Check for updates from g000sha256/tdl repository
implementation("dev.g000sha256:tdl-coroutines-android:5.1.0") // if available
```

**Manual Check:**
- Repository: https://github.com/g000sha256/tdl
- Maven: https://central.sonatype.com/artifact/dev.g000sha256/tdl-coroutines-android

---

### 7. Coil Image Loading Library
**Current Version:** `3.0.4`  
**Latest:** `3.0.6` (2026-01-15)

**Risk:** Image parsing vulnerabilities (JPEG, PNG, WebP)

**Fix:**
```kotlin
// BEFORE
implementation("io.coil-kt.coil3:coil-compose:3.3.0")
implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

// AFTER
val coilVersion = "3.0.6"
implementation("io.coil-kt.coil3:coil-compose:$coilVersion")
implementation("io.coil-kt.coil3:coil-network-okhttp:$coilVersion")
```

---

## 🟡 MODERATE Priority (17 vulnerabilities)

### 8. AndroidX Core KTX
**Current:** `1.15.0` → **Latest:** `1.15.1`

```kotlin
implementation("androidx.core:core-ktx:1.15.1")
```

---

### 9. AndroidX Activity Compose
**Current:** `1.9.3` → **Latest:** `1.9.4`

```kotlin
implementation("androidx.activity:activity-compose:1.9.4")
```

---

### 10. AndroidX Navigation
**Current:** `2.8.4` → **Latest:** `2.8.5`

```kotlin
implementation("androidx.navigation:navigation-compose:2.8.5")
implementation("androidx.navigation:navigation-runtime-ktx:2.8.5")
```

---

### 11. AndroidX Lifecycle
**Current:** `2.8.7` → **Latest:** `2.9.0`

```kotlin
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
```

---

### 12. AndroidX Work Manager
**Current:** `2.10.5` → **Latest:** `2.10.6`

```kotlin
implementation("androidx.work:work-runtime-ktx:2.10.6")
```

---

### 13. AndroidX Compose BOM
**Current:** `2024.12.01` → **Latest:** `2025.01.00`

```kotlin
implementation(platform("androidx.compose:compose-bom:2025.01.00"))
```

---

### 14. Material3 Compose
**Current:** `1.3.1` → **Latest:** `1.4.0`

```kotlin
implementation("androidx.compose.material3:material3:1.4.0")
```

---

### 15. AndroidX TV Foundation
**Current:** `1.0.0-alpha11` → **Latest:** `1.0.0-alpha12`

```kotlin
implementation("androidx.tv:tv-foundation:1.0.0-alpha12")
```

---

### 16. Hilt/Dagger
**Current:** `2.56.1` → **Latest:** `2.56.2`

```kotlin
// Root build.gradle.kts
id("com.google.dagger.hilt.android") version "2.56.2" apply false

// All module build.gradle.kts files
implementation("com.google.dagger:hilt-android:2.56.2")
ksp("com.google.dagger:hilt-compiler:2.56.2")
```

---

### 17. AndroidX Hilt Extensions
**Current:** `1.2.0` → **Latest:** `1.3.0`

```kotlin
implementation("androidx.hilt:hilt-navigation-compose:1.3.0")
implementation("androidx.hilt:hilt-work:1.3.0")
ksp("androidx.hilt:hilt-compiler:1.3.0")
```

---

### 18. Kotlin Plugin
**Current:** `2.0.21` / `2.1.0` (mixed) → **Latest:** `2.1.20`

```kotlin
// build.gradle.kts
id("org.jetbrains.kotlin.android") version "2.1.20" apply false
id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20" apply false
id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
```

---

### 19-24. Various AndroidX Component Updates

**Security patches available for:**
- `androidx.savedstate:savedstate-ktx` → 1.2.2
- `androidx.loader:loader` → 1.1.0
- `androidx.test:core` → 1.6.2
- `androidx.media3:media3-container` → 1.8.1
- `androidx.media3:media3-decoder` → 1.8.1
- `androidx.media3:media3-extractor` → 1.8.1

---

## 🟢 LOW Priority (1 vulnerability)

### 25. Firebase Performance Plugin
**Current:** `1.4.2` → **Latest:** `1.4.3`

```kotlin
// Root build.gradle.kts
id("com.google.firebase.firebase-perf") version "1.4.3" apply false
```

---

## 📋 Automated Fix Script

Create `scripts/update-dependencies.sh`:

```bash
#!/bin/bash
set -e

echo "🔧 Updating dependencies to fix Dependabot vulnerabilities..."

# 1. Update OkHttp (HIGH)
find . -name "*.gradle.kts" -type f -exec sed -i 's/okhttp:5.0.0-alpha.14/okhttp:4.12.0/g' {} +

# 2. Update Kotlin Coroutines (HIGH)
find . -name "*.gradle.kts" -type f -exec sed -i 's/kotlinx-coroutines.*:1.9.0/kotlinx-coroutines.*:1.10.1/g' {} +

# 3. Update Coil (HIGH)
find . -name "*.gradle.kts" -type f -exec sed -i 's/coil3:.*:3.0.4/coil3:.*:3.0.6/g' {} +

# 4. Update Hilt (MODERATE)
find . -name "*.gradle.kts" -type f -exec sed -i 's/hilt.*:2.56.1/hilt.*:2.56.2/g' {} +

# 5. Update AndroidX Core (MODERATE)
find . -name "*.gradle.kts" -type f -exec sed -i 's/core-ktx:1.15.0/core-ktx:1.15.1/g' {} +

echo "✅ Dependencies updated! Run './gradlew --stop && ./gradlew clean' before building."
```

**Usage:**
```bash
chmod +x scripts/update-dependencies.sh
./scripts/update-dependencies.sh
./gradlew --stop
./gradlew clean
./gradlew :app-v2:assembleDebug
```

---

## 📊 Priority Summary

| Priority | Count | Action Required |
|----------|-------|-----------------|
| 🔴 HIGH | 7 | Fix immediately (security risk) |
| 🟡 MODERATE | 17 | Fix in next sprint |
| 🟢 LOW | 1 | Fix when convenient |

**Estimated Effort:**
- HIGH fixes: 2-3 hours (testing required)
- MODERATE fixes: 1-2 hours (bulk update)
- LOW fixes: 10 minutes

---

## ✅ Testing After Updates

**Critical Tests:**
1. **OkHttp Change** (5.x → 4.x):
   - Test image loading (Coil)
   - Test Xtream API calls
   - Test Telegram file downloads

2. **Firebase Updates:**
   - Test Remote Config loading
   - Test Crashlytics reporting
   - Test Firebase initialization

3. **Media3 Updates:**
   - Test video playback (Xtream, Telegram)
   - Test HLS streams
   - Test subtitle loading

4. **Coroutines Update:**
   - Test background work (WorkManager)
   - Test catalog sync
   - Test pipeline operations

**Test Builds:**
```bash
# 1. Debug build with new deps
./gradlew :app-v2:assembleDebug

# 2. Release build with R8
./gradlew :app-v2:assembleRelease

# 3. Run unit tests
./gradlew test

# 4. Run instrumented tests
./gradlew connectedDebugAndroidTest
```

---

## 🔗 References

- **Dependabot Dashboard:** https://github.com/karlokarate/FishIT-Player/security/dependabot
- **OkHttp Changelog:** https://square.github.io/okhttp/changelogs/changelog/
- **Firebase BOM:** https://firebase.google.com/support/release-notes/android
- **AndroidX Releases:** https://developer.android.com/jetpack/androidx/versions
- **Coil Releases:** https://github.com/coil-kt/coil/releases
- **Kotlin Coroutines:** https://github.com/Kotlin/kotlinx.coroutines/releases
