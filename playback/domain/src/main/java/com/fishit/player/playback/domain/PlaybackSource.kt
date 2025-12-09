package com.fishit.player.playback.domain

/**
 * Represents a resolved playback source ready for the player.
 *
 * This is a lightweight descriptor that the internal player uses
 * to create the appropriate Media3 MediaItem and DataSource.
 *
 * **Note:** This does NOT contain Media3 types directly to keep
 * playback:domain free of Media3 dependencies. The actual MediaItem
 * is built by InternalPlaybackSourceResolver in player:internal.
 *
 * @property uri The resolved playback URI
 * @property mimeType Optional MIME type hint (e.g., "video/mp4", "application/x-mpegURL")
 * @property headers HTTP headers for authenticated/protected streams
 * @property drmConfig Optional DRM configuration (license URL, etc.)
 * @property dataSourceType Hint for which DataSource to use
 */
data class PlaybackSource(
    val uri: String,
    val mimeType: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val drmConfig: DrmConfig? = null,
    val dataSourceType: DataSourceType = DataSourceType.DEFAULT,
)

/**
 * Hint for which DataSource implementation to use.
 */
enum class DataSourceType {
    /** Use default HTTP DataSource */
    DEFAULT,

    /** Use Telegram-specific DataSource (TelegramFileDataSource) */
    TELEGRAM_FILE,

    /** Use local file DataSource */
    LOCAL_FILE,

    /** Use progressive download DataSource */
    PROGRESSIVE,
}

/**
 * DRM configuration for protected content.
 *
 * Reserved for future Widevine/ClearKey support.
 */
data class DrmConfig(
    val licenseUrl: String,
    val scheme: String = "widevine",
    val headers: Map<String, String> = emptyMap(),
)
