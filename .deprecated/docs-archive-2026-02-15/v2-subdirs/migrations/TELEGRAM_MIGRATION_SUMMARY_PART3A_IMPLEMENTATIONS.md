# Telegram Legacy Module Migration Summary - Part 3A: Transport Implementations

**Migration Date:** 2025-01-16  
**Commit:** `52709299`

---

## 1. TdlibAuthSession.kt (auth/)

**Path:** `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/auth/TdlibAuthSession.kt`

**Lines:** 365

```kotlin
package com.fishit.player.infra.transport.telegram.auth

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramAuthClient
import com.fishit.player.infra.transport.telegram.TelegramAuthException
import com.fishit.player.infra.transport.telegram.TelegramAuthState
import com.fishit.player.infra.transport.telegram.TelegramSessionConfig
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.AuthorizationState
import dev.g000sha256.tdl.dto.AuthorizationStateClosed
import dev.g000sha256.tdl.dto.AuthorizationStateClosing
import dev.g000sha256.tdl.dto.AuthorizationStateLoggingOut
import dev.g000sha256.tdl.dto.AuthorizationStateReady
import dev.g000sha256.tdl.dto.AuthorizationStateWaitCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPassword
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPhoneNumber
import dev.g000sha256.tdl.dto.AuthorizationStateWaitTdlibParameters
import dev.g000sha256.tdl.dto.PhoneNumberAuthenticationSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TDLib Authorization Session Manager (v2 Architecture).
 *
 * Manages TDLib authentication state machine with Flow-based events.
 * Ported from legacy `T_TelegramSession` with v2 architecture compliance.
 *
 * **Key Behaviors (from legacy):**
 * - Resume-first: If already authorized on boot → Ready without UI involvement
 * - Automatic TDLib parameters setup
 * - Interactive auth: phone → code → password → ready
 * - Auth event streaming via SharedFlow
 * - Exponential backoff on retries
 *
 * **v2 Compliance:**
 * - No UI references (emits events instead of snackbars)
 * - Uses UnifiedLog for all logging
 * - DI-scoped (receives TdlClient, doesn't create it)
 *
 * @param client The TDLib client (injected via DI)
 * @param config Session configuration (API credentials, paths)
 * @param scope Coroutine scope for background operations
 *
 * @see TelegramAuthClient interface this implements
 * @see contracts/TELEGRAM_LEGACY_MODULE_MIGRATION_CONTRACT.md
 */
class TdlibAuthSession(
    private val client: TdlClient,
    private val config: TelegramSessionConfig,
    private val scope: CoroutineScope
) : TelegramAuthClient {

    companion object {
        private const val TAG = "TdlibAuthSession"
        private const val LOGIN_TIMEOUT_MS = 300_000L // 5 minutes
        private const val DEFAULT_RETRIES = 3
    }

    @Volatile
    private var currentState: AuthorizationState? = null

    @Volatile
    private var previousState: AuthorizationState? = null

    private val collectorStarted = AtomicBoolean(false)
    private val tdParamsSet = AtomicBoolean(false)

    private val _authState = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Idle)
    override val authState: Flow<TelegramAuthState> = _authState.asStateFlow()

    private val _authEvents = MutableSharedFlow<AuthEvent>(replay = 1)

    /**
     * Internal auth events for detailed state tracking.
     * Domain/UI can observe these for interactive auth handling.
     */
    val authEvents: Flow<AuthEvent> = _authEvents.asSharedFlow()

    // ========== TelegramAuthClient Implementation ==========

    override suspend fun ensureAuthorized() {
        UnifiedLog.d(TAG, "ensureAuthorized() - starting auth flow")

        startAuthCollectorIfNeeded()

        // Get initial state
        val initialResult = client.getAuthorizationState()
        when (initialResult) {
            is TdlResult.Success -> {
                val state = initialResult.result
                currentState = state
                updateAuthState(state)
                handleAuthState(state)

                // If already ready, we're done
                if (state is AuthorizationStateReady) {
                    UnifiedLog.i(TAG, "Already authorized - Ready ✅")
                    return
                }

                // Wait for ready state with timeout
                waitForReady()
            }
            is TdlResult.Failure -> {
                val error = "Auth check failed: ${initialResult.code} - ${initialResult.message}"
                _authState.value = TelegramAuthState.Error(error)
                UnifiedLog.e(TAG, error)
                throw TelegramAuthException(error)
            }
        }
    }

    override suspend fun isAuthorized(): Boolean {
        return try {
            val result = client.getAuthorizationState()
            result is TdlResult.Success && result.result is AuthorizationStateReady
        } catch (e: Exception) {
            UnifiedLog.w(TAG, "isAuthorized check failed: ${e.message}")
            false
        }
    }

    override suspend fun sendPhoneNumber(phoneNumber: String) {
        UnifiedLog.d(TAG, "Sending phone number...")
        executeWithRetry("sendPhoneNumber", DEFAULT_RETRIES) {
            val settings = PhoneNumberAuthenticationSettings(
                allowFlashCall = false,
                allowMissedCall = false,
                allowSmsRetrieverApi = false,
                hasUnknownPhoneNumber = false,
                isCurrentPhoneNumber = false,
                firebaseAuthenticationSettings = null,
                authenticationTokens = emptyArray()
            )
            client.setAuthenticationPhoneNumber(phoneNumber, settings).getOrThrow()
            UnifiedLog.d(TAG, "Phone number submitted successfully")
        }
    }

    override suspend fun sendCode(code: String) {
        UnifiedLog.d(TAG, "Sending verification code...")
        executeWithRetry("sendCode", 2) {
            client.checkAuthenticationCode(code).getOrThrow()
            UnifiedLog.d(TAG, "Code submitted successfully")
        }
    }

    override suspend fun sendPassword(password: String) {
        UnifiedLog.d(TAG, "Sending 2FA password...")
        executeWithRetry("sendPassword", 2) {
            client.checkAuthenticationPassword(password).getOrThrow()
            UnifiedLog.d(TAG, "Password submitted successfully")
        }
    }

    override suspend fun logout() {
        UnifiedLog.d(TAG, "Logging out...")
        try {
            client.logOut().getOrThrow()
            _authState.value = TelegramAuthState.LoggedOut
        } catch (e: Exception) {
            UnifiedLog.e(TAG, "Error during logout: ${e.message}")
            throw TelegramAuthException("Logout failed: ${e.message}", e)
        }
    }

    // ========== Internal Methods ==========

    private suspend fun waitForReady() {
        try {
            withTimeout(LOGIN_TIMEOUT_MS) {
                while (true) {
                    when (val s = currentState) {
                        is AuthorizationStateReady -> {
                            UnifiedLog.i(TAG, "Authorization complete - Ready ✅")
                            _authEvents.emit(AuthEvent.Ready)
                            return@withTimeout
                        }
                        is AuthorizationStateClosing,
                        is AuthorizationStateClosed,
                        is AuthorizationStateLoggingOut -> {
                            val error = "Fatal auth state: ${s::class.simpleName}"
                            UnifiedLog.e(TAG, error)
                            _authEvents.emit(AuthEvent.Error(error))
                            throw TelegramAuthException(error)
                        }
                        else -> delay(200)
                    }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            val error = "Login timeout - no response from TDLib after 5 minutes"
            UnifiedLog.e(TAG, error)
            _authEvents.emit(AuthEvent.Error(error))
            throw TelegramAuthException(error, e)
        }
    }

    private fun startAuthCollectorIfNeeded() {
        if (!collectorStarted.compareAndSet(false, true)) return

        UnifiedLog.d(TAG, "Starting auth state flow collector...")

        scope.launch {
            try {
                client.authorizationStateUpdates.collect { update ->
                    val state = update.authorizationState
                    val previousStateName = previousState?.let { it::class.simpleName } ?: "None"
                    val currentStateName = state::class.simpleName

                    UnifiedLog.d(TAG, "Auth state: $previousStateName → $currentStateName")

                    previousState = currentState
                    currentState = state

                    updateAuthState(state)
                    _authEvents.emit(AuthEvent.StateChanged(state))

                    // Handle automatic state transitions
                    handleAuthState(state)

                    // Detect reauth requirement (Ready → WaitPhone/WaitCode/WaitPassword)
                    if (previousState is AuthorizationStateReady && needsReauth(state)) {
                        val reason = "Auth state changed from Ready to $currentStateName"
                        UnifiedLog.w(TAG, "Reauth required: $reason")
                        _authEvents.emit(AuthEvent.ReauthRequired(reason))
                    }
                }
            } catch (e: Exception) {
                UnifiedLog.e(TAG, "Auth collector error: ${e.message}")
                _authEvents.emit(AuthEvent.Error(e.message ?: "Unknown error"))
            }
        }
    }

    private fun needsReauth(state: AuthorizationState): Boolean {
        return state is AuthorizationStateWaitPhoneNumber ||
            state is AuthorizationStateWaitCode ||
            state is AuthorizationStateWaitPassword
    }

    private suspend fun handleAuthState(state: AuthorizationState) {
        when (state) {
            is AuthorizationStateWaitTdlibParameters -> {
                if (tdParamsSet.compareAndSet(false, true)) {
                    UnifiedLog.d(TAG, "Setting TDLib parameters...")
                    setTdlibParameters()
                }
            }
            is AuthorizationStateWaitPhoneNumber -> {
                // Config may have phone number for auto-submission
                config.phoneNumber?.let { phone ->
                    if (phone.isNotBlank()) {
                        UnifiedLog.d(TAG, "Auto-submitting phone from config...")
                        try {
                            sendPhoneNumber(phone)
                        } catch (e: Exception) {
                            UnifiedLog.w(TAG, "Auto phone submission failed: ${e.message}")
                        }
                    }
                }
            }
            else -> { /* No automatic handling */ }
        }
    }

    private suspend fun setTdlibParameters() {
        val params = dev.g000sha256.tdl.dto.SetTdlibParameters(
            useTestDc = false,
            databaseDirectory = config.databasePath,
            filesDirectory = config.filesPath,
            useFileDatabase = true,
            useChatInfoDatabase = true,
            useMessageDatabase = true,
            useSecretChats = false,
            apiId = config.apiId,
            apiHash = config.apiHash,
            systemLanguageCode = "en",
            deviceModel = config.deviceModel,
            systemVersion = config.systemVersion,
            applicationVersion = config.appVersion,
            databaseEncryptionKey = null
        )

        val result = client.setTdlibParameters(params)
        when (result) {
            is TdlResult.Success -> UnifiedLog.d(TAG, "TDLib parameters set successfully")
            is TdlResult.Failure -> {
                val error = "Failed to set TDLib parameters: ${result.code} - ${result.message}"
                UnifiedLog.e(TAG, error)
                throw TelegramAuthException(error)
            }
        }
    }

    private fun updateAuthState(state: AuthorizationState) {
        _authState.value = when (state) {
            is AuthorizationStateWaitTdlibParameters -> TelegramAuthState.Connecting
            is AuthorizationStateWaitPhoneNumber -> TelegramAuthState.WaitPhoneNumber
            is AuthorizationStateWaitCode -> TelegramAuthState.WaitCode
            is AuthorizationStateWaitPassword -> TelegramAuthState.WaitPassword
            is AuthorizationStateReady -> TelegramAuthState.Ready
            is AuthorizationStateLoggingOut -> TelegramAuthState.LoggingOut
            is AuthorizationStateClosed -> TelegramAuthState.Closed
            else -> TelegramAuthState.Idle
        }
    }

    private suspend inline fun <T> executeWithRetry(
        operation: String,
        retries: Int,
        block: () -> T
    ): T {
        var lastError: Exception? = null
        repeat(retries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastError = e
                UnifiedLog.w(TAG, "$operation failed (attempt ${attempt + 1}/$retries): ${e.message}")
                if (attempt < retries - 1) {
                    delay(500L * (attempt + 1)) // Exponential backoff
                }
            }
        }
        val errorMsg = "$operation failed after $retries attempts: ${lastError?.message}"
        _authEvents.emit(AuthEvent.Error(errorMsg))
        throw lastError ?: TelegramAuthException(errorMsg)
    }
}

/**
 * Extension function to convert TdlResult to value or throw exception.
 */
private fun <T> TdlResult<T>.getOrThrow(): T = when (this) {
    is TdlResult.Success -> result
    is TdlResult.Failure -> throw RuntimeException("TDLib error $code: $message")
}

/**
 * Authentication state events emitted during login flow.
 */
sealed class AuthEvent {
    /** Auth state changed to a new TDLib state */
    data class StateChanged(val state: AuthorizationState) : AuthEvent()

    /** Error occurred during auth */
    data class Error(val message: String, val code: Int? = null) : AuthEvent()

    /** Successfully authorized and ready */
    data object Ready : AuthEvent()

    /** Reauth required (was Ready, now needs login again) */
    data class ReauthRequired(val reason: String) : AuthEvent()
}
```

---

## 2. TelegramFileDownloadManager.kt (file/)

**Path:** `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/file/TelegramFileDownloadManager.kt`

**Lines:** 242

```kotlin
package com.fishit.player.infra.transport.telegram.file

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramFileClient
import com.fishit.player.infra.transport.telegram.TgFile
import com.fishit.player.infra.transport.telegram.TgFileUpdate
import com.fishit.player.infra.transport.telegram.TgStorageStats
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.FileTypeAnimation
import dev.g000sha256.tdl.dto.FileTypeAudio
import dev.g000sha256.tdl.dto.FileTypeDocument
import dev.g000sha256.tdl.dto.FileTypePhoto
import dev.g000sha256.tdl.dto.FileTypeVideo
import dev.g000sha256.tdl.dto.FileTypeVideoNote
import dev.g000sha256.tdl.dto.FileTypeVoiceNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * TDLib File Download Manager (v2 Architecture - Transport Layer Only).
 *
 * Manages file downloads using TDLib's built-in download system.
 * This is a **transport-only** component - no playback-specific logic.
 *
 * **Key Behaviors (from legacy):**
 * - Start/cancel downloads with priority
 * - Observe file download updates via Flow
 * - RemoteId → FileId resolution for stale file recovery
 * - Storage statistics and optimization
 *
 * **What belongs here (Transport):**
 * - TDLib download primitives
 * - File state observation
 * - Storage maintenance
 *
 * **What does NOT belong here (goes to Playback):**
 * - MP4 moov validation
 * - Streaming readiness checks
 * - Playback-specific thresholds
 *
 * @param client The TDLib client (injected via DI)
 * @param scope Coroutine scope for background operations
 *
 * @see TelegramFileClient interface this implements
 * @see contracts/TELEGRAM_LEGACY_MODULE_MIGRATION_CONTRACT.md Section 5.1
 */
class TelegramFileDownloadManager(
    private val client: TdlClient,
    private val scope: CoroutineScope
) : TelegramFileClient {

    companion object {
        private const val TAG = "TelegramFileDownloadManager"
    }

    private val _fileUpdates = MutableSharedFlow<TgFileUpdate>(replay = 0, extraBufferCapacity = 64)
    override val fileUpdates: Flow<TgFileUpdate> = _fileUpdates.asSharedFlow()

    init {
        // Collect file updates from TDLib
        scope.launch {
            try {
                client.fileUpdates.collect { update ->
                    val file = update.file
                    emitFileUpdate(file)
                }
            } catch (e: Exception) {
                UnifiedLog.e(TAG, "File updates collector error: ${e.message}")
            }
        }
    }

    // ========== TelegramFileClient Implementation ==========

    override suspend fun startDownload(
        fileId: Int,
        priority: Int,
        offset: Long,
        limit: Long
    ) {
        UnifiedLog.d(TAG, "startDownload(fileId=$fileId, priority=$priority, offset=$offset, limit=$limit)")

        val result = client.downloadFile(
            fileId = fileId,
            priority = priority,
            offset = offset,
            limit = limit,
            synchronous = false
        )

        when (result) {
            is TdlResult.Success -> {
                UnifiedLog.d(TAG, "Download started for fileId=$fileId")
                emitFileUpdate(result.result)
            }
            is TdlResult.Failure -> {
                val error = "downloadFile failed: ${result.code} - ${result.message}"
                UnifiedLog.e(TAG, error)
                _fileUpdates.emit(TgFileUpdate.Failed(fileId, error, result.code))
            }
        }
    }

    override suspend fun cancelDownload(fileId: Int, deleteLocalCopy: Boolean) {
        UnifiedLog.d(TAG, "cancelDownload(fileId=$fileId, delete=$deleteLocalCopy)")

        val result = client.cancelDownloadFile(
            fileId = fileId,
            onlyIfPending = false
        )

        when (result) {
            is TdlResult.Success -> {
                UnifiedLog.d(TAG, "Download cancelled for fileId=$fileId")
                if (deleteLocalCopy) {
                    deleteFile(fileId)
                }
            }
            is TdlResult.Failure -> {
                UnifiedLog.w(TAG, "cancelDownloadFile failed: ${result.code} - ${result.message}")
            }
        }
    }

    override suspend fun getFile(fileId: Int): TgFile? {
        val result = client.getFile(fileId)
        return when (result) {
            is TdlResult.Success -> mapFile(result.result)
            is TdlResult.Failure -> {
                UnifiedLog.w(TAG, "getFile($fileId) failed: ${result.message}")
                null
            }
        }
    }

    override suspend fun resolveRemoteId(remoteId: String): TgFile? {
        UnifiedLog.d(TAG, "resolveRemoteId: $remoteId")

        val result = client.getRemoteFile(
            remoteFileId = remoteId,
            fileType = null
        )

        return when (result) {
            is TdlResult.Success -> {
                val file = result.result
                UnifiedLog.d(TAG, "Resolved remoteId to fileId=${file.id}")
                mapFile(file)
            }
            is TdlResult.Failure -> {
                UnifiedLog.w(TAG, "resolveRemoteId failed: ${result.code} - ${result.message}")
                null
            }
        }
    }

    override suspend fun getDownloadedPrefixSize(fileId: Int): Long {
        val file = getFile(fileId) ?: return 0L
        return file.downloadedPrefixSize
    }

    override suspend fun getStorageStats(): TgStorageStats {
        val result = client.getStorageStatisticsFast()
        return when (result) {
            is TdlResult.Success -> {
                val stats = result.result
                TgStorageStats(
                    totalSize = stats.filesSize,
                    photoCount = stats.fileCount, // Simplified - would need type breakdown
                    videoCount = 0,
                    documentCount = 0,
                    audioCount = 0,
                    otherCount = 0
                )
            }
            is TdlResult.Failure -> {
                UnifiedLog.w(TAG, "getStorageStatisticsFast failed: ${result.message}")
                TgStorageStats(0, 0, 0, 0, 0, 0)
            }
        }
    }

    override suspend fun optimizeStorage(maxSizeBytes: Long, maxAgeDays: Int): Long {
        UnifiedLog.d(TAG, "optimizeStorage(maxSize=${maxSizeBytes / 1024 / 1024}MB, maxAge=${maxAgeDays}d)")

        val ttl = maxAgeDays * 24 * 60 * 60 // Convert to seconds

        val result = client.optimizeStorage(
            size = maxSizeBytes,
            ttl = ttl,
            count = Int.MAX_VALUE,
            immunityDelay = 3600, // 1 hour immunity for recently accessed files
            fileTypes = null, // All types
            chatIds = null, // All chats
            excludeChatIds = null,
            returnDeletedFileStatistics = true,
            chatLimit = 0
        )

        return when (result) {
            is TdlResult.Success -> {
                val freed = result.result.size
                UnifiedLog.i(TAG, "Storage optimized: freed ${freed / 1024 / 1024}MB")
                freed
            }
            is TdlResult.Failure -> {
                UnifiedLog.w(TAG, "optimizeStorage failed: ${result.message}")
                0L
            }
        }
    }

    // ========== Internal Methods ==========

    private suspend fun deleteFile(fileId: Int) {
        val result = client.deleteFile(fileId)
        when (result) {
            is TdlResult.Success -> UnifiedLog.d(TAG, "Deleted file $fileId")
            is TdlResult.Failure -> UnifiedLog.w(TAG, "deleteFile failed: ${result.message}")
        }
    }

    private suspend fun emitFileUpdate(file: dev.g000sha256.tdl.dto.File) {
        val local = file.local
        val fileId = file.id

        when {
            local.isDownloadingCompleted && local.path.isNotEmpty() -> {
                _fileUpdates.emit(TgFileUpdate.Completed(fileId, local.path))
            }
            local.isDownloadingActive -> {
                _fileUpdates.emit(
                    TgFileUpdate.Progress(
                        fileId = fileId,
                        downloadedSize = local.downloadedSize,
                        totalSize = file.size,
                        downloadedPrefixSize = local.downloadedPrefixSize
                    )
                )
            }
        }
    }

    private fun mapFile(file: dev.g000sha256.tdl.dto.File): TgFile {
        return TgFile(
            id = file.id,
            remoteId = file.remote?.id ?: "",
            uniqueId = file.remote?.uniqueId ?: "",
            size = file.size,
            expectedSize = file.expectedSize,
            localPath = file.local.path.takeIf { it.isNotEmpty() },
            isDownloadingActive = file.local.isDownloadingActive,
            isDownloadingCompleted = file.local.isDownloadingCompleted,
            downloadedSize = file.local.downloadedSize,
            downloadedPrefixSize = file.local.downloadedPrefixSize
        )
    }
}
```

---

**See also:**
- [Part 3B: Chat Browser + Thumb Fetcher](TELEGRAM_MIGRATION_SUMMARY_PART3B_IMPLEMENTATIONS.md)
- [Part 3C: Playback Layer](TELEGRAM_MIGRATION_SUMMARY_PART3C_PLAYBACK.md)
