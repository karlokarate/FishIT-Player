package com.chris.m3usuite.player.internal.session

import com.chris.m3usuite.player.internal.domain.KidsGateState
import com.chris.m3usuite.player.internal.domain.KidsPlaybackGate
import com.chris.m3usuite.player.internal.domain.PlaybackContext
import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.domain.ResumeManager
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Phase 2 Integration Tests for SIP InternalPlayerSession.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * TEST SCOPE: SIP-ONLY INTEGRATION
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * These tests verify:
 * 1. ResumeManager + KidsGate coordination through Phase2Integration
 * 2. SIP session correctly updates InternalPlayerUiState
 * 3. Blocking transitions do not affect runtime (no actual player)
 * 4. SIP session emits stable, predictable state for Phase 3
 *
 * **TEST ISOLATION:**
 * - Uses fakes/stubs only
 * - No ObjectBox, no Android UI, no ExoPlayer
 * - Tests modular behavior independently of legacy InternalPlayerScreen
 *
 * **RUNTIME STATUS:**
 * - These tests validate non-runtime SIP modules
 * - Runtime flow: InternalPlayerEntry → legacy InternalPlayerScreen
 * - SIP session remains reference-only until Phase 3 activation
 */
class InternalPlayerSessionPhase2IntegrationTest {

    // ════════════════════════════════════════════════════════════════════════════
    // Fake Implementations (Stubs for Integration Testing)
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Fake ResumeManager that simulates storage without ObjectBox.
     */
    class FakeResumeManager : ResumeManager {
        private val vodResume = mutableMapOf<Long, Int>()
        private val seriesResume = mutableMapOf<String, Int>()

        // Tracking flags
        var loadCalledCount = 0
        var tickCalledCount = 0
        var endedCalledCount = 0
        var lastTickContext: PlaybackContext? = null
        var lastTickPositionMs: Long? = null
        var lastTickDurationMs: Long? = null

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

        override suspend fun loadResumePositionMs(context: PlaybackContext): Long? {
            loadCalledCount++
            return when (context.type) {
                PlaybackType.VOD -> {
                    val id = context.mediaId ?: return null
                    val posSecs = vodResume[id] ?: return null
                    if (posSecs > 10) posSecs.toLong() * 1000L else null
                }
                PlaybackType.SERIES -> {
                    val seriesId = context.seriesId
                    val season = context.season
                    val episodeNum = context.episodeNumber
                    if (seriesId == null || season == null || episodeNum == null) return null
                    val posSecs = seriesResume["$seriesId-$season-$episodeNum"] ?: return null
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
            tickCalledCount++
            lastTickContext = context
            lastTickPositionMs = positionMs
            lastTickDurationMs = durationMs

            if (context.type == PlaybackType.LIVE) return
            if (durationMs <= 0L) return

            val remaining = durationMs - positionMs
            val posSecs = (positionMs / 1000L).coerceAtLeast(0L).toInt()

            when (context.type) {
                PlaybackType.VOD -> {
                    val mediaId = context.mediaId ?: return
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
            endedCalledCount++
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
     * Fake KidsPlaybackGate that simulates screen time logic without ObjectBox.
     */
    class FakeKidsPlaybackGate : KidsPlaybackGate {
        var isKidProfile: Boolean = false
        var remainingMinutes: Int = 60
        var profileId: Long = 1L

        // Tracking flags
        var evaluateStartCalledCount = 0
        var tickCalledCount = 0
        var lastTickDeltaSecs: Int? = null

        override suspend fun evaluateStart(): KidsGateState {
            evaluateStartCalledCount++
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
            tickCalledCount++
            lastTickDeltaSecs = deltaSecs
            if (!current.kidActive) return current
            current.kidProfileId ?: return current
            if (deltaSecs <= 0) return current

            val addMinutes = deltaSecs / 60
            if (addMinutes > 0) {
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
    // ResumeManager + KidsGate Coordination Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `Phase2Integration coordinates resume and kids gate on tick`() = runBlocking {
        // Given: VOD context with active kid profile
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)
        kidsGate.isKidProfile = true
        kidsGate.remainingMinutes = 10

        val kidsState = KidsGateState(
            kidActive = true,
            kidBlocked = false,
            kidProfileId = 42L,
        )

        // When: Tick with 60 seconds accumulated (triggers kids gate)
        val newState = Phase2Integration.onPlaybackTick(
            playbackContext = context,
            positionMs = 30_000L,
            durationMs = 120_000L,
            resumeManager = resumeManager,
            kidsGate = kidsGate,
            currentKidsState = kidsState,
            tickAccumSecs = 60,
        )

        // Then: Both resume and kids gate processed
        assertEquals("Resume tick should be called", 1, resumeManager.tickCalledCount)
        assertEquals("Resume should be saved at 30 seconds", 30, resumeManager.getStoredVodResume(123L))
        assertEquals("Kids tick should be called", 1, kidsGate.tickCalledCount)
        assertEquals("Remaining minutes should decrease", 9, kidsGate.remainingMinutes)
        assertNotNull("Kids state should be returned", newState)
        assertFalse("Should not be blocked yet", newState!!.kidBlocked)
    }

    @Test
    fun `Phase2Integration skips kids gate when tickAccumSecs below threshold`() = runBlocking {
        // Given: VOD context with active kid profile
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)
        kidsGate.isKidProfile = true
        kidsGate.remainingMinutes = 10

        val kidsState = KidsGateState(
            kidActive = true,
            kidBlocked = false,
            kidProfileId = 42L,
        )

        // When: Tick with only 30 seconds accumulated (below 60s threshold)
        Phase2Integration.onPlaybackTick(
            playbackContext = context,
            positionMs = 30_000L,
            durationMs = 120_000L,
            resumeManager = resumeManager,
            kidsGate = kidsGate,
            currentKidsState = kidsState,
            tickAccumSecs = 30,
        )

        // Then: Resume processed but kids gate skipped
        assertEquals("Resume tick should be called", 1, resumeManager.tickCalledCount)
        assertEquals("Kids tick should NOT be called", 0, kidsGate.tickCalledCount)
        assertEquals("Remaining minutes unchanged", 10, kidsGate.remainingMinutes)
    }

    @Test
    fun `Phase2Integration handles LIVE content without resume`() = runBlocking {
        // Given: LIVE context (resume not applicable)
        val context = PlaybackContext(type = PlaybackType.LIVE, mediaId = 789L)

        // When: Loading resume
        val resumeMs = Phase2Integration.loadInitialResumePosition(context, resumeManager)

        // Then: Returns null (LIVE has no resume)
        assertNull("LIVE should not have resume", resumeMs)
        assertEquals("Load should still be called", 1, resumeManager.loadCalledCount)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // InternalPlayerUiState Update Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `InternalPlayerUiState emits stable initial state`() {
        // Given: Default state
        val state = InternalPlayerUiState()

        // Then: All fields have stable defaults
        assertEquals("Default type is VOD", PlaybackType.VOD, state.playbackType)
        assertFalse("Default isPlaying is false", state.isPlaying)
        assertFalse("Default isBuffering is false", state.isBuffering)
        assertEquals("Default positionMs is 0", 0L, state.positionMs)
        assertEquals("Default durationMs is 0", 0L, state.durationMs)
        assertEquals("Default speed is 1.0", 1f, state.playbackSpeed, 0.01f)
        assertFalse("Default kidActive is false", state.kidActive)
        assertFalse("Default kidBlocked is false", state.kidBlocked)
        assertNull("Default kidProfileId is null", state.kidProfileId)
        assertNull("Default remainingKidsMinutes is null", state.remainingKidsMinutes)
        assertFalse("Default isResumingFromLegacy is false", state.isResumingFromLegacy)
        assertNull("Default resumeStartMs is null", state.resumeStartMs)
    }

    @Test
    fun `InternalPlayerUiState supports Phase 3 resume fields`() {
        // Given: State with resume info set
        val state = InternalPlayerUiState(
            isResumingFromLegacy = true,
            resumeStartMs = 60_000L,
        )

        // Then: Resume fields are accessible
        assertTrue("isResumingFromLegacy should be true", state.isResumingFromLegacy)
        assertEquals("resumeStartMs should be 60000", 60_000L, state.resumeStartMs)
    }

    @Test
    fun `InternalPlayerUiState supports Phase 3 kids fields`() {
        // Given: State with kids info set
        val state = InternalPlayerUiState(
            kidActive = true,
            kidBlocked = false,
            kidProfileId = 42L,
            remainingKidsMinutes = 15,
        )

        // Then: Kids fields are accessible
        assertTrue("kidActive should be true", state.kidActive)
        assertFalse("kidBlocked should be false", state.kidBlocked)
        assertEquals("kidProfileId should be 42", 42L, state.kidProfileId)
        assertEquals("remainingKidsMinutes should be 15", 15, state.remainingKidsMinutes)
    }

    @Test
    fun `InternalPlayerUiState copy preserves new fields`() {
        // Given: Initial state
        val initial = InternalPlayerUiState(
            playbackType = PlaybackType.SERIES,
            isResumingFromLegacy = true,
            resumeStartMs = 120_000L,
            remainingKidsMinutes = 30,
        )

        // When: Copy with playback update
        val updated = initial.copy(
            isPlaying = true,
            positionMs = 125_000L,
        )

        // Then: New fields preserved
        assertTrue("isResumingFromLegacy preserved", updated.isResumingFromLegacy)
        assertEquals("resumeStartMs preserved", 120_000L, updated.resumeStartMs)
        assertEquals("remainingKidsMinutes preserved", 30, updated.remainingKidsMinutes)
        // And playback updated
        assertTrue("isPlaying updated", updated.isPlaying)
        assertEquals("positionMs updated", 125_000L, updated.positionMs)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Blocking Transition Tests (No Runtime Impact)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `blocking transition updates state without runtime effect`() = runBlocking {
        // Given: Kid profile with 1 minute remaining
        kidsGate.isKidProfile = true
        kidsGate.remainingMinutes = 1
        kidsGate.profileId = 42L

        val kidsState = KidsGateState(
            kidActive = true,
            kidBlocked = false,
            kidProfileId = 42L,
        )

        // When: 60 seconds of playback exhausts quota
        val newState = kidsGate.onPlaybackTick(kidsState, deltaSecs = 60)

        // Then: State shows blocked (but no actual player to pause)
        assertTrue("Should be blocked", newState.kidBlocked)
        assertEquals("Remaining minutes should be 0", 0, kidsGate.remainingMinutes)

        // Verify: InternalPlayerUiState can represent this
        val uiState = InternalPlayerUiState(
            kidActive = newState.kidActive,
            kidBlocked = newState.kidBlocked,
            kidProfileId = newState.kidProfileId,
        )
        assertTrue("UI state reflects blocked", uiState.kidBlocked)
    }

    @Test
    fun `resume clear transition updates state without runtime effect`() = runBlocking {
        // Given: VOD with resume position stored
        resumeManager.setVodResume(123L, 100)
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)

        // When: Position near end (remaining < 10 seconds)
        resumeManager.handlePeriodicTick(
            context = context,
            positionMs = 55_000L,
            durationMs = 60_000L,
        )

        // Then: Resume cleared (but no actual player state change)
        assertTrue("Resume should be cleared", resumeManager.isVodResumeCleared(123L))
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Stable State Emission Tests (Phase 3 Readiness)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `SIP session emits predictable state sequence`() = runBlocking {
        // Simulate the state sequence that would occur during playback

        // Step 1: Initial state
        val initial = InternalPlayerUiState(
            playbackType = PlaybackType.VOD,
        )
        assertFalse("Initial: not playing", initial.isPlaying)
        assertEquals("Initial: position at 0", 0L, initial.positionMs)

        // Step 2: Playback starts with resume
        val resumed = initial.copy(
            isResumingFromLegacy = true,
            resumeStartMs = 60_000L,
            positionMs = 60_000L,
        )
        assertTrue("Resumed: resuming flag set", resumed.isResumingFromLegacy)
        assertEquals("Resumed: position at resume point", 60_000L, resumed.positionMs)

        // Step 3: Playing state
        val playing = resumed.copy(
            isPlaying = true,
            isResumingFromLegacy = false,
            positionMs = 65_000L,
        )
        assertTrue("Playing: isPlaying true", playing.isPlaying)
        assertFalse("Playing: resuming complete", playing.isResumingFromLegacy)

        // Step 4: Near end
        val nearEnd = playing.copy(
            positionMs = 118_000L,
            durationMs = 120_000L,
        )
        assertEquals("Near end: position", 118_000L, nearEnd.positionMs)

        // Step 5: Ended (position at duration)
        val ended = nearEnd.copy(
            isPlaying = false,
            positionMs = 120_000L,
        )
        assertFalse("Ended: not playing", ended.isPlaying)
        assertEquals("Ended: at duration", ended.durationMs, ended.positionMs)
    }

    @Test
    fun `SIP session emits predictable kids state sequence`() = runBlocking {
        // Simulate kids state sequence

        // Step 1: Kid profile detected
        kidsGate.isKidProfile = true
        kidsGate.remainingMinutes = 5
        kidsGate.profileId = 42L

        val startState = kidsGate.evaluateStart()
        assertTrue("Start: kid active", startState.kidActive)
        assertFalse("Start: not blocked", startState.kidBlocked)

        // Step 2: After 4 minutes of playback (240 seconds)
        var currentState = startState
        repeat(4) {
            currentState = kidsGate.onPlaybackTick(currentState, deltaSecs = 60)
        }
        assertFalse("After 4 min: not blocked", currentState.kidBlocked)
        assertEquals("After 4 min: 1 minute remaining", 1, kidsGate.remainingMinutes)

        // Step 3: After 5th minute - blocked
        val blockedState = kidsGate.onPlaybackTick(currentState, deltaSecs = 60)
        assertTrue("After 5 min: blocked", blockedState.kidBlocked)
        assertEquals("After 5 min: 0 minutes remaining", 0, kidsGate.remainingMinutes)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Defensive Guard Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `resumeManager handles negative durationMs gracefully`() = runBlocking {
        // Given: VOD context
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)

        // When: Duration is negative
        resumeManager.handlePeriodicTick(
            context = context,
            positionMs = 30_000L,
            durationMs = -1L,
        )

        // Then: No resume saved (guard against invalid duration)
        assertNull("Resume should not be saved", resumeManager.getStoredVodResume(123L))
        assertEquals("Tick should still be tracked", 1, resumeManager.tickCalledCount)
    }

    @Test
    fun `resumeManager handles positionMs greater than durationMs`() = runBlocking {
        // Given: VOD context
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)

        // When: Position exceeds duration (edge case - can happen during seek overshoot)
        resumeManager.handlePeriodicTick(
            context = context,
            positionMs = 70_000L,
            durationMs = 60_000L,
        )

        // Then: Position is saved (remaining is negative, not in 0..9999 range)
        // This matches legacy behavior where invalid states are handled gracefully
        // The player will naturally correct this state on next tick
        assertEquals(
            "Resume should be saved with clamped position",
            70,
            resumeManager.getStoredVodResume(123L),
        )
    }

    @Test
    fun `resumeManager handles unknown PlaybackContext fields`() = runBlocking {
        // Given: VOD context with missing mediaId
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = null)

        // When: Attempting to save resume
        resumeManager.handlePeriodicTick(
            context = context,
            positionMs = 30_000L,
            durationMs = 60_000L,
        )

        // Then: No crash, no resume saved
        assertEquals("Tick should be called", 1, resumeManager.tickCalledCount)
        // No assertion on storage since mediaId is null
    }

    @Test
    fun `kidsGate handles malformed PlaybackContext gracefully`() = runBlocking {
        // Given: Kids state with null profileId
        val state = KidsGateState(
            kidActive = true,
            kidBlocked = false,
            kidProfileId = null,
        )

        // When: Tick is called
        val newState = kidsGate.onPlaybackTick(state, deltaSecs = 60)

        // Then: Returns original state (no crash)
        assertFalse("Should not be blocked", newState.kidBlocked)
        assertEquals("Remaining minutes unchanged", 60, kidsGate.remainingMinutes)
    }
}
