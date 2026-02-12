package com.fishit.player.pipeline.xtream.integration

import com.fishit.player.core.model.SourceIdParser
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.ids.XtreamIdCodec
import com.fishit.player.core.model.ids.XtreamParsedSourceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SSOT Roundtrip Verification Test
 *
 * Verifies that ALL identity formats exchanged between modules at runtime
 * are correctly generated and parsed via the SSOT generators/parsers.
 *
 * These tests verify the exact delegation chains introduced in the SSOT
 * inline-parsing elimination session:
 *
 * 1. XtreamIdCodec: format ↔ parse roundtrip for ALL content types
 * 2. XtreamIdCodec: NX sourceKey format (src:xtream:account:...) → parse
 * 3. XtreamIdCodec: Legacy composite episodes (Format C, underscore)
 * 4. SourceIdParser: Telegram sourceId → parseTelegramSourceId
 * 5. SourceIdParser: Xtream sourceId → parseXtreamVodId / parseXtreamEpisodeId
 * 6. Cross-module: Data layer produces → Feature layer parses
 */
class SsotRoundtripVerificationTest {

    // =========================================================================
    // 1. XtreamIdCodec: Standard Format ↔ Parse Roundtrips
    // =========================================================================

    @Test
    fun `VOD format roundtrip`() {
        val sourceId = XtreamIdCodec.vod(12345)
        assertEquals("xtream:vod:12345", sourceId)

        val parsed = XtreamIdCodec.parse(sourceId)
        assertNotNull(parsed)
        assertTrue(parsed is XtreamParsedSourceId.Vod)
        assertEquals(12345L, parsed.vodId)
    }

    @Test
    fun `Series format roundtrip`() {
        val sourceId = XtreamIdCodec.series(678)
        assertEquals("xtream:series:678", sourceId)

        val parsed = XtreamIdCodec.parse(sourceId)
        assertNotNull(parsed)
        assertTrue(parsed is XtreamParsedSourceId.Series)
        assertEquals(678L, parsed.seriesId)
    }

    @Test
    fun `Episode direct ID roundtrip`() {
        val sourceId = XtreamIdCodec.episode(999)
        assertEquals("xtream:episode:999", sourceId)

        val parsed = XtreamIdCodec.parse(sourceId)
        assertNotNull(parsed)
        assertTrue(parsed is XtreamParsedSourceId.Episode)
        assertEquals(999L, parsed.episodeId)
    }

    @Test
    fun `Episode composite roundtrip`() {
        val sourceId = XtreamIdCodec.episodeComposite(100, 2, 5)
        assertEquals("xtream:episode:series:100:s2:e5", sourceId)

        val parsed = XtreamIdCodec.parse(sourceId)
        assertNotNull(parsed)
        assertTrue(parsed is XtreamParsedSourceId.EpisodeComposite)
        assertEquals(100L, parsed.seriesId)
        assertEquals(2, parsed.season)
        assertEquals(5, parsed.episode)
    }

    @Test
    fun `Live format roundtrip`() {
        val sourceId = XtreamIdCodec.live(42)
        assertEquals("xtream:live:42", sourceId)

        val parsed = XtreamIdCodec.parse(sourceId)
        assertNotNull(parsed)
        assertTrue(parsed is XtreamParsedSourceId.Live)
        assertEquals(42L, parsed.channelId)
    }

    // =========================================================================
    // 2. XtreamIdCodec: NX sourceKey format (src:xtream:account:...)
    //    This is the critical runtime path: Data layer stores NX sourceKeys,
    //    Feature layer parses them with XtreamIdCodec.parse()
    // =========================================================================

    @Test
    fun `NX VOD sourceKey parsed correctly`() {
        val nxSourceKey = "src:xtream:myserver:vod:12345"
        val parsed = XtreamIdCodec.parse(nxSourceKey)
        assertNotNull(parsed, "NX vod sourceKey must be parseable")
        assertTrue(parsed is XtreamParsedSourceId.Vod)
        assertEquals(12345L, parsed.vodId)
    }

    @Test
    fun `NX Series sourceKey parsed correctly`() {
        val nxSourceKey = "src:xtream:myserver:series:678"
        val parsed = XtreamIdCodec.parse(nxSourceKey)
        assertNotNull(parsed, "NX series sourceKey must be parseable")
        assertTrue(parsed is XtreamParsedSourceId.Series)
        assertEquals(678L, parsed.seriesId)
    }

    @Test
    fun `NX Live sourceKey parsed correctly`() {
        val nxSourceKey = "src:xtream:myserver:live:42"
        val parsed = XtreamIdCodec.parse(nxSourceKey)
        assertNotNull(parsed, "NX live sourceKey must be parseable")
        assertTrue(parsed is XtreamParsedSourceId.Live)
        assertEquals(42L, parsed.channelId)
    }

    @Test
    fun `NX Episode composite sourceKey parsed correctly`() {
        // This is what NxXtreamSeriesIndexRepository stores in DB:
        // src:xtream:{account}:episode:series:{seriesId}:s{season}:e{episode}
        val nxSourceKey = "src:xtream:myserver:episode:series:100:s2:e5"
        val parsed = XtreamIdCodec.parse(nxSourceKey)
        assertNotNull(parsed, "NX episode composite sourceKey must be parseable")
        assertTrue(parsed is XtreamParsedSourceId.EpisodeComposite)
        assertEquals(100L, parsed.seriesId)
        assertEquals(2, parsed.season)
        assertEquals(5, parsed.episode)
    }

    @Test
    fun `NX Episode direct ID sourceKey parsed correctly`() {
        val nxSourceKey = "src:xtream:myserver:episode:54321"
        val parsed = XtreamIdCodec.parse(nxSourceKey)
        assertNotNull(parsed, "NX episode direct ID sourceKey must be parseable")
        assertTrue(parsed is XtreamParsedSourceId.Episode)
        assertEquals(54321L, parsed.episodeId)
    }

    @Test
    fun `NX xc prefix variant parsed correctly`() {
        // Some accounts use xc: instead of xtream:
        val nxSourceKey = "src:xc:myserver:vod:789"
        val parsed = XtreamIdCodec.parse(nxSourceKey)
        assertNotNull(parsed, "NX xc: sourceKey must be parseable")
        assertTrue(parsed is XtreamParsedSourceId.Vod)
        assertEquals(789L, parsed.vodId)
    }

    // =========================================================================
    // 3. XtreamIdCodec: Legacy Composite Episodes
    //    These formats exist in DB from older code versions
    // =========================================================================

    @Test
    fun `Legacy series-episode SxxExx format`() {
        // Legacy format: xtream:series:{seriesId}:S{season}E{episode}
        val parsed = XtreamIdCodec.parse("xtream:series:123:S01E05")
        assertNotNull(parsed, "Legacy SxxExx episode format must be parseable")
        assertTrue(parsed is XtreamParsedSourceId.EpisodeComposite)
        assertEquals(123L, parsed.seriesId)
        assertEquals(1, parsed.season)
        assertEquals(5, parsed.episode)
    }

    @Test
    fun `Legacy series without SxxExx is plain series`() {
        // Regular series without episode suffix should remain Series
        val parsed = XtreamIdCodec.parse("xtream:series:123")
        assertNotNull(parsed)
        assertTrue(parsed is XtreamParsedSourceId.Series)
        assertEquals(123L, (parsed as XtreamParsedSourceId.Series).seriesId)
    }

    @Test
    fun `Legacy episode Format C - xtream_episode_seriesId_season_episode`() {
        // Format C: xtream:episode:{seriesId}:{season}:{episode}
        val parsed = XtreamIdCodec.parse("xtream:episode:100:2:5")
        assertNotNull(parsed, "Legacy Format C must be parseable")
        assertTrue(parsed is XtreamParsedSourceId.EpisodeComposite)
        assertEquals(100L, parsed.seriesId)
        assertEquals(2, parsed.season)
        assertEquals(5, parsed.episode)
    }

    @Test
    fun `Legacy episode underscore format`() {
        // Underscore format: xtream:episode:{seriesId}_{season}_{episode}
        val parsed = XtreamIdCodec.parse("xtream:episode:100_2_5")
        assertNotNull(parsed, "Underscore episode format must be parseable")
        assertTrue(parsed is XtreamParsedSourceId.EpisodeComposite)
        assertEquals(100L, parsed.seriesId)
        assertEquals(2, parsed.season)
        assertEquals(5, parsed.episode)
    }

    @Test
    fun `NX underscore episode format`() {
        // NX format with underscore itemKey (exists in older DBs):
        // src:xtream:myserver:episode:{seriesId}_{season}_{episode}
        val nxSourceKey = "src:xtream:myserver:episode:100_2_5"
        val parsed = XtreamIdCodec.parse(nxSourceKey)
        assertNotNull(parsed, "NX underscore episode must be parseable")
        assertTrue(parsed is XtreamParsedSourceId.EpisodeComposite)
        assertEquals(100L, parsed.seriesId)
        assertEquals(2, parsed.season)
        assertEquals(5, parsed.episode)
    }

    // =========================================================================
    // 4. SourceIdParser: Telegram sourceId parsing
    //    Used by: PlayMediaUseCase, PlayerNavViewModel, TelegramPlaybackSourceFactory
    // =========================================================================

    @Test
    fun `SourceIdParser parses msg format`() {
        val result = SourceIdParser.parseTelegramSourceId("msg:123456:789")
        assertNotNull(result, "msg: format must be parseable")
        assertEquals(123456L, result.first)
        assertEquals(789L, result.second)
    }

    @Test
    fun `SourceIdParser parses telegram format`() {
        val result = SourceIdParser.parseTelegramSourceId("telegram:123456:789")
        assertNotNull(result, "telegram: format must be parseable")
        assertEquals(123456L, result.first)
        assertEquals(789L, result.second)
    }

    @Test
    fun `SourceIdParser parses tg format`() {
        val result = SourceIdParser.parseTelegramSourceId("tg:123456:789")
        assertNotNull(result, "tg: format must be parseable")
        assertEquals(123456L, result.first)
        assertEquals(789L, result.second)
    }

    @Test
    fun `SourceIdParser rejects non-telegram formats`() {
        assertNull(SourceIdParser.parseTelegramSourceId("xtream:vod:123"))
        assertNull(SourceIdParser.parseTelegramSourceId("io:file:abc"))
        assertNull(SourceIdParser.parseTelegramSourceId(""))
    }

    @Test
    fun `SourceIdParser detects msg as Telegram`() {
        assertTrue(SourceIdParser.isTelegram("msg:123:456"))
        assertTrue(SourceIdParser.isTelegram("telegram:123:456"))
        assertTrue(SourceIdParser.isTelegram("tg:123:456"))
    }

    @Test
    fun `SourceIdParser extractSourceType handles all prefixes`() {
        assertEquals(SourceType.TELEGRAM, SourceIdParser.extractSourceType("msg:123:456"))
        assertEquals(SourceType.TELEGRAM, SourceIdParser.extractSourceType("telegram:123:456"))
        assertEquals(SourceType.TELEGRAM, SourceIdParser.extractSourceType("tg:123:456"))
        assertEquals(SourceType.XTREAM, SourceIdParser.extractSourceType("xtream:vod:123"))
        assertEquals(SourceType.XTREAM, SourceIdParser.extractSourceType("xc:vod:123"))
        assertEquals(SourceType.IO, SourceIdParser.extractSourceType("io:file:abc"))
        assertEquals(SourceType.IO, SourceIdParser.extractSourceType("local:file:abc"))
    }

    // =========================================================================
    // 5. SourceIdParser: Xtream VOD/Episode parsing
    //    Used by: PlayMediaUseCase, PlayerNavViewModel
    // =========================================================================

    @Test
    fun `SourceIdParser parseXtreamVodId delegates to XtreamIdCodec`() {
        // Confirms SourceIdParser.parseXtreamVodId uses XtreamIdCodec under the hood
        assertEquals(123, SourceIdParser.parseXtreamVodId("xtream:vod:123"))
        assertEquals(999, SourceIdParser.parseXtreamVodId("xtream:vod:999"))
        assertNull(SourceIdParser.parseXtreamVodId("xtream:series:123"))
        assertNull(SourceIdParser.parseXtreamVodId("telegram:123:456"))
    }

    @Test
    fun `SourceIdParser parseXtreamEpisodeId delegates to XtreamIdCodec`() {
        // Composite format
        val result = SourceIdParser.parseXtreamEpisodeId("xtream:episode:series:100:s2:e5")
        assertNotNull(result)
        assertEquals(100, result.first)    // seriesId
        assertEquals(2, result.second)     // season
        assertEquals(5, result.third)      // episode
    }

    @Test
    fun `SourceIdParser parseXtreamEpisodeId handles legacy Format C`() {
        val result = SourceIdParser.parseXtreamEpisodeId("xtream:episode:100:2:5")
        assertNotNull(result, "Legacy Format C must work through SourceIdParser")
        assertEquals(100, result.first)
        assertEquals(2, result.second)
        assertEquals(5, result.third)
    }

    @Test
    fun `SourceIdParser parseXtreamEpisodeId handles legacy SxxExx on series`() {
        val result = SourceIdParser.parseXtreamEpisodeId("xtream:series:123:S01E05")
        assertNotNull(result, "Legacy SxxExx series format must work through SourceIdParser")
        assertEquals(123, result.first)
        assertEquals(1, result.second)
        assertEquals(5, result.third)
    }

    // =========================================================================
    // 6. Cross-module runtime scenarios
    //    Simulates real data flow: Producer creates → Consumer parses
    // =========================================================================

    @Test
    fun `Scenario - Pipeline produces VOD sourceId, Feature parses for playback`() {
        // Pipeline creates sourceId via XtreamIdCodec
        val sourceId = XtreamIdCodec.vod(42)

        // Feature layer parses for playback extras
        val parsed = XtreamIdCodec.parse(sourceId)
        assertNotNull(parsed)
        assertTrue(parsed is XtreamParsedSourceId.Vod)

        // PlayMediaUseCase would do:
        val vodIdForHints = parsed.vodId.toString()
        assertEquals("42", vodIdForHints)
    }

    @Test
    fun `Scenario - Data layer stores NX episode, Feature reads for series navigation`() {
        // NxXtreamSeriesIndexRepository stores:
        // src:xtream:{account}:episode:series:{seriesId}:s{season}:e{episode}
        val storedSourceKey = "src:xtream:provider1:episode:series:500:s3:e12"

        // SeriesEpisodeUseCases.parseEpisodeIds() calls XtreamIdCodec.parse():
        val parsed = XtreamIdCodec.parse(storedSourceKey)
        assertNotNull(parsed, "Feature layer must parse NX episode sourceKey")
        assertTrue(parsed is XtreamParsedSourceId.EpisodeComposite)
        assertEquals(500L, parsed.seriesId)
        assertEquals(3, parsed.season)
        assertEquals(12, parsed.episode)
    }

    @Test
    fun `Scenario - UnifiedDetailViewModel extracts seriesId from NX source`() {
        // NX sourceKey format stored in DB
        val nxSourceKey = "src:xtream:provider1:series:200"

        // UnifiedDetailViewModel calls XtreamIdCodec.parse()
        val parsed = XtreamIdCodec.parse(nxSourceKey)
        assertNotNull(parsed)
        assertTrue(parsed is XtreamParsedSourceId.Series)
        val seriesId = parsed.seriesId.toInt()
        assertEquals(200, seriesId)
    }

    @Test
    fun `Scenario - DetailEnrichmentService checks isVod via XtreamIdCodec`() {
        assertTrue(XtreamIdCodec.isVod("xtream:vod:123"))
        assertTrue(XtreamIdCodec.isSeries("xtream:series:456"))
        assertTrue(XtreamIdCodec.isLive("xtream:live:789"))

        // Non-matching cases
        assertTrue(!XtreamIdCodec.isVod("xtream:series:123"))
        assertTrue(!XtreamIdCodec.isSeries("xtream:vod:456"))
    }

    @Test
    fun `Scenario - Telegram pipeline produces, playback factory consumes`() {
        // TelegramRawMetadataExtensions creates: TelegramRemoteId(chatId, messageId).toSourceKey()
        // which produces "msg:{chatId}:{messageId}"
        val sourceId = "msg:-1001234567890:42"

        // TelegramPlaybackSourceFactoryImpl parses via SourceIdParser:
        val parsed = SourceIdParser.parseTelegramSourceId(sourceId)
        assertNotNull(parsed, "Playback factory must parse msg: format")
        assertEquals(-1001234567890L, parsed.first)
        assertEquals(42L, parsed.second)
    }

    @Test
    fun `Scenario - Negative chatId handled correctly through SSOT chain`() {
        // Telegram chat IDs are commonly negative (e.g., -1001234567890 for supergroups)
        val sourceId = "msg:-1001234567890:999"

        val parsed = SourceIdParser.parseTelegramSourceId(sourceId)
        assertNotNull(parsed)
        assertEquals(-1001234567890L, parsed.first)
        assertEquals(999L, parsed.second)
    }

    @Test
    fun `Scenario - XtreamIdCodec extractVodId used by SourceIdParser`() {
        // SourceIdParser.parseXtreamVodId delegates to XtreamIdCodec.extractVodId
        val vodId = XtreamIdCodec.extractVodId("xtream:vod:12345")
        assertNotNull(vodId)
        assertEquals(12345L, vodId)

        // Confirm it extracts from NX format too (via parse)
        val nxVodId = XtreamIdCodec.parse("src:xtream:account:vod:6789")
        assertNotNull(nxVodId)
        assertTrue(nxVodId is XtreamParsedSourceId.Vod)
        assertEquals(6789L, nxVodId.vodId)
    }

    @Test
    fun `Scenario - Season 0 specials are valid`() {
        // Xtream uses season 0 for "specials" — must not be rejected
        val sourceId = XtreamIdCodec.episodeComposite(100, 0, 1)
        assertEquals("xtream:episode:series:100:s0:e1", sourceId)

        val parsed = XtreamIdCodec.parse(sourceId)
        assertNotNull(parsed)
        assertTrue(parsed is XtreamParsedSourceId.EpisodeComposite)
        assertEquals(0, parsed.season)
    }

    @Test
    fun `Scenario - Large IDs as used by real Xtream providers`() {
        // Real providers can have very large IDs
        val sourceId = XtreamIdCodec.vod(2147483647) // Int.MAX_VALUE
        val parsed = XtreamIdCodec.parse(sourceId)
        assertNotNull(parsed)
        assertTrue(parsed is XtreamParsedSourceId.Vod)
        assertEquals(2147483647L, parsed.vodId)
    }

    // =========================================================================
    // 8. Negative ID tests — real providers use negative IDs (e.g. -441)
    // =========================================================================

    @Test
    fun `Negative series ID format roundtrip`() {
        // Real providers have negative series IDs like -441
        val sourceId = XtreamIdCodec.series(-441)
        assertEquals("xtream:series:-441", sourceId)
        val parsed = XtreamIdCodec.parse(sourceId)
        assertNotNull(parsed, "Negative series ID must be parseable")
        assertTrue(parsed is XtreamParsedSourceId.Series)
        assertEquals(-441L, parsed.seriesId)
    }

    @Test
    fun `Negative VOD ID format roundtrip`() {
        val sourceId = XtreamIdCodec.vod(-123)
        assertEquals("xtream:vod:-123", sourceId)
        val parsed = XtreamIdCodec.parse(sourceId)
        assertNotNull(parsed)
        assertTrue(parsed is XtreamParsedSourceId.Vod)
        assertEquals(-123L, parsed.vodId)
    }

    @Test
    fun `Negative series ID in composite episode format`() {
        val sourceId = XtreamIdCodec.episodeComposite(-441, 1, 5)
        assertEquals("xtream:episode:series:-441:s1:e5", sourceId)
        val parsed = XtreamIdCodec.parse(sourceId)
        assertNotNull(parsed, "Episode with negative seriesId must be parseable")
        assertTrue(parsed is XtreamParsedSourceId.EpisodeComposite)
        assertEquals(-441L, parsed.seriesId)
        assertEquals(1, parsed.season)
        assertEquals(5, parsed.episode)
    }

    @Test
    fun `NX format with negative series ID`() {
        val nxSourceKey = "src:xtream:myserver:series:-441"
        val parsed = XtreamIdCodec.parse(nxSourceKey)
        assertNotNull(parsed, "NX sourceKey with negative seriesId must be parseable")
        assertTrue(parsed is XtreamParsedSourceId.Series)
        assertEquals(-441L, parsed.seriesId)
    }

    @Test
    fun `NX format with negative episode composite series ID`() {
        // This is the key runtime path: stored as src:xtream:acc:episode:series:-441:s1:e5
        val nxSourceKey = "src:xtream:myserver:episode:series:-441:s1:e5"
        val parsed = XtreamIdCodec.parse(nxSourceKey)
        assertNotNull(parsed, "NX episode with negative seriesId must be parseable")
        assertTrue(parsed is XtreamParsedSourceId.EpisodeComposite)
        assertEquals(-441L, parsed.seriesId)
        assertEquals(1, parsed.season)
        assertEquals(5, parsed.episode)
    }

    @Test
    fun `Negative episode direct ID`() {
        val sourceId = XtreamIdCodec.episode(-999)
        assertEquals("xtream:episode:-999", sourceId)
        val parsed = XtreamIdCodec.parse(sourceId)
        assertNotNull(parsed)
        assertTrue(parsed is XtreamParsedSourceId.Episode)
        assertEquals(-999L, parsed.episodeId)
    }

    @Test
    fun `SourceIdParser handles negative VOD ID`() {
        val vodId = SourceIdParser.parseXtreamVodId("xtream:vod:-441")
        assertEquals(-441, vodId)
    }

    @Test
    fun `SourceIdParser handles negative series ID in episode`() {
        val result = SourceIdParser.parseXtreamEpisodeId("xtream:episode:series:-441:s1:e5")
        assertNotNull(result, "Negative seriesId in episode must be parseable via SourceIdParser")
        assertEquals(-441, result.first)
        assertEquals(1, result.second)
        assertEquals(5, result.third)
    }

    @Test
    fun `Legacy Format C with negative seriesId`() {
        val parsed = XtreamIdCodec.parse("xtream:episode:-441:2:5")
        assertNotNull(parsed, "Legacy Format C with negative seriesId must be parseable")
        assertTrue(parsed is XtreamParsedSourceId.EpisodeComposite)
        assertEquals(-441L, parsed.seriesId)
    }

    @Test
    fun `Underscore format with negative seriesId`() {
        val parsed = XtreamIdCodec.parse("xtream:episode:-441_1_5")
        assertNotNull(parsed, "Underscore format with negative seriesId must be parseable")
        assertTrue(parsed is XtreamParsedSourceId.EpisodeComposite)
        assertEquals(-441L, parsed.seriesId)
        assertEquals(1, parsed.season)
        assertEquals(5, parsed.episode)
    }

    // =========================================================================
    // 7. Rejection tests — invalid inputs must return null, not crash
    // =========================================================================

    @Test
    fun `XtreamIdCodec parse rejects garbage`() {
        assertNull(XtreamIdCodec.parse(""))
        assertNull(XtreamIdCodec.parse("random-string"))
        assertNull(XtreamIdCodec.parse("not:xtream:vod:123"))
        assertNull(XtreamIdCodec.parse("xtream:"))
        assertNull(XtreamIdCodec.parse("xtream:unknown:123"))
    }

    @Test
    fun `SourceIdParser parse rejects garbage gracefully`() {
        assertNull(SourceIdParser.parseTelegramSourceId(""))
        assertNull(SourceIdParser.parseTelegramSourceId("garbage"))
        assertNull(SourceIdParser.parseTelegramSourceId("msg:"))
        assertNull(SourceIdParser.parseTelegramSourceId("msg:abc:def"))
        assertNull(SourceIdParser.parseXtreamVodId("garbage:garbage"))
    }

    @Test
    fun `NX format with missing account still returns null`() {
        // Malformed NX: src:xtream (no account, no kind, no id)
        assertNull(XtreamIdCodec.parse("src:xtream"))
        assertNull(XtreamIdCodec.parse("src:xtream:"))
    }
}
