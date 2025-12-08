package com.fishit.player.infra.logging.feature.di

import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.infra.logging.feature.UnifiedLoggingFeatureProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Hilt module for logging infrastructure feature providers.
 *
 * Binds the unified logging [FeatureProvider] into the multibinding set
 * that powers [AppFeatureRegistry].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LoggingFeatureModule {

    @Binds
    @IntoSet
    abstract fun bindUnifiedLoggingFeature(
        impl: UnifiedLoggingFeatureProvider
    ): FeatureProvider
}
