package com.chris.m3usuite.player.internal.subtitles

/**
 * Predefined subtitle style presets for quick selection.
 *
 * Contract Reference: `INTERNAL_PLAYER_SUBTITLE_CC_CONTRACT_PHASE4.md` Section 8.2
 *
 * **Presets:**
 * - DEFAULT: Standard white-on-black with outline (contract defaults)
 * - HIGH_CONTRAST: Yellow on black for maximum readability
 * - TV_LARGE: Larger text with strong outline for TV viewing
 * - MINIMAL: Smaller text with subtle background for unobtrusive viewing
 */
enum class SubtitlePreset {
    /**
     * Default subtitle style.
     * White text on semi-transparent black with outline.
     */
    DEFAULT,

    /**
     * High contrast style for accessibility.
     * Yellow text on solid black.
     */
    HIGH_CONTRAST,

    /**
     * Large text optimized for TV/couch viewing.
     * 1.5x scale with strong outline.
     */
    TV_LARGE,

    /**
     * Minimal style for users who prefer subtlety.
     * 0.8x scale with low-opacity background.
     */
    MINIMAL,
    ;

    /**
     * Converts this preset to its corresponding [SubtitleStyle].
     */
    fun toStyle(): SubtitleStyle = when (this) {
        DEFAULT -> SubtitleStyle()

        HIGH_CONTRAST -> SubtitleStyle(
            foregroundColor = 0xFFFFFF00.toInt(),  // Yellow
            backgroundColor = 0xFF000000.toInt(),  // Solid black
            foregroundOpacity = 1.0f,
            backgroundOpacity = 1.0f,
        )

        TV_LARGE -> SubtitleStyle(
            textScale = 1.5f,
            edgeStyle = EdgeStyle.OUTLINE,
        )

        MINIMAL -> SubtitleStyle(
            textScale = 0.8f,
            backgroundOpacity = 0.3f,
        )
    }
}
