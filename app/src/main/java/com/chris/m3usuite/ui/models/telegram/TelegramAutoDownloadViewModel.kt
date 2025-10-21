
package com.chris.m3usuite.ui.models.telegram

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.service.TelegramServiceClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AutoUiState(
    // Wi‑Fi
    val wifiEnabled: Boolean = true,
    val wifiPreloadLarge: Boolean = true,
    val wifiPreloadNextAudio: Boolean = true,
    val wifiPreloadStories: Boolean = false,
    val wifiLessDataCalls: Boolean = false,
    // Mobile
    val mobileEnabled: Boolean = true,
    val mobilePreloadLarge: Boolean = false,
    val mobilePreloadNextAudio: Boolean = false,
    val mobilePreloadStories: Boolean = false,
    val mobileLessDataCalls: Boolean = true,
    // Roaming
    val roamingEnabled: Boolean = false,
    val roamingPreloadLarge: Boolean = false,
    val roamingPreloadNextAudio: Boolean = false,
    val roamingPreloadStories: Boolean = false,
    val roamingLessDataCalls: Boolean = true,
    val isApplying: Boolean = false
)

class TelegramAutoDownloadViewModel(
    private val app: Application,
    private val store: SettingsStore,
    private val tg: TelegramServiceClient,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _state = MutableStateFlow(AutoUiState())
    val state: StateFlow<AutoUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                // wifi
                store.tgAutoWifiEnabled,
                store.tgAutoWifiPreloadLarge,
                store.tgAutoWifiPreloadNextAudio,
                store.tgAutoWifiPreloadStories,
                store.tgAutoWifiLessDataCalls,
                // mobile
                store.tgAutoMobileEnabled,
                store.tgAutoMobilePreloadLarge,
                store.tgAutoMobilePreloadNextAudio,
                store.tgAutoMobilePreloadStories,
                store.tgAutoMobileLessDataCalls,
                // roaming
                store.tgAutoRoamingEnabled,
                store.tgAutoRoamingPreloadLarge,
                store.tgAutoRoamingPreloadNextAudio,
                store.tgAutoRoamingPreloadStories,
                store.tgAutoRoamingLessDataCalls
            ) { arr: Array<Any?> ->
                AutoUiState(
                    // wifi
                    wifiEnabled = arr[0] as Boolean,
                    wifiPreloadLarge = arr[1] as Boolean,
                    wifiPreloadNextAudio = arr[2] as Boolean,
                    wifiPreloadStories = arr[3] as Boolean,
                    wifiLessDataCalls = arr[4] as Boolean,
                    // mobile
                    mobileEnabled = arr[5] as Boolean,
                    mobilePreloadLarge = arr[6] as Boolean,
                    mobilePreloadNextAudio = arr[7] as Boolean,
                    mobilePreloadStories = arr[8] as Boolean,
                    mobileLessDataCalls = arr[9] as Boolean,
                    // roaming
                    roamingEnabled = arr[10] as Boolean,
                    roamingPreloadLarge = arr[11] as Boolean,
                    roamingPreloadNextAudio = arr[12] as Boolean,
                    roamingPreloadStories = arr[13] as Boolean,
                    roamingLessDataCalls = arr[14] as Boolean
                )
            }.collectLatest { ui -> _state.value = ui }
        }
    }

    // --- Public change APIs (persist + apply to TDLib) ---
    fun setWifi(
        enabled: Boolean? = null,
        preloadLarge: Boolean? = null,
        preloadNextAudio: Boolean? = null,
        preloadStories: Boolean? = null,
        lessDataCalls: Boolean? = null
    ) = applyNetwork(
        "wifi",
        _state.value.copy(
            wifiEnabled = enabled ?: _state.value.wifiEnabled,
            wifiPreloadLarge = preloadLarge ?: _state.value.wifiPreloadLarge,
            wifiPreloadNextAudio = preloadNextAudio ?: _state.value.wifiPreloadNextAudio,
            wifiPreloadStories = preloadStories ?: _state.value.wifiPreloadStories,
            wifiLessDataCalls = lessDataCalls ?: _state.value.wifiLessDataCalls
        )
    )

    fun setMobile(
        enabled: Boolean? = null,
        preloadLarge: Boolean? = null,
        preloadNextAudio: Boolean? = null,
        preloadStories: Boolean? = null,
        lessDataCalls: Boolean? = null
    ) = applyNetwork(
        "mobile",
        _state.value.copy(
            mobileEnabled = enabled ?: _state.value.mobileEnabled,
            mobilePreloadLarge = preloadLarge ?: _state.value.mobilePreloadLarge,
            mobilePreloadNextAudio = preloadNextAudio ?: _state.value.mobilePreloadNextAudio,
            mobilePreloadStories = preloadStories ?: _state.value.mobilePreloadStories,
            mobileLessDataCalls = lessDataCalls ?: _state.value.mobileLessDataCalls
        )
    )

    fun setRoaming(
        enabled: Boolean? = null,
        preloadLarge: Boolean? = null,
        preloadNextAudio: Boolean? = null,
        preloadStories: Boolean? = null,
        lessDataCalls: Boolean? = null
    ) = applyNetwork(
        "roaming",
        _state.value.copy(
            roamingEnabled = enabled ?: _state.value.roamingEnabled,
            roamingPreloadLarge = preloadLarge ?: _state.value.roamingPreloadLarge,
            roamingPreloadNextAudio = preloadNextAudio ?: _state.value.roamingPreloadNextAudio,
            roamingPreloadStories = preloadStories ?: _state.value.roamingPreloadStories,
            roamingLessDataCalls = lessDataCalls ?: _state.value.roamingLessDataCalls
        )
    )

    private fun applyNetwork(type: String, s: AutoUiState) {
        _state.update { it.copy(isApplying = true) }
        viewModelScope.launch {
            // 1) Persist
            withContext(io) {
                when (type) {
                    "wifi" -> {
                        store.setTgAutoWifiEnabled(s.wifiEnabled)
                        store.setTgAutoWifiPreloadLarge(s.wifiPreloadLarge)
                        store.setTgAutoWifiPreloadNextAudio(s.wifiPreloadNextAudio)
                        store.setTgAutoWifiPreloadStories(s.wifiPreloadStories)
                        store.setTgAutoWifiLessDataCalls(s.wifiLessDataCalls)
                    }
                    "mobile" -> {
                        store.setTgAutoMobileEnabled(s.mobileEnabled)
                        store.setTgAutoMobilePreloadLarge(s.mobilePreloadLarge)
                        store.setTgAutoMobilePreloadNextAudio(s.mobilePreloadNextAudio)
                        store.setTgAutoMobilePreloadStories(s.mobilePreloadStories)
                        store.setTgAutoMobileLessDataCalls(s.mobileLessDataCalls)
                    }
                    else -> { // roaming
                        store.setTgAutoRoamingEnabled(s.roamingEnabled)
                        store.setTgAutoRoamingPreloadLarge(s.roamingPreloadLarge)
                        store.setTgAutoRoamingPreloadNextAudio(s.roamingPreloadNextAudio)
                        store.setTgAutoRoamingPreloadStories(s.roamingPreloadStories)
                        store.setTgAutoRoamingLessDataCalls(s.roamingLessDataCalls)
                    }
                }
            }
            // 2) Apply live to TDLib
            tg.setAutoDownload(
                type = type,
                enabled = when (type) {
                    "wifi" -> s.wifiEnabled
                    "mobile" -> s.mobileEnabled
                    else -> s.roamingEnabled
                },
                preloadLarge = when (type) {
                    "wifi" -> s.wifiPreloadLarge
                    "mobile" -> s.mobilePreloadLarge
                    else -> s.roamingPreloadLarge
                },
                preloadNext = when (type) {
                    "wifi" -> s.wifiPreloadNextAudio
                    "mobile" -> s.mobilePreloadNextAudio
                    else -> s.roamingPreloadNextAudio
                },
                preloadStories = when (type) {
                    "wifi" -> s.wifiPreloadStories
                    "mobile" -> s.mobilePreloadStories
                    else -> s.roamingPreloadStories
                },
                lessDataCalls = when (type) {
                    "wifi" -> s.wifiLessDataCalls
                    "mobile" -> s.mobileLessDataCalls
                    else -> s.roamingLessDataCalls
                }
            )
            _state.update { it.copy(isApplying = false) }
        }
    }

    companion object {
        fun Factory(app: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(TelegramAutoDownloadViewModel::class.java)) {
                    val store = SettingsStore(app.applicationContext)
                    val service = TelegramServiceClient(app.applicationContext)
                    return TelegramAutoDownloadViewModel(app, store, service) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
