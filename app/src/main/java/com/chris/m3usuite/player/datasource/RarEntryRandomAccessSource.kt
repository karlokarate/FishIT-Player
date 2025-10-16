package com.chris.m3usuite.player.datasource

import android.util.Log
import com.github.junrar.Archive
import com.github.junrar.exception.RarException
import com.github.junrar.rarfile.FileHeader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

class RarEntryRandomAccessSource(
    private val rarFile: File,
    private val entryName: String,
    private val cacheDir: File,
    private val chunkSize: Int = 1 * 1024 * 1024,
    private val memoryCacheBytes: Long = 4L * 1024 * 1024,
    private val ringBufferBytes: Long = 150L * 1024 * 1024,
) : RandomAccessSource {

    companion object {
        private const val TAG = "RarRandomSource"
        private const val CACHE_PREFIX = "rar_entry_"
    }

    private val chunkCache = object : LinkedHashMap<Int, ByteArray>(8, 0.75f, true) {
        private var currentBytes = 0L
        override fun put(key: Int, value: ByteArray): ByteArray? {
            val previous = super.put(key, value)
            if (previous != null) currentBytes -= previous.size
            currentBytes += value.size
            trim()
            return previous
        }

        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ByteArray>): Boolean {
            return if (currentBytes > memoryCacheBytes) {
                currentBytes -= eldest.value.size
                true
            } else {
                false
            }
        }

        private fun trim() {
            while (currentBytes > memoryCacheBytes && isNotEmpty()) {
                val iterator = entries.iterator()
                if (iterator.hasNext()) {
                    val entry = iterator.next()
                    currentBytes -= entry.value.size
                    iterator.remove()
                } else break
            }
        }
    }

    private var sizeBytes: Long = -1
    private var materialized: File? = null
    private val prepared = AtomicBoolean(false)
    private var headerName: String = entryName

    init {
        cacheDir.mkdirs()
        loadMetadata()
    }

    private fun cacheKey(): String {
        val base = rarFile.nameWithoutExtension.ifBlank { rarFile.name }
        val hash = entryName.lowercase(Locale.US).hashCode().toString(16)
        return "$CACHE_PREFIX${base}_$hash.mp3"
    }

    private fun loadMetadata() {
        if (!rarFile.exists()) throw IOException("RAR file missing: ${rarFile.path}")
        Archive(rarFile).use { archive ->
            val header = findHeader(archive) ?: throw IOException("Entry $entryName not found in ${rarFile.path}")
            headerName = header.fileName
            sizeBytes = header.fullUnpackSize
        }
    }

    private fun findHeader(archive: Archive): FileHeader? {
        val target = entryName.lowercase(Locale.US)
        return archive.fileHeaders.firstOrNull { header ->
            val name = header.fileName.lowercase(Locale.US)
            name == target || name.endsWith("/$target")
        }
    }

    private fun ensureMaterialized() {
        if (prepared.get()) return
        synchronized(this) {
            if (prepared.get()) return
            val outFile = File(cacheDir, cacheKey())
            if (!outFile.exists() || outFile.length() != sizeBytes) {
                Log.d(TAG, "materialize ${rarFile.path}#$entryName -> ${outFile.path}")
                Archive(rarFile).use { archive ->
                    val header = findHeader(archive) ?: throw IOException("Entry $entryName not found")
                    FileOutputStream(outFile).use { stream ->
                        try {
                            archive.extractFile(header, stream)
                        } catch (re: RarException) {
                            throw IOException("Failed to extract $entryName", re)
                        }
                    }
                }
            }
            materialized = outFile
            trimDiskCache()
            prepared.set(true)
        }
    }

    private fun trimDiskCache() {
        val files = cacheDir.listFiles { file -> file.name.startsWith(CACHE_PREFIX) } ?: return
        var total = files.sumOf { it.length() }
        if (total <= ringBufferBytes) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= ringBufferBytes) return
            val length = file.length()
            if (file.delete()) {
                total -= length
            }
        }
    }

    override fun size(): Long {
        ensureMaterialized()
        return sizeBytes
    }

    override fun mime(): String = "audio/mpeg"

    override fun read(offset: Long, dst: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        ensureMaterialized()
        val file = materialized ?: return -1
        if (offset >= sizeBytes) return -1
        var remaining = min(len.toLong(), sizeBytes - offset).toInt()
        var written = 0
        var pointer = offset
        while (remaining > 0) {
            val chunkIndex = (pointer / chunkSize).toInt()
            val chunk = loadChunk(file, chunkIndex)
            val offsetInChunk = (pointer % chunkSize).toInt()
            val bytesFromChunk = min(remaining, chunk.size - offsetInChunk)
            System.arraycopy(chunk, offsetInChunk, dst, off + written, bytesFromChunk)
            written += bytesFromChunk
            remaining -= bytesFromChunk
            pointer += bytesFromChunk
        }
        return written
    }

    override fun close() {
        chunkCache.clear()
    }

    private fun loadChunk(file: File, index: Int): ByteArray {
        chunkCache[index]?.let { return it }
        val bufSize = min(chunkSize.toLong(), sizeBytes - index * chunkSize.toLong()).toInt()
        val buffer = ByteArray(bufSize)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(index.toLong() * chunkSize)
            raf.readFully(buffer)
        }
        chunkCache[index] = buffer
        return buffer
    }
}
