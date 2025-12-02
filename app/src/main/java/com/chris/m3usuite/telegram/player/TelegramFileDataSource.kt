package com.chris.m3usuite.telegram.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import com.chris.m3usuite.telegram.core.T_TelegramFileDownloader
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import com.chris.m3usuite.telegram.logging.TelegramLogRepository
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException

/**
 * DataSource for Telegram files using TDLib + FileDataSource for Zero-Copy Streaming.
 *
 * **Phase D+ RemoteId-First URL Format:**
 * `tg://file/<fileIdOrZero>?chatId=...&messageId=...&remoteId=...&uniqueId=...`
 *
 * **Resolution Strategy:**
 * 1. Parse URL to extract fileId, remoteId, chatId, messageId
 * 2. If fileId is valid (> 0), use it directly (fast path)
 * 3. If fileId is 0 or invalid, resolve via getRemoteFile(remoteId)
 * 4. Call ensureFileReady() to ensure TDLib has downloaded prefix
 * 5. Delegate to FileDataSource for actual I/O
 *
 * **Zero-Copy Architecture:**
 * - TDLib downloads file to its cache directory on disk
 * - This DataSource delegates to Media3's FileDataSource for all I/O
 * - No ByteArray buffers, no custom position tracking
 * - ExoPlayer/FileDataSource handles seeking and scrubbing
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
    private var remoteId: String? = null
    private var uniqueId: String? = null
    private var durationMs: Long? = null
    private var fileSizeBytes: Long? = null

    companion object {
        /**
         * Minimum prefix size to request from TDLib (256 KB).
         * Ensures header and initial data are available for container parsing.
         */
        const val MIN_PREFIX_BYTES = 256 * 1024L

        /**
         * Timeout for remoteId to fileId resolution (10 seconds).
         * This is shorter than ensureFileReady() timeout since resolution is just an API call,
         * not a file download operation.
         */
        const val REMOTE_ID_RESOLUTION_TIMEOUT_MS = 10_000L
    }

    override fun addTransferListener(transferListener: TransferListener) {
        this.transferListener = transferListener
    }

    /**
     * Open the data source for the given DataSpec.
     * Parses the tg:// URL, resolves fileId if needed, ensures TDLib has the file ready,
     * and delegates to FileDataSource.
     *
     * **Phase D+ URL Format:**
     * `tg://file/<fileIdOrZero>?chatId=<chatId>&messageId=<messageId>&remoteId=<remoteId>&uniqueId=<uniqueId>`
     *
     * **Legacy URL Format (still supported):**
     * `tg://file/<fileId>?chatId=<chatId>&messageId=<messageId>`
     *
     * **IMPORTANT - Blocking Behavior:**
     * This method uses runBlocking() to ensure the TDLib file is ready, which blocks
     * the calling thread. ExoPlayer typically calls open() from background threads,
     * but callers should be aware of this blocking behavior.
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
        var fileIdInt = fileIdStr.toIntOrNull() ?: 0

        // Extract query parameters
        val chatIdStr = uri.getQueryParameter("chatId")
        val messageIdStr = uri.getQueryParameter("messageId")
        val remoteIdParam = uri.getQueryParameter("remoteId")
        val uniqueIdParam = uri.getQueryParameter("uniqueId")
        val durationMsStr = uri.getQueryParameter("durationMs")
        val fileSizeBytesStr = uri.getQueryParameter("fileSizeBytes")

        if (chatIdStr.isNullOrBlank() || messageIdStr.isNullOrBlank()) {
            throw IOException("Missing chatId or messageId parameter in Telegram URI: $uri")
        }

        chatId = chatIdStr.toLongOrNull()
            ?: throw IOException("Invalid chatId parameter in Telegram URI: $uri")
        messageId = messageIdStr.toLongOrNull()
            ?: throw IOException("Invalid messageId parameter in Telegram URI: $uri")
        remoteId = remoteIdParam
        uniqueId = uniqueIdParam
        durationMs = durationMsStr?.toLongOrNull()
        fileSizeBytes = fileSizeBytesStr?.toLongOrNull()

        TelegramLogRepository.info(
            source = "TelegramFileDataSource",
            message = "opening",
            details =
                mapOf(
                    "fileIdFromPath" to fileIdStr,
                    "chatId" to chatId.toString(),
                    "messageId" to messageId.toString(),
                    "remoteId" to (remoteIdParam ?: "none"),
                    "uniqueId" to (uniqueIdParam ?: "none"),
                    "durationMs" to (durationMs?.toString() ?: "none"),
                    "fileSizeBytes" to (fileSizeBytes?.toString() ?: "none"),
                    "dataSpecPosition" to dataSpec.position.toString(),
                ),
        )

        // Phase D+: RemoteId-first resolution
        // If fileId is invalid (0 or negative), try to resolve via remoteId
        if (fileIdInt <= 0 && !remoteIdParam.isNullOrBlank()) {
            TelegramLogRepository.debug(
                source = "TelegramFileDataSource",
                message = "fileId invalid, resolving via remoteId",
                details = mapOf("remoteId" to remoteIdParam),
            )

            val resolvedFileId =
                try {
                    runBlocking {
                        // Use withTimeoutOrNull to prevent ANR if resolution takes too long
                        // Default timeout matches ensureFileReady() timeout for consistency
                        kotlinx.coroutines.withTimeoutOrNull(REMOTE_ID_RESOLUTION_TIMEOUT_MS) {
                            serviceClient.downloader().resolveRemoteFileId(remoteIdParam)
                        }
                    }
                } catch (e: Exception) {
                    TelegramLogRepository.error(
                        source = "TelegramFileDataSource",
                        message = "Failed to resolve remoteId",
                        exception = e,
                        details = mapOf("remoteId" to remoteIdParam),
                    )
                    null
                }

            if (resolvedFileId != null && resolvedFileId > 0) {
                fileIdInt = resolvedFileId
                TelegramLogRepository.info(
                    source = "TelegramFileDataSource",
                    message = "Resolved remoteId to fileId",
                    details =
                        mapOf(
                            "remoteId" to remoteIdParam,
                            "resolvedFileId" to resolvedFileId.toString(),
                        ),
                )
            } else {
                throw IOException(
                    "Cannot resolve fileId: path fileId=$fileIdStr is invalid and " +
                        "remoteId resolution failed for remoteId=$remoteIdParam",
                )
            }
        } else if (fileIdInt <= 0) {
            // No remoteId available and fileId is invalid
            throw IOException(
                "Invalid fileId in Telegram URI: $uri (fileId=$fileIdStr must be positive, " +
                    "or remoteId must be provided for resolution)",
            )
        }

        fileId = fileIdInt

        // Notify TransferListener that TDLib preparation phase is starting
        transferListener?.onTransferStart(this, dataSpec, /* isNetwork = */ true)

        // Ensure TDLib has the file ready with sufficient prefix
        // Phase D+ Fix: If initial attempt fails with 404, try remoteId resolution
        var localPath: String? = null
        var lastException: Exception? = null

        // Determine mode based on dataSpec position
        val ensureMode =
            if (dataSpec.position == 0L) {
                T_TelegramFileDownloader.EnsureFileReadyMode.INITIAL_START
            } else {
                T_TelegramFileDownloader.EnsureFileReadyMode.SEEK
            }

        try {
            localPath =
                runBlocking {
                    val downloader = serviceClient.downloader()
                    downloader.ensureFileReady(
                        fileId = fileIdInt,
                        startPosition = dataSpec.position,
                        minBytes = MIN_PREFIX_BYTES,
                        mode = ensureMode,
                        fileSizeBytes = fileSizeBytes, // Pass from URL parameters
                    )
                }
        } catch (e: Exception) {
            // Check if this is a 404 error and we have a remoteId to fall back to
            if (e.message?.contains("404") == true && !remoteIdParam.isNullOrBlank()) {
                TelegramLogRepository.warn(
                    source = "TelegramFileDataSource",
                    message = "fileId returned 404, attempting remoteId resolution",
                    details =
                        mapOf(
                            "staleFileId" to fileIdInt.toString(),
                            "remoteId" to remoteIdParam,
                        ),
                )

                // Try to resolve via remoteId
                val resolvedFileId =
                    try {
                        runBlocking {
                            kotlinx.coroutines.withTimeoutOrNull(REMOTE_ID_RESOLUTION_TIMEOUT_MS) {
                                serviceClient.downloader().resolveRemoteFileId(remoteIdParam)
                            }
                        }
                    } catch (resolveEx: Exception) {
                        TelegramLogRepository.error(
                            source = "TelegramFileDataSource",
                            message = "remoteId resolution failed during 404 fallback: ${resolveEx.message}",
                            details = mapOf("remoteId" to remoteIdParam),
                        )
                        null
                    }

                if (resolvedFileId != null && resolvedFileId > 0) {
                    TelegramLogRepository.info(
                        source = "TelegramFileDataSource",
                        message = "remoteId resolved to new fileId after 404",
                        details =
                            mapOf(
                                "staleFileId" to fileIdInt.toString(),
                                "newFileId" to resolvedFileId.toString(),
                                "remoteId" to remoteIdParam,
                            ),
                    )

                    // Use local variable for candidate fileId; only update instance vars on success
                    val candidateFileId = resolvedFileId
                    try {
                        localPath =
                            runBlocking {
                                val downloader = serviceClient.downloader()
                                downloader.ensureFileReady(
                                    fileId = candidateFileId,
                                    startPosition = dataSpec.position,
                                    minBytes = MIN_PREFIX_BYTES,
                                    mode = ensureMode,
                                    fileSizeBytes = fileSizeBytes,
                                )
                            }
                        // Only update instance variables if ensureFileReady succeeds
                        fileIdInt = candidateFileId
                        fileId = candidateFileId
                    } catch (retryEx: Exception) {
                        lastException = retryEx
                    }
                } else {
                    // Resolution failed or returned same/invalid fileId
                    lastException = e
                }
            } else {
                // Not a 404 or no remoteId available
                lastException = e
            }
        }

        // Check if we succeeded
        if (localPath == null) {
            // Notify TransferListener that TDLib preparation phase failed
            transferListener?.onTransferEnd(this, dataSpec, /* isNetwork = */ true)

            val errorException = lastException ?: IOException("Failed to prepare Telegram file: unknown error")
            TelegramLogRepository.error(
                source = "TelegramFileDataSource",
                message = "Failed to ensure file ready",
                exception = errorException,
                details =
                    mapOf(
                        "fileId" to fileIdInt.toString(),
                        "remoteId" to (remoteIdParam ?: "none"),
                        "position" to dataSpec.position.toString(),
                    ),
            )
            // Reset state variables to avoid partial initialization
            resetState()
            throw IOException("Failed to prepare Telegram file: ${errorException.message}", errorException)
        }

        // Notify TransferListener that TDLib preparation phase completed successfully
        transferListener?.onTransferEnd(this, dataSpec, /* isNetwork = */ true)

        // Phase D+: Get correct file size from TDLib (never use downloadedPrefixSize)
        // This ensures ExoPlayer sees a stable file size for seeking and progress tracking
        val tdlibFileInfo =
            try {
                runBlocking {
                    val downloader = serviceClient.downloader()
                    downloader.getFileInfo(fileIdInt)
                }
            } catch (e: Exception) {
                TelegramLogRepository.warn(
                    source = "TelegramFileDataSource",
                    message = "Failed to get TDLib file info: ${e.message}, will use local file size",
                    details = mapOf("fileId" to fileIdInt.toString()),
                )
                null
            }

        val correctFileSize =
            tdlibFileInfo?.expectedSize?.toLong()
                ?: run {
                    // Fallback to local file size if TDLib query fails
                    TelegramLogRepository.debug(
                        source = "TelegramFileDataSource",
                        message = "Using local file size as fallback",
                        details = mapOf("fileId" to fileIdInt.toString()),
                    )
                    val file = File(localPath)
                    if (file.exists()) file.length() else C.LENGTH_UNSET.toLong()
                }

        // Build file:// URI
        val file = File(localPath)
        if (!file.exists()) {
            throw IOException("TDLib local file not found: $localPath")
        }

        resolvedUri = Uri.fromFile(file)

        // Create FileDataSource and open with correct file size
        // IMPORTANT: Use correctFileSize from TDLib, NOT downloadedPrefixSize
        val fileDataSource = FileDataSource()
        transferListener?.let { fileDataSource.addTransferListener(it) }
        delegate = fileDataSource

        // Build DataSpec with correct file size for ExoPlayer
        val fileDataSpec =
            dataSpec
                .buildUpon()
                .setUri(resolvedUri!!)
                .setLength(if (correctFileSize > 0) correctFileSize else C.LENGTH_UNSET.toLong())
                .build()

        TelegramLogRepository.info(
            source = "TelegramFileDataSource",
            message = "opened",
            details =
                mapOf(
                    "fileId" to fileIdInt.toString(),
                    "remoteId" to (remoteIdParam ?: "none"),
                    "localPath" to localPath,
                    "dataSpecPosition" to dataSpec.position.toString(),
                    "correctFileSize" to correctFileSize.toString(),
                    "localFileSize" to file.length().toString(),
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
    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = delegate?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

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
            resetState()
        }

        TelegramLogRepository.debug(
            source = "TelegramFileDataSource",
            message = "closed",
        )
    }

    private fun resetState() {
        delegate = null
        resolvedUri = null
        fileId = null
        chatId = null
        messageId = null
        remoteId = null
        uniqueId = null
        durationMs = null
        fileSizeBytes = null
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
