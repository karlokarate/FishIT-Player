package com.fishit.player.core.persistence.repositories.obx;

/**
 * ObjectBox implementation of ProfileRepository.
 * Ported from v1 profile handling logic.
 */
@javax.inject.Singleton()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0002\b\u0007\u0018\u00002\u00020\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0014\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\nH\u0096@\u00a2\u0006\u0002\u0010\fJ\u0010\u0010\r\u001a\u0004\u0018\u00010\u000bH\u0096@\u00a2\u0006\u0002\u0010\fJ\u0018\u0010\u000e\u001a\u0004\u0018\u00010\u000b2\u0006\u0010\u000f\u001a\u00020\u0010H\u0096@\u00a2\u0006\u0002\u0010\u0011J\u0016\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u000f\u001a\u00020\u0010H\u0096@\u00a2\u0006\u0002\u0010\u0011J\f\u0010\u0014\u001a\u00020\u000b*\u00020\u0007H\u0002R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R2\u0010\u0005\u001a&\u0012\f\u0012\n \b*\u0004\u0018\u00010\u00070\u0007 \b*\u0012\u0012\f\u0012\n \b*\u0004\u0018\u00010\u00070\u0007\u0018\u00010\u00060\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0015"}, d2 = {"Lcom/fishit/player/core/persistence/repositories/obx/ObxProfileRepository;", "Lcom/fishit/player/core/persistence/repositories/ProfileRepository;", "boxStore", "Lio/objectbox/BoxStore;", "(Lio/objectbox/BoxStore;)V", "profileBox", "Lio/objectbox/Box;", "Lcom/fishit/player/core/persistence/obx/ObxProfile;", "kotlin.jvm.PlatformType", "getAllProfiles", "", "Lcom/fishit/player/core/persistence/repositories/Profile;", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getCurrentProfile", "getProfile", "id", "", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "isKidsProfile", "", "toDomainModel", "persistence_debug"})
public final class ObxProfileRepository implements com.fishit.player.core.persistence.repositories.ProfileRepository {
    @org.jetbrains.annotations.NotNull()
    private final io.objectbox.BoxStore boxStore = null;
    private final io.objectbox.Box<com.fishit.player.core.persistence.obx.ObxProfile> profileBox = null;
    
    @javax.inject.Inject()
    public ObxProfileRepository(@org.jetbrains.annotations.NotNull()
    io.objectbox.BoxStore boxStore) {
        super();
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getCurrentProfile(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.Profile> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getProfile(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.Profile> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getAllProfiles(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.fishit.player.core.persistence.repositories.Profile>> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object isKidsProfile(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Boolean> $completion) {
        return null;
    }
    
    private final com.fishit.player.core.persistence.repositories.Profile toDomainModel(com.fishit.player.core.persistence.obx.ObxProfile $this$toDomainModel) {
        return null;
    }
}