package com.chris.m3usuite.data.obx

import android.content.Context
import io.objectbox.BoxStore
import java.util.concurrent.atomic.AtomicReference

object ObxStore {
    private val ref = AtomicReference<BoxStore?>()

    fun get(context: Context): BoxStore {
        val cur = ref.get()
        if (cur != null) return cur
        synchronized(this) {
            val again = ref.get()
            if (again != null) return again
            val box = MyObjectBox.builder().androidContext(context.applicationContext).build()
            ref.set(box)
            return box
        }
    }
}

