package com.chris.m3usuite.player.datasource

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.chris.m3usuite.telegram.service.TelegramServiceClient
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi

class TdlibRandomAccessSource(
    private val fileId: Int,
    private val service: TelegramServiceClient,
    private val updateFilesFlow: SharedFlow<TdApi.UpdateFile>
) : DataSource {

    private var opened = false
    private var length: Long = -1L
    private var position: Long = 0L
    private var bytesRemaining: Long = -1L
    private var raf: RandomAccessFile? = null
    private var localPath: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var updateJob: kotlinx.coroutines.Job? = null
    private val availableRanges = sortedSetOf<LongRange>(compareBy { it.first })
    private val rangeLock = Any()
    private val updateSignal = Channel<Unit>(capacity = Channel.CONFLATED)
    private var triggerLogCount = 0

    private val chunkSize = 512 * 1024L
    private val readahead = 2 * 1024 * 1024L

    override fun addTransferListener(transferListener: TransferListener) {
        // TelegramDataSource reports transfer progress externally.
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val file = runBlockingIo { service.getFile(fileId) }
        length = file.size.toLong()
        position = if (dataSpec.position >= 0) dataSpec.position else 0L
        val lengthUnset = C.LENGTH_UNSET.toLong()
        bytesRemaining = if (dataSpec.length == lengthUnset) {
            if (length > 0) length - position else -1L
        } else dataSpec.length

        localPath = file.local?.path?.takeIf { it.isNotEmpty() }
        markAvailableFromFile(file)
        startCollectingUpdates()

        triggerDownload(position, max(chunkSize, bytesRemaining.takeIf { it > 0 } ?: readahead))

        opened = true
        return if (dataSpec.length == lengthUnset) length else dataSpec.length
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (!opened) throw IOException("Not opened")
        if (readLength == 0) return 0
        if (bytesRemaining == 0L) return -1

        ensureLocalFileOpen()
        waitUntilAvailable(position, readLength.toLong())

        val toRead = if (bytesRemaining < 0) readLength else min(readLength.toLong(), bytesRemaining).toInt()
        val read = raf!!.read(buffer, offset, toRead)
        if (read < 0) throw EOFException("Reached EOF before expected")
        position += read
        if (bytesRemaining > 0) bytesRemaining -= read

        // Nach jedem Lesevorgang: zusätzlichen Bereich vorausladen,
        // um Spulen und kontinuierliche Wiedergabe zu glätten
        if (read > 0) {
            triggerDownload(position, readahead)
        }

        return read
    }

    override fun getUri() = null

    override fun close() {
        try {
            raf?.close()
        } finally {
            raf = null
            opened = false
            availableRanges.clear()
            updateJob?.cancel()
            updateJob = null
        }
    }

    private fun ensureLocalFileOpen() {
        if (raf != null) return
        val latestFile = runBlockingIo { service.getFile(fileId) }
        localPath = latestFile.local?.path?.takeIf { it.isNotEmpty() } ?: localPath
        markAvailableFromFile(latestFile)
        val path = localPath ?: throw IOException("Local path not available yet")
        val file = File(path)
        if (!file.exists()) throw IOException("Local file not found yet: $path")
        raf = RandomAccessFile(file, "r").apply { seek(position) }
    }

    private fun waitUntilAvailable(pos: Long, need: Long, timeoutMs: Long = 10_000L) {
        if (need <= 0) return
        triggerDownload(pos, max(need, chunkSize))
        val success = runBlockingIo { waitUntilAvailableInternal(pos, need, timeoutMs) }
        if (!success) throw IOException("Timeout waiting for bytes")
    }

    private suspend fun waitUntilAvailableInternal(pos: Long, need: Long, timeoutMs: Long): Boolean {
        val endExclusive = pos + need
        if (isRangeAvailable(endExclusive = endExclusive, start = pos)) return true
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var backoff = 25L
        while (true) {
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0) return false
            val waitFor = min(backoff, remaining)
            val received = try {
                withTimeoutOrNull(waitFor) { updateSignal.receive() } != null
            } catch (_: CancellationException) {
                return false
            }
            if (isRangeAvailable(endExclusive = endExclusive, start = pos)) return true
            backoff = if (received) 25L else (backoff * 2).coerceAtMost(250L)
        }
    }

    private fun markRange(range: LongRange) {
        synchronized(rangeLock) {
            val all = ArrayList<LongRange>(availableRanges.size + 1)
            all.addAll(availableRanges)
            all.add(range)
            all.sortBy { it.first }
            val merged = mutableListOf<LongRange>()
            for (r in all) {
                if (merged.isEmpty()) {
                    merged.add(r)
                } else {
                    val lastIndex = merged.lastIndex
                    val lastRange = merged[lastIndex]
                    if (r.first <= lastRange.last + 1) {
                        val start = min(lastRange.first, r.first)
                        val end = max(lastRange.last, r.last)
                        merged[lastIndex] = start..end
                    } else {
                        merged.add(r)
                    }
                }
            }
            availableRanges.clear()
            availableRanges.addAll(merged)
        }
    }

    private fun markAvailableFromFile(file: TdApi.File) {
        val local = file.local ?: return
        if (!local.path.isNullOrBlank()) {
            localPath = local.path
        }
        val downloaded = listOfNotNull(
            runCatching { local.downloadedPrefixSize.toLong() }.getOrNull(),
            runCatching { local.downloadedSize.toLong() }.getOrNull(),
            localPath?.let { path -> runCatching { File(path).takeIf { it.exists() }?.length() }.getOrNull() }
        ).maxOrNull() ?: 0L
        if (downloaded > 0) {
            markRange(0L..(downloaded - 1))
            updateSignal.trySend(Unit)
        }
        if (local.isDownloadingCompleted) {
            val expected = file.size.toLong().takeIf { it > 0 } ?: length
            if (expected > 0) {
                markRange(0L..(expected - 1))
                updateSignal.trySend(Unit)
            }
        }
    }

    private fun startCollectingUpdates() {
        if (updateJob != null) return
        updateJob = scope.launch {
            updateFilesFlow
                .filter { it.file.id == fileId }
                .collect { update ->
                    markAvailableFromFile(update.file)
                }
        }
    }

    private fun isRangeAvailable(start: Long, endExclusive: Long): Boolean {
        if (endExclusive <= start) return true
        val targetStart = start
        val targetEndInclusive = endExclusive - 1
        return synchronized(rangeLock) {
            availableRanges.any { range ->
                range.first <= targetStart && range.last >= targetEndInclusive
            }
        }
    }

    private fun triggerDownload(offset: Long, requestLen: Long) {
        val effectiveLimit = max(chunkSize, requestLen)
        runBlockingIo {
            if (triggerLogCount < 5) {
                Log.d(
                    "TdlibRandomAccess",
                    "downloadFile(file=$fileId, offset=$offset, limit=$effectiveLimit)"
                )
                triggerLogCount++
            }
            service.downloadFile(
                fileId = fileId,
                priority = 32,
                offset = max(0L, offset),
                limit = effectiveLimit,
                synchronous = false
            )
        }
    }
}

private fun <T> runBlockingIo(block: suspend () -> T): T {
    return kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { block() }
}
