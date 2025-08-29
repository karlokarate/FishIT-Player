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
        val db = DbProvider.get(context)
        val live = db.mediaDao().listByType("live", 200000, 0)
        val vod = db.mediaDao().listByType("vod", 200000, 0)
        val epg = settings.epgUrl.first().trim()
        val sb = StringBuilder()
        if (epg.isNotBlank()) sb.append("#EXTM3U url-tvg=\"$epg\"\n") else sb.append("#EXTM3U\n")

        fun esc(s: String?): String = s?.replace("\"", "'") ?: ""

        live.filter { !it.url.isNullOrBlank() }.forEach { it ->
            val name = it.name
            val tvgId = esc(it.epgChannelId)
            val logo = esc(it.logo)
            val group = esc(it.categoryName)
            sb.append("#EXTINF:-1")
            if (tvgId.isNotBlank()) sb.append(" tvg-id=\"$tvgId\"")
            if (logo.isNotBlank()) sb.append(" tvg-logo=\"$logo\"")
            if (group.isNotBlank()) sb.append(" group-title=\"$group\"")
            sb.append(", ").append(name).append('\n')
            sb.append(it.url).append('\n')
        }

        vod.filter { !it.url.isNullOrBlank() }.forEach { it ->
            val name = it.name
            val poster = esc(it.poster)
            val group = esc(it.categoryName)
            sb.append("#EXTINF:-1")
            if (poster.isNotBlank()) sb.append(" tvg-logo=\"$poster\"")
            if (group.isNotBlank()) sb.append(" group-title=\"$group\"")
            sb.append(", ").append(name).append('\n')
            sb.append(it.url).append('\n')
        }
        sb.toString()
    }
}

