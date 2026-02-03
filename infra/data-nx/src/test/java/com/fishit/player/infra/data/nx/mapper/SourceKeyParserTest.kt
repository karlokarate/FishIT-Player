package com.fishit.player.infra.data.nx.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Comprehensive unit tests for SourceKeyParser.
 *
 * Tests cover:
 * - Valid sourceKey format parsing
 * - Invalid format handling
 * - Edge cases (null, empty, malformed)
 * - Xtream-specific extraction (VOD, Live, Series, Episodes)
 * - Telegram-specific extraction (chatId, messageId)
 * - Numeric ID extraction
 */
class SourceKeyParserTest {

    // =========================================================================
    // Core Parsing Tests
    // =========================================================================

    @Test
    fun `parse - valid Xtream VOD sourceKey`() {
        val result = SourceKeyParser.parse("src:xtream:myserver:vod:12345")
        
        assertEquals("xtream", result?.sourceType)
        assertEquals("myserver", result?.accountKey)
        assertEquals("vod", result?.itemKind)
        assertEquals("12345", result?.itemKey)
    }

    @Test
    fun `parse - valid Xtream Live sourceKey`() {
        val result = SourceKeyParser.parse("src:xtream:server2:live:67890")
        
        assertEquals("xtream", result?.sourceType)
        assertEquals("server2", result?.accountKey)
        assertEquals("live", result?.itemKind)
        assertEquals("67890", result?.itemKey)
    }

    @Test
    fun `parse - valid Xtream Episode sourceKey with composite ID`() {
        val result = SourceKeyParser.parse("src:xtream:myserver:episode:100_1_5")
        
        assertEquals("xtream", result?.sourceType)
        assertEquals("myserver", result?.accountKey)
        assertEquals("episode", result?.itemKind)
        assertEquals("100_1_5", result?.itemKey)
    }

    @Test
    fun `parse - valid Telegram sourceKey with multi-part ID`() {
        val result = SourceKeyParser.parse("src:telegram:myaccount:file:123456789:987654321")
        
        assertEquals("telegram", result?.sourceType)
        assertEquals("myaccount", result?.accountKey)
        assertEquals("file", result?.itemKind)
        assertEquals("123456789:987654321", result?.itemKey)
    }

    @Test
    fun `parse - null input returns null`() {
        assertNull(SourceKeyParser.parse(null))
    }

    @Test
    fun `parse - empty input returns null`() {
        assertNull(SourceKeyParser.parse(""))
    }

    @Test
    fun `parse - blank input returns null`() {
        assertNull(SourceKeyParser.parse("   "))
    }

    @Test
    fun `parse - legacy sourceId format returns null (not supported)`() {
        // Legacy format: xtream:vod:123 (NO src: prefix)
        assertNull(SourceKeyParser.parse("xtream:vod:123"))
    }

    @Test
    fun `parse - malformed sourceKey with missing parts returns null`() {
        // Only 3 parts instead of minimum 5
        assertNull(SourceKeyParser.parse("src:xtream:account"))
    }

    @Test
    fun `parse - sourceKey with wrong prefix returns null`() {
        assertNull(SourceKeyParser.parse("nosrc:xtream:account:vod:123"))
    }

    // =========================================================================
    // Generic Extraction Tests
    // =========================================================================

    @Test
    fun `extractSourceType - valid sourceKey`() {
        assertEquals("xtream", SourceKeyParser.extractSourceType("src:xtream:server:vod:123"))
        assertEquals("telegram", SourceKeyParser.extractSourceType("src:telegram:account:file:1:2"))
    }

    @Test
    fun `extractSourceType - invalid input returns null`() {
        assertNull(SourceKeyParser.extractSourceType(null))
        assertNull(SourceKeyParser.extractSourceType(""))
        assertNull(SourceKeyParser.extractSourceType("invalid"))
    }

    @Test
    fun `extractAccountKey - valid sourceKey`() {
        assertEquals("myserver", SourceKeyParser.extractAccountKey("src:xtream:myserver:vod:123"))
        assertEquals("account2", SourceKeyParser.extractAccountKey("src:telegram:account2:file:1:2"))
    }

    @Test
    fun `extractAccountKey - invalid input returns unknown`() {
        assertEquals("unknown", SourceKeyParser.extractAccountKey(null))
        assertEquals("unknown", SourceKeyParser.extractAccountKey(""))
        assertEquals("unknown", SourceKeyParser.extractAccountKey("xtream:vod:123"))
    }

    @Test
    fun `extractItemKind - valid sourceKey`() {
        assertEquals("vod", SourceKeyParser.extractItemKind("src:xtream:server:vod:123"))
        assertEquals("live", SourceKeyParser.extractItemKind("src:xtream:server:live:456"))
        assertEquals("episode", SourceKeyParser.extractItemKind("src:xtream:server:episode:100_1_5"))
        assertEquals("file", SourceKeyParser.extractItemKind("src:telegram:account:file:1:2"))
    }

    @Test
    fun `extractItemKind - invalid input returns null`() {
        assertNull(SourceKeyParser.extractItemKind(null))
        assertNull(SourceKeyParser.extractItemKind(""))
        assertNull(SourceKeyParser.extractItemKind("invalid"))
    }

    @Test
    fun `extractItemKey - valid sourceKey`() {
        assertEquals("12345", SourceKeyParser.extractItemKey("src:xtream:server:vod:12345"))
        assertEquals("100_1_5", SourceKeyParser.extractItemKey("src:xtream:server:episode:100_1_5"))
        assertEquals("123456789:987654321", SourceKeyParser.extractItemKey("src:telegram:account:file:123456789:987654321"))
    }

    @Test
    fun `extractItemKey - invalid input returns null`() {
        assertNull(SourceKeyParser.extractItemKey(null))
        assertNull(SourceKeyParser.extractItemKey(""))
        assertNull(SourceKeyParser.extractItemKey("invalid"))
    }

    @Test
    fun `extractNumericItemKey - valid sourceKey with numeric ID`() {
        assertEquals(12345L, SourceKeyParser.extractNumericItemKey("src:xtream:server:vod:12345"))
        assertEquals(67890L, SourceKeyParser.extractNumericItemKey("src:xtream:server:live:67890"))
    }

    @Test
    fun `extractNumericItemKey - already numeric string`() {
        assertEquals(999L, SourceKeyParser.extractNumericItemKey("999"))
    }

    @Test
    fun `extractNumericItemKey - composite ID returns null`() {
        assertNull(SourceKeyParser.extractNumericItemKey("src:xtream:server:episode:100_1_5"))
    }

    @Test
    fun `extractNumericItemKey - invalid input returns null`() {
        assertNull(SourceKeyParser.extractNumericItemKey(null))
        assertNull(SourceKeyParser.extractNumericItemKey(""))
        assertNull(SourceKeyParser.extractNumericItemKey("not_numeric"))
    }

    // =========================================================================
    // Xtream-Specific Extraction Tests
    // =========================================================================

    @Test
    fun `extractXtreamStreamId - VOD sourceKey`() {
        assertEquals("12345", SourceKeyParser.extractXtreamStreamId("src:xtream:server:vod:12345"))
    }

    @Test
    fun `extractXtreamStreamId - Live sourceKey`() {
        assertEquals("67890", SourceKeyParser.extractXtreamStreamId("src:xtream:server:live:67890"))
    }

    @Test
    fun `extractXtreamStreamId - Series sourceKey`() {
        assertEquals("100", SourceKeyParser.extractXtreamStreamId("src:xtream:server:series:100"))
    }

    @Test
    fun `extractXtreamStreamId - Episode sourceKey returns series ID`() {
        // For episodes, should extract the series ID (first component)
        assertEquals("100", SourceKeyParser.extractXtreamStreamId("src:xtream:server:episode:100_1_5"))
    }

    @Test
    fun `extractXtreamStreamId - invalid input returns null`() {
        assertNull(SourceKeyParser.extractXtreamStreamId(null))
        assertNull(SourceKeyParser.extractXtreamStreamId(""))
        assertNull(SourceKeyParser.extractXtreamStreamId("invalid"))
    }

    @Test
    fun `extractXtreamEpisodeId - direct episode ID`() {
        assertEquals(12345, SourceKeyParser.extractXtreamEpisodeId("src:xtream:server:episode:12345"))
    }

    @Test
    fun `extractXtreamEpisodeId - composite format returns episode number`() {
        // Format: seriesId_season_episode → extract episode (third component)
        assertEquals(5, SourceKeyParser.extractXtreamEpisodeId("src:xtream:server:episode:100_1_5"))
        assertEquals(12, SourceKeyParser.extractXtreamEpisodeId("src:xtream:server:episode:200_2_12"))
    }

    @Test
    fun `extractXtreamEpisodeId - invalid composite format returns null`() {
        assertNull(SourceKeyParser.extractXtreamEpisodeId("src:xtream:server:episode:100_1"))
        assertNull(SourceKeyParser.extractXtreamEpisodeId("src:xtream:server:episode:invalid"))
    }

    @Test
    fun `extractXtreamEpisodeId - invalid input returns null`() {
        assertNull(SourceKeyParser.extractXtreamEpisodeId(null))
        assertNull(SourceKeyParser.extractXtreamEpisodeId(""))
    }

    @Test
    fun `extractXtreamSeriesIdFromEpisode - composite format`() {
        // Format: seriesId_season_episode → extract seriesId (first component)
        assertEquals(100, SourceKeyParser.extractXtreamSeriesIdFromEpisode("src:xtream:server:episode:100_1_5"))
        assertEquals(200, SourceKeyParser.extractXtreamSeriesIdFromEpisode("src:xtream:server:episode:200_2_12"))
    }

    @Test
    fun `extractXtreamSeriesIdFromEpisode - invalid composite format returns null`() {
        assertNull(SourceKeyParser.extractXtreamSeriesIdFromEpisode("src:xtream:server:episode:100_1"))
        assertNull(SourceKeyParser.extractXtreamSeriesIdFromEpisode("src:xtream:server:episode:12345"))
    }

    @Test
    fun `extractXtreamSeriesIdFromEpisode - invalid input returns null`() {
        assertNull(SourceKeyParser.extractXtreamSeriesIdFromEpisode(null))
        assertNull(SourceKeyParser.extractXtreamSeriesIdFromEpisode(""))
    }

    // =========================================================================
    // Telegram-Specific Extraction Tests
    // =========================================================================

    @Test
    fun `extractTelegramChatId - valid sourceKey`() {
        assertEquals(123456789L, SourceKeyParser.extractTelegramChatId("src:telegram:account:file:123456789:987654321"))
        assertEquals(-100123456789L, SourceKeyParser.extractTelegramChatId("src:telegram:account:file:-100123456789:987654321"))
    }

    @Test
    fun `extractTelegramChatId - invalid input returns null`() {
        assertNull(SourceKeyParser.extractTelegramChatId(null))
        assertNull(SourceKeyParser.extractTelegramChatId(""))
        assertNull(SourceKeyParser.extractTelegramChatId("src:telegram:account:file:invalid:123"))
    }

    @Test
    fun `extractTelegramMessageId - valid sourceKey`() {
        assertEquals(987654321L, SourceKeyParser.extractTelegramMessageId("src:telegram:account:file:123456789:987654321"))
    }

    @Test
    fun `extractTelegramMessageId - invalid input returns null`() {
        assertNull(SourceKeyParser.extractTelegramMessageId(null))
        assertNull(SourceKeyParser.extractTelegramMessageId(""))
        assertNull(SourceKeyParser.extractTelegramMessageId("src:telegram:account:file:123:invalid"))
    }

    @Test
    fun `extractTelegramIds - valid sourceKey returns pair`() {
        val result = SourceKeyParser.extractTelegramIds("src:telegram:account:file:123456789:987654321")
        
        assertEquals(Pair(123456789L, 987654321L), result)
    }

    @Test
    fun `extractTelegramIds - invalid chatId returns null`() {
        assertNull(SourceKeyParser.extractTelegramIds("src:telegram:account:file:invalid:987654321"))
    }

    @Test
    fun `extractTelegramIds - invalid messageId returns null`() {
        assertNull(SourceKeyParser.extractTelegramIds("src:telegram:account:file:123456789:invalid"))
    }

    @Test
    fun `extractTelegramIds - invalid input returns null`() {
        assertNull(SourceKeyParser.extractTelegramIds(null))
        assertNull(SourceKeyParser.extractTelegramIds(""))
        assertNull(SourceKeyParser.extractTelegramIds("invalid"))
    }

    // =========================================================================
    // Integration Tests (Multiple Operations)
    // =========================================================================

    @Test
    fun `integration - parse and extract from same sourceKey`() {
        val sourceKey = "src:xtream:myserver:vod:12345"
        
        // Full parse
        val parsed = SourceKeyParser.parse(sourceKey)
        assertEquals("xtream", parsed?.sourceType)
        assertEquals("myserver", parsed?.accountKey)
        assertEquals("vod", parsed?.itemKind)
        assertEquals("12345", parsed?.itemKey)
        
        // Individual extractions should match
        assertEquals("xtream", SourceKeyParser.extractSourceType(sourceKey))
        assertEquals("myserver", SourceKeyParser.extractAccountKey(sourceKey))
        assertEquals("vod", SourceKeyParser.extractItemKind(sourceKey))
        assertEquals("12345", SourceKeyParser.extractItemKey(sourceKey))
        assertEquals(12345L, SourceKeyParser.extractNumericItemKey(sourceKey))
        assertEquals("12345", SourceKeyParser.extractXtreamStreamId(sourceKey))
    }

    @Test
    fun `integration - Telegram sourceKey with all extractions`() {
        val sourceKey = "src:telegram:myaccount:file:123456789:987654321"
        
        assertEquals("telegram", SourceKeyParser.extractSourceType(sourceKey))
        assertEquals("myaccount", SourceKeyParser.extractAccountKey(sourceKey))
        assertEquals("file", SourceKeyParser.extractItemKind(sourceKey))
        assertEquals("123456789:987654321", SourceKeyParser.extractItemKey(sourceKey))
        assertEquals(123456789L, SourceKeyParser.extractTelegramChatId(sourceKey))
        assertEquals(987654321L, SourceKeyParser.extractTelegramMessageId(sourceKey))
        assertEquals(Pair(123456789L, 987654321L), SourceKeyParser.extractTelegramIds(sourceKey))
    }

    @Test
    fun `integration - Xtream episode sourceKey with all extractions`() {
        val sourceKey = "src:xtream:myserver:episode:100_2_15"
        
        assertEquals("xtream", SourceKeyParser.extractSourceType(sourceKey))
        assertEquals("myserver", SourceKeyParser.extractAccountKey(sourceKey))
        assertEquals("episode", SourceKeyParser.extractItemKind(sourceKey))
        assertEquals("100_2_15", SourceKeyParser.extractItemKey(sourceKey))
        assertEquals("100", SourceKeyParser.extractXtreamStreamId(sourceKey))
        assertEquals(15, SourceKeyParser.extractXtreamEpisodeId(sourceKey))
        assertEquals(100, SourceKeyParser.extractXtreamSeriesIdFromEpisode(sourceKey))
    }
}
