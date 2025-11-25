package com.chris.m3usuite.data.obx

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

/**
 * ObjectBox stores only *semantic* metadata and Telegram identifiers.
 *
 * TDLib is the single source of truth for:
 *  - file paths
 *  - local cached files
 *  - mimeType
 *  - width/height/duration
 *  - download status
 *
 * OBX contains:
 *  - identification (chatId, messageId, fileId, fileUniqueId)
 *  - semantic metadata (title, genres, fsk, description)
 *  - series metadata (season/episode)
 *  - user flags (kids/favorite/hidden)
 *  - caption + captionLower for searching
 *  - telegram poster/thumbnail IDs (not paths!)
 */
@Entity
data class ObxTelegramMessageRefactor(
    @Id
    var id: Long = 0,
    // Telegram identifiers
    @Index
    var chatId: Long = 0,
    @Index
    var messageId: Long = 0,
    @Index
    var fileId: Int? = null,
    var fileUniqueId: String? = null,
    // Caption (TDLib text or filename), lowercased for search
    var caption: String? = null,
    var captionLower: String? = null,
    // File metadata (semantic; real technical info stays in TDLib)
    var durationSecs: Int? = null,
    var supportsStreaming: Boolean? = null,
    // Timestamp (TDLib message.date)
    var date: Long? = null,
    // Filename from TDLib
    var fileName: String? = null,
    // Optional language tag (heuristic)
    var language: String? = null,
    // User flags
    var isKidsContent: Boolean = false,
    var isFavorite: Boolean = false,
    var isHidden: Boolean = false,
    // Movie metadata
    var title: String? = null,
    var year: Int? = null,
    var genres: String? = null,
    var fsk: Int? = null,
    var description: String? = null,
    var posterFileId: Int? = null,
    // Episode metadata
    var isSeries: Boolean = false,
    var seriesName: String? = null,
    var seriesNameNormalized: String? = null,
    var seasonNumber: Int? = null,
    var episodeNumber: Int? = null,
    var episodeTitle: String? = null,
    // Thumbnail (NOT a path; tdlib resolves paths)
    var thumbFileId: Int? = null,
)
