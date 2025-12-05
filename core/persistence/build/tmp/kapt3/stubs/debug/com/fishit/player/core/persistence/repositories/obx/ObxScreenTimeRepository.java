package com.fishit.player.core.persistence.repositories.obx;

/**
 * ObjectBox implementation of ScreenTimeRepository.
 * Ported from v1 screen time tracking logic.
 */
@javax.inject.Singleton()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000F\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0006\b\u0007\u0018\u00002\u00020\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0018\u0010\u000b\u001a\u0004\u0018\u00010\f2\u0006\u0010\r\u001a\u00020\u000eH\u0096@\u00a2\u0006\u0002\u0010\u000fJ\u0012\u0010\u0010\u001a\u0004\u0018\u00010\t2\u0006\u0010\r\u001a\u00020\u000eH\u0002J\b\u0010\u0011\u001a\u00020\u0012H\u0002J\u0016\u0010\u0013\u001a\u00020\u00142\u0006\u0010\r\u001a\u00020\u000eH\u0096@\u00a2\u0006\u0002\u0010\u000fJ\u001e\u0010\u0015\u001a\u00020\u00162\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u0017\u001a\u00020\u0014H\u0096@\u00a2\u0006\u0002\u0010\u0018J\u001e\u0010\u0019\u001a\u00020\u00162\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u001a\u001a\u00020\u0014H\u0096@\u00a2\u0006\u0002\u0010\u0018J\f\u0010\u001b\u001a\u00020\f*\u00020\tH\u0002R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R2\u0010\u0007\u001a&\u0012\f\u0012\n \n*\u0004\u0018\u00010\t0\t \n*\u0012\u0012\f\u0012\n \n*\u0004\u0018\u00010\t0\t\u0018\u00010\b0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001c"}, d2 = {"Lcom/fishit/player/core/persistence/repositories/obx/ObxScreenTimeRepository;", "Lcom/fishit/player/core/persistence/repositories/ScreenTimeRepository;", "boxStore", "Lio/objectbox/BoxStore;", "(Lio/objectbox/BoxStore;)V", "dateFormat", "Ljava/text/SimpleDateFormat;", "screenTimeBox", "Lio/objectbox/Box;", "Lcom/fishit/player/core/persistence/obx/ObxScreenTimeEntry;", "kotlin.jvm.PlatformType", "getTodayEntry", "Lcom/fishit/player/core/persistence/repositories/ScreenTimeEntry;", "profileId", "", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getTodayEntryInternal", "getTodayString", "", "remainingMinutes", "", "setDailyLimit", "", "limitMinutes", "(JILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "tickUsageIfPlaying", "deltaSecs", "toDomainModel", "persistence_debug"})
public final class ObxScreenTimeRepository implements com.fishit.player.core.persistence.repositories.ScreenTimeRepository {
    @org.jetbrains.annotations.NotNull()
    private final io.objectbox.BoxStore boxStore = null;
    private final io.objectbox.Box<com.fishit.player.core.persistence.obx.ObxScreenTimeEntry> screenTimeBox = null;
    @org.jetbrains.annotations.NotNull()
    private final java.text.SimpleDateFormat dateFormat = null;
    
    @javax.inject.Inject()
    public ObxScreenTimeRepository(@org.jetbrains.annotations.NotNull()
    io.objectbox.BoxStore boxStore) {
        super();
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object remainingMinutes(long profileId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Integer> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object tickUsageIfPlaying(long profileId, int deltaSecs, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getTodayEntry(long profileId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.ScreenTimeEntry> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object setDailyLimit(long profileId, int limitMinutes, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final com.fishit.player.core.persistence.obx.ObxScreenTimeEntry getTodayEntryInternal(long profileId) {
        return null;
    }
    
    private final java.lang.String getTodayString() {
        return null;
    }
    
    private final com.fishit.player.core.persistence.repositories.ScreenTimeEntry toDomainModel(com.fishit.player.core.persistence.obx.ObxScreenTimeEntry $this$toDomainModel) {
        return null;
    }
}