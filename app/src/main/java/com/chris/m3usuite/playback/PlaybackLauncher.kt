package com.chris.m3usuite.playback

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.player.PlayerChooser
import com.chris.m3usuite.data.repo.ResumeRepository
import com.chris.m3usuite.core.playback.PlayUrlHelper
import kotlinx.coroutines.Dispatchers
import com.chris.m3usuite.core.telemetry.Telemetry
import kotlinx.coroutines.withContext

interface PlaybackLauncher {
    suspend fun launch(req: PlayRequest): PlayerResult
}

@Composable
fun rememberPlaybackLauncher(
    onOpenInternal: (PlayRequest) -> Unit = {},
    onResult: ((PlayRequest, PlayerResult) -> Unit)? = null
): PlaybackLauncher {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val resumeRepo = remember { ResumeRepository(ctx) }
    return remember(ctx, store) {
        object : PlaybackLauncher {
            override suspend fun launch(req: PlayRequest): PlayerResult {
                try {
                    // Telemetry: announce request
                    Telemetry.event(
                        "play.request",
                        mapOf(
                            "type" to req.type,
                            "mediaId" to (req.mediaId ?: -1L),
                            "seriesId" to (req.seriesId ?: -1),
                            "season" to (req.season ?: -1),
                            "episode" to (req.episodeNum ?: -1),
                            "episodeId" to (req.episodeId ?: -1),
                            "mime" to (req.mimeType ?: ""),
                            "urlHasCreds" to req.url.contains("username=")
                        )
                    )
                    val startMs = req.startPositionMs ?: withContext(Dispatchers.IO) {
                        when (req.type) {
                            "vod" -> resumeRepo.recentVod(60)
                                .firstOrNull { it.mediaId == req.mediaId }
                                ?.positionSecs?.toLong()?.times(1000)
                            else -> null
                        }
                    }
                    val mime = req.mimeType ?: PlayUrlHelper.guessMimeType(req.url, null)
                    PlayerChooser.start(
                        context = ctx,
                        store = store,
                        url = req.url,
                        headers = req.headers,
                        startPositionMs = startMs,
                        mimeType = mime
                    ) { s, resolvedMime ->
                        onOpenInternal(
                            req.copy(startPositionMs = s, mimeType = resolvedMime ?: mime)
                        )
                    }
                    Telemetry.event(
                        "play.launch",
                        mapOf(
                            "type" to req.type,
                            "mediaId" to (req.mediaId ?: -1L),
                            "seriesId" to (req.seriesId ?: -1),
                            "season" to (req.season ?: -1),
                            "episode" to (req.episodeNum ?: -1),
                            "episodeId" to (req.episodeId ?: -1)
                        )
                    )
                    val result = PlayerResult.Stopped(positionMs = startMs ?: 0L)
                    Telemetry.event(
                        "play.result",
                        mapOf(
                            "status" to "stopped",
                            "type" to req.type,
                            "mediaId" to (req.mediaId ?: -1L),
                            "seriesId" to (req.seriesId ?: -1),
                            "season" to (req.season ?: -1),
                            "episode" to (req.episodeNum ?: -1),
                            "episodeId" to (req.episodeId ?: -1),
                            "positionMs" to (startMs ?: 0L),
                            "mime" to (mime ?: "")
                        )
                    )
                    onResult?.invoke(req, result)
                    return result
                } catch (t: Throwable) {
                    Telemetry.error("play.error", t, mapOf("type" to req.type, "mediaId" to (req.mediaId ?: -1L)))
                    Telemetry.event(
                        "play.result",
                        mapOf(
                            "status" to "error",
                            "type" to req.type,
                            "mediaId" to (req.mediaId ?: -1L),
                            "seriesId" to (req.seriesId ?: -1),
                            "season" to (req.season ?: -1),
                            "episode" to (req.episodeNum ?: -1),
                            "episodeId" to (req.episodeId ?: -1),
                            "message" to (t.message ?: "")
                        )
                    )
                    return PlayerResult.Error(t.message ?: "Playback failed", t)
                }
            }
        }
    }
}
