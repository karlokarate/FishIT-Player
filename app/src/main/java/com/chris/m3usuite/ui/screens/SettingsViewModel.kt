package com.chris.m3usuite.ui.screens

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.BuildConfig
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
    val logDir: String = "",
    val overrideApiId: Int = 0,
    val overrideApiHash: String = "",
    val effectiveApiId: Int = BuildConfig.TG_API_ID,
    val effectiveApiHash: String = BuildConfig.TG_API_HASH,
    val buildApiId: Int = BuildConfig.TG_API_ID,
    val buildApiHash: String = BuildConfig.TG_API_HASH,
    val apiKeysMissing: Boolean = BuildConfig.TG_API_ID <= 0 || BuildConfig.TG_API_HASH.isBlank()
)

sealed interface SettingsIntent {
    data class RequestCode(val phone: String) : SettingsIntent
    data class SubmitCode(val code: String) : SettingsIntent
    data class SubmitPassword(val password: String) : SettingsIntent
    data object ResendCode : SettingsIntent
    data class ConfirmChats(val ids: Set<Long>) : SettingsIntent
    data class SetLogDir(val treeUri: Uri) : SettingsIntent
    data object StartFullSync : SettingsIntent
    data class SetEnabled(val value: Boolean) : SettingsIntent
    data class SetApiId(val value: String) : SettingsIntent
    data class SetApiHash(val value: String) : SettingsIntent
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
    private val buildApiId = BuildConfig.TG_API_ID
    private val buildApiHash = BuildConfig.TG_API_HASH

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
        _state.update {
            it.copy(
                buildApiId = buildApiId,
                buildApiHash = buildApiHash,
                effectiveApiId = effectiveId(it.overrideApiId),
                effectiveApiHash = effectiveHash(it.overrideApiHash),
                apiKeysMissing = areKeysMissing(it.overrideApiId, it.overrideApiHash)
            )
        }
        observePreferences()
        observeAuthSignals()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            combine(
                store.tgEnabled,
                selectedIdsFlow,
                store.logDirTreeUri,
                store.tgApiId,
                store.tgApiHash
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                TelegramPrefs(
                    enabled = values[0] as Boolean,
                    selectedIds = values[1] as Set<Long>,
                    logDir = values[2] as String,
                    overrideApiId = values[3] as Int,
                    overrideApiHash = values[4] as String
                )
            }.collectLatest { prefs ->
                    _state.update {
                        it.copy(
                            tgEnabled = prefs.enabled,
                            logDir = prefs.logDir,
                            overrideApiId = prefs.overrideApiId,
                            overrideApiHash = prefs.overrideApiHash,
                            effectiveApiId = effectiveId(prefs.overrideApiId),
                            effectiveApiHash = effectiveHash(prefs.overrideApiHash),
                            apiKeysMissing = areKeysMissing(prefs.overrideApiId, prefs.overrideApiHash)
                        )
                    }
                    if (prefs.selectedIds.isEmpty()) {
                        _state.update { it.copy(selectedChats = emptyList(), isResolvingChats = false) }
                    } else {
                        _state.update { it.copy(isResolvingChats = true) }
                        val chats = resolveChatNames(prefs.selectedIds)
                        _state.update { it.copy(selectedChats = chats, isResolvingChats = false) }
                    }
                    if (prefs.enabled) ensureClientStarted()
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
                is SettingsIntent.RequestCode -> {
                    ensureClientStarted()
                    runCatching { tg.requestCode(intent.phone) }
                }
                is SettingsIntent.SubmitCode -> {
                    ensureClientStarted()
                    runCatching { tg.submitCode(intent.code) }
                }
                is SettingsIntent.SubmitPassword -> {
                    ensureClientStarted()
                    runCatching { tg.submitPassword(intent.password) }
                }
                SettingsIntent.ResendCode -> runCatching { tg.resendCode() }
                is SettingsIntent.ConfirmChats -> handleConfirmChats(intent.ids)
                is SettingsIntent.SetLogDir -> handleSetLogDir(intent.treeUri)
                SettingsIntent.StartFullSync -> handleStartFullSync()
                is SettingsIntent.SetEnabled -> handleSetEnabled(intent.value)
                is SettingsIntent.SetApiId -> handleSetApiId(intent.value)
                is SettingsIntent.SetApiHash -> handleSetApiHash(intent.value)
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

    private suspend fun handleSetEnabled(value: Boolean) {
        withContext(ioDispatcher) { store.setTelegramEnabled(value) }
        _state.update { it.copy(tgEnabled = value) }
        if (value) ensureClientStarted() else runCatching { tg.logout() }
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

    private suspend fun handleSetApiId(raw: String) {
        val trimmed = raw.trim()
        val parsed = trimmed.toIntOrNull() ?: 0
        withContext(ioDispatcher) { store.setTelegramApiId(parsed) }
        _state.update {
            val effHash = effectiveHash(it.overrideApiHash)
            val effId = effectiveId(parsed)
            it.copy(
                overrideApiId = parsed,
                effectiveApiId = effId,
                effectiveApiHash = effHash,
                apiKeysMissing = areKeysMissing(parsed, it.overrideApiHash)
            )
        }
        ensureClientStarted(parsed, state.value.overrideApiHash)
    }

    private suspend fun handleSetApiHash(raw: String) {
        val trimmed = raw.trim()
        withContext(ioDispatcher) { store.setTelegramApiHash(trimmed) }
        _state.update {
            val effId = effectiveId(it.overrideApiId)
            val effHash = effectiveHash(trimmed)
            it.copy(
                overrideApiHash = trimmed,
                effectiveApiId = effId,
                effectiveApiHash = effHash,
                apiKeysMissing = areKeysMissing(it.overrideApiId, trimmed)
            )
        }
        ensureClientStarted(state.value.overrideApiId, trimmed)
    }

    private fun effectiveId(overrideId: Int): Int = if (overrideId > 0) overrideId else buildApiId

    private fun effectiveHash(overrideHash: String): String = if (overrideHash.isNotBlank()) overrideHash else buildApiHash

    private fun areKeysMissing(overrideId: Int, overrideHash: String): Boolean {
        val effId = effectiveId(overrideId)
        val effHash = effectiveHash(overrideHash)
        return effId <= 0 || effHash.isBlank()
    }

    private suspend fun ensureClientStarted(overrideId: Int? = null, overrideHash: String? = null) {
        val snapshot = state.value
        if (!snapshot.tgEnabled) return
        val effId = effectiveId(overrideId ?: snapshot.overrideApiId)
        val effHash = effectiveHash(overrideHash ?: snapshot.overrideApiHash)
        if (effId > 0 && effHash.isNotBlank()) {
            runCatching {
                tg.start(effId, effHash)
                tg.getAuth()
            }.onFailure {
                _effects.emit(SettingsEffect.Snackbar("TDLib-Start fehlgeschlagen: ${it.message ?: "Unbekannt"}"))
            }
        } else {
            _effects.emit(SettingsEffect.Snackbar("API-Keys fehlen – ID und HASH setzen."))
        }
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

    private data class TelegramPrefs(
        val enabled: Boolean,
        val selectedIds: Set<Long>,
        val logDir: String,
        val overrideApiId: Int,
        val overrideApiHash: String
    )

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
