package com.chris.m3usuite.player.internal.live

import android.content.Context
import com.chris.m3usuite.data.obx.ObxLive_
import com.chris.m3usuite.data.obx.ObxStore
import io.objectbox.query.QueryBuilder

/**
 * Default implementation of [LiveChannelRepository] that bridges to the existing data layer.
 *
 * This implementation wraps:
 * - [ObxStore] for ObjectBox access
 * - [ObxLive] entity queries
 *
 * It converts [ObxLive] entities to domain [LiveChannel] models, maintaining separation
 * between the data layer and the domain layer.
 *
 * **Phase 3 Step 3.B**: This repository is instantiated in [rememberInternalPlayerSession]
 * when [PlaybackContext.type] == [PlaybackType.LIVE].
 *
 * @param context Android context for accessing ObjectBox store.
 */
class DefaultLiveChannelRepository(
    private val context: Context,
) : LiveChannelRepository {
    /**
     * Retrieves live channels filtered by category and/or provider.
     *
     * **Implementation notes:**
     * - Uses [ObxStore.get] to access the live channels box
     * - Filters by [ObxLive.categoryId] when [categoryHint] is provided
     * - Currently does not filter by provider (providerHint is accepted but not used)
     * - Maps [ObxLive] entities to [LiveChannel] domain models
     *
     * **ID Mapping:**
     * - [ObxLive.streamId] is Int
     * - [LiveChannel.id] is Long for forward compatibility
     * - Conversion: `streamId.toLong()`
     *
     * @param categoryHint Optional category filter (matched against ObxLive.categoryId).
     * @param providerHint Optional provider filter (currently not implemented).
     * @return List of live channels matching the criteria.
     */
    override suspend fun getChannels(
        categoryHint: String?,
        providerHint: String?,
    ): List<LiveChannel> =
        try {
            val liveBox = ObxStore.get(context).boxFor(com.chris.m3usuite.data.obx.ObxLive::class.java)
            val queryBuilder =
                if (categoryHint != null) {
                    liveBox
                        .query(ObxLive_.categoryId.equal(categoryHint))
                } else {
                    liveBox.query()
                }

            if (!providerHint.isNullOrBlank()) {
                queryBuilder.equal(ObxLive_.providerKey, providerHint, QueryBuilder.StringOrder.CASE_INSENSITIVE)
            }

            val query = queryBuilder.build()

            query.use { q ->
                q.find().map { obxLive ->
                    LiveChannel(
                        id = obxLive.streamId.toLong(),
                        name = obxLive.name,
                        // URL wird zur Laufzeit gebaut (PlayUrlHelper), hier Platzhalter um Filter im Controller nicht zu verwerfen
                        url = "stream://${obxLive.streamId}",
                        category = obxLive.categoryId,
                        logoUrl = obxLive.logo,
                    )
                }
            }
        } catch (e: Throwable) {
            // Fail-safe: Return empty list on any error
            emptyList()
        }

    /**
     * Retrieves a single channel by ID.
     *
     * **ID Mapping:**
     * - [channelId] is Long (from [LiveChannel.id])
     * - [ObxLive.streamId] is Int
     * - Conversion: `channelId.toInt()`
     *
     * @param channelId The channel's unique identifier.
     * @return The channel, or null if not found.
     */
    override suspend fun getChannel(channelId: Long): LiveChannel? =
        try {
            val liveBox = ObxStore.get(context).boxFor(com.chris.m3usuite.data.obx.ObxLive::class.java)
            val query =
                liveBox
                    .query(ObxLive_.streamId.equal(channelId.toInt()))
                    .build()

            query.use { q ->
                q.findFirst()?.let { obxLive ->
                    LiveChannel(
                        id = obxLive.streamId.toLong(),
                        name = obxLive.name,
                        url = "", // URL is constructed dynamically, not needed for controller
                        category = obxLive.categoryId,
                        logoUrl = obxLive.logo,
                    )
                }
            }
        } catch (e: Throwable) {
            // Fail-safe: Return null on any error
            null
        }
}
