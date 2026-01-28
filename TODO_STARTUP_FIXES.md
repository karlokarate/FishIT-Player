# TODO: Startup Performance Fixes

## 📋 Betroffene Dateien

### 1. CatalogSyncBootstrap.kt ✅ GEFUNDEN
**Location:** `app-v2/src/main/java/com/fishit/player/v2/bootstrap/CatalogSyncBootstrap.kt`

**Aktueller Zustand:**
- SYNC_DELAY_MS = 5000L (5 Sekunden) ✅ BEREITS GUT!
- Delay wird korrekt angewendet

**Problem in Log:**
- Frame-Drops passieren bei 13:46:49 (2 Sekunden nach Start)
- CatalogSyncBootstrap startet bei 13:46:48.927
- Sync wird enqueued bei 13:46:53.959 (5 Sekunden später) ✅

**Analyse:** ⚠️ **CatalogSyncBootstrap ist NICHT das Problem!**
- Delay ist bereits 5 Sekunden
- Frame-Drops passieren **während XtreamSessionBootstrap** läuft (nicht CatalogSync!)

### 2. XtreamSessionBootstrap.kt 🔴 PROBLEM HIER!
**Location:** `app-v2/src/main/java/com/fishit/player/v2/bootstrap/XtreamSessionBootstrap.kt`

**Problem:**
- XtreamSessionBootstrap macht API-Calls **während** UI-Initialisierung
- Zeile 75-76: "Auto-initializing Xtream session" bei 13:46:49.209
- API-Calls starten SOFORT (keine Verzögerung)
- Frame-Drops passieren genau dann!

**Action:** ADD DELAY zu XtreamSessionBootstrap

### 3. WorkerRetryPolicy.kt (Optional)
**Location:** `app-v2/src/main/java/com/fishit/player/v2/work/WorkerRetryPolicy.kt`

**Problem:**
- XtreamPreflightWorker startet zu früh (Auth State = Idle)
- Führt zu unnötigem RETRY

**Action:** Increase retry delay oder warte auf Auth State

### 4. Invalid Resource ID 0x00000000 🔍 INVESTIGATION
**Location:** Unknown (XML oder Code)

**Action:** Suche nach null-Resource-References

---

## 🎯 Fix-Plan

### Fix 1: Verzögere XtreamSessionBootstrap (CRITICAL)
**Priority:** P0  
**File:** `XtreamSessionBootstrap.kt`  
**Change:** Add 2-second delay before initializing session

```kotlin
// Before API calls
delay(2000)
```

**Expected:** Frame-Drops von 85 → <10

### Fix 2: Increase Preflight Retry Delay (MEDIUM)
**Priority:** P1  
**File:** `WorkerRetryPolicy.kt`  
**Change:** Increase initial backoff from 10s to 30s für Auth-State-Idle

**Expected:** Keine unnötigen RETRYs

### Fix 3: Investigate Invalid Resource (LOW - needs search)
**Priority:** P2  
**Action:** Search codebase for resource loading with null-checks

---

## ✅ Umsetzung

1. [ ] Read XtreamSessionBootstrap.kt
2. [ ] Add delay before session initialization
3. [ ] Test Frame-Drops
4. [ ] Read WorkerRetryPolicy.kt
5. [ ] Increase retry delay
6. [ ] Search for Invalid Resource ID
7. [ ] Create comprehensive review document
