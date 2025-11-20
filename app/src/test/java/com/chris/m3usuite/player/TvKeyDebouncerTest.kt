package com.chris.m3usuite.player

import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for TvKeyDebouncer.
 *
 * Tests the rate-limiting behavior to ensure:
 * 1. First key press is processed immediately
 * 2. Rapid subsequent presses are debounced
 * 3. Presses after debounce period are processed
 * 4. Different keys are debounced independently
 */
class TvKeyDebouncerTest {
    private lateinit var scope: CoroutineScope
    private lateinit var debouncer: TvKeyDebouncer
    private val eventCounter = AtomicInteger(0)

    @Before
    fun setup() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        debouncer = TvKeyDebouncer(scope, debounceMs = 100L)
        eventCounter.set(0)
    }

    @After
    fun teardown() {
        debouncer.resetAll()
    }

    @Test
    fun `first key press is processed immediately`() =
        runBlocking {
            // Create a mock KeyEvent for DPAD_LEFT
            val handled =
                debouncer.handleKeyEventRateLimited(
                    keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                    event = createKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN),
                ) { _, _ ->
                    eventCounter.incrementAndGet()
                    true
                }

            assertTrue("Event should be handled", handled)
            assertEquals("Event should be processed immediately", 1, eventCounter.get())
        }

    @Test
    fun `rapid key presses are debounced`() =
        runBlocking {
            // First press - should be processed
            debouncer.handleKeyEventRateLimited(
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                event = createKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN),
            ) { _, _ ->
                eventCounter.incrementAndGet()
                true
            }

            // Second press immediately after - should be debounced
            debouncer.handleKeyEventRateLimited(
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                event = createKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN),
            ) { _, _ ->
                eventCounter.incrementAndGet()
                true
            }

            // Third press immediately after - should be debounced
            debouncer.handleKeyEventRateLimited(
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                event = createKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN),
            ) { _, _ ->
                eventCounter.incrementAndGet()
                true
            }

            assertEquals("Only first event should be processed", 1, eventCounter.get())
        }

    @Test
    fun `key press after debounce period is processed`() =
        runBlocking {
            // First press
            debouncer.handleKeyEventRateLimited(
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                event = createKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN),
            ) { _, _ ->
                eventCounter.incrementAndGet()
                true
            }

            assertEquals("First event processed", 1, eventCounter.get())

            // Wait for debounce period to expire
            delay(150L) // 100ms debounce + 50ms buffer

            // Second press after debounce period
            debouncer.handleKeyEventRateLimited(
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                event = createKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN),
            ) { _, _ ->
                eventCounter.incrementAndGet()
                true
            }

            assertEquals("Second event should be processed after debounce", 2, eventCounter.get())
        }

    @Test
    fun `different keys are debounced independently`() =
        runBlocking {
            // Press LEFT
            debouncer.handleKeyEventRateLimited(
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                event = createKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN),
            ) { _, _ ->
                eventCounter.incrementAndGet()
                true
            }

            // Press RIGHT immediately - should be processed (different key)
            debouncer.handleKeyEventRateLimited(
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
                event = createKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.ACTION_DOWN),
            ) { _, _ ->
                eventCounter.incrementAndGet()
                true
            }

            assertEquals("Both different keys should be processed", 2, eventCounter.get())
        }

    @Test
    fun `resetKey clears debouncing state for specific key`() =
        runBlocking {
            // First press
            debouncer.handleKeyEventRateLimited(
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                event = createKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN),
            ) { _, _ ->
                eventCounter.incrementAndGet()
                true
            }

            // Reset the key
            debouncer.resetKey(KeyEvent.KEYCODE_DPAD_LEFT)

            // Immediate press should be processed (state was reset)
            debouncer.handleKeyEventRateLimited(
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                event = createKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN),
            ) { _, _ ->
                eventCounter.incrementAndGet()
                true
            }

            assertEquals("Event after reset should be processed", 2, eventCounter.get())
        }

    @Test
    fun `ACTION_UP events are handled without processing handler`() =
        runBlocking {
            // ACTION_UP should return true but not call handler
            val handled =
                debouncer.handleKeyEventRateLimited(
                    keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                    event = createKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_UP),
                ) { _, _ ->
                    eventCounter.incrementAndGet()
                    true
                }

            assertTrue("ACTION_UP should be handled", handled)
            assertEquals("Handler should not be called for ACTION_UP", 0, eventCounter.get())
        }

    @Test
    fun `debouncing can be disabled`() =
        runBlocking {
            val noDebounceDeb = TvKeyDebouncer(scope, debounceMs = 100L, enableDebouncing = false)

            // Multiple rapid presses should all be processed when debouncing is disabled
            repeat(5) {
                noDebounceDeb.handleKeyEvent(
                    keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                    event = createKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN),
                ) { _, _ ->
                    eventCounter.incrementAndGet()
                    true
                }
            }

            assertEquals("All events should be processed when debouncing disabled", 5, eventCounter.get())
        }

    // Helper to create mock KeyEvent (simplified for testing)
    private fun createKeyEvent(
        keyCode: Int,
        action: Int,
    ): KeyEvent = KeyEvent(action, keyCode)
}
