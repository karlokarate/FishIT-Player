
package com.chris.m3usuite.ui.models.telegram

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.service.TelegramServiceClient
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

/** Lightweight UI projection of a Telegram chat. */
data class ChatUi(val id: Long, val title: String)

/** Aggregated UI state for Telegram block. */
data class TelegramUiState(
    val enabled: Boolean = false,
    val auth: TelegramServiceClient.AuthState = TelegramServiceClient.AuthState.Idle,
    val resendLeftSec: Int = 0,
    val selected: List<ChatUi> = emptyList(),
    val isResolvingChats: Boolean = false,
    val isSyncRunning: Boolean = false,
    val logDir: String = "",
    val httpLogLevel: Int = 1
)

sealed interface TelegramIntent {
    data class RequestCode(val phone: String) : TelegramIntent
    data class SubmitCode(val code: String) : TelegramIntent
    data class SubmitPassword(val password: String) : TelegramIntent
    data object ResendCode : TelegramIntent
    data class ConfirmChats(val ids: Set<Long>) : TelegramIntent
    data class SetLogDir(val treeUri: Uri) : TelegramIntent
    data object StartFullSync : TelegramIntent
    data class SetEnabled(val value: Boolean) : TelegramIntent
    data class SetHttpLogLevel(val level: Int) : TelegramIntent
    data object RequestQr : TelegramIntent
}

sealed interface TelegramEffect {
    data class Snackbar(val message: String) : TelegramEffect
}

class TelegramSettingsViewModel(
    private val app: Application,
    private val store: SettingsStore,
    private val tg: TelegramServiceClient,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _state = MutableStateFlow(TelegramUiState())
    val state: StateFlow<TelegramUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TelegramEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<TelegramEffect> = _effects.asSharedFlow()

    private val selectedIdsFlow = store.tgSelectedChatsCsv
        .map { csv ->
            csv.split(',')
                .mapNotNull { it.trim().toLongOrNull() }
                .toSet()
        }
        .distinctUntilChanged()

    init {
        runCatching { tg.bind() }
        observeStore()
        observeAuthSignals()
    }

    private fun observeStore() {
        viewModelScope.launch {
            combine(
                store.tgEnabled,
                selectedIdsFlow,
                store.logDirTreeUri,
                store.tgLogVerbosity
            ) { values: Array<Any?> ->
                val enabled = values[0] as Boolean
                @Suppress("UNCHECKED_CAST")
                val ids = values[1] as Set<Long>
                val dir = values[2] as String
                val lvl = values[3] as Int
                Quad(enabled, ids, dir, lvl)
            }.collectLatest { (enabled, ids, dir, lvl) ->
                _state.update { it.copy(enabled = enabled, logDir = dir, httpLogLevel = lvl.coerceIn(0, 5)) }
                if (ids.isEmpty()) {
                    _state.update { it.copy(selected = emptyList(), isResolvingChats = false) }
                } else {
                    _state.update { it.copy(isResolvingChats = true) }
                    val chats = resolveChatNames(ids)
                    _state.update { it.copy(selected = chats, isResolvingChats = false) }
                }
            }
        }
    }

    private fun observeAuthSignals() {
        viewModelScope.launch {
            tg.authState.collectLatest { auth ->
                _state.update { it.copy(auth = auth) }
            }
        }
        viewModelScope.launch {
            tg.authEvents.collectLatest { event ->
                when (event) {
                    is TelegramServiceClient.AuthEvent.CodeSent -> {
                        _state.update { it.copy(auth = TelegramServiceClient.AuthState.CodeSent) }
                    }
                    is TelegramServiceClient.AuthEvent.PasswordRequired -> {
                        _state.update { it.copy(auth = TelegramServiceClient.AuthState.PasswordRequired) }
                    }
                    is TelegramServiceClient.AuthEvent.SignedIn -> {
                        _state.update { it.copy(auth = TelegramServiceClient.AuthState.SignedIn) }
                    }
                    is TelegramServiceClient.AuthEvent.Error -> {
                        _state.update { it.copy(auth = TelegramServiceClient.AuthState.Error) }
                        _effects.tryEmit(TelegramEffect.Snackbar(event.userMessage))
                    }
                }
            }
        }
        viewModelScope.launch {
            tg.resendInSec.collectLatest { left ->
                _state.update { it.copy(resendLeftSec = left) }
            }
        }
    }

    fun onIntent(intent: TelegramIntent) {
        viewModelScope.launch {
            when (intent) {
                is TelegramIntent.RequestCode -> runCatching { tg.requestCode(intent.phone) }
                is TelegramIntent.SubmitCode -> runCatching { tg.submitCode(intent.code) }
                is TelegramIntent.SubmitPassword -> runCatching { tg.submitPassword(intent.password) }
                is TelegramIntent.ResendCode -> runCatching { tg.resendCode() }
                is TelegramIntent.ConfirmChats -> confirmChats(intent.ids)
                is TelegramIntent.SetLogDir -> setLogDir(intent.treeUri)
                is TelegramIntent.StartFullSync -> startFullSync()
                is TelegramIntent.SetEnabled -> setEnabled(intent.value)
                is TelegramIntent.SetHttpLogLevel -> setHttpLogLevel(intent.level)
                TelegramIntent.RequestQr -> runCatching { tg.requestQr() }
            }
        }
    }

    private suspend fun confirmChats(ids: Set<Long>) {
        val csv = ids.asSequence().map { it.toString() }.sorted().joinToString(",")
        withContext(io) { store.setTelegramSelectedChatsCsv(csv) }
        if (ids.isEmpty()) {
            _state.update { it.copy(selected = emptyList(), isResolvingChats = false) }
        } else {
            _state.update { it.copy(isResolvingChats = true) }
            val chats = resolveChatNames(ids)
            _state.update { it.copy(selected = chats, isResolvingChats = false) }
        }
    }

    private suspend fun setLogDir(uri: Uri) {
        tg.persistTreePermission(uri)
        withContext(io) { store.setLogDirTreeUri(uri.toString()) }
        _state.update { it.copy(logDir = uri.toString()) }
        _effects.emit(TelegramEffect.Snackbar("Log-Verzeichnis gespeichert."))
    }

    private suspend fun startFullSync() {
        _state.update { it.copy(isSyncRunning = true) }
        val result = runCatching {
            withContext(io) {
                com.chris.m3usuite.work.TelegramSyncWorker.scheduleNow(
                    app.applicationContext,
                    mode = com.chris.m3usuite.work.TelegramSyncWorker.MODE_ALL,
                    refreshHome = true
                )
            }
        }
        _state.update { it.copy(isSyncRunning = false) }
        result.onSuccess { _effects.emit(TelegramEffect.Snackbar("Telegram-Sync gestartet")) }
            .onFailure { _effects.emit(TelegramEffect.Snackbar("Telegram-Sync fehlgeschlagen: ${it.message ?: "Unbekannt"}")) }
    }

    private suspend fun setEnabled(value: Boolean) {
        withContext(io) { store.setTgEnabled(value) }
        _state.update { it.copy(enabled = value) }
        // Apply runtime options again after enable
        runCatching { tg.applyAllSettings() }
    }

    private suspend fun setHttpLogLevel(level: Int) {
        val norm = level.coerceIn(0, 5)
        withContext(io) { store.setTgLogVerbosity(norm) }
        _state.update { it.copy(httpLogLevel = norm) }
        runCatching { tg.setLogVerbosity(norm) }
    }

    /** Returns a list of chats for a given list tag ("main", "archive", "folder:ID"). */
    suspend fun listChats(list: String, query: String? = null, limit: Int = 200): List<ChatUi> {
        val pairs = tg.listChats(list, limit, query)
        return pairs.map { (id, title) -> ChatUi(id, title) }
    }

    /** Returns all custom folder IDs (for folder selection chips). */
    suspend fun listFolders(): IntArray = tg.listFolders()

    suspend fun resolveChatNames(ids: Set<Long>): List<ChatUi> = withContext(io) {
        if (ids.isEmpty()) return@withContext emptyList()
        val resolved = runCatching { tg.resolveChatTitles(ids.toLongArray()) }.getOrNull().orEmpty()
        val nameMap = resolved.toMap()
        ids.map { id ->
            val title = nameMap[id] ?: runCatching { tg.resolveChatTitle(id) }.getOrNull() ?: id.toString()
            ChatUi(id, title)
        }.sortedBy { it.title.lowercase() }
    }

    fun optimizeStorage() {
        runCatching { tg.optimizeStorage() }
        _effects.tryEmit(TelegramEffect.Snackbar("Speicher-Optimierung angestoßen"))
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { tg.unbind() }
    }

    companion object {
        fun Factory(app: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(TelegramSettingsViewModel::class.java)) {
                    val store = SettingsStore(app.applicationContext)
                    val service = TelegramServiceClient(app.applicationContext)
                    return TelegramSettingsViewModel(app, store, service) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}

/** Simple value class for combine of 4 values (no standard Quad in Kotlin). */
private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
