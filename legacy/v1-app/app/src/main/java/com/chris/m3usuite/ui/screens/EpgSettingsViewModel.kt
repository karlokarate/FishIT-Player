package com.chris.m3usuite.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.data.repo.SettingsRepository
import com.chris.m3usuite.domain.usecases.ScheduleEpgRefresh
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class EpgSettingsState(
    val useXtreamForFavorites: Boolean = true,
    val skipXmltvIfXtreamOk: Boolean = false,
    val isRefreshing: Boolean = false,
)

class EpgSettingsViewModel(
    app: Application,
    private val repo: SettingsRepository,
    private val scheduleRefresh: ScheduleEpgRefresh,
) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(EpgSettingsState())
    val state: StateFlow<EpgSettingsState> = _state

    init {
        observe()
    }

    private fun observe() {
        viewModelScope.launch {
            combine(repo.epgFavUseXtream, repo.epgFavSkipXmltvIfXtreamOk) { useXt, skipXmltv ->
                EpgSettingsState(useXtreamForFavorites = useXt, skipXmltvIfXtreamOk = skipXmltv)
            }.collect { _state.value = it }
        }
    }

    fun onToggleUseXtream(value: Boolean) =
        viewModelScope.launch {
            repo.setEpgFavUseXtream(value)
        }

    fun onToggleSkipXmltv(value: Boolean) =
        viewModelScope.launch {
            repo.setEpgFavSkipXmltvIfXtreamOk(value)
        }

    fun onRefreshFavoritesNow() =
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true)
            runCatching { scheduleRefresh(_state.value.skipXmltvIfXtreamOk) }
            _state.value = _state.value.copy(isRefreshing = false)
        }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val store = SettingsStore(app)
                    val repo = SettingsRepository(store)
                    val schedule = ScheduleEpgRefresh(app)
                    @Suppress("UNCHECKED_CAST")
                    return EpgSettingsViewModel(app, repo, schedule) as T
                }
            }
        }
    }
}
