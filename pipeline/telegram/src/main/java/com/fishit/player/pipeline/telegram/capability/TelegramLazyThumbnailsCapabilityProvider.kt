package com.fishit.player.pipeline.telegram.capability

import com.fishit.player.core.feature.FeatureOwner
import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.core.feature.FeatureScope
import com.fishit.player.core.feature.TelegramFeatures
import javax.inject.Inject

/**
 * Pipeline capability provider for Telegram lazy thumbnail loading.
 *
 * This capability enables:
 * - On-demand thumbnail loading from Telegram API file references
 * - Efficient memory usage by loading thumbnails only when visible
 * - Automatic thumbnail caching
 *
 * See: docs/v2/features/telegram/FEATURE_telegram.lazy_thumbnails.md
 */
class TelegramLazyThumbnailsCapabilityProvider
    @Inject
    constructor() : FeatureProvider {
        override val featureId = TelegramFeatures.LAZY_THUMBNAILS

        override val scope = FeatureScope.PIPELINE

        override val owner =
            FeatureOwner(
                moduleName = "pipeline:telegram",
            )
    }
