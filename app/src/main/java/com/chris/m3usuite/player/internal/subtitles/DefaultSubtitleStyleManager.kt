package com.chris.m3usuite.player.internal.subtitles

import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Default implementation of [SubtitleStyleManager] using DataStore for persistence.
 *
 * **Persistence Strategy:**
 * - Uses existing SettingsStore keys: SUB_SCALE, SUB_FG, SUB_BG, SUB_FG_OPACITY_PCT, SUB_BG_OPACITY_PCT
 * - Per-profile persistence handled by SettingsStore's currentProfileId
 * - Edge style is not persisted separately (always defaults to OUTLINE per contract)
 *
 * **StateFlow Behavior:**
 * - Collects from SettingsStore flows and combines into SubtitleStyle
 * - Updates emit immediately when DataStore is written
 * - Thread-safe concurrent access via coroutines
 *
 * @param settingsStore DataStore-backed settings repository
 * @param scope Coroutine scope for flow collection (typically tied to session lifecycle)
 */
class DefaultSubtitleStyleManager(
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
) : SubtitleStyleManager {
    private val _currentStyle = MutableStateFlow(SubtitleStyle())
    override val currentStyle: StateFlow<SubtitleStyle> = _currentStyle.asStateFlow()

    private val _currentPreset = MutableStateFlow(SubtitlePreset.DEFAULT)
    override val currentPreset: StateFlow<SubtitlePreset> = _currentPreset.asStateFlow()

    init {
        // Collect from SettingsStore flows and combine into SubtitleStyle
        scope.launch {
            combine(
                settingsStore.subtitleScale,
                settingsStore.subtitleFg,
                settingsStore.subtitleBg,
                settingsStore.subtitleFgOpacityPct,
                settingsStore.subtitleBgOpacityPct,
            ) { scale, fg, bg, fgOpacityPct, bgOpacityPct ->
                // Convert legacy scale (0.04-0.12 fractional) to new scale (0.5-2.0)
                // Legacy uses fractional text size directly on subtitleView
                // New model uses normalized scale factor
                // Heuristic: 0.06 (legacy default) → 1.0 (new default)
                // Scale range mapping: 0.04-0.12 → 0.5-2.0
                val normalizedScale =
                    ((scale - 0.04f) / (0.12f - 0.04f) * (2.0f - 0.5f) + 0.5f)
                        .coerceIn(0.5f, 2.0f)

                SubtitleStyle(
                    textScale = normalizedScale,
                    foregroundColor = fg,
                    backgroundColor = bg,
                    foregroundOpacity = (fgOpacityPct / 100f).coerceIn(0.5f, 1.0f),
                    backgroundOpacity = (bgOpacityPct / 100f).coerceIn(0.0f, 1.0f),
                    edgeStyle = EdgeStyle.OUTLINE, // Always OUTLINE per contract
                )
            }.collect { style ->
                _currentStyle.value = style
            }
        }
    }

    override suspend fun updateStyle(style: SubtitleStyle) {
        // Validate style before persisting
        require(style.isValid()) {
            "Invalid SubtitleStyle: $style"
        }

        // Convert new scale (0.5-2.0) back to legacy fractional scale (0.04-0.12)
        val legacyScale =
            ((style.textScale - 0.5f) / (2.0f - 0.5f) * (0.12f - 0.04f) + 0.04f)
                .coerceIn(0.04f, 0.12f)

        // Persist to DataStore
        settingsStore.setSubtitleStyle(
            scale = legacyScale,
            fg = style.foregroundColor,
            bg = style.backgroundColor,
        )
        settingsStore.setSubtitleFgOpacityPct((style.foregroundOpacity * 100).toInt())
        settingsStore.setSubtitleBgOpacityPct((style.backgroundOpacity * 100).toInt())

        // Note: EdgeStyle is not persisted separately
        // It is always OUTLINE per contract default
    }

    override suspend fun applyPreset(preset: SubtitlePreset) {
        _currentPreset.value = preset
        updateStyle(preset.toStyle())
    }

    override suspend fun resetToDefault() {
        applyPreset(SubtitlePreset.DEFAULT)
    }
}
