/**
 * Unit tests for WorkEntityBuilder.
 *
 * Verifies the mapping logic from NormalizedMediaMetadata to NX_Work,
 * including recognition state, external IDs, and timestamp selection.
 */
package com.fishit.player.infra.data.nx.writer.builder

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.TmdbRef
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.infra.data.nx.mapper.MediaTypeMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorkEntityBuilderTest {
    
    private val builder = WorkEntityBuilder()
    
    @Test
    fun `build() creates work with CONFIRMED recognition when tmdb present`() {
        val metadata = NormalizedMediaMetadata(
            canonicalTitle = "Test Movie",
            mediaType = MediaType.MOVIE,
            year = 2024,
            tmdb = TmdbRef(TmdbMediaType.MOVIE, 12345),
        )
        
        val work = builder.build(metadata, "movie:tmdb:12345")
        
        assertEquals(NxWorkRepository.RecognitionState.CONFIRMED, work.recognitionState)
        assertEquals("12345", work.tmdbId)
    }
    
    @Test
    fun `build() creates work with HEURISTIC recognition when no tmdb`() {
        val metadata = NormalizedMediaMetadata(
            canonicalTitle = "Test Movie",
            mediaType = MediaType.MOVIE,
            year = 2024,
        )
        
        val work = builder.build(metadata, "movie:fallback:test-movie:2024")
        
        assertEquals(NxWorkRepository.RecognitionState.HEURISTIC, work.recognitionState)
        assertEquals(null, work.tmdbId)
    }
    
    @Test
    fun `build() prefers typed tmdb over externalIds`() {
        val metadata = NormalizedMediaMetadata(
            canonicalTitle = "Test Movie",
            mediaType = MediaType.MOVIE,
            tmdb = TmdbRef(TmdbMediaType.MOVIE, 12345),
            externalIds = ExternalIds(tmdb = TmdbRef(TmdbMediaType.MOVIE, 99999)),
        )
        
        val work = builder.build(metadata, "movie:tmdb:12345")
        
        assertEquals("12345", work.tmdbId)
    }
    
    @Test
    fun `build() falls back to externalIds tmdb when typed tmdb null`() {
        val metadata = NormalizedMediaMetadata(
            canonicalTitle = "Test Movie",
            mediaType = MediaType.MOVIE,
            externalIds = ExternalIds(tmdb = TmdbRef(TmdbMediaType.MOVIE, 99999)),
        )
        
        val work = builder.build(metadata, "movie:tmdb:99999")
        
        assertEquals("99999", work.tmdbId)
    }
    
    @Test
    fun `build() uses addedTimestamp when available`() {
        val addedTimestamp = 1609459200L  // 2021-01-01
        val metadata = NormalizedMediaMetadata(
            canonicalTitle = "Test Movie",
            mediaType = MediaType.MOVIE,
            addedTimestamp = addedTimestamp,
        )
        
        val work = builder.build(metadata, "movie:test")
        
        assertEquals(addedTimestamp, work.createdAtMs)
    }
    
    @Test
    fun `build() uses current time when addedTimestamp zero or null`() {
        val metadata = NormalizedMediaMetadata(
            canonicalTitle = "Test Movie",
            mediaType = MediaType.MOVIE,
            addedTimestamp = 0,  // Invalid timestamp
        )
        
        val beforeBuild = System.currentTimeMillis()
        val work = builder.build(metadata, "movie:test")
        val afterBuild = System.currentTimeMillis()
        
        // createdAtMs should be between beforeBuild and afterBuild
        assertTrue(work.createdAtMs in beforeBuild..afterBuild)
    }
    
    @Test
    fun `build() maps all metadata fields correctly`() {
        val metadata = NormalizedMediaMetadata(
            canonicalTitle = "Breaking Bad",
            mediaType = MediaType.SERIES,
            year = 2008,
            durationMs = 2880000L,  // 48 minutes
            poster = ImageRef.Http("https://example.com/poster.jpg"),
            backdrop = ImageRef.Http("https://example.com/backdrop.jpg"),
            rating = 9.5,
            genres = "Drama, Crime",
            plot = "A high school chemistry teacher...",
            director = "Vince Gilligan",
            cast = "Bryan Cranston, Aaron Paul",
            trailer = "https://youtube.com/trailer",
            tmdb = TmdbRef(TmdbMediaType.TV, 1396),
            externalIds = ExternalIds(imdbId = "tt0903747", tvdbId = "81189"),
            isAdult = false,
        )
        
        val work = builder.build(metadata, "series:tmdb:1396")
        
        assertEquals("series:tmdb:1396", work.workKey)
        assertEquals(MediaTypeMapper.toWorkType(MediaType.SERIES), work.type)
        assertEquals("Breaking Bad", work.displayTitle)
        assertEquals("Breaking Bad", work.sortTitle)
        assertEquals("breaking bad", work.titleNormalized)
        assertEquals(2008, work.year)
        assertEquals(2880000L, work.runtimeMs)
        assertEquals(9.5, work.rating)
        assertEquals("Drama, Crime", work.genres)
        assertEquals("A high school chemistry teacher...", work.plot)
        assertEquals("Vince Gilligan", work.director)
        assertEquals("Bryan Cranston, Aaron Paul", work.cast)
        assertEquals("https://youtube.com/trailer", work.trailer)
        assertEquals("1396", work.tmdbId)
        assertEquals("tt0903747", work.imdbId)
        assertEquals("81189", work.tvdbId)
        assertEquals(false, work.isAdult)
        assertNotNull(work.posterRef)
        assertNotNull(work.backdropRef)
        assertEquals(NxWorkRepository.RecognitionState.CONFIRMED, work.recognitionState)
        assertNotNull(work.createdAtMs)
        assertNotNull(work.updatedAtMs)
    }
}
