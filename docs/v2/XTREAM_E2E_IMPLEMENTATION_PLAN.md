# 🎯 Xtream End-to-End Implementation Plan

> **Ziel:** App startet → User loggt sich ein (Xtream) → Backfill startet → VODs/Serien/LiveTV erscheinen als Tiles → Playback funktioniert sofort → Details werden on-demand nachgeladen

**Branch:** `architecture/v2-bootstrap`  
**Erstellt:** 2024-12-30  
**Status:** Ready for Implementation

---

## 📊 Ist-Analyse (Current State)

| Komponente | Status | Probleme |
|------------|--------|----------|
| Login/Auth | ✅ Funktioniert | - |
| SourceActivationStore | ✅ Funktioniert | - |
| CatalogSync Trigger | ✅ Funktioniert | - |
| VOD Persist | ✅ Funktioniert | - |
| Series Persist | ⚠️ Batch-Problem | Flush bei Budget-Exceeded |
| LiveTV Persist | ⚠️ Batch-Problem | Kleine Batches nie voll |
| UI Data Loading | ✅ ObjectBox Flows | - |
| Playback | ❌ Headers fehlen | HTTP 403/520 |
| Detail Enrichment | ⚠️ Nicht angebunden | Service existiert |

---

## 🔄 Architektur-Kette (Contract-Konform)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         XTREAM END-TO-END FLOW                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌───────┐ │
│  │ TRANSPORT│───▶│ PIPELINE │───▶│ CATALOG  │───▶│   DATA   │───▶│  UI   │ │
│  │          │    │          │    │   SYNC   │    │          │    │       │ │
│  │ XtreamApi│    │ XtreamCat│    │ CatalogSy│    │ ObxVod   │    │Library│ │
│  │ Client   │    │ alogPipe │    │ ncService│    │ ObxSeries│    │Screen │ │
│  │          │    │          │    │          │    │ ObxLive  │    │       │ │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘    └───────┘ │
│       │                │                │              │              │     │
│       │                │                │              │              │     │
│       ▼                ▼                ▼              ▼              ▼     │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                        DATA TRANSFORMATION                           │  │
│  │                                                                      │  │
│  │  XtreamVodStream ─▶ XtreamVodItem ─▶ RawMediaMetadata ─▶ ObxVod     │  │
│  │       (API)            (Pipeline)       (Canonical)        (DB)      │  │
│  │                              │                                       │  │
│  │                              ▼                                       │  │
│  │                    toRawMediaMetadata()                              │  │
│  │                    ├── sourceId: "xtream:vod:{id}"                   │  │
│  │                    ├── sourceType: XTREAM                            │  │
│  │                    ├── mediaType: MOVIE/SERIES/EPISODE               │  │
│  │                    ├── playbackHints: { vodId, containerExt }        │  │
│  │                    └── globalId: "" (EMPTY! - Normalizer assigns)    │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 📋 Implementierungs-Phasen

### Phase 0: Bereits Erledigt (Fixes vom 30.12.2024)

| Task | Datei | Status |
|------|-------|--------|
| Player HTTP Headers | `InternalPlayerSession.kt` | ✅ Done |
| Live Batch Limit (100) | `DefaultCatalogSyncService.kt` | ✅ Done |
| Debug Logging | `InternalPlayerSession.kt` | ✅ Done |

---

### Phase 1: Catalog Sync Robustness 🔧

**Ziel:** Series und LiveTV werden zuverlässig persistiert, auch bei Budget-Exceeded

#### Task 1.1: Series Batch Limit (wie Live)

**Datei:** `core/catalog-sync/src/main/java/com/fishit/player/core/catalogsync/DefaultCatalogSyncService.kt`

**Problem:** Serien kommen später als VODs, Batch wird nie voll vor Budget-Exceeded

**Lösung:** Separater Series-Batch mit kleinerem Limit

```kotlin
// In syncXtream() - analog zum Live-Batch
val seriesBatch = mutableListOf<RawMediaMetadata>()

when (event.item.raw.mediaType) {
    MediaType.LIVE_CHANNEL -> liveBatch.add(raw)
    MediaType.SERIES, MediaType.SERIES_EPISODE -> seriesBatch.add(raw)
    else -> catalogBatch.add(raw)
}

// Series Batch flush (Limit 100)
val seriesBatchLimit = minOf(100, syncConfig.batchSize)
if (seriesBatch.size >= seriesBatchLimit) {
    persistXtreamCatalogBatch(seriesBatch)
    itemsPersisted += seriesBatch.size
    seriesBatch.clear()
}
```

#### Task 1.2: Guaranteed Final Flush

**Problem:** Bei Budget-Exceeded emittiert der Flow nie `ScanCompleted` oder `ScanCancelled`

**Lösung:** `finally` Block im Flow

```kotlin
// In syncXtream() - nach dem try/catch
} finally {
    // GUARANTEED FLUSH: Auch bei Budget-Exceeded oder anderen Exits
    if (catalogBatch.isNotEmpty()) {
        persistXtreamCatalogBatch(catalogBatch)
        itemsPersisted += catalogBatch.size
    }
    if (seriesBatch.isNotEmpty()) {
        persistXtreamCatalogBatch(seriesBatch)
        itemsPersisted += seriesBatch.size
    }
    if (liveBatch.isNotEmpty()) {
        persistXtreamLiveBatch(liveBatch)
        itemsPersisted += liveBatch.size
    }
    UnifiedLog.i(TAG) { "Final flush: total persisted=$itemsPersisted" }
}
```

---

### Phase 2: Detail Enrichment On-Demand 🔍

**Ziel:** Details werden sofort nachgeladen wenn Detail-Seite geöffnet wird

#### Task 2.1: Enrichment in UnifiedDetailViewModel aktivieren

**Datei:** `feature/detail/src/main/java/com/fishit/player/feature/detail/UnifiedDetailViewModel.kt`

**Aktuelle Situation:** `DetailEnrichmentService` existiert, wird aber nicht aufgerufen

**Änderung:**

```kotlin
@HiltViewModel
class UnifiedDetailViewModel @Inject constructor(
    private val useCases: UnifiedDetailUseCases,
    private val detailEnrichmentService: DetailEnrichmentService, // NEU
) : ViewModel() {

    private fun handleMediaState(mediaState: UnifiedMediaState) {
        when (mediaState) {
            is UnifiedMediaState.Success -> {
                // Initial state update (fast)
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        media = mediaState.media,
                        resume = mediaState.resume,
                        selectedSource = mediaState.selectedSource,
                        sourceGroups = useCases.sortSourcesForDisplay(mediaState.media.sources),
                    )
                }
                
                // Background enrichment (if needed)
                viewModelScope.launch {
                    val enriched = detailEnrichmentService.enrichIfNeeded(mediaState.media)
                    if (enriched !== mediaState.media) {
                        _state.update { it.copy(media = enriched) }
                    }
                }
            }
            // ... other cases
        }
    }
}
```

#### Task 2.2: Enrichment Service Logging verbessern

**Datei:** `feature/detail/src/main/java/com/fishit/player/feature/detail/enrichment/DetailEnrichmentService.kt`

```kotlin
suspend fun enrichIfNeeded(media: CanonicalMediaWithSources): CanonicalMediaWithSources {
    val startMs = System.currentTimeMillis()
    
    // ... existing logic ...
    
    UnifiedLog.i(TAG) { 
        "enrichIfNeeded: completed in ${System.currentTimeMillis() - startMs}ms " +
        "canonicalId=${media.canonicalId.key.value} enriched=${result !== media}"
    }
    return result
}
```

---

### Phase 3: UI Tile Display Optimization 🎨

**Ziel:** VODs, Serien, LiveTV erscheinen als performante Tiles in Rows

#### Task 3.1: Verify LibraryScreen Flow

**Prüfen:** Daten fließen von ObjectBox → LibraryContentRepository → LibraryViewModel → LibraryScreen

**Dateien:**
- `infra/data-xtream/src/main/java/com/fishit/player/infra/data/xtream/LibraryContentRepositoryAdapter.kt`
- `feature/library/src/main/java/com/fishit/player/feature/library/LibraryViewModel.kt`
- `feature/library/src/main/java/com/fishit/player/feature/library/LibraryScreen.kt`

#### Task 3.2: Verify LiveScreen Flow

**Prüfen:** Daten fließen von ObjectBox → LiveContentRepository → LiveViewModel → LiveScreen

**Dateien:**
- `infra/data-xtream/src/main/java/com/fishit/player/infra/data/xtream/LiveContentRepositoryAdapter.kt`
- `feature/live/src/main/java/com/fishit/player/feature/live/LiveViewModel.kt`
- `feature/live/src/main/java/com/fishit/player/feature/live/LiveScreen.kt`

#### Task 3.3: Optional - FishTile Migration

**Falls Zeit:** Material3 `Card` durch `FishTile` aus `core:ui-layout` ersetzen für konsistentes TV-Look

---

### Phase 4: Playback End-to-End Verification 🎬

**Ziel:** Playback funktioniert für VOD, Serien, LiveTV

#### Task 4.1: VOD Playback Test

```
Library Tab → VOD Tile Click → Detail Screen → Play Button → Player
                                                     │
                                    PlaybackContext mit:
                                    - canonicalId: "xtream:vod:123"
                                    - sourceType: XTREAM
                                    - extras: { contentType: "vod", vodId: "123" }
```

#### Task 4.2: Series Episode Playback Test

```
Library Tab → Series Tile → Series Detail → Episode Select → Player
                                                    │
                                    PlaybackContext mit:
                                    - canonicalId: "xtream:series:456:s1e1"
                                    - sourceType: XTREAM
                                    - extras: { contentType: "series", seriesId: "456", ... }
```

#### Task 4.3: Live Channel Playback Test

```
Live Tab → Channel Tile Click → Player (direct)
                    │
    PlaybackContext mit:
    - canonicalId: "xtream:live:789"
    - sourceType: XTREAM
    - extras: { contentType: "live", streamId: "789" }
```

---

### Phase 5: Backfill Background Worker 🔄

**Ziel:** Details werden im Hintergrund nachgeladen (VOD_INFO, SERIES_INFO)

#### Task 5.1: Verify Worker Phase Execution

**Datei:** `app-v2/src/main/java/com/fishit/player/v2/work/XtreamCatalogScanWorker.kt`

**Prüfen:** VOD_INFO und SERIES_INFO Phasen werden erreicht nach LIST-Phasen

#### Task 5.2: Info Backfill Throttling

**Problem:** Zu viele API-Calls können Panel blockieren

**Aktuelle Einstellung:**
```kotlin
private const val INFO_BACKFILL_BATCH_SIZE = 10
private const val INFO_BACKFILL_THROTTLE_MS = 200L
```

**Optional anpassen:** Falls Panel-Rate-Limits triggern

---

## 📁 Betroffene Dateien (Zusammenfassung)

| Phase | Datei | Änderungstyp |
|-------|-------|--------------|
| 1.1 | `core/catalog-sync/.../DefaultCatalogSyncService.kt` | Modify |
| 1.2 | `core/catalog-sync/.../DefaultCatalogSyncService.kt` | Modify |
| 2.1 | `feature/detail/.../UnifiedDetailViewModel.kt` | Modify |
| 2.2 | `feature/detail/.../DetailEnrichmentService.kt` | Modify |
| 3.x | Verification Only | - |
| 4.x | Verification Only | - |
| 5.x | Verification Only | - |

---

## 🧪 Testplan

### Manueller Test-Flow

1. **Clean Install** → App starten
2. **Xtream Login** → URL eingeben → Verbinden
3. **Warten** → CatalogSync sollte automatisch starten (5s nach SourceActivation)
4. **Library Tab** → VODs sollten erscheinen (nach ~30s für große Panels)
5. **VOD Tile Click** → Detail Screen → Poster, Titel sichtbar
6. **Play Button** → Player startet → Stream läuft
7. **Series Tab** → Serien sollten erscheinen
8. **Live Tab** → Channels sollten erscheinen
9. **Live Channel Click** → Player startet → Stream läuft

### Log-Checkpoints

```bash
# Login Success
adb logcat | grep "XtreamAuth"

# Catalog Sync Start
adb logcat | grep "CatalogSync"

# Batch Persist
adb logcat | grep "persistXtream"

# Player Headers
adb logcat | grep "InternalPlayerSession.*headers"

# Detail Enrichment
adb logcat | grep "DetailEnrichment"
```

---

## 🚀 Implementierungs-Reihenfolge

```
Phase 1.1 → Phase 1.2 → Phase 2.1 → Phase 2.2 → Verification
   │            │           │           │
   ▼            ▼           ▼           ▼
Series      Guaranteed   Detail VM   Logging
Batch       Final Flush  Enrichment  Improve
```

**Geschätzte Zeit:** 2-3 Stunden für Phase 1-2, dann Verification

---

## ⚠️ Wichtige Constraints (aus AGENTS.md)

- ❌ Pipeline darf NICHT `globalId` setzen (nur `""`)
- ❌ Pipeline darf NICHT direkt TMDB aufrufen
- ❌ Pipeline darf NICHT auf Data Layer (`ObxVod` etc.) zugreifen
- ✅ Normalizer setzt `globalId` über `FallbackCanonicalKeyGenerator`
- ✅ Transport → Pipeline → CatalogSync → Data → UI (strict layer order)
- ✅ PlaybackHints in `RawMediaMetadata.playbackHints` für Factory-Nutzung

---

*Plan erstellt am 2024-12-30 – Ready for Implementation*
