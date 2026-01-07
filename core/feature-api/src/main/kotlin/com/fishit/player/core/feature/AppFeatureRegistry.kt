package com.fishit.player.core.feature

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central registry for all features in the v2 app.
 *
 * This registry is populated via Hilt multibindings - each module contributes
 * its [FeatureProvider] implementations to the [providers] set.
 *
 * Usage:
 * ```kotlin
 * @Inject lateinit var featureRegistry: FeatureRegistry
 *
 * if (featureRegistry.isSupported(TelegramFeatures.FULL_HISTORY_STREAMING)) {
 *     // Feature is available
 * }
 * ```
 *
 * Note: This class is managed entirely through DI - no global mutable singleton.
 */
@Singleton
class AppFeatureRegistry
    @Inject
    constructor(
        providers: Set<@JvmSuppressWildcards FeatureProvider>,
    ) : FeatureRegistry {
        private val providersById: Map<FeatureId, List<FeatureProvider>> =
            providers.groupBy { it.featureId }

        private val ownersById: Map<FeatureId, FeatureOwner> =
            providers.associate { it.featureId to it.owner }

        /**
         * Returns the total number of registered features.
         */
        val featureCount: Int get() = providersById.size

        /**
         * Returns all registered feature IDs.
         */
        val allFeatureIds: Set<FeatureId> get() = providersById.keys

        override fun isSupported(featureId: FeatureId): Boolean = providersById.containsKey(featureId)

        override fun providersFor(featureId: FeatureId): List<FeatureProvider> = providersById[featureId].orEmpty()

        override fun ownerOf(featureId: FeatureId): FeatureOwner? = ownersById[featureId]
    }
