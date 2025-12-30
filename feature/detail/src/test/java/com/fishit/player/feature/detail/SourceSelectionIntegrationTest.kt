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
 * Integration tests demonstrating the SSOT source selection model and race-proof playback.
 *
 * These tests simulate the exact bug scenario that motivated this refactor:
 * 1. User loads detail screen → selectedSource captured (no containerExtension)
 * 2. Background enrichment fetches get_vod_info → updates media.sources with containerExtension
 * 3. User clicks Play → old code used stale selectedSource → playback fails
 * 4. NEW code derives activeSource from SSOT media.sources → playback works
 */
class SourceSelectionIntegrationTest {

    // ==========================================================================
    // The Bug Scenario: Stale selectedSource vs SSOT
    // ==========================================================================

    /**
     * This test simulates the EXACT bug that was occurring:
     *
     * Before fix: selectedSource was captured early and not updated when
     * enrichment added containerExtension to media.sources[].playbackHints
     *
     * After fix: We don't store selectedSource at all. We derive activeSource
     * from media.sources at the moment of use.
     */
    @Test
    fun `SSOT model prevents stale playbackHints bug`() {
        // Step 1: Initial media load (catalog data, no containerExtension)
        val initialSource = createXtreamVodSource(vodId = 52073, containerExtension = null)
        val initialMedia = createMedia(sources = listOf(initialSource))

        // OLD BEHAVIOR (buggy): Capture selectedSource at load time
        val oldStyleSelectedSource = initialSource // This would be stored in state

        // Step 2: Simulate background enrichment (get_vod_info returns containerExtension=mkv)
        val enrichedSource = createXtreamVodSource(vodId = 52073, containerExtension = "mkv")
        val enrichedMedia = createMedia(sources = listOf(enrichedSource))

        // OLD BEHAVIOR: oldStyleSelectedSource still has NO containerExtension!
        assertNull(oldStyleSelectedSource.playbackHints[PlaybackHintKeys.Xtream.CONTAINER_EXT])

        // NEW BEHAVIOR (SSOT): Derive activeSource from current media.sources
        val activeSource = SourceSelection.resolveActiveSource(
            media = enrichedMedia,
            selectedSourceKey = initialSource.sourceId, // We only stored the KEY
            resume = null,
        )

        // NEW BEHAVIOR: activeSource has the containerExtension!
        assertNotNull(activeSource)
        assertEquals("mkv", activeSource.playbackHints[PlaybackHintKeys.Xtream.CONTAINER_EXT])
        assertTrue(SourceSelection.isPlaybackReady(activeSource))
    }

    /**
     * Verify that getMissingPlaybackHints correctly detects when enrichment is needed.
     */
    @Test
    fun `getMissingPlaybackHints detects need for enrichment before playback`() {
        // Before enrichment
        val unenrichedSource = createXtreamVodSource(vodId = 52073, containerExtension = null)
        val missingBefore = SourceSelection.getMissingPlaybackHints(unenrichedSource)
        assertEquals(listOf(PlaybackHintKeys.Xtream.CONTAINER_EXT), missingBefore)
        assertTrue(!SourceSelection.isPlaybackReady(unenrichedSource))

        // After enrichment
        val enrichedSource = createXtreamVodSource(vodId = 52073, containerExtension = "mkv")
        val missingAfter = SourceSelection.getMissingPlaybackHints(enrichedSource)
        assertTrue(missingAfter.isEmpty())
        assertTrue(SourceSelection.isPlaybackReady(enrichedSource))
    }

    /**
     * Verify that enrichment of one source type doesn't affect detection of another.
     */
    @Test
    fun `getMissingPlaybackHints is source-type-aware`() {
        // Telegram sources don't need containerExtension
        val telegramSource = createTelegramSource()
        assertTrue(SourceSelection.getMissingPlaybackHints(telegramSource).isEmpty())
        assertTrue(SourceSelection.isPlaybackReady(telegramSource))

        // Live Xtream sources don't need containerExtension (they use HLS)
        val liveSource = createXtreamLiveSource()
        assertTrue(SourceSelection.getMissingPlaybackHints(liveSource).isEmpty())
        assertTrue(SourceSelection.isPlaybackReady(liveSource))

        // Only VOD/Series Xtream sources need containerExtension
        val vodSource = createXtreamVodSource(containerExtension = null)
        assertEquals(listOf(PlaybackHintKeys.Xtream.CONTAINER_EXT), SourceSelection.getMissingPlaybackHints(vodSource))
    }

    // ==========================================================================
    // Multi-Source Selection Scenarios
    // ==========================================================================

    /**
     * When user has multiple sources and one becomes unavailable,
     * selection should gracefully fall back.
     */
    @Test
    fun `selection gracefully falls back when selected source disappears`() {
        val xtreamSource = createXtreamVodSource(vodId = 123, containerExtension = "mp4")
        val telegramSource = createTelegramSource()
        val media = createMedia(sources = listOf(xtreamSource, telegramSource))

        // User manually selects Telegram source
        val manualKey = telegramSource.sourceId

        // Later, Telegram source is removed (e.g., message deleted)
        val mediaWithoutTelegram = createMedia(sources = listOf(xtreamSource))

        val activeSource = SourceSelection.resolveActiveSource(
            media = mediaWithoutTelegram,
            selectedSourceKey = manualKey, // Still has old key
            resume = null,
        )

        // Should fall back to Xtream source
        assertNotNull(activeSource)
        assertEquals(xtreamSource.sourceId, activeSource.sourceId)
    }

    /**
     * Resume-based selection should work across source updates.
     */
    @Test
    fun `resume source selection survives enrichment`() {
        // Initial: Two sources, user last watched on Telegram
        val telegramSource = createTelegramSource()
        val xtreamSource = createXtreamVodSource(vodId = 123, containerExtension = null)
        val resume = createResume(lastSourceId = telegramSource.sourceId, positionMs = 1800000L)

        val initialMedia = createMedia(sources = listOf(xtreamSource, telegramSource))

        // After enrichment: Xtream source now has containerExtension
        val enrichedXtreamSource = createXtreamVodSource(vodId = 123, containerExtension = "mkv")
        val enrichedMedia = createMedia(sources = listOf(enrichedXtreamSource, telegramSource))

        // Selection should still prefer resume source (Telegram), not enriched Xtream
        val activeSource = SourceSelection.resolveActiveSource(
            media = enrichedMedia,
            selectedSourceKey = null, // No manual selection
            resume = resume,
        )

        assertNotNull(activeSource)
        assertEquals(telegramSource.sourceId, activeSource.sourceId)
    }

    // ==========================================================================
    // Test Data Builders
    // ==========================================================================

    private fun createMedia(
        sources: List<MediaSourceRef> = emptyList()
    ): CanonicalMediaWithSources = CanonicalMediaWithSources(
        canonicalId = CanonicalMediaId(
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
    ): MediaSourceRef = MediaSourceRef(
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
    ): MediaSourceRef = MediaSourceRef(
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
    ): CanonicalResumeInfo = CanonicalResumeInfo(
        lastSourceId = lastSourceId,
        positionMs = positionMs,
        durationMs = durationMs,
    )
}
