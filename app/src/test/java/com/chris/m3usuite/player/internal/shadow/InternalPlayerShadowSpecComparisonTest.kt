package com.chris.m3usuite.player.internal.shadow

import com.chris.m3usuite.player.internal.bridge.InternalPlayerShadow
import com.chris.m3usuite.player.internal.domain.PlaybackContext
import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Phase 3 Step 4: Spec-Driven Shadow Comparison.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * TEST SCOPE: SPEC-DRIVEN DIAGNOSTICS
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * These tests verify:
 * 1. SIP compliance with spec → classified correctly
 * 2. Legacy violations → correctly labeled as SpecPreferredSIP
 * 3. SIP violations → correctly labeled as SpecPreferredLegacy
 * 4. Both violate → BothViolateSpec
 * 5. Allowed discrepancies → DontCare
 * 6. All diagnostics survive malformed, null, or extreme state
 *
 * **KEY PRINCIPLE:**
 * No test forces SIP to mimic legacy bugs. Legacy is NOT the source of truth.
 * The Behavior Contract (docs/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md) defines correctness.
 *
 * **TEST ISOLATION:**
 * - Uses fakes/stubs only
 * - No ObjectBox, no Android UI, no ExoPlayer
 * - Tests comparison logic independently of actual player behavior
 */
class InternalPlayerShadowSpecComparisonTest {

    // ════════════════════════════════════════════════════════════════════════════
    // Resume Spec Comparison Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `resume - ExactMatch when both comply with spec`() {
        // Given: Both SIP and Legacy have valid resume positions (>10s, not near end)
        val legacy = InternalPlayerUiState(resumeStartMs = 60_000L)
        val sip = InternalPlayerUiState(resumeStartMs = 60_000L)
        val duration = 120_000L

        // When: Comparing against spec
        val result = ShadowComparisonService.compareResumeAgainstSpec(
            legacy = legacy,
            sip = sip,
            playbackType = PlaybackType.VOD,
            durationMs = duration,
        )

        // Then: ExactMatch
        assertEquals(ShadowComparisonService.ParityKind.ExactMatch, result.parityKind)
        assertEquals("resume", result.dimension)
    }

    @Test
    fun `resume - SpecPreferredSIP when SIP is correct and legacy violates 10s threshold`() {
        // Given: SIP has null resume (correct for position <=10s), Legacy incorrectly stored 5s
        val legacy = InternalPlayerUiState(resumeStartMs = 5_000L) // Violates: <=10s should not resume
        val sip = InternalPlayerUiState(resumeStartMs = null) // Correct: cleared

        // When: Comparing against spec
        val result = ShadowComparisonService.compareResumeAgainstSpec(
            legacy = legacy,
            sip = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 120_000L,
        )

        // Then: SpecPreferredSIP (SIP is correct, legacy violates spec)
        assertEquals(ShadowComparisonService.ParityKind.SpecPreferredSIP, result.parityKind)
        assertTrue("Should flag legacy bug", result.flags.contains("legacyBug:resumeThreshold"))
    }

    @Test
    fun `resume - SpecPreferredLegacy when SIP violates 10s threshold`() {
        // Given: Legacy has null resume (correct), SIP incorrectly stored 5s
        val legacy = InternalPlayerUiState(resumeStartMs = null) // Correct: cleared
        val sip = InternalPlayerUiState(resumeStartMs = 5_000L) // Violates: <=10s should not resume

        // When: Comparing against spec
        val result = ShadowComparisonService.compareResumeAgainstSpec(
            legacy = legacy,
            sip = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 120_000L,
        )

        // Then: SpecPreferredLegacy (Legacy is correct, SIP violates spec)
        assertEquals(ShadowComparisonService.ParityKind.SpecPreferredLegacy, result.parityKind)
        assertTrue("Should flag SIP violation", result.flags.contains("sipViolation:resumeThreshold"))
    }

    @Test
    fun `resume - BothViolateSpec when both store invalid resume`() {
        // Given: Both have resume <= 10s (both violate spec)
        val legacy = InternalPlayerUiState(resumeStartMs = 5_000L)
        val sip = InternalPlayerUiState(resumeStartMs = 8_000L)

        // When: Comparing against spec
        val result = ShadowComparisonService.compareResumeAgainstSpec(
            legacy = legacy,
            sip = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 120_000L,
        )

        // Then: BothViolateSpec
        assertEquals(ShadowComparisonService.ParityKind.BothViolateSpec, result.parityKind)
        assertTrue(result.flags.any { it.contains("bothViolate") })
    }

    @Test
    fun `resume - LIVE never resumes - SIP correct, legacy violates`() {
        // Given: LIVE content - SIP has null (correct), Legacy has resume (violates spec)
        val legacy = InternalPlayerUiState(resumeStartMs = 60_000L) // Violates: LIVE never resumes
        val sip = InternalPlayerUiState(resumeStartMs = null) // Correct

        // When: Comparing against spec for LIVE
        val result = ShadowComparisonService.compareResumeAgainstSpec(
            legacy = legacy,
            sip = sip,
            playbackType = PlaybackType.LIVE,
            durationMs = null,
        )

        // Then: SpecPreferredSIP
        assertEquals(ShadowComparisonService.ParityKind.SpecPreferredSIP, result.parityKind)
        assertTrue(result.specDetails.contains("LIVE"))
        assertTrue(result.flags.contains("legacyBug:liveResume"))
    }

    @Test
    fun `resume - LIVE never resumes - both correct with null`() {
        // Given: LIVE content - both have null (correct)
        val legacy = InternalPlayerUiState(resumeStartMs = null)
        val sip = InternalPlayerUiState(resumeStartMs = null)

        // When: Comparing against spec for LIVE
        val result = ShadowComparisonService.compareResumeAgainstSpec(
            legacy = legacy,
            sip = sip,
            playbackType = PlaybackType.LIVE,
            durationMs = null,
        )

        // Then: ExactMatch
        assertEquals(ShadowComparisonService.ParityKind.ExactMatch, result.parityKind)
    }

    @Test
    fun `resume - LIVE never resumes - both violate`() {
        // Given: LIVE content - both have resume (both violate spec)
        val legacy = InternalPlayerUiState(resumeStartMs = 60_000L)
        val sip = InternalPlayerUiState(resumeStartMs = 90_000L)

        // When: Comparing against spec for LIVE
        val result = ShadowComparisonService.compareResumeAgainstSpec(
            legacy = legacy,
            sip = sip,
            playbackType = PlaybackType.LIVE,
            durationMs = null,
        )

        // Then: BothViolateSpec
        assertEquals(ShadowComparisonService.ParityKind.BothViolateSpec, result.parityKind)
        assertTrue(result.flags.contains("bothViolate:liveResume"))
    }

    @Test
    fun `resume - DontCare when both compliant but different values`() {
        // Given: Both have valid resume positions (>10s), but different values
        val legacy = InternalPlayerUiState(resumeStartMs = 60_000L)
        val sip = InternalPlayerUiState(resumeStartMs = 65_000L)

        // When: Comparing against spec
        val result = ShadowComparisonService.compareResumeAgainstSpec(
            legacy = legacy,
            sip = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 120_000L,
        )

        // Then: DontCare (both compliant, minor difference allowed)
        assertEquals(ShadowComparisonService.ParityKind.DontCare, result.parityKind)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Kids Gate Spec Comparison Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `kids - ExactMatch when both comply with quota blocking rule`() {
        // Given: Both correctly blocked when quota <= 0
        val legacy = InternalPlayerUiState(kidActive = true, kidBlocked = true, remainingKidsMinutes = 0)
        val sip = InternalPlayerUiState(kidActive = true, kidBlocked = true, remainingKidsMinutes = 0)

        // When: Comparing against spec
        val result = ShadowComparisonService.compareKidsGateAgainstSpec(legacy, sip)

        // Then: ExactMatch
        assertEquals(ShadowComparisonService.ParityKind.ExactMatch, result.parityKind)
        assertEquals("kids", result.dimension)
    }

    @Test
    fun `kids - SpecPreferredSIP when SIP correctly blocks and legacy does not`() {
        // Given: SIP blocks when quota=0 (correct), Legacy doesn't block (violates spec)
        val legacy = InternalPlayerUiState(kidActive = true, kidBlocked = false, remainingKidsMinutes = 0)
        val sip = InternalPlayerUiState(kidActive = true, kidBlocked = true, remainingKidsMinutes = 0)

        // When: Comparing against spec
        val result = ShadowComparisonService.compareKidsGateAgainstSpec(legacy, sip)

        // Then: SpecPreferredSIP
        assertEquals(ShadowComparisonService.ParityKind.SpecPreferredSIP, result.parityKind)
        assertTrue(result.flags.contains("legacyBug:kidsBlockRule"))
    }

    @Test
    fun `kids - SpecPreferredLegacy when SIP incorrectly does not block`() {
        // Given: Legacy blocks when quota=0 (correct), SIP doesn't block (violates spec)
        val legacy = InternalPlayerUiState(kidActive = true, kidBlocked = true, remainingKidsMinutes = 0)
        val sip = InternalPlayerUiState(kidActive = true, kidBlocked = false, remainingKidsMinutes = 0)

        // When: Comparing against spec
        val result = ShadowComparisonService.compareKidsGateAgainstSpec(legacy, sip)

        // Then: SpecPreferredLegacy
        assertEquals(ShadowComparisonService.ParityKind.SpecPreferredLegacy, result.parityKind)
        assertTrue(result.flags.contains("sipViolation:kidsBlockRule"))
    }

    @Test
    fun `kids - BothViolateSpec when both fail to block with quota 0`() {
        // Given: Both don't block when quota=0 (both violate spec)
        val legacy = InternalPlayerUiState(kidActive = true, kidBlocked = false, remainingKidsMinutes = 0)
        val sip = InternalPlayerUiState(kidActive = true, kidBlocked = false, remainingKidsMinutes = 0)

        // When: Comparing against spec
        val result = ShadowComparisonService.compareKidsGateAgainstSpec(legacy, sip)

        // Then: BothViolateSpec
        assertEquals(ShadowComparisonService.ParityKind.BothViolateSpec, result.parityKind)
    }

    @Test
    fun `kids - ExactMatch when both not blocked with quota available`() {
        // Given: Both correctly not blocked when quota > 0
        val legacy = InternalPlayerUiState(kidActive = true, kidBlocked = false, remainingKidsMinutes = 15)
        val sip = InternalPlayerUiState(kidActive = true, kidBlocked = false, remainingKidsMinutes = 15)

        // When: Comparing against spec
        val result = ShadowComparisonService.compareKidsGateAgainstSpec(legacy, sip)

        // Then: ExactMatch
        assertEquals(ShadowComparisonService.ParityKind.ExactMatch, result.parityKind)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Position Spec Comparison Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `position - DontCare when drift within tolerance`() {
        // Given: Position drift is within 1s tolerance
        val legacy = InternalPlayerUiState(currentPositionMs = 60_000L)
        val sip = InternalPlayerUiState(currentPositionMs = 60_500L) // 500ms drift

        // When: Comparing against spec
        val result = ShadowComparisonService.comparePositionAgainstSpec(legacy, sip)

        // Then: DontCare
        assertEquals(ShadowComparisonService.ParityKind.DontCare, result.parityKind)
        assertEquals("position", result.dimension)
    }

    @Test
    fun `position - ExactMatch with flag when drift exceeds tolerance`() {
        // Given: Position drift exceeds tolerance
        val legacy = InternalPlayerUiState(currentPositionMs = 60_000L)
        val sip = InternalPlayerUiState(currentPositionMs = 65_000L) // 5s drift

        // When: Comparing against spec
        val result = ShadowComparisonService.comparePositionAgainstSpec(legacy, sip)

        // Then: ExactMatch but flagged for investigation
        assertEquals(ShadowComparisonService.ParityKind.ExactMatch, result.parityKind)
        assertTrue(result.flags.any { it.contains("positionDrift") })
    }

    @Test
    fun `position - DontCare when either position is null`() {
        // Given: One position is null
        val legacy = InternalPlayerUiState(currentPositionMs = null)
        val sip = InternalPlayerUiState(currentPositionMs = 60_000L)

        // When: Comparing against spec
        val result = ShadowComparisonService.comparePositionAgainstSpec(legacy, sip)

        // Then: DontCare
        assertEquals(ShadowComparisonService.ParityKind.DontCare, result.parityKind)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // compareAgainstSpec Tests (All Dimensions)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `compareAgainstSpec returns results for all dimensions`() {
        // Given: States for comparison
        val legacy = InternalPlayerUiState(
            resumeStartMs = 60_000L,
            kidActive = true,
            kidBlocked = false,
            remainingKidsMinutes = 15,
            currentPositionMs = 30_000L,
        )
        val sip = InternalPlayerUiState(
            resumeStartMs = 60_000L,
            kidActive = true,
            kidBlocked = false,
            remainingKidsMinutes = 15,
            currentPositionMs = 30_500L,
        )

        // When: Comparing all dimensions
        val results = ShadowComparisonService.compareAgainstSpec(
            legacy = legacy,
            sip = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 120_000L,
        )

        // Then: Results for all dimensions
        assertEquals(3, results.size)
        assertTrue(results.any { it.dimension == "resume" })
        assertTrue(results.any { it.dimension == "kids" })
        assertTrue(results.any { it.dimension == "position" })
    }

    // ════════════════════════════════════════════════════════════════════════════
    // InternalPlayerShadow Integration Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `invokeSpecComparison invokes callback for each dimension`() {
        // Given: States for comparison
        val legacy = InternalPlayerUiState(
            resumeStartMs = 60_000L,
            kidActive = true,
            kidBlocked = true,
            remainingKidsMinutes = 0,
            currentPositionMs = 30_000L,
        )
        val sip = InternalPlayerUiState(
            resumeStartMs = 60_000L,
            kidActive = true,
            kidBlocked = true,
            remainingKidsMinutes = 0,
            currentPositionMs = 30_000L,
        )
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)

        // When: Invoking spec comparison
        val receivedResults = mutableListOf<ShadowComparisonService.SpecComparisonResult>()
        InternalPlayerShadow.invokeSpecComparison(
            legacyState = legacy,
            sipState = sip,
            playbackContext = context,
            durationMs = 120_000L,
            callback = { receivedResults.add(it) },
        )

        // Then: Received results for all dimensions
        assertEquals(3, receivedResults.size)
        // Note: Position with 0 drift returns DontCare, not ExactMatch
        assertTrue(receivedResults.any { it.dimension == "resume" && it.parityKind == ShadowComparisonService.ParityKind.ExactMatch })
        assertTrue(receivedResults.any { it.dimension == "kids" && it.parityKind == ShadowComparisonService.ParityKind.ExactMatch })
        assertTrue(receivedResults.any { it.dimension == "position" && it.parityKind == ShadowComparisonService.ParityKind.DontCare })
    }

    @Test
    fun `invokeSpecComparison is safe with null callback`() {
        // Given: States
        val legacy = InternalPlayerUiState(resumeStartMs = 60_000L)
        val sip = InternalPlayerUiState(resumeStartMs = 60_000L)
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)

        // When/Then: No exception thrown with null callback
        InternalPlayerShadow.invokeSpecComparison(
            legacyState = legacy,
            sipState = sip,
            playbackContext = context,
            callback = null,
        )
    }

    @Test
    fun `startShadowSession accepts onSpecComparison parameter`() {
        // Given: Session parameters with spec comparison callback
        var specComparisonInvoked = false

        // When: Starting session with callback
        InternalPlayerShadow.startShadowSession(
            url = "https://example.com/video.mp4",
            startMs = null,
            mimeType = null,
            mediaItem = null,
            playbackContext = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L),
            onSpecComparison = { specComparisonInvoked = true },
        )

        // Then: No exception thrown (callback not invoked in placeholder implementation)
        assertFalse("Placeholder should not invoke callback", specComparisonInvoked)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Robustness Tests (Malformed/Null/Extreme State)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `spec comparison handles null resume values`() {
        // Given: Both have null resume
        val legacy = InternalPlayerUiState(resumeStartMs = null)
        val sip = InternalPlayerUiState(resumeStartMs = null)

        // When/Then: No exception
        val result = ShadowComparisonService.compareResumeAgainstSpec(
            legacy = legacy,
            sip = sip,
            playbackType = PlaybackType.VOD,
            durationMs = null,
        )
        assertNotNull(result)
    }

    @Test
    fun `spec comparison handles null duration`() {
        // Given: Valid resume positions but null duration
        val legacy = InternalPlayerUiState(resumeStartMs = 60_000L)
        val sip = InternalPlayerUiState(resumeStartMs = 60_000L)

        // When/Then: No exception
        val result = ShadowComparisonService.compareResumeAgainstSpec(
            legacy = legacy,
            sip = sip,
            playbackType = PlaybackType.VOD,
            durationMs = null,
        )
        assertNotNull(result)
    }

    @Test
    fun `spec comparison handles extreme position values`() {
        // Given: Extreme position values
        val legacy = InternalPlayerUiState(currentPositionMs = Long.MAX_VALUE)
        val sip = InternalPlayerUiState(currentPositionMs = Long.MAX_VALUE - 100)

        // When/Then: No exception
        val result = ShadowComparisonService.comparePositionAgainstSpec(legacy, sip)
        assertNotNull(result)
    }

    @Test
    fun `spec comparison handles negative remaining minutes`() {
        // Given: Negative remaining minutes (edge case)
        val legacy = InternalPlayerUiState(kidActive = true, kidBlocked = true, remainingKidsMinutes = -5)
        val sip = InternalPlayerUiState(kidActive = true, kidBlocked = true, remainingKidsMinutes = -5)

        // When/Then: No exception
        val result = ShadowComparisonService.compareKidsGateAgainstSpec(legacy, sip)
        assertNotNull(result)
    }

    @Test
    fun `spec comparison handles default state`() {
        // Given: Default states
        val legacy = InternalPlayerUiState()
        val sip = InternalPlayerUiState()

        // When/Then: No exception
        val results = ShadowComparisonService.compareAgainstSpec(
            legacy = legacy,
            sip = sip,
            playbackType = PlaybackType.VOD,
            durationMs = null,
        )
        assertEquals(3, results.size)
    }

    @Test
    fun `invokeSpecComparison survives exception in callback`() {
        // Given: Callback that throws
        val legacy = InternalPlayerUiState(resumeStartMs = 60_000L)
        val sip = InternalPlayerUiState(resumeStartMs = 60_000L)
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)

        // When/Then: No exception propagated (fail-safe)
        InternalPlayerShadow.invokeSpecComparison(
            legacyState = legacy,
            sipState = sip,
            playbackContext = context,
            callback = { throw RuntimeException("Test exception") },
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // SpecComparisonResult Structure Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `SpecComparisonResult contains all required fields`() {
        // Given: A comparison result
        val legacy = InternalPlayerUiState(resumeStartMs = 5_000L)
        val sip = InternalPlayerUiState(resumeStartMs = null)

        // When: Getting result
        val result = ShadowComparisonService.compareResumeAgainstSpec(
            legacy = legacy,
            sip = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 120_000L,
        )

        // Then: All fields are present
        assertNotNull(result.parityKind)
        assertNotNull(result.dimension)
        assertNotNull(result.specDetails)
        // legacyValue, sipValue, and flags may be null/empty
    }

    @Test
    fun `ParityKind enum has all expected values`() {
        // Then: All expected values exist
        val values = ShadowComparisonService.ParityKind.values()
        assertTrue(values.contains(ShadowComparisonService.ParityKind.ExactMatch))
        assertTrue(values.contains(ShadowComparisonService.ParityKind.SpecPreferredSIP))
        assertTrue(values.contains(ShadowComparisonService.ParityKind.SpecPreferredLegacy))
        assertTrue(values.contains(ShadowComparisonService.ParityKind.BothViolateSpec))
        assertTrue(values.contains(ShadowComparisonService.ParityKind.DontCare))
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Phase 3B Helper Methods Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `filterSipViolations returns only SIP violations`() {
        // Given: Mix of parity kinds
        val results = listOf(
            ShadowComparisonService.SpecComparisonResult(
                parityKind = ShadowComparisonService.ParityKind.ExactMatch,
                dimension = "resume",
                legacyValue = null,
                sipValue = null,
                specDetails = "Match",
            ),
            ShadowComparisonService.SpecComparisonResult(
                parityKind = ShadowComparisonService.ParityKind.SpecPreferredSIP,
                dimension = "kids",
                legacyValue = null,
                sipValue = null,
                specDetails = "SIP preferred",
            ),
            ShadowComparisonService.SpecComparisonResult(
                parityKind = ShadowComparisonService.ParityKind.SpecPreferredLegacy,
                dimension = "position",
                legacyValue = null,
                sipValue = null,
                specDetails = "Legacy preferred",
            ),
            ShadowComparisonService.SpecComparisonResult(
                parityKind = ShadowComparisonService.ParityKind.BothViolateSpec,
                dimension = "other",
                legacyValue = null,
                sipValue = null,
                specDetails = "Both violate",
            ),
        )

        // When: Filtering SIP violations
        val violations = ShadowComparisonService.filterSipViolations(results)

        // Then: Only SpecPreferredLegacy and BothViolateSpec
        assertEquals(2, violations.size)
        assertTrue(violations.all {
            it.parityKind == ShadowComparisonService.ParityKind.SpecPreferredLegacy ||
                it.parityKind == ShadowComparisonService.ParityKind.BothViolateSpec
        })
    }

    @Test
    fun `needsEnforcement returns true when violations exist`() {
        val results = listOf(
            ShadowComparisonService.SpecComparisonResult(
                parityKind = ShadowComparisonService.ParityKind.SpecPreferredLegacy,
                dimension = "resume",
                legacyValue = null,
                sipValue = null,
                specDetails = "SIP violates",
            ),
        )

        assertTrue(ShadowComparisonService.needsEnforcement(results))
    }

    @Test
    fun `needsEnforcement returns false when no violations`() {
        val results = listOf(
            ShadowComparisonService.SpecComparisonResult(
                parityKind = ShadowComparisonService.ParityKind.ExactMatch,
                dimension = "resume",
                legacyValue = null,
                sipValue = null,
                specDetails = "Match",
            ),
            ShadowComparisonService.SpecComparisonResult(
                parityKind = ShadowComparisonService.ParityKind.SpecPreferredSIP,
                dimension = "kids",
                legacyValue = null,
                sipValue = null,
                specDetails = "SIP preferred",
            ),
        )

        assertFalse(ShadowComparisonService.needsEnforcement(results))
    }

    @Test
    fun `buildStructuredDiff creates map by dimension`() {
        val results = listOf(
            ShadowComparisonService.SpecComparisonResult(
                parityKind = ShadowComparisonService.ParityKind.ExactMatch,
                dimension = "resume",
                legacyValue = 60_000L,
                sipValue = 60_000L,
                specDetails = "Match",
            ),
            ShadowComparisonService.SpecComparisonResult(
                parityKind = ShadowComparisonService.ParityKind.DontCare,
                dimension = "position",
                legacyValue = 30_000L,
                sipValue = 30_500L,
                specDetails = "Within tolerance",
            ),
        )

        val diff = ShadowComparisonService.buildStructuredDiff(results)

        assertEquals(2, diff.size)
        assertTrue(diff.containsKey("resume"))
        assertTrue(diff.containsKey("position"))
        assertEquals(ShadowComparisonService.ParityKind.ExactMatch, diff["resume"]?.parityKind)
        assertEquals(ShadowComparisonService.ParityKind.DontCare, diff["position"]?.parityKind)
    }

    @Test
    fun `summarizeViolations returns human-readable summary`() {
        val results = listOf(
            ShadowComparisonService.SpecComparisonResult(
                parityKind = ShadowComparisonService.ParityKind.SpecPreferredLegacy,
                dimension = "resume",
                legacyValue = null,
                sipValue = 5_000L,
                specDetails = "SIP violates 10s rule",
            ),
        )

        val summary = ShadowComparisonService.summarizeViolations(results)

        assertTrue(summary.contains("resume"))
        assertTrue(summary.contains("SpecPreferredLegacy"))
    }

    @Test
    fun `summarizeViolations returns no violations message when empty`() {
        val results = listOf(
            ShadowComparisonService.SpecComparisonResult(
                parityKind = ShadowComparisonService.ParityKind.ExactMatch,
                dimension = "resume",
                legacyValue = null,
                sipValue = null,
                specDetails = "Match",
            ),
        )

        val summary = ShadowComparisonService.summarizeViolations(results)

        assertTrue(summary.contains("No SIP violations"))
    }
}
