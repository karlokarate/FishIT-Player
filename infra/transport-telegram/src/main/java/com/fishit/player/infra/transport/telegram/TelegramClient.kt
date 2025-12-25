package com.fishit.player.infra.transport.telegram

/**
 * Unified Telegram transport client facade.
 *
 * This interface combines all typed transport clients into a single API surface:
 * - [TelegramAuthClient]
 * - Authentication operations
 * - [TelegramHistoryClient]
 * - Chat and message operations
 * - [TelegramFileClient]
 * - File download operations
 * - [TelegramThumbFetcher]
 * - Thumbnail fetching
 *
 * **Architecture:**
 * - Single implementation ([DefaultTelegramClient]) composes existing modules
 * - Hilt provides typed interfaces via delegation to this single instance
 * - Avoids multiple TdlClient wrappers competing for resources
 *
 * **Usage:** Consumers should depend on the specific typed interface they need, not this unified
 * interface (Dependency Inversion Principle).
 *
 * **Note:** This does NOT inherit from [TelegramTransportClient] (deprecated legacy interface). If
 * you need the legacy interface for migration purposes, use it directly.
 */
interface TelegramClient :
        TelegramAuthClient, TelegramHistoryClient, TelegramFileClient, TelegramThumbFetcher
