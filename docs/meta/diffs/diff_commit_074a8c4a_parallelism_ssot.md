# Git Diff: Commit 074a8c4a

**Commit:** `074a8c4a0113001768084deef5196bf091568955`
**Author:** karlokarate
**Date:** Mon Dec 22 08:26:45 2025 +0000
**Message:** feat(transport-xtream): implement parallelism SSOT via DI injection

## Changed Files

| File | Changes |
|------|---------|
| DefaultXtreamApiClient.kt | +64 -3 |
| XtreamDiscovery.kt | +14 -0 |
| XtreamParallelism.kt | +41 (new) |
| XtreamTransportModule.kt | +31 -0 |
| XtreamParallelismTest.kt | +48 (new) |
| XtreamTransportConfigTest.kt | +186 (new) |

**Total:** 6 files changed, 354 insertions(+), 30 deletions(-)

---

## Diff

```diff
diff --git a/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/DefaultXtreamApiClient.kt b/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/DefaultXtreamApiClient.kt
index 21f221f4..ddb56584 100644
--- a/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/DefaultXtreamApiClient.kt
+++ b/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/DefaultXtreamApiClient.kt
@@ -28,6 +28,7 @@ import kotlinx.serialization.json.jsonObject
 import kotlinx.serialization.json.jsonPrimitive
 import kotlinx.serialization.json.longOrNull
 import okhttp3.HttpUrl
+import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
 import okhttp3.OkHttpClient
 import okhttp3.Request
 
@@ -45,10 +46,18 @@ import okhttp3.Request
  * Basiert auf v1 XtreamClient.kt mit Verbesserungen aus:
  * - tellytv/telly (Panel-Kompatibilität)
  * - Real-World Testing mit verschiedenen Panels
+ *
+ * @param http OkHttpClient with Premium Contract settings (timeouts, headers, dispatcher)
+ * @param json JSON parser
+ * @param parallelism Device-aware parallelism from DI (SSOT for Semaphores)
+ * @param io Coroutine dispatcher for IO operations
+ * @param capabilityStore Optional cache for capabilities
+ * @param portStore Optional cache for resolved ports
  */
 class DefaultXtreamApiClient(
     private val http: OkHttpClient,
     private val json: Json = Json { ignoreUnknownKeys = true },
+    private val parallelism: XtreamParallelism = XtreamParallelism(XtreamTransportConfig.PARALLELISM_PHONE_TABLET),
     private val io: CoroutineDispatcher = Dispatchers.IO,
     private val capabilityStore: XtreamCapabilityStore? = null,
     private val portStore: XtreamPortStore? = null,
@@ -93,6 +102,23 @@ class DefaultXtreamApiClient(
         private val VOD_ID_FIELDS = listOf("vod_id", "movie_id", "id", "stream_id")
         private val LIVE_ID_FIELDS = listOf("stream_id", "id")
         private val SERIES_ID_FIELDS = listOf("series_id", "id")
+
+        /**
+         * Redact URL for safe logging: returns "host/path" only.
+         * No query parameters (which contain credentials) are logged.
+         */
+        private fun redactUrl(url: String): String {
+            return try {
+                val httpUrl = url.toHttpUrlOrNull()
+                if (httpUrl != null) {
+                    "${httpUrl.host}${httpUrl.encodedPath}"
+                } else {
+                    "<invalid-url>"
+                }
+            } catch (_: Exception) {
+                "<invalid-url>"
+            }
+        }
     }
 
     private data class CacheEntry(
@@ -103,12 +129,11 @@ class DefaultXtreamApiClient(
     /**
      * Semaphore for EPG parallel requests.
      *
-     * Premium Contract Section 5: Use device-class parallelism.
-     * Note: This is a default fallback; actual parallelism is controlled via
-     * OkHttp Dispatcher in DI module. The semaphore here is for additional
-     * coroutine-level throttling if needed.
+     * Premium Contract Section 5: Use device-class parallelism from DI (SSOT).
+     * This semaphore provides coroutine-level throttling consistent with
+     * OkHttp Dispatcher limits.
      */
-    private val epgSemaphore = Semaphore(XtreamTransportConfig.PARALLELISM_PHONE_TABLET)
+    private val epgSemaphore = Semaphore(parallelism.value)
 
     // =========================================================================
     // Lifecycle
@@ -865,8 +890,7 @@ class DefaultXtreamApiClient(
         builder.addQueryParameter("password", cfg.password)
 
         val url = builder.build().toString()
-        val redactedUrl = url.replace(Regex("(password|username)=[^&]*"), "$1=***")
-        UnifiedLog.d(TAG, "buildPlayerApiUrl: Built URL: $redactedUrl")
+        UnifiedLog.d(TAG, "buildPlayerApiUrl: action=$action -> ${redactUrl(url)}")
         
         return url
     }
@@ -897,13 +921,13 @@ class DefaultXtreamApiClient(
         url: String,
         isEpg: Boolean,
     ): String? {
-        val redactedUrl = url.replace(Regex("(password|username)=[^&]*"), "$1=***")
-        UnifiedLog.d(TAG, "fetchRaw: Fetching URL: $redactedUrl, isEpg=$isEpg")
+        val safeUrl = redactUrl(url)
+        UnifiedLog.d(TAG, "fetchRaw: Fetching $safeUrl, isEpg=$isEpg")
         
         // Check cache
         val cached = readCache(url, isEpg)
         if (cached != null) {
-            UnifiedLog.d(TAG, "fetchRaw: Cache hit for $redactedUrl, returning ${cached.length} bytes")
+            UnifiedLog.d(TAG, "fetchRaw: Cache hit for $safeUrl, returning ${cached.length} bytes")
             return cached
         }
 
@@ -921,20 +945,20 @@ class DefaultXtreamApiClient(
                 .build()
 
         return try {
-            UnifiedLog.d(TAG, "fetchRaw: Executing HTTP request to $redactedUrl")
+            UnifiedLog.d(TAG, "fetchRaw: Executing HTTP request to $safeUrl")
             http.newCall(request).execute().use { response ->
-                UnifiedLog.d(TAG, "fetchRaw: Received response code ${response.code} for $redactedUrl")
+                UnifiedLog.d(TAG, "fetchRaw: Response code ${response.code} for $safeUrl")
                 
                 if (!response.isSuccessful) {
-                    UnifiedLog.w(TAG, "fetchRaw: Request failed with code ${response.code} for $redactedUrl")
+                    UnifiedLog.w(TAG, "fetchRaw: Request failed with code ${response.code} for $safeUrl")
                     return null
                 }
                 
                 val body = response.body.string()
-                UnifiedLog.d(TAG, "fetchRaw: Received ${body.length} bytes from $redactedUrl")
+                UnifiedLog.d(TAG, "fetchRaw: Received ${body.length} bytes from $safeUrl")
                 
                 if (body.isEmpty()) {
-                    UnifiedLog.w(TAG, "fetchRaw: Response body is empty for $redactedUrl")
+                    UnifiedLog.w(TAG, "fetchRaw: Response body is empty for $safeUrl")
                     return null
                 }
                 
@@ -944,7 +968,7 @@ class DefaultXtreamApiClient(
                 body
             }
         } catch (e: Exception) {
-            UnifiedLog.e(TAG, "fetchRaw: Exception while fetching $redactedUrl", e)
+            UnifiedLog.e(TAG, "fetchRaw: Exception while fetching $safeUrl", e)
             null
         }
     }
@@ -1033,8 +1057,8 @@ class DefaultXtreamApiClient(
                     listOf(80, 8080, 8000, 8880, 2052, 2082, 2086)
                 }
 
-            // Premium Contract Section 5: Use centralized parallelism config
-            val sem = Semaphore(XtreamTransportConfig.PARALLELISM_PHONE_TABLET)
+            // Premium Contract Section 5: Use device-class parallelism from DI (SSOT)
+            val sem = Semaphore(parallelism.value)
             val jobs =
                 candidates.distinct().map { port ->
                     async { sem.withPermit { if (tryPing(config, port)) port else null } }
@@ -1119,8 +1143,8 @@ class DefaultXtreamApiClient(
     ): XtreamCapabilities =
         coroutineScope {
             val actions = mutableMapOf<String, XtreamActionCapability>()
-            // Premium Contract Section 5: Use centralized parallelism config
-            val sem = Semaphore(XtreamTransportConfig.PARALLELISM_PHONE_TABLET)
+            // Premium Contract Section 5: Use device-class parallelism from DI (SSOT)
+            val sem = Semaphore(parallelism.value)
 
             suspend fun probe(
                 action: String,
diff --git a/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamDiscovery.kt b/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamDiscovery.kt
index 95eef04c..ea034c7a 100644
--- a/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamDiscovery.kt
+++ b/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamDiscovery.kt
@@ -31,10 +31,16 @@ import java.util.concurrent.TimeUnit
  * - Panel-Typ-Hinweise (Xtream-UI, XUI.ONE, etc.)
  *
  * Basiert auf v1 XtreamCapabilities.kt mit verbessertem Probing.
+ *
+ * @param http OkHttpClient with Premium Contract settings
+ * @param json JSON parser
+ * @param parallelism Device-aware parallelism from DI (SSOT for Semaphores)
+ * @param io Coroutine dispatcher for IO operations
  */
 class XtreamDiscovery(
     private val http: OkHttpClient,
     private val json: Json = Json { ignoreUnknownKeys = true },
+    private val parallelism: XtreamParallelism = XtreamParallelism(XtreamTransportConfig.PARALLELISM_PHONE_TABLET),
     private val io: CoroutineDispatcher = Dispatchers.IO,
 ) {
     // =========================================================================
@@ -206,8 +212,8 @@ class XtreamDiscovery(
             val candidates =
                 (if (isHttps) HTTPS_PORTS else HTTP_PORTS).filter { it != defaultPort }.distinct()
 
-            // Premium Contract Section 5: Use centralized parallelism config
-            val semaphore = Semaphore(XtreamTransportConfig.PARALLELISM_PHONE_TABLET)
+            // Premium Contract Section 5: Use device-class parallelism from DI (SSOT)
+            val semaphore = Semaphore(parallelism.value)
             val jobs =
                 candidates.map { port ->
                     async { semaphore.withPermit { if (tryProbe(config, port)) port else null } }
@@ -311,8 +317,8 @@ class XtreamDiscovery(
     ): XtreamCapabilities =
         coroutineScope {
             val actions = mutableMapOf<String, XtreamActionCapability>()
-            // Premium Contract Section 5: Use centralized parallelism config
-            val semaphore = Semaphore(XtreamTransportConfig.PARALLELISM_PHONE_TABLET)
+            // Premium Contract Section 5: Use device-class parallelism from DI (SSOT)
+            val semaphore = Semaphore(parallelism.value)
 
             suspend fun probe(
                 action: String,
diff --git a/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamParallelism.kt b/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamParallelism.kt
new file mode 100644
index 00000000..b9a18575
--- /dev/null
+++ b/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/XtreamParallelism.kt
@@ -0,0 +1,41 @@
+package com.fishit.player.infra.transport.xtream
+
+import javax.inject.Qualifier
+
+/**
+ * Qualifier for the device-aware parallelism value.
+ *
+ * Used to inject the parallelism level determined by [XtreamTransportConfig.getParallelism].
+ *
+ * Premium Contract Section 5:
+ * - Phone/Tablet: parallelism = 10
+ * - FireTV/low-RAM: parallelism = 3
+ *
+ * @see XtreamTransportConfig.getParallelism
+ */
+@Qualifier
+@Retention(AnnotationRetention.BINARY)
+annotation class XtreamParallelismValue
+
+/**
+ * XtreamParallelism – Device-aware parallelism wrapper for Xtream transport.
+ *
+ * This is a simple wrapper class (not value class due to Hilt/KSP limitations)
+ * that provides the parallelism level determined by [XtreamTransportConfig.getParallelism].
+ *
+ * Used by:
+ * - OkHttp Dispatcher (maxRequests, maxRequestsPerHost)
+ * - Coroutine Semaphores in DefaultXtreamApiClient and XtreamDiscovery
+ *
+ * Premium Contract Section 5:
+ * - Phone/Tablet: parallelism = 10
+ * - FireTV/low-RAM: parallelism = 3
+ *
+ * @property value The parallelism level (number of concurrent requests).
+ * @see XtreamTransportConfig.getParallelism
+ */
+data class XtreamParallelism(val value: Int) {
+    init {
+        require(value > 0) { "Parallelism must be positive, was $value" }
+    }
+}
diff --git a/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/di/XtreamTransportModule.kt b/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/di/XtreamTransportModule.kt
index 390676f9..f3cd15da 100644
--- a/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/di/XtreamTransportModule.kt
+++ b/infra/transport-xtream/src/main/java/com/fishit/player/infra/transport/xtream/di/XtreamTransportModule.kt
@@ -6,6 +6,7 @@ import com.fishit.player.infra.transport.xtream.EncryptedXtreamCredentialsStore
 import com.fishit.player.infra.transport.xtream.XtreamApiClient
 import com.fishit.player.infra.transport.xtream.XtreamCredentialsStore
 import com.fishit.player.infra.transport.xtream.XtreamDiscovery
+import com.fishit.player.infra.transport.xtream.XtreamParallelism
 import com.fishit.player.infra.transport.xtream.XtreamTransportConfig
 import dagger.Binds
 import dagger.Module
@@ -42,6 +43,23 @@ annotation class XtreamHttpClient
 @InstallIn(SingletonComponent::class)
 object XtreamTransportModule {
 
+    /**
+     * Provides the device-aware parallelism as SSOT.
+     *
+     * Premium Contract Section 5:
+     * - Phone/Tablet: 10
+     * - FireTV/low-RAM: 3
+     *
+     * This value is used by:
+     * - OkHttp Dispatcher limits
+     * - All coroutine Semaphores in DefaultXtreamApiClient and XtreamDiscovery
+     */
+    @Provides
+    @Singleton
+    fun provideXtreamParallelism(
+        @ApplicationContext context: Context,
+    ): XtreamParallelism = XtreamParallelism(XtreamTransportConfig.getParallelism(context))
+
     /**
      * Provides Xtream-specific OkHttpClient with Premium Contract settings.
      *
@@ -65,9 +83,8 @@ object XtreamTransportModule {
     @XtreamHttpClient
     fun provideXtreamOkHttpClient(
         @ApplicationContext context: Context,
+        parallelism: XtreamParallelism,
     ): OkHttpClient {
-        val parallelism = XtreamTransportConfig.getParallelism(context)
-
         return OkHttpClient.Builder()
             // Premium Contract Section 3: HTTP Timeouts
             .connectTimeout(XtreamTransportConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
@@ -98,8 +115,8 @@ object XtreamTransportModule {
                 // Premium Contract Section 5: Device-class parallelism
                 dispatcher(
                     okhttp3.Dispatcher().apply {
-                        maxRequests = parallelism
-                        maxRequestsPerHost = parallelism
+                        maxRequests = parallelism.value
+                        maxRequestsPerHost = parallelism.value
                     },
                 )
             }
@@ -119,14 +136,16 @@ object XtreamTransportModule {
     fun provideXtreamDiscovery(
         @XtreamHttpClient okHttpClient: OkHttpClient,
         json: Json,
-    ): XtreamDiscovery = XtreamDiscovery(okHttpClient, json)
+        parallelism: XtreamParallelism,
+    ): XtreamDiscovery = XtreamDiscovery(okHttpClient, json, parallelism = parallelism)
 
     @Provides
     @Singleton
     fun provideXtreamApiClient(
         @XtreamHttpClient okHttpClient: OkHttpClient,
         json: Json,
-    ): XtreamApiClient = DefaultXtreamApiClient(okHttpClient, json)
+        parallelism: XtreamParallelism,
+    ): XtreamApiClient = DefaultXtreamApiClient(okHttpClient, json, parallelism = parallelism)
 }
 
 /** Hilt module for Xtream credentials storage. */
diff --git a/infra/transport-xtream/src/test/java/com/fishit/player/infra/transport/xtream/XtreamParallelismTest.kt b/infra/transport-xtream/src/test/java/com/fishit/player/infra/transport/xtream/XtreamParallelismTest.kt
new file mode 100644
index 00000000..749db315
--- /dev/null
+++ b/infra/transport-xtream/src/test/java/com/fishit/player/infra/transport/xtream/XtreamParallelismTest.kt
@@ -0,0 +1,48 @@
+package com.fishit.player.infra.transport.xtream
+
+import org.junit.Assert.assertEquals
+import org.junit.Assert.assertThrows
+import org.junit.Test
+
+/**
+ * Unit tests for XtreamParallelism wrapper class.
+ */
+class XtreamParallelismTest {
+
+    @Test
+    fun `XtreamParallelism stores correct value`() {
+        val parallelism = XtreamParallelism(10)
+        assertEquals(10, parallelism.value)
+    }
+
+    @Test
+    fun `XtreamParallelism accepts FireTV parallelism value`() {
+        val parallelism = XtreamParallelism(3)
+        assertEquals(3, parallelism.value)
+    }
+
+    @Test
+    fun `XtreamParallelism rejects zero value`() {
+        assertThrows(IllegalArgumentException::class.java) {
+            XtreamParallelism(0)
+        }
+    }
+
+    @Test
+    fun `XtreamParallelism rejects negative value`() {
+        assertThrows(IllegalArgumentException::class.java) {
+            XtreamParallelism(-1)
+        }
+    }
+
+    @Test
+    fun `XtreamParallelism equality works correctly`() {
+        val p1 = XtreamParallelism(10)
+        val p2 = XtreamParallelism(10)
+        val p3 = XtreamParallelism(3)
+
+        assertEquals(p1, p2)
+        assertEquals(p1.hashCode(), p2.hashCode())
+        assert(p1 != p3)
+    }
+}
diff --git a/infra/transport-xtream/src/test/java/com/fishit/player/infra/transport/xtream/XtreamTransportConfigTest.kt b/infra/transport-xtream/src/test/java/com/fishit/player/infra/transport/xtream/XtreamTransportConfigTest.kt
new file mode 100644
index 00000000..edbac7e0
--- /dev/null
+++ b/infra/transport-xtream/src/test/java/com/fishit/player/infra/transport/xtream/XtreamTransportConfigTest.kt
@@ -0,0 +1,186 @@
+package com.fishit.player.infra.transport.xtream
+
+import android.app.ActivityManager
+import android.app.UiModeManager
+import android.content.Context
+import android.content.res.Configuration
+import io.mockk.every
+import io.mockk.mockk
+import org.junit.Assert.assertEquals
+import org.junit.Test
+
+/**
+ * Unit tests for XtreamTransportConfig.
+ *
+ * Verifies Premium Contract Section 5 compliance:
+ * - Phone/Tablet: parallelism = 10
+ * - FireTV/low-RAM: parallelism = 3
+ */
+class XtreamTransportConfigTest {
+
+    @Test
+    fun `detectDeviceClass returns PHONE_TABLET for non-TV high-RAM device`() {
+        // Arrange
+        val context = mockk<Context>()
+        val uiModeManager = mockk<UiModeManager>()
+        val activityManager = mockk<ActivityManager>()
+        val memoryInfo = ActivityManager.MemoryInfo().apply {
+            totalMem = 4L * 1024 * 1024 * 1024 // 4GB RAM
+        }
+
+        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
+        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
+        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_NORMAL
+        every { activityManager.getMemoryInfo(any()) } answers {
+            val info = firstArg<ActivityManager.MemoryInfo>()
+            info.totalMem = memoryInfo.totalMem
+        }
+        every { activityManager.isLowRamDevice } returns false
+
+        // Act
+        val result = XtreamTransportConfig.detectDeviceClass(context)
+
+        // Assert
+        assertEquals(XtreamTransportConfig.DeviceClass.PHONE_TABLET, result)
+        assertEquals(10, result.parallelism)
+    }
+
+    @Test
+    fun `detectDeviceClass returns TV_LOW_RAM for TV device`() {
+        // Arrange
+        val context = mockk<Context>()
+        val uiModeManager = mockk<UiModeManager>()
+        val activityManager = mockk<ActivityManager>()
+        val memoryInfo = ActivityManager.MemoryInfo().apply {
+            totalMem = 4L * 1024 * 1024 * 1024 // 4GB RAM
+        }
+
+        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
+        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
+        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_TELEVISION
+        every { activityManager.getMemoryInfo(any()) } answers {
+            val info = firstArg<ActivityManager.MemoryInfo>()
+            info.totalMem = memoryInfo.totalMem
+        }
+        every { activityManager.isLowRamDevice } returns false
+
+        // Act
+        val result = XtreamTransportConfig.detectDeviceClass(context)
+
+        // Assert
+        assertEquals(XtreamTransportConfig.DeviceClass.TV_LOW_RAM, result)
+        assertEquals(3, result.parallelism)
+    }
+
+    @Test
+    fun `detectDeviceClass returns TV_LOW_RAM for low-RAM device`() {
+        // Arrange
+        val context = mockk<Context>()
+        val uiModeManager = mockk<UiModeManager>()
+        val activityManager = mockk<ActivityManager>()
+        val memoryInfo = ActivityManager.MemoryInfo().apply {
+            totalMem = 1L * 1024 * 1024 * 1024 // 1GB RAM (below 2GB threshold)
+        }
+
+        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
+        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
+        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_NORMAL
+        every { activityManager.getMemoryInfo(any()) } answers {
+            val info = firstArg<ActivityManager.MemoryInfo>()
+            info.totalMem = memoryInfo.totalMem
+        }
+        every { activityManager.isLowRamDevice } returns false
+
+        // Act
+        val result = XtreamTransportConfig.detectDeviceClass(context)
+
+        // Assert
+        assertEquals(XtreamTransportConfig.DeviceClass.TV_LOW_RAM, result)
+        assertEquals(3, result.parallelism)
+    }
+
+    @Test
+    fun `detectDeviceClass returns TV_LOW_RAM when isLowRamDevice is true`() {
+        // Arrange
+        val context = mockk<Context>()
+        val uiModeManager = mockk<UiModeManager>()
+        val activityManager = mockk<ActivityManager>()
+        val memoryInfo = ActivityManager.MemoryInfo().apply {
+            totalMem = 4L * 1024 * 1024 * 1024 // 4GB RAM
+        }
+
+        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
+        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
+        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_NORMAL
+        every { activityManager.getMemoryInfo(any()) } answers {
+            val info = firstArg<ActivityManager.MemoryInfo>()
+            info.totalMem = memoryInfo.totalMem
+        }
+        every { activityManager.isLowRamDevice } returns true
+
+        // Act
+        val result = XtreamTransportConfig.detectDeviceClass(context)
+
+        // Assert
+        assertEquals(XtreamTransportConfig.DeviceClass.TV_LOW_RAM, result)
+        assertEquals(3, result.parallelism)
+    }
+
+    @Test
+    fun `getParallelism returns 10 for phone tablet`() {
+        // Arrange
+        val context = mockk<Context>()
+        val uiModeManager = mockk<UiModeManager>()
+        val activityManager = mockk<ActivityManager>()
+
+        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
+        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
+        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_NORMAL
+        every { activityManager.getMemoryInfo(any()) } answers {
+            val info = firstArg<ActivityManager.MemoryInfo>()
+            info.totalMem = 4L * 1024 * 1024 * 1024
+        }
+        every { activityManager.isLowRamDevice } returns false
+
+        // Act
+        val result = XtreamTransportConfig.getParallelism(context)
+
+        // Assert
+        assertEquals(10, result)
+    }
+
+    @Test
+    fun `getParallelism returns 3 for FireTV`() {
+        // Arrange
+        val context = mockk<Context>()
+        val uiModeManager = mockk<UiModeManager>()
+        val activityManager = mockk<ActivityManager>()
+
+        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
+        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
+        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_TELEVISION
+        every { activityManager.getMemoryInfo(any()) } answers {
+            val info = firstArg<ActivityManager.MemoryInfo>()
+            info.totalMem = 2L * 1024 * 1024 * 1024
+        }
+        every { activityManager.isLowRamDevice } returns false
+
+        // Act
+        val result = XtreamTransportConfig.getParallelism(context)
+
+        // Assert
+        assertEquals(3, result)
+    }
+
+    @Test
+    fun `constants match Premium Contract Section 5`() {
+        assertEquals(10, XtreamTransportConfig.PARALLELISM_PHONE_TABLET)
+        assertEquals(3, XtreamTransportConfig.PARALLELISM_FIRETV_LOW_RAM)
+    }
+
+    @Test
+    fun `DeviceClass parallelism values are correct`() {
+        assertEquals(10, XtreamTransportConfig.DeviceClass.PHONE_TABLET.parallelism)
+        assertEquals(3, XtreamTransportConfig.DeviceClass.TV_LOW_RAM.parallelism)
+    }
+}
```
