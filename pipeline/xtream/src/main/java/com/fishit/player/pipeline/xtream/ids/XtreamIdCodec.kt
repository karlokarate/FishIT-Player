@file:Suppress("unused", "DEPRECATION")

package com.fishit.player.pipeline.xtream.ids

// =============================================================================
// Backward Compatibility Re-exports
// =============================================================================
// The canonical location for XtreamIdCodec is now:
//   com.fishit.player.core.model.ids.XtreamIdCodec
//
// These typealiases provide backward compatibility for existing pipeline code.
// New code should import directly from core.model.ids package.
// =============================================================================

/**
 * @see com.fishit.player.core.model.ids.XtreamIdCodec
 */
typealias XtreamIdCodec = com.fishit.player.core.model.ids.XtreamIdCodec

/**
 * @see com.fishit.player.core.model.ids.XtreamParsedSourceId
 */
typealias XtreamParsedSourceId = com.fishit.player.core.model.ids.XtreamParsedSourceId

/**
 * @see com.fishit.player.core.model.ids.XtreamVodId
 */
typealias XtreamVodId = com.fishit.player.core.model.ids.XtreamVodId

/**
 * @see com.fishit.player.core.model.ids.XtreamSeriesId
 */
typealias XtreamSeriesId = com.fishit.player.core.model.ids.XtreamSeriesId

/**
 * @see com.fishit.player.core.model.ids.XtreamEpisodeId
 */
typealias XtreamEpisodeId = com.fishit.player.core.model.ids.XtreamEpisodeId

/**
 * @see com.fishit.player.core.model.ids.XtreamChannelId
 */
typealias XtreamChannelId = com.fishit.player.core.model.ids.XtreamChannelId

/**
 * @see com.fishit.player.core.model.ids.XtreamCategoryId
 */
typealias XtreamCategoryId = com.fishit.player.core.model.ids.XtreamCategoryId
