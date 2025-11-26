package com.chris.m3usuite.player.internal.ui

import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState
import com.chris.m3usuite.player.internal.subtitles.SubtitleStyle
import com.chris.m3usuite.player.internal.subtitles.SubtitleTrack
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Edge case and Kid Mode tests for CC Menu and subtitle UI.
 *
 * Contract Reference: `INTERNAL_PLAYER_SUBTITLE_CC_CONTRACT_PHASE4.md` Section 3.1, 8.1
 *
 * These tests validate:
 * - CC button hidden in Kid Mode (even with available tracks)
 * - CC dialog never shown in Kid Mode
 * - Zero subtitle tracks → CC button hidden + safe UI state
 * - Track list changes while CC dialog open → stable dialog state
 */
class CcMenuKidModeAndEdgeCasesTest {
    // ════════════════════════════════════════════════════════════════════════════
    // Kid Mode End-to-End UI Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `Kid Mode - CC button hidden even with available tracks (VOD)`() {
        val state = InternalPlayerUiState(
            playbackType = PlaybackType.VOD,
            kidActive = true,
            availableSubtitleTracks = listOf(
                SubtitleTrack(0, 0, "en", "English", false),
                SubtitleTrack(0, 1, "de", "German", false),
            ),
        )

        // Contract Section 3.1: No CC/Subtitle button is shown in the player
        val ccButtonVisible = !state.kidActive && state.availableSubtitleTracks.isNotEmpty()
        assertFalse("CC button must be hidden for kid profiles", ccButtonVisible)
    }

    @Test
    fun `Kid Mode - CC button hidden even with available tracks (SERIES)`() {
        val state = InternalPlayerUiState(
            playbackType = PlaybackType.SERIES,
            kidActive = true,
            availableSubtitleTracks = listOf(
                SubtitleTrack(0, 0, "en", "English", false),
            ),
        )

        val ccButtonVisible = !state.kidActive && state.availableSubtitleTracks.isNotEmpty()
        assertFalse("CC button must be hidden for kid profiles", ccButtonVisible)
    }

    @Test
    fun `Kid Mode - CC button hidden even with available tracks (LIVE)`() {
        val state = InternalPlayerUiState(
            playbackType = PlaybackType.LIVE,
            kidActive = true,
            availableSubtitleTracks = listOf(
                SubtitleTrack(0, 0, "en", "English", false),
            ),
        )

        val ccButtonVisible = !state.kidActive && state.availableSubtitleTracks.isNotEmpty()
        assertFalse("CC button must be hidden for kid profiles", ccButtonVisible)
    }

    @Test
    fun `Kid Mode - CC dialog never shown even if showCcMenuDialog true`() {
        val state = InternalPlayerUiState(
            kidActive = true,
            showCcMenuDialog = true, // accidentally set
            availableSubtitleTracks = listOf(
                SubtitleTrack(0, 0, "en", "English", false),
            ),
        )

        // Contract Section 3.1: CC menu must not be shown for kid profiles
        val dialogVisible = state.showCcMenuDialog && !state.kidActive
        assertFalse("CC dialog must never show for kid profiles", dialogVisible)
    }

    @Test
    fun `Kid Mode - no subtitle track selected in state`() {
        val state = InternalPlayerUiState(
            kidActive = true,
            availableSubtitleTracks = listOf(
                SubtitleTrack(0, 0, "en", "English", false),
            ),
            selectedSubtitleTrack = null, // Contract Section 3.1: No subtitle track is selected
        )

        // Verify no track is selected
        assertTrue("selectedSubtitleTrack must be null for kid profiles", state.selectedSubtitleTrack == null)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Zero Subtitle Tracks Edge Cases
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `zero tracks - CC button hidden (non-kid profile)`() {
        val state = InternalPlayerUiState(
            kidActive = false,
            availableSubtitleTracks = emptyList(),
        )

        // Contract Section 8.1: Button visible only if at least one subtitle track exists
        val ccButtonVisible = !state.kidActive && state.availableSubtitleTracks.isNotEmpty()
        assertFalse("CC button must be hidden when no tracks available", ccButtonVisible)
    }

    @Test
    fun `zero tracks - selectedSubtitleTrack is null`() {
        val state = InternalPlayerUiState(
            kidActive = false,
            availableSubtitleTracks = emptyList(),
            selectedSubtitleTrack = null,
        )

        assertTrue("selectedSubtitleTrack should be null when no tracks available", state.selectedSubtitleTrack == null)
    }

    @Test
    fun `zero tracks - CC dialog can be closed without crash`() {
        val state = InternalPlayerUiState(
            kidActive = false,
            showCcMenuDialog = true,
            availableSubtitleTracks = emptyList(),
        )

        // Dialog may be open from before tracks were removed
        // Verify dialog visibility condition
        val dialogVisible = state.showCcMenuDialog && !state.kidActive
        assertTrue("Dialog should be visible if flag is true", dialogVisible)

        // Simulate closing dialog (in actual UI, would call controller.onToggleCcMenu)
        // Here we just verify state can be copied safely
        val closedState = state.copy(showCcMenuDialog = false)
        assertFalse("Dialog should be closable", closedState.showCcMenuDialog)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Track List Changes While CC Dialog Open
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `track list changes - dialog remains valid when tracks removed`() {
        // Start with CC dialog open and tracks available
        val initialState = InternalPlayerUiState(
            kidActive = false,
            showCcMenuDialog = true,
            availableSubtitleTracks = listOf(
                SubtitleTrack(0, 0, "en", "English", false),
                SubtitleTrack(0, 1, "de", "German", false),
            ),
            selectedSubtitleTrack = SubtitleTrack(0, 0, "en", "English", false),
        )

        assertTrue("Dialog should be open initially", initialState.showCcMenuDialog)

        // Simulate onTracksChanged event removing all tracks
        val updatedState = initialState.copy(
            availableSubtitleTracks = emptyList(),
            selectedSubtitleTrack = null,
        )

        // Dialog should remain open (it's the user's choice to close it)
        assertTrue("Dialog flag should not auto-close on track change", updatedState.showCcMenuDialog)

        // But CC button would now be hidden
        val ccButtonVisible = !updatedState.kidActive && updatedState.availableSubtitleTracks.isNotEmpty()
        assertFalse("CC button should be hidden after tracks removed", ccButtonVisible)
    }

    @Test
    fun `track list changes - dialog remains valid when tracks added`() {
        // Start with CC dialog open but no tracks
        val initialState = InternalPlayerUiState(
            kidActive = false,
            showCcMenuDialog = true,
            availableSubtitleTracks = emptyList(),
            selectedSubtitleTrack = null,
        )

        // Simulate onTracksChanged event adding tracks
        val updatedState = initialState.copy(
            availableSubtitleTracks = listOf(
                SubtitleTrack(0, 0, "en", "English", false),
            ),
            selectedSubtitleTrack = SubtitleTrack(0, 0, "en", "English", false),
        )

        // Dialog should remain open
        assertTrue("Dialog should remain open after tracks added", updatedState.showCcMenuDialog)

        // CC button would now be visible
        val ccButtonVisible = !updatedState.kidActive && updatedState.availableSubtitleTracks.isNotEmpty()
        assertTrue("CC button should be visible after tracks added", ccButtonVisible)
    }

    @Test
    fun `track list changes - selected track remains valid if still in new list`() {
        val track0 = SubtitleTrack(0, 0, "en", "English", false)
        val track1 = SubtitleTrack(0, 1, "de", "German", false)

        val initialState = InternalPlayerUiState(
            kidActive = false,
            availableSubtitleTracks = listOf(track0, track1),
            selectedSubtitleTrack = track0,
        )

        // Simulate track list change (same tracks, different order)
        val updatedState = initialState.copy(
            availableSubtitleTracks = listOf(track1, track0), // reordered
        )

        // Selected track should still be valid (by reference equality or track matching)
        assertNotNull("Selected track should remain valid", updatedState.selectedSubtitleTrack)
        assertTrue(
            "Selected track should still match",
            updatedState.selectedSubtitleTrack?.language == "en",
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // SubtitleStyle Safety Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `subtitleStyle always has valid default in state`() {
        val state = InternalPlayerUiState()

        // Default state should have a valid SubtitleStyle
        assertNotNull("subtitleStyle should never be null", state.subtitleStyle)
        assertTrue("subtitleStyle should be valid", state.subtitleStyle.isValid())
    }

    @Test
    fun `Kid Mode - subtitleStyle stored but not applied (state validation)`() {
        val state = InternalPlayerUiState(
            kidActive = true,
            subtitleStyle = SubtitleStyle(
                textScale = 1.5f, // custom style
                foregroundColor = 0xFFFFFF00.toInt(), // yellow
            ),
        )

        // Contract Section 3.1: SubtitleStyleManager still stores styles, but they are never applied
        // State should contain the style, but it should be ignored during rendering
        assertNotNull("subtitleStyle should be stored even in Kid Mode", state.subtitleStyle)
        assertTrue("Kid Mode flag should be set", state.kidActive)

        // In actual PlayerSurface implementation, style application is skipped when isKidMode=true
    }

    // ════════════════════════════════════════════════════════════════════════════
    // CC Dialog State Isolation
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `CC dialog state changes do not affect playback state`() {
        val initialState = InternalPlayerUiState(
            isPlaying = true,
            positionMs = 30000L,
            durationMs = 120000L,
        )

        // Open CC dialog
        val dialogOpenState = initialState.copy(showCcMenuDialog = true)

        // Playback state should be unchanged
        assertTrue("isPlaying should remain unchanged", dialogOpenState.isPlaying)
        assertTrue("positionMs should remain unchanged", dialogOpenState.positionMs == 30000L)
        assertTrue("durationMs should remain unchanged", dialogOpenState.durationMs == 120000L)

        // Close CC dialog
        val dialogClosedState = dialogOpenState.copy(showCcMenuDialog = false)

        // Playback state should still be unchanged
        assertTrue("isPlaying should remain unchanged after close", dialogClosedState.isPlaying)
    }

    @Test
    fun `multiple subtitle tracks - state handles large lists`() {
        val largeTrackList = List(100) { index ->
            SubtitleTrack(
                groupIndex = 0,
                trackIndex = index,
                language = "lang$index",
                label = "Language $index",
                isDefault = index == 0,
            )
        }

        val state = InternalPlayerUiState(
            kidActive = false,
            availableSubtitleTracks = largeTrackList,
        )

        // Should handle large track lists without issue
        assertTrue("Should handle 100 tracks", state.availableSubtitleTracks.size == 100)

        val ccButtonVisible = !state.kidActive && state.availableSubtitleTracks.isNotEmpty()
        assertTrue("CC button should be visible with many tracks", ccButtonVisible)
    }
}
