package com.chris.m3usuite.ui.home

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Global snackbar event manager for app-wide notifications.
 * Used to display important messages like Telegram reauth requirements.
 */
object GlobalSnackbarEvent {
    private val _events = MutableSharedFlow<SnackbarMessage>(replay = 0, extraBufferCapacity = 10)
    val events: SharedFlow<SnackbarMessage> = _events.asSharedFlow()

    /**
     * Emit a snackbar message to be displayed in the UI.
     */
    suspend fun show(message: String) {
        _events.emit(SnackbarMessage(message))
    }
}

data class SnackbarMessage(
    val message: String,
)
