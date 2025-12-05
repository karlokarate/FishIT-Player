package com.fishit.player.core.persistence.repositories;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\t\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0002\b7\b\u0086\b\u0018\u00002\u00020\u0001B\u0095\u0001\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0003\u0012\b\u0010\u0006\u001a\u0004\u0018\u00010\u0007\u0012\b\u0010\b\u001a\u0004\u0018\u00010\t\u0012\b\u0010\n\u001a\u0004\u0018\u00010\u000b\u0012\b\u0010\f\u001a\u0004\u0018\u00010\t\u0012\b\u0010\r\u001a\u0004\u0018\u00010\t\u0012\b\u0010\u000e\u001a\u0004\u0018\u00010\u0007\u0012\b\u0010\u000f\u001a\u0004\u0018\u00010\t\u0012\b\u0010\u0010\u001a\u0004\u0018\u00010\u0003\u0012\b\u0010\u0011\u001a\u0004\u0018\u00010\u0007\u0012\b\u0010\u0012\u001a\u0004\u0018\u00010\u0007\u0012\b\u0010\u0013\u001a\u0004\u0018\u00010\t\u0012\b\u0010\u0014\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\u0002\u0010\u0015J\t\u0010-\u001a\u00020\u0003H\u00c6\u0003J\u000b\u0010.\u001a\u0004\u0018\u00010\tH\u00c6\u0003J\u0010\u0010/\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003\u00a2\u0006\u0002\u0010%J\u0010\u00100\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003\u00a2\u0006\u0002\u0010\u001bJ\u0010\u00101\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003\u00a2\u0006\u0002\u0010\u001bJ\u000b\u00102\u001a\u0004\u0018\u00010\tH\u00c6\u0003J\u0010\u00103\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003\u00a2\u0006\u0002\u0010\u001bJ\t\u00104\u001a\u00020\u0003H\u00c6\u0003J\t\u00105\u001a\u00020\u0003H\u00c6\u0003J\u0010\u00106\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003\u00a2\u0006\u0002\u0010\u001bJ\u000b\u00107\u001a\u0004\u0018\u00010\tH\u00c6\u0003J\u0010\u00108\u001a\u0004\u0018\u00010\u000bH\u00c6\u0003\u00a2\u0006\u0002\u0010(J\u000b\u00109\u001a\u0004\u0018\u00010\tH\u00c6\u0003J\u000b\u0010:\u001a\u0004\u0018\u00010\tH\u00c6\u0003J\u0010\u0010;\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003\u00a2\u0006\u0002\u0010\u001bJ\u00bc\u0001\u0010<\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u00032\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\t2\n\b\u0002\u0010\n\u001a\u0004\u0018\u00010\u000b2\n\b\u0002\u0010\f\u001a\u0004\u0018\u00010\t2\n\b\u0002\u0010\r\u001a\u0004\u0018\u00010\t2\n\b\u0002\u0010\u000e\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\u000f\u001a\u0004\u0018\u00010\t2\n\b\u0002\u0010\u0010\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u0011\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\u0012\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\u0013\u001a\u0004\u0018\u00010\t2\n\b\u0002\u0010\u0014\u001a\u0004\u0018\u00010\u0007H\u00c6\u0001\u00a2\u0006\u0002\u0010=J\u0013\u0010>\u001a\u00020\u000b2\b\u0010?\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010@\u001a\u00020\u0007H\u00d6\u0001J\t\u0010A\u001a\u00020\tH\u00d6\u0001R\u0013\u0010\f\u001a\u0004\u0018\u00010\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0017R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0019R\u0015\u0010\u000e\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\n\n\u0002\u0010\u001c\u001a\u0004\b\u001a\u0010\u001bR\u0015\u0010\u0006\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\n\n\u0002\u0010\u001c\u001a\u0004\b\u001d\u0010\u001bR\u0013\u0010\r\u001a\u0004\u0018\u00010\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001e\u0010\u0017R\u0015\u0010\u0012\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\n\n\u0002\u0010\u001c\u001a\u0004\b\u001f\u0010\u001bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b \u0010\u0019R\u0011\u0010\u0005\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b!\u0010\u0019R\u0013\u0010\u000f\u001a\u0004\u0018\u00010\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\"\u0010\u0017R\u0013\u0010\b\u001a\u0004\u0018\u00010\t\u00a2\u0006\b\n\u0000\u001a\u0004\b#\u0010\u0017R\u0015\u0010\u0010\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\n\n\u0002\u0010&\u001a\u0004\b$\u0010%R\u0015\u0010\n\u001a\u0004\u0018\u00010\u000b\u00a2\u0006\n\n\u0002\u0010)\u001a\u0004\b\'\u0010(R\u0013\u0010\u0013\u001a\u0004\u0018\u00010\t\u00a2\u0006\b\n\u0000\u001a\u0004\b*\u0010\u0017R\u0015\u0010\u0011\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\n\n\u0002\u0010\u001c\u001a\u0004\b+\u0010\u001bR\u0015\u0010\u0014\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\n\n\u0002\u0010\u001c\u001a\u0004\b,\u0010\u001b\u00a8\u0006B"}, d2 = {"Lcom/fishit/player/core/persistence/repositories/TelegramMessage;", "", "id", "", "chatId", "messageId", "fileId", "", "remoteId", "", "supportsStreaming", "", "caption", "fileName", "durationSecs", "mimeType", "sizeBytes", "width", "height", "title", "year", "(JJJLjava/lang/Integer;Ljava/lang/String;Ljava/lang/Boolean;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/Long;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/Integer;)V", "getCaption", "()Ljava/lang/String;", "getChatId", "()J", "getDurationSecs", "()Ljava/lang/Integer;", "Ljava/lang/Integer;", "getFileId", "getFileName", "getHeight", "getId", "getMessageId", "getMimeType", "getRemoteId", "getSizeBytes", "()Ljava/lang/Long;", "Ljava/lang/Long;", "getSupportsStreaming", "()Ljava/lang/Boolean;", "Ljava/lang/Boolean;", "getTitle", "getWidth", "getYear", "component1", "component10", "component11", "component12", "component13", "component14", "component15", "component2", "component3", "component4", "component5", "component6", "component7", "component8", "component9", "copy", "(JJJLjava/lang/Integer;Ljava/lang/String;Ljava/lang/Boolean;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/Long;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/Integer;)Lcom/fishit/player/core/persistence/repositories/TelegramMessage;", "equals", "other", "hashCode", "toString", "persistence_debug"})
public final class TelegramMessage {
    private final long id = 0L;
    private final long chatId = 0L;
    private final long messageId = 0L;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Integer fileId = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String remoteId = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Boolean supportsStreaming = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String caption = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String fileName = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Integer durationSecs = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String mimeType = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Long sizeBytes = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Integer width = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Integer height = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.String title = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Integer year = null;
    
    public final long component1() {
        return 0L;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component10() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Long component11() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer component12() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer component13() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component14() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer component15() {
        return null;
    }
    
    public final long component2() {
        return 0L;
    }
    
    public final long component3() {
        return 0L;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer component4() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component5() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Boolean component6() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component7() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component8() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer component9() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.fishit.player.core.persistence.repositories.TelegramMessage copy(long id, long chatId, long messageId, @org.jetbrains.annotations.Nullable()
    java.lang.Integer fileId, @org.jetbrains.annotations.Nullable()
    java.lang.String remoteId, @org.jetbrains.annotations.Nullable()
    java.lang.Boolean supportsStreaming, @org.jetbrains.annotations.Nullable()
    java.lang.String caption, @org.jetbrains.annotations.Nullable()
    java.lang.String fileName, @org.jetbrains.annotations.Nullable()
    java.lang.Integer durationSecs, @org.jetbrains.annotations.Nullable()
    java.lang.String mimeType, @org.jetbrains.annotations.Nullable()
    java.lang.Long sizeBytes, @org.jetbrains.annotations.Nullable()
    java.lang.Integer width, @org.jetbrains.annotations.Nullable()
    java.lang.Integer height, @org.jetbrains.annotations.Nullable()
    java.lang.String title, @org.jetbrains.annotations.Nullable()
    java.lang.Integer year) {
        return null;
    }
    
    @java.lang.Override()
    public boolean equals(@org.jetbrains.annotations.Nullable()
    java.lang.Object other) {
        return false;
    }
    
    @java.lang.Override()
    public int hashCode() {
        return 0;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public java.lang.String toString() {
        return null;
    }
    
    public TelegramMessage(long id, long chatId, long messageId, @org.jetbrains.annotations.Nullable()
    java.lang.Integer fileId, @org.jetbrains.annotations.Nullable()
    java.lang.String remoteId, @org.jetbrains.annotations.Nullable()
    java.lang.Boolean supportsStreaming, @org.jetbrains.annotations.Nullable()
    java.lang.String caption, @org.jetbrains.annotations.Nullable()
    java.lang.String fileName, @org.jetbrains.annotations.Nullable()
    java.lang.Integer durationSecs, @org.jetbrains.annotations.Nullable()
    java.lang.String mimeType, @org.jetbrains.annotations.Nullable()
    java.lang.Long sizeBytes, @org.jetbrains.annotations.Nullable()
    java.lang.Integer width, @org.jetbrains.annotations.Nullable()
    java.lang.Integer height, @org.jetbrains.annotations.Nullable()
    java.lang.String title, @org.jetbrains.annotations.Nullable()
    java.lang.Integer year) {
        super();
    }
    
    public final long getId() {
        return 0L;
    }
    
    public final long getChatId() {
        return 0L;
    }
    
    public final long getMessageId() {
        return 0L;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer getFileId() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getRemoteId() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Boolean getSupportsStreaming() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getCaption() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getFileName() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer getDurationSecs() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getMimeType() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Long getSizeBytes() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer getWidth() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer getHeight() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getTitle() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer getYear() {
        return null;
    }
}