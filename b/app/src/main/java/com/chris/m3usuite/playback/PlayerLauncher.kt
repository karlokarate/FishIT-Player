package com.chris.m3usuite.playback

import android.app.Activity
import android.content.Context
import com.chris.m3usuite.player.ExternalPlayer
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * Central launcher facade. Decides internal vs external, wires headers/start position,
 * and provides a single entry point for Live/VOD/Series playback.
 *
 * Internals:
 * - Resolver: Default decides Internal/External based on Settings + Permissions ("ask" supported).
 * - Resume: Reads missing startPosition from ResumeRepository for VOD/Series (best-effort).
 * - Telemetry: lightweight Log lines (can be upgraded later).
 *
 * Internal player start is delegated to the provided [internalPlayer] callback so screens can:
 *  - push a nav route, or
 *  - toggle an inline InternalPlayerScreen
 */
class PlayerLauncher(
    private val context: Context,
    private val store: SettingsStore,
    private val internalPlayer: (request: PlayRequest, startMs: Long?, mimeType: String?) -> Unit,
    private val resolver: PlayerResolver = DefaultPlayerResolver(context, store, internalPlayer),
    private val resumeStore: ResumeStore = DefaultResumeStore(context)
){
    /**
     * Launch playback for the given request.
     * - Fills missing startPositionMs via ResumeStore (VOD/Series only).
     * - Delegates to resolver for Internal/External.
     */
    suspend fun launch(request: PlayRequest): PlayerResult {
        val filledStart = request.startPositionMs ?: when (request.type) {
            MediaType.VOD -> resumeStore.read(request.mediaId)
            MediaType.SERIES -> resumeStore.read(request.mediaId)
            MediaType.LIVE -> null
        }
        val filled = request.copy(startPositionMs = filledStart)
        android.util.Log.d("PlayerLauncher", "launch type=${filled.type} id=${filled.mediaId} start=${filled.startPositionMs} urlScheme=${filled.url.substringBefore(':', "")}")
        return resolver.launch(filled)
    }
}

/**
 * Compose helper. Provide a screen-local launcher with your internal-player start behavior.
 */
@androidx.compose.runtime.Composable
fun rememberPlayerLauncher(
    internalPlayer: (request: PlayRequest, startMs: Long?, mimeType: String?) -> Unit
): PlayerLauncher {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val store = androidx.compose.runtime.remember(ctx) { SettingsStore(ctx) }
    return androidx.compose.runtime.remember(ctx, internalPlayer) {
        PlayerLauncher(ctx, store, internalPlayer)
    }
}

// -------------------------
// Models / Contracts
// -------------------------

enum class MediaType { VOD, SERIES, LIVE }

data class DrmInfo(
    val scheme: String,
    val licenseUrl: String,
    val requestHeaders: Map<String, String> = emptyMap()
)

data class PlayRequest(
    val type: MediaType,
    val mediaId: String,                 // e.g. "vod:2000000000001" or "series:123:2:5" or "live:1000000000001"
    val title: String?,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val drm: DrmInfo? = null,
    val startPositionMs: Long? = null,
    val autoplay: Boolean = true,
    val audioLang: String? = null,
    val subtitleLang: String? = null,
    val mimeType: String? = null
)

sealed interface PlayerResult {
    data class Completed(val durationMs: Long) : PlayerResult
    data class Stopped(val positionMs: Long) : PlayerResult
    data class Error(val message: String, val cause: Throwable? = null) : PlayerResult
}

interface PlayerResolver {
    suspend fun launch(request: PlayRequest): PlayerResult
}

interface ResumeStore {
    suspend fun read(mediaId: String): Long?
    suspend fun write(mediaId: String, positionMs: Long)
    suspend fun clear(mediaId: String)
}

// -------------------------
// Default Implementations
// -------------------------

private class DefaultPlayerResolver(
    private val context: Context,
    private val store: SettingsStore,
    private val internalPlayer: (request: PlayRequest, startMs: Long?, mimeType: String?) -> Unit
) : PlayerResolver {

    override suspend fun launch(request: PlayRequest): PlayerResult {
        // Enforce internal for Telegram
        if (request.url.startsWith("tg://", ignoreCase = true)) {
            internalPlayer(request, request.startPositionMs, request.mimeType)
            return PlayerResult.Stopped(request.startPositionMs ?: 0L)
        }
        // Permission gating: Kids/Guests may be blocked from external players
        val perms = com.chris.m3usuite.data.repo.PermissionRepository(context, store).current()
        val disallowExternal = !perms.canUseExternalPlayer
        val mode = store.playerMode.first()

        val useInternal = disallowExternal || mode == "internal"
        val useExternal = !disallowExternal && mode == "external"
        val preferAsk = !disallowExternal && mode !in listOf("internal", "external")

        if (useInternal) {
            internalPlayer(request, request.startPositionMs, request.mimeType)
            return PlayerResult.Stopped(request.startPositionMs ?: 0L)
        }
        if (useExternal) {
            val pkg = store.preferredPlayerPkg.first().ifBlank { null }
            ExternalPlayer.open(
                context = context,
                url = request.url,
                headers = request.headers,
                preferredPkg = pkg,
                startPositionMs = request.startPositionMs
            )
            return PlayerResult.Stopped(request.startPositionMs ?: 0L)
        }
        if (preferAsk) {
            val wantInternal = askInternalOrExternal(context)
            if (wantInternal) {
                internalPlayer(request, request.startPositionMs, request.mimeType)
                return PlayerResult.Stopped(request.startPositionMs ?: 0L)
            } else {
                val pkg = store.preferredPlayerPkg.first().ifBlank { null }
                ExternalPlayer.open(
                    context = context,
                    url = request.url,
                    headers = request.headers,
                    preferredPkg = pkg,
                    startPositionMs = request.startPositionMs
                )
                return PlayerResult.Stopped(request.startPositionMs ?: 0L)
            }
        }
        // Fallback
        internalPlayer(request, request.startPositionMs, request.mimeType)
        return PlayerResult.Stopped(request.startPositionMs ?: 0L)
    }

    private suspend fun askInternalOrExternal(context: Context): Boolean {
        // true -> internal, false -> external
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val act = findActivity(context)
            if (act == null) {
                cont.resumeWith(Result.success(true))
                return@suspendCancellableCoroutine
            }
            act.runOnUiThread {
                val dlg = android.app.AlertDialog.Builder(act)
                    .setTitle("Wie abspielen?")
                    .setItems(arrayOf("Intern", "Extern")) { d, which ->
                        if (cont.isActive) cont.resumeWith(Result.success(which == 0))
                        d.dismiss()
                    }
                    .setOnCancelListener { if (cont.isActive) cont.resumeWith(Result.success(true)) }
                    .create()
                cont.invokeOnCancellation { runCatching { dlg.dismiss() } }
                dlg.show()
            }
        }
    }

    private tailrec fun findActivity(ctx: Context?): Activity? = when (ctx) {
        is Activity -> ctx
        is android.content.ContextWrapper -> findActivity(ctx.baseContext)
        else -> null
