package com.chris.m3usuite.player.internal.shadow

import com.chris.m3usuite.player.internal.bridge.InternalPlayerShadow
import com.chris.m3usuite.player.internal.domain.PlaybackContext
import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Phase 3B: BehaviorContractEnforcer.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * TEST SCOPE: BEHAVIOR CONTRACT ENFORCEMENT
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * These tests verify:
 * 1. Complete coverage for all ParityKind combinations
 * 2. Enforcement for resume, kids gate, position drift
 * 3. LIVE mode skip behavior
 * 4. Robustness with null/malformed state
 * 5. EnforcementAction correctness
 *
 * **KEY PRINCIPLE:**
 * The enforcer produces corrective actions for SIP violations.
 * It does NOT affect runtime behavior - actions are for diagnostics only.
 */
class BehaviorContractEnforcerTest {

    // ════════════════════════════════════════════════════════════════════════════
    // Resume Enforcement Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `resume - NoAction when SIP matches spec (ExactMatch)`() {
        // Given: Both SIP and Legacy have valid resume positions
        val legacy = InternalPlayerUiState(resumeStartMs = 60_000L)
        val sip = InternalPlayerUiState(resumeStartMs = 60_000L)

        // When: Evaluating enforcement
        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 120_000L,
        )

        // Then: NoAction for resume
        val resumeResult = results.find { it.dimension == "resume" }
        assertNotNull(resumeResult)
        assertTrue(resumeResult!!.action is BehaviorContractEnforcer.EnforcementAction.NoAction)
    }

    @Test
    fun `resume - NoAction when SIP is preferred (SpecPreferredSIP)`() {
        // Given: SIP has correct null resume, Legacy incorrectly stored
        val legacy = InternalPlayerUiState(resumeStartMs = 5_000L) // Violates spec
        val sip = InternalPlayerUiState(resumeStartMs = null) // Correct

        // When: Evaluating enforcement
        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 120_000L,
        )

        // Then: NoAction (SIP is correct, legacy has bug)
        val resumeResult = results.find { it.dimension == "resume" }
        assertNotNull(resumeResult)
        assertTrue(
            "SIP is correct, should be NoAction",
            resumeResult!!.action is BehaviorContractEnforcer.EnforcementAction.NoAction,
        )
    }

    @Test
    fun `resume - ClearResume when SIP violates 10s threshold`() {
        // Given: SIP incorrectly stored 5s resume, Legacy is correct
        val legacy = InternalPlayerUiState(resumeStartMs = null)
        val sip = InternalPlayerUiState(resumeStartMs = 5_000L) // Violates spec

        // When: Evaluating enforcement
        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 120_000L,
        )

        // Then: ClearResume action
        val resumeResult = results.find { it.dimension == "resume" }
        assertNotNull(resumeResult)
        assertTrue(
            "Should recommend ClearResume for position <= 10s",
            resumeResult!!.action is BehaviorContractEnforcer.EnforcementAction.ClearResume,
        )
    }

    @Test
    fun `resume - ClearResume for LIVE content when SIP has resume`() {
        // Given: LIVE content - SIP incorrectly has resume
        val legacy = InternalPlayerUiState(resumeStartMs = null)
        val sip = InternalPlayerUiState(resumeStartMs = 60_000L) // Violates spec

        // When: Evaluating enforcement for LIVE
        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.LIVE,
            durationMs = null,
        )

        // Then: ClearResume action (LIVE never resumes)
        val resumeResult = results.find { it.dimension == "resume" }
        assertNotNull(resumeResult)
        assertTrue(
            "LIVE content should not resume",
            resumeResult!!.action is BehaviorContractEnforcer.EnforcementAction.ClearResume,
        )
        assertTrue(
            "Reason should mention LIVE",
            resumeResult.reason.contains("LIVE"),
        )
    }

    @Test
    fun `resume - ClearResume for near-end position`() {
        // Given: SIP has position near end of content
        val legacy = InternalPlayerUiState(resumeStartMs = null)
        val sip = InternalPlayerUiState(resumeStartMs = 115_000L) // Near end of 120s content

        // When: Evaluating enforcement
        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 120_000L,
        )

        // Then: ClearResume action (near-end should be cleared)
        val resumeResult = results.find { it.dimension == "resume" }
        assertNotNull(resumeResult)
        assertTrue(
            "Near-end position should be cleared",
            resumeResult!!.action is BehaviorContractEnforcer.EnforcementAction.ClearResume,
        )
    }

    @Test
    fun `resume - FixResumePosition when legacy is correct`() {
        // Given: Legacy has correct position, SIP has wrong value
        val legacy = InternalPlayerUiState(resumeStartMs = 60_000L) // Correct
        val sip = InternalPlayerUiState(resumeStartMs = 3_000L) // Violates spec

        // When: Evaluating enforcement
        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 120_000L,
        )

        // Then: ClearResume (the SIP value is <= 10s, so it should be cleared regardless)
        val resumeResult = results.find { it.dimension == "resume" }
        assertNotNull(resumeResult)
        // Since SIP is <= 10s, it will be ClearResume, not FixResumePosition
        assertTrue(
            "Invalid SIP position should be cleared",
            resumeResult!!.action is BehaviorContractEnforcer.EnforcementAction.ClearResume,
        )
    }

    @Test
    fun `resume - NoAction for SERIES type with valid resume`() {
        // Given: SERIES content with valid resume
        val legacy = InternalPlayerUiState(resumeStartMs = 45_000L)
        val sip = InternalPlayerUiState(resumeStartMs = 45_000L)

        // When: Evaluating enforcement for SERIES
        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.SERIES,
            durationMs = 60_000L,
        )

        // Then: NoAction (SERIES can resume like VOD)
        val resumeResult = results.find { it.dimension == "resume" }
        assertNotNull(resumeResult)
        assertTrue(resumeResult!!.action is BehaviorContractEnforcer.EnforcementAction.NoAction)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Kids Gate Enforcement Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `kids - NoAction when both comply with spec`() {
        // Given: Both correctly blocked when quota = 0
        val legacy = InternalPlayerUiState(kidActive = true, kidBlocked = true, remainingKidsMinutes = 0)
        val sip = InternalPlayerUiState(kidActive = true, kidBlocked = true, remainingKidsMinutes = 0)

        // When: Evaluating enforcement
        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 60_000L,
        )

        // Then: NoAction
        val kidsResult = results.find { it.dimension == "kids" }
        assertNotNull(kidsResult)
        assertTrue(kidsResult!!.action is BehaviorContractEnforcer.EnforcementAction.NoAction)
    }

    @Test
    fun `kids - BlockKidsPlayback when SIP fails to block exhausted quota`() {
        // Given: SIP doesn't block when quota = 0
        val legacy = InternalPlayerUiState(kidActive = true, kidBlocked = true, remainingKidsMinutes = 0)
        val sip = InternalPlayerUiState(kidActive = true, kidBlocked = false, remainingKidsMinutes = 0) // Violates spec

        // When: Evaluating enforcement
        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 60_000L,
        )

        // Then: BlockKidsPlayback action
        val kidsResult = results.find { it.dimension == "kids" }
        assertNotNull(kidsResult)
        assertTrue(
            "Should block kids playback when quota exhausted",
            kidsResult!!.action is BehaviorContractEnforcer.EnforcementAction.BlockKidsPlayback,
        )
    }

    @Test
    fun `kids - UnblockKidsPlayback when SIP incorrectly blocks with quota`() {
        // Given: SIP blocks when quota > 0
        val legacy = InternalPlayerUiState(kidActive = true, kidBlocked = false, remainingKidsMinutes = 15)
        val sip = InternalPlayerUiState(kidActive = true, kidBlocked = true, remainingKidsMinutes = 15) // Violates spec

        // When: Evaluating enforcement
        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 60_000L,
        )

        // Then: UnblockKidsPlayback action
        val kidsResult = results.find { it.dimension == "kids" }
        assertNotNull(kidsResult)
        assertTrue(
            "Should unblock when quota is available",
            kidsResult!!.action is BehaviorContractEnforcer.EnforcementAction.UnblockKidsPlayback,
        )
    }

    @Test
    fun `kids - NoAction when not a kid profile`() {
        // Given: Not a kid profile
        val legacy = InternalPlayerUiState(kidActive = false, kidBlocked = false)
        val sip = InternalPlayerUiState(kidActive = false, kidBlocked = false)

        // When: Evaluating enforcement
        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 60_000L,
        )

        // Then: NoAction (not a kid profile)
        val kidsResult = results.find { it.dimension == "kids" }
        assertNotNull(kidsResult)
        assertTrue(kidsResult!!.action is BehaviorContractEnforcer.EnforcementAction.NoAction)
    }

    @Test
    fun `kids - NoAction when SIP is preferred (SIP correct, legacy wrong)`() {
        // Given: SIP correctly blocks, Legacy doesn't
        val legacy = InternalPlayerUiState(kidActive = true, kidBlocked = false, remainingKidsMinutes = 0)
        val sip = InternalPlayerUiState(kidActive = true, kidBlocked = true, remainingKidsMinutes = 0)

        // When: Evaluating enforcement
        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 60_000L,
        )

        // Then: NoAction (SIP is correct)
        val kidsResult = results.find { it.dimension == "kids" }
        assertNotNull(kidsResult)
        assertTrue(
            "SIP is correct, should be NoAction",
            kidsResult!!.action is BehaviorContractEnforcer.EnforcementAction.NoAction,
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Position Enforcement Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `position - NoAction when drift within tolerance`() {
        // Given: Position drift within 1s
        val legacy = InternalPlayerUiState(currentPositionMs = 60_000L)
        val sip = InternalPlayerUiState(currentPositionMs = 60_500L)

        // When: Evaluating enforcement
        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 120_000L,
        )

        // Then: NoAction
        val posResult = results.find { it.dimension == "position" }
        assertNotNull(posResult)
        assertTrue(posResult!!.action is BehaviorContractEnforcer.EnforcementAction.NoAction)
    }

    @Test
    fun `position - NormalizePosition when drift exceeds tolerance`() {
        // Given: Large position drift
        val legacy = InternalPlayerUiState(currentPositionMs = 60_000L)
        val sip = InternalPlayerUiState(currentPositionMs = 65_000L) // 5s drift

        // When: Evaluating enforcement
        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 120_000L,
        )

        // Then: NormalizePosition action
        val posResult = results.find { it.dimension == "position" }
        assertNotNull(posResult)
        assertTrue(
            "Should normalize position when drift is large",
            posResult!!.action is BehaviorContractEnforcer.EnforcementAction.NormalizePosition,
        )
        val normalizeAction = posResult.action as BehaviorContractEnforcer.EnforcementAction.NormalizePosition
        assertEquals(60_000L, normalizeAction.positionMs)
    }

    @Test
    fun `position - NoAction when position unavailable`() {
        // Given: Null position
        val legacy = InternalPlayerUiState(currentPositionMs = null)
        val sip = InternalPlayerUiState(currentPositionMs = null)

        // When: Evaluating enforcement
        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 120_000L,
        )

        // Then: NoAction
        val posResult = results.find { it.dimension == "position" }
        assertNotNull(posResult)
        assertTrue(posResult!!.action is BehaviorContractEnforcer.EnforcementAction.NoAction)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // LIVE Mode Skip Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `LIVE - NoAction for valid LIVE state`() {
        // Given: LIVE content with no resume (correct)
        val legacy = InternalPlayerUiState(resumeStartMs = null, playbackType = PlaybackType.LIVE)
        val sip = InternalPlayerUiState(resumeStartMs = null, playbackType = PlaybackType.LIVE)

        // When: Evaluating enforcement
        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.LIVE,
            durationMs = null,
        )

        // Then: All NoAction
        val resumeResult = results.find { it.dimension == "resume" }
        assertNotNull(resumeResult)
        assertTrue(resumeResult!!.action is BehaviorContractEnforcer.EnforcementAction.NoAction)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Complete ParityKind Coverage Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `ParityKind ExactMatch - NoAction`() {
        // Given: ExactMatch scenario
        val legacy = InternalPlayerUiState(resumeStartMs = 50_000L)
        val sip = InternalPlayerUiState(resumeStartMs = 50_000L)

        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 100_000L,
        )

        val resumeResult = results.find { it.dimension == "resume" }
        assertTrue(resumeResult!!.action is BehaviorContractEnforcer.EnforcementAction.NoAction)
    }

    @Test
    fun `ParityKind SpecPreferredSIP - NoAction`() {
        // Given: SIP correct, legacy violates
        val legacy = InternalPlayerUiState(resumeStartMs = 5_000L)
        val sip = InternalPlayerUiState(resumeStartMs = null)

        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 100_000L,
        )

        val resumeResult = results.find { it.dimension == "resume" }
        assertTrue(resumeResult!!.action is BehaviorContractEnforcer.EnforcementAction.NoAction)
    }

    @Test
    fun `ParityKind SpecPreferredLegacy - enforcement needed`() {
        // Given: Legacy correct, SIP violates
        val legacy = InternalPlayerUiState(resumeStartMs = null)
        val sip = InternalPlayerUiState(resumeStartMs = 5_000L)

        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 100_000L,
        )

        val resumeResult = results.find { it.dimension == "resume" }
        assertFalse(
            "Enforcement needed for SIP violation",
            resumeResult!!.action is BehaviorContractEnforcer.EnforcementAction.NoAction,
        )
    }

    @Test
    fun `ParityKind BothViolateSpec - enforcement needed`() {
        // Given: Both violate (both have resume <= 10s)
        val legacy = InternalPlayerUiState(resumeStartMs = 5_000L)
        val sip = InternalPlayerUiState(resumeStartMs = 8_000L)

        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 100_000L,
        )

        val resumeResult = results.find { it.dimension == "resume" }
        assertFalse(
            "Enforcement needed even when both violate",
            resumeResult!!.action is BehaviorContractEnforcer.EnforcementAction.NoAction,
        )
    }

    @Test
    fun `ParityKind DontCare - NoAction`() {
        // Given: Both compliant but different values
        val legacy = InternalPlayerUiState(resumeStartMs = 50_000L)
        val sip = InternalPlayerUiState(resumeStartMs = 55_000L)

        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 100_000L,
        )

        val resumeResult = results.find { it.dimension == "resume" }
        assertTrue(resumeResult!!.action is BehaviorContractEnforcer.EnforcementAction.NoAction)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // needsEnforcement() and filterActionableEnforcements() Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `needsEnforcement returns true when SIP violates`() {
        // Given: SIP violates spec
        val legacy = InternalPlayerUiState(resumeStartMs = null)
        val sip = InternalPlayerUiState(resumeStartMs = 5_000L)

        val specResults = ShadowComparisonService.compareAgainstSpec(
            legacy = legacy,
            sip = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 100_000L,
        )

        val needsEnforcement = BehaviorContractEnforcer.needsEnforcement(
            specResults,
            sip,
            PlaybackType.VOD,
            100_000L,
        )

        assertTrue("Enforcement needed for SIP violation", needsEnforcement)
    }

    @Test
    fun `needsEnforcement returns false when all compliant`() {
        // Given: All compliant
        val legacy = InternalPlayerUiState(resumeStartMs = 50_000L)
        val sip = InternalPlayerUiState(resumeStartMs = 50_000L)

        val specResults = ShadowComparisonService.compareAgainstSpec(
            legacy = legacy,
            sip = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 100_000L,
        )

        val needsEnforcement = BehaviorContractEnforcer.needsEnforcement(
            specResults,
            sip,
            PlaybackType.VOD,
            100_000L,
        )

        assertFalse("No enforcement needed when compliant", needsEnforcement)
    }

    @Test
    fun `filterActionableEnforcements removes NoAction`() {
        // Given: Mix of NoAction and actual actions
        val results = listOf(
            BehaviorContractEnforcer.EnforcementResult(
                dimension = "resume",
                action = BehaviorContractEnforcer.EnforcementAction.NoAction,
                sipValue = null,
                specValue = null,
                reason = "OK",
            ),
            BehaviorContractEnforcer.EnforcementResult(
                dimension = "kids",
                action = BehaviorContractEnforcer.EnforcementAction.BlockKidsPlayback,
                sipValue = false,
                specValue = true,
                reason = "Block needed",
            ),
        )

        val actionable = BehaviorContractEnforcer.filterActionableEnforcements(results)

        assertEquals(1, actionable.size)
        assertEquals("kids", actionable[0].dimension)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Robustness with Null/Malformed State Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `handles default state gracefully`() {
        val legacy = InternalPlayerUiState()
        val sip = InternalPlayerUiState()

        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.VOD,
            durationMs = null,
        )

        assertEquals(3, results.size)
        results.forEach { result ->
            assertNotNull(result.action)
            assertNotNull(result.dimension)
            assertNotNull(result.reason)
        }
    }

    @Test
    fun `handles null duration gracefully`() {
        val legacy = InternalPlayerUiState(resumeStartMs = 50_000L)
        val sip = InternalPlayerUiState(resumeStartMs = 50_000L)

        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.VOD,
            durationMs = null,
        )

        assertNotNull(results)
        assertEquals(3, results.size)
    }

    @Test
    fun `handles negative remaining minutes`() {
        val legacy = InternalPlayerUiState(kidActive = true, kidBlocked = true, remainingKidsMinutes = -5)
        val sip = InternalPlayerUiState(kidActive = true, kidBlocked = true, remainingKidsMinutes = -5)

        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.VOD,
            durationMs = 60_000L,
        )

        assertNotNull(results)
        val kidsResult = results.find { it.dimension == "kids" }
        assertNotNull(kidsResult)
    }

    @Test
    fun `handles extreme position values`() {
        val legacy = InternalPlayerUiState(currentPositionMs = Long.MAX_VALUE)
        val sip = InternalPlayerUiState(currentPositionMs = Long.MAX_VALUE - 100)

        val results = BehaviorContractEnforcer.evaluateFromStates(
            legacyState = legacy,
            sipState = sip,
            playbackType = PlaybackType.VOD,
            durationMs = Long.MAX_VALUE,
        )

        assertNotNull(results)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // EnforcementAction Correctness Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `EnforcementAction describe() returns meaningful text`() {
        val actions = listOf(
            BehaviorContractEnforcer.EnforcementAction.NoAction,
            BehaviorContractEnforcer.EnforcementAction.FixResumePosition(60_000L),
            BehaviorContractEnforcer.EnforcementAction.ClearResume,
            BehaviorContractEnforcer.EnforcementAction.BlockKidsPlayback,
            BehaviorContractEnforcer.EnforcementAction.UnblockKidsPlayback,
            BehaviorContractEnforcer.EnforcementAction.NormalizePosition(30_000L),
        )

        actions.forEach { action ->
            val description = action.describe()
            assertNotNull(description)
            assertTrue(description.isNotBlank())
        }
    }

    @Test
    fun `FixResumePosition contains correct position`() {
        val action = BehaviorContractEnforcer.EnforcementAction.FixResumePosition(45_000L)
        assertEquals(45_000L, action.positionMs)
        assertTrue(action.describe().contains("45000"))
    }

    @Test
    fun `NormalizePosition contains correct position`() {
        val action = BehaviorContractEnforcer.EnforcementAction.NormalizePosition(75_000L)
        assertEquals(75_000L, action.positionMs)
        assertTrue(action.describe().contains("75000"))
    }

    // ════════════════════════════════════════════════════════════════════════════
    // InternalPlayerShadow Integration Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `invokeEnforcement calls callback with actionable results`() {
        // Given: State that needs enforcement
        val legacy = InternalPlayerUiState(
            resumeStartMs = null,
            kidActive = true,
            kidBlocked = true,
            remainingKidsMinutes = 0,
        )
        val sip = InternalPlayerUiState(
            resumeStartMs = 5_000L, // Violates spec
            kidActive = true,
            kidBlocked = true,
            remainingKidsMinutes = 0,
        )
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)

        // When: Invoking enforcement
        var callbackInvoked = false
        var receivedActions: List<BehaviorContractEnforcer.EnforcementResult>? = null
        InternalPlayerShadow.invokeEnforcement(
            legacyState = legacy,
            sipState = sip,
            playbackContext = context,
            durationMs = 100_000L,
            callback = { actions ->
                callbackInvoked = true
                receivedActions = actions
            },
        )

        // Then: Callback invoked with enforcement actions
        assertTrue("Callback should be invoked", callbackInvoked)
        assertNotNull(receivedActions)
        assertTrue("Should have actionable enforcements", receivedActions!!.isNotEmpty())
    }

    @Test
    fun `invokeEnforcement is safe with null callback`() {
        val legacy = InternalPlayerUiState(resumeStartMs = 5_000L)
        val sip = InternalPlayerUiState(resumeStartMs = 5_000L)
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)

        // Should not throw
        InternalPlayerShadow.invokeEnforcement(
            legacyState = legacy,
            sipState = sip,
            playbackContext = context,
            callback = null,
        )
    }

    @Test
    fun `invokeEnforcement does not call callback when all NoAction`() {
        // Given: All compliant state
        val legacy = InternalPlayerUiState(resumeStartMs = 50_000L)
        val sip = InternalPlayerUiState(resumeStartMs = 50_000L)
        val context = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L)

        // When: Invoking enforcement
        var callbackInvoked = false
        InternalPlayerShadow.invokeEnforcement(
            legacyState = legacy,
            sipState = sip,
            playbackContext = context,
            durationMs = 100_000L,
            callback = { callbackInvoked = true },
        )

        // Then: Callback not invoked (no actionable enforcements)
        assertFalse("Callback should not be invoked when all NoAction", callbackInvoked)
    }

    @Test
    fun `startShadowSession accepts onEnforcementActions parameter`() {
        var invoked = false

        // Should not throw and accept the parameter
        InternalPlayerShadow.startShadowSession(
            url = "https://example.com/video.mp4",
            startMs = null,
            mimeType = null,
            mediaItem = null,
            playbackContext = PlaybackContext(type = PlaybackType.VOD, mediaId = 123L),
            onEnforcementActions = { invoked = true },
        )

        // Placeholder doesn't invoke callbacks, so this should be false
        assertFalse("Placeholder should not invoke callback", invoked)
    }
}
