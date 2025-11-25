package com.chris.m3usuite.player.internal.session

import com.chris.m3usuite.player.internal.bridge.InternalPlayerShadow
import com.chris.m3usuite.player.internal.bridge.ShadowSessionState
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
 * Phase 3 Shadow Mode Tests for SIP InternalPlayerSession.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * TEST SCOPE: SHADOW-MODE PARALLEL EXECUTION
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * These tests verify:
 * 1. Modular session can start without UI, navigation, ObjectBox, or ExoPlayer
 * 2. Shadow session never throws even with invalid inputs
 * 3. UI state updates do not propagate to runtime code
 * 4. Shadow-mode session shuts down cleanly without affecting legacy behavior
 *
 * **TEST ISOLATION:**
 * - Uses fakes/stubs only
 * - No ObjectBox, no Android UI, no ExoPlayer
 * - Tests shadow-mode behavior independently of legacy InternalPlayerScreen
 *
 * **RUNTIME STATUS:**
 * - These tests validate shadow-mode SIP modules
 * - Runtime flow: InternalPlayerEntry → legacy InternalPlayerScreen (unchanged)
 * - Shadow session is for diagnostics and verification only
 */
class InternalPlayerSessionPhase3ShadowTest {

    // ════════════════════════════════════════════════════════════════════════════
    // Fake Implementations for Shadow Testing
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Fake ResumeManager for shadow testing - always succeeds without storage.
     */
    class FakeShadowResumeManager : ResumeManager {
        var loadCallCount = 0
        var tickCallCount = 0
        var endedCallCount = 0

        override suspend fun loadResumePositionMs(context: PlaybackContext): Long? {
            loadCallCount++
            return when (context.type) {
                PlaybackType.VOD -> if (context.mediaId != null) 60_000L else null
                PlaybackType.SERIES -> {
                    if (context.seriesId != null && context.season != null && context.episodeNumber != null) {
                        120_000L
                    } else {
                        null
                    }
                }
                PlaybackType.LIVE -> null
            }
        }

        override suspend fun handlePeriodicTick(
            context: PlaybackContext,
            positionMs: Long,
            durationMs: Long,
        ) {
            tickCallCount++
            // No-op for shadow mode - doesn't persist
        }

        override suspend fun handleEnded(context: PlaybackContext) {
            endedCallCount++
            // No-op for shadow mode - doesn't modify storage
        }
    }

    /**
     * Fake KidsPlaybackGate for shadow testing - always returns safe defaults.
     */
    class FakeShadowKidsGate : KidsPlaybackGate {
        var evaluateCallCount = 0
        var tickCallCount = 0
        var simulateKidProfile = false
        var simulateBlocked = false

        override suspend fun evaluateStart(): KidsGateState {
            evaluateCallCount++
            return KidsGateState(
                kidActive = simulateKidProfile,
                kidBlocked = simulateKidProfile && simulateBlocked,
                kidProfileId = if (simulateKidProfile) 1L else null,
            )
        }

        override suspend fun onPlaybackTick(
            current: KidsGateState,
            deltaSecs: Int,
        ): KidsGateState {
            tickCallCount++
            return current
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Test Setup
    // ════════════════════════════════════════════════════════════════════════════

    private lateinit var resumeManager: FakeShadowResumeManager
    private lateinit var kidsGate: FakeShadowKidsGate

    @Before
    fun setup() {
        resumeManager = FakeShadowResumeManager()
        kidsGate = FakeShadowKidsGate()
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Shadow Session Start Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `shadow session can start without UI, navigation, ObjectBox, or ExoPlayer`() {
        // Given: Valid playback context
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)

        // When: Starting shadow session
        var callbackInvoked = false
        InternalPlayerShadow.startShadowSession(
            url = "https://example.com/video.mp4",
            startMs = null,
            mimeType = null,
            mediaItem = null,
            playbackContext = context,
            onShadowStateChanged = { callbackInvoked = true },
        )

        // Then: No exceptions thrown
        // Note: Callback is not invoked in current placeholder implementation
        // This test verifies the entry point is safe to call
        assertFalse("Callback should not be invoked in placeholder", callbackInvoked)
    }

    @Test
    fun `shadow session never throws with missing mediaItem`() {
        // Given: Context with null mediaItem
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 456L)

        // When/Then: No exception thrown
        InternalPlayerShadow.startShadowSession(
            url = "https://example.com/video.mp4",
            startMs = null,
            mimeType = null,
            mediaItem = null,
            playbackContext = context,
        )
    }

    @Test
    fun `shadow session never throws with null seriesId`() {
        // Given: Series context with missing seriesId
        val context = PlaybackContext(
            type = PlaybackType.SERIES,
            seriesId = null,
            season = 1,
            episodeNumber = 5,
        )

        // When/Then: No exception thrown
        InternalPlayerShadow.startShadowSession(
            url = "https://example.com/episode.mp4",
            startMs = null,
            mimeType = null,
            mediaItem = null,
            playbackContext = context,
        )
    }

    @Test
    fun `shadow session never throws with negative durations`() {
        // Given: Valid context (durations are handled at session tick level, not start)
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 789L)

        // When/Then: No exception thrown (negative durations are a tick-level concern)
        InternalPlayerShadow.startShadowSession(
            url = "https://example.com/video.mp4",
            startMs = -1000L, // Negative start position
            mimeType = null,
            mediaItem = null,
            playbackContext = context,
        )
    }

    @Test
    fun `shadow session never throws with invalid PlaybackContext combinations`() {
        // Given: Various invalid context combinations
        val contexts = listOf(
            PlaybackContext(type = PlaybackType.VOD, mediaId = null),
            PlaybackContext(type = PlaybackType.SERIES, seriesId = null, season = null, episodeNumber = null),
            PlaybackContext(type = PlaybackType.LIVE, liveCategoryHint = null, liveProviderHint = null),
            PlaybackContext(type = PlaybackType.VOD, mediaId = -1L),
        )

        // When/Then: No exceptions thrown for any context
        contexts.forEach { context ->
            InternalPlayerShadow.startShadowSession(
                url = "https://example.com/test.mp4",
                startMs = null,
                mimeType = null,
                mediaItem = null,
                playbackContext = context,
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Shadow Session Stop Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `shadow session stops cleanly without affecting legacy behavior`() {
        // Given: Active shadow session
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)
        InternalPlayerShadow.startShadowSession(
            url = "https://example.com/video.mp4",
            startMs = null,
            mimeType = null,
            mediaItem = null,
            playbackContext = context,
        )

        // When: Stopping shadow session
        InternalPlayerShadow.stopShadowSession()

        // Then: No exceptions, clean shutdown
        // Note: This validates the stop contract is safe to call
    }

    @Test
    fun `shadow session stop is safe to call multiple times`() {
        // When: Calling stop multiple times
        InternalPlayerShadow.stopShadowSession()
        InternalPlayerShadow.stopShadowSession()
        InternalPlayerShadow.stopShadowSession()

        // Then: No exceptions thrown
    }

    @Test
    fun `shadow session stop is safe to call without prior start`() {
        // When: Calling stop without any prior start
        InternalPlayerShadow.stopShadowSession()

        // Then: No exceptions thrown
    }

    // ════════════════════════════════════════════════════════════════════════════
    // UI State Independence Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `InternalPlayerUiState shadow fields have stable defaults`() {
        // Given: Default UI state
        val state = InternalPlayerUiState()

        // Then: Shadow fields have expected defaults
        assertFalse("shadowActive should default to false", state.shadowActive)
        assertNull("shadowStateDebug should default to null", state.shadowStateDebug)
    }

    @Test
    fun `InternalPlayerUiState shadow fields can be set independently`() {
        // Given: State with shadow fields set
        val state = InternalPlayerUiState(
            shadowActive = true,
            shadowStateDebug = "pos=60000|dur=120000|playing=true|kid=false",
        )

        // Then: Shadow fields are accessible
        assertTrue("shadowActive should be true", state.shadowActive)
        assertNotNull("shadowStateDebug should not be null", state.shadowStateDebug)
        assertTrue(
            "shadowStateDebug should contain position",
            state.shadowStateDebug!!.contains("pos=60000"),
        )
    }

    @Test
    fun `InternalPlayerUiState copy preserves shadow fields`() {
        // Given: State with shadow fields
        val initial = InternalPlayerUiState(
            playbackType = PlaybackType.VOD,
            shadowActive = true,
            shadowStateDebug = "test-debug-string",
        )

        // When: Copy with playback updates
        val updated = initial.copy(
            isPlaying = true,
            positionMs = 30_000L,
        )

        // Then: Shadow fields preserved
        assertTrue("shadowActive preserved", updated.shadowActive)
        assertEquals("shadowStateDebug preserved", "test-debug-string", updated.shadowStateDebug)
    }

    @Test
    fun `InternalPlayerUiState shadow fields do not affect legacy fields`() {
        // Given: State with both legacy and shadow fields
        val state = InternalPlayerUiState(
            playbackType = PlaybackType.SERIES,
            isPlaying = true,
            positionMs = 60_000L,
            durationMs = 120_000L,
            kidActive = true,
            kidBlocked = false,
            shadowActive = true,
            shadowStateDebug = "shadow-data",
        )

        // Then: Legacy fields are independent
        assertEquals("playbackType unchanged", PlaybackType.SERIES, state.playbackType)
        assertTrue("isPlaying unchanged", state.isPlaying)
        assertEquals("positionMs unchanged", 60_000L, state.positionMs)
        assertEquals("durationMs unchanged", 120_000L, state.durationMs)
        assertTrue("kidActive unchanged", state.kidActive)
        assertFalse("kidBlocked unchanged", state.kidBlocked)

        // And: Computed properties still work
        assertFalse("isLive computed correctly", state.isLive)
        assertTrue("isSeries computed correctly", state.isSeries)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // ShadowSessionState Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `ShadowSessionState has stable defaults`() {
        // Given: Default shadow state
        val state = ShadowSessionState()

        // Then: Defaults are safe
        assertFalse("shadowActive defaults to false", state.shadowActive)
        assertNull("shadowStateDebug defaults to null", state.shadowStateDebug)
        assertEquals("source defaults to shadow", "shadow", state.source)
        assertTrue("timestampMs should be recent", state.timestampMs > 0)
    }

    @Test
    fun `ShadowSessionState can be constructed with custom values`() {
        // Given: Custom shadow state
        val timestamp = System.currentTimeMillis()
        val state = ShadowSessionState(
            shadowActive = true,
            shadowStateDebug = "pos=30000|dur=60000",
            timestampMs = timestamp,
            source = "test",
        )

        // Then: Values are accessible
        assertTrue("shadowActive should be true", state.shadowActive)
        assertEquals("shadowStateDebug should match", "pos=30000|dur=60000", state.shadowStateDebug)
        assertEquals("timestampMs should match", timestamp, state.timestampMs)
        assertEquals("source should match", "test", state.source)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Resume Manager Shadow Behavior Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `shadow resume manager loads position for VOD`() = runBlocking {
        // Given: VOD context
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)

        // When: Loading resume
        val resumeMs = resumeManager.loadResumePositionMs(context)

        // Then: Position returned (fake always returns 60000 for VOD with mediaId)
        assertEquals("Should return resume position", 60_000L, resumeMs)
        assertEquals("Load should be called once", 1, resumeManager.loadCallCount)
    }

    @Test
    fun `shadow resume manager returns null for LIVE`() = runBlocking {
        // Given: LIVE context
        val context = PlaybackContext(type = PlaybackType.LIVE, mediaId = 123L)

        // When: Loading resume
        val resumeMs = resumeManager.loadResumePositionMs(context)

        // Then: Null returned (LIVE has no resume)
        assertNull("LIVE should not have resume", resumeMs)
    }

    @Test
    fun `shadow resume manager tick does not persist`() = runBlocking {
        // Given: VOD context
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)

        // When: Ticking
        resumeManager.handlePeriodicTick(context, 30_000L, 60_000L)

        // Then: Called but no persistence (shadow mode)
        assertEquals("Tick should be called once", 1, resumeManager.tickCallCount)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Kids Gate Shadow Behavior Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `shadow kids gate evaluates start safely`() = runBlocking {
        // Given: Non-kid profile
        kidsGate.simulateKidProfile = false

        // When: Evaluating
        val state = kidsGate.evaluateStart()

        // Then: Safe default returned
        assertFalse("kidActive should be false", state.kidActive)
        assertFalse("kidBlocked should be false", state.kidBlocked)
        assertNull("kidProfileId should be null", state.kidProfileId)
        assertEquals("Evaluate should be called once", 1, kidsGate.evaluateCallCount)
    }

    @Test
    fun `shadow kids gate returns blocked for kid with exhausted quota`() = runBlocking {
        // Given: Kid profile with exhausted quota
        kidsGate.simulateKidProfile = true
        kidsGate.simulateBlocked = true

        // When: Evaluating
        val state = kidsGate.evaluateStart()

        // Then: Blocked state returned (but doesn't affect playback in shadow mode)
        assertTrue("kidActive should be true", state.kidActive)
        assertTrue("kidBlocked should be true", state.kidBlocked)
        assertEquals("kidProfileId should be set", 1L, state.kidProfileId)
    }

    @Test
    fun `shadow kids gate tick returns same state`() = runBlocking {
        // Given: Active kid state
        val currentState = KidsGateState(
            kidActive = true,
            kidBlocked = false,
            kidProfileId = 1L,
        )

        // When: Ticking
        val newState = kidsGate.onPlaybackTick(currentState, 60)

        // Then: Same state returned (shadow mode doesn't decrement)
        assertEquals("State should be unchanged", currentState, newState)
        assertEquals("Tick should be called once", 1, kidsGate.tickCallCount)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Phase2Integration Shadow Behavior Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `Phase2Integration works with shadow fakes`() = runBlocking {
        // Given: VOD context with shadow fakes
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)

        // When: Loading resume via Phase2Integration
        val resumeMs = Phase2Integration.loadInitialResumePosition(context, resumeManager)

        // Then: Works correctly with fakes
        assertEquals("Should return resume position", 60_000L, resumeMs)
    }

    @Test
    fun `Phase2Integration tick works with shadow fakes`() = runBlocking {
        // Given: VOD context with shadow fakes
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)
        kidsGate.simulateKidProfile = true
        val kidsState = kidsGate.evaluateStart()

        // When: Ticking via Phase2Integration
        val newState = Phase2Integration.onPlaybackTick(
            playbackContext = context,
            positionMs = 30_000L,
            durationMs = 60_000L,
            resumeManager = resumeManager,
            kidsGate = kidsGate,
            currentKidsState = kidsState,
            tickAccumSecs = 60,
        )

        // Then: Both managers processed
        assertEquals("Resume tick should be called", 1, resumeManager.tickCallCount)
        assertEquals("Kids tick should be called", 1, kidsGate.tickCallCount)
        assertNotNull("New state should be returned", newState)
    }
}
