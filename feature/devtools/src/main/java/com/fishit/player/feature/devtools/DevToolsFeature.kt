package com.fishit.player.feature.devtools

import com.fishit.player.core.feature.FeatureId
import com.fishit.player.core.feature.FeatureOwner
import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.core.feature.FeatureScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DevTools Feature Provider
 *
 * This feature provides a minimal UI for testing login flows:
 * - Telegram authentication (phone, code, password)
 * - Xtream configuration (full URL parsing)
 *
 * This feature is debug-only and not intended for production use.
 */
@Singleton
class DevToolsFeatureProvider @Inject constructor() : FeatureProvider {
    override val featureId: FeatureId = FeatureId("ui.screen.devtools")
    override val scope: FeatureScope = FeatureScope.UI_SCREEN
    override val owner: FeatureOwner = FeatureOwner(moduleName = "feature:devtools")
}
