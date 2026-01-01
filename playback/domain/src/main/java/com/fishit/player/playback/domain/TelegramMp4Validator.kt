package com.fishit.player.playback.domain

import kotlinx.coroutines.delay
import java.io.File
import java.io.RandomAccessFile

/**
 * MP4 header validation for Telegram file playback.
 *
 * @deprecated Use [com.fishit.player.playback.domain.mp4.Mp4MoovAtomValidator] instead. This class
 * is retained for backward compatibility but will be removed in a future release.
 *
 * **Migration Guide:**
 * ```kotlin
 * // Old:
 * TelegramMp4Validator.ensureFileReadyWithMp4Validation(file, timeout)
 *
 * // New:
 * val result = Mp4MoovAtomValidator.checkMoovAtom(file.absolutePath, file.length())
 * if (!result.isReadyForPlayback) {
 *     throw Mp4ValidationException("Moov not ready")
 * }
 * ```
 *
 * @see com.fishit.player.playback.domain.mp4.Mp4MoovAtomValidator
 * @see com.fishit.player.playback.domain.mp4.Mp4MoovValidationConfig
 */
@Deprecated(
    message = "Use Mp4MoovAtomValidator from playback.domain.mp4 package instead",
    replaceWith =
        ReplaceWith(
            "Mp4MoovAtomValidator.checkMoovAtom(file.absolutePath, file.length())",
            "com.fishit.player.playback.domain.mp4.Mp4MoovAtomValidator",
        ),
)
object TelegramMp4Validator {
    /** Minimum bytes needed to check for atoms. */
    private const val MIN_HEADER_SIZE = 8L

    /** Check interval while waiting for moov. */
    private const val CHECK_INTERVAL_MS = 100L

    /** Common MP4 container types (for quick validation). */
    private val MP4_FTYPES = setOf("ftyp", "styp")

    /**
     * Wait for MP4 moov atom to be available in downloaded prefix.
     *
     * @param file Local file being downloaded
     * @param timeoutMillis Maximum time to wait for moov atom
     * @throws Mp4ValidationException if moov not found within timeout
     * @throws Mp4ValidationException if file is not a valid MP4
     */
    @Deprecated("Use Mp4MoovAtomValidator.checkMoovAtom() instead")
    suspend fun ensureFileReadyWithMp4Validation(
        file: File,
        timeoutMillis: Long = 30_000L,
    ) {
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            try {
                val result = checkMoovAtom(file)
                when (result) {
                    MoovCheckResult.FOUND -> return // Ready to play!
                    MoovCheckResult.NOT_YET -> {
                        delay(CHECK_INTERVAL_MS)
                        continue
                    }
                    MoovCheckResult.INVALID_FORMAT -> {
                        throw Mp4ValidationException(
                            "File is not a valid MP4 container: ${file.name}",
                        )
                    }
                    MoovCheckResult.FILE_TOO_SMALL -> {
                        delay(CHECK_INTERVAL_MS)
                        continue
                    }
                }
            } catch (e: Mp4ValidationException) {
                throw e
            } catch (e: Exception) {
                // File read error, likely still downloading
                delay(CHECK_INTERVAL_MS)
            }
        }

        throw Mp4ValidationException(
            "Timeout waiting for moov atom in ${file.name}. " +
                "Downloaded: ${file.length()} bytes after ${timeoutMillis}ms",
        )
    }

    /**
     * Quick check for moov atom presence (non-blocking).
     *
     * @param file File to check
     * @return true if moov atom is present
     */
    fun hasMoovAtom(file: File): Boolean =
        try {
            checkMoovAtom(file) == MoovCheckResult.FOUND
        } catch (e: Exception) {
            false
        }

    /** Parse file and check for moov atom. */
    private fun checkMoovAtom(file: File): MoovCheckResult {
        if (!file.exists() || file.length() < MIN_HEADER_SIZE) {
            return MoovCheckResult.FILE_TOO_SMALL
        }

        RandomAccessFile(file, "r").use { raf ->
            val fileSize = raf.length()
            var position = 0L
            var foundFtyp = false

            while (position + 8 <= fileSize) {
                raf.seek(position)

                // Read atom size (big-endian)
                val sizeBytes = ByteArray(4)
                if (raf.read(sizeBytes) != 4) break
                val size = sizeBytes.toUInt32()

                // Read atom type (ASCII)
                val typeBytes = ByteArray(4)
                if (raf.read(typeBytes) != 4) break
                val type = String(typeBytes, Charsets.US_ASCII)

                // Check for valid MP4 start
                if (position == 0L) {
                    if (type !in MP4_FTYPES) {
                        return MoovCheckResult.INVALID_FORMAT
                    }
                    foundFtyp = true
                }

                // Found moov!
                if (type == "moov") {
                    // Verify we have the complete moov atom
                    return if (position + size <= fileSize) {
                        MoovCheckResult.FOUND
                    } else {
                        MoovCheckResult.NOT_YET // Moov partially downloaded
                    }
                }

                // Handle special size values
                val atomSize =
                    when {
                        size == 0L -> fileSize - position // Atom extends to EOF
                        size == 1L -> {
                            // Extended size (64-bit) after type
                            if (position + 16 > fileSize) break
                            val extSizeBytes = ByteArray(8)
                            if (raf.read(extSizeBytes) != 8) break
                            extSizeBytes.toUInt64()
                        }
                        size < 8 -> return MoovCheckResult.INVALID_FORMAT
                        else -> size
                    }

                position += atomSize
            }

            // Reached end of available data without finding moov
            return if (foundFtyp) MoovCheckResult.NOT_YET else MoovCheckResult.INVALID_FORMAT
        }
    }

    /** Convert 4 bytes to unsigned 32-bit integer (big-endian). */
    private fun ByteArray.toUInt32(): Long =
        ((this[0].toLong() and 0xFF) shl 24) or
            ((this[1].toLong() and 0xFF) shl 16) or
            ((this[2].toLong() and 0xFF) shl 8) or
            (this[3].toLong() and 0xFF)

    /** Convert 8 bytes to unsigned 64-bit integer (big-endian). */
    private fun ByteArray.toUInt64(): Long =
        ((this[0].toLong() and 0xFF) shl 56) or
            ((this[1].toLong() and 0xFF) shl 48) or
            ((this[2].toLong() and 0xFF) shl 40) or
            ((this[3].toLong() and 0xFF) shl 32) or
            ((this[4].toLong() and 0xFF) shl 24) or
            ((this[5].toLong() and 0xFF) shl 16) or
            ((this[6].toLong() and 0xFF) shl 8) or
            (this[7].toLong() and 0xFF)
}

/** Result of moov atom check. */
private enum class MoovCheckResult {
    /** Moov atom found and complete. */
    FOUND,

    /** Valid MP4 but moov not yet available (still downloading). */
    NOT_YET,

    /** File is too small to determine structure. */
    FILE_TOO_SMALL,

    /** File is not a valid MP4 container. */
    INVALID_FORMAT,
}

/** Exception thrown when MP4 validation fails. */
class Mp4ValidationException(
    message: String,
) : Exception(message)
