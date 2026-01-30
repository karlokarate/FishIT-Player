# Diagnose: Sync stoppt & keine Daten im UI (logcat_010.txt)

**Erstellt:** 2026-01-30  
**Logcat:** `scripts/logcat_010.txt`  
**Problem:** Sync läuft durch, findet aber 0 Items, UI bleibt leer

---

## 🔍 Root Cause Analysis

### Der Sync läuft NICHT ab - er findet einfach 0 Items!

```
08:25:26.726 SourceActi...onObserver I  Active sources changed: [XTREAM] → enqueuing auto sync
08:25:27.197 CatalogSyncService      I  Starting enhanced Xtream sync: live=true, vod=true, series=true
08:25:28.111 XtreamCatalogPipeline   D  [LIVE] Scan complete: 0 channels
08:25:28.546 XtreamCatalogPipeline   D  [SERIES] Scan complete: 0 items
08:25:32.928 XtreamCatalogPipeline   D  [VOD] Scan complete: 0 items
08:25:32.951 HomeCacheInvalidator    I  INVALIDATE_ALL source=XTREAM
08:25:32.973 WM-WorkerWrapper        I  Worker result SUCCESS
08:25:33.924 WorkManage...teObserver I  Sync state: SUCCEEDED
```

### API gibt leere Responses zurück

```
08:25:28.111 XtreamApiClient D  streamContentInBatches(get_live_streams): 0 items without category_id (fallback)
08:25:28.543 XtreamApiClient D  streamContentInBatches(get_series): 0 items without category_id (fallback)
08:25:32.925 XtreamApiClient D  streamContentInBatches(get_vod_streams): 0 items without category_id (fallback)
```

Die API probiert 3 Strategien für jede Content-Art:
1. `category_id=*` (alle Kategorien)
2. `category_id=0` (unkategorisiert)
3. Ohne `category_id` Parameter (Fallback)

**Alle drei geben 0 Items zurück!**

### Mögliche Ursachen

1. **API sendet leere Arrays**: `[]`
2. **API sendet Items mit unerwarteter Struktur** (werden vom Mapper zu `null` gemappt)
3. **API sendet Fehler-Response** (wird als leeres Array interpretiert)
4. **Credentials/Session ungültig** (aber Auth zeigt SUCCESS)

---

## 🛠️ Lösung: Diagnose-Patch installiert

Ich habe einen Diagnose-Patch in `DefaultXtreamApiClient.kt` eingefügt, der die **ersten 500 Bytes** jeder API-Response loggt:

```kotlin
// 🔍 DIAGNOSTIC: Log first 500 chars of response to see what we're getting
UnifiedLog.d(TAG) {
    "streamFromUrl PREVIEW (${read} bytes): ${previewStr.take(500)}"
}
```

### Was wird geloggt?

- **Erfolgreiche Requests:** Zeigt die ersten 500 Bytes der JSON-Response
- **Leere Responses:** Zeigt `0 bytes`
- **Fehler:** Zeigt Fehlercode

---

## 📋 Nächste Schritte

### 1. App neu bauen und installieren

```powershell
cd C:\Users\admin\StudioProjects\FishIT-Player
.\gradlew assembleDebug
adb install -r app-v2\build\outputs\apk\debug\app-v2-debug.apk
```

### 2. Logcat sammeln

```powershell
# Altes Logcat löschen
adb logcat -c

# App starten und Onboarding durchlaufen (Xtream Credentials eingeben)

# Logcat sammeln (während Sync läuft)
adb logcat > scripts\logcat_011_diagnosis.txt
```

### 3. Nach diesem Muster suchen

Im neuen Logcat nach folgenden Zeilen suchen:

```
XtreamApiClient  D  streamFromUrl PREVIEW (xxx bytes): {...
```

**Das zeigt uns:**
- Ist die Response wirklich leer? (`[] ` oder `0 bytes`)
- Hat die Response ein unerwartetes Format? (z.B. `{"error": "..."}`)
- Sind Items vorhanden, werden aber nicht geparst? (z.B. `[{"num": 1, ...`)

---

## 🔎 Analyse-Beispiele

### Szenario A: Leere Response
```
XtreamApiClient  D  streamFromUrl PREVIEW (2 bytes): []
XtreamApiClient  D  StreamBatch: 0 items in 0 batches (5ms) | errors=0
```
**Bedeutung:** API sendet wirklich leere Arrays → Server-Problem oder Credentials falsch

### Szenario B: Fehler-Response
```
XtreamApiClient  D  streamFromUrl PREVIEW (45 bytes): {"error":"Invalid category_id"}
XtreamApiClient  W  streamInBatches: Expected JSON array, got: START_OBJECT
```
**Bedeutung:** API gibt Fehler zurück statt Array → Parameter-Problem

### Szenario C: Items vorhanden, aber nicht geparst
```
XtreamApiClient  D  streamFromUrl PREVIEW (500 bytes): [{"num":1,"name":"Channel 1","stream_id":123,...
XtreamApiClient  D  StreamBatch: 0 items in 0 batches (50ms) | errors=15
```
**Bedeutung:** API sendet Items, aber Mapper wirft Exceptions → Parsing-Bug

### Szenario D: Items erfolgreich geparst
```
XtreamApiClient  D  streamFromUrl PREVIEW (500 bytes): [{"num":1,"name":"Channel 1",...
XtreamApiClient  D  StreamBatch: 1523 items in 4 batches (234ms) | errors=0
```
**Bedeutung:** Alles funktioniert! (Sollte nach dem Fix der Fall sein)

---

## 🎯 Erwartetes Ergebnis

Nach dem Diagnose-Patch sollten wir im Logcat genau sehen, was die API zurückgibt.

Dann können wir entscheiden:
1. **Server-Problem:** API-Provider kontaktieren / andere Credentials testen
2. **Client-Problem:** Parsing-Bug fixen / Parameter anpassen
3. **Config-Problem:** Xtream-URL / Credentials korrigieren

---

## 📝 Commit-Message für später

```
fix(xtream): Add diagnostic logging for empty catalog responses

- Log first 500 bytes of API responses to diagnose 0-item syncs
- Enhanced error reporting for streamFromUrl failures
- Helps identify if API sends empty arrays or invalid JSON

Context: Users report sync completes with 0 items and empty UI
Root cause: Unknown - API responses not visible in logs
Solution: Temporary diagnostic logging to capture actual response content

Related: logcat_010.txt analysis
```
