# Telegram Legacy Module Migration Summary - Part 2: Interface Definitions

**Migration Date:** 2025-01-16  
**Commit:** `52709299`

---

## 1. TelegramAuthClient.kt

**Path:** `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/TelegramAuthClient.kt`

```kotlin
package com.fishit.player.infra.transport.telegram

import kotlinx.coroutines.flow.Flow

/**
 * Typed interface for Telegram authentication operations.
 *
 * This is part of the v2 Transport API Surface. Upper layers (pipeline, playback)
 * consume this interface instead of accessing TDLib directly.
 *
 * **v2 Architecture:**
 * - Transport layer owns TDLib lifecycle and auth state machine
 * - Pipeline/Playback consume typed interfaces
 * - No TDLib types (`TdApi.*`) exposed beyond transport
 *
 * **Implementation:** [DefaultTelegramClient] implements this interface internally.
 *
 * @see TelegramHistoryClient for message fetching
 * @see TelegramFileClient for file downloads
 * @see contracts/TELEGRAM_LEGACY_MODULE_MIGRATION_CONTRACT.md
 */
interface TelegramAuthClient {

    /**
     * Current authentication state.
     *
     * Emits updates whenever auth state changes. UI/Domain should observe this
     * to handle interactive auth steps (code entry, password entry).
     */
    val authState: Flow<TelegramAuthState>

    /**
     * Ensure the TDLib client is authorized and ready.
     *
     * Implements "resume-first" behavior:
     * - If already authorized on boot → Ready without UI involvement
     * - If not authorized → initiates auth flow, caller observes [authState]
     *
     * @throws TelegramAuthException if authorization fails
     */
    suspend fun ensureAuthorized()

    /**
     * Check if currently authorized without initiating auth flow.
     *
     * @return true if authorized and ready to use
     */
    suspend fun isAuthorized(): Boolean

    /**
     * Submit phone number for authentication.
     *
     * Called when [authState] emits [TelegramAuthState.WaitPhoneNumber].
     *
     * @param phoneNumber Phone number in international format (e.g., "+49123456789")
     * @throws TelegramAuthException if submission fails
     */
    suspend fun sendPhoneNumber(phoneNumber: String)

    /**
     * Submit verification code for authentication.
     *
     * Called when [authState] emits [TelegramAuthState.WaitCode].
     *
     * @param code The verification code received via SMS/call
     * @throws TelegramAuthException if code is invalid
     */
    suspend fun sendCode(code: String)

    /**
     * Submit 2FA password for authentication.
     *
     * Called when [authState] emits [TelegramAuthState.WaitPassword].
     *
     * @param password The two-factor authentication password
     * @throws TelegramAuthException if password is incorrect
     */
    suspend fun sendPassword(password: String)

    /**
     * Log out from current Telegram session.
     *
     * This will invalidate the current session and require re-authentication.
     */
    suspend fun logout()
}
```

---

## 2. TelegramHistoryClient.kt

**Path:** `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/TelegramHistoryClient.kt`

```kotlin
package com.fishit.player.infra.transport.telegram

import kotlinx.coroutines.flow.Flow

/**
 * Typed interface for Telegram chat and message history operations.
 *
 * This is part of the v2 Transport API Surface. Pipeline layer consumes
 * this interface to fetch messages for catalog ingestion.
 *
 * **v2 Architecture:**
 * - Returns [TgMessage], [TgChat] wrapper types (not raw TDLib DTOs)
 * - No media classification/normalization (belongs in pipeline)
 * - No persistence (belongs in data layer)
 *
 * **Paging Rule (critical):**
 * Per TDLib semantics, `getChatHistory` requires:
 * - First page: `fromMessageId=0`, `offset=0`
 * - Subsequent pages: `fromMessageId=oldestMsgId`, `offset=-1` (to avoid duplicates)
 *
 * **Implementation:** [DefaultTelegramClient] implements this interface internally.
 *
 * @see TelegramAuthClient for authentication
 * @see TelegramFileClient for file downloads
 * @see contracts/TELEGRAM_LEGACY_MODULE_MIGRATION_CONTRACT.md
 */
interface TelegramHistoryClient {

    /**
     * Stream of incoming messages (live updates).
     *
     * Emits new messages as they arrive. Pipeline can use this for
     * warm ingestion (new content appears without manual refresh).
     *
     * **Note:** Emits ALL messages, not just media. Pipeline filters.
     */
    val messageUpdates: Flow<TgMessage>

    /**
     * Get list of available chats.
     *
     * Returns chats from the main chat list, ordered by last message time.
     * Excludes bot chats per product decision.
     *
     * @param limit Maximum number of chats to return
     * @return List of chat info (id, title, type, member count, last message)
     */
    suspend fun getChats(limit: Int = 100): List<TgChat>

    /**
     * Get a single chat by ID.
     *
     * Uses internal cache to reduce API calls.
     *
     * @param chatId Telegram chat ID
     * @return Chat info or null if not found
     */
    suspend fun getChat(chatId: Long): TgChat?

    /**
     * Fetch message history from a chat (paged).
     *
     * **Paging Rule:**
     * - First page: `fromMessageId=0`, `offset=0`
     * - Subsequent pages: `fromMessageId=oldestMsgId`, `offset=-1`
     *
     * @param chatId Chat ID to fetch from
     * @param limit Maximum messages per page (default 100)
     * @param fromMessageId Starting message ID for pagination (0 = most recent)
     * @param offset Offset from fromMessageId (0 for first page, -1 for subsequent)
     * @return List of messages in reverse chronological order (newest first)
     */
    suspend fun fetchMessages(
        chatId: Long,
        limit: Int = 100,
        fromMessageId: Long = 0,
        offset: Int = 0
    ): List<TgMessage>

    /**
     * Load complete message history from a chat.
     *
     * Iterates through all pages until history is exhausted.
     * Use with caution for large chats.
     *
     * **Product decision:** This is used for background backfill.
     * During playback, backfill should be paused (see playback policy).
     *
     * @param chatId Chat ID to load
     * @param pageSize Messages per page (default 100)
     * @param maxMessages Safety limit (default 10000)
     * @param onProgress Optional callback for progress updates
     * @return Complete list of messages
     */
    suspend fun loadAllMessages(
        chatId: Long,
        pageSize: Int = 100,
        maxMessages: Int = 10000,
        onProgress: ((loaded: Int) -> Unit)? = null
    ): List<TgMessage>

    /**
     * Search for messages in a chat.
     *
     * @param chatId Chat to search in
     * @param query Search query text
     * @param limit Maximum results (default 100)
     * @return List of matching messages
     */
    suspend fun searchMessages(
        chatId: Long,
        query: String,
        limit: Int = 100
    ): List<TgMessage>
}
```

---

## 3. TelegramFileClient.kt

**Path:** `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/TelegramFileClient.kt`

```kotlin
package com.fishit.player.infra.transport.telegram

import kotlinx.coroutines.flow.Flow

/**
 * Typed interface for Telegram file download operations.
 *
 * This is part of the v2 Transport API Surface. Playback layer consumes
 * this interface for progressive file downloads during streaming.
 *
 * **v2 Architecture:**
 * - Transport handles TDLib file download primitives
 * - No MP4 parsing or playback-specific logic (belongs in playback layer)
 * - Returns [TgFile] wrapper types with download state
 *
 * **RemoteId-First Design:**
 * Files are identified by stable `remoteId` rather than `fileId`.
 * If a fileId becomes stale (TDLib cache eviction), resolve via remoteId.
 *
 * **Implementation:** [DefaultTelegramClient] implements this interface internally.
 *
 * @see TelegramAuthClient for authentication
 * @see TelegramHistoryClient for message fetching
 * @see contracts/TELEGRAM_LEGACY_MODULE_MIGRATION_CONTRACT.md
 */
interface TelegramFileClient {

    /**
     * Stream of file download updates.
     *
     * Emits [TgFileUpdate] events for all active downloads:
     * - Progress updates (downloaded bytes, total size)
     * - Completion events (local path available)
     * - Error events
     *
     * Playback layer observes this to determine streaming readiness.
     */
    val fileUpdates: Flow<TgFileUpdate>

    /**
     * Start downloading a file.
     *
     * Downloads are queued and managed internally with concurrency limits.
     * Observe [fileUpdates] for progress.
     *
     * **Priority values:**
     * - 32 = High (playback, current item)
     * - 16 = Medium (prefetch, next items)
     * - 1-8 = Low (background backfill)
     *
     * @param fileId TDLib file ID
     * @param priority Download priority (1-32, higher = more urgent)
     * @param offset Starting offset for partial download (default 0)
     * @param limit Bytes to download (0 = entire file)
     */
    suspend fun startDownload(
        fileId: Int,
        priority: Int = 1,
        offset: Long = 0,
        limit: Long = 0
    )

    /**
     * Cancel an active download.
     *
     * @param fileId TDLib file ID
     * @param deleteLocalCopy Whether to delete partially downloaded file
     */
    suspend fun cancelDownload(fileId: Int, deleteLocalCopy: Boolean = false)

    /**
     * Get current file state.
     *
     * @param fileId TDLib file ID
     * @return Current file state or null if not found
     */
    suspend fun getFile(fileId: Int): TgFile?

    /**
     * Resolve remoteId to current fileId.
     *
     * Use this when a fileId becomes stale. TDLib may reassign fileIds
     * after cache eviction, but remoteId remains stable.
     *
     * **Fallback pattern:**
     * 1. Try operation with cached fileId
     * 2. If "file not found" error → resolve via remoteId
     * 3. Retry operation with new fileId
     *
     * @param remoteId Stable remote file identifier
     * @return Resolved [TgFile] with current fileId, or null if not found
     */
    suspend fun resolveRemoteId(remoteId: String): TgFile?

    /**
     * Get downloaded prefix size for a file.
     *
     * Used by playback layer to determine if enough data is available
     * for streaming to begin.
     *
     * @param fileId TDLib file ID
     * @return Number of bytes downloaded from the beginning of the file
     */
    suspend fun getDownloadedPrefixSize(fileId: Int): Long

    /**
     * Get storage statistics.
     *
     * Returns fast approximation of TDLib cache usage.
     *
     * @return Storage stats (total size, file counts by type)
     */
    suspend fun getStorageStats(): TgStorageStats

    /**
     * Optimize storage by removing old files.
     *
     * TDLib will remove files based on size/age thresholds.
     *
     * @param maxSizeBytes Maximum cache size to maintain
     * @param maxAgeDays Maximum file age in days
     * @return Number of bytes freed
     */
    suspend fun optimizeStorage(
        maxSizeBytes: Long = 5L * 1024 * 1024 * 1024, // 5GB default
        maxAgeDays: Int = 30
    ): Long
}

/**
 * File download update event.
 */
sealed class TgFileUpdate {
    /** File ID this update refers to */
    abstract val fileId: Int

    /**
     * Download progress update.
     *
     * @param fileId TDLib file ID
     * @param downloadedSize Bytes downloaded so far
     * @param totalSize Total file size (0 if unknown)
     * @param downloadedPrefixSize Bytes downloaded from beginning
     */
    data class Progress(
        override val fileId: Int,
        val downloadedSize: Long,
        val totalSize: Long,
        val downloadedPrefixSize: Long
    ) : TgFileUpdate()

    /**
     * Download completed.
     *
     * @param fileId TDLib file ID
     * @param localPath Full path to downloaded file
     */
    data class Completed(
        override val fileId: Int,
        val localPath: String
    ) : TgFileUpdate()

    /**
     * Download failed.
     *
     * @param fileId TDLib file ID
     * @param error Error description
     * @param errorCode TDLib error code (if available)
     */
    data class Failed(
        override val fileId: Int,
        val error: String,
        val errorCode: Int? = null
    ) : TgFileUpdate()
}

/**
 * Storage statistics from TDLib.
 */
data class TgStorageStats(
    val totalSize: Long,
    val photoCount: Int,
    val videoCount: Int,
    val documentCount: Int,
    val audioCount: Int,
    val otherCount: Int
)
```

---

## 4. TelegramThumbFetcher.kt

**Path:** `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/TelegramThumbFetcher.kt`

```kotlin
package com.fishit.player.infra.transport.telegram

/**
 * Typed interface for Telegram thumbnail fetching.
 *
 * This is part of the v2 Transport API Surface. Integrates with Coil 3
 * image loading for efficient thumbnail display.
 *
 * **v2 Architecture:**
 * - Transport handles TDLib thumbnail download
 * - Returns local file path for Coil to load
 * - Uses remoteId-first design for stable caching
 *
 * **Bounded Error Tracking:**
 * To prevent log spam, implementations should track failed remoteIds
 * in a bounded LRU set and skip repeated fetch attempts.
 *
 * **Implementation:** [TelegramThumbFetcherImpl] in `infra/transport-telegram/imaging/`
 *
 * @see TelegramFileClient for general file downloads
 * @see contracts/TELEGRAM_LEGACY_MODULE_MIGRATION_CONTRACT.md
 */
interface TelegramThumbFetcher {

    /**
     * Fetch thumbnail for a Telegram file.
     *
     * Downloads the thumbnail if not cached, returns local path.
     *
     * **Priority:** Thumbnails use medium priority (16) to avoid
     * blocking playback downloads but still load quickly for UI.
     *
     * @param thumbRef Reference to the thumbnail (fileId, remoteId, etc.)
     * @return Local file path to thumbnail, or null if unavailable
     */
    suspend fun fetchThumbnail(thumbRef: TgThumbnailRef): String?

    /**
     * Check if thumbnail is already cached locally.
     *
     * @param thumbRef Reference to the thumbnail
     * @return true if available locally without download
     */
    suspend fun isCached(thumbRef: TgThumbnailRef): Boolean

    /**
     * Prefetch thumbnails for a list of items.
     *
     * Used for scroll-ahead prefetching. Lower priority than on-screen items.
     *
     * @param thumbRefs List of thumbnail references to prefetch
     */
    suspend fun prefetch(thumbRefs: List<TgThumbnailRef>)

    /**
     * Clear the "failed remoteIds" tracking set.
     *
     * Call this when network conditions change or user requests refresh.
     */
    fun clearFailedCache()
}

/**
 * Reference to a Telegram thumbnail.
 *
 * Uses remoteId as the stable identifier. fileId may become stale
 * after TDLib cache eviction.
 */
data class TgThumbnailRef(
    /** TDLib file ID (may be stale) */
    val fileId: Int,
    /** Stable remote identifier */
    val remoteId: String,
    /** Thumbnail width */
    val width: Int,
    /** Thumbnail height */
    val height: Int,
    /** Thumbnail format (jpeg, webp, etc.) */
    val format: String = "jpeg"
)
```

---

**See also:**
- [Part 1: Overview](TELEGRAM_MIGRATION_SUMMARY_PART1_OVERVIEW.md)
- [Part 3: Implementation Code](TELEGRAM_MIGRATION_SUMMARY_PART3_IMPLEMENTATIONS.md)
