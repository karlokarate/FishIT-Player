package com.fishit.player.infra.transport.telegram

import com.fishit.player.infra.transport.telegram.internal.TdLibLogInstaller
import dev.g000sha256.tdl.TdlClient

/**
 * Internal factory for creating the single process-wide TDLib client.
 *
 * Notes:
 * - Transport owns TDLib.
 * - Upper layers must never see TDLib types.
 * - Logging config is registered before creating the client.
 */
internal object TelegramTdlibClientFactory {
    fun create(
        loggingConfig: TelegramLoggingConfig = TelegramLoggingConfig.default(),
    ): TdlClient {
        TdLibLogInstaller.install(loggingConfig)
        return TdlClient.create()
    }
}
