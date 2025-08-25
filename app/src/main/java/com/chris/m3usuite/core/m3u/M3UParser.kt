package com.chris.m3usuite.core.m3u

import com.chris.m3usuite.data.db.MediaItem
import java.util.Locale

object M3UParser {
    private val attrRegex = Regex("""(\w[\w\-]*)="([^"]*)"""")
    private const val EXTINF = "#EXTINF"

    fun parse(text: String): List<MediaItem> {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val out = mutableListOf<MediaItem>()
        var lastInf: String? = null
        for (line in lines) {
            if (line.startsWith("#EXTM3U")) continue
            if (line.startsWith(EXTINF)) lastInf = line
            else if (!line.startsWith("#") && lastInf != null) {
                val (attrs, name) = parseExtInf(lastInf!!)
                val url = line.substringBefore('|')
                val type = inferType(attrs, url)
                out += MediaItem(
                    type = type,
                    streamId = null,
                    name = name,
                    sortTitle = normalize(name),
                    categoryId = attrs["group-title"],
                    categoryName = attrs["group-title"],
                    logo = attrs["tvg-logo"],
                    poster = attrs["tvg-logo"],
                    backdrop = null,
                    epgChannelId = attrs["tvg-id"],
                    year = null, rating = null, durationSecs = null,
                    plot = null,
                    url = url,
                    extraJson = null
                )
                lastInf = null
            }
        }
        return out
    }

    private fun parseExtInf(line: String): Pair<Map<String,String>, String> {
        val parts = line.removePrefix("$EXTINF:").split(",", limit = 2)
        val attrsPart = parts.getOrNull(0).orEmpty()
        val displayName = parts.getOrNull(1)?.trim().orEmpty()
        val attrs = mutableMapOf<String,String>()
        for (m in attrRegex.findAll(attrsPart)) attrs[m.groupValues[1]] = m.groupValues[2]
        val name = if (displayName.isNotBlank()) displayName else attrs["tvg-name"].orEmpty()
        return attrs to name
    }

    private fun normalize(name: String): String =
        name.lowercase(Locale.ROOT).replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

    private fun inferType(attrs: Map<String,String>, url: String): String {
        val grp = (attrs["group-title"] ?: "").lowercase(Locale.ROOT)
        if (grp.contains("series") || grp.contains("serien") || grp.contains("tv shows")) return "series"
        if (grp.contains("vod") || grp.contains("movie") || grp.contains("filme")) return "vod"
        if (url.contains(Regex("""\.(mp4|mkv|avi|mov)$""", RegexOption.IGNORE_CASE))) return "vod"
        return "live"
    }
}
