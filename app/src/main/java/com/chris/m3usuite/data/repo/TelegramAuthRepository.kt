package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.telegram.TdLibReflection
import com.chris.m3usuite.telegram.service.TelegramServiceClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren

/**
 * Repository to manage Telegram authentication using reflection-based TDLib bridge.
 * Requires TDLib native/libs present at runtime. Otherwise, it remains inactive.
 */
class TelegramAuthRepository(private val context: Context, private val apiId: Int, private val apiHash: String) {
    private val _authState = MutableStateFlow(TdLibReflection.AuthState.UNKNOWN)
    val authState: StateFlow<TdLibReflection.AuthState> get() = _authState
    private val _errors = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 16)
    val errors: kotlinx.coroutines.flow.Flow<String> get() = _errors

    private var client: TdLibReflection.ClientHandle? = null
    private var svc: TelegramServiceClient? = null
    private var useService: Boolean = true
    private val _qr = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 8)
    val qrLinks: kotlinx.coroutines.flow.Flow<String> get() = _qr
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)

    fun isAvailable(): Boolean = TdLibReflection.available()

    fun hasValidKeys(): Boolean = apiId > 0 && apiHash.isNotBlank()

    /** Bind the background TDLib service so it can receive commands/push even before explicit start(). */
    fun bindService() {
        if (!useService) return
        if (svc == null) {
            svc = TelegramServiceClient(context.applicationContext).also { s ->
                s.bind()
                // Bridge service auth states into local flow
                scope.launch(Dispatchers.Main.immediate) {
                    s.authStates().collect { st ->
                        _authState.value = runCatching { TdLibReflection.AuthState.valueOf(st) }.getOrDefault(TdLibReflection.AuthState.UNKNOWN)
                    }
                }
                // Bridge service errors into local flow
                scope.launch(Dispatchers.Main.immediate) {
                    s.errors().collect { em -> _errors.tryEmit(em) }
                }
                // QR links from service
                scope.launch(Dispatchers.Main.immediate) { s.qrLinks().collect { link -> _qr.tryEmit(link) } }
            }
        }
    }

    /** Unbind the background TDLib service to release resources when UI leaves Settings. */
    fun unbindService() {
        // Mark background before unbind so TDLib can lower activity
        runCatching { svc?.setInBackground(true) }
        svc?.unbind()
        svc = null
        // Cancel any running collectors bound to this repo scope
        try { job.cancelChildren() } catch (_: Throwable) {}
    }

    /** Explicitly inform TDLib about app background/foreground state when service is bound. */
    fun setInBackground(isInBackground: Boolean) {
        runCatching { svc?.setInBackground(isInBackground) }
    }

    /** Initialize client/service and push TdlibParameters. */
    fun start(): Boolean {
        if (!isAvailable()) return false
        if (!hasValidKeys()) return false
        if (useService) {
            if (svc == null) {
                svc = TelegramServiceClient(context.applicationContext).also { s ->
                    s.bind()
                    // Bridge service auth states into local flow
                    scope.launch(Dispatchers.Main.immediate) {
                        s.authStates().collect { st ->
                            _authState.value = runCatching { TdLibReflection.AuthState.valueOf(st) }.getOrDefault(TdLibReflection.AuthState.UNKNOWN)
                        }
                    }
                    // Bridge service errors into local flow
                    scope.launch(Dispatchers.Main.immediate) {
                        s.errors().collect { em -> _errors.tryEmit(em) }
                    }
                    // QR links from service
                    scope.launch(Dispatchers.Main.immediate) { s.qrLinks().collect { link -> _qr.tryEmit(link) } }
                }
            }
            svc?.start(apiId, apiHash)
            return true
        }
        if (client == null) client = TdLibReflection.createClient(_authState)
        val params = TdLibReflection.buildTdlibParameters(context, apiId, apiHash) ?: return false
        client?.let { TdLibReflection.sendSetTdlibParameters(it, params) }
        client?.let { TdLibReflection.sendCheckDatabaseEncryptionKey(it) }
        return true
    }

    fun sendPhoneNumber(phone: String) {
        if (useService) { svc?.sendPhone(phone); return }
        val c = client ?: return
        TdLibReflection.sendSetPhoneNumber(c, phone)
    }

    fun sendCode(code: String) {
        if (useService) { svc?.sendCode(code); return }
        val c = client ?: return
        TdLibReflection.sendCheckCode(c, code)
    }

    fun checkDbKey() {
        if (useService) return // handled inside service
        val c = client ?: return
        TdLibReflection.sendCheckDatabaseEncryptionKey(c)
    }

    fun sendPassword(password: String) {
        if (useService) { svc?.sendPassword(password); return }
        val c = client ?: return
        TdLibReflection.sendCheckPassword(c, password)
    }

    fun requestQrLogin() {
        if (useService) { svc?.requestQr(); return }
        val c = client ?: return
        TdLibReflection.sendRequestQrCodeAuthentication(c)
    }

    fun logout() {
        if (useService) { svc?.logout(); return }
        val c = client ?: return
        TdLibReflection.sendLogOut(c)
    }
}
