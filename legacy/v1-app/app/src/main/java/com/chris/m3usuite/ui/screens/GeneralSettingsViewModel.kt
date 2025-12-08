package com.chris.m3usuite.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.data.repo.SettingsRepository
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GeneralSettingsState(
    val showAdults: Boolean = false,
    val isSaving: Boolean = false,
)

class GeneralSettingsViewModel(
    app: Application,
    private val repo: SettingsRepository,
) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(GeneralSettingsState())
    val state: StateFlow<GeneralSettingsState> = _state

    init {
        observe()
    }

    private fun observe() {
        viewModelScope.launch {
            repo.showAdults.collectLatest { value ->
                _state.update { it.copy(showAdults = value, isSaving = false) }
            }
        }
    }

    fun onToggleShowAdults(value: Boolean) {
        _state.update { it.copy(showAdults = value, isSaving = true) }
        viewModelScope.launch {
            repo.setShowAdults(value)
        }
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(GeneralSettingsViewModel::class.java)) {
                        val store = SettingsStore(app)
                        val repo = SettingsRepository(store)
                        return GeneralSettingsViewModel(app, repo) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
    }
}
