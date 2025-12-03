package com.chris.m3usuite.telegram.util

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MP4/MOV container header parser for validating streamability.
 *
 * **Purpose:**
 * Checks if an MP4 file has a complete 'moov' atom within the downloaded prefix.
 * This is essential for progressive streaming - the moov atom contains all metadata
 * needed for playback (track info, codec details, sample tables, etc.).
 *
 * **MP4 Container Structure:**
 * - Files consist of nested boxes/atoms (e.g., ftyp, mdat, moov, free)
 * - Each box has: 4-byte size + 4-byte type + data
 * - 'moov' atom MUST be present and complete for ExoPlayer to start playback
 * - Optimized files have moov at start; non-optimized may have it at the end
 *
 * **TDLib Integration:**
 * - TDLib's `supportsStreaming` flag indicates moov is at the start
 * - But we still validate the actual downloaded bytes for safety
 * - This prevents premature playback starts with incomplete headers
 *
 * **Reference:**
 * - ISO/IEC 14496-12 (MP4 container format)
 * - Apple QuickTime File Format Specification
 */
object Mp4HeaderParser {
    private const val ATOM_HEADER_SIZE = 8L // 4 bytes size + 4 bytes type
    private const val FTYP_ATOM = 0x66747970 // 'ftyp'
    private const val MOOV_ATOM = 0x6D6F6F76 // 'moov'
    private const val MDAT_ATOM = 0x6D646174 // 'mdat'
    private const val FREE_ATOM = 0x66726565 // 'free'
    private const val SKIP_ATOM = 0x736B6970 // 'skip'
    private const val WIDE_ATOM = 0x77696465 // 'wide'

    /**
     * Result of MP4 header validation.
     */
    sealed class ValidationResult {
        /** moov atom found and completely within available data */
        data class MoovComplete(
            val moovOffset: Long,
            val moovSize: Long,
        ) : ValidationResult()

        /** moov atom found but not yet complete */
        data class MoovIncomplete(
            val moovOffset: Long,
            val moovSize: Long,
            val availableBytes: Long,
        ) : ValidationResult()

        /** moov atom not yet found in available data */
        data class MoovNotFound(
            val availableBytes: Long,
            val scannedAtoms: List<String>,
        ) : ValidationResult()

        /** File format invalid or corrupted */
        data class Invalid(
            val reason: String,
        ) : ValidationResult()
    }

    /**
     * Validate if MP4 file has a complete moov atom within the available prefix.
     *
     * @param file Local file path to check
     * @param availableBytes Number of bytes downloaded by TDLib (from downloaded_prefix_size)
     * @return ValidationResult indicating moov atom status
     */
    fun validateMoovAtom(
        file: File,
        availableBytes: Long,
    ): ValidationResult {
        if (!file.exists() || !file.canRead()) {
            return ValidationResult.Invalid("File does not exist or is not readable: ${file.path}")
        }

        if (availableBytes < ATOM_HEADER_SIZE) {
            return ValidationResult.MoovNotFound(
                availableBytes = availableBytes,
                scannedAtoms = emptyList(),
            )
        }

        return try {
            RandomAccessFile(file, "r").use { raf ->
                scanForMoov(raf, availableBytes)
            }
        } catch (e: Exception) {
            ValidationResult.Invalid("Failed to read file: ${e.message}")
        }
    }

    /**
     * Scan MP4 file for moov atom within available bytes.
     */
    private fun scanForMoov(
        raf: RandomAccessFile,
        availableBytes: Long,
    ): ValidationResult {
        val scannedAtoms = mutableListOf<String>()
        var offset = 0L

        // Scan atoms until we find moov or run out of available data
        while (offset + ATOM_HEADER_SIZE <= availableBytes) {
            raf.seek(offset)

            // Read atom header (size + type)
            val sizeBytes = ByteArray(4)
            val typeBytes = ByteArray(4)

            if (raf.read(sizeBytes) != 4 || raf.read(typeBytes) != 4) {
                break // EOF or read error
            }

            val atomSize =
                ByteBuffer
                    .wrap(sizeBytes)
                    .order(ByteOrder.BIG_ENDIAN)
                    .int
                    .toLong()
            val atomType = ByteBuffer.wrap(typeBytes).order(ByteOrder.BIG_ENDIAN).int

            // Handle extended size (atomSize == 1 means 64-bit size follows)
            val actualSize =
                if (atomSize == 1L) {
                    if (offset + 16 > availableBytes) {
                        // Can't read extended size yet
                        break
                    }
                    raf.seek(offset + 8)
                    val extSizeBytes = ByteArray(8)
                    if (raf.read(extSizeBytes) != 8) break
                    ByteBuffer.wrap(extSizeBytes).order(ByteOrder.BIG_ENDIAN).long
                } else if (atomSize == 0L) {
                    // atomSize 0 means "to end of file" - not useful for our validation
                    availableBytes - offset
                } else {
                    atomSize
                }

            // Validate size sanity
            if (actualSize < ATOM_HEADER_SIZE) {
                return ValidationResult.Invalid(
                    "Invalid atom size: $actualSize at offset $offset (type=${atomTypeToString(atomType)})",
                )
            }

            val atomName = atomTypeToString(atomType)
            scannedAtoms.add(atomName)

            // Check if this is the moov atom
            if (atomType == MOOV_ATOM) {
                val moovEnd = offset + actualSize

                return if (moovEnd <= availableBytes) {
                    // moov is complete within available data
                    ValidationResult.MoovComplete(
                        moovOffset = offset,
                        moovSize = actualSize,
                    )
                } else {
                    // moov started but not yet complete
                    ValidationResult.MoovIncomplete(
                        moovOffset = offset,
                        moovSize = actualSize,
                        availableBytes = availableBytes,
                    )
                }
            }

            // Move to next atom
            offset += actualSize

            // Safety check: avoid infinite loops on malformed files
            if (actualSize <= 0 || offset >= availableBytes * 2) {
                break
            }
        }

        // moov not found in scanned range
        return ValidationResult.MoovNotFound(
            availableBytes = availableBytes,
            scannedAtoms = scannedAtoms,
        )
    }

    /**
     * Convert 4-byte atom type to readable string.
     */
    private fun atomTypeToString(type: Int): String {
        val bytes =
            ByteBuffer
                .allocate(4)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(type)
                .array()
        return String(bytes, Charsets.ISO_8859_1)
    }

    /**
     * Quick check if file looks like valid MP4/MOV without parsing full structure.
     * Checks for common ftyp signatures at start of file.
     */
    fun isValidMp4Start(file: File): Boolean {
        if (!file.exists() || !file.canRead()) return false

        return try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 12) return false

                val header = ByteArray(12)
                if (raf.read(header) != 12) return false

                // Check for ftyp atom at start
                val atomType = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.BIG_ENDIAN).int
                atomType == FTYP_ATOM
            }
        } catch (e: Exception) {
            false
        }
    }
}
