package com.chris.m3usuite.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.BuildConfig
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.service.TelegramServiceClient
import com.chris.m3usuite.work.TelegramSyncWorker
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class TelegramSettingsViewModel(
    application: Application,
    private val store: SettingsStore
) : AndroidViewModel(application) {

    data class UiState(
        val enabled: Boolean = false,
        val selectedChats: Set<Long> = emptySet(),
        val resolvedSelectionLabel: String? = null,
        val isResolvingSelection: Boolean = false,
        val resendSeconds: Int = 0
    )

    sealed interface Effect {
        data class Snackbar(val message: String) : Effect
    }

    private val serviceClient = TelegramServiceClient(application)
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()
    private val currentApiId = AtomicReference(BuildConfig.TG_API_ID)
    private val currentApiHash = AtomicReference(BuildConfig.TG_API_HASH)

    private var resolveJob: kotlinx.coroutines.Job? = null

    init {
        serviceClient.bind()
        viewModelScope.launch {
            store.tgEnabled.collect { enabled ->
                _uiState.update { it.copy(enabled = enabled) }
                triggerSelectionResolve(enabled, _uiState.value.selectedChats)
            }
        }
        viewModelScope.launch {
            store.tgSelectedChatsCsv.collect { csv ->
                val ids = csv.split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()
                _uiState.update { it.copy(selectedChats = ids) }
                triggerSelectionResolve(_uiState.value.enabled, ids)
            }
        }
        viewModelScope.launch {
            store.tgApiId.collect { override ->
                val resolved = if (override > 0) override else BuildConfig.TG_API_ID
                currentApiId.set(resolved)
            }
        }
        viewModelScope.launch {
            store.tgApiHash.collect { override ->
                val resolved = if (override.isNotBlank()) override else BuildConfig.TG_API_HASH
                currentApiHash.set(resolved)
            }
        }
        viewModelScope.launch {
            serviceClient.resendInSec.collect { seconds ->
                _uiState.update { it.copy(resendSeconds = seconds) }
            }
        }
    }

    private fun triggerSelectionResolve(enabled: Boolean, ids: Set<Long>) {
        resolveJob?.cancel()
        if (!enabled || ids.isEmpty()) {
            _uiState.update { it.copy(resolvedSelectionLabel = null, isResolvingSelection = false) }
            return
        }
        resolveJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isResolvingSelection = true) }
            val label = resolveChatNames(ids)
            _uiState.update { it.copy(resolvedSelectionLabel = label, isResolvingSelection = false) }
        }
    }

    private suspend fun resolveChatNames(ids: Set<Long>): String? = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext null
        val sorted = ids.sorted()
        runCatching {
            ensureClientReady()
            val ready = withTimeoutOrNull(5_000) {
                serviceClient.awaitAuthorized()
                true
            } ?: false
            if (!ready) return@runCatching sorted.joinToString(", ") { it.toString() }
            val resolved = serviceClient.resolveChatTitles(sorted.toLongArray())
            if (resolved.isNotEmpty()) {
                resolved.joinToString(", ") { it.second }
            } else {
                sorted.joinToString(", ") { it.toString() }
            }
        }.getOrElse {
            sorted.joinToString(", ") { it.toString() }
        }
    }

    private suspend fun ensureClientReady() {
        val apiId = currentApiId.get()
        val apiHash = currentApiHash.get()
        if (apiId <= 0 || apiHash.isBlank()) return
        serviceClient.start(apiId, apiHash)
        serviceClient.getAuth()
    }

    fun onIntent(intent: Intent) {
        when (intent) {
            is Intent.ConfirmChats -> viewModelScope.launch {
                val csv = intent.chatIds.sorted().joinToString(",")
                store.setTelegramSelectedChatsCsv(csv)
                scheduleSync()
                _effects.send(Effect.Snackbar("Telegram Sync gestartet"))
            }
            Intent.RequestSync -> viewModelScope.launch { scheduleSync() }
            is Intent.Snackbar -> viewModelScope.launch { _effects.send(Effect.Snackbar(intent.message)) }
        }
    }

    private suspend fun scheduleSync() {
        TelegramSyncWorker.scheduleNow(
            getApplication(),
            mode = TelegramSyncWorker.MODE_ALL,
            refreshHome = true
        )
    }

    override fun onCleared() {
        super.onCleared()
        resolveJob?.cancel()
        serviceClient.unbind()
    }

    sealed interface Intent {
        data class ConfirmChats(val chatIds: Set<Long>) : Intent
        object RequestSync : Intent
        data class Snackbar(val message: String) : Intent
    }

    class Factory(
        private val application: Application,
        private val store: SettingsStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TelegramSettingsViewModel::class.java)) {
                return TelegramSettingsViewModel(application, store) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
