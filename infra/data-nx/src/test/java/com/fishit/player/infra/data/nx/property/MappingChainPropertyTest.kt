package com.fishit.player.infra.data.nx.property

import com.fishit.player.core.metadata.RegexMediaMetadataNormalizer
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.PipelineIdTag
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.persistence.obx.NxKeyGenerator
import com.fishit.player.infra.data.nx.mapper.MediaTypeMapper
import com.fishit.player.infra.data.nx.mapper.SourceItemKindMapper
import com.fishit.player.infra.data.nx.mapper.SourceKeyParser
import com.fishit.player.infra.data.nx.writer.builder.SourceRefBuilder
import com.fishit.player.infra.data.nx.writer.builder.VariantBuilder
import com.fishit.player.infra.data.nx.writer.builder.WorkEntityBuilder
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Property-based tests for the mapping chain.
 *
 * Uses Kotest generators to fuzz-test the full chain with random inputs,
 * verifying **invariants** that must hold for ALL possible inputs:
 *
 * 1. **No crashes** — normalizer + builders never throw for valid inputs
 * 2. **Key format** — work keys, source keys match expected patterns
 * 3. **Recognition consistency** — TMDB presence ↔ CONFIRMED state
 * 4. **Type mapping** — MediaType ↔ WorkType mapping is consistent
 * 5. **Container normalization** — m3u8/m3u always normalize to "hls"
 * 6. **Clean item keys** — Xtream keys get numeric ID extracted
 * 7. **Title invariants** — canonicalTitle is never blank
 * 8. **ImageRef validity** — constructed refs have valid fields
 *
 * Each test runs 200 iterations with distinct random seeds.
 */
@OptIn(io.kotest.common.ExperimentalKotest::class)
class MappingChainPropertyTest {

    private val normalizer = RegexMediaMetadataNormalizer()
    private val workBuilder = WorkEntityBuilder()
    private val sourceRefBuilder = SourceRefBuilder()
    private val variantBuilder = VariantBuilder()

    private val fixedNow = 1700000000000L
    private val config = PropTestConfig(iterations = 200)

    // =========================================================================
    // Key building (mirrors NxCatalogWriter - uses canonical mappers)
    // =========================================================================

    /**
     * Build work key from normalized metadata.
     * Delegates to NxKeyGenerator.workKey() — single source of truth.
     */
    private fun buildWorkKey(normalized: NormalizedMediaMetadata): String {
        val workType = MediaTypeMapper.toWorkType(normalized.mediaType)
        return NxKeyGenerator.workKey(
            workType = workType,
            title = normalized.canonicalTitle,
            year = normalized.year,
            tmdbId = normalized.tmdb?.id,
            season = normalized.season,
            episode = normalized.episode,
        )
    }

    // =========================================================================
    // Property 1: Chain never crashes for any valid input
    // =========================================================================

    @Test
    fun `normalizer never throws for any RawMediaMetadata`() = runTest {
        checkAll(config, Generators.rawMediaMetadata) { raw ->
            // Should complete without exception
            val normalized = normalizer.normalize(raw)
            assertNotNull(normalized, "normalize() must return non-null")
        }
    }

    @Test
    fun `full chain never throws for any RawMediaMetadata`() = runTest {
        checkAll(config, Generators.rawMediaMetadata) { raw ->
            val normalized = normalizer.normalize(raw)
            val workKey = buildWorkKey(normalized)
            val sourceKey = SourceKeyParser.buildSourceKey(raw.sourceType, "test:account", raw.sourceId)
            val variantKey = "$sourceKey#original"

            // All builders must complete without exception
            val work = workBuilder.build(normalized, workKey, fixedNow)
            val sourceRef = sourceRefBuilder.build(raw, workKey, "test:account", sourceKey, fixedNow)
            if (raw.playbackHints.isNotEmpty()) {
                variantBuilder.build(variantKey, workKey, sourceKey, raw.playbackHints, normalized.durationMs, fixedNow)
            }

            assertNotNull(work)
            assertNotNull(sourceRef)
        }
    }

    // =========================================================================
    // Property 2: Canonical title is never blank
    // =========================================================================

    @Test
    fun `canonicalTitle is never blank after normalization`() = runTest {
        checkAll(config, Generators.rawMediaMetadata) { raw ->
            val normalized = normalizer.normalize(raw)
            // F4 Fix: canonicalTitle is ALWAYS non-blank (parser returns "[Untitled]" for blank input)
            assertTrue(
                normalized.canonicalTitle.isNotBlank(),
                "canonicalTitle must never be blank. Input: '${raw.originalTitle}'",
            )
        }
    }

    @Test
    fun `blank originalTitle produces Untitled fallback`() = runTest {
        // Explicit test for F4 fix: blank input → "[Untitled]"
        val blankInputs = listOf("", "   ", "\t", "\n")
        for (input in blankInputs) {
            val raw = RawMediaMetadata(
                originalTitle = input,
                mediaType = MediaType.MOVIE,
                pipelineIdTag = PipelineIdTag.TELEGRAM,
                sourceType = SourceType.TELEGRAM,
                sourceId = "test:blank",
                sourceLabel = "Test",
            )
            val normalized = normalizer.normalize(raw)
            assertEquals(
                "[Untitled]",
                normalized.canonicalTitle,
                "Blank originalTitle '$input' should become '[Untitled]'",
            )
        }
    }

    // =========================================================================
    // Property 3: Work key format is always valid
    // =========================================================================

    @Test
    fun `work key matches expected pattern`() = runTest {
        checkAll(config, Generators.rawMediaMetadata) { raw ->
            val normalized = normalizer.normalize(raw)
            val workKey = buildWorkKey(normalized)

            // Format: {type}:{authority}:{id}
            val parts = workKey.split(":")
            assertTrue(parts.size >= 3, "workKey must have at least 3 colon-separated parts: $workKey")
            // All WorkType enum values lowercase (from MediaTypeMapper.toWorkType())
            val validTypes = listOf(
                "movie", "series", "episode", "live_channel", "clip",
                "audiobook", "music_track", "unknown"
            )
            assertTrue(
                parts[0] in validTypes,
                "workKey type must be valid: ${parts[0]}",
            )
            assertTrue(
                parts[1] in listOf("tmdb", "heuristic"),
                "workKey authority must be tmdb or heuristic: ${parts[1]}",
            )
            assertTrue(parts[2].isNotBlank(), "workKey id must not be blank")
        }
    }

    // =========================================================================
    // Property 4: Recognition state is consistent with TMDB presence
    // =========================================================================

    @Test
    fun `recognition state is CONFIRMED iff tmdb ref is present`() = runTest {
        checkAll(config, Generators.rawMediaMetadata) { raw ->
            val normalized = normalizer.normalize(raw)
            val workKey = buildWorkKey(normalized)
            val work = workBuilder.build(normalized, workKey, fixedNow)

            if (normalized.tmdb != null) {
                assertEquals(
                    NxWorkRepository.RecognitionState.CONFIRMED,
                    work.recognitionState,
                    "tmdb present → CONFIRMED. tmdb=${normalized.tmdb}",
                )
            } else {
                assertEquals(
                    NxWorkRepository.RecognitionState.HEURISTIC,
                    work.recognitionState,
                    "no tmdb → HEURISTIC",
                )
            }
        }
    }

    // =========================================================================
    // Property 5: Type mapping consistency
    // =========================================================================

    @Test
    fun `WorkType matches MediaType through mapper`() = runTest {
        checkAll(config, Generators.rawMediaMetadata) { raw ->
            val normalized = normalizer.normalize(raw)
            val workKey = buildWorkKey(normalized)
            val work = workBuilder.build(normalized, workKey, fixedNow)

            val expected = MediaTypeMapper.toWorkType(normalized.mediaType)
            assertEquals(expected, work.type, "Work.type must equal toWorkType(${normalized.mediaType})")
        }
    }

    // =========================================================================
    // Property 6: Container normalization invariants
    // =========================================================================

    @Test
    fun `m3u8 and m3u always normalize to hls`() = runTest {
        val hlsHints = Arb.of(
            mapOf("xtream.containerExtension" to "m3u8"),
            mapOf("xtream.containerExtension" to "m3u"),
            mapOf("containerExtension" to "m3u8"),
            mapOf("containerExtension" to "m3u"),
            mapOf("xtream.containerExtension" to "M3U8"),
            mapOf("xtream.containerExtension" to "M3U"),
        )

        checkAll(config, hlsHints) { hints ->
            val variant = variantBuilder.build(
                "test:key#original", "work:key", "test:key", hints, 1000L, fixedNow,
            )
            assertEquals("hls", variant.container, "m3u/m3u8 must normalize to hls. Hints: $hints")
        }
    }

    @Test
    fun `known containers pass through correctly`() = runTest {
        val knownContainers = Arb.of("mp4", "mkv", "avi", "webm", "ts", "mov", "wmv", "flv")

        checkAll(config, knownContainers) { ext ->
            val variant = variantBuilder.build(
                "test:key#original", "work:key", "test:key",
                mapOf("xtream.containerExtension" to ext), 1000L, fixedNow,
            )
            assertEquals(ext, variant.container, "Known container '$ext' should pass through")
        }
    }

    @Test
    fun `empty playbackHints produce null container`() {
        val variant = variantBuilder.build(
            "test:key#original", "work:key", "test:key", emptyMap(), 1000L, fixedNow,
        )
        assertEquals(null, variant.container, "No hints → null container")
    }

    // =========================================================================
    // Property 7: Clean item key extraction
    // =========================================================================

    @Test
    fun `xtream sourceIds produce clean numeric item keys`() = runTest {
        checkAll(config, Generators.xtreamSourceId) { sourceId ->
            val raw = RawMediaMetadata(
                originalTitle = "Test",
                sourceType = SourceType.XTREAM,
                sourceLabel = "test",
                sourceId = sourceId,
            )
            val sourceRef = sourceRefBuilder.build(raw, "work:key", "xtream:server", "src:key", fixedNow)

            // xtream:vod:12345 → 12345 (clean numeric)
            assertFalse(
                sourceRef.sourceItemKey.startsWith("xtream:"),
                "Xtream sourceItemKey must not contain 'xtream:' prefix. Got: ${sourceRef.sourceItemKey}",
            )
            assertTrue(
                sourceRef.sourceItemKey.all { it.isDigit() },
                "Xtream sourceItemKey must be purely numeric. Got: ${sourceRef.sourceItemKey}",
            )
        }
    }

    @Test
    fun `telegram sourceIds preserve full format`() = runTest {
        checkAll(config, Generators.telegramSourceId) { sourceId ->
            val raw = RawMediaMetadata(
                originalTitle = "Test",
                sourceType = SourceType.TELEGRAM,
                sourceLabel = "test",
                sourceId = sourceId,
            )
            val sourceRef = sourceRefBuilder.build(raw, "work:key", "telegram:account", "src:key", fixedNow)

            assertTrue(
                sourceRef.sourceItemKey.startsWith("msg:"),
                "Telegram sourceItemKey must preserve msg: format. Got: ${sourceRef.sourceItemKey}",
            )
            assertEquals(sourceId, sourceRef.sourceItemKey, "Telegram sourceItemKey must be full sourceId")
        }
    }

    // =========================================================================
    // Property 8: Source key format
    // =========================================================================

    @Test
    fun `source key always starts with src prefix`() = runTest {
        checkAll(config, Generators.sourceType, Generators.sourceId) { srcType, srcId ->
            val sourceKey = SourceKeyParser.buildSourceKey(srcType, "test-account", srcId)
            assertTrue(
                sourceKey.startsWith("src:"),
                "sourceKey must start with 'src:'. Got: $sourceKey",
            )
        }
    }

    // =========================================================================
    // Property 9: WorkEntityBuilder sets sortTitle and titleNormalized correctly
    // =========================================================================

    @Test
    fun `sortTitle equals canonicalTitle`() = runTest {
        checkAll(config, Generators.rawMediaMetadata) { raw ->
            val normalized = normalizer.normalize(raw)
            val workKey = buildWorkKey(normalized)
            val work = workBuilder.build(normalized, workKey, fixedNow)

            assertEquals(
                normalized.canonicalTitle,
                work.sortTitle,
                "sortTitle must equal canonicalTitle",
            )
        }
    }

    @Test
    fun `titleNormalized is lowercase of canonicalTitle`() = runTest {
        checkAll(config, Generators.rawMediaMetadata) { raw ->
            val normalized = normalizer.normalize(raw)
            val workKey = buildWorkKey(normalized)
            val work = workBuilder.build(normalized, workKey, fixedNow)

            assertEquals(
                normalized.canonicalTitle.lowercase(),
                work.titleNormalized,
                "titleNormalized must be lowercase canonicalTitle",
            )
        }
    }

    // =========================================================================
    // Property 10: tmdbId prefers typed tmdb ref, falls back to externalIds
    // =========================================================================

    @Test
    fun `tmdbId is set from tmdb ref or externalIds`() = runTest {
        checkAll(config, Generators.rawMediaMetadata) { raw ->
            val normalized = normalizer.normalize(raw)
            val workKey = buildWorkKey(normalized)
            val work = workBuilder.build(normalized, workKey, fixedNow)

            val expectedTmdbId = (normalized.tmdb ?: normalized.externalIds.tmdb)?.id?.toString()
            assertEquals(
                expectedTmdbId,
                work.tmdbId,
                "tmdbId must prefer typed tmdb ref, fall back to externalIds.tmdb",
            )
        }
    }

    // =========================================================================
    // Property 11: displayTitle equals canonicalTitle (no post-processing)
    // =========================================================================

    @Test
    fun `Work displayTitle equals normalized canonicalTitle`() = runTest {
        checkAll(config, Generators.rawMediaMetadata) { raw ->
            val normalized = normalizer.normalize(raw)
            val workKey = buildWorkKey(normalized)
            val work = workBuilder.build(normalized, workKey, fixedNow)

            assertEquals(
                normalized.canonicalTitle,
                work.displayTitle,
                "displayTitle must match canonicalTitle",
            )
        }
    }

    // =========================================================================
    // Property 12: SourceRef.sourceItemKind matches mapper
    // =========================================================================

    @Test
    fun `sourceItemKind matches SourceItemKindMapper`() = runTest {
        checkAll(config, Generators.rawMediaMetadata) { raw ->
            val sourceRef = sourceRefBuilder.build(raw, "work:key", "test:account", "src:key", fixedNow)
            val expected = SourceItemKindMapper.fromMediaType(raw.mediaType)

            assertEquals(
                expected,
                sourceRef.sourceItemKind,
                "sourceItemKind must match SourceItemKindMapper.fromMediaType(${raw.mediaType})",
            )
        }
    }

    // =========================================================================
    // Property 13: SourceRef availability is always ACTIVE for new items
    // =========================================================================

    @Test
    fun `sourceRef availability is always ACTIVE`() = runTest {
        checkAll(config, Generators.rawMediaMetadata) { raw ->
            val sourceRef = sourceRefBuilder.build(raw, "work:key", "test:account", "src:key", fixedNow)

            assertEquals(
                NxWorkSourceRefRepository.AvailabilityState.ACTIVE,
                sourceRef.availability,
                "New sourceRef must always be ACTIVE",
            )
        }
    }

    // =========================================================================
    // Property 14: SourceRef sourceTitle preserves original title
    // =========================================================================

    @Test
    fun `sourceRef sourceTitle equals raw originalTitle`() = runTest {
        checkAll(config, Generators.rawMediaMetadata) { raw ->
            val sourceRef = sourceRefBuilder.build(raw, "work:key", "test:account", "src:key", fixedNow)

            assertEquals(
                raw.originalTitle,
                sourceRef.sourceTitle,
                "sourceTitle must be the raw originalTitle",
            )
        }
    }

    // =========================================================================
    // Property 15: createdAtMs uses addedTimestamp when > 0, else now
    // =========================================================================

    @Test
    fun `createdAtMs uses addedTimestamp when positive`() = runTest {
        checkAll(config, Generators.rawMediaMetadata) { raw ->
            val normalized = normalizer.normalize(raw)
            val workKey = buildWorkKey(normalized)
            val work = workBuilder.build(normalized, workKey, fixedNow)

            val expectedCreatedAt = normalized.addedTimestamp?.takeIf { it > 0 } ?: fixedNow
            assertEquals(
                expectedCreatedAt,
                work.createdAtMs,
                "createdAtMs must use addedTimestamp (if >0) or fall back to now",
            )
        }
    }

    @Test
    fun `updatedAtMs always equals now`() = runTest {
        checkAll(config, Generators.rawMediaMetadata) { raw ->
            val normalized = normalizer.normalize(raw)
            val workKey = buildWorkKey(normalized)
            val work = workBuilder.build(normalized, workKey, fixedNow)

            assertEquals(
                fixedNow,
                work.updatedAtMs,
                "updatedAtMs must always equal now parameter",
            )
        }
    }

    // =========================================================================
    // Property 16: Xtream mediaType is preserved (not refined by normalizer)
    // =========================================================================

    @Test
    fun `xtream source preserves mediaType through normalization`() = runTest {
        checkAll(config, Generators.mediaType) { mType ->
            val raw = RawMediaMetadata(
                originalTitle = "Test Item 2024",
                mediaType = mType,
                sourceType = SourceType.XTREAM,
                sourceLabel = "test",
                sourceId = "xtream:vod:123",
            )
            val normalized = normalizer.normalize(raw)

            assertEquals(
                mType,
                normalized.mediaType,
                "Xtream mediaType must pass through unchanged (normalizer only refines UNKNOWN for non-Xtream)",
            )
        }
    }

    // =========================================================================
    // Property 17: Variant is deterministic (same input → same output)
    // =========================================================================

    @Test
    fun `variant builder is deterministic`() = runTest {
        checkAll(config, Generators.playbackHints) { hints ->
            val v1 = variantBuilder.build("k#o", "wk", "sk", hints, 1000L, fixedNow)
            val v2 = variantBuilder.build("k#o", "wk", "sk", hints, 1000L, fixedNow)

            assertEquals(v1.container, v2.container, "container must be deterministic")
            assertEquals(v1.label, v2.label, "label must be deterministic")
            assertEquals(v1.isDefault, v2.isDefault, "isDefault must be deterministic")
        }
    }

    // =========================================================================
    // Property 18: Normalizer passes through tmdb from externalIds
    // =========================================================================

    @Test
    fun `normalizer tmdb equals raw externalIds tmdb`() = runTest {
        checkAll(config, Generators.rawMediaMetadata) { raw ->
            val normalized = normalizer.normalize(raw)

            assertEquals(
                raw.externalIds.tmdb,
                normalized.tmdb,
                "normalized.tmdb must be populated from raw.externalIds.tmdb",
            )
        }
    }

    // =========================================================================
    // Property 19: Normalizer passes through all ImageRef fields unchanged
    // =========================================================================

    @Test
    fun `normalizer passes through poster and backdrop unchanged`() = runTest {
        checkAll(config, Generators.rawMediaMetadata) { raw ->
            val normalized = normalizer.normalize(raw)

            assertEquals(raw.poster, normalized.poster, "poster must pass through")
            assertEquals(raw.backdrop, normalized.backdrop, "backdrop must pass through")
            assertEquals(raw.thumbnail, normalized.thumbnail, "thumbnail must pass through")
        }
    }

    // =========================================================================
    // Property 20: isAdult passes through normalizer unchanged
    // =========================================================================

    @Test
    fun `normalizer passes through isAdult unchanged`() = runTest {
        checkAll(config, Generators.rawMediaMetadata) { raw ->
            val normalized = normalizer.normalize(raw)

            assertEquals(
                raw.isAdult,
                normalized.isAdult,
                "isAdult must pass through normalizer unchanged",
            )
        }
    }
}
