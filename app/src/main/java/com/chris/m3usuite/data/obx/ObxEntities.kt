package com.chris.m3usuite.data.obx

import io.objectbox.annotation.*

@Entity
data class ObxCategory(
    @Id var id: Long = 0,
    @Index var kind: String = "",            // live|vod|series
    @Index var categoryId: String = "",
    var categoryName: String? = null,
)

@Entity
data class ObxLive(
    @Id var id: Long = 0,
    @Unique @Index var streamId: Int = 0,
    @Index var nameLower: String = "",
    @Index var sortTitleLower: String = "",
    var name: String = "",
    var logo: String? = null,
    var epgChannelId: String? = null,
    var tvArchive: Int? = null,
    @Index var categoryId: String? = null,
    @Index var providerKey: String? = null,
    @Index var genreKey: String? = null,
)

@Entity
data class ObxVod(
    @Id var id: Long = 0,
    @Unique @Index var vodId: Int = 0,
    @Index var nameLower: String = "",
    @Index var sortTitleLower: String = "",
    var name: String = "",
    var poster: String? = null,
    var imagesJson: String? = null,
    @Index var year: Int? = null,
    @Index var yearKey: Int? = null,
    var rating: Double? = null,
    var plot: String? = null,
    var genre: String? = null,
    var director: String? = null,
    var cast: String? = null,
    var country: String? = null,
    var releaseDate: String? = null,
    var imdbId: String? = null,
    var tmdbId: String? = null,
    var trailer: String? = null,
    var containerExt: String? = null,
    var durationSecs: Int? = null,
    @Index var categoryId: String? = null,
    @Index var providerKey: String? = null,
    @Index var genreKey: String? = null,
    @Index var importedAt: Long? = null,
    @Index var updatedAt: Long? = null,
)

@Entity
data class ObxSeries(
    @Id var id: Long = 0,
    @Unique @Index var seriesId: Int = 0,
    @Index var nameLower: String = "",
    @Index var sortTitleLower: String = "",
    var name: String = "",
    var imagesJson: String? = null,
    @Index var year: Int? = null,
    @Index var yearKey: Int? = null,
    var rating: Double? = null,
    var plot: String? = null,
    var genre: String? = null,
    var director: String? = null,
    var cast: String? = null,
    var imdbId: String? = null,
    var tmdbId: String? = null,
    var trailer: String? = null,
    var country: String? = null,
    var releaseDate: String? = null,
    @Index var categoryId: String? = null,
    @Index var providerKey: String? = null,
    @Index var genreKey: String? = null,
    @Index var importedAt: Long? = null,
    @Index var updatedAt: Long? = null,
)

@Entity
data class ObxEpisode(
    @Id var id: Long = 0,
    @Index var seriesId: Int = 0,
    @Index var season: Int = 0,
    var episodeNum: Int = 0,
    @Index var episodeId: Int = 0,
    var title: String? = null,
    var durationSecs: Int? = null,
    var rating: Double? = null,
    var plot: String? = null,
    var airDate: String? = null,
    var playExt: String? = null,
    var imageUrl: String? = null,
    // Telegram bridging for direct playback via tg://
    @Index var tgChatId: Long? = null,
    @Index var tgMessageId: Long? = null,
    @Index var tgFileId: Int? = null,
    // Enriched media meta for UI/filtering
    var mimeType: String? = null,
    var width: Int? = null,
    var height: Int? = null,
    @Index var sizeBytes: Long? = null,
    var supportsStreaming: Boolean? = null,
    @Index var language: String? = null,
)

@Entity
data class ObxEpgNowNext(
    @Id var id: Long = 0,
    @Index var streamId: Int? = null,
    @Index var channelId: String? = null,
    var nowTitle: String? = null,
    var nowStartMs: Long? = null,
    var nowEndMs: Long? = null,
    var nextTitle: String? = null,
    var nextStartMs: Long? = null,
    var nextEndMs: Long? = null,
    @Index var updatedAt: Long = 0,
)

@Entity
data class ObxProfile(
    @Id var id: Long = 0,
    var name: String = "",
    @Index var type: String = "adult", // adult | kid | guest
    var avatarPath: String? = null,
    var createdAt: Long = 0,
    var updatedAt: Long = 0,
)

@Entity
data class ObxProfilePermissions(
    @Id var id: Long = 0,
    @Index var profileId: Long = 0,
    var canOpenSettings: Boolean = true,
    var canChangeSources: Boolean = true,
    var canUseExternalPlayer: Boolean = true,
    var canEditFavorites: Boolean = true,
    var canSearch: Boolean = true,
    var canSeeResume: Boolean = true,
    var canEditWhitelist: Boolean = true,
)

@Entity
data class ObxKidContentAllow(
    @Id var id: Long = 0,
    @Index var kidProfileId: Long = 0,
    @Index var contentType: String = "vod", // live|vod|series
    @Index var contentId: Long = 0,
)

@Entity
data class ObxKidCategoryAllow(
    @Id var id: Long = 0,
    @Index var kidProfileId: Long = 0,
    @Index var contentType: String = "vod",
    @Index var categoryId: String = "",
)

@Entity
data class ObxKidContentBlock(
    @Id var id: Long = 0,
    @Index var kidProfileId: Long = 0,
    @Index var contentType: String = "vod",
    @Index var contentId: Long = 0,
)

@Entity
data class ObxScreenTimeEntry(
    @Id var id: Long = 0,
    @Index var kidProfileId: Long = 0,
    @Index var dayYyyymmdd: String = "",
    var usedMinutes: Int = 0,
    var limitMinutes: Int = 0,
)

@Entity
data class ObxResumeMark(
    @Id var id: Long = 0,
    @Index var type: String = "vod", // vod | series
    // VOD: encoded media id (OBX scheme) to avoid Room mapping
    @Index var mediaEncodedId: Long? = null,
    // Series: composite identification for OBX episodes (when xtream episode id is not present)
    @Index var seriesId: Int? = null,
    @Index var season: Int? = null,
    @Index var episodeNum: Int? = null,
    var positionSecs: Int = 0,
    @Index var updatedAt: Long = 0,
)

@Entity
data class ObxTelegramMessage(
    @Id var id: Long = 0,
    @Index var chatId: Long = 0,
    @Index var messageId: Long = 0,
    @Index var fileId: Int? = null,
    @Index var fileUniqueId: String? = null,
    var supportsStreaming: Boolean? = null,
    var caption: String? = null,
    @Index var captionLower: String? = null,
    var date: Long? = null,
    @Index var localPath: String? = null,
    var thumbFileId: Int? = null,
    var thumbLocalPath: String? = null,
    var fileName: String? = null,
    // Enriched metadata (best-effort via TDLib):
    var durationSecs: Int? = null,
    var mimeType: String? = null,
    @Index var sizeBytes: Long? = null,
    var width: Int? = null,
    var height: Int? = null,
    @Index var language: String? = null,
)

// --- Aggregated index tables (persisted once per import; no full scans required) ---

@Entity
data class ObxIndexProvider(
    @Id var id: Long = 0,
    @Index var kind: String = "",  // live|vod|series
    @Index var key: String = "",
    var count: Long = 0,
)

@Entity
data class ObxIndexYear(
    @Id var id: Long = 0,
    @Index var kind: String = "",
    @Index var key: Int = 0,
    var count: Long = 0,
)

@Entity
data class ObxIndexGenre(
    @Id var id: Long = 0,
    @Index var kind: String = "",
    @Index var key: String = "",
    var count: Long = 0,
)

@Entity
data class ObxIndexLang(
    @Id var id: Long = 0,
    @Index var kind: String = "",
    @Index var key: String = "",
    var count: Long = 0,
)

@Entity
data class ObxIndexQuality(
    @Id var id: Long = 0,
    @Index var kind: String = "",
    @Index var key: String = "",
    var count: Long = 0,
)
