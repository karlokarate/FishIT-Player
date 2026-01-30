# Logcat 19 Tiefenanalyse - FishIT-Player

**Datum:** 2026-01-30  
**Logcat-Zeitraum:** 09:32:49 - 09:40:24 (ca. 8 Minuten)

---

## 🔴 KRITISCHE PROBLEME

### 1. SERIES WERDEN NICHT GESYNCT

**Evidence:**
- Zeile 268: `Starting Xtream catalog scan: vod=true, series=true, episodes=false, live=true` - Config korrekt!
- LIVE Scan: ✅ 21.000 items in 103s (Zeile 1598-1599)
- VOD Scan: ⚠️ Läuft bis Logcat-Ende (ca. 10.000+ items)
- SERIES Scan: ❌ NIEMALS GESTARTET - kein `[SERIES] Starting` im gesamten Log!

**Root Cause:**
- Der VOD-Scan dauert zu lange und wird entweder:
  - a) Nie abgeschlossen bevor der User die App verlässt, ODER
  - b) Hat einen Fehler, der die SERIES-Phase verhindert

**Zeile 1595-1597 zeigt den LIVE-Fehler:**
```
StreamingJsonParser: streamInBatches mapper error #1: timeout
XtreamApiClient: streamContentInBatches(get_live_streams): streamFromUrl FAILED | SocketException: Socket closed
```
→ Timeout führt zu Stream-Abbruch!

### 2. EXTREME UI-LAGS (77 Frames skipped!)

**Frame Skip Statistics aus Logcat:**
| Zeile | Skipped Frames | Davey Duration |
|-------|---------------|----------------|
| 1800 | 45 frames | - |
| 1801 | - | 847ms |
| 1841 | 63 frames | - |
| 1847 | - | 1132ms |
| 1868 | 56 frames | - |
| 1869 | - | 984ms |
| 1951 | **77 frames!** | 1403ms |
| 1952 | - | 1403ms |

**Root Cause:**
- NxWorkRepository emittiert **hunderte Male pro Sekunde** auf dem Main Thread
- Zeile 2800-3000: Über 200 `observeByType EMITTING: type=SERIES, count=0` in 3 Sekunden!
- GC läuft alle 200-500ms mit 30-90MB frei (Memory Pressure)

### 3. HOMESCREEN MOVIE ROW NICHT SICHTBAR

**Evidence:**
- Zeile 2511-2512: `NxWorkRepository: observeByType EMITTING: type=MOVIE, count=50` ✅ Daten vorhanden
- Zeile 5387: `NxWorkRepository: observeByType EMITTING: type=MOVIE, count=50` ✅ Auch im Library
- Aber HomeScreen zeigt keine Film-Row!

**Root Cause:**
- Die Flow-Verbindung zwischen NxWorkRepository und HomeViewModel/HomeScreen ist defekt
- Möglicherweise fehlt `distinctUntilChanged()` und die UI wird mit leeren/alten Daten überflutet

### 4. SOCKET TIMEOUTS WÄHREND SYNC

**Evidence:**
- Zeile 1595: `StreamingJsonParser: streamInBatches mapper error #1: timeout`
- Zeile 1596: `XtreamApiClient: streamFromUrl FAILED | SocketException: Socket closed`

**Root Cause:**
- OkHttp Read Timeout zu kurz für große JSON-Streams
- Keine Retry-Logik bei Timeout

### 5. EXCESSIVE FLOW EMISSIONS

**Evidence:**
- Zwischen Zeile 2800-3000: `type=SERIES, count=0` wird **200+ Mal** emittiert in ~3 Sekunden!
- Dies blockiert den Main Thread und verursacht Frame Drops

**Root Cause:**
- Kein `distinctUntilChanged()` auf den Flows
- Kein `debounce()` für Progress-Updates
- ObjectBox-Queries triggern bei jeder DB-Änderung

---

## 📋 TODO-LISTE (Priorität)

### P0 - Kritisch (sofort fixen)

1. **[BUG-001] SERIES-Sync wird nie gestartet**
   - Untersuchen: Warum VOD nie abschließt
   - Socket Timeout auf 120s erhöhen
   - Retry-Logik bei Timeout hinzufügen

2. **[BUG-002] HomeScreen Movie Row fehlt**
   - NxHomeContentRepo/HomeViewModel Flow-Verbindung prüfen
   - Sicherstellen, dass observeByType korrekt an UI weitergeleitet wird

3. **[PERF-001] Flow Emission Spam**
   - `distinctUntilChanged()` zu NxWorkRepository.observeByType hinzufügen
   - `debounce(300)` für Progress-Flows

### P1 - Wichtig

4. **[PERF-002] Frame Drops während Sync**
   - Sync-Batching auf IO-Dispatcher verschieben
   - Progress-Emissions auf 500 statt 100 items erhöhen

5. **[PERF-003] GC Pressure**
   - Streaming-JSON Parser Memory optimieren
   - Batch-Size für ObjectBox anpassen

### P2 - Nice-to-have

6. **[LOG-001] Duplicate Phase Logs**
   - `SyncPerfMetrics: Phase LIVE started` wird tausendmal geloggt
   - Phase-Start nur einmal pro Phase loggen

---

## 🔧 GEPLANTE FIXES

### Fix 1: Socket Timeout erhöhen

**Datei:** `infra/transport-xtream/*/XtreamApiClient.kt`

```kotlin
// Alt: 30 Sekunden Timeout
// Neu: 120 Sekunden für große JSON-Streams
private val client = OkHttpClient.Builder()
    .readTimeout(120, TimeUnit.SECONDS)
    .build()
```

### Fix 2: Flow Debouncing in NxWorkRepository

**Datei:** `infra/data-*/NxWorkRepository.kt`

```kotlin
override fun observeByType(type: MediaType, limit: Int): Flow<List<NxWork>> =
    box.query()
        .equal(NxWork_.mediaType, type.name)
        .build()
        .subscribe()
        .asFlow()
        .distinctUntilChanged() // NEU: Verhindert duplicate emissions
        .debounce(100) // NEU: Throttle für UI
        .flowOn(Dispatchers.IO)
```

### Fix 3: Progress Emissions reduzieren

**Datei:** `pipeline/xtream/*/XtreamCatalogPipelineImpl.kt`

```kotlin
// Alt: PROGRESS_LOG_INTERVAL = 100
// Neu: 500
companion object {
    private const val PROGRESS_LOG_INTERVAL = 500
}
```

### Fix 4: Phase-Log nur einmal

**Datei:** `app-v2/*/SyncPerfMetrics.kt`

```kotlin
private var currentPhase: String? = null

fun logPhaseStarted(phase: String) {
    if (phase != currentPhase) {  // NEU: Nur bei Phasenwechsel
        currentPhase = phase
        UnifiedLog.d(TAG, "Phase $phase started")
    }
}
```

---

## 📊 PERFORMANCE-METRIKEN AUS LOGCAT

| Metrik | Wert | Status |
|--------|------|--------|
| LIVE Sync Time | 103 Sekunden | ⚠️ OK |
| LIVE Items | 21.000 | ✅ |
| VOD Items (partial) | ~10.000+ | ⚠️ Incomplete |
| SERIES Items | 0 | ❌ Not synced |
| Max Skipped Frames | 77 | 🔴 KRITISCH |
| Max Davey Duration | 1403ms | 🔴 KRITISCH |
| GC Frequency | 200-500ms | ⚠️ Zu häufig |
| Memory Peak | 164MB | ⚠️ Hoch |

---

## 🎯 NÄCHSTE SCHRITTE

1. XtreamApiClient Timeout erhöhen
2. NxWorkRepository Flow-Optimierung
3. HomeViewModel/HomeScreen Flow-Verbindung prüfen
4. SyncPerfMetrics Logging optimieren
5. Progress-Intervall erhöhen

**Geschätzte Implementierungszeit:** 2-3 Stunden
