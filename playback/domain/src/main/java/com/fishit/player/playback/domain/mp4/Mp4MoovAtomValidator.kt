package com.fishit.player.playback.domain.mp4

import java.io.File
import java.io.RandomAccessFile

/**
 * Result of checking for MP4 moov atom.
 *
 * @property found `true` if moov atom header was located in the scanned prefix
 * @property complete `true` if the entire moov atom has been downloaded
 * @property moovStart Byte offset where moov atom starts (if found)
 * @property moovSize Size of moov atom in bytes (if found)
 */
data class MoovCheckResult(
    val found: Boolean,
    val complete: Boolean,
    val moovStart: Long? = null,
    val moovSize: Long? = null,
) {
    companion object {
        /** Moov atom not found in scanned prefix. */
        val NOT_FOUND = MoovCheckResult(found = false, complete = false)

        /** File too small to contain valid MP4 header. */
        val FILE_TOO_SMALL = MoovCheckResult(found = false, complete = false)

        /** File is not a valid MP4 (missing ftyp box). */
        val INVALID_FORMAT = MoovCheckResult(found = false, complete = false)
    }

    /** `true` if moov was found AND the entire moov data is available. */
    val isReadyForPlayback: Boolean
        get() = found && complete
}

/**
 * Global MP4 moov atom validator for streaming playback.
 *
 * **Design Notes:**
 * - Source-agnostic: Works for Telegram, Xtream, local files, etc.
 * - Uses `availableBytes` parameter for streaming scenarios where file is being downloaded
 * progressively.
 * - Does NOT require file to be fully downloaded.
 *
 * **MP4 Box Format:**
 * ```
 * | 4 bytes: size (big-endian) | 4 bytes: type (ASCII) | size-8 bytes: data |
 * ```
 * For extended size (size==1): actual size in next 8 bytes (big-endian uint64).
 *
 * @see Mp4MoovValidationConfig for configuration constants
 */
object Mp4MoovAtomValidator {
    private const val BOX_HEADER_SIZE = 8
    private const val EXTENDED_SIZE_HEADER = 16
    private const val FTYP = "ftyp"
    private const val MOOV = "moov"

    /**
     * Checks if MP4 file has a valid moov atom header within the available prefix.
     *
     * @param localPath Absolute path to the MP4 file
     * @param availableBytes Number of bytes currently downloaded. Use this instead of
     * ```
     *        `file.length()` for streaming scenarios where file grows progressively.
     * @return [MoovCheckResult]
     * ```
     * with details about moov location and completeness
     */
    fun checkMoovAtom(
        localPath: String,
        availableBytes: Long,
    ): MoovCheckResult {
        val file = File(localPath)
        if (!file.exists() || availableBytes < BOX_HEADER_SIZE) {
            return MoovCheckResult.FILE_TOO_SMALL
        }

        return try {
            RandomAccessFile(file, "r").use { raf -> scanForMoov(raf, availableBytes) }
        } catch (_: Exception) {
            MoovCheckResult.INVALID_FORMAT
        }
    }

    /**
     * Scans MP4 box structure looking for moov atom.
     *
     * Box scan algorithm:
     * 1. Read 8-byte header (size + type)
     * 2. If size==1, read 8 more bytes for extended size
     * 3. Check if box type is "moov"
     * 4. Skip to next box (current position + size - header)
     * 5. Stop if exceeded MAX_PREFIX_SCAN_BYTES or availableBytes
     */
    private fun scanForMoov(
        raf: RandomAccessFile,
        availableBytes: Long,
    ): MoovCheckResult {
        var position = 0L
        var foundFtyp = false
        val maxScan = minOf(availableBytes, Mp4MoovValidationConfig.MAX_PREFIX_SCAN_BYTES)

        while (position + BOX_HEADER_SIZE <= maxScan) {
            raf.seek(position)

            // Read box header
            val header = ByteArray(BOX_HEADER_SIZE)
            val bytesRead = raf.read(header)
            if (bytesRead < BOX_HEADER_SIZE) break

            val boxSize = header.toUInt32(0)
            val boxType = String(header, 4, 4, Charsets.US_ASCII)

            // Handle extended size (size == 1)
            val actualSize: Long =
                when {
                    boxSize == 1L -> {
                        if (position + EXTENDED_SIZE_HEADER > maxScan) break
                        val extHeader = ByteArray(8)
                        if (raf.read(extHeader) < 8) break
                        extHeader.toUInt64(0)
                    }
                    boxSize == 0L -> {
                        // Box extends to end of file
                        availableBytes - position
                    }
                    else -> boxSize
                }

            // Validate box size is sane
            if (actualSize < BOX_HEADER_SIZE) {
                return MoovCheckResult.INVALID_FORMAT
            }

            when (boxType) {
                FTYP -> foundFtyp = true
                MOOV -> {
                    // Must have seen ftyp first for valid MP4
                    if (!foundFtyp) return MoovCheckResult.INVALID_FORMAT

                    val moovEnd = position + actualSize
                    val complete = moovEnd <= availableBytes

                    return MoovCheckResult(
                        found = true,
                        complete = complete,
                        moovStart = position,
                        moovSize = actualSize,
                    )
                }
            }

            // Advance to next box
            position += actualSize
        }

        // Scanned prefix without finding moov
        return if (!foundFtyp) {
            MoovCheckResult.INVALID_FORMAT
        } else {
            MoovCheckResult.NOT_FOUND
        }
    }

    /** Reads 4 bytes as big-endian unsigned int32. */
    private fun ByteArray.toUInt32(offset: Int): Long =
        ((this[offset].toLong() and 0xFF) shl 24) or
            ((this[offset + 1].toLong() and 0xFF) shl 16) or
            ((this[offset + 2].toLong() and 0xFF) shl 8) or
            (this[offset + 3].toLong() and 0xFF)

    /** Reads 8 bytes as big-endian unsigned int64. */
    private fun ByteArray.toUInt64(offset: Int): Long =
        ((this[offset].toLong() and 0xFF) shl 56) or
            ((this[offset + 1].toLong() and 0xFF) shl 48) or
            ((this[offset + 2].toLong() and 0xFF) shl 40) or
            ((this[offset + 3].toLong() and 0xFF) shl 32) or
            ((this[offset + 4].toLong() and 0xFF) shl 24) or
            ((this[offset + 5].toLong() and 0xFF) shl 16) or
            ((this[offset + 6].toLong() and 0xFF) shl 8) or
            (this[offset + 7].toLong() and 0xFF)
}
