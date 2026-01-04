package com.fishit.player.infra.logging

/**
 * Log redactor for removing sensitive information from log messages.
 *
 * **Contract (LOGGING_CONTRACT_V2):**
 * - All buffered logs MUST be redacted before storage
 * - Redaction is deterministic and non-reversible
 * - No secrets (passwords, tokens, API keys) may persist in memory
 *
 * **Redaction patterns:**
 * - `username=...` → `username=***`
 * - `password=...` → `password=***`
 * - `Bearer <token>` → `Bearer ***`
 * - `api_key=...` → `api_key=***`
 * - Xtream query params: `&user=...`, `&pass=...`
 *
 * **Thread Safety:**
 * - All methods are stateless and thread-safe
 * - No internal mutable state
 */
object LogRedactor {
    // Regex patterns for sensitive data
    private val PATTERNS: List<Pair<Regex, String>> =
        listOf(
            // Standard key=value patterns (case insensitive)
            Regex("""(?i)(username|user|login)\s*=\s*[^\s&,;]+""") to "$1=***",
            Regex("""(?i)(password|pass|passwd|pwd)\s*=\s*[^\s&,;]+""") to "$1=***",
            Regex("""(?i)(api_key|apikey|api-key)\s*=\s*[^\s&,;]+""") to "$1=***",
            Regex("""(?i)(token|access_token|auth_token)\s*=\s*[^\s&,;]+""") to "$1=***",
            Regex("""(?i)(secret|client_secret)\s*=\s*[^\s&,;]+""") to "$1=***",
            // Bearer token pattern
            Regex("""Bearer\s+[A-Za-z0-9\-._~+/]+=*""") to "Bearer ***",
            // Basic auth header
            Regex("""Basic\s+[A-Za-z0-9+/]+=*""") to "Basic ***",
            // Xtream-specific URL query params
            Regex("""(?i)[?&](username|user)=[^&\s]+""") to "$1=***",
            Regex("""(?i)[?&](password|pass)=[^&\s]+""") to "$1=***",
            // JSON-like patterns
            Regex(""""(password|pass|passwd|pwd|token|api_key|secret)"\s*:\s*"[^"]*"""") to """"$1":"***"""",
            // Phone numbers (for Telegram auth)
            Regex("""(?<!\d)\+?\d{10,15}(?!\d)""") to "***PHONE***",
        )

    /**
     * Redact sensitive information from a log message.
     *
     * @param message The original log message
     * @return The redacted message with secrets replaced by ***
     */
    fun redact(message: String): String {
        if (message.isBlank()) return message

        var result = message
        for ((pattern, replacement) in PATTERNS) {
            result = pattern.replace(result, replacement)
        }
        return result
    }

    /**
     * Redact sensitive information from a throwable's message.
     *
     * @param throwable The throwable to redact
     * @return A redacted version of the throwable message, or null if no message
     */
    fun redactThrowable(throwable: Throwable?): String? {
        val message = throwable?.message ?: return null
        return redact(message)
    }

    /**
     * Create a redacted copy of a [BufferedLogEntry].
     *
     * @param entry The original log entry
     * @return A new entry with redacted message and throwable info
     */
    fun redactEntry(entry: BufferedLogEntry): BufferedLogEntry =
        entry.copy(
            message = redact(entry.message),
            // Re-redact throwable info (already data-only, no Throwable reference)
            throwableInfo =
                entry.throwableInfo?.let { info ->
                    RedactedThrowableInfo(
                        type = info.type,
                        message = redact(info.message ?: ""),
                    )
                },
        )
}
