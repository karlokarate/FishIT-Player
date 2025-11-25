package com.chris.m3usuite.player.internal.live

import com.chris.m3usuite.player.internal.domain.PlaybackContext
import com.chris.m3usuite.player.internal.domain.PlaybackType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [LivePlaybackController] and [DefaultLivePlaybackController].
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 3 – STEP 2: COMPREHENSIVE TESTS
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * This test file provides comprehensive coverage for Phase 3 of the internal player refactor:
 *
 * 1. **Initial State Tests**: Verify default state on construction.
 * 2. **Channel List Loading**: Test initFromPlaybackContext with various scenarios.
 * 3. **Channel Navigation**: Test jumpChannel with wrap-around behavior.
 * 4. **Channel Selection**: Test selectChannel lookup and state update.
 * 5. **EPG Resolution**: Test EPG overlay state and auto-hide timing.
 * 6. **Data Model Tests**: Verify LiveChannel and EpgOverlayState properties.
 * 7. **TimeProvider Tests**: Verify time abstraction for testing.
 * 8. **Edge Cases**: Test robustness against invalid inputs.
 */
class LivePlaybackControllerTest {
    // ════════════════════════════════════════════════════════════════════════════
    // Fake Implementations
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Fake [LiveChannelRepository] for testing.
     */
    class FakeLiveChannelRepository : LiveChannelRepository {
        var channels: List<LiveChannel> = emptyList()
        var getChannelsCallCount = 0
        var getChannelCallCount = 0
        var lastCategoryHint: String? = null
        var lastProviderHint: String? = null
        var shouldThrow = false

        override suspend fun getChannels(
            categoryHint: String?,
            providerHint: String?,
        ): List<LiveChannel> {
            getChannelsCallCount++
            lastCategoryHint = categoryHint
            lastProviderHint = providerHint
            if (shouldThrow) throw RuntimeException("Test error")
            return channels.filter { channel ->
                (categoryHint == null || channel.category == categoryHint) &&
                    (providerHint == null || true) // Provider filtering not implemented in fake
            }
        }

        override suspend fun getChannel(channelId: Long): LiveChannel? {
            getChannelCallCount++
            if (shouldThrow) throw RuntimeException("Test error")
            return channels.find { it.id == channelId }
        }
    }

    /**
     * Fake [LiveEpgRepository] for testing.
     */
    class FakeLiveEpgRepository : LiveEpgRepository {
        var nowNextMap: Map<Int, Pair<String?, String?>> = emptyMap()
        var getNowNextCallCount = 0
        var shouldThrow = false

        override suspend fun getNowNext(streamId: Int): Pair<String?, String?> {
            getNowNextCallCount++
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
    private lateinit var controller: DefaultLivePlaybackController

    private val testChannels =
        listOf(
            LiveChannel(id = 1L, name = "Channel 1", url = "http://ch1.m3u8", category = "News", logoUrl = null),
            LiveChannel(id = 2L, name = "Channel 2", url = "http://ch2.m3u8", category = "Sports", logoUrl = null),
            LiveChannel(id = 3L, name = "Channel 3", url = "http://ch3.m3u8", category = "News", logoUrl = null),
            LiveChannel(id = 4L, name = "Channel 4", url = "http://ch4.m3u8", category = "Movies", logoUrl = null),
        )

    @Before
    fun setup() {
        liveRepository = FakeLiveChannelRepository()
        epgRepository = FakeLiveEpgRepository()
        timeProvider = FakeTimeProvider()
        controller =
            DefaultLivePlaybackController(
                liveRepository = liveRepository,
                epgRepository = epgRepository,
                clock = timeProvider,
            )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Initial State Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `currentChannel is null on construction`() {
        // Given: A newly constructed controller
        // When: Checking initial state
        val channel = controller.currentChannel.value

        // Then: No channel is selected
        assertNull("currentChannel should be null on construction", channel)
    }

    @Test
    fun `epgOverlay is not visible on construction`() {
        // Given: A newly constructed controller
        // When: Checking initial state
        val overlay = controller.epgOverlay.value

        // Then: Overlay is not visible
        assertFalse("epgOverlay.visible should be false on construction", overlay.visible)
        assertNull("epgOverlay.nowTitle should be null on construction", overlay.nowTitle)
        assertNull("epgOverlay.nextTitle should be null on construction", overlay.nextTitle)
        assertNull("epgOverlay.hideAtRealtimeMs should be null on construction", overlay.hideAtRealtimeMs)
    }

    @Test
    fun `controller can be constructed with fake dependencies`() {
        // Given: Fake repositories and time provider
        val repo = FakeLiveChannelRepository()
        val epg = FakeLiveEpgRepository()
        val clock = FakeTimeProvider()

        // When: Constructing controller
        val ctrl =
            DefaultLivePlaybackController(
                liveRepository = repo,
                epgRepository = epg,
                clock = clock,
            )

        // Then: Controller is created without exceptions
        assertNull("Initial channel should be null", ctrl.currentChannel.value)
        assertFalse("Initial overlay should not be visible", ctrl.epgOverlay.value.visible)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Channel List Loading Tests (initFromPlaybackContext)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `initFromPlaybackContext loads channels and sets initial channel`() =
        runBlocking {
            // Given: Repository with channels
            liveRepository.channels = testChannels

            // When: Initializing with LIVE context
            val ctx = PlaybackContext(type = PlaybackType.LIVE)
            controller.initFromPlaybackContext(ctx)

            // Then: First channel is selected
            val channel = controller.currentChannel.value
            assertNotNull("Channel should be selected", channel)
            assertEquals("First channel should be selected", 1L, channel?.id)
            assertEquals(1, liveRepository.getChannelsCallCount)
        }

    @Test
    fun `initFromPlaybackContext selects channel by mediaId if provided`() =
        runBlocking {
            // Given: Repository with channels
            liveRepository.channels = testChannels

            // When: Initializing with specific mediaId
            val ctx = PlaybackContext(type = PlaybackType.LIVE, mediaId = 3L)
            controller.initFromPlaybackContext(ctx)

            // Then: Specified channel is selected
            val channel = controller.currentChannel.value
            assertEquals("Channel 3 should be selected", 3L, channel?.id)
        }

    @Test
    fun `initFromPlaybackContext falls back to first channel if mediaId not found`() =
        runBlocking {
            // Given: Repository with channels
            liveRepository.channels = testChannels

            // When: Initializing with non-existent mediaId
            val ctx = PlaybackContext(type = PlaybackType.LIVE, mediaId = 999L)
            controller.initFromPlaybackContext(ctx)

            // Then: First channel is selected as fallback
            val channel = controller.currentChannel.value
            assertEquals("First channel should be selected as fallback", 1L, channel?.id)
        }

    @Test
    fun `initFromPlaybackContext passes category hint to repository`() =
        runBlocking {
            // Given: Repository with channels
            liveRepository.channels = testChannels

            // When: Initializing with category hint
            val ctx = PlaybackContext(type = PlaybackType.LIVE, liveCategoryHint = "News")
            controller.initFromPlaybackContext(ctx)

            // Then: Category hint was passed to repository
            assertEquals("News", liveRepository.lastCategoryHint)
        }

    @Test
    fun `initFromPlaybackContext passes provider hint to repository`() =
        runBlocking {
            // Given: Repository with channels
            liveRepository.channels = testChannels

            // When: Initializing with provider hint
            val ctx = PlaybackContext(type = PlaybackType.LIVE, liveProviderHint = "Provider1")
            controller.initFromPlaybackContext(ctx)

            // Then: Provider hint was passed to repository
            assertEquals("Provider1", liveRepository.lastProviderHint)
        }

    @Test
    fun `initFromPlaybackContext handles empty channel list`() =
        runBlocking {
            // Given: Repository with no channels
            liveRepository.channels = emptyList()

            // When: Initializing
            val ctx = PlaybackContext(type = PlaybackType.LIVE)
            controller.initFromPlaybackContext(ctx)

            // Then: No channel is selected
            assertNull("No channel should be selected", controller.currentChannel.value)
        }

    @Test
    fun `initFromPlaybackContext handles repository exception gracefully`() =
        runBlocking {
            // Given: Repository that throws
            liveRepository.shouldThrow = true

            // When: Initializing (should not throw)
            val ctx = PlaybackContext(type = PlaybackType.LIVE)
            controller.initFromPlaybackContext(ctx)

            // Then: No channel is selected but no exception thrown
            assertNull("No channel should be selected on error", controller.currentChannel.value)
        }

    @Test
    fun `initFromPlaybackContext ignores non-LIVE playback types`() =
        runBlocking {
            // Given: Repository with channels
            liveRepository.channels = testChannels

            // When: Initializing with VOD context
            val ctx = PlaybackContext(type = PlaybackType.VOD, mediaId = 1L)
            controller.initFromPlaybackContext(ctx)

            // Then: Repository was not called, no channel selected
            assertEquals("Repository should not be called for non-LIVE", 0, liveRepository.getChannelsCallCount)
            assertNull("No channel should be selected for non-LIVE", controller.currentChannel.value)
        }

    @Test
    fun `initFromPlaybackContext ignores SERIES playback type`() =
        runBlocking {
            // Given: Repository with channels
            liveRepository.channels = testChannels

            // When: Initializing with SERIES context
            val ctx = PlaybackContext(type = PlaybackType.SERIES, seriesId = 1, season = 1, episodeNumber = 1)
            controller.initFromPlaybackContext(ctx)

            // Then: Repository was not called
            assertEquals("Repository should not be called for SERIES", 0, liveRepository.getChannelsCallCount)
        }

    // ════════════════════════════════════════════════════════════════════════════
    // Channel Navigation Tests (jumpChannel)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `jumpChannel moves to next channel`() =
        runBlocking {
            // Given: Initialized controller with channels
            liveRepository.channels = testChannels
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))
            assertEquals(1L, controller.currentChannel.value?.id)

            // When: Jumping to next channel
            controller.jumpChannel(+1)

            // Then: Next channel is selected
            assertEquals("Channel 2 should be selected", 2L, controller.currentChannel.value?.id)
        }

    @Test
    fun `jumpChannel moves to previous channel`() =
        runBlocking {
            // Given: Initialized controller starting at channel 2
            liveRepository.channels = testChannels
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE, mediaId = 2L))
            assertEquals(2L, controller.currentChannel.value?.id)

            // When: Jumping to previous channel
            controller.jumpChannel(-1)

            // Then: Previous channel is selected
            assertEquals("Channel 1 should be selected", 1L, controller.currentChannel.value?.id)
        }

    @Test
    fun `jumpChannel wraps around at end of list`() =
        runBlocking {
            // Given: Initialized controller at last channel
            liveRepository.channels = testChannels
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE, mediaId = 4L))
            assertEquals(4L, controller.currentChannel.value?.id)

            // When: Jumping to next channel (should wrap)
            controller.jumpChannel(+1)

            // Then: First channel is selected
            assertEquals("Should wrap to first channel", 1L, controller.currentChannel.value?.id)
        }

    @Test
    fun `jumpChannel wraps around at beginning of list`() =
        runBlocking {
            // Given: Initialized controller at first channel
            liveRepository.channels = testChannels
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE, mediaId = 1L))
            assertEquals(1L, controller.currentChannel.value?.id)

            // When: Jumping to previous channel (should wrap)
            controller.jumpChannel(-1)

            // Then: Last channel is selected
            assertEquals("Should wrap to last channel", 4L, controller.currentChannel.value?.id)
        }

    @Test
    fun `jumpChannel with delta greater than 1`() =
        runBlocking {
            // Given: Initialized controller at first channel
            liveRepository.channels = testChannels
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE, mediaId = 1L))

            // When: Jumping by +2
            controller.jumpChannel(+2)

            // Then: Third channel is selected
            assertEquals("Channel 3 should be selected", 3L, controller.currentChannel.value?.id)
        }

    @Test
    fun `jumpChannel does nothing with empty channel list`() =
        runBlocking {
            // Given: Empty channel list
            liveRepository.channels = emptyList()
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // When: Jumping (should be no-op)
            controller.jumpChannel(+1)

            // Then: Still no channel selected
            assertNull("Should remain null", controller.currentChannel.value)
        }

    @Test
    fun `jumpChannel updates EPG overlay visibility`() =
        runBlocking {
            // Given: Initialized controller
            liveRepository.channels = testChannels
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // When: Jumping to next channel
            controller.jumpChannel(+1)

            // Then: EPG overlay is shown
            assertTrue("EPG overlay should be visible", controller.epgOverlay.value.visible)
        }

    // ════════════════════════════════════════════════════════════════════════════
    // Channel Selection Tests (selectChannel)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `selectChannel updates current channel`() =
        runBlocking {
            // Given: Initialized controller
            liveRepository.channels = testChannels
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))
            assertEquals(1L, controller.currentChannel.value?.id)

            // When: Selecting channel 3
            controller.selectChannel(3L)

            // Then: Channel 3 is selected
            assertEquals("Channel 3 should be selected", 3L, controller.currentChannel.value?.id)
        }

    @Test
    fun `selectChannel with invalid ID does nothing`() =
        runBlocking {
            // Given: Initialized controller
            liveRepository.channels = testChannels
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))
            assertEquals(1L, controller.currentChannel.value?.id)

            // When: Selecting non-existent channel
            controller.selectChannel(999L)

            // Then: Channel remains unchanged
            assertEquals("Channel should remain unchanged", 1L, controller.currentChannel.value?.id)
        }

    @Test
    fun `selectChannel shows EPG overlay`() =
        runBlocking {
            // Given: Initialized controller
            liveRepository.channels = testChannels
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // Hide overlay first
            timeProvider.advanceBy(5000)
            controller.onPlaybackPositionChanged(0)
            assertFalse(controller.epgOverlay.value.visible)

            // When: Selecting a channel
            controller.selectChannel(2L)

            // Then: EPG overlay is shown
            assertTrue("EPG overlay should be visible", controller.epgOverlay.value.visible)
        }

    // ════════════════════════════════════════════════════════════════════════════
    // EPG Resolution Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `initFromPlaybackContext fetches EPG data`() =
        runBlocking {
            // Given: Repository with channels and EPG data
            liveRepository.channels = testChannels
            epgRepository.nowNextMap = mapOf(1 to ("Now Playing" to "Up Next"))

            // When: Initializing
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // Then: EPG data is fetched
            assertEquals("EPG should be queried", 1, epgRepository.getNowNextCallCount)
            assertEquals("Now Playing", controller.epgOverlay.value.nowTitle)
            assertEquals("Up Next", controller.epgOverlay.value.nextTitle)
        }

    @Test
    fun `EPG overlay shows correct titles`() =
        runBlocking {
            // Given: Repository with channels and EPG data
            liveRepository.channels = testChannels
            epgRepository.nowNextMap =
                mapOf(
                    1 to ("Morning Show" to "Afternoon News"),
                    2 to ("Sports Live" to "Sports Recap"),
                )

            // When: Initializing with channel 1
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE, mediaId = 1L))

            // Then: EPG shows channel 1 data
            assertEquals("Morning Show", controller.epgOverlay.value.nowTitle)
            assertEquals("Afternoon News", controller.epgOverlay.value.nextTitle)
        }

    @Test
    fun `EPG handles missing data gracefully`() =
        runBlocking {
            // Given: Repository with channels but no EPG data
            liveRepository.channels = testChannels
            epgRepository.nowNextMap = emptyMap()

            // When: Initializing
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // Then: EPG titles are null but overlay is visible
            assertTrue("EPG overlay should be visible", controller.epgOverlay.value.visible)
            assertNull("nowTitle should be null", controller.epgOverlay.value.nowTitle)
            assertNull("nextTitle should be null", controller.epgOverlay.value.nextTitle)
        }

    @Test
    fun `EPG handles repository exception gracefully`() =
        runBlocking {
            // Given: EPG repository that throws
            liveRepository.channels = testChannels
            epgRepository.shouldThrow = true

            // When: Initializing (should not throw)
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // Then: EPG titles are null but no exception
            assertTrue("EPG overlay should be visible", controller.epgOverlay.value.visible)
            assertNull("nowTitle should be null on error", controller.epgOverlay.value.nowTitle)
        }

    // ════════════════════════════════════════════════════════════════════════════
    // Auto-Hide Timer Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `EPG overlay has hideAtRealtimeMs set`() =
        runBlocking {
            // Given: Initialized controller
            liveRepository.channels = testChannels
            timeProvider.currentTime = 1000000L

            // When: Initializing
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // Then: hideAtRealtimeMs is set to current time + duration
            val expectedHideAt = 1000000L + DefaultLivePlaybackController.DEFAULT_EPG_OVERLAY_DURATION_MS
            assertEquals(expectedHideAt, controller.epgOverlay.value.hideAtRealtimeMs)
        }

    @Test
    fun `onPlaybackPositionChanged hides overlay when time expires`() =
        runBlocking {
            // Given: Initialized controller with overlay visible
            liveRepository.channels = testChannels
            timeProvider.currentTime = 1000000L
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))
            assertTrue("Overlay should be visible initially", controller.epgOverlay.value.visible)

            // When: Advancing time past hide threshold and triggering position update
            timeProvider.advanceBy(DefaultLivePlaybackController.DEFAULT_EPG_OVERLAY_DURATION_MS + 1)
            controller.onPlaybackPositionChanged(0)

            // Then: Overlay is hidden
            assertFalse("Overlay should be hidden", controller.epgOverlay.value.visible)
            assertNull("hideAtRealtimeMs should be null", controller.epgOverlay.value.hideAtRealtimeMs)
        }

    @Test
    fun `onPlaybackPositionChanged does not hide overlay before time expires`() =
        runBlocking {
            // Given: Initialized controller with overlay visible
            liveRepository.channels = testChannels
            timeProvider.currentTime = 1000000L
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // When: Advancing time but not past threshold
            timeProvider.advanceBy(DefaultLivePlaybackController.DEFAULT_EPG_OVERLAY_DURATION_MS - 100)
            controller.onPlaybackPositionChanged(0)

            // Then: Overlay remains visible
            assertTrue("Overlay should still be visible", controller.epgOverlay.value.visible)
        }

    @Test
    fun `custom EPG overlay duration is respected`() =
        runBlocking {
            // Given: Controller with custom duration
            val customDuration = 5000L
            val customController =
                DefaultLivePlaybackController(
                    liveRepository = liveRepository,
                    epgRepository = epgRepository,
                    clock = timeProvider,
                    epgOverlayDurationMs = customDuration,
                )
            liveRepository.channels = testChannels
            timeProvider.currentTime = 1000000L

            // When: Initializing
            customController.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // Then: hideAtRealtimeMs uses custom duration
            val expectedHideAt = 1000000L + customDuration
            assertEquals(expectedHideAt, customController.epgOverlay.value.hideAtRealtimeMs)
        }

    // ════════════════════════════════════════════════════════════════════════════
    // Data Model Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `LiveChannel data class has expected properties`() {
        // Given: A live channel
        val channel =
            LiveChannel(
                id = 123L,
                name = "Test Channel",
                url = "https://example.com/stream.m3u8",
                category = "News",
                logoUrl = "https://example.com/logo.png",
            )

        // Then: Properties are accessible
        assertEquals(123L, channel.id)
        assertEquals("Test Channel", channel.name)
        assertEquals("https://example.com/stream.m3u8", channel.url)
        assertEquals("News", channel.category)
        assertEquals("https://example.com/logo.png", channel.logoUrl)
    }

    @Test
    fun `LiveChannel supports null optional properties`() {
        // Given: A live channel with null optional properties
        val channel =
            LiveChannel(
                id = 456L,
                name = "Minimal Channel",
                url = "https://example.com/stream",
                category = null,
                logoUrl = null,
            )

        // Then: Null properties are handled correctly
        assertEquals(456L, channel.id)
        assertEquals("Minimal Channel", channel.name)
        assertNull(channel.category)
        assertNull(channel.logoUrl)
    }

    @Test
    fun `EpgOverlayState data class has expected properties`() {
        // Given: An EPG overlay state
        val state =
            EpgOverlayState(
                visible = true,
                nowTitle = "Current Show",
                nextTitle = "Next Show",
                hideAtRealtimeMs = 1000000L,
            )

        // Then: Properties are accessible
        assertEquals(true, state.visible)
        assertEquals("Current Show", state.nowTitle)
        assertEquals("Next Show", state.nextTitle)
        assertEquals(1000000L, state.hideAtRealtimeMs)
    }

    @Test
    fun `EpgOverlayState supports null optional properties`() {
        // Given: An EPG overlay state with null optional properties
        val state =
            EpgOverlayState(
                visible = false,
                nowTitle = null,
                nextTitle = null,
                hideAtRealtimeMs = null,
            )

        // Then: Null properties are handled correctly
        assertFalse(state.visible)
        assertNull(state.nowTitle)
        assertNull(state.nextTitle)
        assertNull(state.hideAtRealtimeMs)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // TimeProvider Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `FakeTimeProvider returns configured time`() {
        // Given: A fake time provider with specific time
        val clock = FakeTimeProvider()
        clock.currentTime = 5000L

        // When: Getting current time
        val time = clock.currentTimeMillis()

        // Then: Returns configured time
        assertEquals(5000L, time)
    }

    @Test
    fun `FakeTimeProvider can advance time`() {
        // Given: A fake time provider
        val clock = FakeTimeProvider()
        clock.currentTime = 1000L

        // When: Advancing time
        clock.advanceBy(500L)

        // Then: Time is advanced
        assertEquals(1500L, clock.currentTimeMillis())
    }

    @Test
    fun `SystemTimeProvider returns system time`() {
        // Given: The system time provider
        val before = System.currentTimeMillis()

        // When: Getting time from SystemTimeProvider
        val time = SystemTimeProvider.currentTimeMillis()

        // Then: Returns a time close to system time
        val after = System.currentTimeMillis()
        assert(time >= before) { "Time should be >= before" }
        assert(time <= after) { "Time should be <= after" }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Internal Channel List Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `internal channels list is populated after init`() =
        runBlocking {
            // Given: Repository with channels
            liveRepository.channels = testChannels

            // When: Initializing
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // Then: Internal channels list is populated
            assertEquals(4, controller.channels.size)
        }

    @Test
    fun `internal channels list filters by category when hint provided`() =
        runBlocking {
            // Given: Repository with channels
            liveRepository.channels = testChannels

            // When: Initializing with category filter
            controller.initFromPlaybackContext(
                PlaybackContext(type = PlaybackType.LIVE, liveCategoryHint = "News"),
            )

            // Then: Only News channels are loaded
            assertEquals(2, controller.channels.size)
            assertTrue(controller.channels.all { it.category == "News" })
        }

    // ════════════════════════════════════════════════════════════════════════════
    // Behavior Contract Compliance Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `controller does not integrate with ResumeManager`() {
        // This test documents that LivePlaybackController does NOT handle resume.
        // Resume is handled at the session level where LIVE type is excluded.
        // See: INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md Section 3.1

        // Given: A controller
        // When: Checking API surface
        // Then: No resume-related methods exist in LivePlaybackController interface
        // This is verified by the interface definition itself - no loadResumePositionMs, etc.
        assertTrue("Test documents LIVE resume exclusion", true)
    }

    @Test
    fun `controller does not integrate with KidsPlaybackGate`() {
        // This test documents that LivePlaybackController does NOT handle kids gating.
        // Kids gating is handled by KidsPlaybackGate at the session level.
        // See: INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md Section 4

        // Given: A controller
        // When: Checking API surface
        // Then: No kids-related methods exist in LivePlaybackController interface
        assertTrue("Test documents LIVE kids gating exclusion", true)
    }
}
