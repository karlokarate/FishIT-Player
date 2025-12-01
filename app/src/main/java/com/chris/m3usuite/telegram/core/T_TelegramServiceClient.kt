package com.chris.m3usuite.telegram.core

import android.content.Context
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.config.AppConfig
import com.chris.m3usuite.telegram.config.ConfigLoader
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import com.chris.m3usuite.telegram.logging.TgLogEntry
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Telegram authentication state for external UI consumption.
 */
sealed class TelegramAuthState {
    object Idle : TelegramAuthState()

    object Connecting : TelegramAuthState()

    object WaitingForPhone : TelegramAuthState()

    object WaitingForCode : TelegramAuthState()

    object WaitingForPassword : TelegramAuthState()

    object Ready : TelegramAuthState()

    data class Error(
        val message: String,
    ) : TelegramAuthState()
}

/**
 * Telegram connection state.
 */
sealed class TgConnectionState {
    object Disconnected : TgConnectionState()

    object Connecting : TgConnectionState()

    object Connected : TgConnectionState()

    data class Error(
        val message: String,
    ) : TgConnectionState()
}

/**
 * Telegram sync state.
 */
sealed class TgSyncState {
    object Idle : TgSyncState()

    data class Running(
        val progress: Int,
        val total: Int,
    ) : TgSyncState()

    data class Completed(
        val itemsProcessed: Int,
    ) : TgSyncState()

    data class Failed(
        val error: String,
    ) : TgSyncState()
}

/**
 * Telegram activity event for Activity Feed.
 */
sealed class TgActivityEvent {
    data class NewMessage(
        val chatId: Long,
        val messageId: Long,
    ) : TgActivityEvent()

    data class NewDownload(
        val fileId: Int,
        val fileName: String,
    ) : TgActivityEvent()

    data class DownloadComplete(
        val fileId: Int,
        val fileName: String,
    ) : TgActivityEvent()

    data class ParseComplete(
        val chatId: Long,
        val itemsFound: Int,
    ) : TgActivityEvent()
}

/**
 * Unified Telegram Engine - Single entry point for all TDLib functionality.
 * Manages exactly ONE TdlClient instance per process.
 *
 * Key responsibilities:
 * - Lifecycle management of TdlClient
 * - Auth state management via T_TelegramSession
 * - Chat browsing via T_ChatBrowser
 * - File downloads via T_TelegramFileDownloader
 * - Update distribution to all components
 * - Reconnection handling on network changes
 * - Process lifecycle integration
 *
 * This is the ONLY class that creates and owns a TdlClient instance.
 * All other components must use this service to access Telegram functionality.
 */
class T_TelegramServiceClient private constructor(
    private val applicationContext: Context,
) {
    companion object {
        private const val TAG = "TelegramServiceClient"

        @Volatile
        private var INSTANCE: T_TelegramServiceClient? = null

        /**
         * Get or create the singleton instance.
         * Thread-safe singleton with double-checked locking.
         *
         * @param context Application context
         * @return Singleton instance
         */
        fun getInstance(context: Context): T_TelegramServiceClient =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: T_TelegramServiceClient(context.applicationContext).also {
                    INSTANCE = it
                }
            }
    }

    // Single TdlClient instance for the entire process
    private var client: TdlClient? = null

    // Core components
    private var session: T_TelegramSession? = null
    private var browser: T_ChatBrowser? = null
    private var downloader: T_TelegramFileDownloader? = null

    // Live update handler for real-time message processing
    private var updateHandler: com.chris.m3usuite.telegram.ingestion.TelegramUpdateHandler? = null

    // Configuration
    private var config: AppConfig? = null

    // Coroutine scope with supervisor job - mutable to allow recreation after shutdown
    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // State tracking
    private val _isStarted = AtomicBoolean(false)
    private val isInitializing = AtomicBoolean(false)

    /**
     * Check if the service is started and ready for operations.
     * Used by TelegramFileLoader and TelegramThumbPrefetcher to gate operations.
     */
    val isStarted: Boolean
        get() = _isStarted.get()

    // State flows
    private val _authState = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Idle)
    val authState: StateFlow<TelegramAuthState> = _authState.asStateFlow()

    private val _connectionState = MutableStateFlow<TgConnectionState>(TgConnectionState.Disconnected)
    val connectionState: StateFlow<TgConnectionState> = _connectionState.asStateFlow()

    private val _syncState = MutableStateFlow<TgSyncState>(TgSyncState.Idle)
    val syncState: StateFlow<TgSyncState> = _syncState.asStateFlow()

    private val _activityEvents = MutableSharedFlow<TgActivityEvent>(replay = 10)
    val activityEvents: SharedFlow<TgActivityEvent> = _activityEvents.asSharedFlow()

    /**
     * Task 1: Unified Telegram Engine State
     *
     * This is the single source of truth for:
     * - Settings screen (enabled toggle + status display)
     * - Telegram playback path (canStream gate)
     *
     * The state is derived from:
     * - isEnabled: From SettingsStore (must be set via setTelegramEnabled)
     * - authState: From T_TelegramSession
     * - isEngineHealthy: From startup/runtime errors
     * - canStream: Derived property (isEnabled && authReady && isEngineHealthy)
     */
    private val _engineState = MutableStateFlow(TelegramEngineState())
    val engineState: StateFlow<TelegramEngineState> = _engineState.asStateFlow()

    /**
     * Task 1: Update the unified engine state.
     * Called internally when any state component changes.
     *
     * This method synchronizes _engineState with _authState and internal health tracking.
     * It should be called after:
     * - setTelegramEnabled()
     * - Auth state changes
     * - Engine health changes (startup success/failure, runtime errors)
     */
    private fun updateEngineState() {
        _engineState.value =
            _engineState.value.copy(
                authState = _authState.value,
            )
    }

    /**
     * Task 1: Set Telegram enabled state.
     * This is the single entry point for changing isEnabled.
     *
     * IMPORTANT: This does NOT persist the value - caller must handle persistence.
     * This only updates the in-memory engine state.
     *
     * @param enabled New enabled state
     */
    fun setTelegramEnabled(enabled: Boolean) {
        _engineState.value = _engineState.value.copy(isEnabled = enabled)
        TelegramLogRepository.info(
            source = "T_TelegramServiceClient",
            message = "Telegram enabled state changed",
            details = mapOf("enabled" to enabled.toString()),
        )
    }

    /**
     * Task 3: Set engine health.
     * Called when engine operations succeed or fail.
     *
     * @param healthy New health status
     * @param error Optional error message (cleared when healthy=true)
     */
    fun setEngineHealth(
        healthy: Boolean,
        error: String? = null,
    ) {
        _engineState.value =
            _engineState.value.copy(
                isEngineHealthy = healthy,
                recentError = if (healthy) null else error,
            )
        TelegramLogRepository.info(
            source = "T_TelegramServiceClient",
            message = "Engine health changed",
            details =
                mapOf(
                    "healthy" to healthy.toString(),
                    "error" to (error ?: "none"),
                ),
        )
    }

    /**
     * Ensure the service is started and ready.
     * This is idempotent - safe to call multiple times.
     *
     * @param context Android context
     * @param settings Settings store for configuration
     */
    suspend fun ensureStarted(
        context: Context,
        settings: SettingsStore,
    ) {
        if (_isStarted.get()) {
            TelegramLogRepository.log(
                level = TgLogEntry.LogLevel.DEBUG,
                source = "T_TelegramServiceClient",
                message = "Already started",
            )
            return
        }

        if (!isInitializing.compareAndSet(false, true)) {
            TelegramLogRepository.debug("T_TelegramServiceClient", "Already initializing, waiting...")
            // Wait for initialization to complete
            while (isInitializing.get()) {
                delay(100)
            }
            return
        }

        try {
            // Recreate serviceScope if it was cancelled (e.g., after shutdown)
            if (!serviceScope.isActive) {
                TelegramLogRepository.debug("T_TelegramServiceClient", "Recreating cancelled serviceScope...")
                serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            }

            TelegramLogRepository.log(
                level = TgLogEntry.LogLevel.INFO,
                source = "T_TelegramServiceClient",
                message = "Starting Unified Telegram Engine",
            )
            _connectionState.value = TgConnectionState.Connecting

            // Load configuration from settings
            val apiIdOverride = settings.tgApiId.first().takeIf { it != 0 }
            val apiHashOverride = settings.tgApiHash.first().takeIf { it.isNotBlank() }
            val phoneNumber = settings.tgPhoneNumber.first() // Can be empty

            config =
                ConfigLoader.load(
                    context = context,
                    apiId = apiIdOverride,
                    apiHash = apiHashOverride,
                    phoneNumber = phoneNumber,
                )

            // Create TdlClient
            client = TdlClient.create()
            TelegramLogRepository.log(
                level = TgLogEntry.LogLevel.INFO,
                source = "T_TelegramServiceClient",
                message = "TdlClient created",
            )

            // Create core components
            session =
                T_TelegramSession(
                    client = client!!,
                    config = config!!,
                    scope = serviceScope,
                )

            browser =
                T_ChatBrowser(
                    session = session!!,
                )

            downloader =
                T_TelegramFileDownloader(
                    context = context,
                    session = session!!,
                )

            // Start update distribution
            startUpdateDistribution()

            // Start auth event collection
            startAuthEventCollection()

            _connectionState.value = TgConnectionState.Connected
            _isStarted.set(true)

            // Task 3: Mark engine as healthy on successful startup
            setEngineHealth(healthy = true, error = null)

            TelegramLogRepository.log(
                level = TgLogEntry.LogLevel.INFO,
                source = "T_TelegramServiceClient",
                message = "Unified Telegram Engine started successfully",
            )
        } catch (e: Exception) {
            TelegramLogRepository.log(
                level = TgLogEntry.LogLevel.ERROR,
                source = "T_TelegramServiceClient",
                message = "Failed to start",
                details = mapOf("error" to (e.message ?: "Unknown error")),
            )
            e.printStackTrace()
            _connectionState.value = TgConnectionState.Error(e.message ?: "Unknown error")
            _authState.value = TelegramAuthState.Error(e.message ?: "Unknown error")

            // Task 3: Mark engine as unhealthy on startup failure
            // But DO NOT change isEnabled - that's a user setting
            setEngineHealth(healthy = false, error = e.message ?: "Unknown error")
            updateEngineState()

            throw e
        } finally {
            isInitializing.set(false)
        }
    }

    /**
     * Initiate login flow.
     * Delegates to T_TelegramSession for actual auth handling.
     *
     * @param phone Phone number (optional, used if provided)
     * @param code Verification code (optional)
     * @param password 2FA password (optional)
     */
    suspend fun login(
        phone: String? = null,
        code: String? = null,
        password: String? = null,
    ) {
        val currentSession = session ?: throw IllegalStateException("Service not started")

        try {
            when {
                password != null -> {
                    TelegramLogRepository.debug("T_TelegramServiceClient", "Submitting password...")
                    currentSession.sendPassword(password)
                }
                code != null -> {
                    TelegramLogRepository.debug("T_TelegramServiceClient", "Submitting code...")
                    currentSession.sendCode(code)
                }
                phone != null -> {
                    TelegramLogRepository.debug("T_TelegramServiceClient", "Submitting phone number...")
                    currentSession.sendPhoneNumber(phone)
                }
                else -> {
                    TelegramLogRepository.debug("T_TelegramServiceClient", "Starting login flow...")
                    currentSession.login()
                }
            }
        } catch (e: Exception) {
            TelegramLogRepository.debug("T_TelegramServiceClient", "Login error: ${e.message}")
            _authState.value = TelegramAuthState.Error(e.message ?: "Login failed")

            // Task 3: Login errors affect auth state but not engine health
            // Engine health is only affected by startup/runtime errors
            updateEngineState()

            throw e
        }
    }

    /**
     * List chats from Telegram.
     *
     * @param context Android context
     * @param limit Maximum number of chats to retrieve
     * @return List of Chat objects
     */
    suspend fun listChats(
        context: Context,
        limit: Int = 100,
    ): List<Chat> {
        val currentBrowser = browser ?: throw IllegalStateException("Service not started")
        return currentBrowser.getTopChats(limit)
    }

    /**
     * Resolve chat title for a given chat ID.
     *
     * @param chatId Chat ID
     * @return Chat title or empty string if not found
     */
    suspend fun resolveChatTitle(chatId: Long): String {
        val currentBrowser = browser ?: throw IllegalStateException("Service not started")
        return currentBrowser.getChat(chatId)?.title ?: ""
    }

    /**
     * Get the file downloader component.
     *
     * @return T_TelegramFileDownloader instance
     */
    fun downloader(): T_TelegramFileDownloader = downloader ?: throw IllegalStateException("Service not started")

    /**
     * Get the chat browser component.
     *
     * @return T_ChatBrowser instance
     */
    fun browser(): T_ChatBrowser = browser ?: throw IllegalStateException("Service not started")

    /**
     * Update sync state (called by TelegramSyncWorker).
     */
    fun updateSyncState(state: TgSyncState) {
        _syncState.value = state
    }

    /**
     * Emit activity event (called by various components).
     */
    suspend fun emitActivityEvent(event: TgActivityEvent) {
        _activityEvents.emit(event)
    }

    /**
     * Check if authentication is ready for ingestion/sync operations.
     *
     * Per design decision 6.11 (Auth & Ingestion Constraints):
     * - Ingestion MUST NOT run unless auth state is [TelegramAuthState.Ready]
     * - All sync workers and ingestion pipelines must check this before processing
     *
     * @return true if auth state is [TelegramAuthState.Ready], false otherwise
     */
    fun isAuthReady(): Boolean = _authState.value == TelegramAuthState.Ready

    /**
     * Wait for authentication to become ready, with timeout.
     *
     * Per design decision 6.11 (Auth & Ingestion Constraints):
     * - Ingestion MUST NOT run unless auth state is [TelegramAuthState.Ready]
     * - If TDLib DB is already authorized from a previous session, this returns
     *   immediately once the auth state collector receives the Ready state
     * - All auth state transitions are logged via [TelegramLogRepository]
     *
     * Typical usage in sync workers:
     * ```kotlin
     * val isReady = serviceClient.awaitAuthReady(timeoutMs = 30_000L)
     * if (!isReady) return Result.failure(workDataOf("error" to "Auth not ready"))
     * ```
     *
     * @param timeoutMs Maximum time to wait in milliseconds (default 30 seconds)
     * @return true if auth became ready within timeout, false otherwise
     */
    suspend fun awaitAuthReady(timeoutMs: Long = 30_000L): Boolean {
        TelegramLogRepository.debug(
            "T_TelegramServiceClient",
            "Waiting for auth ready (current: ${_authState.value::class.simpleName}, timeout: ${timeoutMs}ms)",
        )

        // If already ready, return immediately
        if (_authState.value == TelegramAuthState.Ready) {
            TelegramLogRepository.debug("T_TelegramServiceClient", "Auth already ready")
            return true
        }

        return try {
            withTimeout(timeoutMs) {
                _authState
                    .filter { it == TelegramAuthState.Ready }
                    .first()
                TelegramLogRepository.info("T_TelegramServiceClient", "Auth became ready")
                true
            }
        } catch (e: TimeoutCancellationException) {
            TelegramLogRepository.warn(
                "T_TelegramServiceClient",
                "Auth ready timeout after ${timeoutMs}ms (current: ${_authState.value::class.simpleName})",
            )
            false
        }
    }

    /**
     * Wait for authentication to reach a specific state, with timeout.
     *
     * Useful for UI to wait for specific states like [TelegramAuthState.WaitingForCode]
     * during interactive login flows.
     *
     * @param targetState The specific [TelegramAuthState] to wait for
     * @param timeoutMs Maximum time to wait in milliseconds (default 10 seconds)
     * @return true if target state was reached within timeout, false otherwise
     */
    suspend fun awaitAuthState(
        targetState: TelegramAuthState,
        timeoutMs: Long = 10_000L,
    ): Boolean {
        if (_authState.value == targetState) return true

        return try {
            withTimeout(timeoutMs) {
                _authState
                    .filter { it == targetState }
                    .first()
                true
            }
        } catch (e: TimeoutCancellationException) {
            false
        }
    }

    /**
     * Start distributing TDLib updates to components.
     */
    private fun startUpdateDistribution() {
        val currentClient = client ?: return

        // Distribute new message updates
        serviceScope.launch {
            try {
                currentClient.newMessageUpdates.collect { update ->
                    _activityEvents.emit(
                        TgActivityEvent.NewMessage(
                            chatId = update.message.chatId,
                            messageId = update.message.id,
                        ),
                    )
                }
            } catch (e: Exception) {
                TelegramLogRepository.debug("T_TelegramServiceClient", "Error in newMessageUpdates flow: ${e.message}")
            }
        }

        // Distribute file updates
        serviceScope.launch {
            try {
                currentClient.fileUpdates.collect { update ->
                    val file = update.file
                    val isComplete = file.local?.isDownloadingCompleted ?: false

                    if (isComplete) {
                        _activityEvents.emit(
                            TgActivityEvent.DownloadComplete(
                                fileId = file.id,
                                fileName = file.local?.path?.substringAfterLast('/') ?: "unknown",
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                TelegramLogRepository.debug("T_TelegramServiceClient", "Error in file updates flow: ${e.message}")
            }
        }

        TelegramLogRepository.debug("T_TelegramServiceClient", "Update distribution started")
    }

    /**
     * Start the live update handler for real-time message processing.
     * Called when auth state transitions to READY.
     */
    private fun startUpdateHandler() {
        if (updateHandler != null) {
            TelegramLogRepository.debug("T_TelegramServiceClient", "UpdateHandler already started")
            return
        }

        try {
            updateHandler =
                com.chris.m3usuite.telegram.ingestion.TelegramUpdateHandler(
                    context = applicationContext,
                    serviceClient = this,
                )
            updateHandler?.start()
            TelegramLogRepository.info("T_TelegramServiceClient", "TelegramUpdateHandler started for live updates")
        } catch (e: Exception) {
            TelegramLogRepository.error(
                source = "T_TelegramServiceClient",
                message = "Failed to start TelegramUpdateHandler",
                exception = e,
            )
        }
    }

    /**
     * Start collecting auth events from session and mapping to external auth state.
     */
    private fun startAuthEventCollection() {
        val currentSession = session ?: return

        serviceScope.launch {
            try {
                currentSession.authEvents.collect { event ->
                    when (event) {
                        is AuthEvent.StateChanged -> {
                            val newState = mapAuthorizationStateToAuthState(event.state)
                            _authState.value = newState
                            // Task 1: Update engine state when auth state changes
                            updateEngineState()
                            TelegramLogRepository.debug("T_TelegramServiceClient", "Auth state changed: $newState")
                        }
                        is AuthEvent.Ready -> {
                            _authState.value = TelegramAuthState.Ready
                            // Task 1: Update engine state when ready
                            updateEngineState()
                            TelegramLogRepository.debug("T_TelegramServiceClient", "Auth ready ✅")
                            // Start live update handler for real-time message processing
                            startUpdateHandler()
                        }
                        is AuthEvent.Error -> {
                            _authState.value = TelegramAuthState.Error(event.message)
                            // Task 1: Update engine state on auth error
                            updateEngineState()
                            TelegramLogRepository.debug("T_TelegramServiceClient", "Auth error: ${event.message}")
                        }
                        is AuthEvent.ReauthRequired -> {
                            _authState.value = TelegramAuthState.WaitingForPhone
                            // Task 1: Update engine state
                            updateEngineState()
                            TelegramLogRepository.info("T_TelegramServiceClient", "Reauth required: ${event.reason}")
                            // Show global snackbar notification
                            try {
                                com.chris.m3usuite.ui.home.GlobalSnackbarEvent.show(
                                    "Telegram benötigt eine erneute Anmeldung. Bitte öffne die Telegram-Einstellungen.",
                                )
                            } catch (e: Exception) {
                                TelegramLogRepository.debug("T_TelegramServiceClient", "Error showing reauth snackbar: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                TelegramLogRepository.debug("T_TelegramServiceClient", "Error in auth events flow: ${e.message}")
            }
        }
    }

    /**
     * Map TDLib AuthorizationState to external TelegramAuthState.
     */
    private fun mapAuthorizationStateToAuthState(state: AuthorizationState): TelegramAuthState =
        when (state) {
            is AuthorizationStateWaitPhoneNumber -> TelegramAuthState.WaitingForPhone
            is AuthorizationStateWaitCode -> TelegramAuthState.WaitingForCode
            is AuthorizationStateWaitPassword -> TelegramAuthState.WaitingForPassword
            is AuthorizationStateReady -> TelegramAuthState.Ready
            is AuthorizationStateWaitTdlibParameters -> TelegramAuthState.Connecting
            is AuthorizationStateLoggingOut,
            is AuthorizationStateClosing,
            is AuthorizationStateClosed,
            -> TelegramAuthState.Idle
            else -> TelegramAuthState.Idle
        }

    /**
     * Shutdown the service and cleanup resources.
     */
    fun shutdown() {
        TelegramLogRepository.debug("T_TelegramServiceClient", "Shutting down...")

        // Stop update handler first
        updateHandler?.stop()
        updateHandler = null

        // Cancel scope and wait for coroutines to finish
        serviceScope.cancel()

        // Close TdlClient to release native resources
        runBlocking {
            try {
                client?.close()
                TelegramLogRepository.debug("T_TelegramServiceClient", "TdlClient closed")
            } catch (e: Exception) {
                TelegramLogRepository.debug("T_TelegramServiceClient", "Error closing TdlClient: ${e.message}")
            }
        }

        // Null out references after scope is cancelled
        downloader = null
        browser = null
        session = null
        client = null
        config = null

        _isStarted.set(false)
        _authState.value = TelegramAuthState.Idle
        _connectionState.value = TgConnectionState.Disconnected
        _syncState.value = TgSyncState.Idle

        // Task 1: Update engine state on shutdown
        // Note: isEnabled is NOT reset - it's a user preference
        updateEngineState()

        TelegramLogRepository.info("T_TelegramServiceClient", "Shutdown complete")
    }
}
