# Sync-Optimierung & "Neue Episoden" Feature – Vollständige Analyse

> **Erstellt:** 2025-01-10  
> **Status:** Analyse abgeschlossen – Implementierung erforderlich  
> **Bezug:** XTREAM_PIPELINE_FIELD_AUDIT.md P7 (`lastModified`)

---

## 1. Executive Summary

### Aktueller Stand

| Feature | Status | Details |
|---------|--------|---------|
| **`lastModifiedTimestamp` Feld** | ✅ Implementiert | Fliesst durch gesamte Pipeline bis `NX_WorkSourceRef.sourceLastModifiedMs` |
| **Inkrementeller Sync (Count-Check)** | ✅ Aktiv | `XtreamCatalogScanWorker.runIncrementalSync()` prüft VOD/Series/Live Counts |
| **Inkrementeller Sync (Delta-Fetch)** | ❌ TODO | Kommentar im Code: "TODO: Implement delta fetch using `added` timestamp" |
| **"Neue Episoden" Badge** | ❌ Nicht implementiert | `FishTile` hat `NewBadge`, aber keine Logik für Series-Updates |
| **"Recently Updated" Row** | ⚠️ Teilweise | `observeRecentlyUpdated()` existiert, nutzt aber `NX_Work.updatedAt`, nicht `sourceLastModifiedMs` |

### Kernproblem

Das `sourceLastModifiedMs` Feld wird **persistiert aber nicht genutzt**:

1. **Nicht für Sync-Optimierung:** Incremental Sync vergleicht nur Counts, nicht Timestamps
2. **Nicht für UI-Features:** "New Episodes" Badge nutzt `createdAt` statt `lastModified`
3. **Nicht für Queries:** Keine Repository-Methode fragt nach "Series mit neuen Episoden"

---

## 2. Datenfluss-Analyse

### 2.1 Aktueller Datenfluss (✅ funktioniert)

```
Xtream API Response
    │
    │ series.last_modified = "1721976903" (Unix seconds)
    ▼
XtreamRawMetadataExtensions.kt
    │
    │ lastModifiedTimestamp = series.lastModified * 1000 (→ ms)
    ▼
RawMediaMetadata (core/model)
    │
    │ lastModifiedTimestamp: Long? = 1721976903000
    ▼
NxCatalogWriter.kt
    │
    │ sourceLastModifiedMs = raw.lastModifiedTimestamp
    ▼
NX_WorkSourceRef (ObjectBox Entity)
    │
    │ sourceLastModifiedMs: Long? = 1721976903000
    ▼
[HIER ENDET ES - KEINE WEITERE NUTZUNG]
```

### 2.2 Fehlende Nutzungen

| Use Case | Was fehlt | Wo |
|----------|-----------|-----|
| **Incremental Sync** | Delta-Fetch via `sourceLastModifiedMs` | `XtreamCatalogScanWorker` |
| **New Episodes Detection** | Query "Series where SourceRef.lastModified > lastSyncTime" | `NxWorkSourceRefRepository` |
| **UI Badge** | `hasNewEpisodes` Flag im Home-Repository | `NxHomeContentRepositoryImpl` |
| **Watchlist Sort** | Sortierung nach `sourceLastModifiedMs` | `NxWorkUserStateRepository` |

---

## 3. Erforderliche Änderungen

### 3.1 Repository Layer (core/model + infra/data-nx)

#### A) NxWorkSourceRefRepository.kt – Neue Methode

```kotlin
// core/model/.../NxWorkSourceRefRepository.kt

/**
 * Find series with new episodes since a given timestamp.
 * Used for "New Episodes" UI badge and notifications.
 *
 * @param sinceMs Unix timestamp in milliseconds
 * @param limit Max results
 * @return List of SourceRefs where sourceLastModifiedMs > sinceMs
 */
suspend fun findSeriesWithUpdatedEpisodes(
    sinceMs: Long,
    limit: Int = 100,
): List<SourceRef>
```

#### B) NxWorkSourceRefRepositoryImpl.kt – Implementation

```kotlin
// infra/data-nx/.../NxWorkSourceRefRepositoryImpl.kt

override suspend fun findSeriesWithUpdatedEpisodes(
    sinceMs: Long,
    limit: Int,
): List<SourceRef> = withContext(Dispatchers.IO) {
    box.query(
        NX_WorkSourceRef_.sourceLastModifiedMs.greaterThan(sinceMs)
            .and(NX_WorkSourceRef_.sourceType.equal(SourceType.XTREAM.name))
    )
        .orderDesc(NX_WorkSourceRef_.sourceLastModifiedMs)
        .build()
        .find(0, limit.toLong())
        .map { it.toDomain() }
}
```

### 3.2 Home Repository (infra/data-nx/home)

#### A) Erweitere HomeMediaItem

```kotlin
// core/home-domain/.../HomeMediaItem.kt

data class HomeMediaItem(
    // ... existing fields ...
    val isNew: Boolean = false,
    val hasNewEpisodes: Boolean = false,  // ← NEW
    // ...
)
```

#### B) NxHomeContentRepositoryImpl – hasNewEpisodes Logic

```kotlin
// infra/data-nx/home/NxHomeContentRepositoryImpl.kt

@OptIn(ExperimentalCoroutinesApi::class)
override fun observeSeries(): Flow<List<HomeMediaItem>> {
    val lastUserCheckMs = /* Load from preferences */
    
    return combine(
        workRepository.observeByType(WorkType.SERIES, limit = SERIES_LIMIT),
        sourceRefRepository.findSeriesWithUpdatedEpisodes(sinceMs = lastUserCheckMs)
    ) { works, updatedRefs ->
        val updatedWorkKeys = updatedRefs.map { it.workKey }.toSet()
        
        works.map { work ->
            work.toHomeMediaItemFast(
                sourceType = ...,
                hasNewEpisodes = work.workKey in updatedWorkKeys,
            )
        }
    }
}
```

### 3.3 FishTile UI (core/ui-layout)

#### A) Erweitere FishTile für "New Episodes" Badge

```kotlin
// core/ui-layout/.../FishTile.kt

@Composable
fun FishTile(
    // ... existing params ...
    isNew: Boolean = false,
    hasNewEpisodes: Boolean = false,  // ← NEW
) {
    Box(...) {
        // ... existing content ...
        
        // "NEW" badge for newly added items
        if (isNew) {
            NewBadge(...)
        }
        
        // "NEW EP." badge for series with new episodes
        if (hasNewEpisodes && !isNew) {
            NewEpisodesBadge(...)
        }
    }
}

@Composable
fun NewEpisodesBadge(modifier: Modifier = Modifier) {
    Surface(
        color = FishTheme.colors.secondary,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Text(
            text = "NEW EP.",
            style = FishTheme.typography.labelSmall,
            color = FishTheme.colors.onSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}
```

### 3.4 Sync Worker (app-v2/work)

#### A) Erweitere runIncrementalSync für Delta-Fetch

```kotlin
// app-v2/.../XtreamCatalogScanWorker.kt

private suspend fun runIncrementalSync(...): Result {
    // Step 1: Count comparison (existing)
    val countsDiffer = ...
    
    if (!countsDiffer) {
        // Counts match - but series might have updated episodes!
        // Check sourceLastModifiedMs for updates
        val lastSyncMs = checkpointStore.getXtreamLastSyncTimestamp()
        val updatedSeries = sourceRefRepository.findSeriesWithUpdatedEpisodes(
            sinceMs = lastSyncMs,
            limit = 100
        )
        
        if (updatedSeries.isEmpty()) {
            // No updates at all
            return Result.success(...)
        }
        
        // Mark series as having new episodes
        // UI will show badge via observeSeries()
        checkpointStore.saveXtreamLastSyncTimestamp(System.currentTimeMillis())
        return Result.success(itemsPersisted = 0, hasEpisodeUpdates = true)
    }
    
    // Step 2: Delta-Fetch for new items (existing TODO)
    // ...
}
```

### 3.5 Checkpoint Store (Preferences)

#### A) User's "Last Check" Timestamp

```kotlin
// infra/data-prefs oder app-v2/prefs

interface SyncCheckpointStore {
    // Existing
    suspend fun getXtreamLastSyncTimestamp(): Long
    suspend fun saveXtreamLastSyncTimestamp(timestamp: Long)
    
    // NEW: When user last viewed the Series library
    suspend fun getUserLastSeriesCheckTimestamp(): Long
    suspend fun saveUserLastSeriesCheckTimestamp(timestamp: Long)
}
```

---

## 4. Todo-Liste (Implementierungsreihenfolge)

### Phase 1: Repository Layer (Grundlage)

| # | Task | Datei | Priorität |
|---|------|-------|-----------|
| 1 | Add `findSeriesWithUpdatedEpisodes()` to interface | `NxWorkSourceRefRepository.kt` | P0 |
| 2 | Implement query in repository | `NxWorkSourceRefRepositoryImpl.kt` | P0 |
| 3 | Add Index auf `sourceLastModifiedMs` | `NxEntities.kt` | P0 |

### Phase 2: Domain/UI Model (Erweiterung)

| # | Task | Datei | Priorität |
|---|------|-------|-----------|
| 4 | Add `hasNewEpisodes` to HomeMediaItem | `HomeMediaItem.kt` | P1 |
| 5 | Add `hasNewEpisodes` param to FishTile | `FishTile.kt` | P1 |
| 6 | Create `NewEpisodesBadge` composable | `FishTile.kt` | P1 |

### Phase 3: Integration (Verdrahtung)

| # | Task | Datei | Priorität |
|---|------|-------|-----------|
| 7 | Wire `hasNewEpisodes` in `observeSeries()` | `NxHomeContentRepositoryImpl.kt` | P1 |
| 8 | Add user last-check timestamp preference | `SyncCheckpointStore.kt` / `AppPreferences.kt` | P2 |
| 9 | Update timestamp when user opens Series | `SeriesLibraryScreen.kt` / ViewModel | P2 |

### Phase 4: Sync-Optimierung (Optional Enhancement)

| # | Task | Datei | Priorität |
|---|------|-------|-----------|
| 10 | Enhance incremental sync to use `lastModified` | `XtreamCatalogScanWorker.kt` | P2 |
| 11 | Add delta-fetch logic for changed series | `XtreamCatalogScanWorker.kt` | P3 |

---

## 5. Betroffene Dateien (Komplett)

### Zu modifizieren:

```
core/model/src/main/java/com/fishit/player/core/model/repository/
  └── NxWorkSourceRefRepository.kt          # Interface erweitern

core/persistence/src/main/java/com/fishit/player/core/persistence/obx/
  └── NxEntities.kt                          # Index hinzufügen

core/home-domain/src/main/kotlin/com/fishit/player/core/home/domain/
  └── HomeMediaItem.kt                       # hasNewEpisodes field

core/ui-layout/src/main/java/com/fishit/player/core/ui/layout/
  ├── FishTile.kt                            # hasNewEpisodes param + Badge
  └── FishRow.kt                             # hasNewEpisodes param forwarding

infra/data-nx/src/main/java/com/fishit/player/infra/data/nx/
  ├── repository/NxWorkSourceRefRepositoryImpl.kt  # Query implementation
  └── home/NxHomeContentRepositoryImpl.kt          # Wire to HomeMediaItem
```

### Zu erstellen (optional):

```
core/ui-layout/src/main/java/com/fishit/player/core/ui/layout/
  └── Badges.kt                              # NewEpisodesBadge (oder in FishTile.kt)
```

---

## 6. Risiken & Mitigationen

| Risiko | Wahrscheinlichkeit | Mitigation |
|--------|-------------------|------------|
| Performance bei großen Listen | Mittel | Batch-Query + Index auf `sourceLastModifiedMs` |
| Falsche "New" Anzeige nach Migration | Hoch | Initialer Timestamp auf "jetzt" setzen nach erstem Sync |
| Badge-Spam bei vielen Updates | Gering | Limit auf max. 20 Series mit Badge |

---

## 7. Testplan

### Unit Tests

1. `NxWorkSourceRefRepositoryImpl.findSeriesWithUpdatedEpisodes()`
   - Gibt nur SourceRefs mit `lastModifiedMs > sinceMs` zurück
   - Respektiert Limit
   - Korrekte Sortierung DESC

2. `NxHomeContentRepositoryImpl.observeSeries()`
   - `hasNewEpisodes = true` für aktualisierte Series
   - `hasNewEpisodes = false` für nicht-aktualisierte

### Integration Tests

1. Full Sync → Series haben `sourceLastModifiedMs`
2. User öffnet Series-Bibliothek → Badge verschwindet nach Timestamp-Update

---

## 8. Zusammenfassung

**Das `sourceLastModifiedMs` Feld ist vorhanden aber ungenutzt.** Die Implementierung erfordert:

1. ✅ Daten sind da (Feld in DB)
2. ❌ Repository-Query fehlt
3. ❌ UI-Model fehlt (`hasNewEpisodes`)
4. ❌ Badge-Composable fehlt
5. ❌ Verdrahtung in Home fehlt
6. ❌ User-Timestamp für "last check" fehlt

**Geschätzter Aufwand:** ~4-6 Stunden für Phase 1-3 (Core Feature)
