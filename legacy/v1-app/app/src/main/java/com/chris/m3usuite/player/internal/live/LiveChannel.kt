package com.chris.m3usuite.player.internal.live

/**
 * Domain model representing a live TV channel.
 *
 * This model is intentionally decoupled from:
 * - ObjectBox entities (ObxLive)
 * - Xtream API models
 * - UI/Compose types
 *
 * It exists to provide a clean, testable abstraction for:
 * - Channel navigation in the player
 * - EPG overlay display
 * - Diagnostics and logging
 *
 * @property id Unique channel identifier (typically mapped from ObxLive.streamId).
 * @property name Display name of the channel.
 * @property url Playback URL for the channel stream.
 * @property category Optional category/group the channel belongs to.
 * @property logoUrl Optional URL for the channel logo image.
 */
data class LiveChannel(
    val id: Long,
    val name: String,
    val url: String,
    val category: String?,
    val logoUrl: String?,
)
