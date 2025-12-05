package com.fishit.player.core.persistence.di;

/**
 * Hilt module for binding repository implementations.
 */
@dagger.Module()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00006\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b\'\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\'J\u0010\u0010\u0007\u001a\u00020\b2\u0006\u0010\u0005\u001a\u00020\tH\'J\u0010\u0010\n\u001a\u00020\u000b2\u0006\u0010\u0005\u001a\u00020\fH\'J\u0010\u0010\r\u001a\u00020\u000e2\u0006\u0010\u0005\u001a\u00020\u000fH\'\u00a8\u0006\u0010"}, d2 = {"Lcom/fishit/player/core/persistence/di/PersistenceModule;", "", "()V", "bindContentRepository", "Lcom/fishit/player/core/persistence/repositories/ContentRepository;", "impl", "Lcom/fishit/player/core/persistence/repositories/obx/ObxContentRepository;", "bindProfileRepository", "Lcom/fishit/player/core/persistence/repositories/ProfileRepository;", "Lcom/fishit/player/core/persistence/repositories/obx/ObxProfileRepository;", "bindResumeRepository", "Lcom/fishit/player/core/persistence/repositories/ResumeRepository;", "Lcom/fishit/player/core/persistence/repositories/obx/ObxResumeRepository;", "bindScreenTimeRepository", "Lcom/fishit/player/core/persistence/repositories/ScreenTimeRepository;", "Lcom/fishit/player/core/persistence/repositories/obx/ObxScreenTimeRepository;", "persistence_debug"})
@dagger.hilt.InstallIn(value = {dagger.hilt.components.SingletonComponent.class})
public abstract class PersistenceModule {
    
    public PersistenceModule() {
        super();
    }
    
    @dagger.Binds()
    @javax.inject.Singleton()
    @org.jetbrains.annotations.NotNull()
    public abstract com.fishit.player.core.persistence.repositories.ProfileRepository bindProfileRepository(@org.jetbrains.annotations.NotNull()
    com.fishit.player.core.persistence.repositories.obx.ObxProfileRepository impl);
    
    @dagger.Binds()
    @javax.inject.Singleton()
    @org.jetbrains.annotations.NotNull()
    public abstract com.fishit.player.core.persistence.repositories.ResumeRepository bindResumeRepository(@org.jetbrains.annotations.NotNull()
    com.fishit.player.core.persistence.repositories.obx.ObxResumeRepository impl);
    
    @dagger.Binds()
    @javax.inject.Singleton()
    @org.jetbrains.annotations.NotNull()
    public abstract com.fishit.player.core.persistence.repositories.ContentRepository bindContentRepository(@org.jetbrains.annotations.NotNull()
    com.fishit.player.core.persistence.repositories.obx.ObxContentRepository impl);
    
    @dagger.Binds()
    @javax.inject.Singleton()
    @org.jetbrains.annotations.NotNull()
    public abstract com.fishit.player.core.persistence.repositories.ScreenTimeRepository bindScreenTimeRepository(@org.jetbrains.annotations.NotNull()
    com.fishit.player.core.persistence.repositories.obx.ObxScreenTimeRepository impl);
}