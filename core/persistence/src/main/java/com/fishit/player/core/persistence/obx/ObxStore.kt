package com.fishit.player.core.persistence.obx

import android.content.Context
import io.objectbox.BoxStore
import java.util.concurrent.atomic.AtomicReference

/**
 * Singleton ObjectBox store for FishIT Player v2.
 * 
 * This class provides lazy initialization of the BoxStore instance.
 * The actual DI wiring is done via Hilt in [PersistenceModule].
 */
object ObxStore {
    private val ref = AtomicReference<BoxStore?>()

    /**
     * Get or create the BoxStore instance.
     * Thread-safe lazy initialization with double-check locking.
     * 
     * @param context Android application context
     * @return The initialized BoxStore instance
     */
    fun get(context: Context): BoxStore {
        val cur = ref.get()
        if (cur != null) return cur
        synchronized(this) {
            val again = ref.get()
            if (again != null) return again
            val box = MyObjectBox.builder()
                .androidContext(context.applicationContext)
                .build()
            ref.set(box)
            return box
        }
    }

    /**
     * Close the BoxStore instance. Used for testing.
     */
    internal fun close() {
        ref.get()?.close()
        ref.set(null)
    }
}
