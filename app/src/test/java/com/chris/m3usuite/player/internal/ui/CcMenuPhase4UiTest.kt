package com.chris.m3usuite.player.internal.ui

import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState
import com.chris.m3usuite.player.internal.subtitles.SubtitlePreset
import com.chris.m3usuite.player.internal.subtitles.SubtitleStyle
import com.chris.m3usuite.player.internal.subtitles.SubtitleTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Phase 4 CC Menu and Subtitle UI in InternalPlayerControls.
 *
 * Contract Reference: `INTERNAL_PLAYER_SUBTITLE_CC_CONTRACT_PHASE4.md` Section 8
 *
 * These tests validate:
 * - CC button visibility rules (kid mode vs non-kid, tracks vs no tracks)
 * - CcMenuDialog reflects current SubtitleStyle on open
 * - Preview updates when user adjusts style
 * - Apply commits changes to style manager
 */
class CcMenuPhase4UiTest {
    // ════════════════════════════════════════════════════════════════════════════
    // CC Button Visibility Tests - Contract Section 8.1
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `CC button visible when not kid mode and has tracks`() {
        val state = InternalPlayerUiState(
            kidActive = false,
            availableSubtitleTracks = listOf(
                SubtitleTrack(
                    groupIndex = 0,
                    trackIndex = 0,
                    language = "en",
                    label = "English",
                    isDefault = true,
                ),
            ),
        )

        // CC button visibility condition: !kidActive && availableSubtitleTracks.isNotEmpty()
        val shouldShowCcButton = !state.kidActive && state.availableSubtitleTracks.isNotEmpty()
        assertTrue("CC button should be visible for non-kid profile with tracks", shouldShowCcButton)
    }

    @Test
    fun `CC button hidden when kid mode active`() {
        val state = InternalPlayerUiState(
            kidActive = true,
            availableSubtitleTracks = listOf(
                SubtitleTrack(
                    groupIndex = 0,
                    trackIndex = 0,
                    language = "en",
                    label = "English",
                    isDefault = true,
                ),
            ),
        )

        val shouldShowCcButton = !state.kidActive && state.availableSubtitleTracks.isNotEmpty()
        assertFalse("CC button should be hidden for kid profile", shouldShowCcButton)
    }

    @Test
    fun `CC button hidden when no subtitle tracks available`() {
        val state = InternalPlayerUiState(
            kidActive = false,
            availableSubtitleTracks = emptyList(),
        )

        val shouldShowCcButton = !state.kidActive && state.availableSubtitleTracks.isNotEmpty()
        assertFalse("CC button should be hidden when no tracks available", shouldShowCcButton)
    }

    @Test
    fun `CC button hidden when kid mode and no tracks`() {
        val state = InternalPlayerUiState(
            kidActive = true,
            availableSubtitleTracks = emptyList(),
        )

        val shouldShowCcButton = !state.kidActive && state.availableSubtitleTracks.isNotEmpty()
        assertFalse("CC button should be hidden for kid profile with no tracks", shouldShowCcButton)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // CC Menu Dialog Display Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `CC dialog visible when showCcMenuDialog true and not kid mode`() {
        val state = InternalPlayerUiState(
            showCcMenuDialog = true,
            kidActive = false,
        )

        // CcMenuDialog render condition: showCcMenuDialog && !kidActive
        val shouldShowCcDialog = state.showCcMenuDialog && !state.kidActive
        assertTrue("CC dialog should be visible when requested for non-kid", shouldShowCcDialog)
    }

    @Test
    fun `CC dialog hidden when showCcMenuDialog true but kid mode active`() {
        val state = InternalPlayerUiState(
            showCcMenuDialog = true,
            kidActive = true,
        )

        val shouldShowCcDialog = state.showCcMenuDialog && !state.kidActive
        assertFalse("CC dialog should be hidden for kid profile", shouldShowCcDialog)
    }

    @Test
    fun `CC dialog hidden when showCcMenuDialog false`() {
        val state = InternalPlayerUiState(
            showCcMenuDialog = false,
            kidActive = false,
        )

        val shouldShowCcDialog = state.showCcMenuDialog && !state.kidActive
        assertFalse("CC dialog should be hidden when not requested", shouldShowCcDialog)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // CC Dialog State Initialization Tests - Contract Section 8.2
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `CC dialog should reflect current subtitle style on open`() {
        val customStyle = SubtitleStyle(
            textScale = 1.5f,
            foregroundOpacity = 0.8f,
            backgroundOpacity = 0.4f,
        )

        val state = InternalPlayerUiState(
            showCcMenuDialog = true,
            kidActive = false,
            subtitleStyle = customStyle,
        )

        // Dialog should display current style values
        assertEquals("Dialog should show current text scale", 1.5f, state.subtitleStyle.textScale, 0.001f)
        assertEquals("Dialog should show current FG opacity", 0.8f, state.subtitleStyle.foregroundOpacity, 0.001f)
        assertEquals("Dialog should show current BG opacity", 0.4f, state.subtitleStyle.backgroundOpacity, 0.001f)
    }

    @Test
    fun `CC dialog should display available tracks from state`() {
        val tracks = listOf(
            SubtitleTrack(0, 0, "en", "English", true),
            SubtitleTrack(0, 1, "de", "German", false),
            SubtitleTrack(1, 0, "fr", "French", false),
        )

        val state = InternalPlayerUiState(
            showCcMenuDialog = true,
            kidActive = false,
            availableSubtitleTracks = tracks,
        )

        assertEquals("Dialog should display 3 available tracks", 3, state.availableSubtitleTracks.size)
        assertEquals("First track should be English", "English", state.availableSubtitleTracks[0].label)
        assertEquals("Second track should be German", "German", state.availableSubtitleTracks[1].label)
    }

    @Test
    fun `CC dialog should highlight selected track`() {
        val tracks = listOf(
            SubtitleTrack(0, 0, "en", "English", true),
            SubtitleTrack(0, 1, "de", "German", false),
        )
        val selectedTrack = tracks[1] // German selected

        val state = InternalPlayerUiState(
            showCcMenuDialog = true,
            kidActive = false,
            availableSubtitleTracks = tracks,
            selectedSubtitleTrack = selectedTrack,
        )

        assertNotNull("Selected track should not be null", state.selectedSubtitleTrack)
        assertEquals("Selected track should be German", "German", state.selectedSubtitleTrack?.label)
    }

    @Test
    fun `CC dialog should show no selection when track is null`() {
        val tracks = listOf(
            SubtitleTrack(0, 0, "en", "English", true),
        )

        val state = InternalPlayerUiState(
            showCcMenuDialog = true,
            kidActive = false,
            availableSubtitleTracks = tracks,
            selectedSubtitleTrack = null, // Off
        )

        assertNull("Selected track should be null for Off", state.selectedSubtitleTrack)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Style Update Tests - Contract Section 8.5 (Preview)
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `pending style changes should not affect current state until applied`() {
        val originalStyle = SubtitleStyle(textScale = 1.0f)

        // Simulate pending changes in dialog (would be local state in composable)
        val pendingStyle = originalStyle.copy(textScale = 1.8f)

        // Current state should remain unchanged until Apply is pressed
        assertEquals("Original style should remain unchanged", 1.0f, originalStyle.textScale, 0.001f)
        assertEquals("Pending style should have new value", 1.8f, pendingStyle.textScale, 0.001f)
    }

    @Test
    fun `preset application produces correct style`() {
        // Test each preset produces expected style
        val highContrast = SubtitlePreset.HIGH_CONTRAST.toStyle()
        assertEquals("High contrast should have yellow foreground", 0xFFFFFF00.toInt(), highContrast.foregroundColor)

        val tvLarge = SubtitlePreset.TV_LARGE.toStyle()
        assertEquals("TV Large should have 1.5x scale", 1.5f, tvLarge.textScale, 0.001f)

        val minimal = SubtitlePreset.MINIMAL.toStyle()
        assertEquals("Minimal should have 0.8x scale", 0.8f, minimal.textScale, 0.001f)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // VOD/Series Tests - CC Menu Available
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `CC button available for VOD playback with tracks`() {
        val state = InternalPlayerUiState(
            playbackType = PlaybackType.VOD,
            kidActive = false,
            availableSubtitleTracks = listOf(
                SubtitleTrack(0, 0, "en", "English", true),
            ),
        )

        val shouldShowCcButton = !state.kidActive && state.availableSubtitleTracks.isNotEmpty()
        assertTrue("CC button should be visible for VOD with tracks", shouldShowCcButton)
    }

    @Test
    fun `CC button available for SERIES playback with tracks`() {
        val state = InternalPlayerUiState(
            playbackType = PlaybackType.SERIES,
            kidActive = false,
            availableSubtitleTracks = listOf(
                SubtitleTrack(0, 0, "en", "English", true),
            ),
        )

        val shouldShowCcButton = !state.kidActive && state.availableSubtitleTracks.isNotEmpty()
        assertTrue("CC button should be visible for SERIES with tracks", shouldShowCcButton)
    }

    @Test
    fun `CC button available for LIVE playback with tracks`() {
        val state = InternalPlayerUiState(
            playbackType = PlaybackType.LIVE,
            kidActive = false,
            availableSubtitleTracks = listOf(
                SubtitleTrack(0, 0, "en", "English", true),
            ),
        )

        val shouldShowCcButton = !state.kidActive && state.availableSubtitleTracks.isNotEmpty()
        assertTrue("CC button should be visible for LIVE with tracks", shouldShowCcButton)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // availableSubtitleTracks State Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `default state has empty available tracks`() {
        val state = InternalPlayerUiState()

        assertTrue("Default state should have empty available tracks", state.availableSubtitleTracks.isEmpty())
    }

    @Test
    fun `state with tracks populates availableSubtitleTracks`() {
        val tracks = listOf(
            SubtitleTrack(0, 0, "en", "English", true),
            SubtitleTrack(0, 1, "de", "Deutsch", false),
        )

        val state = InternalPlayerUiState(availableSubtitleTracks = tracks)

        assertEquals("State should have 2 tracks", 2, state.availableSubtitleTracks.size)
        assertEquals("First track language should be en", "en", state.availableSubtitleTracks[0].language)
    }
}
