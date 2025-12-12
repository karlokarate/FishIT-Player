package com.fishit.player.playback.domain.mp4

import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for [Mp4MoovAtomValidator].
 *
 * Tests cover:
 * - Valid MP4 with moov atom at start
 * - Incomplete moov (found but not fully downloaded)
 * - Moov not found in scanned prefix
 * - Invalid MP4 (missing ftyp)
 * - Extended size box handling
 * - Edge cases (empty file, too small file)
 */
class Mp4MoovAtomValidatorTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var testFile: File

    @Before
    fun setup() {
        testFile = tempFolder.newFile("test.mp4")
    }

    // ========== Helper Methods ==========

    private fun writeBytes(vararg bytes: Int) {
        testFile.writeBytes(bytes.map { it.toByte() }.toByteArray())
    }

    private fun writeMp4Header(
            ftypSize: Int = 20,
            moovSize: Int = 100,
            moovDataSize: Int = moovSize - 8, // Full moov data by default
    ) {
        RandomAccessFile(testFile, "rw").use { raf ->
            // ftyp box
            raf.writeInt(ftypSize) // size
            raf.writeBytes("ftyp") // type
            raf.write(ByteArray(ftypSize - 8)) // data

            // moov box (potentially incomplete)
            raf.writeInt(moovSize) // declared size
            raf.writeBytes("moov") // type
            raf.write(ByteArray(moovDataSize)) // actual data (may be less than declared)
        }
    }

    // ========== Test Cases ==========

    @Test
    fun `valid MP4 with complete moov returns FOUND and COMPLETE`() {
        writeMp4Header(ftypSize = 20, moovSize = 100)
        val fileSize = testFile.length()

        val result = Mp4MoovAtomValidator.checkMoovAtom(testFile.absolutePath, fileSize)

        assertTrue("Should find moov", result.found)
        assertTrue("Should be complete", result.complete)
        assertTrue("Should be ready for playback", result.isReadyForPlayback)
        assertEquals(20L, result.moovStart)
        assertEquals(100L, result.moovSize)
    }

    @Test
    fun `incomplete moov returns FOUND but not COMPLETE`() {
        // Write a moov that declares 100 bytes but only has 50 bytes of data
        writeMp4Header(ftypSize = 20, moovSize = 100, moovDataSize = 50)
        val fileSize = testFile.length()

        val result = Mp4MoovAtomValidator.checkMoovAtom(testFile.absolutePath, fileSize)

        assertTrue("Should find moov", result.found)
        assertFalse("Should not be complete", result.complete)
        assertFalse("Should not be ready for playback", result.isReadyForPlayback)
        assertEquals(20L, result.moovStart)
        assertEquals(100L, result.moovSize)
    }

    @Test
    fun `moov not found in prefix returns NOT_FOUND`() {
        // Write just ftyp, no moov
        RandomAccessFile(testFile, "rw").use { raf ->
            raf.writeInt(20) // ftyp size
            raf.writeBytes("ftyp")
            raf.write(ByteArray(12)) // ftyp data

            // Some other box (not moov)
            raf.writeInt(50)
            raf.writeBytes("mdat")
            raf.write(ByteArray(42))
        }

        val result = Mp4MoovAtomValidator.checkMoovAtom(testFile.absolutePath, testFile.length())

        assertFalse("Should not find moov", result.found)
        assertFalse("Should not be complete", result.complete)
    }

    @Test
    fun `file without ftyp returns INVALID_FORMAT`() {
        // Write moov without ftyp first
        RandomAccessFile(testFile, "rw").use { raf ->
            raf.writeInt(100)
            raf.writeBytes("moov")
            raf.write(ByteArray(92))
        }

        val result = Mp4MoovAtomValidator.checkMoovAtom(testFile.absolutePath, testFile.length())

        assertEquals(MoovCheckResult.INVALID_FORMAT, result)
    }

    @Test
    fun `empty file returns FILE_TOO_SMALL`() {
        // testFile is already empty

        val result = Mp4MoovAtomValidator.checkMoovAtom(testFile.absolutePath, 0L)

        assertEquals(MoovCheckResult.FILE_TOO_SMALL, result)
    }

    @Test
    fun `file too small for header returns FILE_TOO_SMALL`() {
        writeBytes(0, 0, 0, 8) // 4 bytes - not enough for box header

        val result = Mp4MoovAtomValidator.checkMoovAtom(testFile.absolutePath, 4L)

        assertEquals(MoovCheckResult.FILE_TOO_SMALL, result)
    }

    @Test
    fun `availableBytes constrains completeness check`() {
        // Write complete file
        writeMp4Header(ftypSize = 20, moovSize = 100)
        val fullSize = testFile.length()

        // But tell validator we only have 50 bytes available
        val result = Mp4MoovAtomValidator.checkMoovAtom(testFile.absolutePath, 50L)

        // With only 50 bytes, we should be past ftyp (20 bytes) and into moov
        // moov starts at 20, ends at 120, but we only have 50 bytes
        assertTrue("Should find moov", result.found)
        assertFalse("Should not be complete with limited bytes", result.complete)
    }

    @Test
    fun `handles box with extended size`() {
        RandomAccessFile(testFile, "rw").use { raf ->
            // ftyp with extended size (size field = 1)
            raf.writeInt(1) // size = 1 means extended size follows
            raf.writeBytes("ftyp")
            raf.writeLong(24L) // actual size in 8 bytes
            raf.write(ByteArray(8)) // remaining data

            // moov box
            raf.writeInt(50)
            raf.writeBytes("moov")
            raf.write(ByteArray(42))
        }

        val result = Mp4MoovAtomValidator.checkMoovAtom(testFile.absolutePath, testFile.length())

        assertTrue("Should find moov after extended ftyp", result.found)
        assertTrue("Should be complete", result.complete)
    }

    @Test
    fun `respects MAX_PREFIX_SCAN_BYTES limit`() {
        // Write a very large file with moov after MAX_PREFIX_SCAN_BYTES
        val maxScan = Mp4MoovValidationConfig.MAX_PREFIX_SCAN_BYTES
        RandomAccessFile(testFile, "rw").use { raf ->
            // ftyp
            raf.writeInt(20)
            raf.writeBytes("ftyp")
            raf.write(ByteArray(12))

            // Large mdat that pushes moov past MAX_PREFIX_SCAN_BYTES
            val mdatSize = (maxScan + 1000).toInt()
            raf.writeInt(mdatSize)
            raf.writeBytes("mdat")
            raf.write(ByteArray(mdatSize - 8))

            // moov (after max scan limit)
            raf.writeInt(100)
            raf.writeBytes("moov")
            raf.write(ByteArray(92))
        }

        val result = Mp4MoovAtomValidator.checkMoovAtom(testFile.absolutePath, testFile.length())

        // Should not find moov because it's past the scan limit
        assertFalse("Should not find moov past MAX_PREFIX_SCAN_BYTES", result.found)
    }

    @Test
    fun `nonexistent file returns FILE_TOO_SMALL`() {
        val nonexistent = File(tempFolder.root, "nonexistent.mp4")

        val result = Mp4MoovAtomValidator.checkMoovAtom(nonexistent.absolutePath, 0L)

        assertEquals(MoovCheckResult.FILE_TOO_SMALL, result)
    }

    @Test
    fun `MoovCheckResult companion objects are correct`() {
        assertEquals(false, MoovCheckResult.NOT_FOUND.found)
        assertEquals(false, MoovCheckResult.NOT_FOUND.complete)

        assertEquals(false, MoovCheckResult.FILE_TOO_SMALL.found)
        assertEquals(false, MoovCheckResult.FILE_TOO_SMALL.complete)

        assertEquals(false, MoovCheckResult.INVALID_FORMAT.found)
        assertEquals(false, MoovCheckResult.INVALID_FORMAT.complete)
    }
}
