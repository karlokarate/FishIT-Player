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
 * Robustness tests for [DefaultLivePlaybackController].
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 3 – TASK 1: LIVE-TV ROBUSTNESS & DATA INTEGRITY TESTS
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * This test file validates the robustness enhancements implemented in Phase 3 Task 1:
 *
 * 1. **EPG Stale Detection**: Verify stale EPG detection and refresh behavior.
 * 2. **EPG Fallback & Caching**: Verify cached EPG data usage on errors.
 * 3. **Smart Channel Zapping**: Verify filtering of invalid channels (null/empty URLs, duplicates).
 * 4. **Controller Sanity Guards**: Verify crash-proof behavior on edge cases.
 * 5. **Live Metrics**: Verify metrics tracking for diagnostics.
 */
class LiveControllerRobustnessTest {
    // ════════════════════════════════════════════════════════════════════════════
    // Test Setup
    // ════════════════════════════════════════════════════════════════════════════

    private lateinit var liveRepository: FakeLiveChannelRepository
    private lateinit var epgRepository: FakeLiveEpgRepository
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var controller: DefaultLivePlaybackController

    private val validChannels =
        listOf(
            LiveChannel(id = 1L, name = "Channel 1", url = "http://ch1.m3u8", category = "News", logoUrl = null),
            LiveChannel(id = 2L, name = "Channel 2", url = "http://ch2.m3u8", category = "Sports", logoUrl = null),
            LiveChannel(id = 3L, name = "Channel 3", url = "http://ch3.m3u8", category = "News", logoUrl = null),
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
    // EPG Stale Detection Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `stale EPG is detected when nowTitle unchanged for threshold duration`() =
        runBlocking {
            // Given: Initialized controller with EPG data
            liveRepository.channels = validChannels
            epgRepository.nowNextMap = mapOf(1 to ("Morning Show" to "Afternoon News"))
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // When: Time advances past stale threshold without EPG change
            timeProvider.advanceBy(DefaultLivePlaybackController.DEFAULT_EPG_STALE_THRESHOLD_MS + 1000)
            controller.onPlaybackPositionChanged(0)

            // Then: Stale detection count increases
            val metrics = controller.liveMetrics.value
            assertEquals("Stale detection should increment", 1, metrics.epgStaleDetectionCount)
        }

    @Test
    fun `stale EPG is not detected when threshold not reached`() =
        runBlocking {
            // Given: Initialized controller with EPG data
            liveRepository.channels = validChannels
            epgRepository.nowNextMap = mapOf(1 to ("Morning Show" to "Afternoon News"))
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))
            val initialMetrics = controller.liveMetrics.value

            // When: Time advances but not past threshold
            timeProvider.advanceBy(DefaultLivePlaybackController.DEFAULT_EPG_STALE_THRESHOLD_MS - 1000)
            controller.onPlaybackPositionChanged(0)

            // Then: Stale detection count does not increase
            val metrics = controller.liveMetrics.value
            assertEquals("Stale detection should not increment", initialMetrics.epgStaleDetectionCount, metrics.epgStaleDetectionCount)
        }

    @Test
    fun `stale detection does not crash when no channel selected`() {
        // Given: Controller with no channel selected
        // When: Checking for stale EPG
        timeProvider.advanceBy(DefaultLivePlaybackController.DEFAULT_EPG_STALE_THRESHOLD_MS + 1000)
        controller.onPlaybackPositionChanged(0)

        // Then: No exception thrown
        assertNull("No channel should be selected", controller.currentChannel.value)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // EPG Fallback & Caching Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `EPG cache is populated on successful fetch`() =
        runBlocking {
            // Given: Repository with EPG data
            liveRepository.channels = validChannels
            epgRepository.nowNextMap = mapOf(1 to ("Show A" to "Show B"))

            // When: Initializing controller
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // Then: EPG data is cached (metrics show refresh)
            val metrics = controller.liveMetrics.value
            assertEquals("EPG should be refreshed once", 1, metrics.epgRefreshCount)
        }

    @Test
    fun `EPG fallback uses cached data on repository error`() =
        runBlocking {
            // Given: Controller with cached EPG data
            liveRepository.channels = validChannels
            epgRepository.nowNextMap = mapOf(1 to ("Cached Show" to "Next Show"))
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))
            assertEquals("EPG should show cached data", "Cached Show", controller.epgOverlay.value.nowTitle)

            // When: Repository throws on subsequent refresh
            epgRepository.shouldThrow = true
            controller.refreshEpgForCurrentChannel()

            // Then: Cached data is used
            val metrics = controller.liveMetrics.value
            assertEquals("Cache hit count should increment", 1, metrics.epgCacheHitCount)
            assertEquals("EPG should still show cached data", "Cached Show", controller.epgOverlay.value.nowTitle)
        }

    @Test
    fun `EPG fallback returns null when no cache exists`() =
        runBlocking {
            // Given: Controller without cached data
            liveRepository.channels = validChannels
            epgRepository.shouldThrow = true

            // When: Initializing with failing repository
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // Then: EPG titles are null but no crash
            assertNull("EPG nowTitle should be null", controller.epgOverlay.value.nowTitle)
            assertNull("EPG nextTitle should be null", controller.epgOverlay.value.nextTitle)
        }

    @Test
    fun `EPG overlay never flickers to empty state after errors`() =
        runBlocking {
            // Given: Controller with cached EPG data
            liveRepository.channels = validChannels
            epgRepository.nowNextMap = mapOf(1 to ("Stable Show" to "Next"))
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))
            val initialNowTitle = controller.epgOverlay.value.nowTitle

            // When: Multiple repository errors occur
            epgRepository.shouldThrow = true
            repeat(5) {
                controller.refreshEpgForCurrentChannel()
                timeProvider.advanceBy(100)
            }

            // Then: EPG data remains stable (cached)
            assertEquals("EPG should not flicker to empty", initialNowTitle, controller.epgOverlay.value.nowTitle)
            assertTrue("Cache hit count should increase", controller.liveMetrics.value.epgCacheHitCount > 0)
        }

    // ════════════════════════════════════════════════════════════════════════════
    // Smart Channel Zapping Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `channels with null URLs are skipped`() =
        runBlocking {
            // Given: Repository with channels including empty URL (url is non-nullable in LiveChannel)
            liveRepository.channels =
                listOf(
                    LiveChannel(id = 1L, name = "Valid", url = "http://valid.m3u8", category = null, logoUrl = null),
                    LiveChannel(id = 2L, name = "Null URL", url = "", category = null, logoUrl = null),
                    LiveChannel(id = 3L, name = "Valid 2", url = "http://valid2.m3u8", category = null, logoUrl = null),
                )

            // When: Initializing controller
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // Then: Empty URL channel is skipped
            assertEquals("Only valid channels should be loaded", 2, controller.channels.size)
            assertTrue("All channels should have valid URLs", controller.channels.all { !it.url.isNullOrBlank() })
            assertEquals("Skip count should increment", 1, controller.liveMetrics.value.channelSkipCount)
        }

    @Test
    fun `channels with empty URLs are skipped`() =
        runBlocking {
            // Given: Repository with channels including empty URL
            liveRepository.channels =
                listOf(
                    LiveChannel(id = 1L, name = "Valid", url = "http://valid.m3u8", category = null, logoUrl = null),
                    LiveChannel(id = 2L, name = "Empty URL", url = "", category = null, logoUrl = null),
                    LiveChannel(id = 3L, name = "Blank URL", url = "   ", category = null, logoUrl = null),
                )

            // When: Initializing controller
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // Then: Empty/blank URL channels are skipped
            assertEquals("Only valid channels should be loaded", 1, controller.channels.size)
            assertEquals("Skip count should increment", 2, controller.liveMetrics.value.channelSkipCount)
        }

    @Test
    fun `duplicate channels are removed`() =
        runBlocking {
            // Given: Repository with duplicate channel URLs
            liveRepository.channels =
                listOf(
                    LiveChannel(id = 1L, name = "Channel 1", url = "http://same.m3u8", category = null, logoUrl = null),
                    LiveChannel(id = 2L, name = "Channel 2", url = "http://different.m3u8", category = null, logoUrl = null),
                    LiveChannel(id = 3L, name = "Channel 1 Duplicate", url = "http://same.m3u8", category = null, logoUrl = null),
                )

            // When: Initializing controller
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // Then: Duplicate is removed
            assertEquals("Duplicate channel should be removed", 2, controller.channels.size)
            val urls = controller.channels.map { it.url }
            assertEquals("URLs should be unique", 2, urls.distinct().size)
        }

    @Test
    fun `category filter is applied correctly`() =
        runBlocking {
            // Given: Repository with channels in different categories
            liveRepository.channels =
                listOf(
                    LiveChannel(id = 1L, name = "News 1", url = "http://news1.m3u8", category = "News", logoUrl = null),
                    LiveChannel(id = 2L, name = "Sports 1", url = "http://sports1.m3u8", category = "Sports", logoUrl = null),
                    LiveChannel(id = 3L, name = "News 2", url = "http://news2.m3u8", category = "News", logoUrl = null),
                )

            // When: Initializing with category hint
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE, liveCategoryHint = "News"))

            // Then: Only News channels are loaded
            assertEquals("Only News channels should be loaded", 2, controller.channels.size)
            assertTrue("All channels should be News category", controller.channels.all { it.category == "News" })
        }

    // ════════════════════════════════════════════════════════════════════════════
    // Controller Sanity Guards Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `jumpChannel never crashes on empty channel list`() {
        // Given: Empty channel list
        liveRepository.channels = emptyList()

        // When: Jumping channel (should not crash)
        controller.jumpChannel(+1)
        controller.jumpChannel(-1)
        controller.jumpChannel(+10)

        // Then: No exception thrown, channel remains null
        assertNull("Channel should remain null", controller.currentChannel.value)
    }

    @Test
    fun `jumpChannel handles repeated jumps correctly`() =
        runBlocking {
            // Given: Initialized controller
            liveRepository.channels = validChannels
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))
            assertEquals(1L, controller.currentChannel.value?.id)

            // When: Multiple rapid jumps
            repeat(10) {
                controller.jumpChannel(+1)
            }

            // Then: Correct channel selected with wrap-around
            val expectedIndex = 10 % validChannels.size
            val expectedId = validChannels[expectedIndex].id
            assertEquals("Channel should wrap around correctly", expectedId, controller.currentChannel.value?.id)
        }

    @Test
    fun `epgOverlay never throws on malformed data`() =
        runBlocking {
            // Given: Repository with very long EPG strings
            val longString = "X".repeat(10000)
            liveRepository.channels = validChannels
            epgRepository.nowNextMap = mapOf(1 to (longString to longString))

            // When: Initializing (should not crash)
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // Then: EPG overlay handles long strings
            assertNotNull("EPG should be populated", controller.epgOverlay.value.nowTitle)
            assertEquals("EPG should contain long string", longString, controller.epgOverlay.value.nowTitle)
        }

    @Test
    fun `overlay automatically hides when switching channels`() =
        runBlocking {
            // Given: Initialized controller with overlay visible
            liveRepository.channels = validChannels
            epgRepository.nowNextMap = mapOf(1 to ("Show A" to "Show B"))
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))
            assertTrue("Overlay should be visible initially", controller.epgOverlay.value.visible)

            // When: Jumping to next channel
            controller.jumpChannel(+1)

            // Then: Overlay is hidden
            assertFalse("Overlay should be hidden after channel switch", controller.epgOverlay.value.visible)
        }

    @Test
    fun `selectChannel hides overlay when switching`() =
        runBlocking {
            // Given: Initialized controller with overlay visible
            liveRepository.channels = validChannels
            epgRepository.nowNextMap = mapOf(1 to ("Show A" to "Show B"))
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))
            assertTrue("Overlay should be visible initially", controller.epgOverlay.value.visible)

            // When: Selecting a different channel
            controller.selectChannel(2L)

            // Then: Overlay is hidden
            assertFalse("Overlay should be hidden after channel selection", controller.epgOverlay.value.visible)
        }

    @Test
    fun `onPlaybackPositionChanged never throws exception`() {
        // Given: Controller in any state
        // When: Position changed with extreme values
        controller.onPlaybackPositionChanged(Long.MAX_VALUE)
        controller.onPlaybackPositionChanged(Long.MIN_VALUE)
        controller.onPlaybackPositionChanged(-1)
        controller.onPlaybackPositionChanged(0)

        // Then: No exception thrown
        assertTrue("Should not throw on any position value", true)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Live Metrics Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `metrics track EPG refresh count`() =
        runBlocking {
            // Given: Initialized controller
            liveRepository.channels = validChannels
            epgRepository.nowNextMap = mapOf(1 to ("Show" to "Next"))

            // When: Multiple EPG refreshes
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))
            controller.refreshEpgForCurrentChannel()
            controller.refreshEpgForCurrentChannel()

            // Then: Metrics track refresh count
            val metrics = controller.liveMetrics.value
            assertEquals("Refresh count should be 3", 3, metrics.epgRefreshCount)
        }

    @Test
    fun `metrics track cache hit count`() =
        runBlocking {
            // Given: Controller with cached data
            liveRepository.channels = validChannels
            epgRepository.nowNextMap = mapOf(1 to ("Cached" to "Data"))
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // When: Repository errors force cache usage
            epgRepository.shouldThrow = true
            repeat(3) {
                controller.refreshEpgForCurrentChannel()
            }

            // Then: Cache hit count is tracked
            val metrics = controller.liveMetrics.value
            assertEquals("Cache hit count should be 3", 3, metrics.epgCacheHitCount)
        }

    @Test
    fun `metrics track stale detection count`() =
        runBlocking {
            // Given: Initialized controller
            liveRepository.channels = validChannels
            epgRepository.nowNextMap = mapOf(1 to ("Show" to "Next"))
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // When: Multiple stale detections occur
            repeat(3) {
                timeProvider.advanceBy(DefaultLivePlaybackController.DEFAULT_EPG_STALE_THRESHOLD_MS + 1000)
                controller.onPlaybackPositionChanged(0)
            }

            // Then: Stale detection count is tracked
            val metrics = controller.liveMetrics.value
            assertTrue("Stale detection should occur", metrics.epgStaleDetectionCount > 0)
        }

    @Test
    fun `metrics track channel skip count`() =
        runBlocking {
            // Given: Repository with invalid channels
            liveRepository.channels =
                listOf(
                    LiveChannel(id = 1L, name = "Valid", url = "http://valid.m3u8", category = null, logoUrl = null),
                    LiveChannel(id = 2L, name = "Empty1", url = "", category = null, logoUrl = null),
                    LiveChannel(id = 3L, name = "Empty2", url = "", category = null, logoUrl = null),
                )

            // When: Initializing controller
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // Then: Skip count is tracked
            val metrics = controller.liveMetrics.value
            assertEquals("Skip count should be 2", 2, metrics.channelSkipCount)
        }

    @Test
    fun `metrics have safe default values`() {
        // Given: Newly constructed controller
        // When: Checking metrics
        val metrics = controller.liveMetrics.value

        // Then: All metrics are zero
        assertEquals("Default epgRefreshCount should be 0", 0, metrics.epgRefreshCount)
        assertEquals("Default epgCacheHitCount should be 0", 0, metrics.epgCacheHitCount)
        assertEquals("Default epgStaleDetectionCount should be 0", 0, metrics.epgStaleDetectionCount)
        assertEquals("Default channelSkipCount should be 0", 0, metrics.channelSkipCount)
        assertEquals("Default lastEpgRefreshTimestamp should be 0", 0L, metrics.lastEpgRefreshTimestamp)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Edge Case Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `controller handles all-invalid channel list gracefully`() =
        runBlocking {
            // Given: Repository with only invalid channels
            liveRepository.channels =
                listOf(
                    LiveChannel(id = 1L, name = "Empty1", url = "", category = null, logoUrl = null),
                    LiveChannel(id = 2L, name = "Empty2", url = "", category = null, logoUrl = null),
                    LiveChannel(id = 3L, name = "Blank", url = "   ", category = null, logoUrl = null),
                )

            // When: Initializing controller
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))

            // Then: No channels loaded, no crash
            assertTrue("No valid channels should be loaded", controller.channels.isEmpty())
            assertNull("No channel should be selected", controller.currentChannel.value)
            assertEquals("All channels should be skipped", 3, controller.liveMetrics.value.channelSkipCount)
        }

    @Test
    fun `wrap-around works correctly at list boundaries`() =
        runBlocking {
            // Given: Small channel list
            liveRepository.channels = validChannels.take(2)
            controller.initFromPlaybackContext(PlaybackContext(type = PlaybackType.LIVE))
            assertEquals(1L, controller.currentChannel.value?.id)

            // When: Jump backward from first channel
            controller.jumpChannel(-1)

            // Then: Wraps to last channel
            assertEquals("Should wrap to last channel", 2L, controller.currentChannel.value?.id)

            // Phase 3 Task 2: Advance time to avoid throttle
            timeProvider.advanceBy(250)

            // When: Jump forward from last channel
            controller.jumpChannel(+1)

            // Then: Wraps to first channel
            assertEquals("Should wrap to first channel", 1L, controller.currentChannel.value?.id)
        }

    // ════════════════════════════════════════════════════════════════════════════
    // Fake Implementations (reusing from LivePlaybackControllerTest)
    // ════════════════════════════════════════════════════════════════════════════

    class FakeLiveChannelRepository : LiveChannelRepository {
        var channels: List<LiveChannel> = emptyList()
        var shouldThrow = false

        override suspend fun getChannels(
            categoryHint: String?,
            providerHint: String?,
        ): List<LiveChannel> {
            if (shouldThrow) throw RuntimeException("Test error")
            return channels.filter { channel ->
                categoryHint == null || channel.category == categoryHint
            }
        }

        override suspend fun getChannel(channelId: Long): LiveChannel? {
            if (shouldThrow) throw RuntimeException("Test error")
            return channels.find { it.id == channelId }
        }
    }

    class FakeLiveEpgRepository : LiveEpgRepository {
        var nowNextMap: Map<Int, Pair<String?, String?>> = emptyMap()
        var shouldThrow = false

        override suspend fun getNowNext(streamId: Int): Pair<String?, String?> {
            if (shouldThrow) throw RuntimeException("Test error")
            return nowNextMap[streamId] ?: (null to null)
        }
    }

    class FakeTimeProvider : TimeProvider {
        var currentTime: Long = 1000000L

        override fun currentTimeMillis(): Long = currentTime

        fun advanceBy(ms: Long) {
            currentTime += ms
        }
    }
}
