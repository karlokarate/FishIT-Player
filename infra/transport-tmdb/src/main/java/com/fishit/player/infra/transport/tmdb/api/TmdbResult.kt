package com.fishit.player.infra.transport.tmdb.api

/**
 * Result wrapper for TMDB API operations.
 *
 * All TMDB operations return this sealed type to ensure errors are explicitly handled.
 * No exceptions leak from the gateway.
 */
sealed class TmdbResult<out T> {
    /**
     * Successful result with data.
     */
    data class Ok<T>(
        val value: T,
    ) : TmdbResult<T>()

    /**
     * Failed result with typed error.
     */
    data class Err(
        val error: TmdbError,
    ) : TmdbResult<Nothing>()
}

/**
 * Typed errors from TMDB operations.
 *
 * These represent all failure modes that can occur during TMDB API calls.
 */
sealed class TmdbError {
    /**
     * Network connectivity error (DNS, connection refused, etc.).
     */
    data object Network : TmdbError()

    /**
     * Request timeout (connect/read/write exceeded configured limits).
     */
    data object Timeout : TmdbError()

    /**
     * Unauthorized (401) - Invalid or missing API key.
     */
    data object Unauthorized : TmdbError()

    /**
     * Not found (404) - Resource does not exist.
     */
    data object NotFound : TmdbError()

    /**
     * Rate limited (429) - Too many requests.
     *
     * @property retryAfter Optional retry-after header value in seconds
     */
    data class RateLimited(
        val retryAfter: Long? = null,
    ) : TmdbError()

    /**
     * Server error (5xx) or other unknown error.
     *
     * @property message Optional error message for logging
     */
    data class Unknown(
        val message: String? = null,
    ) : TmdbError()
}
