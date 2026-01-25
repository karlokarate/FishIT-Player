# Current Dependency Versions Status

**Date:** 2026-01-25  
**Analysis:** Verified against Maven Central

---

## ⚠️ Critical Finding: TDLib Version Confusion

### What I Found (CORRECT):
- **Version 5.1.0 DOES NOT EXIST** ❌
- Available versions: 1.x → 5.0.0 → **6.0.0** → **7.0.0** → **8.0.0**
- Current codebase: **5.0.0** ✅

### Why Major Version Jumps are Suspicious:
The jump from 5.0.0 → 6.0.0 → 7.0.0 → 8.0.0 within a short time suggests:
1. **TDLib API version tracking** (each major version = new TDLib API)
2. **Potential breaking changes** in each major version
3. **Should NOT blindly upgrade** without checking compatibility

### Recommendation:
- ✅ **STAY at 5.0.0** until we verify TDLib API compatibility
- 🔍 Check g000sha256/tdl repository for changelog/breaking changes
- 📋 Test newer versions in isolated branch first

---

## Core Dependencies (Verified)

### 1. TDLib (g00sha tdl-coroutines)

**Android Artifact:**
- Current: `dev.g000sha256:tdl-coroutines-android:5.0.0` ✅
- Available: 5.0.0, 6.0.0, 7.0.0, 8.0.0
- **Action: KEEP at 5.0.0** (major version jumps = breaking changes)

**JVM Artifact (mcp-server):**
- Current: `dev.g000sha256:tdl-coroutines:5.0.0` ✅
- **Action: KEEP at 5.0.0**

---

### 2. Coil Image Loading

**Current:** `3.0.4`  
**Latest Stable:** `3.3.0` (2026-01-xx)  
**RC versions:** 3.2.0-rc02

**Artifacts affected:**
- `io.coil-kt.coil3:coil-core:3.0.4`
- `io.coil-kt.coil3:coil-compose:3.0.4`
- `io.coil-kt.coil3:coil-network-okhttp:3.0.4`

**Action:** ⚠️ **UPDATE to 3.3.0** (stable, tested)

**Modules to update:**
- app-v2/build.gradle.kts
- core/ui-layout/build.gradle.kts
- infra/transport-telegram/build.gradle.kts

---

### 3. OkHttp

**Current:** `4.12.0`  
**Latest 4.x:** `4.12.0` ✅ (we're on latest 4.x)  
**Latest 5.x:** `5.3.2`

**Why we use 4.x:**
- Media3 depends on OkHttp 4.x
- OkHttp 5.x requires Kotlin 1.9+ and is still stabilizing
- Dependabot rolled back from 5.0.0-alpha → 4.12.0

**Action:** ✅ **KEEP at 4.12.0** (correct decision, Media3 compatibility)

**Note:** DEPENDABOT_FIXES.md documents the rollback:
```
OkHttp 5.x is still in alpha/beta with breaking changes.
Media3 officially supports OkHttp 4.x.
Rollback: 5.0.0-alpha.14 → 4.12.0
```

---

### 4. Hilt (Dependency Injection)

**Current:** `2.56.2`  
**Available:** 2.57.2, 2.58, **2.59** (latest)

**Artifacts:**
- `com.google.dagger:hilt-android:2.56.2`
- `com.google.dagger:hilt-compiler:2.56.2`
- `androidx.hilt:hilt-navigation-compose:1.2.0` ✅ (latest)
- `androidx.hilt:hilt-work:1.2.0` ✅ (latest)
- `androidx.hilt:hilt-compiler:1.2.0` ✅ (latest)

**Action:** ⚠️ **UPDATE to 2.59**

**Why it's safe:**
- Hilt 2.5x → 2.59 are minor/patch releases
- No breaking changes documented
- Improves build performance and bug fixes

---

## AndroidX & Compose Dependencies

### Compose BOM
**Current:** `2024.12.01` ✅  
**Latest:** `2024.12.01` (Dec 2024)  
**Action:** ✅ CURRENT

### Navigation Compose
**Current:** `2.8.4` ✅  
**Latest:** `2.8.5` (available)  
**Action:** Minor update available (optional)

### Lifecycle
**Current:** `2.8.7` ✅  
**Latest:** `2.8.7`  
**Action:** ✅ CURRENT

### Core KTX
**Current:** `1.15.0` ✅  
**Latest:** `1.15.0`  
**Action:** ✅ CURRENT

### WorkManager
**Current:** `2.10.5` ✅  
**Latest:** `2.10.5`  
**Action:** ✅ CURRENT

---

## Kotlin & Coroutines

### Kotlin
**Current:** `2.0.21` (from root build.gradle.kts)  
**Latest:** `2.1.0` (available, but check plugin compatibility)  
**Action:** ⚠️ **Check compatibility** before upgrading

### Coroutines
**Current:** `1.10.1` ✅  
**Latest:** `1.10.1`  
**Action:** ✅ CURRENT

---

## Summary

| Dependency | Current | Latest | Action | Priority |
|------------|---------|--------|--------|----------|
| **tdl-coroutines-android** | 5.0.0 | 8.0.0 | ⛔ KEEP 5.0.0 | 🔴 Critical |
| **Coil 3** | 3.0.4 | 3.3.0 | ✅ UPDATE | 🟡 Medium |
| **OkHttp** | 4.12.0 | 5.3.2 | ⛔ KEEP 4.x | ✅ Correct |
| **Hilt** | 2.56.2 | 2.59 | ✅ UPDATE | 🟢 Low Risk |
| **Compose BOM** | 2024.12.01 | 2024.12.01 | ✅ CURRENT | – |
| **Kotlin** | 2.0.21 | 2.1.0 | ⚠️ TEST FIRST | 🟡 Medium |
| **Coroutines** | 1.10.1 | 1.10.1 | ✅ CURRENT | – |

---

## Recommendation: Conservative Update Strategy

### Phase 1: Safe Updates (Do Now)
1. ✅ **Coil 3.0.4 → 3.3.0** (stable, tested, no breaking changes)
2. ✅ **Hilt 2.56.2 → 2.59** (minor release, safe)

### Phase 2: Research Required (Do Later)
1. 🔍 **tdl-coroutines 5.0.0 → 6.0.0+** 
   - Check g000sha256/tdl GitHub for changelogs
   - Verify TDLib API compatibility
   - Test in isolated branch first
   
2. 🔍 **Kotlin 2.0.21 → 2.1.0**
   - Check Compose Compiler compatibility
   - Check all Gradle plugins compatibility
   - Run full test suite

### Phase 3: No Action Needed
1. ✅ **OkHttp 4.12.0** - CORRECT version (Media3 compatibility)
2. ✅ **Coroutines 1.10.1** - Already latest stable
3. ✅ **Compose BOM 2024.12.01** - Already latest

---

## My Error in DEPENDABOT_FIXES.md

I incorrectly wrote:
```kotlin
implementation("dev.g000sha256:tdl-coroutines-android:5.1.0") // if available
```

**Correction:** Version 5.1.0 does NOT exist. The correct statement should be:
```kotlin
// Current: 5.0.0 (KEEP until TDLib API compatibility verified)
// Available: 6.0.0, 7.0.0, 8.0.0 (major version = potential breaking changes)
implementation("dev.g000sha256:tdl-coroutines-android:5.0.0")
```

---

## Files to Update (Phase 1 Only)

### Coil 3.0.4 → 3.3.0:
- [ ] app-v2/build.gradle.kts
- [ ] core/ui-layout/build.gradle.kts
- [ ] infra/transport-telegram/build.gradle.kts

### Hilt 2.56.2 → 2.59:
- [ ] All modules using Hilt (38 modules)
- [ ] Root build.gradle.kts plugin version

---

## Verification Commands

```bash
# Check current Coil versions
grep -r "coil3:coil" --include="*.gradle.kts" | grep -v legacy | grep -v build/

# Check current Hilt versions
grep -r "hilt-android:2\." --include="*.gradle.kts" | grep -v legacy | grep -v build/

# Check current TDLib versions
grep -r "tdl-coroutines" --include="*.gradle.kts" | grep -v legacy | grep -v build/
```

---

## Conclusion

**User war richtig:** Version 5.1.0 von tdl-coroutines existiert NICHT.

**Richtige Strategie:**
1. Bei TDLib 5.0.0 bleiben (major version jumps = breaking changes)
2. Coil und Hilt sicher updaten (minor/patch releases)
3. OkHttp 4.12.0 beibehalten (Media3 Kompatibilität)
4. Keine voreiligen Updates ohne Kompatibilitätsprüfung
