package com.fishit.player.feature.settings

import com.fishit.player.core.catalogsync.SyncUiState

/**
 * UI state for the Settings screen.
 *
 * Contains all data needed to render the premium settings view:
 * - Source activation states (read-only)
 * - Sync state and controls
 * - TMDB enabled status
 * - Cache sizes and clear actions
 */
data class SettingsUiState(
    // === Sources (read-only status) ===
    val telegramActive: Boolean = false,
    val telegramDetails: String? = null,
    val xtreamActive: Boolean = false,
    val xtreamDetails: String? = null,
    val ioActive: Boolean = false,
    val ioDetails: String? = null,

    // === Sync State ===
    val syncState: SyncUiState = SyncUiState.Idle,
    val isSyncActionInProgress: Boolean = false,

    // === TMDB ===
    val tmdbEnabled: Boolean = false,
    val tmdbApiKeyPresent: Boolean = false,
    val isTmdbRefreshing: Boolean = false,

    // === Cache ===
    val telegramCacheSize: String = "—",
    val imageCacheSize: String = "—",
    val dbSize: String = "—",
    val isLoadingCacheSizes: Boolean = true,
    val isClearingTelegramCache: Boolean = false,
    val isClearingImageCache: Boolean = false,

    // === Feedback ===
    val snackbarMessage: String? = null,
)

// Note: formatAsSize() extension is defined in DebugInfoProvider.kt
