Commit: 8b7b17aca47fc8ebd1b88fe24b4e1031753fc564
Author: karlokarate
Date: Mon Dec 22 09:11:17 2025 +0000
Message: refactor: enforce parallelism SSOT, remove epg-model module, move startup contract


 .../com/fishit/player/v2/CatalogSyncBootstrap.kt   | 116 +---
 .../player/core/catalogsync/EpgSyncService.kt      |  26 +
 core/epg-model/build.gradle.kts                    |  31 -
 core/epg-model/src/main/AndroidManifest.xml        |   2 -
 .../player/core/epg/model/CanonicalChannelId.kt    |  70 --
 .../com/fishit/player/core/epg/model/EpgSource.kt  |  33 -
 .../fishit/player/core/epg/model/EpgTimeRange.kt   |  75 ---
 .../player/core/epg/model/NormalizedEpgEvent.kt    |  85 ---
 docs/diff_commit_074a8c4a_parallelism_ssot.md      | 595 ++++++++++++++++
 docs/diff_commit_6d687a88_startup_contract.md      | 749 +++++++++++++++++++++
 .../v2/STARTUP_TRIGGER_CONTRACT.md                 |   8 +-
 .../transport/xtream/DefaultXtreamApiClient.kt     |  25 +-
 .../infra/transport/xtream/XtreamDiscovery.kt      |   2 +-
 .../infra/transport/xtream/XtreamParallelism.kt    |  17 -
 14 files changed, 1416 insertions(+), 418 deletions(-)
--- FULL DIFF ---
diff --git a/app-v2/src/main/java/com/fishit/player/v2/CatalogSyncBootstrap.kt b/app-v2/src/main/java/com/fishit/player/v2/CatalogSyncBootstrap.kt
index 9ec6a5c9..adca7eb9 100644
--- a/app-v2/src/main/java/com/fishit/player/v2/CatalogSyncBootstrap.kt
+++ b/app-v2/src/main/java/com/fishit/player/v2/CatalogSyncBootstrap.kt
@@ -1,11 +1,7 @@
 package com.fishit.player.v2
 
 import com.fishit.player.core.catalogsync.CatalogSyncWorkScheduler
-import com.fishit.player.core.feature.auth.TelegramAuthRepository
-import com.fishit.player.core.feature.auth.TelegramAuthState
-import com.fishit.player.feature.onboarding.domain.XtreamAuthRepository
-import com.fishit.player.feature.onboarding.domain.XtreamAuthState
-import com.fishit.player.feature.onboarding.domain.XtreamConnectionState
+import com.fishit.player.core.catalogsync.SourceActivationStore
 import com.fishit.player.infra.logging.UnifiedLog
 import com.fishit.player.v2.di.AppScopeModule
 import java.util.concurrent.atomic.AtomicBoolean
@@ -15,19 +11,18 @@ import javax.inject.Singleton
 import kotlinx.coroutines.CancellationException
 import kotlinx.coroutines.CoroutineScope
 import kotlinx.coroutines.delay
-import kotlinx.coroutines.flow.combine
 import kotlinx.coroutines.flow.distinctUntilChanged
 import kotlinx.coroutines.flow.first
 import kotlinx.coroutines.flow.map
-import kotlinx.coroutines.flow.onEach
 import kotlinx.coroutines.launch
 
 /**
- * Bootstraps catalog synchronization once per app session after authentication succeeds.
+ * Bootstraps catalog synchronization once per app session after at least one source is active.
  *
- * Responsibilities:
- * - Observe auth/connection state from Telegram and Xtream repositories (domain layer)
- * - Trigger catalog sync when authentication is ready
+ * Contract: STARTUP_TRIGGER_CONTRACT (T-1, T-2, T-3)
+ * - Observes SourceActivationStore (SSOT for source state)
+ * - Triggers catalog sync when activeSources.isNotEmpty()
+ * - Supports all sources: Xtream, Telegram, IO (IO-only usage triggers sync correctly)
  * - Does NOT handle session initialization (that's XtreamSessionBootstrap's job)
  */
 @Singleton
@@ -35,8 +30,7 @@ class CatalogSyncBootstrap
     @Inject
     constructor(
         private val catalogSyncWorkScheduler: CatalogSyncWorkScheduler,
-        private val telegramAuthRepository: TelegramAuthRepository,
-        private val xtreamAuthRepository: XtreamAuthRepository,
+        private val sourceActivationStore: SourceActivationStore,
         @Named(AppScopeModule.APP_LIFECYCLE_SCOPE)
         private val appScope: CoroutineScope,
     ) {
@@ -53,36 +47,18 @@ class CatalogSyncBootstrap
 
             appScope.launch {
                 try {
-                    val readinessSnapshot =
-                        combine(
-                            telegramAuthRepository.authState.map { state ->
-                                state.toTelegramReadiness()
-                            },
-                            combine(
-                                xtreamAuthRepository.connectionState,
-                                xtreamAuthRepository.authState,
-                            ) { connection, auth ->
-                                XtreamReadiness(connectionState = connection, authState = auth)
-                            },
-                        ) { telegramReadiness, xtreamReadiness ->
-                            val snapshot = AuthReadinessSnapshot(telegramReadiness, xtreamReadiness)
-                            UnifiedLog.d(TAG) {
-                                "Auth state update: telegram=${telegramReadiness.state} (ready=${telegramReadiness.isReady}), " +
-                                    "xtreamConn=${xtreamReadiness.connectionState} " +
-                                    "xtreamAuth=${xtreamReadiness.authState} (ready=${xtreamReadiness.isReady})"
-                            }
-                            snapshot
+                    // Contract T-1: Wait for at least one active source
+                    // Uses SourceActivationStore (SSOT) which includes Xtream, Telegram, and IO
+                    sourceActivationStore.observeStates()
+                        .map { snapshot -> snapshot.activeSources }
+                        .distinctUntilChanged()
+                        .first { activeSources ->
+                            val hasActive = activeSources.isNotEmpty()
+                            UnifiedLog.d(TAG) { "Source activation check: activeSources=$activeSources, hasActive=$hasActive" }
+                            hasActive
                         }
-                            .distinctUntilChanged()
-                            .onEach { snapshot ->
-                                UnifiedLog.d(TAG) { "Checking auth state: hasAuth=${snapshot.hasReadySource}" }
-                            }
-                            .first { snapshot -> snapshot.hasReadySource }
 
-                    triggerSync(
-                        telegramReady = readinessSnapshot.telegram.isReady,
-                        xtreamConnected = readinessSnapshot.xtream.isReady,
-                    )
+                    triggerSync()
                 } catch (cancellation: CancellationException) {
                     UnifiedLog.d(TAG) { "Catalog sync bootstrap cancelled" }
                     throw cancellation
@@ -92,55 +68,27 @@ class CatalogSyncBootstrap
             }
         }
 
-/**
-     * Contract T-2: Mandatory delay gate before sync.
-     * This prevents sync from racing with other startup tasks.
-     */
-    private suspend fun triggerSync(
-        telegramReady: Boolean,
-        xtreamConnected: Boolean,
-    ) {
-        if (!hasTriggered.compareAndSet(false, true)) return
+        /**
+         * Contract T-2: Mandatory delay gate before sync.
+         * This prevents sync from racing with other startup tasks.
+         */
+        private suspend fun triggerSync() {
+            if (!hasTriggered.compareAndSet(false, true)) return
 
-        // Contract T-2: Delay gate - wait before triggering sync
-        delay(SYNC_DELAY_MS)
+            // Contract T-2: Delay gate - wait before triggering sync
+            delay(SYNC_DELAY_MS)
 
-        // Contract T-3: Only sync if at least one source is ready
-        if (!telegramReady && !xtreamConnected) {
-            UnifiedLog.i(TAG) { "No sources ready, skipping catalog sync" }
-            return
-        }
+            // Contract T-3: Final check - only sync if at least one source is still active
+            val activeSources = sourceActivationStore.getActiveSources()
+            if (activeSources.isEmpty()) {
+                UnifiedLog.i(TAG) { "No sources active after delay, skipping catalog sync" }
+                return
+            }
 
-            UnifiedLog.i(TAG) { "Catalog sync bootstrap triggered; telegram=$telegramReady xtream=$xtreamConnected" }
+            UnifiedLog.i(TAG) { "Catalog sync bootstrap triggered; activeSources=$activeSources" }
             catalogSyncWorkScheduler.enqueueAutoSync()
         }
 
-        private data class AuthReadinessSnapshot(
-            val telegram: TelegramReadiness,
-            val xtream: XtreamReadiness,
-        ) {
-            val hasReadySource: Boolean
-                get() = telegram.isReady || xtream.isReady
-        }
-
-        private data class TelegramReadiness(
-            val state: TelegramAuthState,
-            val isReady: Boolean,
-        )
-
-        private data class XtreamReadiness(
-            val connectionState: XtreamConnectionState,
-            val authState: XtreamAuthState,
-        ) {
-            val isReady: Boolean
-                get() =
-                    connectionState is XtreamConnectionState.Connected &&
-                        authState is XtreamAuthState.Authenticated
-        }
-
-        private fun TelegramAuthState.toTelegramReadiness(): TelegramReadiness =
-            TelegramReadiness(state = this, isReady = this is TelegramAuthState.Connected)
-
         private companion object {
             private const val TAG = "CatalogSyncBootstrap"
 
diff --git a/core/catalog-sync/src/main/java/com/fishit/player/core/catalogsync/EpgSyncService.kt b/core/catalog-sync/src/main/java/com/fishit/player/core/catalogsync/EpgSyncService.kt
new file mode 100644
index 00000000..c021f0ca
--- /dev/null
+++ b/core/catalog-sync/src/main/java/com/fishit/player/core/catalogsync/EpgSyncService.kt
@@ -0,0 +1,26 @@
+package com.fishit.player.core.catalogsync
+
+/**
+ * EPG Sync Service interface.
+ *
+ * This is a placeholder interface for future EPG synchronization functionality.
+ * No implementation exists yet - this is intentionally a no-op interface.
+ *
+ * Contract: STARTUP_TRIGGER_CONTRACT (X-1)
+ * - EPG sync will use WorkManager unique work: `epg_sync_global`
+ * - No EPG network calls are made until the EPG contract is finalized
+ *
+ * TODO(EPG): Implement epg_sync_global and EPG normalization per upcoming EPG contract.
+ */
+interface EpgSyncService {
+
+    /**
+     * Request an EPG refresh.
+     *
+     * This is a no-op placeholder. When implemented, this will enqueue
+     * EPG sync work via WorkManager with the unique work name `epg_sync_global`.
+     *
+     * @param reason Human-readable reason for the refresh request (for logging)
+     */
+    fun requestEpgRefresh(reason: String)
+}
diff --git a/core/epg-model/build.gradle.kts b/core/epg-model/build.gradle.kts
deleted file mode 100644
index ce954d6f..00000000
--- a/core/epg-model/build.gradle.kts
+++ /dev/null
@@ -1,31 +0,0 @@
-plugins {
-    id("com.android.library")
-    id("org.jetbrains.kotlin.android")
-}
-
-android {
-    namespace = "com.fishit.player.core.epg.model"
-    compileSdk = 35
-
-    defaultConfig {
-        minSdk = 24
-    }
-
-    compileOptions {
-        sourceCompatibility = JavaVersion.VERSION_17
-        targetCompatibility = JavaVersion.VERSION_17
-    }
-
-    kotlinOptions {
-        jvmTarget = "17"
-    }
-}
-
-dependencies {
-    // Kotlin stdlib only - pure model module
-    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
-
-    // Testing
-    testImplementation("junit:junit:4.13.2")
-    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
-}
diff --git a/core/epg-model/src/main/AndroidManifest.xml b/core/epg-model/src/main/AndroidManifest.xml
deleted file mode 100644
index 8072ee00..00000000
--- a/core/epg-model/src/main/AndroidManifest.xml
+++ /dev/null
@@ -1,2 +0,0 @@
-<?xml version="1.0" encoding="utf-8"?>
-<manifest />
diff --git a/core/epg-model/src/main/java/com/fishit/player/core/epg/model/CanonicalChannelId.kt b/core/epg-model/src/main/java/com/fishit/player/core/epg/model/CanonicalChannelId.kt
deleted file mode 100644
index bd49f2a4..00000000
--- a/core/epg-model/src/main/java/com/fishit/player/core/epg/model/CanonicalChannelId.kt
+++ /dev/null
@@ -1,70 +0,0 @@
-package com.fishit.player.core.epg.model
-
-/**
- * CanonicalChannelId – Stable channel identity across sources.
- *
- * Per EPG_SYSTEM_CONTRACT_V1.md (EPG-30):
- * For Xtream: `CanonicalChannelId = "xtream:<providerKey>:<channel_id>"`
- *
- * This ensures:
- * - Unique identification per provider account
- * - Stable EPG mapping regardless of provider stream_id changes
- * - Clean separation from ephemeral IDs
- *
- * @param value The canonical channel identifier string
- */
-@JvmInline
-value class CanonicalChannelId(val value: String) {
-    init {
-        require(value.isNotBlank()) { "CanonicalChannelId must not be blank" }
-    }
-
-    companion object {
-        /**
-         * Create Xtream channel ID from provider key and EPG channel ID.
-         *
-         * @param providerKey Unique provider/account identifier
-         * @param epgChannelId The epg_channel_id from Xtream stream
-         * @return CanonicalChannelId or null if epgChannelId is blank
-         */
-        fun fromXtream(providerKey: String, epgChannelId: String?): CanonicalChannelId? {
-            if (epgChannelId.isNullOrBlank()) return null
-            return CanonicalChannelId("xtream:$providerKey:$epgChannelId")
-        }
-
-        /**
-         * Create Xtream channel ID using stream ID as fallback.
-         * Use only when epg_channel_id is not available.
-         *
-         * @param providerKey Unique provider/account identifier
-         * @param streamId The live stream ID
-         * @return CanonicalChannelId
-         */
-        fun fromXtreamStreamId(providerKey: String, streamId: Int): CanonicalChannelId {
-            return CanonicalChannelId("xtream:$providerKey:stream_$streamId")
-        }
-    }
-
-    /**
-     * Extract the source type from the canonical ID.
-     * @return "xtream" or other source identifier
-     */
-    val sourceType: String
-        get() = value.substringBefore(':')
-
-    /**
-     * Extract the provider key from the canonical ID.
-     * @return Provider key or empty if malformed
-     */
-    val providerKey: String
-        get() = value.split(':').getOrNull(1).orEmpty()
-
-    /**
-     * Extract the raw channel identifier from the canonical ID.
-     * @return Raw channel ID or empty if malformed
-     */
-    val rawChannelId: String
-        get() = value.split(':', limit = 3).getOrNull(2).orEmpty()
-
-    override fun toString(): String = value
-}
diff --git a/core/epg-model/src/main/java/com/fishit/player/core/epg/model/EpgSource.kt b/core/epg-model/src/main/java/com/fishit/player/core/epg/model/EpgSource.kt
deleted file mode 100644
index 73dd3081..00000000
--- a/core/epg-model/src/main/java/com/fishit/player/core/epg/model/EpgSource.kt
+++ /dev/null
@@ -1,33 +0,0 @@
-package com.fishit.player.core.epg.model
-
-/**
- * EpgSource – Identifies the origin of EPG data.
- *
- * Per EPG_SYSTEM_CONTRACT_V1.md (EPG-10, EPG-11):
- * - XTREAM is the primary canonical source
- * - Future sources may include XMLTV, etc.
- */
-enum class EpgSource {
-    /**
-     * Xtream Codes API EPG (get_short_epg, get_simple_data_table).
-     * Primary canonical source per contract.
-     */
-    XTREAM,
-
-    /**
-     * XMLTV format EPG (future).
-     * External XMLTV file import.
-     */
-    XMLTV,
-
-    /**
-     * Manual EPG entry (future).
-     * User-created programme entries.
-     */
-    MANUAL,
-
-    /**
-     * Unknown/fallback source.
-     */
-    UNKNOWN,
-}
diff --git a/core/epg-model/src/main/java/com/fishit/player/core/epg/model/EpgTimeRange.kt b/core/epg-model/src/main/java/com/fishit/player/core/epg/model/EpgTimeRange.kt
deleted file mode 100644
index 64b092fe..00000000
--- a/core/epg-model/src/main/java/com/fishit/player/core/epg/model/EpgTimeRange.kt
+++ /dev/null
@@ -1,75 +0,0 @@
-package com.fishit.player.core.epg.model
-
-import java.time.Instant
-import java.time.temporal.ChronoUnit
-
-/**
- * EpgTimeRange – Time window for EPG queries.
- *
- * Provides convenient factory methods for common EPG query patterns.
- */
-data class EpgTimeRange(
-    val from: Instant,
-    val to: Instant,
-) {
-    init {
-        require(to > from) { "to ($to) must be > from ($from)" }
-    }
-
-    /**
-     * Duration of this range in hours.
-     */
-    val durationHours: Long
-        get() = ChronoUnit.HOURS.between(from, to)
-
-    /**
-     * Check if an instant falls within this range.
-     */
-    operator fun contains(instant: Instant): Boolean {
-        return instant >= from && instant < to
-    }
-
-    companion object {
-        /**
-         * Create range for "now + next N hours".
-         */
-        fun nowPlusHours(hours: Long): EpgTimeRange {
-            val now = Instant.now()
-            return EpgTimeRange(
-                from = now,
-                to = now.plus(hours, ChronoUnit.HOURS),
-            )
-        }
-
-        /**
-         * Create range for today (UTC).
-         */
-        fun today(): EpgTimeRange {
-            val now = Instant.now()
-            val startOfDay = now.truncatedTo(ChronoUnit.DAYS)
-            val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)
-            return EpgTimeRange(from = startOfDay, to = endOfDay)
-        }
-
-        /**
-         * Create range for N days starting from today (UTC).
-         */
-        fun nextDays(days: Int): EpgTimeRange {
-            val now = Instant.now()
-            val startOfDay = now.truncatedTo(ChronoUnit.DAYS)
-            val end = startOfDay.plus(days.toLong(), ChronoUnit.DAYS)
-            return EpgTimeRange(from = startOfDay, to = end)
-        }
-
-        /**
-         * Create range centered around now (±hours).
-         */
-        fun aroundNow(hoursBack: Long, hoursForward: Long): EpgTimeRange {
-            val now = Instant.now()
-            return EpgTimeRange(
-                from = now.minus(hoursBack, ChronoUnit.HOURS),
-                to = now.plus(hoursForward, ChronoUnit.HOURS),
-            )
-        }
-    }
-}
diff --git a/core/epg-model/src/main/java/com/fishit/player/core/epg/model/NormalizedEpgEvent.kt b/core/epg-model/src/main/java/com/fishit/player/core/epg/model/NormalizedEpgEvent.kt
deleted file mode 100644
index 65f5f884..00000000
--- a/core/epg-model/src/main/java/com/fishit/player/core/epg/model/NormalizedEpgEvent.kt
+++ /dev/null
@@ -1,85 +0,0 @@
-package com.fishit.player.core.epg.model
-
-import java.time.Instant
-import java.time.ZoneOffset
-import java.time.format.DateTimeFormatter
-
-/**
- * NormalizedEpgEvent – Canonical EPG programme representation.
- *
- * Per EPG_SYSTEM_CONTRACT_V1.md (EPG-31, EPG-32):
- * - All fields are normalized (Base64 decoded, UTC timestamps)
- * - `epgKey` is a stable hash for idempotent upsert
- * - `dayBucketUtc` enables bucketed storage (EPG-50)
- *
- * This is the domain model consumed by UI and stored in persistence.
- * Raw EPG DTOs (from transport) are converted to this format by the normalizer.
- *
- * @param channelId Stable canonical channel identifier
- * @param startUtc Programme start time (UTC)
- * @param endUtc Programme end time (UTC)
- * @param title Programme title (Base64 decoded)
- * @param description Programme description (Base64 decoded, optional)
- * @param language Language code (normalized, lowercase, optional)
- * @param hasCatchup Whether catchup/archive is available
- * @param isNowPlaying Whether this is the current programme (snapshot)
- * @param source EPG data source
- * @param sourceEventId Raw event ID from source (not stable)
- * @param sourceEpgId Raw EPG ID from source (not stable)
- * @param epgKey Stable hash key for idempotent upsert
- */
-data class NormalizedEpgEvent(
-    val channelId: CanonicalChannelId,
-    val startUtc: Instant,
-    val endUtc: Instant,
-    val title: String,
-    val description: String? = null,
-    val language: String? = null,
-    val hasCatchup: Boolean = false,
-    val isNowPlaying: Boolean = false,
-    val source: EpgSource = EpgSource.XTREAM,
-    val sourceEventId: String? = null,
-    val sourceEpgId: String? = null,
-    val epgKey: String,
-) {
-    init {
-        require(endUtc > startUtc) {
-            "endUtc ($endUtc) must be > startUtc ($startUtc)"
-        }
-        require(title.isNotBlank()) { "title must not be blank" }
-        require(epgKey.isNotBlank()) { "epgKey must not be blank" }
-    }
-
-    /**
-     * Day bucket for storage partitioning (UTC 00:00 boundary).
-     * Per EPG_SYSTEM_CONTRACT_V1.md (EPG-50).
-     */
-    val dayBucketUtc: String
-        get() = DAY_FORMATTER.format(startUtc)
-
-    /**
-     * Duration in minutes.
-     */
-    val durationMinutes: Int
-        get() = ((endUtc.epochSecond - startUtc.epochSecond) / 60).toInt()
-
-    /**
-     * Check if event is currently active at given instant.
-     */
-    fun isActiveAt(instant: Instant): Boolean {
-        return instant >= startUtc && instant < endUtc
-    }
-
-    /**
-     * Check if event overlaps with a time range.
-     */
-    fun overlaps(rangeStart: Instant, rangeEnd: Instant): Boolean {
-        return startUtc < rangeEnd && endUtc > rangeStart
-    }
-
-    companion object {
-        private val DAY_FORMATTER = DateTimeFormatter
-            .ofPattern("yyyy-MM-dd")
-            .withZone(ZoneOffset.UTC)
-    }
-}
diff --git a/docs/diff_commit_074a8c4a_parallelism_ssot.md b/docs/diff_commit_074a8c4a_parallelism_ssot.md
new file mode 100644
index 00000000..164e85e4
--- /dev/null
+++ b/docs/diff_commit_074a8c4a_parallelism_ssot.md
@@ -0,0 +1,595 @@
+# Git Diff: Commit 074a8c4a
+
+**Commit:** `074a8c4a0113001768084deef5196bf091568955`
+**Author:** karlokarate
+**Date:** Mon Dec 22 08:26:45 2025 +0000
+**Message:** feat(transport-xtream): implement parallelism SSOT via DI injection
+
+## Changed Files
+
+| File | Changes |
+|------|---------|
+| DefaultXtreamApiClient.kt | +64 -3 |
+| XtreamDiscovery.kt | +14 -0 |
+| XtreamParallelism.kt | +41 (new) |
+| XtreamTransportModule.kt | +31 -0 |
+| XtreamParallelismTest.kt | +48 (new) |
+| XtreamTransportConfigTest.kt | +186 (new) |
+
+**Total:** 6 files changed, 354 insertions(+), 30 deletions(-)
+
+---
+
+## Diff
+
+```diff
+diff --git a/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/DefaultXtreamApiClient.kt b/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/DefaultXtreamApiClient.kt
+index 21f221f4..ddb56584 100644
+--- a/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/DefaultXtreamApiClient.kt
++++ b/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/DefaultXtreamApiClient.kt
+@@ -28,6 +28,7 @@ import kotlinx.serialization.json.jsonObject
+ import kotlinx.serialization.json.jsonPrimitive
+ import kotlinx.serialization.json.longOrNull
+ import okhttp3.HttpUrl
++import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
+ import okhttp3.OkHttpClient
+ import okhttp3.Request
+ 
+@@ -45,10 +46,18 @@ import okhttp3.Request
+  * Basiert auf v1 XtreamClient.kt mit Verbesserungen aus:
+  * - tellytv/telly (Panel-Kompatibilität)
+  * - Real-World Testing mit verschiedenen Panels
++ *
++ * @param http OkHttpClient with Premium Contract settings (timeouts, headers, dispatcher)
++ * @param json JSON parser
++ * @param parallelism Device-aware parallelism from DI (SSOT for Semaphores)
++ * @param io Coroutine dispatcher for IO operations
++ * @param capabilityStore Optional cache for capabilities
++ * @param portStore Optional cache for resolved ports
+  */
+ class DefaultXtreamApiClient(
+     private val http: OkHttpClient,
+     private val json: Json = Json { ignoreUnknownKeys = true },
++    private val parallelism: XtreamParallelism = XtreamParallelism(XtreamTransportConfig.PARALLELISM_PHONE_TABLET),
+     private val io: CoroutineDispatcher = Dispatchers.IO,
+     private val capabilityStore: XtreamCapabilityStore? = null,
+     private val portStore: XtreamPortStore? = null,
+@@ -93,6 +102,23 @@ class DefaultXtreamApiClient(
+         private val VOD_ID_FIELDS = listOf("vod_id", "movie_id", "id", "stream_id")
+         private val LIVE_ID_FIELDS = listOf("stream_id", "id")
+         private val SERIES_ID_FIELDS = listOf("series_id", "id")
++
++        /**
++         * Redact URL for safe logging: returns "host/path" only.
++         * No query parameters (which contain credentials) are logged.
++         */
++        private fun redactUrl(url: String): String {
++            return try {
++                val httpUrl = url.toHttpUrlOrNull()
++                if (httpUrl != null) {
++                    "${httpUrl.host}${httpUrl.encodedPath}"
++                } else {
++                    "<invalid-url>"
++                }
++            } catch (_: Exception) {
++                "<invalid-url>"
++            }
++        }
+     }
+ 
+     private data class CacheEntry(
+@@ -103,12 +129,11 @@ class DefaultXtreamApiClient(
+     /**
+      * Semaphore for EPG parallel requests.
+      *
+-     * Premium Contract Section 5: Use device-class parallelism.
+-     * Note: This is a default fallback; actual parallelism is controlled via
+-     * OkHttp Dispatcher in DI module. The semaphore here is for additional
+-     * coroutine-level throttling if needed.
++     * Premium Contract Section 5: Use device-class parallelism from DI (SSOT).
++     * This semaphore provides coroutine-level throttling consistent with
++     * OkHttp Dispatcher limits.
+      */
+-    private val epgSemaphore = Semaphore(XtreamTransportConfig.PARALLELISM_PHONE_TABLET)
++    private val epgSemaphore = Semaphore(parallelism.value)
+ 
+     // =========================================================================
+     // Lifecycle
+@@ -865,8 +890,7 @@ class DefaultXtreamApiClient(
+         builder.addQueryParameter("password", cfg.password)
+ 
+         val url = builder.build().toString()
+-        val redactedUrl = url.replace(Regex("(password|username)=[^&]*"), "$1=***")
+-        UnifiedLog.d(TAG, "buildPlayerApiUrl: Built URL: $redactedUrl")
++        UnifiedLog.d(TAG, "buildPlayerApiUrl: action=$action -> ${redactUrl(url)}")
+         
+         return url
+     }
+@@ -897,13 +921,13 @@ class DefaultXtreamApiClient(
+         url: String,
+         isEpg: Boolean,
+     ): String? {
+-        val redactedUrl = url.replace(Regex("(password|username)=[^&]*"), "$1=***")
+-        UnifiedLog.d(TAG, "fetchRaw: Fetching URL: $redactedUrl, isEpg=$isEpg")
++        val safeUrl = redactUrl(url)
++        UnifiedLog.d(TAG, "fetchRaw: Fetching $safeUrl, isEpg=$isEpg")
+         
+         // Check cache
+         val cached = readCache(url, isEpg)
+         if (cached != null) {
+-            UnifiedLog.d(TAG, "fetchRaw: Cache hit for $redactedUrl, returning ${cached.length} bytes")
++            UnifiedLog.d(TAG, "fetchRaw: Cache hit for $safeUrl, returning ${cached.length} bytes")
+             return cached
+         }
+ 
+@@ -921,20 +945,20 @@ class DefaultXtreamApiClient(
+                 .build()
+ 
+         return try {
+-            UnifiedLog.d(TAG, "fetchRaw: Executing HTTP request to $redactedUrl")
++            UnifiedLog.d(TAG, "fetchRaw: Executing HTTP request to $safeUrl")
+             http.newCall(request).execute().use { response ->
+-                UnifiedLog.d(TAG, "fetchRaw: Received response code ${response.code} for $redactedUrl")
++                UnifiedLog.d(TAG, "fetchRaw: Response code ${response.code} for $safeUrl")
+                 
+                 if (!response.isSuccessful) {
+-                    UnifiedLog.w(TAG, "fetchRaw: Request failed with code ${response.code} for $redactedUrl")
++                    UnifiedLog.w(TAG, "fetchRaw: Request failed with code ${response.code} for $safeUrl")
+                     return null
+                 }
+                 
+                 val body = response.body.string()
+-                UnifiedLog.d(TAG, "fetchRaw: Received ${body.length} bytes from $redactedUrl")
++                UnifiedLog.d(TAG, "fetchRaw: Received ${body.length} bytes from $safeUrl")
+                 
+                 if (body.isEmpty()) {
+-                    UnifiedLog.w(TAG, "fetchRaw: Response body is empty for $redactedUrl")
++                    UnifiedLog.w(TAG, "fetchRaw: Response body is empty for $safeUrl")
+                     return null
+                 }
+                 
+@@ -944,7 +968,7 @@ class DefaultXtreamApiClient(
+                 body
+             }
+         } catch (e: Exception) {
+-            UnifiedLog.e(TAG, "fetchRaw: Exception while fetching $redactedUrl", e)
++            UnifiedLog.e(TAG, "fetchRaw: Exception while fetching $safeUrl", e)
+             null
+         }
+     }
+@@ -1033,8 +1057,8 @@ class DefaultXtreamApiClient(
+                     listOf(80, 8080, 8000, 8880, 2052, 2082, 2086)
+                 }
+ 
+-            // Premium Contract Section 5: Use centralized parallelism config
+-            val sem = Semaphore(XtreamTransportConfig.PARALLELISM_PHONE_TABLET)
++            // Premium Contract Section 5: Use device-class parallelism from DI (SSOT)
++            val sem = Semaphore(parallelism.value)
+             val jobs =
+                 candidates.distinct().map { port ->
+                     async { sem.withPermit { if (tryPing(config, port)) port else null } }
+@@ -1119,8 +1143,8 @@ class DefaultXtreamApiClient(
+     ): XtreamCapabilities =
+         coroutineScope {
+             val actions = mutableMapOf<String, XtreamActionCapability>()
+-            // Premium Contract Section 5: Use centralized parallelism config
+-            val sem = Semaphore(XtreamTransportConfig.PARALLELISM_PHONE_TABLET)
++            // Premium Contract Section 5: Use device-class parallelism from DI (SSOT)
++            val sem = Semaphore(parallelism.value)
+ 
+             suspend fun probe(
+                 action: String,
+diff --git a/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamDiscovery.kt b/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamDiscovery.kt
+index 95eef04c..ea034c7a 100644
+--- a/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamDiscovery.kt
++++ b/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamDiscovery.kt
+@@ -31,10 +31,16 @@ import java.util.concurrent.TimeUnit
+  * - Panel-Typ-Hinweise (Xtream-UI, XUI.ONE, etc.)
+  *
+  * Basiert auf v1 XtreamCapabilities.kt mit verbessertem Probing.
++ *
++ * @param http OkHttpClient with Premium Contract settings
++ * @param json JSON parser
++ * @param parallelism Device-aware parallelism from DI (SSOT for Semaphores)
++ * @param io Coroutine dispatcher for IO operations
+  */
+ class XtreamDiscovery(
+     private val http: OkHttpClient,
+     private val json: Json = Json { ignoreUnknownKeys = true },
++    private val parallelism: XtreamParallelism = XtreamParallelism(XtreamTransportConfig.PARALLELISM_PHONE_TABLET),
+     private val io: CoroutineDispatcher = Dispatchers.IO,
+ ) {
+     // =========================================================================
+@@ -206,8 +212,8 @@ class XtreamDiscovery(
+             val candidates =
+                 (if (isHttps) HTTPS_PORTS else HTTP_PORTS).filter { it != defaultPort }.distinct()
+ 
+-            // Premium Contract Section 5: Use centralized parallelism config
+-            val semaphore = Semaphore(XtreamTransportConfig.PARALLELISM_PHONE_TABLET)
++            // Premium Contract Section 5: Use device-class parallelism from DI (SSOT)
++            val semaphore = Semaphore(parallelism.value)
+             val jobs =
+                 candidates.map { port ->
+                     async { semaphore.withPermit { if (tryProbe(config, port)) port else null } }
+@@ -311,8 +317,8 @@ class XtreamDiscovery(
+     ): XtreamCapabilities =
+         coroutineScope {
+             val actions = mutableMapOf<String, XtreamActionCapability>()
+-            // Premium Contract Section 5: Use centralized parallelism config
+-            val semaphore = Semaphore(XtreamTransportConfig.PARALLELISM_PHONE_TABLET)
++            // Premium Contract Section 5: Use device-class parallelism from DI (SSOT)
++            val semaphore = Semaphore(parallelism.value)
+ 
+             suspend fun probe(
+                 action: String,
+diff --git a/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamParallelism.kt b/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamParallelism.kt
+new file mode 100644
+index 00000000..b9a18575
+--- /dev/null
++++ b/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamParallelism.kt
+@@ -0,0 +1,41 @@
++package com.fishit.player.infra.transport.xtream
++
++import javax.inject.Qualifier
++
++/**
++ * Qualifier for the device-aware parallelism value.
++ *
++ * Used to inject the parallelism level determined by [XtreamTransportConfig.getParallelism].
++ *
++ * Premium Contract Section 5:
++ * - Phone/Tablet: parallelism = 10
++ * - FireTV/low-RAM: parallelism = 3
++ *
++ * @see XtreamTransportConfig.getParallelism
++ */
++@Qualifier
++@Retention(AnnotationRetention.BINARY)
++annotation class XtreamParallelismValue
++
++/**
++ * XtreamParallelism – Device-aware parallelism wrapper for Xtream transport.
++ *
++ * This is a simple wrapper class (not value class due to Hilt/KSP limitations)
++ * that provides the parallelism level determined by [XtreamTransportConfig.getParallelism].
++ *
++ * Used by:
++ * - OkHttp Dispatcher (maxRequests, maxRequestsPerHost)
++ * - Coroutine Semaphores in DefaultXtreamApiClient and XtreamDiscovery
++ *
++ * Premium Contract Section 5:
++ * - Phone/Tablet: parallelism = 10
++ * - FireTV/low-RAM: parallelism = 3
++ *
++ * @property value The parallelism level (number of concurrent requests).
++ * @see XtreamTransportConfig.getParallelism
++ */
++data class XtreamParallelism(val value: Int) {
++    init {
++        require(value > 0) { "Parallelism must be positive, was $value" }
++    }
++}
+diff --git a/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/di/XtreamTransportModule.kt b/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/di/XtreamTransportModule.kt
+index 390676f9..f3cd15da 100644
+--- a/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/di/XtreamTransportModule.kt
++++ b/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/di/XtreamTransportModule.kt
+@@ -6,6 +6,7 @@ import com.fishit.player.infra.transport.xtream.EncryptedXtreamCredentialsStore
+ import com.fishit.player.infra.transport.xtream.XtreamApiClient
+ import com.fishit.player.infra.transport.xtream.XtreamCredentialsStore
+ import com.fishit.player.infra.transport.xtream.XtreamDiscovery
++import com.fishit.player.infra.transport.xtream.XtreamParallelism
+ import com.fishit.player.infra.transport.xtream.XtreamTransportConfig
+ import dagger.Binds
+ import dagger.Module
+@@ -42,6 +43,23 @@ annotation class XtreamHttpClient
+ @InstallIn(SingletonComponent::class)
+ object XtreamTransportModule {
+ 
++    /**
++     * Provides the device-aware parallelism as SSOT.
++     *
++     * Premium Contract Section 5:
++     * - Phone/Tablet: 10
++     * - FireTV/low-RAM: 3
++     *
++     * This value is used by:
++     * - OkHttp Dispatcher limits
++     * - All coroutine Semaphores in DefaultXtreamApiClient and XtreamDiscovery
++     */
++    @Provides
++    @Singleton
++    fun provideXtreamParallelism(
++        @ApplicationContext context: Context,
++    ): XtreamParallelism = XtreamParallelism(XtreamTransportConfig.getParallelism(context))
++
+     /**
+      * Provides Xtream-specific OkHttpClient with Premium Contract settings.
+      *
+@@ -65,9 +83,8 @@ object XtreamTransportModule {
+     @XtreamHttpClient
+     fun provideXtreamOkHttpClient(
+         @ApplicationContext context: Context,
++        parallelism: XtreamParallelism,
+     ): OkHttpClient {
+-        val parallelism = XtreamTransportConfig.getParallelism(context)
+-
+         return OkHttpClient.Builder()
+             // Premium Contract Section 3: HTTP Timeouts
+             .connectTimeout(XtreamTransportConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
+@@ -98,8 +115,8 @@ object XtreamTransportModule {
+                 // Premium Contract Section 5: Device-class parallelism
+                 dispatcher(
+                     okhttp3.Dispatcher().apply {
+-                        maxRequests = parallelism
+-                        maxRequestsPerHost = parallelism
++                        maxRequests = parallelism.value
++                        maxRequestsPerHost = parallelism.value
+                     },
+                 )
+             }
+@@ -119,14 +136,16 @@ object XtreamTransportModule {
+     fun provideXtreamDiscovery(
+         @XtreamHttpClient okHttpClient: OkHttpClient,
+         json: Json,
+-    ): XtreamDiscovery = XtreamDiscovery(okHttpClient, json)
++        parallelism: XtreamParallelism,
++    ): XtreamDiscovery = XtreamDiscovery(okHttpClient, json, parallelism = parallelism)
+ 
+     @Provides
+     @Singleton
+     fun provideXtreamApiClient(
+         @XtreamHttpClient okHttpClient: OkHttpClient,
+         json: Json,
+-    ): XtreamApiClient = DefaultXtreamApiClient(okHttpClient, json)
++        parallelism: XtreamParallelism,
++    ): XtreamApiClient = DefaultXtreamApiClient(okHttpClient, json, parallelism = parallelism)
+ }
+ 
+ /** Hilt module for Xtream credentials storage. */
+diff --git a/infra/transport-xtream/src/test/java/com/fishit/player/infra/transport/xtream/XtreamParallelismTest.kt b/infra/transport-xtream/src/test/java/com/fishit/player/infra/transport/xtream/XtreamParallelismTest.kt
+new file mode 100644
+index 00000000..749db315
+--- /dev/null
++++ b/infra/transport-xtream/src/test/java/com/fishit/player/infra/transport/xtream/XtreamParallelismTest.kt
+@@ -0,0 +1,48 @@
++package com.fishit.player.infra.transport.xtream
++
++import org.junit.Assert.assertEquals
++import org.junit.Assert.assertThrows
++import org.junit.Test
++
++/**
++ * Unit tests for XtreamParallelism wrapper class.
++ */
++class XtreamParallelismTest {
++
++    @Test
++    fun `XtreamParallelism stores correct value`() {
++        val parallelism = XtreamParallelism(10)
++        assertEquals(10, parallelism.value)
++    }
++
++    @Test
++    fun `XtreamParallelism accepts FireTV parallelism value`() {
++        val parallelism = XtreamParallelism(3)
++        assertEquals(3, parallelism.value)
++    }
++
++    @Test
++    fun `XtreamParallelism rejects zero value`() {
++        assertThrows(IllegalArgumentException::class.java) {
++            XtreamParallelism(0)
++        }
++    }
++
++    @Test
++    fun `XtreamParallelism rejects negative value`() {
++        assertThrows(IllegalArgumentException::class.java) {
++            XtreamParallelism(-1)
++        }
++    }
++
++    @Test
++    fun `XtreamParallelism equality works correctly`() {
++        val p1 = XtreamParallelism(10)
++        val p2 = XtreamParallelism(10)
++        val p3 = XtreamParallelism(3)
++
++        assertEquals(p1, p2)
++        assertEquals(p1.hashCode(), p2.hashCode())
++        assert(p1 != p3)
++    }
++}
+diff --git a/infra/transport-xtream/src/test/java/com/fishit/player/infra/transport/xtream/XtreamTransportConfigTest.kt b/infra/transport-xtream/src/test/java/com/fishit/player/infra/transport/xtream/XtreamTransportConfigTest.kt
+new file mode 100644
+index 00000000..edbac7e0
+--- /dev/null
++++ b/infra/transport-xtream/src/test/java/com/fishit/player/infra/transport/xtream/XtreamTransportConfigTest.kt
+@@ -0,0 +1,186 @@
++package com.fishit.player.infra.transport.xtream
++
++import android.app.ActivityManager
++import android.app.UiModeManager
++import android.content.Context
++import android.content.res.Configuration
++import io.mockk.every
++import io.mockk.mockk
++import org.junit.Assert.assertEquals
++import org.junit.Test
++
++/**
++ * Unit tests for XtreamTransportConfig.
++ *
++ * Verifies Premium Contract Section 5 compliance:
++ * - Phone/Tablet: parallelism = 10
++ * - FireTV/low-RAM: parallelism = 3
++ */
++class XtreamTransportConfigTest {
++
++    @Test
++    fun `detectDeviceClass returns PHONE_TABLET for non-TV high-RAM device`() {
++        // Arrange
++        val context = mockk<Context>()
++        val uiModeManager = mockk<UiModeManager>()
++        val activityManager = mockk<ActivityManager>()
++        val memoryInfo = ActivityManager.MemoryInfo().apply {
++            totalMem = 4L * 1024 * 1024 * 1024 // 4GB RAM
++        }
++
++        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
++        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
++        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_NORMAL
++        every { activityManager.getMemoryInfo(any()) } answers {
++            val info = firstArg<ActivityManager.MemoryInfo>()
++            info.totalMem = memoryInfo.totalMem
++        }
++        every { activityManager.isLowRamDevice } returns false
++
++        // Act
++        val result = XtreamTransportConfig.detectDeviceClass(context)
++
++        // Assert
++        assertEquals(XtreamTransportConfig.DeviceClass.PHONE_TABLET, result)
++        assertEquals(10, result.parallelism)
++    }
++
++    @Test
++    fun `detectDeviceClass returns TV_LOW_RAM for TV device`() {
++        // Arrange
++        val context = mockk<Context>()
++        val uiModeManager = mockk<UiModeManager>()
++        val activityManager = mockk<ActivityManager>()
++        val memoryInfo = ActivityManager.MemoryInfo().apply {
++            totalMem = 4L * 1024 * 1024 * 1024 // 4GB RAM
++        }
++
++        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
++        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
++        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_TELEVISION
++        every { activityManager.getMemoryInfo(any()) } answers {
++            val info = firstArg<ActivityManager.MemoryInfo>()
++            info.totalMem = memoryInfo.totalMem
++        }
++        every { activityManager.isLowRamDevice } returns false
++
++        // Act
++        val result = XtreamTransportConfig.detectDeviceClass(context)
++
++        // Assert
++        assertEquals(XtreamTransportConfig.DeviceClass.TV_LOW_RAM, result)
++        assertEquals(3, result.parallelism)
++    }
++
++    @Test
++    fun `detectDeviceClass returns TV_LOW_RAM for low-RAM device`() {
++        // Arrange
++        val context = mockk<Context>()
++        val uiModeManager = mockk<UiModeManager>()
++        val activityManager = mockk<ActivityManager>()
++        val memoryInfo = ActivityManager.MemoryInfo().apply {
++            totalMem = 1L * 1024 * 1024 * 1024 // 1GB RAM (below 2GB threshold)
++        }
++
++        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
++        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
++        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_NORMAL
++        every { activityManager.getMemoryInfo(any()) } answers {
++            val info = firstArg<ActivityManager.MemoryInfo>()
++            info.totalMem = memoryInfo.totalMem
++        }
++        every { activityManager.isLowRamDevice } returns false
++
++        // Act
++        val result = XtreamTransportConfig.detectDeviceClass(context)
++
++        // Assert
++        assertEquals(XtreamTransportConfig.DeviceClass.TV_LOW_RAM, result)
++        assertEquals(3, result.parallelism)
++    }
++
++    @Test
++    fun `detectDeviceClass returns TV_LOW_RAM when isLowRamDevice is true`() {
++        // Arrange
++        val context = mockk<Context>()
++        val uiModeManager = mockk<UiModeManager>()
++        val activityManager = mockk<ActivityManager>()
++        val memoryInfo = ActivityManager.MemoryInfo().apply {
++            totalMem = 4L * 1024 * 1024 * 1024 // 4GB RAM
++        }
++
++        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
++        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
++        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_NORMAL
++        every { activityManager.getMemoryInfo(any()) } answers {
++            val info = firstArg<ActivityManager.MemoryInfo>()
++            info.totalMem = memoryInfo.totalMem
++        }
++        every { activityManager.isLowRamDevice } returns true
++
++        // Act
++        val result = XtreamTransportConfig.detectDeviceClass(context)
++
++        // Assert
++        assertEquals(XtreamTransportConfig.DeviceClass.TV_LOW_RAM, result)
++        assertEquals(3, result.parallelism)
++    }
++
++    @Test
++    fun `getParallelism returns 10 for phone tablet`() {
++        // Arrange
++        val context = mockk<Context>()
++        val uiModeManager = mockk<UiModeManager>()
++        val activityManager = mockk<ActivityManager>()
++
++        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
++        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
++        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_NORMAL
++        every { activityManager.getMemoryInfo(any()) } answers {
++            val info = firstArg<ActivityManager.MemoryInfo>()
++            info.totalMem = 4L * 1024 * 1024 * 1024
++        }
++        every { activityManager.isLowRamDevice } returns false
++
++        // Act
++        val result = XtreamTransportConfig.getParallelism(context)
++
++        // Assert
++        assertEquals(10, result)
++    }
++
++    @Test
++    fun `getParallelism returns 3 for FireTV`() {
++        // Arrange
++        val context = mockk<Context>()
++        val uiModeManager = mockk<UiModeManager>()
++        val activityManager = mockk<ActivityManager>()
++
++        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
++        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
++        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_TELEVISION
++        every { activityManager.getMemoryInfo(any()) } answers {
++            val info = firstArg<ActivityManager.MemoryInfo>()
++            info.totalMem = 2L * 1024 * 1024 * 1024
++        }
++        every { activityManager.isLowRamDevice } returns false
++
++        // Act
++        val result = XtreamTransportConfig.getParallelism(context)
++
++        // Assert
++        assertEquals(3, result)
++    }
++
++    @Test
++    fun `constants match Premium Contract Section 5`() {
++        assertEquals(10, XtreamTransportConfig.PARALLELISM_PHONE_TABLET)
++        assertEquals(3, XtreamTransportConfig.PARALLELISM_FIRETV_LOW_RAM)
++    }
++
++    @Test
++    fun `DeviceClass parallelism values are correct`() {
++        assertEquals(10, XtreamTransportConfig.DeviceClass.PHONE_TABLET.parallelism)
++        assertEquals(3, XtreamTransportConfig.DeviceClass.TV_LOW_RAM.parallelism)
++    }
++}
+```
diff --git a/docs/diff_commit_6d687a88_startup_contract.md b/docs/diff_commit_6d687a88_startup_contract.md
new file mode 100644
index 00000000..287e2b5b
--- /dev/null
+++ b/docs/diff_commit_6d687a88_startup_contract.md
@@ -0,0 +1,749 @@
+# Git Diff: Commit 6d687a88
+
+**Commit:** `6d687a8886b43ed07d03b120ef530f159a03ad35`
+**Author:** karlokarate
+**Date:** Mon Dec 22 08:15:09 2025 +0000
+**Message:** fix(startup): implement startup_trigger_contract compliance
+
+## Changed Files
+
+| File | Changes |
+|------|---------|
+| CatalogSyncBootstrap.kt | +30 -1 |
+| FishItV2Application.kt | +14 -0 |
+| MainActivity.kt | +15 -0 |
+| AppNavHost.kt | +18 -0 |
+| startup_trigger_contract.md | +211 (new) |
+| core/epg-model/* | +265 (new module) |
+
+**Total:** 12 files changed, 592 insertions(+), 37 deletions(-)
+
+---
+
+## Diff
+
+```diff
+diff --git a/app-v2/src/main/java/com/fishit/player/v2/CatalogSyncBootstrap.kt b/app-v2/src/main/java/com/fishit/player/v2/CatalogSyncBootstrap.kt
+index e3641d3e..9ec6a5c9 100644
+--- a/app-v2/src/main/java/com/fishit/player/v2/CatalogSyncBootstrap.kt
++++ b/app-v2/src/main/java/com/fishit/player/v2/CatalogSyncBootstrap.kt
+@@ -14,6 +14,7 @@ import javax.inject.Named
+ import javax.inject.Singleton
+ import kotlinx.coroutines.CancellationException
+ import kotlinx.coroutines.CoroutineScope
++import kotlinx.coroutines.delay
+ import kotlinx.coroutines.flow.combine
+ import kotlinx.coroutines.flow.distinctUntilChanged
+ import kotlinx.coroutines.flow.first
+@@ -91,11 +92,24 @@ class CatalogSyncBootstrap
+             }
+         }
+ 
+-        private fun triggerSync(
+-            telegramReady: Boolean,
+-            xtreamConnected: Boolean,
+-        ) {
+-            if (!hasTriggered.compareAndSet(false, true)) return
++/**
++     * Contract T-2: Mandatory delay gate before sync.
++     * This prevents sync from racing with other startup tasks.
++     */
++    private suspend fun triggerSync(
++        telegramReady: Boolean,
++        xtreamConnected: Boolean,
++    ) {
++        if (!hasTriggered.compareAndSet(false, true)) return
++
++        // Contract T-2: Delay gate - wait before triggering sync
++        delay(SYNC_DELAY_MS)
++
++        // Contract T-3: Only sync if at least one source is ready
++        if (!telegramReady && !xtreamConnected) {
++            UnifiedLog.i(TAG) { "No sources ready, skipping catalog sync" }
++            return
++        }
+ 
+             UnifiedLog.i(TAG) { "Catalog sync bootstrap triggered; telegram=$telegramReady xtream=$xtreamConnected" }
+             catalogSyncWorkScheduler.enqueueAutoSync()
+@@ -129,5 +143,11 @@ class CatalogSyncBootstrap
+ 
+         private companion object {
+             private const val TAG = "CatalogSyncBootstrap"
++
++            /**
++             * Contract T-2: Delay gate in milliseconds.
++             * Prevents sync from racing with other startup operations.
++             */
++            private const val SYNC_DELAY_MS = 5_000L
+         }
+     }
+diff --git a/app-v2/src/main/java/com/fishit/player/v2/FishItV2Application.kt b/app-v2/src/main/java/com/fishit/player/v2/FishItV2Application.kt
+index 716e6dff..ea3fad4b 100644
+--- a/app-v2/src/main/java/com/fishit/player/v2/FishItV2Application.kt
++++ b/app-v2/src/main/java/com/fishit/player/v2/FishItV2Application.kt
+@@ -68,23 +68,21 @@ class FishItV2Application :
+     override fun onCreate() {
+         super.onCreate()
+ 
+-        val workManagerInitialized = WORK_MANAGER_INITIALIZED.compareAndSet(false, true)
+-        if (workManagerInitialized) {
+-            WorkManager.initialize(this, workConfiguration)
+-        }
+-
+-        // Early initialization of unified logging system to ensure all subsequent logging works correctly
++        // Contract S-1: UnifiedLog MUST be initialized BEFORE any other subsystem
+         UnifiedLogInitializer.init(isDebug = BuildConfig.DEBUG)
+ 
++        // WorkManager initialization (after logging is ready)
++        val workManagerInitialized = WORK_MANAGER_INITIALIZED.compareAndSet(false, true)
+         if (workManagerInitialized) {
++            WorkManager.initialize(this, workConfiguration)
+             UnifiedLog.i(TAG) { "WorkManager initialized" }
+         }
+ 
+-        // Start source activation observers (must be before bootstraps)
++        // Contract S-2: Start lightweight observers (no heavy work)
+         sourceActivationObserver.start(appScope)
+         telegramActivationObserver.start()
+         
+-        // Start session bootstraps
++        // Contract S-3: Bootstraps started here ONLY (not in MainActivity or AppNavHost)
+         xtreamSessionBootstrap.start()
+         catalogSyncBootstrap.start()
+     }
+diff --git a/app-v2/src/main/java/com/fishit/player/v2/MainActivity.kt b/app-v2/src/main/java/com/fishit/player/v2/MainActivity.kt
+index 962af460..77998d5e 100644
+--- a/app-v2/src/main/java/com/fishit/player/v2/MainActivity.kt
++++ b/app-v2/src/main/java/com/fishit/player/v2/MainActivity.kt
+@@ -11,35 +11,26 @@ import androidx.compose.ui.Modifier
+ import com.fishit.player.v2.navigation.AppNavHost
+ import com.fishit.player.v2.ui.theme.FishItV2Theme
+ import dagger.hilt.android.AndroidEntryPoint
+-import javax.inject.Inject
+ 
+ /**
+  * Main entry activity for FishIT Player v2.
+  */
+ @AndroidEntryPoint
+ class MainActivity : ComponentActivity() {
+-    @Inject
+-    lateinit var catalogSyncBootstrap: CatalogSyncBootstrap
+-
+-    @Inject
+-    lateinit var xtreamSessionBootstrap: XtreamSessionBootstrap
++    // Contract S-3: Bootstraps are started in Application.onCreate() ONLY
++    // No duplicate bootstrap injections or starts here
+ 
+     override fun onCreate(savedInstanceState: Bundle?) {
+         super.onCreate(savedInstanceState)
+         enableEdgeToEdge()
+ 
+-        // Initialize Xtream session before catalog sync
+-        xtreamSessionBootstrap.start()
+-
+         setContent {
+             FishItV2Theme {
+                 Surface(
+                     modifier = Modifier.fillMaxSize(),
+                     color = MaterialTheme.colorScheme.background,
+                 ) {
+-                    AppNavHost(
+-                        catalogSyncBootstrap = catalogSyncBootstrap,
+-                    )
++                    AppNavHost()
+                 }
+             }
+         }
+diff --git a/app-v2/src/main/java/com/fishit/player/v2/navigation/AppNavHost.kt b/app-v2/src/main/java/com/fishit/player/v2/navigation/AppNavHost.kt
+index 5493991a..57f2f9cc 100644
+--- a/app-v2/src/main/java/com/fishit/player/v2/navigation/AppNavHost.kt
++++ b/app-v2/src/main/java/com/fishit/player/v2/navigation/AppNavHost.kt
+@@ -25,10 +25,8 @@ import com.fishit.player.feature.home.debug.DebugPlaybackScreen
+ import com.fishit.player.feature.onboarding.StartScreen
+ import com.fishit.player.feature.settings.DebugScreen
+ import com.fishit.player.ui.PlayerScreen
+-import com.fishit.player.v2.CatalogSyncBootstrap
+ import com.fishit.player.v2.navigation.PlayerNavViewModel
+ import com.fishit.player.v2.ui.debug.DebugSkeletonScreen
+-import kotlinx.coroutines.flow.collectLatest
+ 
+ /**
+  * Top-level navigation host for FishIT Player v2.
+@@ -37,21 +35,17 @@ import kotlinx.coroutines.flow.collectLatest
+  * Start -> Home -> Detail -> Player
+  *               -> Debug -> DebugPlayback (test player)
+  *               -> Settings
++ * 
++ * Contract S-3: Bootstraps are started in Application.onCreate() ONLY.
++ * No bootstrap triggers in navigation or UI layers.
+  */
+ @Composable
+-fun AppNavHost(
+-    catalogSyncBootstrap: CatalogSyncBootstrap,
+-) {
++fun AppNavHost() {
+     val navController = rememberNavController()
+ 
+     FishTheme {
+-        LaunchedEffect(navController, catalogSyncBootstrap) {
+-            navController.currentBackStackEntryFlow.collectLatest { backStackEntry ->
+-                if (backStackEntry.destination.route == Routes.HOME) {
+-                    catalogSyncBootstrap.start()
+-                }
+-            }
+-        }
++        // Contract S-3: Removed LaunchedEffect bootstrap trigger
++        // Sync is managed by CatalogSyncBootstrap in Application.onCreate()
+ 
+         NavHost(
+             navController = navController,
+diff --git a/contracts/startup_trigger_contract.md b/contracts/startup_trigger_contract.md
+new file mode 100644
+index 00000000..2faed2e3
+--- /dev/null
++++ b/contracts/startup_trigger_contract.md
+@@ -0,0 +1,211 @@
++## App Startup & Sync Trigger Contract — Premium Gold
++
++**Version:** 1.0
++**Date:** 2025-12-22
++**Status:** Binding
++**Scope:** App startup sequencing, auto-sync triggering, warm-up vs reliable sync, phone/tablet + FireTV 32-bit
++
++> This contract is binding. Any deviation is a bug unless explicitly documented and approved.
++
++---
++
++## 1) Goals
++
++* **Fast startup** (no jank, especially on FireTV 32-bit)
++* **Immediate UI clarity** (never "empty and silent")
++* **Deterministic background sync** (SSOT WorkManager queue only)
++* **No duplicate bootstraps** and no parallel sync paths
++* **Optional sources** (Xtream/Telegram/IO independent)
++
++---
++
++## 2) Startup Order (MANDATORY)
++
++### S-1 Unified logging first
++
++`UnifiedLogInitializer.init(isDebug = BuildConfig.DEBUG)` MUST be called in `Application.onCreate()` before any other subsystem starts.
++
++### S-2 AppShell starts observers, not heavy work
++
++The following MAY start in `Application.onCreate()`:
++
++* `SourceActivationObserver`
++* `TelegramActivationObserver`
++
++These MUST be **lightweight** (no full scans, no large network calls).
++
++### S-3 Exactly one bootstrap location
++
++Each bootstrap MUST be started in exactly one place:
++
++* either `Application.onCreate()` **or** `MainActivity.onCreate()`
++* never both.
++
++Duplicate `.start()` calls for the same bootstrap are forbidden.
++
++---
++
++## 3) UI First Principles (MANDATORY)
++
++### U-1 UI must render meaningful state immediately
++
++On first render of HOME/LIVE:
++
++* If no sources are active: show "Add source" actions.
++* If sources active but library empty: show "Sync pending" state + passive indicator.
++* UI MUST remain usable while sync runs.
++
++### U-2 UI never performs sync work
++
++UI MUST NOT:
++
++* call transport
++* call pipelines
++* call `CatalogSyncService.sync*()` directly
++  UI triggers sync only through the SSOT scheduler.
++
++---
++
++## 4) Sync Execution Paths (MANDATORY)
++
++### X-1 Single SSOT background path
++
++All reliable sync MUST run through WorkManager unique works only:
++
++* `catalog_sync_global`
++* `tmdb_enrichment_global`
++* (if EPG exists) `epg_sync_global`
++
++No other unique work names for these concerns may exist.
++
++### X-2 Warm-up is allowed but must be bounded and non-critical
++
++A **WarmUpMode** MAY exist to improve perceived responsiveness:
++
++* runs only while the app is in foreground
++* performs small, bounded batches only
++* MUST NOT be required for correctness
++* MUST NOT write large amounts in one run
++* MUST NOT bypass persistence rules (still writes via repositories if it writes)
++
++WarmUpMode MUST never replace WorkManager reliable sync.
++
++---
++
++## 5) Auto-Sync Trigger Policy (Premium Gold)
++
++### T-1 Auto-sync is deferred by design
++
++Auto-sync MUST NOT start heavy work immediately at process start.
++
++Auto-sync MAY be triggered only by one of these events:
++
++1. **Source activation transition** (INACTIVE → ACTIVE)
++2. **User reaches a content screen** (HOME or LIVE becomes visible)
++3. **Idle warm-up window** (app in foreground, user idle) with a short delay
++
++### T-2 Mandatory delay gate (to protect startup)
++
++When auto-sync is triggered, it MUST be scheduled with a short delay window:
++
++* **Default delay:** 3–10 seconds (implementation chooses one constant)
++* Purpose: ensure UI has stabilized and first frame is rendered.
++
++### T-3 No auto-sync if nothing is active
++
++If `activeSources` is empty:
++
++* auto-sync MUST NOT enqueue any work.
++
++### T-4 Cancel when sources become empty
++
++If `activeSources` transitions to empty:
++
++* `catalog_sync_global` MUST be cancelled by name.
++
++---
++
++## 6) Source Order & Serial Execution (MANDATORY)
++
++### Q-1 Serial queue only
++
++At most one catalog sync chain runs at a time.
++
++### Q-2 Fixed source order
++
++If multiple sources are active, catalog sync order MUST be:
++
++1. Xtream
++2. Telegram
++3. IO (if active)
++
++---
++
++## 7) TMDB Trigger Policy (Premium Gold)
++
++### M-1 TMDB details-first is mandatory
++
++If typed `tmdbRef` exists for an item:
++
++* TMDB enrichment MUST prioritize DETAILS_BY_ID to fill SSOT images fast.
++
++### M-2 Search is fallback only
++
++TMDB search/resolution for missing IDs is:
++
++* lower priority than DETAILS_BY_ID
++* bounded by attempt/cooldown policy
++
++### M-3 UI image SSOT is upgrade-only
++
++* `tmdbRef present` ≠ images ready
++* Primary image selection MUST be:
++
++  * `canonical.tmdbPosterRef ?: source.bestPosterRef ?: placeholder`
++* Once `canonical.tmdbPosterRef` exists, UI MUST NOT revert to source poster automatically.
++
++---
++
++## 8) FireTV 32-bit Safety Rules (MANDATORY)
++
++### F-1 No heavy work during cold start
++
++No heavy scans may run during the cold-start window.
++
++### F-2 Bounded memory in all background execution
++
++All background work must:
++
++* use bounded batches
++* persist frequently
++* avoid payload logging
++
++---
++
++## 9) Observability (MANDATORY)
++
++### O-1 Minimal passive indicator for sync
++
++UI MUST provide a small passive sync indicator:
++
++* IDLE / RUNNING / SUCCESS / FAILED
++* No blocking dialogs.
++
++### O-2 UnifiedLog only
++
++All logs MUST use UnifiedLog; no secrets.
++
++---
++
++## 10) Acceptance Criteria (Binding)
++
++This contract is satisfied only if:
++
++* App first frame renders quickly on FireTV and phone.
++* No duplicate bootstraps start from multiple locations.
++* Auto-sync never starts heavy work immediately at process start.
++* Sync triggers only via SSOT scheduler and unique work names.
++* Users can run the app with zero, one, or multiple sources.
++* TMDB enrichment upgrades posters without flicker.
++
++---
+diff --git a/core/epg-model/build.gradle.kts b/core/epg-model/build.gradle.kts
+new file mode 100644
+index 00000000..ce954d6f
+--- /dev/null
++++ b/core/epg-model/build.gradle.kts
+@@ -0,0 +1,31 @@
++plugins {
++    id("com.android.library")
++    id("org.jetbrains.kotlin.android")
++}
++
++android {
++    namespace = "com.fishit.player.core.epg.model"
++    compileSdk = 35
++
++    defaultConfig {
++        minSdk = 24
++    }
++
++    compileOptions {
++        sourceCompatibility = JavaVersion.VERSION_17
++        targetCompatibility = JavaVersion.VERSION_17
++    }
++
++    kotlinOptions {
++        jvmTarget = "17"
++    }
++}
++
++dependencies {
++    // Kotlin stdlib only - pure model module
++    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
++
++    // Testing
++    testImplementation("junit:junit:4.13.2")
++    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
++}
+diff --git a/core/epg-model/src/main/AndroidManifest.xml b/core/epg-model/src/main/AndroidManifest.xml
+new file mode 100644
+index 00000000..8072ee00
+--- /dev/null
++++ b/core/epg-model/src/main/AndroidManifest.xml
+@@ -0,0 +1,2 @@
++<?xml version="1.0" encoding="utf-8"?>
++<manifest />
+diff --git a/core/epg-model/src/main/java/com/fishit/player/core/epg/model/CanonicalChannelId.kt b/core/epg-model/src/main/java/com/fishit/player/core/epg/model/CanonicalChannelId.kt
+new file mode 100644
+index 00000000..bd49f2a4
+--- /dev/null
++++ b/core/epg-model/src/main/java/com/fishit/player/core/epg/model/CanonicalChannelId.kt
+@@ -0,0 +1,70 @@
++package com.fishit.player.core.epg.model
++
++/**
++ * CanonicalChannelId – Stable channel identity across sources.
++ *
++ * Per EPG_SYSTEM_CONTRACT_V1.md (EPG-30):
++ * For Xtream: `CanonicalChannelId = "xtream:<providerKey>:<channel_id>"`
++ *
++ * This ensures:
++ * - Unique identification per provider account
++ * - Stable EPG mapping regardless of provider stream_id changes
++ * - Clean separation from ephemeral IDs
++ *
++ * @param value The canonical channel identifier string
++ */
++@JvmInline
++value class CanonicalChannelId(val value: String) {
++    init {
++        require(value.isNotBlank()) { "CanonicalChannelId must not be blank" }
++    }
++
++    companion object {
++        /**
++         * Create Xtream channel ID from provider key and EPG channel ID.
++         *
++         * @param providerKey Unique provider/account identifier
++         * @param epgChannelId The epg_channel_id from Xtream stream
++         * @return CanonicalChannelId or null if epgChannelId is blank
++         */
++        fun fromXtream(providerKey: String, epgChannelId: String?): CanonicalChannelId? {
++            if (epgChannelId.isNullOrBlank()) return null
++            return CanonicalChannelId("xtream:$providerKey:$epgChannelId")
++        }
++
++        /**
++         * Create Xtream channel ID using stream ID as fallback.
++         * Use only when epg_channel_id is not available.
++         *
++         * @param providerKey Unique provider/account identifier
++         * @param streamId The live stream ID
++         * @return CanonicalChannelId
++         */
++        fun fromXtreamStreamId(providerKey: String, streamId: Int): CanonicalChannelId {
++            return CanonicalChannelId("xtream:$providerKey:stream_$streamId")
++        }
++    }
++
++    /**
++     * Extract the source type from the canonical ID.
++     * @return "xtream" or other source identifier
++     */
++    val sourceType: String
++        get() = value.substringBefore(':')
++
++    /**
++     * Extract the provider key from the canonical ID.
++     * @return Provider key or empty if malformed
++     */
++    val providerKey: String
++        get() = value.split(':').getOrNull(1).orEmpty()
++
++    /**
++     * Extract the raw channel identifier from the canonical ID.
++     * @return Raw channel ID or empty if malformed
++     */
++    val rawChannelId: String
++        get() = value.split(':', limit = 3).getOrNull(2).orEmpty()
++
++    override fun toString(): String = value
++}
+diff --git a/core/epg-model/src/main/java/com/fishit/player/core/epg/model/EpgSource.kt b/core/epg-model/src/main/java/com/fishit/player/core/epg/model/EpgSource.kt
+new file mode 100644
+index 00000000..73dd3081
+--- /dev/null
++++ b/core/epg-model/src/main/java/com/fishit/player/core/epg/model/EpgSource.kt
+@@ -0,0 +1,33 @@
++package com.fishit.player.core.epg.model
++
++/**
++ * EpgSource – Identifies the origin of EPG data.
++ *
++ * Per EPG_SYSTEM_CONTRACT_V1.md (EPG-10, EPG-11):
++ * - XTREAM is the primary canonical source
++ * - Future sources may include XMLTV, etc.
++ */
++enum class EpgSource {
++    /**
++     * Xtream Codes API EPG (get_short_epg, get_simple_data_table).
++     * Primary canonical source per contract.
++     */
++    XTREAM,
++
++    /**
++     * XMLTV format EPG (future).
++     * External XMLTV file import.
++     */
++    XMLTV,
++
++    /**
++     * Manual EPG entry (future).
++     * User-created programme entries.
++     */
++    MANUAL,
++
++    /**
++     * Unknown/fallback source.
++     */
++    UNKNOWN,
++}
+diff --git a/core/epg-model/src/main/java/com/fishit/player/core/epg/model/EpgTimeRange.kt b/core/epg-model/src/main/java/com/fishit/player/core/epg/model/EpgTimeRange.kt
+new file mode 100644
+index 00000000..64b092fe
+--- /dev/null
++++ b/core/epg-model/src/main/java/com/fishit/player/core/epg/model/EpgTimeRange.kt
+@@ -0,0 +1,75 @@
++package com.fishit.player.core.epg.model
++
++import java.time.Instant
++import java.time.temporal.ChronoUnit
++
++/**
++ * EpgTimeRange – Time window for EPG queries.
++ *
++ * Provides convenient factory methods for common EPG query patterns.
++ */
++data class EpgTimeRange(
++    val from: Instant,
++    val to: Instant,
++) {
++    init {
++        require(to > from) { "to ($to) must be > from ($from)" }
++    }
++
++    /**
++     * Duration of this range in hours.
++     */
++    val durationHours: Long
++        get() = ChronoUnit.HOURS.between(from, to)
++
++    /**
++     * Check if an instant falls within this range.
++     */
++    operator fun contains(instant: Instant): Boolean {
++        return instant >= from && instant < to
++    }
++
++    companion object {
++        /**
++         * Create range for "now + next N hours".
++         */
++        fun nowPlusHours(hours: Long): EpgTimeRange {
++            val now = Instant.now()
++            return EpgTimeRange(
++                from = now,
++                to = now.plus(hours, ChronoUnit.HOURS),
++            )
++        }
++
++        /**
++         * Create range for today (UTC).
++         */
++        fun today(): EpgTimeRange {
++            val now = Instant.now()
++            val startOfDay = now.truncatedTo(ChronoUnit.DAYS)
++            val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)
++            return EpgTimeRange(from = startOfDay, to = endOfDay)
++        }
++
++        /**
++         * Create range for N days starting from today (UTC).
++         */
++        fun nextDays(days: Int): EpgTimeRange {
++            val now = Instant.now()
++            val startOfDay = now.truncatedTo(ChronoUnit.DAYS)
++            val end = startOfDay.plus(days.toLong(), ChronoUnit.DAYS)
++            return EpgTimeRange(from = startOfDay, to = end)
++        }
++
++        /**
++         * Create range centered around now (±hours).
++         */
++        fun aroundNow(hoursBack: Long, hoursForward: Long): EpgTimeRange {
++            val now = Instant.now()
++            return EpgTimeRange(
++                from = now.minus(hoursBack, ChronoUnit.HOURS),
++                to = now.plus(hoursForward, ChronoUnit.HOURS),
++            )
++        }
++    }
++}
+diff --git a/core/epg-model/src/main/java/com/fishit/player/core/epg/model/NormalizedEpgEvent.kt b/core/epg-model/src/main/java/com/fishit/player/core/epg/model/NormalizedEpgEvent.kt
+new file mode 100644
+index 00000000..65f5f884
+--- /dev/null
++++ b/core/epg-model/src/main/java/com/fishit/player/core/epg/model/NormalizedEpgEvent.kt
+@@ -0,0 +1,85 @@
++package com.fishit.player.core.epg.model
++
++import java.time.Instant
++import java.time.ZoneOffset
++import java.time.format.DateTimeFormatter
++
++/**
++ * NormalizedEpgEvent – Canonical EPG programme representation.
++ *
++ * Per EPG_SYSTEM_CONTRACT_V1.md (EPG-31, EPG-32):
++ * - All fields are normalized (Base64 decoded, UTC timestamps)
++ * - `epgKey` is a stable hash for idempotent upsert
++ * - `dayBucketUtc` enables bucketed storage (EPG-50)
++ *
++ * This is the domain model consumed by UI and stored in persistence.
++ * Raw EPG DTOs (from transport) are converted to this format by the normalizer.
++ *
++ * @param channelId Stable canonical channel identifier
++ * @param startUtc Programme start time (UTC)
++ * @param endUtc Programme end time (UTC)
++ * @param title Programme title (Base64 decoded)
++ * @param description Programme description (Base64 decoded, optional)
++ * @param language Language code (normalized, lowercase, optional)
++ * @param hasCatchup Whether catchup/archive is available
++ * @param isNowPlaying Whether this is the current programme (snapshot)
++ * @param source EPG data source
++ * @param sourceEventId Raw event ID from source (not stable)
++ * @param sourceEpgId Raw EPG ID from source (not stable)
++ * @param epgKey Stable hash key for idempotent upsert
++ */
++data class NormalizedEpgEvent(
++    val channelId: CanonicalChannelId,
++    val startUtc: Instant,
++    val endUtc: Instant,
++    val title: String,
++    val description: String? = null,
++    val language: String? = null,
++    val hasCatchup: Boolean = false,
++    val isNowPlaying: Boolean = false,
++    val source: EpgSource = EpgSource.XTREAM,
++    val sourceEventId: String? = null,
++    val sourceEpgId: String? = null,
++    val epgKey: String,
++) {
++    init {
++        require(endUtc > startUtc) {
++            "endUtc ($endUtc) must be > startUtc ($startUtc)"
++        }
++        require(title.isNotBlank()) { "title must not be blank" }
++        require(epgKey.isNotBlank()) { "epgKey must not be blank" }
++    }
++
++    /**
++     * Day bucket for storage partitioning (UTC 00:00 boundary).
++     * Per EPG_SYSTEM_CONTRACT_V1.md (EPG-50).
++     */
++    val dayBucketUtc: String
++        get() = DAY_FORMATTER.format(startUtc)
++
++    /**
++     * Duration in minutes.
++     */
++    val durationMinutes: Int
++        get() = ((endUtc.epochSecond - startUtc.epochSecond) / 60).toInt()
++
++    /**
++     * Check if event is currently active at given instant.
++     */
++    fun isActiveAt(instant: Instant): Boolean {
++        return instant >= startUtc && instant < endUtc
++    }
++
++    /**
++     * Check if event overlaps with a time range.
++     */
++    fun overlaps(rangeStart: Instant, rangeEnd: Instant): Boolean {
++        return startUtc < rangeEnd && endUtc > rangeStart
++    }
++
++    companion object {
++        private val DAY_FORMATTER = DateTimeFormatter
++            .ofPattern("yyyy-MM-dd")
++            .withZone(ZoneOffset.UTC)
++    }
++}
+```
diff --git a/contracts/startup_trigger_contract.md b/docs/v2/STARTUP_TRIGGER_CONTRACT.md
similarity index 95%
rename from contracts/startup_trigger_contract.md
rename to docs/v2/STARTUP_TRIGGER_CONTRACT.md
index 2faed2e3..e6bec9e4 100644
--- a/contracts/startup_trigger_contract.md
+++ b/docs/v2/STARTUP_TRIGGER_CONTRACT.md
@@ -1,4 +1,4 @@
-## App Startup & Sync Trigger Contract — Premium Gold
+# App Startup & Sync Trigger Contract — Premium Gold
 
 **Version:** 1.0
 **Date:** 2025-12-22
@@ -12,7 +12,7 @@
 ## 1) Goals
 
 * **Fast startup** (no jank, especially on FireTV 32-bit)
-* **Immediate UI clarity** (never “empty and silent”)
+* **Immediate UI clarity** (never "empty and silent")
 * **Deterministic background sync** (SSOT WorkManager queue only)
 * **No duplicate bootstraps** and no parallel sync paths
 * **Optional sources** (Xtream/Telegram/IO independent)
@@ -51,8 +51,8 @@ Duplicate `.start()` calls for the same bootstrap are forbidden.
 
 On first render of HOME/LIVE:
 
-* If no sources are active: show “Add source” actions.
-* If sources active but library empty: show “Sync pending” state + passive indicator.
+* If no sources are active: show "Add source" actions.
+* If sources active but library empty: show "Sync pending" state + passive indicator.
 * UI MUST remain usable while sync runs.
 
 ### U-2 UI never performs sync work
diff --git a/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/DefaultXtreamApiClient.kt b/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/DefaultXtreamApiClient.kt
index ddb56584..43093dff 100644
--- a/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/DefaultXtreamApiClient.kt
+++ b/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/DefaultXtreamApiClient.kt
@@ -57,7 +57,7 @@ import okhttp3.Request
 class DefaultXtreamApiClient(
     private val http: OkHttpClient,
     private val json: Json = Json { ignoreUnknownKeys = true },
-    private val parallelism: XtreamParallelism = XtreamParallelism(XtreamTransportConfig.PARALLELISM_PHONE_TABLET),
+    private val parallelism: XtreamParallelism,
     private val io: CoroutineDispatcher = Dispatchers.IO,
     private val capabilityStore: XtreamCapabilityStore? = null,
     private val portStore: XtreamPortStore? = null,
@@ -921,13 +921,9 @@ class DefaultXtreamApiClient(
         url: String,
         isEpg: Boolean,
     ): String? {
-        val safeUrl = redactUrl(url)
-        UnifiedLog.d(TAG, "fetchRaw: Fetching $safeUrl, isEpg=$isEpg")
-        
-        // Check cache
+        // Check cache first (no logging for cache hits to reduce noise)
         val cached = readCache(url, isEpg)
         if (cached != null) {
-            UnifiedLog.d(TAG, "fetchRaw: Cache hit for $safeUrl, returning ${cached.length} bytes")
             return cached
         }
 
@@ -944,31 +940,28 @@ class DefaultXtreamApiClient(
                 .get()
                 .build()
 
+        val safeUrl = redactUrl(url)
         return try {
-            UnifiedLog.d(TAG, "fetchRaw: Executing HTTP request to $safeUrl")
             http.newCall(request).execute().use { response ->
-                UnifiedLog.d(TAG, "fetchRaw: Response code ${response.code} for $safeUrl")
-                
                 if (!response.isSuccessful) {
-                    UnifiedLog.w(TAG, "fetchRaw: Request failed with code ${response.code} for $safeUrl")
+                    // INFO only on failure (contract-compliant)
+                    UnifiedLog.i(TAG, "fetchRaw: HTTP ${response.code} for $safeUrl")
                     return null
                 }
                 
                 val body = response.body.string()
-                UnifiedLog.d(TAG, "fetchRaw: Received ${body.length} bytes from $safeUrl")
                 
                 if (body.isEmpty()) {
-                    UnifiedLog.w(TAG, "fetchRaw: Response body is empty for $safeUrl")
+                    UnifiedLog.i(TAG, "fetchRaw: Empty response for $safeUrl")
                     return null
                 }
                 
-                if (body.isNotEmpty()) {
-                    writeCache(url, body)
-                }
+                writeCache(url, body)
                 body
             }
         } catch (e: Exception) {
-            UnifiedLog.e(TAG, "fetchRaw: Exception while fetching $safeUrl", e)
+            // INFO for exception summary (no stack trace in release)
+            UnifiedLog.i(TAG, "fetchRaw: Failed $safeUrl - ${e.javaClass.simpleName}")
             null
         }
     }
diff --git a/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamDiscovery.kt b/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamDiscovery.kt
index ea034c7a..fb9d2729 100644
--- a/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamDiscovery.kt
+++ b/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamDiscovery.kt
@@ -40,7 +40,7 @@ import java.util.concurrent.TimeUnit
 class XtreamDiscovery(
     private val http: OkHttpClient,
     private val json: Json = Json { ignoreUnknownKeys = true },
-    private val parallelism: XtreamParallelism = XtreamParallelism(XtreamTransportConfig.PARALLELISM_PHONE_TABLET),
+    private val parallelism: XtreamParallelism,
     private val io: CoroutineDispatcher = Dispatchers.IO,
 ) {
     // =========================================================================
diff --git a/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamParallelism.kt b/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamParallelism.kt
index b9a18575..bb6f0578 100644
--- a/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamParallelism.kt
+++ b/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamParallelism.kt
@@ -1,22 +1,5 @@
 package com.fishit.player.infra.transport.xtream
 
-import javax.inject.Qualifier
-
-/**
- * Qualifier for the device-aware parallelism value.
- *
- * Used to inject the parallelism level determined by [XtreamTransportConfig.getParallelism].
- *
- * Premium Contract Section 5:
- * - Phone/Tablet: parallelism = 10
- * - FireTV/low-RAM: parallelism = 3
- *
- * @see XtreamTransportConfig.getParallelism
- */
-@Qualifier
-@Retention(AnnotationRetention.BINARY)
-annotation class XtreamParallelismValue
-
 /**
  * XtreamParallelism – Device-aware parallelism wrapper for Xtream transport.
  *
