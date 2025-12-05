/**
 * Playback Domain module for FishIT Player v2.
 *
 * Contains pipeline-agnostic playback logic and contracts:
 * - ResumeManager
 * - KidsPlaybackGate
 * - SubtitleStyleManager
 * - SubtitleSelectionPolicy
 * - LivePlaybackController
 * - TvInputController
 *
 * Must NOT depend on any pipeline, UI, or Firebase modules.
 */
package com.fishit.player.playback.domain
