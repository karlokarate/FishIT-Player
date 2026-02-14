package com.fishit.player.infra.data.nx.writer

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.repository.NxWorkRelationRepository
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkVariantRepository
import com.fishit.player.core.model.repository.toEnrichment
import com.fishit.player.infra.data.nx.writer.builder.WorkEntityBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for [NxEnrichmentWriter.enrichWork].
 *
 * Verifies that enrichWork uses [NxWorkRepository.enrichFromDetail] semantics
 * (always overwrite with non-null detail API values) rather than
 * [NxWorkRepository.enrichIfAbsent] (write-once).
 *
 * This is critical for issue #715: the detail info API (`get_vod_info`)
 * returns richer metadata (plot, cast, backdrop, etc.) that must overwrite
 * the less-complete data from the listing API.
 */
class NxEnrichmentWriterEnrichWorkTest {

    private val workRepository = mockk<NxWorkRepository>(relaxed = true)
    private val variantRepository = mockk<NxWorkVariantRepository>(relaxed = true)
    private val relationRepository = mockk<NxWorkRelationRepository>(relaxed = true)
    private val workEntityBuilder = WorkEntityBuilder()

    private val enrichmentWriter = NxEnrichmentWriter(
        workRepository = workRepository,
        variantRepository = variantRepository,
        relationRepository = relationRepository,
        workEntityBuilder = workEntityBuilder,
    )

    @Test
    fun `enrichWork calls enrichFromDetail not enrichIfAbsent`(): Unit = runBlocking {
        // GIVEN: a workKey and metadata
        val workKey = "movie:tmdb:1126336"
        val metadata = createTestMetadata()

        val enrichmentSlot = slot<NxWorkRepository.Enrichment>()
        coEvery {
            workRepository.enrichFromDetail(eq(workKey), capture(enrichmentSlot))
        } returns NxWorkRepository.Work(
            workKey = workKey,
            type = NxWorkRepository.WorkType.MOVIE,
            displayTitle = "The Change",
            plot = "Rich plot from detail API",
        )

        // WHEN: enrichWork is called
        val result = enrichmentWriter.enrichWork(workKey, metadata)

        // THEN: enrichFromDetail was called (not enrichIfAbsent)
        coVerify(exactly = 1) { workRepository.enrichFromDetail(workKey, any()) }
        coVerify(exactly = 0) { workRepository.enrichIfAbsent(any(), any()) }

        assertNotNull(result)
    }

    @Test
    fun `enrichWork returns null when work not found`(): Unit = runBlocking {
        // GIVEN: enrichFromDetail returns null
        val workKey = "movie:unknown:999"
        coEvery {
            workRepository.enrichFromDetail(eq(workKey), any())
        } returns null

        // WHEN: enrichWork is called
        val result = enrichmentWriter.enrichWork(workKey, createTestMetadata())

        // THEN: returns null
        assertNull(result)
    }

    /**
     * Creates a minimal test [NormalizedMediaMetadata] with all enrichable fields populated.
     */
    private fun createTestMetadata() = com.fishit.player.core.model.NormalizedMediaMetadata(
        canonicalTitle = "The Change",
        mediaType = com.fishit.player.core.model.MediaType.MOVIE,
        plot = "Rich plot from detail API",
        genres = "Thriller, Drama",
        director = "Jan Komasa",
        cast = "Diane Lane, Kyle Chandler",
        poster = ImageRef.Http("https://image.tmdb.org/t/p/w600_and_h900_bestv2/poster.jpg"),
        backdrop = ImageRef.Http("https://image.tmdb.org/t/p/w1280/backdrop.jpg"),
        trailer = "pPS2I9CgQ-w",
        rating = 6.549,
        durationMs = 6717000L,
        releaseDate = "2025-10-29",
        tmdb = com.fishit.player.core.model.TmdbRef(
            type = com.fishit.player.core.model.TmdbMediaType.MOVIE,
            id = 1126336,
        ),
        externalIds = com.fishit.player.core.model.ExternalIds(
            imdbId = "tt12583926",
        ),
    )
}
