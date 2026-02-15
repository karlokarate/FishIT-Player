package com.fishit.player.infra.data.nx.writer

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.repository.NxWorkRelationRepository
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkVariantRepository
import com.fishit.player.infra.data.nx.writer.builder.WorkEntityBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [NxEnrichmentWriter.inheritParentFields].
 *
 * Verifies that after a parent series is enriched, its inheritable fields
 * (poster, backdrop, genres, etc.) are propagated to child episode works
 * using enrichIfAbsent semantics (no overwrites).
 */
class NxEnrichmentWriterInheritanceTest {
    private val workRepository = mockk<NxWorkRepository>(relaxed = true)
    private val variantRepository = mockk<NxWorkVariantRepository>(relaxed = true)
    private val relationRepository = mockk<NxWorkRelationRepository>(relaxed = true)
    private val workEntityBuilder = WorkEntityBuilder()

    private val enrichmentWriter =
        NxEnrichmentWriter(
            workRepository = workRepository,
            variantRepository = variantRepository,
            relationRepository = relationRepository,
            workEntityBuilder = workEntityBuilder,
        )

    // =========================================================================
    // inheritParentFields — basic propagation
    // =========================================================================

    @Test
    fun `inheritParentFields propagates parent fields to children`() =
        runBlocking {
            // GIVEN: enriched parent series work
            val parentWorkKey = "series:tmdb:12345"
            val parentWork =
                NxWorkRepository.Work(
                    workKey = parentWorkKey,
                    type = NxWorkRepository.WorkType.SERIES,
                    displayTitle = "Breaking Bad",
                    poster = ImageRef.Http("https://example.com/poster.jpg"),
                    backdrop = ImageRef.Http("https://example.com/backdrop.jpg"),
                    genres = "Drama,Crime",
                    rating = 9.5,
                    director = "Vince Gilligan",
                    cast = "Bryan Cranston,Aaron Paul",
                    trailer = "https://youtube.com/trailer",
                    tmdbId = "1396",
                    imdbId = "tt0903747",
                )

            coEvery { workRepository.get(parentWorkKey) } returns parentWork

            // AND: two child episode relations
            val childRelations =
                listOf(
                    NxWorkRelationRepository.Relation(
                        parentWorkKey = parentWorkKey,
                        childWorkKey = "episode:tmdb:12345:s01e01",
                        relationType = NxWorkRelationRepository.RelationType.SERIES_EPISODE,
                        seasonNumber = 1,
                        episodeNumber = 1,
                    ),
                    NxWorkRelationRepository.Relation(
                        parentWorkKey = parentWorkKey,
                        childWorkKey = "episode:tmdb:12345:s01e02",
                        relationType = NxWorkRelationRepository.RelationType.SERIES_EPISODE,
                        seasonNumber = 1,
                        episodeNumber = 2,
                    ),
                )
            coEvery { relationRepository.findChildren(parentWorkKey) } returns childRelations

            // AND: enrichIfAbsentBatch succeeds for both children
            val enrichmentSlot = slot<NxWorkRepository.Enrichment>()
            val workKeysSlot = slot<List<String>>()
            coEvery {
                workRepository.enrichIfAbsentBatch(capture(workKeysSlot), capture(enrichmentSlot))
            } returns 2

            // WHEN: inherit parent fields
            val enrichedCount = enrichmentWriter.inheritParentFields(parentWorkKey)

            // THEN: both children were enriched
            assertEquals(2, enrichedCount)

            // AND: enrichIfAbsentBatch was called once with both child keys
            coVerify(exactly = 1) { workRepository.enrichIfAbsentBatch(any(), any()) }
            val capturedKeys = workKeysSlot.captured
            assertTrue(capturedKeys.contains("episode:tmdb:12345:s01e01"))
            assertTrue(capturedKeys.contains("episode:tmdb:12345:s01e02"))

            // AND: enrichment payload contains correct parent fields
            val enrichment = enrichmentSlot.captured
            assertEquals(ImageRef.Http("https://example.com/poster.jpg"), enrichment.poster)
            assertEquals(ImageRef.Http("https://example.com/backdrop.jpg"), enrichment.backdrop)
            assertEquals("Drama,Crime", enrichment.genres)
            assertEquals(9.5, enrichment.rating)
            assertEquals("Vince Gilligan", enrichment.director)
            assertEquals("Bryan Cranston,Aaron Paul", enrichment.cast)
            assertEquals("https://youtube.com/trailer", enrichment.trailer)

            // AND: authority IDs are NOT inherited (ALWAYS_UPDATE would corrupt episode IDs)
            assertEquals(null, enrichment.tmdbId)
            assertEquals(null, enrichment.imdbId)
            assertEquals(null, enrichment.tvdbId)
        }

    // =========================================================================
    // inheritParentFields — edge cases
    // =========================================================================

    @Test
    fun `inheritParentFields returns 0 when parent not found`() =
        runBlocking {
            coEvery { workRepository.get("nonexistent") } returns null

            val result = enrichmentWriter.inheritParentFields("nonexistent")

            assertEquals(0, result)
            coVerify(exactly = 0) { relationRepository.findChildren(any()) }
        }

    @Test
    fun `inheritParentFields returns 0 when no children exist`() =
        runBlocking {
            val parentWorkKey = "series:tmdb:99999"
            coEvery { workRepository.get(parentWorkKey) } returns
                NxWorkRepository.Work(
                    workKey = parentWorkKey,
                    type = NxWorkRepository.WorkType.SERIES,
                    displayTitle = "No Episodes Series",
                )
            coEvery { relationRepository.findChildren(parentWorkKey) } returns emptyList()

            val result = enrichmentWriter.inheritParentFields(parentWorkKey)

            assertEquals(0, result)
            coVerify(exactly = 0) { workRepository.enrichIfAbsent(any(), any()) }
        }

    @Test
    fun `inheritParentFields counts only successfully enriched children`() =
        runBlocking {
            val parentWorkKey = "series:tmdb:55555"
            coEvery { workRepository.get(parentWorkKey) } returns
                NxWorkRepository.Work(
                    workKey = parentWorkKey,
                    type = NxWorkRepository.WorkType.SERIES,
                    displayTitle = "Partial Enrichment",
                    poster = ImageRef.Http("https://example.com/poster.jpg"),
                )

            val childRelations =
                listOf(
                    NxWorkRelationRepository.Relation(
                        parentWorkKey = parentWorkKey,
                        childWorkKey = "episode:ok",
                        relationType = NxWorkRelationRepository.RelationType.SERIES_EPISODE,
                    ),
                    NxWorkRelationRepository.Relation(
                        parentWorkKey = parentWorkKey,
                        childWorkKey = "episode:missing",
                        relationType = NxWorkRelationRepository.RelationType.SERIES_EPISODE,
                    ),
                )
            coEvery { relationRepository.findChildren(parentWorkKey) } returns childRelations

            // enrichIfAbsentBatch returns 1 (only one child was actually enriched)
            coEvery {
                workRepository.enrichIfAbsentBatch(any(), any())
            } returns 1

            val result = enrichmentWriter.inheritParentFields(parentWorkKey)

            assertEquals(1, result)
        }
}
