package com.fishit.player.pipeline.xtream.feature

import com.fishit.player.core.feature.FeatureOwner
import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.core.feature.FeatureScope
import com.fishit.player.core.feature.XtreamFeatures
import javax.inject.Inject

/**
 * Feature provider for Xtream live TV streaming.
 *
 * This feature enables:
 * - Live TV channel streaming via Xtream Codes API
 * - EPG (Electronic Program Guide) integration
 * - Channel listing and categorization
 *
 * See: docs/v2/features/xtream/FEATURE_xtream.live_streaming.md
 */
class XtreamLiveStreamingFeatureProvider @Inject constructor() : FeatureProvider {

    override val featureId = XtreamFeatures.LIVE_STREAMING

    override val scope = FeatureScope.PIPELINE

    override val owner = FeatureOwner(
        moduleName = "pipeline:xtream",
    )
}
