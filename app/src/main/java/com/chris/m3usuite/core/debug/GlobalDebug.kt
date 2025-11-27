package com.chris.m3usuite.core.debug

import com.chris.m3usuite.core.logging.AppLog
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GlobalDebug – lightweight, opt-in debug logging for navigation, DPAD, focus and OBX key backfills.
 * Controlled via Settings (store flag), default OFF in release builds.
 */
object GlobalDebug {
    private const val TAG = "GlobalDebug"
    private val enabled = AtomicBoolean(false)

    /**
     * Toggle for the TV Input Inspector overlay.
     * When enabled, the inspector overlay shows real-time TV input events.
     * Controlled separately from the main debug logging toggle.
     */
    private val tvInputInspectorEnabled = AtomicBoolean(false)

    fun setEnabled(on: Boolean) {
        enabled.set(on)
    }

    fun isEnabled(): Boolean = enabled.get()

    /**
     * Enable or disable the TV Input Inspector overlay.
     *
     * When enabled, [DefaultTvInputDebugSink] will capture events and the
     * [TvInputInspectorOverlay] will display them in real-time.
     *
     * @param on True to enable the inspector overlay
     */
    fun setTvInputInspectorEnabled(on: Boolean) {
        tvInputInspectorEnabled.set(on)
    }

    /**
     * Check if the TV Input Inspector overlay is enabled.
     *
     * @return True if the inspector overlay should be displayed
     */
    fun isTvInputInspectorEnabled(): Boolean = tvInputInspectorEnabled.get()

    // Navigation route changes
    fun logNavigation(
        from: String?,
        to: String?,
    ) {
        if (!enabled.get()) return
        AppLog.log("ui", AppLog.Level.INFO, "nav: ${from.orEmpty()} -> ${to.orEmpty()}")
    }

    // DPAD / key interactions
    fun logDpad(
        action: String,
        extras: Map<String, Any?>? = null,
    ) {
        if (!enabled.get()) return
        val sfx = extras?.entries?.joinToString(", ") { (k, v) -> "$k=$v" }
        val msg = if (sfx.isNullOrBlank()) "dpad: $action" else "dpad: $action { $sfx }"
        AppLog.log("focus", AppLog.Level.INFO, msg)
    }

    // Tree/hierarchy hints
    fun logTree(node: String) {
        if (!enabled.get()) return
        AppLog.log("diagnostics", AppLog.Level.DEBUG, "tree: $node")
    }

    fun logTree(
        node: String,
        hint: String?,
    ) {
        if (!enabled.get()) return
        val msg = if (hint.isNullOrBlank()) "tree: $node" else "tree: $node [$hint]"
        AppLog.log("diagnostics", AppLog.Level.DEBUG, msg)
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
        AppLog.log("focus", AppLog.Level.INFO, "focus:$type id=$id $ui$both")
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
        AppLog.log("focus", AppLog.Level.INFO, "focus:widget component=$component$suffix")
    }

    // OBX index/key backfills
    fun logObxKey(
        kind: String,
        id: String,
        change: String,
    ) {
        if (!enabled.get()) return
        AppLog.log("diagnostics", AppLog.Level.INFO, "obxKey:$kind id=$id $change")
    }

    fun logObxKey(
        kind: String,
        id: String,
        change: Map<String, Any?>,
    ) {
        if (!enabled.get()) return
        val s = change.entries.joinToString(", ") { (k, v) -> "$k=$v" }
        AppLog.log("diagnostics", AppLog.Level.INFO, "obxKey:$kind id=$id { $s }")
    }

    fun logDetailSection(
        type: String,
        id: String,
        section: String,
        entries: Map<String, Any?>,
    ) {
        if (!enabled.get()) return
        if (entries.isEmpty()) return
        val clean =
            entries.entries.associate { (k, v) ->
                val value = v?.toString()?.replace('\n', ' ')?.replace('\r', ' ') ?: ""
                k to value
            }
        AppLog.log(
            category = "diagnostics",
            level = AppLog.Level.DEBUG,
            message = "detail:$type id=$id section=$section",
            extras = clean,
        )
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
        AppLog.log("focus", AppLog.Level.INFO, parts.joinToString(" "))
    }

    // Row scroll plan (index + offset)
    fun logRowScrollPlan(
        index: Int,
        offset: Int,
        reason: String? = null,
    ) {
        if (!enabled.get()) return
        val suffix = reason?.takeIf { it.isNotBlank() } ?: ""
        val msg = if (suffix.isEmpty()) "row:scrollPlan index=$index offset=$offset" else "row:scrollPlan index=$index offset=$offset $suffix"
        AppLog.log("focus", AppLog.Level.INFO, msg)
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
        AppLog.log("focus", AppLog.Level.DEBUG, "row:window row=$rk first=$first last=$last vp=$vpStart..$vpEnd vis=[$items]")
    }

    fun logRowFocusState(
        rowKey: String?,
        index: Int,
        self: Boolean,
        has: Boolean,
    ) {
        if (!enabled.get()) return
        val rk = rowKey ?: ""
        AppLog.log("focus", AppLog.Level.DEBUG, "row:focusState row=$rk idx=$index self=$self has=$has")
    }
}
