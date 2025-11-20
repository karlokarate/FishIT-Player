package com.chris.m3usuite.telegram.core

import android.content.Context
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.config.AppConfig
import com.chris.m3usuite.telegram.config.ConfigLoader
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
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
    // Logging
    private val logger = TelegramLogRepository.getInstance()
    
    // Single TdlClient instance for the entire process
    private var client: TdlClient? = null

    // Core components
    private var session: T_TelegramSession? = null
    private var browser: T_ChatBrowser? = null
    private var downloader: T_TelegramFileDownloader? = null

    // Configuration
    private var config: AppConfig? = null

    // Coroutine scope with supervisor job
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // State tracking
    private val isStarted = AtomicBoolean(false)
    private val isInitializing = AtomicBoolean(false)

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
        if (isStarted.get()) {
            logger.debug("ServiceClient", "Already started")
            return
        }

        if (!isInitializing.compareAndSet(false, true)) {
            logger.debug("ServiceClient", "Already initializing, waiting...")
            // Wait for initialization to complete
            while (isInitializing.get()) {
                delay(100)
            }
            return
        }

        try {
            logger.info("ServiceClient", "Starting Unified Telegram Engine...")
            _connectionState.value = TgConnectionState.Connecting

            // Load configuration from settings
            val apiId = settings.tgApiId.first()
            val apiHash = settings.tgApiHash.first()
            val phoneNumber = "" // Phone will be provided during login

            if (apiId == 0 || apiHash.isBlank()) {
                throw IllegalStateException("API credentials not configured")
            }

            config =
                ConfigLoader.load(
                    context = context,
                    apiId = apiId,
                    apiHash = apiHash,
                    phoneNumber = phoneNumber,
                )

            // Create TdlClient
            client = TdlClient.create()
            logger.info("ServiceClient", "TdlClient created")

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
            isStarted.set(true)

            logger.info("ServiceClient", "Unified Telegram Engine started successfully")
        } catch (e: Exception) {
            logger.error("ServiceClient", "Failed to start: ${e.message}", throwable = e)
            e.printStackTrace()
            _connectionState.value = TgConnectionState.Error(e.message ?: "Unknown error")
            _authState.value = TelegramAuthState.Error(e.message ?: "Unknown error")
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
                    logger.info("ServiceClient", "Submitting password...")
                    currentSession.sendPassword(password)
                }
                code != null -> {
                    logger.info("ServiceClient", "Submitting code...")
                    currentSession.sendCode(code)
                }
                phone != null -> {
                    logger.info("ServiceClient", "Submitting phone number...")
                    currentSession.sendPhoneNumber(phone)
                }
                else -> {
                    logger.info("ServiceClient", "Starting login flow...")
                    currentSession.login()
                }
            }
        } catch (e: Exception) {
            logger.info("ServiceClient", "Login error: ${e.message}")
            _authState.value = TelegramAuthState.Error(e.message ?: "Login failed")
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
                logger.error("ServiceClient", "Error in newMessageUpdates flow", throwable = e)
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
                logger.error("ServiceClient", "Error in file updates flow", throwable = e)
            }
        }

        logger.info("ServiceClient", "Update distribution started")
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
                            logger.info("ServiceClient", "Auth state changed: $newState")
                        }
                        is AuthEvent.Ready -> {
                            _authState.value = TelegramAuthState.Ready
                            logger.info("ServiceClient", "Auth ready ✅")
                        }
                        is AuthEvent.Error -> {
                            _authState.value = TelegramAuthState.Error(event.message)
                            logger.info("ServiceClient", "Auth error: ${event.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("ServiceClient", "Error in auth events flow", throwable = e)
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
        logger.info("ServiceClient", "Shutting down...")

        serviceScope.cancel()

        downloader = null
        browser = null
        session = null
        client = null
        config = null

        isStarted.set(false)
        _authState.value = TelegramAuthState.Idle
        _connectionState.value = TgConnectionState.Disconnected
        _syncState.value = TgSyncState.Idle

        logger.info("ServiceClient", "Shutdown complete")
        T_TelegramServiceClient.INSTANCE = null
    }

    companion object {
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
}
