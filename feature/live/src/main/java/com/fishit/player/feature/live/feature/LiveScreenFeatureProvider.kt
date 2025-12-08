package com.fishit.player.feature.live.feature

import com.fishit.player.core.feature.FeatureOwner
import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.core.feature.FeatureScope
import com.fishit.player.core.feature.UiFeatures
import javax.inject.Inject

/**
 * Feature provider for the Live TV screen.
 *
 * This feature enables:
 * - Live TV channel browsing
 * - EPG (Electronic Program Guide) viewing
 * - Live channel playback
 *
 * See: docs/v2/features/ui/FEATURE_ui.screen.live.md
 */
class LiveScreenFeatureProvider @Inject constructor() : FeatureProvider {

    override val featureId = UiFeatures.SCREEN_LIVE

    override val scope = FeatureScope.UI_SCREEN

    override val owner = FeatureOwner(
        moduleName = "feature:live",
    )
}
