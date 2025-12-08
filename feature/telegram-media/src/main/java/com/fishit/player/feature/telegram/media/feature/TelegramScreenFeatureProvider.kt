package com.fishit.player.feature.telegram.media.feature

import com.fishit.player.core.feature.FeatureOwner
import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.core.feature.FeatureScope
import com.fishit.player.core.feature.UiFeatures
import javax.inject.Inject

/**
 * Feature provider for the Telegram Media screen.
 *
 * This feature enables:
 * - Telegram chat browsing for media content
 * - Telegram video and file playback
 * - Telegram content organization
 *
 * See: docs/v2/features/ui/FEATURE_ui.screen.telegram.md
 */
class TelegramScreenFeatureProvider @Inject constructor() : FeatureProvider {

    override val featureId = UiFeatures.SCREEN_TELEGRAM

    override val scope = FeatureScope.UI_SCREEN

    override val owner = FeatureOwner(
        moduleName = "feature:telegram-media",
    )
}
