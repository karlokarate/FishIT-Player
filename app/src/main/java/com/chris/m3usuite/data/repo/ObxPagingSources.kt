package com.chris.m3usuite.data.repo

import android.content.Context
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.chris.m3usuite.data.obx.toMediaItem
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.model.hasArtwork
import com.chris.m3usuite.prefs.SettingsStore

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
        val limit = params.loadSize.coerceAtLeast(30)
        val offset = pageIndex * limit

        val itemsRaw = when {
            !query.isNullOrBlank()      -> repo.searchVodByName(query.trim(), offset.toLong(), limit.toLong())
            !categoryId.isNullOrBlank() -> repo.vodByCategoryPaged(categoryId!!, offset.toLong(), limit.toLong())
            !providerKey.isNullOrBlank() -> repo.vodByProviderKeyPaged(providerKey!!, offset.toLong(), limit.toLong())
            !genreKey.isNullOrBlank()    -> repo.vodByGenreKeyPaged(genreKey!!, offset.toLong(), limit.toLong())
            yearKey != null              -> repo.vodByYearKeyPaged(yearKey, offset.toLong(), limit.toLong())
            else                         -> repo.vodPagedNewest(offset.toLong(), limit.toLong())
        }
        val catLabelById = runCatching {
            repo.categories("vod").associateBy({ it.categoryId }, { it.categoryName })
        }.getOrElse { emptyMap() }
        val items = itemsRaw.map { it.toMediaItem(context).copy(categoryName = catLabelById[it.categoryId]) }
        val (withArt, withoutArt) = items.partition { it.hasArtwork() }
        val ordered = withArt + withoutArt

        val nextKey = if (items.size < limit) null else pageIndex + 1
        val prevKey = if (pageIndex == 0) null else pageIndex - 1
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
        val limit = params.loadSize.coerceAtLeast(30)
        val offset = page * limit

        val itemsRaw = when {
            !query.isNullOrBlank()      -> repo.searchLiveByName(query.trim(), offset.toLong(), limit.toLong())
            !categoryId.isNullOrBlank() -> repo.liveByCategoryPaged(categoryId!!, offset.toLong(), limit.toLong())
            !providerKey.isNullOrBlank() -> repo.liveByProviderKeyPaged(providerKey!!, offset.toLong(), limit.toLong())
            !genreKey.isNullOrBlank()    -> repo.liveByGenreKeyPaged(genreKey!!, offset.toLong(), limit.toLong())
            else                         -> repo.livePaged(offset.toLong(), limit.toLong())
        }
        val catLabelById = runCatching {
            repo.categories("live").associateBy({ it.categoryId }, { it.categoryName })
        }.getOrElse { emptyMap() }
        val items = itemsRaw.map { it.toMediaItem(context).copy(categoryName = catLabelById[it.categoryId]) }
        val (withArt, withoutArt) = items.partition { it.hasArtwork() }
        val ordered = withArt + withoutArt

        val nextKey = if (items.size < limit) null else page + 1
        val prevKey = if (page == 0) null else page - 1
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
        val limit = params.loadSize.coerceAtLeast(30)
        val offset = page * limit

        val itemsRaw = when {
            !query.isNullOrBlank()      -> repo.searchSeriesByName(query.trim(), offset.toLong(), limit.toLong())
            !categoryId.isNullOrBlank() -> repo.seriesByCategoryPaged(categoryId!!, offset.toLong(), limit.toLong())
            providerKey != null         -> repo.seriesByProviderKeyPaged(providerKey, offset.toLong(), limit.toLong())
            genreKey != null            -> repo.seriesByGenreKeyPaged(genreKey, offset.toLong(), limit.toLong())
            yearKey != null             -> repo.seriesByYearKeyPaged(yearKey, offset.toLong(), limit.toLong())
            else                        -> repo.seriesPagedNewest(offset.toLong(), limit.toLong())
        }
        val catLabelById = runCatching {
            repo.categories("series").associateBy({ it.categoryId }, { it.categoryName })
        }.getOrElse { emptyMap() }
        val items = itemsRaw.map { it.toMediaItem(context).copy(categoryName = catLabelById[it.categoryId]) }
        val (withArt, withoutArt) = items.partition { it.hasArtwork() }
        val ordered = withArt + withoutArt

        val nextKey = if (items.size < limit) null else page + 1
        val prevKey = if (page == 0) null else page - 1
        LoadResult.Page(ordered, prevKey, nextKey)
    } catch (e: Exception) {
        LoadResult.Error(e)
    }
}
