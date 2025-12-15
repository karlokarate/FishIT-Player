package com.fishit.player.infra.data.telegram.auth

import com.fishit.player.core.feature.auth.TelegramAuthRepository
import com.fishit.player.core.feature.auth.TelegramAuthState as DomainAuthState
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramAuthClient
import com.fishit.player.infra.transport.telegram.api.TdlibAuthState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Singleton
class TelegramAuthRepositoryImpl internal constructor(
    private val transport: TelegramAuthClient,
    private val scope: CoroutineScope,
) : TelegramAuthRepository {

    @Inject
    constructor(transport: TelegramAuthClient) : this(
        transport = transport,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private companion object {
        private const val TAG = "TelegramAuthRepo"
    }

    private val _authState = MutableStateFlow<DomainAuthState>(DomainAuthState.Idle)
    override val authState: StateFlow<DomainAuthState> = _authState

    init {
        observeTransportAuthState()
    }

    override suspend fun ensureAuthorized() {
        UnifiedLog.i(TAG) { "ensureAuthorized()" }
        runCatching { transport.ensureAuthorized() }
            .onFailure { error ->
                UnifiedLog.e(TAG, error) { "ensureAuthorized failed" }
                _authState.value = DomainAuthState.Error(error.message ?: "Authorization failed")
            }
    }

    override suspend fun sendPhoneNumber(phoneNumber: String) {
        UnifiedLog.i(TAG) { "sendPhoneNumber()" }
        transport.sendPhoneNumber(phoneNumber)
    }

    override suspend fun sendCode(code: String) {
        UnifiedLog.i(TAG) { "sendCode()" }
        transport.sendCode(code)
    }

    override suspend fun sendPassword(password: String) {
        UnifiedLog.i(TAG) { "sendPassword()" }
        transport.sendPassword(password)
    }

    override suspend fun logout() {
        UnifiedLog.i(TAG) { "logout()" }
        transport.logout()
    }

    private fun observeTransportAuthState() {
        scope.launch {
            transport.authState.collectLatest { state ->
                val mapped = state.toDomain()
                _authState.value = mapped
                if (mapped is DomainAuthState.Error) {
                    UnifiedLog.w(TAG) { "Auth error: ${mapped.message}" }
                }
            }
        }
    }

    private fun TdlibAuthState.toDomain(): DomainAuthState = when (this) {
        TdlibAuthState.Ready -> DomainAuthState.Connected
        TdlibAuthState.Connecting -> DomainAuthState.Idle
        TdlibAuthState.Idle -> DomainAuthState.Idle
        TdlibAuthState.LoggingOut, TdlibAuthState.Closed, TdlibAuthState.LoggedOut ->
            DomainAuthState.Disconnected
        is TdlibAuthState.WaitPhoneNumber -> DomainAuthState.WaitingForPhone
        is TdlibAuthState.WaitCode -> DomainAuthState.WaitingForCode
        is TdlibAuthState.WaitPassword -> DomainAuthState.WaitingForPassword
        TdlibAuthState.WaitTdlibParameters, TdlibAuthState.WaitEncryptionKey -> DomainAuthState.Idle
        is TdlibAuthState.Error -> DomainAuthState.Error(this.message)
        is TdlibAuthState.Unknown -> DomainAuthState.Error("Unknown auth state: ${this.raw}")
    }
}
