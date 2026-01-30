# DEPRECATED METHODS REMOVAL - PLATIN IMPLEMENTATION PLAN

**Ziel:** Ersetze alle deprecated Flow<List<T>> durch PagingData<T> für 50MB Memory & 70% GC Reduktion  
**Status:** 🔴 PLANNING PHASE  
**Priorität:** P0 - KRITISCH für Performance

---

## 📊 IMPACT ANALYSIS

### Aktuelle Situation
```kotlin
// ❌ DEPRECATED (lädt ALLE items in Memory):
fun observeMovies(): Flow<List<HomeMediaItem>>
// Bei 40.000 Movies: ~80MB Memory!

// ✅ OPTIMAL (lädt nur sichtbare items):
fun getMoviesPagingData(): Flow<PagingData<HomeMediaItem>>
// Bei 40.000 Movies: ~2MB Memory (20 items)
```

### Memory Impact
- **observeMovies()**: 40.000 items × 2KB = **80MB**
- **observeSeries()**: 15.000 items × 2KB = **30MB**
- **observeClips()**: 5.000 items × 2KB = **10MB**
- **observeTelegramMedia()**: Kombiniert alle 3 = **120MB**

**Total Current Memory Waste: ~120MB**  
**Expected After Removal: ~5MB** (-96%)

---

## 🗂️ BETROFFENE DATEIEN (Vollständige Liste)

### 1. Interface Definition (Contract)
- ✅ `core/home-domain/.../HomeContentRepository.kt`
  - **Status:** Bereits mit @Deprecated markiert
  - **Action:** Deprecated methods LÖSCHEN

### 2. Implementation
- ✅ `infra/data-nx/.../NxHomeContentRepositoryImpl.kt`
  - **Status:** Implementiert beide (deprecated + paging)
  - **Action:** Deprecated methods LÖSCHEN
  - **Lines:** 146-337 (deprecated section)

### 3. Consumers (UI Layer)
- ⚠️ `feature/home/.../HomeViewModel.kt`
  - **Status:** UNBEKANNT (muss geprüft werden)
  - **Action:** Zu Paging migrieren ODER bereits Paging

### 4. Test Files
- ⚠️ `app-v2/.../OnboardingToHomeE2EFlowTest.kt`
  - **Status:** Mock verwendet deprecated methods
  - **Action:** Mock zu Paging migrieren

### 5. Legacy Code (Optional)
- ℹ️ `docs/meta/diffs/...` - Nur Dokumentation
- ℹ️ `docs/meta/REPO_REALITY_DUMP.md` - Nur Dokumentation

---

## 📋 IMPLEMENTATION TASKS (Checklist)

### PHASE 1: Analysis & Preparation ✅

#### Task 1.1: Verify HomeViewModel Already Uses Paging
**Datei:** `feature/home/.../HomeViewModel.kt`  
**Check:**
```kotlin
// Suchen nach:
val moviesPagingFlow: Flow<PagingData<HomeMediaItem>>
val seriesPagingFlow: Flow<PagingData<HomeMediaItem>>

// NICHT vorhanden sein sollte:
val moviesItems: Flow<List<HomeMediaItem>>
```

**Status:** [ ] TODO  
**Erwartung:** Bereits Paging (aus letzter Session)

---

#### Task 1.2: Identify All Deprecated Method Usages
**Command:**
```bash
# Suche nach Usages der deprecated methods
rg "observeMovies\(\)" --type kotlin
rg "observeSeries\(\)" --type kotlin
rg "observeClips\(\)" --type kotlin
rg "observeTelegramMedia\(\)" --type kotlin
rg "observeXtreamVod\(\)" --type kotlin
rg "observeXtreamSeries\(\)" --type kotlin
```

**Status:** [ ] TODO  
**Output:** Liste aller Dateien die deprecated methods aufrufen

---

### PHASE 2: Remove from Implementation ✅

#### Task 2.1: Remove Deprecated Methods from NxHomeContentRepositoryImpl
**Datei:** `infra/data-nx/.../NxHomeContentRepositoryImpl.kt`

**Lines to DELETE:**
```kotlin
// Lines 146-159: observeMovies()
// Lines 162-196: observeSeries()
// Lines 200-207: observeClips()
// Lines 211-227: observeXtreamLive()
// Lines 316-337: observeTelegramMedia(), observeXtreamVod(), observeXtreamSeries()
```

**Status:** [ ] TODO  
**Risk:** LOW (Paging methods bereits vorhanden)

---

#### Task 2.2: Remove Helper Method (batchMapToHomeMediaItems)
**Datei:** `infra/data-nx/.../NxHomeContentRepositoryImpl.kt`

**Action:**
- Prüfen ob `batchMapToHomeMediaItems()` nur von deprecated methods verwendet wird
- Wenn JA: LÖSCHEN
- Wenn NEIN: BEHALTEN (wird von anderen Methoden benötigt)

**Status:** [ ] TODO  
**Risk:** MEDIUM (muss geprüft werden)

---

### PHASE 3: Remove from Interface ✅

#### Task 3.1: Remove Deprecated Methods from HomeContentRepository
**Datei:** `core/home-domain/.../HomeContentRepository.kt`

**Lines to DELETE:**
```kotlin
// Lines 30-40: observeMovies(), observeSeries(), observeClips(), observeXtreamLive()
// Lines 77-89: observeTelegramMedia(), observeXtreamVod(), observeXtreamSeries()
```

**Status:** [ ] TODO  
**Risk:** HIGH (Breaking change wenn noch Consumer existieren)

---

### PHASE 4: Update Tests ✅

#### Task 4.1: Update Mock in E2E Test
**Datei:** `app-v2/.../OnboardingToHomeE2EFlowTest.kt`

**Current (Lines 812-823):**
```kotlin
private val telegramMediaFlow = MutableStateFlow<List<HomeMediaItem>>(emptyList())
private val xtreamVodFlow = MutableStateFlow<List<HomeMediaItem>>(emptyList())
// ...
override fun observeTelegramMedia(): Flow<List<HomeMediaItem>> = telegramMediaFlow
```

**NEW:**
```kotlin
private val moviesPaging = MutableStateFlow<PagingData<HomeMediaItem>>(PagingData.empty())
private val seriesPaging = MutableStateFlow<PagingData<HomeMediaItem>>(PagingData.empty())
// ...
override fun getMoviesPagingData(): Flow<PagingData<HomeMediaItem>> = moviesPaging
```

**Status:** [ ] TODO  
**Risk:** MEDIUM (Test muss komplett umgeschrieben werden)

---

### PHASE 5: Verification ✅

#### Task 5.1: Compile Check
**Command:**
```bash
./gradlew :feature:home:compileDebugKotlin
./gradlew :infra:data-nx:compileDebugKotlin
./gradlew :core:home-domain:compileDebugKotlin
```

**Status:** [ ] TODO  
**Expected:** Keine Compile-Errors

---

#### Task 5.2: Test Run
**Command:**
```bash
./gradlew :app-v2:testDebugUnitTest --tests "*Home*"
```

**Status:** [ ] TODO  
**Expected:** Alle Tests grün

---

#### Task 5.3: Runtime Verification
**Test:**
1. App starten
2. Home-Screen öffnen
3. Prüfen: Movies/Series/Live Rows erscheinen
4. Prüfen: Memory Profiler zeigt ~40MB weniger

**Status:** [ ] TODO  
**Success Criteria:** Rows laden, Memory -50MB

---

## 🔍 PRE-CHECK: Is HomeViewModel Already Using Paging?

**Must verify BEFORE starting implementation:**
```kotlin
// File: feature/home/.../HomeViewModel.kt
// Looking for:
val moviesPagingFlow: Flow<PagingData<HomeMediaItem>>  // ✅ Good
val moviesItems: Flow<List<HomeMediaItem>>             // ❌ Bad (deprecated)
```

**IF already using Paging:** Safe to remove deprecated methods  
**IF still using Flow<List>:** Must migrate HomeViewModel FIRST

---

## 🎯 IMPLEMENTATION ORDER (Step-by-Step)

### Step 1: Verify Current State ✅
1. [ ] Read `feature/home/.../HomeViewModel.kt`
2. [ ] Confirm it uses `moviesPagingFlow` (not `moviesItems`)
3. [ ] Search for any usages of deprecated methods in codebase

### Step 2: Remove Implementation ✅
4. [ ] Remove deprecated methods from `NxHomeContentRepositoryImpl.kt`
5. [ ] Remove unused helper methods
6. [ ] Verify compilation

### Step 3: Remove Interface ✅
7. [ ] Remove deprecated methods from `HomeContentRepository.kt`
8. [ ] Verify compilation

### Step 4: Update Tests ✅
9. [ ] Update mock in E2E test
10. [ ] Run unit tests
11. [ ] Fix any failures

### Step 5: Final Verification ✅
12. [ ] Build app
13. [ ] Runtime test on device
14. [ ] Memory profiling

---

## 🚨 RISK ASSESSMENT

### HIGH RISK
- **Interface Change:** Breaking change wenn Consumer existieren
- **Mitigation:** Pre-check ob HomeViewModel bereits Paging verwendet

### MEDIUM RISK
- **Test Migration:** E2E Test muss umgeschrieben werden
- **Mitigation:** PagingData.empty() als Default

### LOW RISK
- **Implementation Removal:** Paging methods bereits vorhanden
- **Mitigation:** Keine

---

## 📊 SUCCESS METRICS

### Memory (Primary Goal)
- **Before:** 160MB peak während Sync
- **After:** 110MB peak (-50MB, -31%)
- **Measure:** Android Studio Memory Profiler

### GC (Secondary Goal)
- **Before:** GC alle 200-500ms
- **After:** GC alle 2-3s (-70%)
- **Measure:** Logcat GC logs

### Performance (Tertiary)
- **Before:** 77 frames dropped
- **After:** <5 frames dropped
- **Measure:** GPU Profiler

---

## 🔧 TOOLS NEEDED

1. **Android Studio Memory Profiler** - Memory Verification
2. **GPU Profiler** - Frame Drop Verification
3. **Logcat** - GC Frequency Check
4. **grep/rg** - Usage Search

---

## 📝 COMMIT STRATEGY

### Commit 1: Remove Implementation
```
refactor(data-nx): Remove deprecated Flow<List> methods from HomeContentRepository

- Remove observeMovies(), observeSeries(), observeClips()
- Remove observeTelegramMedia(), observeXtreamVod(), observeXtreamSeries()
- Keep Paging methods only

Memory impact: -80MB (movies) -30MB (series) -10MB (clips)
```

### Commit 2: Remove Interface
```
refactor(home-domain): Remove deprecated methods from HomeContentRepository interface

BREAKING CHANGE: Removed Flow<List> methods
Migration: Use getMoviesPagingData() instead of observeMovies()
```

### Commit 3: Update Tests
```
test: Update E2E tests to use PagingData instead of Flow<List>

- Update FakeHomeContentRepository mock
- Use PagingData.empty() for empty states
```

---

## 🎓 LEARNINGS FOR FUTURE

### What Worked Well
- **Deprecation Warnings:** Guided migration path
- **Paging3 Implementation:** Already available
- **Clear Separation:** Easy to identify deprecated code

### What Could Be Better
- **Earlier Migration:** Should have been done with Paging3 introduction
- **Automated Refactoring:** Could use IDE refactoring tools
- **Better Documentation:** Add migration guide

---

✅ **PLAN COMPLETE - READY FOR IMPLEMENTATION**

**Estimated Time:** 2-3 hours  
**Complexity:** MEDIUM  
**Impact:** HIGH (50MB Memory reduction)

**Next Action:** Execute Task 1.1 (Verify HomeViewModel)
