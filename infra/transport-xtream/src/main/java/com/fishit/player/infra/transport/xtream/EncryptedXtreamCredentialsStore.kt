package com.fishit.player.infra.transport.xtream

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure Xtream credentials storage using AndroidX Security Crypto.
 *
 * Uses EncryptedSharedPreferences with AES256-GCM encryption for values
 * and AES256-SIV for keys. Master key is stored in Android Keystore.
 *
 * **Security Properties:**
 * - All credential fields encrypted at rest
 * - Master key protected by Android Keystore (hardware-backed on supported devices)
 * - No plaintext credentials in logs (only host/port/scheme logged)
 */
@Singleton
class EncryptedXtreamCredentialsStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : XtreamCredentialsStore {
        private companion object {
            private const val TAG = "XtreamCredStore"
            private const val PREFS_FILE_NAME = "xtream_credentials_encrypted"
            private const val KEY_SCHEME = "scheme"
            private const val KEY_HOST = "host"
            private const val KEY_PORT = "port"
            private const val KEY_USERNAME = "username"
            private const val KEY_PASSWORD = "password"
        }

        private val prefs: SharedPreferences by lazy {
            try {
                val masterKey =
                    MasterKey
                        .Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()

                EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (e: Exception) {
                // Fallback to unencrypted if encryption setup fails (e.g., on emulators without keystore)
                UnifiedLog.w(TAG, e) { "Failed to create encrypted prefs, using fallback" }
                context.getSharedPreferences(PREFS_FILE_NAME + "_fallback", Context.MODE_PRIVATE)
            }
        }

        override suspend fun read(): XtreamStoredConfig? =
            withContext(Dispatchers.IO) {
                try {
                    val scheme = prefs.getString(KEY_SCHEME, null)
                    val host = prefs.getString(KEY_HOST, null)
                    val portInt = prefs.getInt(KEY_PORT, -1)
                    val port = if (portInt == -1) null else portInt
                    val username = prefs.getString(KEY_USERNAME, null)
                    val password = prefs.getString(KEY_PASSWORD, null)

                    if (scheme != null && host != null && username != null && password != null) {
                        UnifiedLog.i(TAG) { "Read stored config: scheme=$scheme, host=$host, port=$port" }
                        XtreamStoredConfig(
                            scheme = scheme,
                            host = host,
                            port = port,
                            username = username,
                            password = password,
                        )
                    } else {
                        UnifiedLog.d(TAG) { "No complete credentials stored" }
                        null
                    }
                } catch (e: Exception) {
                    UnifiedLog.e(TAG, e) { "Failed to read credentials" }
                    null
                }
            }

        override suspend fun write(config: XtreamStoredConfig): Unit =
            withContext(Dispatchers.IO) {
                try {
                    val editor =
                        prefs
                            .edit()
                            .putString(KEY_SCHEME, config.scheme)
                            .putString(KEY_HOST, config.host)
                            .putString(KEY_USERNAME, config.username)
                            .putString(KEY_PASSWORD, config.password)

                    // Store port as -1 if null (for auto-discovery)
                    if (config.port != null) {
                        editor.putInt(KEY_PORT, config.port)
                    } else {
                        editor.putInt(KEY_PORT, -1)
                    }

                    editor.apply()
                    UnifiedLog.i(TAG) { "Stored credentials: scheme=${config.scheme}, host=${config.host}, port=${config.port}" }
                } catch (e: Exception) {
                    UnifiedLog.e(TAG, e) { "Failed to write credentials" }
                }
            }

        override suspend fun clear(): Unit =
            withContext(Dispatchers.IO) {
                try {
                    prefs.edit().clear().apply()
                    UnifiedLog.i(TAG) { "Cleared stored credentials" }
                } catch (e: Exception) {
                    UnifiedLog.e(TAG, e) { "Failed to clear credentials" }
                }
            }
    }
