# Macrobenchmark & Baseline Profile for Sync Performance

## Overview
Create comprehensive macrobenchmark tests and baseline profile to optimize sync/worker performance at the Android runtime level.

## Background
**Related to:** Performance/Regression Metrics Task (#XXX)

The current implementation provides unit-level performance metrics tracking (items/sec, persist times, error rates). To further optimize performance and catch regressions at the system level, we need:

1. **Macrobenchmark tests** - Measure real-world app performance under realistic conditions
2. **Baseline Profile** - Pre-compile critical paths for faster startup and execution

## Goals

### 1. Macrobenchmark Tests
Create benchmark tests for critical sync operations:

- **XtreamCatalogScanWorker** full sync (VOD + Series + Episodes)
- **TelegramFullHistoryScanWorker** initial sync
- **Canonical linking backlog processing**
- **Home screen loading after sync**

**Metrics to measure:**
- Time to first item displayed
- Total sync duration
- Memory allocation rate
- Frame drops during background sync
- CPU usage per phase

### 2. Baseline Profile
Generate and include baseline profiles for:

- Worker execution paths (XtreamCatalogScanWorker, TelegramSyncWorkers)
- Repository operations (XtreamCatalogRepository, TelegramContentRepository)
- Normalization path (MediaMetadataNormalizer)
- Home screen rendering

**Expected improvements:**
- 15-30% faster cold start for sync operations
- Reduced jank during background sync
- Better ANR prevention

## Implementation Plan

### Phase 1: Setup Macrobenchmark Module
- [ ] Create `:benchmark` module in project root
- [ ] Add Macrobenchmark dependencies
  ```kotlin
  implementation("androidx.benchmark:benchmark-macro-junit4:1.2.2")
  implementation("androidx.test.ext:junit:1.1.5")
  implementation("androidx.test.uiautomator:uiautomator:2.3.0")
  ```
- [ ] Configure benchmark build type in app module
- [ ] Create benchmark runner configuration

### Phase 2: Implement Benchmark Tests

#### XtreamSyncBenchmark
```kotlin
@RunWith(AndroidJUnit4::class)
class XtreamSyncBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun xtreamFullSyncColdStart() = benchmarkRule.measureRepeated(
        packageName = "com.fishit.player.v2",
        metrics = listOf(
            StartupTimingMetric(),
            FrameTimingMetric(),
            TraceSectionMetric("XtreamCatalogScanWorker")
        ),
        iterations = 5,
        setupBlock = {
            // Clear app data
            device.pressHome()
        }
    ) {
        // Launch app and trigger sync
        startActivityAndWait()
        // Navigate to Settings -> Sync Now
        // Wait for sync completion
    }
}
```

#### TelegramSyncBenchmark
```kotlin
@Test
fun telegramHistorySyncColdStart() = benchmarkRule.measureRepeated(...)
```

#### HomeLoadingBenchmark
```kotlin
@Test
fun homeScreenLoadAfterSync() = benchmarkRule.measureRepeated(...)
```

### Phase 3: Baseline Profile Generation

#### 3.1 Setup Profile Generator
- [ ] Create `:benchmark:baseline-profile` module
- [ ] Add profile generation dependencies
  ```kotlin
  implementation("androidx.profileinstaller:profileinstaller:1.3.1")
  implementation("androidx.benchmark:benchmark-macro-junit4:1.2.2")
  ```

#### 3.2 Define Profile Scenarios
```kotlin
@ExperimentalBaselineProfilesApi
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() = baselineProfileRule.collect(
        packageName = "com.fishit.player.v2",
        profileBlock = {
            // Scenario 1: Xtream sync
            startActivityAndWait()
            navigateToSettings()
            triggerXtreamSync()
            waitForSyncCompletion()
            
            // Scenario 2: Home screen after sync
            navigateToHome()
            scrollHomeRows()
            
            // Scenario 3: Telegram sync
            navigateToSettings()
            triggerTelegramSync()
            waitForSyncCompletion()
            
            // Scenario 4: Detail screen loading
            navigateToHome()
            selectFirstItem()
            waitForDetails()
        }
    )
}
```

#### 3.3 Integrate Profile into App
- [ ] Add profile to app module: `app-v2/src/main/baseline-prof.txt`
- [ ] Configure AGP to use baseline profile:
  ```kotlin
  android {
      buildTypes {
          release {
              ...
              // Enable profile installation
              isProfileable = true
          }
      }
  }
  ```

### Phase 4: CI Integration
- [ ] Add benchmark workflow to `.github/workflows/benchmark.yml`
- [ ] Setup baseline profile validation in PR checks
- [ ] Create performance comparison reports
- [ ] Add regression detection thresholds

### Phase 5: Documentation
- [ ] Document baseline profile generation process
- [ ] Create performance optimization guide
- [ ] Add benchmark running instructions to DEVELOPER_GUIDE.md
- [ ] Document performance thresholds and expectations

## Success Criteria

### Macrobenchmark
- [ ] All critical sync operations have benchmark coverage
- [ ] Benchmarks run in < 10 minutes in CI
- [ ] Performance regression detection catches 5%+ slowdowns
- [ ] Reports generated and archived for comparison

### Baseline Profile
- [ ] Profile covers >= 80% of hot paths in sync operations
- [ ] Measurable improvement in sync startup time (15-30%)
- [ ] Reduced ANRs during background sync
- [ ] Profile validates successfully in CI

## Performance Targets

| Operation | Current (estimate) | Target | Baseline Profile Impact |
|-----------|-------------------|--------|------------------------|
| Xtream Full Sync (1000 items) | 20s | 15s | -25% |
| Telegram History (500 items) | 10s | 8s | -20% |
| Home Screen Load | 2s | 1.5s | -25% |
| Worker Cold Start | 1s | 0.7s | -30% |

## References

- [Macrobenchmark Documentation](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
- [Baseline Profile Guide](https://developer.android.com/topic/performance/baselineprofiles)
- [Performance Best Practices](https://developer.android.com/topic/performance)
- Existing metrics: `SyncPerfMetrics.kt`, `SyncPerformanceRegressionTest.kt`

## Implementation Order
1. ✅ Unit-level metrics (DONE - Task 5)
2. → Macrobenchmark setup (This ticket - Priority 1)
3. → Baseline profile generation (This ticket - Priority 2)
4. → CI integration (This ticket - Priority 3)
5. → Continuous monitoring & optimization (Ongoing)

## Labels
- `performance`
- `benchmark`
- `optimization`
- `infrastructure`

## Assignee
TBD - Requires macrobenchmark expertise

## Milestone
v2 Performance Optimization

---
**Note:** This ticket depends on Task 5 (Performance/Regression Metrics) being complete.
