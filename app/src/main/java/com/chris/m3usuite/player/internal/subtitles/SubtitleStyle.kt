package com.chris.m3usuite.player.internal.subtitles

/**
 * Domain model for subtitle visual styling in the SIP Internal Player.
 *
 * This model follows the contract defined in `INTERNAL_PLAYER_SUBTITLE_CC_CONTRACT_PHASE4.md` Section 4.
 *
 * **Default Values (Contract Section 4.2):**
 * - textScale = 1.0
 * - foregroundColor = White (100% opacity)
 * - backgroundColor = Black (~60% opacity)
 * - foregroundOpacity = 1.0
 * - backgroundOpacity = 0.6
 * - edgeStyle = Outline
 *
 * **Allowed Ranges (Contract Section 4.3):**
 * - textScale: 0.5–2.0
 * - foregroundOpacity: 0.5–1.0
 * - backgroundOpacity: 0.0–1.0
 *
 * @property textScale Text scaling factor (0.5f..2.0f). Default: 1.0
 * @property foregroundColor Foreground color in ARGB format. Default: White
 * @property backgroundColor Background color in ARGB format. Default: Black with ~60% opacity
 * @property foregroundOpacity Foreground opacity (0f..1f). Default: 1.0
 * @property backgroundOpacity Background opacity (0f..1f). Default: 0.6
 * @property edgeStyle Edge rendering style. Default: OUTLINE
 */
data class SubtitleStyle(
    val textScale: Float = 1.0f,
    val foregroundColor: Int = 0xFFFFFFFF.toInt(), // White
    val backgroundColor: Int = 0x99000000.toInt(), // Black ~60% opacity
    val foregroundOpacity: Float = 1.0f,
    val backgroundOpacity: Float = 0.6f,
    val edgeStyle: EdgeStyle = EdgeStyle.OUTLINE,
) {
    init {
        require(textScale in 0.5f..2.0f) {
            "textScale must be in range 0.5–2.0, was: $textScale"
        }
        require(foregroundOpacity in 0.5f..1.0f) {
            "foregroundOpacity must be in range 0.5–1.0, was: $foregroundOpacity"
        }
        require(backgroundOpacity in 0.0f..1.0f) {
            "backgroundOpacity must be in range 0.0–1.0, was: $backgroundOpacity"
        }
    }

    /**
     * Validates that the style conforms to contract requirements.
     * This is a safety check for external integrations.
     */
    fun isValid(): Boolean =
        textScale in 0.5f..2.0f &&
            foregroundOpacity in 0.5f..1.0f &&
            backgroundOpacity in 0.0f..1.0f
}

/**
 * Edge rendering styles for subtitle text.
 *
 * Corresponds to Media3 CaptionStyleCompat edge types.
 *
 * @see androidx.media3.ui.CaptionStyleCompat
 */
enum class EdgeStyle {
    /**
     * No edge rendering.
     */
    NONE,

    /**
     * Outline (stroke) around text.
     * Most common for readability.
     */
    OUTLINE,

    /**
     * Drop shadow behind text.
     */
    SHADOW,

    /**
     * Glow effect around text.
     * Not supported on all platforms.
     */
    GLOW,
}
