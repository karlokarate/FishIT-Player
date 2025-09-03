package com.chris.m3usuite.ui.fx

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

object FishSpin {
    // Loading: when true, fish rotates continuously until set to false
    val isLoading = MutableStateFlow(false)
    // One-time spin trigger: emit Unit to request a single fast rotation
    val spinTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun setLoading(loading: Boolean) {
        isLoading.value = loading
    }

    fun kickOnce() {
        spinTrigger.tryEmit(Unit)
    }
}

