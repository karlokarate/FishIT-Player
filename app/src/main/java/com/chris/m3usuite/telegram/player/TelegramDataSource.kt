package com.chris.m3usuite.telegram.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.chris.m3usuite.telegram.core.StreamingConfig
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.TimeoutException

/**
 * DataSource for streaming Telegram files via TDLib with **Windowed Zero-Copy Streaming**.
 * Handles tg://file/<fileId>?chatId=...&messageId=... URLs.
 *
 * This is a COMPLETE implementation for the Streaming Cluster as specified in:
 * - .github/tdlibAgent.md (Section 4.2: TelegramDataSource - Zero-Copy Streaming)
 * - docs/TDLIB_TASK_GROUPING.md (Cluster C: Streaming / DataSource, Tasks 49-56)
 *
 * **Windowed Zero-Copy Streaming:**
 * - Downloads only a window (currently 16MB) of the file around current playback position
 * - Old windows are discarded when seeking, new windows opened at target position
 * - `read()` writes **directly** from TDLib cache into ExoPlayer's buffer without extra copies
 * - Automatic window transitions when approaching prefetch margin
 * - Only for direct media files: MOVIE, EPISODE, CLIP, AUDIO
 * - RAR_ARCHIVE and other containers use full-download path (not this DataSource)
 *
 * Key responsibilities:
 * - Parse tg:// URLs with fileId, chatId, messageId parameters
 * - Stream Telegram files via T_TelegramFileDownloader with windowing
 * - Properly inform TransferListener of transfer lifecycle (onTransferStart/End)
 * - Handle read operations with proper EOF detection
 * - Manage window transitions on seek or approaching window end
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

    /**
     * The Telegram chat ID associated with the file to be streamed.
     *
     * This is parsed from the `chatId` query parameter in the Telegram file URL
     * (e.g., `tg://file/<fileId>?chatId=<chatId>&messageId=<messageId>`).
     * It is set during the [open] method when the DataSource is initialized.
     */
    private var chatId: Long? = null

    /**
     * The Telegram message ID associated with the file to be streamed.
     *
     * This is parsed from the `messageId` query parameter in the Telegram file URL
     * (e.g., `tg://file/<fileId>?chatId=<chatId>&messageId=<messageId>`).
     * It is set during the [open] method when the DataSource is initialized.
     */
    private var messageId: Long? = null
    private var opened = false
    private var transferListener: TransferListener? = null

    // Window state for streaming
    private var windowStart: Long = 0
    private var windowSize: Long = StreamingConfig.TELEGRAM_STREAM_WINDOW_BYTES

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

        // Log stream start
        TelegramLogRepository.logStreamingActivity(
            fileId = fileId!!.toIntOrNull() ?: 0,
            action = "opening",
            details = mapOf("uri" to uri.toString()),
        )

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
            TelegramLogRepository.error(
                source = "TelegramDataSource",
                message = "Failed to parse Telegram URI",
                exception = e,
            )
            throw IOException("Failed to parse Telegram URI parameters: $uri - ${e.message}", e)
        }

        // Get downloader from service client
        val downloader =
            try {
                serviceClient.downloader()
            } catch (e: Exception) {
                throw IOException("Telegram service not available: ${e.message}", e)
            }

        // Get file size from TDLib
        totalSize =
            runBlocking {
                try {
                    downloader.getFileSize(fileId!!)
                } catch (e: Exception) {
                    TelegramLogRepository.error(
                        source = "TelegramDataSource",
                        message = "Failed to get file size",
                        exception = e,
                        details = mapOf("fileId" to fileId!!),
                    )
                    throw IOException("Failed to get file size for fileId=$fileId: ${e.message}", e)
                }
            }

        // Calculate position and bytes remaining
        position = dataSpec.position
        bytesRemaining =
            if (totalSize >= 0) {
                (totalSize - position).coerceAtLeast(0)
            } else {
                C.LENGTH_UNSET.toLong()
            }

        // Initialize window at current position
        windowStart = position
        windowSize = StreamingConfig.TELEGRAM_STREAM_WINDOW_BYTES

        // Ensure initial window is prepared
        val fileIdInt = fileId!!.toIntOrNull() ?: throw IOException("Invalid file ID: $fileId")
        val windowReady =
            runBlocking {
                try {
                    downloader.ensureWindow(fileIdInt, windowStart, windowSize)
                } catch (e: Exception) {
                    TelegramLogRepository.error(
                        source = "TelegramDataSource",
                        message = "Failed to prepare window",
                        exception = e,
                        details =
                            mapOf(
                                "fileId" to fileId!!,
                                "windowStart" to windowStart.toString(),
                            ),
                    )
                    throw IOException("Failed to prepare window for fileId=$fileId: ${e.message}", e)
                }
            }

        if (!windowReady) {
            throw IOException("Failed to start windowed download for fileId=$fileId")
        }

        opened = true

        // Notify transfer listener that transfer has started
        transferListener?.onTransferStart(this, dataSpec, true)

        // Log successful open
        TelegramLogRepository.logStreamingActivity(
            fileId = fileId!!.toIntOrNull() ?: 0,
            action = "opened",
            details =
                mapOf(
                    "size" to totalSize.toString(),
                    "position" to position.toString(),
                ),
        )

        return if (bytesRemaining >= 0) bytesRemaining else C.LENGTH_UNSET.toLong()
    }

    /**
     * Read data from the Telegram file into the provided buffer with **Windowed Zero-Copy**.
     * This method blocks until data is available or EOF is reached.
     *
     * **Window Transition Logic:**
     * - Checks if current position is within active window
     * - If approaching prefetch margin, opens new window
     * - Directly reads into buffer without intermediate copies
     *
     * @param buffer Destination buffer (provided by ExoPlayer)
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
        val fileIdInt = fid.toIntOrNull() ?: throw IOException("Invalid file ID: $fid")

        // Check if we've reached the end
        if (bytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT
        }

        // Check if we need to transition to a new window
        val windowEnd = windowStart + windowSize
        val distanceToWindowEnd = windowEnd - position

        if (distanceToWindowEnd < StreamingConfig.TELEGRAM_STREAM_PREFETCH_MARGIN || position < windowStart) {
            // Approaching window end or seeking backward, open new window
            val newWindowStart = position

            TelegramLogRepository.logStreamingActivity(
                fileId = fileIdInt,
                action = "window_transition",
                details =
                    mapOf(
                        "old_start" to windowStart.toString(),
                        "new_start" to newWindowStart.toString(),
                        "position" to position.toString(),
                    ),
            )

            windowStart = newWindowStart

            // Ensure new window with timeout to prevent indefinite blocking
            val downloader =
                try {
                    serviceClient.downloader()
                } catch (e: Exception) {
                    throw IOException("Telegram service not available: ${e.message}", e)
                }

            runBlocking {
                try {
                    // Add 30 second timeout to prevent indefinite blocking during window setup failures
                    withTimeout(30_000L) {
                        downloader.ensureWindow(fileIdInt, windowStart, windowSize)
                    }
                } catch (e: TimeoutException) {
                    TelegramLogRepository.error(
                        source = "TelegramDataSource",
                        message = "Window transition timed out",
                        exception = e,
                        details =
                            mapOf(
                                "fileId" to fid,
                                "position" to position.toString(),
                            ),
                    )
                    throw IOException("Window transition timed out at position $position for fileId $fid", e)
                } catch (e: Exception) {
                    TelegramLogRepository.error(
                        source = "TelegramDataSource",
                        message = "Failed to transition window",
                        exception = e,
                        details =
                            mapOf(
                                "fileId" to fid,
                                "position" to position.toString(),
                            ),
                    )
                    throw IOException("Failed to transition window at position $position for fileId $fid: ${e.message}", e)
                }
            }
        }

        // Calculate how many bytes to read
        val bytesToRead =
            if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
                readLength
            } else {
                minOf(readLength.toLong(), bytesRemaining).toInt()
            }

        // Get downloader and read chunk
        val downloader =
            try {
                serviceClient.downloader()
            } catch (e: Exception) {
                throw IOException("Telegram service not available: ${e.message}", e)
            }

        val bytesRead =
            runBlocking {
                try {
                    // Add 10 second timeout for read operations
                    withTimeout(10_000L) {
                        downloader.readFileChunk(fid, position, buffer, offset, bytesToRead)
                    }
                } catch (e: TimeoutException) {
                    throw IOException(
                        "Read operation timed out for Telegram file $fid at position $position",
                        e,
                    )
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
     * Close the data source and release all resources including window state.
     * This method is idempotent - safe to call multiple times.
     */
    override fun close() {
        if (!opened) return

        val closingFileId = fileId
        val closingFileIdInt = closingFileId?.toIntOrNull()
        opened = false

        // Cancel any ongoing downloads and ensure file handles are cleaned up
        if (closingFileIdInt != null) {
            runCatching {
                runBlocking {
                    val downloader = serviceClient.downloader()
                    // Explicit cleanup: ensure file handles are removed even if cancellation fails
                    downloader.cleanupFileHandle(closingFileIdInt)
                    downloader.cancelDownload(closingFileIdInt)
                }
            }
        }

        // Reset state including window state
        val spec = currentDataSpec
        currentUri = null
        currentDataSpec = null
        fileId = null
        chatId = null
        messageId = null
        position = 0
        bytesRemaining = C.LENGTH_UNSET.toLong()
        totalSize = C.LENGTH_UNSET.toLong()
        windowStart = 0
        windowSize = StreamingConfig.TELEGRAM_STREAM_WINDOW_BYTES

        // Notify transfer listener that transfer has ended
        if (spec != null) {
            transferListener?.onTransferEnd(this, spec, true)
        }

        // Log stream close
        closingFileId?.let { fid ->
            TelegramLogRepository.logStreamingActivity(
                fileId = fid.toIntOrNull() ?: 0,
                action = "closed",
                details = null,
            )
        }
    }
}
