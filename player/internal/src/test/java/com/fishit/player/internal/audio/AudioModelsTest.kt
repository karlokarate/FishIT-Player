package com.fishit.player.internal.audio

import com.fishit.player.core.playermodel.AudioChannelLayout
import com.fishit.player.core.playermodel.AudioCodecType
import com.fishit.player.core.playermodel.AudioSelectionState
import com.fishit.player.core.playermodel.AudioTrack
import com.fishit.player.core.playermodel.AudioTrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for audio track models and selection policy. */
class AudioModelsTest {
    // ========== AudioTrack Tests ==========

    @Test
    fun `AudioTrackId value class works correctly`() {
        val id = AudioTrackId("1:0")
        assertEquals("1:0", id.value)
    }

    @Test
    fun `AudioTrackId DEFAULT constant is correct`() {
        assertEquals("DEFAULT", AudioTrackId.DEFAULT.value)
    }

    @Test
    fun `AudioChannelLayout fromChannelCount maps correctly`() {
        assertEquals(AudioChannelLayout.MONO, AudioChannelLayout.fromChannelCount(1))
        assertEquals(AudioChannelLayout.STEREO, AudioChannelLayout.fromChannelCount(2))
        assertEquals(AudioChannelLayout.SURROUND_5_1, AudioChannelLayout.fromChannelCount(6))
        assertEquals(AudioChannelLayout.SURROUND_7_1, AudioChannelLayout.fromChannelCount(8))
        assertEquals(AudioChannelLayout.ATMOS, AudioChannelLayout.fromChannelCount(16))
        assertEquals(AudioChannelLayout.UNKNOWN, AudioChannelLayout.fromChannelCount(3))
    }

    @Test
    fun `AudioCodecType fromMimeType maps correctly`() {
        assertEquals(AudioCodecType.AAC, AudioCodecType.fromMimeType("audio/aac"))
        assertEquals(AudioCodecType.AC3, AudioCodecType.fromMimeType("audio/ac3"))
        assertEquals(AudioCodecType.EAC3, AudioCodecType.fromMimeType("audio/eac3"))
        assertEquals(AudioCodecType.EAC3, AudioCodecType.fromMimeType("audio/ec-3"))
        assertEquals(AudioCodecType.DTS, AudioCodecType.fromMimeType("audio/dts"))
        assertEquals(AudioCodecType.FLAC, AudioCodecType.fromMimeType("audio/flac"))
        assertEquals(AudioCodecType.OPUS, AudioCodecType.fromMimeType("audio/opus"))
        assertEquals(AudioCodecType.UNKNOWN, AudioCodecType.fromMimeType(null))
        assertEquals(AudioCodecType.UNKNOWN, AudioCodecType.fromMimeType("audio/unknown"))
    }

    @Test
    fun `AudioTrack fromMedia3 creates correct track`() {
        val track =
            AudioTrack.fromMedia3(
                groupIndex = 0,
                trackIndex = 1,
                language = "en",
                label = "English 5.1",
                channelCount = 6,
                isDefault = true,
                isDescriptive = false,
                bitrate = 640000,
                sampleRate = 48000,
                mimeType = "audio/eac3",
            )

        assertEquals("0:1", track.id.value)
        assertEquals("en", track.language)
        assertEquals("English 5.1", track.label)
        assertEquals(AudioChannelLayout.SURROUND_5_1, track.channelLayout)
        assertEquals(AudioCodecType.EAC3, track.codecType)
        assertTrue(track.isDefault)
        assertFalse(track.isDescriptive)
        assertEquals(640000, track.bitrate)
        assertEquals(48000, track.sampleRate)
    }

    @Test
    fun `AudioTrack fromMedia3 builds label when not provided`() {
        val track =
            AudioTrack.fromMedia3(
                groupIndex = 0,
                trackIndex = 0,
                language = "de",
                label = null,
                channelCount = 6,
                isDefault = false,
                isDescriptive = false,
            )

        assertTrue(track.label.contains("Deutsch"))
        assertTrue(track.label.contains("5.1"))
    }

    @Test
    fun `AudioTrack fromMedia3 includes AD indicator for descriptive tracks`() {
        val track =
            AudioTrack.fromMedia3(
                groupIndex = 0,
                trackIndex = 0,
                language = "en",
                label = null,
                channelCount = 2,
                isDefault = false,
                isDescriptive = true,
            )

        assertTrue(track.label.contains("(AD)"))
    }

    // ========== AudioSelectionState Tests ==========

    @Test
    fun `AudioSelectionState EMPTY is correctly initialized`() {
        val state = AudioSelectionState.EMPTY
        assertTrue(state.availableTracks.isEmpty())
        assertEquals(AudioTrackId.DEFAULT, state.selectedTrackId)
        assertNull(state.preferredLanguage)
        assertTrue(state.preferSurroundSound)
    }

    @Test
    fun `AudioSelectionState hasMultipleTracks returns correct value`() {
        val emptyState = AudioSelectionState.EMPTY
        assertFalse(emptyState.hasMultipleTracks)

        val singleTrack = createTestState(listOf(createEnglishStereoTrack()))
        assertFalse(singleTrack.hasMultipleTracks)

        val multipleTracks =
            createTestState(listOf(createEnglishStereoTrack(), createGermanSurroundTrack()))
        assertTrue(multipleTracks.hasMultipleTracks)
    }

    @Test
    fun `AudioSelectionState selectedTrack returns correct track`() {
        val track1 = createEnglishStereoTrack()
        val track2 = createGermanSurroundTrack()

        val state =
            AudioSelectionState(
                availableTracks = listOf(track1, track2),
                selectedTrackId = track2.id,
            )

        assertNotNull(state.selectedTrack)
        assertEquals(track2.id, state.selectedTrack?.id)
    }

    @Test
    fun `AudioSelectionState selectedTrack returns null for unknown id`() {
        val state =
            AudioSelectionState(
                availableTracks = listOf(createEnglishStereoTrack()),
                selectedTrackId = AudioTrackId("unknown"),
            )

        assertNull(state.selectedTrack)
    }

    @Test
    fun `AudioSelectionState tracksForLanguage filters correctly`() {
        val state =
            createTestState(
                listOf(
                    createEnglishStereoTrack(),
                    createEnglishSurroundTrack(),
                    createGermanSurroundTrack(),
                ),
            )

        val englishTracks = state.tracksForLanguage("en")
        assertEquals(2, englishTracks.size)

        val germanTracks = state.tracksForLanguage("de")
        assertEquals(1, germanTracks.size)

        val frenchTracks = state.tracksForLanguage("fr")
        assertTrue(frenchTracks.isEmpty())
    }

    // ========== Selection Policy Tests ==========

    @Test
    fun `selectBestTrack returns null for empty list`() {
        val state = AudioSelectionState.EMPTY
        assertNull(state.selectBestTrack())
    }

    @Test
    fun `selectBestTrack prefers preferred language`() {
        val state =
            AudioSelectionState(
                availableTracks =
                    listOf(createEnglishStereoTrack(), createGermanSurroundTrack()),
                preferredLanguage = "de",
            )

        val selected = state.selectBestTrack()
        assertNotNull(selected)
        assertEquals("de", selected?.language)
    }

    @Test
    fun `selectBestTrack prefers surround in preferred language when enabled`() {
        val state =
            AudioSelectionState(
                availableTracks =
                    listOf(createEnglishStereoTrack(), createEnglishSurroundTrack()),
                preferredLanguage = "en",
                preferSurroundSound = true,
            )

        val selected = state.selectBestTrack()
        assertNotNull(selected)
        assertEquals(AudioChannelLayout.SURROUND_5_1, selected?.channelLayout)
    }

    @Test
    fun `selectBestTrack falls back to stereo when surround disabled`() {
        val stereo = createEnglishStereoTrack()
        val state =
            AudioSelectionState(
                availableTracks = listOf(stereo, createEnglishSurroundTrack()),
                preferredLanguage = "en",
                preferSurroundSound = false,
            )

        val selected = state.selectBestTrack()
        assertNotNull(selected)
        // Should return first matching track (stereo in this case based on order)
        assertEquals(stereo.id, selected?.id)
    }

    @Test
    fun `selectBestTrack uses default track when no preferred language`() {
        val defaultTrack =
            AudioTrack(
                id = AudioTrackId("default"),
                language = "fr",
                label = "Français",
                channelLayout = AudioChannelLayout.STEREO,
                isDefault = true,
            )

        val state =
            AudioSelectionState(
                availableTracks =
                    listOf(
                        createEnglishStereoTrack(),
                        defaultTrack,
                        createGermanSurroundTrack(),
                    ),
                preferredLanguage = null,
                preferSurroundSound = false,
            )

        val selected = state.selectBestTrack()
        assertNotNull(selected)
        assertTrue(selected?.isDefault == true)
    }

    @Test
    fun `selectBestTrack prefers surround alternative to stereo default`() {
        val defaultStereo =
            AudioTrack(
                id = AudioTrackId("default"),
                language = "en",
                label = "English Stereo",
                channelLayout = AudioChannelLayout.STEREO,
                isDefault = true,
            )
        val surround =
            AudioTrack(
                id = AudioTrackId("surround"),
                language = "en",
                label = "English 5.1",
                channelLayout = AudioChannelLayout.SURROUND_5_1,
                isDefault = false,
            )

        val state =
            AudioSelectionState(
                availableTracks = listOf(defaultStereo, surround),
                preferredLanguage = null,
                preferSurroundSound = true,
            )

        val selected = state.selectBestTrack()
        assertNotNull(selected)
        assertEquals(AudioChannelLayout.SURROUND_5_1, selected?.channelLayout)
    }

    @Test
    fun `selectBestTrack falls back to first track when no match`() {
        val track1 = createEnglishStereoTrack()
        val track2 = createGermanSurroundTrack()

        val state =
            AudioSelectionState(
                availableTracks = listOf(track1, track2),
                preferredLanguage = "es", // Spanish not available
                preferSurroundSound = false,
            )

        val selected = state.selectBestTrack()
        assertNotNull(selected)
        assertEquals(track1.id, selected?.id)
    }

    // ========== Helper Methods ==========

    private fun createTestState(tracks: List<AudioTrack>): AudioSelectionState = AudioSelectionState(availableTracks = tracks)

    private fun createEnglishStereoTrack(): AudioTrack =
        AudioTrack(
            id = AudioTrackId("en_stereo"),
            language = "en",
            label = "English Stereo",
            channelLayout = AudioChannelLayout.STEREO,
            codecType = AudioCodecType.AAC,
            isDefault = false,
        )

    private fun createEnglishSurroundTrack(): AudioTrack =
        AudioTrack(
            id = AudioTrackId("en_surround"),
            language = "en",
            label = "English 5.1",
            channelLayout = AudioChannelLayout.SURROUND_5_1,
            codecType = AudioCodecType.EAC3,
            isDefault = false,
        )

    private fun createGermanSurroundTrack(): AudioTrack =
        AudioTrack(
            id = AudioTrackId("de_surround"),
            language = "de",
            label = "Deutsch 5.1",
            channelLayout = AudioChannelLayout.SURROUND_5_1,
            codecType = AudioCodecType.EAC3,
            isDefault = false,
        )
}
