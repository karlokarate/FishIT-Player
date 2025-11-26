package com.chris.m3usuite.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chris.m3usuite.data.obx.ObxProfile
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.player.internal.subtitles.DefaultSubtitleStyleManager
import com.chris.m3usuite.player.internal.subtitles.SubtitlePreset
import com.chris.m3usuite.player.internal.subtitles.SubtitleStyle
import com.chris.m3usuite.player.internal.subtitles.SubtitleStyleManager
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI state for subtitle settings in SettingsScreen.
 *
 * **Phase 4 Group 5: SettingsScreen Integration**
 *
 * This state reflects the current subtitle style from SubtitleStyleManager
 * and whether the current profile is a kid profile (which disables subtitle settings).
 *
 * @property style Current subtitle style
 * @property currentPreset Currently selected preset (if applicable)
 * @property isKidProfile True if the current profile is a kid profile
 * @property isSaving True when a save operation is in progress
 */
data class SubtitleSettingsState(
    val style: SubtitleStyle = SubtitleStyle(),
    val currentPreset: SubtitlePreset = SubtitlePreset.DEFAULT,
    val isKidProfile: Boolean = false,
    val isSaving: Boolean = false,
)

/**
 * ViewModel for subtitle settings using SubtitleStyleManager.
 *
 * **Phase 4 Group 5: SettingsScreen Integration**
 *
 * This ViewModel provides subtitle style management for the SettingsScreen,
 * backed by the same SubtitleStyleManager used by the SIP player.
 *
 * **Key Behaviors:**
 * - Uses DefaultSubtitleStyleManager for persistence via SettingsStore
 * - Detects kid profiles and disables subtitle settings
 * - Syncs with SubtitleStyleManager StateFlows for real-time updates
 *
 * **Contract Section 9:**
 * - Per-profile persistence (via SettingsStore.currentProfileId)
 * - Kid Mode: Section is hidden/disabled
 * - Real-time preview updates
 */
class SubtitleSettingsViewModel(
    app: Application,
    private val settings: SettingsStore,
) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(SubtitleSettingsState())
    val state: StateFlow<SubtitleSettingsState> = _state

    // SubtitleStyleManager instance (same implementation as SIP player)
    private val subtitleStyleManager: SubtitleStyleManager =
        DefaultSubtitleStyleManager(
            settingsStore = settings,
            scope = viewModelScope,
        )

    init {
        observeStyleChanges()
        detectKidProfile()
    }

    /**
     * Observes SubtitleStyleManager.currentStyle and currentPreset.
     * Updates state when style changes (e.g., from SIP player).
     */
    private fun observeStyleChanges() {
        viewModelScope.launch {
            subtitleStyleManager.currentStyle.collect { style ->
                _state.value = _state.value.copy(style = style)
            }
        }

        viewModelScope.launch {
            subtitleStyleManager.currentPreset.collect { preset ->
                _state.value = _state.value.copy(currentPreset = preset)
            }
        }
    }

    /**
     * Detects if the current profile is a kid profile.
     * If so, subtitle settings should be hidden/disabled.
     */
    private fun detectKidProfile() {
        viewModelScope.launch {
            settings.currentProfileId.collect { profileId ->
                val isKid =
                    withContext(Dispatchers.IO) {
                        if (profileId <= 0) {
                            false
                        } else {
                            try {
                                val context = getApplication<Application>()
                                val box = ObxStore.get(context).boxFor(ObxProfile::class.java)
                                val profile = box.get(profileId)
                                profile?.type == "kid"
                            } catch (_: Throwable) {
                                false
                            }
                        }
                    }
                _state.value = _state.value.copy(isKidProfile = isKid)
            }
        }
    }

    /**
     * Updates the subtitle style.
     * Called when user changes sliders or color pickers.
     */
    fun onUpdateStyle(style: SubtitleStyle) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            try {
                subtitleStyleManager.updateStyle(style)
            } finally {
                _state.value = _state.value.copy(isSaving = false)
            }
        }
    }

    /**
     * Applies a subtitle preset.
     * Called when user clicks a preset button.
     */
    fun onApplyPreset(preset: SubtitlePreset) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            try {
                subtitleStyleManager.applyPreset(preset)
            } finally {
                _state.value = _state.value.copy(isSaving = false)
            }
        }
    }

    /**
     * Resets subtitle style to contract defaults.
     */
    fun onResetToDefault() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            try {
                subtitleStyleManager.resetToDefault()
            } finally {
                _state.value = _state.value.copy(isSaving = false)
            }
        }
    }

    /**
     * Updates text scale.
     * Convenience method for slider changes.
     *
     * @param scale New text scale (0.5-2.0)
     */
    fun onChangeTextScale(scale: Float) {
        val currentStyle = _state.value.style
        val newStyle = currentStyle.copy(textScale = scale.coerceIn(0.5f, 2.0f))
        onUpdateStyle(newStyle)
    }

    /**
     * Updates foreground opacity.
     * Convenience method for slider changes.
     *
     * @param opacity New foreground opacity (0.5-1.0)
     */
    fun onChangeForegroundOpacity(opacity: Float) {
        val currentStyle = _state.value.style
        val newStyle = currentStyle.copy(foregroundOpacity = opacity.coerceIn(0.5f, 1.0f))
        onUpdateStyle(newStyle)
    }

    /**
     * Updates background opacity.
     * Convenience method for slider changes.
     *
     * @param opacity New background opacity (0.0-1.0)
     */
    fun onChangeBackgroundOpacity(opacity: Float) {
        val currentStyle = _state.value.style
        val newStyle = currentStyle.copy(backgroundOpacity = opacity.coerceIn(0.0f, 1.0f))
        onUpdateStyle(newStyle)
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = SubtitleSettingsViewModel(app, SettingsStore(app)) as T
            }
    }
}
