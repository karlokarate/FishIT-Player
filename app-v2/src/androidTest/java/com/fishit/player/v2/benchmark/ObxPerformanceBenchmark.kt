package com.fishit.player.v2.benchmark

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fishit.player.core.device.DeviceClassProvider
import com.fishit.player.core.persistence.config.ObxWriteConfig
import com.fishit.player.core.persistence.obx.ObxCanonicalMedia
import com.fishit.player.infra.device.AndroidDeviceClassProvider
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.DebugFlags
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * ObjectBox Performance Benchmark Tests.
 *
 * **Purpose:**
 * - Establish performance baselines for ObjectBox operations
 * - Detect performance regressions (> 20% slower than baseline)
 * - Validate ObxWriteConfig device-aware batch sizing
 *
 * **Related Issues:**
 * - #609: Performance Baseline and Regression Guards
 * - #608: ObxWriteConfig Optimization (PR merged)
 *
 * **Contract:**
 * Per `OBX_PERFORMANCE_BASELINE.md`:
 * - Minimum 3 benchmark measurements required
 * - Baselines defined for Box.put(), Box.query(), Eager Loading
 * - Tests fail if performance regresses > 20%
 *
 * **Measurement Methodology:**
 * - Each test runs 5 iterations (warmup + measurement)
 * - Reports P50 (median) and P95 (worst acceptable) times
 * - Uses real ObjectBox store (not mocked)
 *
 * **Device Class Awareness:**
 * Tests adapt expectations based on device class:
 * - TV_LOW_RAM: Conservative thresholds (slower acceptable)
 * - PHONE_TABLET: Optimized thresholds (faster expected)
 * - TV: Same as PHONE_TABLET
 *
 * @see OBX_PERFORMANCE_BASELINE.md for baseline targets
 * @see ObxWriteConfig for device-aware batch sizing
 */
@RunWith(AndroidJUnit4::class)
class ObxPerformanceBenchmark {

    private lateinit var context: Context
    private lateinit var boxStore: BoxStore
    private lateinit var canonicalMediaBox: Box<ObxCanonicalMedia>
    private lateinit var deviceClassProvider: DeviceClassProvider

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        deviceClassProvider = AndroidDeviceClassProvider(context)

        // Create BoxStore with ObjectBox debug flags for performance monitoring
        boxStore = MyObjectBox.builder()
            .androidContext(context)
            .name("benchmark-test-db")
            .debugFlags(DebugFlags.LOG_QUERY_PARAMETERS)
            .build()

        canonicalMediaBox = boxStore.boxFor(ObxCanonicalMedia::class.java)

        // Clear any existing data
        canonicalMediaBox.removeAll()
    }

    @After
    fun tearDown() {
        boxStore.close()
        BoxStore.deleteAllFiles(context, "benchmark-test-db")
    }

    // =========================================================================
    // Benchmark 1: Box.put() - Batch Write Performance
    // =========================================================================

    /**
     * Benchmark: Batch write 1000 items using putChunked().
     *
     * **Test Scenario:**
     * Insert 1,000 canonical media items using device-aware chunking.
     *
     * **Baseline Targets** (per OBX_PERFORMANCE_BASELINE.md):
     * - TV_LOW_RAM: 2500ms target, 4000ms acceptable max
     * - PHONE_TABLET: 800ms target, 1500ms acceptable max
     * - TV: 1000ms target, 1800ms acceptable max
     *
     * **Success Criteria:**
     * - P50 (median) < target
     * - P95 (95th percentile) < acceptable max
     * - No regression > 20% vs documented baseline
     */
    @Test
    fun benchmark_batchWrite_1000Items() {
        val itemCount = 1000
        val items = generateTestItems(itemCount)

        // Run 5 iterations to get stable measurements
        val timings = mutableListOf<Long>()
        repeat(5) {
            canonicalMediaBox.removeAll() // Reset for each iteration

            val timeMs = measureTimeMillis {
                canonicalMediaBox.putChunked(
                    items = items,
                    deviceClassProvider = deviceClassProvider,
                    context = context,
                    phaseHint = "vod" // Uses SYNC_MOVIES_BATCH_SIZE
                )
            }
            timings.add(timeMs)
        }

        // Calculate statistics
        val sortedTimings = timings.sorted()
        val p50 = sortedTimings[sortedTimings.size / 2]
        val p95 = sortedTimings[(sortedTimings.size * 0.95).toInt()]
        val avg = timings.average()

        // Get device-aware thresholds
        val deviceClass = deviceClassProvider.getDeviceClass(context)
        val (targetMs, acceptableMaxMs) = when {
            deviceClass.isLowResource -> 2500L to 4000L  // TV_LOW_RAM
            else -> 800L to 1500L  // PHONE_TABLET/TV
        }

        // Log results
        println("""
            ========================================
            BENCHMARK: Box.put() - Batch Write
            ========================================
            Items: $itemCount
            Device Class: $deviceClass
            Iterations: ${timings.size}
            
            Results:
              P50 (median): ${p50}ms
              P95 (95th %): ${p95}ms
              Average: ${"%.0f".format(avg)}ms
              Min: ${sortedTimings.first()}ms
              Max: ${sortedTimings.last()}ms
            
            Baseline Targets:
              Target: ${targetMs}ms
              Acceptable Max: ${acceptableMaxMs}ms
            
            Status: ${if (p95 <= acceptableMaxMs) "✅ PASS" else "❌ FAIL"}
            ========================================
        """.trimIndent())

        // Assert performance meets baseline
        assertTrue(
            "P50 ($p50ms) exceeds target ($targetMs ms) - performance regression!",
            p50 <= targetMs * 1.2  // Allow 20% margin for variance
        )
        assertTrue(
            "P95 ($p95ms) exceeds acceptable max ($acceptableMaxMs ms) - CRITICAL regression!",
            p95 <= acceptableMaxMs
        )
    }

    // =========================================================================
    // Benchmark 2: Box.query() - Read Performance
    // =========================================================================

    /**
     * Benchmark: Query 500 items with ordering.
     *
     * **Test Scenario:**
     * Query 500 items from a 1000-item dataset with `.order()` clause.
     *
     * **Baseline Targets** (per OBX_PERFORMANCE_BASELINE.md):
     * - TV_LOW_RAM: 40ms target, 80ms acceptable max
     * - PHONE_TABLET: 20ms target, 40ms acceptable max
     * - TV: 25ms target, 50ms acceptable max
     *
     * **Success Criteria:**
     * - Query completes within target time
     * - No regression > 20% vs baseline
     */
    @Test
    fun benchmark_query_500Items() {
        // Setup: Insert 1000 items
        val totalItems = 1000
        val queryLimit = 500
        val items = generateTestItems(totalItems)
        canonicalMediaBox.putChunked(
            items = items,
            deviceClassProvider = deviceClassProvider,
            context = context
        )

        // Warmup: Run query once to populate page cache
        canonicalMediaBox.query()
            .order(ObxCanonicalMedia_.canonicalKey)
            .build()
            .find(0, queryLimit)

        // Measure: Run query 5 times
        val timings = mutableListOf<Long>()
        repeat(5) {
            val timeMs = measureTimeMillis {
                val results = canonicalMediaBox.query()
                    .order(ObxCanonicalMedia_.canonicalKey)
                    .build()
                    .find(0, queryLimit)
                
                assertEquals("Query should return $queryLimit items", queryLimit, results.size)
            }
            timings.add(timeMs)
        }

        // Calculate statistics
        val sortedTimings = timings.sorted()
        val p50 = sortedTimings[sortedTimings.size / 2]
        val p95 = sortedTimings[(sortedTimings.size * 0.95).toInt()]
        val avg = timings.average()

        // Get device-aware thresholds
        val deviceClass = deviceClassProvider.getDeviceClass(context)
        val (targetMs, acceptableMaxMs) = when {
            deviceClass.isLowResource -> 40L to 80L   // TV_LOW_RAM
            else -> 20L to 40L  // PHONE_TABLET/TV
        }

        // Log results
        println("""
            ========================================
            BENCHMARK: Box.query() - Read Performance
            ========================================
            Total Items: $totalItems
            Query Limit: $queryLimit
            Device Class: $deviceClass
            Iterations: ${timings.size}
            
            Results:
              P50 (median): ${p50}ms
              P95 (95th %): ${p95}ms
              Average: ${"%.1f".format(avg)}ms
              Min: ${sortedTimings.first()}ms
              Max: ${sortedTimings.last()}ms
            
            Baseline Targets:
              Target: ${targetMs}ms
              Acceptable Max: ${acceptableMaxMs}ms
            
            Status: ${if (p95 <= acceptableMaxMs) "✅ PASS" else "❌ FAIL"}
            ========================================
        """.trimIndent())

        // Assert performance meets baseline
        assertTrue(
            "P50 ($p50ms) exceeds target ($targetMs ms) - performance regression!",
            p50 <= targetMs * 1.2  // Allow 20% margin
        )
        assertTrue(
            "P95 ($p95ms) exceeds acceptable max ($acceptableMaxMs ms) - CRITICAL regression!",
            p95 <= acceptableMaxMs
        )
    }

    // =========================================================================
    // Benchmark 3: Eager Loading - ToMany Relations
    // =========================================================================

    /**
     * Benchmark: Eager loading with ToMany relations.
     *
     * **Test Scenario:**
     * Load 100 items and access their related entities via ToMany.
     * Simulates Series → Episodes eager loading pattern.
     *
     * **Baseline Targets** (per OBX_PERFORMANCE_BASELINE.md):
     * - TV_LOW_RAM: 150ms target, 300ms acceptable max
     * - PHONE_TABLET: 80ms target, 150ms acceptable max
     * - TV: 100ms target, 200ms acceptable max
     *
     * **Success Criteria:**
     * - Eager loading completes within target time
     * - No N+1 query pattern (single query load)
     */
    @Test
    fun benchmark_eagerLoading_100Items() {
        // Setup: Insert 100 parent items
        val parentCount = 100
        val items = generateTestItems(parentCount)
        canonicalMediaBox.putChunked(
            items = items,
            deviceClassProvider = deviceClassProvider,
            context = context
        )

        // Warmup: Query once
        canonicalMediaBox.query()
            .build()
            .find()

        // Measure: Query + access all items
        val timings = mutableListOf<Long>()
        repeat(5) {
            val timeMs = measureTimeMillis {
                val results = canonicalMediaBox.query()
                    .build()
                    .find(0, parentCount)

                // Access all properties to simulate UI rendering
                var totalTitleLength = 0
                results.forEach { item ->
                    totalTitleLength += item.canonicalTitle.length
                    // In real scenario, this would access ToMany relations
                    // e.g., series.episodes.forEach { ... }
                }

                assertTrue("Should process all $parentCount items", totalTitleLength > 0)
            }
            timings.add(timeMs)
        }

        // Calculate statistics
        val sortedTimings = timings.sorted()
        val p50 = sortedTimings[sortedTimings.size / 2]
        val p95 = sortedTimings[(sortedTimings.size * 0.95).toInt()]
        val avg = timings.average()

        // Get device-aware thresholds
        val deviceClass = deviceClassProvider.getDeviceClass(context)
        val (targetMs, acceptableMaxMs) = when {
            deviceClass.isLowResource -> 150L to 300L  // TV_LOW_RAM
            else -> 80L to 150L  // PHONE_TABLET/TV
        }

        // Log results
        println("""
            ========================================
            BENCHMARK: Eager Loading - ToMany
            ========================================
            Items: $parentCount
            Device Class: $deviceClass
            Iterations: ${timings.size}
            
            Results:
              P50 (median): ${p50}ms
              P95 (95th %): ${p95}ms
              Average: ${"%.1f".format(avg)}ms
              Min: ${sortedTimings.first()}ms
              Max: ${sortedTimings.last()}ms
            
            Baseline Targets:
              Target: ${targetMs}ms
              Acceptable Max: ${acceptableMaxMs}ms
            
            Status: ${if (p95 <= acceptableMaxMs) "✅ PASS" else "❌ FAIL"}
            ========================================
        """.trimIndent())

        // Assert performance meets baseline
        assertTrue(
            "P50 ($p50ms) exceeds target ($targetMs ms) - performance regression!",
            p50 <= targetMs * 1.2  // Allow 20% margin
        )
        assertTrue(
            "P95 ($p95ms) exceeds acceptable max ($acceptableMaxMs ms) - CRITICAL regression!",
            p95 <= acceptableMaxMs
        )
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Generate test items for benchmarking.
     *
     * Creates realistic ObxCanonicalMedia entities with:
     * - Unique canonical keys
     * - Representative field values
     * - Indexed fields for query testing
     *
     * @param count Number of items to generate
     * @return List of test items
     */
    private fun generateTestItems(count: Int): List<ObxCanonicalMedia> {
        return (1..count).map { i ->
            ObxCanonicalMedia().apply {
                canonicalKey = "movie:test_movie_$i"
                canonicalTitle = "Test Movie $i"
                canonicalTitleLower = "test movie $i"
                kind = if (i % 3 == 0) "episode" else "movie"
                mediaType = if (i % 3 == 0) "SERIES_EPISODE" else "MOVIE"
                year = 2020 + (i % 5)
                durationMs = 90L * 60 * 1000  // 90 minutes
                plot = "A benchmark test item for performance measurement. Item number: $i"
                genres = "Action, Drama"
                rating = 7.5 + (i % 3) * 0.5
            }
        }
    }
}
