package com.chris.m3usuite.player.internal.domain

/**
 * High-level domain context for a playback session.
 *
 * This is intentionally decoupled from ExoPlayer, TDLib, and UI.
 * It exists so that:
 * - Resume logic
 * - Kids/screentime enforcement
 * - Live/EPG logic
 * - Diagnostics & logging
 *
 * can all reason about "what" is being played without depending on the
 * concrete player implementation.
 */
data class PlaybackContext(
    val type: PlaybackType,
    // VOD / Telegram-style media id (encoded OBX id or Telegram file id)
    val mediaId: Long? = null,
    // Series (legacy episode id – mostly unused in OBX path, but kept for compatibility)
    val episodeId: Int? = null,
    // Series composite key (OBX series id)
    val seriesId: Int? = null,
    val season: Int? = null,
    val episodeNumber: Int? = null,
    // Live-TV context hints for navigation & lists
    val liveCategoryHint: String? = null,
    val liveProviderHint: String? = null,
    // Optional kid profile id, if already known; if null, the Kids gate
    // will derive it from SettingsStore.currentProfileId.
    val kidProfileId: Long? = null,
)

/**
 * Coarse playback type, derived from the original legacy flags:
 * - VOD / SERIES / LIVE
 *
 * More specific behaviour (e.g. special handling for series finales)
 * should be implemented in domain services using PlaybackContext,
 * not in the UI.
 */
enum class PlaybackType {
    VOD,
    SERIES,
    LIVE,
}
