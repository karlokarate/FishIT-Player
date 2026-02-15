# SSOT Cleanup: TelegramTransportClient → Typed Interfaces

**Branch:** `architecture/v2-bootstrap`  
**Date:** 2025-12-25  
**Status:** ✅ Completed

## Übersicht

Diese Änderungen entfernen alle Nutzungen der deprecated `TelegramTransportClient` Klasse und migrieren zu den typed interfaces (SSOT = Single Source of Truth Pattern):

- `TelegramAuthClient` – Authentifizierungsoperationen
- `TelegramHistoryClient` – Chat/Message-Browsing
- `TelegramFileClient` – Datei-Download-Operationen
- `TelegramThumbFetcher` – Thumbnail-Fetching

---

## Zusammenfassung der Änderungen

| Datei | Änderung |
|-------|----------|
| `TelegramPlaybackSourceFactoryImpl.kt` | ❌ `TelegramTransportClient` entfernt (war unbenutzt) |
| `ImagingModule.kt` | ✅ Migration auf `TelegramThumbFetcher` (typed) |
| `TelegramThumbFetcherImpl.kt` | ✅ Komplett neu geschrieben für typed interface |
| `TelegramClientFactory.kt` | ✅ Neue `createUnifiedClient()` Methode |
| `AppStartupImpl.kt` | ✅ Migration auf `TelegramClient` via Factory |
| `TelegramFileDataSource.kt` | ✅ `TelegramTransportClient` entfernt, nur `TelegramFileClient` |
| `TelegramPlaybackModule.kt` | ✅ `TelegramTransportClient` aus DI entfernt |
| `PlayerDataSourceModule.kt` | ✅ `TelegramTransportClient` aus DI entfernt |

---

## Detailed Diffs

### 1. TelegramPlaybackSourceFactoryImpl.kt

**Pfad:** `playback/telegram/src/main/java/.../TelegramPlaybackSourceFactoryImpl.kt`

Die Klasse war ein reiner URI-Builder und nutzte `TelegramTransportClient` nie wirklich.

```diff
 package com.fishit.player.playback.telegram
 
 import com.fishit.player.core.playermodel.PlaybackContext
 import com.fishit.player.core.playermodel.SourceType
 import com.fishit.player.infra.logging.UnifiedLog
-import com.fishit.player.infra.transport.telegram.TelegramTransportClient
 import com.fishit.player.playback.domain.DataSourceType
 import com.fishit.player.playback.domain.PlaybackSource
 import com.fishit.player.playback.domain.PlaybackSourceException
 import com.fishit.player.playback.domain.PlaybackSourceFactory
 import javax.inject.Inject
 import javax.inject.Singleton
 
 /**
  * Factory for creating Telegram playback sources.
  *
- * Uses [TelegramTransportClient] to resolve file information and build
- * playback URIs for Telegram media files.
+ * Converts a [PlaybackContext] with [SourceType.TELEGRAM] into a [PlaybackSource]
+ * that uses the Telegram-specific DataSource for zero-copy streaming.
  *
  * **Architecture:**
- * - Belongs in playback layer (NOT in transport)
- * - Uses transport layer for file resolution only
+ * - Pure URI builder - no transport dependencies required
  * - Returns [PlaybackSource] with [DataSourceType.TELEGRAM_FILE]
  * - Actual file resolution/download handled by [TelegramFileDataSource]
  */
 @Singleton
-class TelegramPlaybackSourceFactoryImpl @Inject constructor(
-    private val transportClient: TelegramTransportClient,
-) : PlaybackSourceFactory {
+class TelegramPlaybackSourceFactoryImpl @Inject constructor() : PlaybackSourceFactory {
```

---

### 2. ImagingModule.kt

**Pfad:** `app-v2/src/main/java/.../di/ImagingModule.kt`

Migration von `TelegramTransportClient` auf das typed `TelegramThumbFetcher` interface.

```diff
 package com.fishit.player.v2.di
 
 import android.content.Context
 import coil3.ImageLoader
 import com.fishit.player.core.imaging.GlobalImageLoader
 import com.fishit.player.core.imaging.fetcher.TelegramThumbFetcher
-import com.fishit.player.infra.transport.telegram.TelegramTransportClient
+import com.fishit.player.infra.transport.telegram.TelegramThumbFetcher as TransportThumbFetcher
 import dagger.Module
 import dagger.Provides
 import dagger.hilt.InstallIn
 import dagger.hilt.android.qualifiers.ApplicationContext
 import dagger.hilt.components.SingletonComponent
 import javax.inject.Provider
 import javax.inject.Singleton
 import okhttp3.OkHttpClient
 
 @Module
 @InstallIn(SingletonComponent::class)
 object ImagingModule {
     // ...
 
     /**
      * Provides the TelegramThumbFetcher.Factory implementation.
      *
-     * Uses lazy Provider to handle cases where TelegramTransportClient
-     * is not available (e.g., no Telegram connection).
+     * Uses the transport-layer TelegramThumbFetcher (typed interface)
+     * instead of the deprecated TelegramTransportClient.
      */
     @Provides
     @Singleton
     fun provideTelegramThumbFetcherFactory(
-        transportClient: Provider<TelegramTransportClient>
+        transportThumbFetcher: Provider<TransportThumbFetcher>
     ): TelegramThumbFetcher.Factory? =
-        runCatching { TelegramThumbFetcherImpl.Factory(transportClient.get()) }
+        runCatching { TelegramThumbFetcherImpl.Factory(transportThumbFetcher.get()) }
             .getOrNull()
 }
```

---

### 3. TelegramThumbFetcherImpl.kt

**Pfad:** `app-v2/src/main/java/.../di/TelegramThumbFetcherImpl.kt`

Komplett neu geschrieben - von ~130 Zeilen Polling-Implementation auf ~100 Zeilen Delegation.

```diff
 package com.fishit.player.v2.di
 
 import coil3.decode.DataSource
 import coil3.decode.ImageSource
 import coil3.fetch.FetchResult
 import coil3.fetch.SourceFetchResult
 import coil3.request.Options
 import com.fishit.player.core.imaging.fetcher.TelegramThumbFetcher
 import com.fishit.player.core.model.ImageRef
-import com.fishit.player.infra.transport.telegram.TelegramTransportClient
+import com.fishit.player.infra.transport.telegram.TelegramThumbFetcher as TransportThumbFetcher
+import com.fishit.player.infra.transport.telegram.TgThumbnailRef
 import java.io.File
 import java.io.IOException
-import kotlinx.coroutines.delay
 import okio.buffer
 import okio.source
 
 /**
- * Coil TelegramThumbFetcher implementation using TelegramTransportClient.
- *
- * **Polling Strategy:**
- * - Calls transportClient.getThumbnailPath(remoteId)
- * - If path is null (download in progress), polls with backoff
- * - Max ~16 seconds total wait time before failing
+ * Coil TelegramThumbFetcher implementation using typed transport interface.
  *
  * **Architecture:**
- * - core:ui-imaging defines the interface
- * - infra:transport-telegram provides the transport client
- * - This implementation bridges the two, lives in app-v2 for DI wiring
+ * - core:ui-imaging defines the [TelegramThumbFetcher] interface (Coil-facing)
+ * - infra:transport-telegram provides [TransportThumbFetcher] (TDLib-facing)
+ * - This implementation bridges the two
  */
 class TelegramThumbFetcherImpl(
-    private val transportClient: TelegramTransportClient,
+    private val transportFetcher: TransportThumbFetcher,
     private val ref: ImageRef.TelegramThumb,
     private val options: Options,
 ) : TelegramThumbFetcher {
 
-    companion object {
-        private const val MAX_POLL_ATTEMPTS = 8
-        private const val INITIAL_POLL_DELAY_MS = 100L
-        private const val MAX_POLL_DELAY_MS = 2000L
-    }
-
     override suspend fun fetch(
         ref: ImageRef.TelegramThumb,
         options: Options,
     ): FetchResult {
-        // Poll for thumbnail path with exponential backoff
-        var pollDelay = INITIAL_POLL_DELAY_MS
-        var attempts = 0
-        
-        while (attempts < MAX_POLL_ATTEMPTS) {
-            val localPath = transportClient.getThumbnailPath(ref.remoteId)
-            
-            if (localPath != null) {
-                return createResult(localPath)
-            }
-            
-            // Path not ready yet, poll with backoff
-            delay(pollDelay)
-            pollDelay = (pollDelay * 2).coerceAtMost(MAX_POLL_DELAY_MS)
-            attempts++
-        }
-        
-        throw IOException("Thumbnail download timed out for remoteId=${ref.remoteId}")
+        // Convert ImageRef.TelegramThumb to TgThumbnailRef for transport layer
+        val thumbRef = TgThumbnailRef(
+            remoteId = ref.remoteId,
+            width = ref.width,
+            height = ref.height,
+        )
+
+        // Use transport layer to fetch thumbnail
+        val localPath = transportFetcher.fetchThumbnail(thumbRef)
+            ?: throw IOException("Failed to fetch Telegram thumbnail for remoteId=${ref.remoteId}")
+
+        return createResult(localPath)
     }
 
     // ... rest unchanged
 
     class Factory(
-        private val transportClient: TelegramTransportClient,
+        private val transportFetcher: TransportThumbFetcher,
     ) : TelegramThumbFetcher.Factory {
         override fun create(
             ref: ImageRef.TelegramThumb,
             options: Options,
-        ): TelegramThumbFetcher = TelegramThumbFetcherImpl(transportClient, ref, options)
+        ): TelegramThumbFetcher = TelegramThumbFetcherImpl(transportFetcher, ref, options)
     }
 }
```

---

### 4. TelegramClientFactory.kt

**Pfad:** `infra/transport-telegram/src/main/java/.../TelegramClientFactory.kt`

Neue `createUnifiedClient()` Methode, alte `fromExistingSession()` als deprecated markiert.

```diff
 package com.fishit.player.infra.transport.telegram
 
 import com.fishit.player.infra.logging.UnifiedLog
+import com.fishit.player.infra.transport.telegram.internal.DefaultTelegramClient
 import dev.g000sha256.tdl.TdlClient
 // ...
 
 /**
- * Factory for creating TelegramTransportClient from existing sessions.
+ * Factory for creating Telegram clients from existing sessions.
  *
  * **Purpose:**
- * Creates TelegramTransportClient instances for CLI and test usage
+ * Creates TelegramClient instances for CLI and test usage
  * without requiring Android Context or interactive authentication.
  *
  * **Codespace/CI Usage:**
  * ```kotlin
  * val config = TelegramSessionConfig.fromEnvironment()
  *     ?: error("Missing TG_API_ID or TG_API_HASH")
- * val client = TelegramClientFactory.fromExistingSession(config)
+ * val client = TelegramClientFactory.createUnifiedClient(config)
  * ```
+ *
+ * **v2 Migration:**
+ * - Use [createUnifiedClient] for new code (returns [TelegramClient])
+ * - [fromExistingSession] is deprecated (returns [TelegramTransportClient])
  */
 object TelegramClientFactory {
 
     private const val TAG = "TelegramClientFactory"
     private const val AUTH_TIMEOUT_MS = 30_000L
 
+    /**
+     * Create unified TelegramClient from an existing TDLib session.
+     *
+     * This is the preferred method for v2 code. The returned client implements
+     * all typed interfaces: [TelegramAuthClient], [TelegramHistoryClient],
+     * [TelegramFileClient], [TelegramThumbFetcher].
+     */
+    suspend fun createUnifiedClient(
+        config: TelegramSessionConfig,
+        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
+    ): TelegramClient {
+        UnifiedLog.i(TAG, "Creating unified client from session: db=${config.databaseDir}")
+
+        validateSessionDirectories(config)
+        val tdlClient = createTdlClient()
+        verifySession(tdlClient)
+
+        // Create unified client (DefaultTelegramClient)
+        val client = DefaultTelegramClient(
+            tdlClient = tdlClient,
+            sessionConfig = config,
+            authScope = scope,
+            fileScope = scope,
+        )
+
+        withTimeout(AUTH_TIMEOUT_MS) { client.ensureAuthorized() }
+        return client
+    }
+
     /**
      * Create a TelegramTransportClient from an existing TDLib session.
      *
+     * @deprecated Use [createUnifiedClient] instead.
      */
+    @Deprecated(
+        message = "Use createUnifiedClient() instead. TelegramTransportClient is deprecated.",
+        replaceWith = ReplaceWith("createUnifiedClient(config, scope)")
+    )
     suspend fun fromExistingSession(
         config: TelegramSessionConfig,
         scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
     ): TelegramTransportClient {
         // ... existing implementation unchanged
     }
 }
```

---

### 5. AppStartupImpl.kt

**Pfad:** `core/app-startup/src/main/java/.../AppStartupImpl.kt`

Migration von `TelegramTransportClient` auf `TelegramClient` via neue Factory-Methode.

```diff
 package com.fishit.player.core.appstartup
 
 import com.fishit.player.infra.logging.UnifiedLog
+import com.fishit.player.infra.transport.telegram.TelegramClient
 import com.fishit.player.infra.transport.telegram.TelegramClientFactory
-import com.fishit.player.infra.transport.telegram.TelegramTransportClient
 import com.fishit.player.infra.transport.xtream.DefaultXtreamApiClient
 // ...
 
 /**
  * Default AppStartup implementation.
  *
  * **Pipeline Creation Flow:**
- * 1. Telegram: TelegramClientFactory → TelegramTransportClient → TelegramPipelineAdapter
+ * 1. Telegram: TelegramClientFactory → TelegramClient → TelegramPipelineAdapter
  * 2. Xtream: DefaultXtreamApiClient → XtreamPipelineAdapter
  */
 class AppStartupImpl(
     private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
 ) : AppStartup {
 
-    private var telegramClient: TelegramTransportClient? = null
+    private var telegramClient: TelegramClient? = null
     private var xtreamClient: XtreamApiClient? = null
 
     private suspend fun initTelegramPipeline(
         config: TelegramPipelineConfig,
     ): TelegramPipelineAdapter? {
         return try {
-            // Create transport client from existing session
-            val transportClient = TelegramClientFactory.fromExistingSession(
+            // Create unified client from existing session (v2 pattern)
+            val client = TelegramClientFactory.createUnifiedClient(
                 config = config.sessionConfig,
                 scope = scope,
             )
-            telegramClient = transportClient
+            telegramClient = client
 
+            // TelegramClient implements TelegramAuthClient + TelegramHistoryClient
             val metadataExtractor = TelegramStructuredMetadataExtractor()
             val bundler = TelegramMessageBundler()
             val bundleMapper = TelegramBundleToMediaItemMapper(metadataExtractor)
-            val adapter = TelegramPipelineAdapter(transportClient, bundler, bundleMapper)
+            val adapter = TelegramPipelineAdapter(
+                authClient = client,
+                historyClient = client,
+                bundler = bundler,
+                bundleMapper = bundleMapper,
+            )
 
             adapter
         } catch (e: Exception) {
             // ...
         }
     }
 }
```

---

### 6. TelegramFileDataSource.kt

**Pfad:** `playback/telegram/src/main/java/.../TelegramFileDataSource.kt`

Entfernung von `TelegramTransportClient`, nur noch `TelegramFileClient` wird verwendet.

```diff
 package com.fishit.player.playback.telegram
 
 import android.net.Uri
 import androidx.media3.datasource.DataSource
 import androidx.media3.datasource.DataSpec
 import androidx.media3.datasource.FileDataSource
 import androidx.media3.datasource.TransferListener
 import com.fishit.player.infra.logging.UnifiedLog
 import com.fishit.player.infra.transport.telegram.TelegramFileClient
-import com.fishit.player.infra.transport.telegram.TelegramFileException
-import com.fishit.player.infra.transport.telegram.TelegramTransportClient
+import com.fishit.player.infra.transport.telegram.api.TelegramFileException
 // ...
 
 /**
  * Media3 DataSource for Telegram files using TDLib + Zero-Copy Streaming.
  *
  * **v2 Architecture:**
  * - Belongs in `:playback:telegram` (NOT in player:internal)
- * - Uses `TelegramTransportClient` and `TelegramFileClient` from transport layer
+ * - Uses `TelegramFileClient` from transport layer for all file operations
  * - Delegates to FileDataSource for actual I/O (zero-copy)
  *
- * @param transportClient Transport layer client for TDLib file resolution
- * @param fileClient Transport layer client for TDLib file downloads
+ * @param fileClient Transport layer client for TDLib file operations (download, resolve)
  * @param readyEnsurer Playback layer component for streaming readiness
  */
 class TelegramFileDataSource(
-    private val transportClient: TelegramTransportClient,
     private val fileClient: TelegramFileClient,
     private val readyEnsurer: TelegramFileReadyEnsurer,
 ) : DataSource {
 
     /**
      * Resolve fileId from URI parameters.
      */
     private suspend fun resolveFileId(fileId: Int?, remoteId: String?): Int {
         return when {
             fileId != null && fileId > 0 -> fileId
             remoteId != null && remoteId.isNotEmpty() -> {
-                val resolvedFile = transportClient.resolveFileByRemoteId(remoteId)
+                val resolvedFile = fileClient.resolveRemoteId(remoteId)
+                    ?: throw TelegramFileException("Could not resolve remoteId: $remoteId")
                 resolvedFile.id
             }
             else -> throw IOException("...")
         }
     }
 }
 
 class TelegramFileDataSourceFactory(
-    private val transportClient: TelegramTransportClient,
     private val fileClient: TelegramFileClient,
     private val readyEnsurer: TelegramFileReadyEnsurer,
 ) : DataSource.Factory {
     override fun createDataSource(): DataSource {
-        return TelegramFileDataSource(transportClient, fileClient, readyEnsurer)
+        return TelegramFileDataSource(fileClient, readyEnsurer)
     }
 }
```

---

### 7. TelegramPlaybackModule.kt

**Pfad:** `playback/telegram/src/main/java/.../di/TelegramPlaybackModule.kt`

Entfernung von `TelegramTransportClient` aus DI.

```diff
 package com.fishit.player.playback.telegram.di
 
 import com.fishit.player.infra.transport.telegram.TelegramFileClient
-import com.fishit.player.infra.transport.telegram.TelegramTransportClient
 import com.fishit.player.playback.domain.PlaybackSourceFactory
 // ...
 
 /**
  * **Dependencies:**
- * - [TelegramTransportClient] from `:infra:transport-telegram`
  * - [TelegramFileClient] from `:infra:transport-telegram`
  */
 @Module
 @InstallIn(SingletonComponent::class)
 abstract class TelegramPlaybackModule {
     // ...
 
     companion object {
         @Provides
         @Singleton
         fun provideTelegramFileDataSourceFactory(
-            transportClient: TelegramTransportClient,
             fileClient: TelegramFileClient,
-            readyEnsurer: TelegramFileReadyEnsurer
+            readyEnsurer: TelegramFileReadyEnsurer,
         ): TelegramFileDataSourceFactory {
-            return TelegramFileDataSourceFactory(transportClient, fileClient, readyEnsurer)
+            return TelegramFileDataSourceFactory(fileClient, readyEnsurer)
         }
     }
 }
```

---

### 8. PlayerDataSourceModule.kt

**Pfad:** `player/internal/src/main/java/.../di/PlayerDataSourceModule.kt`

Entfernung von `TelegramTransportClient` aus DI.

```diff
 package com.fishit.player.internal.di
 
 import android.content.Context
 import androidx.media3.datasource.DataSource
 import androidx.media3.datasource.DefaultDataSource
 import com.fishit.player.infra.transport.telegram.TelegramFileClient
-import com.fishit.player.infra.transport.telegram.TelegramTransportClient
 import com.fishit.player.playback.domain.DataSourceType
 // ...
 
 @Module
 @InstallIn(SingletonComponent::class)
 object PlayerDataSourceModule {
 
     /**
      * Provides the Telegram file DataSource factory.
      *
-     * Uses [TelegramTransportClient] for zero-copy file streaming via TDLib.
+     * Uses [TelegramFileClient] for zero-copy file streaming via TDLib.
      */
     @Provides
     @Singleton
     fun provideTelegramFileDataSourceFactory(
-        transportClient: TelegramTransportClient,
         fileClient: TelegramFileClient,
         readyEnsurer: TelegramFileReadyEnsurer,
     ): TelegramFileDataSourceFactory {
-        return TelegramFileDataSourceFactory(transportClient, fileClient, readyEnsurer)
+        return TelegramFileDataSourceFactory(fileClient, readyEnsurer)
     }
 }
```

---

## Architektur-Diagramm (nach Migration)

```
┌──────────────────────────────────────────────────────────────────┐
│  App Layer (app-v2, feature/*)                                   │
│  - ImagingModule provides TelegramThumbFetcher.Factory           │
│  - Uses TelegramClient (unified) via DI                          │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│  Playback Layer (playback/telegram)                              │
│  - TelegramFileDataSource uses TelegramFileClient only           │
│  - TelegramPlaybackSourceFactoryImpl is pure URI builder         │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│  Transport Layer (infra/transport-telegram)                      │
│  - TelegramClient (unified interface)                            │
│    ├── TelegramAuthClient                                        │
│    ├── TelegramHistoryClient                                     │
│    ├── TelegramFileClient                                        │
│    └── TelegramThumbFetcher                                      │
│  - DefaultTelegramClient (implementation)                        │
│  - TelegramClientFactory.createUnifiedClient() (v2)              │
│  - TelegramTransportClient (deprecated, still available)         │
└──────────────────────────────────────────────────────────────────┘
```

---

## Verbleibendes Cleanup (Future)

1. **`TelegramTransportClient`** kann vollständig entfernt werden, sobald:
   - Alle CLI/Test-Nutzungen auf `createUnifiedClient()` migriert sind
   - `DefaultTelegramTransportClient` nicht mehr referenziert wird

2. **`TelegramPipelineAdapter` Secondary Constructor** kann entfernt werden, sobald:
   - Keine Legacy-Nutzung mehr existiert

---

## Compliance

- ✅ AGENTS.md Section 4: Layer Boundaries respected
- ✅ AGENTS.md Section 4.7: No Bridge Duplicates introduced
- ✅ Glossary naming conventions followed
- ✅ No `com.chris.m3usuite` references added
