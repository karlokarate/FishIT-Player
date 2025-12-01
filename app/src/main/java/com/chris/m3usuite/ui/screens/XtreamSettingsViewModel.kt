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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    /**
     * BUG 4 fix: Debounce job for saving settings.
     * Prevents saving on every keystroke; waits 500ms after last change.
     */
    private var saveJob: Job? = null

    /**
     * BUG 4 fix: Debounce delay in milliseconds.
     */
    private val saveDebounceMs = 500L

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

    /**
     * BUG 4 fix: Debounced settings change handler.
     *
     * Instead of saving immediately on every keystroke, this method:
     * 1. Updates local state immediately (for responsive UI)
     * 2. Cancels any pending save job
     * 3. Schedules a new save after [saveDebounceMs] delay
     *
     * This prevents race conditions where auto-import starts with partial config.
     */
    fun onChange(
        host: String? = null,
        port: Int? = null,
        user: String? = null,
        pass: String? = null,
        output: String? = null,
    ) {
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

        // Cancel any pending save and schedule a new one
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(saveDebounceMs)
            save(
                com.chris.m3usuite.domain.usecases
                    .XtreamPrefs(s.host, s.port, s.user, s.pass, s.output),
            )
            _state.value = _state.value.copy(isSaving = false)
        }
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
