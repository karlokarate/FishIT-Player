# Migration Notes: Device Detection & Batch Size SSOT

**Related Issues:**
- [#606: ObjectBox Performance Optimization & Vector Search Migration](https://github.com/karlokarate/FishIT-Player/issues/606)
- [#609: Centralized ObjectBox Write Configuration](https://github.com/karlokarate/FishIT-Player/issues/609)

**Related PR:** This PR implements Task 1 from issue #609 (Device Detection Architecture)

---

## Overview

This migration introduces **breaking changes** by removing all backward compatibility code and establishing proper PLATIN-compliant device detection architecture with centralized batch size configuration.

### What Changed

1. **New Device Detection Architecture**
   - Created `core:device-api` module (pure interface, zero dependencies)
   - Created `infra:device-android` module (Android implementation)
   - Removed duplicate device detection from `XtreamTransportConfig`
   - All consumers now use `DeviceClassProvider` interface

2. **Centralized Batch Sizing**
   - Created `ObxWriteConfig` SSOT in `core:persistence`
   - Consolidated 16+ fragmented batch size definitions
   - All batch sizes now device-aware via `DeviceClassProvider`

3. **Removed Code**
   - `WorkerConstants.FIRETV_BATCH_SIZE` (deleted)
   - `WorkerConstants.NORMAL_BATCH_SIZE` (deleted)
   - `ObxWriteConfig` Context-only methods (deleted)
   - `XtreamTransportConfig.getParallelism(Context)` (deleted)
   - `XtreamTransportConfig.detectDeviceClass()` (deleted)
   - `XtreamTransportConfig.DeviceClass` enum (deleted, use `core:device-api`)

---

## Migration Guide

### Before (Old Code - No Longer Works)

```kotlin
// ❌ REMOVED: WorkerConstants batch sizes
val batchSize = WorkerConstants.FIRETV_BATCH_SIZE  // DELETED
val batchSize = WorkerConstants.NORMAL_BATCH_SIZE  // DELETED

// ❌ REMOVED: Context-only methods
val batchSize = ObxWriteConfig.getBatchSize(context)  // DELETED
box.putChunked(items, context)  // DELETED

// ❌ REMOVED: XtreamTransportConfig device detection
val parallelism = XtreamTransportConfig.getParallelism(context)  // DELETED
val deviceClass = XtreamTransportConfig.detectDeviceClass(context)  // DELETED
```

### After (New Code - Required)

```kotlin
// ✅ NEW: Inject DeviceClassProvider via Hilt
class MyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val deviceClassProvider: DeviceClassProvider,  // Inject this
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): Result {
        // Use device-aware batch sizing
        val batchSize = ObxWriteConfig.getBatchSize(deviceClassProvider, context)
        val liveBatch = ObxWriteConfig.getSyncLiveBatchSize(deviceClassProvider, context)
        
        // Use device-aware putChunked
        box.putChunked(items, deviceClassProvider, context)
        
        // Use device-aware parallelism
        val parallelism = XtreamTransportConfig.getParallelism(deviceClassProvider, context)
        
        return Result.success()
    }
}
```

### ViewModel Example

```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val deviceClassProvider: DeviceClassProvider,
    private val repository: MyRepository,
) : ViewModel() {
    
    fun performOperation() {
        val deviceClass = deviceClassProvider.getDeviceClass()
        
        when (deviceClass) {
            DeviceClass.TV_LOW_RAM -> {
                // Limit operations for FireTV
            }
            DeviceClass.TV -> {
                // Medium resource operations
            }
            DeviceClass.PHONE_TABLET -> {
                // Full resource operations
            }
        }
    }
}
```

### Repository Example

```kotlin
@Singleton
class MyRepository @Inject constructor(
    private val deviceClassProvider: DeviceClassProvider,
    private val boxStore: BoxStore,
) {
    private val box by lazy { boxStore.boxFor<MyEntity>() }
    
    suspend fun upsertAll(items: List<MyEntity>, context: Context) {
        // Device-aware batch upsert
        box.putChunked(items, deviceClassProvider, context, phaseHint = "movies")
    }
}
```

---

## Affected Files & Follow-up Tasks

### Files That Need Migration (v2 Only)

**High Priority - Immediate Action Required:**

1. **`app-v2/work/XtreamCatalogScanWorker.kt`**
   - Replace: `WorkerConstants.FIRETV_BATCH_SIZE` usage
   - Action: Inject `DeviceClassProvider`, use `ObxWriteConfig.getBatchSize()`
   - Issue: [#609 Task 2](https://github.com/karlokarate/FishIT-Player/issues/609#task-2)

2. **`app-v2/work/TelegramFullHistoryScanWorker.kt`**
   - Replace: Direct batch size constants
   - Action: Use `ObxWriteConfig` with injected `DeviceClassProvider`
   - Issue: [#609 Task 2](https://github.com/karlokarate/FishIT-Player/issues/609#task-2)

3. **`app-v2/work/TelegramIncrementalScanWorker.kt`**
   - Replace: Direct batch size constants
   - Action: Use `ObxWriteConfig` with injected `DeviceClassProvider`
   - Issue: [#609 Task 2](https://github.com/karlokarate/FishIT-Player/issues/609#task-2)

4. **`core/catalog-sync/CatalogSyncService.kt` (if exists)**
   - Replace: `SyncPhaseConfig` direct usage
   - Action: Inject `DeviceClassProvider`, use device-aware batching
   - Issue: [#609 Task 3](https://github.com/karlokarate/FishIT-Player/issues/609#task-3)

**Medium Priority - Update When Touching Code:**

5. **`infra/data-xtream/XtreamCatalogRepository.kt`**
   - Current: Uses hardcoded batch sizes
   - Action: Use `ObxWriteConfig.putChunked()` with device awareness
   - Issue: [#606 Task 2](https://github.com/karlokarate/FishIT-Player/issues/606#task-2)

6. **`infra/data-telegram/TelegramContentRepository.kt`**
   - Current: Uses hardcoded batch sizes
   - Action: Use `ObxWriteConfig.putChunked()` with device awareness
   - Issue: [#606 Task 2](https://github.com/karlokarate/FishIT-Player/issues/606#task-2)

**Low Priority - Optional Optimization:**

7. **Export operations** (if any use batch streaming)
   - Action: Use `ObxWriteConfig.getExportBatchSize()`
   - Issue: [#606 Task 4](https://github.com/karlokarate/FishIT-Player/issues/606#task-4)

8. **TMDB enrichment workers**
   - Action: Use `ObxWriteConfig.getTmdbEnrichmentBatchSize()`
   - Issue: [#606 Task 3](https://github.com/karlokarate/FishIT-Player/issues/606#task-3)

---

## Dependency Changes

### New Module Dependencies

Add these to your `build.gradle.kts`:

```kotlin
// For any module that needs device detection
implementation(project(":core:device-api"))

// For app-level modules only (avoid in libraries)
implementation(project(":infra:device-android"))
```

### Hilt Setup

Ensure your Application class includes the device module:

```kotlin
@HiltAndroidApp
class FishItApplication : Application() {
    // Device module is automatically included via @InstallIn(SingletonComponent::class)
}
```

---

## Testing Migration

### Unit Tests

```kotlin
class MyWorkerTest {
    @Test
    fun testWithMockedDeviceClass() = runTest {
        // Mock DeviceClassProvider
        val mockProvider = mock<DeviceClassProvider> {
            on { getDeviceClass() } doReturn DeviceClass.PHONE_TABLET
        }
        
        val worker = MyWorker(
            context = mockContext,
            workerParams = mockWorkerParams,
            deviceClassProvider = mockProvider,
        )
        
        val result = worker.doWork()
        assertEquals(Result.success(), result)
    }
}
```

### Integration Tests

See `core/persistence/src/androidTest/.../ObxWriteConfigIntegrationTest.kt` for examples of:
- Real device detection on Android
- Real ObjectBox operations with device-aware batching
- Phase-specific batch sizing validation

---

## Architecture Benefits

### Before (Fragmented)
```
WorkerConstants (hardcoded)
    ↓
SyncPhaseConfig (hardcoded)
    ↓
XtreamTransportConfig.detectDeviceClass() (ad-hoc)
    ↓
Multiple detection implementations
```

### After (PLATIN-Compliant)
```
core:device-api (interface)
    ↓
infra:device-android (implementation)
    ↓ (injected via Hilt)
    ├─→ ObxWriteConfig (batch sizing SSOT)
    ├─→ XtreamTransportConfig (parallelism)
    └─→ All performance configs
```

**Benefits:**
- ✅ Single device detection implementation (no duplication)
- ✅ Testable with pure Kotlin (12 unit tests, deterministic)
- ✅ Proper layer separation (infrastructure layer, not transport)
- ✅ Extensible (easy to add iOS/desktop implementations)
- ✅ Type-safe (interface-based, compile-time checks)
- ✅ PLATIN-compliant (follows architecture guidelines)

---

## Rollout Plan

### Phase 1: Critical Workers (Week 1)
- [ ] Migrate `XtreamCatalogScanWorker`
- [ ] Migrate `TelegramFullHistoryScanWorker`
- [ ] Migrate `TelegramIncrementalScanWorker`
- [ ] Update `CatalogSyncService` (if exists)
- [ ] Run integration tests on FireTV + Phone

### Phase 2: Repository Layer (Week 2)
- [ ] Update `XtreamCatalogRepository`
- [ ] Update `TelegramContentRepository`
- [ ] Add integration tests for repository operations

### Phase 3: Optional Optimizations (Week 3+)
- [ ] Export operations
- [ ] TMDB enrichment workers
- [ ] Any remaining hardcoded batch sizes

---

## Verification Checklist

After migrating a component:

- [ ] Removed all imports of `WorkerConstants.FIRETV_BATCH_SIZE`
- [ ] Removed all imports of `WorkerConstants.NORMAL_BATCH_SIZE`
- [ ] Injected `DeviceClassProvider` via Hilt
- [ ] Used `ObxWriteConfig` methods with `deviceClassProvider` parameter
- [ ] Removed Context-only API calls (they don't exist anymore)
- [ ] Added unit tests with mocked `DeviceClassProvider`
- [ ] Tested on real device (phone/tablet AND FireTV if possible)
- [ ] Verified batch sizes adapt correctly per device class

---

## Troubleshooting

### Compilation Error: "Unresolved reference: FIRETV_BATCH_SIZE"

**Cause:** Code still references deleted constants.

**Fix:**
```kotlin
// Replace this:
val batchSize = WorkerConstants.FIRETV_BATCH_SIZE

// With this:
@Inject lateinit var deviceClassProvider: DeviceClassProvider
val batchSize = ObxWriteConfig.getBatchSize(deviceClassProvider, context)
```

### Compilation Error: "No value passed for parameter 'deviceClassProvider'"

**Cause:** Calling ObxWriteConfig methods without required parameter.

**Fix:**
```kotlin
// Replace this:
ObxWriteConfig.getBatchSize(context)

// With this:
ObxWriteConfig.getBatchSize(deviceClassProvider, context)
```

### Runtime Error: "Cannot create an instance of DeviceClassProvider"

**Cause:** Trying to use `DeviceClassProvider` in non-Hilt context.

**Fix:** Ensure your class is Hilt-injectable:
```kotlin
@HiltWorker  // For Workers
class MyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val deviceClassProvider: DeviceClassProvider,
) : CoroutineWorker(context, workerParams)

@HiltViewModel  // For ViewModels
class MyViewModel @Inject constructor(
    private val deviceClassProvider: DeviceClassProvider,
) : ViewModel()

@Singleton  // For Repositories/Services
class MyRepository @Inject constructor(
    private val deviceClassProvider: DeviceClassProvider,
)
```

---

## Questions?

For issues or questions about this migration:
1. Check [Issue #606](https://github.com/karlokarate/FishIT-Player/issues/606) for overall ObjectBox optimization strategy
2. Check [Issue #609](https://github.com/karlokarate/FishIT-Player/issues/609) for device detection architecture details
3. Review integration tests in `core/persistence/src/androidTest/`
4. Check PLATIN architecture guidelines in `AGENTS.md`

---

## Related Documentation

- `core/persistence/README.md` - ObxWriteConfig usage guide
- `CATALOG_SYNC_WORKERS_CONTRACT_V2.md` - W-17 device class requirements
- `app-work.instructions.md` - PLATIN guidelines for workers
- `AGENTS.md` - Overall architecture principles
- Integration tests: `core/persistence/src/androidTest/.../ObxWriteConfigIntegrationTest.kt`
