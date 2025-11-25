package com.chris.m3usuite.player.internal.session

import com.chris.m3usuite.player.internal.domain.KidsGateState
import com.chris.m3usuite.player.internal.domain.KidsPlaybackGate
import com.chris.m3usuite.player.internal.domain.PlaybackContext
import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.domain.ResumeManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Phase 2 modular resume and kids-gate logic.
 *
 * These tests validate modular behavior independently of the legacy InternalPlayerScreen.
 * They use fake implementations of ResumeManager and KidsPlaybackGate interfaces.
 */
class InternalPlayerSessionPhase2Test {

    // ════════════════════════════════════════════════════════════════════════════
    // Fake Implementations for Testing
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Fake ResumeManager for testing resume behavior without ObjectBox.
     */
    class FakeResumeManager : ResumeManager {
        // Storage for resume positions (in seconds)
        private val vodResume = mutableMapOf<Long, Int>()
        private val seriesResume = mutableMapOf<String, Int>()

        // Track method calls
        var loadCalled = false
        var tickCalled = false
        var endedCalled = false

        fun setVodResume(mediaId: Long, positionSecs: Int) {
            vodResume[mediaId] = positionSecs
        }

        fun setSeriesResume(seriesId: Int, season: Int, episodeNum: Int, positionSecs: Int) {
            seriesResume["$seriesId-$season-$episodeNum"] = positionSecs
        }

        fun getStoredVodResume(mediaId: Long): Int? = vodResume[mediaId]

        fun getStoredSeriesResume(seriesId: Int, season: Int, episodeNum: Int): Int? =
            seriesResume["$seriesId-$season-$episodeNum"]

        fun isVodResumeCleared(mediaId: Long): Boolean = !vodResume.containsKey(mediaId)

        fun isSeriesResumeCleared(seriesId: Int, season: Int, episodeNum: Int): Boolean =
            !seriesResume.containsKey("$seriesId-$season-$episodeNum")

        override suspend fun loadResumePositionMs(context: PlaybackContext): Long? {
            loadCalled = true
            return when (context.type) {
                PlaybackType.VOD -> {
                    val id = context.mediaId ?: return null
                    val posSecs = vodResume[id] ?: return null
                    // Parity: Only restore if > 10 seconds
                    if (posSecs > 10) posSecs.toLong() * 1000L else null
                }
                PlaybackType.SERIES -> {
                    val seriesId = context.seriesId
                    val season = context.season
                    val episodeNum = context.episodeNumber
                    if (seriesId == null || season == null || episodeNum == null) return null
                    val posSecs = seriesResume["$seriesId-$season-$episodeNum"] ?: return null
                    // Parity: Only restore if > 10 seconds
                    if (posSecs > 10) posSecs.toLong() * 1000L else null
                }
                PlaybackType.LIVE -> null
            }
        }

        override suspend fun handlePeriodicTick(
            context: PlaybackContext,
            positionMs: Long,
            durationMs: Long,
        ) {
            tickCalled = true
            if (context.type == PlaybackType.LIVE) return
            if (durationMs <= 0L) return

            val remaining = durationMs - positionMs
            val posSecs = (positionMs / 1000L).coerceAtLeast(0L).toInt()

            when (context.type) {
                PlaybackType.VOD -> {
                    val mediaId = context.mediaId ?: return
                    // Parity: Clear if remaining < 10 seconds
                    if (durationMs > 0 && remaining in 0L..9999L) {
                        vodResume.remove(mediaId)
                    } else {
                        vodResume[mediaId] = posSecs
                    }
                }
                PlaybackType.SERIES -> {
                    val seriesId = context.seriesId
                    val season = context.season
                    val episodeNum = context.episodeNumber
                    if (seriesId == null || season == null || episodeNum == null) return
                    val key = "$seriesId-$season-$episodeNum"
                    // Parity: Clear if remaining < 10 seconds
                    if (durationMs > 0 && remaining in 0L..9999L) {
                        seriesResume.remove(key)
                    } else {
                        seriesResume[key] = posSecs
                    }
                }
                PlaybackType.LIVE -> Unit
            }
        }

        override suspend fun handleEnded(context: PlaybackContext) {
            endedCalled = true
            when (context.type) {
                PlaybackType.VOD -> {
                    val mediaId = context.mediaId ?: return
                    vodResume.remove(mediaId)
                }
                PlaybackType.SERIES -> {
                    val seriesId = context.seriesId
                    val season = context.season
                    val episodeNum = context.episodeNumber
                    if (seriesId == null || season == null || episodeNum == null) return
                    seriesResume.remove("$seriesId-$season-$episodeNum")
                }
                PlaybackType.LIVE -> Unit
            }
        }
    }

    /**
     * Fake KidsPlaybackGate for testing kids gate behavior without ObjectBox.
     */
    class FakeKidsPlaybackGate : KidsPlaybackGate {
        var isKidProfile: Boolean = false
        var remainingMinutes: Int = 60
        var usedMinutes: Int = 0
        var tickUsageCalled = false
        var evaluateStartCalled = false
        var profileId: Long = 1L

        override suspend fun evaluateStart(): KidsGateState {
            evaluateStartCalled = true
            if (!isKidProfile) {
                return KidsGateState(
                    kidActive = false,
                    kidBlocked = false,
                    kidProfileId = null,
                )
            }
            return KidsGateState(
                kidActive = true,
                kidBlocked = remainingMinutes <= 0,
                kidProfileId = profileId,
            )
        }

        override suspend fun onPlaybackTick(
            current: KidsGateState,
            deltaSecs: Int,
        ): KidsGateState {
            tickUsageCalled = true
            if (!current.kidActive) return current
            current.kidProfileId ?: return current
            if (deltaSecs <= 0) return current

            // Simulate ScreenTimeRepository.tickUsageIfPlaying
            // Converts accumulated seconds to minutes
            val addMinutes = deltaSecs / 60
            if (addMinutes > 0) {
                usedMinutes += addMinutes
                remainingMinutes = (remainingMinutes - addMinutes).coerceAtLeast(0)
            }

            return current.copy(kidBlocked = remainingMinutes <= 0)
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Test Setup
    // ════════════════════════════════════════════════════════════════════════════

    private lateinit var resumeManager: FakeResumeManager
    private lateinit var kidsGate: FakeKidsPlaybackGate

    @Before
    fun setup() {
        resumeManager = FakeResumeManager()
        kidsGate = FakeKidsPlaybackGate()
    }

    // ════════════════════════════════════════════════════════════════════════════
    // ResumeManager Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `resumeManager does not resume when stored position is less than or equal to 10 seconds`() = runBlocking {
        // Given: Resume position at 5 seconds
        resumeManager.setVodResume(123L, 5)
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)

        // When: Loading resume position
        val result = resumeManager.loadResumePositionMs(context)

        // Then: Returns null (no resume)
        assertNull("Should not resume when position <= 10 seconds", result)
        assertTrue("Load should be called", resumeManager.loadCalled)
    }

    @Test
    fun `resumeManager does not resume when stored position is exactly 10 seconds`() = runBlocking {
        // Given: Resume position exactly at 10 seconds
        resumeManager.setVodResume(123L, 10)
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)

        // When: Loading resume position
        val result = resumeManager.loadResumePositionMs(context)

        // Then: Returns null (boundary case)
        assertNull("Should not resume when position is exactly 10 seconds", result)
    }

    @Test
    fun `resumeManager resumes when stored position is greater than 10 seconds`() = runBlocking {
        // Given: Resume position at 60 seconds
        resumeManager.setVodResume(456L, 60)
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 456L)

        // When: Loading resume position
        val result = resumeManager.loadResumePositionMs(context)

        // Then: Returns position in milliseconds
        assertEquals("Should resume at 60 seconds in ms", 60_000L, result)
    }

    @Test
    fun `resumeManager resumes series content when stored position is greater than 10 seconds`() = runBlocking {
        // Given: Series resume position at 120 seconds
        resumeManager.setSeriesResume(100, 2, 5, 120)
        val context = PlaybackContext(
            type = PlaybackType.SERIES,
            seriesId = 100,
            season = 2,
            episodeNumber = 5,
        )

        // When: Loading resume position
        val result = resumeManager.loadResumePositionMs(context)

        // Then: Returns position in milliseconds
        assertEquals("Should resume series at 120 seconds in ms", 120_000L, result)
    }

    @Test
    fun `resumeManager returns null for LIVE content`() = runBlocking {
        // Given: LIVE context (resume not applicable)
        val context = PlaybackContext(type = PlaybackType.LIVE, mediaId = 789L)

        // When: Loading resume position
        val result = resumeManager.loadResumePositionMs(context)

        // Then: Returns null
        assertNull("LIVE content should not have resume", result)
    }

    @Test
    fun `resumeManager clears resume when remaining duration is less than 10 seconds`() = runBlocking {
        // Given: VOD with resume position stored
        resumeManager.setVodResume(123L, 100)
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)

        // When: Position near end (5 seconds remaining)
        resumeManager.handlePeriodicTick(
            context = context,
            positionMs = 55_000L,
            durationMs = 60_000L, // 60 seconds total, 5 remaining
        )

        // Then: Resume should be cleared
        assertTrue("Resume should be cleared when near end", resumeManager.isVodResumeCleared(123L))
    }

    @Test
    fun `resumeManager saves resume when remaining duration is 10 seconds or more`() = runBlocking {
        // Given: VOD context
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)

        // When: Position with 15 seconds remaining
        resumeManager.handlePeriodicTick(
            context = context,
            positionMs = 45_000L,
            durationMs = 60_000L, // 60 seconds total, 15 remaining
        )

        // Then: Resume should be saved
        assertEquals("Resume should be saved", 45, resumeManager.getStoredVodResume(123L))
    }

    @Test
    fun `resumeManager clears resume on playback ended`() = runBlocking {
        // Given: VOD with resume position stored
        resumeManager.setVodResume(123L, 100)
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)

        // When: Playback ends
        resumeManager.handleEnded(context)

        // Then: Resume should be cleared
        assertTrue("Resume should be cleared on ended", resumeManager.isVodResumeCleared(123L))
        assertTrue("handleEnded should be called", resumeManager.endedCalled)
    }

    @Test
    fun `resumeManager clears series resume on playback ended`() = runBlocking {
        // Given: Series with resume position stored
        resumeManager.setSeriesResume(100, 2, 5, 300)
        val context = PlaybackContext(
            type = PlaybackType.SERIES,
            seriesId = 100,
            season = 2,
            episodeNumber = 5,
        )

        // When: Playback ends
        resumeManager.handleEnded(context)

        // Then: Resume should be cleared
        assertTrue(
            "Series resume should be cleared on ended",
            resumeManager.isSeriesResumeCleared(100, 2, 5),
        )
    }

    @Test
    fun `resumeManager does nothing for LIVE on ended`() = runBlocking {
        // Given: LIVE context
        val context = PlaybackContext(type = PlaybackType.LIVE, mediaId = 789L)

        // When: Playback ends
        resumeManager.handleEnded(context)

        // Then: No exception, ended should be called
        assertTrue("handleEnded should be called even for LIVE", resumeManager.endedCalled)
    }

    @Test
    fun `resumeManager does not save when duration is invalid`() = runBlocking {
        // Given: VOD context
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)

        // When: Duration is 0 or negative
        resumeManager.handlePeriodicTick(
            context = context,
            positionMs = 30_000L,
            durationMs = 0L,
        )

        // Then: Resume should not be saved (invalid duration guard)
        assertNull("Resume should not be saved with invalid duration", resumeManager.getStoredVodResume(123L))
    }

    // ════════════════════════════════════════════════════════════════════════════
    // KidsPlaybackGate Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `kidsGate returns inactive state for non-kid profile`() = runBlocking {
        // Given: Adult profile
        kidsGate.isKidProfile = false

        // When: Evaluating start
        val state = kidsGate.evaluateStart()

        // Then: Kid gate is not active
        assertFalse("Kid gate should not be active for adult", state.kidActive)
        assertFalse("Should not be blocked for adult", state.kidBlocked)
        assertNull("Kid profile ID should be null", state.kidProfileId)
        assertTrue("evaluateStart should be called", kidsGate.evaluateStartCalled)
    }

    @Test
    fun `kidsGate returns active state for kid profile with remaining time`() = runBlocking {
        // Given: Kid profile with time remaining
        kidsGate.isKidProfile = true
        kidsGate.remainingMinutes = 30
        kidsGate.profileId = 42L

        // When: Evaluating start
        val state = kidsGate.evaluateStart()

        // Then: Kid gate is active but not blocked
        assertTrue("Kid gate should be active", state.kidActive)
        assertFalse("Should not be blocked with time remaining", state.kidBlocked)
        assertEquals("Profile ID should match", 42L, state.kidProfileId)
    }

    @Test
    fun `kidsGate returns blocked state for kid profile with no remaining time`() = runBlocking {
        // Given: Kid profile with no time remaining
        kidsGate.isKidProfile = true
        kidsGate.remainingMinutes = 0
        kidsGate.profileId = 42L

        // When: Evaluating start
        val state = kidsGate.evaluateStart()

        // Then: Kid gate is blocked
        assertTrue("Kid gate should be active", state.kidActive)
        assertTrue("Should be blocked with no time", state.kidBlocked)
    }

    @Test
    fun `kidsGate 60-second accumulation triggers quota decrement`() = runBlocking {
        // Given: Kid profile with time remaining
        kidsGate.isKidProfile = true
        kidsGate.remainingMinutes = 10
        kidsGate.profileId = 42L

        val currentState = KidsGateState(
            kidActive = true,
            kidBlocked = false,
            kidProfileId = 42L,
        )

        // When: 60 seconds of playback accumulated
        val newState = kidsGate.onPlaybackTick(currentState, deltaSecs = 60)

        // Then: Quota should be decremented by 1 minute
        assertTrue("tickUsage should be called", kidsGate.tickUsageCalled)
        assertEquals("Remaining minutes should decrease", 9, kidsGate.remainingMinutes)
        assertFalse("Should not be blocked yet", newState.kidBlocked)
    }

    @Test
    fun `kidsGate quota exhaustion leads to blocked state`() = runBlocking {
        // Given: Kid profile with 1 minute remaining
        kidsGate.isKidProfile = true
        kidsGate.remainingMinutes = 1
        kidsGate.profileId = 42L

        val currentState = KidsGateState(
            kidActive = true,
            kidBlocked = false,
            kidProfileId = 42L,
        )

        // When: 60 seconds of playback (exhausts quota)
        val newState = kidsGate.onPlaybackTick(currentState, deltaSecs = 60)

        // Then: Should be blocked
        assertTrue("Should be blocked when quota exhausted", newState.kidBlocked)
        assertEquals("Remaining minutes should be 0", 0, kidsGate.remainingMinutes)
    }

    @Test
    fun `kidsGate does not decrement for non-kid state`() = runBlocking {
        // Given: Non-kid state
        val currentState = KidsGateState(
            kidActive = false,
            kidBlocked = false,
            kidProfileId = null,
        )

        // When: Tick is called
        val newState = kidsGate.onPlaybackTick(currentState, deltaSecs = 60)

        // Then: State unchanged, no tick usage
        assertFalse("Should still be inactive", newState.kidActive)
        // tickUsageCalled is true but early return happens before actual usage
    }

    @Test
    fun `kidsGate handles zero or negative deltaSecs`() = runBlocking {
        // Given: Kid profile active
        kidsGate.isKidProfile = true
        kidsGate.remainingMinutes = 10
        kidsGate.profileId = 42L

        val currentState = KidsGateState(
            kidActive = true,
            kidBlocked = false,
            kidProfileId = 42L,
        )

        // When: Zero seconds passed
        val newState = kidsGate.onPlaybackTick(currentState, deltaSecs = 0)

        // Then: State unchanged
        assertEquals("Remaining minutes unchanged", 10, kidsGate.remainingMinutes)
        assertFalse("Should not be blocked", newState.kidBlocked)
    }

    @Test
    fun `kidsGate blocked state persists`() = runBlocking {
        // Given: Already blocked state
        kidsGate.isKidProfile = true
        kidsGate.remainingMinutes = 0
        kidsGate.profileId = 42L

        val currentState = KidsGateState(
            kidActive = true,
            kidBlocked = true,
            kidProfileId = 42L,
        )

        // When: Another tick occurs
        val newState = kidsGate.onPlaybackTick(currentState, deltaSecs = 60)

        // Then: State remains blocked
        assertTrue("Should still be blocked", newState.kidBlocked)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Phase2Integration Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `Phase2Integration loadInitialResumePosition delegates to resumeManager`() = runBlocking {
        // Given: VOD context with resume
        resumeManager.setVodResume(123L, 60)
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)

        // When: Loading via Phase2Integration
        val result = Phase2Integration.loadInitialResumePosition(context, resumeManager)

        // Then: Position returned correctly
        assertEquals("Should return resume position", 60_000L, result)
        assertTrue("ResumeManager.load should be called", resumeManager.loadCalled)
    }

    @Test
    fun `Phase2Integration onPlaybackTick handles resume and kids gate`() = runBlocking {
        // Given: VOD context and active kids gate
        val vodContext = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)
        kidsGate.isKidProfile = true
        kidsGate.remainingMinutes = 10

        val kidsState = KidsGateState(
            kidActive = true,
            kidBlocked = false,
            kidProfileId = 42L,
        )

        // When: Tick with 60 seconds accumulated
        val newKidsState = Phase2Integration.onPlaybackTick(
            playbackContext = vodContext,
            positionMs = 30_000L,
            durationMs = 120_000L,
            resumeManager = resumeManager,
            kidsGate = kidsGate,
            currentKidsState = kidsState,
            tickAccumSecs = 60,
        )

        // Then: Both resume and kids gate should be processed
        assertTrue("Resume tick should be called", resumeManager.tickCalled)
        assertEquals("Resume should be saved", 30, resumeManager.getStoredVodResume(123L))
        assertNotNull("Kids state should be returned", newKidsState)
    }

    @Test
    fun `Phase2Integration onPlaybackEnded clears resume`() = runBlocking {
        // Given: VOD context with resume
        resumeManager.setVodResume(123L, 100)
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)

        // When: Playback ends via Phase2Integration
        Phase2Integration.onPlaybackEnded(context, resumeManager)

        // Then: Resume should be cleared
        assertTrue("handleEnded should be called", resumeManager.endedCalled)
        assertTrue("Resume should be cleared", resumeManager.isVodResumeCleared(123L))
    }

    @Test
    fun `Phase2Integration evaluateKidsGateOnStart delegates to kidsGate`() = runBlocking {
        // Given: Kid profile
        kidsGate.isKidProfile = true
        kidsGate.remainingMinutes = 30
        kidsGate.profileId = 42L

        // When: Evaluating via Phase2Integration
        val state = Phase2Integration.evaluateKidsGateOnStart(kidsGate)

        // Then: State returned correctly
        assertTrue("Kid gate should be active", state.kidActive)
        assertFalse("Should not be blocked", state.kidBlocked)
        assertEquals("Profile ID should match", 42L, state.kidProfileId)
        assertTrue("evaluateStart should be called", kidsGate.evaluateStartCalled)
    }
}
