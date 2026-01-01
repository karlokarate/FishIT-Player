package com.fishit.player.playback.domain.defaults

import com.fishit.player.playback.domain.SubtitleStyle
import com.fishit.player.playback.domain.SubtitleStyleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Default SubtitleStyleManager with in-memory storage.
 *
 * This is a stub implementation for Phase 1.
 * Real persistence will be added in Phase 6.
 */
class DefaultSubtitleStyleManager : SubtitleStyleManager {
    private val _style = MutableStateFlow(SubtitleStyle())

    override val style: StateFlow<SubtitleStyle> = _style.asStateFlow()

    override suspend fun updateStyle(style: SubtitleStyle) {
        _style.value = style
    }

    override suspend fun resetToDefault() {
        _style.value = SubtitleStyle()
    }
}
