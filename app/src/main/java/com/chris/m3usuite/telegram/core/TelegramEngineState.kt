package com.chris.m3usuite.telegram.core

/**
 * Unified Telegram Engine State Model (Task 1)
 *
 * This data model represents the complete state of the Telegram integration,
 * serving as the single source of truth for both the Settings screen and
 * Telegram playback.
 *
 * Key Properties:
 * - isEnabled: User setting (persisted) - only changed by explicit user action
 * - authState: TDLib authorization state (from T_TelegramSession)
 * - isEngineHealthy: Internal engine health (startup/runtime errors)
 * - canStream: Derived - true only when all conditions are met for streaming
 *
 * Design Principles:
 * - isEnabled never flips implicitly due to engine failures
 * - Engine errors only affect isEngineHealthy, not isEnabled
 * - canStream is the gate for playback operations
 * - Settings UI binds to isEnabled, shows engine/auth status separately
 */
data class TelegramEngineState(
    /**
     * User's explicit preference for Telegram integration.
     * Only changed by user actions in Settings screen.
     * Persisted across app restarts.
     *
     * This value MUST NOT be changed by engine failures.
     */
    val isEnabled: Boolean = false,

    /**
     * TDLib authorization state.
     * Reflects whether the user is logged in and the session is ready.
     */
    val authState: TelegramAuthState = TelegramAuthState.Idle,

    /**
     * Internal engine health indicator.
     * Set to false when:
     * - TDLib fails to start
     * - Connection errors occur
     * - Runtime exceptions in core components
     *
     * Set to true when:
     * - Engine starts successfully
     * - Auth flow completes
     * - Runtime operations succeed
     *
     * This flag is independent of isEnabled.
     */
    val isEngineHealthy: Boolean = true,

    /**
     * Recent error message for display in Settings.
     * Cleared when operations succeed.
     */
    val recentError: String? = null,
) {
    /**
     * Derived property: Can the engine stream content?
     *
     * This is the single source of truth for:
     * - Telegram playback path (InternalPlayer + playback use case)
     * - Settings screen status indicator
     *
     * Streaming is only allowed when ALL conditions are met:
     * 1. User has enabled Telegram integration (isEnabled)
     * 2. TDLib is authorized and ready (authState == Ready)
     * 3. Engine is healthy (isEngineHealthy)
     */
    val canStream: Boolean
        get() = isEnabled && authState == TelegramAuthState.Ready && isEngineHealthy

    /**
     * Human-readable status for Settings UI.
     */
    fun getStatusText(): String =
        when {
            !isEnabled -> "Deaktiviert"
            !isEngineHealthy && recentError != null -> "Fehler: $recentError"
            authState != TelegramAuthState.Ready -> getAuthStateText()
            canStream -> "Bereit"
            else -> "Nicht bereit"
        }

    private fun getAuthStateText(): String =
        when (authState) {
            TelegramAuthState.Idle -> "Getrennt"
            TelegramAuthState.Connecting -> "Verbinde..."
            TelegramAuthState.WaitingForPhone -> "Telefonnummer erforderlich"
            TelegramAuthState.WaitingForCode -> "Code erforderlich"
            TelegramAuthState.WaitingForPassword -> "Passwort erforderlich"
            TelegramAuthState.Ready -> "Bereit"
            is TelegramAuthState.Error -> "Fehler: ${(authState as TelegramAuthState.Error).message}"
        }
}
