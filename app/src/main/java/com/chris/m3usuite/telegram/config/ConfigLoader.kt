package com.chris.m3usuite.telegram.config

import android.content.Context
import com.chris.m3usuite.BuildConfig
import java.io.File

/**
 * Android-specific configuration loader for TDLib.
 * Loads API credentials from BuildConfig or SharedPreferences and sets up proper Android directories.
 */
object ConfigLoader {
    /**
     * Load TDLib configuration for Android.
     *
     * @param context Android context for accessing app directories
     * @param apiId Optional override for API ID (defaults to BuildConfig or 0)
     * @param apiHash Optional override for API Hash (defaults to BuildConfig or empty)
     * @param phoneNumber User's phone number for authentication
     * @return Configured AppConfig instance
     */
    fun load(
        context: Context,
        apiId: Int? = null,
        apiHash: String? = null,
        phoneNumber: String = "",
    ): AppConfig {
        // Use provided overrides if valid, otherwise fall back to BuildConfig
        val effectiveApiId = apiId?.takeIf { it != 0 } ?: BuildConfig.TG_API_ID
        val effectiveApiHash = apiHash?.takeIf { it.isNotBlank() } ?: BuildConfig.TG_API_HASH

        // Validate that we have credentials from either override or BuildConfig
        require(effectiveApiId != 0 && effectiveApiHash.isNotBlank()) {
            "TDLib API credentials not configured (BuildConfig + overrides are empty)"
        }

        // Use Android app directories for database and files
        // noBackupFilesDir is used to prevent backup of TDLib database (contains local cache)
        val dbDir =
            File(context.noBackupFilesDir, "tdlib-db")
                .apply {
                    mkdirs()
                }.absolutePath

        val filesDir =
            File(context.noBackupFilesDir, "tdlib-files")
                .apply {
                    mkdirs()
                }.absolutePath

        return AppConfig(
            apiId = effectiveApiId,
            apiHash = effectiveApiHash,
            phoneNumber = phoneNumber,
            dbDir = dbDir,
            filesDir = filesDir,
        )
    }
}
