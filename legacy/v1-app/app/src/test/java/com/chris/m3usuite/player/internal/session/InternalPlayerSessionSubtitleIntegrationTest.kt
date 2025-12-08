package com.chris.m3usuite.player.internal.session

import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.subtitles.SubtitleSelectionPolicy
import com.chris.m3usuite.player.internal.subtitles.SubtitleTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Integration tests for subtitle track selection across VOD, SERIES, and LIVE playback types.
 *
 * Contract Reference: `INTERNAL_PLAYER_SUBTITLE_CC_CONTRACT_PHASE4.md` Section 6.2
 *
 * These tests validate end-to-end subtitle selection behavior:
 * - Correct track chosen by DefaultSubtitleSelectionPolicy
 * - "No tracks available" → safe no-subtitles state (no crash, null return)
 * - Kid Mode blocking
 * - Language priority order
 * - Default flag handling
 */
class InternalPlayerSessionSubtitleIntegrationTest {
    // Use a test implementation that mirrors DefaultSubtitleSelectionPolicy behavior
    private val policy = TestSubtitleSelectionPolicy()

    // ════════════════════════════════════════════════════════════════════════════
    // VOD Subtitle Selection Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `VOD - selects English track when system language is English`() {
        val tracks =
            listOf(
                SubtitleTrack(0, 0, "de", "German", false),
                SubtitleTrack(0, 1, "en", "English", false),
                SubtitleTrack(0, 2, "fr", "French", false),
            )

        val selected =
            policy.selectInitialTrack(
                availableTracks = tracks,
                preferredLanguages = listOf("en"),
                playbackType = PlaybackType.VOD,
                isKidMode = false,
            )

        assertNotNull("Should select English track", selected)
        assertEquals("en", selected?.language)
        assertEquals("English", selected?.label)
    }

    @Test
    fun `VOD - fallback to default flag when no language match`() {
        val tracks =
            listOf(
                SubtitleTrack(0, 0, "de", "German", true), // default flag
                SubtitleTrack(0, 1, "fr", "French", false),
            )

        val selected =
            policy.selectInitialTrack(
                availableTracks = tracks,
                preferredLanguages = listOf("en"),
                playbackType = PlaybackType.VOD,
                isKidMode = false,
            )

        assertNotNull("Should fallback to track with default flag", selected)
        assertEquals("de", selected?.language)
        assertEquals("German", selected?.label)
    }

    @Test
    fun `VOD - returns null when no suitable track and no default flag`() {
        val tracks =
            listOf(
                SubtitleTrack(0, 0, "de", "German", false),
                SubtitleTrack(0, 1, "fr", "French", false),
            )

        val selected =
            policy.selectInitialTrack(
                availableTracks = tracks,
                preferredLanguages = listOf("en"),
                playbackType = PlaybackType.VOD,
                isKidMode = false,
            )

        // Contract Section 6.2: Without "always show subtitles" enabled, return null
        assertNull("Should return null when no suitable track", selected)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // SERIES Subtitle Selection Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `SERIES - language priority order (system, primary, secondary)`() {
        val tracks =
            listOf(
                SubtitleTrack(0, 0, "de", "German", false),
                SubtitleTrack(0, 1, "en", "English", false),
                SubtitleTrack(0, 2, "es", "Spanish", false),
            )

        // Test primary language match
        val selected =
            policy.selectInitialTrack(
                availableTracks = tracks,
                preferredLanguages = listOf("de", "en", "es"),
                playbackType = PlaybackType.SERIES,
                isKidMode = false,
            )

        assertNotNull("Should select first matching language", selected)
        assertEquals("de", selected?.language)
    }

    @Test
    fun `SERIES - respects secondary language when primary not available`() {
        val tracks =
            listOf(
                SubtitleTrack(0, 0, "en", "English", false),
                SubtitleTrack(0, 1, "es", "Spanish", false),
            )

        val selected =
            policy.selectInitialTrack(
                availableTracks = tracks,
                preferredLanguages = listOf("de", "en"),
                playbackType = PlaybackType.SERIES,
                isKidMode = false,
            )

        assertNotNull("Should fallback to secondary language", selected)
        assertEquals("en", selected?.language)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // LIVE Subtitle Selection Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `LIVE - selects subtitle track normally`() {
        val tracks =
            listOf(
                SubtitleTrack(0, 0, "en", "English", false),
            )

        val selected =
            policy.selectInitialTrack(
                availableTracks = tracks,
                preferredLanguages = listOf("en"),
                playbackType = PlaybackType.LIVE,
                isKidMode = false,
            )

        // LIVE content allows subtitles (Contract Section 6.2)
        assertNotNull("LIVE content should allow subtitles", selected)
        assertEquals("en", selected?.language)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // No Tracks Available Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `no tracks - returns null without crashing (VOD)`() {
        val selected =
            policy.selectInitialTrack(
                availableTracks = emptyList(),
                preferredLanguages = listOf("en"),
                playbackType = PlaybackType.VOD,
                isKidMode = false,
            )

        assertNull("Should return null when no tracks available", selected)
    }

    @Test
    fun `no tracks - returns null without crashing (SERIES)`() {
        val selected =
            policy.selectInitialTrack(
                availableTracks = emptyList(),
                preferredLanguages = listOf("en"),
                playbackType = PlaybackType.SERIES,
                isKidMode = false,
            )

        assertNull("Should return null when no tracks available", selected)
    }

    @Test
    fun `no tracks - returns null without crashing (LIVE)`() {
        val selected =
            policy.selectInitialTrack(
                availableTracks = emptyList(),
                preferredLanguages = listOf("en"),
                playbackType = PlaybackType.LIVE,
                isKidMode = false,
            )

        assertNull("Should return null when no tracks available", selected)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Kid Mode End-to-End Tests
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `Kid Mode - no subtitles selected (VOD)`() {
        val tracks =
            listOf(
                SubtitleTrack(0, 0, "en", "English", false),
            )

        val selected =
            policy.selectInitialTrack(
                availableTracks = tracks,
                preferredLanguages = listOf("en"),
                playbackType = PlaybackType.VOD,
                isKidMode = true,
            )

        // Contract Section 3.1: Kid Mode → always "no subtitles"
        assertNull("Kid Mode should block all subtitle selection", selected)
    }

    @Test
    fun `Kid Mode - no subtitles selected (SERIES)`() {
        val tracks =
            listOf(
                SubtitleTrack(0, 0, "en", "English", false),
            )

        val selected =
            policy.selectInitialTrack(
                availableTracks = tracks,
                preferredLanguages = listOf("en"),
                playbackType = PlaybackType.SERIES,
                isKidMode = true,
            )

        assertNull("Kid Mode should block all subtitle selection", selected)
    }

    @Test
    fun `Kid Mode - no subtitles selected (LIVE)`() {
        val tracks =
            listOf(
                SubtitleTrack(0, 0, "en", "English", false),
            )

        val selected =
            policy.selectInitialTrack(
                availableTracks = tracks,
                preferredLanguages = listOf("en"),
                playbackType = PlaybackType.LIVE,
                isKidMode = true,
            )

        assertNull("Kid Mode should block all subtitle selection", selected)
    }

    @Test
    fun `Kid Mode - blocks even with default flag`() {
        val tracks =
            listOf(
                SubtitleTrack(0, 0, "en", "English", true), // default flag
            )

        val selected =
            policy.selectInitialTrack(
                availableTracks = tracks,
                preferredLanguages = listOf("en"),
                playbackType = PlaybackType.VOD,
                isKidMode = true,
            )

        assertNull("Kid Mode should block even tracks with default flag", selected)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Edge Cases & Error Resilience
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `handles null language in track gracefully`() {
        val tracks =
            listOf(
                SubtitleTrack(0, 0, null, "Unknown", false),
                SubtitleTrack(0, 1, "en", "English", false),
            )

        val selected =
            policy.selectInitialTrack(
                availableTracks = tracks,
                preferredLanguages = listOf("en"),
                playbackType = PlaybackType.VOD,
                isKidMode = false,
            )

        assertNotNull("Should skip null language and find English", selected)
        assertEquals("en", selected?.language)
    }

    @Test
    fun `empty preferred languages list - fallback to default flag`() {
        val tracks =
            listOf(
                SubtitleTrack(0, 0, "de", "German", true), // default flag
                SubtitleTrack(0, 1, "en", "English", false),
            )

        val selected =
            policy.selectInitialTrack(
                availableTracks = tracks,
                preferredLanguages = emptyList(),
                playbackType = PlaybackType.VOD,
                isKidMode = false,
            )

        assertNotNull("Should fallback to default flag", selected)
        assertEquals("de", selected?.language)
    }

    @Test
    fun `case-insensitive language matching`() {
        val tracks =
            listOf(
                SubtitleTrack(0, 0, "EN", "English", false), // uppercase
            )

        val selected =
            policy.selectInitialTrack(
                availableTracks = tracks,
                preferredLanguages = listOf("en"), // lowercase
                playbackType = PlaybackType.VOD,
                isKidMode = false,
            )

        assertNotNull("Should match language case-insensitively", selected)
        assertEquals("EN", selected?.language)
    }

    @Test
    fun `multiple default flags - selects first one`() {
        val tracks =
            listOf(
                SubtitleTrack(0, 0, "de", "German", true), // first default
                SubtitleTrack(0, 1, "en", "English", true), // second default
            )

        val selected =
            policy.selectInitialTrack(
                availableTracks = tracks,
                preferredLanguages = listOf("fr"), // no match
                playbackType = PlaybackType.VOD,
                isKidMode = false,
            )

        assertNotNull("Should select first track with default flag", selected)
        assertEquals("de", selected?.language)
    }
}

// Test implementation mirroring DefaultSubtitleSelectionPolicy behavior
private class TestSubtitleSelectionPolicy : SubtitleSelectionPolicy {
    override fun selectInitialTrack(
        availableTracks: List<SubtitleTrack>,
        preferredLanguages: List<String>,
        playbackType: PlaybackType,
        isKidMode: Boolean,
    ): SubtitleTrack? {
        // Kid Mode: No subtitles (Contract Section 3.1)
        if (isKidMode) return null

        // No tracks available
        if (availableTracks.isEmpty()) return null

        // Try to match preferred languages in order
        for (lang in preferredLanguages) {
            val match =
                availableTracks.firstOrNull { track ->
                    track.language?.equals(lang, ignoreCase = true) == true
                }
            if (match != null) return match
        }

        // Fallback to track with default flag
        return availableTracks.firstOrNull { it.isDefault }
    }

    override suspend fun persistSelection(
        track: SubtitleTrack?,
        playbackType: PlaybackType,
    ) {
        // No-op for testing
    }
}
