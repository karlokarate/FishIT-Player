package com.fishit.player.pipeline.io

/**
 * Factory interface for converting IoMediaItem to playback sources.
 *
 * This interface is part of the pipeline integration contract.
 * The internal player uses this factory to resolve IO content
 * into playable media sources.
 *
 * **Current Implementation:**
 * This is an interface with stub implementations. Real ExoPlayer/Media3
 * DataSource integration will be added in future phases.
 *
 * **Design Notes:**
 * - Platform-agnostic interface (no Media3 types exposed)
 * - Returns opaque data suitable for InternalPlaybackSourceResolver
 * - Future implementations will integrate with Media3 DataSource APIs
 *
 * **Future Phases:**
 * - Phase 3+: Real Media3 DataSource for local files
 * - Phase 4+: SAF DataSource integration
 * - Phase 5+: SMB DataSource (via smbj or similar)
 */
interface IoPlaybackSourceFactory {
    /**
     * Converts an IoMediaItem into a playback source descriptor.
     *
     * The returned object is opaque from this interface's perspective
     * but will be consumed by the InternalPlaybackSourceResolver.
     *
     * **Stub Behavior:** Returns a simple map with URI.
     *
     * @param item The IO media item to convert.
     * @return Playback source descriptor (implementation-defined structure).
     */
    fun createSource(item: IoMediaItem): Any

    /**
     * Checks if this factory supports the given source type.
     *
     * **Stub Behavior:** Returns true for LocalFile, false for others.
     *
     * @param source The source to check.
     * @return True if supported, false otherwise.
     */
    fun supportsSource(source: IoSource): Boolean
}
