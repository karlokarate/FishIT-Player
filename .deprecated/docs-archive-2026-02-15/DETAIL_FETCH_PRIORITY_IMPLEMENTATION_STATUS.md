# Detail Fetch Priority System - Implementierungsstatus

## ✅ Implementierung abgeschlossen (VOD + Series)

**Datum:** 2026-01-30
**Status:** Build erfolgreich ✅ (1764 tasks)
**Update:** Series Enrichment hinzugefügt

---

## 1. Übersicht

Das Priority-System ist vollständig implementiert und ermöglicht:

- **Sofortige Detail-Fetches** bei Tile-Klicks (HIGH Priority)
- **Kritische Playback-Fetches** für Container Extensions (CRITICAL Priority)  
- **Kooperatives Yielding** des Background-Sync bei User-Aktionen
- **VOD Enrichment** via `getVodInfo()` API
- **Series Enrichment** via `getSeriesInfo()` API

---

## 2. Neue Dateien

### infra/api-priority Module (NEU)

| Datei | Beschreibung |
|-------|-------------|
| `build.gradle.kts` | Module config mit coroutines, hilt, logging |
| `ApiPriority.kt` | Priority enum + PriorityState data class |
| `ApiPriorityDispatcher.kt` | Interface für Priority-Koordination |
| `DefaultApiPriorityDispatcher.kt` | Implementierung mit Semaphore/Mutex/StateFlow |
| `di/ApiPriorityModule.kt` | Hilt @Singleton Bindings |
| `AndroidManifest.xml` | Module manifest |

### Priority Levels

```kotlin
enum class ApiPriority {
    CRITICAL_PLAYBACK,   // Highest - Container ext für Playback
    HIGH_USER_ACTION,    // User clicked tile → Detail fetch
    BACKGROUND_SYNC      // Background catalog sync
}
```

---

## 3. Geänderte Dateien

### settings.gradle.kts
- ✅ `include(":infra:api-priority")` hinzugefügt

### infra/data-detail/build.gradle.kts
- ✅ `implementation(project(":infra:api-priority"))` hinzugefügt

### feature/detail/build.gradle.kts
- ✅ `implementation(project(":infra:api-priority"))` hinzugefügt

### app-v2/build.gradle.kts
- ✅ `implementation(project(":infra:api-priority"))` hinzugefügt

### DetailEnrichmentService.kt (Interface)
- ✅ `enrichImmediate()` Methode hinzugefügt

### DetailEnrichmentServiceImpl.kt
- ✅ `ApiPriorityDispatcher` Injection
- ✅ `enrichImmediate()` mit `withHighPriority()`
- ✅ `ensureEnriched()` mit `withCriticalPriority()`

### SeriesEpisodeUseCases.kt
- ✅ `ApiPriorityDispatcher` Injection für beide UseCases
- ✅ `ensureSeasonsLoadedImmediate()` mit `withHighPriority()`
- ✅ `ensureEpisodesLoadedImmediate()` mit `withHighPriority()`

### UnifiedDetailViewModel.kt
- ✅ `enrichIfNeeded()` → `enrichImmediate()` geändert
- ✅ `ensureSeasonsLoaded()` → `ensureSeasonsLoadedImmediate()` geändert
- ✅ `ensureEpisodesLoaded()` → `ensureEpisodesLoadedImmediate()` geändert

### XtreamCatalogScanWorker.kt
- ✅ `ApiPriorityDispatcher` Injection
- ✅ `shouldYield()` + `awaitHighPriorityComplete()` in collectEnhanced
- ✅ `shouldYield()` + `awaitHighPriorityComplete()` in Channel-Buffered sync
- ✅ `shouldYield()` + `awaitHighPriorityComplete()` in VOD Info Backfill
- ✅ `shouldYield()` + `awaitHighPriorityComplete()` in Series Info Backfill

---

## 4. Flow bei User-Aktion

```
User klickt auf Tile
         │
         ▼
UnifiedDetailViewModel.loadByMediaId()
         │
         ▼
handleMediaState() → SUCCESS
         │
         ▼
detailEnrichmentService.enrichImmediate(media)
         │
         ▼
ApiPriorityDispatcher.withHighPriority() {
    // Setzt highPriorityCount++ → shouldYield() returns true
    // Background Workers pausieren an nächster Batch-Grenze
    
    XtreamApiClient.getVodInfo(vodId)
    
    // highPriorityCount-- → Background kann fortfahren
}
         │
         ▼
UI wird mit Plot, Cast, etc. aktualisiert
```

---

## 5. Flow bei Background Sync Yield

```
XtreamCatalogScanWorker läuft
         │
         ▼
collect { status ->
    // Nach jedem Status-Event:
    if (priorityDispatcher.shouldYield()) {
        // User hat Detail-Screen geöffnet!
        awaitHighPriorityComplete()
        // Pausiert bis User-Fetch fertig
    }
}
```

---

## 6. Series Enrichment (NEU hinzugefügt)

### DetailEnrichmentServiceImpl.kt - Series Support

| Methode | Beschreibung |
|---------|-------------|
| `enrichFromXtream()` | Router: erkennt VOD vs Series anhand SourceKey |
| `enrichVodFromXtream()` | VOD Enrichment via `getVodInfo()` |
| `enrichSeriesFromXtream()` | **NEU:** Series Enrichment via `getSeriesInfo()` |
| `isSeriesSourceKey()` | Erkennt `src:xtream:*:series:*` oder `xtream:series:*` |
| `parseXtreamSeriesId()` | Extrahiert Series ID aus SourceKey |

### SourceKey Format Support

| Format | Beispiel | Erkennung |
|--------|----------|-----------|
| Neues VOD | `src:xtream:acc123:vod:456` | `isVodSourceKey()` |
| Legacy VOD | `xtream:vod:456:mp4` | `isVodSourceKey()` |
| Neues Series | `src:xtream:acc123:series:789` | `isSeriesSourceKey()` |
| Legacy Series | `xtream:series:789` | `isSeriesSourceKey()` |

### Series Info Mapping

| API Feld | Ziel Feld | Resolver |
|----------|-----------|----------|
| `info.name` | - | (canonicalTitle bleibt) |
| `info.year` | `year` | `toIntOrNull()` |
| `info.tmdbId` | `tmdb` | `TmdbRef(TV, id)` |
| `info.imdbId` | `externalIds.imdbId` | Direkt |
| `info.cover` / `posterPath` | `poster` | `ImageRef.Http()` |
| `info.backdropPath[0]` | `backdrop` | `ImageRef.Http()` |
| `info.resolvedPlot` | `plot` | plot/description/overview |
| `info.resolvedGenre` | `genres` | genre/genres |
| `info.director` | `director` | Direkt |
| `info.resolvedCast` | `cast` | cast/actors |
| `info.rating` / `rating5Based` | `rating` | 0-10 scale |
| `info.resolvedTrailer` | `trailer` | youtubeTrailer/trailer |

---

## 7. Nächste Schritte

- [ ] Testing auf echtem Device mit aktivem Background-Sync
- [ ] Logcat-Monitoring der Priority-Events
- [ ] Performance-Messung: Detail-Fetch Latenz mit/ohne aktiven Sync
- [x] **Series Enrichment implementiert** (2026-01-30)

---

## 8. Platin Sync Continuation Strategy

Siehe **`PLATIN_SYNC_CONTINUATION_STRATEGY.md`** für:

- Priority Hierarchy (CRITICAL > HIGH > BACKGROUND)
- Yield Protocol (Schritt-für-Schritt)
- Resume Behavior nach Pause
- Edge Cases (Rapid Navigation, Playback, Cancellation)
- Testing Scenarios

---

## 9. Logs zu beobachten

```
# Priority-Events
adb logcat | grep -E "ApiPriority|DetailEnrich|LoadSeriesSeasons|XtreamCatalogScan"

# Beispiel-Output:
I/ApiPriority: withHighPriority START tag=DetailEnrich:movie:123
D/XtreamCatalogScanWorker: VOD backfill yielding for high-priority user action
I/DetailEnrichment: enrichImmediate: completed in 245ms canonicalId=movie:123 hasPlot=true
D/XtreamCatalogScanWorker: VOD backfill resuming after high-priority completion
I/ApiPriority: withHighPriority END tag=DetailEnrich:movie:123 duration=245ms
```

---

## 10. Zusammenfassung

✅ **Sofortiges Detail-Fetching implementiert**
- Tile-Klick → Immediate HIGH priority API call
- Background-Sync pausiert kooperativ
- Keine Wartezeit mehr für User

✅ **VOD Enrichment**
- `getVodInfo()` für Filme
- Plot, Cast, Genres, Rating, Trailer
- Container Extension für Playback

✅ **Series Enrichment** (NEU)
- `getSeriesInfo()` für Serien
- Gleiche Felder wie VOD (Plot, Cast, etc.)
- TMDB Type = TV (nicht MOVIE)

✅ **Kritische Playback-Fetches**
- Container Extension Fetch mit CRITICAL priority
- Blockiert andere Operationen für garantierte Playback-Bereitschaft

✅ **Background-Sync ist kooperativ**
- Prüft `shouldYield()` an Batch-Grenzen
- Pausiert automatisch bei User-Aktionen
- Fährt fort wenn User-Fetch abgeschlossen
