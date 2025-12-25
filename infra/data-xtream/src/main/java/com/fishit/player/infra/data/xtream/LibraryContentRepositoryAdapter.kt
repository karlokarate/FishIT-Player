package com.fishit.player.infra.data.xtream

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import com.fishit.player.core.persistence.obx.ObxCategory
import com.fishit.player.core.persistence.obx.ObxCategory_
import com.fishit.player.core.persistence.obx.ObxSeries
import com.fishit.player.core.persistence.obx.ObxSeries_
import com.fishit.player.core.persistence.obx.ObxVod
import com.fishit.player.core.persistence.obx.ObxVod_
import com.fishit.player.feature.library.domain.LibraryCategory
import com.fishit.player.feature.library.domain.LibraryContentRepository
import com.fishit.player.feature.library.domain.LibraryMediaItem
import com.fishit.player.infra.logging.UnifiedLog
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Adapter implementation of [LibraryContentRepository] for the library feature.
 *
 * Maps ObjectBox entities directly to LibraryMediaItem domain models, providing
 * full access to entity fields like categoryId, genres, and description.
 *
 * **Architecture:**
 * - Feature layer defines LibraryContentRepository interface
 * - Data layer provides this implementation
 * - Converts ObxVod/ObxSeries → LibraryMediaItem (direct entity mapping)
 *
 * **v2 Compliance (MEDIA_NORMALIZATION_CONTRACT.md):**
 * - Data layer maps entities to feature domain models
 * - No pipeline or transport imports (entities are in core:persistence)
 * - Categories fetched from ObxCategory table with cached lookup
 *
 * **Why direct entity mapping?**
 * RawMediaMetadata is a normalized, source-agnostic model without provider-specific
 * fields like categoryId, genres, or plot. The adapter needs these fields for the
 * LibraryMediaItem domain model, so it reads directly from ObjectBox entities.
 */
@Singleton
class LibraryContentRepositoryAdapter
@Inject
constructor(
        private val boxStore: BoxStore
) : LibraryContentRepository {

    companion object {
        private const val TAG = "LibraryContentRepoAdapter"
    }

    private val vodBox by lazy { boxStore.boxFor<ObxVod>() }
    private val seriesBox by lazy { boxStore.boxFor<ObxSeries>() }
    private val categoryBox by lazy { boxStore.boxFor<ObxCategory>() }

    // Category name cache to avoid repeated DB lookups
    private val categoryNameCache = mutableMapOf<String, String?>()

    override fun observeVod(categoryId: String?): Flow<List<LibraryMediaItem>> {
        val query = if (categoryId != null) {
            vodBox.query(ObxVod_.categoryId.equal(categoryId)).order(ObxVod_.nameLower).build()
        } else {
            vodBox.query().order(ObxVod_.nameLower).build()
        }
        return query.asFlow()
            .map { entities -> entities.map { it.toLibraryMediaItem() } }
            .flowOn(Dispatchers.IO)
    }

    override fun observeSeries(categoryId: String?): Flow<List<LibraryMediaItem>> {
        val query = if (categoryId != null) {
            seriesBox.query(ObxSeries_.categoryId.equal(categoryId)).order(ObxSeries_.nameLower).build()
        } else {
            seriesBox.query().order(ObxSeries_.nameLower).build()
        }
        return query.asFlow()
            .map { entities -> entities.map { it.toLibraryMediaItem() } }
            .flowOn(Dispatchers.IO)
    }

    override fun observeVodCategories(): Flow<List<LibraryCategory>> {
        return categoryBox.query(ObxCategory_.kind.equal("vod")).build()
            .asFlow()
            .map { categories ->
                categories.map { category ->
                    LibraryCategory(
                        id = category.categoryId,
                        name = category.categoryName ?: category.categoryId,
                        itemCount = countItemsInCategory(category.categoryId, "vod")
                    )
                }.sortedBy { it.name }
            }
            .flowOn(Dispatchers.IO)
    }

    override fun observeSeriesCategories(): Flow<List<LibraryCategory>> {
        return categoryBox.query(ObxCategory_.kind.equal("series")).build()
            .asFlow()
            .map { categories ->
                categories.map { category ->
                    LibraryCategory(
                        id = category.categoryId,
                        name = category.categoryName ?: category.categoryId,
                        itemCount = countItemsInCategory(category.categoryId, "series")
                    )
                }.sortedBy { it.name }
            }
            .flowOn(Dispatchers.IO)
    }

    private fun countItemsInCategory(categoryId: String, kind: String): Int {
        return when (kind) {
            "vod" -> vodBox.query(ObxVod_.categoryId.equal(categoryId)).build().count().toInt()
            "series" -> seriesBox.query(ObxSeries_.categoryId.equal(categoryId)).build().count().toInt()
            else -> 0
        }
    }

    override suspend fun search(query: String, limit: Int): List<LibraryMediaItem> =
        withContext(Dispatchers.IO) {
            try {
                val lowerQuery = query.lowercase()
                val vods = vodBox.query(ObxVod_.nameLower.contains(lowerQuery))
                    .build()
                    .find(0, limit.toLong())
                    .map { it.toLibraryMediaItem() }

                val series = seriesBox.query(ObxSeries_.nameLower.contains(lowerQuery))
                    .build()
                    .find(0, limit.toLong())
                    .map { it.toLibraryMediaItem() }

                (vods + series).take(limit)
            } catch (e: Exception) {
                UnifiedLog.e(TAG) { "Search error: ${e.message}" }
                emptyList()
            }
        }

    // ========================================================================
    // Mapping: ObxVod/ObxSeries → LibraryMediaItem
    // ========================================================================

    /**
     * Maps ObxVod entity to LibraryMediaItem domain model.
     *
     * Direct entity mapping provides full access to:
     * - categoryId (for filtering)
     * - genre (parsed to List<String>)
     * - plot (description)
     * - All other entity fields
     */
    private fun ObxVod.toLibraryMediaItem(): LibraryMediaItem {
        return LibraryMediaItem(
            id = "xtream:vod:$vodId",
            title = name,
            poster = poster?.let { ImageRef.Http(it) },
            backdrop = null, // VOD entities don't have separate backdrop
            mediaType = MediaType.MOVIE,
            sourceType = SourceType.XTREAM,
            year = year,
            rating = rating?.toFloat(),
            categoryId = categoryId,
            categoryName = categoryId?.let { lookupCategoryName(it) },
            genres = parseGenres(genre),
            description = plot,
            navigationId = "xtream:vod:$vodId",
            navigationSource = SourceType.XTREAM
        )
    }

    /**
     * Maps ObxSeries entity to LibraryMediaItem domain model.
     */
    private fun ObxSeries.toLibraryMediaItem(): LibraryMediaItem {
        return LibraryMediaItem(
            id = "xtream:series:$seriesId",
            title = name,
            poster = imagesJson?.let { ImageRef.Http(it) }, // imagesJson stores poster URL
            backdrop = null,
            mediaType = MediaType.SERIES,
            sourceType = SourceType.XTREAM,
            year = year,
            rating = rating?.toFloat(),
            categoryId = categoryId,
            categoryName = categoryId?.let { lookupCategoryName(it) },
            genres = parseGenres(genre),
            description = plot,
            navigationId = "xtream:series:$seriesId",
            navigationSource = SourceType.XTREAM
        )
    }

    /**
     * Lookup category name from ObxCategory table with caching.
     */
    private fun lookupCategoryName(categoryId: String): String? {
        return categoryNameCache.getOrPut(categoryId) {
            categoryBox.query(ObxCategory_.categoryId.equal(categoryId))
                .build()
                .findFirst()
                ?.categoryName
        }
    }

    /**
     * Parse comma-separated genre string to list.
     */
    private fun parseGenres(genre: String?): List<String> {
        return genre?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }
}
