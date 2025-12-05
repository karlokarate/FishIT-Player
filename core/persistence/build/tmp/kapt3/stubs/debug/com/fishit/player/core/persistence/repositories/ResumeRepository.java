package com.fishit.player.core.persistence.repositories;

/**
 * Repository for managing resume points (playback positions).
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0007\bf\u0018\u00002\u00020\u0001J\u0016\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u00a6@\u00a2\u0006\u0002\u0010\u0006J\u001c\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\b2\u0006\u0010\n\u001a\u00020\u000bH\u00a6@\u00a2\u0006\u0002\u0010\fJ\u0018\u0010\r\u001a\u0004\u0018\u00010\t2\u0006\u0010\u0004\u001a\u00020\u0005H\u00a6@\u00a2\u0006\u0002\u0010\u0006J&\u0010\u000e\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u000f\u001a\u00020\u000b2\u0006\u0010\u0010\u001a\u00020\u000bH\u00a6@\u00a2\u0006\u0002\u0010\u0011\u00a8\u0006\u0012"}, d2 = {"Lcom/fishit/player/core/persistence/repositories/ResumeRepository;", "", "clearResumePoint", "", "contentId", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getAllResumePoints", "", "Lcom/fishit/player/core/model/ResumePoint;", "profileId", "", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getResumePoint", "saveResumePoint", "positionMs", "durationMs", "(Ljava/lang/String;JJLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "persistence_debug"})
public abstract interface ResumeRepository {
    
    /**
     * Get the resume point for a content ID.
     * @param contentId Unique identifier for the content
     * @return ResumePoint if exists, null otherwise
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getResumePoint(@org.jetbrains.annotations.NotNull()
    java.lang.String contentId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.model.ResumePoint> $completion);
    
    /**
     * Save a resume point for content.
     * Implements v1 resume rules:
     * - Only save if position > 10 seconds
     * - Clear if remaining < 10 seconds
     *
     * @param contentId Unique identifier for the content
     * @param positionMs Current playback position in milliseconds
     * @param durationMs Total duration in milliseconds
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object saveResumePoint(@org.jetbrains.annotations.NotNull()
    java.lang.String contentId, long positionMs, long durationMs, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    /**
     * Clear a resume point for content.
     * @param contentId Unique identifier for the content
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object clearResumePoint(@org.jetbrains.annotations.NotNull()
    java.lang.String contentId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    /**
     * Get all resume points for a profile.
     * @param profileId Profile ID (0 for default/current profile)
     * @return List of resume points
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getAllResumePoints(long profileId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.fishit.player.core.model.ResumePoint>> $completion);
}