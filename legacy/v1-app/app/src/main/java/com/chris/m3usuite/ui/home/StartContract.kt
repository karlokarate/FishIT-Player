package com.chris.m3usuite.ui.home

import androidx.compose.runtime.Immutable
import com.chris.m3usuite.model.MediaItem

@Immutable
data class AssignState(
    val active: Boolean = false,
    val selVod: Set<Long> = emptySet(),
    val selSeries: Set<Long> = emptySet(),
    val selLive: Set<Long> = emptySet(),
)

@Immutable
data class HomeSectionsState(
    val series: List<MediaItem> = emptyList(),
    val vod: List<MediaItem> = emptyList(),
    val live: List<MediaItem> = emptyList(),
    val seriesMixed: List<MediaItem> = emptyList(),
    val vodMixed: List<MediaItem> = emptyList(),
    val seriesNewIds: Set<Long> = emptySet(),
    val vodNewIds: Set<Long> = emptySet(),
    val favLive: List<MediaItem> = emptyList(),
)

@Immutable
data class StartUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val isKid: Boolean = false,
    val showAdults: Boolean = false,
    val canEditFavorites: Boolean = true,
    val canEditWhitelist: Boolean = true,
    val query: String = "",
    val isSearchMode: Boolean = false,
    val headerLibraryTabIndex: Int = 0,
    val tgEnabled: Boolean = false,
)

sealed interface StartEvent {
    data class Toast(
        val message: String,
    ) : StartEvent

    data class Failure(
        val message: String,
    ) : StartEvent
}
