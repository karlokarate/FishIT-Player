package com.fishit.player.infra.logging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import timber.log.Timber

/**
 * Unit tests for [UnifiedLog].
 *
 * Tests the facade's behavior, including:
 * - Minimum level filtering
 * - Timber integration
 * - API method behavior
 */
class UnifiedLogTest {
    private val capturedLogs = mutableListOf<LogEntry>()

    data class LogEntry(
        val priority: Int,
        val tag: String?,
        val message: String,
        val throwable: Throwable?,
    )

    private inner class TestTree : Timber.Tree() {
        override fun log(
            priority: Int,
            tag: String?,
            message: String,
            t: Throwable?,
        ) {
            capturedLogs.add(LogEntry(priority, tag, message, t))
        }
    }

    @Before
    fun setup() {
        // Initialize Timber with a test tree
        capturedLogs.clear()
        Timber.plant(TestTree())

        // Reset minLevel to default
        UnifiedLog.setMinLevel(UnifiedLog.Level.DEBUG)
    }

    @After
    fun teardown() {
        Timber.uprootAll()
        capturedLogs.clear()
    }

    // ========== LEVEL FILTERING TESTS ==========

    @Test
    fun `verbose logs are filtered when minLevel is DEBUG`() {
        UnifiedLog.setMinLevel(UnifiedLog.Level.DEBUG)

        UnifiedLog.v("TestTag", "verbose message")

        // Verify no logs were captured
        assertEquals(0, capturedLogs.size)
    }

    @Test
    fun `debug logs pass when minLevel is DEBUG`() {
        UnifiedLog.setMinLevel(UnifiedLog.Level.DEBUG)

        UnifiedLog.d("TestTag", "debug message")

        // Verify exactly one log was captured
        assertEquals(1, capturedLogs.size)
        val log = capturedLogs[0]
        assertEquals("TestTag", log.tag)
        assertEquals("debug message", log.message)
        assertEquals(android.util.Log.DEBUG, log.priority)
    }

    @Test
    fun `info logs pass when minLevel is DEBUG`() {
        UnifiedLog.setMinLevel(UnifiedLog.Level.DEBUG)

        UnifiedLog.i("TestTag", "info message")

        assertEquals(1, capturedLogs.size)
        val log = capturedLogs[0]
        assertEquals("TestTag", log.tag)
        assertEquals("info message", log.message)
        assertEquals(android.util.Log.INFO, log.priority)
    }

    @Test
    fun `warn logs pass when minLevel is DEBUG`() {
        UnifiedLog.setMinLevel(UnifiedLog.Level.DEBUG)

        UnifiedLog.w("TestTag", "warn message")

        assertEquals(1, capturedLogs.size)
        val log = capturedLogs[0]
        assertEquals("TestTag", log.tag)
        assertEquals("warn message", log.message)
        assertEquals(android.util.Log.WARN, log.priority)
    }

    @Test
    fun `error logs pass when minLevel is DEBUG`() {
        UnifiedLog.setMinLevel(UnifiedLog.Level.DEBUG)

        UnifiedLog.e("TestTag", "error message")

        assertEquals(1, capturedLogs.size)
        val log = capturedLogs[0]
        assertEquals("TestTag", log.tag)
        assertEquals("error message", log.message)
        assertEquals(android.util.Log.ERROR, log.priority)
    }

    @Test
    fun `debug logs are filtered when minLevel is INFO`() {
        UnifiedLog.setMinLevel(UnifiedLog.Level.INFO)

        UnifiedLog.d("TestTag", "debug message")

        // Verify no logs were captured
        assertEquals(0, capturedLogs.size)
    }

    @Test
    fun `info logs pass when minLevel is INFO`() {
        UnifiedLog.setMinLevel(UnifiedLog.Level.INFO)

        UnifiedLog.i("TestTag", "info message")

        assertEquals(1, capturedLogs.size)
        assertEquals("info message", capturedLogs[0].message)
    }

    @Test
    fun `warn and error logs pass when minLevel is WARN`() {
        UnifiedLog.setMinLevel(UnifiedLog.Level.WARN)

        UnifiedLog.w("TestTag", "warn message")
        UnifiedLog.e("TestTag", "error message")

        // Verify exactly 2 logs were captured
        assertEquals(2, capturedLogs.size)
        assertEquals("warn message", capturedLogs[0].message)
        assertEquals("error message", capturedLogs[1].message)
    }

    @Test
    fun `only error logs pass when minLevel is ERROR`() {
        UnifiedLog.setMinLevel(UnifiedLog.Level.ERROR)

        UnifiedLog.v("TestTag", "verbose")
        UnifiedLog.d("TestTag", "debug")
        UnifiedLog.i("TestTag", "info")
        UnifiedLog.w("TestTag", "warn")
        UnifiedLog.e("TestTag", "error")

        // Only ERROR should pass
        assertEquals(1, capturedLogs.size)
        assertEquals("error", capturedLogs[0].message)
        assertEquals(android.util.Log.ERROR, capturedLogs[0].priority)
    }

    // ========== THROWABLE HANDLING TESTS ==========

    @Test
    fun `logs with throwable are passed to Timber`() {
        val exception = RuntimeException("test exception")

        UnifiedLog.e("TestTag", "error with exception", exception)

        assertEquals(1, capturedLogs.size)
        val log = capturedLogs[0]
        // Message may include stack trace from Timber, so check it starts with our message
        assert(log.message.startsWith("error with exception"))
        assertEquals(exception, log.throwable)
    }

    @Test
    fun `warn with throwable is passed to Timber`() {
        val exception = IllegalStateException("test state")

        UnifiedLog.w("TestTag", "warning with exception", exception)

        assertEquals(1, capturedLogs.size)
        val log = capturedLogs[0]
        // Message may include stack trace from Timber, so check it starts with our message
        assert(log.message.startsWith("warning with exception"))
        assertEquals(exception, log.throwable)
    }

    // ========== BACKWARD COMPATIBILITY TESTS ==========

    @Test
    fun `deprecated debug method works`() {
        @Suppress("DEPRECATION")
        UnifiedLog.debug("TestTag", "debug message")

        assertEquals(1, capturedLogs.size)
        assertEquals("debug message", capturedLogs[0].message)
    }

    @Test
    fun `deprecated info method works`() {
        @Suppress("DEPRECATION")
        UnifiedLog.info("TestTag", "info message")

        assertEquals(1, capturedLogs.size)
        assertEquals("info message", capturedLogs[0].message)
    }

    @Test
    fun `deprecated warn method works`() {
        @Suppress("DEPRECATION")
        UnifiedLog.warn("TestTag", "warn message")

        assertEquals(1, capturedLogs.size)
        assertEquals("warn message", capturedLogs[0].message)
    }

    @Test
    fun `deprecated error method works`() {
        @Suppress("DEPRECATION")
        UnifiedLog.error("TestTag", "error message")

        assertEquals(1, capturedLogs.size)
        assertEquals("error message", capturedLogs[0].message)
    }

    @Test
    fun `deprecated verbose method works`() {
        UnifiedLog.setMinLevel(UnifiedLog.Level.VERBOSE)

        @Suppress("DEPRECATION")
        UnifiedLog.verbose("TestTag", "verbose message")

        assertEquals(1, capturedLogs.size)
        assertEquals("verbose message", capturedLogs[0].message)
    }

    // ========== TAG HANDLING TESTS ==========

    @Test
    fun `tag is properly set in Timber`() {
        UnifiedLog.d("CustomTag", "message")

        assertEquals(1, capturedLogs.size)
        assertEquals("CustomTag", capturedLogs[0].tag)
    }

    @Test
    fun `different tags are handled correctly`() {
        UnifiedLog.d("Tag1", "message1")
        UnifiedLog.i("Tag2", "message2")
        UnifiedLog.w("Tag3", "message3")

        assertEquals(3, capturedLogs.size)
        assertEquals("Tag1", capturedLogs[0].tag)
        assertEquals("Tag2", capturedLogs[1].tag)
        assertEquals("Tag3", capturedLogs[2].tag)
    }

    // ========== LAZY LOGGING TESTS ==========

    @Test
    fun `lambda-based logging does not evaluate message when level is filtered`() {
        UnifiedLog.setMinLevel(UnifiedLog.Level.INFO)
        var messageEvaluated = false

        UnifiedLog.d("TestTag") {
            messageEvaluated = true
            "debug message"
        }

        // Message lambda should NOT be evaluated since DEBUG is filtered
        assertEquals(false, messageEvaluated)
        assertEquals(0, capturedLogs.size)
    }

    @Test
    fun `lambda-based logging evaluates message when level is enabled`() {
        UnifiedLog.setMinLevel(UnifiedLog.Level.DEBUG)
        var messageEvaluated = false

        UnifiedLog.d("TestTag") {
            messageEvaluated = true
            "debug message"
        }

        // Message lambda SHOULD be evaluated since DEBUG is enabled
        assertEquals(true, messageEvaluated)
        assertEquals(1, capturedLogs.size)
        assertEquals("debug message", capturedLogs[0].message)
    }

    @Test
    fun `lambda-based verbose logging is lazy when verbose is disabled`() {
        UnifiedLog.setMinLevel(UnifiedLog.Level.DEBUG) // VERBOSE is filtered
        var counter = 0

        UnifiedLog.v("TestTag") { 
            counter++
            "verbose message"
        }

        // Counter should NOT increment since VERBOSE is filtered
        assertEquals(0, counter)
        assertEquals(0, capturedLogs.size)
    }

    @Test
    fun `lambda-based verbose logging evaluates when verbose is enabled`() {
        UnifiedLog.setMinLevel(UnifiedLog.Level.VERBOSE)
        var counter = 0

        UnifiedLog.v("TestTag") {
            counter++
            "verbose message"
        }

        // Counter SHOULD increment exactly once
        assertEquals(1, counter)
        assertEquals(1, capturedLogs.size)
        assertEquals("verbose message", capturedLogs[0].message)
    }

    @Test
    fun `lambda-based info logging with throwable works correctly`() {
        UnifiedLog.setMinLevel(UnifiedLog.Level.DEBUG)
        val exception = RuntimeException("test")
        var evaluated = false

        UnifiedLog.i("TestTag", exception) {
            evaluated = true
            "info with exception"
        }

        assertEquals(true, evaluated)
        assertEquals(1, capturedLogs.size)
        // Message may include stack trace from Timber, so check it starts with our message
        assert(capturedLogs[0].message.startsWith("info with exception"))
        assertEquals(exception, capturedLogs[0].throwable)
    }

    @Test
    fun `lambda-based error logging with throwable is lazy`() {
        UnifiedLog.setMinLevel(UnifiedLog.Level.ERROR)
        val exception = RuntimeException("test")
        var evaluated = false

        // WARN is filtered, so lambda should not be evaluated
        UnifiedLog.w("TestTag", exception) {
            evaluated = true
            "warn message"
        }

        assertEquals(false, evaluated)
        assertEquals(0, capturedLogs.size)
    }

    @Test
    fun `lambda-based error logging evaluates when enabled`() {
        UnifiedLog.setMinLevel(UnifiedLog.Level.ERROR)
        val exception = RuntimeException("test")
        var evaluated = false

        UnifiedLog.e("TestTag", exception) {
            evaluated = true
            "error message"
        }

        assertEquals(true, evaluated)
        assertEquals(1, capturedLogs.size)
        assertEquals(exception, capturedLogs[0].throwable)
    }

    @Test
    fun `isEnabled returns correct values for different levels`() {
        UnifiedLog.setMinLevel(UnifiedLog.Level.INFO)

        assertEquals(false, UnifiedLog.isEnabled(UnifiedLog.Level.VERBOSE))
        assertEquals(false, UnifiedLog.isEnabled(UnifiedLog.Level.DEBUG))
        assertEquals(true, UnifiedLog.isEnabled(UnifiedLog.Level.INFO))
        assertEquals(true, UnifiedLog.isEnabled(UnifiedLog.Level.WARN))
        assertEquals(true, UnifiedLog.isEnabled(UnifiedLog.Level.ERROR))
    }

    @Test
    fun `string-based overload still works for convenience`() {
        UnifiedLog.setMinLevel(UnifiedLog.Level.DEBUG)

        UnifiedLog.d("TestTag", "constant message")

        assertEquals(1, capturedLogs.size)
        assertEquals("constant message", capturedLogs[0].message)
    }
}
