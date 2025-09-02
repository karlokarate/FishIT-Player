package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

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

    suspend fun globalSearchFiltered(query: String, limit: Int, offset: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!isKid()) return@withContext db.mediaDao().globalSearch(query, limit, offset)
        val kidId = settings.currentProfileId.first()
        val types = listOf("live", "vod", "series")
        val allowedByType = types.associateWith { effectiveAllowedIds(kidId, it) }
        db.mediaDao().globalSearch(query, limit, offset).filter { item ->
            val set = allowedByType[item.type] ?: emptySet()
            item.id in set
        }
    }

    suspend fun isAllowed(type: String, id: Long): Boolean = withContext(Dispatchers.IO) {
        if (!isKid()) return@withContext true
        val kidId = settings.currentProfileId.first()
        val allowed = effectiveAllowedIds(kidId, type)
        id in allowed
    }
}
