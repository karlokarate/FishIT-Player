package com.chris.m3usuite.telegram.domain

import android.content.Context
import com.chris.m3usuite.data.repo.SettingsRepository
import com.chris.m3usuite.prefs.SettingsStore

/**
 * Provides a lazily initialized singleton instance of [TelegramStreamingSettingsProvider].
 *
 * Many components (service client, file loader, prefetcher) need access to the runtime streaming
 * settings. This holder ensures we only spin up a single provider that combines DataStore flows,
 * preventing duplicated collectors and redundant work.
 */
object TelegramStreamingSettingsProviderHolder {
    @Volatile private var provider: TelegramStreamingSettingsProvider? = null

    fun get(context: Context): TelegramStreamingSettingsProvider =
            provider
                    ?: synchronized(this) {
                        provider
                                ?: TelegramStreamingSettingsProvider(
                                                settingsRepository =
                                                        SettingsRepository(
                                                                SettingsStore(
                                                                        context.applicationContext
                                                                )
                                                        ),
                                        )
                                        .also { provider = it }
                    }
}
