package com.chris.m3usuite.feature_tg_auth.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.BuildConfig
import com.chris.m3usuite.data.repo.TelegramAuthRepository
import com.chris.m3usuite.feature_tg_auth.data.TgAuthOrchestrator
import com.chris.m3usuite.feature_tg_auth.di.TgAuthModule
import com.chris.m3usuite.feature_tg_auth.domain.TgAuthAction
import com.chris.m3usuite.feature_tg_auth.domain.TgAuthError
import com.chris.m3usuite.feature_tg_auth.domain.TgAuthState
import com.chris.m3usuite.prefs.SettingsStore
import java.lang.ref.WeakReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * ViewModel binding `TgAuthOrchestrator` to Compose and exposing persistent input state.
 */
class TgAuthViewModel(
    private val app: Application,
    private val store: SettingsStore
) : ViewModel() {

    enum class KeysStatus { Missing, BuildConfig, Custom }

    private val _apiIdInput = MutableStateFlow("")
    val apiIdInput: StateFlow<String> = _apiIdInput.asStateFlow()

    private val _apiHashInput = MutableStateFlow("")
    val apiHashInput: StateFlow<String> = _apiHashInput.asStateFlow()

    private val _keysStatus = MutableStateFlow(KeysStatus.Missing)
    val keysStatus: StateFlow<KeysStatus> = _keysStatus.asStateFlow()

    private val _hasKeys = MutableStateFlow(false)
    val hasKeys: StateFlow<Boolean> = _hasKeys.asStateFlow()

    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone.asStateFlow()

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _useCurrentDevice = MutableStateFlow(false)
    val useCurrentDevice: StateFlow<Boolean> = _useCurrentDevice.asStateFlow()

    private val _authState = MutableStateFlow<TgAuthState>(TgAuthState.Unauthenticated)
    val authState: StateFlow<TgAuthState> = _authState.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _qrLink = MutableStateFlow<String?>(null)
    val qrLink: StateFlow<String?> = _qrLink.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    private val _orchestratorToken = MutableStateFlow(0)
    val orchestratorToken: StateFlow<Int> = _orchestratorToken.asStateFlow()

    private var repository: TelegramAuthRepository? = null
    private var orchestrator: TgAuthOrchestrator? = null
    private var orchestratorCollectors: Job? = null
    private var currentKeys: Pair<Int, String>? = null
    private var lastSuggestedCode: String? = null
    private var consentBindings: ConsentBindings? = null

    private data class ConsentBindings(
        val activity: WeakReference<Activity>,
        val lifecycleOwner: WeakReference<LifecycleOwner>,
        val launcher: ActivityResultLauncher<Intent>
    )

    init {
        viewModelScope.launch {
            combine(store.tgApiId, store.tgApiHash) { id, hash -> id to hash }
                .collectLatest { (id, hash) ->
                    _apiIdInput.value = if (id > 0) id.toString() else ""
                    _apiHashInput.value = hash
                    updateKeys(id, hash)
                }
        }
    }

    private fun updateKeys(storeId: Int, storeHash: String) {
        val effectiveId = if (storeId > 0) storeId else BuildConfig.TG_API_ID
        val effectiveHash = if (storeHash.isNotBlank()) storeHash else BuildConfig.TG_API_HASH
        val hasValid = effectiveId > 0 && effectiveHash.isNotBlank()
        _hasKeys.value = hasValid
        _keysStatus.value = when {
            storeId > 0 || storeHash.isNotBlank() -> KeysStatus.Custom
            hasValid -> KeysStatus.BuildConfig
            else -> KeysStatus.Missing
        }
        if (!hasValid) {
            clearOrchestrator()
            return
        }
        val newKeys = effectiveId to effectiveHash
        if (currentKeys == newKeys && orchestrator != null) return
        setOrchestrator(newKeys)
    }

    private fun setOrchestrator(keys: Pair<Int, String>) {
        orchestratorCollectors?.cancel()
        orchestrator?.dispose()
        repository?.unbindService()

        val repo = TelegramAuthRepository(app.applicationContext, keys.first, keys.second)
        repo.bindService()
        repository = repo
        val orchestrator = TgAuthModule.provideOrchestrator(app.applicationContext, repo)
        this.orchestrator = orchestrator
        currentKeys = keys

        orchestratorCollectors = viewModelScope.launch {
            launch {
                orchestrator.state.collect { state ->
                    _authState.value = state
                    handleStateSideEffects(state)
                }
            }
            launch {
                orchestrator.busy.collect { busy -> _busy.value = busy }
            }
            launch {
                orchestrator.errors.collect { error ->
                    emitError(error)
                }
            }
        }
        viewModelScope.launch { orchestrator.start() }
        reattachConsentIfPossible()
        _orchestratorToken.value = _orchestratorToken.value + 1
    }

    private fun handleStateSideEffects(state: TgAuthState) {
        when (state) {
            is TgAuthState.WaitCode -> {
                val suggestion = state.suggestedCode
                if (!suggestion.isNullOrBlank() && (_code.value.isBlank() || suggestion == lastSuggestedCode)) {
                    _code.value = suggestion
                    lastSuggestedCode = suggestion
                }
                _qrLink.value = null
            }
            is TgAuthState.WaitPhone -> {
                _code.value = ""
                _password.value = ""
                lastSuggestedCode = null
                _qrLink.value = null
            }
            is TgAuthState.WaitPassword -> {
                _password.value = ""
                _qrLink.value = null
            }
            is TgAuthState.Qr -> {
                _qrLink.value = state.link
            }
            TgAuthState.Ready -> {
                _qrLink.value = null
            }
            TgAuthState.LoggingOut -> {
                _qrLink.value = null
            }
            TgAuthState.Unauthenticated -> {
                _qrLink.value = null
            }
        }
    }

    private fun clearOrchestrator() {
        orchestratorCollectors?.cancel()
        orchestratorCollectors = null
        orchestrator?.dispose()
        orchestrator = null
        repository?.unbindService()
        repository = null
        currentKeys = null
        _authState.value = TgAuthState.Unauthenticated
        _busy.value = false
        _qrLink.value = null
    }

    fun attachConsent(activity: Activity, lifecycleOwner: LifecycleOwner, launcher: ActivityResultLauncher<Intent>) {
        consentBindings = ConsentBindings(WeakReference(activity), WeakReference(lifecycleOwner), launcher)
        orchestrator?.attach(activity, lifecycleOwner, launcher)
    }

    fun detachConsent() {
        consentBindings = null
        orchestrator?.detach()
    }

    fun handleConsentResult(result: ActivityResult) {
        orchestrator?.handleConsentResult(result)
    }

    fun handleConsentCanceled() {
        orchestrator?.handleConsentCanceled()
    }

    private fun reattachConsentIfPossible() {
        val bindings = consentBindings ?: return
        val activity = bindings.activity.get() ?: return
        val owner = bindings.lifecycleOwner.get() ?: return
        orchestrator?.attach(activity, owner, bindings.launcher)
    }

    fun onPhoneChange(value: String) {
        _phone.value = value
    }

    fun onCodeChange(value: String) {
        _code.value = value
    }

    fun onPasswordChange(value: String) {
        _password.value = value
    }

    fun onUseCurrentDeviceChange(value: Boolean) {
        _useCurrentDevice.value = value
    }

    fun submitPhone() {
        if (!ensureReadyForAuth()) return
        val phone = _phone.value.trim()
        if (phone.isBlank()) {
            emitSnackbar("Bitte eine Telefonnummer eingeben.")
            return
        }
        orchestrator?.dispatch(TgAuthAction.EnterPhone(phone, _useCurrentDevice.value))
    }

    fun submitCode() {
        if (!ensureReadyForAuth()) return
        val currentState = _authState.value
        if (currentState !is TgAuthState.WaitCode) {
            emitSnackbar("Bitte zuerst einen Code anfordern.")
            return
        }
        val code = _code.value.trim()
        if (code.isBlank()) {
            emitSnackbar("Bitte den Bestätigungscode eingeben.")
            return
        }
        orchestrator?.dispatch(TgAuthAction.EnterCode(code))
    }

    fun resendCode() {
        if (!ensureReadyForAuth()) return
        orchestrator?.dispatch(TgAuthAction.ResendCode)
    }

    fun submitPassword() {
        if (!ensureReadyForAuth()) return
        val currentState = _authState.value
        if (currentState !is TgAuthState.WaitPassword) {
            emitSnackbar("Derzeit wird kein Passwort benötigt.")
            return
        }
        val password = _password.value
        if (password.isBlank()) {
            emitSnackbar("Bitte das Zwei-Faktor-Passwort eingeben.")
            return
        }
        orchestrator?.dispatch(TgAuthAction.EnterPassword(password))
    }

    fun requestQr() {
        if (!ensureReadyForAuth()) return
        orchestrator?.dispatch(TgAuthAction.RequestQr)
    }

    fun cancelAuth() {
        if (!ensureReadyForAuth()) return
        orchestrator?.dispatch(TgAuthAction.Cancel)
    }

    fun onApiIdChange(value: String) {
        _apiIdInput.value = value.filter { it.isDigit() }
    }

    fun onApiHashChange(value: String) {
        _apiHashInput.value = value.trim()
    }

    fun saveApiCredentials() {
        viewModelScope.launch {
            val idValue = _apiIdInput.value.trim()
            val id = idValue.toIntOrNull()
            if (id == null || id <= 0) {
                emitSnackbar("API-ID muss eine positive Zahl sein.")
                return@launch
            }
            val hash = _apiHashInput.value.trim()
            if (hash.isBlank()) {
                emitSnackbar("API-Hash darf nicht leer sein.")
                return@launch
            }
            store.setTgApiId(id)
            store.setTgApiHash(hash)
            emitSnackbar("API-Schlüssel gespeichert.")
        }
    }

    fun clearApiCredentials() {
        viewModelScope.launch {
            store.setTgApiId(0)
            store.setTgApiHash("")
            emitSnackbar("API-Schlüssel zurückgesetzt.")
        }
    }

    fun setInBackground(isInBackground: Boolean) {
        repository?.setInBackground(isInBackground)
    }

    private fun ensureReadyForAuth(): Boolean {
        if (!_hasKeys.value) {
            emitSnackbar("Bitte zuerst API-ID und Hash hinterlegen.")
            return false
        }
        if (orchestrator == null) {
            emitSnackbar("Telegram-Anmeldung ist noch nicht bereit.")
            return false
        }
        return true
    }

    private fun emitError(error: TgAuthError) {
        emitSnackbar(error.userMessage)
    }

    private fun emitSnackbar(message: String) {
        viewModelScope.launch { _snackbar.emit(message) }
    }

    override fun onCleared() {
        super.onCleared()
        clearOrchestrator()
        consentBindings = null
    }

    companion object {
        fun Factory(app: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(TgAuthViewModel::class.java)) {
                    val store = SettingsStore(app.applicationContext)
                    return TgAuthViewModel(app, store) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
