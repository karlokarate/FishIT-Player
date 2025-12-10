package com.fishit.player.tools.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
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
import dev.g000sha256.tdl.dto.AuthorizationStateWaitRegistration
import dev.g000sha256.tdl.dto.AuthorizationStateWaitTdlibParameters
import dev.g000sha256.tdl.dto.EmailAddressAuthenticationCode
import dev.g000sha256.tdl.dto.LogStreamFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Interactive Telegram authentication command.
 *
 * Performs a full TDLib login flow:
 * 1. Asks for phone number (or uses config)
 * 2. Asks for SMS/Telegram code
 * 3. Asks for 2FA password if enabled
 * 4. Creates a valid TDLib session
 *
 * Usage:
 *   ./fishit-cli tg auth --phone +491234567890
 *   ./fishit-cli tg auth --session-dir ~/.tdlib-session
 */
class TgAuthCommand : CliktCommand(
    name = "auth",
    help = "Authenticate with Telegram (interactive)"
) {
    private val phone by option("--phone", help = "Phone number (e.g. +491234567890)")
    private val apiId by option("--api-id", help = "Telegram API ID (or use TG_API_ID env)")
    private val apiHash by option("--api-hash", help = "Telegram API Hash (or use TG_API_HASH env)")
    private val sessionDir by option("--session-dir", help = "Session directory (default: ~/.tdlib-session)")
        .default(System.getProperty("user.home") + "/.tdlib-session")

    override fun run() = runBlocking {
        echo("🔐 Telegram Authentication")
        echo("━".repeat(50))
        
        // Resolve configuration
        val resolvedApiId = apiId?.toIntOrNull()
            ?: System.getenv("TG_API_ID")?.toIntOrNull()
            ?: run {
                echo("❌ API ID not found. Set TG_API_ID env or use --api-id")
                return@runBlocking
            }
        
        val resolvedApiHash = apiHash
            ?: System.getenv("TG_API_HASH")
            ?: run {
                echo("❌ API Hash not found. Set TG_API_HASH env or use --api-hash")
                return@runBlocking
            }
        
        val resolvedPhone = phone
            ?: System.getenv("TG_PHONE")
            ?: readInput("📞 Enter phone number (e.g. +491234567890): ")
        
        if (resolvedPhone.isBlank()) {
            echo("❌ Phone number is required")
            return@runBlocking
        }
        
        // Prepare directories
        val dbDir = File(sessionDir, "db").apply { mkdirs() }.absolutePath
        val filesDir = File(sessionDir, "files").apply { mkdirs() }.absolutePath
        
        echo("")
        echo("Configuration:")
        echo("  API ID:      $resolvedApiId")
        echo("  Phone:       $resolvedPhone")
        echo("  Session Dir: $sessionDir")
        echo("")
        
        // Start auth flow
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = TdlClient.create()
        
        val authSession = TelegramAuthSession(
            client = client,
            config = TelegramAuthConfig(
                apiId = resolvedApiId,
                apiHash = resolvedApiHash,
                phoneNumber = resolvedPhone,
                dbDir = dbDir,
                filesDir = filesDir,
            ),
            scope = scope,
            echo = { echo(it) },
            readInput = { prompt -> readInput(prompt) },
        )
        
        when (val result = authSession.login()) {
            is TelegramAuthSession.LoginResult.Ready -> {
                echo("")
                echo("✅ Authentication successful!")
                echo("")
                echo("Session saved to: $sessionDir")
                echo("")
                echo("You can now use the CLI with:")
                echo("  export TG_SESSION_PATH=\"$sessionDir\"")
                echo("  ./fishit-cli tg status")
            }
            is TelegramAuthSession.LoginResult.Failed -> {
                echo("")
                echo("❌ Authentication failed: ${result.reason}")
            }
        }
    }
    
    private fun readInput(prompt: String): String {
        print(prompt)
        System.out.flush()
        return readLine()?.trim() ?: ""
    }
}

/**
 * Configuration for Telegram authentication.
 */
data class TelegramAuthConfig(
    val apiId: Int,
    val apiHash: String,
    val phoneNumber: String,
    val dbDir: String,
    val filesDir: String,
)

/**
 * TDLib authentication session handler.
 * 
 * Handles the complete TDLib auth flow:
 * - WaitTdlibParameters → Set parameters
 * - WaitPhoneNumber → Submit phone
 * - WaitCode → Ask user for code
 * - WaitPassword → Ask user for 2FA password
 * - Ready → Complete
 */
class TelegramAuthSession(
    private val client: TdlClient,
    private val config: TelegramAuthConfig,
    private val scope: CoroutineScope,
    private val echo: (String) -> Unit,
    private val readInput: (String) -> String,
) {
    sealed interface LoginResult {
        data object Ready : LoginResult
        data class Failed(val reason: String) : LoginResult
    }
    
    private var tdParamsSet = false
    private var phoneNumberSet = false
    private var loginComplete = CompletableDeferred<LoginResult>()
    
    suspend fun login(): LoginResult {
        echo("[AUTH] Starting TDLib authentication flow...")
        
        // Configure logging
        try {
            client.setLogStream(LogStreamFile("tdlib_auth.log", 10_000_000L, true)).getOrThrow()
            client.setLogVerbosityLevel(2).getOrThrow()
        } catch (e: Exception) {
            echo("[AUTH] Warning: Could not configure TDLib logging: ${e.message}")
        }
        
        // Start update collector
        scope.launch {
            client.authorizationStateUpdates.collect { update ->
                handleAuthState(update.authorizationState)
            }
        }
        
        // Get initial state
        val initialState = client.getAuthorizationState().getOrThrow()
        echo("[AUTH] Initial state: ${initialState::class.simpleName}")
        handleAuthState(initialState)
        
        return loginComplete.await()
    }
    
    private suspend fun handleAuthState(state: AuthorizationState) {
        echo("[AUTH] State: ${state::class.simpleName}")
        
        when (state) {
            is AuthorizationStateWaitTdlibParameters -> onWaitTdlibParameters()
            is AuthorizationStateWaitPhoneNumber -> onWaitPhoneNumber()
            is AuthorizationStateWaitCode -> onWaitCode()
            is AuthorizationStateWaitPassword -> onWaitPassword()
            is AuthorizationStateWaitOtherDeviceConfirmation -> onWaitOtherDevice(state)
            is AuthorizationStateWaitRegistration -> onWaitRegistration()
            is AuthorizationStateWaitEmailAddress -> onWaitEmailAddress()
            is AuthorizationStateWaitEmailCode -> onWaitEmailCode()
            is AuthorizationStateReady -> onReady()
            is AuthorizationStateLoggingOut,
            is AuthorizationStateClosing,
            is AuthorizationStateClosed -> onClosed(state)
            else -> echo("[AUTH] Unhandled state: $state")
        }
    }
    
    private suspend fun onWaitTdlibParameters() {
        if (tdParamsSet) return
        tdParamsSet = true
        
        echo("[AUTH] Setting TDLib parameters...")
        client.setTdlibParameters(
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
            deviceModel = "fishit-cli",
            systemVersion = System.getProperty("os.name") ?: "Linux",
            applicationVersion = "fishit-cli-1.0",
        ).getOrThrow()
        echo("[AUTH] TDLib parameters set ✓")
    }
    
    private suspend fun onWaitPhoneNumber() {
        if (phoneNumberSet) return
        phoneNumberSet = true
        
        echo("[AUTH] Sending phone number: ${config.phoneNumber}")
        client.setAuthenticationPhoneNumber(config.phoneNumber, null).getOrThrow()
        echo("[AUTH] Phone number submitted ✓")
        echo("")
        echo("📱 A verification code will be sent to your Telegram app or via SMS.")
    }
    
    private suspend fun onWaitCode() {
        echo("")
        while (true) {
            val code = readInput("🔢 Enter verification code: ")
            if (code.isBlank()) {
                echo("Code cannot be empty. Please try again.")
                continue
            }
            try {
                client.checkAuthenticationCode(code).getOrThrow()
                echo("[AUTH] Code accepted ✓")
                return
            } catch (e: Exception) {
                echo("[AUTH] Invalid code: ${e.message}")
                echo("Please try again.")
            }
        }
    }
    
    private suspend fun onWaitPassword() {
        echo("")
        echo("🔐 Two-factor authentication is enabled.")
        while (true) {
            val password = readInput("🔑 Enter 2FA password: ")
            if (password.isBlank()) {
                echo("Password cannot be empty. Please try again.")
                continue
            }
            try {
                client.checkAuthenticationPassword(password).getOrThrow()
                echo("[AUTH] 2FA password accepted ✓")
                return
            } catch (e: Exception) {
                echo("[AUTH] Invalid password: ${e.message}")
                echo("Please try again.")
            }
        }
    }
    
    private fun onWaitOtherDevice(state: AuthorizationStateWaitOtherDeviceConfirmation) {
        echo("")
        echo("📱 Please confirm login on another device.")
        echo("Link/QR: ${state.link}")
    }
    
    private suspend fun onWaitRegistration() {
        echo("")
        echo("📝 This phone number is not registered. Creating new account...")
        val firstName = readInput("First name: ")
        val lastName = readInput("Last name: ")
        client.registerUser(firstName, lastName, false).getOrThrow()
        echo("[AUTH] Registration submitted ✓")
    }
    
    private suspend fun onWaitEmailAddress() {
        echo("")
        val email = readInput("📧 Enter email address: ")
        client.setAuthenticationEmailAddress(email).getOrThrow()
        echo("[AUTH] Email submitted ✓")
    }
    
    private suspend fun onWaitEmailCode() {
        echo("")
        while (true) {
            val code = readInput("📧 Enter email verification code: ")
            if (code.isBlank()) continue
            try {
                client.checkAuthenticationEmailCode(EmailAddressAuthenticationCode(code)).getOrThrow()
                echo("[AUTH] Email code accepted ✓")
                return
            } catch (e: Exception) {
                echo("[AUTH] Invalid email code: ${e.message}")
            }
        }
    }
    
    private suspend fun onReady() {
        echo("[AUTH] Verifying session...")
        try {
            val me = client.getMe().getOrThrow()
            echo("[AUTH] Logged in as: ${me.firstName} ${me.lastName} (@${me.usernames?.activeUsernames?.firstOrNull() ?: "N/A"})")
            completeLogin(LoginResult.Ready)
        } catch (e: Exception) {
            completeLogin(LoginResult.Failed("Session verification failed: ${e.message}"))
        }
    }
    
    private fun onClosed(state: AuthorizationState) {
        completeLogin(LoginResult.Failed("Session closed: ${state::class.simpleName}"))
    }
    
    private fun completeLogin(result: LoginResult) {
        if (!loginComplete.isCompleted) {
            loginComplete.complete(result)
        }
    }
}

// Extension for TdlResult
private fun <T> TdlResult<T>.getOrThrow(): T = when (this) {
    is TdlResult.Success -> result
    is TdlResult.Failure -> throw RuntimeException("TDLib error $code: $message")
}
