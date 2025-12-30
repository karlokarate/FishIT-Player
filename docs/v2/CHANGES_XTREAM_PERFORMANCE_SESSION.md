# Änderungen: Xtream Performance & Episode Strategy + Source Selection Refactoring

> **Datum:** 30. Dezember 2025  
> **Branch:** architecture/v2-bootstrap  
> **Scope:** Xtream-Katalog-Sync-Performance, Episode Lazy Loading, Source Selection SSOT

---

## Übersicht

Diese Session umfasste zwei Hauptaufgaben:

1. **Part A+B: Xtream Performance & Platinum Episode Handling**
   - Phase-Ordering für schnelle UI-Reaktion (Live → VOD → Series)
   - Zeit-basiertes Flushing (1200ms) für progressive Updates
   - Lazy Loading von Episodes via UseCase-Pattern
   - Season/Episode Index mit TTL-basierter Invalidierung

2. **Source Selection Refactoring**
   - Eliminierung von `selectedSource: MediaSourceRef` als gespeicherter State
   - Source Selection wird jetzt zur Laufzeit aus `media.sources` abgeleitet
   - Race-proof Playback via `ensureEnriched()`

---

## Geänderte/Neue Dateien

### Part A: Catalog Sync Performance

| Datei | Typ | Beschreibung |
|-------|-----|--------------|
| [SyncPhaseConfig.kt](core/catalog-sync/.../SyncPhaseConfig.kt) | **NEU** | Per-Phase Batch-Konfiguration (Live=400, Movies=250, Series=150) |
| [SyncPerfMetrics.kt](core/catalog-sync/.../SyncPerfMetrics.kt) | **NEU** | Performance-Metriken für Debug-Builds |
| [SyncBatchManager.kt](core/catalog-sync/.../SyncBatchManager.kt) | **NEU** | Zeit-basiertes Batch-Management (1200ms Flush) |
| [CatalogSyncContract.kt](core/catalog-sync/.../CatalogSyncContract.kt) | MODIFIZIERT | +`SyncActiveState`, +`syncXtreamEnhanced()` |
| [DefaultCatalogSyncService.kt](core/catalog-sync/.../DefaultCatalogSyncService.kt) | MODIFIZIERT | Enhanced Sync mit Time-Based Batching |
| [XtreamCatalogPipelineImpl.kt](pipeline/xtream/.../XtreamCatalogPipelineImpl.kt) | MODIFIZIERT | Scan-Reihenfolge: Live → VOD → Series |

### Part B: Platinum Episode Handling

| Datei | Typ | Beschreibung |
|-------|-----|--------------|
| [ObxEntities.kt](core/persistence/.../ObxEntities.kt) | MODIFIZIERT | +`ObxSeasonIndex`, +`ObxEpisodeIndex` Entities |
| [XtreamSeriesIndexRepository.kt](infra/data-xtream/.../XtreamSeriesIndexRepository.kt) | **NEU** | Repository-Interface für Season/Episode Index |
| [ObxXtreamSeriesIndexRepository.kt](infra/data-xtream/.../ObxXtreamSeriesIndexRepository.kt) | **NEU** | ObjectBox-Implementierung |
| [SeriesEpisodeUseCases.kt](feature/detail/.../series/SeriesEpisodeUseCases.kt) | **NEU** | LoadSeriesSeasonsUseCase, LoadSeasonEpisodesUseCase, EnsureEpisodePlaybackReadyUseCase |
| [XtreamDataModule.kt](infra/data-xtream/.../di/XtreamDataModule.kt) | MODIFIZIERT | +DI Binding für XtreamSeriesIndexRepository |

### Source Selection Refactoring

| Datei | Typ | Beschreibung |
|-------|-----|--------------|
| [SourceSelection.kt](feature/detail/.../SourceSelection.kt) | **NEU** | `resolveActiveSource()` für derived Selection |
| [UnifiedDetailViewModel.kt](feature/detail/.../UnifiedDetailViewModel.kt) | MODIFIZIERT | `selectedSourceKey` statt `selectedSource` |
| [UnifiedDetailUseCases.kt](feature/detail/.../UnifiedDetailUseCases.kt) | MODIFIZIERT | Entfernt `selectedSource` aus Success State |
| [DetailEnrichmentService.kt](feature/detail/.../enrichment/DetailEnrichmentService.kt) | MODIFIZIERT | +`ensureEnriched()` für race-proof Playback |
| [DetailScreen.kt](feature/detail/.../ui/DetailScreen.kt) | MODIFIZIERT | Nutzt `state.activeSource` statt `state.selectedSource` |

### Debug/Diagnostics Improvements

| Datei | Typ | Beschreibung |
|-------|-----|--------------|
| [LeakDiagnostics.kt](feature/settings/.../debug/LeakDiagnostics.kt) | MODIFIZIERT | +`getDetailedStatus()`, Noise Control |
| [LeakDiagnosticsImpl.kt](feature/settings/src/debug/.../LeakDiagnosticsImpl.kt) | MODIFIZIERT | Severity-basierte Leak-Erkennung |
| [LeakDiagnosticsImpl.kt](feature/settings/src/release/.../LeakDiagnosticsImpl.kt) | MODIFIZIERT | No-op Release-Implementierung |
| [DebugScreen.kt](feature/settings/.../DebugScreen.kt) | MODIFIZIERT | Neuer LeakCanary-Diagnostics-Bereich |
| [DebugViewModel.kt](feature/settings/.../DebugViewModel.kt) | MODIFIZIERT | +GC Request, +Heap Dump Trigger |

### Tests

| Datei | Typ | Beschreibung |
|-------|-----|--------------|
| [XtreamPlaybackSourceFactoryImplTest.kt](playback/xtream/.../XtreamPlaybackSourceFactoryImplTest.kt) | MODIFIZIERT | Container Extension Tests |

---

## Funktionale Auswirkungen

### 1. Sync-Reihenfolge (Perceived Speed)

**Vorher:**
```
VOD → Series → Episodes → Live
```

**Nachher:**
```
Live → VOD → Series → (Episodes: lazy loaded)
```

**Effekt:** Live-Kacheln erscheinen in ~1-2 Sekunden statt am Ende.

### 2. Episode Lazy Loading

**Vorher:** Alle Episodes (~100k+) wurden beim Login gesynct.

**Nachher:**
- Episodes werden übersprungen beim initialen Sync
- `LoadSeasonEpisodesUseCase` lädt on-demand
- `EnsureEpisodePlaybackReadyUseCase` garantiert playbackHints vor Wiedergabe

### 3. Source Selection SSOT

**Vorher:**
```kotlin
data class UnifiedDetailState(
    val selectedSource: MediaSourceRef? // Snapshot, wird stale
)
```

**Nachher:**
```kotlin
data class UnifiedDetailState(
    val selectedSourceKey: PipelineItemId? // Nur Key, Source wird derived
) {
    val activeSource: MediaSourceRef?
        get() = SourceSelection.resolveActiveSource(media, selectedSourceKey, resume)
}
```

**Effekt:** Keine Race Conditions mehr bei asynchroner Enrichment.

### 4. Race-Proof Playback

**Flow:**
1. User klickt Play
2. `resolveActiveSource()` aus aktuellem State
3. Check: Hat Source alle playbackHints?
4. Falls nein → `ensureEnriched()` mit 8s Timeout
5. Re-resolve activeSource aus frischem State
6. Erst dann `StartPlayback` Event

---

## Vollständige Diffs

### core/catalog-sync/CatalogSyncContract.kt

```diff
@@ -93,6 +93,22 @@ data class SyncConfig(
     }
 }
 
+/**
+ * Active sync state for UI flow throttling.
+ *
+ * When sync is active, UI should debounce DB-driven flows to avoid
+ * materializing huge lists repeatedly during rapid inserts.
+ *
+ * @property isActive Whether any sync is currently running
+ * @property source The source currently being synced (if any)
+ * @property currentPhase Current phase (LIVE/MOVIES/SERIES)
+ */
+data class SyncActiveState(
+    val isActive: Boolean = false,
+    val source: String? = null,
+    val currentPhase: String? = null,
+)
+
 interface CatalogSyncService {
+    val syncActiveState: kotlinx.coroutines.flow.StateFlow<SyncActiveState>
+
+    fun syncXtreamEnhanced(
+        includeVod: Boolean = true,
+        includeSeries: Boolean = true,
+        includeEpisodes: Boolean = false, // Lazy load episodes by default
+        includeLive: Boolean = true,
+        config: EnhancedSyncConfig = EnhancedSyncConfig.DEFAULT,
+    ): Flow<SyncStatus>
+
+    fun getLastSyncMetrics(): SyncPerfMetrics?
```

### core/persistence/ObxEntities.kt (Auszug)

```diff
+// Season/Episode Index Entities (Part B: Platinum Episode Handling)
+
+@Entity
+data class ObxSeasonIndex(
+    @Id var id: Long = 0,
+    @Index var seriesId: Int = 0,
+    @Index var seasonNumber: Int = 0,
+    var episodeCount: Int? = null,
+    var name: String? = null,
+    var coverUrl: String? = null,
+    var airDate: String? = null,
+    @Index var lastUpdatedMs: Long = System.currentTimeMillis(),
+)
+
+@Entity
+data class ObxEpisodeIndex(
+    @Id var id: Long = 0,
+    @Index var seriesId: Int = 0,
+    @Index var seasonNumber: Int = 0,
+    @Index var episodeNumber: Int = 0,
+    @Index var sourceKey: String = "",
+    @Index var episodeId: Int? = null,
+    var title: String? = null,
+    var thumbUrl: String? = null,
+    var durationSecs: Int? = null,
+    var plotBrief: String? = null,
+    var rating: Double? = null,
+    var airDate: String? = null,
+    var playbackHintsJson: String? = null,
+    @Index var lastUpdatedMs: Long = System.currentTimeMillis(),
+    @Index var playbackHintsUpdatedMs: Long = 0,
+) {
+    companion object {
+        const val INDEX_TTL_MS = 7 * 24 * 60 * 60 * 1000L      // 7 days
+        const val PLAYBACK_HINTS_TTL_MS = 30 * 24 * 60 * 60 * 1000L  // 30 days
+    }
+    
+    val isIndexStale: Boolean
+        get() = System.currentTimeMillis() - lastUpdatedMs > INDEX_TTL_MS
+    
+    val arePlaybackHintsStale: Boolean
+        get() = playbackHintsUpdatedMs == 0L || 
+                System.currentTimeMillis() - playbackHintsUpdatedMs > PLAYBACK_HINTS_TTL_MS
+    
+    val isPlaybackReady: Boolean
+        get() = !playbackHintsJson.isNullOrEmpty() && !arePlaybackHintsStale
+}
```

### pipeline/xtream/XtreamCatalogPipelineImpl.kt (Auszug)

```diff
-            // Phase 1: VOD
-            // Phase 2: Series
-            // Phase 3: Episodes
-            // Phase 4: Live

+            // Phase 1: LIVE (First for perceived speed)
+            // Phase 2: VOD/Movies
+            // Phase 3: Series (Index Only - Episodes are lazy loaded)
+            // Phase 4: Episodes (SKIPPED during initial sync)
```

### feature/detail/UnifiedDetailViewModel.kt (Auszug)

```diff
-data class UnifiedDetailState(
-    val selectedSource: MediaSourceRef? = null,
-)

+data class UnifiedDetailState(
+    val selectedSourceKey: PipelineItemId? = null, // Key only, NOT MediaSourceRef!
+) {
+    val activeSource: MediaSourceRef?
+        get() = SourceSelection.resolveActiveSource(media, selectedSourceKey, resume)
+}
```

### feature/detail/enrichment/DetailEnrichmentService.kt (Auszug)

```diff
+    suspend fun ensureEnriched(
+        canonicalId: CanonicalMediaId,
+        sourceKey: PipelineItemId? = null,
+        requiredHints: List<String> = emptyList(),
+        timeoutMs: Long = ENSURE_TIMEOUT_MS,
+    ): CanonicalMediaWithSources? {
+        // 1. Fast path: hints already present
+        // 2. Mutex per canonicalId (prevent concurrent enrichment)
+        // 3. withTimeoutOrNull(timeoutMs) { enrichIfNeeded() }
+        // 4. Return fresh data from DB (SSOT)
+    }
```

### infra/data-xtream/di/XtreamDataModule.kt

```diff
+    @Binds
+    @Singleton
+    abstract fun bindXtreamSeriesIndexRepository(
+        impl: ObxXtreamSeriesIndexRepository
+    ): XtreamSeriesIndexRepository
```

---

## TTL-Richtlinien

| Daten | TTL | Begründung |
|-------|-----|------------|
| Season Index | 7 Tage | Selten ändernd, aber periodisches Refresh |
| Episode Index | 7 Tage | Neue Episodes können erscheinen |
| Playback Hints | 30 Tage | Stream IDs ändern sich selten |

---

## Nächste Schritte (Optional)

1. **Flow Throttling:** 400ms Debounce während aktivem Sync
2. **Transport Concurrency:** List=1-2, Detail=6 concurrent
3. **Unit Tests:** Paging + TTL Invalidierung
4. **Background Refresh:** WorkManager Job für nächtliches Index-Update

---

## Referenzen

- [XTREAM_PERFORMANCE_AND_EPISODE_STRATEGY.md](docs/v2/diagnostics/XTREAM_PERFORMANCE_AND_EPISODE_STRATEGY.md)
- [AGENTS.md](AGENTS.md) Section 4 (Layer Boundaries)
- [MEDIA_NORMALIZATION_CONTRACT.md](contracts/MEDIA_NORMALIZATION_CONTRACT.md)
