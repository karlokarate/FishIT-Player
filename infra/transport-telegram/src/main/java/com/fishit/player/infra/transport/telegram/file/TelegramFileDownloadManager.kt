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
