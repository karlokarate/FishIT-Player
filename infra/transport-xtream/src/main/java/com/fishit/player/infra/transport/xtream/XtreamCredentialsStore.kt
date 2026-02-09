package com.fishit.player.infra.transport.xtream

/**
 * Secure storage for Xtream server credentials.
 *
 * This interface provides persistence for Xtream configuration across app restarts.
 * Implementations MUST use secure storage (e.g., EncryptedSharedPreferences).
 *
 * **Security Contract:**
 * - Credentials MUST be encrypted at rest
 * - Implementations MUST NOT log sensitive fields (username, password)
 * - Clear operation MUST securely delete all stored data
 */
interface XtreamCredentialsStore {
    /**
     * Check if the secure storage backend is available.
     *
     * Returns false if the Android Keystore or EncryptedSharedPreferences
     * could not be initialized. In that case, [read] will always return null
     * and [write] will silently discard data.
     *
     * @return true if credentials can be read/written, false if storage is broken
     */
    suspend fun isAvailable(): Boolean

    /**
     * Read stored credentials.
     *
     * @return Stored configuration, or null if no credentials are saved
     */
    suspend fun read(): XtreamStoredConfig?

    /**
     * Write credentials to secure storage.
     *
     * @param config Configuration to persist
     */
    suspend fun write(config: XtreamStoredConfig)

    /**
     * Clear all stored credentials.
     * Safe to call even if no credentials are stored.
     */
    suspend fun clear()
}

/**
 * Xtream server configuration for secure storage.
 *
 * This class stores normalized credential fields (not full URLs) to avoid
 * logging/persisting secrets in URL form.
 *
 * @property scheme URL scheme ("http" or "https")
 * @property host Server hostname or IP
 * @property port Server port (null if auto-discovery should be used)
 * @property username Xtream API username
 * @property password Xtream API password
 */
data class XtreamStoredConfig(
    val scheme: String,
    val host: String,
    val port: Int?,
    val username: String,
    val password: String,
) {
    /**
     * Convert to XtreamApiConfig for initializing the client.
     */
    fun toApiConfig(): XtreamApiConfig =
        XtreamApiConfig(
            scheme = scheme,
            host = host,
            port = port,
            username = username,
            password = password,
        )
}
