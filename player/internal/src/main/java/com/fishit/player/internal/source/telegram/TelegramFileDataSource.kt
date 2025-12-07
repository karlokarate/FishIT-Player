package com.fishit.player.internal.source.telegram

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.pipeline.telegram.tdlib.TelegramTdlibClient
import com.fishit.player.pipeline.telegram.tdlib.TelegramFileException
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException

/**
 * Media3 DataSource for Telegram files using TDLib + Zero-Copy Streaming.
 * 
 * **v1 Component Mapping:**
 * - Adapted from v1 `TelegramFileDataSource`
 * - Uses `TelegramTdlibClient` abstraction instead of direct TdlClient
 * - Delegates to FileDataSource for actual I/O (zero-copy)
 * 
 * **v2 Integration:**
 * - Belongs in `:player:internal` (not in pipeline)
 * - Used by `InternalPlaybackSourceResolver` for Telegram content
 * - RemoteId-first URL semantics for cross-session stability
 * 
 * **URL Format:**
 * ```
 * tg://file/<fileId>?chatId=<chatId>&messageId=<messageId>&remoteId=<remoteId>&uniqueId=<uniqueId>
 * ```
 * 
 * **Zero-Copy Architecture:**
 * - TDLib downloads file to its cache directory
 * - This DataSource delegates to Media3's FileDataSource for all I/O
 * - No ByteArray buffers, no custom position tracking
 * - ExoPlayer/FileDataSource handles seeking and scrubbing
 * 
 * **Resolution Strategy:**
 * 1. Parse URL to extract fileId, remoteId, chatId, messageId
 * 2. If fileId is valid (> 0), use it directly (fast path - same session)
 * 3. If fileId is 0 or invalid, resolve via remoteId
 * 4. Ensure file is ready via TDLib
 * 5. Delegate to FileDataSource for actual I/O
 * 
 * @param tdlibClient TDLib client for file access
 */
class TelegramFileDataSource(
    private val tdlibClient: TelegramTdlibClient
) : DataSource {
    
    companion object {
        private const val TAG = "TelegramFileDataSource"
        private const val DOWNLOAD_PRIORITY = 32 // High priority for streaming
    }
    
    private var delegate: FileDataSource? = null
    private var transferListener: TransferListener? = null
    
    // Original telegram URI info for logging
    private var fileId: Int? = null
    private var chatId: Long? = null
    private var messageId: Long? = null
    private var remoteId: String? = null
    
    override fun addTransferListener(transferListener: TransferListener) {
        this.transferListener = transferListener
    }
    
    /**
     * Open the data source for the given DataSpec.
     * 
     * Parses the tg:// URL, resolves fileId if needed, ensures TDLib has the file ready,
     * and delegates to FileDataSource.
     * 
     * **IMPORTANT - Blocking Behavior:**
     * This method uses runBlocking() to ensure the TDLib file is ready.
     * ExoPlayer typically calls open() from background threads.
     * 
     * @param dataSpec The DataSpec to open
     * @return The number of bytes remaining, or C.LENGTH_UNSET if unknown
     * @throws IOException if the URL is invalid or file access fails
     */
    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        
        // Validate URI scheme and host
        if (uri.scheme != "tg" || uri.host != "file") {
            throw IOException("Invalid Telegram URI scheme/host: $uri (expected tg://file/...)")
        }
        
        try {
            // Parse URI components
            val pathSegments = uri.pathSegments
            if (pathSegments.isEmpty()) {
                throw IOException("Invalid Telegram URI: missing file ID in path")
            }
            
            fileId = pathSegments[0].toIntOrNull()
            chatId = uri.getQueryParameter("chatId")?.toLongOrNull()
            messageId = uri.getQueryParameter("messageId")?.toLongOrNull()
            remoteId = uri.getQueryParameter("remoteId")
            
            UnifiedLog.d(TAG, "Opening Telegram file: fileId=$fileId, chatId=$chatId, messageId=$messageId, remoteId=$remoteId")
            
            // Resolve fileId if needed
            val resolvedFileId = runBlocking {
                resolveFileId(fileId, remoteId)
            }
            
            // Ensure file is ready for playback
            val fileLocation = runBlocking {
                tdlibClient.ensureFileReady(
                    fileId = resolvedFileId,
                    priority = DOWNLOAD_PRIORITY,
                    offset = 0,
                    limit = 0 // Download entire file
                )
            }
            
            // Get local path
            val localPath = fileLocation.localPath
                ?: throw IOException("File not available locally: fileId=$resolvedFileId")
            
            if (!File(localPath).exists()) {
                throw IOException("File not found at local path: $localPath")
            }
            
            UnifiedLog.d(TAG, "File ready at: $localPath (size=${fileLocation.size} bytes)")
            
            // Create FileDataSource delegate and open it
            val fileDataSource = FileDataSource()
            transferListener?.let { fileDataSource.addTransferListener(it) }
            
            val fileUri = Uri.fromFile(File(localPath))
            val fileDataSpec = dataSpec.buildUpon()
                .setUri(fileUri)
                .build()
            
            delegate = fileDataSource
            return fileDataSource.open(fileDataSpec)
            
        } catch (e: TelegramFileException) {
            UnifiedLog.e(TAG, "Telegram file error", e)
            throw IOException("Failed to access Telegram file: ${e.message}", e)
        } catch (e: Exception) {
            UnifiedLog.e(TAG, "Failed to open Telegram file", e)
            throw IOException("Failed to open Telegram file: ${e.message}", e)
        }
    }
    
    /**
     * Resolve fileId from URI parameters.
     * 
     * Priority:
     * 1. Use fileId if valid (> 0)
     * 2. Resolve via remoteId if available
     * 3. Fail if neither is valid
     */
    private suspend fun resolveFileId(fileId: Int?, remoteId: String?): Int {
        return when {
            fileId != null && fileId > 0 -> {
                UnifiedLog.d(TAG, "Using fileId from URI: $fileId")
                fileId
            }
            remoteId != null && remoteId.isNotEmpty() -> {
                UnifiedLog.d(TAG, "Resolving fileId via remoteId: $remoteId")
                tdlibClient.resolveFileByRemoteId(remoteId)
            }
            else -> {
                throw TelegramFileException("No valid fileId or remoteId in URI")
            }
        }
    }
    
    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val d = delegate ?: throw IOException("DataSource not opened")
        return d.read(buffer, offset, length)
    }
    
    override fun getUri(): Uri? {
        return delegate?.uri
    }
    
    @Throws(IOException::class)
    override fun close() {
        try {
            delegate?.close()
        } finally {
            delegate = null
            fileId = null
            chatId = null
            messageId = null
            remoteId = null
        }
    }
}

/**
 * Factory for creating TelegramFileDataSource instances.
 * 
 * This factory is registered with `InternalPlaybackSourceResolver`
 * to handle tg:// URIs.
 */
class TelegramFileDataSourceFactory(
    private val tdlibClient: TelegramTdlibClient
) : DataSource.Factory {
    
    override fun createDataSource(): DataSource {
        return TelegramFileDataSource(tdlibClient)
    }
}
