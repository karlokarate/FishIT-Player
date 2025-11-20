package com.chris.m3usuite.core.debug

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GlobalDebug – lightweight, opt-in debug logging for navigation, DPAD, focus and OBX key backfills.
 * Controlled via Settings (store flag), default OFF in release builds.
 */
object GlobalDebug {
    private const val TAG = "GlobalDebug"
    private val enabled = AtomicBoolean(false)

    fun setEnabled(on: Boolean) {
        enabled.set(on)
    }

    fun isEnabled(): Boolean = enabled.get()

    // Navigation route changes
    fun logNavigation(
        from: String?,
        to: String?,
    ) {
        if (!enabled.get()) return
        Log.i(TAG, "nav: ${from.orEmpty()} -> ${to.orEmpty()}")
    }

    // DPAD / key interactions
    fun logDpad(
        action: String,
        extras: Map<String, Any?>? = null,
    ) {
        if (!enabled.get()) return
        val sfx = extras?.entries?.joinToString(", ") { (k, v) -> "$k=$v" }
        if (sfx.isNullOrBlank()) Log.i(TAG, "dpad: $action") else Log.i(TAG, "dpad: $action { $sfx }")
    }

    // Tree/hierarchy hints
    fun logTree(node: String) {
        if (!enabled.get()) return
        Log.i(TAG, node)
    }

    fun logTree(
        node: String,
        hint: String?,
    ) {
        if (!enabled.get()) return
        if (hint.isNullOrBlank()) Log.i(TAG, node) else Log.i(TAG, "$node [$hint]")
    }

    // Focus details emitted by rows/tiles
    fun logTileFocus(
        type: String,
        id: String,
        uiTitle: String?,
        obxTitle: String?,
    ) {
        if (!enabled.get()) return
        val ui = uiTitle?.takeIf { it.isNotBlank() } ?: ""
        val obx = obxTitle?.takeIf { it.isNotBlank() } ?: ""
        val both = if (obx.isNotEmpty()) " ($obx)" else ""
        Log.i(TAG, "focus:$type id=$id $ui$both")
    }

    // Generic widget focus (Buttons, Chips, Clickables ...)
    fun logFocusWidget(
        component: String,
        module: String? = null,
        tag: String? = null,
    ) {
        if (!enabled.get()) return
        val mod = module?.takeIf { it.isNotBlank() } ?: ""
        val tg = tag?.takeIf { it.isNotBlank() } ?: ""
        val suffix =
            buildString {
                if (mod.isNotEmpty()) append(" module=").append(mod)
                if (tg.isNotEmpty()) append(" tag=").append(tg)
            }
        Log.i(TAG, "focus:widget component=$component$suffix")
    }

    // OBX index/key backfills
    fun logObxKey(
        kind: String,
        id: String,
        change: String,
    ) {
        if (!enabled.get()) return
        Log.i(TAG, "obxKey:$kind id=$id $change")
    }

    fun logObxKey(
        kind: String,
        id: String,
        change: Map<String, Any?>,
    ) {
        if (!enabled.get()) return
        val s = change.entries.joinToString(", ") { (k, v) -> "$k=$v" }
        Log.i(TAG, "obxKey:$kind id=$id { $s }")
    }

    fun logDetailSection(
        type: String,
        id: String,
        section: String,
        entries: Map<String, Any?>,
    ) {
        if (!enabled.get()) return
        if (entries.isEmpty()) return
        val body =
            entries.entries.joinToString(", ") { (k, v) ->
                val value = v?.toString()?.replace('\n', ' ')?.replace('\r', ' ')
                "$k=${value ?: ""}"
            }
        Log.i(TAG, "detail:$type id=$id section=$section { $body }")
    }

    // Row navigation intent/decision logging
    fun logRowNav(
        direction: String,
        rowKey: String?,
        fromIndex: Int?,
        targetIndex: Int?,
        note: String? = null,
    ) {
        if (!enabled.get()) return
        val rk = rowKey ?: ""
        val fr = fromIndex?.toString() ?: ""
        val tg = targetIndex?.toString() ?: ""
        val extra = note?.takeIf { it.isNotBlank() } ?: ""
        val parts = mutableListOf("nav:row dir=$direction")
        if (rk.isNotEmpty()) parts += "row=$rk"
        if (fr.isNotEmpty()) parts += "from=$fr"
        if (tg.isNotEmpty()) parts += "to=$tg"
        if (extra.isNotEmpty()) parts += extra
        Log.i(TAG, parts.joinToString(" "))
    }

    // Row scroll plan (index + offset)
    fun logRowScrollPlan(
        index: Int,
        offset: Int,
        reason: String? = null,
    ) {
        if (!enabled.get()) return
        if (reason.isNullOrBlank()) {
            Log.i(TAG, "row:scrollPlan index=$index offset=$offset")
        } else {
            Log.i(TAG, "row:scrollPlan index=$index offset=$offset $reason")
        }
    }

    fun logRowWindow(
        rowKey: String?,
        first: Int,
        last: Int,
        vpStart: Int,
        vpEnd: Int,
        items: String,
    ) {
        if (!enabled.get()) return
        val rk = rowKey ?: ""
        Log.i(TAG, "row:window row=$rk first=$first last=$last vp=$vpStart..$vpEnd vis=[$items]")
    }

    fun logRowFocusState(
        rowKey: String?,
        index: Int,
        self: Boolean,
        has: Boolean,
    ) {
        if (!enabled.get()) return
        val rk = rowKey ?: ""
        Log.i(TAG, "row:focusState row=$rk idx=$index self=$self has=$has")
    }
}
