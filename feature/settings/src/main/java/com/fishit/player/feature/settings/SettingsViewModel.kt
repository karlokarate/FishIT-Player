package com.fishit.player.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.core.catalogsync.CatalogSyncWorkScheduler
import com.fishit.player.core.catalogsync.SyncStateObserver
import com.fishit.player.core.catalogsync.TmdbEnrichmentScheduler
import com.fishit.player.core.metadata.tmdb.TmdbConfigProvider
import com.fishit.player.core.sourceactivation.SourceActivationSnapshot
import com.fishit.player.core.sourceactivation.SourceActivationState
import com.fishit.player.core.sourceactivation.SourceActivationStore
import com.fishit.player.core.sourceactivation.SourceErrorReason
import com.fishit.player.core.sourceactivation.SourceId
import com.fishit.player.infra.cache.CacheManager
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 *
 * **Architecture:**
 * - Depends only on interfaces (injected via Hilt)
 * - UI never calls transport/pipelines directly
 * - All sync triggers via SSOT schedulers (WorkManager)
 * - All cache operations via CacheManager (no direct file IO)
 *
 * **Data Sources:**
 * - [SourceActivationStore] - Source activation states (read-only)
 * - [SyncStateObserver] - Catalog sync state
 * - [CatalogSyncWorkScheduler] - Sync actions (SSOT)
 * - [TmdbEnrichmentScheduler] - TMDB enrichment actions (SSOT)
 * - [TmdbConfigProvider] - TMDB enabled status
 * - [CacheManager] - Cache sizes and clear actions
 *
 * **Contract:** CATALOG_SYNC_WORKERS_CONTRACT_V2, STARTUP_TRIGGER_CONTRACT
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val sourceActivationStore: SourceActivationStore,
        private val syncStateObserver: SyncStateObserver,
        private val catalogSyncWorkScheduler: CatalogSyncWorkScheduler,
        private val tmdbEnrichmentScheduler: TmdbEnrichmentScheduler,
        private val tmdbConfigProvider: TmdbConfigProvider,
        private val cacheManager: CacheManager,
    ) : ViewModel() {
        companion object {
            private const val TAG = "SettingsViewModel"
        }

        private val _state = MutableStateFlow(SettingsUiState())
        val state: StateFlow<SettingsUiState> = _state.asStateFlow()

        init {
            observeSourceActivation()
            observeSyncState()
            loadTmdbStatus()
            loadCacheSizes()
        }

        // =========================================================================
        // Observers
        // =========================================================================

        private fun observeSourceActivation() {
            viewModelScope.launch {
                sourceActivationStore.observeStates().collect { snapshot ->
                    updateSourceStates(snapshot)
                }
            }
        }

        private fun updateSourceStates(snapshot: SourceActivationSnapshot) {
            _state.update {
                it.copy(
                    telegramActive = SourceId.TELEGRAM in snapshot.activeSources,
                    telegramDetails = snapshot.telegram.toDetailsString(),
                    xtreamActive = SourceId.XTREAM in snapshot.activeSources,
                    xtreamDetails = snapshot.xtream.toDetailsString(),
                    ioActive = SourceId.IO in snapshot.activeSources,
                    ioDetails = snapshot.io.toDetailsString(),
                )
            }
        }

        /**
         * Convert SourceActivationState to human-readable details string.
         */
        private fun SourceActivationState.toDetailsString(): String =
            when (this) {
                SourceActivationState.Inactive -> ""
                SourceActivationState.Active -> "Bereit"
                is SourceActivationState.Error ->
                    when (reason) {
                        SourceErrorReason.LOGIN_REQUIRED -> "Login erforderlich"
                        SourceErrorReason.INVALID_CREDENTIALS -> "Ungültige Zugangsdaten"
                        SourceErrorReason.PERMISSION_MISSING -> "Berechtigung fehlt"
                        SourceErrorReason.TRANSPORT_ERROR -> "Verbindungsfehler"
                        SourceErrorReason.KEYSTORE_UNAVAILABLE -> "Sicherer Speicher nicht verfügbar"
                    }
            }

        private fun observeSyncState() {
            viewModelScope.launch {
                syncStateObserver.observeSyncState().collect { syncState ->
                    _state.update {
                        it.copy(
                            syncState = syncState,
                            isSyncActionInProgress = syncState.isRunning,
                        )
                    }
                }
            }
        }

        private fun loadTmdbStatus() {
            val config = tmdbConfigProvider.getConfig()
            _state.update {
                it.copy(
                    tmdbEnabled = config.isEnabled,
                    tmdbApiKeyPresent = config.apiKey.isNotBlank(),
                )
            }
        }

        private fun loadCacheSizes() {
            viewModelScope.launch {
                _state.update { it.copy(isLoadingCacheSizes = true) }

                try {
                    val telegramSize = cacheManager.getTelegramCacheSizeBytes()
                    val imageSize = cacheManager.getImageCacheSizeBytes()
                    val dbSize = cacheManager.getDatabaseSizeBytes()

                    _state.update {
                        it.copy(
                            telegramCacheSize = telegramSize.formatAsSize(),
                            imageCacheSize = imageSize.formatAsSize(),
                            dbSize = dbSize.formatAsSize(),
                            isLoadingCacheSizes = false,
                        )
                    }
                } catch (e: Exception) {
                    UnifiedLog.e(TAG, e) { "Failed to load cache sizes" }
                    _state.update { it.copy(isLoadingCacheSizes = false) }
                }
            }
        }

        // =========================================================================
        // Sync Actions (SSOT via WorkManager)
        // =========================================================================

        /**
         * Trigger sync now (won't interrupt running sync).
         * Uses ExistingWorkPolicy.KEEP.
         */
        fun syncNow() {
            UnifiedLog.i(TAG) { "User triggered: Sync Now" }
            catalogSyncWorkScheduler.enqueueExpertSyncNow()
            showSnackbar("Sync gestartet")
        }

        /**
         * Force rescan (replaces running sync).
         * Uses ExistingWorkPolicy.REPLACE.
         */
        fun forceRescan() {
            UnifiedLog.i(TAG) { "User triggered: Force Rescan" }
            catalogSyncWorkScheduler.enqueueForceRescan()
            showSnackbar("Rescan gestartet")
        }

        /**
         * Cancel running sync.
         */
        fun cancelSync() {
            UnifiedLog.i(TAG) { "User triggered: Cancel Sync" }
            catalogSyncWorkScheduler.cancelSync()
            showSnackbar("Sync abgebrochen")
        }

        // =========================================================================
        // TMDB Actions (SSOT via WorkManager)
        // =========================================================================

        /**
         * Force TMDB refresh (replaces running enrichment).
         * Uses ExistingWorkPolicy.REPLACE.
         */
        fun forceTmdbRefresh() {
            if (!_state.value.tmdbEnabled) {
                showSnackbar("TMDB ist deaktiviert (API Key fehlt)")
                return
            }

            UnifiedLog.i(TAG) { "User triggered: Force TMDB Refresh" }
            _state.update { it.copy(isTmdbRefreshing = true) }
            tmdbEnrichmentScheduler.enqueueForceRefresh()
            showSnackbar("TMDB Refresh gestartet")
            // Note: isTmdbRefreshing will be cleared by observing work state if needed
            _state.update { it.copy(isTmdbRefreshing = false) }
        }

        // =========================================================================
        // Cache Actions (via CacheManager)
        // =========================================================================

        /**
         * Clear Telegram/Telegram API cache.
         */
        fun clearTelegramCache() {
            viewModelScope.launch {
                _state.update { it.copy(isClearingTelegramCache = true) }
                try {
                    val success = cacheManager.clearTelegramCache()
                    if (success) {
                        showSnackbar("Telegram Cache gelöscht")
                        loadCacheSizes() // Refresh sizes
                    } else {
                        showSnackbar("Fehler beim Löschen des Telegram Cache")
                    }
                } catch (e: Exception) {
                    UnifiedLog.e(TAG, e) { "Failed to clear Telegram cache" }
                    showSnackbar("Fehler: ${e.message}")
                } finally {
                    _state.update { it.copy(isClearingTelegramCache = false) }
                }
            }
        }

        /**
         * Clear image cache.
         */
        fun clearImageCache() {
            viewModelScope.launch {
                _state.update { it.copy(isClearingImageCache = true) }
                try {
                    val success = cacheManager.clearImageCache()
                    if (success) {
                        showSnackbar("Image Cache gelöscht")
                        loadCacheSizes() // Refresh sizes
                    } else {
                        showSnackbar("Fehler beim Löschen des Image Cache")
                    }
                } catch (e: Exception) {
                    UnifiedLog.e(TAG, e) { "Failed to clear image cache" }
                    showSnackbar("Fehler: ${e.message}")
                } finally {
                    _state.update { it.copy(isClearingImageCache = false) }
                }
            }
        }

        // =========================================================================
        // UI Helpers
        // =========================================================================

        private fun showSnackbar(message: String) {
            _state.update { it.copy(snackbarMessage = message) }
        }

        fun clearSnackbar() {
            _state.update { it.copy(snackbarMessage = null) }
        }

        /**
         * Refresh cache sizes manually.
         */
        fun refreshCacheSizes() {
            loadCacheSizes()
        }
    }
