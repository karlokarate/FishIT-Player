# CATALOG_SYNC_WORKERS_CONTRACT_V2.md

Version: 2.2  
Date: 2025-12-19  
Status: Binding Contract  
Scope: Background execution, catalog sync orchestration, TMDB enrichment workers, Telegram structured bundle safety

---

## Implementation Status

| Component | Status | Module | Notes |
|-----------|--------|--------|-------|
| CatalogSyncWorkScheduler | ✅ Implemented | core/catalog-sync | Interface for SSOT scheduling |
| CatalogSyncWorkSchedulerImpl | ✅ Implemented | app-v2/work | WorkManager implementation |
| SyncUiState | ✅ Implemented | core/catalog-sync | UI state model |
| SyncStateObserver | ✅ Implemented | core/catalog-sync | Interface for state observation |
| CatalogSyncUiBridge | ✅ Implemented | app-v2/work | WorkManager → SyncUiState mapping |
| DebugScreen Sync UI | ✅ Implemented | feature/settings | SSOT sync controls |
| **CatalogSyncOrchestratorWorker** | ✅ Implemented | app-v2/work | Builds worker chain per W-7 |
| **WorkerConstants** | ✅ Implemented | app-v2/work | Tags, keys, device classes |
| **WorkerInputData** | ✅ Implemented | app-v2/work | Input parsing, RuntimeGuards |
| **XtreamPreflightWorker** | ✅ Implemented | app-v2/work | Xtream auth validation |
| **XtreamCatalogScanWorker** | ✅ Implemented | app-v2/work | Xtream sync via CatalogSyncService |
| **TelegramAuthPreflightWorker** | ✅ Implemented | app-v2/work | TDLib auth validation |
| **TelegramFullHistoryScanWorker** | ✅ Implemented | app-v2/work | Full Telegram sync |
| **TelegramIncrementalScanWorker** | ✅ Implemented | app-v2/work | Incremental Telegram sync |
| **IoQuickScanWorker** | ✅ Implemented | app-v2/work | Permission check + stub |

---

## 0. Binding References

This contract MUST be implemented in compliance with:

- MEDIA_NORMALIZATION_CONTRACT.md
- LOGGING_CONTRACT_V2.md
- GLOSSARY_v2_naming_and_modules.md
- AGENTS.md
- TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md (v2.2+)

---

## 1. Core Principles

### W-1 Optional Sources (MANDATORY)
Xtream, Telegram, and IO are independent. The app MUST work with:
- no sources configured
- exactly one source configured
- multiple sources configured

No source is ever required.

### W-2 Layering (MANDATORY)
- UI MUST NOT call transport or pipeline directly.
- Workers MUST NOT call transport directly.
- All scanning MUST go through CatalogSyncService.

### W-3 Pipeline Purity (MANDATORY)
Pipelines MUST:
- emit RawMediaMetadata only
- never call TMDB
- never normalize titles
- never compute canonical identity
- never persist directly

### W-4 TMDB Placement (MANDATORY)
TMDB API access MUST exist only in core/metadata-normalizer via TmdbMetadataResolver.

### W-5 Telegram Structured Bundles (MANDATORY)
Workers MUST assume Telegram pipelines may emit multiple RawMediaMetadata items per bundle.
Workers MUST NOT perform bundling or clustering logic.

---

## 2. Global Scheduling Model

### W-6 Single Global Sync Queue (MANDATORY) ✅ IMPLEMENTED
- uniqueWorkName = "catalog_sync_global"
- Default policy: ExistingWorkPolicy.KEEP
- Expert force restart: ExistingWorkPolicy.REPLACE

**Implementation:**
- `CatalogSyncWorkScheduler` interface in `core/catalog-sync`
- `CatalogSyncWorkSchedulerImpl` in `app-v2/work`
- Methods: `enqueueAutoSync()`, `enqueueExpertSyncNow()`, `enqueueForceRescan()`, `cancelSync()`

### W-7 Source Order (MANDATORY)
If multiple sources are active, execution order MUST be:
1. Xtream
2. Telegram
3. IO (optional quick scan)

### W-8 No-Source Behavior (MANDATORY)
If no sources are active:
- No workers are scheduled
- UI shows empty state with “Add source” actions

---

## 3. Worker Types and Modes

### W-9 Worker Base Type (MANDATORY)
All workers MUST be CoroutineWorker.

### W-10 Execution Modes (MANDATORY)
- AUTO
- EXPERT_SYNC_NOW
- EXPERT_FORCE_RESCAN

### W-11 UIDT Integration (Android 14+) (MANDATORY BEHAVIOR)
User-initiated long sync MAY use UIDT on Android 14+, with WorkManager as fallback.

---

## 4. Work Tags and InputData

### W-12 Tags (MANDATORY)
All workers MUST include:
- tag: catalog_sync
- tag: source_xtream | source_telegram | source_io | source_tmdb
- tag: mode_auto | mode_expert_sync_now | mode_expert_force_rescan
- tag: worker/<ClassName>

### W-13 Common InputData (MANDATORY)
- sync_run_id: String
- sync_mode: AUTO | EXPERT_SYNC_NOW | EXPERT_FORCE_RESCAN
- active_sources: CSV
- wifi_only: Boolean
- max_runtime_ms: Long
- device_class: FIRETV_LOW_RAM | ANDROID_PHONE_TABLET

### W-14 Source InputData (MANDATORY)
Xtream:
- xtream_sync_scope: INCREMENTAL | FULL

Telegram:
- telegram_sync_kind: INCREMENTAL | FULL_HISTORY

IO:
- io_sync_scope: QUICK

TMDB:
- tmdb_scope: DETAILS_BY_ID | RESOLVE_MISSING_IDS | REFRESH_SSOT | BOTH
- tmdb_force_refresh: Boolean
- tmdb_batch_size_hint: Int
- tmdb_batch_cursor: String?

---

## 5. Constraints and Guards

### W-15 Network Policy (MANDATORY)
Default: NetworkType.CONNECTED (mobile + wifi)

If wifi_only = true:
- NetworkType.UNMETERED

### W-16 Runtime Guards (MANDATORY)
Workers MUST defer if:
- battery < 15%
- Data Saver enabled
- Roaming enabled

### W-17 FireTV Safety (MANDATORY)
On FIRETV_LOW_RAM:
- small batch sizes (35 items - see `ObxWriteConfig.FIRETV_BATCH_CAP`)
- frequent persistence
- no payload logging

**Implementation:** All batch sizes are centralized in `core/persistence/config/ObxWriteConfig.kt`,
which automatically adjusts based on device class detection via `XtreamTransportConfig.detectDeviceClass()`.

---

## 6. Retry and Backoff

### W-18 Backoff (MANDATORY)
- EXPONENTIAL
- initial: 30s
- max: 15min

### W-19 Retry Limits (MANDATORY)
- AUTO: 3
- EXPERT_*: 5

### W-20 Non-Retryable Failures (MANDATORY)
- Telegram not authorized
- Xtream invalid credentials
- IO permission missing
- TMDB API key missing

---

## 7. Pipeline Scan Workers

### CatalogSyncOrchestratorWorker (REQUIRED)
Builds deterministic chain based on active sources.

### XtreamPreflightWorker (REQUIRED)
Validates Xtream config and connectivity.

### XtreamCatalogScanWorker (REQUIRED)
Runs Xtream sync via CatalogSyncService.

### TelegramAuthPreflightWorker (REQUIRED)
Ensures TDLib authorization.

### TelegramFullHistoryScanWorker (REQUIRED)
Runs full Telegram history scan (no artificial limits).

### TelegramIncrementalScanWorker (REQUIRED)
Runs incremental Telegram scan.

### IoQuickScanWorker (REQUIRED)
Runs quick local IO scan.

---

## 8. TMDB Enrichment Workers

### W-21 Typed Canonical Identity (MANDATORY)
Canonical keys MUST use typed TMDB refs:
- tmdb:movie:{id}
- tmdb:tv:{id}

### W-22 TMDB Scope Priority (MANDATORY)
1. DETAILS_BY_ID (externalIds.tmdb present)
2. RESOLVE_MISSING_IDS (search)
3. REFRESH_SSOT

### TmdbEnrichmentOrchestratorWorker (REQUIRED)
Schedules enrichment batches.

### TmdbEnrichmentBatchWorker (REQUIRED)
Runs bounded enrichment batches via resolver.

### TmdbEnrichmentContinuationWorker (REQUIRED)
Continues enrichment until resolved or exhausted.

---

## 9. TMDB Resolution State

Each item MUST persist:
- tmdbResolveState
- tmdbResolveAttempts
- lastTmdbAttemptAt
- tmdbNextEligibleAt
- tmdbLastFailureReason
- tmdbLastResolvedAt
- tmdbResolvedBy

Max attempts: 3  
Cooldown: 24h

---

## 10. Race-Free Image SSOT Rules

- tmdbId presence does NOT imply images are ready.
- TMDB images are SSOT only when canonical TMDB image refs exist.
- UI priority:
  canonical.tmdbPosterRef ?: source.bestPosterRef ?: placeholder
- Upgrade-only: source -> TMDB, never revert automatically.

---

## 11. Logging

- UnifiedLog ONLY
- Required events:
  START, GUARD_DEFER, PROGRESS, CHECKPOINT_SAVED, SUCCESS, FAILURE
- No secrets in logs.

---

## 12. Expert Controls ✅ PARTIALLY IMPLEMENTED

Expert Settings MUST provide:
- ✅ Sync now (`enqueueExpertSyncNow()`)
- ✅ Force restart sync (`enqueueForceRescan()`)
- ✅ Cancel sync (`cancelSync()`)
- 🔲 Telegram full history rescan
- 🔲 Force TMDB refresh
- 🔲 Reset cursors/checkpoints

**UI Implementation:**
- `DebugScreen` in `feature/settings` provides:
  - Sync All button → `syncAll()` → `enqueueExpertSyncNow()`
  - Force Rescan button → `forceRescan()` → `enqueueForceRescan()`
  - Cancel button → `cancelSync()` → `cancelSync()`
  - Status row showing `SyncUiState` (Idle/Running/Success/Failed)

---
