package com.chris.m3usuite.player.datasource

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.chris.m3usuite.telegram.downloader.TelegramFileDownloader
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * DataSource for streaming Telegram files via TDLib.
 * Handles tg://file/<fileId> URLs.
 *
 * Following TDLib best practices:
 * - Uses downloadFile API with proper limits
 * - Implements resume support via offset
 * - Handles caching via TDLib's internal cache
 */
@UnstableApi
class TelegramDataSource(
    private val context: Context,
    private val downloader: TelegramFileDownloader,
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

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        currentUri = uri
        currentDataSpec = dataSpec

        // Parse tg://file/<fileId>
        if (uri.scheme != "tg" || uri.host != "file") {
            throw IOException("Invalid Telegram URI: $uri")
        }

        val segments = uri.pathSegments
        if (segments.isEmpty()) {
            throw IOException("Missing file ID in Telegram URI: $uri")
        }

        val fid = segments[0]
        fileId = fid

        // Get file size from TDLib
        totalSize =
            runBlocking {
                try {
                    downloader.getFileSize(fid)
                } catch (e: Exception) {
                    throw IOException("Failed to get file size for $fid: ${e.message}", e)
                }
            }

        position = dataSpec.position
        bytesRemaining =
            if (totalSize >= 0) {
                (totalSize - position).coerceAtLeast(0)
            } else {
                C.LENGTH_UNSET.toLong()
            }

        opened = true
        transferListener?.onTransferStart(this, dataSpec, true)

        return if (bytesRemaining >= 0) bytesRemaining else C.LENGTH_UNSET.toLong()
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        readLength: Int,
    ): Int {
        if (readLength == 0) return 0
        if (!opened) return C.RESULT_END_OF_INPUT

        val fid = fileId ?: return C.RESULT_END_OF_INPUT

        // Check if we've reached the end
        if (bytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT
        }

        val bytesToRead =
            if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
                readLength
            } else {
                minOf(readLength.toLong(), bytesRemaining).toInt()
            }

        // Read from TDLib via downloader
        val bytesRead =
            runBlocking {
                try {
                    downloader.readFileChunk(fid, position, buffer, offset, bytesToRead)
                } catch (e: Exception) {
                    throw IOException("Failed to read from Telegram file $fid at position $position: ${e.message}", e)
                }
            }

        if (bytesRead <= 0) {
            return C.RESULT_END_OF_INPUT
        }

        position += bytesRead
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining = (bytesRemaining - bytesRead).coerceAtLeast(0)
        }

        currentDataSpec?.let { spec ->
            transferListener?.onBytesTransferred(this, spec, true, bytesRead)
        }

        return bytesRead
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        if (!opened) return
        opened = false

        // Cleanup - TDLib handles its own cache, but we should stop any ongoing downloads
        fileId?.let { fid ->
            runCatching {
                runBlocking {
                    downloader.cancelDownload(fid)
                }
            }
        }

        currentUri = null
        val spec = currentDataSpec
        currentDataSpec = null
        fileId = null
        position = 0
        bytesRemaining = C.LENGTH_UNSET.toLong()
        totalSize = C.LENGTH_UNSET.toLong()
        if (spec != null) {
            transferListener?.onTransferEnd(this, spec, true)
        }
    }
}
