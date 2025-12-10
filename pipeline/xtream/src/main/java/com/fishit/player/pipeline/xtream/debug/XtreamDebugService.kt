package com.fishit.player.pipeline.xtream.debug

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.pipeline.xtream.model.XtreamVodItem

/**
 * Debug service interface for Xtream pipeline inspection.
 *
 * Provides status, VOD listing, and detailed inspection capabilities
 * for CLI and debugging tools.
 *
 * **Usage:**
 * ```kotlin
 * val service = XtreamDebugServiceImpl(adapter)
 * val status = service.getStatus()
 * val vodList = service.listVod(limit = 20)
 * val details = service.inspectVod(streamId = 12345)
 * ```
 */
interface XtreamDebugService {

    /**
     * Get current Xtream pipeline status.
     *
     * @return XtreamStatus with connection info and content counts
     */
    suspend fun getStatus(): XtreamStatus

    /**
     * List VOD items.
     *
     * @param limit Maximum number of items to return
     * @return List of VOD summaries
     */
    suspend fun listVod(limit: Int = 20): List<XtreamVodSummary>

    /**
     * List series items.
     *
     * @param limit Maximum number of items to return
     * @return List of series summaries
     */
    suspend fun listSeries(limit: Int = 20): List<XtreamSeriesSummary>

    /**
     * List live channels.
     *
     * @param limit Maximum number of items to return
     * @return List of channel summaries
     */
    suspend fun listLive(limit: Int = 20): List<XtreamLiveSummary>

    /**
     * Inspect a VOD item in detail.
     *
     * @param streamId VOD stream ID
     * @return Detailed VOD information including raw and normalized data
     */
    suspend fun inspectVod(streamId: Int): XtreamVodDetails?
}

/**
 * Overall Xtream pipeline status.
 *
 * @property baseUrl Connected server base URL
 * @property isAuthenticated Whether the session is authenticated
 * @property vodCountEstimate Estimated number of VOD items
 * @property seriesCountEstimate Estimated number of series
 * @property liveCountEstimate Estimated number of live channels
 */
data class XtreamStatus(
    val baseUrl: String,
    val isAuthenticated: Boolean,
    val vodCountEstimate: Int,
    val seriesCountEstimate: Int,
    val liveCountEstimate: Int,
)

/**
 * Summary of a VOD item.
 *
 * @property streamId VOD stream ID
 * @property title VOD title
 * @property year Release year (if available)
 * @property categoryName Category name
 * @property extension Container extension
 * @property normalizedMediaType Detected media type
 */
data class XtreamVodSummary(
    val streamId: Int,
    val title: String,
    val year: Int?,
    val categoryName: String?,
    val extension: String?,
    val normalizedMediaType: MediaType,
)

/**
 * Summary of a series item.
 *
 * @property seriesId Series ID
 * @property title Series title
 * @property year Release year (if available)
 * @property categoryName Category name
 * @property rating Rating (0-10)
 */
data class XtreamSeriesSummary(
    val seriesId: Int,
    val title: String,
    val year: Int?,
    val categoryName: String?,
    val rating: Double?,
)

/**
 * Summary of a live channel.
 *
 * @property channelId Channel ID
 * @property name Channel name
 * @property categoryName Category name
 * @property hasTvArchive Whether TV archive is available
 */
data class XtreamLiveSummary(
    val channelId: Int,
    val name: String,
    val categoryName: String?,
    val hasTvArchive: Boolean,
)

/**
 * Detailed VOD information.
 *
 * @property raw Original XtreamVodItem
 * @property rawMedia Converted RawMediaMetadata
 */
data class XtreamVodDetails(
    val raw: XtreamVodItem,
    val rawMedia: RawMediaMetadata,
)
