package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.paging.filter
import com.chris.m3usuite.data.obx.toMediaItem

/**
 * Filtered media queries that respect Kid profile allowances when active.
 * Adult profile returns unfiltered results (legacy behavior).
 */
class MediaQueryRepository(
    private val context: Context,
    private val settings: SettingsStore
) {
    private val obxRepo by lazy { XtreamObxRepository(context, settings) }
    private val box get() = com.chris.m3usuite.data.obx.ObxStore.get(context)

    private fun isObxId(id: Long): Boolean = id >= 1_000_000_000_000L
    private fun obxKindPrefix(type: String): Long = when (type) {
        "live" -> 1_000_000_000_000L
        "vod" -> 2_000_000_000_000L
        else -> 3_000_000_000_000L
    }
    private fun decodeStreamIdFromObxId(type: String, id: Long): Int? {
        val base = obxKindPrefix(type)
        if (id < base) return null
        val v = (id - base)
        return v.toInt()
    }

    private suspend fun isKid(): Boolean = withContext(Dispatchers.IO) {
        val id = settings.currentProfileId.first()
        if (id <= 0) return@withContext false
        val prof = box.boxFor(com.chris.m3usuite.data.obx.ObxProfile::class.java).get(id)
        prof?.type != "adult"
    }

    private suspend fun allowedIdsForType(kidId: Long, type: String): Set<Long> = withContext(Dispatchers.IO) {
        val b = box.boxFor(com.chris.m3usuite.data.obx.ObxKidContentAllow::class.java)
        b.query(com.chris.m3usuite.data.obx.ObxKidContentAllow_.kidProfileId.equal(kidId).and(com.chris.m3usuite.data.obx.ObxKidContentAllow_.contentType.equal(type))).build().find().map { it.contentId }.toSet()
    }

    private suspend fun allowedIdsByCategories(kidId: Long, type: String): Set<Long> = withContext(Dispatchers.IO) {
        val catsBox = box.boxFor(com.chris.m3usuite.data.obx.ObxKidCategoryAllow::class.java)
        val allowedCats = catsBox.query(
            com.chris.m3usuite.data.obx.ObxKidCategoryAllow_.kidProfileId.equal(kidId)
                .and(com.chris.m3usuite.data.obx.ObxKidCategoryAllow_.contentType.equal(type))
        ).build().find().map { it.categoryId }.toSet()
        if (allowedCats.isEmpty()) return@withContext emptySet()
        when (type) {
            "live" -> obxRepo.livePaged(0, Long.MAX_VALUE).filter { it.categoryId in allowedCats }.map { 1_000_000_000_000L + it.streamId }.toSet()
            "vod" -> obxRepo.vodPaged(0, Long.MAX_VALUE).filter { it.categoryId in allowedCats }.map { 2_000_000_000_000L + it.vodId }.toSet()
            else -> obxRepo.seriesPaged(0, Long.MAX_VALUE).filter { it.categoryId in allowedCats }.map { 3_000_000_000_000L + it.seriesId }.toSet()
        }
    }

    private suspend fun blockedIdsForType(kidId: Long, type: String): Set<Long> = withContext(Dispatchers.IO) {
        val b = box.boxFor(com.chris.m3usuite.data.obx.ObxKidContentBlock::class.java)
        b.query(com.chris.m3usuite.data.obx.ObxKidContentBlock_.kidProfileId.equal(kidId).and(com.chris.m3usuite.data.obx.ObxKidContentBlock_.contentType.equal(type))).build().find().map { it.contentId }.toSet()
    }

    private suspend fun effectiveAllowedIds(kidId: Long, type: String): Set<Long> {
        val byItem = allowedIdsForType(kidId, type)
        val byCats = allowedIdsByCategories(kidId, type)
        val blocks = blockedIdsForType(kidId, type)
        return (byItem + byCats) - blocks
    }

    suspend fun listByTypeFiltered(type: String, limit: Int, offset: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        val items: List<MediaItem> = when (type) {
            "live" -> obxRepo.livePaged(offset.toLong(), limit.toLong()).map { it.toMediaItem(context) }
            "vod" -> obxRepo.vodPaged(offset.toLong(), limit.toLong()).map { it.toMediaItem(context) }
            else -> obxRepo.seriesPaged(offset.toLong(), limit.toLong()).map { it.toMediaItem(context) }
        }
        if (!isKid()) return@withContext items
        val kidId = settings.currentProfileId.first()
        val allowed = effectiveAllowedIds(kidId, type)
        items.filter { mi -> mi.id in allowed }
    }

    suspend fun byTypeAndCategoryFiltered(type: String, cat: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        val items: List<MediaItem> = when (type) {
            "live" -> {
                val base = if (!cat.isNullOrBlank()) obxRepo.liveByCategoryPaged(cat, 0, Int.MAX_VALUE.toLong()) else obxRepo.livePaged(0, Int.MAX_VALUE.toLong())
                base.map { it.toMediaItem(context) }
            }
            "vod" -> {
                val base = if (!cat.isNullOrBlank()) obxRepo.vodByCategoryPaged(cat, 0, Int.MAX_VALUE.toLong()) else obxRepo.vodPaged(0, Int.MAX_VALUE.toLong())
                base.map { it.toMediaItem(context) }
            }
            else -> {
                val base = if (!cat.isNullOrBlank()) obxRepo.seriesByCategoryPaged(cat, 0, Int.MAX_VALUE.toLong()) else obxRepo.seriesPaged(0, Int.MAX_VALUE.toLong())
                base.map { it.toMediaItem(context) }
            }
        }
        if (!isKid()) return@withContext items
        val kidId = settings.currentProfileId.first()
        val allowed = effectiveAllowedIds(kidId, type)
        items.filter { mi -> mi.id in allowed }
    }

    suspend fun categoriesByTypeFiltered(type: String): List<String?> = withContext(Dispatchers.IO) {
        val cats = obxRepo.categories(type).map { it.categoryName }
        // Kid-filtering of categories (approximation via allowed items)
        if (!isKid()) return@withContext cats
        val items = listByTypeFiltered(type, 10_000, 0)
        items.mapNotNull { it.categoryName }.distinct()
    }

    private fun toFtsQuery(input: String): String {
        val cleaned = input.trim().lowercase()
        if (cleaned.isEmpty()) return ""
        val tokens = cleaned.split(Regex("\\s+")).filter { it.length >= 2 }.map { it + "*" }
        return if (tokens.isEmpty()) "" else tokens.joinToString(" AND ")
    }

    suspend fun globalSearchFiltered(query: String, limit: Int, offset: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        val q = query.trim()
        val lv = obxRepo.searchLiveByName(q, offset.toLong(), limit.toLong())
        val vv = obxRepo.searchVodByName(q, offset.toLong(), limit.toLong())
        val sv = obxRepo.searchSeriesByName(q, offset.toLong(), limit.toLong())
        val all = (lv.map { it.toMediaItem(context) } + vv.map { it.toMediaItem(context) } + sv.map { it.toMediaItem(context) })
        if (!isKid()) return@withContext all.take(limit)
        val kidId = settings.currentProfileId.first()
        val allowed = (effectiveAllowedIds(kidId, "live") + effectiveAllowedIds(kidId, "vod") + effectiveAllowedIds(kidId, "series"))
        all.filter { it.id in allowed }.take(limit)
    }

    private fun pagerConfig(): PagingConfig = PagingConfig(
        pageSize = 60,
        prefetchDistance = 2,
        initialLoadSize = 120,
        enablePlaceholders = false
    )

    fun pagingByTypeFilteredFlow(type: String, cat: String?): Flow<PagingData<MediaItem>> {
        val paging = when (type) {
            "live" -> Pager(pagerConfig()) { ObxLivePagingSource(context, settings, cat, null) }.flow
            "vod" -> Pager(pagerConfig()) { ObxVodPagingSource(context, settings, cat, null) }.flow
            else -> Pager(pagerConfig()) { ObxSeriesPagingSource(context, settings, cat, null) }.flow
        }
        return paging.map { data ->
            if (!isKid()) return@map data
            val kidId = settings.currentProfileId.first()
            val allowed = effectiveAllowedIds(kidId, type)
            data.filter { mi -> mi.id in allowed }
        }
    }

    fun pagingSearchFilteredFlow(query: String): Flow<PagingData<MediaItem>> {
        val trimmed = query.trim().takeIf { it.isNotEmpty() }
        // Use OBX paging sources with query
        val flow = Pager(pagerConfig()) { ObxVodPagingSource(context, settings, null, trimmed) }.flow
        return flow.map { data ->
            if (!isKid()) return@map data
            val kidId = settings.currentProfileId.first()
            val allowed = effectiveAllowedIds(kidId, "vod")
            data.filter { mi -> mi.id in allowed }
        }
    }

    suspend fun isAllowed(type: String, id: Long): Boolean = withContext(Dispatchers.IO) {
        if (!isKid()) return@withContext true
        val kidId = settings.currentProfileId.first()
        val allowed = effectiveAllowedIds(kidId, type)
        id in allowed
    }
}
