package com.fishit.player.feature.detail

import com.fishit.player.core.model.CanonicalMediaId
import com.fishit.player.core.model.MediaKind
import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.ids.CanonicalKey
import com.fishit.player.core.model.ids.PipelineItemId
import com.fishit.player.core.model.repository.CanonicalMediaWithSources
import com.fishit.player.core.model.repository.CanonicalResumeInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [SourceSelection] - the SSOT-derived source selection logic.
 *
 * These tests verify that source selection is:
 * 1. Deterministic - same inputs always produce same output
 * 2. Priority-based - respects resume > manual selection > best quality
 * 3. Robust - handles edge cases (empty sources, missing keys, etc.)
 */
class SourceSelectionTest {
    // ==========================================================================
    // Test Data Builders
    // ==========================================================================

    private fun createMedia(sources: List<MediaSourceRef> = emptyList()): CanonicalMediaWithSources =
        CanonicalMediaWithSources(
            canonicalId =
                CanonicalMediaId(
                    key = CanonicalKey.of("movie:test:2024"),
                    kind = MediaKind.MOVIE,
                ),
            canonicalTitle = "Test Movie",
            mediaType = MediaType.MOVIE,
            year = 2024,
            tmdbId = null,
            imdbId = null,
            poster = null,
            backdrop = null,
            thumbnail = null,
            plot = null,
            rating = null,
            durationMs = 7200000L,
            genres = null,
            director = null,
            cast = null,
            trailer = null,
            sources = sources,
        )

    private fun createXtreamVodSource(
        vodId: Int = 123,
        containerExtension: String? = null,
        priority: Int = 0,
        resolution: Int? = 1080,
    ): MediaSourceRef {
        val hints = mutableMapOf<String, String>()
        if (containerExtension != null) {
            hints[PlaybackHintKeys.Xtream.CONTAINER_EXT] = containerExtension
        }
        return MediaSourceRef(
            sourceType = SourceType.XTREAM,
            sourceId = PipelineItemId.of("xtream:vod:$vodId"),
            sourceLabel = "Xtream VOD #$vodId",
            quality = MediaSourceRef.QualityInfo(resolution = resolution),
            languages = null,
            format = null,
            sizeBytes = null,
            durationMs = 7200000L,
            addedAt = System.currentTimeMillis(),
            priority = priority,
            playbackHints = hints,
        )
    }

    private fun createTelegramSource(
        messageId: Long = 456,
        chatId: Long = 789,
        priority: Int = 0,
        resolution: Int? = 720,
    ): MediaSourceRef =
        MediaSourceRef(
            sourceType = SourceType.TELEGRAM,
            sourceId = PipelineItemId.of("msg:$chatId:$messageId"),
            sourceLabel = "Telegram Message $messageId",
            quality = MediaSourceRef.QualityInfo(resolution = resolution),
            languages = null,
            format = null,
            sizeBytes = null,
            durationMs = 7200000L,
            addedAt = System.currentTimeMillis(),
            priority = priority,
            playbackHints = emptyMap(),
        )

    private fun createXtreamLiveSource(
        channelId: Int = 111,
        priority: Int = 0,
    ): MediaSourceRef =
        MediaSourceRef(
            sourceType = SourceType.XTREAM,
            sourceId = PipelineItemId.of("xtream:live:$channelId"),
            sourceLabel = "Channel #$channelId",
            quality = null,
            languages = null,
            format = null,
            sizeBytes = null,
            durationMs = null,
            addedAt = System.currentTimeMillis(),
            priority = priority,
            playbackHints = emptyMap(),
        )

    private fun createResume(
        lastSourceId: PipelineItemId,
        positionMs: Long = 3600000L,
        durationMs: Long = 7200000L,
    ): CanonicalResumeInfo =
        CanonicalResumeInfo(
            lastSourceId = lastSourceId,
            positionMs = positionMs,
            durationMs = durationMs,
        )

    // ==========================================================================
    // resolveActiveSource Tests
    // ==========================================================================

    @Test
    fun `resolveActiveSource returns null for null media`() {
        val result =
            SourceSelection.resolveActiveSource(
                media = null,
                selectedSourceKey = null,
                resume = null,
            )
        assertNull(result)
    }

    @Test
    fun `resolveActiveSource returns null for empty sources`() {
        val media = createMedia(sources = emptyList())
        val result =
            SourceSelection.resolveActiveSource(
                media = media,
                selectedSourceKey = null,
                resume = null,
            )
        assertNull(result)
    }

    @Test
    fun `resolveActiveSource returns manually selected source when valid`() {
        val xtreamSource = createXtreamVodSource(vodId = 123)
        val telegramSource = createTelegramSource(messageId = 456)
        val media = createMedia(sources = listOf(xtreamSource, telegramSource))

        val result =
            SourceSelection.resolveActiveSource(
                media = media,
                selectedSourceKey = telegramSource.sourceId,
                resume = null,
            )

        assertNotNull(result)
        assertEquals(telegramSource.sourceId, result.sourceId)
    }

    @Test
    fun `resolveActiveSource ignores invalid manual selection key`() {
        val xtreamSource = createXtreamVodSource(vodId = 123)
        val media = createMedia(sources = listOf(xtreamSource))

        val result =
            SourceSelection.resolveActiveSource(
                media = media,
                selectedSourceKey = PipelineItemId.of("nonexistent:source:key"),
                resume = null,
            )

        // Should fall back to first source since manual key is invalid
        assertNotNull(result)
        assertEquals(xtreamSource.sourceId, result.sourceId)
    }

    @Test
    fun `resolveActiveSource prefers resume source when no manual selection`() {
        val xtreamSource = createXtreamVodSource(vodId = 123, priority = 5)
        val telegramSource = createTelegramSource(messageId = 456, priority = 0)
        val media = createMedia(sources = listOf(xtreamSource, telegramSource))
        val resume = createResume(lastSourceId = telegramSource.sourceId)

        val result =
            SourceSelection.resolveActiveSource(
                media = media,
                selectedSourceKey = null,
                resume = resume,
            )

        // Resume source should be selected (even with lower priority)
        assertNotNull(result)
        assertEquals(telegramSource.sourceId, result.sourceId)
    }

    @Test
    fun `resolveActiveSource falls back to priority when resume source missing`() {
        val xtreamSource = createXtreamVodSource(vodId = 123, priority = 5)
        val telegramSource = createTelegramSource(messageId = 456, priority = 0)
        val media = createMedia(sources = listOf(xtreamSource, telegramSource))
        // Resume refers to a source that no longer exists
        val resume = createResume(lastSourceId = PipelineItemId.of("deleted:source:999"))

        val result =
            SourceSelection.resolveActiveSource(
                media = media,
                selectedSourceKey = null,
                resume = resume,
            )

        // Should fall back to highest priority source
        assertNotNull(result)
        assertEquals(xtreamSource.sourceId, result.sourceId)
    }

    @Test
    fun `resolveActiveSource selects best quality when no resume and equal priority`() {
        val source720p = createXtreamVodSource(vodId = 1, resolution = 720, priority = 0)
        val source1080p = createXtreamVodSource(vodId = 2, resolution = 1080, priority = 0)
        val source480p = createXtreamVodSource(vodId = 3, resolution = 480, priority = 0)
        val media = createMedia(sources = listOf(source720p, source1080p, source480p))

        val result =
            SourceSelection.resolveActiveSource(
                media = media,
                selectedSourceKey = null,
                resume = null,
            )

        // Highest resolution should be selected
        assertNotNull(result)
        assertEquals(source1080p.sourceId, result.sourceId)
    }

    @Test
    fun `resolveActiveSource returns first source as ultimate fallback`() {
        val source1 = createXtreamVodSource(vodId = 1, resolution = null, priority = 0)
        val source2 = createXtreamVodSource(vodId = 2, resolution = null, priority = 0)
        val media = createMedia(sources = listOf(source1, source2))

        val result =
            SourceSelection.resolveActiveSource(
                media = media,
                selectedSourceKey = null,
                resume = null,
            )

        // First source as fallback
        assertNotNull(result)
        assertEquals(source1.sourceId, result.sourceId)
    }

    // ==========================================================================
    // getMissingPlaybackHints Tests
    // ==========================================================================

    @Test
    fun `getMissingPlaybackHints returns empty for Telegram source`() {
        val source = createTelegramSource()
        val result = SourceSelection.getMissingPlaybackHints(source)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getMissingPlaybackHints returns empty for Live Xtream source`() {
        val source = createXtreamLiveSource()
        val result = SourceSelection.getMissingPlaybackHints(source)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getMissingPlaybackHints returns containerExt for Xtream VOD without it`() {
        val source = createXtreamVodSource(vodId = 123, containerExtension = null)
        val result = SourceSelection.getMissingPlaybackHints(source)

        assertEquals(1, result.size)
        assertTrue(result.contains(PlaybackHintKeys.Xtream.CONTAINER_EXT))
    }

    @Test
    fun `getMissingPlaybackHints returns empty for Xtream VOD with containerExt`() {
        val source = createXtreamVodSource(vodId = 123, containerExtension = "mkv")
        val result = SourceSelection.getMissingPlaybackHints(source)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getMissingPlaybackHints ignores blank containerExt`() {
        val source =
            MediaSourceRef(
                sourceType = SourceType.XTREAM,
                sourceId = PipelineItemId.of("xtream:vod:123"),
                sourceLabel = "Test",
                quality = null,
                languages = null,
                format = null,
                sizeBytes = null,
                durationMs = null,
                addedAt = System.currentTimeMillis(),
                priority = 0,
                playbackHints = mapOf(PlaybackHintKeys.Xtream.CONTAINER_EXT to "   "), // blank
            )
        val result = SourceSelection.getMissingPlaybackHints(source)

        assertEquals(1, result.size)
        assertTrue(result.contains(PlaybackHintKeys.Xtream.CONTAINER_EXT))
    }

    // ==========================================================================
    // isPlaybackReady Tests
    // ==========================================================================

    @Test
    fun `isPlaybackReady returns true for source with all hints`() {
        val source = createXtreamVodSource(vodId = 123, containerExtension = "mp4")
        assertTrue(SourceSelection.isPlaybackReady(source))
    }

    @Test
    fun `isPlaybackReady returns false for Xtream VOD without containerExt`() {
        val source = createXtreamVodSource(vodId = 123, containerExtension = null)
        assertTrue(!SourceSelection.isPlaybackReady(source))
    }

    @Test
    fun `isPlaybackReady returns true for Telegram source`() {
        val source = createTelegramSource()
        assertTrue(SourceSelection.isPlaybackReady(source))
    }

    // ==========================================================================
    // Determinism Tests
    // ==========================================================================

    @Test
    fun `resolveActiveSource is deterministic - same inputs produce same output`() {
        val xtreamSource = createXtreamVodSource(vodId = 123, priority = 5)
        val telegramSource = createTelegramSource(messageId = 456, priority = 3)
        val media = createMedia(sources = listOf(xtreamSource, telegramSource))
        val resume = createResume(lastSourceId = telegramSource.sourceId)

        // Call multiple times with same inputs
        val results =
            (1..10).map {
                SourceSelection.resolveActiveSource(
                    media = media,
                    selectedSourceKey = null,
                    resume = resume,
                )
            }

        // All results should be identical
        assertTrue(results.all { it?.sourceId == results.first()?.sourceId })
    }
}
