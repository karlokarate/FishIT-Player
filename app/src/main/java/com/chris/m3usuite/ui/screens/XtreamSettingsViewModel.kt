package com.chris.m3usuite.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.data.repo.SettingsRepository
import com.chris.m3usuite.domain.usecases.SaveXtreamPrefs
import com.chris.m3usuite.domain.usecases.TriggerXtreamDeltaImport
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class XtreamSettingsState(
    val host: String = "",
    val port: Int = 80,
    val user: String = "",
    val pass: String = "",
    val output: String = "m3u8",
    val isSaving: Boolean = false,
    val isImportInFlight: Boolean = false,
)

class XtreamSettingsViewModel(
    app: Application,
    private val repo: SettingsRepository,
    private val save: SaveXtreamPrefs,
    private val triggerImport: TriggerXtreamDeltaImport,
) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(XtreamSettingsState())
    val state: StateFlow<XtreamSettingsState> = _state

    init {
        observe()
    }

    private fun observe() {
        viewModelScope.launch {
            combine(
                repo.xtHost,
                repo.xtPort,
                repo.xtUser,
                repo.xtPass,
                repo.xtOutput,
            ) { values: Array<Any?> ->
                XtreamSettingsState(
                    host = values[0] as String,
                    port = values[1] as Int,
                    user = values[2] as String,
                    pass = values[3] as String,
                    output = values[4] as String,
                )
            }.collect { _state.value = it }
        }
    }

    fun onChange(
        host: String? = null,
        port: Int? = null,
        user: String? = null,
        pass: String? = null,
        output: String? = null,
    ) = viewModelScope.launch {
        val cur = _state.value
        val s =
            cur.copy(
                host = host ?: cur.host,
                port = port ?: cur.port,
                user = user ?: cur.user,
                pass = pass ?: cur.pass,
                output = output ?: cur.output,
                isSaving = true,
            )
        _state.value = s
        save(
            com.chris.m3usuite.domain.usecases
                .XtreamPrefs(s.host, s.port, s.user, s.pass, s.output),
        )
        _state.value = s.copy(isSaving = false)
    }

    fun onTriggerDeltaImport(
        includeLive: Boolean,
        vodLimit: Int = 0,
        seriesLimit: Int = 0,
    ) = viewModelScope.launch {
        _state.value = _state.value.copy(isImportInFlight = true)
        runCatching { triggerImport(includeLive, vodLimit, seriesLimit) }
        _state.value = _state.value.copy(isImportInFlight = false)
    }

    /** Navigation-Event wird im Screen behandelt, öffnet `XtreamPortalCheckScreen`. */
    fun onOpenPortalCookieBridge(onNavigate: () -> Unit) = onNavigate()

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val store = SettingsStore(app)
                    val repo = SettingsRepository(store)
                    val save = SaveXtreamPrefs(repo)
                    val trigger = TriggerXtreamDeltaImport(app)
                    @Suppress("UNCHECKED_CAST")
                    return XtreamSettingsViewModel(app, repo, save, trigger) as T
                }
            }
        }
    }
}
