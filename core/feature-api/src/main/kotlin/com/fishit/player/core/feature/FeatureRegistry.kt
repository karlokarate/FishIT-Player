package com.fishit.player.core.feature

/**
 * Central registry for discovering and querying features.
 *
 * The registry collects all [FeatureProvider] instances via DI multibindings
 * and provides methods to query feature availability and ownership.
 *
 * Usage:
 * ```kotlin
 * @Inject lateinit var featureRegistry: FeatureRegistry
 *
 * fun checkFeature() {
 *     if (featureRegistry.isSupported(TelegramFeatures.FULL_HISTORY_STREAMING)) {
 *         // Feature is available
 *     }
 * }
 * ```
 */
interface FeatureRegistry {
    /**
     * Returns true if the given feature is supported (has at least one provider).
     */
    fun isSupported(featureId: FeatureId): Boolean

    /**
     * Returns all providers for the given feature, or empty list if none.
     */
    fun providersFor(featureId: FeatureId): List<FeatureProvider>

    /**
     * Returns the owner of the given feature, or null if not found.
     *
     * If multiple providers exist for a feature, returns the first one's owner.
     */
    fun ownerOf(featureId: FeatureId): FeatureOwner?
}
