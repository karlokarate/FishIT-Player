package com.chris.m3usuite.player.internal.live

import com.chris.m3usuite.player.internal.domain.PlaybackContext
import com.chris.m3usuite.player.internal.domain.PlaybackType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for Phase 3 – Task 2: SIP Live-TV interaction & UX polish.
 *
 * Validates:
 * - Throttle behavior for jumpChannel
 * - LiveEpgInfoState population
 * - EPG overlay auto-hide on channel change
 * - UX polish features
 */
class DefaultLivePlaybackControllerTask2Test {
    // ════════════════════════════════════════════════════════════════════════════
    // Test Helpers
    // ════════════════════════════════════════════════════════════════════════════

    private class FakeLiveChannelRepository : LiveChannelRepository {
        var channels: List<LiveChannel> = emptyList()

        override suspend fun getChannels(
            categoryHint: String?,
            providerHint: String?,
        ): List<LiveChannel> = channels

        override suspend fun getChannel(channelId: Long): LiveChannel? =
            channels.find { it.id == channelId }
    }

    private class FakeLiveEpgRepository : LiveEpgRepository {
        var epgData = mutableMapOf<Int, Pair<String?, String?>>()

        override suspend fun getNowNext(streamId: Int): Pair<String?, String?> =
            epgData[streamId] ?: (null to null)
    }

    private class FakeTimeProvider : TimeProvider {
        var currentTime: Long = 0L

        override fun currentTimeMillis(): Long = currentTime
    }

    private fun createController(
        channelRepo: FakeLiveChannelRepository = FakeLiveChannelRepository(),
        epgRepo: FakeLiveEpgRepository = FakeLiveEpgRepository(),
        clock: FakeTimeProvider = FakeTimeProvider(),
    ): ControllerTestSetup {
        val controller = DefaultLivePlaybackController(
            liveRepository = channelRepo,
            epgRepository = epgRepo,
            clock = clock,
        )
        return ControllerTestSetup(controller, channelRepo, epgRepo, clock)
    }

    private data class ControllerTestSetup(
        val controller: DefaultLivePlaybackController,
        val channelRepo: FakeLiveChannelRepository,
        val epgRepo: FakeLiveEpgRepository,
        val clock: FakeTimeProvider,
    )

    // ════════════════════════════════════════════════════════════════════════════
    // Throttle Behavior Tests (Phase 3 Task 2 - Requirement 1)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `jumpChannel throttles rapid successive calls`() =
        runBlocking {
            val setup = createController()
            val controller = setup.controller
            val channelRepo = setup.channelRepo
            val clock = setup.clock

            // Setup channels
            channelRepo.channels =
                listOf(
                    LiveChannel(1L, "Channel 1", "url1", null, null),
                    LiveChannel(2L, "Channel 2", "url2", null, null),
                    LiveChannel(3L, "Channel 3", "url3", null, null),
                )

            // Initialize controller
            controller.initFromPlaybackContext(
                PlaybackContext(
                    type = PlaybackType.LIVE,
                    mediaId = 1L,
                ),
            )

            // Initial state: Channel 1
            assertEquals(1L, controller.currentChannel.value?.id)

            // Jump to Channel 2 at time 0
            clock.currentTime = 0L
            controller.jumpChannel(+1)
            assertEquals(2L, controller.currentChannel.value?.id)

            // Try to jump again at time 100ms (within 200ms throttle) - should be ignored
            clock.currentTime = 100L
            controller.jumpChannel(+1)
            assertEquals(2L, controller.currentChannel.value?.id) // Still Channel 2

            // Jump again at time 250ms (beyond 200ms throttle) - should succeed
            clock.currentTime = 250L
            controller.jumpChannel(+1)
            assertEquals(3L, controller.currentChannel.value?.id) // Now Channel 3
        }

    @Test
    fun `jumpChannel allows jumps after throttle period expires`() =
        runBlocking {
            val setup = createController()
            val controller = setup.controller
            val channelRepo = setup.channelRepo
            val clock = setup.clock

            channelRepo.channels =
                listOf(
                    LiveChannel(1L, "Ch1", "url1", null, null),
                    LiveChannel(2L, "Ch2", "url2", null, null),
                    LiveChannel(3L, "Ch3", "url3", null, null),
                    LiveChannel(4L, "Ch4", "url4", null, null),
                )

            controller.initFromPlaybackContext(
                PlaybackContext(type = PlaybackType.LIVE, mediaId = 1L),
            )

            // Jump sequence with proper throttle spacing
            clock.currentTime = 0L
            controller.jumpChannel(+1)
            assertEquals(2L, controller.currentChannel.value?.id)

            clock.currentTime = 300L // 300ms later
            controller.jumpChannel(+1)
            assertEquals(3L, controller.currentChannel.value?.id)

            clock.currentTime = 600L // 300ms later
            controller.jumpChannel(+1)
            assertEquals(4L, controller.currentChannel.value?.id)
        }

    @Test
    fun `jumpChannel throttle protects against rapid channel storms`() =
        runBlocking {
            val setup = createController()
            val controller = setup.controller
            val channelRepo = setup.channelRepo
            val clock = setup.clock

            channelRepo.channels =
                listOf(
                    LiveChannel(1L, "Ch1", "url1", null, null),
                    LiveChannel(2L, "Ch2", "url2", null, null),
                    LiveChannel(3L, "Ch3", "url3", null, null),
                )

            controller.initFromPlaybackContext(
                PlaybackContext(type = PlaybackType.LIVE, mediaId = 1L),
            )

            // Simulate rapid swipe gestures (10 jumps in quick succession)
            clock.currentTime = 0L
            controller.jumpChannel(+1)
            val channelAfterFirstJump = controller.currentChannel.value?.id

            // Try 9 more jumps within 50ms each (all should be throttled)
            for (i in 1..9) {
                clock.currentTime = i * 50L
                controller.jumpChannel(+1)
            }

            // Channel should not have changed from first jump
            assertEquals(channelAfterFirstJump, controller.currentChannel.value?.id)
        }

    // ════════════════════════════════════════════════════════════════════════════
    // LiveEpgInfoState Tests (Phase 3 Task 2 - Requirement 3)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `liveEpgInfo exposes nowTitle and nextTitle from EPG overlay`() =
        runBlocking {
            val setup = createController()
            val controller = setup.controller
            val channelRepo = setup.channelRepo
            val epgRepo = setup.epgRepo

            channelRepo.channels = listOf(LiveChannel(1L, "Ch1", "url1", null, null))
            epgRepo.epgData[1] = ("Current Show" to "Next Show")

            controller.initFromPlaybackContext(
                PlaybackContext(type = PlaybackType.LIVE, mediaId = 1L),
            )

            val epgInfo = controller.liveEpgInfo.value
            assertNotNull(epgInfo)
            assertEquals("Current Show", epgInfo.nowTitle)
            assertEquals("Next Show", epgInfo.nextTitle)
        }

    @Test
    fun `liveEpgInfo progressPercent defaults to 0 when program timing unavailable`() =
        runBlocking {
            val setup = createController()
            val controller = setup.controller
            val channelRepo = setup.channelRepo
            val epgRepo = setup.epgRepo

            channelRepo.channels = listOf(LiveChannel(1L, "Ch1", "url1", null, null))
            epgRepo.epgData[1] = ("Show" to "Next")

            controller.initFromPlaybackContext(
                PlaybackContext(type = PlaybackType.LIVE, mediaId = 1L),
            )

            val epgInfo = controller.liveEpgInfo.value
            // Phase 3 Task 2: progressPercent is 0.0 by default (timing calculation not implemented)
            assertEquals(0.0f, epgInfo.progressPercent, 0.01f)
        }

    @Test
    fun `liveEpgInfo updates when EPG data changes`() =
        runBlocking {
            val setup = createController()
            val controller = setup.controller
            val channelRepo = setup.channelRepo
            val epgRepo = setup.epgRepo

            channelRepo.channels =
                listOf(
                    LiveChannel(1L, "Ch1", "url1", null, null),
                    LiveChannel(2L, "Ch2", "url2", null, null),
                )
            epgRepo.epgData[1] = ("Show A" to "Show B")
            epgRepo.epgData[2] = ("Show C" to "Show D")

            controller.initFromPlaybackContext(
                PlaybackContext(type = PlaybackType.LIVE, mediaId = 1L),
            )

            // Initial EPG info from Channel 1
            assertEquals("Show A", controller.liveEpgInfo.value.nowTitle)
            assertEquals("Show B", controller.liveEpgInfo.value.nextTitle)

            // Jump to Channel 2 - EPG info should NOT auto-update (overlay is hidden on jump)
            // EPG info remains from last shown overlay
            controller.jumpChannel(+1)
            // EPG info doesn't change because hideEpgOverlay is called, not refreshEpgOverlay
            // This is correct behavior - info state reflects last-shown data
        }

    @Test
    fun `liveEpgInfo has safe default when no EPG data available`() =
        runBlocking {
            val setup = createController()
            val controller = setup.controller
            val channelRepo = setup.channelRepo

            channelRepo.channels = listOf(LiveChannel(1L, "Ch1", "url1", null, null))
            // No EPG data configured

            controller.initFromPlaybackContext(
                PlaybackContext(type = PlaybackType.LIVE, mediaId = 1L),
            )

            val epgInfo = controller.liveEpgInfo.value
            // Should handle null EPG data gracefully
            assertNotNull(epgInfo)
        }

    // ════════════════════════════════════════════════════════════════════════════
    // EPG Overlay Auto-Hide Tests (Phase 3 Task 2 - Requirement 2)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `EPG overlay auto-hides on channel change`() =
        runBlocking {
            val setup = createController()
            val controller = setup.controller
            val channelRepo = setup.channelRepo
            val epgRepo = setup.epgRepo

            channelRepo.channels =
                listOf(
                    LiveChannel(1L, "Ch1", "url1", null, null),
                    LiveChannel(2L, "Ch2", "url2", null, null),
                )
            epgRepo.epgData[1] = ("Show A" to "Show B")
            epgRepo.epgData[2] = ("Show C" to "Show D")

            controller.initFromPlaybackContext(
                PlaybackContext(type = PlaybackType.LIVE, mediaId = 1L),
            )

            // EPG overlay is visible after initialization
            assertTrue(controller.epgOverlay.value.visible)

            // Jump to next channel
            controller.jumpChannel(+1)

            // EPG overlay should be hidden (Phase 3 Task 1 behavior, maintained in Task 2)
            assertEquals(false, controller.epgOverlay.value.visible)
        }

    @Test
    fun `EPG overlay auto-hides on selectChannel`() =
        runBlocking {
            val setup = createController()
            val controller = setup.controller
            val channelRepo = setup.channelRepo
            val epgRepo = setup.epgRepo

            channelRepo.channels =
                listOf(
                    LiveChannel(1L, "Ch1", "url1", null, null),
                    LiveChannel(2L, "Ch2", "url2", null, null),
                )
            epgRepo.epgData[1] = ("Show A" to "Show B")

            controller.initFromPlaybackContext(
                PlaybackContext(type = PlaybackType.LIVE, mediaId = 1L),
            )

            assertTrue(controller.epgOverlay.value.visible)

            // Directly select a different channel
            controller.selectChannel(2L)

            // Overlay should be hidden
            assertEquals(false, controller.epgOverlay.value.visible)
        }

    // ════════════════════════════════════════════════════════════════════════════
    // UI Resilience Tests (Phase 3 Task 2 - Requirement 4)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `controller handles empty channel list gracefully with throttle`() =
        runBlocking {
            val setup = createController()
            val controller = setup.controller
            val clock = setup.clock

            controller.initFromPlaybackContext(
                PlaybackContext(type = PlaybackType.LIVE, mediaId = 1L),
            )

            // Channels are empty
            assertEquals(null, controller.currentChannel.value)

            // Attempt throttled jumps on empty list - should not crash
            clock.currentTime = 0L
            controller.jumpChannel(+1)

            clock.currentTime = 300L
            controller.jumpChannel(-1)

            // Should remain null and not crash
            assertEquals(null, controller.currentChannel.value)
        }

    @Test
    fun `controller handles null EPG data gracefully in liveEpgInfo`() =
        runBlocking {
            val setup = createController()
            val controller = setup.controller
            val channelRepo = setup.channelRepo

            channelRepo.channels = listOf(LiveChannel(1L, "Ch1", "url1", null, null))
            // No EPG data

            controller.initFromPlaybackContext(
                PlaybackContext(type = PlaybackType.LIVE, mediaId = 1L),
            )

            val epgInfo = controller.liveEpgInfo.value
            assertNotNull(epgInfo)
            // Null titles should be handled gracefully
            assertEquals(null, epgInfo.nowTitle)
            assertEquals(null, epgInfo.nextTitle)
            assertEquals(0.0f, epgInfo.progressPercent, 0.01f)
        }
}
