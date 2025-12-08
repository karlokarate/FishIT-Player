package com.fishit.player.infra.logging.feature

import com.fishit.player.core.feature.FeatureOwner
import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.core.feature.FeatureScope
import com.fishit.player.core.feature.LoggingFeatures
import javax.inject.Inject

/**
 * Feature provider for the unified logging system.
 *
 * This feature enables:
 * - Centralized logging facade (UnifiedLog API)
 * - Structured logging with tags and levels
 * - Backend integration (Logcat, file logging, crash reporting)
 * - Log buffering and batching
 *
 * See: docs/v2/features/logging/FEATURE_infra.logging.unified.md
 */
class UnifiedLoggingFeatureProvider @Inject constructor() : FeatureProvider {

    override val featureId = LoggingFeatures.UNIFIED_LOGGING

    override val scope = FeatureScope.APP

    override val owner = FeatureOwner(
        moduleName = "infra:logging",
    )
}
