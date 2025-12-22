package com.fishit.player.v2.di

import android.content.Context
import coil3.ImageLoader
import com.fishit.player.core.catalogsync.SourceActivationStore
import com.fishit.player.core.catalogsync.SourceId
import com.fishit.player.core.feature.auth.TelegramAuthRepository
import com.fishit.player.core.feature.auth.TelegramAuthState
import com.fishit.player.feature.settings.ConnectionInfo
import com.fishit.player.feature.settings.ContentCounts
import com.fishit.player.feature.settings.DebugInfoProvider
import com.fishit.player.infra.data.telegram.TelegramContentRepository
import com.fishit.player.infra.data.xtream.XtreamCatalogRepository
import com.fishit.player.infra.data.xtream.XtreamLiveRepository
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.xtream.XtreamCredentialsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [DebugInfoProvider].
 *
 * Provides real system information for DebugViewModel:
 * - Connection status from auth repositories
 * - Cache sizes from file system
 * - Content counts from data repositories
 *
 * **Architecture:**
 * - Lives in app-v2 module (has access to all infra modules)
 * - Injected into DebugViewModel via Hilt
 * - Bridges feature/settings to infra layer
 */
@Singleton
class DefaultDebugInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sourceActivationStore: SourceActivationStore,
    private val telegramAuthRepository: TelegramAuthRepository,
    private val xtreamCredentialsStore: XtreamCredentialsStore,
    private val telegramContentRepository: TelegramContentRepository,
    private val xtreamCatalogRepository: XtreamCatalogRepository,
    private val xtreamLiveRepository: XtreamLiveRepository,
    private val imageLoader: ImageLoader,
) : DebugInfoProvider {

    companion object {
        private const val TAG = "DefaultDebugInfoProvider"
        private const val TDLIB_DB_DIR = "tdlib"
        private const val TDLIB_FILES_DIR = "tdlib-files"
    }

    // =========================================================================
    // Connection Status
    // =========================================================================

    override fun observeTelegramConnection(): Flow<ConnectionInfo> {
        return telegramAuthRepository.authState.map { state ->
            when (state) {
                is TelegramAuthState.Connected -> ConnectionInfo(
                    isConnected = true,
                    details = "Authorized"
                )
                is TelegramAuthState.WaitingForPhone,
                is TelegramAuthState.WaitingForCode,
                is TelegramAuthState.WaitingForPassword -> ConnectionInfo(
                    isConnected = false,
                    details = "Auth in progress..."
                )
                is TelegramAuthState.Error -> ConnectionInfo(
                    isConnected = false,
                    details = "Error: ${state.message}"
                )
                else -> ConnectionInfo(
                    isConnected = false,
                    details = null
                )
            }
        }
    }

    override fun observeXtreamConnection(): Flow<ConnectionInfo> {
        return sourceActivationStore.observeStates().map { snapshot ->
            val isActive = SourceId.XTREAM in snapshot.activeSources
            val storedConfig = runCatching { xtreamCredentialsStore.read() }.getOrNull()
            
            ConnectionInfo(
                isConnected = isActive,
                details = if (isActive && storedConfig != null) {
                    storedConfig.host
                } else {
                    null
                }
            )
        }
    }

    // =========================================================================
    // Cache Sizes
    // =========================================================================

    override suspend fun getTelegramCacheSize(): Long? = withContext(Dispatchers.IO) {
        try {
            // TDLib uses noBackupFilesDir for its data
            val tdlibDir = File(context.noBackupFilesDir, TDLIB_DB_DIR)
            val filesDir = File(context.noBackupFilesDir, TDLIB_FILES_DIR)
            
            var totalSize = 0L
            
            if (tdlibDir.exists()) {
                totalSize += calculateDirectorySize(tdlibDir)
            }
            if (filesDir.exists()) {
                totalSize += calculateDirectorySize(filesDir)
            }
            
            UnifiedLog.d(TAG) { "TDLib cache size: $totalSize bytes" }
            totalSize
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to calculate TDLib cache size" }
            null
        }
    }

    override suspend fun getImageCacheSize(): Long? = withContext(Dispatchers.IO) {
        try {
            // Get Coil disk cache size
            val diskCache = imageLoader.diskCache
            val size = diskCache?.size ?: 0L
            
            UnifiedLog.d(TAG) { "Image cache size: $size bytes" }
            size
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to calculate image cache size" }
            null
        }
    }

    override suspend fun getDatabaseSize(): Long? = withContext(Dispatchers.IO) {
        try {
            // ObjectBox stores data in the app's internal storage
            val objectboxDir = File(context.filesDir, "objectbox")
            val size = if (objectboxDir.exists()) {
                calculateDirectorySize(objectboxDir)
            } else {
                0L
            }
            UnifiedLog.d(TAG) { "Database size: $size bytes" }
            size
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to calculate database size" }
            null
        }
    }

    // =========================================================================
    // Content Counts
    // =========================================================================

    override fun observeContentCounts(): Flow<ContentCounts> {
        return combine(
            // Telegram media count
            telegramContentRepository.observeAll().map { it.size },
            // Xtream VOD count
            xtreamCatalogRepository.observeVod().map { it.size },
            // Xtream series count
            xtreamCatalogRepository.observeSeries().map { it.size },
            // Xtream live count
            xtreamLiveRepository.observeChannels().map { it.size }
        ) { telegramCount, vodCount, seriesCount, liveCount ->
            ContentCounts(
                telegramMediaCount = telegramCount,
                xtreamVodCount = vodCount,
                xtreamSeriesCount = seriesCount,
                xtreamLiveCount = liveCount
            )
        }
    }

    // =========================================================================
    // Cache Actions
    // =========================================================================

    override suspend fun clearTelegramCache(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Only clear files directory, preserve database
            val filesDir = File(context.noBackupFilesDir, TDLIB_FILES_DIR)
            
            if (filesDir.exists()) {
                deleteDirectoryContents(filesDir)
                UnifiedLog.i(TAG) { "Cleared TDLib files cache" }
            }
            true
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to clear TDLib cache" }
            false
        }
    }

    override suspend fun clearImageCache(): Boolean = withContext(Dispatchers.IO) {
        try {
            imageLoader.diskCache?.clear()
            imageLoader.memoryCache?.clear()
            UnifiedLog.i(TAG) { "Cleared image cache" }
            true
        } catch (e: Exception) {
            UnifiedLog.e(TAG, e) { "Failed to clear image cache" }
            false
        }
    }

    // =========================================================================
    // Helper Functions
    // =========================================================================

    private fun calculateDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    private fun deleteDirectoryContents(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        }
    }
}
