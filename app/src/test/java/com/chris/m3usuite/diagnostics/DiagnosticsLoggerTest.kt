package com.chris.m3usuite.diagnostics

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for DiagnosticsLogger.
 * 
 * Tests:
 * 1. Event logging and retrieval
 * 2. Sensitive data filtering
 * 3. Log level filtering
 * 4. Event export functionality
 * 5. Category-specific helpers
 */
class DiagnosticsLoggerTest {
    
    @Before
    fun setup() {
        DiagnosticsLogger.isEnabled = true
        DiagnosticsLogger.logLevel = DiagnosticsLogger.LogLevel.VERBOSE
        DiagnosticsLogger.enableConsoleOutput = false // Disable for tests
        DiagnosticsLogger.clearEvents()
    }
    
    @After
    fun teardown() {
        DiagnosticsLogger.clearEvents()
    }
    
    @Test
    fun `logEvent creates event with correct properties`() = runBlocking {
        DiagnosticsLogger.logEvent(
            category = "test",
            event = "test_event",
            level = DiagnosticsLogger.LogLevel.INFO,
            screen = "test_screen",
            metadata = mapOf("key1" to "value1")
        )
        
        // Wait for async processing
        delay(100)
        
        val events = DiagnosticsLogger.getRecentEvents(10)
        assertEquals("Should have one event", 1, events.size)
        
        val event = events.first()
        assertEquals("Category should match", "test", event.category)
        assertEquals("Event name should match", "test_event", event.event)
        assertEquals("Screen should match", "test_screen", event.screen)
        assertEquals("Metadata should match", "value1", event.metadata["key1"])
    }
    
    @Test
    fun `sensitive data is filtered from metadata`() = runBlocking {
        DiagnosticsLogger.logEvent(
            category = "test",
            event = "test_event",
            metadata = mapOf(
                "safe_key" to "safe_value",
                "token" to "secret_token",
                "password" to "secret_pass",
                "auth_header" to "Bearer secret",
                "secret_code" to "12345"
            )
        )
        
        delay(100)
        
        val events = DiagnosticsLogger.getRecentEvents(10)
        val event = events.first()
        
        assertTrue("Safe key should be present", event.metadata.containsKey("safe_key"))
        assertFalse("Token should be filtered", event.metadata.containsKey("token"))
        assertFalse("Password should be filtered", event.metadata.containsKey("password"))
        assertFalse("Auth header should be filtered", event.metadata.containsKey("auth_header"))
        assertFalse("Secret should be filtered", event.metadata.containsKey("secret_code"))
    }
    
    @Test
    fun `log level filtering works correctly`() = runBlocking {
        DiagnosticsLogger.logLevel = DiagnosticsLogger.LogLevel.WARN
        
        // These should be filtered out
        DiagnosticsLogger.logEvent(
            category = "test",
            event = "verbose_event",
            level = DiagnosticsLogger.LogLevel.VERBOSE
        )
        
        DiagnosticsLogger.logEvent(
            category = "test",
            event = "debug_event",
            level = DiagnosticsLogger.LogLevel.DEBUG
        )
        
        DiagnosticsLogger.logEvent(
            category = "test",
            event = "info_event",
            level = DiagnosticsLogger.LogLevel.INFO
        )
        
        // These should be logged
        DiagnosticsLogger.logEvent(
            category = "test",
            event = "warn_event",
            level = DiagnosticsLogger.LogLevel.WARN
        )
        
        DiagnosticsLogger.logEvent(
            category = "test",
            event = "error_event",
            level = DiagnosticsLogger.LogLevel.ERROR
        )
        
        delay(100)
        
        val events = DiagnosticsLogger.getRecentEvents(10)
        assertEquals("Should only have WARN and ERROR events", 2, events.size)
        assertTrue("Should contain warn event", events.any { it.event == "warn_event" })
        assertTrue("Should contain error event", events.any { it.event == "error_event" })
    }
    
    @Test
    fun `logError creates event with exception details`() = runBlocking {
        val exception = RuntimeException("Test exception")
        
        DiagnosticsLogger.logError(
            category = "test",
            event = "error_occurred",
            throwable = exception,
            screen = "test_screen"
        )
        
        delay(100)
        
        val events = DiagnosticsLogger.getRecentEvents(10)
        val event = events.first()
        
        assertEquals("Should be ERROR level", "ERROR", event.level)
        assertEquals("Should contain error type", "RuntimeException", event.metadata["error_type"])
        assertTrue("Should contain error message", 
            event.metadata["error_message"]?.contains("Test exception") == true)
    }
    
    @Test
    fun `getRecentEvents respects limit parameter`() = runBlocking {
        // Log 10 events
        repeat(10) { i ->
            DiagnosticsLogger.logEvent(
                category = "test",
                event = "event_$i"
            )
        }
        
        delay(100)
        
        val events5 = DiagnosticsLogger.getRecentEvents(5)
        assertEquals("Should return 5 events", 5, events5.size)
        
        val events3 = DiagnosticsLogger.getRecentEvents(3)
        assertEquals("Should return 3 events", 3, events3.size)
    }
    
    @Test
    fun `exportEventsAsJson returns valid JSON`() = runBlocking {
        DiagnosticsLogger.logEvent(
            category = "test",
            event = "test_event",
            metadata = mapOf("key" to "value")
        )
        
        delay(100)
        
        val json = DiagnosticsLogger.exportEventsAsJson(10)
        
        assertNotNull("JSON should not be null", json)
        assertTrue("JSON should contain category", json.contains("\"category\":\"test\""))
        assertTrue("JSON should contain event", json.contains("\"event\":\"test_event\""))
    }
    
    @Test
    fun `clearEvents removes all events`() = runBlocking {
        repeat(5) {
            DiagnosticsLogger.logEvent(
                category = "test",
                event = "test_event"
            )
        }
        
        delay(100)
        
        var events = DiagnosticsLogger.getRecentEvents(10)
        assertTrue("Should have events before clear", events.isNotEmpty())
        
        DiagnosticsLogger.clearEvents()
        
        events = DiagnosticsLogger.getRecentEvents(10)
        assertTrue("Should have no events after clear", events.isEmpty())
    }
    
    @Test
    fun `Xtream helper logs correctly`() = runBlocking {
        DiagnosticsLogger.Xtream.logLoadStart("live", "home")
        DiagnosticsLogger.Xtream.logLoadComplete("live", 150, 234, "home")
        DiagnosticsLogger.Xtream.logLoadError("vod", "timeout", "detail")
        
        delay(100)
        
        val events = DiagnosticsLogger.getRecentEvents(10)
        assertEquals("Should have 3 events", 3, events.size)
        
        val startEvent = events.find { it.event == "load_start" }
        assertNotNull("Should have start event", startEvent)
        assertEquals("Should be xtream category", "xtream", startEvent?.category)
        
        val completeEvent = events.find { it.event == "load_complete" }
        assertNotNull("Should have complete event", completeEvent)
        assertEquals("Should have count", "150", completeEvent?.metadata?.get("count"))
        assertEquals("Should have duration", "234", completeEvent?.metadata?.get("duration_ms"))
    }
    
    @Test
    fun `Media3 helper logs correctly`() = runBlocking {
        DiagnosticsLogger.Media3.logPlaybackStart("player", "vod")
        DiagnosticsLogger.Media3.logSeekOperation("player", 1000, 2000)
        DiagnosticsLogger.Media3.logBufferEvent("player", 500, true)
        
        delay(100)
        
        val events = DiagnosticsLogger.getRecentEvents(10)
        assertEquals("Should have 3 events", 3, events.size)
        
        val seekEvent = events.find { it.event == "seek_operation" }
        assertNotNull("Should have seek event", seekEvent)
        assertEquals("Should have from_ms", "1000", seekEvent?.metadata?.get("from_ms"))
        assertEquals("Should have to_ms", "2000", seekEvent?.metadata?.get("to_ms"))
        assertEquals("Should have delta", "1000", seekEvent?.metadata?.get("delta_ms"))
    }
    
    @Test
    fun `ComposeTV helper logs correctly`() = runBlocking {
        DiagnosticsLogger.ComposeTV.logScreenLoad("settings", 123)
        DiagnosticsLogger.ComposeTV.logFocusChange("settings", "field1", "field2")
        DiagnosticsLogger.ComposeTV.logKeyEvent("player", "DPAD_LEFT", "DOWN")
        
        delay(100)
        
        val events = DiagnosticsLogger.getRecentEvents(10)
        assertEquals("Should have 3 events", 3, events.size)
        
        val focusEvent = events.find { it.event == "focus_change" }
        assertNotNull("Should have focus event", focusEvent)
        assertEquals("Should have from", "field1", focusEvent?.metadata?.get("from"))
        assertEquals("Should have to", "field2", focusEvent?.metadata?.get("to"))
    }
    
    @Test
    fun `disabled logger does not log events`() = runBlocking {
        DiagnosticsLogger.isEnabled = false
        
        DiagnosticsLogger.logEvent(
            category = "test",
            event = "test_event"
        )
        
        delay(100)
        
        val events = DiagnosticsLogger.getRecentEvents(10)
        assertTrue("Should have no events when disabled", events.isEmpty())
    }
}
