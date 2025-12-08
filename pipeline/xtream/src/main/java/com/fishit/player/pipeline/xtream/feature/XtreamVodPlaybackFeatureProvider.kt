package com.fishit.player.pipeline.xtream.feature

import com.fishit.player.core.feature.FeatureOwner
import com.fishit.player.core.feature.FeatureProvider
import com.fishit.player.core.feature.FeatureScope
import com.fishit.player.core.feature.XtreamFeatures
import javax.inject.Inject

/**
 * Feature provider for Xtream VOD (Video on Demand) playback.
 *
 * This feature enables:
 * - VOD movie and video playback via Xtream Codes API
 * - Movie metadata retrieval
 * - VOD categorization and browsing
 *
 * See: docs/v2/features/xtream/FEATURE_xtream.vod_playback.md
 */
class XtreamVodPlaybackFeatureProvider @Inject constructor() : FeatureProvider {

    override val featureId = XtreamFeatures.VOD_PLAYBACK

    override val scope = FeatureScope.PIPELINE

    override val owner = FeatureOwner(
        moduleName = "pipeline:xtream",
    )
}
