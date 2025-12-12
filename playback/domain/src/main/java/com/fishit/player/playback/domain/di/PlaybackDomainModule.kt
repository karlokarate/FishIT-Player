package com.fishit.player.playback.domain.di

import com.fishit.player.playback.domain.KidsPlaybackGate
import com.fishit.player.playback.domain.LivePlaybackController
import com.fishit.player.playback.domain.PlaybackSourceFactory
import com.fishit.player.playback.domain.ResumeManager
import com.fishit.player.playback.domain.SubtitleSelectionPolicy
import com.fishit.player.playback.domain.SubtitleStyleManager
import com.fishit.player.playback.domain.TvInputController
import com.fishit.player.playback.domain.defaults.DefaultKidsPlaybackGate
import com.fishit.player.playback.domain.defaults.DefaultLivePlaybackController
import com.fishit.player.playback.domain.defaults.DefaultResumeManager
import com.fishit.player.playback.domain.defaults.DefaultSubtitleSelectionPolicy
import com.fishit.player.playback.domain.defaults.DefaultSubtitleStyleManager
import com.fishit.player.playback.domain.defaults.DefaultTvInputController
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
 * **PlaybackSourceFactory Set:**
 * Declares an empty set for PlaybackSourceFactory contributions.
 * Source-specific modules (TelegramPlaybackModule, XtreamPlaybackModule) can
 * contribute factories via @Binds @IntoSet when their transports are available.
 * When no factories are contributed, PlaybackSourceResolver uses its fallback
 * (Big Buck Bunny test stream).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackDomainModule {

    /**
     * Declares an empty set for PlaybackSourceFactory contributions.
     *
     * Modules like TelegramPlaybackModule and XtreamPlaybackModule can contribute
     * their factories via @Binds @IntoSet. When no modules contribute, the set
     * is empty (but not null), and PlaybackSourceResolver uses its fallback.
     *
     * **Current state:**
     * - TelegramPlaybackModule: disabled (no TdlibClientProvider)
     * - XtreamPlaybackModule: can be enabled when needed
     * - Result: empty set → fallback to test stream
     */
    @Multibinds
    abstract fun bindPlaybackSourceFactories(): Set<PlaybackSourceFactory>

    companion object {

        @Provides
        @Singleton
        fun provideResumeManager(): ResumeManager = DefaultResumeManager()

        @Provides
        @Singleton
        fun provideKidsPlaybackGate(): KidsPlaybackGate = DefaultKidsPlaybackGate()

        @Provides
        @Singleton
        fun provideSubtitleStyleManager(): SubtitleStyleManager = DefaultSubtitleStyleManager()

        @Provides
        @Singleton
        fun provideSubtitleSelectionPolicy(): SubtitleSelectionPolicy = DefaultSubtitleSelectionPolicy()

        @Provides
        @Singleton
        fun provideLivePlaybackController(): LivePlaybackController = DefaultLivePlaybackController()

        @Provides
        @Singleton
        fun provideTvInputController(): TvInputController = DefaultTvInputController()
    }
}
