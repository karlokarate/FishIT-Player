package com.chris.m3usuite.diagnostics

import kotlin.system.measureTimeMillis

/**
 * Performance monitoring utility for tracking operation timing.
 * 
 * Features:
 * - Measure code block execution time
 * - Track operation performance patterns
 * - Integration with DiagnosticsLogger
 * 
 * Usage:
 *   val duration = PerformanceMonitor.measure("load_live_list") {
 *     // expensive operation
 *   }
 *   
 *   // Or with context:
 *   PerformanceMonitor.measureAndLog(
 *     category = "xtream",
 *     operation = "load_live_list",
 *     screen = "home"
 *   ) {
 *     // expensive operation
 *   }
 */
object PerformanceMonitor {
    
    /**
     * Measure execution time of a block.
     * 
     * @return Duration in milliseconds
     */
    inline fun <T> measure(operation: String, block: () -> T): Pair<T, Long> {
        val startTime = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - startTime
        return result to duration
    }
    
    /**
     * Measure and log execution time.
     */
    inline fun <T> measureAndLog(
        category: String,
        operation: String,
        screen: String? = null,
        component: String? = null,
        threshold: Long = 100L, // Only log if duration exceeds threshold
        additionalMetadata: Map<String, String> = emptyMap(),
        block: () -> T
    ): T {
        val startTime = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - startTime
        
        if (duration >= threshold) {
            val metadata = buildMap {
                putAll(additionalMetadata)
                put("duration_ms", duration.toString())
            }
            
            DiagnosticsLogger.logEvent(
                category = category,
                event = "${operation}_timed",
                level = if (duration > 1000) {
                    DiagnosticsLogger.LogLevel.WARN
                } else {
                    DiagnosticsLogger.LogLevel.INFO
                },
                screen = screen,
                component = component,
                metadata = metadata
            )
        }
        
        return result
    }
    
    /**
     * Timer for measuring multiple segments.
     */
    class Timer(
        private val category: String,
        private val operation: String,
        private val screen: String? = null
    ) {
        private val startTime = System.currentTimeMillis()
        private val checkpoints = mutableMapOf<String, Long>()
        
        fun checkpoint(name: String) {
            val elapsed = System.currentTimeMillis() - startTime
            checkpoints[name] = elapsed
        }
        
        fun finish(additionalMetadata: Map<String, String> = emptyMap()) {
            val totalDuration = System.currentTimeMillis() - startTime
            
            val metadata = buildMap {
                putAll(additionalMetadata)
                put("total_ms", totalDuration.toString())
                checkpoints.forEach { (name, elapsed) ->
                    put("checkpoint_${name}_ms", elapsed.toString())
                }
            }
            
            DiagnosticsLogger.logEvent(
                category = category,
                event = "${operation}_complete",
                screen = screen,
                metadata = metadata
            )
        }
    }
    
    /**
     * Create a timer for tracking multiple checkpoints.
     */
    fun startTimer(
        category: String,
        operation: String,
        screen: String? = null
    ): Timer {
        return Timer(category, operation, screen)
    }
}
