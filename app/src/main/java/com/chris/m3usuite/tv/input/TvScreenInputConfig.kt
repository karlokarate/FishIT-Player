package com.chris.m3usuite.tv.input

import com.chris.m3usuite.tv.input.TvAction.Companion.isNavigationAction
import com.chris.m3usuite.tv.input.TvAction.Companion.isSeekAction

/**
 * Per-screen input configuration for the TV input system.
 *
 * TvScreenInputConfig defines how key roles map to semantic actions for a specific screen.
 * Each screen can have different mappings (e.g., DPAD_LEFT seeks in player but navigates
 * in browse screens).
 *
 * This is a pure data model with no side effects. The actual filtering and blocking
 * logic is provided by helper functions that operate on this config.
 *
 * Contract Reference: INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 4.2
 *
 * Phase 6 Task 2:
 * - Data model for per-screen key mappings
 * - Kids Mode filtering applied at resolve time
 * - Overlay blocking applied at resolve time
 *
 * @property screenId The screen this configuration applies to
 * @property bindings Map of key roles to optional actions (null = no action/ignored)
 *
 * @see TvScreenId for screen identifiers
 * @see TvKeyRole for key roles
 * @see TvAction for semantic actions
 */
data class TvScreenInputConfig(
    /** The screen this configuration applies to */
    val screenId: TvScreenId,
    /** Map of key roles to optional actions (null = no action/ignored) */
    val bindings: Map<TvKeyRole, TvAction?> = emptyMap(),
) {
    /**
     * Get the raw action for a key role (without filtering).
     *
     * @param role The key role to look up
     * @return The mapped action, or null if unmapped
     */
    fun getRawAction(role: TvKeyRole): TvAction? = bindings[role]

    /**
     * Check if this config has a binding for the given role.
     */
    fun hasBinding(role: TvKeyRole): Boolean = bindings.containsKey(role)

    /**
     * Get all bound roles for this config.
     */
    fun boundRoles(): Set<TvKeyRole> = bindings.keys

    companion object {
        /**
         * Create an empty config for a screen.
         */
        fun empty(screenId: TvScreenId): TvScreenInputConfig = TvScreenInputConfig(screenId)
    }
}

/**
 * Resolve the action for a key role on a given screen, applying Kids Mode and overlay filtering.
 *
 * This is the main entry point for action resolution. It:
 * 1. Looks up the raw action from the screen's config
 * 2. Applies Kids Mode filtering if ctx.isKidProfile is true
 * 3. Applies overlay blocking if ctx.hasBlockingOverlay is true
 * 4. Applies MiniPlayer filtering if ctx.isMiniPlayerVisible is true (Phase 7)
 *
 * Contract Reference:
 * - Section 7.1: Kids Mode filtering rules
 * - Section 8.1: Overlay blocking rules
 * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 5: MiniPlayer filtering
 *
 * @param config The screen input configuration
 * @param role The key role to resolve
 * @param ctx The current screen context
 * @return The resolved action, or null if blocked/unmapped
 */
fun resolve(
    config: TvScreenInputConfig,
    role: TvKeyRole,
    ctx: TvScreenContext,
): TvAction? {
    // Step 1: Get raw action from config
    val rawAction = config.getRawAction(role)

    // Step 2: Apply Kids Mode filter (BEFORE screen config per contract)
    val afterKidsFilter = filterForKidsMode(rawAction, ctx)

    // Step 3: Apply overlay blocking filter
    val afterOverlayFilter = filterForOverlays(afterKidsFilter, ctx)

    // Step 4: Apply MiniPlayer visibility filter (Phase 7)
    return filterForMiniPlayer(afterOverlayFilter, ctx)
}

/**
 * Filter an action for Kids Mode restrictions.
 *
 * When a kid profile is active, certain actions are blocked:
 * - FAST_FORWARD, REWIND (key roles, but also seek actions)
 * - All SEEK_* actions
 * - OPEN_CC_MENU, OPEN_ASPECT_MENU, OPEN_LIVE_LIST
 *
 * Allowed actions for Kids:
 * - Navigation (DPAD movement)
 * - BACK
 * - PLAY_PAUSE (basic playback toggle)
 * - OPEN_QUICK_ACTIONS (but limited menu)
 *
 * Contract Reference: Section 7.1
 *
 * @param action The action to filter (may be null)
 * @param ctx The screen context with kid profile info
 * @return The action if allowed, null if blocked
 */
fun filterForKidsMode(
    action: TvAction?,
    ctx: TvScreenContext,
): TvAction? {
    // Not a kid profile - pass through
    if (!ctx.isKidProfile) return action

    // Null action passes through
    if (action == null) return null

    // Check if action is blocked for kids
    return if (isBlockedForKids(action)) {
        null
    } else {
        action
    }
}

/**
 * Filter an action for overlay blocking restrictions.
 *
 * When a blocking overlay is active, only navigation and BACK are allowed:
 * - NAVIGATE_* actions → Allowed (navigation inside overlay)
 * - BACK → Allowed (closes overlay)
 * - All other actions → Blocked
 *
 * Contract Reference: Section 8.1
 *
 * @param action The action to filter (may be null)
 * @param ctx The screen context with overlay info
 * @return The action if allowed, null if blocked
 */
fun filterForOverlays(
    action: TvAction?,
    ctx: TvScreenContext,
): TvAction? {
    // No blocking overlay - pass through
    if (!ctx.hasBlockingOverlay) return action

    // Null action passes through
    if (action == null) return null

    // Check if action is allowed in overlay
    return if (isAllowedInOverlay(action)) {
        action
    } else {
        null
    }
}

// ══════════════════════════════════════════════════════════════════
// PRIVATE HELPER FUNCTIONS
// ══════════════════════════════════════════════════════════════════

/**
 * Check if an action is blocked for kid profiles.
 *
 * Blocked Actions for Kids (Contract Section 7.1 + GLOBAL_TV_REMOTE_BEHAVIOR_MAP):
 * - All SEEK_* actions (standard and PIP)
 * - OPEN_CC_MENU
 * - OPEN_ASPECT_MENU
 * - OPEN_LIVE_LIST
 * - PIP seek actions (PIP_SEEK_FORWARD, PIP_SEEK_BACKWARD)
 * - Advanced settings (OPEN_ADVANCED_SETTINGS)
 *
 * Note: FAST_FORWARD and REWIND are TvKeyRoles, not TvActions.
 * The screen config should NOT map FF/RW to seek actions for kids,
 * and this filter catches any seek actions that slip through.
 */
private fun isBlockedForKids(action: TvAction): Boolean =
    action.isSeekAction() ||
        action == TvAction.OPEN_CC_MENU ||
        action == TvAction.OPEN_ASPECT_MENU ||
        action == TvAction.OPEN_LIVE_LIST ||
        action == TvAction.PIP_SEEK_FORWARD ||
        action == TvAction.PIP_SEEK_BACKWARD ||
        action == TvAction.OPEN_ADVANCED_SETTINGS

/**
 * Check if an action is allowed when a blocking overlay is active.
 *
 * Allowed Actions in Overlays (Contract Section 8.1):
 * - NAVIGATE_UP, NAVIGATE_DOWN, NAVIGATE_LEFT, NAVIGATE_RIGHT
 * - BACK (closes overlay)
 */
private fun isAllowedInOverlay(action: TvAction): Boolean = action.isNavigationAction() || action == TvAction.BACK

// ══════════════════════════════════════════════════════════════════
// PHASE 7 – MiniPlayer Visibility Filter
// Contract Reference: INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 5
// ══════════════════════════════════════════════════════════════════

/**
 * Filter an action for MiniPlayer visibility restrictions.
 *
 * When the MiniPlayer overlay is visible, row fast-scroll actions are blocked:
 * - ROW_FAST_SCROLL_FORWARD → Blocked
 * - ROW_FAST_SCROLL_BACKWARD → Blocked
 *
 * All other actions pass through unchanged.
 *
 * Contract Reference: INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 5
 *
 * @param action The action to filter (may be null)
 * @param ctx The screen context with MiniPlayer visibility info
 * @return The action if allowed, null if blocked
 */
fun filterForMiniPlayer(
    action: TvAction?,
    ctx: TvScreenContext,
): TvAction? {
    // MiniPlayer not visible - pass through
    if (!ctx.isMiniPlayerVisible) return action

    // Null action passes through
    if (action == null) return null

    // Check if action is blocked when MiniPlayer is visible
    return if (isBlockedForMiniPlayer(action)) {
        null
    } else {
        action
    }
}

/**
 * Check if an action is blocked when MiniPlayer is visible.
 *
 * Blocked Actions with MiniPlayer (Phase 7 Contract Section 5):
 * - ROW_FAST_SCROLL_FORWARD
 * - ROW_FAST_SCROLL_BACKWARD
 *
 * This prevents accidental row fast-scrolling while the MiniPlayer overlay
 * is visible, which could cause navigation confusion.
 */
private fun isBlockedForMiniPlayer(action: TvAction): Boolean =
    action == TvAction.ROW_FAST_SCROLL_FORWARD ||
        action == TvAction.ROW_FAST_SCROLL_BACKWARD
