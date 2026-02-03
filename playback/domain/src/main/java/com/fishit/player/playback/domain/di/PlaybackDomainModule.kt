package com.fishit.player.playback.domain.di

import com.fishit.player.core.model.repository.NxEpgRepository
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkUserStateRepository
import com.fishit.player.playback.domain.KidsPlaybackGate
import com.fishit.player.playback.domain.LivePlaybackController
import com.fishit.player.playback.domain.PlaybackSourceFactory
import com.fishit.player.playback.domain.ResumeManager
import com.fishit.player.playback.domain.SubtitleSelectionPolicy
import com.fishit.player.playback.domain.SubtitleStyleManager
import com.fishit.player.playback.domain.TvInputController
import com.fishit.player.playback.domain.defaults.DefaultKidsPlaybackGate
import com.fishit.player.playback.domain.defaults.DefaultSubtitleSelectionPolicy
import com.fishit.player.playback.domain.defaults.DefaultSubtitleStyleManager
import com.fishit.player.playback.domain.defaults.DefaultTvInputController
import com.fishit.player.playback.domain.defaults.NxLivePlaybackController
import com.fishit.player.playback.domain.defaults.NxResumeManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

/**
 * Hilt module providing default implementations for playback domain interfaces.
 *
 * Phase 1: All implementations are stubs that don't crash but don't persist data.
 * Phase 6+: Real implementations will replace these bindings.
 *
 * ## PlaybackSourceFactory Set (MANDATORY PATTERN)
 *
 * This module declares an empty set for [PlaybackSourceFactory] contributions via [@Multibinds].
 * **ALL playback sources** must use the `@Binds @IntoSet` pattern to contribute their factory:
 *
 * | Module | Status | Notes |
 * |--------|--------|-------|
 * | `playback/domain` | ✅ Ready | Base contracts + `@Multibinds` declaration |
 * | `playback/telegram` | ⏸️ Disabled | Waiting for `transport-telegram` typed interfaces |
 * | `playback/xtream` | ✅ Ready | Can be enabled when needed |
 * | `playback/local` | 🔮 Future | For `pipeline/io` local files |
 * | `playback/audiobook` | 🔮 Future | For `pipeline/audiobook` |
 *
 * When no factories are contributed, [PlaybackSourceResolver] uses its fallback
 * (Big Buck Bunny test stream).
 *
 * **Pattern for new sources:**
 * ```kotlin
 * // playback/<source>/di/<Source>PlaybackModule.kt
 * @Module
 * @InstallIn(SingletonComponent::class)
 * abstract class <Source>PlaybackModule {
 *     @Binds @IntoSet
 *     abstract fun bind<Source>Factory(impl: <Source>PlaybackSourceFactoryImpl): PlaybackSourceFactory
 * }
 * ```
 *
 * @see PlaybackSourceFactory
 * @see AGENTS.md Section "Binding Rule for Playback Modules (ALL Sources)"
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackDomainModule {
    /**
     * Declares an empty set for [PlaybackSourceFactory] contributions.
     *
     * All playback source modules (Telegram, Xtream, Local, Audiobook, future)
     * contribute their factories via `@Binds @IntoSet`. When no modules contribute,
     * the set is empty (but not null), and [PlaybackSourceResolver] uses its fallback.
     *
     * **Current state:**
     * - TelegramPlaybackModule: disabled (waiting for transport-telegram)
     * - XtreamPlaybackModule: can be enabled when needed
     * - LocalPlaybackModule: not yet created (future)
     * - AudiobookPlaybackModule: not yet created (future)
     * - Result: empty set → fallback to Big Buck Bunny test stream
     */
    @Multibinds
    abstract fun bindPlaybackSourceFactories(): Set<PlaybackSourceFactory>

    companion object {
        /**
         * Provides NX-backed ResumeManager.
         *
         * Uses NxWorkUserStateRepository for persistence. This is the ONLY
         * ResumeManager implementation. Legacy stubs have been removed per
         * AUDIT_LEGACY_WILDWUCHS_2026.md.
         *
         * @see NxResumeManager for implementation details
         */
        @Provides
        @Singleton
        fun provideResumeManager(userStateRepository: NxWorkUserStateRepository): ResumeManager =
            NxResumeManager(userStateRepository)

        @Provides
        @Singleton
        fun provideKidsPlaybackGate(): KidsPlaybackGate = DefaultKidsPlaybackGate()

        @Provides
        @Singleton
        fun provideSubtitleStyleManager(): SubtitleStyleManager = DefaultSubtitleStyleManager()

        @Provides
        @Singleton
        fun provideSubtitleSelectionPolicy(): SubtitleSelectionPolicy = DefaultSubtitleSelectionPolicy()

        /**
         * Provides NX-backed LivePlaybackController with on-demand EPG fetching.
         *
         * EPG data is fetched only when:
         * 1. switchToChannel() is called (player opens a live channel)
         * 2. refreshEpg() is called explicitly
         *
         * This is the ONLY LivePlaybackController implementation. Legacy stub
         * (DefaultLivePlaybackController) has been removed per AUDIT_LEGACY_WILDWUCHS_2026.md.
         *
         * @see NxLivePlaybackController for implementation details
         */
        @Provides
        @Singleton
        fun provideLivePlaybackController(
            workRepository: NxWorkRepository,
            epgRepository: NxEpgRepository,
        ): LivePlaybackController = NxLivePlaybackController(workRepository, epgRepository)

        @Provides
        @Singleton
        fun provideTvInputController(): TvInputController = DefaultTvInputController()
    }
}
