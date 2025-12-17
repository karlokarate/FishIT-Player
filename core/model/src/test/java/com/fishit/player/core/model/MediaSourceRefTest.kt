package com.fishit.player.core.model

import com.fishit.player.core.model.ids.PipelineItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for MediaSourceRef and related quality/language parsing. */
class MediaSourceRefTest {

    // ========== MediaQuality.fromFilename Tests ==========

    @Test
    fun `parses 4K resolution from filename`() {
        val quality = MediaQuality.fromFilename("Movie.2020.2160p.UHD.BluRay.x265-GROUP")
        assertNotNull(quality)
        assertEquals(2160, quality?.resolution)
        assertEquals("HEVC", quality?.codec)
    }

    @Test
    fun `parses 1080p resolution from filename`() {
        val quality = MediaQuality.fromFilename("Movie.2020.1080p.BluRay.x264-GROUP")
        assertNotNull(quality)
        assertEquals(1080, quality?.resolution)
    }

    @Test
    fun `parses HDR10 from filename`() {
        val quality = MediaQuality.fromFilename("Movie.2020.2160p.HDR10.WEB-DL")
        assertNotNull(quality)
        assertEquals("HDR10", quality?.hdr)
    }

    @Test
    fun `parses Dolby Vision from filename`() {
        val quality = MediaQuality.fromFilename("Movie.2020.2160p.Dolby.Vision.WEB-DL")
        assertNotNull(quality)
        assertEquals("Dolby Vision", quality?.hdr)
    }

    @Test
    fun `parses HEVC codec from filename`() {
        val quality = MediaQuality.fromFilename("Movie.2020.1080p.BluRay.HEVC-GROUP")
        assertNotNull(quality)
        assertEquals("HEVC", quality?.codec)
    }

    @Test
    fun `returns null for filename without quality info`() {
        val quality = MediaQuality.fromFilename("random_video.mp4")
        assertNull(quality)
    }

    // ========== LanguageInfo.fromFilename Tests ==========

    @Test
    fun `parses German language from filename`() {
        val lang = LanguageInfo.fromFilename("Movie.2020.German.1080p.BluRay")
        assertNotNull(lang)
        assertTrue(lang!!.audioLanguages.contains("de"))
        assertEquals("de", lang.primaryAudio)
    }

    @Test
    fun `parses Multi-DL language from filename`() {
        val lang = LanguageInfo.fromFilename("Movie.2020.German.DL.1080p.BluRay")
        assertNotNull(lang)
        assertTrue(lang!!.isMulti)
    }

    @Test
    fun `parses dubbed from filename`() {
        val lang = LanguageInfo.fromFilename("Movie.2020.German.Dubbed.1080p")
        assertNotNull(lang)
        assertTrue(lang!!.isDubbed)
    }

    @Test
    fun `parses multiple languages from filename`() {
        val lang = LanguageInfo.fromFilename("Movie.2020.German.English.1080p")
        assertNotNull(lang)
        assertTrue(lang!!.audioLanguages.contains("de"))
        assertTrue(lang.audioLanguages.contains("en"))
    }

    // ========== MediaFormat.fromFilename Tests ==========

    @Test
    fun `parses MKV container from filename`() {
        val format = MediaFormat.fromFilename("Movie.2020.1080p.BluRay.x264.mkv")
        assertNotNull(format)
        assertEquals("mkv", format?.container)
    }

    @Test
    fun `parses MP4 container from filename`() {
        val format = MediaFormat.fromFilename("Movie.2020.1080p.WEB-DL.mp4")
        assertNotNull(format)
        assertEquals("mp4", format?.container)
    }

    @Test
    fun `parses DTS audio from filename`() {
        val format = MediaFormat.fromFilename("Movie.2020.1080p.BluRay.DTS.mkv")
        assertNotNull(format)
        assertEquals("dts", format?.audioCodec)
    }

    @Test
    fun `parses TrueHD audio from filename`() {
        val format = MediaFormat.fromFilename("Movie.2020.1080p.BluRay.TrueHD.mkv")
        assertNotNull(format)
        assertEquals("truehd", format?.audioCodec)
    }

    @Test
    fun `parses 5-1 channel layout from filename`() {
        val format = MediaFormat.fromFilename("Movie.2020.1080p.DD5.1.mkv")
        assertNotNull(format)
        assertEquals("5.1", format?.audioChannels)
    }

    // ========== MediaQuality.fromDimensions Tests ==========

    @Test
    fun `creates quality from 4K dimensions`() {
        val quality = MediaQuality.fromDimensions(3840, 2160)
        assertNotNull(quality)
        assertEquals(2160, quality?.resolution)
        assertEquals("4K", quality?.resolutionLabel)
    }

    @Test
    fun `creates quality from 1080p dimensions`() {
        val quality = MediaQuality.fromDimensions(1920, 1080)
        assertNotNull(quality)
        assertEquals(1080, quality?.resolution)
        assertEquals("1080p", quality?.resolutionLabel)
    }

    @Test
    fun `creates quality from 720p dimensions`() {
        val quality = MediaQuality.fromDimensions(1280, 720)
        assertNotNull(quality)
        assertEquals(720, quality?.resolution)
        assertEquals("720p", quality?.resolutionLabel)
    }

    // ========== SourceBadge Tests ==========

    @Test
    fun `returns correct badge for Telegram`() {
        val badge = SourceBadge.fromSourceType(SourceType.TELEGRAM)
        assertEquals(SourceBadge.TELEGRAM, badge)
        assertEquals("TG", badge.displayText)
    }

    @Test
    fun `returns correct badge for Xtream`() {
        val badge = SourceBadge.fromSourceType(SourceType.XTREAM)
        assertEquals(SourceBadge.XTREAM, badge)
        assertEquals("XC", badge.displayText)
    }

    @Test
    fun `returns correct badge for IO`() {
        val badge = SourceBadge.fromSourceType(SourceType.IO)
        assertEquals(SourceBadge.IO, badge)
        assertEquals("Local", badge.displayText)
    }

    // ========== MediaSourceRef Creation Tests ==========

    @Test
    fun `creates Telegram source ref correctly`() {
        val ref =
                createTelegramSourceRef(
                        chatId = 123L,
                        messageId = 456L,
                        chatName = "Movie Group",
                        filename = "Movie.2020.German.1080p.BluRay.mkv",
                        sizeBytes = 5_000_000_000L,
                )

        assertEquals(SourceType.TELEGRAM, ref.sourceType)
        assertEquals("telegram:123:456", ref.sourceId)
        assertEquals("Telegram: Movie Group", ref.sourceLabel)
        assertNotNull(ref.quality)
        assertEquals(1080, ref.quality?.resolution)
        assertNotNull(ref.languages)
        assertTrue(ref.languages!!.audioLanguages.contains("de"))
    }

    @Test
    fun `creates Xtream VOD source ref correctly`() {
        val ref =
                createXtreamVodSourceRef(
                        vodId = 789,
                        providerName = "Provider A",
                        title = "Movie.2020.4K.HDR",
                        containerExt = "mkv",
                )

        assertEquals(SourceType.XTREAM, ref.sourceType)
        assertEquals("xtream:vod:789", ref.sourceId)
        assertEquals("Xtream: Provider A", ref.sourceLabel)
    }

    @Test
    fun `creates IO source ref correctly`() {
        val ref =
                createIoSourceRef(
                        uri = "file:///storage/movies/test.mkv",
                        filename = "Movie.2020.German.DL.1080p.BluRay.x264.mkv",
                        sizeBytes = 4_500_000_000L,
                )

        assertEquals(SourceType.IO, ref.sourceType)
        assertTrue(ref.sourceId.startsWith("io:file:"))
        assertNotNull(ref.quality)
        assertNotNull(ref.languages)
        assertTrue(ref.languages!!.isMulti)
    }

    // ========== SourceIdParser Tests ==========

    @Test
    fun `extracts Telegram source type from ID`() {
        val type = SourceIdParser.extractSourceType("telegram:123:456")
        assertEquals(SourceType.TELEGRAM, type)
    }

    @Test
    fun `extracts Xtream source type from ID`() {
        val type = SourceIdParser.extractSourceType("xtream:vod:789")
        assertEquals(SourceType.XTREAM, type)
    }

    @Test
    fun `extracts IO source type from ID`() {
        val type = SourceIdParser.extractSourceType("io:file:/path/to/file")
        assertEquals(SourceType.IO, type)
    }

    @Test
    fun `parses Telegram source ID correctly`() {
        val result = SourceIdParser.parseTelegramSourceId("telegram:123:456")
        assertNotNull(result)
        assertEquals(123L, result?.first)
        assertEquals(456L, result?.second)
    }

    @Test
    fun `parses Xtream VOD ID correctly`() {
        val vodId = SourceIdParser.parseXtreamVodId("xtream:vod:789")
        assertEquals(789, vodId)
    }

    @Test
    fun `parses Xtream episode ID correctly`() {
        val result = SourceIdParser.parseXtreamEpisodeId("xtream:series:123:S01E05")
        assertNotNull(result)
        assertEquals(123, result?.first)
        assertEquals(1, result?.second)
        assertEquals(5, result?.third)
    }

    // ========== Display Label Tests ==========

    @Test
    fun `generates correct display label for quality`() {
        val quality =
                MediaQuality(
                        resolution = 2160,
                        resolutionLabel = "4K",
                        hdr = "HDR10",
                        codec = "HEVC",
                )
        val label = quality.toDisplayLabel()
        assertTrue(label.contains("4K"))
        assertTrue(label.contains("HDR10"))
        assertTrue(label.contains("HEVC"))
    }

    @Test
    fun `generates correct display label for source ref`() {
        val ref =
                MediaSourceRef(
                        sourceType = SourceType.TELEGRAM,
                        sourceId = PipelineItemId("telegram:123:456"),
                        sourceLabel = "Telegram: Movies",
                        quality = MediaQuality(resolution = 1080),
                        languages =
                                LanguageInfo(audioLanguages = listOf("de", "en"), isMulti = true),
                        format = MediaFormat(container = "mkv"),
                        sizeBytes = 4_500_000_000L,
                )

        val label = ref.toDisplayLabel()
        assertTrue(label.contains("1080"))
        assertTrue(label.contains("Multi"))
        assertTrue(label.contains("MKV"))
        assertTrue(label.contains("GB"))
    }
}
