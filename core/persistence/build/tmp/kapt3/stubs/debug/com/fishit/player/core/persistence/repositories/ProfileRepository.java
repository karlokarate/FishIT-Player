package com.fishit.player.core.persistence.repositories;

/**
 * Repository for managing user profiles.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\bf\u0018\u00002\u00020\u0001J\u0014\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003H\u00a6@\u00a2\u0006\u0002\u0010\u0005J\u0010\u0010\u0006\u001a\u0004\u0018\u00010\u0004H\u00a6@\u00a2\u0006\u0002\u0010\u0005J\u0018\u0010\u0007\u001a\u0004\u0018\u00010\u00042\u0006\u0010\b\u001a\u00020\tH\u00a6@\u00a2\u0006\u0002\u0010\nJ\u0016\u0010\u000b\u001a\u00020\f2\u0006\u0010\b\u001a\u00020\tH\u00a6@\u00a2\u0006\u0002\u0010\n\u00a8\u0006\r"}, d2 = {"Lcom/fishit/player/core/persistence/repositories/ProfileRepository;", "", "getAllProfiles", "", "Lcom/fishit/player/core/persistence/repositories/Profile;", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getCurrentProfile", "getProfile", "id", "", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "isKidsProfile", "", "persistence_debug"})
public abstract interface ProfileRepository {
    
    /**
     * Get the currently active profile.
     * @return Current profile or null if none set
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getCurrentProfile(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.Profile> $completion);
    
    /**
     * Get a profile by ID.
     * @param id Profile ID
     * @return Profile if exists, null otherwise
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getProfile(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.Profile> $completion);
    
    /**
     * Get all profiles.
     * @return List of all profiles
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getAllProfiles(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.fishit.player.core.persistence.repositories.Profile>> $completion);
    
    /**
     * Check if a profile is a kids profile.
     * @param id Profile ID
     * @return true if profile is a kids profile, false otherwise
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object isKidsProfile(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Boolean> $completion);
}