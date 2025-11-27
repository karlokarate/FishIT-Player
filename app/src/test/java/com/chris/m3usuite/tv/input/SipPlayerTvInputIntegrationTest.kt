package com.chris.m3usuite.tv.input

import android.view.KeyEvent
import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

/**
 * Integration tests for TvInputController with SIP Internal Player.
 *
 * Verifies:
 * - PLAY_PAUSE key on PLAYER screen triggers PLAY_PAUSE action
 * - Action flows through controller to listener
 * - TvScreenContext is correctly built from player state
 * - Kids Mode filtering works with player state
 * - Quick actions visibility toggling
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SipPlayerTvInputIntegrationTest {
    private lateinit var testScope: TestScope
    private lateinit var controller: DefaultTvInputController
    private lateinit var mockActionListener: MockTvActionListener
    private lateinit var host: GlobalTvInputHost

    @Before
    fun setup() {
        testScope = TestScope()
        mockActionListener = MockTvActionListener()
        controller =
            DefaultTvInputController(
                configs = DefaultTvScreenConfigs.all,
            )
        controller.actionListener = mockActionListener
        host =
            GlobalTvInputHost(
                controller = controller,
                configs = DefaultTvScreenConfigs.all,
                scope = testScope,
                debounceMs = 300L,
                enableDebouncing = false, // Disable for faster tests
            )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // PLAY_PAUSE KEY TESTS
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `PLAY_PAUSE key on PLAYER screen triggers PLAY_PAUSE action`() =
        testScope.runTest {
            val ctx = TvScreenContext.player()
            val event = createKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

            host.handleKeyEvent(event, ctx)

            assertEquals(TvAction.PLAY_PAUSE, mockActionListener.lastAction)
        }

    @Test
    fun `DPAD_CENTER on PLAYER screen triggers PLAY_PAUSE action`() =
        testScope.runTest {
            val ctx = TvScreenContext.player()
            val event = createKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)

            host.handleKeyEvent(event, ctx)

            assertEquals(TvAction.PLAY_PAUSE, mockActionListener.lastAction)
        }

    // ════════════════════════════════════════════════════════════════════════════
    // SEEK TESTS
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `FAST_FORWARD on PLAYER screen triggers SEEK_FORWARD_30S`() =
        testScope.runTest {
            val ctx = TvScreenContext.player()
            val event = createKeyEvent(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)

            host.handleKeyEvent(event, ctx)

            assertEquals(TvAction.SEEK_FORWARD_30S, mockActionListener.lastAction)
        }

    @Test
    fun `REWIND on PLAYER screen triggers SEEK_BACKWARD_30S`() =
        testScope.runTest {
            val ctx = TvScreenContext.player()
            val event = createKeyEvent(KeyEvent.KEYCODE_MEDIA_REWIND)

            host.handleKeyEvent(event, ctx)

            assertEquals(TvAction.SEEK_BACKWARD_30S, mockActionListener.lastAction)
        }

    // ════════════════════════════════════════════════════════════════════════════
    // TV SCREEN CONTEXT FROM PLAYER STATE TESTS
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `toTvScreenContext creates correct context for VOD playback`() {
        val playerState =
            InternalPlayerUiState(
                playbackType = PlaybackType.VOD,
                kidActive = false,
            )

        val tvCtx = playerState.toTvScreenContext()

        assertEquals(TvScreenId.PLAYER, tvCtx.screenId)
        assertTrue(tvCtx.isPlayerScreen)
        assertFalse(tvCtx.isLive)
        assertFalse(tvCtx.isKidProfile)
        assertFalse(tvCtx.hasBlockingOverlay)
    }

    @Test
    fun `toTvScreenContext creates correct context for LIVE playback`() {
        val playerState =
            InternalPlayerUiState(
                playbackType = PlaybackType.LIVE,
                kidActive = false,
            )

        val tvCtx = playerState.toTvScreenContext()

        assertEquals(TvScreenId.PLAYER, tvCtx.screenId)
        assertTrue(tvCtx.isPlayerScreen)
        assertTrue(tvCtx.isLive)
        assertFalse(tvCtx.isKidProfile)
    }

    @Test
    fun `toTvScreenContext creates correct context for kid profile`() {
        val playerState =
            InternalPlayerUiState(
                playbackType = PlaybackType.VOD,
                kidActive = true,
                kidProfileId = 123L,
            )

        val tvCtx = playerState.toTvScreenContext()

        assertEquals(TvScreenId.PLAYER, tvCtx.screenId)
        assertTrue(tvCtx.isKidProfile)
    }

    @Test
    fun `toTvScreenContext detects blocking overlay from showCcMenuDialog`() {
        val playerState =
            InternalPlayerUiState(
                showCcMenuDialog = true,
            )

        val tvCtx = playerState.toTvScreenContext()

        assertTrue(tvCtx.hasBlockingOverlay)
    }

    @Test
    fun `toTvScreenContext detects blocking overlay from showSettingsDialog`() {
        val playerState =
            InternalPlayerUiState(
                showSettingsDialog = true,
            )

        val tvCtx = playerState.toTvScreenContext()

        assertTrue(tvCtx.hasBlockingOverlay)
    }

    @Test
    fun `toTvScreenContext detects blocking overlay from kidBlocked`() {
        val playerState =
            InternalPlayerUiState(
                kidBlocked = true,
            )

        val tvCtx = playerState.toTvScreenContext()

        assertTrue(tvCtx.hasBlockingOverlay)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // KIDS MODE FILTERING IN PLAYER CONTEXT
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `FAST_FORWARD is blocked for kid profile on PLAYER`() =
        testScope.runTest {
            val ctx = TvScreenContext.player(isKidProfile = true)
            val event = createKeyEvent(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)

            val handled = host.handleKeyEvent(event, ctx)

            // Should not be handled (blocked)
            assertFalse(handled)
            assertNull(mockActionListener.lastAction)
        }

    @Test
    fun `PLAY_PAUSE is allowed for kid profile on PLAYER`() =
        testScope.runTest {
            val ctx = TvScreenContext.player(isKidProfile = true)
            val event = createKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

            host.handleKeyEvent(event, ctx)

            assertEquals(TvAction.PLAY_PAUSE, mockActionListener.lastAction)
        }

    // ════════════════════════════════════════════════════════════════════════════
    // QUICK ACTIONS VISIBILITY TESTS
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `MENU key opens quick actions on PLAYER`() =
        testScope.runTest {
            val ctx = TvScreenContext.player()
            val event = createKeyEvent(KeyEvent.KEYCODE_MENU)

            host.handleKeyEvent(event, ctx)

            assertTrue(controller.quickActionsVisible.value)
        }

    @Test
    fun `BACK key closes quick actions when visible`() =
        testScope.runTest {
            val ctx = TvScreenContext.player()

            // First open quick actions
            controller.setQuickActionsVisible(true)
            assertTrue(controller.quickActionsVisible.value)

            // Then press BACK
            val event = createKeyEvent(KeyEvent.KEYCODE_BACK)
            host.handleKeyEvent(event, ctx)

            assertFalse(controller.quickActionsVisible.value)
        }

    // ════════════════════════════════════════════════════════════════════════════
    // CHANNEL CONTROL TESTS (LIVE)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `CHANNEL_UP on LIVE player triggers CHANNEL_UP action`() =
        testScope.runTest {
            val ctx = TvScreenContext.player(isLive = true)
            val event = createKeyEvent(KeyEvent.KEYCODE_CHANNEL_UP)

            host.handleKeyEvent(event, ctx)

            assertEquals(TvAction.CHANNEL_UP, mockActionListener.lastAction)
        }

    @Test
    fun `CHANNEL_DOWN on LIVE player triggers CHANNEL_DOWN action`() =
        testScope.runTest {
            val ctx = TvScreenContext.player(isLive = true)
            val event = createKeyEvent(KeyEvent.KEYCODE_CHANNEL_DOWN)

            host.handleKeyEvent(event, ctx)

            assertEquals(TvAction.CHANNEL_DOWN, mockActionListener.lastAction)
        }

    // ════════════════════════════════════════════════════════════════════════════
    // OVERLAY BLOCKING IN PLAYER
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `playback actions are blocked when overlay is open`() =
        testScope.runTest {
            val ctx = TvScreenContext.player(hasBlockingOverlay = true)
            val event = createKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

            val handled = host.handleKeyEvent(event, ctx)

            // Should not be handled (blocked by overlay)
            assertFalse(handled)
        }

    @Test
    fun `BACK is allowed when overlay is open`() =
        testScope.runTest {
            val ctx = TvScreenContext.player(hasBlockingOverlay = true)
            val event = createKeyEvent(KeyEvent.KEYCODE_BACK)

            val handled = host.handleKeyEvent(event, ctx)

            // BACK should still work to close the overlay
            assertTrue(handled)
        }

    // ════════════════════════════════════════════════════════════════════════════
    // FULL PIPELINE END-TO-END TEST
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `end-to-end pipeline from KeyEvent to action callback`() =
        testScope.runTest {
            // Build player state
            val playerState =
                InternalPlayerUiState(
                    playbackType = PlaybackType.VOD,
                    isPlaying = true,
                    kidActive = false,
                )

            // Convert to TvScreenContext
            val tvCtx = playerState.toTvScreenContext()

            // Simulate key event
            val event = createKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

            // Process through pipeline
            host.handleKeyEvent(event, tvCtx)

            // Verify action was received
            assertEquals(TvAction.PLAY_PAUSE, mockActionListener.lastAction)
            assertEquals(1, mockActionListener.actionCount)
        }

    @Test
    fun `end-to-end pipeline with kid profile blocking`() =
        testScope.runTest {
            // Build player state with kid profile
            val playerState =
                InternalPlayerUiState(
                    playbackType = PlaybackType.VOD,
                    isPlaying = true,
                    kidActive = true,
                    kidProfileId = 42L,
                )

            // Convert to TvScreenContext
            val tvCtx = playerState.toTvScreenContext()
            assertTrue(tvCtx.isKidProfile) // Verify conversion

            // Simulate blocked key event (FAST_FORWARD blocked for kids)
            val event = createKeyEvent(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)

            // Process through pipeline
            val handled = host.handleKeyEvent(event, tvCtx)

            // Verify action was blocked
            assertFalse(handled)
            assertNull(mockActionListener.lastAction)
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

    private class MockTvActionListener : TvActionListener {
        var lastAction: TvAction? = null
        var actionCount = 0
        var shouldHandle = true

        override fun onAction(action: TvAction): Boolean {
            lastAction = action
            actionCount++
            return shouldHandle
        }
    }
}
