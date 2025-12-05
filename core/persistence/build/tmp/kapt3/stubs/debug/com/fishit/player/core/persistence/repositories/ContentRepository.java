package com.fishit.player.core.persistence.repositories;

/**
 * Repository for querying content from various sources (Xtream, Telegram, IO).
 * This is a minimal interface for Phase 2 - full implementation will be done in pipeline-specific repositories.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00008\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\bf\u0018\u00002\u00020\u0001J(\u0010\u0002\u001a\u0004\u0018\u00010\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\u0007H\u00a6@\u00a2\u0006\u0002\u0010\tJ\u0018\u0010\n\u001a\u0004\u0018\u00010\u000b2\u0006\u0010\f\u001a\u00020\u0005H\u00a6@\u00a2\u0006\u0002\u0010\rJ\u0018\u0010\u000e\u001a\u0004\u0018\u00010\u000f2\u0006\u0010\f\u001a\u00020\u0005H\u00a6@\u00a2\u0006\u0002\u0010\rJ\u0018\u0010\u0010\u001a\u0004\u0018\u00010\u00112\u0006\u0010\f\u001a\u00020\u0005H\u00a6@\u00a2\u0006\u0002\u0010\rJ\u0018\u0010\u0012\u001a\u0004\u0018\u00010\u00132\u0006\u0010\f\u001a\u00020\u0005H\u00a6@\u00a2\u0006\u0002\u0010\r\u00a8\u0006\u0014"}, d2 = {"Lcom/fishit/player/core/persistence/repositories/ContentRepository;", "", "getEpisode", "Lcom/fishit/player/core/persistence/repositories/EpisodeContent;", "seriesId", "", "season", "", "episode", "(JIILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getLiveById", "Lcom/fishit/player/core/persistence/repositories/LiveContent;", "id", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getSeriesById", "Lcom/fishit/player/core/persistence/repositories/SeriesContent;", "getTelegramMessageById", "Lcom/fishit/player/core/persistence/repositories/TelegramContent;", "getVodById", "Lcom/fishit/player/core/persistence/repositories/VodContent;", "persistence_debug"})
public abstract interface ContentRepository {
    
    /**
     * Get VOD content by ID.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getVodById(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.VodContent> $completion);
    
    /**
     * Get Series content by ID.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getSeriesById(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.SeriesContent> $completion);
    
    /**
     * Get a specific episode.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getEpisode(long seriesId, int season, int episode, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.EpisodeContent> $completion);
    
    /**
     * Get Live channel by ID.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getLiveById(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.LiveContent> $completion);
    
    /**
     * Get Telegram message by ID.
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getTelegramMessageById(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.TelegramContent> $completion);
}