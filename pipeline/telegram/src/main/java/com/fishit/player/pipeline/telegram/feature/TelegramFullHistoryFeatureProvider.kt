package com.fishit.player.pipeline.telegram.feature

import com.fishit.player.core.feature.FeatureOwner
import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.core.feature.FeatureScope
import com.fishit.player.core.feature.TelegramFeatures
import javax.inject.Inject

/**
 * Feature provider for Telegram full history streaming.
 *
 * This feature enables:
 * - Complete chat history scanning with cursor-based paging
 * - Efficient message traversal without arbitrary limits
 * - Background synchronization of chat content
 *
 * See: docs/v2/features/telegram/FEATURE_telegram.full_history_streaming.md
 */
class TelegramFullHistoryFeatureProvider @Inject constructor() : FeatureProvider {

    override val featureId = TelegramFeatures.FULL_HISTORY_STREAMING

    override val scope = FeatureScope.PIPELINE

    override val owner = FeatureOwner(
        moduleName = "pipeline:telegram",
    )
}
