package com.chris.m3usuite.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.data.repo.SettingsRepository
import com.chris.m3usuite.domain.usecases.PlayerPrefs
import com.chris.m3usuite.domain.usecases.SavePlayerPrefs
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerSettingsState(
    val mode: String = "internal", // "ask" | "internal" | "external"
    val preferredPkg: String = "",
    val rotationLocked: Boolean = false,
    val autoplayNext: Boolean = false,
    val subScale: Float = 0.06f,
    val subFgArgb: Int = 0xE6FFFFFF.toInt(),
    val subBgArgb: Int = 0x66000000,
    val subFgOpacityPct: Int = 90,
    val subBgOpacityPct: Int = 40,
    val isSaving: Boolean = false,
)

class PlayerSettingsViewModel(
    app: Application,
    private val repo: SettingsRepository,
    private val save: SavePlayerPrefs,
) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(PlayerSettingsState())
    val state: StateFlow<PlayerSettingsState> = _state

    init {
        observe()
    }

    private fun observe() {
        viewModelScope.launch {
            combine(
                repo.playerMode,
                repo.preferredPlayerPkg,
                repo.rotationLocked,
                repo.autoplayNext,
                repo.subtitleScale,
                repo.subtitleFg,
                repo.subtitleBg,
                repo.subtitleFgOpacityPct,
                repo.subtitleBgOpacityPct,
            ) { values: Array<Any?> ->
                @Suppress("UNCHECKED_CAST")
                PlayerSettingsState(
                    mode = values[0] as String,
                    preferredPkg = values[1] as String,
                    rotationLocked = values[2] as Boolean,
                    autoplayNext = values[3] as Boolean,
                    subScale = values[4] as Float,
                    subFgArgb = values[5] as Int,
                    subBgArgb = values[6] as Int,
                    subFgOpacityPct = values[7] as Int,
                    subBgOpacityPct = values[8] as Int,
                    isSaving = false,
                )
            }.collect { _state.value = it }
        }
    }

    fun onChangeMode(mode: String) =
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, mode = mode) }
            save(_state.value.toPrefs().copy(mode = mode))
        }

    fun onChangePreferredPkg(pkg: String) =
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, preferredPkg = pkg) }
            save(_state.value.toPrefs().copy(preferredPkg = pkg))
        }

    fun onToggleRotation(locked: Boolean) =
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, rotationLocked = locked) }
            save(_state.value.toPrefs().copy(rotationLocked = locked))
        }

    fun onToggleAutoplay(next: Boolean) =
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, autoplayNext = next) }
            save(_state.value.toPrefs().copy(autoplayNext = next))
        }

    fun onChangeSubtitle(
        scale: Float? = null,
        fgArgb: Int? = null,
        bgArgb: Int? = null,
        fgPct: Int? = null,
        bgPct: Int? = null,
    ) = viewModelScope.launch {
        val s =
            _state.value.copy(
                subScale = scale ?: _state.value.subScale,
                subFgArgb = fgArgb ?: _state.value.subFgArgb,
                subBgArgb = bgArgb ?: _state.value.subBgArgb,
                subFgOpacityPct = (fgPct ?: _state.value.subFgOpacityPct).coerceIn(0, 100),
                subBgOpacityPct = (bgPct ?: _state.value.subBgOpacityPct).coerceIn(0, 100),
                isSaving = true,
            )
        _state.value = s
        save(s.toPrefs())
    }

    private fun PlayerSettingsState.toPrefs() =
        PlayerPrefs(
            mode = mode,
            preferredPkg = preferredPkg,
            rotationLocked = rotationLocked,
            autoplayNext = autoplayNext,
            subScale = subScale,
            subFgArgb = subFgArgb,
            subBgArgb = subBgArgb,
            subFgOpacityPct = subFgOpacityPct,
            subBgOpacityPct = subBgOpacityPct,
        )

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val store = SettingsStore(app)
                    val repo = SettingsRepository(store)
                    val save = SavePlayerPrefs(repo)
                    @Suppress("UNCHECKED_CAST")
                    return PlayerSettingsViewModel(app, repo, save) as T
                }
            }
        }
    }
}
