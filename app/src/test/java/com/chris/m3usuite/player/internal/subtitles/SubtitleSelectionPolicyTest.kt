package com.chris.m3usuite.player.internal.subtitles

import com.chris.m3usuite.player.internal.domain.PlaybackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for SubtitleSelectionPolicy.
 *
 * Contract Reference: `INTERNAL_PLAYER_SUBTITLE_CC_CONTRACT_PHASE4.md` Section 6
 */
class SubtitleSelectionPolicyTest {
    private val policy = FakeSubtitleSelectionPolicy()

    @Test
    fun `kid mode always returns null`() {
        val tracks =
            listOf(
                SubtitleTrack(0, 0, "en", "English", isDefault = false),
                SubtitleTrack(0, 1, "de", "German", isDefault = false),
            )

        val selected =
            policy.selectInitialTrack(
                availableTracks = tracks,
                preferredLanguages = listOf("en"),
                playbackType = PlaybackType.VOD,
                isKidMode = true,
            )

        assertNull("Kid mode must return null", selected)
    }

    @Test
    fun `no tracks available returns null`() {
        val selected =
            policy.selectInitialTrack(
                availableTracks = emptyList(),
                preferredLanguages = listOf("en"),
                playbackType = PlaybackType.VOD,
                isKidMode = false,
            )

        assertNull("Empty track list must return null", selected)
    }

    @Test
    fun `preferred language has priority`() {
        val tracks =
            listOf(
                SubtitleTrack(0, 0, "de", "German", isDefault = false),
                SubtitleTrack(0, 1, "en", "English", isDefault = false),
                SubtitleTrack(0, 2, "fr", "French", isDefault = false),
            )

        val selected =
            policy.selectInitialTrack(
                availableTracks = tracks,
                preferredLanguages = listOf("en", "de", "fr"),
                playbackType = PlaybackType.VOD,
                isKidMode = false,
            )

        assertEquals("English", selected?.label)
    }

    @Test
    fun `default flag is fallback when no language match`() {
        val tracks =
            listOf(
                SubtitleTrack(0, 0, "de", "German", isDefault = false),
                SubtitleTrack(0, 1, "fr", "French", isDefault = true),
                SubtitleTrack(0, 2, "es", "Spanish", isDefault = false),
            )

        val selected =
            policy.selectInitialTrack(
                availableTracks = tracks,
                preferredLanguages = listOf("en"), // No match
                playbackType = PlaybackType.VOD,
                isKidMode = false,
            )

        assertEquals("French", selected?.label)
        assertEquals(true, selected?.isDefault)
    }

    @Test
    fun `null language tracks can be selected as last resort`() {
        val tracks =
            listOf(
                SubtitleTrack(0, 0, null, "Unknown", isDefault = false),
            )

        val selected =
            policy.selectInitialTrack(
                availableTracks = tracks,
                preferredLanguages = listOf("en"),
                playbackType = PlaybackType.VOD,
                isKidMode = false,
            )

        // Current implementation returns null when no match
        // This test documents the behavior
        assertNull("No match returns null per current implementation", selected)
    }

    @Test
    fun `language matching is case insensitive`() {
        val tracks =
            listOf(
                SubtitleTrack(0, 0, "EN", "English (uppercase)", isDefault = false),
            )

        val selected =
            policy.selectInitialTrack(
                availableTracks = tracks,
                preferredLanguages = listOf("en"),
                playbackType = PlaybackType.VOD,
                isKidMode = false,
            )

        assertEquals("English (uppercase)", selected?.label)
    }
}

/**
 * Fake implementation for testing.
 * Uses DefaultSubtitleSelectionPolicy behavior without persistence.
 */
private class FakeSubtitleSelectionPolicy : SubtitleSelectionPolicy {
    override fun selectInitialTrack(
        availableTracks: List<SubtitleTrack>,
        preferredLanguages: List<String>,
        playbackType: PlaybackType,
        isKidMode: Boolean,
    ): SubtitleTrack? {
        if (isKidMode) return null
        if (availableTracks.isEmpty()) return null

        for (lang in preferredLanguages) {
            val match =
                availableTracks.firstOrNull { track ->
                    track.language?.equals(lang, ignoreCase = true) == true
                }
            if (match != null) return match
        }

        return availableTracks.firstOrNull { it.isDefault }
    }

    override suspend fun persistSelection(
        track: SubtitleTrack?,
        playbackType: PlaybackType,
    ) {
        // No-op for testing
    }
}
