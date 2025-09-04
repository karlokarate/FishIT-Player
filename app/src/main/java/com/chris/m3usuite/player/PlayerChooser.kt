package com.chris.m3usuite.player

import android.content.Context
import kotlinx.coroutines.flow.first
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.repo.PermissionRepository

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
        buildInternal: (startPositionMs: Long?) -> Unit
    ) {
        // Force internal for Telegram scheme
        if (url.startsWith("tg://", ignoreCase = true)) {
            buildInternal(startPositionMs)
            return
        }
        // Enforce permissions: no external for kids/guests unless allowed
        val perms = PermissionRepository(context, store).current()
        val disallowExternal = !perms.canUseExternalPlayer
        if (disallowExternal) {
            buildInternal(startPositionMs)
            return
        }

        when (store.playerMode.first()) {
            "internal" -> buildInternal(startPositionMs)
            "external" -> {
                val pkg = store.preferredPlayerPkg.first().ifBlank { null }
                if (pkg == null) {
                    // No preferred external player selected → avoid system chooser; play internally
                    buildInternal(startPositionMs)
                } else {
                    ExternalPlayer.open(
                        context = context,
                        url = url,
                        headers = headers,
                        startPositionMs = startPositionMs,
                        preferredPkg = pkg
                    )
                }
            }
            else -> {
                // Immer fragen: Dialog mit "Intern" oder "Extern"
                val wantInternal = askInternalOrExternal(context)
                if (wantInternal) {
                    buildInternal(startPositionMs)
                } else {
                    val pkg = store.preferredPlayerPkg.first().ifBlank { null }
                    ExternalPlayer.open(
                        context = context,
                        url = url,
                        headers = headers,
                        preferredPkg = pkg,
                        startPositionMs = startPositionMs
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
                cont.resume(true, onCancellation = null) // Fallback: intern
                return@suspendCancellableCoroutine
            }
            act.runOnUiThread {
                val dlg = android.app.AlertDialog.Builder(act)
                    .setTitle("Wie abspielen?")
                    .setItems(arrayOf("Intern", "Extern")) { d, which ->
                        cont.resume(which == 0, onCancellation = null)
                        d.dismiss()
                    }
                    .setOnCancelListener { cont.resume(true, onCancellation = null) }
                    .create()
                dlg.show()
            }
        }
    }

    private tailrec fun findActivity(ctx: Context?): android.app.Activity? = when (ctx) {
        is android.app.Activity -> ctx
        is android.content.ContextWrapper -> findActivity(ctx.baseContext)
        else -> null
    }
}
