package com.chris.m3usuite.core.cache

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for CacheManager.
 *
 * Tests basic cache clearing functionality using temporary directories.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CacheManagerTest {
    private lateinit var context: Context
    private lateinit var cacheManager: CacheManager
    private lateinit var tempLogDir: File
    private lateinit var tempTdlibDbDir: File
    private lateinit var tempTdlibFilesDir: File
    private lateinit var tempRarCacheDir: File

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        cacheManager = CacheManager(context)

        // Create temporary directories for testing
        tempLogDir = File(context.filesDir, "logs").apply { mkdirs() }
        tempTdlibDbDir = File(context.noBackupFilesDir, "tdlib-db").apply { mkdirs() }
        tempTdlibFilesDir = File(context.noBackupFilesDir, "tdlib-files").apply { mkdirs() }
        tempRarCacheDir = File(context.cacheDir, "rar").apply { mkdirs() }
    }

    @After
    fun teardown() {
        // Clean up test directories
        tempLogDir.deleteRecursively()
        tempTdlibDbDir.deleteRecursively()
        tempTdlibFilesDir.deleteRecursively()
        tempRarCacheDir.deleteRecursively()
    }

    @Test
    fun `clearLogCache with empty directory returns success`() =
        runBlocking {
            val result = cacheManager.clearLogCache()

            assertTrue("Should succeed with empty directory", result.success)
            assertEquals("Should delete 0 files", 0, result.filesDeleted)
            assertEquals("Should free 0 bytes", 0L, result.bytesFreed)
        }

    @Test
    fun `clearLogCache deletes files in log directory`() =
        runBlocking {
            // Create test files
            File(tempLogDir, "test1.log").writeText("test content 1")
            File(tempLogDir, "test2.log").writeText("test content 2")

            val result = cacheManager.clearLogCache()

            assertTrue("Should succeed", result.success)
            assertEquals("Should delete 2 files", 2, result.filesDeleted)
            assertTrue("Should free some bytes", result.bytesFreed > 0)
        }

    @Test
    fun `clearTdlibCache handles missing directories gracefully`() =
        runBlocking {
            // Delete directories to simulate missing cache
            tempTdlibDbDir.deleteRecursively()
            tempTdlibFilesDir.deleteRecursively()

            val result = cacheManager.clearTdlibCache()

            assertTrue("Should succeed even with missing directories", result.success)
            assertEquals("Should delete 0 files", 0, result.filesDeleted)
        }

    @Test
    fun `clearTdlibCache clears both db and files directories`() =
        runBlocking {
            // Create test files in both directories
            File(tempTdlibDbDir, "db_file.db").writeText("database content")
            File(tempTdlibFilesDir, "media_file.mp4").writeText("media content")

            val result = cacheManager.clearTdlibCache()

            assertTrue("Should succeed", result.success)
            assertEquals("Should delete 2 files", 2, result.filesDeleted)
            assertTrue("Should free some bytes", result.bytesFreed > 0)
        }

    @Test
    fun `clearXtreamCache handles empty cache`() =
        runBlocking {
            val result = cacheManager.clearXtreamCache()

            assertTrue("Should succeed with empty cache", result.success)
            assertEquals("Should delete 0 files", 0, result.filesDeleted)
        }

    @Test
    fun `clearAllCaches combines results from all cache operations`() =
        runBlocking {
            // Create test files in multiple directories
            File(tempLogDir, "log.txt").writeText("log")
            File(tempTdlibDbDir, "db.db").writeText("db")
            File(tempRarCacheDir, "rar.rar").writeText("rar")

            val result = cacheManager.clearAllCaches()

            assertTrue("Should succeed", result.success)
            assertEquals("Should delete 3 files", 3, result.filesDeleted)
            assertTrue("Should free some bytes", result.bytesFreed > 0)
        }

    @Test
    fun `CacheResult can store error messages`() {
        val result = CacheResult(
            success = false,
            filesDeleted = 0,
            bytesFreed = 0L,
            errorMessage = "Test error message",
        )

        assertFalse("Should be marked as failure", result.success)
        assertNotNull("Should have error message", result.errorMessage)
        assertEquals("Error message should match", "Test error message", result.errorMessage)
    }

    @Test
    fun `clearLogCache handles nested directories`() =
        runBlocking {
            // Create nested directory structure
            val subDir = File(tempLogDir, "subdir").apply { mkdirs() }
            File(tempLogDir, "root_file.log").writeText("root content")
            File(subDir, "nested_file.log").writeText("nested content")

            val result = cacheManager.clearLogCache()

            assertTrue("Should succeed", result.success)
            assertTrue("Should delete at least 2 files (may include directory)", result.filesDeleted >= 2)
            assertTrue("Should free some bytes", result.bytesFreed > 0)
        }
}
