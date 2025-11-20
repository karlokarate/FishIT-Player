package com.chris.m3usuite.player

import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TV-optimized key event debouncer for Fire TV / Android TV remotes.
 *
 * Problem: Fire TV remotes can generate rapid, unstoppable seek/scrubbing events that
 * cause the player to enter an endless scrubbing state in both directions.
 *
 * Solution: Debounce key events with configurable delays and rate limiting.
 *
 * Usage:
 *   val debouncer = TvKeyDebouncer(scope, debounceMs = 300)
 *
 *   view.setOnKeyListener { _, keyCode, event ->
 *       debouncer.handleKeyEvent(keyCode, event) { code, isDown ->
 *           when (code) {
 *               KeyEvent.KEYCODE_DPAD_LEFT -> handleSeekBackward()
 *               KeyEvent.KEYCODE_DPAD_RIGHT -> handleSeekForward()
 *           }
 *           true
 *       }
 *   }
 */
class TvKeyDebouncer(
    private val scope: CoroutineScope,
    private val debounceMs: Long = 300L,
    private val enableDebouncing: Boolean = true,
) {
    private val lastKeyTime = mutableMapOf<Int, Long>()
    private val keyJobs = mutableMapOf<Int, Job>()

    /**
     * Handle a key event with debouncing.
     *
     * @param keyCode The key code from KeyEvent
     * @param event The KeyEvent
     * @param handler Lambda that processes the debounced event. Returns true if handled.
     * @return True if the event was handled (or debounced), false otherwise
     */
    fun handleKeyEvent(
        keyCode: Int,
        event: KeyEvent,
        handler: (keyCode: Int, isDown: Boolean) -> Boolean,
    ): Boolean {
        if (!enableDebouncing) {
            return handler(keyCode, event.action == KeyEvent.ACTION_DOWN)
        }

        val isDown = event.action == KeyEvent.ACTION_DOWN
        val currentTime = System.currentTimeMillis()

        // Only process ACTION_DOWN events to avoid duplicate processing
        if (!isDown) {
            return true
        }

        // Check if this key was pressed recently
        val lastTime = lastKeyTime[keyCode] ?: 0L
        val timeSinceLastPress = currentTime - lastTime

        // If key was pressed too recently, ignore (debounce)
        if (timeSinceLastPress < debounceMs) {
            // Cancel any pending job for this key
            keyJobs[keyCode]?.cancel()

            // Schedule a new job to handle the event after debounce period
            val job =
                scope.launch {
                    delay(debounceMs - timeSinceLastPress)
                    lastKeyTime[keyCode] = System.currentTimeMillis()
                    handler(keyCode, true)
                    keyJobs.remove(keyCode)
                }
            keyJobs[keyCode] = job

            return true
        }

        // Update last key time
        lastKeyTime[keyCode] = currentTime

        // Cancel any pending job for this key
        keyJobs[keyCode]?.cancel()
        keyJobs.remove(keyCode)

        // Handle the event immediately
        return handler(keyCode, true)
    }

    /**
     * Handle a key event with rate limiting (simpler alternative).
     * Only allows one event per key per debounceMs period.
     */
    fun handleKeyEventRateLimited(
        keyCode: Int,
        event: KeyEvent,
        handler: (keyCode: Int, isDown: Boolean) -> Boolean,
    ): Boolean {
        val isDown = event.action == KeyEvent.ACTION_DOWN
        val currentTime = System.currentTimeMillis()

        // Only process ACTION_DOWN events
        if (!isDown) {
            return true
        }

        // Check if this key was pressed recently
        val lastTime = lastKeyTime[keyCode] ?: 0L
        val timeSinceLastPress = currentTime - lastTime

        // If key was pressed too recently, ignore (rate limit)
        if (timeSinceLastPress < debounceMs) {
            return true // Consumed but not processed
        }

        // Update last key time
        lastKeyTime[keyCode] = currentTime

        // Handle the event
        return handler(keyCode, true)
    }

    /**
     * Reset debouncing state for a specific key.
     */
    fun resetKey(keyCode: Int) {
        lastKeyTime.remove(keyCode)
        keyJobs[keyCode]?.cancel()
        keyJobs.remove(keyCode)
    }

    /**
     * Reset all debouncing state.
     */
    fun resetAll() {
        lastKeyTime.clear()
        keyJobs.values.forEach { it.cancel() }
        keyJobs.clear()
    }
}
