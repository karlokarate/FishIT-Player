package com.fishit.player.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [MimeDecider].
 *
 * Covers MIME-based and extension-based detection of video/audio.
 */
class MimeDeciderTest {

    // ========== MIME-Type Based Detection ==========

    @Test
    fun `inferKind returns VIDEO for video MIME types`() {
        assertEquals(MimeMediaKind.VIDEO, MimeDecider.inferKind("video/mp4", null))
        assertEquals(MimeMediaKind.VIDEO, MimeDecider.inferKind("video/x-matroska", null))
        assertEquals(MimeMediaKind.VIDEO, MimeDecider.inferKind("video/webm", null))
        assertEquals(MimeMediaKind.VIDEO, MimeDecider.inferKind("VIDEO/MP4", null)) // case insensitive
    }

    @Test
    fun `inferKind returns AUDIO for audio MIME types`() {
        assertEquals(MimeMediaKind.AUDIO, MimeDecider.inferKind("audio/mpeg", null))
        assertEquals(MimeMediaKind.AUDIO, MimeDecider.inferKind("audio/ogg", null))
        assertEquals(MimeMediaKind.AUDIO, MimeDecider.inferKind("AUDIO/FLAC", null)) // case insensitive
    }

    @Test
    fun `inferKind returns null for non-media MIME types`() {
        assertNull(MimeDecider.inferKind("application/pdf", null))
        assertNull(MimeDecider.inferKind("image/png", null))
        assertNull(MimeDecider.inferKind("text/plain", null))
    }

    // ========== Extension-Based Detection ==========

    @Test
    fun `inferKind returns VIDEO for video file extensions`() {
        assertEquals(MimeMediaKind.VIDEO, MimeDecider.inferKind(null, "movie.mp4"))
        assertEquals(MimeMediaKind.VIDEO, MimeDecider.inferKind(null, "movie.mkv"))
        assertEquals(MimeMediaKind.VIDEO, MimeDecider.inferKind(null, "movie.avi"))
        assertEquals(MimeMediaKind.VIDEO, MimeDecider.inferKind(null, "movie.MKV")) // case insensitive
    }

    @Test
    fun `inferKind returns AUDIO for audio file extensions`() {
        assertEquals(MimeMediaKind.AUDIO, MimeDecider.inferKind(null, "song.mp3"))
        assertEquals(MimeMediaKind.AUDIO, MimeDecider.inferKind(null, "song.flac"))
        assertEquals(MimeMediaKind.AUDIO, MimeDecider.inferKind(null, "song.ogg"))
        assertEquals(MimeMediaKind.AUDIO, MimeDecider.inferKind(null, "song.OPUS")) // case insensitive
    }

    @Test
    fun `inferKind returns null for non-media file extensions`() {
        assertNull(MimeDecider.inferKind(null, "document.pdf"))
        assertNull(MimeDecider.inferKind(null, "archive.zip"))
        assertNull(MimeDecider.inferKind(null, "no_extension"))
    }

    // ========== MIME Takes Precedence Over Extension ==========

    @Test
    fun `inferKind prefers MIME over conflicting extension`() {
        // MIME says video, but file extension is audio – MIME wins
        assertEquals(MimeMediaKind.VIDEO, MimeDecider.inferKind("video/mp4", "soundtrack.mp3"))
    }

    // ========== isPlayableMedia ==========

    @Test
    fun `isPlayableMedia returns true for video and audio`() {
        assertTrue(MimeDecider.isPlayableMedia("video/mp4", null))
        assertTrue(MimeDecider.isPlayableMedia("audio/mpeg", null))
        assertTrue(MimeDecider.isPlayableMedia(null, "movie.mkv"))
        assertTrue(MimeDecider.isPlayableMedia(null, "song.flac"))
    }

    @Test
    fun `isPlayableMedia returns false for non-media`() {
        assertFalse(MimeDecider.isPlayableMedia("application/zip", null))
        assertFalse(MimeDecider.isPlayableMedia(null, "file.rar"))
        assertFalse(MimeDecider.isPlayableMedia(null, null))
    }

    // ========== Edge Cases ==========

    @Test
    fun `inferKind handles null inputs`() {
        assertNull(MimeDecider.inferKind(null, null))
    }

    @Test
    fun `inferKind handles empty strings`() {
        assertNull(MimeDecider.inferKind("", ""))
        assertNull(MimeDecider.inferKind("", null))
        assertNull(MimeDecider.inferKind(null, ""))
    }

    @Test
    fun `inferKind handles files with multiple dots`() {
        assertEquals(MimeMediaKind.VIDEO, MimeDecider.inferKind(null, "Movie.2020.1080p.BluRay.x264.mkv"))
        assertEquals(MimeMediaKind.AUDIO, MimeDecider.inferKind(null, "Album.2020.FLAC.mp3"))
    }
}
