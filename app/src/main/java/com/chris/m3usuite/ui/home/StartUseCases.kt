package com.chris.m3usuite.ui.home

import android.content.Context
import androidx.paging.PagingData
import com.chris.m3usuite.data.repo.MediaQueryRepository
import com.chris.m3usuite.data.repo.TelegramContentRepository
import com.chris.m3usuite.domain.selectors.sortByYearDesc
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import com.chris.m3usuite.model.isAdultCategory


/**
 * Lightweight "domain" layer for Start screen.
 * Uses existing repositories one-to-one to avoid any behavioural changes.
 */
class StartUseCases(
    private val context: Context,
    private val store: SettingsStore,
    private val mediaRepo: MediaQueryRepository = MediaQueryRepository(context, store),
    private val telegramRepo: TelegramContentRepository = TelegramContentRepository(context, store),
) {
    suspend fun listByTypeFiltered(type: String, limit: Int, offset: Int): List<MediaItem> =
        withContext(Dispatchers.IO) { mediaRepo.listByTypeFiltered(type, limit, offset) }

    suspend fun isAllowed(type: String, id: Long): Boolean =
        withContext(Dispatchers.IO) { mediaRepo.isAllowed(type, id) }

    fun pagingSearchSeries(query: String): Flow<PagingData<MediaItem>> =
        mediaRepo.pagingSearchFilteredFlow("series", query)

    fun pagingSearchVod(query: String): Flow<PagingData<MediaItem>> =
        mediaRepo.pagingSearchFilteredFlow("vod", query)

    fun pagingSearchLive(query: String): Flow<PagingData<MediaItem>> =
        mediaRepo.pagingSearchFilteredFlow("live", query)

    fun pagingByTypeFilteredLive(provider: String?): Flow<PagingData<MediaItem>> =
        if (provider.isNullOrBlank()) mediaRepo.pagingByTypeFilteredFlow("live", null)
        else mediaRepo.pagingByTypeFilteredFlow("live", provider)

    suspend fun telegramSearchAllChats(query: String, limit: Int): List<MediaItem> =
        withContext(Dispatchers.IO) { telegramRepo.searchAllChats(query, limit) }

    suspend fun computeSeriesMixed(recent: List<MediaItem>, newest: List<MediaItem>, showAdults: Boolean, kid: Boolean): Pair<List<MediaItem>, Set<Long>> =
        withContext(Dispatchers.Default) {
            val filteredRecent = recent
                .filter { showAdults || !it.isAdultCategory() }
                .filter { it.id > 0 } // same semantics as original sort+distinct
                .distinctBy { it.id }

            val filteredNewest = newest
                .filter { showAdults || !it.isAdultCategory() }
                .distinctBy { it.id }

            val recentIds = filteredRecent.map { it.id }.toSet()
            val newestExcl = filteredNewest.filter { it.id !in recentIds }
            (filteredRecent + newestExcl) to newestExcl.map { it.id }.toSet()
        }

    suspend fun computeVodMixed(recent: List<MediaItem>, newest: List<MediaItem>, showAdults: Boolean): Pair<List<MediaItem>, Set<Long>> =
        withContext(Dispatchers.Default) {
            val filteredRecent = recent
                .filter { showAdults || !it.isAdultCategory() }
                .distinctBy { it.id }

            val filteredNewest = newest
                .filter { showAdults || !it.isAdultCategory() }
                .distinctBy { it.id }

            val recentIds = filteredRecent.map { it.id }.toSet()
            val newestExcl = filteredNewest.filter { it.id !in recentIds }
            (filteredRecent + newestExcl) to newestExcl.map { it.id }.toSet()
        }

    suspend fun sortSeries(list: List<MediaItem>): List<MediaItem> =
        withContext(Dispatchers.Default) { sortByYearDesc(list, { it.year }, { it.name }).distinctBy { it.id } }

    suspend fun sortVod(list: List<MediaItem>): List<MediaItem> =
        withContext(Dispatchers.Default) { sortByYearDesc(list, { it.year }, { it.name }).distinctBy { it.id } }
}