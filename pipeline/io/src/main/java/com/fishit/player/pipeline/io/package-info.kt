/**
 * IO Pipeline module for FishIT Player v2.
 *
 * Purpose:
 * The IO pipeline provides integration for local and network-based media content.
 *
 * Current Status (Phase 2 - Task 4):
 * This is a stub implementation. All interfaces and models are defined,
 * but implementations provide fake/empty data without real filesystem access.
 *
 * Architecture:
 * - Domain Models: IoMediaItem, IoSource
 * - Interfaces: IoContentRepository, IoPlaybackSourceFactory
 * - Implementations: Stub implementations in impl package
 * - Extensions: toPlaybackContext for PlaybackContext conversion
 *
 * Future Phases:
 * - Phase 3: Basic filesystem access
 * - Phase 4: Android SAF integration
 * - Phase 5: Network shares (SMB)
 * - Phase 6: Advanced features (thumbnails, metadata extraction)
 *
 * Dependencies:
 * Allowed: :core:model, :core:persistence, :infra:logging, Kotlin stdlib, coroutines
 * Forbidden: :player:internal, :feature:*, other :pipeline:*
 */
package com.fishit.player.pipeline.io
