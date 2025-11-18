package com.chris.m3usuite.telegram.core

enum class MediaKind {
    MOVIE,          // Film
    SERIES,         // komplette Serie (Metadaten ohne einzelne Episode)
    EPISODE,        // einzelne Folge einer Serie
    CLIP,           // kurzer Clip / unbekannte Länge
    RAR_ARCHIVE,    // .rar / .zip / .7z usw.
    PHOTO,
    TEXT_ONLY,
    ADULT,
    OTHER
}

data class MediaInfo(
    val chatId: Long,
    val messageId: Long,
    val kind: MediaKind,
    val chatTitle: String? = null,

    val fileName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,

    val title: String? = null,
    val originalTitle: String? = null,
    val year: Int? = null,
    val durationMinutes: Int? = null,
    val country: String? = null,
    val fsk: Int? = null,
    val collection: String? = null,
    val genres: List<String> = emptyList(),
    val director: String? = null,
    val tmdbRating: Double? = null,
    val tmdbVotes: Int? = null,

    val totalEpisodes: Int? = null,
    val totalSeasons: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,

    val extraInfo: String? = null
)

data class SubChatRef(
    val parentChatId: Long,
    val parentChatTitle: String?,
    val parentMessageId: Long,
    val label: String,
    val linkedChatId: Long? = null,
    val inviteLinks: List<String> = emptyList()
)

data class InviteLink(
    val chatId: Long,
    val messageId: Long,
    val url: String
)

sealed class ParsedItem {
    data class Media(val info: MediaInfo) : ParsedItem()
    data class SubChat(val ref: SubChatRef) : ParsedItem()
    data class Invite(val invite: InviteLink) : ParsedItem()
    data class None(val chatId: Long, val messageId: Long) : ParsedItem()
}
