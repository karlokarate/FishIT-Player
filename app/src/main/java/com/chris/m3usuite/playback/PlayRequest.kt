package com.chris.m3usuite.playback

data class PlayRequest(
    val type: String,                 // "vod" | "series" | "live"
    val mediaId: Long? = null,        // encoded id for vod/series/live (when available)
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val drm: Any? = null,             // placeholder for future DRM info
    val startPositionMs: Long? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val mimeType: String? = null,
    // Series/Episode metadata (optional; enrich telemetry/result updates)
    val seriesId: Int? = null,
    val season: Int? = null,
    val episodeNum: Int? = null,
    val episodeId: Int? = null
)
