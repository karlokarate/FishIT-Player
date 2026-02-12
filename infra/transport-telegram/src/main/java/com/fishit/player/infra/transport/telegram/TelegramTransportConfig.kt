package com.fishit.player.infra.transport.telegram

import com.fishit.player.infra.transport.telegram.util.RetryConfig

/**
 * TelegramTransportConfig – Zentrale Transport-Konfiguration für alle Telegram-Operationen.
 *
 * Diese Klasse ist die SSOT (Single Source of Truth) für:
 * - OkHttp Timeouts (Proxy-Kommunikation via localhost:8089)
 * - Retry-Konfigurationen (exponential backoff)
 * - Parallelitäts-Parameter (Chat-Scanning, File-Downloads)
 *
 * **Architektur:**
 * ```
 * Kotlin (OkHttp) → localhost:8089 → Python (Telethon) → Telegram MTProto
 * ```
 *
 * Die Timeouts berücksichtigen die Localhost-Proxy-Latenz plus die
 * tatsächliche Telegram-API-Latenz (MTProto).
 *
 * @see XtreamTransportConfig for the Xtream counterpart (same pattern)
 */
object TelegramTransportConfig {

    // =========================================================================
    // OkHttp Timeouts — Proxy Client (API calls + file streaming)
    // =========================================================================

    /**
     * Connect timeout in seconds.
     * Allows time for Chaquopy Python interpreter startup + Telethon init.
     */
    const val CONNECT_TIMEOUT_SECONDS: Long = 30L

    /**
     * Read timeout in seconds for regular API calls (auth, chat list, message history).
     * Telegram API responses are typically fast, but proxy adds latency.
     */
    const val READ_TIMEOUT_SECONDS: Long = 30L

    /**
     * Write timeout in seconds.
     * Only small JSON payloads are sent to the proxy — 10s is generous.
     */
    const val WRITE_TIMEOUT_SECONDS: Long = 10L

    // =========================================================================
    // Streaming Timeouts (File downloads via /file endpoint)
    // =========================================================================

    /**
     * Extended read timeout for file downloads streamed through the proxy.
     * Large files (1 GB+) need significant time at Telegram's throttled rates.
     */
    const val STREAMING_READ_TIMEOUT_SECONDS: Long = 120L

    // =========================================================================
    // OkHttp Timeouts — Health Client (fast polling)
    // =========================================================================

    /**
     * Connect timeout for health-check polling.
     * Must be very short — health checks poll frequently and must fail fast.
     */
    const val HEALTH_CONNECT_TIMEOUT_SECONDS: Long = 2L

    /**
     * Read timeout for health-check polling.
     */
    const val HEALTH_READ_TIMEOUT_SECONDS: Long = 2L

    // =========================================================================
    // Retry Configurations
    // =========================================================================

    /**
     * Default retry for regular API operations (chat list, messages, etc.).
     * 5 attempts with 500ms–30s exponential backoff.
     */
    val RETRY_DEFAULT: RetryConfig = RetryConfig.DEFAULT

    /**
     * Aggressive retry for authentication operations.
     * 7 attempts with 1s–60s backoff — auth must succeed or the whole chain fails.
     */
    val RETRY_AUTH: RetryConfig = RetryConfig.AUTH

    /**
     * Quick retry for transient failures (network blips, proxy restarts).
     * 3 attempts with 200ms–2s backoff.
     */
    val RETRY_QUICK: RetryConfig = RetryConfig.QUICK

    // =========================================================================
    // Parallelism
    // =========================================================================

    /**
     * Number of Telegram chats to scan in parallel during catalog sync.
     * Limited by Telegram API rate limits and proxy throughput.
     *
     * Note: Telegram imposes FloodWait penalties for excessive parallelism,
     * so this is intentionally conservative compared to Xtream (which uses 12).
     */
    const val CHAT_SCAN_PARALLELISM: Int = 3

    /**
     * Maximum concurrent file download operations.
     */
    const val FILE_DOWNLOAD_PARALLELISM: Int = 2

    // =========================================================================
    // Pagination
    // =========================================================================

    /**
     * Default page size for message history fetching.
     * Telethon's iter_messages default; balanced between throughput and memory.
     */
    const val MESSAGE_PAGE_SIZE: Int = 100

    // =========================================================================
    // Proxy Configuration
    // =========================================================================

    /**
     * Default proxy port for the Telethon sidecar.
     * Matches the port in tg_proxy.py and TelegramSessionConfig.
     */
    const val DEFAULT_PROXY_PORT: Int = 8089

    /**
     * Proxy base URL for OkHttp requests.
     */
    const val PROXY_BASE_URL: String = "http://127.0.0.1:$DEFAULT_PROXY_PORT"
}
