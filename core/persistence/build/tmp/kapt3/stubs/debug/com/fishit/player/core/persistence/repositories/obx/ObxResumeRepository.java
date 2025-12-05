package com.fishit.player.core.persistence.repositories.obx;

/**
 * ObjectBox-backed implementation of ResumeRepository.
 * Ported from v1: app/src/main/java/com/chris/m3usuite/data/repo/ResumeRepository.kt
 *
 * Resume Rules (from v1 contract):
 * - Only save if positionMs > 10,000 (>10 seconds)
 * - Clear if remaining < 10,000 (<10 seconds to end)
 * - LIVE content never gets resume marks
 */
@javax.inject.Singleton()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000F\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\b\u0007\u0018\u0000 \u001b2\u00020\u0001:\u0001\u001bB\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0016\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\u000bH\u0096@\u00a2\u0006\u0002\u0010\fJ\u0016\u0010\r\u001a\u00020\t2\u0006\u0010\u000e\u001a\u00020\u000fH\u0096@\u00a2\u0006\u0002\u0010\u0010J\u001c\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u00130\u00122\u0006\u0010\n\u001a\u00020\u000bH\u0096@\u00a2\u0006\u0002\u0010\fJ\u0018\u0010\u0014\u001a\u0004\u0018\u00010\u00132\u0006\u0010\u000e\u001a\u00020\u000fH\u0096@\u00a2\u0006\u0002\u0010\u0010J\u001c\u0010\u0015\u001a\u000e\u0012\u0004\u0012\u00020\u000f\u0012\u0004\u0012\u00020\u000f0\u00162\u0006\u0010\u000e\u001a\u00020\u000fH\u0002J0\u0010\u0017\u001a\u00020\t2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0018\u001a\u00020\u000b2\u0006\u0010\u0019\u001a\u00020\u000b2\b\u0010\n\u001a\u0004\u0018\u00010\u000bH\u0096@\u00a2\u0006\u0002\u0010\u001aR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001c"}, d2 = {"Lcom/fishit/player/core/persistence/repositories/obx/ObxResumeRepository;", "Lcom/fishit/player/core/persistence/repositories/ResumeRepository;", "boxStore", "Lio/objectbox/BoxStore;", "(Lio/objectbox/BoxStore;)V", "resumeBox", "Lio/objectbox/Box;", "Lcom/fishit/player/core/persistence/obx/ObxResumeMark;", "clearAllResumePoints", "", "profileId", "", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "clearResumePoint", "contentId", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getAllResumePoints", "", "Lcom/fishit/player/core/model/ResumePoint;", "getResumePoint", "parseContentId", "Lkotlin/Pair;", "saveResumePoint", "positionMs", "durationMs", "(Ljava/lang/String;JJLjava/lang/Long;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "Companion", "persistence_debug"})
public final class ObxResumeRepository implements com.fishit.player.core.persistence.repositories.ResumeRepository {
    @org.jetbrains.annotations.NotNull()
    private final io.objectbox.BoxStore boxStore = null;
    @org.jetbrains.annotations.NotNull()
    private final io.objectbox.Box<com.fishit.player.core.persistence.obx.ObxResumeMark> resumeBox = null;
    private static final long MIN_POSITION_MS = 10000L;
    private static final long MIN_REMAINING_MS = 10000L;
    @org.jetbrains.annotations.NotNull()
    public static final com.fishit.player.core.persistence.repositories.obx.ObxResumeRepository.Companion Companion = null;
    
    @javax.inject.Inject()
    public ObxResumeRepository(@org.jetbrains.annotations.NotNull()
    io.objectbox.BoxStore boxStore) {
        super();
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getResumePoint(@org.jetbrains.annotations.NotNull()
    java.lang.String contentId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.model.ResumePoint> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object saveResumePoint(@org.jetbrains.annotations.NotNull()
    java.lang.String contentId, long positionMs, long durationMs, @org.jetbrains.annotations.Nullable()
    java.lang.Long profileId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object clearResumePoint(@org.jetbrains.annotations.NotNull()
    java.lang.String contentId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getAllResumePoints(long profileId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.fishit.player.core.model.ResumePoint>> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object clearAllResumePoints(long profileId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    /**
     * Parse contentId into (type, id) pair.
     * Format: "vod:123" or "series:456:1:3"
     */
    private final kotlin.Pair<java.lang.String, java.lang.String> parseContentId(java.lang.String contentId) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0006"}, d2 = {"Lcom/fishit/player/core/persistence/repositories/obx/ObxResumeRepository$Companion;", "", "()V", "MIN_POSITION_MS", "", "MIN_REMAINING_MS", "persistence_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}