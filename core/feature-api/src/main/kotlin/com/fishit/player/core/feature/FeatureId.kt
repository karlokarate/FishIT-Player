package com.fishit.player.core.feature

/**
 * A unique identifier for a feature in the v2 feature system.
 *
 * Naming convention: `<domain>.<subdomain>.<capability>`
 *
 * Examples:
 * - `media.canonical_model`
 * - `telegram.full_history_streaming`
 * - `ui.screen.home`
 */
@JvmInline
value class FeatureId(
    val value: String,
) {
    override fun toString(): String = value
}
