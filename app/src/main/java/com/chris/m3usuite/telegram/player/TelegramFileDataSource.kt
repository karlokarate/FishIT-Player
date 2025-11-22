package com.chris.m3usuite.telegram.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException

/**
 * DataSource for Telegram files using TDLib + FileDataSource for Zero-Copy Streaming.
 * Handles tg://file/<fileId>?chatId=...&messageId=... URLs.
 *
 * **Zero-Copy Architecture:**
 * - TDLib downloads file to its cache directory on disk
 * - This DataSource delegates to Media3's FileDataSource for all I/O
 * - No ByteArray buffers, no custom position tracking
 * - ExoPlayer/FileDataSource handles seeking and scrubbing
 * - TDLib handles chunking/range downloads via ensureFileReady()
 *
 * **Flow:**
 * 1. Parse tg:// URI to extract fileId, chatId, messageId
 * 2. Call ensureFileReady() to ensure TDLib has downloaded prefix
 * 3. Get localPath from TDLib
 * 4. Create file:// URI and delegate to FileDataSource
 * 5. All read/seek operations handled by FileDataSource
 *
 * This eliminates:
 * - Custom ringbuffer ByteArray
 * - Window state management (windowStart, windowSize)
 * - Custom position arithmetic
 * - Duplicate file copies on disk
 *
 * @param serviceClient T_TelegramServiceClient for TDLib access
 */
@UnstableApi
class TelegramFileDataSource(
    private val serviceClient: T_TelegramServiceClient,
) : DataSource {
    private var delegate: FileDataSource? = null
    private var resolvedUri: Uri? = null
    private var transferListener: TransferListener? = null

    // Original telegram URI info for logging
    private var fileId: Int? = null
    private var chatId: Long? = null
    private var messageId: Long? = null

    companion object {
        /**
         * Minimum prefix size to request from TDLib (256 KB).
         * Ensures header and initial data are available for container parsing.
         */
        const val MIN_PREFIX_BYTES = 256 * 1024L
    }

    override fun addTransferListener(transferListener: TransferListener) {
        this.transferListener = transferListener
    }

    /**
     * Open the data source for the given DataSpec.
     * Parses the tg:// URL, ensures TDLib has the file ready, and delegates to FileDataSource.
     *
     * URL format: tg://file/<fileId>?chatId=<chatId>&messageId=<messageId>
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

        // Extract fileId from path segments
        val segments = uri.pathSegments
        if (segments.isEmpty()) {
            throw IOException("Missing file ID in Telegram URI: $uri")
        }

        val fileIdStr = segments[0]
        val fileIdInt = fileIdStr.toIntOrNull()
        if (fileIdInt == null || fileIdInt <= 0) {
            throw IOException("Invalid fileId in Telegram URI: $uri (fileId=$fileIdStr must be a positive integer)")
        }
        fileId = fileIdInt

        // Extract chatId and messageId from query parameters
        val chatIdStr = uri.getQueryParameter("chatId")
        val messageIdStr = uri.getQueryParameter("messageId")

        if (chatIdStr.isNullOrBlank() || messageIdStr.isNullOrBlank()) {
            throw IOException("Missing chatId or messageId parameter in Telegram URI: $uri")
        }

        chatId = chatIdStr.toLongOrNull()
            ?: throw IOException("Invalid chatId parameter in Telegram URI: $uri")
        messageId = messageIdStr.toLongOrNull()
            ?: throw IOException("Invalid messageId parameter in Telegram URI: $uri")

        TelegramLogRepository.info(
            source = "TelegramFileDataSource",
            message = "opening",
            details = mapOf(
                "fileId" to fileIdInt.toString(),
                "chatId" to chatId.toString(),
                "messageId" to messageId.toString(),
                "dataSpecPosition" to dataSpec.position.toString(),
            ),
        )

        // Ensure TDLib has the file ready with sufficient prefix
        val localPath = runBlocking {
            try {
                val downloader = serviceClient.downloader()
                downloader.ensureFileReady(
                    fileId = fileIdInt,
                    startPosition = dataSpec.position,
                    minBytes = MIN_PREFIX_BYTES,
                )
            } catch (e: Exception) {
                TelegramLogRepository.error(
                    source = "TelegramFileDataSource",
                    message = "Failed to ensure file ready",
                    exception = e,
                    details = mapOf(
                        "fileId" to fileIdInt.toString(),
                        "position" to dataSpec.position.toString(),
                    ),
                )
                // Reset state variables to avoid partial initialization
                delegate = null
                resolvedUri = null
                fileId = null
                chatId = null
                messageId = null
                throw IOException("Failed to prepare Telegram file: ${e.message}", e)
            }
        }

        // Build file:// URI
        val file = File(localPath)
        if (!file.exists()) {
            throw IOException("TDLib local file not found: $localPath")
        }

        resolvedUri = Uri.fromFile(file)

        // Create FileDataSource and open with same position/length
        val fileDataSource = FileDataSource()
        transferListener?.let { fileDataSource.addTransferListener(it) }
        delegate = fileDataSource

        val fileDataSpec = dataSpec.buildUpon().setUri(resolvedUri!!).build()

        TelegramLogRepository.info(
            source = "TelegramFileDataSource",
            message = "opened",
            details = mapOf(
                "fileId" to fileIdInt.toString(),
                "localPath" to localPath,
                "dataSpecPosition" to dataSpec.position.toString(),
                "fileSize" to file.length().toString(),
            ),
        )

        return fileDataSource.open(fileDataSpec)
    }

    /**
     * Read data from the file via FileDataSource.
     * All position tracking and buffering handled by FileDataSource.
     *
     * @param buffer Destination buffer
     * @param offset Offset in buffer to start writing
     * @param length Maximum number of bytes to read
     * @return Number of bytes read, or C.RESULT_END_OF_INPUT if EOF
     */
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return delegate?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
    }

    /**
     * Get the URI currently being read (file:// URI).
     *
     * @return Resolved file URI, or null if not opened
     */
    override fun getUri(): Uri? = resolvedUri

    /**
     * Close the data source and release all resources.
     */
    override fun close() {
        try {
            delegate?.close()
        } finally {
            delegate = null
            resolvedUri = null
            fileId = null
            chatId = null
            messageId = null
        }

        TelegramLogRepository.debug(
            source = "TelegramFileDataSource",
            message = "closed",
        )
    }
}

/**
 * Factory for creating TelegramFileDataSource instances.
 *
 * @param serviceClient T_TelegramServiceClient for TDLib access
 */
@UnstableApi
class TelegramFileDataSourceFactory(
    private val serviceClient: T_TelegramServiceClient,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = TelegramFileDataSource(serviceClient)
}
