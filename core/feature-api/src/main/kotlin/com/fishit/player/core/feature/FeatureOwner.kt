package com.fishit.player.core.feature

/**
 * Declares the module and optional team responsible for a feature.
 *
 * @property moduleName The Gradle module name, e.g., "pipeline:telegram", "infra:cache"
 * @property team Optional team name for larger organizations
 */
data class FeatureOwner(
    val moduleName: String,
    val team: String? = null,
)
