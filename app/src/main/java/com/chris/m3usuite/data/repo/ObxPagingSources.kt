package com.chris.m3usuite.data.repo

import android.content.Context
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.chris.m3usuite.data.obx.toMediaItem
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.model.hasArtwork
import com.chris.m3usuite.model.isAdultCategory
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * PagingSources backed by ObjectBox queries for large datasets.
 * Implements offset-based paging using repository methods.
 */
class ObxVodPagingSource(
    private val context: Context,
    private val store: SettingsStore,
    private val categoryId: String?,
    private val query: String?,
    private val providerKey: String? = null,
    private val genreKey: String? = null,
    private val yearKey: Int? = null,
) : PagingSource<Int, MediaItem>() {
    private val repo by lazy { XtreamObxRepository(context, store) }

    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor)
        return page?.prevKey?.plus(1) ?: page?.nextKey?.minus(1)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> = try {
        val pageIndex = params.key ?: 0
        val pageSize = params.loadSize.coerceAtLeast(30)
        val baseOffset = pageIndex * pageSize
        val showAdults = store.showAdults.first()

        suspend fun fetch(offset: Long, limit: Long) = when {
            !query.isNullOrBlank()      -> repo.searchVodByName(query.trim(), offset, limit)
            !categoryId.isNullOrBlank() -> repo.vodByCategoryPaged(categoryId!!, offset, limit)
            !providerKey.isNullOrBlank() -> repo.vodByProviderKeyPaged(providerKey!!, offset, limit)
            !genreKey.isNullOrBlank()    -> repo.vodByGenreKeyPaged(genreKey!!, offset, limit)
            yearKey != null              -> repo.vodByYearKeyPaged(yearKey, offset, limit)
            else                         -> repo.vodPagedNewest(offset, limit)
        }

        val catLabelById = runCatching {
            repo.categories("vod").associateBy({ it.categoryId }, { it.categoryName })
        }.getOrElse { emptyMap() }

        fun mapBatch(raw: List<com.chris.m3usuite.data.obx.ObxVod>): List<MediaItem> =
            raw.map { obx -> obx.toMediaItem(context).copy(categoryName = catLabelById[obx.categoryId]) }

        val collected = mutableListOf<MediaItem>()
        var nextOffset = baseOffset.toLong()
        var reachedEnd = false

        while (collected.size < pageSize && !reachedEnd) {
            val raw = fetch(nextOffset, pageSize.toLong())
            if (raw.isEmpty()) {
                reachedEnd = true
                break
            }
            val mapped = mapBatch(raw)
            val filtered = if (showAdults) mapped else mapped.filterNot { it.isAdultCategory() }
            collected += filtered
            if (raw.size < pageSize) {
                reachedEnd = true
            }
            nextOffset += raw.size
            if (!showAdults && filtered.isEmpty()) {
                continue
            }
        }

        val pageItems = collected.take(pageSize)
        val (withArt, withoutArt) = pageItems.partition { it.hasArtwork() }
        val ordered = withArt + withoutArt
        val prevKey = if (pageIndex == 0) null else pageIndex - 1
        val nextKey = if (reachedEnd || nextOffset == baseOffset.toLong()) null else (nextOffset / pageSize.toLong()).toInt()
        LoadResult.Page(ordered, prevKey, nextKey)
    } catch (e: Exception) {
        LoadResult.Error(e)
    }
}

class ObxLivePagingSource(
    private val context: Context,
    private val store: SettingsStore,
    private val categoryId: String?,
    private val query: String?,
    private val providerKey: String? = null,
    private val genreKey: String? = null,
) : PagingSource<Int, MediaItem>() {
    private val repo by lazy { XtreamObxRepository(context, store) }

    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor)
        return page?.prevKey?.plus(1) ?: page?.nextKey?.minus(1)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> = try {
        val page = params.key ?: 0
        val pageSize = params.loadSize.coerceAtLeast(30)
        val baseOffset = page * pageSize
        val showAdults = store.showAdults.first()

        suspend fun fetch(offset: Long, limit: Long) = when {
            !query.isNullOrBlank()      -> repo.searchLiveByName(query.trim(), offset, limit)
            !categoryId.isNullOrBlank() -> repo.liveByCategoryPaged(categoryId!!, offset, limit)
            !providerKey.isNullOrBlank() -> repo.liveByProviderKeyPaged(providerKey!!, offset, limit)
            !genreKey.isNullOrBlank()    -> repo.liveByGenreKeyPaged(genreKey!!, offset, limit)
            else                         -> repo.livePaged(offset, limit)
        }

        val catLabelById = runCatching {
            repo.categories("live").associateBy({ it.categoryId }, { it.categoryName })
        }.getOrElse { emptyMap() }

        fun mapBatch(raw: List<com.chris.m3usuite.data.obx.ObxLive>): List<MediaItem> =
            raw.map { obx -> obx.toMediaItem(context).copy(categoryName = catLabelById[obx.categoryId]) }

        val collected = mutableListOf<MediaItem>()
        var nextOffset = baseOffset.toLong()
        var reachedEnd = false

        while (collected.size < pageSize && !reachedEnd) {
            val raw = fetch(nextOffset, pageSize.toLong())
            if (raw.isEmpty()) {
                reachedEnd = true
                break
            }
            val mapped = mapBatch(raw)
            val filtered = if (showAdults) mapped else mapped.filterNot { it.isAdultCategory() }
            collected += filtered
            if (raw.size < pageSize) {
                reachedEnd = true
            }
            nextOffset += raw.size
            if (!showAdults && filtered.isEmpty()) {
                continue
            }
        }

        val pageItems = collected.take(pageSize)
        val (withArt, withoutArt) = pageItems.partition { it.hasArtwork() }
        val ordered = withArt + withoutArt
        val prevKey = if (page == 0) null else page - 1
        val nextKey = if (reachedEnd || nextOffset == baseOffset.toLong()) null else (nextOffset / pageSize.toLong()).toInt()
        LoadResult.Page(ordered, prevKey, nextKey)
    } catch (e: Exception) {
        LoadResult.Error(e)
    }
}

class ObxSeriesPagingSource(
    private val context: Context,
    private val store: SettingsStore,
    private val categoryId: String?,
    private val query: String?,
    private val providerKey: String? = null,
    private val genreKey: String? = null,
    private val yearKey: Int? = null,
) : PagingSource<Int, MediaItem>() {
    private val repo by lazy { XtreamObxRepository(context, store) }

    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor)
        return page?.prevKey?.plus(1) ?: page?.nextKey?.minus(1)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> = try {
        val page = params.key ?: 0
        val pageSize = params.loadSize.coerceAtLeast(30)
        val baseOffset = page * pageSize
        val showAdults = store.showAdults.first()

        suspend fun fetch(offset: Long, limit: Long) = when {
            !query.isNullOrBlank()      -> repo.searchSeriesByName(query.trim(), offset, limit)
            !categoryId.isNullOrBlank() -> repo.seriesByCategoryPaged(categoryId!!, offset, limit)
            providerKey != null         -> repo.seriesByProviderKeyPaged(providerKey, offset, limit)
            genreKey != null            -> repo.seriesByGenreKeyPaged(genreKey, offset, limit)
            yearKey != null             -> repo.seriesByYearKeyPaged(yearKey, offset, limit)
            else                        -> repo.seriesPagedNewest(offset, limit)
        }

        val catLabelById = runCatching {
            repo.categories("series").associateBy({ it.categoryId }, { it.categoryName })
        }.getOrElse { emptyMap() }

        fun mapBatch(raw: List<com.chris.m3usuite.data.obx.ObxSeries>): List<MediaItem> =
            raw.map { obx -> obx.toMediaItem(context).copy(categoryName = catLabelById[obx.categoryId]) }

        val collected = mutableListOf<MediaItem>()
        var nextOffset = baseOffset.toLong()
        var reachedEnd = false

        while (collected.size < pageSize && !reachedEnd) {
            val raw = fetch(nextOffset, pageSize.toLong())
            if (raw.isEmpty()) {
                reachedEnd = true
                break
            }
            val mapped = mapBatch(raw)
            val filtered = if (showAdults) mapped else mapped.filterNot { it.isAdultCategory() }
            collected += filtered
            if (raw.size < pageSize) {
                reachedEnd = true
            }
            nextOffset += raw.size
            if (!showAdults && filtered.isEmpty()) {
                continue
            }
        }

        val pageItems = collected.take(pageSize)
        val (withArt, withoutArt) = pageItems.partition { it.hasArtwork() }
        val ordered = withArt + withoutArt
        val prevKey = if (page == 0) null else page - 1
        val nextKey = if (reachedEnd || nextOffset == baseOffset.toLong()) null else (nextOffset / pageSize.toLong()).toInt()
        LoadResult.Page(ordered, prevKey, nextKey)
    } catch (e: Exception) {
        LoadResult.Error(e)
    }
}
