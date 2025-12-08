package com.chris.m3usuite.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.chris.m3usuite.core.logging.UnifiedLog
import com.chris.m3usuite.data.obx.ObxProfile
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.repo.KidContentRepository
import com.chris.m3usuite.data.repo.PermissionRepository
import com.chris.m3usuite.data.repo.ProfileObxRepository
import com.chris.m3usuite.data.repo.TelegramContentRepository
import com.chris.m3usuite.data.repo.XtreamObxRepository
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.domain.TelegramItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
class StartViewModel(
    private val appContext: Context,
    private val store: SettingsStore = SettingsStore(appContext),
    private val obxRepo: XtreamObxRepository = XtreamObxRepository(appContext, store),
    private val permRepo: PermissionRepository = PermissionRepository(appContext, store),
    private val profileRepo: ProfileObxRepository = ProfileObxRepository(appContext),
    private val kidRepo: KidContentRepository = KidContentRepository(appContext),
    private val tgRepo: TelegramContentRepository = TelegramContentRepository(appContext, store),
    private val use: StartUseCases = StartUseCases(appContext, store),
) : ViewModel() {
    // --- Query handling (kept compatible with current StartScreen) ---
    val query = MutableStateFlow("")
    val debouncedQuery =
        query
            .map { it.trimStart() }
            .distinctUntilChanged()
            .debounce(300)
            .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // --- Basic settings / header ---
    val showAdults = store.showAdults.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val headerLibraryTabIndex = store.libraryTabIndex.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // --- Permissions & Profile derived state ---
    private val currentProfileId = store.currentProfileId.stateIn(viewModelScope, SharingStarted.Eagerly, -1L)

    val isKid: StateFlow<Boolean> =
        currentProfileId
            .map { id ->
                if (id <= 0) {
                    false
                } else {
                    withContext(Dispatchers.IO) {
                        val box = ObxStore.get(appContext).boxFor(ObxProfile::class.java)
                        val p = box.get(id)
                        p?.type == "kid"
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val canEditFavorites: StateFlow<Boolean> =
        flow {
            emit(permRepo.current().canEditFavorites)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val canEditWhitelist: StateFlow<Boolean> =
        flow {
            emit(permRepo.current().canEditWhitelist)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // --- Home lists ---
    private val _series = MutableStateFlow<List<MediaItem>>(emptyList())
    val series: StateFlow<List<MediaItem>> = _series

    private val _movies = MutableStateFlow<List<MediaItem>>(emptyList())
    val movies: StateFlow<List<MediaItem>> = _movies

    private val _live = MutableStateFlow<List<MediaItem>>(emptyList())
    val live: StateFlow<List<MediaItem>> = _live

    private val _seriesMixed = MutableStateFlow<List<MediaItem>>(emptyList())
    val seriesMixed: StateFlow<List<MediaItem>> = _seriesMixed

    private val _seriesNewIds = MutableStateFlow<Set<Long>>(emptySet())
    val seriesNewIds: StateFlow<Set<Long>> = _seriesNewIds

    private val _vodMixed = MutableStateFlow<List<MediaItem>>(emptyList())
    val vodMixed: StateFlow<List<MediaItem>> = _vodMixed

    private val _vodNewIds = MutableStateFlow<Set<Long>>(emptySet())
    val vodNewIds: StateFlow<Set<Long>> = _vodNewIds

    // Favorites (Live)
    private val favCsv = store.favoriteLiveIdsCsv
    private val _favLive = MutableStateFlow<List<MediaItem>>(emptyList())
    val favLive: StateFlow<List<MediaItem>> = _favLive

    // Telegram content by chat (Phase D: Now uses TelegramItem from ObxTelegramItem)
    private val _telegramVodByChat =
        MutableStateFlow<Map<Long, Pair<String, List<TelegramItem>>>>(emptyMap())
    val telegramVodByChat: StateFlow<Map<Long, Pair<String, List<TelegramItem>>>> =
        _telegramVodByChat

    // Telegram chat summaries (Phase D: for UI rows showing per-chat VOD counts)
    private val _telegramChatSummaries =
        MutableStateFlow<List<TelegramContentRepository.TelegramChatSummary>>(emptyList())
    val telegramChatSummaries: StateFlow<List<TelegramContentRepository.TelegramChatSummary>> =
        _telegramChatSummaries

    // Legacy: Keep old property for backward compatibility during migration
    @Deprecated("Use telegramVodByChat for TelegramItem-based data")
    private val _telegramContentByChat =
        MutableStateFlow<Map<Long, Pair<String, List<MediaItem>>>>(emptyMap())

    @Deprecated("Use telegramVodByChat for TelegramItem-based data")
    val telegramContentByChat: StateFlow<Map<Long, Pair<String, List<MediaItem>>>> =
        _telegramContentByChat

    // Telegram enabled?
    val tgEnabled: StateFlow<Boolean> =
        combine(
            store.tgEnabled,
            store.tgSelectedChatsCsv,
        ) { enabled, chatCsv -> enabled && chatCsv.isNotBlank() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Paging for search
    private val _seriesPaging = MutableStateFlow<PagingData<MediaItem>?>(null)
    val seriesPaging: StateFlow<PagingData<MediaItem>?> = _seriesPaging

    private val _vodPaging = MutableStateFlow<PagingData<MediaItem>?>(null)
    val vodPaging: StateFlow<PagingData<MediaItem>?> = _vodPaging

    private val _livePaging = MutableStateFlow<PagingData<MediaItem>?>(null)
    val livePaging: StateFlow<PagingData<MediaItem>?> = _livePaging

    // UI State
    private val _ui = MutableStateFlow(StartUiState(loading = true))
    val ui: StateFlow<StartUiState> = _ui

    // One-off events
    private val _events = MutableSharedFlow<StartEvent>()
    val events: SharedFlow<StartEvent> = _events

    private var recomputeJob: Job? = null

    init {
        // Initial load
        viewModelScope.launch { reloadFromObx() }
        viewModelScope.launch { observeObxChanges() }
        viewModelScope.launch { observeDeltaImport() }
        viewModelScope.launch { observeFavoritesCsv() }
        viewModelScope.launch { observeTelegramContent() }
        viewModelScope.launch { observeQuery() }
    }

    private suspend fun observeQuery() {
        debouncedQuery.collect { q ->
            val isSearch = q.isNotBlank()
            _ui.update { it.copy(query = q, isSearchMode = isSearch) }

            if (isSearch) {
                _seriesPaging.value =
                    use
                        .pagingSearchSeries(q)
                        .stateIn(
                            viewModelScope,
                            SharingStarted.Lazily,
                            PagingData.empty<MediaItem>(),
                        ).value

                _vodPaging.value =
                    use
                        .pagingSearchVod(q)
                        .stateIn(
                            viewModelScope,
                            SharingStarted.Lazily,
                            PagingData.empty<MediaItem>(),
                        ).value

                _livePaging.value =
                    use
                        .pagingSearchLive(q)
                        .stateIn(
                            viewModelScope,
                            SharingStarted.Lazily,
                            PagingData.empty<MediaItem>(),
                        ).value
            } else {
                _seriesPaging.value = null
                _vodPaging.value = null
                _livePaging.value = null
            }
        }
    }

    fun setQuery(q: String) {
        query.value = q
    }

    suspend fun reloadFromObx() {
        _ui.update { it.copy(loading = true) }
        val isKidNow = isKid.value
        val showAdultsNow = showAdults.value

        val filteredSeries = use.listByTypeFiltered("series", 600, 0)
        val filteredVod = use.listByTypeFiltered("vod", 600, 0)
        val filteredLive = use.listByTypeFiltered("live", 600, 0)

        _series.value = use.sortSeries(filteredSeries)
        _movies.value = use.sortVod(filteredVod)
        _live.value = filteredLive.distinctBy { it.id }

        recomputeMixedRows(isKidNow, showAdultsNow)

        _ui.update {
            it.copy(
                loading = false,
                isKid = isKidNow,
                showAdults = showAdultsNow,
                canEditFavorites = canEditFavorites.value,
                canEditWhitelist = canEditWhitelist.value,
            )
        }
    }

    private fun observeObxChanges() =
        viewModelScope.launch {
            merge(
                obxRepo.liveChanges().map { "live" },
                obxRepo.vodChanges().map { "vod" },
                obxRepo.seriesChanges().map { "series" },
            ).debounce(350).collectLatest { kind ->
                when (kind) {
                    "live" -> _live.value = use.listByTypeFiltered("live", 600, 0).distinctBy { it.id }
                    "vod" -> {
                        _movies.value = use.sortVod(use.listByTypeFiltered("vod", 600, 0))
                        recomputeMixedRows(isKid.value, showAdults.value)
                    }
                    "series" -> {
                        _series.value = use.sortSeries(use.listByTypeFiltered("series", 600, 0))
                        recomputeMixedRows(isKid.value, showAdults.value)
                    }
                }
            }
        }

    private fun observeDeltaImport() =
        viewModelScope.launch {
            WorkManager
                .getInstance(appContext)
                .getWorkInfosForUniqueWorkLiveData("xtream_delta_import_once")
                .asFlow()
                .map { infos -> infos.firstOrNull { it.state == WorkInfo.State.SUCCEEDED }?.id }
                .distinctUntilChanged()
                .collect { if (it != null) reloadFromObx() }
        }

    private fun observeFavoritesCsv() =
        viewModelScope.launch {
            favCsv.collectLatest { csv ->
                if (csv.isBlank()) {
                    _favLive.value = emptyList()
                    return@collectLatest
                }
                val rawIds = csv.split(',').mapNotNull { it.toLongOrNull() }.distinct()
                if (rawIds.isEmpty()) {
                    _favLive.value = emptyList()
                    return@collectLatest
                }
                val allAllowed = use.listByTypeFiltered("live", 6000, 0)
                if (allAllowed.isEmpty()) {
                    _favLive.value = emptyList()
                    return@collectLatest
                }
                val translated = rawIds.filter { it >= 1_000_000_000_000L }
                val map = allAllowed.associateBy { it.id }
                _favLive.value = translated.mapNotNull { map[it] }.distinctBy { it.id }
            }
        }

    private fun observeTelegramContent() =
        viewModelScope.launch {
            // Phase D: Use new ObxTelegramItem-based APIs

            // Observe chat summaries for quick overview
            launch {
                tgRepo.observeVodChatSummaries().collectLatest { summaries ->
                    UnifiedLog.debug(
                        "telegram-ui",
                        "StartVM received ${summaries.size} Telegram chat summaries",
                    )
                    _telegramChatSummaries.value = summaries
                }
            }

            // Observe full TelegramItem data by chat for row rendering
            launch {
                tgRepo.observeVodItemsByChat().collectLatest { chatMap ->
                    // Resolve chat titles for each chat
                    val chatMapWithTitles = mutableMapOf<Long, Pair<String, List<TelegramItem>>>()

                    for ((chatId, items) in chatMap) {
                        val chatTitle =
                            withContext(Dispatchers.IO) {
                                try {
                                    com.chris.m3usuite.telegram.core
                                        .T_TelegramServiceClient
                                        .getInstance(appContext)
                                        .resolveChatTitle(chatId)
                                } catch (e: Exception) {
                                    "Chat $chatId"
                                }
                            }
                        chatMapWithTitles[chatId] = chatTitle to items.take(120)
                    }

                    val totalItems = chatMap.values.sumOf { it.size }
                    UnifiedLog.debug(
                        "telegram-ui",
                        "StartVM received ${chatMap.size} chats, $totalItems total items from ObxTelegramItem (new pipeline)",
                    )

                    _telegramVodByChat.value = chatMapWithTitles
                }
            }
        }

    fun onReorderFavorites(newOrderIds: List<Long>) =
        viewModelScope.launch(Dispatchers.IO) {
            store.setFavoriteLiveIdsCsv(newOrderIds.joinToString(","))
            val aggressive = store.epgFavSkipXmltvIfXtreamOk.first()
            runCatching {
                com.chris.m3usuite.work.SchedulingGateway
                    .refreshFavoritesEpgNow(appContext, aggressive)
            }
        }

    fun onFavoritesRemove(removeIds: List<Long>) =
        viewModelScope.launch(Dispatchers.IO) {
            val current =
                store.favoriteLiveIdsCsv
                    .first()
                    .split(',')
                    .mapNotNull { it.toLongOrNull() }
                    .toMutableList()
            current.removeAll(removeIds.toSet())
            store.setFavoriteLiveIdsCsv(current.joinToString(","))
            val aggressive = store.epgFavSkipXmltvIfXtreamOk.first()
            runCatching {
                com.chris.m3usuite.work.SchedulingGateway
                    .refreshFavoritesEpgNow(appContext, aggressive)
            }
        }

    fun setFavoritesCsv(csv: String) =
        viewModelScope.launch(Dispatchers.IO) {
            store.setFavoriteLiveIdsCsv(csv)
            val aggressive = store.epgFavSkipXmltvIfXtreamOk.first()
            runCatching {
                com.chris.m3usuite.work.SchedulingGateway
                    .refreshFavoritesEpgNow(appContext, aggressive)
            }
        }

    private fun recomputeMixedRows(
        isKid: Boolean,
        showAdults: Boolean,
    ) {
        recomputeJob?.cancel()
        recomputeJob =
            viewModelScope.launch(Dispatchers.Default) {
                // series
                val recentSeries = _series.value.take(36)
                val newestSeries = _series.value.take(64)
                val (mixedSeries, newIdsSeries) = use.computeSeriesMixed(recentSeries, newestSeries, showAdults, isKid)
                _seriesMixed.value = mixedSeries
                _seriesNewIds.value = newIdsSeries

                // vod
                val recentVod = _movies.value.take(36)
                val newestVod = _movies.value.take(64)
                val (mixedVod, newIdsVod) = use.computeVodMixed(recentVod, newestVod, showAdults)
                _vodMixed.value = mixedVod
                _vodNewIds.value = newIdsVod
            }
    }

    fun allowForAllKids(
        type: String,
        id: Long,
    ) = viewModelScope.launch(Dispatchers.IO) {
        val kids = profileRepo.all().filter { it.type == "kid" }
        kids.forEach { kidRepo.allow(it.id, type, id) }
        _events.emit(StartEvent.Toast("Für Kinder freigegeben"))
    }

    companion object {
        fun Factory(ctx: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = StartViewModel(ctx.applicationContext) as T
            }
    }
}
