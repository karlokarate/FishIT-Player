package com.fishit.player.core.persistence.repositories;

/**
 * Repository for managing playback resume positions.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0006\bf\u0018\u00002\u00020\u0001J\u0016\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u00a6@\u00a2\u0006\u0002\u0010\u0006J\u0016\u0010\u0007\u001a\u00020\u00032\u0006\u0010\b\u001a\u00020\tH\u00a6@\u00a2\u0006\u0002\u0010\nJ\u001c\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\r0\f2\u0006\u0010\u0004\u001a\u00020\u0005H\u00a6@\u00a2\u0006\u0002\u0010\u0006J\u0018\u0010\u000e\u001a\u0004\u0018\u00010\r2\u0006\u0010\b\u001a\u00020\tH\u00a6@\u00a2\u0006\u0002\u0010\nJ2\u0010\u000f\u001a\u00020\u00032\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\u0010\u001a\u00020\u00052\u0006\u0010\u0011\u001a\u00020\u00052\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u0005H\u00a6@\u00a2\u0006\u0002\u0010\u0012\u00a8\u0006\u0013"}, d2 = {"Lcom/fishit/player/core/persistence/repositories/ResumeRepository;", "", "clearAllResumePoints", "", "profileId", "", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "clearResumePoint", "contentId", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getAllResumePoints", "", "Lcom/fishit/player/core/model/ResumePoint;", "getResumePoint", "saveResumePoint", "positionMs", "durationMs", "(Ljava/lang/String;JJLjava/lang/Long;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "persistence_debug"})
public abstract interface ResumeRepository {
    
    /**
     * Get the resume point for a specific content ID.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getResumePoint(@org.jetbrains.annotations.NotNull()
    java.lang.String contentId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.model.ResumePoint> $completion);
    
    /**
     * Save or update a resume point.
     *
     * Rules (from v1 contract):
     * - Only save if positionMs > 10,000 (>10 seconds)
     * - Clear resume point if remaining < 10,000 (<10 seconds to end)
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object saveResumePoint(@org.jetbrains.annotations.NotNull()
    java.lang.String contentId, long positionMs, long durationMs, @org.jetbrains.annotations.Nullable()
    java.lang.Long profileId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    /**
     * Clear/delete a resume point for specific content.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object clearResumePoint(@org.jetbrains.annotations.NotNull()
    java.lang.String contentId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    /**
     * Get all resume points for a profile.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getAllResumePoints(long profileId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.fishit.player.core.model.ResumePoint>> $completion);
    
    /**
     * Clear all resume points for a profile.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object clearAllResumePoints(long profileId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    /**
     * Repository for managing playback resume positions.
     */
    @kotlin.Metadata(mv = {1, 9, 0}, k = 3, xi = 48)
    public static final class DefaultImpls {
    }
}