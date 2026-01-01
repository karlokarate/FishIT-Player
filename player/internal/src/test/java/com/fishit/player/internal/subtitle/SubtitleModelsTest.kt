package com.fishit.player.internal.subtitle

import com.fishit.player.core.playermodel.SubtitleSelectionState
import com.fishit.player.core.playermodel.SubtitleSourceType
import com.fishit.player.core.playermodel.SubtitleTrack
import com.fishit.player.core.playermodel.SubtitleTrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for subtitle-related models and logic.
 *
 * Tests:
 * - SubtitleTrack creation and properties
 * - SubtitleSelectionState behavior
 * - Track ID handling
 */
class SubtitleModelsTest {
    // ========== SubtitleTrackId Tests ==========

    @Test
    fun `SubtitleTrackId OFF is special constant`() {
        assertEquals("OFF", SubtitleTrackId.OFF.value)
    }

    @Test
    fun `SubtitleTrackId equality works correctly`() {
        val id1 = SubtitleTrackId("1:0")
        val id2 = SubtitleTrackId("1:0")
        val id3 = SubtitleTrackId("1:1")

        assertEquals(id1, id2)
        assertFalse(id1 == id3)
    }

    // ========== SubtitleTrack Tests ==========

    @Test
    fun `SubtitleTrack fromMedia3 creates correct track`() {
        val track =
            SubtitleTrack.fromMedia3(
                groupIndex = 0,
                trackIndex = 1,
                language = "en",
                label = "English",
                isDefault = true,
                isForced = false,
                isClosedCaption = false,
                mimeType = "text/vtt",
            )

        assertEquals(SubtitleTrackId("0:1"), track.id)
        assertEquals("en", track.language)
        assertEquals("English", track.label)
        assertTrue(track.isDefault)
        assertFalse(track.isForced)
        assertFalse(track.isClosedCaption)
        assertEquals(SubtitleSourceType.EMBEDDED, track.sourceType)
        assertEquals("text/vtt", track.mimeType)
    }

    @Test
    fun `SubtitleTrack fromMedia3 generates label from language when null`() {
        val track =
            SubtitleTrack.fromMedia3(
                groupIndex = 0,
                trackIndex = 0,
                language = "de",
                label = null,
                isDefault = false,
            )

        assertEquals("de", track.label)
    }

    @Test
    fun `SubtitleTrack fromMedia3 generates fallback label when both null`() {
        val track =
            SubtitleTrack.fromMedia3(
                groupIndex = 0,
                trackIndex = 2,
                language = null,
                label = null,
                isDefault = false,
            )

        assertEquals("Track 3", track.label)
    }

    // ========== SubtitleSelectionState Tests ==========

    @Test
    fun `INITIAL state has isLoading true`() {
        val state = SubtitleSelectionState.INITIAL

        assertTrue(state.isLoading)
        assertTrue(state.availableTracks.isEmpty())
        assertEquals(SubtitleTrackId.OFF, state.selectedTrackId)
    }

    @Test
    fun `EMPTY state has isLoading false`() {
        val state = SubtitleSelectionState.EMPTY

        assertFalse(state.isLoading)
        assertTrue(state.availableTracks.isEmpty())
    }

    @Test
    fun `DISABLED state has isEnabled false`() {
        val state = SubtitleSelectionState.DISABLED

        assertFalse(state.isEnabled)
        assertFalse(state.isLoading)
    }

    @Test
    fun `selectedTrack returns null when OFF`() {
        val track = SubtitleTrack(id = SubtitleTrackId("0:0"), language = "en", label = "English")

        val state =
            SubtitleSelectionState(
                availableTracks = listOf(track),
                selectedTrackId = SubtitleTrackId.OFF,
            )

        assertNull(state.selectedTrack)
    }

    @Test
    fun `selectedTrack returns correct track when selected`() {
        val track1 = SubtitleTrack(id = SubtitleTrackId("0:0"), language = "en", label = "English")
        val track2 = SubtitleTrack(id = SubtitleTrackId("0:1"), language = "de", label = "Deutsch")

        val state =
            SubtitleSelectionState(
                availableTracks = listOf(track1, track2),
                selectedTrackId = SubtitleTrackId("0:1"),
                isEnabled = true,
            )

        assertEquals(track2, state.selectedTrack)
    }

    @Test
    fun `hasAvailableTracks returns correct value`() {
        val emptyState = SubtitleSelectionState(availableTracks = emptyList())
        assertFalse(emptyState.hasAvailableTracks)

        val populatedState =
            SubtitleSelectionState(
                availableTracks =
                    listOf(
                        SubtitleTrack(
                            id = SubtitleTrackId("0:0"),
                            language = "en",
                            label = "English",
                        ),
                    ),
            )
        assertTrue(populatedState.hasAvailableTracks)
    }

    @Test
    fun `trackCount returns correct count`() {
        val tracks =
            listOf(
                SubtitleTrack(
                    id = SubtitleTrackId("0:0"),
                    language = "en",
                    label = "English",
                ),
                SubtitleTrack(
                    id = SubtitleTrackId("0:1"),
                    language = "de",
                    label = "Deutsch",
                ),
                SubtitleTrack(
                    id = SubtitleTrackId("0:2"),
                    language = "es",
                    label = "Español",
                ),
            )

        val state = SubtitleSelectionState(availableTracks = tracks)

        assertEquals(3, state.trackCount)
    }
}
