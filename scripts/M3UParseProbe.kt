package probe

import java.io.File
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
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

        fun inferType(url: String): String {
            val lower = url.lowercase()
            return when {
                lower.contains("/series/") -> "SERIES"
                lower.contains("/movie/") || lower.contains("/vod/") -> "VOD"
                lower.endsWith(".m3u8") || lower.endsWith(".ts") -> "LIVE"
                lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") -> "VOD"
                else -> "UNKNOWN"
            }
        }

        fun parseAttr(extinfLine: String, key: String): String? {
            // Matches key="value" (very common IPTV format)
            val pattern = Regex("\\b" + Regex.escape(key) + "=\\\"([^\\\"]*)\\\"")
            return pattern.find(extinfLine)?.groupValues?.getOrNull(1)
        }

        fun parseGroupTitle(extinfLine: String): String? {
            return parseAttr(extinfLine, "group-title")
        }

        fun parseLogo(extinfLine: String): String? {
            return parseAttr(extinfLine, "tvg-logo")
        }

        fun parsePoster(extinfLine: String): String? {
            // Non-standard but used by some providers
            return parseAttr(extinfLine, "poster")
                ?: parseAttr(extinfLine, "xui-poster")
        }

        fun parseEpgId(extinfLine: String): String? {
            return parseAttr(extinfLine, "tvg-id")
        }

        fun tallyItem(extinfLine: String, urlLine: String) {
            total.incrementAndGet()

            val inferredType = inferType(urlLine)
            byType[inferredType] = (byType[inferredType] ?: 0) + 1

            val cat = parseGroupTitle(extinfLine).orEmpty().ifBlank { "(none)" }
            byCategory[cat] = (byCategory[cat] ?: 0) + 1

            if (!parseLogo(extinfLine).isNullOrBlank()) withLogo.incrementAndGet()
            if (!parsePoster(extinfLine).isNullOrBlank()) withPoster.incrementAndGet()
            if (!parseEpgId(extinfLine).isNullOrBlank()) withEpg.incrementAndGet()
        }

        val ms = measureTimeMillis {
            FileInputStream(f).use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    var pendingExtinf: String? = null
                    reader.lineSequence().forEach { rawLine ->
                        val line = rawLine.trim()
                        if (line.isEmpty()) return@forEach

                        if (line.startsWith("#EXTINF", ignoreCase = true)) {
                            pendingExtinf = line
                            return@forEach
                        }

                        // URL line (may follow EXTINF)
                        val extinf = pendingExtinf
                        if (extinf != null && !line.startsWith("#")) {
                            tallyItem(extinfLine = extinf, urlLine = line)
                            pendingExtinf = null
                        }
                    }
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

