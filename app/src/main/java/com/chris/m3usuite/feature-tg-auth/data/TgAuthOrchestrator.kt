package com.chris.m3usuite.feature_tg_auth.data

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.chris.m3usuite.data.repo.TelegramAuthRepository
import com.chris.m3usuite.feature_tg_auth.domain.TgAuthAction
import com.chris.m3usuite.feature_tg_auth.domain.TgAuthError
import com.chris.m3usuite.feature_tg_auth.domain.TgAuthState
import com.chris.m3usuite.telegram.TdLibReflection
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TgAuthOrchestrator(
    private val repository: TelegramAuthRepository,
    private val smsConsentManager: TgSmsConsentManager,
    private val errorMapper: TgErrorMapper,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<TgAuthState>(TgAuthState.Unauthenticated)
    val state: StateFlow<TgAuthState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<TgAuthError>(extraBufferCapacity = 8)
    val errors = _errors.asSharedFlow()

    private var lastQrLink: String? = null
    private var lastAuthState: TdLibReflection.AuthState? = null
    private var resendAvailableAtMillis: Long? = null
    private var started = false

    init {
        scope.launch {
            repository.authState.collectLatest { auth ->
                lastAuthState = auth
                updateState(mapState(auth))
            }
        }
        scope.launch {
            repository.errors.collectLatest { error ->
                val mapped = errorMapper.map(error)
                _errors.emit(mapped)
                handleErrorSideEffects(mapped)
            }
        }
        scope.launch {
            repository.qrLinks.collectLatest { link ->
                lastQrLink = link
                if (_state.value is TgAuthState.Qr) {
                    _state.value = TgAuthState.Qr(link)
                }
            }
        }
    }

    fun attach(activity: Activity, lifecycleOwner: LifecycleOwner, launcher: ActivityResultLauncher<Intent>) {
        smsConsentManager.attach(activity, launcher) { code ->
            scope.launch { dispatch(TgAuthAction.EnterCode(code)) }
            _state.update { current ->
                if (current is TgAuthState.WaitCode) current.copy(suggestedCode = code, lastError = null) else current
            }
        }
        lifecycleOwner.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    smsConsentManager.detach()
                    source.lifecycle.removeObserver(this)
                }
            }
        })
    }

    fun detach() {
        smsConsentManager.detach()
    }

    fun handleConsentResult(result: ActivityResult) {
        smsConsentManager.handleConsentResult(result)
    }

    fun handleConsentCanceled() {
        smsConsentManager.handleConsentCanceled()
    }

    suspend fun start(): Boolean {
        val ok = ensureStarted()
        if (ok) repository.requestAuthState()
        return ok
    }

    fun dispatch(action: TgAuthAction) {
        scope.launch {
            when (action) {
                is TgAuthAction.EnterPhone -> {
                    if (!ensureStarted()) return@launch
                    resendAvailableAtMillis = null
                    repository.sendPhoneNumber(action.phoneE164, action.useCurrentDevice)
                }
                is TgAuthAction.EnterCode -> {
                    if (!ensureStarted()) return@launch
                    _state.update { current ->
                        if (current is TgAuthState.WaitCode) current.copy(suggestedCode = action.code, lastError = null) else current
                    }
                    repository.sendCode(action.code)
                }
                is TgAuthAction.EnterPassword -> {
                    if (!ensureStarted()) return@launch
                    _state.update { current ->
                        if (current is TgAuthState.WaitPassword) current.copy(lastError = null) else current
                    }
                    repository.sendPassword(action.password)
                }
                TgAuthAction.ResendCode -> {
                    if (!ensureStarted()) return@launch
                    val cooldownUntil = clock() + DEFAULT_RESEND_COOLDOWN_MS
                    resendAvailableAtMillis = cooldownUntil
                    repository.resendCode()
                    _state.update { current ->
                        if (current is TgAuthState.WaitCode) current.copy(resendAvailableAtMillis = cooldownUntil, lastError = null) else current
                    }
                }
                TgAuthAction.Cancel -> {
                    repository.logout()
                    updateState(TgAuthState.LoggingOut)
                }
                TgAuthAction.RequestQr -> {
                    if (!ensureStarted()) return@launch
                    repository.requestQrLogin()
                }
            }
        }
    }

    fun dispose() {
        job.cancel()
    }

    private fun handleErrorSideEffects(error: TgAuthError) {
        when (error) {
            is TgAuthError.InvalidCode, TgAuthError.CodeExpired -> smsConsentManager.rearmWithJitter()
            is TgAuthError.FloodWait -> {
                val waitSeconds = error.retryDelaySeconds ?: 0
                if (waitSeconds > 0) {
                    val until = clock() + waitSeconds.seconds.inWholeMilliseconds
                    resendAvailableAtMillis = until
                    _state.update { current ->
                        if (current is TgAuthState.WaitCode) current.copy(resendAvailableAtMillis = until, lastError = error)
                        else current
                    }
                } else {
                    _state.update { current ->
                        if (current is TgAuthState.WaitCode) current.copy(lastError = error)
                        else current
                    }
                }
            }
            else -> {
                _state.update { current ->
                    when (current) {
                        is TgAuthState.WaitCode -> current.copy(lastError = error)
                        is TgAuthState.WaitPassword -> current.copy(lastError = error)
                        else -> current
                    }
                }
            }
        }
    }

    private suspend fun ensureStarted(): Boolean {
        if (started) return true
        val ok = repository.start()
        started = ok
        if (!ok) {
            _errors.emit(TgAuthError.Generic("TDLib konnte nicht gestartet werden."))
        } else {
            repository.requestAuthState()
        }
        return ok
    }

    private fun mapState(state: TdLibReflection.AuthState): TgAuthState = when (state) {
        TdLibReflection.AuthState.WAIT_FOR_CODE -> {
            val suggested = (_state.value as? TgAuthState.WaitCode)?.suggestedCode
            TgAuthState.WaitCode(resendAvailableAtMillis, suggestedCode = suggested, lastError = (_state.value as? TgAuthState.WaitCode)?.lastError)
        }
        TdLibReflection.AuthState.WAIT_FOR_PASSWORD -> {
            TgAuthState.WaitPassword(lastError = (_state.value as? TgAuthState.WaitPassword)?.lastError)
        }
        TdLibReflection.AuthState.WAIT_OTHER_DEVICE -> TgAuthState.Qr(lastQrLink)
        TdLibReflection.AuthState.AUTHENTICATED -> TgAuthState.Ready
        TdLibReflection.AuthState.LOGGING_OUT -> TgAuthState.LoggingOut
        TdLibReflection.AuthState.WAIT_FOR_NUMBER,
        TdLibReflection.AuthState.WAIT_ENCRYPTION_KEY,
        TdLibReflection.AuthState.UNAUTHENTICATED -> TgAuthState.WaitPhone
        else -> TgAuthState.Unauthenticated
    }

    private fun updateState(newState: TgAuthState) {
        _state.value = newState
        if (newState is TgAuthState.WaitCode) {
            smsConsentManager.startConsent()
        }
    }

    companion object {
        private const val DEFAULT_RESEND_COOLDOWN_MS = 30_000L
    }
}
