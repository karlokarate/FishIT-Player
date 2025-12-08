package com.chris.m3usuite.tv.input

import android.view.KeyEvent
import com.chris.m3usuite.player.TvKeyDebouncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Integration tests for TvKeyDebouncer with TvKeyMapper.
 *
 * Tests verify that debounced events produce correct TvKeyRole when passed to the mapper.
 * This test simulates the Phase 6 pipeline: KeyEvent → TvKeyDebouncer → TvKeyMapper → TvKeyRole
 *
 * Contract Reference: INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 9.2
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvKeyDebouncerIntegrationTest {
    private lateinit var testScope: CoroutineScope
    private lateinit var debouncer: TvKeyDebouncer

    @Before
    fun setUp() {
        testScope = CoroutineScope(Dispatchers.Unconfined + Job())
        debouncer = TvKeyDebouncer(testScope, debounceMs = 300L)
    }

    // ══════════════════════════════════════════════════════════════════
    // BASIC INTEGRATION TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `debounced DPAD event maps to correct TvKeyRole`() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)
        var mappedRole: TvKeyRole? = null

        debouncer.handleKeyEvent(KeyEvent.KEYCODE_DPAD_UP, event) { keyCode, _ ->
            mappedRole = TvKeyMapper.mapKeyCode(keyCode)
            true
        }

        assertEquals(TvKeyRole.DPAD_UP, mappedRole)
    }

    @Test
    fun `debounced media key event maps to correct TvKeyRole`() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        var mappedRole: TvKeyRole? = null

        debouncer.handleKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, event) { keyCode, _ ->
            mappedRole = TvKeyMapper.mapKeyCode(keyCode)
            true
        }

        assertEquals(TvKeyRole.PLAY_PAUSE, mappedRole)
    }

    @Test
    fun `debounced BACK key event maps to correct TvKeyRole`() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
        var mappedRole: TvKeyRole? = null

        debouncer.handleKeyEvent(KeyEvent.KEYCODE_BACK, event) { keyCode, _ ->
            mappedRole = TvKeyMapper.mapKeyCode(keyCode)
            true
        }

        assertEquals(TvKeyRole.BACK, mappedRole)
    }

    @Test
    fun `debounced number key event maps to correct TvKeyRole`() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_5)
        var mappedRole: TvKeyRole? = null

        debouncer.handleKeyEvent(KeyEvent.KEYCODE_5, event) { keyCode, _ ->
            mappedRole = TvKeyMapper.mapKeyCode(keyCode)
            true
        }

        assertEquals(TvKeyRole.NUM_5, mappedRole)
    }

    // ══════════════════════════════════════════════════════════════════
    // UNSUPPORTED KEY TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `debounced unsupported key returns null from mapper`() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)
        var mappedRole: TvKeyRole? = TvKeyRole.DPAD_UP // Initialize to non-null

        debouncer.handleKeyEvent(KeyEvent.KEYCODE_A, event) { keyCode, _ ->
            mappedRole = TvKeyMapper.mapKeyCode(keyCode)
            true
        }

        assertNull(mappedRole)
    }

    // ══════════════════════════════════════════════════════════════════
    // DEBOUNCING BEHAVIOR TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `first key event is processed immediately`() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)
        var processedCount = 0
        var lastRole: TvKeyRole? = null

        debouncer.handleKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, event) { keyCode, _ ->
            processedCount++
            lastRole = TvKeyMapper.mapKeyCode(keyCode)
            true
        }

        assertEquals(1, processedCount)
        assertEquals(TvKeyRole.DPAD_LEFT, lastRole)
    }

    @Test
    fun `ACTION_UP events are handled (not processed for debouncing)`() {
        val event = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP)
        var handlerCalled = false

        val result =
            debouncer.handleKeyEvent(KeyEvent.KEYCODE_DPAD_UP, event) { _, _ ->
                handlerCalled = true
                true
            }

        // ACTION_UP should return true (consumed) but not call handler (per TvKeyDebouncer design)
        assertTrue(result)
        assertFalse(handlerCalled)
    }

    // ══════════════════════════════════════════════════════════════════
    // RATE-LIMITED MODE TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `rate-limited mode processes first key event`() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
        var mappedRole: TvKeyRole? = null

        debouncer.handleKeyEventRateLimited(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, event) { keyCode, _ ->
            mappedRole = TvKeyMapper.mapKeyCode(keyCode)
            true
        }

        assertEquals(TvKeyRole.FAST_FORWARD, mappedRole)
    }

    @Test
    fun `rate-limited mode maps REWIND correctly`() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_REWIND)
        var mappedRole: TvKeyRole? = null

        debouncer.handleKeyEventRateLimited(KeyEvent.KEYCODE_MEDIA_REWIND, event) { keyCode, _ ->
            mappedRole = TvKeyMapper.mapKeyCode(keyCode)
            true
        }

        assertEquals(TvKeyRole.REWIND, mappedRole)
    }

    // ══════════════════════════════════════════════════════════════════
    // RESET TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `resetKey allows immediate re-processing of same key`() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
        var processedCount = 0

        // First event
        debouncer.handleKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, event) { keyCode, _ ->
            processedCount++
            TvKeyMapper.mapKeyCode(keyCode)
            true
        }

        // Reset the key
        debouncer.resetKey(KeyEvent.KEYCODE_DPAD_RIGHT)

        // Second event should process immediately after reset
        debouncer.handleKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, event) { keyCode, _ ->
            processedCount++
            TvKeyMapper.mapKeyCode(keyCode)
            true
        }

        assertEquals(2, processedCount)
    }

    @Test
    fun `resetAll clears all debounce state`() {
        val upEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)
        var upCount = 0
        var downCount = 0

        // First events
        debouncer.handleKeyEvent(KeyEvent.KEYCODE_DPAD_UP, upEvent) { _, _ ->
            upCount++
            true
        }
        debouncer.handleKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, downEvent) { _, _ ->
            downCount++
            true
        }

        // Reset all
        debouncer.resetAll()

        // Both keys should process immediately after reset
        debouncer.handleKeyEvent(KeyEvent.KEYCODE_DPAD_UP, upEvent) { _, _ ->
            upCount++
            true
        }
        debouncer.handleKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, downEvent) { _, _ ->
            downCount++
            true
        }

        assertEquals(2, upCount)
        assertEquals(2, downCount)
    }

    // ══════════════════════════════════════════════════════════════════
    // DISABLED DEBOUNCING TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `disabled debouncing passes all events through`() {
        val debouncerDisabled = TvKeyDebouncer(testScope, debounceMs = 300L, enableDebouncing = false)
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        var mappedRole: TvKeyRole? = null

        debouncerDisabled.handleKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER, event) { keyCode, _ ->
            mappedRole = TvKeyMapper.mapKeyCode(keyCode)
            true
        }

        assertEquals(TvKeyRole.DPAD_CENTER, mappedRole)
    }

    // ══════════════════════════════════════════════════════════════════
    // CHANNEL KEY INTEGRATION TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `debounced CHANNEL_UP maps correctly`() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CHANNEL_UP)
        var mappedRole: TvKeyRole? = null

        debouncer.handleKeyEvent(KeyEvent.KEYCODE_CHANNEL_UP, event) { keyCode, _ ->
            mappedRole = TvKeyMapper.mapKeyCode(keyCode)
            true
        }

        assertEquals(TvKeyRole.CHANNEL_UP, mappedRole)
    }

    @Test
    fun `debounced CHANNEL_DOWN maps correctly`() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN)
        var mappedRole: TvKeyRole? = null

        debouncer.handleKeyEvent(KeyEvent.KEYCODE_CHANNEL_DOWN, event) { keyCode, _ ->
            mappedRole = TvKeyMapper.mapKeyCode(keyCode)
            true
        }

        assertEquals(TvKeyRole.CHANNEL_DOWN, mappedRole)
    }

    // ══════════════════════════════════════════════════════════════════
    // INFO KEY INTEGRATION TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `debounced INFO key maps correctly`() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_INFO)
        var mappedRole: TvKeyRole? = null

        debouncer.handleKeyEvent(KeyEvent.KEYCODE_INFO, event) { keyCode, _ ->
            mappedRole = TvKeyMapper.mapKeyCode(keyCode)
            true
        }

        assertEquals(TvKeyRole.INFO, mappedRole)
    }

    @Test
    fun `debounced GUIDE key maps correctly`() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_GUIDE)
        var mappedRole: TvKeyRole? = null

        debouncer.handleKeyEvent(KeyEvent.KEYCODE_GUIDE, event) { keyCode, _ ->
            mappedRole = TvKeyMapper.mapKeyCode(keyCode)
            true
        }

        assertEquals(TvKeyRole.GUIDE, mappedRole)
    }

    // ══════════════════════════════════════════════════════════════════
    // PIPELINE COMPLETENESS TESTS
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `complete pipeline - DPAD_UP event produces DPAD_UP role`() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)
        val role = executeCompletePipeline(event)
        assertEquals(TvKeyRole.DPAD_UP, role)
    }

    @Test
    fun `complete pipeline - MENU event produces MENU role`() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU)
        val role = executeCompletePipeline(event)
        assertEquals(TvKeyRole.MENU, role)
    }

    @Test
    fun `complete pipeline - all DPAD keys produce correct roles`() {
        assertEquals(TvKeyRole.DPAD_UP, executeCompletePipeline(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)))
        assertEquals(TvKeyRole.DPAD_DOWN, executeCompletePipeline(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)))
        assertEquals(TvKeyRole.DPAD_LEFT, executeCompletePipeline(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)))
        assertEquals(TvKeyRole.DPAD_RIGHT, executeCompletePipeline(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
        assertEquals(TvKeyRole.DPAD_CENTER, executeCompletePipeline(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)))
    }

    @Test
    fun `complete pipeline - all number keys produce correct roles`() {
        for (i in 0..9) {
            val keyCode = KeyEvent.KEYCODE_0 + i
            val event = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val role = executeCompletePipeline(event)
            assertNotNull("Number key $i should map to a role", role)
            with(TvKeyRole.Companion) {
                assertEquals(i, role!!.toDigit())
            }
        }
    }

    /**
     * Helper function to execute the complete Phase 6 pipeline simulation.
     */
    private fun executeCompletePipeline(event: KeyEvent): TvKeyRole? {
        var result: TvKeyRole? = null
        debouncer.handleKeyEvent(event.keyCode, event) { keyCode, _ ->
            result = TvKeyMapper.mapKeyCode(keyCode)
            true
        }
        return result
    }
}
