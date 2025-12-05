package com.fishit.player.core.persistence.di

import com.fishit.player.core.model.repository.ContentRepository
import com.fishit.player.core.model.repository.ProfileRepository
import com.fishit.player.core.model.repository.ResumeRepository
import com.fishit.player.core.model.repository.ScreenTimeRepository
import com.fishit.player.core.persistence.repository.ObxContentRepository
import com.fishit.player.core.persistence.repository.ObxProfileRepository
import com.fishit.player.core.persistence.repository.ObxResumeRepository
import com.fishit.player.core.persistence.repository.ObxScreenTimeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for binding repository implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PersistenceModule {
    
    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ObxProfileRepository): ProfileRepository
    
    @Binds
    @Singleton
    abstract fun bindResumeRepository(impl: ObxResumeRepository): ResumeRepository
    
    @Binds
    @Singleton
    abstract fun bindContentRepository(impl: ObxContentRepository): ContentRepository
    
    @Binds
    @Singleton
    abstract fun bindScreenTimeRepository(impl: ObxScreenTimeRepository): ScreenTimeRepository
}
