package tdltest

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.AuthorizationState
import dev.g000sha256.tdl.dto.AuthorizationStateClosed
import dev.g000sha256.tdl.dto.AuthorizationStateClosing
import dev.g000sha256.tdl.dto.AuthorizationStateLoggingOut
import dev.g000sha256.tdl.dto.AuthorizationStateReady
import dev.g000sha256.tdl.dto.AuthorizationStateWaitCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitEmailAddress
import dev.g000sha256.tdl.dto.AuthorizationStateWaitEmailCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitOtherDeviceConfirmation
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPassword
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPhoneNumber
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPremiumPurchase
import dev.g000sha256.tdl.dto.AuthorizationStateWaitRegistration
import dev.g000sha256.tdl.dto.AuthorizationStateWaitTdlibParameters
import dev.g000sha256.tdl.dto.EmailAddressAuthenticationCode
import dev.g000sha256.tdl.dto.LogStreamFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

// Helper to unwrap TdlResult
fun <T> TdlResult<T>.getOrThrow(): T = when (this) {
    is TdlResult.Success -> result
    is TdlResult.Failure -> throw RuntimeException("TDLib error $code: $message")
}

class TelegramSession(
    val client: TdlClient,
    val config: AppConfig,
    private val scope: CoroutineScope
) {

    sealed interface LoginResult {
        data object Ready : LoginResult
        data class Restart(val reason: String) : LoginResult
    }

    private val _authState = MutableStateFlow<AuthorizationState?>(null)
    val authState: StateFlow<AuthorizationState?> = _authState.asStateFlow()

    private var tdParamsSet = false
    private var phoneNumberSet = false
    private var emailAddressSet = false
    private var registrationSet = false

    private var authUpdatesJob: Job? = null
    private var allUpdatesJob: Job? = null
    private var pendingLogin: CompletableDeferred<LoginResult>? = null
    private var loggingConfigured = false

    /**
     * Flow-gesteuerter Login: folgt strikt den TDLib-States (WaitTdlibParameters → WaitPhoneNumber → WaitCode → WaitPassword → Ready)
     * und hält die Update-Collector dauerhaft aktiv, damit TDLib-States/Antworten nicht verloren gehen.
     */
    suspend fun login(): LoginResult = supervisorScope {
        println("[AUTH] Login-Flow wird gestartet (Flow-basiert, TDLib States)...")

        configureTdLibLogging()

        // Guards resetten für einen frischen Lauf
        tdParamsSet = false
        phoneNumberSet = false
        emailAddressSet = false
        registrationSet = false

        ensureUpdateCollectors()
        val loginResult = CompletableDeferred<LoginResult>()
        pendingLogin = loginResult

        // Initial state ermitteln, nachdem Collector gestartet ist.
        val initial = client.getAuthorizationState().getOrThrow()
        println("[AUTH] Initialer State (getAuthorizationState): ${initial::class.simpleName}")
        _authState.value = initial
        handleAuthState(initial)

        val result = loginResult.await()
        pendingLogin = null
        result
    }

    private fun ensureUpdateCollectors() {
        if (allUpdatesJob == null) {
            allUpdatesJob = scope.launch {
                // Halte den globalen Update-Stream offen, damit TDLib Antworten liefern kann.
                client.allUpdates.collect { /* no-op; keeps stream draining */ }
            }
        }
        if (authUpdatesJob == null) {
            authUpdatesJob = scope.launch {
                client.authorizationStateUpdates.collect { update ->
                    val state = update.authorizationState
                    println("[AUTH] UpdateAuthorizationState: ${state::class.simpleName}")
                    _authState.value = state
                    handleAuthState(state)
                }
            }
        }
    }

    private fun completeLogin(result: LoginResult) {
        val pending = pendingLogin
        if (pending != null && !pending.isCompleted) {
            pending.complete(result)
        }
    }

    private suspend fun handleAuthState(state: AuthorizationState) {
        println("[AUTH] handleAuthState(${state::class.simpleName})")
        DebugLog.log("AuthState=${state::class.simpleName}")

        when (state) {
            is AuthorizationStateWaitTdlibParameters -> onWaitTdlibParameters()
            is AuthorizationStateWaitPhoneNumber     -> onWaitPhoneNumber()
            is AuthorizationStateWaitCode            -> onWaitCode()
            is AuthorizationStateWaitPassword        -> onWaitPassword()
            is AuthorizationStateWaitOtherDeviceConfirmation -> onWaitOtherDeviceConfirmation(state)
            is AuthorizationStateWaitRegistration    -> onWaitRegistration()
            is AuthorizationStateWaitEmailAddress    -> onWaitEmailAddress()
            is AuthorizationStateWaitEmailCode       -> onWaitEmailCode()
            is AuthorizationStateWaitPremiumPurchase -> onWaitPremiumPurchase()
            is AuthorizationStateReady               -> {
                println("[AUTH] State READY empfangen ✅")
                if (verifyReadySession()) {
                    completeLogin(LoginResult.Ready)
                }
            }

            is AuthorizationStateLoggingOut          -> {
                println("[AUTH] State LOGGING_OUT – Login neu starten")
                requestRestart("TDLib meldet LoggingOut")
            }
            is AuthorizationStateClosing             -> {
                println("[AUTH] State CLOSING – Login neu starten")
                requestRestart("TDLib meldet Closing")
            }
            is AuthorizationStateClosed              -> {
                println("[AUTH] State CLOSED – Login neu starten")
                requestRestart("TDLib meldet Closed")
            }
            else -> println("[AUTH] Unbehandelter Auth-State: $state")
        }
    }

    private suspend fun configureTdLibLogging() {
        if (loggingConfigured) return
        val logPath = "tdlib_native.log"
        try {
            client.setLogStream(LogStreamFile(logPath, 10_000_000L, true)).getOrThrow()
            client.setLogVerbosityLevel(4).getOrThrow()
            println("[AUTH] TDLib Logging aktiv: $logPath (verbosity=4)")
            DebugLog.log("TDLib logging enabled path=$logPath verbosity=4")
            loggingConfigured = true
        } catch (t: Throwable) {
            println("[AUTH] Konnte TDLib-Logging nicht setzen: ${t.message}")
            DebugLog.log("TDLib logging failed: ${t.message}")
        }
    }

    private fun requestRestart(reason: String) {
        // Beim Neustart erlauben wir erneut das Setzen der Guards.
        tdParamsSet = false
        phoneNumberSet = false
        emailAddressSet = false
        registrationSet = false
        val pending = pendingLogin
        if (pending != null && !pending.isCompleted) {
            pending.complete(LoginResult.Restart(reason))
        } else {
            println("[AUTH] Restart benötigt: $reason (kein aktiver Login-Flow)")
        }
    }

    private suspend fun verifyReadySession(): Boolean {
        return try {
            client.getMe().getOrThrow()
            true
        } catch (t: Throwable) {
            println("[AUTH] Ready erhalten, aber getMe fehlgeschlagen (${t.message}) – Login neu starten")
            requestRestart("getMe schlägt trotz READY fehl")
            false
        }
    }

    private suspend fun onWaitTdlibParameters() {
        println("[AUTH] → AuthorizationStateWaitTdlibParameters")

        if (tdParamsSet) {
            println("[AUTH] TdlibParameters wurden bereits gesetzt – überspringe.")
            return
        }
        tdParamsSet = true

        val ok = client.setTdlibParameters(
            useTestDc = false,
            databaseDirectory = config.dbDir,
            filesDirectory = config.filesDir,
            databaseEncryptionKey = ByteArray(0),
            useFileDatabase = true,
            useChatInfoDatabase = true,
            useMessageDatabase = true,
            useSecretChats = false,
            apiId = config.apiId,
            apiHash = config.apiHash,
            systemLanguageCode = "de",
            deviceModel = "tdl-cli",
            systemVersion = System.getProperty("os.name") ?: "Linux",
            applicationVersion = "tdl-cli-0.1",
        ).getOrThrow()

        println("[AUTH] TdlibParameters gesetzt: $ok")
    }

    private suspend fun onWaitPhoneNumber() {
        println("[AUTH] → AuthorizationStateWaitPhoneNumber")

        if (phoneNumberSet) {
            println("[AUTH] Telefonnummer wurde bereits gesetzt – überspringe.")
            return
        }
        phoneNumberSet = true

        client.setAuthenticationPhoneNumber(config.phoneNumber, null).getOrThrow()
        println("[AUTH] Telefonnummer an TDLib übermittelt: ${config.phoneNumber}")
    }

    private suspend fun onWaitCode() {
        println("[AUTH] → AuthorizationStateWaitCode")

        while (true) {
            val code = CliIo.readNonEmptyString(
                "[AUTH] Bitte den per SMS/Telegram gesendeten Code eingeben: "
            )
            try {
                client.checkAuthenticationCode(code).getOrThrow()
                println("[AUTH] Code akzeptiert.")
                return
            } catch (t: Throwable) {
                println("[AUTH] Code ungültig (${t.message}). Bitte erneut versuchen.")
            }
        }
    }

    private suspend fun onWaitPassword() {
        println("[AUTH] → AuthorizationStateWaitPassword (2FA aktiv)")

        while (true) {
            val password = CliIo.readNonEmptyString("[AUTH] 2FA-Passwort eingeben: ")
            try {
                client.checkAuthenticationPassword(password).getOrThrow()
                println("[AUTH] 2FA-Passwort akzeptiert.")
                return
            } catch (t: Throwable) {
                println("[AUTH] Passwort ungültig (${t.message}). Bitte erneut versuchen.")
            }
        }
    }

    private fun onWaitOtherDeviceConfirmation(state: AuthorizationStateWaitOtherDeviceConfirmation) {
        println("[AUTH] → AuthorizationStateWaitOtherDeviceConfirmation")
        val link = state.link
        println("[AUTH] Bitte den Login über ein anderes Gerät bestätigen. Link/QR: $link")
    }

    private suspend fun onWaitRegistration() {
        println("[AUTH] → AuthorizationStateWaitRegistration")
        if (registrationSet) {
            println("[AUTH] Registrierungsdaten wurden bereits gesendet – warte auf Bestätigung.")
            return
        }
        val firstName = CliIo.readNonEmptyString("[AUTH] Vorname für neues Konto: ")
        val lastName = CliIo.readNonEmptyString("[AUTH] Nachname für neues Konto: ")
        val ok = client.registerUser(firstName, lastName, false).getOrThrow()
        registrationSet = true
        println("[AUTH] Registrierung gesendet: $ok")
    }

    private suspend fun onWaitEmailAddress() {
        println("[AUTH] → AuthorizationStateWaitEmailAddress")
        if (emailAddressSet) {
            println("[AUTH] E-Mail-Adresse wurde bereits übermittelt – warte auf Code.")
            return
        }
        val email = CliIo.readNonEmptyString("[AUTH] Bitte Login-E-Mail eingeben: ")
        client.setAuthenticationEmailAddress(email).getOrThrow()
        emailAddressSet = true
        println("[AUTH] E-Mail-Adresse übermittelt.")
    }

    private suspend fun onWaitEmailCode() {
        println("[AUTH] → AuthorizationStateWaitEmailCode")
        while (true) {
            val code = CliIo.readNonEmptyString("[AUTH] E-Mail-Code eingeben: ")
            try {
                client.checkAuthenticationEmailCode(EmailAddressAuthenticationCode(code)).getOrThrow()
                println("[AUTH] E-Mail-Code akzeptiert.")
                return
            } catch (t: Throwable) {
                println("[AUTH] E-Mail-Code ungültig (${t.message}). Bitte erneut versuchen.")
            }
        }
    }

    private fun onWaitPremiumPurchase() {
        println("[AUTH] → AuthorizationStateWaitPremiumPurchase")
        println("[AUTH] Premium-Kauf abschließen und zurückkehren; TDLib wartet auf Abschluss.")
    }
}
