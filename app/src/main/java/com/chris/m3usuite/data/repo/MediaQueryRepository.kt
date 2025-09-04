package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.MediaItem
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

/**
 * Filtered media queries that respect Kid profile allowances when active.
 * Adult profile returns unfiltered results (legacy behavior).
 */
class MediaQueryRepository(
    private val context: Context,
    private val settings: SettingsStore
) {
    private val db get() = DbProvider.get(context)

    private suspend fun isKid(): Boolean = withContext(Dispatchers.IO) {
        val id = settings.currentProfileId.first()
        if (id <= 0) return@withContext false
        val prof = db.profileDao().byId(id)
        prof?.type != "adult"
    }

    private suspend fun allowedIdsForType(kidId: Long, type: String): Set<Long> = withContext(Dispatchers.IO) {
        db.kidContentDao().listForKidAndType(kidId, type).map { it.contentId }.toSet()
    }

    private suspend fun allowedIdsByCategories(kidId: Long, type: String): Set<Long> = withContext(Dispatchers.IO) {
        val cats = db.kidCategoryAllowDao().listForKidAndType(kidId, type).map { it.categoryId }.filter { it.isNotBlank() }
        if (cats.isEmpty()) emptySet() else db.mediaDao().idsByTypeAndCategories(type, cats).toSet()
    }

    private suspend fun blockedIdsForType(kidId: Long, type: String): Set<Long> = withContext(Dispatchers.IO) {
        db.kidContentBlockDao().listForKidAndType(kidId, type).map { it.contentId }.toSet()
    }

    private suspend fun effectiveAllowedIds(kidId: Long, type: String): Set<Long> {
        val byItem = allowedIdsForType(kidId, type)
        val byCats = allowedIdsByCategories(kidId, type)
        val blocks = blockedIdsForType(kidId, type)
        return (byItem + byCats) - blocks
    }

    suspend fun listByTypeFiltered(type: String, limit: Int, offset: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!isKid()) return@withContext db.mediaDao().listByType(type, limit, offset)
        val kidId = settings.currentProfileId.first()
        val allowed = effectiveAllowedIds(kidId, type)
        db.mediaDao().listByType(type, limit, offset).filter { it.id in allowed }
    }

    suspend fun byTypeAndCategoryFiltered(type: String, cat: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!isKid()) return@withContext db.mediaDao().byTypeAndCategory(type, cat)
        val kidId = settings.currentProfileId.first()
        val allowed = effectiveAllowedIds(kidId, type)
        db.mediaDao().byTypeAndCategory(type, cat).filter { it.id in allowed }
    }

    suspend fun categoriesByTypeFiltered(type: String): List<String?> = withContext(Dispatchers.IO) {
        if (!isKid()) return@withContext db.mediaDao().categoriesByType(type)
        val kidId = settings.currentProfileId.first()
        val allowed = effectiveAllowedIds(kidId, type)
        db.mediaDao().byTypeAndCategory(type, null).filter { it.id in allowed }.map { it.categoryName }.distinct()
    }

    private fun toFtsQuery(input: String): String {
        val cleaned = input.trim().lowercase()
        if (cleaned.isEmpty()) return ""
        val tokens = cleaned.split(Regex("\\s+")).filter { it.length >= 2 }.map { it + "*" }
        return if (tokens.isEmpty()) "" else tokens.joinToString(" AND ")
    }

    suspend fun globalSearchFiltered(query: String, limit: Int, offset: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        val fts = toFtsQuery(query)
        val base = if (fts.isNotBlank()) db.mediaDao().globalSearchFts(fts, limit, offset)
                   else db.mediaDao().globalSearch(query, limit, offset)
        if (!isKid()) return@withContext base
        val kidId = settings.currentProfileId.first()
        val types = listOf("live", "vod", "series")
        val allowedByType = types.associateWith { effectiveAllowedIds(kidId, it) }
        base.filter { item ->
            val set = allowedByType[item.type] ?: emptySet()
            item.id in set
        }
    }

    private fun pagerConfig(): PagingConfig = PagingConfig(
        pageSize = 60,
        prefetchDistance = 2,
        initialLoadSize = 120,
        enablePlaceholders = false
    )

    fun pagingByTypeFilteredFlow(type: String, cat: String?): Flow<PagingData<MediaItem>> {
        val source = { db.mediaDao().pagingByTypeAndCategory(type, cat) }
        val flow = Pager(pagerConfig(), pagingSourceFactory = source).flow
        return flow.map { data ->
            if (!isKid()) return@map data
            val kidId = settings.currentProfileId.first()
            val allowed = effectiveAllowedIds(kidId, type)
            data.filter { mi -> mi.id in allowed }
        }
    }

    fun pagingSearchFilteredFlow(query: String): Flow<PagingData<MediaItem>> {
        val fts = toFtsQuery(query)
        val source = if (fts.isNotBlank()) { { db.mediaDao().pagingSearchFts(fts) } }
                     else { { db.mediaDao().pagingByType("vod") } } // harmless fallback
        val flow = Pager(pagerConfig(), pagingSourceFactory = source).flow
        return flow.map { data ->
            if (!isKid()) return@map data
            val kidId = settings.currentProfileId.first()
            val types = listOf("live", "vod", "series")
            val allowedByType = types.associateWith { effectiveAllowedIds(kidId, it) }
            data.filter { mi -> (allowedByType[mi.type] ?: emptySet()).contains(mi.id) }
        }
    }

    suspend fun isAllowed(type: String, id: Long): Boolean = withContext(Dispatchers.IO) {
        if (!isKid()) return@withContext true
        val kidId = settings.currentProfileId.first()
        val allowed = effectiveAllowedIds(kidId, type)
        id in allowed
    }
}
