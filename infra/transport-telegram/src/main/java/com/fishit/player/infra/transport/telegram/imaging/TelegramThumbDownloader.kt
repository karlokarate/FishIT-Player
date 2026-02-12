package com.fishit.player.infra.transport.telegram.imaging

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramFileClient
import com.fishit.player.infra.transport.telegram.TelegramRemoteId
import com.fishit.player.infra.transport.telegram.TelegramRemoteResolver
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of thumbnail download request containing resolved IDs and paths.
 *
 * @param thumbFileId The Telegram file ID for the thumbnail (null if not available)
 * @param localPath The local file path if thumbnail is already downloaded (null if pending/unavailable)
 */
data class ThumbRequestResult(
    val thumbFileId: Int?,
    val localPath: String?,
)

/**
 * Helper for ensuring Telegram thumbnails are downloaded using proxy cache only.
 *
 * This helper implements the RemoteId-First thumbnail strategy:
 * 1. Resolve remoteId → thumbFileId via TelegramRemoteResolver
 * 2. Check if thumbnail already downloaded (thumbLocalPath)
 * 3. If not, trigger download with LOW priority (non-blocking)
 * 4. Return local path if available, null otherwise
 *
 * ## Architecture Notes
 *
 * - **No secondary cache**: Only proxy cache is used
 * - **App-scope downloads**: Downloads run in fileClient's app-level scope, not UI scope
 * - **Idempotent**: Safe to call multiple times for same remoteId
 * - **Non-blocking**: Returns null if download not complete, triggers async download
 *
 * ## Usage Pattern
 *
 * ```kotlin
 * // In ViewModel or Composable:
 *
 * // 1. Show miniThumb immediately
 * Image(data = ImageRef.InlineBytes(miniThumb))
 *
 * // 2. Trigger thumb download (non-blocking)
 * val thumbPath = thumbDownloader.ensureThumbDownloaded(remoteId)
 * if (thumbPath != null) {
 *     Image(data = ImageRef.LocalFile(thumbPath))
 * }
 *
 * // 3. Observe fileUpdates to detect completion and trigger recomposition
 * LaunchedEffect(remoteId) {
 *     fileClient.fileUpdates
 *         .filter { it.fileId == thumbFileId }
 *         .filter { it.file.local.isDownloadingCompleted }
 *         .collect {
 *             // Trigger state update to reload image from proxy cache
 *             thumbnailState.value = it.file.local.path
 *         }
 * }
 * // No polling loops needed - fileUpdates provides real-time notification
 * ```
 *
 * @param remoteResolver RemoteId resolver for message → thumbFileId
 * @param fileClient Transport client for file operations
 */
@Singleton
class TelegramThumbDownloader
    @Inject
    constructor(
        private val remoteResolver: TelegramRemoteResolver,
        private val fileClient: TelegramFileClient,
    ) {
        companion object {
            private const val TAG = "TelegramThumbDownloader"
            private const val THUMB_DOWNLOAD_PRIORITY = 8 // Low priority - background
        }

        /**
         * Ensures a thumbnail is downloaded via proxy cache.
         *
         * This method:
         * 1. Resolves remoteId to get current thumbFileId and thumbLocalPath
         * 2. If thumbLocalPath already exists → return it
         * 3. If thumbFileId available but not downloaded → trigger download and return null
         * 4. If no thumbFileId → return null
         *
         * **Non-blocking**: Triggers download but returns immediately. Caller should observe
         * `fileUpdates` Flow to detect when download completes.
         *
         * **Idempotent**: Safe to call multiple times. The proxy handles duplicate download requests.
         *
         * @param remoteId The remote identifier (chatId + messageId)
         * @return Local file path if thumbnail already cached, null if download in progress or unavailable
         */
        suspend fun ensureThumbDownloaded(remoteId: TelegramRemoteId): String? {
            UnifiedLog.d(TAG) {
                "ensureThumbDownloaded: chatId=***${remoteId.chatId.toString().takeLast(3)}, " +
                    "messageId=***${remoteId.messageId.toString().takeLast(3)}"
            }

            // Step 1: Resolve to get current thumbFileId and local path
            val resolved = remoteResolver.resolveMedia(remoteId)
            if (resolved == null) {
                UnifiedLog.d(TAG) { "Could not resolve media (masked), no thumbnail available" }
                return null
            }

            // Step 2: If already downloaded, return path
            val thumbLocalPath = resolved.thumbLocalPath
            if (thumbLocalPath != null && thumbLocalPath.isNotBlank()) {
                UnifiedLog.d(TAG) { "Thumbnail already cached at: $thumbLocalPath" }
                return thumbLocalPath
            }

            // Step 3: If thumbFileId available, trigger download
            val thumbFileId = resolved.thumbFileId
            if (thumbFileId != null && thumbFileId > 0) {
                UnifiedLog.d(TAG) { "Triggering thumbnail download: thumbFileId=$thumbFileId (LOW priority)" }
                try {
                    fileClient.startDownload(
                        fileId = thumbFileId,
                        priority = THUMB_DOWNLOAD_PRIORITY,
                        offset = 0,
                        limit = 0, // Full file
                    )
                } catch (e: Exception) {
                    UnifiedLog.w(TAG) { "Failed to start thumbnail download: ${e.message}" }
                }
                return null // Download in progress, not yet available
            }

            // Step 4: No thumbFileId available
            UnifiedLog.d(TAG) { "No thumbFileId available for message (masked)" }
            return null
        }

        /**
         * Checks if a thumbnail is already downloaded (cached via proxy).
         *
         * This is a quick check that doesn't trigger any downloads.
         *
         * @param remoteId The remote identifier (chatId + messageId)
         * @return true if thumbnail is cached locally, false otherwise
         */
        suspend fun isThumbCached(remoteId: TelegramRemoteId): Boolean {
            val resolved = remoteResolver.resolveMedia(remoteId) ?: return false
            val thumbLocalPath = resolved.thumbLocalPath
            return thumbLocalPath != null && thumbLocalPath.isNotBlank()
        }

        /**
         * Ensures thumbnail download and returns both fileId and local path.
         *
         * This method:
         * 1. Resolves remoteId to get current thumbFileId and thumbLocalPath
         * 2. If thumbLocalPath already exists → return ThumbRequestResult(thumbFileId, localPath)
         * 3. If thumbFileId available but not downloaded → trigger download and return ThumbRequestResult(thumbFileId, null)
         * 4. If no thumbFileId → return ThumbRequestResult(null, null)
         *
         * **Non-blocking**: Triggers download but returns immediately. Use thumbFileId to observe
         * `fileUpdates` Flow to detect when download completes.
         *
         * **Idempotent**: Safe to call multiple times. The proxy handles duplicate download requests.
         *
         * **Privacy**: Does NOT log local paths, only logs boolean presence.
         *
         * @param remoteId The remote identifier (chatId + messageId)
         * @return ThumbRequestResult with thumbFileId and localPath (both nullable)
         */
        suspend fun ensureThumbDownload(remoteId: TelegramRemoteId): ThumbRequestResult {
            UnifiedLog.d(TAG) {
                "ensureThumbDownload: chatId=***${remoteId.chatId.toString().takeLast(3)}, " +
                    "messageId=***${remoteId.messageId.toString().takeLast(3)}"
            }

            // Step 1: Resolve to get current thumbFileId and local path
            val resolved = remoteResolver.resolveMedia(remoteId)
            if (resolved == null) {
                UnifiedLog.d(TAG) { "Could not resolve media (masked), no thumbnail available" }
                return ThumbRequestResult(thumbFileId = null, localPath = null)
            }

            val thumbFileId = resolved.thumbFileId
            val thumbLocalPath = resolved.thumbLocalPath

            // Step 2: If already downloaded, return both fileId and path
            if (thumbLocalPath != null && thumbLocalPath.isNotBlank()) {
                UnifiedLog.d(TAG) { "Thumbnail already cached: thumbFileId=$thumbFileId, hasLocalPath=true" }
                return ThumbRequestResult(thumbFileId = thumbFileId, localPath = thumbLocalPath)
            }

            // Step 3: If thumbFileId available, trigger download
            if (thumbFileId != null && thumbFileId > 0) {
                UnifiedLog.d(TAG) { "Triggering thumbnail download: thumbFileId=$thumbFileId (LOW priority), hasLocalPath=false" }
                try {
                    fileClient.startDownload(
                        fileId = thumbFileId,
                        priority = THUMB_DOWNLOAD_PRIORITY,
                        offset = 0,
                        limit = 0, // Full file
                    )
                } catch (e: Exception) {
                    UnifiedLog.w(TAG) { "Failed to start thumbnail download: ${e.message}" }
                }
                return ThumbRequestResult(thumbFileId = thumbFileId, localPath = null)
            }

            // Step 4: No thumbFileId available
            UnifiedLog.d(TAG) { "No thumbFileId available for message (masked)" }
            return ThumbRequestResult(thumbFileId = null, localPath = null)
        }
    }
