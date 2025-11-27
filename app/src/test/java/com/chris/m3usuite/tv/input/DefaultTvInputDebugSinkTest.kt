package com.chris.m3usuite.tv.input

import android.view.KeyEvent
import com.chris.m3usuite.core.debug.GlobalDebug
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

/**
 * Unit tests for [DefaultTvInputDebugSink].
 *
 * Verifies:
 * - Event capture to history and events flow when enabled
 * - No capture when disabled
 * - History size limits
 * - Correct snapshot data
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTvInputDebugSinkTest {

    @Before
    fun setup() {
        DefaultTvInputDebugSink.clearHistory()
        GlobalDebug.setTvInputInspectorEnabled(true)
    }

    @After
    fun teardown() {
        DefaultTvInputDebugSink.clearHistory()
        GlobalDebug.setTvInputInspectorEnabled(false)
    }

    @Test
    fun `onTvInputEvent captures event when enabled`() = runTest {
        val event = createMockKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
        val ctx = TvScreenContext.player()

        DefaultTvInputDebugSink.onTvInputEvent(
            event = event,
            role = TvKeyRole.DPAD_CENTER,
            action = TvAction.PLAY_PAUSE,
            ctx = ctx,
            handled = true,
        )

        val history = DefaultTvInputDebugSink.history.first()
        assertEquals(1, history.size)
        val snapshot = history[0]
        assertEquals("KEYCODE_DPAD_CENTER", snapshot.keyCodeName)
        assertEquals(TvKeyRole.DPAD_CENTER, snapshot.role)
        assertEquals(TvAction.PLAY_PAUSE, snapshot.action)
        assertEquals(TvScreenId.PLAYER, snapshot.screenId)
        assertTrue(snapshot.handled)
    }

    @Test
    fun `onTvInputEvent does not capture when disabled`() = runTest {
        GlobalDebug.setTvInputInspectorEnabled(false)

        val event = createMockKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
        val ctx = TvScreenContext.player()

        DefaultTvInputDebugSink.onTvInputEvent(
            event = event,
            role = TvKeyRole.DPAD_CENTER,
            action = TvAction.PLAY_PAUSE,
            ctx = ctx,
            handled = true,
        )

        val history = DefaultTvInputDebugSink.history.first()
        assertTrue(history.isEmpty())
    }

    @Test
    fun `onTvInputEvent records null role correctly`() = runTest {
        val event = createMockKeyEvent(KeyEvent.KEYCODE_VOLUME_UP) // Unsupported
        val ctx = TvScreenContext.player()

        DefaultTvInputDebugSink.onTvInputEvent(
            event = event,
            role = null,
            action = null,
            ctx = ctx,
            handled = false,
        )

        val history = DefaultTvInputDebugSink.history.first()
        assertEquals(1, history.size)
        val snapshot = history[0]
        assertNull(snapshot.role)
        assertNull(snapshot.action)
        assertFalse(snapshot.handled)
    }

    @Test
    fun `onTvInputEvent records blocked action as null`() = runTest {
        val event = createMockKeyEvent(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
        val ctx = TvScreenContext.player(isKidProfile = true)

        DefaultTvInputDebugSink.onTvInputEvent(
            event = event,
            role = TvKeyRole.FAST_FORWARD,
            action = null, // Blocked by Kids Mode
            ctx = ctx,
            handled = false,
        )

        val history = DefaultTvInputDebugSink.history.first()
        assertEquals(1, history.size)
        val snapshot = history[0]
        assertEquals(TvKeyRole.FAST_FORWARD, snapshot.role)
        assertNull(snapshot.action) // Blocked
        assertFalse(snapshot.handled)
    }

    @Test
    fun `history is limited to max size`() = runTest {
        val ctx = TvScreenContext.player()

        // Add more than MAX_HISTORY_SIZE events
        repeat(15) { i ->
            val event = createMockKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
            DefaultTvInputDebugSink.onTvInputEvent(
                event = event,
                role = TvKeyRole.DPAD_CENTER,
                action = TvAction.PLAY_PAUSE,
                ctx = ctx,
                handled = true,
            )
        }

        val history = DefaultTvInputDebugSink.history.first()
        // MAX_HISTORY_SIZE is 10
        assertEquals(10, history.size)
    }

    @Test
    fun `clearHistory removes all events`() = runTest {
        val event = createMockKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
        val ctx = TvScreenContext.player()

        DefaultTvInputDebugSink.onTvInputEvent(
            event = event,
            role = TvKeyRole.DPAD_CENTER,
            action = TvAction.PLAY_PAUSE,
            ctx = ctx,
            handled = true,
        )

        // Verify event was captured
        var history = DefaultTvInputDebugSink.history.first()
        assertEquals(1, history.size)

        // Clear and verify
        DefaultTvInputDebugSink.clearHistory()
        history = DefaultTvInputDebugSink.history.first()
        assertTrue(history.isEmpty())
    }

    @Test
    fun `snapshot contains correct action type`() = runTest {
        val downEvent = createMockKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN)
        val ctx = TvScreenContext.player()

        DefaultTvInputDebugSink.onTvInputEvent(
            event = downEvent,
            role = TvKeyRole.DPAD_CENTER,
            action = TvAction.PLAY_PAUSE,
            ctx = ctx,
            handled = true,
        )

        val history = DefaultTvInputDebugSink.history.first()
        assertEquals("DOWN", history[0].actionType)
    }

    @Test
    fun `snapshot contains timestamp`() = runTest {
        val beforeTime = System.currentTimeMillis()
        
        val event = createMockKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
        val ctx = TvScreenContext.player()

        DefaultTvInputDebugSink.onTvInputEvent(
            event = event,
            role = TvKeyRole.DPAD_CENTER,
            action = TvAction.PLAY_PAUSE,
            ctx = ctx,
            handled = true,
        )

        val afterTime = System.currentTimeMillis()
        val history = DefaultTvInputDebugSink.history.first()
        val timestamp = history[0].timestamp

        assertTrue("Timestamp should be within test range", timestamp >= beforeTime && timestamp <= afterTime)
    }

    private fun createMockKeyEvent(
        keyCode: Int,
        action: Int = KeyEvent.ACTION_DOWN,
    ): KeyEvent = Mockito.mock(KeyEvent::class.java).also {
        Mockito.`when`(it.keyCode).thenReturn(keyCode)
        Mockito.`when`(it.action).thenReturn(action)
    }
}
