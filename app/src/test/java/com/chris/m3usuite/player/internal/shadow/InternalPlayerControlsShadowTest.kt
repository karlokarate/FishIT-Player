package com.chris.m3usuite.player.internal.shadow

import com.chris.m3usuite.player.internal.bridge.InternalPlayerShadow
import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.state.AspectRatioMode
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Phase 3 Step 3: Controls Shadow Mode.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * TEST SCOPE: CONTROLS SHADOW MODE DIAGNOSTICS
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * These tests verify:
 * 1. Controls evaluation never throws under edge cases (null/empty/invalid state)
 * 2. Diagnostic strings are correctly emitted for various control states
 * 3. No modification to InternalPlayerUiState
 * 4. No interaction with legacy state or actual UI components
 * 5. InternalPlayerShadow integration works correctly
 *
 * **TEST ISOLATION:**
 * - Uses fakes/stubs only
 * - No ObjectBox, no Android UI, no ExoPlayer
 * - Tests control shadow behavior independently of runtime
 *
 * **RUNTIME STATUS:**
 * - These tests validate Phase 3 Step 3 controls shadow mode
 * - Runtime flow: InternalPlayerEntry → legacy InternalPlayerScreen (unchanged)
 * - Controls shadow is for diagnostics and verification only
 */
class InternalPlayerControlsShadowTest {

    // ════════════════════════════════════════════════════════════════════════════
    // Safety / Edge Case Tests - Must Never Throw
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `evaluateControls never throws with default state`() {
        // Given: Default UI state
        val state = InternalPlayerUiState()

        // When/Then: No exception thrown
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: At least the evaluation marker is emitted
        assertTrue("Should emit diagnostics", diagnostics.isNotEmpty())
        assertTrue(
            "Should emit evaluation marker",
            diagnostics.contains("shadow-controls-evaluated"),
        )
    }

    @Test
    fun `evaluateControls never throws with null callback`() {
        // Given: State with some fields set
        val state = InternalPlayerUiState(
            isPlaying = true,
            positionMs = 60_000L,
            durationMs = 120_000L,
        )

        // When/Then: No exception thrown with null callback
        InternalPlayerControlsShadow.evaluateControls(state, null)
    }

    @Test
    fun `evaluateControls never throws with null aspectRatioMode fields`() {
        // Given: State with default aspectRatioMode (FIT)
        val state = InternalPlayerUiState(
            aspectRatioMode = AspectRatioMode.FIT,
        )

        // When/Then: No exception thrown
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: Aspect ratio diagnostic emitted
        assertTrue(
            "Should emit aspect ratio diagnostic",
            diagnostics.any { it.contains("aspectRatioMode") },
        )
    }

    @Test
    fun `evaluateControls never throws with missing subtitle flags`() {
        // Given: State with tracks dialog hidden (default)
        val state = InternalPlayerUiState(
            showTracksDialog = false,
        )

        // When/Then: No exception thrown
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: No subtitle menu diagnostic (it's closed)
        assertFalse(
            "Should not emit subtitle menu opened",
            diagnostics.any { it.contains("subtitle/tracks menu: opened") },
        )
    }

    @Test
    fun `evaluateControls never throws with impossible trickplay combination - active with no speed`() {
        // Given: State with zero speed (impossible/invalid)
        val state = InternalPlayerUiState(
            playbackSpeed = 0f,
        )

        // When/Then: No exception thrown
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: Invalid speed diagnostic emitted
        assertTrue(
            "Should emit trickplay invalid speed diagnostic",
            diagnostics.any { it.contains("invalid speed") },
        )
    }

    @Test
    fun `evaluateControls never throws with negative speed`() {
        // Given: State with negative speed (invalid)
        val state = InternalPlayerUiState(
            playbackSpeed = -2f,
        )

        // When/Then: No exception thrown
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: Invalid speed diagnostic emitted
        assertTrue(
            "Should emit trickplay invalid speed diagnostic",
            diagnostics.any { it.contains("invalid speed") },
        )
    }

    @Test
    fun `evaluateControls never throws with all null optional fields`() {
        // Given: State with all optional fields null
        val state = InternalPlayerUiState(
            kidProfileId = null,
            sleepTimerRemainingMs = null,
            resumeStartMs = null,
            shadowStateDebug = null,
            currentPositionMs = null,
            comparisonDurationMs = null,
            playbackError = null,
        )

        // When/Then: No exception thrown
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: Evaluation succeeded
        assertTrue(
            "Should emit evaluation marker",
            diagnostics.contains("shadow-controls-evaluated"),
        )
    }

    @Test
    fun `evaluateControls never throws with extreme values`() {
        // Given: State with extreme values
        val state = InternalPlayerUiState(
            positionMs = Long.MAX_VALUE,
            durationMs = Long.MAX_VALUE,
            playbackSpeed = Float.MAX_VALUE,
            remainingKidsMinutes = Int.MAX_VALUE,
        )

        // When/Then: No exception thrown
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: Evaluation succeeded
        assertTrue(
            "Should emit evaluation marker",
            diagnostics.contains("shadow-controls-evaluated"),
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Diagnostic String Emission Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `evaluateControls emits playback state diagnostic`() {
        // Given: Playing state
        val state = InternalPlayerUiState(
            isPlaying = true,
            isBuffering = false,
        )

        // When: Evaluating
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: Playback state diagnostic emitted
        assertTrue(
            "Should emit playback state",
            diagnostics.any { it.contains("playback state: playing=true, buffering=false") },
        )
    }

    @Test
    fun `evaluateControls emits buffering state diagnostic`() {
        // Given: Buffering state
        val state = InternalPlayerUiState(
            isPlaying = false,
            isBuffering = true,
        )

        // When: Evaluating
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: Buffering state diagnostic emitted
        assertTrue(
            "Should emit buffering state",
            diagnostics.any { it.contains("playing=false, buffering=true") },
        )
    }

    @Test
    fun `evaluateControls emits trickplay active for fast-forward`() {
        // Given: Fast-forward speed
        val state = InternalPlayerUiState(
            playbackSpeed = 2f,
        )

        // When: Evaluating
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: Trickplay active diagnostic emitted
        assertTrue(
            "Should emit trickplay active",
            diagnostics.any { it.contains("trickplay active: speed=+2.0x") },
        )
    }

    @Test
    fun `evaluateControls emits trickplay active for slow-motion`() {
        // Given: Slow-motion speed
        val state = InternalPlayerUiState(
            playbackSpeed = 0.5f,
        )

        // When: Evaluating
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: Trickplay active diagnostic emitted
        assertTrue(
            "Should emit slow-motion trickplay",
            diagnostics.any { it.contains("trickplay active: speed=0.5x") },
        )
    }

    @Test
    fun `evaluateControls emits inactive trickplay for normal speed`() {
        // Given: Normal speed
        val state = InternalPlayerUiState(
            playbackSpeed = 1f,
        )

        // When: Evaluating
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: Inactive trickplay diagnostic emitted
        assertTrue(
            "Should emit trickplay inactive",
            diagnostics.any { it.contains("trickplay: inactive (normal speed)") },
        )
    }

    @Test
    fun `evaluateControls emits aspect ratio diagnostic`() {
        // Given: Various aspect ratio modes
        AspectRatioMode.entries.forEach { mode ->
            val state = InternalPlayerUiState(aspectRatioMode = mode)

            // When: Evaluating
            val diagnostics = mutableListOf<String>()
            InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

            // Then: Aspect ratio diagnostic emitted
            assertTrue(
                "Should emit aspectRatioMode: ${mode.name}",
                diagnostics.any { it.contains("aspectRatioMode: ${mode.name}") },
            )
        }
    }

    @Test
    fun `evaluateControls emits position diagnostic`() {
        // Given: State with position and duration
        val state = InternalPlayerUiState(
            positionMs = 12_000L,
            durationMs = 60_000L,
        )

        // When: Evaluating
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: Position diagnostic emitted
        assertTrue(
            "Should emit position diagnostic",
            diagnostics.any { it.contains("position: 12000ms / 60000ms") },
        )
    }

    @Test
    fun `evaluateControls emits seek preview diagnostic`() {
        // Given: State with different currentPositionMs (scrubbing)
        val state = InternalPlayerUiState(
            positionMs = 30_000L,
            currentPositionMs = 45_000L, // Scrubbing ahead
        )

        // When: Evaluating
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: Seek preview diagnostic emitted
        assertTrue(
            "Should emit seek preview diagnostic",
            diagnostics.any { it.contains("seekPreview requested at 45000ms") },
        )
    }

    @Test
    fun `evaluateControls emits subtitle menu opened diagnostic`() {
        // Given: Tracks dialog shown
        val state = InternalPlayerUiState(
            showTracksDialog = true,
        )

        // When: Evaluating
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: Subtitle menu diagnostic emitted
        assertTrue(
            "Should emit subtitle menu opened",
            diagnostics.any { it.contains("subtitle/tracks menu: opened") },
        )
    }

    @Test
    fun `evaluateControls emits speed dialog opened diagnostic`() {
        // Given: Speed dialog shown
        val state = InternalPlayerUiState(
            showSpeedDialog = true,
        )

        // When: Evaluating
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: Speed dialog diagnostic emitted
        assertTrue(
            "Should emit speed dialog opened",
            diagnostics.any { it.contains("speed dialog: opened") },
        )
    }

    @Test
    fun `evaluateControls emits settings dialog opened diagnostic`() {
        // Given: Settings dialog shown
        val state = InternalPlayerUiState(
            showSettingsDialog = true,
        )

        // When: Evaluating
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: Settings dialog diagnostic emitted
        assertTrue(
            "Should emit settings dialog opened",
            diagnostics.any { it.contains("settings dialog: opened") },
        )
    }

    @Test
    fun `evaluateControls emits loop mode enabled diagnostic`() {
        // Given: Loop mode enabled
        val state = InternalPlayerUiState(
            isLooping = true,
        )

        // When: Evaluating
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: Loop mode diagnostic emitted
        assertTrue(
            "Should emit loop mode enabled",
            diagnostics.any { it.contains("loop mode: enabled") },
        )
    }

    @Test
    fun `evaluateControls emits debug overlay visible diagnostic`() {
        // Given: Debug overlay shown
        val state = InternalPlayerUiState(
            showDebugInfo = true,
        )

        // When: Evaluating
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: Debug overlay diagnostic emitted
        assertTrue(
            "Should emit debug overlay visible",
            diagnostics.any { it.contains("debug overlay: visible") },
        )
    }

    @Test
    fun `evaluateControls emits sleep timer active diagnostic`() {
        // Given: Sleep timer active
        val state = InternalPlayerUiState(
            sleepTimerRemainingMs = 300_000L,
        )

        // When: Evaluating
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: Sleep timer diagnostic emitted
        assertTrue(
            "Should emit sleep timer active",
            diagnostics.any { it.contains("sleep timer active: 300000ms remaining") },
        )
    }

    @Test
    fun `evaluateControls emits kids gate diagnostic`() {
        // Given: Kids gate active
        val state = InternalPlayerUiState(
            kidActive = true,
            kidBlocked = false,
            kidProfileId = 123L,
            remainingKidsMinutes = 15,
        )

        // When: Evaluating
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: Kids gate diagnostic emitted
        assertTrue(
            "Should emit kids gate active",
            diagnostics.any {
                it.contains("kids gate: active") &&
                    it.contains("profileId=123") &&
                    it.contains("blocked=false") &&
                    it.contains("remaining=15min")
            },
        )
    }

    @Test
    fun `evaluateControls emits kids gate blocked diagnostic`() {
        // Given: Kids gate blocked
        val state = InternalPlayerUiState(
            kidActive = true,
            kidBlocked = true,
            kidProfileId = 456L,
        )

        // When: Evaluating
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: Kids gate blocked diagnostic emitted
        assertTrue(
            "Should emit kids gate blocked",
            diagnostics.any { it.contains("kids gate: active") && it.contains("blocked=true") },
        )
    }

    @Test
    fun `evaluateControls emits resume state diagnostic`() {
        // Given: Resuming state
        val state = InternalPlayerUiState(
            isResumingFromLegacy = true,
            resumeStartMs = 45_000L,
        )

        // When: Evaluating
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: Resume diagnostic emitted
        assertTrue(
            "Should emit resuming diagnostic",
            diagnostics.any { it.contains("resuming from: 45000ms") },
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // No Modification / Immutability Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `evaluateControls does not modify UiState`() {
        // Given: State with various fields
        val state = InternalPlayerUiState(
            isPlaying = true,
            positionMs = 30_000L,
            durationMs = 60_000L,
            playbackSpeed = 2f,
            isLooping = true,
            kidActive = true,
            kidBlocked = false,
            aspectRatioMode = AspectRatioMode.FILL,
        )

        // When: Evaluating
        InternalPlayerControlsShadow.evaluateControls(state) { /* no-op */ }

        // Then: State is unchanged (immutable data class)
        assertTrue("isPlaying unchanged", state.isPlaying)
        assertEquals("positionMs unchanged", 30_000L, state.positionMs)
        assertEquals("durationMs unchanged", 60_000L, state.durationMs)
        assertEquals("playbackSpeed unchanged", 2f, state.playbackSpeed)
        assertTrue("isLooping unchanged", state.isLooping)
        assertTrue("kidActive unchanged", state.kidActive)
        assertFalse("kidBlocked unchanged", state.kidBlocked)
        assertEquals("aspectRatioMode unchanged", AspectRatioMode.FILL, state.aspectRatioMode)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // InternalPlayerShadow Integration Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `InternalPlayerShadow evaluateControlsInShadowMode works correctly`() {
        // Given: State with various fields
        val state = InternalPlayerUiState(
            isPlaying = true,
            playbackSpeed = 1.5f,
            aspectRatioMode = AspectRatioMode.ZOOM,
        )

        // When: Using InternalPlayerShadow utility
        val diagnostics = mutableListOf<String>()
        InternalPlayerShadow.evaluateControlsInShadowMode(state) { diagnostics.add(it) }

        // Then: Diagnostics emitted
        assertTrue("Should emit evaluation marker", diagnostics.contains("shadow-controls-evaluated"))
        assertTrue("Should emit playback state", diagnostics.any { it.contains("playing=true") })
        assertTrue("Should emit trickplay", diagnostics.any { it.contains("trickplay active") })
        assertTrue("Should emit aspect ratio", diagnostics.any { it.contains("aspectRatioMode: ZOOM") })
    }

    @Test
    fun `InternalPlayerShadow evaluateControlsInShadowMode is safe with null callback`() {
        // Given: State
        val state = InternalPlayerUiState(isPlaying = true)

        // When/Then: No exception thrown with null callback
        InternalPlayerShadow.evaluateControlsInShadowMode(state, null)
    }

    @Test
    fun `startShadowSession accepts onShadowControlsDiagnostic parameter`() {
        // Given: Session parameters with controls diagnostic callback
        var diagnosticReceived = false

        // When: Starting session with callback (placeholder - won't invoke yet)
        InternalPlayerShadow.startShadowSession(
            url = "https://example.com/video.mp4",
            startMs = null,
            mimeType = null,
            mediaItem = null,
            playbackContext = com.chris.m3usuite.player.internal.domain.PlaybackContext(
                type = PlaybackType.VOD,
                mediaId = 123L,
            ),
            onShadowStateChanged = null,
            onShadowComparison = null,
            onShadowControlsDiagnostic = { diagnosticReceived = true },
        )

        // Then: No exception thrown (callback not invoked in placeholder implementation)
        // Note: This test validates the parameter acceptance, not invocation
        assertFalse(
            "Placeholder should not invoke callback",
            diagnosticReceived,
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // No Linkage to Actual UI Components Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `evaluateControls does not require Android Context`() {
        // Given: State
        val state = InternalPlayerUiState()

        // When/Then: No Android context needed, no exception
        InternalPlayerControlsShadow.evaluateControls(state) { /* no-op */ }
    }

    @Test
    fun `evaluateControls works without Composable context`() {
        // Given: State
        val state = InternalPlayerUiState()

        // When/Then: No Composable context needed, no exception
        val diagnostics = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics.add(it) }

        // Then: Works in pure JUnit context
        assertTrue("Works without Composable", diagnostics.isNotEmpty())
    }

    @Test
    fun `evaluateControls is pure function with no side effects`() {
        // Given: Same state
        val state = InternalPlayerUiState(
            isPlaying = true,
            playbackSpeed = 2f,
        )

        // When: Called multiple times
        val diagnostics1 = mutableListOf<String>()
        val diagnostics2 = mutableListOf<String>()
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics1.add(it) }
        InternalPlayerControlsShadow.evaluateControls(state) { diagnostics2.add(it) }

        // Then: Same output both times (pure function)
        assertEquals("Same diagnostic count", diagnostics1.size, diagnostics2.size)
        assertEquals("Same diagnostics", diagnostics1, diagnostics2)
    }
}
