package com.fishit.player.core.persistence.obx;

/**
 * ObxStore provides a singleton BoxStore instance for v2.
 * Adapted from v1: app/src/main/java/com/chris/m3usuite/data/obx/ObxStore.kt
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0006\u001a\u00020\u00052\u0006\u0010\u0007\u001a\u00020\bR\u0016\u0010\u0003\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00050\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\t"}, d2 = {"Lcom/fishit/player/core/persistence/obx/ObxStore;", "", "()V", "ref", "Ljava/util/concurrent/atomic/AtomicReference;", "Lio/objectbox/BoxStore;", "get", "context", "Landroid/content/Context;", "persistence_debug"})
public final class ObxStore {
    @org.jetbrains.annotations.NotNull()
    private static final java.util.concurrent.atomic.AtomicReference<io.objectbox.BoxStore> ref = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.fishit.player.core.persistence.obx.ObxStore INSTANCE = null;
    
    private ObxStore() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final io.objectbox.BoxStore get(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return null;
    }
}