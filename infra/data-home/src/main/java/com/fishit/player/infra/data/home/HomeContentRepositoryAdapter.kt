package com.fishit.player.infra.data.home

import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.feature.home.domain.HomeContentRepository
import com.fishit.player.feature.home.domain.HomeMediaItem
import com.fishit.player.infra.data.telegram.TelegramContentRepository
import com.fishit.player.infra.data.xtream.XtreamCatalogRepository
import com.fishit.player.infra.data.xtream.XtreamLiveRepository
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter that implements the Home feature's HomeContentRepository interface.
 *
 * **Architecture (Dependency Inversion):**
 * - Feature layer (feature:home) defines HomeContentRepository interface
 * - Data layer (infra:data-home) provides this implementation
 * - Feature depends on interface (not on infra module)
 * - Infra depends on feature (to implement interface)
 *
 * **Responsibility:**
 * - Aggregates multiple data repositories (Telegram, Xtream VOD/Live/Series)
 * - Maps RawMediaMetadata → HomeMediaItem (feature-domain model)
 * - Shields Home feature from direct data layer dependencies
 * - Provides error handling and empty fallbacks for missing sources
 *
 * **Design:**
 * This is a composite adapter that coordinates multiple underlying repositories
 * and performs mapping to the feature's domain model. If a source is unavailable
 * (e.g., no Telegram auth), it returns empty flows instead of failing, allowing
 * the Home screen to gracefully handle missing sources.
 */
@Singleton
class HomeContentRepositoryAdapter @Inject constructor(
    private val telegramContentRepository: TelegramContentRepository,
    private val xtreamCatalogRepository: XtreamCatalogRepository,
    private val xtreamLiveRepository: XtreamLiveRepository,
) : HomeContentRepository {

    override fun observeTelegramMedia(): Flow<List<HomeMediaItem>> {
        return telegramContentRepository.observeAll()
            .map { items -> items.map { it.toHomeMediaItem() } }
            .catch { throwable ->
                UnifiedLog.e(TAG, throwable) { "Failed to observe Telegram media content" }
                emit(emptyList())
            }
    }

    override fun observeXtreamLive(): Flow<List<HomeMediaItem>> {
        return xtreamLiveRepository.observeChannels()
            .map { items -> items.map { it.toHomeMediaItem() } }
            .catch { throwable ->
                UnifiedLog.e(TAG, throwable) { "Failed to observe Xtream live TV channels" }
                emit(emptyList())
            }
    }

    override fun observeXtreamVod(): Flow<List<HomeMediaItem>> {
        return xtreamCatalogRepository.observeVod()
            .map { items -> items.map { it.toHomeMediaItem() } }
            .catch { throwable ->
                UnifiedLog.e(TAG, throwable) { "Failed to observe Xtream VOD content" }
                emit(emptyList())
            }
    }

    override fun observeXtreamSeries(): Flow<List<HomeMediaItem>> {
        return xtreamCatalogRepository.observeSeries()
            .map { items -> items.map { it.toHomeMediaItem() } }
            .catch { throwable ->
                UnifiedLog.e(TAG, throwable) { "Failed to observe Xtream series content" }
                emit(emptyList())
            }
    }

    companion object {
        private const val TAG = "HomeContentRepositoryAdapter"
    }
}

/**
 * Maps RawMediaMetadata to HomeMediaItem (feature-domain model).
 *
 * This mapping extracts only the fields needed for Home screen display,
 * keeping the feature layer decoupled from the full RawMediaMetadata structure.
 */
private fun RawMediaMetadata.toHomeMediaItem(): HomeMediaItem {
    val bestPoster = poster ?: thumbnail
    val bestBackdrop = backdrop ?: thumbnail
    return HomeMediaItem(
        id = sourceId,
        title = originalTitle.ifBlank { sourceLabel },
        poster = bestPoster,
        placeholderThumbnail = placeholderThumbnail,
        backdrop = bestBackdrop,
        mediaType = mediaType,
        sourceType = sourceType,
        duration = durationMs ?: 0L,
        year = year,
        navigationId = sourceId,
        navigationSource = sourceType
    )
}
