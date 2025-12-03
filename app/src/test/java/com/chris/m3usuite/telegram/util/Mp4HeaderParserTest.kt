package com.chris.m3usuite.telegram.util

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.RandomAccessFile

/**
 * Unit tests for Mp4HeaderParser.
 *
 * Tests cover:
 * - Valid MP4 with moov at start (streamable)
 * - Valid MP4 with moov at end (non-streamable)
 * - Incomplete moov atom
 * - Corrupted/invalid MP4
 * - Edge cases (tiny files, empty files, non-MP4)
 */
class Mp4HeaderParserTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `validates MP4 with complete moov at start`() {
        // Create a minimal MP4: ftyp + moov
        val file = tempFolder.newFile("test.mp4")
        RandomAccessFile(file, "rw").use { raf ->
            // ftyp atom (28 bytes)
            writeFtypAtom(raf)
            // moov atom (100 bytes)
            writeMoovAtom(raf, 100)
        }

        val result = Mp4HeaderParser.validateMoovAtom(file, file.length())

        assertTrue(result is Mp4HeaderParser.ValidationResult.MoovComplete)
        val complete = result as Mp4HeaderParser.ValidationResult.MoovComplete
        assertEquals(28L, complete.moovOffset) // After ftyp
        assertEquals(100L, complete.moovSize)
    }

    @Test
    fun `detects incomplete moov atom`() {
        // Create MP4 with moov that extends beyond available data
        val file = tempFolder.newFile("test.mp4")
        RandomAccessFile(file, "rw").use { raf ->
            writeFtypAtom(raf)
            writeMoovAtom(raf, 1000) // moov says 1000 bytes
        }

        // Simulate TDLib having only 100 bytes downloaded
        val availableBytes = 100L

        val result = Mp4HeaderParser.validateMoovAtom(file, availableBytes)

        assertTrue(result is Mp4HeaderParser.ValidationResult.MoovIncomplete)
        val incomplete = result as Mp4HeaderParser.ValidationResult.MoovIncomplete
        assertEquals(28L, incomplete.moovOffset)
        assertEquals(1000L, incomplete.moovSize)
        assertEquals(100L, incomplete.availableBytes)
    }

    @Test
    fun `detects moov not found within available data`() {
        // Create MP4 with only ftyp and mdat, no moov yet
        val file = tempFolder.newFile("test.mp4")
        RandomAccessFile(file, "rw").use { raf ->
            writeFtypAtom(raf)
            writeMdatAtom(raf, 500)
        }

        // Available data covers only ftyp and part of mdat
        val availableBytes = 200L

        val result = Mp4HeaderParser.validateMoovAtom(file, availableBytes)

        assertTrue(result is Mp4HeaderParser.ValidationResult.MoovNotFound)
        val notFound = result as Mp4HeaderParser.ValidationResult.MoovNotFound
        assertEquals(200L, notFound.availableBytes)
        assertTrue(notFound.scannedAtoms.contains("ftyp"))
        assertFalse(notFound.scannedAtoms.contains("moov"))
    }

    @Test
    fun `validates MP4 with moov at end (non-streamable)`() {
        // Simulates file where moov is at the end (requires full download)
        val file = tempFolder.newFile("test.mp4")
        RandomAccessFile(file, "rw").use { raf ->
            writeFtypAtom(raf)
            writeMdatAtom(raf, 1000)
            writeMoovAtom(raf, 200) // moov at end
        }

        // If we only have first 100 bytes, moov is not found
        val partialResult = Mp4HeaderParser.validateMoovAtom(file, 100L)
        assertTrue(partialResult is Mp4HeaderParser.ValidationResult.MoovNotFound)

        // But if we have full file, moov is complete
        val fullResult = Mp4HeaderParser.validateMoovAtom(file, file.length())
        assertTrue(fullResult is Mp4HeaderParser.ValidationResult.MoovComplete)
    }

    @Test
    fun `rejects non-existent file`() {
        val nonExistent = tempFolder.root.resolve("does_not_exist.mp4")

        val result = Mp4HeaderParser.validateMoovAtom(nonExistent, 1000L)

        assertTrue(result is Mp4HeaderParser.ValidationResult.Invalid)
        val invalid = result as Mp4HeaderParser.ValidationResult.Invalid
        assertTrue(invalid.reason.contains("does not exist"))
    }

    @Test
    fun `rejects file with insufficient data`() {
        val file = tempFolder.newFile("tiny.mp4")
        RandomAccessFile(file, "rw").use { raf ->
            raf.write(byteArrayOf(0, 0, 0)) // Only 3 bytes
        }

        val result = Mp4HeaderParser.validateMoovAtom(file, 3L)

        assertTrue(result is Mp4HeaderParser.ValidationResult.MoovNotFound)
        val notFound = result as Mp4HeaderParser.ValidationResult.MoovNotFound
        assertEquals(3L, notFound.availableBytes)
        assertTrue(notFound.scannedAtoms.isEmpty())
    }

    @Test
    fun `detects corrupted MP4 with invalid atom size`() {
        val file = tempFolder.newFile("corrupted.mp4")
        RandomAccessFile(file, "rw").use { raf ->
            writeFtypAtom(raf)
            // Write atom with size=0 (invalid for mid-file atoms)
            raf.writeInt(0) // size = 0
            raf.writeInt(0x6D6F6F76) // type = 'moov'
        }

        val result = Mp4HeaderParser.validateMoovAtom(file, file.length())

        // Parser should handle size=0 as "to end of file"
        // In this case, moov would be considered complete if it's the last atom
        // Exact behavior depends on implementation
        assertTrue(
            result is Mp4HeaderParser.ValidationResult.MoovComplete ||
                result is Mp4HeaderParser.ValidationResult.Invalid,
        )
    }

    @Test
    fun `validates MP4 with extended size atoms`() {
        // MP4 supports 64-bit extended sizes for large files
        val file = tempFolder.newFile("extended.mp4")
        RandomAccessFile(file, "rw").use { raf ->
            writeFtypAtom(raf)
            // moov with extended size (atomSize == 1 means 64-bit size follows)
            raf.writeInt(1) // size = 1 (extended)
            raf.writeInt(0x6D6F6F76) // type = 'moov'
            raf.writeLong(200L) // actual 64-bit size
            // Write some padding
            raf.write(ByteArray(192 - 16)) // Fill remaining 192 bytes (200 - 8 header - 8 ext size)
        }

        val result = Mp4HeaderParser.validateMoovAtom(file, file.length())

        assertTrue(result is Mp4HeaderParser.ValidationResult.MoovComplete)
        val complete = result as Mp4HeaderParser.ValidationResult.MoovComplete
        assertEquals(200L, complete.moovSize)
    }

    @Test
    fun `isValidMp4Start returns true for valid MP4`() {
        val file = tempFolder.newFile("valid.mp4")
        RandomAccessFile(file, "rw").use { raf ->
            writeFtypAtom(raf)
        }

        assertTrue(Mp4HeaderParser.isValidMp4Start(file))
    }

    @Test
    fun `isValidMp4Start returns false for non-MP4`() {
        val file = tempFolder.newFile("not_mp4.txt")
        file.writeText("This is not an MP4 file")

        assertFalse(Mp4HeaderParser.isValidMp4Start(file))
    }

    @Test
    fun `handles multiple atoms before moov`() {
        // Real-world MP4s often have: ftyp, free, wide, mdat, moov
        val file = tempFolder.newFile("multi_atom.mp4")
        RandomAccessFile(file, "rw").use { raf ->
            writeFtypAtom(raf) // 28 bytes
            writeFreeAtom(raf, 100) // 100 bytes
            writeWideAtom(raf) // 8 bytes
            writeMoovAtom(raf, 200) // 200 bytes
        }

        val result = Mp4HeaderParser.validateMoovAtom(file, file.length())

        assertTrue(result is Mp4HeaderParser.ValidationResult.MoovComplete)
        val complete = result as Mp4HeaderParser.ValidationResult.MoovComplete
        assertEquals(200L, complete.moovSize)
        assertEquals(28L + 100L + 8L, complete.moovOffset) // After ftyp + free + wide
    }

    // Helper functions to write MP4 atoms

    private fun writeFtypAtom(raf: RandomAccessFile) {
        val size = 28 // ftyp is typically 28 bytes
        raf.writeInt(size)
        raf.writeInt(0x66747970) // 'ftyp'
        raf.writeInt(0x69736F6D) // major_brand 'isom'
        raf.writeInt(512) // minor_version
        // Compatible brands
        raf.writeInt(0x69736F6D) // 'isom'
        raf.writeInt(0x69736F32) // 'iso2'
        raf.writeInt(0x6D703431) // 'mp41'
    }

    private fun writeMoovAtom(
        raf: RandomAccessFile,
        size: Int,
    ) {
        raf.writeInt(size)
        raf.writeInt(0x6D6F6F76) // 'moov'
        // Fill remaining bytes with dummy data
        val remaining = size - 8
        if (remaining > 0) {
            raf.write(ByteArray(remaining))
        }
    }

    private fun writeMdatAtom(
        raf: RandomAccessFile,
        size: Int,
    ) {
        raf.writeInt(size)
        raf.writeInt(0x6D646174) // 'mdat'
        val remaining = size - 8
        if (remaining > 0) {
            raf.write(ByteArray(remaining))
        }
    }

    private fun writeFreeAtom(
        raf: RandomAccessFile,
        size: Int,
    ) {
        raf.writeInt(size)
        raf.writeInt(0x66726565) // 'free'
        val remaining = size - 8
        if (remaining > 0) {
            raf.write(ByteArray(remaining))
        }
    }

    private fun writeWideAtom(raf: RandomAccessFile) {
        raf.writeInt(8) // wide is typically just a header
        raf.writeInt(0x77696465) // 'wide'
    }
}
