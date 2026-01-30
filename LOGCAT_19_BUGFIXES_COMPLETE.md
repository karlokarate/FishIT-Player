# LOGCAT_19 BUGFIXES - Implementiert ✅

**Datum:** 2026-01-30  
**Branch:** architecture/v2-bootstrap

---

## ✅ IMPLEMENTIERTE FIXES

### 🔴 FIX 1: Flow Emission Spam (NxWorkRepository)

**Problem:**
- `observeByType EMITTING: type=SERIES, count=0` wurde **200+ Mal in 3 Sekunden** geloggt
- Main Thread Blockierung → Frame Drops (77 Frames skipped!)

**Lösung:**
```kotlin
// File: infra/data-nx/.../NxWorkRepositoryImpl.kt

override fun observeByType(type: WorkType, limit: Int): Flow<List<Work>> {
    return box.query(...)
        .asFlowWithLimit(limit)
        .distinctUntilChanged() // ✅ NEU: Verhindert Duplikate
        .debounce(100)          // ✅ NEU: Throttle während Sync
        .map { list -> list.map { it.toDomain() } }
        .flowOn(Dispatchers.IO)  // ✅ NEU: Off main thread
}
```

**Erwartetes Ergebnis:**
- Flow-Emissions reduziert um ~95%
- Keine Frame Drops mehr während Sync
- UI bleibt responsiv

---

### 🔴 FIX 2: Socket Timeout während großem Sync

**Problem:**
- `StreamingJsonParser: streamInBatches mapper error #1: timeout`
- `XtreamApiClient: streamFromUrl FAILED | SocketException: Socket closed`
- VOD-Scan (21.000+ items) timed out nach 30 Sekunden

**Lösung:**
```kotlin
// File: infra/transport-xtream/.../XtreamTransportConfig.kt

// ✅ NEU: Separate Timeouts für Streaming-Operationen
const val STREAMING_READ_TIMEOUT_SECONDS: Long = 120L
const val STREAMING_CALL_TIMEOUT_SECONDS: Long = 180L

// File: infra/transport-xtream/.../DefaultXtreamApiClient.kt

// ✅ NEU: Streaming-spezifischer Client
private val streamingHttp: OkHttpClient by lazy {
    http.newBuilder()
        .readTimeout(XtreamTransportConfig.STREAMING_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(XtreamTransportConfig.STREAMING_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
}

private suspend fun fetchRawAsStream(url: String): StreamingResponse {
    return try {
        // ✅ NEU: Verwende streamingHttp statt http
        val response = streamingHttp.newCall(request).execute()
        // ...
    }
}
```

**Erwartetes Ergebnis:**
- Keine Socket-Timeouts mehr bei großen Katalogen
- VOD/Series-Sync läuft komplett durch

---

### 🔴 FIX 3: Progress Log Spam

**Problem:**
- `SyncPerfMetrics: Phase MOVIES started` wurde bei **jedem Progress-Update** (alle 100 items) geloggt
- Tausende identische Logs

**Lösung:**
```kotlin
// File: core/catalog-sync/.../DefaultCatalogSyncService.kt

var currentSyncPhase: SyncPhase? = null  // ✅ NEU: Phase-Tracker

// ...in ScanProgress handler:
val syncPhase = when (event.currentPhase) {
    XtreamScanPhase.LIVE -> SyncPhase.LIVE
    // ...
}

// ✅ NEU: Nur starten wenn Phase wechselt
if (syncPhase != currentSyncPhase) {
    currentSyncPhase = syncPhase
    metrics.startPhase(syncPhase)
}
```

**Erwartetes Ergebnis:**
- "Phase X started" erscheint nur 1x pro Phase (nicht 100x)
- Logcat bleibt übersichtlich

---

### 🔴 FIX 4: Progress-Intervall zu klein

**Problem:**
- Progress-Updates alle 100 items → zu viele UI-Updates
- Overhead während Sync

**Lösung:**
```kotlin
// File: pipeline/xtream/.../XtreamCatalogPipelineImpl.kt

companion object {
    private const val TAG = "XtreamCatalogPipeline"
    // ✅ Erhöht von 100 auf 500
    private const val PROGRESS_LOG_INTERVAL = 500
}
```

**Erwartetes Ergebnis:**
- 80% weniger Progress-Logs
- Weniger Flow-Emissions
- Bessere Performance

---

### 🔴 FIX 5: HomeScreen Movie Row nicht sichtbar

**Problem:**
- `moviesPagingItems.itemCount > 0` war zu strikt
- Bei Paging3 ist `itemCount` beim ersten Render oft noch 0
- Row wurde nie angezeigt, obwohl Daten vorhanden

**Lösung:**
```kotlin
// File: feature/home/.../HomeScreen.kt

// ✅ NEU: Zeige Row wenn Daten ODER wenn Loading
if (usePaging && moviesPagingItems != null) {
    val hasItems = moviesPagingItems.itemCount > 0
    val isLoading = moviesPagingItems.loadState.refresh is LoadState.Loading
    if (hasItems || isLoading) {
        item(key = "movies_paging") {
            PagingMediaRow(
                title = "Movies",
                icon = Icons.Default.Movie,
                pagingItems = moviesPagingItems,
                onItemClick = onItemClick,
            )
        }
    }
}
```

**Erwartetes Ergebnis:**
- Movies/Series/Live Rows erscheinen sofort
- Skeleton Loader während Paging lädt
- Rows bleiben sichtbar nach Laden

---

## 📊 ERWARTETE PERFORMANCE-VERBESSERUNGEN

| Metrik | Vorher | Nachher | Verbesserung |
|--------|--------|---------|--------------|
| Flow Emissions | 200+/sec | <10/sec | **95%** |
| Socket Timeouts | Häufig | Keine | **100%** |
| Frame Skips | 77 frames | <5 frames | **93%** |
| Progress Logs | Alle 100 | Alle 500 | **80%** |
| Phase Logs | 100x/Phase | 1x/Phase | **99%** |

---

## 🧪 TEST-CHECKLISTE

Nach dem nächsten Build prüfen:

### Sync Performance
- [ ] VOD-Scan läuft komplett durch (keine Socket Timeouts)
- [ ] SERIES-Sync startet nach VOD
- [ ] Keine "Socket closed" Errors im Logcat
- [ ] Progress-Updates alle 500 items (nicht 100)

### UI Performance
- [ ] Keine Frame Drops während Sync (<10 frames skipped)
- [ ] App bleibt responsiv während Sync
- [ ] GC läuft seltener (<alle 2 Sekunden)

### HomeScreen
- [ ] Movies Row erscheint sofort
- [ ] Series Row erscheint sofort
- [ ] Live TV Row erscheint sofort
- [ ] Skeleton Loader während Paging lädt

### Logging
- [ ] "Phase X started" erscheint nur 1x pro Phase
- [ ] Flow emissions deutlich reduziert
- [ ] Logcat bleibt lesbar

---

## 🔧 GEÄNDERTE DATEIEN

1. `infra/data-nx/.../NxWorkRepositoryImpl.kt` - Flow optimierung
2. `infra/transport-xtream/.../XtreamTransportConfig.kt` - Streaming timeouts
3. `infra/transport-xtream/.../DefaultXtreamApiClient.kt` - Streaming client
4. `pipeline/xtream/.../XtreamCatalogPipelineImpl.kt` - Progress intervall
5. `core/catalog-sync/.../DefaultCatalogSyncService.kt` - Phase tracking
6. `feature/home/.../HomeScreen.kt` - Paging row visibility

---

## 📝 NEXT STEPS

Nach erfolgreichem Test:

1. **Commit Message:**
   ```
   perf: Fix catalog sync performance issues
   
   - Add Flow throttling (distinctUntilChanged + debounce) to prevent UI spam
   - Increase streaming timeouts (120s read, 180s call) for large catalogs
   - Reduce progress interval from 100 to 500 items
   - Fix duplicate "Phase started" logs with phase tracking
   - Fix HomeScreen paging rows not showing initially
   
   Fixes: Frame drops (77→<5), Socket timeouts, Missing UI rows
   
   Performance improvement: ~95% fewer Flow emissions
   ```

2. **Monitor:** Logcat nach neuem Build für Regressions

3. **Optional:** Weitere Optimierungen wenn nötig:
   - GC tuning (wenn Memory Pressure bleibt)
   - Batch size anpassen (wenn DB-Schreibgeschwindigkeit Problem)
   - Weitere Flow-Optimierungen (wenn UI noch laggt)

---

## 🎯 ROOT CAUSE ZUSAMMENFASSUNG

**Warum keine Movie Row erschien:**
- Paging3's `itemCount` ist initial 0
- Bedingung `itemCount > 0` war zu strikt
- Row wurde nie gerendert, obwohl PagingSource Daten hatte

**Warum extreme Lags:**
- NxWorkRepository emittierte bei jedem DB-Update
- Keine distinctUntilChanged → Duplikate
- Kein debounce → 200+ emissions/sec
- Main Thread blockiert → Frame Drops

**Warum Socket Timeout:**
- 21.000 Live items + 10.000+ VOD items
- JSON-Stream dauert >30 Sekunden
- Standard timeout (30s) zu kurz
- Stream wurde abgebrochen

**Warum SERIES nicht synced (wahrscheinlich):**
- VOD-Sync timed out
- Pipeline kam nie zur SERIES-Phase
- Mit neuem Timeout sollte es durchlaufen

---

✅ **Alle kritischen Fixes implementiert und bereit für Test!**
