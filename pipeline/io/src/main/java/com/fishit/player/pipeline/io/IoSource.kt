package com.fishit.player.pipeline.io

/**
 * Represents a source location for IO content.
 *
 * This sealed class hierarchy allows the IO pipeline to represent
 * different types of content sources in a type-safe manner.
 *
 * **Current Implementation:**
 * This is a stub implementation. All source types are defined but not
 * yet connected to real filesystem or Android Storage Access Framework (SAF) operations.
 *
 * **Future Phases:**
 * - Phase 3+: Add real filesystem access for LocalFile
 * - Phase 4+: Integrate Android SAF for Saf source
 * - Phase 5+: Add SMB/network share support
 */
sealed class IoSource {
    /**
     * Local device file path.
     *
     * @property path Absolute file path on the device.
     */
    data class LocalFile(
        val path: String,
    ) : IoSource()

    /**
     * Android Storage Access Framework (SAF) URI.
     *
     * Stub: Not yet connected to Android ContentResolver.
     *
     * @property uri SAF content:// URI.
     */
    data class Saf(
        val uri: String,
    ) : IoSource()

    /**
     * SMB network share.
     *
     * Stub: Network share support is planned for future phases.
     *
     * @property smbUri SMB URI (e.g., smb://server/share/path).
     */
    data class Smb(
        val smbUri: String,
    ) : IoSource()

    /**
     * Generic URI fallback for other schemes.
     *
     * @property uri Generic URI string.
     */
    data class GenericUri(
        val uri: String,
    ) : IoSource()

    /**
     * Returns the URI representation of this source.
     */
    fun toUriString(): String =
        when (this) {
            is LocalFile -> "file://$path"
            is Saf -> uri
            is Smb -> smbUri
            is GenericUri -> uri
        }
}
