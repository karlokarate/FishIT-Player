package com.fishit.player.infra.data.xtream

import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.feature.library.domain.LibraryCategory
import com.fishit.player.feature.library.domain.LibraryContentRepository
import com.fishit.player.feature.library.domain.LibraryMediaItem
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter implementation of [LibraryContentRepository] for the library feature.
 *
 * Maps RawMediaMetadata from [XtreamCatalogRepository] to LibraryMediaItem domain models.
 *
 * **Architecture:**
 * - Feature layer defines LibraryContentRepository interface
 * - Data layer provides this implementation
 * - Converts RawMediaMetadata → LibraryMediaItem
 */
@Singleton
class LibraryContentRepositoryAdapter @Inject constructor(
    private val catalogRepository: XtreamCatalogRepository
) : LibraryContentRepository {

    companion object {
        private const val TAG = "LibraryContentRepoAdapter"
    }

    override fun observeVod(categoryId: String?): Flow<List<LibraryMediaItem>> {
        return catalogRepository.observeVod(categoryId)
            .map { items ->
                items.map { it.toLibraryMediaItem() }
            }
    }

    override fun observeSeries(categoryId: String?): Flow<List<LibraryMediaItem>> {
        return catalogRepository.observeSeries(categoryId)
            .map { items ->
                items.map { it.toLibraryMediaItem() }
            }
    }

    override fun observeVodCategories(): Flow<List<LibraryCategory>> {
        // Categories are extracted from VOD items' extras
        return catalogRepository.observeVod()
            .map { items ->
                items
                    .mapNotNull { item ->
                        val categoryId = item.extras["categoryId"]
                        val categoryName = item.extras["categoryName"]
                        if (categoryId != null && categoryName != null) {
                            categoryId to categoryName
                        } else null
                    }
                    .groupBy { it.first }
                    .map { (id, pairs) ->
                        LibraryCategory(
                            id = id,
                            name = pairs.first().second,
                            itemCount = pairs.size
                        )
                    }
                    .sortedBy { it.name }
            }
    }

    override fun observeSeriesCategories(): Flow<List<LibraryCategory>> {
        // Categories are extracted from series items' extras
        return catalogRepository.observeSeries()
            .map { items ->
                items
                    .mapNotNull { item ->
                        val categoryId = item.extras["categoryId"]
                        val categoryName = item.extras["categoryName"]
                        if (categoryId != null && categoryName != null) {
                            categoryId to categoryName
                        } else null
                    }
                    .groupBy { it.first }
                    .map { (id, pairs) ->
                        LibraryCategory(
                            id = id,
                            name = pairs.first().second,
                            itemCount = pairs.size
                        )
                    }
                    .sortedBy { it.name }
            }
    }

    override suspend fun search(query: String, limit: Int): List<LibraryMediaItem> {
        return try {
            catalogRepository.search(query, limit)
                .map { it.toLibraryMediaItem() }
        } catch (e: Exception) {
            UnifiedLog.e(TAG) { "Search error: ${e.message}" }
            emptyList()
        }
    }

    /**
     * Maps RawMediaMetadata to LibraryMediaItem.
     */
    private fun RawMediaMetadata.toLibraryMediaItem(): LibraryMediaItem {
        return LibraryMediaItem(
            id = sourceId,
            title = originalTitle,
            poster = poster ?: thumbnail,
            backdrop = backdrop,
            mediaType = mediaType,
            sourceType = sourceType,
            year = year,
            rating = rating?.toFloat(),
            categoryId = extras["categoryId"],
            categoryName = extras["categoryName"],
            genres = extras["genres"]?.split(",")?.map { it.trim() } ?: emptyList(),
            description = extras["overview"] ?: extras["description"],
            navigationId = sourceId,
            navigationSource = sourceType
        )
    }
}
