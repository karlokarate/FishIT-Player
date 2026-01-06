---
applyTo:
  - app-v2/src/main/java/com/fishit/player/v2/work/**
---

# 🏆 PLATIN Instructions: app-v2/work (Catalog Sync Workers)

> **PLATIN STANDARD** - WorkManager Workers for Catalog Synchronization.
>
> **Purpose:** Background sync workers that orchestrate catalog synchronization
> across all sources (Xtream, Telegram, IO). Implements parallel chains per source
> with preflight checks and scan workers.

---

## 🔴 ABSOLUTE HARD RULES

### 1. Workers Do NOT Call Pipelines Directly (W-3)
```kotlin
// ✅ CORRECT: Worker enqueues child workers
workManager.beginUniqueWork(workName, policy, childWorker).enqueue()

// ❌ FORBIDDEN: Direct pipeline call in worker
telegramPipeline.scan(chatId)  // NO! Pipeline is called by scan workers
```

### 2. No-Source Behavior (W-8)
```kotlin
// ✅ CORRECT: Check for active sources first
val activeSources = sourceActivationStore.getActiveSources()
if (activeSources.isEmpty()) {
    UnifiedLog.i(TAG) { "SUCCESS (no active sources, nothing to sync)" }
    return Result.success(WorkerOutputData.success(itemsPersisted = 0, durationMs = durationMs))
}

// ❌ FORBIDDEN: Assume sources exist
buildXtreamChain(inputData)  // What if Xtream isn't active?
```

### 3. Parallel Chains Per Source (W-6)
```kotlin
// ✅ CORRECT: Each source gets independent chain with unique work name
val xtreamWorkName = "catalog_sync_global_xtream"
val telegramWorkName = "catalog_sync_global_telegram"

workManager.beginUniqueWork(xtreamWorkName, policy, xtreamChain.first())
    .then(xtreamChain.drop(1))
    .enqueue()

workManager.beginUniqueWork(telegramWorkName, policy, telegramChain.first())
    .then(telegramChain.drop(1))
    .enqueue()

// ❌ FORBIDDEN: Sequential blocking chains
workManager.beginUniqueWork("catalog_sync", policy, xtreamPreflight)
    .then(xtreamScan)
    .then(telegramPreflight)  // Blocked if Xtream retries!
    .enqueue()
```

### 4. Mandatory Tags (W-12)
```kotlin
// ✅ CORRECT: All required tags present
OneTimeWorkRequestBuilder<XtreamPreflightWorker>()
    .setInputData(inputData)
    .addTag(WorkerConstants.TAG_CATALOG_SYNC)       // Base tag
    .addTag(WorkerConstants.TAG_SOURCE_XTREAM)      // Source tag
    .addTag(WorkerConstants.TAG_WORKER_XTREAM_PREFLIGHT)  // Worker tag
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
    .build()

// ❌ FORBIDDEN: Missing tags
OneTimeWorkRequestBuilder<XtreamPreflightWorker>()
    .setInputData(inputData)
    .build()  // No tags = can't query/cancel by source!
```

### 5. Non-Retryable Failures Return failure() (W-20)
```kotlin
// ✅ CORRECT: Non-retryable returns failure with reason
if (!telegramClient.isAuthorized()) {
    return Result.failure(
        WorkerOutputData.failure(WorkerConstants.FAILURE_TELEGRAM_NOT_AUTHORIZED)
    )
}

// ❌ FORBIDDEN: Retry on non-retryable error
if (!telegramClient.isAuthorized()) {
    return Result.retry()  // Will retry forever!
}
```

### 6. UnifiedLog for ALL Logging (Per Contract)
```kotlin
// ✅ CORRECT: UnifiedLog with lambda
UnifiedLog.i(TAG) { "START sync_run_id=$syncRunId mode=$mode" }
UnifiedLog.e(TAG, exception) { "FAILURE reason=$reason" }

// ❌ FORBIDDEN: android.util.Log
Log.d(TAG, "Starting sync")  // NO!
```

---

## 📋 Worker Structure (Per CATALOG_SYNC_WORKERS_CONTRACT_V2)

### CatalogSyncOrchestratorWorker
```kotlin
/**
 * Orchestrator - builds and enqueues PARALLEL worker chains per active source.
 *
 * Contract: W-6, W-8
 * - Reads SourceActivationStore for active sources
 * - Does NOT call pipelines or CatalogSyncService directly
 * - Each source gets independent chain (not sequential)
 */
@HiltWorker
class CatalogSyncOrchestratorWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sourceActivationStore: SourceActivationStore,
) : CoroutineWorker(context, workerParams)
```

### Preflight Workers (Per Source)
```kotlin
/**
 * XtreamPreflightWorker - validates credentials before scan.
 * TelegramAuthPreflightWorker - validates Telegram authorization.
 *
 * Contract: W-20
 * - Returns failure() for non-retryable errors (invalid credentials)
 * - Returns retry() for transient errors (network timeout)
 * - Returns success() to proceed to scan worker
 */
```

### Scan Workers (Per Source)
```kotlin
/**
 * XtreamCatalogScanWorker - fetches catalog via transport, persists via data layer.
 * TelegramFullHistoryScanWorker / TelegramIncrementalScanWorker
 * IoQuickScanWorker
 *
 * Contract: W-15
 * - Calls pipeline to process items
 * - Persists RawMediaMetadata to data layer
 * - Reports items_persisted and duration_ms in output
 */
```

---

## 📋 WorkerConstants (SSOT for All Workers)

### Work Names
```kotlin
const val WORK_NAME_CATALOG_SYNC = "catalog_sync_global"
const val WORK_NAME_TMDB_ENRICHMENT = "tmdb_enrichment_global"
```

### Tags (W-12)
```kotlin
// Base tag
const val TAG_CATALOG_SYNC = "catalog_sync"

// Source tags
const val TAG_SOURCE_XTREAM = "source_xtream"
const val TAG_SOURCE_TELEGRAM = "source_telegram"
const val TAG_SOURCE_IO = "source_io"

// Worker tags (format: worker/<ClassName>)
const val TAG_WORKER_ORCHESTRATOR = "worker/CatalogSyncOrchestratorWorker"
const val TAG_WORKER_XTREAM_PREFLIGHT = "worker/XtreamPreflightWorker"
const val TAG_WORKER_XTREAM_SCAN = "worker/XtreamCatalogScanWorker"
```

### InputData Keys (W-13, W-14)
```kotlin
// Common keys
const val KEY_SYNC_RUN_ID = "sync_run_id"
const val KEY_SYNC_MODE = "sync_mode"
const val KEY_ACTIVE_SOURCES = "active_sources"
const val KEY_WIFI_ONLY = "wifi_only"
const val KEY_MAX_RUNTIME_MS = "max_runtime_ms"
const val KEY_DEVICE_CLASS = "device_class"

// Source-specific keys
const val KEY_XTREAM_SYNC_SCOPE = "xtream_sync_scope"
const val KEY_TELEGRAM_SYNC_KIND = "telegram_sync_kind"
const val KEY_IO_SYNC_SCOPE = "io_sync_scope"
```

### Sync Modes
```kotlin
const val SYNC_MODE_AUTO = "AUTO"
const val SYNC_MODE_EXPERT_NOW = "EXPERT_SYNC_NOW"
const val SYNC_MODE_FORCE_RESCAN = "EXPERT_FORCE_RESCAN"
```

### Device Classes (W-17)
```kotlin
const val DEVICE_CLASS_FIRETV_LOW_RAM = "FIRETV_LOW_RAM"
const val DEVICE_CLASS_ANDROID_PHONE_TABLET = "ANDROID_PHONE_TABLET"

// Batch sizes per device class
const val FIRETV_BATCH_SIZE = 35
const val NORMAL_BATCH_SIZE = 100
```

### Failure Reasons (W-20 - Non-Retryable)
```kotlin
const val FAILURE_TELEGRAM_NOT_AUTHORIZED = "TELEGRAM_NOT_AUTHORIZED"
const val FAILURE_XTREAM_INVALID_CREDENTIALS = "XTREAM_INVALID_CREDENTIALS"
const val FAILURE_XTREAM_NOT_CONFIGURED = "XTREAM_NOT_CONFIGURED"
const val FAILURE_IO_PERMISSION_MISSING = "IO_PERMISSION_MISSING"
const val FAILURE_NOT_IMPLEMENTED = "NOT_IMPLEMENTED"
```

---

## ⚠️ Critical Architecture Patterns

### Runtime Guards (W-16)
```kotlin
// Check guards before proceeding (respects sync mode)
val guardReason = RuntimeGuards.checkGuards(applicationContext, input.syncMode)
if (guardReason != null) {
    UnifiedLog.w(TAG) { "GUARD_DEFER reason=$guardReason" }
    return Result.retry()  // Defer execution
}
```

### Work Policy Selection
```kotlin
// KEEP policy by default - don't cancel running work
// REPLACE only on explicit FORCE_RESCAN
val enqueuePolicy = if (input.syncMode == WorkerConstants.SYNC_MODE_FORCE_RESCAN) {
    ExistingWorkPolicy.REPLACE
} else {
    ExistingWorkPolicy.KEEP
}
```

### Backoff Configuration (W-18)
```kotlin
.setBackoffCriteria(
    BackoffPolicy.EXPONENTIAL,
    WorkerConstants.BACKOFF_INITIAL_SECONDS,  // 30s
    TimeUnit.SECONDS,
)
```

### Output Data Pattern
```kotlin
object WorkerOutputData {
    fun success(itemsPersisted: Int, durationMs: Long): Data =
        Data.Builder()
            .putInt(WorkerConstants.KEY_ITEMS_PERSISTED, itemsPersisted)
            .putLong(WorkerConstants.KEY_DURATION_MS, durationMs)
            .build()

    fun failure(reason: String): Data =
        Data.Builder()
            .putString(WorkerConstants.KEY_FAILURE_REASON, reason)
            .build()
}
```

---

## 📐 Architecture Position

```
┌─────────────────────────────────────────────────────────────┐
│                     ▶ app-v2/work ◀                         │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ CatalogSyncOrchestratorWorker                       │    │
│  │  └─► XtreamPreflightWorker → XtreamCatalogScanWorker│    │
│  │  └─► TelegramAuthPreflightWorker → TelegramScanWorker│   │
│  │  └─► IoQuickScanWorker                              │    │
│  └─────────────────────────────────────────────────────┘    │
│                            │                                │
│                   reads snapshot                            │
│                            ▼                                │
├─────────────────────────────────────────────────────────────┤
│                       infra/work                            │
│            (DefaultSourceActivationStore)                   │
│                            │                                │
│              implements interface from                      │
│                            ▼                                │
├─────────────────────────────────────────────────────────────┤
│                 core/source-activation-api                  │
└─────────────────────────────────────────────────────────────┘
```

**Data Flow:**
1. Orchestrator reads `SourceActivationStore` for active sources
2. Builds parallel chains per source (Xtream, Telegram, IO)
3. Preflight workers validate auth/credentials
4. Scan workers call pipelines and persist results

---

## ✅ PLATIN Checklist

### All Workers
- [ ] Uses `@HiltWorker` with `@AssistedInject`
- [ ] Extends `CoroutineWorker` (not `Worker`)
- [ ] Uses `UnifiedLog` exclusively (no `android.util.Log`)
- [ ] TAG = class name (e.g., `"CatalogSyncOrchestratorWorker"`)
- [ ] Logs START, SUCCESS/FAILURE with structured fields

### Orchestrator Worker
- [ ] Checks `SourceActivationStore.getActiveSources()` (W-8)
- [ ] Returns success with 0 items if no active sources
- [ ] Builds independent chains per source (W-6)
- [ ] Uses unique work names per source chain
- [ ] Respects sync mode for REPLACE/KEEP policy

### Preflight Workers
- [ ] Returns `Result.failure()` for non-retryable errors (W-20)
- [ ] Returns `Result.retry()` for transient errors
- [ ] Returns `Result.success()` to proceed to scan

### Scan Workers
- [ ] Persists items via data layer (not direct pipeline)
- [ ] Reports `items_persisted` and `duration_ms` in output
- [ ] Respects device class for batch sizes (W-17)

### Tags (W-12)
- [ ] Every worker has `TAG_CATALOG_SYNC`
- [ ] Every worker has source tag (`TAG_SOURCE_XTREAM`, etc.)
- [ ] Every worker has worker tag (`TAG_WORKER_*`)

---

## 📚 Reference Documents (Priority Order)

1. `contracts/CATALOG_SYNC_WORKERS_CONTRACT_V2.md` - W-1 through W-22
2. `contracts/LOGGING_CONTRACT_V2.md` - Logging requirements
3. `.github/instructions/infra-work.instructions.md` - SourceActivationStore
4. `core/source-activation-api/` - Interface definitions

---

## 🚨 Common Violations & Solutions

### Violation 1: Sequential Blocking Chains
```kotlin
// ❌ WRONG: All sources in one chain - Xtream retries block Telegram
workManager.beginUniqueWork("catalog_sync", KEEP, xtreamPreflight)
    .then(xtreamScan)
    .then(telegramPreflight)
    .then(telegramScan)
    .enqueue()

// ✅ CORRECT: Independent chains per source
workManager.beginUniqueWork("catalog_sync_global_xtream", KEEP, xtreamChain)
workManager.beginUniqueWork("catalog_sync_global_telegram", KEEP, telegramChain)
```

### Violation 2: Retry on Non-Retryable Error
```kotlin
// ❌ WRONG: Retry forever on permanent failure
if (credentials == null) {
    return Result.retry()  // Will never succeed!
}

// ✅ CORRECT: Fail fast on permanent errors
if (credentials == null) {
    return Result.failure(
        WorkerOutputData.failure(WorkerConstants.FAILURE_XTREAM_NOT_CONFIGURED)
    )
}
```

### Violation 3: Missing Runtime Guards
```kotlin
// ❌ WRONG: No guard check
override suspend fun doWork(): Result {
    startSync()  // Might drain battery on low power!
}

// ✅ CORRECT: Check guards first
override suspend fun doWork(): Result {
    val guardReason = RuntimeGuards.checkGuards(context, syncMode)
    if (guardReason != null) return Result.retry()
    startSync()
}
```

### Violation 4: Hardcoded Device Assumptions
```kotlin
// ❌ WRONG: Hardcoded batch size
val batchSize = 100

// ✅ CORRECT: Device-aware batch size
val batchSize = if (input.deviceClass == DEVICE_CLASS_FIRETV_LOW_RAM) {
    WorkerConstants.FIRETV_BATCH_SIZE  // 35
} else {
    WorkerConstants.NORMAL_BATCH_SIZE  // 100
}
```
