package com.chris.m3usuite.player.internal.live

/**
 * Domain model representing the state of the EPG (Electronic Program Guide) overlay.
 *
 * This model is intentionally decoupled from:
 * - UI/Compose types
 * - EPG repository implementation details
 * - Timer/handler implementations
 *
 * It exists to provide a clean, testable abstraction for:
 * - Overlay visibility management
 * - Now/Next program title display
 * - Auto-hide timing
 *
 * @property visible Whether the EPG overlay is currently visible.
 * @property nowTitle Title of the currently playing program, if available.
 * @property nextTitle Title of the next program, if available.
 * @property hideAtRealtimeMs Realtime timestamp (from System.currentTimeMillis) when
 *           the overlay should auto-hide. Null if no auto-hide is scheduled.
 */
data class EpgOverlayState(
    val visible: Boolean,
    val nowTitle: String?,
    val nextTitle: String?,
    val hideAtRealtimeMs: Long?,
)
