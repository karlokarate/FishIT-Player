package com.fishit.player.core.persistence.repositories;

/**
 * Repository for accessing content (VOD, Series, Live, Telegram).
 * Provides read-only access to synced content from pipelines.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000>\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\bf\u0018\u00002\u00020\u0001J(\u0010\u0002\u001a\u0004\u0018\u00010\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u00052\u0006\u0010\u0007\u001a\u00020\u0005H\u00a6@\u00a2\u0006\u0002\u0010\bJ\u0018\u0010\t\u001a\u0004\u0018\u00010\n2\u0006\u0010\u000b\u001a\u00020\fH\u00a6@\u00a2\u0006\u0002\u0010\rJ\u0018\u0010\u000e\u001a\u0004\u0018\u00010\n2\u0006\u0010\u000f\u001a\u00020\u0005H\u00a6@\u00a2\u0006\u0002\u0010\u0010J\u0018\u0010\u0011\u001a\u0004\u0018\u00010\u00122\u0006\u0010\u000b\u001a\u00020\fH\u00a6@\u00a2\u0006\u0002\u0010\rJ\u0018\u0010\u0013\u001a\u0004\u0018\u00010\u00122\u0006\u0010\u0004\u001a\u00020\u0005H\u00a6@\u00a2\u0006\u0002\u0010\u0010J \u0010\u0014\u001a\u0004\u0018\u00010\u00152\u0006\u0010\u0016\u001a\u00020\f2\u0006\u0010\u0017\u001a\u00020\fH\u00a6@\u00a2\u0006\u0002\u0010\u0018J\u0018\u0010\u0019\u001a\u0004\u0018\u00010\u00152\u0006\u0010\u000b\u001a\u00020\fH\u00a6@\u00a2\u0006\u0002\u0010\rJ\u0018\u0010\u001a\u001a\u0004\u0018\u00010\u001b2\u0006\u0010\u000b\u001a\u00020\fH\u00a6@\u00a2\u0006\u0002\u0010\rJ\u0018\u0010\u001c\u001a\u0004\u0018\u00010\u001b2\u0006\u0010\u001d\u001a\u00020\u0005H\u00a6@\u00a2\u0006\u0002\u0010\u0010\u00a8\u0006\u001e"}, d2 = {"Lcom/fishit/player/core/persistence/repositories/ContentRepository;", "", "getEpisode", "Lcom/fishit/player/core/persistence/repositories/Episode;", "seriesId", "", "season", "episode", "(IIILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getLiveById", "Lcom/fishit/player/core/persistence/repositories/Live;", "id", "", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getLiveByStreamId", "streamId", "(ILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getSeriesById", "Lcom/fishit/player/core/persistence/repositories/Series;", "getSeriesByStreamId", "getTelegramMessage", "Lcom/fishit/player/core/persistence/repositories/TelegramMessage;", "chatId", "messageId", "(JJLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getTelegramMessageById", "getVodById", "Lcom/fishit/player/core/persistence/repositories/Vod;", "getVodByStreamId", "vodId", "persistence_debug"})
public abstract interface ContentRepository {
    
    /**
     * Get a VOD by its ObjectBox ID.
     * @param id ObjectBox entity ID
     * @return VOD data or null if not found
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getVodById(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.Vod> $completion);
    
    /**
     * Get a VOD by its Xtream stream ID.
     * @param vodId Xtream VOD ID
     * @return VOD data or null if not found
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getVodByStreamId(int vodId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.Vod> $completion);
    
    /**
     * Get a Series by its ObjectBox ID.
     * @param id ObjectBox entity ID
     * @return Series data or null if not found
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getSeriesById(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.Series> $completion);
    
    /**
     * Get a Series by its Xtream series ID.
     * @param seriesId Xtream series ID
     * @return Series data or null if not found
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getSeriesByStreamId(int seriesId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.Series> $completion);
    
    /**
     * Get a specific episode.
     * @param seriesId Xtream series ID
     * @param season Season number
     * @param episode Episode number
     * @return Episode data or null if not found
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getEpisode(int seriesId, int season, int episode, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.Episode> $completion);
    
    /**
     * Get a Live channel by its ObjectBox ID.
     * @param id ObjectBox entity ID
     * @return Live channel data or null if not found
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getLiveById(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.Live> $completion);
    
    /**
     * Get a Live channel by its Xtream stream ID.
     * @param streamId Xtream stream ID
     * @return Live channel data or null if not found
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getLiveByStreamId(int streamId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.Live> $completion);
    
    /**
     * Get a Telegram message by its ObjectBox ID.
     * @param id ObjectBox entity ID
     * @return Telegram message data or null if not found
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getTelegramMessageById(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.TelegramMessage> $completion);
    
    /**
     * Get a Telegram message by chat and message ID.
     * @param chatId Telegram chat ID
     * @param messageId Telegram message ID
     * @return Telegram message data or null if not found
     */
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getTelegramMessage(long chatId, long messageId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.TelegramMessage> $completion);
}