package com.chris.m3usuite.ui.screens

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.service.TelegramServiceClient
import com.chris.m3usuite.work.TelegramSyncWorker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Simple UI projection of a Telegram chat. */
data class ChatUi(val id: Long, val title: String)

/** Aggregated UI state for Telegram-specific preferences. */
data class SettingsUiState(
    val tgEnabled: Boolean = false,
    val authState: TelegramServiceClient.AuthState = TelegramServiceClient.AuthState.Idle,
    val resendLeft: Int = 0,
    val selectedChats: List<ChatUi> = emptyList(),
    val isResolvingChats: Boolean = false,
    val isSyncRunning: Boolean = false,
    val logDir: String = ""
)

sealed interface SettingsIntent {
    data class RequestCode(val phone: String) : SettingsIntent
    data class SubmitCode(val code: String) : SettingsIntent
    data class SubmitPassword(val password: String) : SettingsIntent
    data object ResendCode : SettingsIntent
    data class ConfirmChats(val ids: Set<Long>) : SettingsIntent
    data class SetLogDir(val treeUri: Uri) : SettingsIntent
    data object StartFullSync : SettingsIntent
}

sealed interface SettingsEffect {
    data class Snackbar(val message: String) : SettingsEffect
}

class SettingsViewModel(
    private val app: Application,
    private val store: SettingsStore,
    private val tg: TelegramServiceClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<SettingsEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<SettingsEffect> = _effects.asSharedFlow()

    private val selectedIdsFlow = store.tgSelectedChatsCsv
        .map { csv ->
            csv.split(',')
                .mapNotNull { it.trim().toLongOrNull() }
                .toSet()
        }
        .distinctUntilChanged()

    init {
        runCatching { tg.bind() }
        observePreferences()
        observeAuthSignals()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            combine(
                store.tgEnabled,
                selectedIdsFlow,
                store.logDirTreeUri
            ) { enabled, ids, dir -> Triple(enabled, ids, dir) }
                .collectLatest { (enabled, ids, dir) ->
                    _state.update { it.copy(tgEnabled = enabled, logDir = dir) }
                    if (ids.isEmpty()) {
                        _state.update { it.copy(selectedChats = emptyList(), isResolvingChats = false) }
                    } else {
                        _state.update { it.copy(isResolvingChats = true) }
                        val chats = resolveChatNames(ids)
                        _state.update { it.copy(selectedChats = chats, isResolvingChats = false) }
                    }
                }
        }
    }

    private fun observeAuthSignals() {
        viewModelScope.launch {
            tg.authState.collectLatest { auth ->
                _state.update { it.copy(authState = auth) }
            }
        }
        viewModelScope.launch {
            tg.authEvents.collectLatest { event ->
                when (event) {
                    is TelegramServiceClient.AuthEvent.CodeSent ->
                        _state.update { it.copy(authState = TelegramServiceClient.AuthState.CodeSent) }

                    is TelegramServiceClient.AuthEvent.Error ->
                        _state.update { it.copy(authState = TelegramServiceClient.AuthState.Error) }

                    is TelegramServiceClient.AuthEvent.PasswordRequired ->
                        _state.update { it.copy(authState = TelegramServiceClient.AuthState.PasswordRequired) }

                    is TelegramServiceClient.AuthEvent.SignedIn ->
                        _state.update { it.copy(authState = TelegramServiceClient.AuthState.SignedIn) }
                }
            }
        }
        viewModelScope.launch {
            tg.resendInSec.collectLatest { left ->
                _state.update { it.copy(resendLeft = left) }
            }
        }
    }

    fun onIntent(intent: SettingsIntent) {
        viewModelScope.launch {
            when (intent) {
                is SettingsIntent.RequestCode -> runCatching { tg.requestCode(intent.phone) }
                is SettingsIntent.SubmitCode -> runCatching { tg.submitCode(intent.code) }
                is SettingsIntent.SubmitPassword -> runCatching { tg.submitPassword(intent.password) }
                SettingsIntent.ResendCode -> runCatching { tg.resendCode() }
                is SettingsIntent.ConfirmChats -> handleConfirmChats(intent.ids)
                is SettingsIntent.SetLogDir -> handleSetLogDir(intent.treeUri)
                SettingsIntent.StartFullSync -> handleStartFullSync()
            }
        }
    }

    private suspend fun handleConfirmChats(ids: Set<Long>) {
        val csv = ids.asSequence().map { it.toString() }.sorted().joinToString(",")
        withContext(ioDispatcher) { store.setTelegramSelectedChatsCsv(csv) }
        if (ids.isEmpty()) {
            _state.update { it.copy(selectedChats = emptyList(), isResolvingChats = false) }
        } else {
            _state.update { it.copy(isResolvingChats = true) }
            val chats = resolveChatNames(ids)
            _state.update { it.copy(selectedChats = chats, isResolvingChats = false) }
        }
    }

    private suspend fun handleSetLogDir(uri: Uri) {
        tg.persistTreePermission(uri)
        withContext(ioDispatcher) { store.setLogDirTreeUri(uri.toString()) }
        _state.update { it.copy(logDir = uri.toString()) }
    }

    private suspend fun handleStartFullSync() {
        _state.update { it.copy(isSyncRunning = true) }
        val result = runCatching {
            withContext(ioDispatcher) {
                TelegramSyncWorker.scheduleNow(
                    app.applicationContext,
                    mode = TelegramSyncWorker.MODE_ALL,
                    refreshHome = true
                )
            }
        }
        _state.update { it.copy(isSyncRunning = false) }
        result.fold(
            onSuccess = {
                _effects.emit(SettingsEffect.Snackbar("Telegram Sync gestartet."))
            },
            onFailure = { error ->
                Log.w("SettingsViewModel", "Failed to trigger Telegram sync", error)
                _effects.emit(
                    SettingsEffect.Snackbar(
                        "Telegram Sync fehlgeschlagen: ${error.message ?: "Unbekannter Fehler"}"
                    )
                )
            }
        )
    }

    private suspend fun resolveChatNames(ids: Set<Long>): List<ChatUi> = withContext(ioDispatcher) {
        if (ids.isEmpty()) return@withContext emptyList()
        val resolved = runCatching { tg.resolveChatTitles(ids.toLongArray()) }.getOrNull().orEmpty()
        val nameMap = resolved.toMap()
        ids.map { id ->
            val title = nameMap[id] ?: runCatching { tg.resolveChatTitle(id) }.getOrNull() ?: id.toString()
            ChatUi(id, title)
        }.sortedBy { it.title.lowercase() }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { tg.unbind() }
    }

    companion object {
        fun provideFactory(
            application: Application,
            store: SettingsStore
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                    val service = TelegramServiceClient(application.applicationContext)
                    return SettingsViewModel(application, store, service) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
