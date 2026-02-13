package com.fishit.player.infra.data.nx.property

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.PipelineIdTag
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.TmdbRef
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of

/**
 * Kotest [Arb] generators for domain model types used in property-based testing.
 *
 * Design goals:
 * - Generate **realistic** data shapes that pipelines actually produce
 * - Cover edge cases: empty strings, null fields, extreme values
 * - Keep generators composable and reusable
 *
 * Generators follow pipeline contracts:
 * - Xtream sourceIds: "xtream:{vod|live|series|episode}:{numericId}"
 * - Telegram sourceIds: "msg:{chatId}:{messageId}"
 * - ImageRef variants: Http, TelegramThumb (no LocalFile/InlineBytes from pipelines)
 */
object Generators {

    // =========================================================================
    // Primitives
    // =========================================================================

    /** Realistic movie/series titles (mix of clean and scene-style) */
    val title: Arb<String> = Arb.of(
        "Oppenheimer",
        "Breaking Bad S05E16 German DL 1080p BluRay x264.mkv",
        "The.Matrix.1999.1080p.BluRay.x264-YTS",
        "Inception 2010",
        "BBC One HD",
        "Unknown Video",
        "A",
        "",
        "   ",
        "日本語タイトル",
        "Тест кириллица",
        "Movie with  extra   spaces",
        "Special Ch@r$! in (title) [2024]",
        "S01E01 - Pilot Episode",
        "Live: 24/7 News Channel",
        "very-long-title-that-goes-on-and-on-and-on-to-test-slug-truncation-at-fifty-chars-boundary",
    )

    /** Year in realistic range or null */
    val year: Arb<Int?> = Arb.of(null, 1990, 2000, 2010, 2020, 2023, 2024, 2025)

    /** Season number or null */
    val season: Arb<Int?> = Arb.of(null, 1, 2, 5, 10, 99)

    /** Episode number or null */
    val episode: Arb<Int?> = Arb.of(null, 1, 7, 16, 100, 999)

    /** Duration in ms or null */
    val durationMs: Arb<Long?> = Arb.of(
        null, 0L, 1000L, 60_000L, 3_600_000L, 7_200_000L, 10_860_000L,
    )

    /** Rating 0.0-10.0 or null */
    val rating: Arb<Double?> = Arb.of(null, 0.0, 1.5, 5.0, 7.5, 8.1, 9.9, 10.0)

    // =========================================================================
    // Enums
    // =========================================================================

    val mediaType: Arb<MediaType> = Arb.enum<MediaType>()
    val sourceType: Arb<SourceType> = Arb.enum<SourceType>()
    val pipelineIdTag: Arb<PipelineIdTag> = Arb.enum<PipelineIdTag>()

    // =========================================================================
    // Source IDs (realistic pipeline-produced formats)
    // =========================================================================

    /** Xtream-style sourceId: "xtream:{type}:{numericId}" */
    val xtreamSourceId: Arb<String> = Arb.bind(
        Arb.of("vod", "live", "series", "episode"),
        Arb.int(1..99999),
    ) { kind, id -> "xtream:$kind:$id" }

    /** Telegram-style sourceId: "msg:{chatId}:{messageId}" */
    val telegramSourceId: Arb<String> = Arb.bind(
        Arb.long(-1_002_000_000_000L..-1_001_000_000_000L),
        Arb.int(1..999999),
    ) { chatId, msgId -> "msg:$chatId:$msgId" }

    /** Generic sourceId (any format) */
    val sourceId: Arb<String> = Arb.choice(xtreamSourceId, telegramSourceId)

    // =========================================================================
    // ImageRef
    // =========================================================================

    val httpImageRef: Arb<ImageRef.Http> = Arb.bind(
        Arb.of(
            "https://image.tmdb.org/t/p/w500/poster.jpg",
            "https://cdn.example.com/img/thumb.png",
            "http://iptv.example.com/logos/logo.png",
        ),
        Arb.of(null, 200, 500, 1280),
        Arb.of(null, 200, 750, 720),
    ) { url, w, h -> ImageRef.Http(url = url, preferredWidth = w, preferredHeight = h) }

    val telegramThumbRef: Arb<ImageRef.TelegramThumb> = Arb.bind(
        Arb.of("AgACAgIAAxkBAAIBZ2XYabcdef", "AgACAgQAAnother123", "AQAD_short"),
        Arb.of(null, -1001234567890L),
        Arb.of(null, 42001L),
        Arb.of(null, 320),
        Arb.of(null, 180),
    ) { remoteId, chatId, msgId, w, h ->
        ImageRef.TelegramThumb(remoteId, chatId, msgId, w, h)
    }

    val imageRef: Arb<ImageRef?> = Arb.choice(
        httpImageRef.map { it as ImageRef },
        telegramThumbRef.map { it as ImageRef },
        Arb.constant(null as ImageRef?),
    )

    // =========================================================================
    // ExternalIds / TmdbRef
    // =========================================================================

    val tmdbRef: Arb<TmdbRef?> = Arb.of(
        null,
        TmdbRef(TmdbMediaType.MOVIE, 872585),
        TmdbRef(TmdbMediaType.TV, 1396),
        TmdbRef(TmdbMediaType.MOVIE, 1),
        TmdbRef(TmdbMediaType.TV, 999999),
    )

    val externalIds: Arb<ExternalIds> = Arb.bind(
        tmdbRef,
        Arb.of(null, "tt15398776", "tt0903747", "tt0137523"),
        Arb.of(null, "12345", "67890"),
    ) { tmdb, imdbId, tvdbId ->
        ExternalIds(tmdb = tmdb, imdbId = imdbId, tvdbId = tvdbId)
    }

    // =========================================================================
    // Playback Hints
    // =========================================================================

    val playbackHints: Arb<Map<String, String>> = Arb.of(
        emptyMap(),
        mapOf("xtream.containerExtension" to "mp4", "xtream.streamId" to "12345"),
        mapOf("xtream.containerExtension" to "m3u8"),
        mapOf("xtream.containerExtension" to "m3u"),
        mapOf("xtream.containerExtension" to "mkv"),
        mapOf("xtream.containerExtension" to "ts"),
        mapOf("xtream.containerExtension" to "avi"),
        mapOf("telegram.fileId" to "BQACAgIAAxkBAAIBZ"),
    )

    // =========================================================================
    // Timestamps
    // =========================================================================

    /** addedTimestamp: null, 0, or positive epoch millis */
    val addedTimestamp: Arb<Long?> = Arb.of(
        null, 0L, 1700000000000L, 1600000000000L, -1L,
    )

    // =========================================================================
    // Nullable string fields
    // =========================================================================

    val nullableText: Arb<String?> = Arb.of(
        null, "", "Some text", "Action, Drama", "Christopher Nolan", "2023-07-19",
    )

    // =========================================================================
    // Full RawMediaMetadata
    // =========================================================================

    /**
     * Generate realistic [RawMediaMetadata] instances.
     * Covers all field combinations including edge cases.
     */
    val rawMediaMetadata: Arb<RawMediaMetadata> = Arb.bind(
        title,
        mediaType,
        year,
        season,
        episode,
        durationMs,
        sourceType,
        sourceId,
        externalIds,
        playbackHints,
        Arb.boolean(),
        imageRef,
        imageRef,
        addedTimestamp,
    ) { title, mType, yr, sn, ep, dur, srcType, srcId, extIds, hints, isAdult,
        poster, backdrop, addedTs ->
        RawMediaMetadata(
            originalTitle = title,
            mediaType = mType,
            year = yr,
            season = sn,
            episode = ep,
            durationMs = dur,
            sourceType = srcType,
            sourceLabel = "test-account",
            sourceId = srcId,
            externalIds = extIds,
            playbackHints = hints,
            isAdult = isAdult,
            poster = poster,
            backdrop = backdrop,
            addedTimestamp = addedTs,
        )
    }
}
