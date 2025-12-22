# Git Diff: Commit 6d687a88

**Commit:** `6d687a8886b43ed07d03b120ef530f159a03ad35`
**Author:** karlokarate
**Date:** Mon Dec 22 08:15:09 2025 +0000
**Message:** fix(startup): implement startup_trigger_contract compliance

## Changed Files

| File | Changes |
|------|---------|
| CatalogSyncBootstrap.kt | +30 -1 |
| FishItV2Application.kt | +14 -0 |
| MainActivity.kt | +15 -0 |
| AppNavHost.kt | +18 -0 |
| startup_trigger_contract.md | +211 (new) |
| core/epg-model/* | +265 (new module) |

**Total:** 12 files changed, 592 insertions(+), 37 deletions(-)

---

## Diff

```diff
diff --git a/app-v2/src/main/java/com/fishit/player/v2/CatalogSyncBootstrap.kt b/app-v2/src/main/java/com/fishit/player/v2/CatalogSyncBootstrap.kt
index e3641d3e..9ec6a5c9 100644
--- a/app-v2/src/main/java/com/fishit/player/v2/CatalogSyncBootstrap.kt
+++ b/app-v2/src/main/java/com/fishit/player/v2/CatalogSyncBootstrap.kt
@@ -14,6 +14,7 @@ import javax.inject.Named
 import javax.inject.Singleton
 import kotlinx.coroutines.CancellationException
 import kotlinx.coroutines.CoroutineScope
+import kotlinx.coroutines.delay
 import kotlinx.coroutines.flow.combine
 import kotlinx.coroutines.flow.distinctUntilChanged
 import kotlinx.coroutines.flow.first
@@ -91,11 +92,24 @@ class CatalogSyncBootstrap
             }
         }
 
-        private fun triggerSync(
-            telegramReady: Boolean,
-            xtreamConnected: Boolean,
-        ) {
-            if (!hasTriggered.compareAndSet(false, true)) return
+/**
+     * Contract T-2: Mandatory delay gate before sync.
+     * This prevents sync from racing with other startup tasks.
+     */
+    private suspend fun triggerSync(
+        telegramReady: Boolean,
+        xtreamConnected: Boolean,
+    ) {
+        if (!hasTriggered.compareAndSet(false, true)) return
+
+        // Contract T-2: Delay gate - wait before triggering sync
+        delay(SYNC_DELAY_MS)
+
+        // Contract T-3: Only sync if at least one source is ready
+        if (!telegramReady && !xtreamConnected) {
+            UnifiedLog.i(TAG) { "No sources ready, skipping catalog sync" }
+            return
+        }
 
             UnifiedLog.i(TAG) { "Catalog sync bootstrap triggered; telegram=$telegramReady xtream=$xtreamConnected" }
             catalogSyncWorkScheduler.enqueueAutoSync()
@@ -129,5 +143,11 @@ class CatalogSyncBootstrap
 
         private companion object {
             private const val TAG = "CatalogSyncBootstrap"
+
+            /**
+             * Contract T-2: Delay gate in milliseconds.
+             * Prevents sync from racing with other startup operations.
+             */
+            private const val SYNC_DELAY_MS = 5_000L
         }
     }
diff --git a/app-v2/src/main/java/com/fishit/player/v2/FishItV2Application.kt b/app-v2/src/main/java/com/fishit/player/v2/FishItV2Application.kt
index 716e6dff..ea3fad4b 100644
--- a/app-v2/src/main/java/com/fishit/player/v2/FishItV2Application.kt
+++ b/app-v2/src/main/java/com/fishit/player/v2/FishItV2Application.kt
@@ -68,23 +68,21 @@ class FishItV2Application :
     override fun onCreate() {
         super.onCreate()
 
-        val workManagerInitialized = WORK_MANAGER_INITIALIZED.compareAndSet(false, true)
-        if (workManagerInitialized) {
-            WorkManager.initialize(this, workConfiguration)
-        }
-
-        // Early initialization of unified logging system to ensure all subsequent logging works correctly
+        // Contract S-1: UnifiedLog MUST be initialized BEFORE any other subsystem
         UnifiedLogInitializer.init(isDebug = BuildConfig.DEBUG)
 
+        // WorkManager initialization (after logging is ready)
+        val workManagerInitialized = WORK_MANAGER_INITIALIZED.compareAndSet(false, true)
         if (workManagerInitialized) {
+            WorkManager.initialize(this, workConfiguration)
             UnifiedLog.i(TAG) { "WorkManager initialized" }
         }
 
-        // Start source activation observers (must be before bootstraps)
+        // Contract S-2: Start lightweight observers (no heavy work)
         sourceActivationObserver.start(appScope)
         telegramActivationObserver.start()
         
-        // Start session bootstraps
+        // Contract S-3: Bootstraps started here ONLY (not in MainActivity or AppNavHost)
         xtreamSessionBootstrap.start()
         catalogSyncBootstrap.start()
     }
diff --git a/app-v2/src/main/java/com/fishit/player/v2/MainActivity.kt b/app-v2/src/main/java/com/fishit/player/v2/MainActivity.kt
index 962af460..77998d5e 100644
--- a/app-v2/src/main/java/com/fishit/player/v2/MainActivity.kt
+++ b/app-v2/src/main/java/com/fishit/player/v2/MainActivity.kt
@@ -11,35 +11,26 @@ import androidx.compose.ui.Modifier
 import com.fishit.player.v2.navigation.AppNavHost
 import com.fishit.player.v2.ui.theme.FishItV2Theme
 import dagger.hilt.android.AndroidEntryPoint
-import javax.inject.Inject
 
 /**
  * Main entry activity for FishIT Player v2.
  */
 @AndroidEntryPoint
 class MainActivity : ComponentActivity() {
-    @Inject
-    lateinit var catalogSyncBootstrap: CatalogSyncBootstrap
-
-    @Inject
-    lateinit var xtreamSessionBootstrap: XtreamSessionBootstrap
+    // Contract S-3: Bootstraps are started in Application.onCreate() ONLY
+    // No duplicate bootstrap injections or starts here
 
     override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
         enableEdgeToEdge()
 
-        // Initialize Xtream session before catalog sync
-        xtreamSessionBootstrap.start()
-
         setContent {
             FishItV2Theme {
                 Surface(
                     modifier = Modifier.fillMaxSize(),
                     color = MaterialTheme.colorScheme.background,
                 ) {
-                    AppNavHost(
-                        catalogSyncBootstrap = catalogSyncBootstrap,
-                    )
+                    AppNavHost()
                 }
             }
         }
diff --git a/app-v2/src/main/java/com/fishit/player/v2/navigation/AppNavHost.kt b/app-v2/src/main/java/com/fishit/player/v2/navigation/AppNavHost.kt
index 5493991a..57f2f9cc 100644
--- a/app-v2/src/main/java/com/fishit/player/v2/navigation/AppNavHost.kt
+++ b/app-v2/src/main/java/com/fishit/player/v2/navigation/AppNavHost.kt
@@ -25,10 +25,8 @@ import com.fishit.player.feature.home.debug.DebugPlaybackScreen
 import com.fishit.player.feature.onboarding.StartScreen
 import com.fishit.player.feature.settings.DebugScreen
 import com.fishit.player.ui.PlayerScreen
-import com.fishit.player.v2.CatalogSyncBootstrap
 import com.fishit.player.v2.navigation.PlayerNavViewModel
 import com.fishit.player.v2.ui.debug.DebugSkeletonScreen
-import kotlinx.coroutines.flow.collectLatest
 
 /**
  * Top-level navigation host for FishIT Player v2.
@@ -37,21 +35,17 @@ import kotlinx.coroutines.flow.collectLatest
  * Start -> Home -> Detail -> Player
  *               -> Debug -> DebugPlayback (test player)
  *               -> Settings
+ * 
+ * Contract S-3: Bootstraps are started in Application.onCreate() ONLY.
+ * No bootstrap triggers in navigation or UI layers.
  */
 @Composable
-fun AppNavHost(
-    catalogSyncBootstrap: CatalogSyncBootstrap,
-) {
+fun AppNavHost() {
     val navController = rememberNavController()
 
     FishTheme {
-        LaunchedEffect(navController, catalogSyncBootstrap) {
-            navController.currentBackStackEntryFlow.collectLatest { backStackEntry ->
-                if (backStackEntry.destination.route == Routes.HOME) {
-                    catalogSyncBootstrap.start()
-                }
-            }
-        }
+        // Contract S-3: Removed LaunchedEffect bootstrap trigger
+        // Sync is managed by CatalogSyncBootstrap in Application.onCreate()
 
         NavHost(
             navController = navController,
diff --git a/contracts/startup_trigger_contract.md b/contracts/startup_trigger_contract.md
new file mode 100644
index 00000000..2faed2e3
--- /dev/null
+++ b/contracts/startup_trigger_contract.md
@@ -0,0 +1,211 @@
+## App Startup & Sync Trigger Contract — Premium Gold
+
+**Version:** 1.0
+**Date:** 2025-12-22
+**Status:** Binding
+**Scope:** App startup sequencing, auto-sync triggering, warm-up vs reliable sync, phone/tablet + FireTV 32-bit
+
+> This contract is binding. Any deviation is a bug unless explicitly documented and approved.
+
+---
+
+## 1) Goals
+
+* **Fast startup** (no jank, especially on FireTV 32-bit)
+* **Immediate UI clarity** (never "empty and silent")
+* **Deterministic background sync** (SSOT WorkManager queue only)
+* **No duplicate bootstraps** and no parallel sync paths
+* **Optional sources** (Xtream/Telegram/IO independent)
+
+---
+
+## 2) Startup Order (MANDATORY)
+
+### S-1 Unified logging first
+
+`UnifiedLogInitializer.init(isDebug = BuildConfig.DEBUG)` MUST be called in `Application.onCreate()` before any other subsystem starts.
+
+### S-2 AppShell starts observers, not heavy work
+
+The following MAY start in `Application.onCreate()`:
+
+* `SourceActivationObserver`
+* `TelegramActivationObserver`
+
+These MUST be **lightweight** (no full scans, no large network calls).
+
+### S-3 Exactly one bootstrap location
+
+Each bootstrap MUST be started in exactly one place:
+
+* either `Application.onCreate()` **or** `MainActivity.onCreate()`
+* never both.
+
+Duplicate `.start()` calls for the same bootstrap are forbidden.
+
+---
+
+## 3) UI First Principles (MANDATORY)
+
+### U-1 UI must render meaningful state immediately
+
+On first render of HOME/LIVE:
+
+* If no sources are active: show "Add source" actions.
+* If sources active but library empty: show "Sync pending" state + passive indicator.
+* UI MUST remain usable while sync runs.
+
+### U-2 UI never performs sync work
+
+UI MUST NOT:
+
+* call transport
+* call pipelines
+* call `CatalogSyncService.sync*()` directly
+  UI triggers sync only through the SSOT scheduler.
+
+---
+
+## 4) Sync Execution Paths (MANDATORY)
+
+### X-1 Single SSOT background path
+
+All reliable sync MUST run through WorkManager unique works only:
+
+* `catalog_sync_global`
+* `tmdb_enrichment_global`
+* (if EPG exists) `epg_sync_global`
+
+No other unique work names for these concerns may exist.
+
+### X-2 Warm-up is allowed but must be bounded and non-critical
+
+A **WarmUpMode** MAY exist to improve perceived responsiveness:
+
+* runs only while the app is in foreground
+* performs small, bounded batches only
+* MUST NOT be required for correctness
+* MUST NOT write large amounts in one run
+* MUST NOT bypass persistence rules (still writes via repositories if it writes)
+
+WarmUpMode MUST never replace WorkManager reliable sync.
+
+---
+
+## 5) Auto-Sync Trigger Policy (Premium Gold)
+
+### T-1 Auto-sync is deferred by design
+
+Auto-sync MUST NOT start heavy work immediately at process start.
+
+Auto-sync MAY be triggered only by one of these events:
+
+1. **Source activation transition** (INACTIVE → ACTIVE)
+2. **User reaches a content screen** (HOME or LIVE becomes visible)
+3. **Idle warm-up window** (app in foreground, user idle) with a short delay
+
+### T-2 Mandatory delay gate (to protect startup)
+
+When auto-sync is triggered, it MUST be scheduled with a short delay window:
+
+* **Default delay:** 3–10 seconds (implementation chooses one constant)
+* Purpose: ensure UI has stabilized and first frame is rendered.
+
+### T-3 No auto-sync if nothing is active
+
+If `activeSources` is empty:
+
+* auto-sync MUST NOT enqueue any work.
+
+### T-4 Cancel when sources become empty
+
+If `activeSources` transitions to empty:
+
+* `catalog_sync_global` MUST be cancelled by name.
+
+---
+
+## 6) Source Order & Serial Execution (MANDATORY)
+
+### Q-1 Serial queue only
+
+At most one catalog sync chain runs at a time.
+
+### Q-2 Fixed source order
+
+If multiple sources are active, catalog sync order MUST be:
+
+1. Xtream
+2. Telegram
+3. IO (if active)
+
+---
+
+## 7) TMDB Trigger Policy (Premium Gold)
+
+### M-1 TMDB details-first is mandatory
+
+If typed `tmdbRef` exists for an item:
+
+* TMDB enrichment MUST prioritize DETAILS_BY_ID to fill SSOT images fast.
+
+### M-2 Search is fallback only
+
+TMDB search/resolution for missing IDs is:
+
+* lower priority than DETAILS_BY_ID
+* bounded by attempt/cooldown policy
+
+### M-3 UI image SSOT is upgrade-only
+
+* `tmdbRef present` ≠ images ready
+* Primary image selection MUST be:
+
+  * `canonical.tmdbPosterRef ?: source.bestPosterRef ?: placeholder`
+* Once `canonical.tmdbPosterRef` exists, UI MUST NOT revert to source poster automatically.
+
+---
+
+## 8) FireTV 32-bit Safety Rules (MANDATORY)
+
+### F-1 No heavy work during cold start
+
+No heavy scans may run during the cold-start window.
+
+### F-2 Bounded memory in all background execution
+
+All background work must:
+
+* use bounded batches
+* persist frequently
+* avoid payload logging
+
+---
+
+## 9) Observability (MANDATORY)
+
+### O-1 Minimal passive indicator for sync
+
+UI MUST provide a small passive sync indicator:
+
+* IDLE / RUNNING / SUCCESS / FAILED
+* No blocking dialogs.
+
+### O-2 UnifiedLog only
+
+All logs MUST use UnifiedLog; no secrets.
+
+---
+
+## 10) Acceptance Criteria (Binding)
+
+This contract is satisfied only if:
+
+* App first frame renders quickly on FireTV and phone.
+* No duplicate bootstraps start from multiple locations.
+* Auto-sync never starts heavy work immediately at process start.
+* Sync triggers only via SSOT scheduler and unique work names.
+* Users can run the app with zero, one, or multiple sources.
+* TMDB enrichment upgrades posters without flicker.
+
+---
diff --git a/core/epg-model/build.gradle.kts b/core/epg-model/build.gradle.kts
new file mode 100644
index 00000000..ce954d6f
--- /dev/null
+++ b/core/epg-model/build.gradle.kts
@@ -0,0 +1,31 @@
+plugins {
+    id("com.android.library")
+    id("org.jetbrains.kotlin.android")
+}
+
+android {
+    namespace = "com.fishit.player.core.epg.model"
+    compileSdk = 35
+
+    defaultConfig {
+        minSdk = 24
+    }
+
+    compileOptions {
+        sourceCompatibility = JavaVersion.VERSION_17
+        targetCompatibility = JavaVersion.VERSION_17
+    }
+
+    kotlinOptions {
+        jvmTarget = "17"
+    }
+}
+
+dependencies {
+    // Kotlin stdlib only - pure model module
+    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
+
+    // Testing
+    testImplementation("junit:junit:4.13.2")
+    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
+}
diff --git a/core/epg-model/src/main/AndroidManifest.xml b/core/epg-model/src/main/AndroidManifest.xml
new file mode 100644
index 00000000..8072ee00
--- /dev/null
+++ b/core/epg-model/src/main/AndroidManifest.xml
@@ -0,0 +1,2 @@
+<?xml version="1.0" encoding="utf-8"?>
+<manifest />
diff --git a/core/epg-model/src/main/java/com/fishit/player/core/epg/model/CanonicalChannelId.kt b/core/epg-model/src/main/java/com/fishit/player/core/epg/model/CanonicalChannelId.kt
new file mode 100644
index 00000000..bd49f2a4
--- /dev/null
+++ b/core/epg-model/src/main/java/com/fishit/player/core/epg/model/CanonicalChannelId.kt
@@ -0,0 +1,70 @@
+package com.fishit.player.core.epg.model
+
+/**
+ * CanonicalChannelId – Stable channel identity across sources.
+ *
+ * Per EPG_SYSTEM_CONTRACT_V1.md (EPG-30):
+ * For Xtream: `CanonicalChannelId = "xtream:<providerKey>:<channel_id>"`
+ *
+ * This ensures:
+ * - Unique identification per provider account
+ * - Stable EPG mapping regardless of provider stream_id changes
+ * - Clean separation from ephemeral IDs
+ *
+ * @param value The canonical channel identifier string
+ */
+@JvmInline
+value class CanonicalChannelId(val value: String) {
+    init {
+        require(value.isNotBlank()) { "CanonicalChannelId must not be blank" }
+    }
+
+    companion object {
+        /**
+         * Create Xtream channel ID from provider key and EPG channel ID.
+         *
+         * @param providerKey Unique provider/account identifier
+         * @param epgChannelId The epg_channel_id from Xtream stream
+         * @return CanonicalChannelId or null if epgChannelId is blank
+         */
+        fun fromXtream(providerKey: String, epgChannelId: String?): CanonicalChannelId? {
+            if (epgChannelId.isNullOrBlank()) return null
+            return CanonicalChannelId("xtream:$providerKey:$epgChannelId")
+        }
+
+        /**
+         * Create Xtream channel ID using stream ID as fallback.
+         * Use only when epg_channel_id is not available.
+         *
+         * @param providerKey Unique provider/account identifier
+         * @param streamId The live stream ID
+         * @return CanonicalChannelId
+         */
+        fun fromXtreamStreamId(providerKey: String, streamId: Int): CanonicalChannelId {
+            return CanonicalChannelId("xtream:$providerKey:stream_$streamId")
+        }
+    }
+
+    /**
+     * Extract the source type from the canonical ID.
+     * @return "xtream" or other source identifier
+     */
+    val sourceType: String
+        get() = value.substringBefore(':')
+
+    /**
+     * Extract the provider key from the canonical ID.
+     * @return Provider key or empty if malformed
+     */
+    val providerKey: String
+        get() = value.split(':').getOrNull(1).orEmpty()
+
+    /**
+     * Extract the raw channel identifier from the canonical ID.
+     * @return Raw channel ID or empty if malformed
+     */
+    val rawChannelId: String
+        get() = value.split(':', limit = 3).getOrNull(2).orEmpty()
+
+    override fun toString(): String = value
+}
diff --git a/core/epg-model/src/main/java/com/fishit/player/core/epg/model/EpgSource.kt b/core/epg-model/src/main/java/com/fishit/player/core/epg/model/EpgSource.kt
new file mode 100644
index 00000000..73dd3081
--- /dev/null
+++ b/core/epg-model/src/main/java/com/fishit/player/core/epg/model/EpgSource.kt
@@ -0,0 +1,33 @@
+package com.fishit.player.core.epg.model
+
+/**
+ * EpgSource – Identifies the origin of EPG data.
+ *
+ * Per EPG_SYSTEM_CONTRACT_V1.md (EPG-10, EPG-11):
+ * - XTREAM is the primary canonical source
+ * - Future sources may include XMLTV, etc.
+ */
+enum class EpgSource {
+    /**
+     * Xtream Codes API EPG (get_short_epg, get_simple_data_table).
+     * Primary canonical source per contract.
+     */
+    XTREAM,
+
+    /**
+     * XMLTV format EPG (future).
+     * External XMLTV file import.
+     */
+    XMLTV,
+
+    /**
+     * Manual EPG entry (future).
+     * User-created programme entries.
+     */
+    MANUAL,
+
+    /**
+     * Unknown/fallback source.
+     */
+    UNKNOWN,
+}
diff --git a/core/epg-model/src/main/java/com/fishit/player/core/epg/model/EpgTimeRange.kt b/core/epg-model/src/main/java/com/fishit/player/core/epg/model/EpgTimeRange.kt
new file mode 100644
index 00000000..64b092fe
--- /dev/null
+++ b/core/epg-model/src/main/java/com/fishit/player/core/epg/model/EpgTimeRange.kt
@@ -0,0 +1,75 @@
+package com.fishit.player.core.epg.model
+
+import java.time.Instant
+import java.time.temporal.ChronoUnit
+
+/**
+ * EpgTimeRange – Time window for EPG queries.
+ *
+ * Provides convenient factory methods for common EPG query patterns.
+ */
+data class EpgTimeRange(
+    val from: Instant,
+    val to: Instant,
+) {
+    init {
+        require(to > from) { "to ($to) must be > from ($from)" }
+    }
+
+    /**
+     * Duration of this range in hours.
+     */
+    val durationHours: Long
+        get() = ChronoUnit.HOURS.between(from, to)
+
+    /**
+     * Check if an instant falls within this range.
+     */
+    operator fun contains(instant: Instant): Boolean {
+        return instant >= from && instant < to
+    }
+
+    companion object {
+        /**
+         * Create range for "now + next N hours".
+         */
+        fun nowPlusHours(hours: Long): EpgTimeRange {
+            val now = Instant.now()
+            return EpgTimeRange(
+                from = now,
+                to = now.plus(hours, ChronoUnit.HOURS),
+            )
+        }
+
+        /**
+         * Create range for today (UTC).
+         */
+        fun today(): EpgTimeRange {
+            val now = Instant.now()
+            val startOfDay = now.truncatedTo(ChronoUnit.DAYS)
+            val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)
+            return EpgTimeRange(from = startOfDay, to = endOfDay)
+        }
+
+        /**
+         * Create range for N days starting from today (UTC).
+         */
+        fun nextDays(days: Int): EpgTimeRange {
+            val now = Instant.now()
+            val startOfDay = now.truncatedTo(ChronoUnit.DAYS)
+            val end = startOfDay.plus(days.toLong(), ChronoUnit.DAYS)
+            return EpgTimeRange(from = startOfDay, to = end)
+        }
+
+        /**
+         * Create range centered around now (±hours).
+         */
+        fun aroundNow(hoursBack: Long, hoursForward: Long): EpgTimeRange {
+            val now = Instant.now()
+            return EpgTimeRange(
+                from = now.minus(hoursBack, ChronoUnit.HOURS),
+                to = now.plus(hoursForward, ChronoUnit.HOURS),
+            )
+        }
+    }
+}
diff --git a/core/epg-model/src/main/java/com/fishit/player/core/epg/model/NormalizedEpgEvent.kt b/core/epg-model/src/main/java/com/fishit/player/core/epg/model/NormalizedEpgEvent.kt
new file mode 100644
index 00000000..65f5f884
--- /dev/null
+++ b/core/epg-model/src/main/java/com/fishit/player/core/epg/model/NormalizedEpgEvent.kt
@@ -0,0 +1,85 @@
+package com.fishit.player.core.epg.model
+
+import java.time.Instant
+import java.time.ZoneOffset
+import java.time.format.DateTimeFormatter
+
+/**
+ * NormalizedEpgEvent – Canonical EPG programme representation.
+ *
+ * Per EPG_SYSTEM_CONTRACT_V1.md (EPG-31, EPG-32):
+ * - All fields are normalized (Base64 decoded, UTC timestamps)
+ * - `epgKey` is a stable hash for idempotent upsert
+ * - `dayBucketUtc` enables bucketed storage (EPG-50)
+ *
+ * This is the domain model consumed by UI and stored in persistence.
+ * Raw EPG DTOs (from transport) are converted to this format by the normalizer.
+ *
+ * @param channelId Stable canonical channel identifier
+ * @param startUtc Programme start time (UTC)
+ * @param endUtc Programme end time (UTC)
+ * @param title Programme title (Base64 decoded)
+ * @param description Programme description (Base64 decoded, optional)
+ * @param language Language code (normalized, lowercase, optional)
+ * @param hasCatchup Whether catchup/archive is available
+ * @param isNowPlaying Whether this is the current programme (snapshot)
+ * @param source EPG data source
+ * @param sourceEventId Raw event ID from source (not stable)
+ * @param sourceEpgId Raw EPG ID from source (not stable)
+ * @param epgKey Stable hash key for idempotent upsert
+ */
+data class NormalizedEpgEvent(
+    val channelId: CanonicalChannelId,
+    val startUtc: Instant,
+    val endUtc: Instant,
+    val title: String,
+    val description: String? = null,
+    val language: String? = null,
+    val hasCatchup: Boolean = false,
+    val isNowPlaying: Boolean = false,
+    val source: EpgSource = EpgSource.XTREAM,
+    val sourceEventId: String? = null,
+    val sourceEpgId: String? = null,
+    val epgKey: String,
+) {
+    init {
+        require(endUtc > startUtc) {
+            "endUtc ($endUtc) must be > startUtc ($startUtc)"
+        }
+        require(title.isNotBlank()) { "title must not be blank" }
+        require(epgKey.isNotBlank()) { "epgKey must not be blank" }
+    }
+
+    /**
+     * Day bucket for storage partitioning (UTC 00:00 boundary).
+     * Per EPG_SYSTEM_CONTRACT_V1.md (EPG-50).
+     */
+    val dayBucketUtc: String
+        get() = DAY_FORMATTER.format(startUtc)
+
+    /**
+     * Duration in minutes.
+     */
+    val durationMinutes: Int
+        get() = ((endUtc.epochSecond - startUtc.epochSecond) / 60).toInt()
+
+    /**
+     * Check if event is currently active at given instant.
+     */
+    fun isActiveAt(instant: Instant): Boolean {
+        return instant >= startUtc && instant < endUtc
+    }
+
+    /**
+     * Check if event overlaps with a time range.
+     */
+    fun overlaps(rangeStart: Instant, rangeEnd: Instant): Boolean {
+        return startUtc < rangeEnd && endUtc > rangeStart
+    }
+
+    companion object {
+        private val DAY_FORMATTER = DateTimeFormatter
+            .ofPattern("yyyy-MM-dd")
+            .withZone(ZoneOffset.UTC)
+    }
+}
```
