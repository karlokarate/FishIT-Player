package com.chris.m3usuite.player.datasource

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.obx.ObxTelegramMessage
import com.chris.m3usuite.data.obx.ObxTelegramMessage_
import com.chris.m3usuite.telegram.TdLibReflection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * TDLib-backed random access reader that pulls data on demand using DownloadFile and keeps
 * track of locally cached ranges. Designed for on-demand playback via ExoPlayer without
 * routing traffic through HTTP proxies.
 */
class TdlibRandomAccessSource(
    private val context: Context,
    private val fileId: Int,
    private val priority: Int = DEFAULT_PRIORITY,
    private val readaheadBytes: Int = DEFAULT_READAHEAD_BYTES,
) : RandomAccessSource {

    companion object {
        private const val TAG = "TdlibRandomSource"
        private const val DEFAULT_PRIORITY = 48
        private const val DEFAULT_READAHEAD_BYTES = 512 * 1024
        private const val MAX_WAIT_ATTEMPTS = 6
        private const val MAX_WAIT_MS = 1_500L
    }

    private val authFlow = MutableStateFlow(TdLibReflection.AuthState.UNKNOWN)
    private val traceSampler = TraceSampler()
    private val pendingRanges = mutableSetOf<LongRange>()
    private val downloadedRanges = mutableListOf<LongRange>()
    private val completed = AtomicBoolean(false)

    @Volatile private var localPath: String? = null
    @Volatile private var expectedSize: Long = -1
    @Volatile private var mimeType: String = "application/octet-stream"
    @Volatile private var downloadedBytes: Long = 0

    private var updateCloseable: AutoCloseable? = null
    private var clientHandle: TdLibReflection.ClientHandle? = null

    init {
        loadCachedMetadata()
        ensureClient()
        refreshFileInfo()
        subscribeToUpdates()
    }

    override fun size(): Long = expectedSize

    override fun mime(): String = mimeType

    override fun read(offset: Long, dst: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        var attempt = 0
        var retries = 0
        val trace = traceSampler.start("TDLIB", offset, len)
        while (attempt < 5) {
            val file = currentFile()
            if (file != null) {
                val available = max(0L, file.length() - offset)
                if (available > 0) {
                    val toRead = min(len.toLong(), available).toInt()
                    if (toRead > 0) {
                        RandomAccessFile(file, "r").use { raf ->
                            raf.seek(offset)
                            val read = raf.read(dst, off, toRead)
                            if (read > 0) {
                                maybePrefetch(offset + read)
                                trace?.finish(read, retries)
                                return read
                            }
                        }
                    }
                } else if (completed.get()) {
                    trace?.finish(-1, retries)
                    return if (attempt == 0) -1 else 0
                }
            }
            if (!waitForData(offset, len)) {
                if (completed.get()) {
                    trace?.finish(-1, retries)
                    return if (attempt == 0) -1 else 0
                }
            } else {
                retries++
            }
            attempt++
        }
        trace?.finish(-1, retries)
        return -1
    }

    override fun close() {
        runCatching { updateCloseable?.close() }
        updateCloseable = null
    }

    private fun loadCachedMetadata() {
        runCatching {
            val row = runBlocking {
                val store = ObxStore.get(context)
                val box = store.boxFor(ObxTelegramMessage::class.java)
                box.query(ObxTelegramMessage_.fileId.equal(fileId.toLong())).build().findFirst()
            }
            if (row != null) applyRow(row)
        }
    }

    private fun applyRow(row: ObxTelegramMessage) {
        row.mimeType?.takeIf { it.isNotBlank() }?.let { mimeType = it }
        row.sizeBytes?.takeIf { it > 0 }?.let { expectedSize = it }
        row.localPath?.takeIf { it.isNotBlank() }?.let { path ->
            if (File(path).exists()) localPath = path
        }
    }

    private fun ensureClient(): TdLibReflection.ClientHandle? {
        var handle = clientHandle
        if (handle != null) return handle
        handle = TdLibReflection.getOrCreateClient(context, authFlow)
        clientHandle = handle
        return handle
    }

    private fun refreshFileInfo() {
        val handle = ensureClient() ?: return
        val fn = TdLibReflection.buildGetFile(fileId) ?: return
        val info = TdLibReflection.sendForResult(
            handle,
            fn,
            timeoutMs = 2_000,
            retries = 1,
            traceTag = "TdlibRandomSource:getFile"
        )?.let { TdLibReflection.extractFileInfo(it) }
        if (info != null) updateFromInfo(info)
    }

    private fun subscribeToUpdates() {
        updateCloseable = TdLibReflection.addUpdateListener listener@{ obj ->
            val name = obj.javaClass.name
            if (!name.endsWith("TdApi\$UpdateFile")) return@listener
            val fileObj = runCatching {
                obj.javaClass.getDeclaredField("file").apply { isAccessible = true }.get(obj)
            }.getOrNull() ?: return@listener
            val info = TdLibReflection.extractFileInfo(fileObj) ?: return@listener
            if (info.fileId != fileId) return@listener
            updateFromInfo(info)
        }
    }

    private fun updateFromInfo(info: TdLibReflection.FileInfo) {
        expectedSize = max(expectedSize, info.expectedSize)
        downloadedBytes = max(downloadedBytes, info.downloadedSize)
        if (!info.localPath.isNullOrBlank()) {
            localPath = info.localPath
        }
        if (info.downloadingCompleted) {
            completed.set(true)
        }
        mergeRange(0, info.downloadedSize)
        persistProgress()
    }

    private fun persistProgress() {
        runCatching {
            val path = localPath
            val bytes = downloadedBytes
            val size = expectedSize
            runBlocking {
                val store = ObxStore.get(context)
                val box = store.boxFor(ObxTelegramMessage::class.java)
                val row = box.query(ObxTelegramMessage_.fileId.equal(fileId.toLong())).build().findFirst()
                if (row != null) {
                    var changed = false
                    if (!path.isNullOrBlank() && row.localPath != path) {
                        row.localPath = path
                        changed = true
                    }
                    if (size > 0 && row.sizeBytes != size) {
                        row.sizeBytes = size
                        changed = true
                    }
                    if (changed) box.put(row)
                }
            }
        }
    }

    private fun mergeRange(start: Long, length: Long) {
        if (length <= 0) return
        val end = start + length - 1
        synchronized(downloadedRanges) {
            val newRange = LongRange(start, end)
            val merged = mutableListOf<LongRange>()
            var inserted = false
            for (existing in downloadedRanges) {
                if (existing.overlaps(newRange) || existing.endInclusive + 1 >= newRange.start && newRange.endInclusive + 1 >= existing.start) {
                    val combined = LongRange(
                        min(existing.start, newRange.start),
                        max(existing.endInclusive, newRange.endInclusive)
                    )
                    if (!inserted) {
                        merged.add(combined)
                        inserted = true
                    } else {
                        val prev = merged.removeAt(merged.lastIndex)
                        merged.add(
                            LongRange(
                                min(prev.start, combined.start),
                                max(prev.endInclusive, combined.endInclusive)
                            )
                        )
                    }
                } else {
                    merged.add(existing)
                }
            }
            if (!inserted) merged.add(newRange)
            downloadedRanges.clear()
            downloadedRanges.addAll(merged.sortedBy { it.start })
        }
    }

    private fun currentFile(): File? {
        val path = localPath ?: return null
        val file = File(path)
        return if (file.exists()) file else null
    }

    private fun waitForData(offset: Long, len: Int): Boolean {
        requestRange(offset, len)
        var waitMs = 200L
        repeat(MAX_WAIT_ATTEMPTS) {
            if (completed.get()) return false
            if (currentFile()?.length()?.let { it > offset } == true) return true
            try {
                Thread.sleep(waitMs)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
            waitMs = min(MAX_WAIT_MS, waitMs * 2)
        }
        return currentFile()?.length()?.let { it > offset } == true
    }

    private fun maybePrefetch(nextOffset: Long) {
        if (readaheadBytes <= 0) return
        requestRange(nextOffset, readaheadBytes)
    }

    private fun requestRange(offset: Long, length: Int) {
        val handle = ensureClient() ?: return
        val clampedOffset = offset.coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val limit = max(readaheadBytes, length).coerceAtMost(Int.MAX_VALUE)
        val range = LongRange(clampedOffset.toLong(), clampedOffset.toLong() + limit)
        synchronized(pendingRanges) {
            if (pendingRanges.any { it.overlaps(range) }) return
            pendingRanges.add(range)
        }
        var attempt = 0
        var backoff = 150L
        while (attempt < 4) {
            val fn = TdLibReflection.buildDownloadFile(fileId, priority, clampedOffset, limit, false)
            if (fn != null) {
                val result = TdLibReflection.sendForResult(
                    handle,
                    fn,
                    timeoutMs = 1_000,
                    retries = 0,
                    traceTag = String.format(Locale.US, "TdlibRandomSource:download@%d", clampedOffset)
                )
                if (result != null) break
            }
            attempt++
            try {
                Thread.sleep(backoff)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
            backoff = min(1_200L, backoff * 2)
        }
        synchronized(pendingRanges) { pendingRanges.remove(range) }
    }

    private fun LongRange.overlaps(other: LongRange): Boolean {
        return start <= other.endInclusive && other.start <= endInclusive
    }

    private class TraceSampler {
        private val sampleRate = 10
        fun start(src: String, offset: Long, len: Int): Trace? {
            return if (Random.nextInt(sampleRate) == 0) {
                Trace(src, offset, len)
            } else null
        }
    }

    private class Trace(private val src: String, private val offset: Long, private val len: Int) {
        private val started = SystemClock.elapsedRealtime()
        fun finish(read: Int, retries: Int) {
            val took = SystemClock.elapsedRealtime() - started
            Log.d(TAG, "read src=$src offset=$offset len=$len tookMs=$took retries=$retries bytes=$read")
        }
    }
}
