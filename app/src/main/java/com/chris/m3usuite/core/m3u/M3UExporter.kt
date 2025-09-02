package com.chris.m3usuite.core.m3u

import android.content.Context
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

object M3UExporter {
    /**
     * Build an M3U playlist text from current DB contents.
     * - Adds url-tvg header if EPG_URL is set
     * - Exports LIVE and VOD entries that have a concrete URL
     */
    suspend fun build(context: Context, settings: SettingsStore): String = withContext(Dispatchers.IO) {
        val sw = java.io.StringWriter(1024 * 1024)
        stream(context, settings, sw)
        sw.toString()
    }

    /**
     * Streaming export to an Appendable/Writer to reduce memory usage.
     * Reads DB in batches to avoid large heap spikes.
     */
    suspend fun stream(
        context: Context,
        settings: SettingsStore,
        out: java.lang.Appendable,
        batchSize: Int = 5000
    ) = withContext(Dispatchers.IO) {
        val db = DbProvider.get(context)
        val epg = settings.epgUrl.first().trim()
        if (epg.isNotBlank()) out.append("#EXTM3U url-tvg=\"").append(epg).append("\"\n") else out.append("#EXTM3U\n")

        fun esc(s: String?): String = s?.replace("\"", "'") ?: ""

        suspend fun writeType(type: String) {
            var offset = 0
            while (true) {
                val chunk = db.mediaDao().listByType(type, batchSize, offset)
                if (chunk.isEmpty()) break
                for (it in chunk) {
                    val url = it.url ?: continue
                    if (url.isBlank()) continue
                    val name = it.name
                    val group = esc(it.categoryName)
                    if (type == "live") {
                        val tvgId = esc(it.epgChannelId)
                        val logo = esc(it.logo)
                        out.append("#EXTINF:-1")
                        if (tvgId.isNotBlank()) out.append(" tvg-id=\"").append(tvgId).append("\"")
                        if (logo.isNotBlank()) out.append(" tvg-logo=\"").append(logo).append("\"")
                        if (group.isNotBlank()) out.append(" group-title=\"").append(group).append("\"")
                        out.append(", ").append(name).append('\n')
                        out.append(url).append('\n')
                    } else if (type == "vod") {
                        val poster = esc(it.poster)
                        out.append("#EXTINF:-1")
                        if (poster.isNotBlank()) out.append(" tvg-logo=\"").append(poster).append("\"")
                        if (group.isNotBlank()) out.append(" group-title=\"").append(group).append("\"")
                        out.append(", ").append(name).append('\n')
                        out.append(url).append('\n')
                    }
                }
                if (chunk.size < batchSize) break
                offset += batchSize
            }
        }

        writeType("live")
        writeType("vod")
    }
}
