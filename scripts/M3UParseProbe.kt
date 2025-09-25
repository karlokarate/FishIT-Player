package probe

import com.chris.m3usuite.core.m3u.M3UParser
import com.chris.m3usuite.model.MediaItem
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

object M3UParseProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            System.err.println("Usage: M3UParseProbe <path-to-m3u>")
            return
        }
        val f = File(args[0])
        require(f.exists()) { "File not found: ${f.absolutePath}" }

        val total = AtomicLong(0)
        val byType = linkedMapOf<String, Long>()
        val byCategory = linkedMapOf<String, Long>()
        val withLogo = AtomicLong(0)
        val withPoster = AtomicLong(0)
        val withEpg = AtomicLong(0)

        fun tally(batch: List<MediaItem>) {
            total.addAndGet(batch.size.toLong())
            batch.forEach { mi ->
                val t = mi.type.ifBlank { "(blank)" }
                byType[t] = (byType[t] ?: 0) + 1
                val cat = (mi.categoryName ?: "").ifBlank { "(none)" }
                byCategory[cat] = (byCategory[cat] ?: 0) + 1
                if (!mi.logo.isNullOrBlank()) withLogo.incrementAndGet()
                if (!mi.poster.isNullOrBlank()) withPoster.incrementAndGet()
                if (!mi.epgChannelId.isNullOrBlank()) withEpg.incrementAndGet()
            }
        }

        val ms = measureTimeMillis {
            FileInputStream(f).use { input ->
                val approx = f.length()
                kotlinx.coroutines.runBlocking {
                    M3UParser.parseStreaming(
                        source = input,
                        approxSizeBytes = approx,
                        batchSize = 2000,
                        onBatch = { batch -> tally(batch) },
                        onProgress = { /* ignore */ },
                        cancel = { false }
                    )
                }
            }
        }

        println("Parsed file: ${f.name}")
        println("Size bytes: ${f.length()}")
        println("Time ms: $ms")
        println("Total items: ${total.get()}")
        println("By type:")
        byType.forEach { (k, v) -> println("  $k: $v") }
        println("Top categories (first 20):")
        byCategory.entries.sortedByDescending { it.value }.take(20).forEach { (k, v) -> println("  $k: $v") }
        println("Logo present: ${withLogo.get()}; Poster present: ${withPoster.get()}; EPG IDs: ${withEpg.get()}")
    }
}

