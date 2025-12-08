package com.fishit.player.feature.settings.feature

import com.fishit.player.core.feature.FeatureOwner
import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.core.feature.FeatureScope
import com.fishit.player.core.feature.UiFeatures
import javax.inject.Inject

/**
 * Feature provider for the Settings screen.
 *
 * This feature enables:
 * - App settings and preferences
 * - Cache management
 * - Account and profile configuration
 *
 * See: docs/v2/features/ui/FEATURE_ui.screen.settings.md
 */
class SettingsScreenFeatureProvider @Inject constructor() : FeatureProvider {

    override val featureId = UiFeatures.SCREEN_SETTINGS

    override val scope = FeatureScope.UI_SCREEN

    override val owner = FeatureOwner(
        moduleName = "feature:settings",
    )
}
