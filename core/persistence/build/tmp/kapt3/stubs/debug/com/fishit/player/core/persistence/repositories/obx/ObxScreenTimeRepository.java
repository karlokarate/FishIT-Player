package com.fishit.player.core.persistence.repositories.obx;

/**
 * ObjectBox-backed implementation of ScreenTimeRepository.
 * Implements kids profile screen time tracking with daily limits.
 */
@javax.inject.Singleton()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00008\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0006\b\u0007\u0018\u0000 \u00152\u00020\u0001:\u0001\u0015B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0018\u0010\b\u001a\u0004\u0018\u00010\t2\u0006\u0010\n\u001a\u00020\u000bH\u0096@\u00a2\u0006\u0002\u0010\fJ\u0016\u0010\r\u001a\u00020\u000e2\u0006\u0010\n\u001a\u00020\u000bH\u0096@\u00a2\u0006\u0002\u0010\fJ\u001e\u0010\u000f\u001a\u00020\u00102\u0006\u0010\n\u001a\u00020\u000b2\u0006\u0010\u0011\u001a\u00020\u000eH\u0096@\u00a2\u0006\u0002\u0010\u0012J\u001e\u0010\u0013\u001a\u00020\u00102\u0006\u0010\n\u001a\u00020\u000b2\u0006\u0010\u0014\u001a\u00020\u000eH\u0096@\u00a2\u0006\u0002\u0010\u0012R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0016"}, d2 = {"Lcom/fishit/player/core/persistence/repositories/obx/ObxScreenTimeRepository;", "Lcom/fishit/player/core/persistence/repositories/ScreenTimeRepository;", "boxStore", "Lio/objectbox/BoxStore;", "(Lio/objectbox/BoxStore;)V", "screenTimeBox", "Lio/objectbox/Box;", "Lcom/fishit/player/core/persistence/obx/ObxScreenTimeEntry;", "getTodayEntry", "Lcom/fishit/player/core/persistence/repositories/ScreenTimeEntry;", "kidProfileId", "", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "remainingMinutes", "", "setDailyLimit", "", "limitMinutes", "(JILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "tickUsageIfPlaying", "deltaSecs", "Companion", "persistence_debug"})
public final class ObxScreenTimeRepository implements com.fishit.player.core.persistence.repositories.ScreenTimeRepository {
    @org.jetbrains.annotations.NotNull()
    private final io.objectbox.BoxStore boxStore = null;
    @org.jetbrains.annotations.NotNull()
    private final io.objectbox.Box<com.fishit.player.core.persistence.obx.ObxScreenTimeEntry> screenTimeBox = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.text.SimpleDateFormat dateFormat = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.fishit.player.core.persistence.repositories.obx.ObxScreenTimeRepository.Companion Companion = null;
    
    @javax.inject.Inject()
    public ObxScreenTimeRepository(@org.jetbrains.annotations.NotNull()
    io.objectbox.BoxStore boxStore) {
        super();
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object remainingMinutes(long kidProfileId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Integer> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object tickUsageIfPlaying(long kidProfileId, int deltaSecs, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getTodayEntry(long kidProfileId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.ScreenTimeEntry> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object setDailyLimit(long kidProfileId, int limitMinutes, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2 = {"Lcom/fishit/player/core/persistence/repositories/obx/ObxScreenTimeRepository$Companion;", "", "()V", "dateFormat", "Ljava/text/SimpleDateFormat;", "persistence_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}