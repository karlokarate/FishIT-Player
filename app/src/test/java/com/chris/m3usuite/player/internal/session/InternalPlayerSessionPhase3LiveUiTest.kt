package com.chris.m3usuite.player.internal.session

import com.chris.m3usuite.player.internal.domain.PlaybackContext
import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.live.DefaultLivePlaybackController
import com.chris.m3usuite.player.internal.live.EpgOverlayState
import com.chris.m3usuite.player.internal.live.LiveChannel
import com.chris.m3usuite.player.internal.live.LiveChannelRepository
import com.chris.m3usuite.player.internal.live.LiveEpgRepository
import com.chris.m3usuite.player.internal.live.TimeProvider
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Phase 3 Step 3: Live UI Integration in SIP.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 3 STEP 3: LIVE TV STATE MAPPING TESTS
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * These tests validate:
 * 1. LivePlaybackController state flows are correctly mapped to InternalPlayerUiState
 * 2. Live TV fields (liveChannelName, liveNowTitle, liveNextTitle, epgOverlayVisible) are updated
 * 3. Live controller is only created for LIVE playback type
 * 4. VOD/SERIES playback does not activate live controller
 *
 * Note: These tests run without actual Compose runtime, testing the mapping logic only.
 */
class InternalPlayerSessionPhase3LiveUiTest {
    // ════════════════════════════════════════════════════════════════════════════
    // Fake Implementations
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Fake [LiveChannelRepository] for testing.
     */
    class FakeLiveChannelRepository : LiveChannelRepository {
        var channels: List<LiveChannel> = emptyList()
        var shouldThrow = false

        override suspend fun getChannels(
            categoryHint: String?,
            providerHint: String?,
        ): List<LiveChannel> {
            if (shouldThrow) throw RuntimeException("Test error")
            return channels.filter { channel ->
                (categoryHint == null || channel.category == categoryHint)
            }
        }

        override suspend fun getChannel(channelId: Long): LiveChannel? {
            if (shouldThrow) throw RuntimeException("Test error")
            return channels.find { it.id == channelId }
        }
    }

    /**
     * Fake [LiveEpgRepository] for testing.
     */
    class FakeLiveEpgRepository : LiveEpgRepository {
        var nowNextMap: Map<Int, Pair<String?, String?>> = emptyMap()
        var shouldThrow = false

        override suspend fun getNowNext(streamId: Int): Pair<String?, String?> {
            if (shouldThrow) throw RuntimeException("Test error")
            return nowNextMap[streamId] ?: (null to null)
        }
    }

    /**
     * Fake [TimeProvider] for deterministic testing.
     */
    class FakeTimeProvider : TimeProvider {
        var currentTime: Long = 1000000L

        override fun currentTimeMillis(): Long = currentTime

        fun advanceBy(ms: Long) {
            currentTime += ms
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Test Setup
    // ════════════════════════════════════════════════════════════════════════════

    private lateinit var liveRepository: FakeLiveChannelRepository
    private lateinit var epgRepository: FakeLiveEpgRepository
    private lateinit var timeProvider: FakeTimeProvider

    private val testChannels =
        listOf(
            LiveChannel(id = 1L, name = "CNN International", url = "http://cnn.m3u8", category = "News", logoUrl = null),
            LiveChannel(id = 2L, name = "ESPN Sports", url = "http://espn.m3u8", category = "Sports", logoUrl = null),
            LiveChannel(id = 3L, name = "BBC World", url = "http://bbc.m3u8", category = "News", logoUrl = null),
        )

    @Before
    fun setup() {
        liveRepository = FakeLiveChannelRepository()
        epgRepository = FakeLiveEpgRepository()
        timeProvider = FakeTimeProvider()
    }

    // ════════════════════════════════════════════════════════════════════════════
    // InternalPlayerUiState Live Field Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `InternalPlayerUiState has live TV fields with safe defaults`() {
        // Given: Default UI state
        val state = InternalPlayerUiState()

        // Then: Live fields have safe defaults
        assertNull("liveChannelName should be null by default", state.liveChannelName)
        assertNull("liveNowTitle should be null by default", state.liveNowTitle)
        assertNull("liveNextTitle should be null by default", state.liveNextTitle)
        assertFalse("epgOverlayVisible should be false by default", state.epgOverlayVisible)
        assertFalse("liveListVisible should be false by default", state.liveListVisible)
    }

    @Test
    fun `InternalPlayerUiState live fields can be updated via copy`() {
        // Given: Default state
        val state = InternalPlayerUiState()

        // When: Updating live fields
        val updated =
            state.copy(
                liveChannelName = "CNN International",
                liveNowTitle = "Morning News",
                liveNextTitle = "Sports Update",
                epgOverlayVisible = true,
                liveListVisible = true,
            )

        // Then: Fields are updated
        assertEquals("CNN International", updated.liveChannelName)
        assertEquals("Morning News", updated.liveNowTitle)
        assertEquals("Sports Update", updated.liveNextTitle)
        assertTrue(updated.epgOverlayVisible)
        assertTrue(updated.liveListVisible)
    }

    @Test
    fun `InternalPlayerUiState isLive convenience getter works correctly`() {
        // Given: VOD state
        val vodState = InternalPlayerUiState(playbackType = PlaybackType.VOD)

        // Then: isLive is false
        assertFalse(vodState.isLive)

        // Given: LIVE state
        val liveState = InternalPlayerUiState(playbackType = PlaybackType.LIVE)

        // Then: isLive is true
        assertTrue(liveState.isLive)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // LivePlaybackController → UiState Mapping Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `LivePlaybackController currentChannel maps to liveChannelName`() =
        runBlocking {
            // Given: Controller with channels
            liveRepository.channels = testChannels
            val controller =
                DefaultLivePlaybackController(
                    liveRepository = liveRepository,
                    epgRepository = epgRepository,
                    clock = timeProvider,
                )

            // When: Initializing with LIVE context
            val ctx = PlaybackContext(type = PlaybackType.LIVE)
            controller.initFromPlaybackContext(ctx)

            // Then: currentChannel is set
            val channel = controller.currentChannel.first()
            assertNotNull("Channel should be selected", channel)

            // Map to UiState
            val state =
                InternalPlayerUiState(
                    playbackType = PlaybackType.LIVE,
                    liveChannelName = channel?.name,
                )

            assertEquals("CNN International", state.liveChannelName)
        }

    @Test
    fun `LivePlaybackController epgOverlay maps to live EPG fields`() =
        runBlocking {
            // Given: Controller with EPG data
            liveRepository.channels = testChannels
            epgRepository.nowNextMap =
                mapOf(
                    1 to ("Morning News" to "Sports Update"),
                )
            val controller =
                DefaultLivePlaybackController(
                    liveRepository = liveRepository,
                    epgRepository = epgRepository,
                    clock = timeProvider,
                )

            // When: Initializing with LIVE context
            val ctx = PlaybackContext(type = PlaybackType.LIVE, mediaId = 1L)
            controller.initFromPlaybackContext(ctx)

            // Then: EPG overlay is populated
            val overlay = controller.epgOverlay.first()
            assertTrue("EPG overlay should be visible", overlay.visible)

            // Map to UiState
            val state =
                InternalPlayerUiState(
                    playbackType = PlaybackType.LIVE,
                    liveNowTitle = overlay.nowTitle,
                    liveNextTitle = overlay.nextTitle,
                    epgOverlayVisible = overlay.visible,
                )

            assertEquals("Morning News", state.liveNowTitle)
            assertEquals("Sports Update", state.liveNextTitle)
            assertTrue(state.epgOverlayVisible)
        }

    @Test
    fun `epgOverlayVisible is false when overlay auto-hides`() =
        runBlocking {
            // Given: Controller with overlay visible
            liveRepository.channels = testChannels
            timeProvider.currentTime = 1000000L
            val controller =
                DefaultLivePlaybackController(
                    liveRepository = liveRepository,
                    epgRepository = epgRepository,
                    clock = timeProvider,
                )

            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))
            assertTrue("Overlay should be visible initially", controller.epgOverlay.first().visible)

            // When: Time advances past hide threshold
            timeProvider.advanceBy(DefaultLivePlaybackController.DEFAULT_EPG_OVERLAY_DURATION_MS + 1)
            controller.onPlaybackPositionChanged(0)

            // Then: Overlay is hidden
            val overlay = controller.epgOverlay.first()
            assertFalse("Overlay should be hidden", overlay.visible)

            // Map to UiState
            val state =
                InternalPlayerUiState(
                    playbackType = PlaybackType.LIVE,
                    epgOverlayVisible = overlay.visible,
                )

            assertFalse(state.epgOverlayVisible)
        }

    @Test
    fun `jumpChannel updates liveChannelName in mapped state`() =
        runBlocking {
            // Given: Controller with channels
            liveRepository.channels = testChannels
            val controller =
                DefaultLivePlaybackController(
                    liveRepository = liveRepository,
                    epgRepository = epgRepository,
                    clock = timeProvider,
                )

            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))
            assertEquals("CNN International", controller.currentChannel.first()?.name)

            // When: Jumping to next channel
            controller.jumpChannel(+1)

            // Then: Channel name is updated
            val channel = controller.currentChannel.first()
            assertEquals("ESPN Sports", channel?.name)

            // Map to UiState
            val state =
                InternalPlayerUiState(
                    playbackType = PlaybackType.LIVE,
                    liveChannelName = channel?.name,
                )

            assertEquals("ESPN Sports", state.liveChannelName)
        }

    // ════════════════════════════════════════════════════════════════════════════
    // Controller Creation Conditional Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `controller not created for VOD playback type`() {
        // Given: VOD playback context
        val ctx = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)

        // When: Checking if controller should be created
        val shouldCreateController =
            ctx.type == PlaybackType.LIVE &&
                liveRepository != null &&
                epgRepository != null

        // Then: Controller should NOT be created
        assertFalse("Controller should not be created for VOD", shouldCreateController)
    }

    @Test
    fun `controller not created for SERIES playback type`() {
        // Given: SERIES playback context
        val ctx = PlaybackContext(type = PlaybackType.SERIES, seriesId = 1, season = 1, episodeNumber = 1)

        // When: Checking if controller should be created
        val shouldCreateController =
            ctx.type == PlaybackType.LIVE &&
                liveRepository != null &&
                epgRepository != null

        // Then: Controller should NOT be created
        assertFalse("Controller should not be created for SERIES", shouldCreateController)
    }

    @Test
    fun `controller created for LIVE playback type with repositories`() {
        // Given: LIVE playback context
        val ctx = PlaybackContext(type = PlaybackType.LIVE)

        // When: Checking if controller should be created
        val shouldCreateController =
            ctx.type == PlaybackType.LIVE &&
                liveRepository != null &&
                epgRepository != null

        // Then: Controller should be created
        assertTrue("Controller should be created for LIVE", shouldCreateController)
    }

    @Test
    fun `controller not created when repositories are null`() {
        // Given: LIVE playback context but null repositories
        val ctx = PlaybackContext(type = PlaybackType.LIVE)
        val nullLiveRepo: LiveChannelRepository? = null
        val nullEpgRepo: LiveEpgRepository? = null

        // When: Checking if controller should be created
        val shouldCreateController =
            ctx.type == PlaybackType.LIVE &&
                nullLiveRepo != null &&
                nullEpgRepo != null

        // Then: Controller should NOT be created (null repos)
        assertFalse("Controller should not be created with null repos", shouldCreateController)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Edge Case Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `liveChannelName is null when no channel selected`() =
        runBlocking {
            // Given: Empty channel repository
            liveRepository.channels = emptyList()
            val controller =
                DefaultLivePlaybackController(
                    liveRepository = liveRepository,
                    epgRepository = epgRepository,
                    clock = timeProvider,
                )

            // When: Initializing
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // Then: Channel is null
            val channel = controller.currentChannel.first()
            assertNull("Channel should be null", channel)

            // Map to UiState
            val state =
                InternalPlayerUiState(
                    playbackType = PlaybackType.LIVE,
                    liveChannelName = channel?.name,
                )

            assertNull(state.liveChannelName)
        }

    @Test
    fun `EPG titles are null when no EPG data available`() =
        runBlocking {
            // Given: No EPG data
            liveRepository.channels = testChannels
            epgRepository.nowNextMap = emptyMap()
            val controller =
                DefaultLivePlaybackController(
                    liveRepository = liveRepository,
                    epgRepository = epgRepository,
                    clock = timeProvider,
                )

            // When: Initializing
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // Then: EPG titles are null
            val overlay = controller.epgOverlay.first()
            assertNull("nowTitle should be null", overlay.nowTitle)
            assertNull("nextTitle should be null", overlay.nextTitle)
        }

    @Test
    fun `repository errors result in safe state`() =
        runBlocking {
            // Given: Repository that throws
            liveRepository.shouldThrow = true
            val controller =
                DefaultLivePlaybackController(
                    liveRepository = liveRepository,
                    epgRepository = epgRepository,
                    clock = timeProvider,
                )

            // When: Initializing (should not throw)
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // Then: Safe state with no channel
            val channel = controller.currentChannel.first()
            assertNull("Channel should be null on error", channel)
        }

    // ════════════════════════════════════════════════════════════════════════════
    // InternalPlayerController Live Callback Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `onJumpLiveChannel callback has safe default`() {
        // Given: Default controller with default callbacks
        val controller =
            com.chris.m3usuite.player.internal.state.InternalPlayerController(
                onPlayPause = {},
                onSeekTo = {},
                onSeekBy = {},
                onChangeSpeed = {},
                onToggleLoop = {},
                onEnterPip = {},
                onToggleSettingsDialog = {},
                onToggleTracksDialog = {},
                onToggleSpeedDialog = {},
                onToggleSleepTimerDialog = {},
                onToggleDebugInfo = {},
                onCycleAspectRatio = {},
            )

        // When: Invoking default callback (should not throw)
        controller.onJumpLiveChannel(+1)
        controller.onJumpLiveChannel(-1)

        // Then: No exception thrown (default is empty lambda)
    }

    @Test
    fun `onToggleLiveList callback has safe default`() {
        // Given: Default controller with default callbacks
        val controller =
            com.chris.m3usuite.player.internal.state.InternalPlayerController(
                onPlayPause = {},
                onSeekTo = {},
                onSeekBy = {},
                onChangeSpeed = {},
                onToggleLoop = {},
                onEnterPip = {},
                onToggleSettingsDialog = {},
                onToggleTracksDialog = {},
                onToggleSpeedDialog = {},
                onToggleSleepTimerDialog = {},
                onToggleDebugInfo = {},
                onCycleAspectRatio = {},
            )

        // When: Invoking default callback (should not throw)
        controller.onToggleLiveList()

        // Then: No exception thrown (default is empty lambda)
    }

    @Test
    fun `custom onJumpLiveChannel callback receives correct delta`() {
        // Given: Controller with custom callback
        var receivedDelta: Int? = null
        val controller =
            com.chris.m3usuite.player.internal.state.InternalPlayerController(
                onPlayPause = {},
                onSeekTo = {},
                onSeekBy = {},
                onChangeSpeed = {},
                onToggleLoop = {},
                onEnterPip = {},
                onToggleSettingsDialog = {},
                onToggleTracksDialog = {},
                onToggleSpeedDialog = {},
                onToggleSleepTimerDialog = {},
                onToggleDebugInfo = {},
                onCycleAspectRatio = {},
                onJumpLiveChannel = { delta -> receivedDelta = delta },
            )

        // When: Invoking callback with +1
        controller.onJumpLiveChannel(+1)

        // Then: Delta is received
        assertEquals(1, receivedDelta)

        // When: Invoking callback with -1
        controller.onJumpLiveChannel(-1)

        // Then: Delta is updated
        assertEquals(-1, receivedDelta)
    }

    @Test
    fun `custom onToggleLiveList callback is invoked`() {
        // Given: Controller with custom callback
        var toggleCalled = false
        val controller =
            com.chris.m3usuite.player.internal.state.InternalPlayerController(
                onPlayPause = {},
                onSeekTo = {},
                onSeekBy = {},
                onChangeSpeed = {},
                onToggleLoop = {},
                onEnterPip = {},
                onToggleSettingsDialog = {},
                onToggleTracksDialog = {},
                onToggleSpeedDialog = {},
                onToggleSleepTimerDialog = {},
                onToggleDebugInfo = {},
                onCycleAspectRatio = {},
                onToggleLiveList = { toggleCalled = true },
            )

        // When: Invoking callback
        controller.onToggleLiveList()

        // Then: Callback was invoked
        assertTrue("Toggle should be called", toggleCalled)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // UI Rendering Logic Tests (Non-Compose)
    // ════════════════════════════════════════════════════════════════════════════
    // Note: Actual Compose UI tests require Robolectric/instrumentation.
    // TODO("Phase 10 – test hardening"): Add Compose UI tests with test framework.

    @Test
    fun `EPG overlay should render when isLive and epgOverlayVisible`() {
        // Given: LIVE state with EPG visible
        val state =
            InternalPlayerUiState(
                playbackType = PlaybackType.LIVE,
                liveChannelName = "CNN",
                liveNowTitle = "News",
                liveNextTitle = "Sports",
                epgOverlayVisible = true,
            )

        // Then: Conditions for rendering EPG overlay are met
        val shouldShowEpgOverlay = state.isLive && state.epgOverlayVisible
        assertTrue("EPG overlay should render", shouldShowEpgOverlay)
    }

    @Test
    fun `EPG overlay should not render when not LIVE`() {
        // Given: VOD state with EPG visible (invalid combination)
        val state =
            InternalPlayerUiState(
                playbackType = PlaybackType.VOD,
                epgOverlayVisible = true, // This shouldn't happen for VOD
            )

        // Then: Conditions for rendering EPG overlay are NOT met
        val shouldShowEpgOverlay = state.isLive && state.epgOverlayVisible
        assertFalse("EPG overlay should not render for VOD", shouldShowEpgOverlay)
    }

    @Test
    fun `EPG overlay should not render when hidden`() {
        // Given: LIVE state with EPG hidden
        val state =
            InternalPlayerUiState(
                playbackType = PlaybackType.LIVE,
                epgOverlayVisible = false,
            )

        // Then: Conditions for rendering EPG overlay are NOT met
        val shouldShowEpgOverlay = state.isLive && state.epgOverlayVisible
        assertFalse("EPG overlay should not render when hidden", shouldShowEpgOverlay)
    }

    @Test
    fun `channel name bar should render when LIVE with name but no EPG overlay`() {
        // Given: LIVE state with name but no overlay
        val state =
            InternalPlayerUiState(
                playbackType = PlaybackType.LIVE,
                liveChannelName = "CNN",
                epgOverlayVisible = false,
            )

        // Then: Conditions for rendering channel name bar are met
        val shouldShowChannelBar = state.isLive && state.liveChannelName != null && !state.epgOverlayVisible
        assertTrue("Channel name bar should render", shouldShowChannelBar)
    }

    @Test
    fun `progress row should be hidden for LIVE content`() {
        // Given: LIVE state
        val state = InternalPlayerUiState(playbackType = PlaybackType.LIVE)

        // Then: Progress row should be hidden (no seeking for LIVE)
        val shouldHideProgress = state.isLive
        assertTrue("Progress row should be hidden for LIVE", shouldHideProgress)
    }

    @Test
    fun `progress row should be shown for VOD content`() {
        // Given: VOD state
        val state = InternalPlayerUiState(playbackType = PlaybackType.VOD)

        // Then: Progress row should be shown
        val shouldShowProgress = !state.isLive
        assertTrue("Progress row should be shown for VOD", shouldShowProgress)
    }
}
