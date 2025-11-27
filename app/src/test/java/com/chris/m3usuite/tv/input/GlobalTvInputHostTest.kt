package com.chris.m3usuite.tv.input

import android.view.KeyEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

/**
 * Unit tests for [GlobalTvInputHost].
 *
 * Verifies:
 * - KeyEvent → TvKeyRole → TvAction → controller pipeline
 * - TvKeyDebouncer integration
 * - TvInputDebugSink callback invocation
 * - Unsupported keycode handling
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GlobalTvInputHostTest {
    private lateinit var testScope: TestScope
    private lateinit var mockController: MockTvInputController
    private lateinit var mockDebugSink: MockTvInputDebugSink
    private lateinit var host: GlobalTvInputHost

    @Before
    fun setup() {
        testScope = TestScope()
        mockController = MockTvInputController()
        mockDebugSink = MockTvInputDebugSink()
        host =
            GlobalTvInputHost(
                controller = mockController,
                configs = DefaultTvScreenConfigs.all,
                scope = testScope,
                debug = mockDebugSink,
                debounceMs = 300L,
                enableDebouncing = true,
            )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // PIPELINE TESTS
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `handleKeyEvent maps DPAD_CENTER to controller`() =
        testScope.runTest {
            val event = createKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
            val ctx = TvScreenContext.player()

            host.handleKeyEvent(event, ctx)

            assertEquals(TvKeyRole.DPAD_CENTER, mockController.lastRole)
            assertEquals(ctx, mockController.lastContext)
        }

    @Test
    fun `handleKeyEvent maps MEDIA_PLAY_PAUSE to controller`() =
        testScope.runTest {
            val event = createKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            val ctx = TvScreenContext.player()

            host.handleKeyEvent(event, ctx)

            assertEquals(TvKeyRole.PLAY_PAUSE, mockController.lastRole)
        }

    @Test
    fun `handleKeyEvent maps DPAD_LEFT to controller`() =
        testScope.runTest {
            val event = createKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT)
            val ctx = TvScreenContext.library()

            host.handleKeyEvent(event, ctx)

            assertEquals(TvKeyRole.DPAD_LEFT, mockController.lastRole)
            assertEquals(ctx, mockController.lastContext)
        }

    @Test
    fun `handleKeyEvent maps FAST_FORWARD to controller`() =
        testScope.runTest {
            val event = createKeyEvent(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            val ctx = TvScreenContext.player()

            host.handleKeyEvent(event, ctx)

            assertEquals(TvKeyRole.FAST_FORWARD, mockController.lastRole)
        }

    @Test
    fun `handleKeyEvent maps MENU to controller`() =
        testScope.runTest {
            val event = createKeyEvent(KeyEvent.KEYCODE_MENU)
            val ctx = TvScreenContext.player()

            host.handleKeyEvent(event, ctx)

            assertEquals(TvKeyRole.MENU, mockController.lastRole)
        }

    @Test
    fun `handleKeyEvent maps BACK to controller`() =
        testScope.runTest {
            val event = createKeyEvent(KeyEvent.KEYCODE_BACK)
            val ctx = TvScreenContext.player()

            host.handleKeyEvent(event, ctx)

            assertEquals(TvKeyRole.BACK, mockController.lastRole)
        }

    @Test
    fun `handleKeyEvent maps number keys to controller`() =
        testScope.runTest {
            val event = createKeyEvent(KeyEvent.KEYCODE_5)
            val ctx = TvScreenContext.profileGate()

            host.handleKeyEvent(event, ctx)

            assertEquals(TvKeyRole.NUM_5, mockController.lastRole)
        }

    // ════════════════════════════════════════════════════════════════════════════
    // UNSUPPORTED KEYCODE TESTS
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `handleKeyEvent returns false for unsupported keycodes`() =
        testScope.runTest {
            // Volume keys are not supported
            val event = createKeyEvent(KeyEvent.KEYCODE_VOLUME_UP)
            val ctx = TvScreenContext.player()

            val handled = host.handleKeyEvent(event, ctx)

            assertFalse(handled)
            assertNull(mockController.lastRole) // Controller should not be called
        }

    @Test
    fun `handleKeyEvent logs unsupported keycodes to debug sink`() =
        testScope.runTest {
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
    fun `handleKeyEvent ignores ACTION_UP events`() =
        testScope.runTest {
            val event = createKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP)
            val ctx = TvScreenContext.player()

            val handled = host.handleKeyEvent(event, ctx)

            assertFalse(handled)
            assertNull(mockController.lastRole) // Controller should not be called
        }

    // ════════════════════════════════════════════════════════════════════════════
    // DEBOUNCER TESTS
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `handleKeyEvent debounces rapid duplicate events`() =
        testScope.runTest {
            val event1 = createKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
            val ctx = TvScreenContext.player()

            // First event should be processed immediately
            host.handleKeyEvent(event1, ctx)
            assertEquals(1, mockController.callCount)

            // Rapid second event within debounce window should be ignored initially
            val event2 = createKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
            host.handleKeyEvent(event2, ctx)

            // Still only 1 call (debounced)
            assertEquals(1, mockController.callCount)
        }

    @Test
    fun `handleKeyEvent processes events after debounce period`() =
        testScope.runTest {
            val event1 = createKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
            val ctx = TvScreenContext.player()

            // First event
            host.handleKeyEvent(event1, ctx)
            assertEquals(1, mockController.callCount)

            // Wait past debounce period
            advanceTimeBy(350L)

            // Second event should now be processed
            val event2 = createKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
            host.handleKeyEvent(event2, ctx)

            // Should have 2 calls now
            assertEquals(2, mockController.callCount)
        }

    @Test
    fun `handleKeyEvent allows different keys concurrently`() =
        testScope.runTest {
            val ctx = TvScreenContext.player()

            // Different keys should not debounce each other
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
    fun `handleKeyEvent calls debug sink with correct values`() =
        testScope.runTest {
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
    fun `handleKeyEvent reports unhandled events to debug sink`() =
        testScope.runTest {
            mockController.shouldHandle = false
            val event = createKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
            val ctx = TvScreenContext.player()

            host.handleKeyEvent(event, ctx)

            assertFalse(mockDebugSink.lastHandled)
        }

    @Test
    fun `handleKeyEvent reports blocked actions (null) to debug sink`() =
        testScope.runTest {
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
    fun `reset clears debouncer state`() =
        testScope.runTest {
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
    fun `resetKey clears debouncer state for specific key`() =
        testScope.runTest {
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

            // DPAD_DOWN should still be debounced (not reset)
            host.handleKeyEvent(createKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN), ctx)
            assertEquals(3, mockController.callCount) // Still 3, debounced
        }

    // ════════════════════════════════════════════════════════════════════════════
    // CONFIGURATION TESTS
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `host respects custom debounce time`() =
        testScope.runTest {
            // Create host with shorter debounce
            val shortDebounceHost =
                GlobalTvInputHost(
                    controller = mockController,
                    configs = DefaultTvScreenConfigs.all,
                    scope = testScope,
                    debug = mockDebugSink,
                    debounceMs = 100L,
                    enableDebouncing = true,
                )

            val event = createKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
            val ctx = TvScreenContext.player()

            shortDebounceHost.handleKeyEvent(event, ctx)
            val countAfterFirst = mockController.callCount

            // Wait 150ms (past 100ms debounce)
            advanceTimeBy(150L)

            shortDebounceHost.handleKeyEvent(event, ctx)

            // Should have processed both
            assertEquals(countAfterFirst + 1, mockController.callCount)
        }

    @Test
    fun `host can disable debouncing`() =
        testScope.runTest {
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
    ): KeyEvent =
        Mockito.mock(KeyEvent::class.java).also {
            Mockito.`when`(it.keyCode).thenReturn(keyCode)
            Mockito.`when`(it.action).thenReturn(action)
        }

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
