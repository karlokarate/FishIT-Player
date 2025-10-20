package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.telegram.PhoneNumberSanitizer
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
import java.util.Locale

/**
 * Repository to manage Telegram authentication using reflection-based TDLib bridge.
 * Requires TDLib native/libs present at runtime. Otherwise, it remains inactive.
 */
class TelegramAuthRepository(private val context: Context, private val apiId: Int, private val apiHash: String) {
    data class AuthError(
        val message: String,
        val code: Int? = null,
        val rawMessage: String? = null,
        val type: String? = null
    )

    private val _authState = MutableStateFlow(TdLibReflection.AuthState.UNKNOWN)
    val authState: StateFlow<TdLibReflection.AuthState> get() = _authState
    private val _errors = kotlinx.coroutines.flow.MutableSharedFlow<AuthError>(replay = 0, extraBufferCapacity = 16)
    val errors: kotlinx.coroutines.flow.Flow<AuthError> get() = _errors
    private val _authEvents = kotlinx.coroutines.flow.MutableSharedFlow<com.chris.m3usuite.telegram.service.TelegramServiceClient.AuthEvent>(replay = 0, extraBufferCapacity = 16)
    val authEvents: kotlinx.coroutines.flow.Flow<com.chris.m3usuite.telegram.service.TelegramServiceClient.AuthEvent> get() = _authEvents
    private val _resendSeconds = MutableStateFlow(0)
    val resendSeconds: StateFlow<Int> get() = _resendSeconds

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
                // Request current auth state immediately
                runCatching { s.getAuth() }
                // Bridge service auth states into local flow
                scope.launch(Dispatchers.Main.immediate) {
                    s.authStates().collect { st ->
                        _authState.value = runCatching { TdLibReflection.AuthState.valueOf(st) }.getOrDefault(TdLibReflection.AuthState.UNKNOWN)
                    }
                }
                // Bridge service errors into local flow
                scope.launch(Dispatchers.Main.immediate) {
                    s.errors().collect { em ->
                        _errors.tryEmit(AuthError(em.message, em.code, em.rawMessage, em.type))
                    }
                }
                // QR links from service
                scope.launch(Dispatchers.Main.immediate) { s.qrLinks().collect { link -> _qr.tryEmit(link) } }
                scope.launch(Dispatchers.Main.immediate) {
                    s.authEvents.collect { event -> _authEvents.tryEmit(event) }
                }
                scope.launch(Dispatchers.Main.immediate) {
                    s.resendInSec.collect { secs -> _resendSeconds.value = secs }
                }
            }
        }
    }

    /** Unbind the background TDLib service to release resources when UI leaves Settings. */
    fun unbindService() {
        // Mark background before unbind so TDLib can lower activity
        runCatching { svc?.setInBackground(true) }
        svc?.unbind()
        svc = null
        _resendSeconds.value = 0
        // Cancel any running collectors bound to this repo scope
        try { job.cancelChildren() } catch (_: Throwable) {}
    }

    /** Explicitly inform TDLib about app background/foreground state when service is bound. */
    fun setInBackground(isInBackground: Boolean) {
        runCatching { svc?.setInBackground(isInBackground) }
    }

    fun setPreferIpv6(enabled: Boolean) {
        if (useService) {
            svc?.setPreferIpv6(enabled)
        } else {
            client?.let { TdLibReflection.sendSetOptionBoolean(it, "prefer_ipv6", enabled) }
        }
    }

    fun setStayOnline(enabled: Boolean) {
        if (useService) {
            svc?.setStayOnline(enabled)
        } else {
            client?.let { TdLibReflection.sendSetOptionBoolean(it, "online", enabled) }
        }
    }

    fun setLogVerbosity(level: Int) {
        TdLibReflection.setLogVerbosityLevel(level)
        if (useService) {
            svc?.setLogVerbosity(level)
        } else {
            client?.let { TdLibReflection.sendSetLogVerbosityLevel(it, level) }
        }
    }

    fun setStorageOptimizer(enabled: Boolean) {
        if (useService) {
            svc?.setStorageOptimizer(enabled)
        } else {
            client?.let { TdLibReflection.sendSetOptionBoolean(it, "use_storage_optimizer", enabled) }
        }
    }

    fun setIgnoreFileNames(enabled: Boolean) {
        if (useService) {
            svc?.setIgnoreFileNames(enabled)
        } else {
            client?.let { TdLibReflection.sendSetOptionBoolean(it, "ignore_file_names", enabled) }
        }
    }

    fun applyProxy(type: String, host: String, port: Int, username: String, password: String, secret: String, enabled: Boolean) {
        if (useService) {
            svc?.applyProxy(type, host, port, username, password, secret, enabled)
        } else {
            val clientHandle = client ?: return
            scope.launch(Dispatchers.IO) {
                TdLibReflection.configureProxy(
                    clientHandle,
                    TdLibReflection.ProxyConfig(
                        kind = when (type.lowercase(Locale.getDefault())) {
                            "socks", "socks5" -> TdLibReflection.ProxyKind.SOCKS5
                            "http", "https" -> TdLibReflection.ProxyKind.HTTP
                            "mtproto", "mtproxy" -> TdLibReflection.ProxyKind.MTPROTO
                            else -> TdLibReflection.ProxyKind.NONE
                        },
                        host = host,
                        port = port,
                        username = username,
                        password = password,
                        secret = secret,
                        enabled = enabled
                    )
                )
            }
        }
    }

    fun disableProxy() {
        if (useService) {
            svc?.disableProxy()
        } else {
            client?.let { handle -> scope.launch(Dispatchers.IO) { TdLibReflection.disableProxy(handle) } }
        }
    }

    fun setAutoDownload(type: String, enabled: Boolean, preloadLarge: Boolean, preloadNext: Boolean, preloadStories: Boolean, lessDataCalls: Boolean) {
        if (useService) {
            svc?.setAutoDownload(type, enabled, preloadLarge, preloadNext, preloadStories, lessDataCalls)
        } else {
            val clientHandle = client ?: return
            scope.launch(Dispatchers.IO) {
                val network = when (type.lowercase(Locale.getDefault())) {
                    "mobile" -> TdLibReflection.AutoDownloadNetwork.MOBILE
                    "roaming" -> TdLibReflection.AutoDownloadNetwork.ROAMING
                    else -> TdLibReflection.AutoDownloadNetwork.WIFI
                }
                val presets = TdLibReflection.fetchAutoDownloadSettingsPresets(clientHandle) ?: return@launch
                val base = when (network) {
                    TdLibReflection.AutoDownloadNetwork.WIFI -> presets.wifi
                    TdLibReflection.AutoDownloadNetwork.MOBILE -> presets.mobile
                    TdLibReflection.AutoDownloadNetwork.ROAMING -> presets.roaming
                }
                val updated = base.copy(
                    isAutoDownloadEnabled = enabled,
                    preloadLargeVideos = preloadLarge,
                    preloadNextAudio = preloadNext,
                    preloadStories = preloadStories,
                    useLessDataForCalls = lessDataCalls
                )
                TdLibReflection.sendSetAutoDownloadSettings(clientHandle, updated, network)
            }
        }
    }

    fun optimizeStorage() {
        if (useService) {
            svc?.optimizeStorage()
        } else {
            client?.let { handle -> scope.launch(Dispatchers.IO) { TdLibReflection.sendOptimizeStorage(handle) } }
        }
    }

    fun applyAllRuntimeSettings() {
        if (useService) {
            svc?.applyAllSettings()
        } else {
            // No background service; options are applied lazily when the direct client is used.
        }
    }

    /** Initialize client/service and push TdlibParameters. */
    fun start(): Boolean {
        if (!isAvailable()) return false
        if (!hasValidKeys()) return false
        if (useService) {
            if (svc == null) {
                svc = TelegramServiceClient(context.applicationContext).also { s ->
                    s.bind()
                    runCatching { s.getAuth() }
                    // Bridge service auth states into local flow
                    scope.launch(Dispatchers.Main.immediate) {
                        s.authStates().collect { st ->
                            _authState.value = runCatching { TdLibReflection.AuthState.valueOf(st) }.getOrDefault(TdLibReflection.AuthState.UNKNOWN)
                        }
                    }
                    // Bridge service errors into local flow
                    scope.launch(Dispatchers.Main.immediate) {
                        s.errors().collect { em ->
                            _errors.tryEmit(AuthError(em.message, em.code, em.rawMessage, em.type))
                        }
                    }
                    // QR links from service
                    scope.launch(Dispatchers.Main.immediate) { s.qrLinks().collect { link -> _qr.tryEmit(link) } }
                    scope.launch(Dispatchers.Main.immediate) {
                        s.authEvents.collect { event -> _authEvents.tryEmit(event) }
                    }
                    scope.launch(Dispatchers.Main.immediate) {
                        s.resendInSec.collect { secs -> _resendSeconds.value = secs }
                    }
                }
            }
            svc?.start(apiId, apiHash)
            svc?.getAuth()
            return true
        }
        if (client == null) client = TdLibReflection.createClient(_authState)
        val params = TdLibReflection.buildTdlibParameters(context, apiId, apiHash) ?: return false
        client?.let { TdLibReflection.sendSetTdlibParameters(it, params) }
        client?.let { TdLibReflection.sendCheckDatabaseEncryptionKey(it) }
        return true
    }

    fun requestAuthState() {
        if (useService) { svc?.getAuth(); return }
    }

    fun sendPhoneNumber(phone: String, isCurrentDevice: Boolean) {
        val sanitized = PhoneNumberSanitizer.sanitize(context, phone)
        if (sanitized.isBlank()) {
            scope.launch(Dispatchers.Main.immediate) {
                _errors.emit(AuthError("Ungültige Telefonnummer – bitte internationale Vorwahl angeben."))
            }
            return
        }
        _resendSeconds.value = 0
        if (useService) {
            svc?.sendPhone(
                phone = sanitized,
                isCurrentDevice = isCurrentDevice,
                allowFlashCall = false,
                allowMissedCall = false,
                allowSmsRetriever = true
            )
            svc?.getAuth()
            return
        }
        val c = client ?: return
        TdLibReflection.sendSetPhoneNumber(c, sanitized, TdLibReflection.PhoneAuthSettings(isCurrentPhoneNumber = isCurrentDevice))
        TdLibReflection.sendGetAuthorizationState(c)
    }

    fun sendCode(code: String) {
        if (useService) { svc?.sendCode(code); return }
        val c = client ?: return
        TdLibReflection.sendCheckCode(c, code)
    }

    fun resendCode() {
        if (useService) {
            svc?.resendCode()
            return
        }
        val c = client ?: return
        TdLibReflection.sendResendAuthenticationCode(c)
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
