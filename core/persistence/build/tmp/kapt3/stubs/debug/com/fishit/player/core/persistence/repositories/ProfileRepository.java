package com.fishit.player.core.persistence.repositories;

/**
 * Repository for managing user profiles.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0002\b\u0004\bf\u0018\u00002\u00020\u0001J\u0016\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u00a6@\u00a2\u0006\u0002\u0010\u0006J\u0014\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\bH\u00a6@\u00a2\u0006\u0002\u0010\nJ\u0010\u0010\u000b\u001a\u0004\u0018\u00010\tH\u00a6@\u00a2\u0006\u0002\u0010\nJ\u0018\u0010\f\u001a\u0004\u0018\u00010\t2\u0006\u0010\u0004\u001a\u00020\u0005H\u00a6@\u00a2\u0006\u0002\u0010\u0006J\u0016\u0010\r\u001a\u00020\u000e2\u0006\u0010\u0004\u001a\u00020\u0005H\u00a6@\u00a2\u0006\u0002\u0010\u0006J\u0016\u0010\u000f\u001a\u00020\u00052\u0006\u0010\u0010\u001a\u00020\tH\u00a6@\u00a2\u0006\u0002\u0010\u0011\u00a8\u0006\u0012"}, d2 = {"Lcom/fishit/player/core/persistence/repositories/ProfileRepository;", "", "deleteProfile", "", "id", "", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getAllProfiles", "", "Lcom/fishit/player/core/model/Profile;", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getCurrentProfile", "getProfile", "isKidsProfile", "", "saveProfile", "profile", "(Lcom/fishit/player/core/model/Profile;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "persistence_debug"})
public abstract interface ProfileRepository {
    
    /**
     * Get the currently active profile.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getCurrentProfile(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.model.Profile> $completion);
    
    /**
     * Get a profile by its ID.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getProfile(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.model.Profile> $completion);
    
    /**
     * Get all profiles.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getAllProfiles(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.fishit.player.core.model.Profile>> $completion);
    
    /**
     * Check if a profile is a kids profile.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object isKidsProfile(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Boolean> $completion);
    
    /**
     * Create or update a profile.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object saveProfile(@org.jetbrains.annotations.NotNull()
    com.fishit.player.core.model.Profile profile, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Long> $completion);
    
    /**
     * Delete a profile.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object deleteProfile(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
}