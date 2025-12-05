package com.fishit.player.core.persistence.repositories.obx;

/**
 * ObjectBox-backed implementation of ContentRepository.
 * Ported from v1: app/src/main/java/com/chris/m3usuite/data/repo/XtreamObxRepository.kt
 */
@javax.inject.Singleton()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000b\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0007\u0018\u00002\u00020\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J(\u0010\u0010\u001a\u0004\u0018\u00010\u00112\u0006\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\u0015H\u0096@\u00a2\u0006\u0002\u0010\u0017J\u0018\u0010\u0018\u001a\u0004\u0018\u00010\u00192\u0006\u0010\u001a\u001a\u00020\u0013H\u0096@\u00a2\u0006\u0002\u0010\u001bJ\u0018\u0010\u001c\u001a\u0004\u0018\u00010\u001d2\u0006\u0010\u001a\u001a\u00020\u0013H\u0096@\u00a2\u0006\u0002\u0010\u001bJ\u0018\u0010\u001e\u001a\u0004\u0018\u00010\u001f2\u0006\u0010\u001a\u001a\u00020\u0013H\u0096@\u00a2\u0006\u0002\u0010\u001bJ\u0018\u0010 \u001a\u0004\u0018\u00010!2\u0006\u0010\u001a\u001a\u00020\u0013H\u0096@\u00a2\u0006\u0002\u0010\u001bR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\b\u001a\b\u0012\u0004\u0012\u00020\t0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u000b0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\f\u001a\b\u0012\u0004\u0012\u00020\r0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u000f0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\""}, d2 = {"Lcom/fishit/player/core/persistence/repositories/obx/ObxContentRepository;", "Lcom/fishit/player/core/persistence/repositories/ContentRepository;", "boxStore", "Lio/objectbox/BoxStore;", "(Lio/objectbox/BoxStore;)V", "episodeBox", "Lio/objectbox/Box;", "Lcom/fishit/player/core/persistence/obx/ObxEpisode;", "liveBox", "Lcom/fishit/player/core/persistence/obx/ObxLive;", "seriesBox", "Lcom/fishit/player/core/persistence/obx/ObxSeries;", "telegramBox", "Lcom/fishit/player/core/persistence/obx/ObxTelegramMessage;", "vodBox", "Lcom/fishit/player/core/persistence/obx/ObxVod;", "getEpisode", "Lcom/fishit/player/core/persistence/repositories/EpisodeContent;", "seriesId", "", "season", "", "episode", "(JIILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getLiveById", "Lcom/fishit/player/core/persistence/repositories/LiveContent;", "id", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getSeriesById", "Lcom/fishit/player/core/persistence/repositories/SeriesContent;", "getTelegramMessageById", "Lcom/fishit/player/core/persistence/repositories/TelegramContent;", "getVodById", "Lcom/fishit/player/core/persistence/repositories/VodContent;", "persistence_release"})
public final class ObxContentRepository implements com.fishit.player.core.persistence.repositories.ContentRepository {
    @org.jetbrains.annotations.NotNull()
    private final io.objectbox.BoxStore boxStore = null;
    @org.jetbrains.annotations.NotNull()
    private final io.objectbox.Box<com.fishit.player.core.persistence.obx.ObxVod> vodBox = null;
    @org.jetbrains.annotations.NotNull()
    private final io.objectbox.Box<com.fishit.player.core.persistence.obx.ObxSeries> seriesBox = null;
    @org.jetbrains.annotations.NotNull()
    private final io.objectbox.Box<com.fishit.player.core.persistence.obx.ObxEpisode> episodeBox = null;
    @org.jetbrains.annotations.NotNull()
    private final io.objectbox.Box<com.fishit.player.core.persistence.obx.ObxLive> liveBox = null;
    @org.jetbrains.annotations.NotNull()
    private final io.objectbox.Box<com.fishit.player.core.persistence.obx.ObxTelegramMessage> telegramBox = null;
    
    @javax.inject.Inject()
    public ObxContentRepository(@org.jetbrains.annotations.NotNull()
    io.objectbox.BoxStore boxStore) {
        super();
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getVodById(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.VodContent> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getSeriesById(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.SeriesContent> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getEpisode(long seriesId, int season, int episode, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.EpisodeContent> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getLiveById(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.LiveContent> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object getTelegramMessageById(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.fishit.player.core.persistence.repositories.TelegramContent> $completion) {
        return null;
    }
}