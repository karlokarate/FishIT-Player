package com.fishit.player.feature.library.feature

import com.fishit.player.core.feature.FeatureOwner
import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.core.feature.FeatureScope
import com.fishit.player.core.feature.UiFeatures
import javax.inject.Inject

/**
 * Feature provider for the Library screen.
 *
 * This feature enables:
 * - Media library browsing and organization
 * - Content filtering and sorting
 * - Collection management
 *
 * See: docs/v2/features/ui/FEATURE_ui.screen.library.md
 */
class LibraryScreenFeatureProvider @Inject constructor() : FeatureProvider {

    override val featureId = UiFeatures.SCREEN_LIBRARY

    override val scope = FeatureScope.UI_SCREEN

    override val owner = FeatureOwner(
        moduleName = "feature:library",
    )
}
