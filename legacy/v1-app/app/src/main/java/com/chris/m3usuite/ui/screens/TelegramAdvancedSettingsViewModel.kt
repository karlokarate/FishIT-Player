package com.chris.m3usuite.ui.screens

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * State for Telegram Advanced Settings section.
 */
data class TelegramAdvancedSettingsState(
    // Engine settings
    val maxGlobalDownloads: Int = 5,
    val maxVideoDownloads: Int = 2,
    val maxThumbDownloads: Int = 3,
    val showEngineOverlay: Boolean = false,
    // Streaming / buffering settings
    val ensureFileReadyTimeoutSec: Int = 10, // Stored as seconds for UI convenience
    val showStreamingOverlay: Boolean = false,
    // Thumbnail / poster prefetch settings
    val thumbPrefetchEnabled: Boolean = true,
    val thumbPrefetchBatchSize: Int = 8,
    val thumbMaxParallel: Int = 2,
    val thumbPauseWhileVodBuffering: Boolean = true,
    val thumbFullDownload: Boolean = true,
    // ExoPlayer buffer settings (in seconds for UI)
    val exoMinBufferSec: Int = 50,
    val exoMaxBufferSec: Int = 50,
    val exoBufferForPlaybackSec: Float = 2.5f,
    val exoBufferForPlaybackAfterRebufferSec: Float = 5.0f,
    val exoExactSeek: Boolean = true,
    // Diagnostics / logging settings
    val tgAppLogLevel: Int = 1, // 0=ERROR, 1=WARN, 2=INFO, 3=DEBUG
    val jankTelemetrySampleRate: Int = 10,
)

/**
 * ViewModel for Telegram Advanced Settings section.
 * Manages runtime controls for Telegram/streaming/buffering/logging parameters.
 */
class TelegramAdvancedSettingsViewModel(
    private val app: Application,
    private val store: SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(TelegramAdvancedSettingsState())
    val state: StateFlow<TelegramAdvancedSettingsState> = _state.asStateFlow()

    init {
        // Load settings from DataStore and convert to UI units
        viewModelScope.launch {
            combine(
                // Engine settings
                store.tgMaxGlobalDownloads,
                store.tgMaxVideoDownloads,
                store.tgMaxThumbDownloads,
                store.tgShowEngineOverlay,
                // Streaming / buffering settings
                store.tgEnsureFileReadyTimeoutMs,
                store.tgShowStreamingOverlay,
                // Thumbnail / poster prefetch settings
                store.tgThumbPrefetchEnabled,
                store.tgThumbPrefetchBatchSize,
                store.tgThumbMaxParallel,
                store.tgThumbPauseWhileVodBuffering,
                store.tgThumbFullDownload,
                // ExoPlayer buffer settings
                store.exoMinBufferMs,
                store.exoMaxBufferMs,
                store.exoBufferForPlaybackMs,
                store.exoBufferForPlaybackAfterRebufferMs,
                store.exoExactSeek,
                // Diagnostics / logging settings
                store.tgAppLogLevel,
                store.jankTelemetrySampleRate,
            ) { values ->
                TelegramAdvancedSettingsState(
                    // Engine settings
                    maxGlobalDownloads = values[0] as Int,
                    maxVideoDownloads = values[1] as Int,
                    maxThumbDownloads = values[2] as Int,
                    showEngineOverlay = values[3] as Boolean,
                    // Streaming / buffering settings
                    ensureFileReadyTimeoutSec = ((values[4] as Long) / 1000L).toInt(),
                    showStreamingOverlay = values[5] as Boolean,
                    // Thumbnail / poster prefetch settings
                    thumbPrefetchEnabled = values[6] as Boolean,
                    thumbPrefetchBatchSize = values[7] as Int,
                    thumbMaxParallel = values[8] as Int,
                    thumbPauseWhileVodBuffering = values[9] as Boolean,
                    thumbFullDownload = values[10] as Boolean,
                    // ExoPlayer buffer settings (convert ms to seconds)
                    exoMinBufferSec = ((values[11] as Int) / 1000),
                    exoMaxBufferSec = ((values[12] as Int) / 1000),
                    exoBufferForPlaybackSec = ((values[13] as Int) / 1000f),
                    exoBufferForPlaybackAfterRebufferSec = ((values[14] as Int) / 1000f),
                    exoExactSeek = values[15] as Boolean,
                    // Diagnostics / logging settings
                    tgAppLogLevel = values[16] as Int,
                    jankTelemetrySampleRate = values[17] as Int,
                )
            }.collect { newState ->
                _state.value = newState
            }
        }
    }

    // Engine settings
    fun setMaxGlobalDownloads(value: Int) {
        viewModelScope.launch { store.setTgMaxGlobalDownloads(value) }
    }

    fun setMaxVideoDownloads(value: Int) {
        viewModelScope.launch { store.setTgMaxVideoDownloads(value) }
    }

    fun setMaxThumbDownloads(value: Int) {
        viewModelScope.launch { store.setTgMaxThumbDownloads(value) }
    }

    fun setShowEngineOverlay(value: Boolean) {
        viewModelScope.launch { store.setTgShowEngineOverlay(value) }
    }

    // Streaming / buffering settings (convert from KB/seconds to bytes/ms)
    fun setEnsureFileReadyTimeoutSec(value: Int) {
        viewModelScope.launch { store.setTgEnsureFileReadyTimeoutMs(value.toLong() * 1000L) }
    }

    fun setShowStreamingOverlay(value: Boolean) {
        viewModelScope.launch { store.setTgShowStreamingOverlay(value) }
    }

    // Thumbnail / poster prefetch settings
    fun setThumbPrefetchEnabled(value: Boolean) {
        viewModelScope.launch { store.setTgThumbPrefetchEnabled(value) }
    }

    fun setThumbPrefetchBatchSize(value: Int) {
        viewModelScope.launch { store.setTgThumbPrefetchBatchSize(value) }
    }

    fun setThumbMaxParallel(value: Int) {
        viewModelScope.launch { store.setTgThumbMaxParallel(value) }
    }

    fun setThumbPauseWhileVodBuffering(value: Boolean) {
        viewModelScope.launch { store.setTgThumbPauseWhileVodBuffering(value) }
    }

    fun setThumbFullDownload(value: Boolean) {
        viewModelScope.launch { store.setTgThumbFullDownload(value) }
    }

    // ExoPlayer buffer settings (convert from seconds to ms)
    fun setExoMinBufferSec(value: Int) {
        viewModelScope.launch { store.setExoMinBufferMs(value * 1000) }
    }

    fun setExoMaxBufferSec(value: Int) {
        viewModelScope.launch { store.setExoMaxBufferMs(value * 1000) }
    }

    fun setExoBufferForPlaybackSec(value: Float) {
        viewModelScope.launch { store.setExoBufferForPlaybackMs((value * 1000f).toInt()) }
    }

    fun setExoBufferForPlaybackAfterRebufferSec(value: Float) {
        viewModelScope.launch { store.setExoBufferForPlaybackAfterRebufferMs((value * 1000f).toInt()) }
    }

    fun setExoExactSeek(value: Boolean) {
        viewModelScope.launch { store.setExoExactSeek(value) }
    }

    // Diagnostics / logging settings
    fun setTgAppLogLevel(value: Int) {
        viewModelScope.launch { store.setTgAppLogLevel(value) }
    }

    fun setJankTelemetrySampleRate(value: Int) {
        viewModelScope.launch { store.setJankTelemetrySampleRate(value) }
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val store = SettingsStore(app)
                    return TelegramAdvancedSettingsViewModel(app, store) as T
                }
            }
    }
}
