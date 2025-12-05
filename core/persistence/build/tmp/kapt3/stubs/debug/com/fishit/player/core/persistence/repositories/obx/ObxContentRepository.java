package com.fishit.player.core.persistence.repositories.obx;

/**
 * ObjectBox implementation of ContentRepository.
 * Provides read-only access to synced content.
 */
@javax.inject.Singleton()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000j\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u0007\u0018\u00002\u00020\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J(\u0010\u0011\u001a\u0004\u0018\u00010\u00122\u0006\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u00142\u0006\u0010\u0016\u001a\u00020\u0014H\u0096@\u00a2\u0006\u0002\u0010\u0017J\u0018\u0010\u0018\u001a\u0004\u0018\u00010\u00192\u0006\u0010\u001a\u001a\u00020\u001bH\u0096@\u00a2\u0006\u0002\u0010\u001cJ\u0018\u0010\u001d\u001a\u0004\u0018\u00010\u00192\u0006\u0010\u001e\u001a\u00020\u0014H\u0096@\u00a2\u0006\u0002\u0010\u001fJ\u0018\u0010 \u001a\u0004\u0018\u00010!2\u0006\u0010\u001a\u001a\u00020\u001bH\u0096@\u00a2\u0006\u0002\u0010\u001cJ\u0018\u0010\"\u001a\u0004\u0018\u00010!2\u0006\u0010\u0013\u001a\u00020\u0014H\u0096@\u00a2\u0006\u0002\u0010\u001fJ \u0010#\u001a\u0004\u0018\u00010$2\u0006\u0010%\u001a\u00020\u001b2\u0006\u0010&\u001a\u00020\u001bH\u0096@\u00a2\u0006\u0002\u0010\'J\u0018\u0010(\u001a\u0004\u0018\u00010$2\u0006\u0010\u001a\u001a\u00020\u001bH\u0096@\u00a2\u0006\u0002\u0010\u001cJ\u0018\u0010)\u001a\u0004\u0018\u00010*2\u0006\u0010\u001a\u001a\u00020\u001bH\u0096@\u00a2\u0006\u0002\u0010\u001cJ\u0018\u0010+\u001a\u0004\u0018\u00010*2\u0006\u0010,\u001a\u00020\u0014H\u0096@\u00a2\u0006\u0002\u0010\u001fJ\f\u0010-\u001a\u00020\u0012*\u00020\u0007H\u0002J\f\u0010-\u001a\u00020\u0019*\u00020\nH\u0002J\f\u0010-\u001a\u00020!*\u00020\fH\u0002J\f\u0010-\u001a\u00020$*\u00020\u000eH\u0002J\f\u0010-\u001a\u00020**\u00020\u0010H\u0002R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R2\u0010\u0005\u001a&\u0012\f\u0012\n \b*\u0004\u0018\u00010\u00070\u0007 \b*\u0012\u0012\f\u0012\n \b*\u0004\u0018\u00010\u00070\u0007\u0018\u00010\u00060\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R2\u0010\t\u001a&\u0012\f\u0012\n \b*\u0004\u0018\u00010\n0\n \b*\u0012\u0012\f\u0012\n \b*\u0004\u0018\u00010\n0\n\u0018\u00010\u00060\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R2\u0010\u000b\u001a&\u0012\f\u0012\n \b*\u0004\u0018\u00010\f0\f \b*\u0012\u0012\f\u0012\n \b*\u0004\u0018\u00010\f0\f\u0018\u00010\u00060\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R2\u0010\r\u001a&\u0012\f\u0012\n \b*\u0004\u0018\u00010\u000e0\u000e \b*\u0012\u0012\f\u0012\n \b*\u0004\u0018\u00010\u000e0\u000e\u0018\u00010\u00060\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R2\u0010\u000f\u001a&\u0012\f\u0012\n \b*\u0004\u0018\u00010\u00100\u0010 \b*\u0012\u0012\f\u0012\n \b*\u0004\u0018\u00010\u00100\u0010\u0018\u00010\u00060\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006."}, d2 = {"Lcom/fishit/player/core/persistence/repositories/obx/ObxContentRepository;", "Lcom/fishit/player/core/persistence/repositories/ContentRepository;", "boxStore", "Lio/objectbox/BoxStore;", "(Lio/objectbox/BoxStore;)V", "episodeBox", "Lio/objectbox/Box;", "Lcom/fishit/player/core/persistence/obx/ObxEpisode;", "kotlin.jvm.PlatformType", "liveBox", "Lcom/fishit/player/core/persistence/obx/ObxLive;", "seriesBox", "Lcom/fishit/player/core/persistence/obx/ObxSeries;", "telegramBox", "Lcom/fishit/player/core/persistence/obx/ObxTelegramMessage;", "vodBox", "Lcom/fishit/player/core/persistence/obx/ObxVod;", "getEpisode", "Lcom/fishit/player/core/persistence/repositories/Episode;", "seriesId", "", "season", "episode", "(IIILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getLiveById", "Lcom/fishit/player/core/persistence/repositories/Live;", "id", "", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getLiveByStreamId", "streamId", "(ILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getSeriesById", "Lcom/fishit/player/core/persistence/repositories/Series;", "getSeriesByStreamId", "getTelegramMessage", "Lcom/fishit/player/core/persistence/repositories/TelegramMessage;", "chatId", "messageId", "(JJLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getTelegramMessageById", "getVodById", "Lcom/fishit/player/core/persistence/repositories/Vod;", "getVodByStreamId", "vodId", "toDomainModel", "persistence_debug"})
public final class ObxContentRepository implements com.fishit.player.core.persistence.repositories.ContentRepository {
    @org.jetbrains.annotations.NotNull()
    private final io.objectbox.BoxStore boxStore = null;
    private final io.objectbox.Box<com.fishit.player.core.persistence.obx.ObxVod> vodBox = null;
    private final io.objectbox.Box<com.fishit.player.core.persistence.obx.ObxSeries> seriesBox = null;
    private final io.objectbox.Box<com.fishit.player.core.persistence.obx.ObxEpisode> episodeBox = null;
    private final io.objectbox.Box<com.fishit.player.core.persistence.obx.ObxLive> liveBox = null;
    private final io.objectbox.Box<com.fishit.player.core.persistence.obx.ObxTelegramMessage> telegramBox = null;
    
    @javax.inject.Inject()
    public ObxContentRepository(@org.jetbrains.annotations.NotNull()
    io.objectbox.BoxStore boxStore) {
        super();
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getVodById(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.Vod> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getVodByStreamId(int vodId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.Vod> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getSeriesById(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.Series> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getSeriesByStreamId(int seriesId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.Series> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getEpisode(int seriesId, int season, int episode, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.Episode> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getLiveById(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.Live> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getLiveByStreamId(int streamId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.Live> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getTelegramMessageById(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.TelegramMessage> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getTelegramMessage(long chatId, long messageId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.TelegramMessage> $completion) {
        return null;
    }
    
    private final com.fishit.player.core.persistence.repositories.Vod toDomainModel(com.fishit.player.core.persistence.obx.ObxVod $this$toDomainModel) {
        return null;
    }
    
    private final com.fishit.player.core.persistence.repositories.Series toDomainModel(com.fishit.player.core.persistence.obx.ObxSeries $this$toDomainModel) {
        return null;
    }
    
    private final com.fishit.player.core.persistence.repositories.Episode toDomainModel(com.fishit.player.core.persistence.obx.ObxEpisode $this$toDomainModel) {
        return null;
    }
    
    private final com.fishit.player.core.persistence.repositories.Live toDomainModel(com.fishit.player.core.persistence.obx.ObxLive $this$toDomainModel) {
        return null;
    }
    
    private final com.fishit.player.core.persistence.repositories.TelegramMessage toDomainModel(com.fishit.player.core.persistence.obx.ObxTelegramMessage $this$toDomainModel) {
        return null;
    }
}