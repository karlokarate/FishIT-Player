package com.fishit.player.core.persistence.repositories;

/**
 * Repository for managing screen time tracking for kids profiles.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0005\bf\u0018\u00002\u00020\u0001J\u0018\u0010\u0002\u001a\u0004\u0018\u00010\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u00a6@\u00a2\u0006\u0002\u0010\u0006J\u0016\u0010\u0007\u001a\u00020\b2\u0006\u0010\u0004\u001a\u00020\u0005H\u00a6@\u00a2\u0006\u0002\u0010\u0006J\u001e\u0010\t\u001a\u00020\n2\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u000b\u001a\u00020\bH\u00a6@\u00a2\u0006\u0002\u0010\fJ\u001e\u0010\r\u001a\u00020\n2\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u000e\u001a\u00020\bH\u00a6@\u00a2\u0006\u0002\u0010\f\u00a8\u0006\u000f"}, d2 = {"Lcom/fishit/player/core/persistence/repositories/ScreenTimeRepository;", "", "getTodayEntry", "Lcom/fishit/player/core/persistence/repositories/ScreenTimeEntry;", "profileId", "", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "remainingMinutes", "", "setDailyLimit", "", "limitMinutes", "(JILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "tickUsageIfPlaying", "deltaSecs", "persistence_debug"})
public abstract interface ScreenTimeRepository {
    
    /**
     * Get remaining screen time in minutes for a kid profile.
     * @param profileId Kid profile ID
     * @return Remaining minutes (can be negative if over limit)
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object remainingMinutes(long profileId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Integer> $completion);
    
    /**
     * Tick usage time if playback is active.
     * @param profileId Kid profile ID
     * @param deltaSecs Seconds to add to usage
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object tickUsageIfPlaying(long profileId, int deltaSecs, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    /**
     * Get today's screen time entry for a profile.
     * @param profileId Kid profile ID
     * @return Screen time entry or null if none exists
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getTodayEntry(long profileId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.ScreenTimeEntry> $completion);
    
    /**
     * Set the daily limit for a profile.
     * @param profileId Kid profile ID
     * @param limitMinutes Daily limit in minutes
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object setDailyLimit(long profileId, int limitMinutes, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
}