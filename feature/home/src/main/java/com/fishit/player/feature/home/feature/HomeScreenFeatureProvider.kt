package com.fishit.player.feature.home.feature

import com.fishit.player.core.feature.FeatureOwner
import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.core.feature.FeatureScope
import com.fishit.player.core.feature.UiFeatures
import javax.inject.Inject

/**
 * Feature provider for the Home screen.
 *
 * This feature enables:
 * - Main landing page and navigation hub
 * - Content discovery and recommendations
 * - Quick access to recent and favorite content
 *
 * See: docs/v2/features/ui/FEATURE_ui.screen.home.md
 */
class HomeScreenFeatureProvider @Inject constructor() : FeatureProvider {

    override val featureId = UiFeatures.SCREEN_HOME

    override val scope = FeatureScope.UI_SCREEN

    override val owner = FeatureOwner(
        moduleName = "feature:home",
    )
}
