package com.fishit.player.pipeline.io

/**
 * Stub implementation of IoPlaybackSourceFactory.
 *
 * This implementation provides a minimal playback source descriptor
 * without real Media3/ExoPlayer integration.
 *
 * **Purpose:**
 * - Validate interface contracts
 * - Enable early integration testing
 * - Provide predictable behavior for unit tests
 *
 * **Future:**
 * A real implementation will integrate with Media3 DataSource APIs
 * to handle local files, SAF URIs, and network shares.
 */
class StubIoPlaybackSourceFactory : IoPlaybackSourceFactory {
    /**
     * Creates a simple source descriptor.
     *
     * Returns a map with basic source information.
     * Real implementation will create Media3 DataSource configuration.
     */
    override fun createSource(item: IoMediaItem): Any =
        mapOf(
            "type" to "io",
            "uri" to item.source.toUriString(),
            "id" to item.id,
            "title" to item.title,
        )

    /**
     * Checks source support.
     *
     * Stub: Only claims to support LocalFile.
     * Real implementation will check for platform capabilities.
     */
    override fun supportsSource(source: IoSource): Boolean =
        when (source) {
            is IoSource.LocalFile -> true
            is IoSource.Saf -> false // Stub: Not implemented yet
            is IoSource.Smb -> false // Stub: Not implemented yet
            is IoSource.GenericUri -> false // Stub: Not implemented yet
        }
}
