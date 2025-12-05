package com.fishit.player.core.persistence.obx;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0007\n\u0002\u0010\u0006\n\u0002\bq\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0087\b\u0018\u00002\u00020\u0001B\u00b3\u0002\u0012\b\b\u0002\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0007\u0012\b\b\u0002\u0010\b\u001a\u00020\u0007\u0012\b\b\u0002\u0010\t\u001a\u00020\u0007\u0012\n\b\u0002\u0010\n\u001a\u0004\u0018\u00010\u0007\u0012\n\b\u0002\u0010\u000b\u001a\u0004\u0018\u00010\u0007\u0012\n\b\u0002\u0010\f\u001a\u0004\u0018\u00010\u0005\u0012\n\b\u0002\u0010\r\u001a\u0004\u0018\u00010\u0005\u0012\n\b\u0002\u0010\u000e\u001a\u0004\u0018\u00010\u000f\u0012\n\b\u0002\u0010\u0010\u001a\u0004\u0018\u00010\u0007\u0012\n\b\u0002\u0010\u0011\u001a\u0004\u0018\u00010\u0007\u0012\n\b\u0002\u0010\u0012\u001a\u0004\u0018\u00010\u0007\u0012\n\b\u0002\u0010\u0013\u001a\u0004\u0018\u00010\u0007\u0012\n\b\u0002\u0010\u0014\u001a\u0004\u0018\u00010\u0007\u0012\n\b\u0002\u0010\u0015\u001a\u0004\u0018\u00010\u0007\u0012\n\b\u0002\u0010\u0016\u001a\u0004\u0018\u00010\u0007\u0012\n\b\u0002\u0010\u0017\u001a\u0004\u0018\u00010\u0007\u0012\n\b\u0002\u0010\u0018\u001a\u0004\u0018\u00010\u0007\u0012\n\b\u0002\u0010\u0019\u001a\u0004\u0018\u00010\u0007\u0012\n\b\u0002\u0010\u001a\u001a\u0004\u0018\u00010\u0005\u0012\n\b\u0002\u0010\u001b\u001a\u0004\u0018\u00010\u0007\u0012\n\b\u0002\u0010\u001c\u001a\u0004\u0018\u00010\u0007\u0012\n\b\u0002\u0010\u001d\u001a\u0004\u0018\u00010\u0007\u0012\n\b\u0002\u0010\u001e\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u001f\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\u0002\u0010 J\t\u0010d\u001a\u00020\u0003H\u00c6\u0003J\u0010\u0010e\u001a\u0004\u0018\u00010\u000fH\u00c6\u0003\u00a2\u0006\u0002\u0010NJ\u000b\u0010f\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u000b\u0010g\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u000b\u0010h\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u000b\u0010i\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u000b\u0010j\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u000b\u0010k\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u000b\u0010l\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u000b\u0010m\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u000b\u0010n\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\t\u0010o\u001a\u00020\u0005H\u00c6\u0003J\u000b\u0010p\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u0010\u0010q\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003\u00a2\u0006\u0002\u0010.J\u000b\u0010r\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u000b\u0010s\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u000b\u0010t\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u0010\u0010u\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003\u00a2\u0006\u0002\u0010?J\u0010\u0010v\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003\u00a2\u0006\u0002\u0010?J\t\u0010w\u001a\u00020\u0007H\u00c6\u0003J\t\u0010x\u001a\u00020\u0007H\u00c6\u0003J\t\u0010y\u001a\u00020\u0007H\u00c6\u0003J\u000b\u0010z\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u000b\u0010{\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J\u0010\u0010|\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003\u00a2\u0006\u0002\u0010.J\u0010\u0010}\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003\u00a2\u0006\u0002\u0010.J\u00bc\u0002\u0010~\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00072\b\b\u0002\u0010\b\u001a\u00020\u00072\b\b\u0002\u0010\t\u001a\u00020\u00072\n\b\u0002\u0010\n\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\u000b\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\f\u001a\u0004\u0018\u00010\u00052\n\b\u0002\u0010\r\u001a\u0004\u0018\u00010\u00052\n\b\u0002\u0010\u000e\u001a\u0004\u0018\u00010\u000f2\n\b\u0002\u0010\u0010\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\u0011\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\u0012\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\u0013\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\u0014\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\u0015\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\u0016\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\u0017\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\u0018\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\u0019\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\u001a\u001a\u0004\u0018\u00010\u00052\n\b\u0002\u0010\u001b\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\u001c\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\u001d\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\u001e\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u001f\u001a\u0004\u0018\u00010\u0003H\u00c6\u0001\u00a2\u0006\u0002\u0010\u007fJ\u0016\u0010\u0080\u0001\u001a\u00030\u0081\u00012\t\u0010\u0082\u0001\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\n\u0010\u0083\u0001\u001a\u00020\u0005H\u00d6\u0001J\n\u0010\u0084\u0001\u001a\u00020\u0007H\u00d6\u0001R\u001c\u0010\u0013\u001a\u0004\u0018\u00010\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b!\u0010\"\"\u0004\b#\u0010$R \u0010\u001b\u001a\u0004\u0018\u00010\u00078\u0006@\u0006X\u0087\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b%\u0010\"\"\u0004\b&\u0010$R\u001c\u0010\u0019\u001a\u0004\u0018\u00010\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\'\u0010\"\"\u0004\b(\u0010$R\u001c\u0010\u0014\u001a\u0004\u0018\u00010\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b)\u0010\"\"\u0004\b*\u0010$R\u001c\u0010\u0012\u001a\u0004\u0018\u00010\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b+\u0010\"\"\u0004\b,\u0010$R\u001e\u0010\u001a\u001a\u0004\u0018\u00010\u0005X\u0086\u000e\u00a2\u0006\u0010\n\u0002\u00101\u001a\u0004\b-\u0010.\"\u0004\b/\u00100R\u001c\u0010\u0011\u001a\u0004\u0018\u00010\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b2\u0010\"\"\u0004\b3\u0010$R \u0010\u001d\u001a\u0004\u0018\u00010\u00078\u0006@\u0006X\u0087\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b4\u0010\"\"\u0004\b5\u0010$R\u001e\u0010\u0002\u001a\u00020\u00038\u0006@\u0006X\u0087\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b6\u00107\"\u0004\b8\u00109R\u001c\u0010\u000b\u001a\u0004\u0018\u00010\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b:\u0010\"\"\u0004\b;\u0010$R\u001c\u0010\u0016\u001a\u0004\u0018\u00010\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b<\u0010\"\"\u0004\b=\u0010$R\"\u0010\u001e\u001a\u0004\u0018\u00010\u00038\u0006@\u0006X\u0087\u000e\u00a2\u0006\u0010\n\u0002\u0010B\u001a\u0004\b>\u0010?\"\u0004\b@\u0010AR\u001a\u0010\t\u001a\u00020\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\bC\u0010\"\"\u0004\bD\u0010$R\u001e\u0010\u0006\u001a\u00020\u00078\u0006@\u0006X\u0087\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\bE\u0010\"\"\u0004\bF\u0010$R\u001c\u0010\u0010\u001a\u0004\u0018\u00010\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\bG\u0010\"\"\u0004\bH\u0010$R\u001c\u0010\n\u001a\u0004\u0018\u00010\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\bI\u0010\"\"\u0004\bJ\u0010$R \u0010\u001c\u001a\u0004\u0018\u00010\u00078\u0006@\u0006X\u0087\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\bK\u0010\"\"\u0004\bL\u0010$R\u001e\u0010\u000e\u001a\u0004\u0018\u00010\u000fX\u0086\u000e\u00a2\u0006\u0010\n\u0002\u0010Q\u001a\u0004\bM\u0010N\"\u0004\bO\u0010PR\u001c\u0010\u0015\u001a\u0004\u0018\u00010\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\bR\u0010\"\"\u0004\bS\u0010$R\u001e\u0010\b\u001a\u00020\u00078\u0006@\u0006X\u0087\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\bT\u0010\"\"\u0004\bU\u0010$R\u001c\u0010\u0017\u001a\u0004\u0018\u00010\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\bV\u0010\"\"\u0004\bW\u0010$R\u001c\u0010\u0018\u001a\u0004\u0018\u00010\u0007X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\bX\u0010\"\"\u0004\bY\u0010$R\"\u0010\u001f\u001a\u0004\u0018\u00010\u00038\u0006@\u0006X\u0087\u000e\u00a2\u0006\u0010\n\u0002\u0010B\u001a\u0004\bZ\u0010?\"\u0004\b[\u0010AR\u001e\u0010\u0004\u001a\u00020\u00058\u0006@\u0006X\u0087\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\\\u0010]\"\u0004\b^\u0010_R\"\u0010\f\u001a\u0004\u0018\u00010\u00058\u0006@\u0006X\u0087\u000e\u00a2\u0006\u0010\n\u0002\u00101\u001a\u0004\b`\u0010.\"\u0004\ba\u00100R\"\u0010\r\u001a\u0004\u0018\u00010\u00058\u0006@\u0006X\u0087\u000e\u00a2\u0006\u0010\n\u0002\u00101\u001a\u0004\bb\u0010.\"\u0004\bc\u00100\u00a8\u0006\u0085\u0001"}, d2 = {"Lcom/fishit/player/core/persistence/obx/ObxVod;", "", "id", "", "vodId", "", "nameLower", "", "sortTitleLower", "name", "poster", "imagesJson", "year", "yearKey", "rating", "", "plot", "genre", "director", "cast", "country", "releaseDate", "imdbId", "tmdbId", "trailer", "containerExt", "durationSecs", "categoryId", "providerKey", "genreKey", "importedAt", "updatedAt", "(JILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Double;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Long;Ljava/lang/Long;)V", "getCast", "()Ljava/lang/String;", "setCast", "(Ljava/lang/String;)V", "getCategoryId", "setCategoryId", "getContainerExt", "setContainerExt", "getCountry", "setCountry", "getDirector", "setDirector", "getDurationSecs", "()Ljava/lang/Integer;", "setDurationSecs", "(Ljava/lang/Integer;)V", "Ljava/lang/Integer;", "getGenre", "setGenre", "getGenreKey", "setGenreKey", "getId", "()J", "setId", "(J)V", "getImagesJson", "setImagesJson", "getImdbId", "setImdbId", "getImportedAt", "()Ljava/lang/Long;", "setImportedAt", "(Ljava/lang/Long;)V", "Ljava/lang/Long;", "getName", "setName", "getNameLower", "setNameLower", "getPlot", "setPlot", "getPoster", "setPoster", "getProviderKey", "setProviderKey", "getRating", "()Ljava/lang/Double;", "setRating", "(Ljava/lang/Double;)V", "Ljava/lang/Double;", "getReleaseDate", "setReleaseDate", "getSortTitleLower", "setSortTitleLower", "getTmdbId", "setTmdbId", "getTrailer", "setTrailer", "getUpdatedAt", "setUpdatedAt", "getVodId", "()I", "setVodId", "(I)V", "getYear", "setYear", "getYearKey", "setYearKey", "component1", "component10", "component11", "component12", "component13", "component14", "component15", "component16", "component17", "component18", "component19", "component2", "component20", "component21", "component22", "component23", "component24", "component25", "component26", "component3", "component4", "component5", "component6", "component7", "component8", "component9", "copy", "(JILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Double;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Long;Ljava/lang/Long;)Lcom/fishit/player/core/persistence/obx/ObxVod;", "equals", "", "other", "hashCode", "toString", "persistence_release"})
@io.objectbox.annotation.Entity()
public final class ObxVod {
    @io.objectbox.annotation.Id()
    private long id;
    @io.objectbox.annotation.Unique()
    @io.objectbox.annotation.Index()
    private int vodId;
    @io.objectbox.annotation.Index()
    @org.jetbrains.annotations.NotNull()
    private java.lang.String nameLower;
    @io.objectbox.annotation.Index()
    @org.jetbrains.annotations.NotNull()
    private java.lang.String sortTitleLower;
    @org.jetbrains.annotations.NotNull()
    private java.lang.String name;
    @org.jetbrains.annotations.Nullable()
    private java.lang.String poster;
    @org.jetbrains.annotations.Nullable()
    private java.lang.String imagesJson;
    @io.objectbox.annotation.Index()
    @org.jetbrains.annotations.Nullable()
    private java.lang.Integer year;
    @io.objectbox.annotation.Index()
    @org.jetbrains.annotations.Nullable()
    private java.lang.Integer yearKey;
    @org.jetbrains.annotations.Nullable()
    private java.lang.Double rating;
    @org.jetbrains.annotations.Nullable()
    private java.lang.String plot;
    @org.jetbrains.annotations.Nullable()
    private java.lang.String genre;
    @org.jetbrains.annotations.Nullable()
    private java.lang.String director;
    @org.jetbrains.annotations.Nullable()
    private java.lang.String cast;
    @org.jetbrains.annotations.Nullable()
    private java.lang.String country;
    @org.jetbrains.annotations.Nullable()
    private java.lang.String releaseDate;
    @org.jetbrains.annotations.Nullable()
    private java.lang.String imdbId;
    @org.jetbrains.annotations.Nullable()
    private java.lang.String tmdbId;
    @org.jetbrains.annotations.Nullable()
    private java.lang.String trailer;
    @org.jetbrains.annotations.Nullable()
    private java.lang.String containerExt;
    @org.jetbrains.annotations.Nullable()
    private java.lang.Integer durationSecs;
    @io.objectbox.annotation.Index()
    @org.jetbrains.annotations.Nullable()
    private java.lang.String categoryId;
    @io.objectbox.annotation.Index()
    @org.jetbrains.annotations.Nullable()
    private java.lang.String providerKey;
    @io.objectbox.annotation.Index()
    @org.jetbrains.annotations.Nullable()
    private java.lang.String genreKey;
    @io.objectbox.annotation.Index()
    @org.jetbrains.annotations.Nullable()
    private java.lang.Long importedAt;
    @io.objectbox.annotation.Index()
    @org.jetbrains.annotations.Nullable()
    private java.lang.Long updatedAt;
    
    public final long component1() {
        return 0L;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Double component10() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component11() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component12() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component13() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component14() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component15() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component16() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component17() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component18() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component19() {
        return null;
    }
    
    public final int component2() {
        return 0;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component20() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer component21() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component22() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component23() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component24() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Long component25() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Long component26() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component3() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component4() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component5() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component6() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String component7() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer component8() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer component9() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.fishit.player.core.persistence.obx.ObxVod copy(long id, int vodId, @org.jetbrains.annotations.NotNull()
    java.lang.String nameLower, @org.jetbrains.annotations.NotNull()
    java.lang.String sortTitleLower, @org.jetbrains.annotations.NotNull()
    java.lang.String name, @org.jetbrains.annotations.Nullable()
    java.lang.String poster, @org.jetbrains.annotations.Nullable()
    java.lang.String imagesJson, @org.jetbrains.annotations.Nullable()
    java.lang.Integer year, @org.jetbrains.annotations.Nullable()
    java.lang.Integer yearKey, @org.jetbrains.annotations.Nullable()
    java.lang.Double rating, @org.jetbrains.annotations.Nullable()
    java.lang.String plot, @org.jetbrains.annotations.Nullable()
    java.lang.String genre, @org.jetbrains.annotations.Nullable()
    java.lang.String director, @org.jetbrains.annotations.Nullable()
    java.lang.String cast, @org.jetbrains.annotations.Nullable()
    java.lang.String country, @org.jetbrains.annotations.Nullable()
    java.lang.String releaseDate, @org.jetbrains.annotations.Nullable()
    java.lang.String imdbId, @org.jetbrains.annotations.Nullable()
    java.lang.String tmdbId, @org.jetbrains.annotations.Nullable()
    java.lang.String trailer, @org.jetbrains.annotations.Nullable()
    java.lang.String containerExt, @org.jetbrains.annotations.Nullable()
    java.lang.Integer durationSecs, @org.jetbrains.annotations.Nullable()
    java.lang.String categoryId, @org.jetbrains.annotations.Nullable()
    java.lang.String providerKey, @org.jetbrains.annotations.Nullable()
    java.lang.String genreKey, @org.jetbrains.annotations.Nullable()
    java.lang.Long importedAt, @org.jetbrains.annotations.Nullable()
    java.lang.Long updatedAt) {
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
    
    public ObxVod(long id, int vodId, @org.jetbrains.annotations.NotNull()
    java.lang.String nameLower, @org.jetbrains.annotations.NotNull()
    java.lang.String sortTitleLower, @org.jetbrains.annotations.NotNull()
    java.lang.String name, @org.jetbrains.annotations.Nullable()
    java.lang.String poster, @org.jetbrains.annotations.Nullable()
    java.lang.String imagesJson, @org.jetbrains.annotations.Nullable()
    java.lang.Integer year, @org.jetbrains.annotations.Nullable()
    java.lang.Integer yearKey, @org.jetbrains.annotations.Nullable()
    java.lang.Double rating, @org.jetbrains.annotations.Nullable()
    java.lang.String plot, @org.jetbrains.annotations.Nullable()
    java.lang.String genre, @org.jetbrains.annotations.Nullable()
    java.lang.String director, @org.jetbrains.annotations.Nullable()
    java.lang.String cast, @org.jetbrains.annotations.Nullable()
    java.lang.String country, @org.jetbrains.annotations.Nullable()
    java.lang.String releaseDate, @org.jetbrains.annotations.Nullable()
    java.lang.String imdbId, @org.jetbrains.annotations.Nullable()
    java.lang.String tmdbId, @org.jetbrains.annotations.Nullable()
    java.lang.String trailer, @org.jetbrains.annotations.Nullable()
    java.lang.String containerExt, @org.jetbrains.annotations.Nullable()
    java.lang.Integer durationSecs, @org.jetbrains.annotations.Nullable()
    java.lang.String categoryId, @org.jetbrains.annotations.Nullable()
    java.lang.String providerKey, @org.jetbrains.annotations.Nullable()
    java.lang.String genreKey, @org.jetbrains.annotations.Nullable()
    java.lang.Long importedAt, @org.jetbrains.annotations.Nullable()
    java.lang.Long updatedAt) {
        super();
    }
    
    public final long getId() {
        return 0L;
    }
    
    public final void setId(long p0) {
    }
    
    public final int getVodId() {
        return 0;
    }
    
    public final void setVodId(int p0) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getNameLower() {
        return null;
    }
    
    public final void setNameLower(@org.jetbrains.annotations.NotNull()
    java.lang.String p0) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getSortTitleLower() {
        return null;
    }
    
    public final void setSortTitleLower(@org.jetbrains.annotations.NotNull()
    java.lang.String p0) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getName() {
        return null;
    }
    
    public final void setName(@org.jetbrains.annotations.NotNull()
    java.lang.String p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getPoster() {
        return null;
    }
    
    public final void setPoster(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getImagesJson() {
        return null;
    }
    
    public final void setImagesJson(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer getYear() {
        return null;
    }
    
    public final void setYear(@org.jetbrains.annotations.Nullable()
    java.lang.Integer p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer getYearKey() {
        return null;
    }
    
    public final void setYearKey(@org.jetbrains.annotations.Nullable()
    java.lang.Integer p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Double getRating() {
        return null;
    }
    
    public final void setRating(@org.jetbrains.annotations.Nullable()
    java.lang.Double p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getPlot() {
        return null;
    }
    
    public final void setPlot(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getGenre() {
        return null;
    }
    
    public final void setGenre(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getDirector() {
        return null;
    }
    
    public final void setDirector(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getCast() {
        return null;
    }
    
    public final void setCast(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getCountry() {
        return null;
    }
    
    public final void setCountry(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getReleaseDate() {
        return null;
    }
    
    public final void setReleaseDate(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getImdbId() {
        return null;
    }
    
    public final void setImdbId(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getTmdbId() {
        return null;
    }
    
    public final void setTmdbId(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getTrailer() {
        return null;
    }
    
    public final void setTrailer(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getContainerExt() {
        return null;
    }
    
    public final void setContainerExt(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Integer getDurationSecs() {
        return null;
    }
    
    public final void setDurationSecs(@org.jetbrains.annotations.Nullable()
    java.lang.Integer p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getCategoryId() {
        return null;
    }
    
    public final void setCategoryId(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getProviderKey() {
        return null;
    }
    
    public final void setProviderKey(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getGenreKey() {
        return null;
    }
    
    public final void setGenreKey(@org.jetbrains.annotations.Nullable()
    java.lang.String p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Long getImportedAt() {
        return null;
    }
    
    public final void setImportedAt(@org.jetbrains.annotations.Nullable()
    java.lang.Long p0) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Long getUpdatedAt() {
        return null;
    }
    
    public final void setUpdatedAt(@org.jetbrains.annotations.Nullable()
    java.lang.Long p0) {
    }
    
    public ObxVod() {
        super();
    }
}