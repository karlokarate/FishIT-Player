package com.chris.m3usuite.player.datasource

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.obx.ObxTelegramMessage
import com.chris.m3usuite.data.obx.ObxTelegramMessage_
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException

@UnstableApi
class RarDataSourceRefactor(
    private val context: Context,
) : DataSource {
    private var source: RarEntryRandomAccessSource? = null
    private var currentUri: Uri? = null
    private var currentDataSpec: DataSpec? = null
    private var position: Long = 0
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false
    private var transferListener: TransferListener? = null

    override fun addTransferListener(transferListener: TransferListener) {
        this.transferListener = transferListener
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        currentUri = uri

        val segments = uri.pathSegments
        if (segments.size < 3 || segments[0] != "msg") {
            throw IOException("Invalid rar:// uri: $uri")
        }

        // rar://msg/<messageId>/<entry/path/in/archive>
        val msgId =
            segments[1].toLongOrNull()
                ?: throw IOException("Invalid message id in $uri")

        val entryName = segments.subList(2, segments.size).joinToString("/")

        // NEU: RAR-Dateipfad nicht mehr aus ObxTelegramMessage.localPath,
        // sondern über TDLib-Downloader ermitteln
        val rarPath = ensureRarPathForMessage(msgId)
        val file = File(rarPath)
        if (!file.exists()) throw IOException("RAR file missing: $rarPath")

        val cacheDir = File(context.cacheDir, "rar")
        val src = RarEntryRandomAccessSource(file, entryName, cacheDir)

        source = src
        currentDataSpec = dataSpec
        position = dataSpec.position

        val totalSize = src.size()
        bytesRemaining =
            if (totalSize >= 0) {
                totalSize - position
            } else {
                C.LENGTH_UNSET.toLong()
            }

        opened = true
        transferListener?.onTransferStart(this, dataSpec, /* isNetwork= */ true)

        return if (bytesRemaining >= 0) bytesRemaining else C.LENGTH_UNSET.toLong()
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        readLength: Int,
    ): Int {
        if (readLength == 0) return 0
        val src = source ?: return C.RESULT_END_OF_INPUT

        val read = src.read(position, buffer, offset, readLength)
        if (read == -1) return C.RESULT_END_OF_INPUT

        if (read > 0) {
            position += read
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                bytesRemaining = (bytesRemaining - read).coerceAtLeast(0)
            }
            currentDataSpec?.let { spec ->
                transferListener?.onBytesTransferred(this, spec, /* isNetwork= */ true, read)
            }
        }

        return read
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        if (!opened) return
        opened = false

        runCatching { source?.close() }
        source = null

        currentDataSpec?.let { spec ->
            transferListener?.onTransferEnd(this, spec, /* isNetwork= */ true)
        }
        currentDataSpec = null
    }

    /**
     * Sorgt dafür, dass die vollständige RAR-Datei im TDLib-Cache liegt und liefert den Pfad.
     *
     * - Liest ObxTelegramMessage anhand der messageId
     * - Nimmt deren fileId
     * - Nutzt T_TelegramFileDownloader.ensureFileReady() um mindestens einen Teil des Files zu laden
     * - Prüft, dass der Pfad existiert
     */
    @Throws(IOException::class)
    private fun ensureRarPathForMessage(messageId: Long): String =
        runBlocking {
            val store = ObxStore.get(context)
            val box = store.boxFor(ObxTelegramMessage::class.java)

            val row =
                box
                    .query(ObxTelegramMessage_.messageId.equal(messageId))
                    .build()
                    .findFirst()
                    ?: throw IOException("RAR message $messageId not found in ObjectBox")

            val fileId =
                row.fileId
                    ?: throw IOException("RAR message $messageId has no fileId")

            val serviceClient = T_TelegramServiceClient.getInstance(context)

            // TODO: T_TelegramServiceClient.fileDownloader property not yet available
            // TODO: StreamingConfig constants not yet defined
            // Uncomment when these are implemented (Phase 3+)
            // For now, this RarDataSourceRefactor serves as reference only
            throw IOException("RarDataSourceRefactor: fileDownloader not yet implemented in T_TelegramServiceClient")

            /*
            val downloader: T_TelegramFileDownloader = serviceClient.fileDownloader

            val minBytes = StreamingConfigRefactor.MIN_READ_AHEAD_BYTES
                .coerceAtLeast(4L * 1024 * 1024) // min. 4 MiB, reicht locker als Start

            val path =
                try {
                    withContext(Dispatchers.IO) {
                        downloader.ensureFileReady(
                            fileId = fileId,
                            startPosition = 0L,
                            minBytes = minBytes,
                            timeoutMs = StreamingConfigRefactor.ENSURE_READY_TIMEOUT_MS,
                        )
                    }
                } catch (t: Throwable) {
                    UnifiedLog.error(
                        source = "RarDataSource",
                        message = "Failed to ensure RAR file via TDLib",
                        details =
                            mapOf(
                                "messageId" to messageId.toString(),
                                "fileId" to fileId.toString(),
                            ),
                        exception = t,
                    )
                    throw IOException(
                        "Failed to download Telegram RAR file (messageId=$messageId, fileId=$fileId): ${t.message}",
                        t,
                    )
                }

            val file = File(path)
            if (!file.exists()) {
                throw IOException("Telegram RAR file not found after TDLib download: $path")
            }

            UnifiedLog.info(
                source = "RarDataSource",
                message = "Resolved Telegram RAR file path",
                details =
                    mapOf(
                        "messageId" to messageId.toString(),
                        "fileId" to fileId.toString(),
                        "path" to path,
                    ),
            )

            path
             */
        }
}
