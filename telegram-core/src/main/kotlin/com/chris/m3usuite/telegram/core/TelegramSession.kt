package com.chris.m3usuite.telegram.core

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Hilfs-Erweiterung für TdlResult -> Erfolgswert oder Exception
fun <T> TdlResult<T>.getOrThrow(): T = when (this) {
    is TdlResult.Success -> result
    is TdlResult.Failure -> throw RuntimeException("TDLib error $code: $message")
}

/**
 * Flow-based Telegram authentication and session management.
 * 
 * This class handles the complete authentication flow using TDLib's authorization state updates.
 * It manages the state machine for login, code verification, and password authentication.
 * 
 * @param client The TDLib client instance
 * @param config Application configuration containing API credentials and storage paths
 * @param scope Coroutine scope for managing async operations (e.g., viewModelScope in Android)
 * @param codeProvider Callback for providing authentication code (e.g., from UI dialog)
 * @param passwordProvider Callback for providing 2FA password (e.g., from UI dialog)
 */
class TelegramSession(
    val client: TdlClient,
    val config: AppConfig,
    private val scope: CoroutineScope,
    private val codeProvider: suspend () -> String,
    private val passwordProvider: suspend () -> String
) {

    @Volatile
    private var currentState: AuthorizationState? = null

    private var collectorStarted = false
    private var tdParamsSet = false

    /**
     * Start the login flow using Flow-based state updates.
     * This method will suspend until authentication is complete (AuthorizationStateReady)
     * or until an error occurs.
     */
    suspend fun login() {
        startAuthCollectorIfNeeded()

        // Get initial state for debugging
        val initial = client.getAuthorizationState().getOrThrow()
        currentState = initial

        // Wait for AuthorizationStateReady
        while (true) {
            when (val s = currentState) {
                is AuthorizationStateReady -> {
                    return
                }

                is AuthorizationStateClosing,
                is AuthorizationStateClosed,
                is AuthorizationStateLoggingOut -> {
                    error("[AUTH] Fatal State: ${s::class.simpleName}")
                }

                else -> {
                    delay(200)
                }
            }
        }
    }

    private fun startAuthCollectorIfNeeded() {
        if (collectorStarted) return
        collectorStarted = true

        scope.launch {
            try {
                client.authorizationStateUpdates.collect { update ->
                    val state = update.authorizationState
                    currentState = state
                    handleAuthState(state)
                }
            } catch (t: Throwable) {
                // In production, this should be logged to your logging system
                throw RuntimeException("Error in authorizationStateUpdates flow: ${t.message}", t)
            }
        }
    }

    private suspend fun handleAuthState(state: AuthorizationState) {
        when (state) {
            is AuthorizationStateWaitTdlibParameters -> onWaitTdlibParameters()
            is AuthorizationStateWaitPhoneNumber     -> onWaitPhoneNumber()
            is AuthorizationStateWaitCode            -> onWaitCode()
            is AuthorizationStateWaitPassword        -> onWaitPassword()
            is AuthorizationStateReady               -> {
                // Authentication complete
            }
            is AuthorizationStateLoggingOut          -> {
                // Logging out
            }
            is AuthorizationStateClosing             -> {
                // Client is closing
            }
            is AuthorizationStateClosed              -> {
                // Client is closed
            }
            else -> {
                // Unhandled state
            }
        }
    }

    private suspend fun onWaitTdlibParameters() {
        // Prevent setting TdlibParameters multiple times
        if (tdParamsSet) {
            return
        }
        tdParamsSet = true

        client.setTdlibParameters(
            /* useTestDc             */ false,
            /* databaseDirectory     */ config.dbDir,
            /* filesDirectory        */ config.filesDir,
            /* databaseEncryptionKey */ ByteArray(0),
            /* useFileDatabase       */ true,
            /* useChatInfoDatabase   */ true,
            /* useMessageDatabase    */ true,
            /* useSecretChats        */ false,
            /* apiId                 */ config.apiId,
            /* apiHash               */ config.apiHash,
            /* systemLanguageCode    */ "de",
            /* deviceModel           */ "Android-FishIT",
            /* systemVersion         */ "Android",
            /* applicationVersion    */ "FishIT-Player-1.0"
        ).getOrThrow()
    }

    private suspend fun onWaitPhoneNumber() {
        val settings = PhoneNumberAuthenticationSettings(
            /* allowFlashCall                 */ false,
            /* allowMissedCall                */ false,
            /* allowSmsRetrieverApi           */ false,
            /* hasUnknownPhoneNumber          */ false,
            /* isCurrentPhoneNumber           */ false,
            /* firebaseAuthenticationSettings */ null,
            /* authenticationTokens           */ emptyArray()
        )

        client.setAuthenticationPhoneNumber(config.phoneNumber, settings).getOrThrow()
    }

    private suspend fun onWaitCode() {
        val code = codeProvider()
        client.checkAuthenticationCode(code).getOrThrow()
    }

    private suspend fun onWaitPassword() {
        val password = passwordProvider()
        client.checkAuthenticationPassword(password).getOrThrow()
    }
}
