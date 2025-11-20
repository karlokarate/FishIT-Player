package com.chris.m3usuite.ui.layout

import android.content.Context
import com.chris.m3usuite.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Focus/logging helpers for Fish tiles. */
object FishLogging {
    suspend fun logVodFocus(
        ctx: Context,
        media: MediaItem,
    ) {
        val sid = media.streamId ?: return
        val obxTitle =
            withContext(Dispatchers.IO) {
                runCatching {
                    val store =
                        com.chris.m3usuite.data.obx.ObxStore
                            .get(ctx)
                    store
                        .boxFor(com.chris.m3usuite.data.obx.ObxVod::class.java)
                        .query(
                            com.chris.m3usuite.data.obx.ObxVod_.vodId
                                .equal(sid.toLong()),
                        ).build()
                        .findFirst()
                        ?.name
                }.getOrNull()
            }
        com.chris.m3usuite.core.debug.GlobalDebug
            .logTileFocus("vod", sid.toString(), media.name, obxTitle)
        com.chris.m3usuite.core.debug.GlobalDebug
            .logTree("row:vod", "tile:$sid")
    }

    suspend fun logSeriesFocus(
        ctx: Context,
        media: MediaItem,
    ) {
        val sid = media.streamId ?: return
        val obxTitle =
            withContext(Dispatchers.IO) {
                runCatching {
                    val store =
                        com.chris.m3usuite.data.obx.ObxStore
                            .get(ctx)
                    store
                        .boxFor(com.chris.m3usuite.data.obx.ObxSeries::class.java)
                        .query(
                            com.chris.m3usuite.data.obx.ObxSeries_.seriesId
                                .equal(sid),
                        ).build()
                        .findFirst()
                        ?.name
                }.getOrNull()
            }
        com.chris.m3usuite.core.debug.GlobalDebug
            .logTileFocus("series", sid.toString(), media.name, obxTitle)
        com.chris.m3usuite.core.debug.GlobalDebug
            .logTree("row:series", "tile:$sid")
    }

    suspend fun logLiveFocus(
        ctx: Context,
        media: MediaItem,
    ) {
        val sid = media.streamId ?: return
        val obxTitle =
            withContext(Dispatchers.IO) {
                runCatching {
                    val store =
                        com.chris.m3usuite.data.obx.ObxStore
                            .get(ctx)
                    store
                        .boxFor(com.chris.m3usuite.data.obx.ObxLive::class.java)
                        .query(
                            com.chris.m3usuite.data.obx.ObxLive_.streamId
                                .equal(sid),
                        ).build()
                        .findFirst()
                        ?.name
                }.getOrNull()
            }
        com.chris.m3usuite.core.debug.GlobalDebug
            .logTileFocus("live", sid.toString(), media.name, obxTitle)
        com.chris.m3usuite.core.debug.GlobalDebug
            .logTree("row:live", "tile:$sid")
    }

    suspend fun logTelegramFocus(
        ctx: Context,
        media: MediaItem,
    ) {
        val key = media.tgMessageId ?: media.id
        com.chris.m3usuite.core.debug.GlobalDebug
            .logTileFocus("telegram", key.toString(), media.name, null)
        com.chris.m3usuite.core.debug.GlobalDebug
            .logTree("row:telegram", "tile:$key")
    }
}
