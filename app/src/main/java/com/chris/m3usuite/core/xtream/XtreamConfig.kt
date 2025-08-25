package com.chris.m3usuite.core.xtream

import android.net.Uri

data class XtreamConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val output: String = "m3u8"
) {
    val portalBase: String get() = "http://$host:$port"
    val baseQuery: String get() = "$portalBase/player_api.php?username=$username&password=$password"

    fun liveUrl(streamId: Int): String {
        val ext = if (output.equals("ts", true)) "ts" else "m3u8"
        return "$portalBase/live/$username/$password/$streamId.$ext"
    }
    fun vodUrl(streamId: Int, container: String?): String {
        val ext = if (container.isNullOrBlank()) "mp4" else container
        return "$portalBase/movie/$username/$password/$streamId.$ext"
    }
    fun seriesEpisodeUrl(episodeId: Int, container: String?): String {
        val ext = if (container.isNullOrBlank()) "mp4" else container
        return "$portalBase/series/$username/$password/$episodeId.$ext"
    }

    companion object {
        fun fromM3uUrl(m3u: String): XtreamConfig? = runCatching {
            val u = Uri.parse(m3u)
            val host = u.host ?: return null
            val port = if (u.port > 0) u.port else 80
            val user = u.getQueryParameter("username") ?: return null
            val pass = u.getQueryParameter("password") ?: return null
            val out = u.getQueryParameter("output") ?: "m3u8"
            XtreamConfig(host, port, user, pass, out)
        }.getOrNull()
    }
}
