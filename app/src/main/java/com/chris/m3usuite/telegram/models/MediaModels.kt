package com.chris.m3usuite.telegram.models

/**
 * Media content type classification for parsed Telegram messages.
 */
enum class MediaKind {
    MOVIE, // Film
    SERIES, // komplette Serie (Metadaten ohne einzelne Episode)
    EPISODE, // einzelne Folge einer Serie
    CLIP, // kurzer Clip / unbekannte Länge
    RAR_ARCHIVE, // .rar / .zip / .7z usw.
    PHOTO,
    TEXT_ONLY,
    ADULT,
    OTHER,
}

/**
 * Parsed media information from a Telegram message.
 * Contains both file metadata and enriched content metadata (title, year, genres, etc.)
 */
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
    val extraInfo: String? = null,
    // File IDs for TDLib access
    val fileId: Int? = null,
    val fileUniqueId: String? = null,
    // Poster from structured pattern (MessagePhoto)
    val posterFileId: Int? = null,
    val posterPhotoSizes: List<Any>? = null, // TDLib PhotoSize objects
    // Series-specific metadata
    val seriesName: String? = null,
    val episodeTitle: String? = null,
    // Flag to mark messages as consumed in structured patterns
    val isConsumed: Boolean = false,
)

/**
 * Chat context configuration for parsing behavior.
 */
data class ChatContext(
    val chatId: Long,
    val chatTitle: String?,
    val isStructuredMovieChat: Boolean = false,
)

/**
 * Reference to a sub-chat (e.g., a series folder containing episode links).
 */
data class SubChatRef(
    val parentChatId: Long,
    val parentChatTitle: String?,
    val parentMessageId: Long,
    val label: String,
    val linkedChatId: Long? = null,
    val inviteLinks: List<String> = emptyList(),
)

/**
 * Telegram invite link found in a message.
 */
data class InviteLink(
    val chatId: Long,
    val messageId: Long,
    val url: String,
)

/**
 * Result of parsing a Telegram message.
 * Can be media content, a sub-chat reference, an invite link, or nothing of interest.
 */
sealed class ParsedItem {
    data class Media(
        val info: MediaInfo,
    ) : ParsedItem()

    data class SubChat(
        val ref: SubChatRef,
    ) : ParsedItem()

    data class Invite(
        val invite: InviteLink,
    ) : ParsedItem()

    data class None(
        val chatId: Long,
        val messageId: Long,
    ) : ParsedItem()
}
