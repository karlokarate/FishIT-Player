package com.chris.m3usuite.telegram.core

/**
 * Configuration data class for Telegram API.
 * In Android, this is typically populated from:
 * - BuildConfig (apiId, apiHash)
 * - SharedPreferences or EncryptedSharedPreferences (phoneNumber)
 * - Context.filesDir / Context.noBackupFilesDir (dbDir, filesDir)
 */
data class AppConfig(
    val apiId: Int,
    val apiHash: String,
    val phoneNumber: String,
    val dbDir: String,
    val filesDir: String
)

/**
 * Interface for loading configuration in Android.
 * Implementations should provide configuration from Android-specific sources.
 * 
 * Example implementation:
 * ```
 * class AndroidConfigLoader(
 *     private val context: Context,
 *     private val prefs: SharedPreferences
 * ) : ConfigLoader {
 *     override fun load(): AppConfig {
 *         return AppConfig(
 *             apiId = BuildConfig.TG_API_ID,
 *             apiHash = BuildConfig.TG_API_HASH,
 *             phoneNumber = prefs.getString("tg_phone_number", "") ?: "",
 *             dbDir = File(context.noBackupFilesDir, "td-db").apply { mkdirs() }.absolutePath,
 *             filesDir = File(context.filesDir, "td-files").apply { mkdirs() }.absolutePath
 *         )
 *     }
 * }
 * ```
 */
interface ConfigLoader {
    fun load(): AppConfig
}
