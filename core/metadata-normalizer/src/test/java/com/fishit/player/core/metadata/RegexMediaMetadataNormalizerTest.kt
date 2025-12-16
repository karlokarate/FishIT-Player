package com.fishit.player.core.metadata

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.ids.TmdbId
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for RegexMediaMetadataNormalizer.
 *
 * Validates that the normalizer:
 * - Cleans titles by removing technical tags
 * - Extracts year, season, episode from filenames
 * - Prefers explicit metadata over parsed values
 * - Is deterministic (same input → same output)
 */
class RegexMediaMetadataNormalizerTest {
    private val normalizer = RegexMediaMetadataNormalizer()

    // ========== MOVIE NORMALIZATION TESTS ==========

    @Test
    fun `normalize cleans movie title with quality tags`() =
        runTest {
            // Given: raw metadata with technical tags in title
            val raw =
                RawMediaMetadata(
                    originalTitle = "X-Men.2000.1080p.BluRay.x264-GROUP",
                    year = null,
                    season = null,
                    episode = null,
                    durationMinutes = 104,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.IO,
                    sourceLabel = "Local Files",
                    sourceId = "file:///storage/movies/xmen.mkv",
                )

            // When: normalizing
            val normalized = normalizer.normalize(raw)

            // Then: title is cleaned, year extracted
            assertEquals("X-Men", normalized.canonicalTitle)
            assertEquals(2000, normalized.year)
            assertNull(normalized.season)
            assertNull(normalized.episode)
        }

    @Test
    fun `normalize extracts year from filename`() =
        runTest {
            // Given: raw metadata without explicit year
            val raw =
                RawMediaMetadata(
                    originalTitle = "Die Maske - 1994.mp4",
                    year = null,
                    season = null,
                    episode = null,
                    durationMinutes = 101,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.TELEGRAM,
                    sourceLabel = "Telegram: Movies",
                    sourceId = "tg://message/123",
                )

            // When: normalizing
            val normalized = normalizer.normalize(raw)

            // Then: title cleaned and year extracted
            assertEquals("Die Maske", normalized.canonicalTitle)
            assertEquals(1994, normalized.year)
        }

    @Test
    fun `normalize prefers explicit year over parsed year`() =
        runTest {
            // Given: raw metadata with explicit year AND year in filename
            val raw =
                RawMediaMetadata(
                    originalTitle = "Movie Title - 2019.mp4",
                    year = 2020, // Explicit year (e.g., from Xtream API)
                    season = null,
                    episode = null,
                    durationMinutes = 120,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.XTREAM,
                    sourceLabel = "Xtream: Provider A",
                    sourceId = "xtream://vod/999",
                )

            // When: normalizing
            val normalized = normalizer.normalize(raw)

            // Then: explicit year takes precedence
            assertEquals("Movie Title", normalized.canonicalTitle)
            assertEquals(2020, normalized.year) // Uses explicit, not parsed 2019
        }

    @Test
    fun `normalize handles German movie with quality tags`() =
        runTest {
            val raw =
                RawMediaMetadata(
                    originalTitle = "Champagne Problems - 2025 HDR DD+5.1 with Dolby Atmos.mp4",
                    year = null,
                    season = null,
                    episode = null,
                    durationMinutes = 115,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.TELEGRAM,
                    sourceLabel = "Telegram: UHD Movies",
                    sourceId = "tg://message/456",
                )

            val normalized = normalizer.normalize(raw)

            assertEquals("Champagne Problems", normalized.canonicalTitle)
            assertEquals(2025, normalized.year)
        }

    @Test
    fun `normalize handles movie with underscores`() =
        runTest {
            val raw =
                RawMediaMetadata(
                    originalTitle = "Crocodile_Dundee_Ein_Krokodil_zum_Küssen_1986_HDR_DD+5_1.mp4",
                    year = null,
                    season = null,
                    episode = null,
                    durationMinutes = 97,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.IO,
                    sourceLabel = "Local Files",
                    sourceId = "file:///movies/crocodile.mp4",
                )

            val normalized = normalizer.normalize(raw)

            assertTrue(normalized.canonicalTitle.contains("Crocodile Dundee"))
            assertEquals(1986, normalized.year)
        }

    // ========== SERIES NORMALIZATION TESTS ==========

    @Test
    fun `normalize extracts season and episode from SxxEyy format`() =
        runTest {
            val raw =
                RawMediaMetadata(
                    originalTitle = "Breaking Bad - S05 E16 - Felina.mp4",
                    year = null,
                    season = null,
                    episode = null,
                    durationMinutes = 55,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.TELEGRAM,
                    sourceLabel = "Telegram: TV Shows",
                    sourceId = "tg://message/789",
                )

            val normalized = normalizer.normalize(raw)

            assertTrue(normalized.canonicalTitle.contains("Breaking Bad"))
            assertEquals(5, normalized.season)
            assertEquals(16, normalized.episode)
        }

    @Test
    fun `normalize prefers explicit season and episode over parsed`() =
        runTest {
            // Given: both explicit and parsed season/episode
            val raw =
                RawMediaMetadata(
                    originalTitle = "Show Name - S02 E05.mp4",
                    year = null,
                    season = 3, // Explicit from API
                    episode = 10, // Explicit from API
                    durationMinutes = 45,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.XTREAM,
                    sourceLabel = "Xtream: Series",
                    sourceId = "xtream://series/123",
                )

            val normalized = normalizer.normalize(raw)

            // Then: explicit values take precedence
            assertEquals(3, normalized.season) // Uses explicit, not parsed S02
            assertEquals(10, normalized.episode) // Uses explicit, not parsed E05
        }

    @Test
    fun `normalize handles series with channel tag`() =
        runTest {
            val raw =
                RawMediaMetadata(
                    originalTitle = "S10E26 - @ArcheMovie - PAW Patrol.mp4",
                    year = null,
                    season = null,
                    episode = null,
                    durationMinutes = 22,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.TELEGRAM,
                    sourceLabel = "Telegram: Kids Shows",
                    sourceId = "tg://message/321",
                )

            val normalized = normalizer.normalize(raw)

            assertTrue(normalized.canonicalTitle.contains("PAW Patrol"))
            assertFalse(normalized.canonicalTitle.contains("@ArcheMovie"))
            assertEquals(10, normalized.season)
            assertEquals(26, normalized.episode)
        }

    @Test
    fun `normalize handles series with underscores and episode title`() =
        runTest {
            val raw =
                RawMediaMetadata(
                    originalTitle = "The_New_Batman_Adventures_S04_E22_Grüne_Augen,_giftiges_Herz.mp4",
                    year = null,
                    season = null,
                    episode = null,
                    durationMinutes = 22,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.IO,
                    sourceLabel = "Local Files",
                    sourceId = "file:///series/batman/s04e22.mp4",
                )

            val normalized = normalizer.normalize(raw)

            assertTrue(normalized.canonicalTitle.contains("Batman Adventures"))
            assertEquals(4, normalized.season)
            assertEquals(22, normalized.episode)
        }

    // ========== EDGE CASES ==========

    @Test
    fun `normalize handles title with number (300)`() =
        runTest {
            val raw =
                RawMediaMetadata(
                    originalTitle = "300 - 2006.mp4",
                    year = null,
                    season = null,
                    episode = null,
                    durationMinutes = 117,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.IO,
                    sourceLabel = "Local Files",
                    sourceId = "file:///movies/300.mp4",
                )

            val normalized = normalizer.normalize(raw)

            assertTrue(normalized.canonicalTitle.contains("300"))
            assertEquals(2006, normalized.year)
        }

    @Test
    fun `normalize handles title with year in title and actual year`() =
        runTest {
            val raw =
                RawMediaMetadata(
                    originalTitle = "2001 - A Space Odyssey - 1968.mp4",
                    year = null,
                    season = null,
                    episode = null,
                    durationMinutes = 149,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.IO,
                    sourceLabel = "Local Files",
                    sourceId = "file:///movies/2001.mp4",
                )

            val normalized = normalizer.normalize(raw)

            // Should prefer year at end
            assertEquals(1968, normalized.year)
            assertTrue(
                normalized.canonicalTitle.contains("2001") ||
                    normalized.canonicalTitle.contains("Space Odyssey"),
            )
        }

    @Test
    fun `normalize handles filename without year`() =
        runTest {
            val raw =
                RawMediaMetadata(
                    originalTitle = "Random Movie Title.mp4",
                    year = null,
                    season = null,
                    episode = null,
                    durationMinutes = null,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.IO,
                    sourceLabel = "Local Files",
                    sourceId = "file:///movies/random.mp4",
                )

            val normalized = normalizer.normalize(raw)

            assertEquals("Random Movie Title", normalized.canonicalTitle)
            assertNull(normalized.year)
        }

    @Test
    fun `normalize handles filename without extension`() =
        runTest {
            val raw =
                RawMediaMetadata(
                    originalTitle = "Test Movie 2020",
                    year = null,
                    season = null,
                    episode = null,
                    durationMinutes = null,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.IO,
                    sourceLabel = "Local Files",
                    sourceId = "file:///movies/test",
                )

            val normalized = normalizer.normalize(raw)

            assertEquals("Test Movie", normalized.canonicalTitle)
            assertEquals(2020, normalized.year)
        }

    @Test
    fun `normalize preserves TMDB ID from source`() =
        runTest {
            val raw =
                RawMediaMetadata(
                    originalTitle = "The Matrix",
                    year = 1999,
                    season = null,
                    episode = null,
                    durationMinutes = 136,
                    externalIds = ExternalIds(tmdbId = TmdbId(603), imdbId = "tt0133093"),
                    sourceType = SourceType.XTREAM,
                    sourceLabel = "Xtream: Premium IPTV",
                    sourceId = "xtream://vod/12345",
                )

            val normalized = normalizer.normalize(raw)

            assertEquals("The Matrix", normalized.canonicalTitle)
            assertEquals(TmdbId(603), normalized.tmdbId)
            assertEquals("tt0133093", normalized.externalIds.imdbId)
        }

    @Test
    fun `normalize does not extract year from timestamp-like numbers`() =
        runTest {
            // Filenames with timestamps or message IDs should not have years extracted
            val raw =
                RawMediaMetadata(
                    originalTitle = "Movie_20231205_file.mp4",
                    year = null,
                    season = null,
                    episode = null,
                    durationMinutes = null,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.TELEGRAM,
                    sourceLabel = "Telegram: Movies",
                    sourceId = "tg://message/123",
                )

            val normalized = normalizer.normalize(raw)

            // Should NOT extract 2023 from 20231205
            assertEquals("Movie 20231205 file", normalized.canonicalTitle)
            assertNull(normalized.year)
        }

    @Test
    fun `normalize extracts year from actual parentheses`() =
        runTest {
            val raw =
                RawMediaMetadata(
                    originalTitle = "After the Hunt (2025).mp4",
                    year = null,
                    season = null,
                    episode = null,
                    durationMinutes = null,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.IO,
                    sourceLabel = "Local Files",
                    sourceId = "file:///movies/hunt.mp4",
                )

            val normalized = normalizer.normalize(raw)

            assertEquals("After the Hunt", normalized.canonicalTitle)
            assertEquals(2025, normalized.year)
        }

    // ========== DETERMINISM TESTS ==========

    @Test
    fun `normalize same input twice returns same result`() =
        runTest {
            val raw =
                RawMediaMetadata(
                    originalTitle = "Movie.Title.2020.1080p.WEB-DL.x264.mp4",
                    year = null,
                    season = null,
                    episode = null,
                    durationMinutes = 120,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.IO,
                    sourceLabel = "Local Files",
                    sourceId = "file:///movies/movie.mp4",
                )

            val result1 = normalizer.normalize(raw)
            val result2 = normalizer.normalize(raw)

            assertEquals(result1, result2)
        }

    @Test
    fun `normalize different inputs return different results`() =
        runTest {
            val raw1 =
                RawMediaMetadata(
                    originalTitle = "Movie A 2020.mp4",
                    year = null,
                    season = null,
                    episode = null,
                    durationMinutes = null,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.IO,
                    sourceLabel = "Local Files",
                    sourceId = "file:///a.mp4",
                )

            val raw2 =
                RawMediaMetadata(
                    originalTitle = "Movie B 2021.mp4",
                    year = null,
                    season = null,
                    episode = null,
                    durationMinutes = null,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.IO,
                    sourceLabel = "Local Files",
                    sourceId = "file:///b.mp4",
                )

            val result1 = normalizer.normalize(raw1)
            val result2 = normalizer.normalize(raw2)

            // Results should be different
            assertTrue(
                result1.canonicalTitle != result2.canonicalTitle ||
                    result1.year != result2.year,
            )
        }

    // ========== MEDIATYPE INFERENCE TESTS ==========

    @Test
    fun `normalize infers SERIES_EPISODE type from season and episode`() =
        runTest {
            // Given: raw metadata with UNKNOWN type but has season/episode
            val raw =
                RawMediaMetadata(
                    originalTitle = "Friends.S01E01.720p.BluRay.x264",
                    mediaType = MediaType.UNKNOWN,
                    year = null,
                    season = null, // Will be parsed from filename
                    episode = null, // Will be parsed from filename
                    durationMinutes = 22,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.IO,
                    sourceLabel = "Local Files",
                    sourceId = "file:///storage/friends.mkv",
                )

            // When: normalizing
            val normalized = normalizer.normalize(raw)

            // Then: type is inferred as SERIES_EPISODE
            assertEquals(MediaType.SERIES_EPISODE, normalized.mediaType)
            assertEquals(1, normalized.season)
            assertEquals(1, normalized.episode)
        }

    @Test
    fun `normalize infers MOVIE type from year when no season or episode`() =
        runTest {
            // Given: raw metadata with UNKNOWN type but has year
            val raw =
                RawMediaMetadata(
                    originalTitle = "Inception.2010.1080p.BluRay.x264",
                    mediaType = MediaType.UNKNOWN,
                    year = null, // Will be parsed from filename
                    season = null,
                    episode = null,
                    durationMinutes = 148,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.IO,
                    sourceLabel = "Local Files",
                    sourceId = "file:///storage/inception.mkv",
                )

            // When: normalizing
            val normalized = normalizer.normalize(raw)

            // Then: type is inferred as MOVIE
            assertEquals(MediaType.MOVIE, normalized.mediaType)
            assertEquals(2010, normalized.year)
        }

    @Test
    fun `normalize preserves explicit mediaType over inference`() =
        runTest {
            // Given: raw metadata with explicit CLIP type but has year
            val raw =
                RawMediaMetadata(
                    originalTitle = "Trailer.2024.1080p.WEB",
                    mediaType = MediaType.CLIP, // Explicit type
                    year = null, // Will be parsed from filename
                    season = null,
                    episode = null,
                    durationMinutes = 3,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.TELEGRAM,
                    sourceLabel = "Telegram: Trailers",
                    sourceId = "tg://message/789",
                )

            // When: normalizing
            val normalized = normalizer.normalize(raw)

            // Then: explicit CLIP type is preserved
            assertEquals(MediaType.CLIP, normalized.mediaType)
            assertEquals(2024, normalized.year)
        }

    @Test
    fun `normalize keeps UNKNOWN type when no metadata for inference`() =
        runTest {
            // Given: raw metadata with UNKNOWN type and no year/season/episode
            val raw =
                RawMediaMetadata(
                    originalTitle = "Random Video File",
                    mediaType = MediaType.UNKNOWN,
                    year = null,
                    season = null,
                    episode = null,
                    durationMinutes = 45,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.IO,
                    sourceLabel = "Local Files",
                    sourceId = "file:///storage/video.mkv",
                )

            // When: normalizing
            val normalized = normalizer.normalize(raw)

            // Then: type remains UNKNOWN (no inference possible)
            assertEquals(MediaType.UNKNOWN, normalized.mediaType)
        }
}
