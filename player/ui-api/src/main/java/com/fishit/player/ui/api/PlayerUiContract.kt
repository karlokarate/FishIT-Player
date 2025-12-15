package com.fishit.player.ui.api

/**
 * RESERVED MODULE: Player UI API Contracts
 *
 * Purpose: UI-facing interfaces/contracts only (no implementation, no screens)
 *
 * TODO: Implement when ready for player:ui split:
 * - [ ] Define stable UI API interfaces
 * - [ ] Player screen contract definitions
 * - [ ] UI event/state models
 * - [ ] Navigation integration contracts
 *
 * Contract Rules:
 * - Interfaces only, NO implementation code
 * - NO Compose dependencies (no @Composable, no UI code)
 * - NO Hilt dependencies (contracts should be DI-agnostic)
 * - Once created, player:ui will depend on player:ui-api
 * - This enables future modularization of player UI
 *
 * See: docs/v2/FROZEN_MODULE_MANIFEST.md
 */
interface PlayerUiContract {
    // TODO: Define player UI contracts when ready for split
    // Example placeholder - remove when implementing:
    // interface PlayerScreenFactory {
    //     fun createPlayerScreen(context: PlaybackContext): PlayerScreen
    // }
}
