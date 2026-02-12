// Module: core/catalog-sync/sources/telegram
// Telegram sync configuration implementing BaseSyncConfig

package com.fishit.player.core.catalogsync.sources.telegram

import com.fishit.player.core.model.sync.BaseSyncConfig
import com.fishit.player.core.model.sync.DeviceProfile

/**
 * Configuration for Telegram catalog synchronization.
 *
 * Mirrors [XtreamSyncConfig] pattern: ONE configurable entry point
 * controls all Telegram sync behavior.
 *
 * **Telegram-specific parameters:**
 * - Chat selection (which Telegram chats to scan)
 * - High-water marks (incremental sync per chat)
 * - Parallel chat scanning (PLATINUM pattern)
 * - Checkpoint resume (skip already-completed chats)
 *
 * **Usage:**
 * ```kotlin
 * // Full sync (all chats)
 * telegramSyncService.sync(TelegramSyncConfig.fullSync(accountKey))
 *     .collect { status -> handleStatus(status) }
 *
 * // Incremental sync with checkpoint resume
 * telegramSyncService.sync(TelegramSyncConfig.incremental(accountKey))
 *     .collect { ... }
 *
 * // Specific chats only
 * telegramSyncService.sync(TelegramSyncConfig.withChats(
 *     accountKey = accountKey,
 *     chatIds = setOf(-100123456789L, -100987654321L),
 * )).collect { ... }
 * ```
 *
 * @property accountKey SSOT account key from NxKeyGenerator.telegramAccountKeyFromUserId()
 * @property deviceProfile Device profile for adaptive tuning
 * @property forceFullSync Ignore checkpoints, scan everything from scratch
 * @property chatIds Specific chat IDs to scan (null = all available chats)
 * @property excludeChatIds Chat IDs to skip (from checkpoint resume)
 * @property chatParallelism Number of chats to scan in parallel (default 3)
 * @property enableCheckpoints Enable checkpoint persistence for resumable sync
 * @property maxMessagesPerChat Optional limit on messages scanned per chat (null = no limit)
 *
 * @see BaseSyncConfig Common sync config contract
 * @see TelegramSyncService Consumer of this config
 */
data class TelegramSyncConfig(
    // === Required ===
    override val accountKey: String,

    // === Device Optimization ===
    override val deviceProfile: DeviceProfile = DeviceProfile.AUTO,
    override val forceFullSync: Boolean = false,

    // === Chat Selection ===
    val chatIds: Set<Long>? = null,
    val excludeChatIds: Set<Long> = emptySet(),

    // === PLATINUM Parallelism ===
    val chatParallelism: Int = DEFAULT_CHAT_PARALLELISM,

    // === Checkpoint ===
    val enableCheckpoints: Boolean = true,

    // === Limits ===
    val maxMessagesPerChat: Int? = null,
) : BaseSyncConfig {

    companion object {
        const val DEFAULT_CHAT_PARALLELISM = 3

        /**
         * Full catalog sync — all chats, ignore checkpoints.
         *
         * Use when: First sync, user manual refresh, account switch detected.
         */
        fun fullSync(accountKey: String): TelegramSyncConfig =
            TelegramSyncConfig(
                accountKey = accountKey,
                forceFullSync = true,
            )

        /**
         * Incremental sync — checkpoint-based, only new messages.
         *
         * Use when: Background periodic sync, app startup.
         */
        fun incremental(accountKey: String): TelegramSyncConfig =
            TelegramSyncConfig(
                accountKey = accountKey,
                forceFullSync = false,
            )

        /**
         * Sync specific chats only.
         *
         * Use when: User selects specific chats in Chat Selection UI.
         */
        fun withChats(
            accountKey: String,
            chatIds: Set<Long>,
        ): TelegramSyncConfig =
            TelegramSyncConfig(
                accountKey = accountKey,
                chatIds = chatIds,
            )
    }
}
