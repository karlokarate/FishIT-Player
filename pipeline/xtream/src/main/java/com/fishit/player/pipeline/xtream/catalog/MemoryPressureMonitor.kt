package com.fishit.player.pipeline.xtream.catalog

import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

/**
 * Memory pressure monitor for adaptive sync throttling.
 *
 * **Purpose:**
 * - Monitors JVM heap usage during catalog sync
 * - Provides adaptive backpressure when memory pressure is high
 * - Prevents OutOfMemoryError by throttling parallel streams
 *
 * **Thresholds (AGGRESSIVE SAFETY - Post-Crash Fix):**
 * - **< 50% (Normal):** Full speed, no throttling
 * - **50-65% (Warning):** Light throttling (100ms delays)
 * - **65-80% (High):** Heavy throttling (300ms delays)
 * - **80%+ (Critical):** Emergency brake (800ms delays + GC + yield)
 *
 * **Features:**
 * - Exponential backoff when consecutive throttles occur
 * - Yield after GC to give consumers time to catch up
 * - Adaptive logging (more frequent when memory high)
 *
 * **Usage:**
 * ```kotlin
 * val monitor = MemoryPressureMonitor()
 * monitor.checkAndThrottle()  // Suspends if memory pressure high
 * ```
 */
class MemoryPressureMonitor(
    private val normalThreshold: Int = 50,      // 50% - Start early!
    private val warningThreshold: Int = 65,     // 65% - Heavy throttle
    private val criticalThreshold: Int = 80,    // 80% - Emergency!
) {
    companion object {
        private const val TAG = "MemoryPressure"
        private const val MAX_CONSECUTIVE_THROTTLES = 10
    }

    private val runtime = Runtime.getRuntime()
    private var lastLogTimeMs = 0L
    private var throttleEventCount = 0
    private var consecutiveThrottles = 0  // Track consecutive throttles for backoff

    /**
     * Get current memory usage percentage (0-100).
     */
    fun getMemoryUsagePercent(): Int {
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        return ((usedMemory * 100) / maxMemory).toInt()
    }

    /**
     * Get current memory usage in MB.
     */
    fun getMemoryUsageMB(): Long {
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }

    /**
     * Get max heap size in MB.
     */
    fun getMaxMemoryMB(): Long {
        return runtime.maxMemory() / (1024 * 1024)
    }

    /**
     * Check memory pressure and throttle if needed.
     *
     * **Throttling Strategy (with exponential backoff):**
     * - Normal (< 50%): No delay, reset consecutive counter
     * - Warning (50-65%): 100ms delay × backoff multiplier
     * - High (65-80%): 300ms delay × backoff multiplier
     * - Critical (80%+): 800ms delay + GC + yield
     *
     * **Backoff Multiplier:**
     * - Increases with consecutive throttles (max 3x)
     * - Resets when memory drops below normal threshold
     *
     * @return true if throttled, false if no action taken
     */
    suspend fun checkAndThrottle(): Boolean {
        val usagePercent = getMemoryUsagePercent()
        val now = System.currentTimeMillis()

        // Log every 5 seconds when memory > 50%, or every 10 seconds otherwise
        val logInterval = if (usagePercent > normalThreshold) 5_000 else 10_000
        if (now - lastLogTimeMs > logInterval) {
            val usedMB = getMemoryUsageMB()
            val maxMB = getMaxMemoryMB()
            UnifiedLog.d(TAG) {
                "Memory: ${usedMB}MB / ${maxMB}MB (${usagePercent}%) | throttles=$throttleEventCount | consecutive=$consecutiveThrottles"
            }
            lastLogTimeMs = now
        }

        // Calculate backoff multiplier (1.0 to 3.0)
        val backoffMultiplier = 1.0 + (consecutiveThrottles.coerceAtMost(MAX_CONSECUTIVE_THROTTLES) * 0.2)

        // Determine throttle action
        return when {
            usagePercent < normalThreshold -> {
                // Normal: Full speed, reset consecutive counter
                consecutiveThrottles = 0
                false
            }
            usagePercent < warningThreshold -> {
                // Warning: Light throttle with backoff
                throttleEventCount++
                consecutiveThrottles++
                val delayMs = (100 * backoffMultiplier).toLong()
                if (throttleEventCount % 10 == 0) {
                    UnifiedLog.w(TAG) {
                        "Memory pressure WARNING: ${usagePercent}% | Light throttle (${delayMs}ms, backoff=${backoffMultiplier}x)"
                    }
                }
                delay(delayMs)
                true
            }
            usagePercent < criticalThreshold -> {
                // High: Heavy throttle with backoff
                throttleEventCount++
                consecutiveThrottles++
                val delayMs = (300 * backoffMultiplier).toLong()
                if (throttleEventCount % 5 == 0) {
                    UnifiedLog.w(TAG) {
                        "Memory pressure HIGH: ${usagePercent}% | Heavy throttle (${delayMs}ms, backoff=${backoffMultiplier}x)"
                    }
                }
                delay(delayMs)
                true
            }
            else -> {
                // Critical: Emergency brake + GC + yield
                throttleEventCount++
                consecutiveThrottles++
                val delayMs = (800 * backoffMultiplier).toLong()
                UnifiedLog.e(TAG) {
                    "Memory pressure CRITICAL: ${usagePercent}% | Emergency brake (${delayMs}ms) + GC + yield"
                }
                System.gc()  // Suggest GC
                yield()      // Give other coroutines a chance to run (especially consumers!)
                delay(delayMs)
                true
            }
        }
    }

    /**
     * Check if memory pressure is too high to start new parallel work.
     *
     * Use this before launching new parallel coroutines.
     *
     * @return true if it's safe to start parallel work (< 60%)
     */
    fun canStartParallelWork(): Boolean {
        return getMemoryUsagePercent() < 60
    }

    /**
     * Get summary statistics.
     */
    fun getSummary(): String {
        val usedMB = getMemoryUsageMB()
        val maxMB = getMaxMemoryMB()
        val usagePercent = getMemoryUsagePercent()
        return "Memory: ${usedMB}MB/${maxMB}MB (${usagePercent}%) | Throttles: $throttleEventCount"
    }

    /**
     * Reset statistics (e.g., at start of new sync).
     */
    fun reset() {
        throttleEventCount = 0
        consecutiveThrottles = 0
        lastLogTimeMs = 0
    }
}
