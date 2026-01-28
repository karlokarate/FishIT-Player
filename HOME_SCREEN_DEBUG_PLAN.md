# HOME SCREEN DEBUGGING - Comprehensive Logging Added

## 🎯 Problem

User navigiert zum Home-Screen aber sieht keine Movies/Series

## ✅ Was ich gemacht habe

### 1. Umfangreiches Diagnostic Logging hinzugefügt

**Files modifiziert:**
- `NxHomeContentRepositoryImpl.kt` - Added logging to `observeMovies()` and `observeSeries()`
- `NxWorkRepositoryImpl.kt` - Added logging to `observeByType()` with count tracking

**Expected Logs (wenn alles funktioniert):**
```
NxHomeContentRepo: observeMovies() CALLED - THIS SHOULD APPEAR IN LOGCAT!
NxWorkRepository: observeByType CALLED: type=MOVIE (entity=MOVIE), limit=50
NxWorkRepository: observeByType EMITTING: type=MOVIE, count=1300
```

**If no logs appear:** Home-Screen wird nicht initialisiert (ViewModel-Fehler oder Navigation-Bug)

**If logs show count=0:** DB-Schema-Mismatch → Items sind in DB aber Query findet sie nicht

---

## 🚀 NEXT STEPS - User muss testen

### Test-Szenario:

```bash
# 1. Build mit neuem Logging
./gradlew assembleDebug

# 2. Install
adb install -r app-v2/build/outputs/apk/debug/app-v2-debug.apk

# 3. Start fresh logcat
adb logcat -c

# 4. App öffnen
# 5. Login (Xtream credentials eingeben)
# 6. ZUM HOME-SCREEN NAVIGIEREN (wichtig!)
# 7. 5 Sekunden warten

# 8. Logcat speichern
adb logcat > logcat_005_with_debug.txt
```

---

## 🔍 Diagnostic Cases

### CASE A: Logs erscheinen mit count > 0

```
NxHomeContentRepo: observeMovies() CALLED
NxWorkRepository: observeByType CALLED: type=MOVIE, limit=50
NxWorkRepository: observeByType EMITTING: type=MOVIE, count=1300
```

**Bedeutung:** ✅ Query funktioniert, Items werden gefunden  
**Next:** Prüfen ob UI-Rendering-Bug (Compose)  
**Action:** Check HomeViewModel state updates

---

### CASE B: Logs erscheinen mit count = 0

```
NxHomeContentRepo: observeMovies() CALLED
NxWorkRepository: observeByType CALLED: type=MOVIE, limit=50
NxWorkRepository: observeByType EMITTING: type=MOVIE, count=0  ← PROBLEM!
```

**Bedeutung:** ❌ DB-Schema-Mismatch → Query findet Items nicht  
**Root Cause:** `workType` in DB hat falschen Wert  
**Next:** Check DB Schema

**Debug Query:**
```bash
adb shell
su
cd /data/data/com.fishit.player.v2/databases/
sqlite3 fishit-v2.db

# Check work_type values
SELECT work_type, COUNT(*) FROM NX_Work GROUP BY work_type;

# Expected output:
# MOVIE | 1300
# SERIES | 500
# LIVE | 1200

# If you see different values (e.g., "Movie" instead of "MOVIE"):
# → Schema mismatch bug!
```

---

### CASE C: Keine Logs erscheinen

```
(kein NxHomeContentRepo oder NxWorkRepository Log)
```

**Bedeutung:** ❌ Home-Screen wird nicht initialisiert  
**Root Causes:**
1. Navigation schlägt fehl
2. HomeViewModel-Crash beim Init
3. Repository wird nicht injected

**Action:** Check für Crashes:
```bash
adb logcat | grep -E "AndroidRuntime|FATAL|HomeViewModel|Navigation"
```

---

## 📊 Expected Flow (Happy Path)

```
User öffnet App
  ↓
Login successful (Line 227-229 in logcat_004)
  ↓
Navigate to Home-Screen
  ↓
HomeViewModel.init() ← SHOULD LOG
  ↓
NxHomeContentRepo.observeMovies() CALLED ← SHOULD LOG
  ↓
NxWorkRepository.observeByType(MOVIE, 50) ← SHOULD LOG
  ↓
Query DB: SELECT * FROM NX_Work WHERE work_type='MOVIE' LIMIT 50
  ↓
NxWorkRepository EMITTING: count=1300 ← SHOULD LOG
  ↓
HomeViewModel updates UI state
  ↓
Compose renders movies
```

**Current Status:** Flow stops somewhere before `observeMovies()` is called

---

## 🐛 Possible Root Causes (Priority Order)

### 1. Navigation Bug (60% probability)

**Theory:** Navigation vom Login-Screen zum Home-Screen schlägt fehl

**Evidence Needed:**
- Check für `NavController` oder `Navigation` Logs
- Check für Home-Screen Composable-Logs

**Test:**
```kotlin
// Add to HomeScreen.kt Composable
@Composable
fun HomeScreen(...) {
    LaunchedEffect(Unit) {
        Log.i("HomeScreen", "HomeScreen COMPOSABLE RENDERED!")
    }
    // ...
}
```

---

### 2. HomeViewModel Crash (30% probability)

**Theory:** HomeViewModel wirft Exception beim Init → kein UI

**Evidence Needed:**
- Check logcat für Crashes/Exceptions
- Check für `HomeViewModel` Logs

**Test:**
```kotlin
// Add to HomeViewModel init
class HomeViewModel(...) : ViewModel() {
    init {
        Log.i("HomeViewModel", "HomeViewModel INITIALIZED!")
        // ...
    }
}
```

---

### 3. DB Schema Mismatch (10% probability)

**Theory:** Items haben falsche `work_type` Werte

**Evidence:** Would show as `count=0` in logs (not "no logs")

**Test:** Run SQL query (see CASE B above)

---

## 📝 Summary for User

**Status:** ✅ **DIAGNOSTIC LOGGING ADDED**

**What I did:**
1. Added 3 log points to track query execution
2. Added item count logging to see if DB query finds items
3. Added clear "THIS SHOULD APPEAR" messages

**What you need to do:**
1. Build & install app with new logging
2. Login + Navigate to Home-Screen
3. Capture logcat
4. Send logcat_005 here

**What we'll learn:**
- Does Home-Screen initialize?
- Does DB query execute?
- How many items does query find?

---

**Next Log:** `logcat_005_with_debug.txt`  
**Expected:** Clear diagnostic messages showing exactly where flow stops  
**Status:** ⏸️ **AWAITING TEST** - Build & capture new logcat

---

**Created:** 2026-01-28  
**Purpose:** Diagnose why Home-Screen shows no content despite successful sync
