package com.fishit.player.pipeline.io

/**
 * Represents a media item from local/IO storage.
 *
 * This is the domain model for content discovered from:
 * - Device storage
 * - Android Storage Access Framework (SAF)
 * - Network shares (future)
 *
 * **Current Implementation:**
 * This is a stub data model. It defines the structure but is not yet
 * populated by real filesystem scanning or SAF queries.
 *
 * @property id Unique identifier for this item (e.g., hash of source URI).
 * @property source The source location of this media item.
 * @property title Display title (e.g., filename without extension).
 * @property fileName Original filename with extension.
 * @property mimeType MIME type if available (e.g., "video/mp4").
 * @property sizeBytes File size in bytes, null if unknown.
 * @property durationMs Duration in milliseconds, null if unknown or not a video.
 * @property thumbnailPath Optional path to local thumbnail image.
 * @property lastModifiedMs Last modified timestamp in milliseconds since epoch.
 * @property metadata Additional key-value metadata (e.g., codec info, resolution).
 */
data class IoMediaItem(
    val id: String,
    val source: IoSource,
    val title: String,
    val fileName: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val durationMs: Long? = null,
    val thumbnailPath: String? = null,
    val lastModifiedMs: Long = 0L,
    val metadata: Map<String, String> = emptyMap(),
) {
    /**
     * Generates a ContentId for resume tracking.
     *
     * Follows the resume contract from Phase 2 Task 1:
     * - IO content uses: `"io:file:{uri}"`
     *
     * @return ContentId string suitable for ObxResumeMark.
     */
    fun toContentId(): String = "io:file:${source.toUriString()}"

    companion object {
        /**
         * Creates a fake test item for testing and development.
         *
         * @param id Item ID.
         * @param path File path.
         * @param title Display title.
         * @return A fake IoMediaItem for testing.
         */
        fun fake(
            id: String = "fake-io-item",
            path: String = "/storage/emulated/0/Movies/test.mp4",
            title: String = "Test Video",
        ): IoMediaItem =
            IoMediaItem(
                id = id,
                source = IoSource.LocalFile(path),
                title = title,
                fileName = path.substringAfterLast('/'),
                mimeType = "video/mp4",
                sizeBytes = 1024L * 1024L * 100L, // 100 MB
                durationMs = 90_000L, // 1.5 minutes
                lastModifiedMs = System.currentTimeMillis(),
            )
    }
}
