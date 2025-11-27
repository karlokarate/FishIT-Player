package com.chris.m3usuite.tv.input

/**
 * DSL builder for creating TV input configuration.
 *
 * Provides a declarative DSL syntax for defining per-screen key→action mappings:
 *
 * ```kotlin
 * tvInputConfig {
 *     screen(TvScreenId.PLAYER) {
 *         on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.SEEK_FORWARD_30S
 *         on(TvKeyRole.DPAD_UP) mapsTo TvAction.FOCUS_QUICK_ACTIONS
 *         on(TvKeyRole.MENU) mapsTo TvAction.OPEN_QUICK_ACTIONS
 *     }
 *
 *     screen(TvScreenId.LIBRARY) {
 *         on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.PAGE_DOWN
 *         on(TvKeyRole.REWIND) mapsTo TvAction.PAGE_UP
 *     }
 * }
 * ```
 *
 * Contract Reference: INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 4.2
 *
 * Phase 6 Task 2:
 * - Declarative DSL for per-screen key→action mappings
 * - Type-safe at compile time
 * - Produces immutable TvScreenInputConfig instances
 * - Does NOT reference FocusKit or TvInputController (Task 3)
 *
 * @see TvScreenInputConfig for the resulting data model
 * @see ScreenConfigBuilder for per-screen configuration
 */

/**
 * Entry point for the TV input configuration DSL.
 *
 * Usage:
 * ```kotlin
 * val configs = tvInputConfig {
 *     screen(TvScreenId.PLAYER) {
 *         on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.SEEK_FORWARD_30S
 *         // ... more mappings
 *     }
 * }
 * ```
 *
 * @param block The configuration block
 * @return Map of screen IDs to their configurations
 */
fun tvInputConfig(block: TvInputConfigBuilder.() -> Unit): Map<TvScreenId, TvScreenInputConfig> {
    val builder = TvInputConfigBuilder()
    builder.block()
    return builder.build()
}

/**
 * Top-level builder for TV input configuration.
 *
 * Collects per-screen configurations and produces an immutable map.
 */
class TvInputConfigBuilder {
    private val configs = mutableMapOf<TvScreenId, TvScreenInputConfig>()

    /**
     * Define configuration for a specific screen.
     *
     * Usage:
     * ```kotlin
     * screen(TvScreenId.PLAYER) {
     *     on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.SEEK_FORWARD_30S
     * }
     * ```
     *
     * @param screenId The screen to configure
     * @param block The configuration block for this screen
     */
    fun screen(
        screenId: TvScreenId,
        block: ScreenConfigBuilder.() -> Unit,
    ) {
        val screenBuilder = ScreenConfigBuilder(screenId)
        screenBuilder.block()
        configs[screenId] = screenBuilder.build()
    }

    /**
     * Build the final immutable configuration map.
     *
     * @return Map of screen IDs to their configurations
     */
    fun build(): Map<TvScreenId, TvScreenInputConfig> = configs.toMap()
}

/**
 * Builder for a single screen's input configuration.
 *
 * Provides DSL methods for mapping key roles to actions.
 *
 * @property screenId The screen being configured
 */
class ScreenConfigBuilder(val screenId: TvScreenId) {
    private val bindings = mutableMapOf<TvKeyRole, TvAction?>()

    /**
     * Start a key binding declaration.
     *
     * Usage:
     * ```kotlin
     * on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.SEEK_FORWARD_30S
     * ```
     *
     * @param role The key role to bind
     * @return A KeyBindingBuilder for completing the binding
     */
    fun on(role: TvKeyRole): KeyBindingBuilder = KeyBindingBuilder(role, this)

    /**
     * Internal method to add a binding.
     */
    internal fun addBinding(
        role: TvKeyRole,
        action: TvAction?,
    ) {
        bindings[role] = action
    }

    /**
     * Build the screen configuration.
     *
     * @return The immutable TvScreenInputConfig
     */
    fun build(): TvScreenInputConfig =
        TvScreenInputConfig(
            screenId = screenId,
            bindings = bindings.toMap(),
        )
}

/**
 * Builder for a single key→action binding.
 *
 * Provides the `mapsTo` infix function for completing bindings.
 */
class KeyBindingBuilder(
    private val role: TvKeyRole,
    private val parent: ScreenConfigBuilder,
) {
    /**
     * Complete the binding by specifying the target action.
     *
     * Usage:
     * ```kotlin
     * on(TvKeyRole.FAST_FORWARD) mapsTo TvAction.SEEK_FORWARD_30S
     * ```
     *
     * @param action The action to map to (or null for no action)
     */
    infix fun mapsTo(action: TvAction?) {
        parent.addBinding(role, action)
    }
}

// ══════════════════════════════════════════════════════════════════
// CONVENIENCE EXTENSIONS
// ══════════════════════════════════════════════════════════════════

/**
 * Get the configuration for a specific screen, or an empty config if not defined.
 *
 * @param screenId The screen to look up
 * @return The screen's configuration, or empty config if not defined
 */
fun Map<TvScreenId, TvScreenInputConfig>.getOrEmpty(screenId: TvScreenId): TvScreenInputConfig =
    this[screenId] ?: TvScreenInputConfig.empty(screenId)

/**
 * Resolve an action using this config map.
 *
 * This is a convenience method that combines config lookup and resolution:
 * 1. Gets the config for the screen (or empty if not defined)
 * 2. Resolves the action with Kids Mode and overlay filtering
 *
 * @param screenId The screen ID
 * @param role The key role to resolve
 * @param ctx The screen context
 * @return The resolved action, or null if blocked/unmapped
 */
fun Map<TvScreenId, TvScreenInputConfig>.resolve(
    screenId: TvScreenId,
    role: TvKeyRole,
    ctx: TvScreenContext,
): TvAction? {
    val config = getOrEmpty(screenId)
    return resolve(config, role, ctx)
}
