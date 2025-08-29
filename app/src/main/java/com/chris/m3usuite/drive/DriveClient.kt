package com.chris.m3usuite.drive

import android.app.Activity
import android.content.Context

/**
 * Minimal Drive client shim to avoid compile-time dependency issues.
 * If Google Play Services + Drive REST libs are available, replace with a full implementation.
 */
object DriveDefaults { const val DEFAULT_FOLDER_ID = "18UrvlyDSHdmmf3jBm5A0jUGzzDo38-4E" }

object DriveClient {
    fun isSignedIn(@Suppress("UNUSED_PARAMETER") ctx: Context): Boolean = false
    fun signIn(@Suppress("UNUSED_PARAMETER") activity: Activity, onResult: (Boolean) -> Unit) { onResult(false) }
    fun uploadBytes(
        @Suppress("UNUSED_PARAMETER") ctx: Context,
        @Suppress("UNUSED_PARAMETER") folderId: String,
        @Suppress("UNUSED_PARAMETER") name: String,
        @Suppress("UNUSED_PARAMETER") mime: String,
        @Suppress("UNUSED_PARAMETER") bytes: ByteArray
    ): String = throw UnsupportedOperationException("Drive upload not available")

    fun downloadLatestByPrefix(
        @Suppress("UNUSED_PARAMETER") ctx: Context,
        @Suppress("UNUSED_PARAMETER") folderId: String,
        @Suppress("UNUSED_PARAMETER") namePrefix: String
    ): ByteArray? = null
}
