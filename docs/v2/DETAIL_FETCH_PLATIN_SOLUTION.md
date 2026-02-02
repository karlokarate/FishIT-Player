# 🏆 Detail Fetch Platin-Lösung für FishIT-Player

## 📊 Analyse: Aktueller Stand vs. Markt

### Aktuelle FishIT-Player Implementierung

```
User klickt Tile → Navigation zum Detail Screen
                     ↓
              UnifiedDetailViewModel.loadByMediaId()
                     ↓
              handleMediaState(Success) ← Initial aus Cache/DB
                     ↓
              viewModelScope.launch {  ← HINTERGRUND
                  detailEnrichmentService.enrichIfNeeded(media)
              }
```

**Problem:** Detail-Enrichment läuft in einem normalen Background-Coroutine OHNE Priorität über den laufenden Catalog-Sync.

### Vergleich: Andere Xtream Apps (via MCP Search)

| App | Detail Fetch Trigger | Caching | Priority/Queue | Sync Pause |
|-----|---------------------|---------|----------------|------------|
| **M3UAndroid** (oxyroid) | On-demand bei Tile-Klick | ❌ Keins | ❌ Keins | ❌ Nein |
| **cactuvi** (linakis) | On-demand beim Screen-Open | ❌ Keins für Details | ❌ Keins | ❌ Nein |
| **SpectreTV** | Unbekannt | Unbekannt | Unbekannt | Unbekannt |
| **SecureTV** | Unbekannt | Unbekannt | Unbekannt | Unbekannt |
| **XtreamPlayer** | Minimal | ❌ Keins | ❌ Keins | ❌ Nein |

**Erkenntnisse:**
1. Keine der Konkurrenz-Apps hat eine ausgefeilte Prioritäts-Queue
2. Alle nutzen Simple "On-demand" Pattern ohne Caching von Details
3. Keiner pausiert Sync für Detail-Fetches
4. FishIT hat bereits ein fortschrittlicheres System (DetailEnrichmentService)

---

## 🎯 Platin-Lösung: Priority-Based Detail Fetching

### Architektur-Übersicht

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     USER ACTION PRIORITY SYSTEM                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────────┐                                                       │
│  │ User klickt Tile │──────► ApiPriorityDispatcher.highPriority()          │
│  └──────────────────┘              │                                        │
│                                    ▼                                        │
│                         ┌──────────────────────┐                            │
│                         │ PRIORITY QUEUE       │                            │
│                         │ ────────────────     │                            │
│                         │ [P1] User Detail     │ ◄── SOFORT (Timeout: 8s)  │
│                         │ [P2] Playback Ready  │ ◄── Blockierend            │
│                         │ [P3] Background Sync │ ◄── PAUSIERT bei P1/P2    │
│                         └──────────────────────┘                            │
│                                    │                                        │
│                                    ▼                                        │
│                         ┌──────────────────────┐                            │
│                         │ XtreamApiClient      │                            │
│                         │ • getVodInfo()       │                            │
│                         │ • getSeriesInfo()    │                            │
│                         └──────────────────────┘                            │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Komponenten

#### 1. ApiPriorityDispatcher (NEU)

```kotlin
// Location: core/api-priority/src/main/java/.../ApiPriorityDispatcher.kt

interface ApiPriorityDispatcher {
    
    enum class Priority {
        /** User-initiated detail fetch - höchste Priorität, pausiert Background */
        HIGH_USER_ACTION,
        
        /** Playback-kritische Calls (ensureEnriched vor Play) */
        CRITICAL_PLAYBACK,
        
        /** Background catalog sync */
        BACKGROUND_SYNC
    }
    
    /**
     * Execute a high-priority API call, pausing background sync.
     * 
     * @param tag Identifying tag for logging/debugging
     * @param block The suspending API call to execute
     * @return Result of the API call
     */
    suspend fun <T> withHighPriority(
        tag: String,
        block: suspend () -> T
    ): T
    
    /**
     * Execute a playback-critical call with guaranteed execution slot.
     */
    suspend fun <T> withPlaybackPriority(
        tag: String,
        timeoutMs: Long = 8000L,
        block: suspend () -> T
    ): T?
    
    /**
     * Execute background work, yielding to higher priorities.
     */
    suspend fun <T> withBackgroundPriority(
        tag: String,
        block: suspend () -> T
    ): T
    
    /** Flow of current priority state for UI indicators */
    val priorityState: StateFlow<PriorityState>
}

data class PriorityState(
    val activeHighPriorityCalls: Int = 0,
    val backgroundPaused: Boolean = false,
    val currentOperation: String? = null
)
```

#### 2. DetailEnrichmentService (ERWEITERT)

```kotlin
// Location: core/detail-domain/src/main/java/.../DetailEnrichmentService.kt

interface DetailEnrichmentService {
    
    /**
     * IMMEDIATE enrichment with HIGH PRIORITY.
     * - Pauses background sync while running
     * - Should be called when user clicks a tile
     * - Returns immediately if already enriched
     */
    suspend fun enrichImmediate(
        media: CanonicalMediaWithSources
    ): CanonicalMediaWithSources
    
    /**
     * Check-and-enrich with normal priority (existing method).
     * - Runs in background
     * - Does NOT pause sync
     */
    suspend fun enrichIfNeeded(
        media: CanonicalMediaWithSources
    ): CanonicalMediaWithSources
    
    /**
     * Blocking ensure-enriched for playback with timeout.
     * - CRITICAL priority (pauses everything)
     * - Returns null on timeout (should use fallback)
     */
    suspend fun ensureEnriched(
        canonicalId: CanonicalMediaId,
        sourceKey: PipelineItemId? = null,
        requiredHints: List<String> = emptyList(),
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): CanonicalMediaWithSources?
}
```

#### 3. UnifiedDetailViewModel (ANGEPASST)

```kotlin
// CHANGE: enrichIfNeeded → enrichImmediate beim Screen-Open

private fun handleMediaState(mediaState: UnifiedMediaState) {
    when (mediaState) {
        is UnifiedMediaState.Success -> {
            // Initial state update (fast path - show cached data)
            _state.update { ... }

            // HIGH PRIORITY enrichment - pauses background sync!
            viewModelScope.launch {
                val enriched = detailEnrichmentService.enrichImmediate(mediaState.media) // CHANGED!
                if (enriched !== mediaState.media) {
                    _state.update { ... }
                }
            }
            
            // Series: HIGH PRIORITY episode fetch
            if (mediaState.media.mediaType == MediaType.SERIES) {
                viewModelScope.launch {
                    loadSeriesDetailsImmediate(mediaState.media) // CHANGED!
                }
            }
        }
    }
}
```

#### 4. CatalogSyncWorker (ANGEPASST)

```kotlin
// Workers check priority state before batch operations

override suspend fun doWork(): Result {
    return priorityDispatcher.withBackgroundPriority("CatalogSync") {
        // Existing sync logic...
        // Will automatically yield when HIGH priority call comes in
        
        vodStreams.chunked(BATCH_SIZE).forEach { batch ->
            // Check if we should yield
            if (priorityDispatcher.priorityState.value.activeHighPriorityCalls > 0) {
                UnifiedLog.d(TAG) { "Yielding to high-priority detail fetch..." }
                yield() // Coroutine checkpoint
            }
            processBatch(batch)
        }
    }
}
```

---

## 📐 Implementierungs-Phasen

### Phase 1: ApiPriorityDispatcher (Core Infrastructure)

**Aufwand:** ~4h

1. Neues Modul `core/api-priority/` erstellen
2. `ApiPriorityDispatcher` Interface definieren
3. `DefaultApiPriorityDispatcher` mit Semaphore-basierter Implementierung
4. Hilt-Modul für DI

**Technische Details:**

```kotlin
@Singleton
class DefaultApiPriorityDispatcher @Inject constructor() : ApiPriorityDispatcher {
    
    // Semaphore: 1 permit for HIGH priority, blocks BACKGROUND
    private val highPrioritySemaphore = Semaphore(1)
    
    // Counter for active high-priority operations
    private val activeHighPriority = AtomicInteger(0)
    
    private val _priorityState = MutableStateFlow(PriorityState())
    override val priorityState = _priorityState.asStateFlow()
    
    override suspend fun <T> withHighPriority(tag: String, block: suspend () -> T): T {
        try {
            activeHighPriority.incrementAndGet()
            _priorityState.update { it.copy(
                activeHighPriorityCalls = activeHighPriority.get(),
                backgroundPaused = true,
                currentOperation = tag
            )}
            
            // Acquire semaphore to signal background to pause
            highPrioritySemaphore.acquire()
            
            return block()
        } finally {
            highPrioritySemaphore.release()
            activeHighPriority.decrementAndGet()
            _priorityState.update { it.copy(
                activeHighPriorityCalls = activeHighPriority.get(),
                backgroundPaused = activeHighPriority.get() > 0
            )}
        }
    }
    
    override suspend fun <T> withBackgroundPriority(tag: String, block: suspend () -> T): T {
        // Wait if high-priority is active
        while (_priorityState.value.activeHighPriorityCalls > 0) {
            delay(100) // Yield to high priority
        }
        return block()
    }
}
```

### Phase 2: DetailEnrichmentService Update

**Aufwand:** ~2h

1. `enrichImmediate()` Methode hinzufügen
2. Integration mit `ApiPriorityDispatcher`
3. Logging für Priority-Switches

```kotlin
// DetailEnrichmentServiceImpl.kt

override suspend fun enrichImmediate(
    media: CanonicalMediaWithSources
): CanonicalMediaWithSources {
    if (!needsEnrichment(media)) return media
    
    return priorityDispatcher.withHighPriority("DetailEnrich:${media.canonicalId}") {
        enrichFromXtream(media)
    }
}
```

### Phase 3: ViewModel & Worker Integration

**Aufwand:** ~2h

1. `UnifiedDetailViewModel` auf `enrichImmediate` umstellen
2. `XtreamCatalogScanWorker` mit yield-Checkpoints versehen
3. `LoadSeriesSeasonsUseCase` auf High-Priority umstellen

### Phase 4: Testing & Observability

**Aufwand:** ~2h

1. Unit Tests für Priority-Dispatcher
2. UI-Indikator für "Background paused" (optional)
3. Logging-Verbesserungen für Debugging

---

## 🔍 Entscheidungen & Trade-offs

### Frage: Sollte Sync pausiert werden?

**Entscheidung: JA, aber "soft pause"**

- Kein harter Stopp des WorkManager
- Coroutine-basiertes Yielding
- Background Batches checken Priority-State und pausieren zwischen Batches

**Begründung:**
- User-Action hat IMMER Priorität über Background
- Harter Sync-Stopp könnte inkonsistente States erzeugen
- Soft-Pause ist sicherer und schneller wiederherzustellen

### Frage: Parallel oder sequentiell?

**Entscheidung: Priorisiertes Parallel**

- High-Priority Calls können jederzeit starten
- Background läuft parallel, pausiert aber wenn High-Priority aktiv
- Mehrere High-Priority Calls können parallel laufen (z.B. VOD + Series)

**Begründung:**
- Maximale Responsiveness für User
- Sync wird nicht komplett gestoppt, nur gebremst

### Frage: Caching von Detail-Daten?

**Entscheidung: Ja, mit TTL**

- VOD/Series Info wird nach Fetch in ObjectBox persistiert
- TTL: 24h für VOD, 1h für Series (Episoden können sich ändern)
- `enrichImmediate` prüft Cache-Freshness vor API-Call

**Begründung:**
- Vermeidet redundante API-Calls
- Ermöglicht Offline-Viewing der Detail-Seite
- Markt-Differenzierung (keine andere App cached Detail-Daten!)

---

## 📊 Erwartete Verbesserungen

| Metrik | Vorher | Nachher |
|--------|--------|---------|
| Detail-Load Zeit | ~2-3s (wenn Sync läuft) | <500ms (garantiert) |
| User-perceived Latency | Variabel | Konstant niedrig |
| API Rate Conflicts | Möglich | Verhindert |
| Background Sync Impact | Unkontrolliert | Kontrolliert pausiert |

---

## 🏁 Zusammenfassung

Die **Platin-Lösung** für FishIT-Player ist:

1. **`ApiPriorityDispatcher`** - Zentrale Prioritäts-Steuerung für alle API-Calls
2. **`enrichImmediate()`** - High-Priority Detail-Fetch beim Tile-Klick
3. **Soft-Pause für Sync** - Background yields zu User-Actions
4. **Detail-Caching** - Persistierung mit TTL (Markt-Differenzierung!)

Diese Lösung ist **fortschrittlicher als alle analysierten Konkurrenz-Apps** (M3UAndroid, cactuvi, etc.) und bietet:
- Garantierte Responsiveness
- Kontrollierte Ressourcen-Nutzung
- Robuste Fehlerbehandlung
- Zukunftssichere Architektur

---

## Referenzen

- [UnifiedDetailViewModel.kt](../../../feature/detail/src/main/java/com/fishit/player/feature/detail/UnifiedDetailViewModel.kt)
- [DetailEnrichmentServiceImpl.kt](../../../infra/data-detail/src/main/java/com/fishit/player/infra/data/detail/DetailEnrichmentServiceImpl.kt)
- [XtreamApiClient.kt](../../../infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamApiClient.kt)
- M3UAndroid Analysis (oxyroid/M3UAndroid)
- cactuvi Analysis (linakis/cactuvi)
