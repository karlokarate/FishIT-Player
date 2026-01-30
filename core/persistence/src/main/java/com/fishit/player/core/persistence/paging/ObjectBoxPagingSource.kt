package com.fishit.player.core.persistence.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.objectbox.query.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Generic PagingSource for ObjectBox queries.
 *
 * Provides efficient cursor-based pagination using ObjectBox's native
 * `query.find(offset, limit)` which leverages database-level pagination.
 *
 * **Usage:**
 * ```kotlin
 * val pagingSource = ObjectBoxPagingSource(
 *     queryFactory = { box.query().order(Entity_.title).build() },
 *     mapper = { entity -> entity.toDomain() }
 * )
 * ```
 *
 * **Performance:**
 * - O(1) memory per page (only current page in RAM)
 * - Native database offset/limit (no full-table scan)
 * - Supports invalidation via ObjectBox subscriptions
 *
 * @param T Entity type stored in ObjectBox
 * @param R Domain model type exposed to UI
 * @param queryFactory Factory to create the query (called fresh on each load for invalidation)
 * @param mapper Function to convert entity to domain model
 */
class ObjectBoxPagingSource<T, R : Any>(
    private val queryFactory: () -> Query<T>,
    private val mapper: (T) -> R?,
) : PagingSource<Int, R>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, R> {
        return try {
            // Use offset-based pagination (key = offset, not page number)
            // This correctly handles initialLoadSize != pageSize
            val offset = params.key ?: 0
            val loadSize = params.loadSize

            val items = withContext(Dispatchers.IO) {
                val query = queryFactory()
                val results = query.find(offset.toLong(), loadSize.toLong())
                    .mapNotNull(mapper)

                // DEBUG: Log query execution
                android.util.Log.d("ObjectBoxPagingSource",
                    "🔍 DB Query: offset=$offset loadSize=$loadSize → results=${results.size}")

                results
            }

            // Calculate prev/next keys as offsets
            val prevKey = if (offset > 0) maxOf(0, offset - loadSize) else null
            val nextKey = if (items.size == loadSize) offset + loadSize else null

            LoadResult.Page(
                data = items,
                prevKey = prevKey,
                nextKey = nextKey,
                // itemsBefore/itemsAfter help Paging calculate correct indices
                itemsBefore = offset,
                itemsAfter = LoadResult.Page.COUNT_UNDEFINED,
            )
        } catch (e: Exception) {
            android.util.Log.e("ObjectBoxPagingSource", "❌ DB Query ERROR: ${e.message}", e)
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, R>): Int? {
        // Return the offset closest to the anchor position for refresh
        return state.anchorPosition?.let { anchorPosition ->
            // Find the closest loaded page and return its offset
            val closestPage = state.closestPageToPosition(anchorPosition)
            closestPage?.prevKey?.let { it + state.config.pageSize }
                ?: closestPage?.nextKey?.let { it - state.config.pageSize }
        }
    }
}

/**
 * Configuration for ObjectBox PagingSource.
 *
 * @param pageSize Items per page (default 50)
 * @param prefetchDistance How many items ahead to prefetch (default pageSize)
 * @param initialLoadSize Items to load initially (default pageSize * 3)
 * @param maxSize Maximum items in memory (default unlimited)
 */
data class ObjectBoxPagingConfig(
    val pageSize: Int = 50,
    val prefetchDistance: Int = pageSize,
    val initialLoadSize: Int = pageSize * 3,
    val maxSize: Int = Int.MAX_VALUE,
) {
    companion object {
        /** Default config for library browsing (50 items/page) */
        val DEFAULT = ObjectBoxPagingConfig()
        
        /** Config for grids with larger tiles (30 items/page) */
        val GRID = ObjectBoxPagingConfig(
            pageSize = 30,
            prefetchDistance = 30,
            initialLoadSize = 90,
        )
        
        /** Config for lists with smaller items (100 items/page) */
        val LIST = ObjectBoxPagingConfig(
            pageSize = 100,
            prefetchDistance = 50,
            initialLoadSize = 200,
        )
    }
}
