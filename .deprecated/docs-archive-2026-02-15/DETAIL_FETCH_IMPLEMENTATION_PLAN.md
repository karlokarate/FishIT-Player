# 🏆 Detail Fetch Priority System - Implementierungsplan

## Übersicht

Dieses Dokument beschreibt den schrittweisen Implementierungsplan für das Priority-basierte Detail-Fetching System.

---

## Phase 1: API Priority Dispatcher (Core Infrastructure)

### 1.1 Neues Modul erstellen: `infra/api-priority`

> **Warum infra/ statt core/?**  
> `ApiPriorityDispatcher` ist Infrastruktur-Code (Semaphore, Concurrency), nicht Domain-Logik.
> Per AGENTS.md gehört solcher Code in `infra/`.

**Dateien:**

```
infra/api-priority/
├── build.gradle.kts
└── src/main/java/com/fishit/player/infra/priority/
    ├── ApiPriority.kt           # Enum + Data Classes
    ├── ApiPriorityDispatcher.kt # Interface
    ├── DefaultApiPriorityDispatcher.kt # Implementation
    └── di/
        └── ApiPriorityModule.kt # Hilt Module
```

**Geschätzter Aufwand:** ~2h

---

### 1.2 Interface & Implementierung

```kotlin
// ApiPriority.kt
enum class ApiPriority {
    HIGH_USER_ACTION,      // User klickt Tile → Detail Screen
    CRITICAL_PLAYBACK,     // Play-Button → ensureEnriched()
    BACKGROUND_SYNC        // CatalogSync Workers
}

data class PriorityState(
    val activeHighPriorityCalls: Int = 0,
    val activeCriticalCalls: Int = 0,
    val backgroundSuspended: Boolean = false,
    val currentOperation: String? = null
)

// ApiPriorityDispatcher.kt
interface ApiPriorityDispatcher {
    val priorityState: StateFlow<PriorityState>
    
    suspend fun <T> withHighPriority(tag: String, block: suspend () -> T): T
    suspend fun <T> withCriticalPriority(tag: String, timeoutMs: Long, block: suspend () -> T): T?
    suspend fun <T> withBackgroundPriority(tag: String, block: suspend () -> T): T
    
    /** Check if background should yield */
    fun shouldYield(): Boolean
}
```

---

## Phase 2: DetailEnrichmentService Erweiterung

### 2.1 Interface erweitern

**Datei:** `core/detail-domain/src/.../DetailEnrichmentService.kt`

```kotlin
interface DetailEnrichmentService {
    // EXISTING
    suspend fun enrichIfNeeded(media: CanonicalMediaWithSources): CanonicalMediaWithSources
    suspend fun ensureEnriched(...): CanonicalMediaWithSources?
    
    // NEW: High-Priority immediate enrichment
    suspend fun enrichImmediate(media: CanonicalMediaWithSources): CanonicalMediaWithSources
}
```

### 2.2 Implementation anpassen

**Datei:** `infra/data-detail/src/.../DetailEnrichmentServiceImpl.kt`

- Inject `ApiPriorityDispatcher`
- `enrichImmediate()` → `withHighPriority()`
- `ensureEnriched()` → `withCriticalPriority()`
- `enrichIfNeeded()` → unverändert (Background)

**Geschätzter Aufwand:** ~1.5h

---

## Phase 3: Series Use Cases Anpassung

### 3.1 LoadSeriesSeasonsUseCase

**Datei:** `feature/detail/src/.../series/LoadSeriesSeasonsUseCase.kt`

```kotlin
class LoadSeriesSeasonsUseCase @Inject constructor(
    private val refresher: XtreamSeriesIndexRefresher,
    private val priorityDispatcher: ApiPriorityDispatcher, // NEW
) {
    suspend fun ensureSeasonsLoadedImmediate(seriesId: Int): List<SeasonIndex> {
        return priorityDispatcher.withHighPriority("SeriesSeasons:$seriesId") {
            refresher.refreshSeasons(seriesId)
        }
    }
}
```

**Geschätzter Aufwand:** ~1h

---

## Phase 4: UnifiedDetailViewModel Anpassung

### 4.1 Umstellung auf Priority-Calls

**Datei:** `feature/detail/src/.../UnifiedDetailViewModel.kt`

**Änderungen in `handleMediaState()`:**

```kotlin
is UnifiedMediaState.Success -> {
    // Initial state update (fast path)
    _state.update { ... }

    // HIGH PRIORITY enrichment (pausiert Background Sync!)
    viewModelScope.launch {
        val enriched = detailEnrichmentService.enrichImmediate(mediaState.media) // CHANGED
        if (enriched !== mediaState.media) {
            _state.update { ... }
        }
    }

    // Series: HIGH PRIORITY episode fetch
    if (mediaState.media.mediaType == MediaType.SERIES) {
        viewModelScope.launch {
            loadSeriesSeasonsUseCase.ensureSeasonsLoadedImmediate(seriesId) // CHANGED
        }
    }
}
```

**Geschätzter Aufwand:** ~1h

---

## Phase 5: CatalogSyncWorker Integration

### 5.1 Worker mit Yield-Checkpoints

**Datei:** `app-v2/src/.../work/XtreamCatalogScanWorker.kt`

```kotlin
@HiltWorker
class XtreamCatalogScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val priorityDispatcher: ApiPriorityDispatcher, // NEW
    // ... existing deps
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return priorityDispatcher.withBackgroundPriority("CatalogSync") {
            // Process in batches with yield checkpoints
            items.chunked(BATCH_SIZE).forEach { batch ->
                if (priorityDispatcher.shouldYield()) {
                    UnifiedLog.d(TAG) { "Yielding to high-priority call..." }
                    yield() // Coroutine checkpoint
                }
                processBatch(batch)
            }
            Result.success()
        }
    }
}
```

**Geschätzter Aufwand:** ~1.5h

---

## Phase 6: Tests & Logging

### 6.1 Unit Tests

**Neue Test-Dateien:**
- `infra/api-priority/src/test/.../ApiPriorityDispatcherTest.kt`
- `infra/data-detail/src/test/.../DetailEnrichmentPriorityTest.kt`

### 6.2 Logging Tags

```kotlin
object PriorityLogTags {
    const val TAG_PRIORITY = "ApiPriority"
    const val TAG_DETAIL_ENRICH = "DetailEnrich"
    const val TAG_SYNC_YIELD = "SyncYield"
}
```

**Geschätzter Aufwand:** ~2h

---

## Dependency Graph

```
┌─────────────────────────────────────────────────────────────────┐
│                        app-v2                                   │
│                           │                                     │
│         ┌─────────────────┼─────────────────┐                   │
│         ▼                 ▼                 ▼                   │
│  ┌────────────┐   ┌─────────────┐   ┌───────────────┐          │
│  │ feature/   │   │ infra/work  │   │ infra/        │          │
│  │ detail     │   │             │   │ api-priority  │ ◄── NEW  │
│  └─────┬──────┘   └──────┬──────┘   └───────────────┘          │
│        │                 │                 ▲                    │
│        ▼                 │                 │                    │
│  ┌─────────────┐         │                 │                    │
│  │ core/       │         │                 │                    │
│  │ detail-     │─────────┼─────────────────┘                    │
│  │ domain      │         │                                      │
│  └─────┬───────┘         │                                      │
│        │                 │                                      │
│        ▼                 ▼                                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              infra/data-detail                          │   │
│  │  DetailEnrichmentServiceImpl (uses PriorityDispatcher)  │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## Implementierungs-Reihenfolge

| # | Task | Modul | Aufwand | Dependencies |
|---|------|-------|---------|--------------|
| 1 | ApiPriorityDispatcher Interface & Impl | `infra/api-priority` | 2h | - |
| 2 | settings.gradle.kts updaten | root | 5min | Task 1 |
| 3 | DetailEnrichmentService Interface erweitern | `core/detail-domain` | 30min | - |
| 4 | DetailEnrichmentServiceImpl anpassen | `infra/data-detail` | 1.5h | Task 1, 3 |
| 5 | LoadSeriesSeasonsUseCase anpassen | `feature/detail` | 1h | Task 1 |
| 6 | UnifiedDetailViewModel anpassen | `feature/detail` | 1h | Task 4, 5 |
| 7 | XtreamCatalogScanWorker anpassen | `app-v2` | 1.5h | Task 1 |
| 8 | Unit Tests | alle | 2h | Task 1-7 |

**Gesamt: ~10h**

---

## Risiken & Mitigationen

| Risiko | Wahrscheinlichkeit | Impact | Mitigation |
|--------|-------------------|--------|------------|
| Deadlock bei Semaphore | Niedrig | Hoch | Timeout + tryAcquire() |
| Worker-Cancellation | Mittel | Mittel | Graceful yield statt hard stop |
| Race Condition State | Niedrig | Mittel | AtomicInteger + StateFlow |

---

## Akzeptanz-Kriterien

- [ ] Detail Screen öffnet sich in <500ms auch bei aktivem Sync
- [ ] Background Sync pausiert sichtbar während Detail-Fetch
- [ ] Keine API-Rate-Errors durch parallele Calls
- [ ] Logging zeigt Priority-Switches klar an
- [ ] Unit Tests alle grün
