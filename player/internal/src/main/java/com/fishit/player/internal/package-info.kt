/**
 * Internal Player module for FishIT Player v2.
 *
 * The v2 Structured Internal Player (SIP) using Media3/ExoPlayer.
 *
 * Organized into packages:
 * - internal.state
 * - internal.session
 * - internal.source
 * - internal.ui
 * - internal.subtitles
 * - internal.live
 * - internal.tv
 * - internal.mini
 * - internal.system
 * - internal.debug
 *
 * Public entrypoint: InternalPlayerEntry(playbackContext: PlaybackContext, ...)
 *
 * Must NOT depend on any :feature:* or :pipeline:* module directly.
 */
package com.fishit.player.internal
