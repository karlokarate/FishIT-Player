package com.chris.m3usuite.player

import android.content.Context
import com.chris.m3usuite.data.repo.PermissionRepository
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * Zentrale Wahl "Immer fragen | Intern | Extern".
 * Die drei Detail-Screens (Vod/Series/Live) rufen nur noch diese Funktion auf.
 */
object PlayerChooser {
    /**
     * Startet je nach Settings den internen Player, externen Player oder fragt.
     * @param buildInternal lambda: (startMs) -> Unit ruft deine Nav-Route "player?..." auf
     */
    suspend fun start(
        context: Context,
        store: SettingsStore,
        url: String,
        headers: Map<String, String> = emptyMap(),
        startPositionMs: Long? = null,
        mimeType: String? = null,
        buildInternal: (startPositionMs: Long?, mimeType: String?) -> Unit,
    ) {
        // Force internal for Telegram scheme
        if (url.startsWith("tg://", ignoreCase = true)) {
            buildInternal(startPositionMs, mimeType)
            return
        }
        // Enforce permissions: no external for kids/guests unless allowed
        val perms = PermissionRepository(context, store).current()
        val disallowExternal = !perms.canUseExternalPlayer
        if (disallowExternal) {
            buildInternal(startPositionMs, mimeType)
            return
        }

        when (store.playerMode.first()) {
            "internal" -> {
                com.chris.m3usuite.core.logging.AppLog.log("player", com.chris.m3usuite.core.logging.AppLog.Level.DEBUG, "mode=internal; starting internal")
                buildInternal(startPositionMs, mimeType)
            }
            "external" -> {
                com.chris.m3usuite.core.logging.AppLog.log(
                    "player",
                    com.chris.m3usuite.core.logging.AppLog.Level.DEBUG,
                    "mode=external; starting external preferredPkg=${store.preferredPlayerPkg.first()}",
                )
                val pkg = store.preferredPlayerPkg.first().ifBlank { null }
                if (pkg == null) {
                    // No preferred external player selected → avoid system chooser; play internally
                    com.chris.m3usuite.core.logging.AppLog.log("player", com.chris.m3usuite.core.logging.AppLog.Level.DEBUG, "no preferred package; fallback to internal")
                    buildInternal(startPositionMs, mimeType)
                } else {
                    ExternalPlayer.open(
                        context = context,
                        url = url,
                        headers = headers,
                        startPositionMs = startPositionMs,
                        preferredPkg = pkg,
                    )
                }
            }
            else -> {
                // Immer fragen: Dialog mit "Intern" oder "Extern"
                val wantInternal = askInternalOrExternal(context)
                com.chris.m3usuite.core.logging.AppLog.log("player", com.chris.m3usuite.core.logging.AppLog.Level.DEBUG, "ask result: wantInternal=$wantInternal")
                if (wantInternal) {
                    buildInternal(startPositionMs, mimeType)
                } else {
                    val pkg = store.preferredPlayerPkg.first().ifBlank { null }
                    ExternalPlayer.open(
                        context = context,
                        url = url,
                        headers = headers,
                        preferredPkg = pkg,
                        startPositionMs = startPositionMs,
                    )
                }
            }
        }
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
                val dlg =
                    android.app.AlertDialog
                        .Builder(act)
                        .setTitle("Wie abspielen?")
                        .setItems(arrayOf("Intern", "Extern")) { d, which ->
                            if (cont.isActive) cont.resumeWith(Result.success(which == 0))
                            d.dismiss()
                        }.setOnCancelListener { if (cont.isActive) cont.resumeWith(Result.success(true)) }
                        .create()
                cont.invokeOnCancellation { runCatching { dlg.dismiss() } }
                dlg.show()
            }
        }
    }

    private tailrec fun findActivity(ctx: Context?): android.app.Activity? =
        when (ctx) {
            is android.app.Activity -> ctx
            is android.content.ContextWrapper -> findActivity(ctx.baseContext)
            else -> null
        }
}
