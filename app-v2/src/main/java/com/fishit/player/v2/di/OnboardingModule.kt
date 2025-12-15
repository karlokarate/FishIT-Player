package com.fishit.player.v2.di

import android.content.Context
import com.fishit.player.feature.onboarding.domain.TelegramAuthRepository
import com.fishit.player.feature.onboarding.domain.TelegramAuthState
import com.fishit.player.feature.onboarding.domain.XtreamAuthRepository
import com.fishit.player.feature.onboarding.domain.XtreamAuthState
import com.fishit.player.feature.onboarding.domain.XtreamConfig
import com.fishit.player.feature.onboarding.domain.XtreamConnectionState
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.telegram.TdlibClientProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.g000sha256.tdl.TdlClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Singleton

/**
 * Hilt module providing temporary stub implementations for onboarding repositories.
 *
 * TODO: Move these implementations to proper infra modules:
 *  - TelegramAuthRepository → infra:data-telegram
 *  - XtreamAuthRepository → infra:data-xtream
 *  - TdlibClientProvider → infra:transport-telegram or app-level binding
 */
@Module
@InstallIn(SingletonComponent::class)
object OnboardingModule {
    private const val TAG = "OnboardingModule"

    /**
     * Provides TdlibClientProvider for TelegramTransportModule.
     *
     * TODO: This should be provided by infra:transport-telegram or moved to a proper
     * transport-level DI module once TDLib initialization is implemented.
     */
    @Provides
    @Singleton
    fun provideTdlibClientProvider(
        @ApplicationContext context: Context,
    ): TdlibClientProvider {
        UnifiedLog.w(TAG) { "Using stub TdlibClientProvider - TDLib not fully initialized" }
        return object : TdlibClientProvider {
            override fun getClient(): TdlClient {
                // Return a fresh TdlClient instance
                // TODO: Implement proper TDLib session management
                return TdlClient.create()
            }
        }
    }

    /**
     * Provides stub TelegramAuthRepository implementation.
     *
     * TODO: Replace with real implementation in infra:data-telegram
     */
    @Provides
    @Singleton
    fun provideTelegramAuthRepository(): TelegramAuthRepository {
        UnifiedLog.w(TAG) { "Using stub TelegramAuthRepository - auth flow not implemented" }
        return object : TelegramAuthRepository {
            private val _authState = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Idle)
            override val authState: StateFlow<TelegramAuthState> = _authState

            override suspend fun ensureAuthorized() {
                _authState.value = TelegramAuthState.Error("Telegram auth not yet implemented")
            }

            override suspend fun sendPhoneNumber(phoneNumber: String) {
                _authState.value = TelegramAuthState.Error("Telegram auth not yet implemented")
            }

            override suspend fun sendCode(code: String) {
                _authState.value = TelegramAuthState.Error("Telegram auth not yet implemented")
            }

            override suspend fun sendPassword(password: String) {
                _authState.value = TelegramAuthState.Error("Telegram auth not yet implemented")
            }

            override suspend fun logout() {
                _authState.value = TelegramAuthState.Disconnected
            }
        }
    }

    /**
     * Provides stub XtreamAuthRepository implementation.
     *
     * TODO: Replace with real implementation in infra:data-xtream
     */
    @Provides
    @Singleton
    fun provideXtreamAuthRepository(): XtreamAuthRepository {
        UnifiedLog.w(TAG) { "Using stub XtreamAuthRepository - auth flow not implemented" }
        return object : XtreamAuthRepository {
            private val _connectionState =
                MutableStateFlow<XtreamConnectionState>(XtreamConnectionState.Disconnected)
            override val connectionState: StateFlow<XtreamConnectionState> = _connectionState

            private val _authState = MutableStateFlow<XtreamAuthState>(XtreamAuthState.Idle)
            override val authState: StateFlow<XtreamAuthState> = _authState

            override suspend fun initialize(config: XtreamConfig): Result<Unit> {
                return Result.failure(Exception("Xtream auth not yet implemented"))
            }

            override suspend fun close() {
                _connectionState.value = XtreamConnectionState.Disconnected
            }

            override suspend fun saveCredentials(config: XtreamConfig) {
                // No-op stub
            }

            override suspend fun clearCredentials() {
                // No-op stub
            }
        }
    }
}
