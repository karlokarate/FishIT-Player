package com.fishit.player.infra.data.nx.di

import com.fishit.player.core.detail.domain.XtreamSeriesIndexRepository
import com.fishit.player.core.home.domain.HomeContentRepository
import com.fishit.player.core.library.domain.LibraryContentRepository
import com.fishit.player.core.live.domain.LiveContentRepository
import com.fishit.player.core.model.repository.NxCloudOutboxRepository
import com.fishit.player.core.model.repository.NxIngestLedgerRepository
import com.fishit.player.core.model.repository.NxProfileRepository
import com.fishit.player.core.model.repository.NxProfileRuleRepository
import com.fishit.player.core.model.repository.NxProfileUsageRepository
import com.fishit.player.core.model.repository.NxSourceAccountRepository
import com.fishit.player.core.model.repository.NxWorkAuthorityRepository
import com.fishit.player.core.model.repository.NxWorkDiagnostics
import com.fishit.player.core.model.repository.NxWorkEmbeddingRepository
import com.fishit.player.core.model.repository.NxWorkRedirectRepository
import com.fishit.player.core.model.repository.NxWorkRelationRepository
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkRuntimeStateRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefDiagnostics
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.model.repository.NxWorkUserStateDiagnostics
import com.fishit.player.core.model.repository.NxWorkUserStateRepository
import com.fishit.player.core.model.repository.NxWorkVariantDiagnostics
import com.fishit.player.core.model.repository.NxWorkVariantRepository
import com.fishit.player.core.telegrammedia.domain.TelegramMediaRepository
import com.fishit.player.infra.data.nx.home.NxHomeContentRepositoryImpl
import com.fishit.player.infra.data.nx.library.NxLibraryContentRepositoryImpl
import com.fishit.player.infra.data.nx.live.NxLiveContentRepositoryImpl
import com.fishit.player.infra.data.nx.repository.NxCloudOutboxRepositoryImpl
import com.fishit.player.infra.data.nx.repository.NxIngestLedgerRepositoryImpl
import com.fishit.player.infra.data.nx.repository.NxProfileRepositoryImpl
import com.fishit.player.infra.data.nx.repository.NxProfileRuleRepositoryImpl
import com.fishit.player.infra.data.nx.repository.NxProfileUsageRepositoryImpl
import com.fishit.player.infra.data.nx.repository.NxSourceAccountRepositoryImpl
import com.fishit.player.infra.data.nx.repository.NxWorkAuthorityRepositoryImpl
import com.fishit.player.infra.data.nx.repository.NxWorkDiagnosticsImpl
import com.fishit.player.infra.data.nx.repository.NxWorkEmbeddingRepositoryImpl
import com.fishit.player.infra.data.nx.repository.NxWorkRedirectRepositoryImpl
import com.fishit.player.infra.data.nx.repository.NxWorkRelationRepositoryImpl
import com.fishit.player.infra.data.nx.repository.NxWorkRepositoryImpl
import com.fishit.player.infra.data.nx.repository.NxWorkRuntimeStateRepositoryImpl
import com.fishit.player.infra.data.nx.repository.NxWorkSourceRefDiagnosticsImpl
import com.fishit.player.infra.data.nx.repository.NxWorkSourceRefRepositoryImpl
import com.fishit.player.infra.data.nx.repository.NxWorkUserStateDiagnosticsImpl
import com.fishit.player.infra.data.nx.repository.NxWorkUserStateRepositoryImpl
import com.fishit.player.infra.data.nx.repository.NxWorkVariantDiagnosticsImpl
import com.fishit.player.infra.data.nx.repository.NxWorkVariantRepositoryImpl
import com.fishit.player.infra.data.nx.telegram.NxTelegramMediaRepositoryImpl
import com.fishit.player.infra.data.nx.xtream.NxXtreamSeriesIndexRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing NX data layer bindings.
 *
 * ## Provided Bindings
 * ### Priority 1 (UI SSOT)
 * - [NxWorkRepository] → [NxWorkRepositoryImpl]
 * - [NxWorkDiagnostics] → [NxWorkDiagnosticsImpl]
 * - [NxWorkUserStateRepository] → [NxWorkUserStateRepositoryImpl]
 * - [NxWorkUserStateDiagnostics] → [NxWorkUserStateDiagnosticsImpl]
 *
 * ### Priority 2 (Multi-source identity)
 * - [NxWorkSourceRefRepository] → [NxWorkSourceRefRepositoryImpl]
 * - [NxWorkSourceRefDiagnostics] → [NxWorkSourceRefDiagnosticsImpl]
 * - [NxWorkVariantRepository] → [NxWorkVariantRepositoryImpl]
 * - [NxWorkVariantDiagnostics] → [NxWorkVariantDiagnosticsImpl]
 *
 * ## Usage
 * This module is automatically installed in [SingletonComponent].
 * Inject interfaces via constructor injection:
 *
 * ```kotlin
 * class MyViewModel @Inject constructor(
 *     private val workRepo: NxWorkRepository,
 *     private val userStateRepo: NxWorkUserStateRepository,
 *     private val sourceRefRepo: NxWorkSourceRefRepository,
 *     private val variantRepo: NxWorkVariantRepository,
 * ) : ViewModel()
 * ```
 *
 * ## Architecture
 * - Interfaces live in `core/model/repository/`
 * - Implementations live in `infra/data-nx/repository/`
 * - This module wires them together
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NxDataModule {

    // ────────────────────────────────────────────────────────────────────
    // Priority 1: UI SSOT (Works)
    // ────────────────────────────────────────────────────────────────────

    @Binds
    @Singleton
    abstract fun bindNxWorkRepository(
        impl: NxWorkRepositoryImpl,
    ): NxWorkRepository

    @Binds
    @Singleton
    abstract fun bindNxWorkDiagnostics(
        impl: NxWorkDiagnosticsImpl,
    ): NxWorkDiagnostics

    // ────────────────────────────────────────────────────────────────────
    // Priority 1: UI SSOT (User State)
    // ────────────────────────────────────────────────────────────────────

    @Binds
    @Singleton
    abstract fun bindNxWorkUserStateRepository(
        impl: NxWorkUserStateRepositoryImpl,
    ): NxWorkUserStateRepository

    @Binds
    @Singleton
    abstract fun bindNxWorkUserStateDiagnostics(
        impl: NxWorkUserStateDiagnosticsImpl,
    ): NxWorkUserStateDiagnostics

    // ────────────────────────────────────────────────────────────────────
    // Priority 2: Multi-source identity
    // ────────────────────────────────────────────────────────────────────

    @Binds
    @Singleton
    abstract fun bindNxWorkSourceRefRepository(
        impl: NxWorkSourceRefRepositoryImpl,
    ): NxWorkSourceRefRepository

    @Binds
    @Singleton
    abstract fun bindNxWorkSourceRefDiagnostics(
        impl: NxWorkSourceRefDiagnosticsImpl,
    ): NxWorkSourceRefDiagnostics

    // ────────────────────────────────────────────────────────────────────
    // Priority 2: Playback variants
    // ────────────────────────────────────────────────────────────────────

    @Binds
    @Singleton
    abstract fun bindNxWorkVariantRepository(
        impl: NxWorkVariantRepositoryImpl,
    ): NxWorkVariantRepository

    @Binds
    @Singleton
    abstract fun bindNxWorkVariantDiagnostics(
        impl: NxWorkVariantDiagnosticsImpl,
    ): NxWorkVariantDiagnostics

    // ────────────────────────────────────────────────────────────────────
    // Priority 3: Relationships and Navigation
    // ────────────────────────────────────────────────────────────────────

    @Binds
    @Singleton
    abstract fun bindNxWorkRelationRepository(
        impl: NxWorkRelationRepositoryImpl,
    ): NxWorkRelationRepository

    @Binds
    @Singleton
    abstract fun bindNxWorkRuntimeStateRepository(
        impl: NxWorkRuntimeStateRepositoryImpl,
    ): NxWorkRuntimeStateRepository

    // ────────────────────────────────────────────────────────────────────
    // Priority 3: Audit & Ingest
    // ────────────────────────────────────────────────────────────────────

    @Binds
    @Singleton
    abstract fun bindNxIngestLedgerRepository(
        impl: NxIngestLedgerRepositoryImpl,
    ): NxIngestLedgerRepository

    // ────────────────────────────────────────────────────────────────────
    // Priority 3: Profile System
    // ────────────────────────────────────────────────────────────────────

    @Binds
    @Singleton
    abstract fun bindNxProfileRepository(
        impl: NxProfileRepositoryImpl,
    ): NxProfileRepository

    @Binds
    @Singleton
    abstract fun bindNxProfileRuleRepository(
        impl: NxProfileRuleRepositoryImpl,
    ): NxProfileRuleRepository

    @Binds
    @Singleton
    abstract fun bindNxProfileUsageRepository(
        impl: NxProfileUsageRepositoryImpl,
    ): NxProfileUsageRepository

    // ────────────────────────────────────────────────────────────────────
    // Priority 3: Source Management
    // ────────────────────────────────────────────────────────────────────

    @Binds
    @Singleton
    abstract fun bindNxSourceAccountRepository(
        impl: NxSourceAccountRepositoryImpl,
    ): NxSourceAccountRepository

    // ────────────────────────────────────────────────────────────────────
    // Priority 3: Cloud & Sync
    // ────────────────────────────────────────────────────────────────────

    @Binds
    @Singleton
    abstract fun bindNxCloudOutboxRepository(
        impl: NxCloudOutboxRepositoryImpl,
    ): NxCloudOutboxRepository

    // ────────────────────────────────────────────────────────────────────
    // Priority 3: Advanced Features
    // ────────────────────────────────────────────────────────────────────

    @Binds
    @Singleton
    abstract fun bindNxWorkEmbeddingRepository(
        impl: NxWorkEmbeddingRepositoryImpl,
    ): NxWorkEmbeddingRepository

    @Binds
    @Singleton
    abstract fun bindNxWorkRedirectRepository(
        impl: NxWorkRedirectRepositoryImpl,
    ): NxWorkRedirectRepository

    @Binds
    @Singleton
    abstract fun bindNxWorkAuthorityRepository(
        impl: NxWorkAuthorityRepositoryImpl,
    ): NxWorkAuthorityRepository

    // ────────────────────────────────────────────────────────────────────
    // Feature Repositories (NX-based implementations)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Binds the NX-based HomeContentRepository implementation.
     *
     * This replaces the legacy HomeContentRepositoryAdapter from infra:data-home.
     * The HomeDataModule binding MUST be removed to avoid duplicate bindings.
     */
    @Binds
    @Singleton
    abstract fun bindHomeContentRepository(
        impl: NxHomeContentRepositoryImpl,
    ): HomeContentRepository

    /**
     * Binds the NX-based LibraryContentRepository implementation.
     *
     * This replaces the legacy LibraryContentRepositoryAdapter from infra:data-xtream.
     * The XtreamDataModule binding MUST be removed to avoid duplicate bindings.
     */
    @Binds
    @Singleton
    abstract fun bindLibraryContentRepository(
        impl: NxLibraryContentRepositoryImpl,
    ): LibraryContentRepository

    /**
     * Binds the NX-based LiveContentRepository implementation.
     *
     * This replaces the legacy LiveContentRepositoryAdapter from infra:data-xtream.
     * The XtreamDataModule binding MUST be removed to avoid duplicate bindings.
     */
    @Binds
    @Singleton
    abstract fun bindLiveContentRepository(
        impl: NxLiveContentRepositoryImpl,
    ): LiveContentRepository

    /**
     * Binds the NX-based TelegramMediaRepository implementation.
     *
     * This replaces the legacy TelegramMediaRepositoryAdapter from infra:data-telegram.
     * The TelegramDataModule binding MUST be removed to avoid duplicate bindings.
     */
    @Binds
    @Singleton
    abstract fun bindTelegramMediaRepository(
        impl: NxTelegramMediaRepositoryImpl,
    ): TelegramMediaRepository

    // ────────────────────────────────────────────────────────────────────
    // Xtream Repositories (NX-based implementations)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Binds the NX-based XtreamSeriesIndexRepository implementation.
     *
     * This replaces the legacy ObxXtreamSeriesIndexRepository from infra:data-xtream.
     * The XtreamDataModule binding MUST be removed to avoid duplicate bindings.
     *
     * ## Architecture Note
     * Unlike legacy which stored seasons/episodes in ObxSeasonIndex/ObxEpisodeIndex,
     * the NX implementation uses:
     * - NX_Work (workType=EPISODE) for episodes
     * - NX_WorkRelation (relationType=SERIES_EPISODE) for series↔episode links
     * - NX_WorkSourceRef to map seriesId to workKey
     *
     * This aligns with the NX SSOT architecture where all media (including episodes)
     * are stored as NX_Work entries.
     */
    @Binds
    @Singleton
    abstract fun bindXtreamSeriesIndexRepository(
        impl: NxXtreamSeriesIndexRepository,
    ): XtreamSeriesIndexRepository
}
