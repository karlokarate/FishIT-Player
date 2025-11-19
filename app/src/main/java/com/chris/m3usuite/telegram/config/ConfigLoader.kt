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
        phoneNumber: String = ""
    ): AppConfig {
        // Use provided values or fall back to BuildConfig defaults
        val finalApiId = apiId ?: 0  // Should be set via BuildConfig or Settings
        val finalApiHash = apiHash ?: ""  // Should be set via BuildConfig or Settings
        
        // Use Android app directories for database and files
        // noBackupFilesDir is used to prevent backup of TDLib database (contains local cache)
        val dbDir = File(context.noBackupFilesDir, "tdlib-db").apply { 
            mkdirs() 
        }.absolutePath
        
        val filesDir = File(context.noBackupFilesDir, "tdlib-files").apply { 
            mkdirs() 
        }.absolutePath

        return AppConfig(
            apiId = finalApiId,
            apiHash = finalApiHash,
            phoneNumber = phoneNumber,
            dbDir = dbDir,
            filesDir = filesDir
        )
    }
}
