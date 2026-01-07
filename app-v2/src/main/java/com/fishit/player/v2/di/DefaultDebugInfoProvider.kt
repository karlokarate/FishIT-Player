package com.fishit.player.v2.di

import android.content.Context
import com.fishit.player.core.feature.auth.TelegramAuthRepository
import com.fishit.player.core.feature.auth.TelegramAuthState
import com.fishit.player.core.sourceactivation.SourceActivationStore
import com.fishit.player.core.sourceactivation.SourceId
import com.fishit.player.feature.settings.ConnectionInfo
import com.fishit.player.feature.settings.ContentCounts
import com.fishit.player.feature.settings.DebugInfoProvider
import com.fishit.player.feature.settings.TelegramCredentialStatus
import com.fishit.player.infra.cache.CacheManager
import com.fishit.player.infra.data.telegram.TelegramContentRepository
import com.fishit.player.infra.data.xtream.XtreamCatalogRepository
import com.fishit.player.infra.data.xtream.XtreamLiveRepository
import com.fishit.player.infra.transport.xtream.XtreamCredentialsStore
import com.fishit.player.v2.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [DebugInfoProvider].
 *
 * Provides real system information for DebugViewModel:
 * - Connection status from auth repositories
 * - Cache sizes via [CacheManager] (no direct file IO)
 * - Content counts from data repositories
 *
 * **Architecture:**
 * - Lives in app-v2 module (has access to all infra modules)
 * - Injected into DebugViewModel via Hilt
 * - Bridges feature/settings to infra layer
 * - Delegates all file IO to CacheManager (contract compliant)
 */
@Singleton
class DefaultDebugInfoProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val sourceActivationStore: SourceActivationStore,
        private val telegramAuthRepository: TelegramAuthRepository,
        private val xtreamCredentialsStore: XtreamCredentialsStore,
        private val telegramContentRepository: TelegramContentRepository,
        private val xtreamCatalogRepository: XtreamCatalogRepository,
        private val xtreamLiveRepository: XtreamLiveRepository,
        private val cacheManager: CacheManager,
    ) : DebugInfoProvider {
        companion object {
            private const val TAG = "DefaultDebugInfoProvider"
        }

        // =========================================================================
        // Connection Status
        // =========================================================================

        override fun observeTelegramConnection(): Flow<ConnectionInfo> =
            telegramAuthRepository.authState.map { state ->
                when (state) {
                    is TelegramAuthState.Connected ->
                        ConnectionInfo(
                            isConnected = true,
                            details = "Authorized",
                        )
                    is TelegramAuthState.WaitingForPhone,
                    is TelegramAuthState.WaitingForCode,
                    is TelegramAuthState.WaitingForPassword,
                    ->
                        ConnectionInfo(
                            isConnected = false,
                            details = "Auth in progress...",
                        )
                    is TelegramAuthState.Error ->
                        ConnectionInfo(
                            isConnected = false,
                            details = "Error: ${state.message}",
                        )
                    else ->
                        ConnectionInfo(
                            isConnected = false,
                            details = null,
                        )
                }
            }

        override fun observeXtreamConnection(): Flow<ConnectionInfo> =
            sourceActivationStore.observeStates().map { snapshot ->
                val isActive = SourceId.XTREAM in snapshot.activeSources
                val storedConfig = runCatching { xtreamCredentialsStore.read() }.getOrNull()

                ConnectionInfo(
                    isConnected = isActive,
                    details =
                        if (isActive && storedConfig != null) {
                            storedConfig.host
                        } else {
                            null
                        },
                )
            }

        // =========================================================================
        // Cache Sizes - Delegated to CacheManager (no direct file IO)
        // =========================================================================

        override suspend fun getTelegramCacheSize(): Long? = cacheManager.getTelegramCacheSizeBytes()

        override suspend fun getImageCacheSize(): Long? = cacheManager.getImageCacheSizeBytes()

        override suspend fun getDatabaseSize(): Long? = cacheManager.getDatabaseSizeBytes()

        // =========================================================================
        // Content Counts
        // =========================================================================

        override fun observeContentCounts(): Flow<ContentCounts> =
            combine(
                // Telegram media count
                telegramContentRepository.observeAll().map { it.size },
                // Xtream VOD count
                xtreamCatalogRepository.observeVod().map { it.size },
                // Xtream series count
                xtreamCatalogRepository.observeSeries().map { it.size },
                // Xtream live count
                xtreamLiveRepository.observeChannels().map { it.size },
            ) { telegramCount, vodCount, seriesCount, liveCount ->
                ContentCounts(
                    telegramMediaCount = telegramCount,
                    xtreamVodCount = vodCount,
                    xtreamSeriesCount = seriesCount,
                    xtreamLiveCount = liveCount,
                )
            }

        // =========================================================================
        // API Credential Status
        // =========================================================================

        override fun getTelegramCredentialStatus(): TelegramCredentialStatus {
            val apiId = BuildConfig.TG_API_ID
            val apiHash = BuildConfig.TG_API_HASH

            val isConfigured = apiId != 0 && apiHash.isNotBlank()

            val statusMessage =
                when {
                    isConfigured -> "Configured (API ID: $apiId)"
                    apiId == 0 && apiHash.isBlank() -> "Missing (TG_API_ID and TG_API_HASH not set)"
                    apiId == 0 -> "Missing (TG_API_ID not set)"
                    apiHash.isBlank() -> "Missing (TG_API_HASH not set)"
                    else -> "Unknown status"
                }

            return TelegramCredentialStatus(
                isConfigured = isConfigured,
                statusMessage = statusMessage,
            )
        }

        override fun isTmdbApiKeyConfigured(): Boolean = BuildConfig.TMDB_API_KEY.isNotBlank()

        // =========================================================================
        // Cache Actions - Delegated to CacheManager (no direct file IO)
        // =========================================================================

        override suspend fun clearTelegramCache(): Boolean = cacheManager.clearTelegramCache()

        override suspend fun clearImageCache(): Boolean = cacheManager.clearImageCache()
    }
