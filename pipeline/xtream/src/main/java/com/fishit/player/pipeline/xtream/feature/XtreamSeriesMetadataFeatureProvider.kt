package com.fishit.player.pipeline.xtream.feature

import com.fishit.player.core.feature.FeatureOwner
import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.core.feature.FeatureScope
import com.fishit.player.core.feature.XtreamFeatures
import javax.inject.Inject

/**
 * Feature provider for Xtream series and episode metadata.
 *
 * This feature enables:
 * - TV series metadata retrieval via Xtream Codes API
 * - Season and episode organization
 * - Series episode playback
 *
 * See: docs/v2/features/xtream/FEATURE_xtream.series_metadata.md
 */
class XtreamSeriesMetadataFeatureProvider @Inject constructor() : FeatureProvider {

    override val featureId = XtreamFeatures.SERIES_METADATA

    override val scope = FeatureScope.PIPELINE

    override val owner = FeatureOwner(
        moduleName = "pipeline:xtream",
    )
}
