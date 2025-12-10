package com.fishit.player.infra.transport.telegram

/**
 * Configuration for TDLib logging forwarding.
 */
data class TelegramLoggingConfig(
    val enabled: Boolean,
    val maxVerbosity: TdLibLogVerbosity,
    val tag: String = DEFAULT_TAG,
) {
    companion object {
        const val DEFAULT_TAG: String = "tdlib"

        /** Default config with debug verbosity level. */
        fun default(): TelegramLoggingConfig = verbose()

        /** Debug builds: keep verbose for diagnostics. Release: warn/error only. */
        fun forEnvironment(isDebug: Boolean): TelegramLoggingConfig =
            if (isDebug) verbose() else quiet()

        fun verbose(): TelegramLoggingConfig = TelegramLoggingConfig(
            enabled = true,
            maxVerbosity = TdLibLogVerbosity.VERBOSE,
        )

        fun quiet(): TelegramLoggingConfig = TelegramLoggingConfig(
            enabled = true,
            maxVerbosity = TdLibLogVerbosity.WARN,
        )
    }
}

/**
 * TDLib log verbosity levels (0..5) as defined by TDLib logging API.
 */
enum class TdLibLogVerbosity(val level: Int) {
    FATAL(0),
    ERROR(1),
    WARN(2),
    INFO(3),
    DEBUG(4),
    VERBOSE(5);
}
