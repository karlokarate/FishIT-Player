@file:Suppress("MemberVisibilityCanBePrivate")

package com.fishit.player.core.feature

/*
 * Central catalog of all v2 feature IDs, grouped by domain.
 *
 * See: docs/v2/architecture/FEATURE_SYSTEM_TARGET_MODEL.md
 */

// =============================================================================
// Canonical Media Features
// =============================================================================

/**
 * Features related to the canonical media model and normalization.
 */
object CanonicalMediaFeatures {
    /** Canonical media model (MediaItem, Episode, etc.) */
    val CANONICAL_MODEL = FeatureId("media.canonical_model")

    /** RawMediaMetadata → normalized MediaItem transformation */
    val NORMALIZE = FeatureId("media.normalize")

    /** TMDB metadata resolution */
    val RESOLVE_TMDB = FeatureId("media.resolve.tmdb")
}

// =============================================================================
// Telegram Features
// =============================================================================

/**
 * Features related to Telegram/TDLib pipeline.
 */
object TelegramFeatures {
    /** Complete chat history scanning and streaming */
    val FULL_HISTORY_STREAMING = FeatureId("telegram.full_history_streaming")

    /** On-demand thumbnail loading from TDLib */
    val LAZY_THUMBNAILS = FeatureId("telegram.lazy_thumbnails")
}

// =============================================================================
// Xtream Features
// =============================================================================

/**
 * Features related to Xtream Codes API pipeline.
 */
object XtreamFeatures {
    /** Live TV streaming via Xtream */
    val LIVE_STREAMING = FeatureId("xtream.live_streaming")

    /** VOD playback via Xtream */
    val VOD_PLAYBACK = FeatureId("xtream.vod_playback")

    /** Series and episode metadata from Xtream */
    val SERIES_METADATA = FeatureId("xtream.series_metadata")
}

// =============================================================================
// App Features
// =============================================================================

/**
 * App-level features.
 */
object AppFeatures {
    /** App-wide cache management (clear cache, storage info, etc.) */
    val CACHE_MANAGEMENT = FeatureId("app.cache_management")
}

// =============================================================================
// Logging Features
// =============================================================================

/**
 * Logging and telemetry features.
 */
object LoggingFeatures {
    /** Unified logging facade */
    val UNIFIED_LOGGING = FeatureId("infra.logging.unified")
}

// =============================================================================
// Settings Features
// =============================================================================

/**
 * Settings and configuration features.
 */
object SettingsFeatures {
    /** Single DataStore for all app settings */
    val CORE_SINGLE_DATASTORE = FeatureId("settings.core_single_datastore")
}

// =============================================================================
// UI Screen Features
// =============================================================================

/**
 * UI screen features representing navigation destinations.
 */
object UiFeatures {
    /** Home screen */
    val SCREEN_HOME = FeatureId("ui.screen.home")

    /** Library screen */
    val SCREEN_LIBRARY = FeatureId("ui.screen.library")

    /** Telegram media screen */
    val SCREEN_TELEGRAM = FeatureId("ui.screen.telegram")

    /** Settings screen */
    val SCREEN_SETTINGS = FeatureId("ui.screen.settings")

    /** Live TV screen */
    val SCREEN_LIVE = FeatureId("ui.screen.live")

    /** Audiobooks screen */
    val SCREEN_AUDIOBOOKS = FeatureId("ui.screen.audiobooks")
}
