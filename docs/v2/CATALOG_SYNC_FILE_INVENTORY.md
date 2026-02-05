# Catalog Sync File Inventory

> Generated: 2026-02-05  
> Purpose: Complete inventory of all catalog sync related files for consolidation planning

---

## 1. Workers (`app-v2/src/main/java/com/fishit/player/v2/work/`)

### Active Workers (v2)

| File | Class | LOC | Purpose |
|------|-------|-----|---------|
| `CatalogSyncOrchestratorWorker.kt` | `CatalogSyncOrchestratorWorker` | ~200 | Entry point, enqueues source-specific chains |
| `XtreamCatalogScanWorker.kt` | `XtreamCatalogScanWorker` | **1337** ⚠️ | Full Xtream catalog sync (VOD/Series/Episodes/Live) |
| `XtreamPreflightWorker.kt` | `XtreamPreflightWorker` | ~208 | Validates Xtream auth before scan |
| `TelegramFullHistoryScanWorker.kt` | `TelegramFullHistoryScanWorker` | ~300 | Full Telegram chat history scan |
| `TelegramIncrementalScanWorker.kt` | `TelegramIncrementalScanWorker` | ~250 | Delta Telegram scan |
| `TelegramAuthPreflightWorker.kt` | `TelegramAuthPreflightWorker` | ~150 | Validates TDLib auth |
| `IoQuickScanWorker.kt` | `IoQuickScanWorker` | ~100 | Local IO scan (stub) |
| `TmdbEnrichmentOrchestratorWorker.kt` | `TmdbEnrichmentOrchestratorWorker` | ~200 | Batches TMDB enrichment work |
| `TmdbEnrichmentBatchWorker.kt` | `TmdbEnrichmentBatchWorker` | **490** | Resolves TMDB metadata |
| `TmdbEnrichmentContinuationWorker.kt` | `TmdbEnrichmentContinuationWorker` | ~150 | Handles scope transitions |
| `CanonicalLinkingBacklogWorker.kt` | `CanonicalLinkingBacklogWorker` | **505** | Links items to canonical media |

### Utility Classes (NOT Workers)

| File | Purpose |
|------|---------|
| `WorkerInputData.kt` | Input parsing, `RuntimeGuards`, `WorkerOutputData` |
| `WorkerRetryPolicy.kt` | Bounded retry logic |
| `WorkerConstants.kt` | Tags, keys, limits |
| `CatalogSyncWorkSchedulerImpl.kt` | WorkManager binding for `CatalogSyncWorkScheduler` interface |
| `TmdbEnrichmentSchedulerImpl.kt` | WorkManager binding for `TmdbEnrichmentScheduler` |
| `CanonicalLinkingSchedulerImpl.kt` | WorkManager binding for `CanonicalLinkingScheduler` |
| `WorkManagerSyncStateObserver.kt` | Observes WorkManager → exposes sync state |

---

## 2. Core Catalog Sync (`core/catalog-sync/`)

### Main Service Layer

| File | Class/Interface | LOC | Purpose |
|------|-----------------|-----|---------|
| `CatalogSyncContract.kt` | `CatalogSyncService`, `SyncStatus`, `SyncConfig` | 419 | Core contracts |
| `DefaultCatalogSyncService.kt` | `DefaultCatalogSyncService` | **1975** ⚠️ | Main orchestration impl |
| `CatalogSyncWorkScheduler.kt` | `CatalogSyncWorkScheduler` | 74 | Scheduler interface |

### Checkpointing

| File | Class | LOC | Purpose |
|------|-------|-----|---------|
| `SyncCheckpointStore.kt` | `SyncCheckpointStore`, `DataStoreSyncCheckpointStore` | 249 | Checkpoint persistence |
| `TelegramSyncCheckpoint.kt` | `TelegramSyncCheckpoint` | 305 | Telegram resumable checkpoint |
| `XtreamSyncCheckpoint.kt` | `XtreamSyncCheckpoint` | 275 | Xtream resumable checkpoint |

### Batching & Buffering

| File | Class | LOC | Purpose |
|------|-------|-----|---------|
| `SyncBatchManager.kt` | `SyncBatchManager` | 202 | Time-based batch management |
| `ChannelSyncBuffer.kt` | `ChannelSyncBuffer<T>` | 245 | Producer-consumer decoupling |

### Sync Strategy

| File | Class | LOC | Purpose |
|------|-------|-----|---------|
| `IncrementalSyncDecider.kt` | `IncrementalSyncDecider` | 241 | 4-tier sync decision (ETag→Count→Timestamp→Fingerprint) |
| `SyncPhaseConfig.kt` | `SyncPhaseConfig`, `EnhancedSyncConfig` | 182 | Phase configuration presets |

### Observability

| File | Class/Interface | LOC | Purpose |
|------|-----------------|-----|---------|
| `SyncStateObserver.kt` | `SyncStateObserver` | 32 | Reactive sync state |
| `SyncUiState.kt` | `SyncUiState` | 57 | UI state sealed interface |
| `SyncPerfMetrics.kt` | `SyncPerfMetrics` | 334 | Performance metrics |

### Schedulers

| File | Interface | LOC | Purpose |
|------|-----------|-----|---------|
| `CanonicalLinkingScheduler.kt` | `CanonicalLinkingScheduler` | 63 | Backlog processing scheduler |
| `TmdbEnrichmentScheduler.kt` | `TmdbEnrichmentScheduler` | 36 | TMDB enrichment scheduler |
| `EpgSyncService.kt` | `EpgSyncService` | 26 | EPG sync (placeholder) |

### Enhanced Sync (Strategy Pattern)

| File | Class | LOC | Purpose |
|------|-------|-----|---------|
| `enhanced/XtreamEnhancedSyncOrchestrator.kt` | `XtreamEnhancedSyncOrchestrator` | 153 | Event-driven sync |
| `enhanced/EnhancedSyncState.kt` | `EnhancedSyncState` | 75 | Immutable state |
| `enhanced/EnhancedBatchRouter.kt` | `EnhancedBatchRouter` | 118 | Batch flush decisions |
| `enhanced/XtreamEventHandler.kt` | `XtreamEventHandler<E>` | 59 | Strategy interface |
| `enhanced/XtreamEventHandlerRegistry.kt` | `XtreamEventHandlerRegistry` | 55 | Event dispatcher |

### Event Handlers

| File | Class | LOC | Purpose |
|------|-------|-----|---------|
| `handlers/ItemDiscoveredHandler.kt` | `ItemDiscoveredHandler` | 97 | Handle ItemDiscovered |
| `handlers/ScanCompletedHandler.kt` | `ScanCompletedHandler` | 72 | Handle ScanCompleted |
| `handlers/ScanProgressHandler.kt` | `ScanProgressHandler` | 84 | Handle ScanProgress |
| `handlers/SeriesEpisodeHandler.kt` | `SeriesEpisodeHandler` | 68 | Handle SeriesEpisode |
| `handlers/ScanErrorHandler.kt` | `ScanErrorHandler` | 53 | Handle ScanError |
| `handlers/ScanCancelledHandler.kt` | `ScanCancelledHandler` | 57 | Handle ScanCancelled |

### Other

| File | Class | LOC | Purpose |
|------|-------|-----|---------|
| `XtreamCategoryPreloader.kt` | `XtreamCategoryPreloader` | 273 | Category preloading |
| `MediaSourceRefBuilder.kt` | `MediaSourceRefBuilder` | 83 | Source ref utility |
| `di/CatalogSyncModule.kt` | DI Module | 38 | Hilt bindings |

---

## 3. Pipeline Xtream (`pipeline/xtream/`)

### Catalog Contract

| File | Class/Interface | LOC | Purpose |
|------|-----------------|-----|---------|
| `catalog/XtreamCatalogContract.kt` | `XtreamCatalogPipeline`, events | 389 | Pipeline contract |
| `catalog/XtreamCatalogPipelineImpl.kt` | `XtreamCatalogPipelineImpl` | 210 | Default impl |
| `catalog/XtreamCatalogSource.kt` | `XtreamCatalogSource` | 203 | Data source interface |
| `catalog/DefaultXtreamCatalogSource.kt` | `DefaultXtreamCatalogSource` | 264 | Default impl |

### Phase Orchestration

| File | Class | LOC | Purpose |
|------|-------|-----|---------|
| `phase/PhaseScanOrchestrator.kt` | `PhaseScanOrchestrator` | ~120 | Coordinates 4 phases |
| `phase/ScanPhase.kt` | `ScanPhase` interface | ~60 | Phase contract |
| `phase/VodItemPhase.kt` | `VodItemPhase` | ~100 | VOD streaming |
| `phase/SeriesItemPhase.kt` | `SeriesItemPhase` | ~100 | Series streaming |
| `phase/LiveChannelPhase.kt` | `LiveChannelPhase` | ~100 | Live channels |
| `phase/EpisodeStreamingPhase.kt` | `EpisodeStreamingPhase` | ~140 | Episode parallel loading |

### Adapter

| File | Class | LOC | Purpose |
|------|-------|-----|---------|
| `adapter/XtreamPipelineAdapter.kt` | `XtreamPipelineAdapter` | 345 | Transport→Pipeline conversion |

---

## 4. Transport Xtream (`infra/transport-xtream/`)

### API Client

| File | Class/Interface | LOC | Purpose |
|------|-----------------|-----|---------|
| `XtreamApiClient.kt` | `XtreamApiClient` | 564 | Full API interface |
| `DefaultXtreamApiClient.kt` | `DefaultXtreamApiClient` | 216 | Thin façade |

### Specialized Components

| File | Class | LOC | Purpose |
|------|-------|-----|---------|
| `client/XtreamStreamFetcher.kt` | `XtreamStreamFetcher` | 615 | Stream fetching |
| `client/XtreamCategoryFetcher.kt` | `XtreamCategoryFetcher` | 111 | Category enumeration |
| `client/XtreamConnectionManager.kt` | `XtreamConnectionManager` | ~200 | Lifecycle management |
| `streaming/StreamingJsonParser.kt` | `StreamingJsonParser` | 517 | O(1) memory JSON parsing |

---

## 5. Data Layer (`infra/data-xtream/`)

| File | Interface | LOC | Purpose |
|------|-----------|-----|---------|
| `XtreamCatalogRepository.kt` | `XtreamCatalogRepository` | 239 | VOD/Series persistence |
| `XtreamLiveRepository.kt` | `XtreamLiveRepository` | 82 | Live channel persistence |

---

## 6. Duplication Analysis Summary

### Identified Patterns

| Pattern ID | Description | Affected Files | Est. Duplication |
|------------|-------------|----------------|------------------|
| **A** | doWork() Prologue (guards, logging start) | ALL 11 workers | ~250 lines |
| **B** | doWork() Epilogue (success/error handling) | ALL 11 workers | ~350 lines |
| **C** | SyncStatus Flow Collection | 4 scan workers | ~200 lines |
| **D** | Preflight Auth Validation | 2 preflight workers | ~80 lines |
| **F** | WorkRequest Builder Boilerplate | Orchestrator (6x) | ~60 lines |

**Total Estimated Duplication: ~940 lines**

### Recommended Extractions

1. **`BaseWorkerWithGuards`** - Abstract class for all workers (Patterns A+B)
2. **`SyncStatusCollector`** - Handler for status flow collection (Pattern C)
3. **`PreflightWorkerBase`** - Abstract for preflight workers (Pattern D)
4. **`WorkRequestFactory`** - Builder utility (Pattern F)

---

## 7. File Statistics

| Category | Files | Total LOC |
|----------|-------|-----------|
| Workers (v2) | 11 | ~3,690 |
| Worker Utilities | 7 | ~600 |
| Core Sync | 24 | ~5,500 |
| Pipeline Xtream | 11 | ~2,000 |
| Transport Xtream | 21 | ~3,500 |
| Data Xtream | 4 | ~400 |
| **TOTAL** | **78** | **~15,690** |

---

## 8. Architecture Verification

✅ **No violations found:**
- Workers only in `app-v2/work/`
- Pipelines don't import WorkManager
- Feature modules use scheduler interfaces, not CatalogSyncService directly
- All sync code follows SSOT pattern

---

*Document generated as part of Issue #669 worker consolidation analysis*
