package com.fishit.player.infra.data.nx.di

import com.fishit.player.core.detail.domain.NxDetailMediaRepository
import com.fishit.player.core.detail.domain.XtreamSeriesIndexRepository
import com.fishit.player.core.home.domain.HomeContentRepository
import com.fishit.player.core.library.domain.LibraryContentRepository
import com.fishit.player.core.live.domain.LiveContentRepository
import com.fishit.player.core.model.repository.CanonicalMediaRepository
import com.fishit.player.core.model.repository.NxCloudOutboxRepository
import com.fishit.player.core.model.repository.NxEpgRepository
import com.fishit.player.core.model.repository.NxIngestLedgerRepository
import com.fishit.player.core.model.repository.NxProfileRepository
import com.fishit.player.core.model.repository.NxProfileRuleRepository
import com.fishit.player.core.model.repository.NxProfileUsageRepository
import com.fishit.player.core.model.repository.NxCategorySelectionRepository
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
import com.fishit.player.infra.data.nx.repository.NxCategorySelectionRepositoryImpl
import com.fishit.player.infra.data.nx.repository.NxSourceAccountRepositoryImpl
import com.fishit.player.infra.data.nx.repository.NxWorkAuthorityRepositoryImpl
import com.fishit.player.infra.data.nx.repository.NxWorkDiagnosticsImpl
import com.fishit.player.infra.data.nx.repository.NxWorkEmbeddingRepositoryImpl
import com.fishit.player.infra.data.nx.detail.repository.NxDetailMediaRepositoryImpl
import com.fishit.player.infra.data.nx.canonical.NxCanonicalMediaRepositoryImpl
import com.fishit.player.infra.data.nx.repository.NxEpgRepositoryImpl
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
import com.fishit.player.infra.data.nx.xtream.NxXtreamCatalogRepositoryImpl
import com.fishit.player.infra.data.nx.xtream.NxXtreamLiveRepositoryImpl
import com.fishit.player.infra.data.nx.xtream.NxXtreamSeriesIndexRepository
import com.fishit.player.infra.data.xtream.XtreamCatalogRepository
import com.fishit.player.infra.data.xtream.XtreamLiveRepository
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

    @Binds
    @Singleton
    abstract fun bindNxCategorySelectionRepository(
        impl: NxCategorySelectionRepositoryImpl,
    ): NxCategorySelectionRepository

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
    // Priority 3: EPG (Live TV Program Guide)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Binds the NX-based EPG Repository implementation.
     *
     * This replaces the legacy ObxEpgNowNext entity with a full program schedule.
     * NX_EpgEntry stores complete EPG data linked to NX_Work channels via channelWorkKey.
     */
    @Binds
    @Singleton
    abstract fun bindNxEpgRepository(
        impl: NxEpgRepositoryImpl,
    ): NxEpgRepository

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
     * This is the ONLY implementation for TelegramMediaRepository.
     * Legacy TelegramMediaRepositoryAdapter has been removed per AUDIT_LEGACY_WILDWUCHS_2026.md.
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

    /**
     * Binds the NX-based XtreamCatalogRepository implementation.
     *
     * This replaces the legacy ObxXtreamCatalogRepository from infra:data-xtream.
     * The XtreamDataModule binding MUST be removed to avoid duplicate bindings.
     *
     * ## Architecture Note
     * The NX implementation reads from:
     * - NX_Work (workType=MOVIE/SERIES/EPISODE) for media entries
     * - NX_WorkSourceRef (sourceType=XTREAM) for source mapping
     *
     * Write operations delegate to NxCatalogWriter via CatalogSyncService.
     */
    @Binds
    @Singleton
    abstract fun bindXtreamCatalogRepository(
        impl: NxXtreamCatalogRepositoryImpl,
    ): XtreamCatalogRepository

    /**
     * Binds the NX-based XtreamLiveRepository implementation.
     *
     * This replaces the legacy ObxXtreamLiveRepository from infra:data-xtream.
     * The XtreamDataModule binding MUST be removed to avoid duplicate bindings.
     *
     * ## Architecture Note
     * The NX implementation reads from:
     * - NX_Work (workType=LIVE_CHANNEL) for live channels
     * - NX_WorkSourceRef (sourceType=XTREAM) for source mapping
     *
     * Write operations delegate to NxCatalogWriter via CatalogSyncService.
     */
    @Binds
    @Singleton
    abstract fun bindXtreamLiveRepository(
        impl: NxXtreamLiveRepositoryImpl,
    ): XtreamLiveRepository

    // ────────────────────────────────────────────────────────────────────
    // Canonical Media Repository (NX-based implementation)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Binds the NX-based CanonicalMediaRepository implementation.
     *
     * ⚠️ MIGRATION NOTE: This replaces the legacy ObxCanonicalMediaRepository
     * from infra:data-obx. The PersistenceModule binding MUST be removed
     * to avoid duplicate Hilt bindings.
     *
     * ## Architecture Note
     * The NX implementation maps CanonicalMediaRepository operations to:
     * - NX_Work → CanonicalMedia / NormalizedMediaMetadata
     * - NX_WorkSourceRef → MediaSourceRef
     * - NX_WorkUserState → CanonicalResumeInfo
     *
     * ## INV-6 Compliance
     * This is the SSOT for canonical media across the entire app.
     * All layers MUST use this interface for media operations.
     */
    @Binds
    @Singleton
    abstract fun bindCanonicalMediaRepository(
        impl: NxCanonicalMediaRepositoryImpl,
    ): CanonicalMediaRepository

    // ────────────────────────────────────────────────────────────────────
    // Detail Repository (NX-based implementation)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Binds the NX-based NxDetailMediaRepository implementation.
     *
     * This provides the detail screen data from NX_* entities:
     * - NX_Work → media metadata
     * - NX_WorkSourceRef + NX_WorkVariant → playback sources
     * - NX_WorkUserState → resume/favorites
     *
     * ## INV-6 Compliance
     * This is the SSOT for detail screen data. Feature:detail MUST use
     * this interface instead of legacy CanonicalMediaRepository.
     */
    @Binds
    @Singleton
    abstract fun bindNxDetailMediaRepository(
        impl: NxDetailMediaRepositoryImpl,
    ): NxDetailMediaRepository
}
