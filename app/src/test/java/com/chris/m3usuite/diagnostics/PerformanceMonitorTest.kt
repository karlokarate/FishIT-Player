package com.chris.m3usuite.diagnostics

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PerformanceMonitor.
 * 
 * Tests:
 * 1. Basic timing measurement
 * 2. Measure and log with thresholds
 * 3. Timer with checkpoints
 */
class PerformanceMonitorTest {
    
    @Before
    fun setup() {
        DiagnosticsLogger.isEnabled = true
        DiagnosticsLogger.logLevel = DiagnosticsLogger.LogLevel.VERBOSE
        DiagnosticsLogger.enableConsoleOutput = false
        DiagnosticsLogger.clearEvents()
    }
    
    @After
    fun teardown() {
        DiagnosticsLogger.clearEvents()
    }
    
    @Test
    fun `measure returns result and duration`() = runBlocking {
        val (result, duration) = PerformanceMonitor.measure("test_operation") {
            delay(50)
            "test_result"
        }
        
        assertEquals("Result should match", "test_result", result)
        assertTrue("Duration should be at least 50ms", duration >= 50)
        assertTrue("Duration should be reasonable (< 200ms)", duration < 200)
    }
    
    @Test
    fun `measureAndLog only logs when exceeding threshold`() = runBlocking {
        // Fast operation (should not log with 100ms threshold)
        PerformanceMonitor.measureAndLog(
            category = "test",
            operation = "fast_operation",
            threshold = 100L
        ) {
            delay(10)
            "result"
        }
        
        delay(100)
        
        var events = DiagnosticsLogger.getRecentEvents(10)
        assertEquals("Should not log fast operation", 0, events.size)
        
        // Slow operation (should log with 50ms threshold)
        PerformanceMonitor.measureAndLog(
            category = "test",
            operation = "slow_operation",
            threshold = 50L
        ) {
            delay(100)
            "result"
        }
        
        delay(100)
        
        events = DiagnosticsLogger.getRecentEvents(10)
        assertEquals("Should log slow operation", 1, events.size)
        
        val event = events.first()
        assertEquals("Should have correct event name", "slow_operation_timed", event.event)
        assertTrue("Should have duration metadata", event.metadata.containsKey("duration_ms"))
        
        val duration = event.metadata["duration_ms"]?.toLongOrNull() ?: 0
        assertTrue("Duration should be at least 100ms", duration >= 100)
    }
    
    @Test
    fun `measureAndLog logs WARN for very slow operations`() = runBlocking {
        DiagnosticsLogger.logLevel = DiagnosticsLogger.LogLevel.INFO
        
        // Very slow operation (> 1000ms should be WARN level)
        PerformanceMonitor.measureAndLog(
            category = "test",
            operation = "very_slow_operation",
            threshold = 100L
        ) {
            delay(1100)
            "result"
        }
        
        delay(200)
        
        val events = DiagnosticsLogger.getRecentEvents(10)
        assertEquals("Should log very slow operation", 1, events.size)
        
        val event = events.first()
        assertEquals("Should be WARN level", "WARN", event.level)
    }
    
    @Test
    fun `measureAndLog includes additional metadata`() = runBlocking {
        PerformanceMonitor.measureAndLog(
            category = "test",
            operation = "operation_with_metadata",
            threshold = 10L,
            additionalMetadata = mapOf("key1" to "value1", "key2" to "value2")
        ) {
            delay(50)
            "result"
        }
        
        delay(100)
        
        val events = DiagnosticsLogger.getRecentEvents(10)
        val event = events.first()
        
        assertEquals("Should have metadata key1", "value1", event.metadata["key1"])
        assertEquals("Should have metadata key2", "value2", event.metadata["key2"])
        assertTrue("Should also have duration", event.metadata.containsKey("duration_ms"))
    }
    
    @Test
    fun `Timer tracks multiple checkpoints`() = runBlocking {
        val timer = PerformanceMonitor.startTimer(
            category = "test",
            operation = "multi_stage_operation",
            screen = "test_screen"
        )
        
        delay(50)
        timer.checkpoint("stage1")
        
        delay(50)
        timer.checkpoint("stage2")
        
        delay(50)
        timer.checkpoint("stage3")
        
        timer.finish(mapOf("total_items" to "100"))
        
        delay(100)
        
        val events = DiagnosticsLogger.getRecentEvents(10)
        assertEquals("Should have one event", 1, events.size)
        
        val event = events.first()
        assertEquals("Should have correct event name", "multi_stage_operation_complete", event.event)
        assertEquals("Should have screen", "test_screen", event.screen)
        
        // Check total duration
        assertTrue("Should have total_ms", event.metadata.containsKey("total_ms"))
        val totalMs = event.metadata["total_ms"]?.toLongOrNull() ?: 0
        assertTrue("Total should be at least 150ms", totalMs >= 150)
        
        // Check checkpoints
        assertTrue("Should have stage1 checkpoint", 
            event.metadata.containsKey("checkpoint_stage1_ms"))
        assertTrue("Should have stage2 checkpoint", 
            event.metadata.containsKey("checkpoint_stage2_ms"))
        assertTrue("Should have stage3 checkpoint", 
            event.metadata.containsKey("checkpoint_stage3_ms"))
        
        // Check additional metadata
        assertEquals("Should have total_items", "100", event.metadata["total_items"])
        
        // Verify checkpoint order
        val stage1 = event.metadata["checkpoint_stage1_ms"]?.toLongOrNull() ?: 0
        val stage2 = event.metadata["checkpoint_stage2_ms"]?.toLongOrNull() ?: 0
        val stage3 = event.metadata["checkpoint_stage3_ms"]?.toLongOrNull() ?: 0
        
        assertTrue("Stage2 should be after stage1", stage2 > stage1)
        assertTrue("Stage3 should be after stage2", stage3 > stage2)
    }
    
    @Test
    fun `Timer with no checkpoints still works`() = runBlocking {
        val timer = PerformanceMonitor.startTimer(
            category = "test",
            operation = "simple_operation"
        )
        
        delay(100)
        
        timer.finish()
        
        delay(100)
        
        val events = DiagnosticsLogger.getRecentEvents(10)
        assertEquals("Should have one event", 1, events.size)
        
        val event = events.first()
        assertTrue("Should have total_ms", event.metadata.containsKey("total_ms"))
        
        val totalMs = event.metadata["total_ms"]?.toLongOrNull() ?: 0
        assertTrue("Total should be at least 100ms", totalMs >= 100)
    }
    
    @Test
    fun `measure works with exceptions`() {
        try {
            PerformanceMonitor.measure("failing_operation") {
                throw RuntimeException("Test exception")
            }
            fail("Should have thrown exception")
        } catch (e: RuntimeException) {
            assertEquals("Should propagate exception", "Test exception", e.message)
        }
    }
    
    @Test
    fun `measureAndLog works with different result types`() = runBlocking {
        // Int result
        val intResult = PerformanceMonitor.measureAndLog(
            category = "test",
            operation = "int_operation",
            threshold = 0L
        ) {
            42
        }
        assertEquals("Should return int result", 42, intResult)
        
        // String result
        val stringResult = PerformanceMonitor.measureAndLog(
            category = "test",
            operation = "string_operation",
            threshold = 0L
        ) {
            "test_string"
        }
        assertEquals("Should return string result", "test_string", stringResult)
        
        // List result
        val listResult = PerformanceMonitor.measureAndLog(
            category = "test",
            operation = "list_operation",
            threshold = 0L
        ) {
            listOf(1, 2, 3)
        }
        assertEquals("Should return list result", listOf(1, 2, 3), listResult)
    }
}
