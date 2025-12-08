package com.chris.m3usuite.telegram.config

/**
 * Configuration data for TDLib initialization.
 * This holds the API credentials and directory paths needed to start a TDLib client.
 */
data class AppConfig(
    val apiId: Int,
    val apiHash: String,
    val phoneNumber: String,
    val dbDir: String,
    val filesDir: String,
)
