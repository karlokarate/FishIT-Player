package com.chris.m3usuite.model

data class Episode(
    val id: Long = 0L,
    val seriesStreamId: Int = 0,
    val episodeId: Int = 0, // optional legacy id (0 for OBX-only)
    val season: Int = 0,
    val episodeNum: Int = 0,
    val title: String? = null,
    val plot: String? = null,
    val durationSecs: Int? = null,
    val rating: Double? = null,
    val airDate: String? = null,
    val containerExt: String? = null,
    val poster: String? = null,
    val tgChatId: Long? = null,
    val tgMessageId: Long? = null,
    val tgFileId: Int? = null,
    // Optional enriched meta (Telegram-derived)
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val sizeBytes: Long? = null,
    val supportsStreaming: Boolean? = null,
    val language: String? = null,
)
