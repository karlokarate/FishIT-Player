package com.fishit.player.core.persistence.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fishit.player.core.model.CanonicalMediaId
import com.fishit.player.core.model.MediaKind
import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.TmdbRef
import com.fishit.player.core.model.ids.asCanonicalId
import com.fishit.player.core.model.ids.asPipelineItemId
import com.fishit.player.core.persistence.obx.ObxCanonicalMedia
import com.fishit.player.core.persistence.obx.ObxCanonicalMedia_
import com.fishit.player.core.persistence.obx.ObxCanonicalResumeMark
import com.fishit.player.core.persistence.obx.ObxCanonicalResumeMark_
import com.fishit.player.core.persistence.obx.ObxStore
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ObxCanonicalMediaRepositoryTmdbKeyCompatibilityTest {
    private lateinit var boxStore: BoxStore
    private lateinit var repository: ObxCanonicalMediaRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        boxStore = ObxStore.get(context)
        repository = ObxCanonicalMediaRepository(boxStore)
    }

    @After
    fun tearDown() {
        boxStore.close()
        ObxStore.close()
    }

    @Test
    fun `upsert upgrades legacy tmdb key to typed key`() =
        runTest {
            // Simulate previously persisted legacy canonical key.
            boxStore
                .boxFor<ObxCanonicalMedia>()
                .put(
                    ObxCanonicalMedia(
                        canonicalKey = "tmdb:603",
                        kind = "movie",
                        mediaType = MediaType.MOVIE.name,
                        canonicalTitle = "The Matrix",
                        canonicalTitleLower = "the matrix",
                        year = 1999,
                        tmdbId = "603",
                    ),
                )

            val canonicalId =
                repository.upsertCanonicalMedia(
                    NormalizedMediaMetadata(
                        canonicalTitle = "The Matrix",
                        mediaType = MediaType.MOVIE,
                        year = 1999,
                        tmdb = TmdbRef(TmdbMediaType.MOVIE, 603),
                    ),
                )

            assertEquals("tmdb:movie:603", canonicalId.key.value)

            val legacyCount =
                boxStore
                    .boxFor<ObxCanonicalMedia>()
                    .query(ObxCanonicalMedia_.canonicalKey.equal("tmdb:603"))
                    .build()
                    .count()
            val typedCount =
                boxStore
                    .boxFor<ObxCanonicalMedia>()
                    .query(ObxCanonicalMedia_.canonicalKey.equal("tmdb:movie:603"))
                    .build()
                    .count()

            assertEquals(0, legacyCount)
            assertEquals(1, typedCount)
        }

    @Test
    fun `setCanonicalResume with legacy key writes under resolved canonical key`() =
        runTest {
            // Canonical entry already in the preferred typed format.
            boxStore
                .boxFor<ObxCanonicalMedia>()
                .put(
                    ObxCanonicalMedia(
                        canonicalKey = "tmdb:movie:603",
                        kind = "movie",
                        mediaType = MediaType.MOVIE.name,
                        canonicalTitle = "The Matrix",
                        canonicalTitleLower = "the matrix",
                        year = 1999,
                        tmdbId = "603",
                    ),
                )

            val legacyCanonicalId = CanonicalMediaId(MediaKind.MOVIE, "tmdb:603".asCanonicalId())

            repository.setCanonicalResume(
                canonicalId = legacyCanonicalId,
                profileId = 1L,
                positionMs = 60_000L,
                durationMs = 120 * 60_000L,
                sourceRef =
                    MediaSourceRef(
                        sourceType = SourceType.XTREAM,
                        sourceId = "xtream:vod:123".asPipelineItemId(),
                        sourceLabel = "Xtream VOD",
                    ),
            )

            val resume =
                boxStore
                    .boxFor<ObxCanonicalResumeMark>()
                    .query(ObxCanonicalResumeMark_.profileId.equal(1L))
                    .build()
                    .findFirst()

            assertNotNull(resume)
            assertEquals("tmdb:movie:603", resume.canonicalKey)
        }
}
