package com.fishit.player.infra.transport.telegram.internal

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TelegramLoggingConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Installs TDLib logging configuration once per process.
 *
 * Note: The actual log verbosity level is set via TdlClient.setLogVerbosityLevel()
 * which must be called after the client is created. This installer only tracks
 * whether logging setup has been requested.
 *
 * The g00sha256 tdl-coroutines library handles TDLib log routing internally.
 */
internal object TdLibLogInstaller {
    private val installed = AtomicBoolean(false)
    private var currentConfig: TelegramLoggingConfig? = null

    fun install(config: TelegramLoggingConfig) {
        if (!config.enabled) return
        if (!installed.compareAndSet(false, true)) return

        currentConfig = config
        UnifiedLog.d(config.tag, "TDLib logging config registered (maxVerbosity=${config.maxVerbosity})")
    }

    /**
     * Get the configured verbosity level for applying to TdlClient.
     * Returns null if logging is not configured.
     */
    fun getVerbosityLevel(): Int? = currentConfig?.maxVerbosity?.level

    /**
     * Check if logging is installed.
     */
    fun isInstalled(): Boolean = installed.get()
}
