package com.fishit.player.core.sourceactivation

/**
 * Error reasons for source activation failures.
 *
 * These map to specific failure conditions that can be surfaced to the UI.
 *
 * **Location:** This enum lives in `core:source-activation-api` to allow both
 * `catalog-sync` (implementation) and `data-*` modules (activation calls)
 * to share it without circular dependencies.
 */
enum class SourceErrorReason {
    /** User must log in again */
    LOGIN_REQUIRED,

    /** Credentials are invalid or expired */
    INVALID_CREDENTIALS,

    /** Required permission (e.g., storage) is missing */
    PERMISSION_MISSING,

    /** Network or transport-level error (retryable) */
    TRANSPORT_ERROR,

    /** Android Keystore unavailable — cannot read encrypted credentials */
    KEYSTORE_UNAVAILABLE,
}
