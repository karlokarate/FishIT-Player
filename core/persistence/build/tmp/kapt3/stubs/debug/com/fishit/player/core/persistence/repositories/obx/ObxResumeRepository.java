package com.fishit.player.core.persistence.repositories.obx;

/**
 * ObjectBox implementation of ResumeRepository.
 * Ported from v1 resume handling logic.
 */
@javax.inject.Singleton()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000>\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0007\b\u0007\u0018\u00002\u00020\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0016\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\fH\u0096@\u00a2\u0006\u0002\u0010\rJ\u001c\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00100\u000f2\u0006\u0010\u0011\u001a\u00020\u0012H\u0096@\u00a2\u0006\u0002\u0010\u0013J\u0018\u0010\u0014\u001a\u0004\u0018\u00010\u00102\u0006\u0010\u000b\u001a\u00020\fH\u0096@\u00a2\u0006\u0002\u0010\rJ&\u0010\u0015\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\u0016\u001a\u00020\u00122\u0006\u0010\u0017\u001a\u00020\u0012H\u0096@\u00a2\u0006\u0002\u0010\u0018R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R2\u0010\u0005\u001a&\u0012\f\u0012\n \b*\u0004\u0018\u00010\u00070\u0007 \b*\u0012\u0012\f\u0012\n \b*\u0004\u0018\u00010\u00070\u0007\u0018\u00010\u00060\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0019"}, d2 = {"Lcom/fishit/player/core/persistence/repositories/obx/ObxResumeRepository;", "Lcom/fishit/player/core/persistence/repositories/ResumeRepository;", "boxStore", "Lio/objectbox/BoxStore;", "(Lio/objectbox/BoxStore;)V", "resumeBox", "Lio/objectbox/Box;", "Lcom/fishit/player/core/persistence/obx/ObxResumeMark;", "kotlin.jvm.PlatformType", "clearResumePoint", "", "contentId", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getAllResumePoints", "", "Lcom/fishit/player/core/model/ResumePoint;", "profileId", "", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getResumePoint", "saveResumePoint", "positionMs", "durationMs", "(Ljava/lang/String;JJLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "persistence_debug"})
public final class ObxResumeRepository implements com.fishit.player.core.persistence.repositories.ResumeRepository {
    @org.jetbrains.annotations.NotNull()
    private final io.objectbox.BoxStore boxStore = null;
    private final io.objectbox.Box<com.fishit.player.core.persistence.obx.ObxResumeMark> resumeBox = null;
    
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
    java.lang.String contentId, long positionMs, long durationMs, @org.jetbrains.annotations.NotNull()
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
}