
package com.chris.m3usuite.ui.models.telegram

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.BuildConfig
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.service.TelegramServiceClient
import com.chris.m3usuite.tg.TgGate
import com.chris.m3usuite.work.SchedulingGateway
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lightweight UI projection of a Telegram chat. */
data class ChatUi(val id: Long, val title: String)

@Immutable
data class TelegramSyncProgressUi(
    val processedChats: Int,
    val totalChats: Int
)

/** Aggregated UI state for Telegram block. */
data class TelegramUiState(
    val enabled: Boolean = false,
    val auth: TelegramServiceClient.AuthState = TelegramServiceClient.AuthState.Idle,
    val resendLeftSec: Int = 0,
    val selected: List<ChatUi> = emptyList(),
    val isResolvingChats: Boolean = false,
    val isSyncRunning: Boolean = false,
    val syncProgress: TelegramSyncProgressUi? = null,
    val logDir: String = "",
    val httpLogLevel: Int = 1,
    val versionInfo: TdlibVersionUi? = null,
    val overrideApiId: Int = 0,
    val overrideApiHash: String = "",
    val effectiveApiId: Int = BuildConfig.TG_API_ID,
    val effectiveApiHash: String = BuildConfig.TG_API_HASH,
    val buildApiId: Int = BuildConfig.TG_API_ID,
    val buildApiHash: String = BuildConfig.TG_API_HASH,
    val apiKeysMissing: Boolean = BuildConfig.TG_API_ID <= 0 || BuildConfig.TG_API_HASH.isBlank()
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
    data object ProbeVersion : TelegramIntent
    data class SetApiId(val value: String) : TelegramIntent
    data class SetApiHash(val value: String) : TelegramIntent
}

sealed interface TelegramEffect {
    data class Snackbar(val message: String) : TelegramEffect
    data class ShowQr(val link: String) : TelegramEffect
}

@Immutable
data class TdlibVersionUi(
    val version: String?,
    val versionType: String?,
    val versionError: String?,
    val nativeVersion: String?,
    val nativeVersionType: String?,
    val nativeError: String?,
    val commit: String?,
    val commitType: String?,
    val commitError: String?,
    val fetchedAtMillis: Long
)

class TelegramSettingsViewModel(
    private val app: Application,
    private val store: SettingsStore,
    private val tg: TelegramServiceClient,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val buildApiId = BuildConfig.TG_API_ID
    private val buildApiHash = BuildConfig.TG_API_HASH

    private val _state = MutableStateFlow(TelegramUiState())
    val state: StateFlow<TelegramUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TelegramEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<TelegramEffect> = _effects.asSharedFlow()

    /** Ausgewählte Chats direkt typisiert aus dem SettingsStore (IOC/DataStore, kein OBX) */
    private val selectedIdsFlow = store.tgSelectedChatIds

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
        observeStore()
        observeAuthSignals()
        observeQrLinks()
        observeErrors()
        observeSyncState()
    }

    private fun observeStore() {
        viewModelScope.launch {
            combine(
                store.tgEnabled,
                selectedIdsFlow,
                store.logDirTreeUri,
                store.tgLogVerbosity,
                store.tgApiId,
                store.tgApiHash
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                TelegramPrefs(
                    enabled = values[0] as Boolean,
                    selectedIds = values[1] as Set<Long>,
                    logDir = values[2] as String,
                    logVerbosity = values[3] as Int,
                    overrideApiId = values[4] as Int,
                    overrideApiHash = values[5] as String
                )
            }.collectLatest { prefs ->
                _state.update {
                    it.copy(
                        enabled = prefs.enabled,
                        logDir = prefs.logDir,
                        httpLogLevel = prefs.logVerbosity.coerceIn(0, 5),
                        overrideApiId = prefs.overrideApiId,
                        overrideApiHash = prefs.overrideApiHash,
                        effectiveApiId = effectiveId(prefs.overrideApiId),
                        effectiveApiHash = effectiveHash(prefs.overrideApiHash),
                        apiKeysMissing = areKeysMissing(prefs.overrideApiId, prefs.overrideApiHash)
                    )
                }
                if (prefs.selectedIds.isEmpty()) {
                    _state.update { it.copy(selected = emptyList(), isResolvingChats = false) }
                } else {
                    _state.update { it.copy(isResolvingChats = true) }
                    val chats = resolveChatNames(prefs.selectedIds)
                    _state.update { it.copy(selected = chats, isResolvingChats = false) }
                }
                if (prefs.enabled) ensureClientStarted()
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

    private fun observeQrLinks() {
        viewModelScope.launch {
            tg.qrLinks().collectLatest { link ->
                if (link.isNotBlank()) {
                    _effects.emit(TelegramEffect.ShowQr(link))
                }
            }
        }
    }

    private fun observeErrors() {
        viewModelScope.launch {
            tg.errors().collectLatest { error ->
                val message = error.message.ifBlank { error.rawMessage ?: "Unbekannter Fehler" }
                _effects.emit(TelegramEffect.Snackbar("Telegram: $message"))
            }
        }
    }

    private fun observeSyncState() {
        viewModelScope.launch {
            SchedulingGateway.telegramSyncState.collectLatest { sync ->
                when (sync) {
                    SchedulingGateway.TelegramSyncState.Idle -> {
                        _state.update { it.copy(isSyncRunning = false, syncProgress = null) }
                    }
                    is SchedulingGateway.TelegramSyncState.Running -> {
                        val processed = sync.processedChats.coerceAtLeast(0)
                        val total = sync.totalChats.coerceAtLeast(0)
                        _state.update {
                            it.copy(
                                isSyncRunning = true,
                                syncProgress = TelegramSyncProgressUi(processedChats = processed, totalChats = total)
                            )
                        }
                    }
                    is SchedulingGateway.TelegramSyncState.Success -> {
                        _state.update { it.copy(isSyncRunning = false, syncProgress = null) }
                        _effects.emit(TelegramEffect.Snackbar(buildSyncSuccessMessage(sync.result)))
                        SchedulingGateway.acknowledgeTelegramSync()
                    }
                    is SchedulingGateway.TelegramSyncState.Failure -> {
                        _state.update { it.copy(isSyncRunning = false, syncProgress = null) }
                        _effects.emit(TelegramEffect.Snackbar("Telegram-Sync fehlgeschlagen: ${sync.error}"))
                        SchedulingGateway.acknowledgeTelegramSync()
                    }
                }
            }
        }
    }

    fun onIntent(intent: TelegramIntent) {
        viewModelScope.launch {
            when (intent) {
                is TelegramIntent.RequestCode -> {
                    val phone = intent.phone.trim()
                    if (phone.isBlank()) {
                        showSnackbar("Bitte Telefonnummer eingeben.")
                        return@launch
                    }
                    if (!ensureClientStarted()) return@launch
                    runTelegramAction(
                        purpose = "Code anfordern",
                        successMessage = "Code angefordert – SMS/Telegram prüfen.",
                        retries = 1
                    ) {
                        tg.requestCode(phone)
                    }
                }
                is TelegramIntent.SubmitCode -> {
                    val code = intent.code.trim()
                    if (code.isBlank()) {
                        showSnackbar("Bitte den Bestätigungscode eingeben.")
                        return@launch
                    }
                    if (!ensureClientStarted()) return@launch
                    runTelegramAction(
                        purpose = "Code senden",
                        successMessage = "Code gesendet.",
                        retries = 1
                    ) {
                        tg.submitCode(code)
                    }
                }
                is TelegramIntent.SubmitPassword -> {
                    val password = intent.password
                    if (password.isBlank()) {
                        showSnackbar("Bitte das 2FA-Passwort eingeben.")
                        return@launch
                    }
                    if (!ensureClientStarted()) return@launch
                    runTelegramAction(
                        purpose = "Passwort senden",
                        successMessage = "Passwort gesendet.",
                        retries = 1
                    ) {
                        tg.submitPassword(password)
                    }
                }
                TelegramIntent.ResendCode -> {
                    val left = _state.value.resendLeftSec
                    if (left > 0) {
                        showSnackbar("Bitte noch ${left}s warten, bevor der Code erneut angefordert wird.")
                        return@launch
                    }
                    if (!ensureClientStarted()) return@launch
                    runTelegramAction(
                        purpose = "Code erneut senden",
                        successMessage = "Code erneut angefordert.",
                        retries = 1
                    ) {
                        tg.resendCode()
                    }
                }
                is TelegramIntent.ConfirmChats -> confirmChats(intent.ids)
                is TelegramIntent.SetLogDir -> setLogDir(intent.treeUri)
                is TelegramIntent.StartFullSync -> startFullSync()
                is TelegramIntent.SetEnabled -> setEnabled(intent.value)
                is TelegramIntent.SetHttpLogLevel -> setHttpLogLevel(intent.level)
                TelegramIntent.RequestQr -> {
                    if (!ensureClientStarted()) return@launch
                    runTelegramAction(purpose = "QR-Login anfordern") {
                        tg.requestQr()
                    }
                }
                TelegramIntent.ProbeVersion -> probeVersion()
                is TelegramIntent.SetApiId -> setApiId(intent.value)
                is TelegramIntent.SetApiHash -> setApiHash(intent.value)
            }
        }
    }

    private suspend fun runTelegramAction(
        purpose: String,
        successMessage: String? = null,
        retries: Int = 0,
        block: suspend () -> Unit
    ) {
        var attempt = 0
        var lastError: Throwable? = null
        val maxAttempts = (retries + 1).coerceAtLeast(1)
        while (attempt < maxAttempts) {
            attempt++
            val result = runCatching { block() }
            if (result.isSuccess) {
                if (!successMessage.isNullOrBlank()) {
                    showSnackbar(successMessage)
                }
                return
            }
            lastError = result.exceptionOrNull()
        }
        val detail = lastError?.message?.takeIf { it.isNotBlank() } ?: "Unbekannter Fehler"
        showSnackbar("$purpose fehlgeschlagen: $detail")
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
            val enabled = _state.value.enabled
            when {
                !enabled -> {
                    _effects.emit(
                        TelegramEffect.Snackbar("Telegram ist deaktiviert – Sync startet nach Aktivierung automatisch.")
                    )
                }
                TgGate.mirrorOnly() -> {
                    _effects.emit(TelegramEffect.Snackbar("Telegram-Indexer ist deaktiviert (Mirror-Only)."))
                }
                else -> {
                    SchedulingGateway.scheduleTelegramSync(
                        app.applicationContext,
                        com.chris.m3usuite.work.TelegramSyncWorker.MODE_ALL,
                        refreshHome = true
                    )
                    _effects.emit(TelegramEffect.Snackbar("Telegram-Sync gestartet."))
                }
            }
        }
    }

    private suspend fun setLogDir(uri: Uri) {
        tg.persistTreePermission(uri)
        withContext(io) { store.setLogDirTreeUri(uri.toString()) }
        _state.update { it.copy(logDir = uri.toString()) }
        _effects.emit(TelegramEffect.Snackbar("Log-Verzeichnis gespeichert."))
    }

    private suspend fun startFullSync() {
        if (TgGate.mirrorOnly()) {
            _effects.emit(TelegramEffect.Snackbar("Telegram-Indexer ist deaktiviert (Mirror-Only)."))
            return
        }
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
        if (value) ensureClientStarted() else runCatching { tg.logout() }
    }

    private suspend fun setHttpLogLevel(level: Int) {
        val norm = level.coerceIn(0, 5)
        withContext(io) { store.setTgLogVerbosity(norm) }
        _state.update { it.copy(httpLogLevel = norm) }
        if (_state.value.enabled) ensureClientStarted()
        runCatching { tg.setLogVerbosity(norm) }
            .onFailure { _effects.emit(TelegramEffect.Snackbar("Log-Level konnte nicht gesetzt werden: ${it.message ?: "Unbekannt"}")) }
    }

    private suspend fun setApiId(raw: String) {
        val trimmed = raw.trim()
        val parsed = trimmed.toIntOrNull() ?: 0
        withContext(io) { store.setTelegramApiId(parsed) }
        runCatching { tg.applyAllSettings() }
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

    private suspend fun setApiHash(raw: String) {
        val trimmed = raw.trim()
        withContext(io) { store.setTelegramApiHash(trimmed) }
        runCatching { tg.applyAllSettings() }
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

    private suspend fun ensureClientStarted(overrideId: Int? = null, overrideHash: String? = null): Boolean {
        val snapshot = state.value
        if (!snapshot.enabled) {
            showSnackbar("Telegram ist deaktiviert.")
            return false
        }
        val effId = effectiveId(overrideId ?: snapshot.overrideApiId)
        val effHash = effectiveHash(overrideHash ?: snapshot.overrideApiHash)
        if (effId <= 0 || effHash.isBlank()) {
            showSnackbar("API-Keys fehlen – ID und HASH setzen.")
            return false
        }
        return runCatching {
            tg.start(effId, effHash)
            tg.getAuth()
        }.onFailure {
            showSnackbar("TDLib-Start fehlgeschlagen: ${it.message ?: "Unbekannt"}")
        }.isSuccess
    }

    private suspend fun probeVersion() {
        val snapshotResult = runCatching { tg.fetchTdlibVersion() }
        val snapshot = snapshotResult.getOrNull()
        if (snapshot == null) {
            val message = "TDLib-Version konnte nicht abgefragt werden: ${snapshotResult.exceptionOrNull()?.message ?: "Unbekannter Fehler"}"
            _effects.emit(TelegramEffect.Snackbar(message))
            _state.update {
                it.copy(
                    versionInfo = TdlibVersionUi(
                        version = null,
                        versionType = null,
                        versionError = message,
                        nativeVersion = null,
                        nativeVersionType = null,
                        nativeError = message,
                        commit = null,
                        commitType = null,
                        commitError = message,
                        fetchedAtMillis = System.currentTimeMillis()
                    )
                )
            }
            return
        }
        val info = TdlibVersionUi(
            version = snapshot.version,
            versionType = snapshot.versionType,
            versionError = snapshot.versionError,
            nativeVersion = snapshot.tdlibVersion,
            nativeVersionType = snapshot.tdlibVersionType,
            nativeError = snapshot.tdlibVersionError,
            commit = snapshot.commitHash,
            commitType = snapshot.commitType,
            commitError = snapshot.commitError,
            fetchedAtMillis = System.currentTimeMillis()
        )
        _state.update { it.copy(versionInfo = info) }
        _effects.emit(TelegramEffect.Snackbar(buildVersionSummary(info)))
    }

    private fun buildVersionSummary(info: TdlibVersionUi): String {
        val appVersion = info.version ?: info.versionError ?: "n/a"
        val native = info.nativeVersion ?: info.nativeError ?: "n/a"
        val commit = info.commit ?: info.commitError ?: "-"
        return "TDLib-Version: $appVersion • Native: $native • Commit: $commit"
    }

    private fun buildSyncSuccessMessage(result: SchedulingGateway.TelegramSyncResult): String {
        val parts = mutableListOf<String>()
        if (result.moviesAdded > 0) parts += "${result.moviesAdded} Filme"
        if (result.seriesAdded > 0) parts += "${result.seriesAdded} Serien"
        if (result.episodesAdded > 0) parts += "${result.episodesAdded} Episoden"
        val detail = if (parts.isEmpty()) "keine neuen Inhalte" else parts.joinToString(", ")
        return "Telegram-Sync abgeschlossen – $detail"
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

    private suspend fun showSnackbar(message: String) {
        _effects.emit(TelegramEffect.Snackbar(message))
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

private data class TelegramPrefs(
    val enabled: Boolean,
    val selectedIds: Set<Long>,
    val logDir: String,
    val logVerbosity: Int,
    val overrideApiId: Int,
    val overrideApiHash: String
)
