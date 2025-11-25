package com.chris.m3usuite.player.internal.session

import com.chris.m3usuite.player.internal.bridge.InternalPlayerShadow
import com.chris.m3usuite.player.internal.domain.PlaybackContext
import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.shadow.ShadowComparisonService
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Phase 3 Step 2: Shadow Comparison Pipeline.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * TEST SCOPE: LEGACY ↔ SHADOW STATE COMPARISON
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * These tests verify:
 * 1. Resume parity detection between legacy and shadow states
 * 2. Kids-gate parity detection between legacy and shadow states
 * 3. Position offset calculation for drift detection
 * 4. ComparisonResult flags correctly indicate mismatches
 * 5. Callback invocation in shadow session works correctly
 * 6. No runtime path impact (comparison is diagnostics-only)
 *
 * **TEST ISOLATION:**
 * - Uses fakes/stubs only
 * - No ObjectBox, no Android UI, no ExoPlayer
 * - Tests comparison logic independently of actual player behavior
 *
 * **RUNTIME STATUS:**
 * - These tests validate Phase 3 Step 2 comparison pipeline
 * - Runtime flow: InternalPlayerEntry → legacy InternalPlayerScreen (unchanged)
 * - Comparison service is for diagnostics and verification only
 */
class InternalPlayerShadowComparisonTest {
    // ════════════════════════════════════════════════════════════════════════════
    // Resume Parity Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `resume parity is OK when both states have same resumeStartMs`() {
        // Given: Both states have the same resume start position
        val legacy = InternalPlayerUiState(resumeStartMs = 60_000L)
        val shadow = InternalPlayerUiState(resumeStartMs = 60_000L)

        // When: Comparing states
        val result = ShadowComparisonService.compare(legacy, shadow)

        // Then: Resume parity is OK
        assertTrue("Resume parity should be OK", result.resumeParityOk)
        assertFalse("Should not have resumeMismatch flag", result.flags.contains("resumeMismatch"))
    }

    @Test
    fun `resume parity is OK when both states have null resumeStartMs`() {
        // Given: Both states have null resume start position
        val legacy = InternalPlayerUiState(resumeStartMs = null)
        val shadow = InternalPlayerUiState(resumeStartMs = null)

        // When: Comparing states
        val result = ShadowComparisonService.compare(legacy, shadow)

        // Then: Resume parity is OK
        assertTrue("Resume parity should be OK for null values", result.resumeParityOk)
        assertFalse("Should not have resumeMismatch flag", result.flags.contains("resumeMismatch"))
    }

    @Test
    fun `resume parity detects mismatch when positions differ`() {
        // Given: States have different resume start positions
        val legacy = InternalPlayerUiState(resumeStartMs = 60_000L)
        val shadow = InternalPlayerUiState(resumeStartMs = 90_000L)

        // When: Comparing states
        val result = ShadowComparisonService.compare(legacy, shadow)

        // Then: Resume parity is NOT OK
        assertFalse("Resume parity should NOT be OK", result.resumeParityOk)
        assertTrue("Should have resumeMismatch flag", result.flags.contains("resumeMismatch"))
    }

    @Test
    fun `resume parity detects mismatch when one is null`() {
        // Given: One state has resume, other doesn't
        val legacy = InternalPlayerUiState(resumeStartMs = 60_000L)
        val shadow = InternalPlayerUiState(resumeStartMs = null)

        // When: Comparing states
        val result = ShadowComparisonService.compare(legacy, shadow)

        // Then: Resume parity is NOT OK
        assertFalse("Resume parity should NOT be OK when one is null", result.resumeParityOk)
        assertTrue("Should have resumeMismatch flag", result.flags.contains("resumeMismatch"))
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Kids-Gate Parity Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `kids-gate parity is OK when both states have same kidBlocked`() {
        // Given: Both states have the same kid blocked status
        val legacy = InternalPlayerUiState(kidBlocked = true)
        val shadow = InternalPlayerUiState(kidBlocked = true)

        // When: Comparing states
        val result = ShadowComparisonService.compare(legacy, shadow)

        // Then: Kids gate parity is OK
        assertTrue("Kids gate parity should be OK", result.kidsGateParityOk)
        assertFalse("Should not have kidsGateMismatch flag", result.flags.contains("kidsGateMismatch"))
    }

    @Test
    fun `kids-gate parity is OK when both states are not blocked`() {
        // Given: Both states have kid not blocked
        val legacy = InternalPlayerUiState(kidBlocked = false)
        val shadow = InternalPlayerUiState(kidBlocked = false)

        // When: Comparing states
        val result = ShadowComparisonService.compare(legacy, shadow)

        // Then: Kids gate parity is OK
        assertTrue("Kids gate parity should be OK", result.kidsGateParityOk)
        assertFalse("Should not have kidsGateMismatch flag", result.flags.contains("kidsGateMismatch"))
    }

    @Test
    fun `kids-gate parity detects mismatch when blocked status differs`() {
        // Given: States have different kid blocked status
        val legacy = InternalPlayerUiState(kidBlocked = true)
        val shadow = InternalPlayerUiState(kidBlocked = false)

        // When: Comparing states
        val result = ShadowComparisonService.compare(legacy, shadow)

        // Then: Kids gate parity is NOT OK
        assertFalse("Kids gate parity should NOT be OK", result.kidsGateParityOk)
        assertTrue("Should have kidsGateMismatch flag", result.flags.contains("kidsGateMismatch"))
    }

    @Test
    fun `kids-gate parity detects mismatch in opposite direction`() {
        // Given: States have different kid blocked status (reversed)
        val legacy = InternalPlayerUiState(kidBlocked = false)
        val shadow = InternalPlayerUiState(kidBlocked = true)

        // When: Comparing states
        val result = ShadowComparisonService.compare(legacy, shadow)

        // Then: Kids gate parity is NOT OK
        assertFalse("Kids gate parity should NOT be OK (reversed)", result.kidsGateParityOk)
        assertTrue("Should have kidsGateMismatch flag", result.flags.contains("kidsGateMismatch"))
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Position Offset Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `position offset is calculated correctly when both positions available`() {
        // Given: Both states have position values
        val legacy = InternalPlayerUiState(currentPositionMs = 60_000L)
        val shadow = InternalPlayerUiState(currentPositionMs = 58_000L)

        // When: Comparing states
        val result = ShadowComparisonService.compare(legacy, shadow)

        // Then: Offset is calculated correctly (legacy - shadow)
        assertEquals("Position offset should be 2000ms", 2000L, result.positionOffsetMs)
    }

    @Test
    fun `position offset is negative when shadow is ahead`() {
        // Given: Shadow position is ahead of legacy
        val legacy = InternalPlayerUiState(currentPositionMs = 58_000L)
        val shadow = InternalPlayerUiState(currentPositionMs = 60_000L)

        // When: Comparing states
        val result = ShadowComparisonService.compare(legacy, shadow)

        // Then: Offset is negative (legacy - shadow)
        assertEquals("Position offset should be -2000ms", -2000L, result.positionOffsetMs)
    }

    @Test
    fun `position offset is zero when positions match`() {
        // Given: Both positions are the same
        val legacy = InternalPlayerUiState(currentPositionMs = 60_000L)
        val shadow = InternalPlayerUiState(currentPositionMs = 60_000L)

        // When: Comparing states
        val result = ShadowComparisonService.compare(legacy, shadow)

        // Then: Offset is zero
        assertEquals("Position offset should be 0", 0L, result.positionOffsetMs)
    }

    @Test
    fun `position offset is null when legacy position is null`() {
        // Given: Legacy position is null
        val legacy = InternalPlayerUiState(currentPositionMs = null)
        val shadow = InternalPlayerUiState(currentPositionMs = 60_000L)

        // When: Comparing states
        val result = ShadowComparisonService.compare(legacy, shadow)

        // Then: Offset is null (cannot calculate)
        assertNull("Position offset should be null when legacy is null", result.positionOffsetMs)
    }

    @Test
    fun `position offset is null when shadow position is null`() {
        // Given: Shadow position is null
        val legacy = InternalPlayerUiState(currentPositionMs = 60_000L)
        val shadow = InternalPlayerUiState(currentPositionMs = null)

        // When: Comparing states
        val result = ShadowComparisonService.compare(legacy, shadow)

        // Then: Offset is null (cannot calculate)
        assertNull("Position offset should be null when shadow is null", result.positionOffsetMs)
    }

    @Test
    fun `position offset is null when both positions are null`() {
        // Given: Both positions are null
        val legacy = InternalPlayerUiState(currentPositionMs = null)
        val shadow = InternalPlayerUiState(currentPositionMs = null)

        // When: Comparing states
        val result = ShadowComparisonService.compare(legacy, shadow)

        // Then: Offset is null
        assertNull("Position offset should be null when both are null", result.positionOffsetMs)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // ComparisonResult Flags Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `flags list is empty when all parity checks pass`() {
        // Given: Both states are identical in checked fields
        val legacy =
            InternalPlayerUiState(
                resumeStartMs = 60_000L,
                kidBlocked = false,
            )
        val shadow =
            InternalPlayerUiState(
                resumeStartMs = 60_000L,
                kidBlocked = false,
            )

        // When: Comparing states
        val result = ShadowComparisonService.compare(legacy, shadow)

        // Then: No flags
        assertTrue("Flags should be empty when all parity checks pass", result.flags.isEmpty())
    }

    @Test
    fun `flags contain both mismatches when resume and kids differ`() {
        // Given: Both resume and kids status differ
        val legacy =
            InternalPlayerUiState(
                resumeStartMs = 60_000L,
                kidBlocked = false,
            )
        val shadow =
            InternalPlayerUiState(
                resumeStartMs = 90_000L,
                kidBlocked = true,
            )

        // When: Comparing states
        val result = ShadowComparisonService.compare(legacy, shadow)

        // Then: Both flags present
        assertEquals("Should have 2 flags", 2, result.flags.size)
        assertTrue("Should have resumeMismatch", result.flags.contains("resumeMismatch"))
        assertTrue("Should have kidsGateMismatch", result.flags.contains("kidsGateMismatch"))
    }

    @Test
    fun `flags contain only resume mismatch when only resume differs`() {
        // Given: Only resume differs
        val legacy =
            InternalPlayerUiState(
                resumeStartMs = 60_000L,
                kidBlocked = false,
            )
        val shadow =
            InternalPlayerUiState(
                resumeStartMs = 90_000L,
                kidBlocked = false,
            )

        // When: Comparing states
        val result = ShadowComparisonService.compare(legacy, shadow)

        // Then: Only resume flag present
        assertEquals("Should have 1 flag", 1, result.flags.size)
        assertTrue("Should have resumeMismatch", result.flags.contains("resumeMismatch"))
        assertFalse("Should not have kidsGateMismatch", result.flags.contains("kidsGateMismatch"))
    }

    @Test
    fun `flags contain only kids mismatch when only kids differs`() {
        // Given: Only kids status differs
        val legacy =
            InternalPlayerUiState(
                resumeStartMs = 60_000L,
                kidBlocked = false,
            )
        val shadow =
            InternalPlayerUiState(
                resumeStartMs = 60_000L,
                kidBlocked = true,
            )

        // When: Comparing states
        val result = ShadowComparisonService.compare(legacy, shadow)

        // Then: Only kids flag present
        assertEquals("Should have 1 flag", 1, result.flags.size)
        assertFalse("Should not have resumeMismatch", result.flags.contains("resumeMismatch"))
        assertTrue("Should have kidsGateMismatch", result.flags.contains("kidsGateMismatch"))
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Callback Invocation Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `invokeComparison invokes callback with comparison result`() {
        // Given: States with known differences
        val legacy =
            InternalPlayerUiState(
                resumeStartMs = 60_000L,
                kidBlocked = false,
                currentPositionMs = 30_000L,
            )
        val shadow =
            InternalPlayerUiState(
                resumeStartMs = 90_000L, // Different
                kidBlocked = false,
                currentPositionMs = 28_000L, // Different
            )

        // When: Invoking comparison with callback
        var receivedResult: ShadowComparisonService.ComparisonResult? = null
        InternalPlayerShadow.invokeComparison(
            legacyState = legacy,
            shadowState = shadow,
            callback = { result -> receivedResult = result },
        )

        // Then: Callback received correct result
        assertNotNull("Callback should be invoked", receivedResult)
        assertFalse("Resume parity should be false", receivedResult!!.resumeParityOk)
        assertTrue("Kids parity should be true", receivedResult!!.kidsGateParityOk)
        assertEquals("Position offset should be 2000ms", 2000L, receivedResult!!.positionOffsetMs)
        assertTrue("Should have resumeMismatch flag", receivedResult!!.flags.contains("resumeMismatch"))
    }

    @Test
    fun `invokeComparison is safe with null callback`() {
        // Given: States
        val legacy = InternalPlayerUiState(resumeStartMs = 60_000L)
        val shadow = InternalPlayerUiState(resumeStartMs = 90_000L)

        // When: Invoking comparison with null callback
        // Then: No exception thrown
        InternalPlayerShadow.invokeComparison(
            legacyState = legacy,
            shadowState = shadow,
            callback = null,
        )
    }

    @Test
    fun `invokeComparison works with fully matching states`() {
        // Given: Identical states
        val legacy =
            InternalPlayerUiState(
                resumeStartMs = 60_000L,
                kidBlocked = false,
                currentPositionMs = 30_000L,
            )
        val shadow =
            InternalPlayerUiState(
                resumeStartMs = 60_000L,
                kidBlocked = false,
                currentPositionMs = 30_000L,
            )

        // When: Invoking comparison
        var receivedResult: ShadowComparisonService.ComparisonResult? = null
        InternalPlayerShadow.invokeComparison(
            legacyState = legacy,
            shadowState = shadow,
            callback = { result -> receivedResult = result },
        )

        // Then: All parity checks pass
        assertNotNull("Callback should be invoked", receivedResult)
        assertTrue("Resume parity should be true", receivedResult!!.resumeParityOk)
        assertTrue("Kids parity should be true", receivedResult!!.kidsGateParityOk)
        assertEquals("Position offset should be 0", 0L, receivedResult!!.positionOffsetMs)
        assertTrue("Flags should be empty", receivedResult!!.flags.isEmpty())
    }

    // ════════════════════════════════════════════════════════════════════════════
    // No Runtime Path Impact Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `comparison does not modify legacy state`() {
        // Given: Legacy state
        val legacy =
            InternalPlayerUiState(
                resumeStartMs = 60_000L,
                kidBlocked = false,
                isPlaying = true,
                positionMs = 30_000L,
            )
        val shadow =
            InternalPlayerUiState(
                resumeStartMs = 90_000L,
                kidBlocked = true,
            )

        // When: Comparing states
        ShadowComparisonService.compare(legacy, shadow)

        // Then: Legacy state is unchanged (immutable data class)
        assertEquals("resumeStartMs unchanged", 60_000L, legacy.resumeStartMs)
        assertFalse("kidBlocked unchanged", legacy.kidBlocked)
        assertTrue("isPlaying unchanged", legacy.isPlaying)
        assertEquals("positionMs unchanged", 30_000L, legacy.positionMs)
    }

    @Test
    fun `comparison does not modify shadow state`() {
        // Given: Shadow state
        val legacy =
            InternalPlayerUiState(
                resumeStartMs = 60_000L,
                kidBlocked = false,
            )
        val shadow =
            InternalPlayerUiState(
                resumeStartMs = 90_000L,
                kidBlocked = true,
                isPlaying = false,
                positionMs = 45_000L,
            )

        // When: Comparing states
        ShadowComparisonService.compare(legacy, shadow)

        // Then: Shadow state is unchanged (immutable data class)
        assertEquals("resumeStartMs unchanged", 90_000L, shadow.resumeStartMs)
        assertTrue("kidBlocked unchanged", shadow.kidBlocked)
        assertFalse("isPlaying unchanged", shadow.isPlaying)
        assertEquals("positionMs unchanged", 45_000L, shadow.positionMs)
    }

    @Test
    fun `shadow session with comparison callback does not affect runtime`() {
        // Given: Shadow session with comparison callback
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)
        var comparisonInvoked = false

        // When: Starting shadow session
        InternalPlayerShadow.startShadowSession(
            url = "https://example.com/video.mp4",
            startMs = null,
            mimeType = null,
            mediaItem = null,
            playbackContext = context,
            onShadowStateChanged = null,
            onShadowComparison = { comparisonInvoked = true },
        )

        // Then: Session started without error (callback not invoked in placeholder)
        // Note: In the current placeholder implementation, the callback is not invoked
        // This test verifies that the callback parameter does not cause runtime issues
        assertFalse(
            "Comparison callback should not be invoked in placeholder",
            comparisonInvoked,
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // InternalPlayerUiState Comparison Fields Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `InternalPlayerUiState currentPositionMs defaults to null`() {
        // Given: Default state
        val state = InternalPlayerUiState()

        // Then: currentPositionMs is null
        assertNull("currentPositionMs should default to null", state.currentPositionMs)
    }

    @Test
    fun `InternalPlayerUiState comparisonDurationMs defaults to null`() {
        // Given: Default state
        val state = InternalPlayerUiState()

        // Then: comparisonDurationMs is null
        assertNull("comparisonDurationMs should default to null", state.comparisonDurationMs)
    }

    @Test
    fun `InternalPlayerUiState comparison fields can be set`() {
        // Given: State with comparison fields set
        val state =
            InternalPlayerUiState(
                currentPositionMs = 60_000L,
                comparisonDurationMs = 120_000L,
            )

        // Then: Values are accessible
        assertEquals("currentPositionMs should be set", 60_000L, state.currentPositionMs)
        assertEquals("comparisonDurationMs should be set", 120_000L, state.comparisonDurationMs)
    }

    @Test
    fun `InternalPlayerUiState comparison fields are independent from runtime fields`() {
        // Given: State with both runtime and comparison fields
        val state =
            InternalPlayerUiState(
                positionMs = 30_000L, // Runtime field
                durationMs = 60_000L, // Runtime field
                currentPositionMs = 35_000L, // Comparison field
                comparisonDurationMs = 65_000L, // Comparison field
            )

        // Then: Fields are independent
        assertEquals("positionMs unchanged", 30_000L, state.positionMs)
        assertEquals("durationMs unchanged", 60_000L, state.durationMs)
        assertEquals("currentPositionMs independent", 35_000L, state.currentPositionMs)
        assertEquals("comparisonDurationMs independent", 65_000L, state.comparisonDurationMs)
    }

    @Test
    fun `InternalPlayerUiState copy preserves comparison fields`() {
        // Given: State with comparison fields
        val initial =
            InternalPlayerUiState(
                currentPositionMs = 60_000L,
                comparisonDurationMs = 120_000L,
            )

        // When: Copy with runtime field update
        val updated = initial.copy(isPlaying = true)

        // Then: Comparison fields preserved
        assertEquals("currentPositionMs preserved", 60_000L, updated.currentPositionMs)
        assertEquals("comparisonDurationMs preserved", 120_000L, updated.comparisonDurationMs)
    }
}
