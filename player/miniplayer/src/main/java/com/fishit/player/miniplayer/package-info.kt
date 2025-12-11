/**
 * MiniPlayer module for v2 Internal Player.
 *
 * ════════════════════════════════════════════════════════════════════════════════ PHASE 5 –
 * MiniPlayer Migration
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * This module provides the in-app MiniPlayer functionality:
 * - [MiniPlayerState]: Immutable state model (visibility, mode, anchor, size, position)
 * - [MiniPlayerManager]: State machine for full player ↔ MiniPlayer transitions
 * - [MiniPlayerOverlay]: Compose UI component for the floating player overlay
 *
 * **Key Principles:**
 * - No direct ExoPlayer access: Uses player:internal's session
 * - No pipeline imports: Pure UI/state layer
 * - Thread-safe state via StateFlow
 * - Battle-tested logic ported from v1
 *
 * **Layer:** player/miniplayer **Consumers:** feature/player-ui, app-v2
 *
 * @see MiniPlayerState
 * @see MiniPlayerManager
 */
package com.fishit.player.miniplayer
