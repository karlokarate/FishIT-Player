---
applyTo: 
  - core/catalog-sync/**
---

# 🏆 PLATIN Instructions: core/catalog-sync

> **PLATIN STANDARD** - Catalog Sync Orchestration Layer.
>
> **Purpose:** Central orchestrator between Pipeline and Data layers.
> Consumes catalog events from pipelines, optionally normalizes via `MediaMetadataNormalizer`,
> and persists to data repositories via batch upserts.
>
> **SSOT Position:** Transport → Pipeline → **CatalogSync** → Data → Domain → UI

---

## 🔴 ABSOLUTE HARD RULES

### 1. Pipeline → Repository Flow ONLY

```kotlin
// ✅ CORRECT: Consume pipeline events, persist to repository
@Singleton
class DefaultCatalogSyncService @Inject constructor(
    private val telegramPipeline: TelegramCatalogPipeline,
    private val xtreamPipeline: XtreamCatalogPipeline,
    private val telegramRepository: TelegramContentRepository,
    private val xtreamCatalogRepository: XtreamCatalogRepository,
    private val normalizer: MediaMetadataNormalizer,
    private val canonicalMediaRepository: CanonicalMediaRepository,
) : CatalogSyncService

// ❌ FORBIDDEN: Direct transport/network calls
class WrongSyncService @Inject constructor(
    private val xtreamApiClient: XtreamApiClient,  // WRONG - use Pipeline!
    private val telegramClient: TelegramClient,    // WRONG - use Pipeline!
)
```

**Why:** CatalogSync sits BETWEEN Pipeline and Data. It never touches Transport directly.

---

### 2. RawMediaMetadata is the ONLY Input Type

```kotlin
// ✅ CORRECT: Extract RawMediaMetadata from pipeline events
when (event) {
    is TelegramCatalogEvent.ItemDiscovered -> {
        batch.add(event.item.raw)  // RawMediaMetadata
    }
    is XtreamCatalogEvent.ItemDiscovered -> {
        batch.add(event.item.raw)  // RawMediaMetadata
    }
}

// ❌ FORBIDDEN: Working with pipeline-internal DTOs outside sync
val xtreamVodItem: XtreamVodItem = ...   // WRONG - internal to pipeline
repository.upsert(xtreamVodItem)         // WRONG - only RawMediaMetadata
```

---

### 3. No Direct ObjectBox/DB Access

```kotlin
// ✅ CORRECT: Use repository abstractions
telegramRepository.upsertAll(batch)
xtreamCatalogRepository.upsertAll(batch)
canonicalMediaRepository.upsertBatch(normalizedItems)

// ❌ FORBIDDEN: Direct entity access
val box = ObjectBoxStore.get().boxFor<MediaEntity>()  // WRONG
box.put(entity)                                        // WRONG
```

---

### 4. Normalization Invariant (MANDATORY - HS-03)

**Critical Invariant:** Raw metadata is ALWAYS stored in pipeline-specific repositories, regardless of normalization status.

```kotlin
// ✅ CORRECT: Raw is ALWAYS persisted first
// Step 1: Store raw immediately (mandatory)
telegramRepository.upsertAll(batch)  // ALWAYS happens

// Step 2: Normalize and store canonical (conditional based on config or worker)
if (config.normalizeBeforePersist) {
    val normalized = normalizer.normalize(rawItem)
    canonicalMediaRepository.upsert(normalized)
}

// ❌ FORBIDDEN: Skipping raw storage
if (config.normalizeBeforePersist) {
    val normalized = normalizer.normalize(rawItem)
    canonicalMediaRepository.upsert(normalized)
    // WRONG - raw is not stored!
}
```

**Normalization Flow (Per MEDIA_NORMALIZATION_CONTRACT):**
1. Pipeline produces `RawMediaMetadata`
2. **CatalogSync ALWAYS stores raw in pipeline-specific repo** (mandatory - enables source-specific queries)
3. CatalogSync normalizes via `MediaMetadataNormalizer` (conditional: immediate or deferred via worker)
4. CatalogSync upserts to `CanonicalMediaRepository` (cross-pipeline identity)
5. CatalogSync links source via `addOrUpdateSourceRef`

**Normalization Timing Options:**
- **Option A (Immediate):** `normalizeBeforePersist=true` - Normalize during sync (slower, but canonical data available immediately)
- **Option B (Deferred):** `normalizeBeforePersist=false` - Background worker normalizes later (faster sync, canonical data delayed)
- **Guarantee:** If deferred, a background worker MUST eventually normalize all raw items

**Why Raw is Always Stored:**
- Source-specific features (e.g., Telegram chat filtering) require raw data
- Canonical normalization can fail (no TMDB match) - raw provides fallback
- Raw enables re-normalization when normalizer logic improves

---

### 5. Emit SyncStatus Events - NO UI Updates

```kotlin
// ✅ CORRECT: Emit status for consumers
emit(SyncStatus.InProgress(progress = itemsPersisted, total = totalItems))
emit(SyncStatus.Completed(itemCount = totalPersisted, durationMs = elapsed))
emit(SyncStatus.Error(message = exception.message ?: "Unknown error"))

// ❌ FORBIDDEN: Direct UI updates
showProgress(progress)     // WRONG - belongs in ViewModel/UI
Toast.show("Sync done")    // WRONG - belongs in UI layer
```

---

### 6. SyncActiveState for UI Flow Throttling

```kotlin
// ✅ CORRECT: Broadcast sync state for UI debouncing
_syncActiveState.value = SyncActiveState(
    isActive = true,
    source = SOURCE_XTREAM,
    currentPhase = "MOVIES",
)

// UI layer uses this:
syncActiveState.flatMapLatest { state ->
    if (state.isActive) {
        repository.observeAll().debounce(400.milliseconds)
    } else {
        repository.observeAll()
    }
}
```

---

### 7. Performance Batching (Per Premium Contract)

```kotlin
// ✅ CORRECT: Phase-specific batch sizes (DEFAULT values)
const val BATCH_SIZE_LIVE = 600     // Rapid stream inserts
const val BATCH_SIZE_MOVIES = 400   // Balanced
const val BATCH_SIZE_SERIES = 200   // Larger items

// Time-based flush for progressive UI updates
const val TIME_FLUSH_INTERVAL_MS = 1200L
```

**Phase Ordering (Perceived Speed):**
1. Live → Movies → Series

**Device Class Adjustment (see app-work.instructions.md):**

These are **default** batch sizes for normal devices (phone/tablet). On **FireTV low-RAM devices**, workers apply a global reduction factor:

- FireTV: All batches capped at **35 items** (overrides phase-specific sizes)
- Normal: Uses phase-specific sizes above (600/400/200)

**How They Work Together:**

1. Worker reads `device_class` from InputData
2. If `FIRETV_LOW_RAM`: `effectiveBatchSize = min(phaseBatchSize, 35)`
3. If `ANDROID_PHONE_TABLET`: `effectiveBatchSize = phaseBatchSize` (600/400/200)

This ensures FireTV never overwhelms limited RAM while normal devices maximize throughput.


---

### 8. UnifiedLog for ALL Logging

```kotlin
// ✅ CORRECT: UnifiedLog with lazy evaluation
UnifiedLog.d(TAG) { "Sync started: source=$source, config=$config" }
UnifiedLog.i(TAG) { "Batch persisted: ${batch.size} items in ${duration}ms" }
UnifiedLog.e(TAG, exception) { "Sync failed: ${exception.message}" }

// ❌ FORBIDDEN
println("Sync debug")
Log.d(TAG, "message")
```

---

## 📋 Module Contents

### CatalogSyncService (Interface)

```kotlin
interface CatalogSyncService {
    val syncActiveState: StateFlow<SyncActiveState>
    
    fun syncTelegram(
        chatIds: List<Long>? = null,
        syncConfig: SyncConfig = SyncConfig.DEFAULT,
    ): Flow<SyncStatus>
    
    fun syncXtream(
        syncConfig: SyncConfig = SyncConfig.DEFAULT,
        includeVod: Boolean = true,
        includeSeries: Boolean = true,
        includeLive: Boolean = true,
    ): Flow<SyncStatus>
    
    fun getLastSyncMetrics(): SyncPerfMetrics?
}
```

### SyncStatus (Sealed Interface)

```kotlin
sealed interface SyncStatus {
    data class Started(val source: String) : SyncStatus
    data class InProgress(val progress: Long, val total: Long?, val phase: String?) : SyncStatus
    data class Completed(val itemCount: Long, val durationMs: Long) : SyncStatus
    data class Cancelled(val reason: String) : SyncStatus
    data class Error(val message: String, val cause: Throwable?) : SyncStatus
}
```

### SyncConfig

```kotlin
data class SyncConfig(
    val batchSize: Int = 200,
    val normalizeBeforePersist: Boolean = true,
    val progressEmitInterval: Int = 100,
    val enableMetrics: Boolean = BuildConfig.DEBUG,
)
```

### SyncCheckpointStore

```kotlin
interface SyncCheckpointStore {
    suspend fun getLastChatId(source: String): Long?
    suspend fun setLastChatId(source: String, chatId: Long)
    suspend fun clear(source: String)
}
```

---

## 📋 Allowed Dependencies

```kotlin
// ✅ ALLOWED
implementation(project(":core:model"))                // RawMediaMetadata, MediaSourceRef
implementation(project(":core:metadata-normalizer"))  // MediaMetadataNormalizer
implementation(project(":core:persistence"))          // CanonicalMediaRepository
implementation(project(":core:source-activation-api"))
implementation(project(":core:feature-api"))          // TelegramAuthRepository
implementation(project(":infra:logging"))             // UnifiedLog
implementation(project(":infra:data-telegram"))       // TelegramContentRepository
implementation(project(":infra:data-xtream"))         // XtreamCatalogRepository
implementation(project(":pipeline:telegram"))         // TelegramCatalogPipeline
implementation(project(":pipeline:xtream"))           // XtreamCatalogPipeline

// ❌ FORBIDDEN
implementation(project(":infra:transport-telegram"))  // NO transport!
implementation(project(":infra:transport-xtream"))    // NO transport!
implementation(project(":feature:*"))                 // NO feature layer!
implementation(project(":player:*"))                  // NO player!
```

---

## 📐 Architecture Position

```
┌─────────────────────────────────────────────────────────────┐
│  Transport Layer (infra/transport-*)                        │
│  - TDLib, OkHttp, File I/O                                  │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Pipeline Layer (pipeline/*)                                │
│  - TelegramCatalogPipeline, XtreamCatalogPipeline          │
│  - Emits: TelegramCatalogEvent, XtreamCatalogEvent         │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  **CatalogSync (THIS MODULE)**                              │
│  - CatalogSyncService                                       │
│  - Consumes events, normalizes, persists                    │
│  - Emits: SyncStatus, SyncActiveState                       │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Data Layer (infra/data-*)                                  │
│  - TelegramContentRepository                                │
│  - XtreamCatalogRepository, XtreamLiveRepository           │
│  - CanonicalMediaRepository                                 │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Domain Layer (core/*-domain)                               │
│  - Use cases consume repository data                        │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔍 Worker Integration (Per CATALOG_SYNC_WORKERS_CONTRACT)

### Workers Call CatalogSyncService - NEVER Transport

```kotlin
// ✅ CORRECT: Worker → CatalogSyncService
@HiltWorker
class XtreamSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val catalogSyncService: CatalogSyncService,  // ✅
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        catalogSyncService.syncXtream(SyncConfig.DEFAULT).collect { status ->
            when (status) {
                is SyncStatus.Completed -> return Result.success()
                is SyncStatus.Error -> return Result.retry()
            }
        }
    }
}

// ❌ FORBIDDEN: Worker → Transport
class WrongWorker @AssistedInject constructor(
    private val xtreamApiClient: XtreamApiClient,  // WRONG!
)
```

---

## ✅ PLATIN Checklist

### Layer Boundaries
- [ ] Only consumes from Pipeline (`TelegramCatalogPipeline`, `XtreamCatalogPipeline`)
- [ ] Only persists via Repository abstractions
- [ ] NEVER imports `infra/transport-*`
- [ ] NEVER imports `feature/*`
- [ ] NEVER imports `player/*`

### Data Flow
- [ ] Input: `TelegramCatalogEvent`, `XtreamCatalogEvent`
- [ ] Extraction: `event.item.raw` → `RawMediaMetadata`
- [ ] Normalization: Optional via `MediaMetadataNormalizer`
- [ ] Output: Repository `upsertAll()` calls

### Status Emission
- [ ] Emits `SyncStatus.Started`, `InProgress`, `Completed`, `Error`
- [ ] Broadcasts `SyncActiveState` for UI throttling
- [ ] NEVER updates UI directly

### Performance
- [ ] Phase-specific batch sizes (Live=600, Movies=400, Series=200)
- [ ] Time-based flush (1200ms)
- [ ] Metrics collection in debug builds

### Logging
- [ ] Uses `UnifiedLog` exclusively
- [ ] Lazy message evaluation
- [ ] No secrets in logs

---

## 📚 Reference Documents (Priority Order)

1. **XTREAM_SCAN_PREMIUM_CONTRACT_V1.md** - Section 0: Layer Rules
2. **MEDIA_NORMALIZATION_CONTRACT.md** - Normalization flow
3. **LOGGING_CONTRACT_V2.md** - Logging standards
4. **CATALOG_SYNC_WORKERS_CONTRACT_V2** - Worker integration (in app-v2)
5. **GLOSSARY_v2_naming_and_modules.md** - Naming conventions

---

## 🚨 Common Violations & Solutions

### Violation 1: Transport Import in CatalogSync

```kotlin
// ❌ WRONG
import com.fishit.player.infra.transport.xtream.XtreamApiClient

class WrongSyncService(
    private val apiClient: XtreamApiClient,
)

// ✅ FIX: Use Pipeline abstraction
import com.fishit.player.pipeline.xtream.catalog.XtreamCatalogPipeline

class DefaultCatalogSyncService(
    private val xtreamPipeline: XtreamCatalogPipeline,
)
```

### Violation 2: Direct UI Update

```kotlin
// ❌ WRONG
fun syncXtream() {
    showToast("Sync started")  // WRONG
}

// ✅ FIX: Emit status event
fun syncXtream(): Flow<SyncStatus> = flow {
    emit(SyncStatus.Started(SOURCE_XTREAM))
}
```

### Violation 3: Skipping Normalization for Canonical

```kotlin
// ❌ WRONG: Storing raw directly in canonical
canonicalMediaRepository.upsert(rawItem)  // WRONG - not normalized

// ✅ FIX: Normalize first
val normalized = normalizer.normalize(rawItem)
canonicalMediaRepository.upsert(normalized)
```

### Violation 4: Missing SyncActiveState Broadcast

```kotlin
// ❌ WRONG: UI can't throttle
fun syncXtream() = flow {
    // No active state broadcast
    pipeline.scan().collect { ... }
}

// ✅ FIX: Broadcast sync state
fun syncXtream() = flow {
    _syncActiveState.value = SyncActiveState(isActive = true, source = SOURCE_XTREAM)
    try {
        pipeline.scan().collect { ... }
    } finally {
        _syncActiveState.value = SyncActiveState(isActive = false)
    }
}
```
