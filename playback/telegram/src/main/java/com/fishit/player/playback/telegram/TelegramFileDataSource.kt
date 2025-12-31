package com.fishit.player.playback.telegram

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramFileClient
import com.fishit.player.infra.transport.telegram.TelegramRemoteId
import com.fishit.player.infra.transport.telegram.TelegramRemoteResolver
import com.fishit.player.infra.transport.telegram.api.TelegramFileException
import com.fishit.player.playback.telegram.config.TelegramFileReadyEnsurer
import com.fishit.player.playback.telegram.config.TelegramStreamingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Media3 DataSource for Telegram files using TDLib + Zero-Copy Streaming.
 *
 * **v2 Architecture:**
 * - Belongs in `:playback:telegram` (NOT in player:internal)
 * - Uses `TelegramRemoteResolver` for RemoteId-first resolution
 * - Uses `TelegramFileClient` from transport layer for all file operations
 * - Delegates to FileDataSource for actual I/O (zero-copy)
 * - Uses `TelegramFileReadyEnsurer` for non-blocking file readiness
 *
 * **URL Format:**
 * ```
 * tg://file/<fileId>?chatId=<chatId>&messageId=<messageId>&remoteId=<remoteId>
 * ```
 *
 * **Zero-Copy Architecture:**
 * - TDLib downloads file to its cache directory
 * - This DataSource delegates to Media3's FileDataSource for all I/O
 * - No ByteArray buffers, no custom position tracking
 * - ExoPlayer/FileDataSource handles seeking and scrubbing
 *
 * **Resolution Strategy (RemoteId-First):**
 * 1. Parse URL to extract fileId, remoteId, chatId, messageId
 * 2. If chatId + messageId available, resolve via TelegramRemoteResolver (PREFERRED - always fresh)
 * 3. Else if fileId is valid (> 0), use it directly (fast path - same session, may be stale)
 * 4. Else if remoteId available, resolve via TelegramFileClient.resolveRemoteId (fallback)
 * 5. Ensure file is ready via TelegramFileReadyEnsurer (non-blocking coroutine)
 * 6. Delegate to FileDataSource for actual I/O
 *
 * **Threading Model:**
 * - ExoPlayer calls open() from a background loader thread (not main)
 * - We use a dedicated IO coroutine scope to avoid blocking the main thread
 * - The latch-based approach allows ExoPlayer's thread to wait without runBlocking
 *
 * @param remoteResolver RemoteId resolver for chatId+messageId → fileId (SSOT)
 * @param fileClient Transport layer client for TDLib file operations (download, fallback resolve)
 * @param readyEnsurer Playback layer component for streaming readiness
 */
class TelegramFileDataSource(
    private val remoteResolver: TelegramRemoteResolver,
    private val fileClient: TelegramFileClient,
    private val readyEnsurer: TelegramFileReadyEnsurer,
) : DataSource {
    companion object {
        private const val TAG = "TelegramFileDataSource"

        /**
         * Maximum time to wait for file readiness in open(). This is a safety timeout;
         * TelegramFileReadyEnsurer has its own internal timeouts.
         */
        private const val OPEN_TIMEOUT_SECONDS = 120L
    }

    private var delegate: FileDataSource? = null
    private var transferListener: TransferListener? = null

    // Coroutine scope for async operations (cancelled on close)
    private var scope: CoroutineScope? = null

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
     * Parses the tg:// URL, resolves fileId if needed, ensures TDLib has the file ready via
     * TelegramFileReadyEnsurer, and delegates to FileDataSource.
     *
     * **Threading:**
     * - ExoPlayer calls this from a background loader thread (not main)
     * - We launch a coroutine on IO dispatcher and wait using a CountDownLatch
     * - This avoids runBlocking while still being compatible with DataSource API
     *
     * **Streaming:**
     * - Does NOT throw if file is not fully downloaded
     * - TelegramFileReadyEnsurer polls until streaming-ready (moov atom validated)
     * - Progressive download continues after open() returns
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
            val mimeType = uri.getQueryParameter("mimeType")
            val attemptMode = uri.getQueryParameter("attemptMode") // DIRECT_FIRST or BUFFERED_5MB

            UnifiedLog.d(TAG) {
                "Opening Telegram file: fileId=$fileId, chatId=$chatId, messageId=$messageId, " +
                    "mimeType=$mimeType, attemptMode=$attemptMode"
            }

            // Create scope for async operations
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            // Use latch-based waiting to avoid runBlocking while supporting DataSource API
            val resultHolder = AtomicReference<OpenResult>()
            val latch = CountDownLatch(1)

            scope?.let { s ->
                s.launchOpenOperation(
                    fileId = fileId,
                    remoteId = remoteId,
                    mimeType = mimeType,
                    attemptMode = attemptMode,
                    dataSpec = dataSpec,
                    resultHolder = resultHolder,
                    latch = latch,
                )
            }
                ?: throw IOException("Failed to create coroutine scope")

            // Wait for async operation (ExoPlayer calls from background thread, this is safe)
            val completed = latch.await(OPEN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!completed) {
                throw IOException(
                    "Timeout waiting for Telegram file readiness after ${OPEN_TIMEOUT_SECONDS}s",
                )
            }

            // Check result
            val result =
                resultHolder.get() ?: throw IOException("No result from file readiness check")

            return when (result) {
                is OpenResult.Success -> result.bytesRemaining
                is OpenResult.Error -> throw result.error
            }
        } catch (e: TelegramFileException) {
            UnifiedLog.e(TAG, "Telegram file error", e)
            throw IOException("Failed to access Telegram file: ${e.message}", e)
        } catch (e: TelegramStreamingException) {
            UnifiedLog.e(TAG, "Telegram streaming error", e)
            throw IOException("Streaming readiness failed: ${e.message}", e)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            UnifiedLog.e(TAG, "Failed to open Telegram file", e)
            throw IOException("Failed to open Telegram file: ${e.message}", e)
        }
    }

    /** Launches the async open operation in a coroutine. */
    private fun CoroutineScope.launchOpenOperation(
        fileId: Int?,
        remoteId: String?,
        mimeType: String?,
        attemptMode: String?,
        dataSpec: DataSpec,
        resultHolder: AtomicReference<OpenResult>,
        latch: CountDownLatch,
    ) {
        launch {
            try {
                val bytesRemaining = performOpen(fileId, remoteId, mimeType, attemptMode, dataSpec)
                resultHolder.set(OpenResult.Success(bytesRemaining))
            } catch (e: Exception) {
                val ioException =
                    when (e) {
                        is IOException -> e
                        else -> IOException("Open failed: ${e.message}", e)
                    }
                resultHolder.set(OpenResult.Error(ioException))
            } finally {
                latch.countDown()
            }
        }
    }

    /** Performs the actual open operation (called from coroutine). */
    private suspend fun performOpen(
        fileId: Int?,
        remoteId: String?,
        mimeType: String?,
        attemptMode: String?,
        dataSpec: DataSpec,
    ): Long =
        withContext(Dispatchers.IO) {
            // Resolve fileId if needed (with chatId+messageId for RemoteId-First)
            val resolvedFileId = resolveFileId(fileId, remoteId, chatId, messageId)

            UnifiedLog.d(TAG) { "Resolved fileId: $resolvedFileId, triggering readiness check (mime=$mimeType, attemptMode=$attemptMode)" }

            // Use TelegramFileReadyEnsurer for non-blocking readiness
            // Pass MIME type and attemptMode to enable appropriate strategy
            val localPath = if (attemptMode != null) {
                readyEnsurer.ensureReadyForAttempt(resolvedFileId, attemptMode, mimeType)
            } else {
                readyEnsurer.ensureReadyForPlayback(resolvedFileId, mimeType)
            }

            val localFile = File(localPath)
            if (!localFile.exists()) {
                throw IOException("File not found at local path: $localPath")
            }

            UnifiedLog.d(TAG) { "File ready at: $localPath (size=${localFile.length()} bytes)" }

            // Create FileDataSource delegate and open it
            val fileDataSource = FileDataSource()
            transferListener?.let { fileDataSource.addTransferListener(it) }

            val fileUri = Uri.fromFile(localFile)
            val fileDataSpec = dataSpec.buildUpon().setUri(fileUri).build()

            delegate = fileDataSource
            fileDataSource.open(fileDataSpec)
        }

    /**
     * Resolve fileId from URI parameters using RemoteId-First strategy.
     *
     * Priority (aligned with RemoteId-First Architecture):
     * 1. If chatId + messageId available → resolve via TelegramRemoteResolver (PREFERRED)
     *    - Always returns fresh fileId from current TDLib message
     *    - Works after cache eviction, message updates, app restarts
     * 2. Else if fileId valid (> 0) → use it directly (fast path - same session)
     *    - May be stale if TDLib cache changed
     * 3. Else if remoteId available → resolve via TelegramFileClient.resolveRemoteId (fallback)
     *    - Stable but requires remoteId parameter
     * 4. Else fail
     *
     * @param fileId Optional TDLib file ID from URI
     * @param remoteId Optional stable remote ID from URI
     * @param chatId Optional chat ID from URI (for RemoteResolver)
     * @param messageId Optional message ID from URI (for RemoteResolver)
     * @return Resolved fileId ready for download
     */
    private suspend fun resolveFileId(
        fileId: Int?,
        remoteId: String?,
        chatId: Long?,
        messageId: Long?,
    ): Int =
        when {
            // Path 1: RemoteId-First (chatId + messageId) - PREFERRED
            chatId != null && messageId != null -> {
                UnifiedLog.d(TAG) {
                    "Resolving via TelegramRemoteResolver (RemoteId-First): chatId=$chatId, messageId=$messageId"
                }
                val resolved = remoteResolver.resolveMedia(TelegramRemoteId(chatId, messageId))
                    ?: throw TelegramFileException(
                        "Could not resolve media via RemoteId: chatId=$chatId, messageId=$messageId"
                    )
                UnifiedLog.d(TAG) {
                    "Resolved via RemoteResolver: fileId=${resolved.mediaFileId}, " +
                        "localPath=${resolved.mediaLocalPath != null}, " +
                        "mime=${resolved.mimeType}"
                }
                resolved.mediaFileId
            }
            // Path 2: Direct fileId (fast path - same session)
            fileId != null && fileId > 0 -> {
                UnifiedLog.d(TAG) { "Using fileId from URI: $fileId (may be stale if cache changed)" }
                fileId
            }
            // Path 3: Fallback to remoteId resolution (TelegramFileClient)
            remoteId != null && remoteId.isNotEmpty() -> {
                UnifiedLog.d(TAG) { "Resolving fileId via remoteId: $remoteId" }
                val resolvedFile =
                    fileClient.resolveRemoteId(remoteId)
                        ?: throw TelegramFileException(
                            "Could not resolve remoteId: $remoteId",
                        )
                resolvedFile.id
            }
            // Path 4: No resolution method available
            else -> throw TelegramFileException(
                "Cannot resolve fileId: no chatId+messageId, no valid fileId, and no remoteId"
            )
        }

    @Throws(IOException::class)
    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        val d = delegate ?: throw IOException("DataSource not opened")
        return d.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = delegate?.uri

    @Throws(IOException::class)
    override fun close() {
        try {
            delegate?.close()
            scope?.cancel()
        } finally {
            delegate = null
            scope = null
            fileId = null
            chatId = null
            messageId = null
            remoteId = null
        }
    }

    /** Internal result type for async open operation. */
    private sealed class OpenResult {
        data class Success(
            val bytesRemaining: Long,
        ) : OpenResult()

        data class Error(
            val error: IOException,
        ) : OpenResult()
    }
}

/**
 * Factory for creating TelegramFileDataSource instances.
 *
 * This factory is registered with the player's source resolver to handle tg:// URIs.
 *
 * @param remoteResolver RemoteId resolver for chatId+messageId → fileId (SSOT)
 * @param fileClient Transport layer client for TDLib file operations
 * @param readyEnsurer Playback layer component for streaming readiness
 */
class TelegramFileDataSourceFactory(
    private val remoteResolver: TelegramRemoteResolver,
    private val fileClient: TelegramFileClient,
    private val readyEnsurer: TelegramFileReadyEnsurer,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = TelegramFileDataSource(remoteResolver, fileClient, readyEnsurer)
}
