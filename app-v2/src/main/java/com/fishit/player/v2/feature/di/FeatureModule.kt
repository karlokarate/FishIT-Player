package com.fishit.player.v2.feature.di

import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.core.feature.FeatureRegistry
import com.fishit.player.v2.feature.AppFeatureRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

/**
 * Hilt module for the v2 Feature System.
 *
 * This module:
 * 1. Declares the multibinding set for [FeatureProvider] instances
 * 2. Provides the [FeatureRegistry] implementation as an APP-scoped singleton
 *
 * Each feature module should contribute to the [FeatureProvider] set via:
 * ```kotlin
 * @Module
 * @InstallIn(SingletonComponent::class)
 * abstract class MyFeatureModule {
 *     @Binds
 *     @IntoSet
 *     abstract fun bindMyFeature(impl: MyFeatureProvider): FeatureProvider
 * }
 * ```
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FeatureModule {

    /**
     * Declares the multibinding set for FeatureProviders.
     * This allows the set to be empty if no providers are bound yet.
     */
    @Multibinds
    abstract fun featureProviders(): Set<FeatureProvider>

    companion object {
        /**
         * Provides the FeatureRegistry as an APP-scoped singleton.
         *
         * The registry is constructed from all bound FeatureProvider instances
         * collected via Hilt multibindings.
         */
        @Provides
        @Singleton
        fun provideFeatureRegistry(
            providers: Set<@JvmSuppressWildcards FeatureProvider>,
        ): FeatureRegistry = AppFeatureRegistry(providers)
    }
}
