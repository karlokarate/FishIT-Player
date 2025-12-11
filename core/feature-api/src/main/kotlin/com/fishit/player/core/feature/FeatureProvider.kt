package com.fishit.player.core.feature

/**
 * Interface implemented by each feature to declare its identity.
 *
 * Each module exposes its feature providers as DI-injectable singletons.
 * Providers are collected via multibinding into a [FeatureRegistry].
 *
 * Naming Convention:
 * - App features in feature/ modules should use FeatureProvider naming
 * - Pipeline capabilities in pipeline/ modules should use CapabilityProvider naming
 *
 * Example implementation:
 * ```kotlin
 * class TelegramFullHistoryCapabilityProvider : FeatureProvider {
 *     override val featureId = TelegramFeatures.FULL_HISTORY_STREAMING
 *     override val scope = FeatureScope.PIPELINE
 *     override val owner = FeatureOwner(moduleName = "pipeline:telegram")
 * }
 * ```
 */
interface FeatureProvider {
    /**
     * Unique identifier for this feature.
     */
    val featureId: FeatureId

    /**
     * Lifecycle scope of this feature.
     */
    val scope: FeatureScope

    /**
     * Module and team that owns this feature.
     */
    val owner: FeatureOwner
}
