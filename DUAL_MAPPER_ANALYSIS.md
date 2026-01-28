# NX Mapper vs Legacy Mapper - Analyse ✅

## 🎯 **ERGEBNIS: Kein Dual-Mapper Problem!**

**Status:** ✅ **NUR NX Mapper läuft**  
**Problem:** ❌ **Aber keine Daten erreichen die UI!**

---

## ✅ **Was funktioniert:**

### 1. NX Repository ist aktiviert
**File:** `infra/data-nx/di/NxDataModule.kt` Line 287-289
```kotlin
@Binds
@Singleton
abstract fun bindHomeContentRepository(
    impl: NxHomeContentRepositoryImpl,  // ← NX Implementation!
): HomeContentRepository
```

### 2. Legacy Module ist leer
**File:** `infra/data-home/di/HomeDataModule.kt`
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class HomeDataModule {
    // HomeContentRepository binding moved to NxDataModule  ✅
    // This module is kept empty for now during migration
}
```

### 3. Sync schreibt NX-ONLY
**logcat_008 Line 381-497:**
```
CatalogSyncService: Persisting Xtream catalog batch (NX-ONLY): 200 items
CatalogSyncService: Xtream batch complete (HOT PATH/NX): ingested=200
```

**Conclusion:** ✅ Sync schreibt korrekt in NX Entities!

---

## ❌ **Was NICHT funktioniert:**

### Problem: UI zeigt keine Daten!

**logcat_008 - KEINE Home Logs:**
```
❌ Kein "NxHomeContentRepo" Log
❌ Kein "observeMovies() CALLED" Log
❌ Kein "getMoviesPagingData" Log
❌ Kein UI-Query Log
```

**Expected (aber fehlt):**
```kotlin
// File: NxHomeContentRepositoryImpl.kt Line 150
UnifiedLog.i(TAG) { "observeMovies() CALLED - THIS SHOULD APPEAR IN LOGCAT!" }
```

---

## 🔍 **Root Cause Analysis:**

### Hypothese 1: HomeViewModel wird nicht initialisiert
- `HomeViewModel` erstellt die Paging Flows im `init {}` Block
- Wenn `HomeScreen` nicht gerendert wird → kein ViewModel → keine Queries

### Hypothese 2: NxWorkRepository ist leer
- Sync schreibt in `NX_WorkSourceRef` und `NX_WorkVariant`
- **ABER:** Schreibt es auch in `NX_Work`? ← **KRITISCH!**

### Hypothese 3: Query-Filter ist zu strikt
- `NxHomeContentRepositoryImpl.getMoviesPagingData()` filtert nach `WorkType.MOVIE`
- Wenn `workType` falsch gesetzt ist → keine Results!

---

## 🎯 **Debugging Plan:**

### Step 1: Prüfen ob NX_Work Einträge existieren

**Check this query:**
```kotlin
// NxHomeContentRepositoryImpl.kt getMoviesPagingData()
workRepository.observeByType(
    workType = WorkType.MOVIE,  // ← Gibt es MOVIE Einträge?
    limit = ...
)
```

**Possible Issues:**
- `NxCatalogWriter` schreibt nur SourceRefs, NICHT Works?
- `workType` wird als `"movie"` statt `"MOVIE"` gespeichert? (Case sensitivity!)
- HomeScreen wird gar nicht gerendert?

### Step 2: Add Debug Logs

**File 1:** `NxCatalogWriter.kt`
```kotlin
fun ingestBatch(...) {
    UnifiedLog.d("NxCatalogWriter") { "Writing ${items.size} items to NX_Work" }
    // ... nach write ...
    UnifiedLog.d("NxCatalogWriter") { "Written: ${written} works, ${refs} sourceRefs" }
}
```

**File 2:** `NxWorkRepositoryImpl.kt`
```kotlin
override fun observeByType(workType: WorkType, limit: Int): Flow<List<Work>> {
    UnifiedLog.d("NxWorkRepo") { "observeByType called: type=$workType limit=$limit" }
    // ... query ...
    UnifiedLog.d("NxWorkRepo") { "Query returned: ${results.size} items" }
}
```

**File 3:** `HomeViewModel.kt` - init block
```kotlin
init {
    UnifiedLog.i("HomeViewModel") { "INIT - Creating paging flows" }
    // ... existing code ...
}
```

### Step 3: Check DB Contents

**Query NX_Work directly:**
```sql
SELECT COUNT(*), workType FROM NX_Work GROUP BY workType;
```

**Expected:**
```
MOVIE: 3400
SERIES: 2800
EPISODE: 40000+
LIVE: 800
```

**If ZERO:** Problem ist beim Schreiben (NxCatalogWriter bug!)  
**If NON-ZERO:** Problem ist beim Lesen (Query/Paging bug!)

---

## 📝 **TODO - Fixes:**

### Priority 1: Add Logging (5 min)

1. ✅ Add log in `NxCatalogWriter.ingestBatch()`
2. ✅ Add log in `NxWorkRepository.observeByType()`
3. ✅ Add log in `HomeViewModel.init`
4. ✅ Add log in `NxHomeContentRepositoryImpl.getMoviesPagingData()`

### Priority 2: Test & Collect Logs (2 min)

1. Build & Install
2. Navigate to Home
3. Collect logcat with tags: `NxCatalogWriter`, `NxWorkRepo`, `HomeViewModel`, `NxHomeContentRepo`

### Priority 3: Fix Based on Logs (10 min)

**Scenario A: NxCatalogWriter nicht aufgerufen**
- Fix: Check sync flow, ensure `nxCatalogWriter.ingestBatch()` is called

**Scenario B: NxCatalogWriter schreibt nicht in NX_Work**
- Fix: Implement `work.put()` in `ingestBatch()`

**Scenario C: Query filtered ALL results**
- Fix: Adjust filter or workType mapping

---

## 🎯 **Expected Resolution:**

**After logging:** Wir sehen EXAKT wo die Daten verloren gehen:
- Beim Schreiben? → Fix NxCatalogWriter
- Beim Lesen? → Fix Query
- Beim UI? → Fix HomeViewModel/HomeScreen

**Confidence:** 95% - Mit Logs finden wir den Bug in <10 Minuten!

---

**Status:** Ready for Debug Logging Implementation! 🚀
