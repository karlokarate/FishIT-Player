package com.fishit.player.core.persistence.di

import android.content.Context
import com.fishit.player.core.persistence.obx.ObxStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.objectbox.BoxStore
import javax.inject.Singleton

/**
 * Hilt module for providing ObjectBox BoxStore.
 */
@Module
@InstallIn(SingletonComponent::class)
object ObxStoreModule {
    
    @Provides
    @Singleton
    fun provideBoxStore(@ApplicationContext context: Context): BoxStore {
        return ObxStore.get(context)
    }
}
