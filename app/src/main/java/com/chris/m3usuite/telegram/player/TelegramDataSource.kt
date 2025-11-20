package com.chris.m3usuite.telegram.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * DataSource for streaming Telegram files via TDLib.
 * Handles tg://file/<fileId>?chatId=...&messageId=... URLs.
 *
 * This is a COMPLETE implementation for the Streaming Cluster as specified in:
 * - .github/tdlibAgent.md (Section 4.2: TelegramDataSource - Zero-Copy Streaming)
 * - docs/TDLIB_TASK_GROUPING.md (Cluster C: Streaming / DataSource, Tasks 49-56)
 *
 * Key responsibilities:
 * - Parse tg:// URLs with fileId, chatId, messageId parameters
 * - Stream Telegram files via T_TelegramFileDownloader from T_TelegramServiceClient
 * - Properly inform TransferListener of transfer lifecycle (onTransferStart/End)
 * - Handle read operations with proper EOF detection
 * - Clean up resources on close
 *
 * Following TDLib and Media3 best practices:
 * - Uses T_TelegramServiceClient for unified Telegram engine access
 * - Implements DataSource contract correctly for ExoPlayer/Media3
 * - Handles resume support via position/offset
 * - Provides proper error handling with IOException propagation
 */
@UnstableApi
class TelegramDataSource(
    private val context: Context,
    private val serviceClient: T_TelegramServiceClient,
) : DataSource {
    private var currentUri: Uri? = null
    private var currentDataSpec: DataSpec? = null
    private var position: Long = 0
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var totalSize: Long = C.LENGTH_UNSET.toLong()
    private var fileId: String? = null
    private var opened = false
    private var transferListener: TransferListener? = null

    override fun addTransferListener(transferListener: TransferListener) {
        this.transferListener = transferListener
    }

    /**
     * Open the data source for the given DataSpec.
     * Parses the tg:// URL and prepares for streaming.
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
        currentUri = uri
        currentDataSpec = dataSpec

        // Validate URI scheme and host
        if (uri.scheme != "tg" || uri.host != "file") {
            throw IOException("Invalid Telegram URI scheme/host: $uri (expected tg://file/...)")
        }

        // Extract fileId from path segments
        val segments = uri.pathSegments
        if (segments.isEmpty()) {
            throw IOException("Missing file ID in Telegram URI: $uri")
        }

        fileId = segments[0]

        // Extract chatId and messageId from query parameters
        try {
            val chatIdStr = uri.getQueryParameter("chatId")
            val messageIdStr = uri.getQueryParameter("messageId")

            if (chatIdStr.isNullOrBlank()) {
                throw IOException("Missing chatId parameter in Telegram URI: $uri")
            }
            if (messageIdStr.isNullOrBlank()) {
                throw IOException("Missing messageId parameter in Telegram URI: $uri")
            }

            chatId = chatIdStr.toLongOrNull()
                ?: throw IOException("Invalid chatId parameter in Telegram URI: $uri")
            messageId = messageIdStr.toLongOrNull()
                ?: throw IOException("Invalid messageId parameter in Telegram URI: $uri")
        } catch (e: Exception) {
            throw IOException("Failed to parse Telegram URI parameters: $uri - ${e.message}", e)
        }

        // Get downloader from service client
        val downloader = try {
            serviceClient.downloader()
        } catch (e: Exception) {
            throw IOException("Telegram service not available: ${e.message}", e)
        }

        // Get file size from TDLib
        totalSize = runBlocking {
            try {
                downloader.getFileSize(fileId!!)
            } catch (e: Exception) {
                throw IOException("Failed to get file size for fileId=$fileId: ${e.message}", e)
            }
        }

        // Calculate position and bytes remaining
        position = dataSpec.position
        bytesRemaining = if (totalSize >= 0) {
            (totalSize - position).coerceAtLeast(0)
        } else {
            C.LENGTH_UNSET.toLong()
        }

        opened = true

        // Notify transfer listener that transfer has started
        transferListener?.onTransferStart(this, dataSpec, true)

        return if (bytesRemaining >= 0) bytesRemaining else C.LENGTH_UNSET.toLong()
    }

    /**
     * Read data from the Telegram file into the provided buffer.
     * This method blocks until data is available or EOF is reached.
     *
     * @param buffer Destination buffer
     * @param offset Offset in buffer to start writing
     * @param readLength Maximum number of bytes to read
     * @return Number of bytes read, or C.RESULT_END_OF_INPUT if EOF
     * @throws IOException if read operation fails
     */
    override fun read(
        buffer: ByteArray,
        offset: Int,
        readLength: Int,
    ): Int {
        if (readLength == 0) return 0
        if (!opened) {
            throw IOException("TelegramDataSource.read() called when data source is not open (likely before open() or after close())")
        }

        val fid = fileId ?: throw IOException("No file ID available")

        // Check if we've reached the end
        if (bytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT
        }

        // Calculate how many bytes to read
        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            readLength
        } else {
            minOf(readLength.toLong(), bytesRemaining).toInt()
        }

        // Get downloader and read chunk
        val downloader = try {
            serviceClient.downloader()
        } catch (e: Exception) {
            throw IOException("Telegram service not available: ${e.message}", e)
        }

        val bytesRead = runBlocking {
            try {
                downloader.readFileChunk(fid, position, buffer, offset, bytesToRead)
            } catch (e: Exception) {
                throw IOException(
                    "Failed to read from Telegram file $fid at position $position: ${e.message}",
                    e,
                )
            }
        }

        // Handle EOF
        if (bytesRead <= 0) {
            return C.RESULT_END_OF_INPUT
        }

        // Update position and remaining bytes
        position += bytesRead.toLong()
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining = (bytesRemaining - bytesRead).coerceAtLeast(0)
        }

        // Notify transfer listener of bytes transferred
        currentDataSpec?.let { spec ->
            transferListener?.onBytesTransferred(this, spec, true, bytesRead)
        }

        return bytesRead
    }

    /**
     * Get the URI currently being read.
     *
     * @return Current URI, or null if not opened
     */
    override fun getUri(): Uri? = currentUri

    /**
     * Close the data source and release all resources.
     * This method is idempotent - safe to call multiple times.
     */
    override fun close() {
        if (!opened) return

        opened = false

        // Cancel any ongoing downloads
        fileId?.let { fid ->
            runCatching {
                runBlocking {
                    val downloader = serviceClient.downloader()
                    downloader.cancelDownload(fid)
                }
            }
        }

        // Reset state
        val spec = currentDataSpec
        currentUri = null
        currentDataSpec = null
        fileId = null
        chatId = null
        messageId = null
        position = 0
        bytesRemaining = C.LENGTH_UNSET.toLong()
        totalSize = C.LENGTH_UNSET.toLong()

        // Notify transfer listener that transfer has ended
        if (spec != null) {
            transferListener?.onTransferEnd(this, spec, true)
        }
    }
}
