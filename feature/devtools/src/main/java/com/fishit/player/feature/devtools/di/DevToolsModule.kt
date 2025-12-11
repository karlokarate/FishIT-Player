package com.fishit.player.feature.devtools.di

import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.feature.devtools.DevToolsFeatureProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Hilt module for DevTools feature.
 *
 * Registers DevToolsFeatureProvider into the global FeatureRegistry.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DevToolsModule {
    @Binds
    @IntoSet
    abstract fun bindDevToolsFeatureProvider(impl: DevToolsFeatureProvider): FeatureProvider
}
