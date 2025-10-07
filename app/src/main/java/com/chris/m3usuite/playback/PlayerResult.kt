package com.chris.m3usuite.playback

sealed interface PlayerResult {
    data class Completed(val durationMs: Long) : PlayerResult
    data class Stopped(val positionMs: Long) : PlayerResult
    data class Error(val message: String, val cause: Throwable? = null) : PlayerResult
}

