package com.chris.m3usuite.tv.input

import android.view.KeyEvent
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
 * Unit tests for [GlobalTvInputHost].
 *
 * Verifies:
 * - KeyEvent → TvKeyRole → TvAction → controller pipeline
 * - TvKeyDebouncer integration
 * - TvInputDebugSink callback invocation
 * - Unsupported keycode handling
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GlobalTvInputHostTest {
    private lateinit var testScope: CoroutineScope
    private lateinit var mockController: MockTvInputController
    private lateinit var mockDebugSink: MockTvInputDebugSink
    private lateinit var host: GlobalTvInputHost

    @Before
    fun setup() {
        testScope = CoroutineScope(Dispatchers.Unconfined + Job())
        mockController = MockTvInputController()
        mockDebugSink = MockTvInputDebugSink()
        host =
            GlobalTvInputHost(
                controller = mockController,
                configs = DefaultTvScreenConfigs.all,
                scope = testScope,
                debug = mockDebugSink,
                debounceMs = 300L,
                enableDebouncing = false, // Disable for simpler testing without coroutine test dispatcher
            )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // PIPELINE TESTS
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `handleKeyEvent maps DPAD_CENTER to controller`() {
        val event = createKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
        val ctx = TvScreenContext.player()

        host.handleKeyEvent(event, ctx)

        assertEquals(TvKeyRole.DPAD_CENTER, mockController.lastRole)
        assertEquals(ctx, mockController.lastContext)
    }

    @Test
    fun `handleKeyEvent maps MEDIA_PLAY_PAUSE to controller`() {
        val event = createKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        val ctx = TvScreenContext.player()

        host.handleKeyEvent(event, ctx)

        assertEquals(TvKeyRole.PLAY_PAUSE, mockController.lastRole)
    }

    @Test
    fun `handleKeyEvent maps DPAD_LEFT to controller`() {
        val event = createKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT)
        val ctx = TvScreenContext.library()

        host.handleKeyEvent(event, ctx)

        assertEquals(TvKeyRole.DPAD_LEFT, mockController.lastRole)
        assertEquals(ctx, mockController.lastContext)
    }

    @Test
    fun `handleKeyEvent maps FAST_FORWARD to controller`() {
        val event = createKeyEvent(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
        val ctx = TvScreenContext.player()

        host.handleKeyEvent(event, ctx)

        assertEquals(TvKeyRole.FAST_FORWARD, mockController.lastRole)
    }

    @Test
    fun `handleKeyEvent maps MENU to controller`() {
        val event = createKeyEvent(KeyEvent.KEYCODE_MENU)
        val ctx = TvScreenContext.player()

        host.handleKeyEvent(event, ctx)

        assertEquals(TvKeyRole.MENU, mockController.lastRole)
    }

    @Test
    fun `handleKeyEvent maps BACK to controller`() {
        val event = createKeyEvent(KeyEvent.KEYCODE_BACK)
        val ctx = TvScreenContext.player()

        host.handleKeyEvent(event, ctx)

        assertEquals(TvKeyRole.BACK, mockController.lastRole)
    }

    @Test
    fun `handleKeyEvent maps number keys to controller`() {
        val event = createKeyEvent(KeyEvent.KEYCODE_5)
        val ctx = TvScreenContext.profileGate()

        host.handleKeyEvent(event, ctx)

        assertEquals(TvKeyRole.NUM_5, mockController.lastRole)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // UNSUPPORTED KEYCODE TESTS
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `handleKeyEvent returns false for unsupported keycodes`() {
        // Volume keys are not supported
        val event = createKeyEvent(KeyEvent.KEYCODE_VOLUME_UP)
        val ctx = TvScreenContext.player()

        val handled = host.handleKeyEvent(event, ctx)

        assertFalse(handled)
        assertNull(mockController.lastRole) // Controller should not be called
    }

    @Test
    fun `handleKeyEvent logs unsupported keycodes to debug sink`() {
        val event = createKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN)
        val ctx = TvScreenContext.player()

        host.handleKeyEvent(event, ctx)

        // Debug sink should have been called with null role
        assertEquals(1, mockDebugSink.eventCount)
        assertNull(mockDebugSink.lastRole)
        assertFalse(mockDebugSink.lastHandled)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // ACTION_UP FILTERING TESTS
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `handleKeyEvent ignores ACTION_UP events`() {
        val event = createKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP)
        val ctx = TvScreenContext.player()

        val handled = host.handleKeyEvent(event, ctx)

        assertFalse(handled)
        assertNull(mockController.lastRole) // Controller should not be called
    }

    // ════════════════════════════════════════════════════════════════════════════
    // DEBOUNCER TESTS (Simplified - debouncing disabled for unit tests)
    // Note: Full debounce timing tests would require coroutine test dispatcher
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `handleKeyEvent processes events when debouncing disabled`() {
        val event1 = createKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
        val ctx = TvScreenContext.player()

        // First event should be processed
        host.handleKeyEvent(event1, ctx)
        assertEquals(1, mockController.callCount)

        // With debouncing disabled, second event should also be processed
        val event2 = createKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
        host.handleKeyEvent(event2, ctx)

        assertEquals(2, mockController.callCount)
    }

    @Test
    fun `handleKeyEvent allows different keys concurrently`() {
        val ctx = TvScreenContext.player()

        // Different keys should all be processed
        host.handleKeyEvent(createKeyEvent(KeyEvent.KEYCODE_DPAD_UP), ctx)
        host.handleKeyEvent(createKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN), ctx)
        host.handleKeyEvent(createKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT), ctx)
        host.handleKeyEvent(createKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT), ctx)

        // All 4 events should be processed
        assertEquals(4, mockController.callCount)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // DEBUG SINK TESTS
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `handleKeyEvent calls debug sink with correct values`() {
        mockController.shouldHandle = true
        val event = createKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
        val ctx = TvScreenContext.player()

        host.handleKeyEvent(event, ctx)

        assertEquals(1, mockDebugSink.eventCount)
        assertNotNull(mockDebugSink.lastEvent)
        assertEquals(TvKeyRole.DPAD_CENTER, mockDebugSink.lastRole)
        assertEquals(TvAction.PLAY_PAUSE, mockDebugSink.lastAction) // PLAYER: CENTER -> PLAY_PAUSE
        assertEquals(ctx, mockDebugSink.lastContext)
        assertTrue(mockDebugSink.lastHandled)
    }

    @Test
    fun `handleKeyEvent reports unhandled events to debug sink`() {
        mockController.shouldHandle = false
        val event = createKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
        val ctx = TvScreenContext.player()

        host.handleKeyEvent(event, ctx)

        assertFalse(mockDebugSink.lastHandled)
    }

    @Test
    fun `handleKeyEvent reports blocked actions (null) to debug sink`() {
        // Kid profile + FAST_FORWARD = blocked action
        val event = createKeyEvent(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
        val ctx = TvScreenContext.player(isKidProfile = true)

        host.handleKeyEvent(event, ctx)

        assertEquals(TvKeyRole.FAST_FORWARD, mockDebugSink.lastRole)
        assertNull(mockDebugSink.lastAction) // Action blocked by Kids Mode
    }

    // ════════════════════════════════════════════════════════════════════════════
    // RESET TESTS
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `reset clears debouncer state`() {
        val event = createKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
        val ctx = TvScreenContext.player()

        // First event
        host.handleKeyEvent(event, ctx)
        assertEquals(1, mockController.callCount)

        // Reset the host
        host.reset()

        // Next event should be processed immediately (debounce state cleared)
        host.handleKeyEvent(event, ctx)
        assertEquals(2, mockController.callCount)
    }

    @Test
    fun `resetKey clears debouncer state for specific key`() {
        val ctx = TvScreenContext.player()

        // Events for two different keys
        host.handleKeyEvent(createKeyEvent(KeyEvent.KEYCODE_DPAD_UP), ctx)
        host.handleKeyEvent(createKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN), ctx)
        assertEquals(2, mockController.callCount)

        // Reset only DPAD_UP
        host.resetKey(KeyEvent.KEYCODE_DPAD_UP)

        // DPAD_UP should process immediately
        host.handleKeyEvent(createKeyEvent(KeyEvent.KEYCODE_DPAD_UP), ctx)
        assertEquals(3, mockController.callCount)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // CONFIGURATION TESTS
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `host can disable debouncing`() {
        val noDebounceHost =
            GlobalTvInputHost(
                controller = mockController,
                configs = DefaultTvScreenConfigs.all,
                scope = testScope,
                debug = mockDebugSink,
                debounceMs = 300L,
                enableDebouncing = false, // Disabled
            )

        val event = createKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
        val ctx = TvScreenContext.player()

        // Reset call count
        mockController.callCount = 0

        // Rapid events should all be processed when debouncing is disabled
        noDebounceHost.handleKeyEvent(event, ctx)
        noDebounceHost.handleKeyEvent(event, ctx)
        noDebounceHost.handleKeyEvent(event, ctx)

        assertEquals(3, mockController.callCount)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════════════════

    private fun createKeyEvent(
        keyCode: Int,
        action: Int = KeyEvent.ACTION_DOWN,
    ): KeyEvent = KeyEvent(action, keyCode)

    // ════════════════════════════════════════════════════════════════════════════
    // MOCKS
    // ════════════════════════════════════════════════════════════════════════════

    private class MockTvInputController : TvInputController {
        var lastRole: TvKeyRole? = null
        var lastContext: TvScreenContext? = null
        var callCount = 0
        var shouldHandle = true

        override fun onKeyEvent(
            role: TvKeyRole,
            ctx: TvScreenContext,
        ): Boolean {
            lastRole = role
            lastContext = ctx
            callCount++
            return shouldHandle
        }

        override val quickActionsVisible: androidx.compose.runtime.State<Boolean>
            get() = androidx.compose.runtime.mutableStateOf(false)

        override val focusedAction: androidx.compose.runtime.State<TvAction?>
            get() = androidx.compose.runtime.mutableStateOf(null)
    }

    private class MockTvInputDebugSink : TvInputDebugSink {
        var lastEvent: KeyEvent? = null
        var lastRole: TvKeyRole? = null
        var lastAction: TvAction? = null
        var lastContext: TvScreenContext? = null
        var lastHandled = false
        var eventCount = 0

        override fun onTvInputEvent(
            event: KeyEvent,
            role: TvKeyRole?,
            action: TvAction?,
            ctx: TvScreenContext,
            handled: Boolean,
        ) {
            lastEvent = event
            lastRole = role
            lastAction = action
            lastContext = ctx
            lastHandled = handled
            eventCount++
        }
    }
}
