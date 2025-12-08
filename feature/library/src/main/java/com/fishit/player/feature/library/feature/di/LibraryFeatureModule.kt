package com.fishit.player.feature.library.feature.di

import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.feature.library.feature.LibraryScreenFeatureProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Hilt module for Library screen feature providers.
 *
 * Binds the Library screen [FeatureProvider] into the multibinding set
 * that powers [AppFeatureRegistry].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryFeatureModule {

    @Binds
    @IntoSet
    abstract fun bindLibraryScreenFeature(
        impl: LibraryScreenFeatureProvider
    ): FeatureProvider
}
