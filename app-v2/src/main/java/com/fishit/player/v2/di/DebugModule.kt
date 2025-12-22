package com.fishit.player.v2.di

import com.fishit.player.feature.settings.DebugInfoProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for debug/diagnostics bindings.
 *
 * Provides:
 * - [DebugInfoProvider] implementation for DebugViewModel
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DebugModule {

    @Binds
    @Singleton
    abstract fun bindDebugInfoProvider(
        impl: DefaultDebugInfoProvider
    ): DebugInfoProvider
}
