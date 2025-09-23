package com.chris.m3usuite.data.repo

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.filter
import com.chris.m3usuite.data.obx.ObxKidCategoryAllow
import com.chris.m3usuite.data.obx.ObxKidCategoryAllow_
import com.chris.m3usuite.data.obx.ObxKidContentAllow
import com.chris.m3usuite.data.obx.ObxKidContentAllow_
import com.chris.m3usuite.data.obx.ObxKidContentBlock
import com.chris.m3usuite.data.obx.ObxKidContentBlock_
import com.chris.m3usuite.data.obx.ObxLive
import com.chris.m3usuite.data.obx.ObxLive_
import com.chris.m3usuite.data.obx.ObxProfile
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.obx.ObxVod
import com.chris.m3usuite.data.obx.ObxVod_
import com.chris.m3usuite.data.obx.ObxSeries
import com.chris.m3usuite.data.obx.ObxSeries_
import com.chris.m3usuite.data.obx.toMediaItem
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Filtered media queries that respect Kid profile allowances when active.
 * Adult profile returns unfiltered results (legacy behavior).
 */
class MediaQueryRepository(
    private val context: Context,
    private val settings: SettingsStore
) {
    private val obxRepo by lazy { XtreamObxRepository(context, settings) }
    private val store get() = ObxStore.get(context)

    private fun obxKindPrefix(type: String): Long = when (type) {
        "live" -> 1_000_000_000_000L
        "vod"  -> 2_000_000_000_000L
        else   -> 3_000_000_000_000L
    }

    private suspend fun currentKidIdOrNull(): Long? = withContext(Dispatchers.IO) {
        val id = settings.currentProfileId.first()
        if (id <= 0) return@withContext null
        val prof = store.boxFor(ObxProfile::class.java).get(id)
        // Alles außer "adult" wird restriktiert (inkl. "guest")
        if (prof?.type == "adult") null else id
    }

    private suspend fun isKid(): Boolean = currentKidIdOrNull() != null

    private suspend fun allowedIdsForType(kidId: Long, type: String): Set<Long> = withContext(Dispatchers.IO) {
        val b = store.boxFor(ObxKidContentAllow::class.java)
        b.query(
            ObxKidContentAllow_.kidProfileId.equal(kidId)
                .and(ObxKidContentAllow_.contentType.equal(type))
        ).build().find().mapTo(LinkedHashSet()) { it.contentId }
    }

    /** Effizient: hole zugelassene OBX-IDs über Category‑Filter direkt aus OBX (Property‑Query → wenig RAM). */
    private suspend fun obxIdsForCategories(type: String, categoryIds: Set<String>): Set<Long> =
        withContext(Dispatchers.IO) {
            if (categoryIds.isEmpty()) return@withContext emptySet()
            when (type) {
                "live" -> {
                    val q = store.boxFor(ObxLive::class.java)
                        .query(ObxLive_.categoryId.oneOf(categoryIds.toTypedArray()))
                        .build()
                    val sids = q.property(ObxLive_.streamId).findInts().toList()
                    sids.mapTo(LinkedHashSet()) { obxKindPrefix("live") + it }
                }
                "vod" -> {
                    val q = store.boxFor(ObxVod::class.java)
                        .query(ObxVod_.categoryId.oneOf(categoryIds.toTypedArray()))
                        .build()
                    val vids = q.property(ObxVod_.vodId).findInts().toList()
                    vids.mapTo(LinkedHashSet()) { obxKindPrefix("vod") + it }
                }
                else -> {
                    val q = store.boxFor(ObxSeries::class.java)
                        .query(ObxSeries_.categoryId.oneOf(categoryIds.toTypedArray()))
                        .build()
                    val sids = q.property(ObxSeries_.seriesId).findInts().toList()
                    sids.mapTo(LinkedHashSet()) { obxKindPrefix("series") + it }
                }
            }
        }

    private suspend fun allowedIdsByCategories(kidId: Long, type: String): Set<Long> = withContext(Dispatchers.IO) {
        val catsBox = store.boxFor(ObxKidCategoryAllow::class.java)
        val allowedCats = catsBox.query(
            ObxKidCategoryAllow_.kidProfileId.equal(kidId)
                .and(ObxKidCategoryAllow_.contentType.equal(type))
        ).build().find().map { it.categoryId }.toSet()
        obxIdsForCategories(type, allowedCats)
    }

    private suspend fun blockedIdsForType(kidId: Long, type: String): Set<Long> = withContext(Dispatchers.IO) {
        val b = store.boxFor(ObxKidContentBlock::class.java)
        b.query(
            ObxKidContentBlock_.kidProfileId.equal(kidId)
                .and(ObxKidContentBlock_.contentType.equal(type))
        ).build().find().mapTo(LinkedHashSet()) { it.contentId }
    }

    private suspend fun effectiveAllowedIds(kidId: Long, type: String): Set<Long> {
        val byItem = allowedIdsForType(kidId, type)
        val byCats = allowedIdsByCategories(kidId, type)
        val blocks = blockedIdsForType(kidId, type)
        return ((byItem + byCats) - blocks)
    }

    suspend fun listByTypeFiltered(type: String, limit: Int, offset: Int): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val itemsRaw: List<MediaItem> = when (type) {
                "live" -> obxRepo.livePaged(offset.toLong(), limit.toLong()).map { it.toMediaItem(context) }
                "vod"  -> obxRepo.vodPaged(offset.toLong(), limit.toLong()).map { it.toMediaItem(context) }
                else   -> obxRepo.seriesPaged(offset.toLong(), limit.toLong()).map { it.toMediaItem(context) }
            }
            // Inject category labels so downstream filters (Adults) can apply reliably
            val catLabelById = runCatching { obxRepo.categories(type).associateBy({ it.categoryId }, { it.categoryName }) }
                .getOrElse { emptyMap() }
            val items = itemsRaw.map { it.copy(categoryName = catLabelById[it.categoryId]) }
            val showA = settings.showAdults.first()
            val base = if (showA) items else items.filter { it.categoryName?.trim()?.equals("For Adults", ignoreCase = true) != true }
            val kidId = currentKidIdOrNull() ?: return@withContext base
            val allowed = effectiveAllowedIds(kidId, type)
            base.filter { it.id in allowed }
        }

    suspend fun byTypeAndCategoryFiltered(type: String, cat: String?): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val itemsRaw: List<MediaItem> = when (type) {
                "live" -> {
                    val base = if (!cat.isNullOrBlank())
                        obxRepo.liveByCategoryPaged(cat, 0, Long.MAX_VALUE)
                    else obxRepo.livePaged(0, Long.MAX_VALUE)
                    base.map { it.toMediaItem(context) }
                }
                "vod" -> {
                    val base = if (!cat.isNullOrBlank())
                        obxRepo.vodByCategoryPaged(cat, 0, Long.MAX_VALUE)
                    else obxRepo.vodPaged(0, Long.MAX_VALUE)
                    base.map { it.toMediaItem(context) }
                }
                else -> {
                    val base = if (!cat.isNullOrBlank())
                        obxRepo.seriesByCategoryPaged(cat, 0, Long.MAX_VALUE)
                    else obxRepo.seriesPaged(0, Long.MAX_VALUE)
                    base.map { it.toMediaItem(context) }
                }
            }
            val catLabelById = runCatching { obxRepo.categories(type).associateBy({ it.categoryId }, { it.categoryName }) }
                .getOrElse { emptyMap() }
            val items = itemsRaw.map { it.copy(categoryName = catLabelById[it.categoryId]) }
            val showA = settings.showAdults.first()
            val base = if (showA) items else items.filter { it.categoryName?.trim()?.equals("For Adults", ignoreCase = true) != true }
            val kidId = currentKidIdOrNull() ?: return@withContext base
            val allowed = effectiveAllowedIds(kidId, type)
            base.filter { it.id in allowed }
        }

    suspend fun categoriesByTypeFiltered(type: String): List<String?> = withContext(Dispatchers.IO) {
        val cats = obxRepo.categories(type).map { it.categoryName }
        val kidId = currentKidIdOrNull() ?: return@withContext cats
        // Näherung über erlaubte Items
        val items = listByTypeFiltered(type, 10_000, 0)
        items.mapNotNull { it.categoryName }.distinct()
    }

    private fun pagerConfig(): PagingConfig = PagingConfig(
        pageSize = 60,
        prefetchDistance = 2,
        initialLoadSize = 120,
        enablePlaceholders = false
    )

    fun pagingByTypeFilteredFlow(type: String, cat: String?): Flow<PagingData<MediaItem>> {
        val flow = when (type) {
            "live" -> Pager(pagerConfig()) { ObxLivePagingSource(context, settings, cat, null) }.flow
            "vod"  -> Pager(pagerConfig()) { ObxVodPagingSource(context, settings, cat, null)  }.flow
            else   -> Pager(pagerConfig()) { ObxSeriesPagingSource(context, settings, cat, null) }.flow
        }
        return flow.map { data ->
            val showA = settings.showAdults.first()
            val kidId = currentKidIdOrNull()
            if (kidId == null) {
                if (showA) data else data.filter { it.categoryName?.trim()?.equals("For Adults", ignoreCase = true) != true }
            } else {
                val allowed = effectiveAllowedIds(kidId, type)
                val base = data.filter { it.id in allowed }
                if (showA) base else base.filter { it.categoryName?.trim()?.equals("For Adults", ignoreCase = true) != true }
            }
        }
    }

    fun pagingSearchFilteredFlow(type: String, query: String): Flow<PagingData<MediaItem>> {
        val trimmed = query.trim().takeIf { it.isNotEmpty() }
        val flow = when (type) {
            "live" -> Pager(pagerConfig()) { ObxLivePagingSource(context, settings, null, trimmed) }.flow
            "series" -> Pager(pagerConfig()) { ObxSeriesPagingSource(context, settings, null, trimmed) }.flow
            else -> Pager(pagerConfig()) { ObxVodPagingSource(context, settings, null, trimmed) }.flow
        }
        return flow.map { data ->
            val showA = settings.showAdults.first()
            val kidId = currentKidIdOrNull()
            if (kidId == null) {
                if (showA) data else data.filter { it.categoryName?.trim()?.equals("For Adults", ignoreCase = true) != true }
            } else {
                val allowed = effectiveAllowedIds(kidId, type)
                val base = data.filter { it.id in allowed }
                if (showA) base else base.filter { it.categoryName?.trim()?.equals("For Adults", ignoreCase = true) != true }
            }
        }
    }

    suspend fun isAllowed(type: String, id: Long): Boolean = withContext(Dispatchers.IO) {
        val kidId = currentKidIdOrNull() ?: return@withContext true
        val allowed = effectiveAllowedIds(kidId, type)
        id in allowed
    }
}
