package com.fishit.player.pipeline.xtream.golden

import com.fishit.player.core.metadata.RegexMediaMetadataNormalizer
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.model.repository.NxWorkVariantRepository
import com.fishit.player.core.persistence.obx.NxKeyGenerator
import com.fishit.player.infra.data.nx.mapper.SourceKeyParser
import com.fishit.player.infra.data.nx.writer.builder.SourceRefBuilder
import com.fishit.player.infra.data.nx.writer.builder.VariantBuilder
import com.fishit.player.infra.data.nx.writer.builder.WorkEntityBuilder
import com.fishit.player.infra.transport.xtream.XtreamLiveStream
import com.fishit.player.infra.transport.xtream.XtreamSeriesInfo
import com.fishit.player.infra.transport.xtream.XtreamSeriesStream
import com.fishit.player.infra.transport.xtream.XtreamVodInfo
import com.fishit.player.infra.transport.xtream.XtreamVodStream
import com.fishit.player.pipeline.xtream.adapter.toPipelineItem
import com.fishit.player.pipeline.xtream.adapter.toEpisodes
import com.fishit.player.pipeline.xtream.mapper.toRawMediaMetadata
import com.fishit.player.pipeline.xtream.mapper.toRawMetadata
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real-data end-to-end chain tests using actual API responses from `test-data/xtream-responses/`.
 *
 * These tests exercise the **complete production code path**:
 *
 * ```
 * Real API JSON (test-data/xtream-responses/)
 *     │ kotlinx.serialization (same as production)
 *     ▼
 * Transport DTO (XtreamVodStream, XtreamLiveStream, etc.)
 *     │ toPipelineItem() (same production function from XtreamPipelineAdapter)
 *     ▼
 * Pipeline DTO (XtreamVodItem, XtreamChannel, etc.)
 *     │ toRawMetadata() / toRawMediaMetadata() (production mapper)
 *     ▼
 * RawMediaMetadata
 *     │ RegexMediaMetadataNormalizer.normalize() (production normalizer)
 *     ▼
 * NormalizedMediaMetadata
 *     │ WorkEntityBuilder + SourceRefBuilder + VariantBuilder (production builders)
 *     ▼
 * NX_Work + NX_WorkSourceRef + NX_WorkVariant (persistence entities)
 * ```
 *
 * **Every function called is the EXACT same function used at runtime.**
 * No hand-crafted fixtures. No test stubs. Real data, real code, real output.
 *
 * Golden files capture ALL 5 chain steps so edge cases and data loss are visible.
 *
 * ## Usage
 *
 * Normal run (compare against golden files):
 * ```
 * ./gradlew :pipeline:xtream:test --tests "*XtreamRealDataChainTest*"
 * ```
 *
 * Regenerate golden files after intentional changes:
 * ```
 * ./gradlew :pipeline:xtream:test --tests "*XtreamRealDataChainTest*" -Dgolden.update=true
 * ```
 */
class XtreamRealDataChainTest {

    // =========================================================================
    // Production components — zero-arg constructors, NO DI needed
    // =========================================================================

    private val normalizer = RegexMediaMetadataNormalizer()
    private val workBuilder = WorkEntityBuilder()
    private val sourceRefBuilder = SourceRefBuilder()
    private val variantBuilder = VariantBuilder()

    // =========================================================================
    // Test configuration
    // =========================================================================

    private val shouldUpdate: Boolean
        get() = System.getProperty("golden.update")?.toBoolean() == true

    private val goldenDir = File("test-data/golden/real-chain")
    private val testDataDir = File("test-data/xtream-responses")
    private val fixedNow = 1700000000000L // Deterministic timestamp
    private val accountKey = "xtream:real-test-provider"

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // =========================================================================
    // Lazy-loaded real API data
    // =========================================================================

    private val vodStreams: List<XtreamVodStream> by lazy {
        val file = File(testDataDir, "vod_streams.json")
        check(file.exists()) { "Real test data not found: ${file.absolutePath}" }
        jsonParser.decodeFromString(file.readText())
    }

    private val seriesStreams: List<XtreamSeriesStream> by lazy {
        val file = File(testDataDir, "series.json")
        check(file.exists()) { "Real test data not found: ${file.absolutePath}" }
        jsonParser.decodeFromString(file.readText())
    }

    private val liveStreams: List<XtreamLiveStream> by lazy {
        val file = File(testDataDir, "live_streams.json")
        check(file.exists()) { "Real test data not found: ${file.absolutePath}" }
        jsonParser.decodeFromString(file.readText())
    }

    private val vodDetailInfo: XtreamVodInfo by lazy {
        val file = File(testDataDir, "vod_details_response_xtream.txt")
        check(file.exists()) { "Real test data not found: ${file.absolutePath}" }
        jsonParser.decodeFromString(file.readText())
    }

    private val seriesDetailInfo: XtreamSeriesInfo by lazy {
        val file = File(testDataDir, "series_detail_response_xtream.txt")
        check(file.exists()) { "Real test data not found: ${file.absolutePath}" }
        jsonParser.decodeFromString(file.readText())
    }

    // =========================================================================
    // Chain execution helpers (mirrors NxCatalogWriter.ingest exactly)
    // =========================================================================

    private data class ChainOutput(
        val work: NxWorkRepository.Work,
        val sourceRef: NxWorkSourceRefRepository.SourceRef,
        val variant: NxWorkVariantRepository.Variant?,
    )

    private fun buildWorkKey(normalized: NormalizedMediaMetadata): String {
        // Mirror MediaTypeMapper.toWorkType() which is internal to data-nx
        val workType = when (normalized.mediaType) {
            MediaType.MOVIE -> WorkType.MOVIE
            MediaType.SERIES -> WorkType.SERIES
            MediaType.SERIES_EPISODE -> WorkType.EPISODE
            MediaType.LIVE -> WorkType.LIVE_CHANNEL
            MediaType.CLIP -> WorkType.CLIP
            MediaType.AUDIOBOOK -> WorkType.AUDIOBOOK
            MediaType.MUSIC -> WorkType.MUSIC_TRACK
            MediaType.PODCAST -> WorkType.UNKNOWN
            MediaType.UNKNOWN -> WorkType.UNKNOWN
        }
        return NxKeyGenerator.workKey(
            workType = workType,
            title = normalized.canonicalTitle,
            year = normalized.year,
            tmdbId = normalized.tmdb?.id,
            season = normalized.season,
            episode = normalized.episode,
        )
    }

    private fun buildSourceKey(raw: RawMediaMetadata): String =
        SourceKeyParser.buildSourceKey(raw.sourceType, accountKey, raw.sourceId)

    private suspend fun runNxChain(raw: RawMediaMetadata): ChainOutput {
        val normalized = normalizer.normalize(raw)
        val workKey = buildWorkKey(normalized)
        val sourceKey = buildSourceKey(raw)
        val variantKey = "$sourceKey#original"

        val work = workBuilder.build(normalized, workKey, fixedNow)
        val sourceRef = sourceRefBuilder.build(raw, workKey, accountKey, sourceKey, fixedNow)
        val variant = if (raw.playbackHints.isNotEmpty()) {
            variantBuilder.build(variantKey, workKey, sourceKey, raw.playbackHints, normalized.durationMs, fixedNow)
        } else {
            null
        }

        return ChainOutput(work, sourceRef, variant)
    }

    // =========================================================================
    // Golden file assertion
    // =========================================================================

    private fun assertGoldenChain(
        testName: String,
        transportJson: String,
        pipelineJson: JsonElement,
        raw: RawMediaMetadata,
        normalized: NormalizedMediaMetadata,
        output: ChainOutput,
    ) {
        val compositeElement = FullChainStepSerializer.toCompositeJson(
            transportInput = transportJson,
            pipeline = pipelineJson,
            raw = raw,
            normalized = normalized,
            work = output.work,
            sourceRef = output.sourceRef,
            variant = output.variant,
        )
        val actualString = FullChainStepSerializer.toJsonString(compositeElement)

        val goldenFile = File(goldenDir, "$testName.json")

        if (!goldenFile.exists() || shouldUpdate) {
            goldenDir.mkdirs()
            goldenFile.writeText(actualString + "\n")
            println("📝 Golden file ${if (goldenFile.exists()) "UPDATED" else "CREATED"}: ${goldenFile.path}")
            return
        }

        val expectedString = goldenFile.readText().trim()
        val expected = FullChainStepSerializer.parseGoldenFile(expectedString)
        val actual = FullChainStepSerializer.parseGoldenFile(actualString)

        assertEquals(
            expected, actual,
            buildString {
                appendLine("Golden file mismatch for '$testName'")
                appendLine()
                appendLine("To update golden files, run:")
                appendLine("  ./gradlew :pipeline:xtream:test --tests \"*XtreamRealDataChainTest*\" -Dgolden.update=true")
            },
        )
    }

    // =========================================================================
    // Helper: Extract single transport JSON item by index from raw file
    // =========================================================================

    /**
     * Extracts the JSON string of a single array element from a large JSON array file.
     * This gives us the verbatim API JSON for step1_transport in the golden file.
     */
    private fun extractJsonArrayItem(fileName: String, index: Int): String {
        val file = File(testDataDir, fileName)
        val elements = jsonParser.parseToJsonElement(file.readText())
        val array = elements as kotlinx.serialization.json.JsonArray
        return jsonParser.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), array[index])
    }

    // =========================================================================
    // VOD Tests
    // =========================================================================

    @Test
    fun `VOD chain - first item from real vod_streams json`() = runTest {
        val transport = vodStreams[0]
        val transportJson = extractJsonArrayItem("vod_streams.json", 0)

        // Step 2: Transport → Pipeline (REAL production function)
        val pipeline = transport.toPipelineItem()
        val pipelineJson = FullChainStepSerializer.vodItemToJson(pipeline)

        // Step 3: Pipeline → Raw (REAL production function)
        val raw = pipeline.toRawMetadata(accountLabel = accountKey)

        // Step 4: Raw → Normalized (REAL production normalizer)
        val normalized = normalizer.normalize(raw)

        // Step 5: Normalized → NX entities (REAL production builders)
        val output = runNxChain(raw)

        // Structural sanity checks
        assertEquals(NxWorkSourceRefRepository.SourceType.XTREAM, output.sourceRef.sourceType)
        assertTrue(output.sourceRef.sourceItemKey.isNotBlank(), "sourceItemKey must not be blank")

        assertGoldenChain("vod_first_item", transportJson, pipelineJson, raw, normalized, output)
    }

    @Test
    fun `VOD chain - item with empty rating and no icon (edge case)`() = runTest {
        // Find an item with empty/null rating — common in real data
        val index = vodStreams.indexOfFirst { it.rating.isNullOrBlank() && it.streamIcon.isNullOrBlank() }
        if (index < 0) {
            println("SKIP: No VOD item with empty rating + no icon found")
            return@runTest
        }

        val transport = vodStreams[index]
        val transportJson = extractJsonArrayItem("vod_streams.json", index)

        val pipeline = transport.toPipelineItem()
        val pipelineJson = FullChainStepSerializer.vodItemToJson(pipeline)
        val raw = pipeline.toRawMetadata(accountLabel = accountKey)
        val normalized = normalizer.normalize(raw)
        val output = runNxChain(raw)

        // Edge case verification: null rating must not cause NPE or wrong defaults
        assertGoldenChain("vod_empty_rating_no_icon", transportJson, pipelineJson, raw, normalized, output)
    }

    @Test
    fun `VOD chain - item with pipe-separated title format (edge case)`() = runTest {
        // Find item with "|" in name — pattern: "Name | Year | Rating"
        val index = vodStreams.indexOfFirst { it.name?.contains("|") == true }
        if (index < 0) {
            println("SKIP: No VOD item with pipe-separated title found")
            return@runTest
        }

        val transport = vodStreams[index]
        val transportJson = extractJsonArrayItem("vod_streams.json", index)

        val pipeline = transport.toPipelineItem()
        val pipelineJson = FullChainStepSerializer.vodItemToJson(pipeline)
        val raw = pipeline.toRawMetadata(accountLabel = accountKey)
        val normalized = normalizer.normalize(raw)
        val output = runNxChain(raw)

        // Verify normalizer handles pipe-separated format
        assertTrue(
            !output.work.displayTitle.contains("|"),
            "Normalizer should handle pipe-separated titles. Got: ${output.work.displayTitle}",
        )

        assertGoldenChain("vod_pipe_title", transportJson, pipelineJson, raw, normalized, output)
    }

    // =========================================================================
    // VOD Detail Test
    // =========================================================================

    @Test
    fun `VOD detail chain - 36 China Town real detail response`() = runTest {
        val vodInfo = vodDetailInfo
        val transportJson = File(testDataDir, "vod_details_response_xtream.txt").readText()

        // VOD detail needs a minimal VodItem for context
        val vodItem = XtreamVodItem(
            id = vodInfo.movieData?.streamId ?: 0,
            name = vodInfo.movieData?.name ?: "",
            containerExtension = vodInfo.movieData?.containerExtension,
            added = vodInfo.movieData?.added?.toLongOrNull(),
            categoryId = vodInfo.movieData?.categoryId,
        )

        // Step 3: Direct transport → Raw (detail response has its own mapper)
        val raw = vodInfo.toRawMediaMetadata(vodItem = vodItem, accountLabel = accountKey)

        // Serialize vodItem as the pipeline step (detail doesn't go through toPipelineItem)
        val pipelineJson = FullChainStepSerializer.vodItemToJson(vodItem)

        // Step 4: Raw → Normalized
        val normalized = normalizer.normalize(raw)

        // Step 5: Normalized → NX entities
        val output = runNxChain(raw)

        // Detail response has rich metadata — verify it propagates
        assertNotNull(output.work.plot, "VOD detail should have a plot")
        assertNotNull(output.work.genres, "VOD detail should have genres")

        assertGoldenChain("vod_detail_36chinatown", transportJson, pipelineJson, raw, normalized, output)
    }

    // =========================================================================
    // Series Tests
    // =========================================================================

    @Test
    fun `Series chain - first valid item from real series json`() = runTest {
        val index = seriesStreams.indexOfFirst { (it.seriesId ?: it.id ?: -1) > 0 }
        check(index >= 0) { "No valid series item found" }

        val transport = seriesStreams[index]
        val transportJson = extractJsonArrayItem("series.json", index)

        val pipeline = transport.toPipelineItem()
        val pipelineJson = FullChainStepSerializer.seriesItemToJson(pipeline)
        val raw = pipeline.toRawMetadata(accountLabel = accountKey)
        val normalized = normalizer.normalize(raw)
        val output = runNxChain(raw)

        assertEquals(NxWorkRepository.WorkType.SERIES, output.work.type)

        assertGoldenChain("series_first_valid", transportJson, pipelineJson, raw, normalized, output)
    }

    @Test
    fun `Series chain - item with negative series_id (edge case)`() = runTest {
        val index = seriesStreams.indexOfFirst { (it.seriesId ?: 0) < 0 }
        if (index < 0) {
            println("SKIP: No series item with negative ID found")
            return@runTest
        }

        val transport = seriesStreams[index]
        val transportJson = extractJsonArrayItem("series.json", index)

        val pipeline = transport.toPipelineItem()
        val pipelineJson = FullChainStepSerializer.seriesItemToJson(pipeline)
        val raw = pipeline.toRawMetadata(accountLabel = accountKey)
        val normalized = normalizer.normalize(raw)
        val output = runNxChain(raw)

        // Negative ID should still produce valid output
        assertTrue(output.sourceRef.sourceItemKey.isNotBlank())

        assertGoldenChain("series_negative_id", transportJson, pipelineJson, raw, normalized, output)
    }

    // =========================================================================
    // Episode Tests
    // =========================================================================

    @Test
    fun `Episode chain - first episode from real series detail`() = runTest {
        val seriesInfo = seriesDetailInfo
        val transportJson = File(testDataDir, "series_detail_response_xtream.txt").readText()

        val seriesName = seriesInfo.info?.name ?: "Unknown"
        val seriesId = 1899 // Known from series list

        // Step 2: Transport → Pipeline (REAL toEpisodes function)
        val episodes = seriesInfo.toEpisodes(seriesId, seriesName)
        assertTrue(episodes.isNotEmpty(), "Should have at least one episode")

        val pipeline = episodes.first()
        val pipelineJson = FullChainStepSerializer.episodeToJson(pipeline)

        // Step 3: Pipeline → Raw
        val raw = pipeline.toRawMediaMetadata()

        // Step 4: Raw → Normalized
        val normalized = normalizer.normalize(raw)

        // Step 5: Normalized → NX entities
        val output = runNxChain(raw)

        assertEquals(NxWorkRepository.WorkType.EPISODE, output.work.type)
        assertNotNull(output.work.season, "Episode should have season")
        assertNotNull(output.work.episode, "Episode should have episode number")

        // Use only the first episode's transport JSON (extract from seriesInfo)
        val firstEpisodeTransportJson = run {
            val parsed = jsonParser.parseToJsonElement(transportJson)
            val obj = parsed as kotlinx.serialization.json.JsonObject
            val episodesMap = obj["episodes"] as? kotlinx.serialization.json.JsonObject ?: return@run "{}"
            val firstSeason = episodesMap.entries.firstOrNull() ?: return@run "{}"
            val firstEp = (firstSeason.value as? kotlinx.serialization.json.JsonArray)?.firstOrNull() ?: return@run "{}"
            jsonParser.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), firstEp)
        }

        assertGoldenChain(
            "episode_first_from_1899",
            firstEpisodeTransportJson,
            pipelineJson,
            raw,
            normalized,
            output,
        )
    }

    // =========================================================================
    // Live Tests
    // =========================================================================

    @Test
    fun `Live channel chain - first item from real live_streams json`() = runTest {
        val transport = liveStreams[0]
        val transportJson = extractJsonArrayItem("live_streams.json", 0)

        val pipeline = transport.toPipelineItem()
        val pipelineJson = FullChainStepSerializer.channelToJson(pipeline)
        val raw = pipeline.toRawMediaMetadata(accountLabel = accountKey)
        val normalized = normalizer.normalize(raw)
        val output = runNxChain(raw)

        assertEquals(NxWorkRepository.WorkType.LIVE_CHANNEL, output.work.type)

        assertGoldenChain("live_first_item", transportJson, pipelineJson, raw, normalized, output)
    }

    @Test
    fun `Live channel chain - item with Unicode decorators (edge case)`() = runTest {
        // Find live channel with Unicode block decorators (▃ ▅ ▆ █ etc.)
        val index = liveStreams.indexOfFirst {
            it.name?.any { c -> c.code in 0x2580..0x259F } == true
        }
        if (index < 0) {
            println("SKIP: No live channel with Unicode block decorators found")
            return@runTest
        }

        val transport = liveStreams[index]
        val transportJson = extractJsonArrayItem("live_streams.json", index)

        val pipeline = transport.toPipelineItem()
        val pipelineJson = FullChainStepSerializer.channelToJson(pipeline)
        val raw = pipeline.toRawMediaMetadata(accountLabel = accountKey)
        val normalized = normalizer.normalize(raw)
        val output = runNxChain(raw)

        // Verify normalizer cleans Unicode decorators from channel name
        assertGoldenChain("live_unicode_decorators", transportJson, pipelineJson, raw, normalized, output)
    }

    @Test
    fun `Live channel chain - item with catchup (tv_archive=1)`() = runTest {
        val index = liveStreams.indexOfFirst { (it.tvArchive ?: 0) > 0 }
        if (index < 0) {
            println("SKIP: No live channel with catchup found")
            return@runTest
        }

        val transport = liveStreams[index]
        val transportJson = extractJsonArrayItem("live_streams.json", index)

        val pipeline = transport.toPipelineItem()
        val pipelineJson = FullChainStepSerializer.channelToJson(pipeline)
        val raw = pipeline.toRawMediaMetadata(accountLabel = accountKey)
        val normalized = normalizer.normalize(raw)
        val output = runNxChain(raw)

        // Catchup fields must propagate to SourceRef
        assertTrue(output.sourceRef.tvArchive > 0, "tvArchive should be > 0")
        assertTrue(output.sourceRef.tvArchiveDuration > 0, "tvArchiveDuration should be > 0")

        assertGoldenChain("live_with_catchup", transportJson, pipelineJson, raw, normalized, output)
    }

    // =========================================================================
    // Batch consistency test — multiple items from same source
    // =========================================================================

    @Test
    fun `VOD chain consistency - first 5 items all produce valid NX entities`() = runTest {
        val count = minOf(5, vodStreams.size)

        for (idx in 0 until count) {
            val transport = vodStreams[idx]
            val pipeline = transport.toPipelineItem()
            val raw = pipeline.toRawMetadata(accountLabel = accountKey)
            val normalized = normalizer.normalize(raw)
            val output = runNxChain(raw)

            // Structural invariants that must hold for ALL items
            assertTrue(
                output.work.workKey.isNotBlank(),
                "Item[$idx] workKey blank: ${transport.name}",
            )
            assertTrue(
                output.sourceRef.sourceKey.isNotBlank(),
                "Item[$idx] sourceKey blank: ${transport.name}",
            )
            assertTrue(
                output.work.displayTitle.isNotBlank(),
                "Item[$idx] displayTitle blank: ${transport.name}",
            )
            assertEquals(
                NxWorkSourceRefRepository.SourceType.XTREAM,
                output.sourceRef.sourceType,
                "Item[$idx] sourceType: ${transport.name}",
            )
        }
    }
}
