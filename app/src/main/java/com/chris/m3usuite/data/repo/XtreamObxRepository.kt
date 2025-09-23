package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.core.http.HttpClientFactory
import com.chris.m3usuite.core.xtream.*
import com.chris.m3usuite.data.obx.*
import com.chris.m3usuite.prefs.SettingsStore
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.Property
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import io.objectbox.android.AndroidScheduler
import kotlin.math.min

private val EXT_REGEX = Regex("^[a-z0-9]{2,5}$")

private fun sanitizeContainerExt(raw: String?): String? {
    val value = raw?.lowercase()?.trim().orEmpty()
    return value.takeIf { it.isNotBlank() && EXT_REGEX.matches(it) }
}

class XtreamObxRepository(
    private val context: Context,
    private val settings: SettingsStore
) {
    // In-flight de-duplication for detail calls (avoid parallel duplicate fetches)
    private val inflightVod = mutableMapOf<Int, kotlinx.coroutines.CompletableDeferred<Result<Boolean>>>()
    private val inflightSeries = mutableMapOf<Int, kotlinx.coroutines.CompletableDeferred<Result<Int>>>()
    // ---- Public on-demand batches (prioritize VOD/Series; bounded concurrency) ----
    suspend fun importVodDetailsForIds(ids: List<Int>, max: Int = 50): Int = withContext(Dispatchers.IO) {
        // Global gate: respect M3U/Xtream workers & API switch
        if (!settings.m3uWorkersEnabled.first()) return@withContext 0
        val target = ids.distinct().take(max)
        if (target.isEmpty()) return@withContext 0
        val client = newClient()
        val store = ObxStore.get(context)
        val vodBox = store.boxFor<ObxVod>()
        val sem = Semaphore(4)
        var updated = 0
        coroutineScope {
            target.mapNotNull { id ->
                val row = vodBox.query(ObxVod_.vodId.equal(id.toLong())).build().findFirst()
                if (row == null) id else if (row.plot.isNullOrBlank() || row.imagesJson.isNullOrBlank() || row.containerExt.isNullOrBlank() || row.rating == null || row.genre.isNullOrBlank() || row.durationSecs == null) id else null
            }.map { vid ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        val res = importVodDetailOnce(vid, client)
                        if (res.isSuccess && res.getOrDefault(false)) synchronized(this@XtreamObxRepository) { updated++ }
                    }
                }
            }.awaitAll()
        }
        updated
    }

    suspend fun importSeriesDetailsForIds(ids: List<Int>, max: Int = 50): Int = withContext(Dispatchers.IO) {
        // Global gate: respect M3U/Xtream workers & API switch
        if (!settings.m3uWorkersEnabled.first()) return@withContext 0
        val target = ids.distinct().take(max)
        if (target.isEmpty()) return@withContext 0
        val client = newClient()
        val store = ObxStore.get(context)
        val seriesBox = store.boxFor<ObxSeries>()
        val epBox = store.boxFor<ObxEpisode>()
        val sem = Semaphore(4)
        var updated = 0
        fun needsSeriesDetailLocal(s: ObxSeries?): Boolean {
            if (s == null) return true
            if (s.plot.isNullOrBlank() || s.imagesJson.isNullOrBlank()) return true
            val q = epBox.query(ObxEpisode_.seriesId.equal(s.seriesId.toLong())).build()
            return try { q.find(0, 20).any { it.playExt.isNullOrBlank() || it.imageUrl.isNullOrBlank() } } finally { q.close() }
        }
        coroutineScope {
            target.mapNotNull { id ->
                val row = seriesBox.query(ObxSeries_.seriesId.equal(id.toLong())).build().findFirst()
                if (needsSeriesDetailLocal(row)) id else null
            }.map { sid ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        val res = importSeriesDetailOnce(sid, client)
                        if (res.isSuccess) synchronized(this@XtreamObxRepository) { updated++ }
                    }
                }
            }.awaitAll()
        }
        updated
    }
    suspend fun hasAnyContent(): Boolean = withContext(Dispatchers.IO) {
        val store = ObxStore.get(context)
        val liveCount = store.boxFor<ObxLive>().count()
        if (liveCount > 0) return@withContext true
        val vodCount = store.boxFor<ObxVod>().count()
        if (vodCount > 0) return@withContext true
        val seriesCount = store.boxFor<ObxSeries>().count()
        seriesCount > 0
    }

    suspend fun countTotals(): Triple<Long, Long, Long> = withContext(Dispatchers.IO) {
        val store = ObxStore.get(context)
        val live = store.boxFor<ObxLive>().count()
        val vod = store.boxFor<ObxVod>().count()
        val series = store.boxFor<ObxSeries>().count()
        Triple(live, vod, series)
    }

    private fun bucketProvider(kind: String, categoryName: String?, title: String?, url: String?): String =
        com.chris.m3usuite.core.util.CategoryNormalizer.normalizeBucket(kind, categoryName, title, url)

    // ---------------------
    // ObjectBox -> Compose change signals
    // ---------------------
    fun liveChanges(): Flow<Unit> = callbackFlow {
        val box = ObxStore.get(context).boxFor<ObxLive>()
        val q = box.query().build()
        // initial signal (current state)
        trySend(Unit).isSuccess
        val sub = q.subscribe().on(AndroidScheduler.mainThread()).observer { trySend(Unit).isSuccess }
        awaitClose { sub.cancel() }
    }

    fun vodChanges(): Flow<Unit> = callbackFlow {
        val box = ObxStore.get(context).boxFor<ObxVod>()
        val q = box.query().build()
        trySend(Unit).isSuccess
        val sub = q.subscribe().on(AndroidScheduler.mainThread()).observer { trySend(Unit).isSuccess }
        awaitClose { sub.cancel() }
    }

    fun seriesChanges(): Flow<Unit> = callbackFlow {
        val box = ObxStore.get(context).boxFor<ObxSeries>()
        val q = box.query().build()
        trySend(Unit).isSuccess
        val sub = q.subscribe().on(AndroidScheduler.mainThread()).observer { trySend(Unit).isSuccess }
        awaitClose { sub.cancel() }
    }

    /**
     * Quick seeding of initial content for immediate UI visibility.
     * Fetches a bounded number of items per kind (no details) and upserts into OBX.
     * Does NOT delete orphans and avoids heavy detail fetches.
     */
    suspend fun seedListsQuick(
        limitPerKind: Int = 200,
        forceRefreshDiscovery: Boolean = false,
        perCategoryLimit: Int? = null
    ): Result<Triple<Int, Int, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = newClient(forceRefreshDiscovery)
            val boxStore = ObxStore.get(context)
            val catBox = boxStore.boxFor<ObxCategory>()
            val liveBox = boxStore.boxFor<ObxLive>()
            val vodBox = boxStore.boxFor<ObxVod>()
            val seriesBox = boxStore.boxFor<ObxSeries>()
            val epBox = boxStore.boxFor<ObxEpisode>()

            // Kategorien: nur upserten (keine Löschungen, um Flackern zu vermeiden)
            run {
                val liveCats = client.getLiveCategories(); val vodCats = client.getVodCategories(); val serCats = client.getSeriesCategories()
                upsertCategories(catBox, "live", liveCats.associate { it.category_id.orEmpty() to it.category_name.orEmpty() }, deleteOrphans = false)
                upsertCategories(catBox, "vod",  vodCats.associate  { it.category_id.orEmpty() to it.category_name.orEmpty() }, deleteOrphans = false)
                upsertCategories(catBox, "series", serCats.associate { it.category_id.orEmpty() to it.category_name.orEmpty() }, deleteOrphans = false)
            }

            var liveCount = 0; var vodCount = 0; var seriesCount = 0

            // Run the three quick list seeds in parallel to fill UI rows ASAP
            coroutineScope {
                val liveJob = async(Dispatchers.IO) {
                    val toPut = mutableListOf<ObxLive>()
                    val liveCatsRows = catBox.query(ObxCategory_.kind.equal("live")).build().find()
                    val liveCats = liveCatsRows.associateBy({ it.categoryId }, { it.categoryName })
                    if (perCategoryLimit != null && perCategoryLimit > 0) {
                        val sem = Semaphore(4)
                        coroutineScope {
                            liveCatsRows.mapNotNull { it.categoryId }.map { catId ->
                                async(Dispatchers.IO) {
                                    sem.withPermit {
                                        val list = client.getLiveStreams(categoryId = catId, offset = 0, limit = Int.MAX_VALUE)
                                        val slice = list.take(perCategoryLimit)
                                        slice.forEach { r ->
                                            val sid = r.stream_id ?: return@forEach
                                            val existing = liveBox.query(ObxLive_.streamId.equal(sid.toLong())).build().findFirst()
                                            val catName = liveCats[r.category_id.orEmpty()]
                                            val provider = bucketProvider("live", catName, r.name, null)
                                            val gKey = deriveGenreKey(r.name, catName, null)
                                            val e = ObxLive(
                                                streamId = sid,
                                                nameLower = r.name.orEmpty().lowercase(),
                                                sortTitleLower = sortKey(r.name),
                                                name = r.name.orEmpty(),
                                                logo = r.stream_icon,
                                                epgChannelId = r.epg_channel_id,
                                                tvArchive = r.tv_archive,
                                                categoryId = r.category_id,
                                                providerKey = provider,
                                                genreKey = gKey
                                            )
                                            if (existing != null) e.id = existing.id
                                            synchronized(toPut) { toPut += e }
                                        }
                                    }
                                }
                            }.awaitAll()
                        }
                    } else {
                        val list = client.getLiveStreams(categoryId = null, offset = 0, limit = limitPerKind)
                        list.forEach { r ->
                            val sid = r.stream_id ?: return@forEach
                            val existing = liveBox.query(ObxLive_.streamId.equal(sid.toLong())).build().findFirst()
                            val catName = liveCats[r.category_id.orEmpty()]
                            val provider = bucketProvider("live", catName, r.name, null)
                            val gKey = deriveGenreKey(r.name, catName, null)
                            val e = ObxLive(
                                streamId = sid,
                                nameLower = r.name.orEmpty().lowercase(),
                                sortTitleLower = sortKey(r.name),
                                name = r.name.orEmpty(),
                                logo = r.stream_icon,
                                epgChannelId = r.epg_channel_id,
                                tvArchive = r.tv_archive,
                                categoryId = r.category_id,
                                providerKey = provider,
                                genreKey = gKey
                            )
                            if (existing != null) e.id = existing.id
                            toPut += e
                        }
                    }
                    if (toPut.isNotEmpty()) liveBox.putChunked(toPut, 2000)
                    toPut.size
                }

                val vodJob = async(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    val vodCatRows = catBox.query(ObxCategory_.kind.equal("vod")).build().find()
                    val vodCats = vodCatRows.associateBy({ it.categoryId }, { it.categoryName })
                    val toPut = mutableListOf<ObxVod>()
                    if (perCategoryLimit != null && perCategoryLimit > 0) {
                        val sem = Semaphore(4)
                        coroutineScope {
                            vodCatRows.mapNotNull { it.categoryId }.map { catId ->
                                async(Dispatchers.IO) {
                                    sem.withPermit {
                                        val list = client.getVodStreams(categoryId = catId, offset = 0, limit = Int.MAX_VALUE)
                                        val slice = list.take(perCategoryLimit)
                                        slice.forEach { r ->
                                            val vid = r.vod_id ?: return@forEach
                                            val existing = vodBox.query(ObxVod_.vodId.equal(vid.toLong())).build().findFirst()
                                            val nameLower = r.name.orEmpty().lowercase()
                                            val catName = vodCats[r.category_id.orEmpty()]
                                            val provider = bucketProvider("vod", catName, r.name, null)
                                            val gKey = deriveGenreKey(r.name, catName, null)
                                            val e = if (existing == null) ObxVod(
                                                vodId = vid,
                                                nameLower = nameLower,
                                                sortTitleLower = sortKey(r.name),
                                                name = r.name.orEmpty(),
                                                poster = r.stream_icon,
                                                categoryId = r.category_id,
                                                providerKey = provider,
                                                genreKey = gKey,
                                                importedAt = now,
                                                updatedAt = now
                                            ) else existing.copy(
                                                nameLower = nameLower,
                                                sortTitleLower = sortKey(r.name),
                                                categoryId = r.category_id,
                                                providerKey = provider,
                                                genreKey = gKey,
                                                importedAt = existing.importedAt,
                                                updatedAt = now
                                            )
                                            synchronized(toPut) { toPut += e }
                                        }
                                    }
                                }
                            }.awaitAll()
                        }
                    } else {
                        val list = client.getVodStreams(categoryId = null, offset = 0, limit = limitPerKind)
                        list.forEach { r ->
                            val vid = r.vod_id ?: return@forEach
                            val existing = vodBox.query(ObxVod_.vodId.equal(vid.toLong())).build().findFirst()
                            val nameLower = r.name.orEmpty().lowercase()
                            val catName = vodCats[r.category_id.orEmpty()]
                            val provider = bucketProvider("vod", catName, r.name, null)
                            val gKey = deriveGenreKey(r.name, catName, null)
                            val e = if (existing == null) ObxVod(
                                vodId = vid,
                                nameLower = nameLower,
                                sortTitleLower = sortKey(r.name),
                                name = r.name.orEmpty(),
                                poster = r.stream_icon,
                                categoryId = r.category_id,
                                providerKey = provider,
                                genreKey = gKey,
                                importedAt = now,
                                updatedAt = now
                            ) else existing.copy(
                                nameLower = nameLower,
                                sortTitleLower = sortKey(r.name),
                                categoryId = r.category_id,
                                providerKey = provider,
                                genreKey = gKey,
                                importedAt = existing.importedAt,
                                updatedAt = now
                            )
                            toPut += e
                        }
                    }
                    if (toPut.isNotEmpty()) vodBox.putChunked(toPut, 2000)
                    toPut.size
                }

                val seriesJob = async(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    val serCatRows = catBox.query(ObxCategory_.kind.equal("series")).build().find()
                    val serCats = serCatRows.associateBy({ it.categoryId }, { it.categoryName })
                    val toPut = mutableListOf<ObxSeries>()
                    if (perCategoryLimit != null && perCategoryLimit > 0) {
                        val sem = Semaphore(4)
                        coroutineScope {
                            serCatRows.mapNotNull { it.categoryId }.map { catId ->
                                async(Dispatchers.IO) {
                                    sem.withPermit {
                                        val list = client.getSeries(categoryId = catId, offset = 0, limit = Int.MAX_VALUE)
                                        val slice = list.take(perCategoryLimit)
                                        slice.forEach { r ->
                                            val sid = r.series_id ?: return@forEach
                                            val existing = seriesBox.query(ObxSeries_.seriesId.equal(sid.toLong())).build().findFirst()
                                            val nameLower = r.name.orEmpty().lowercase()
                                            val catName = serCats[r.category_id.orEmpty()]
                                            val provider = bucketProvider("series", catName, r.name, null)
                                            val gKey = deriveGenreKey(r.name, catName, null)
                                            val seedImagesJson = runCatching {
                                                val c = r.cover
                                                if (!c.isNullOrBlank()) kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(String.serializer()), listOf(c)) else null
                                            }.getOrNull()
                                            val e = if (existing == null) ObxSeries(
                                                seriesId = sid,
                                                nameLower = nameLower,
                                                sortTitleLower = sortKey(r.name),
                                                name = r.name.orEmpty(),
                                                imagesJson = seedImagesJson,
                                                categoryId = r.category_id,
                                                providerKey = provider,
                                                genreKey = gKey,
                                                importedAt = now,
                                                updatedAt = now
                                            ) else existing.copy(
                                                nameLower = nameLower,
                                                sortTitleLower = sortKey(r.name),
                                                imagesJson = existing.imagesJson ?: seedImagesJson,
                                                categoryId = r.category_id,
                                                providerKey = provider,
                                                genreKey = gKey,
                                                importedAt = existing.importedAt,
                                                updatedAt = now
                                            )
                                            synchronized(toPut) { toPut += e }
                                        }
                                    }
                                }
                            }.awaitAll()
                        }
                    } else {
                        val list = client.getSeries(categoryId = null, offset = 0, limit = limitPerKind)
                        list.forEach { r ->
                            val sid = r.series_id ?: return@forEach
                            val existing = seriesBox.query(ObxSeries_.seriesId.equal(sid.toLong())).build().findFirst()
                            val nameLower = r.name.orEmpty().lowercase()
                            val catName = serCats[r.category_id.orEmpty()]
                            val provider = bucketProvider("series", catName, r.name, null)
                            val gKey = deriveGenreKey(r.name, catName, null)
                            val seedImagesJson = runCatching {
                                val c = r.cover
                                if (!c.isNullOrBlank()) kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(String.serializer()), listOf(c)) else null
                            }.getOrNull()
                            val e = if (existing == null) ObxSeries(
                                seriesId = sid,
                                nameLower = nameLower,
                                sortTitleLower = sortKey(r.name),
                                name = r.name.orEmpty(),
                                imagesJson = seedImagesJson,
                                categoryId = r.category_id,
                                providerKey = provider,
                                genreKey = gKey,
                                importedAt = now,
                                updatedAt = now
                            ) else existing.copy(
                                nameLower = nameLower,
                                sortTitleLower = sortKey(r.name),
                                imagesJson = existing.imagesJson ?: seedImagesJson,
                                categoryId = r.category_id,
                                providerKey = provider,
                                genreKey = gKey,
                                importedAt = existing.importedAt,
                                updatedAt = now
                            )
                            toPut += e
                        }
                    }
                    if (toPut.isNotEmpty()) seriesBox.putChunked(toPut, 2000)
                    toPut.size
                }

                liveCount = liveJob.await()
                vodCount = vodJob.await()
                seriesCount = seriesJob.await()
            }

            android.util.Log.i("XtreamObxRepo", "seedListsQuick live=$liveCount vod=$vodCount series=$seriesCount perCategoryLimit=${perCategoryLimit}")
            runCatching { rebuildAggregatedIndexes(boxStore) }
            Triple(liveCount, vodCount, seriesCount)
        }
    }

    /**
     * Heads-only import for all kinds (no details), to maximize initial UX and search completeness.
     * Upserts minimal fields for Live/VOD/Series across the full lists. Runs kinds in parallel.
     */
    suspend fun importHeadsOnly(deleteOrphans: Boolean = false): Result<Triple<Int, Int, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = newClient()
            val box = ObxStore.get(context)
            val catBox = box.boxFor<ObxCategory>()
            val liveBox = box.boxFor<ObxLive>()
            val vodBox = box.boxFor<ObxVod>()
            val seriesBox = box.boxFor<ObxSeries>()

            // Categories first (upsert); do not delete by default (avoids flicker)
            val liveCats = client.getLiveCategories(); val vodCats = client.getVodCategories(); val serCats = client.getSeriesCategories()
            upsertCategories(catBox, "live", liveCats.associate { it.category_id.orEmpty() to it.category_name.orEmpty() }, deleteOrphans)
            upsertCategories(catBox, "vod",  vodCats.associate  { it.category_id.orEmpty() to it.category_name.orEmpty() }, deleteOrphans)
            upsertCategories(catBox, "series", serCats.associate { it.category_id.orEmpty() to it.category_name.orEmpty() }, deleteOrphans)

            val now = System.currentTimeMillis()
            val liveCatMap = catBox.query(ObxCategory_.kind.equal("live")).build().find().associateBy({ it.categoryId }, { it.categoryName })
            val vodCatMap  = catBox.query(ObxCategory_.kind.equal("vod")).build().find().associateBy({ it.categoryId }, { it.categoryName })
            val serCatMap  = catBox.query(ObxCategory_.kind.equal("series")).build().find().associateBy({ it.categoryId }, { it.categoryName })

            val res = coroutineScope {
                val liveJob = async(Dispatchers.IO) {
                    val list = client.getLiveStreams(categoryId = null, offset = 0, limit = Int.MAX_VALUE)
                    val upserts = list.mapNotNull { r ->
                        val sid = r.stream_id ?: return@mapNotNull null
                        val ex = liveBox.query(ObxLive_.streamId.equal(sid.toLong())).build().findFirst()
                        val catName = liveCatMap[r.category_id.orEmpty()]
                        val provider = bucketProvider("live", catName, r.name, null)
                        val gKey = deriveGenreKey(r.name, catName, null)
                        val e = ObxLive(
                            streamId = sid,
                            nameLower = r.name.orEmpty().lowercase(),
                            sortTitleLower = sortKey(r.name),
                            name = r.name.orEmpty(),
                            logo = r.stream_icon,
                            epgChannelId = r.epg_channel_id,
                            tvArchive = r.tv_archive,
                            categoryId = r.category_id,
                            providerKey = provider,
                            genreKey = gKey
                        )
                        if (ex != null) e.id = ex.id
                        e
                    }
                    if (upserts.isNotEmpty()) liveBox.putChunked(upserts, 2000)
                    if (deleteOrphans) {
                        val seen = upserts.map { it.streamId }.toSet()
                        val toRemove = liveBox.all.filter { it.streamId !in seen }
                        if (toRemove.isNotEmpty()) liveBox.remove(toRemove)
                    }
                    upserts.size
                }
                val vodJob = async(Dispatchers.IO) {
                    val list = client.getVodStreams(categoryId = null, offset = 0, limit = Int.MAX_VALUE)
                    val upserts = list.mapNotNull { r ->
                        val vid = r.vod_id ?: return@mapNotNull null
                        val ex = vodBox.query(ObxVod_.vodId.equal(vid.toLong())).build().findFirst()
                        val catName = vodCatMap[r.category_id.orEmpty()]
                        val provider = bucketProvider("vod", catName, r.name, null)
                        val e = ObxVod(
                            vodId = vid,
                            nameLower = r.name.orEmpty().lowercase(),
                            sortTitleLower = sortKey(r.name),
                            name = r.name.orEmpty(),
                            poster = r.stream_icon,
                            categoryId = r.category_id,
                            providerKey = provider,
                            importedAt = ex?.importedAt ?: now,
                            updatedAt = now
                        )
                        if (ex != null) e.id = ex.id
                        e
                    }
                    if (upserts.isNotEmpty()) vodBox.putChunked(upserts, 2000)
                    if (deleteOrphans) {
                        val seen = upserts.map { it.vodId }.toSet()
                        val toRemove = vodBox.all.filter { it.vodId !in seen }
                        if (toRemove.isNotEmpty()) vodBox.remove(toRemove)
                    }
                    upserts.size
                }
                val seriesJob = async(Dispatchers.IO) {
                    val list = client.getSeries(categoryId = null, offset = 0, limit = Int.MAX_VALUE)
                    val upserts = list.mapNotNull { r ->
                        val sid = r.series_id ?: return@mapNotNull null
                        val ex = seriesBox.query(ObxSeries_.seriesId.equal(sid.toLong())).build().findFirst()
                        val catName = serCatMap[r.category_id.orEmpty()]
                        val provider = bucketProvider("series", catName, r.name, null)
                        val seedImagesJson = runCatching { r.cover?.let { Json.encodeToString(ListSerializer(String.serializer()), listOf(it)) } }.getOrNull()
                        val e = ObxSeries(
                            seriesId = sid,
                            nameLower = r.name.orEmpty().lowercase(),
                            sortTitleLower = sortKey(r.name),
                            name = r.name.orEmpty(),
                            imagesJson = ex?.imagesJson ?: seedImagesJson,
                            categoryId = r.category_id,
                            providerKey = provider,
                            importedAt = ex?.importedAt ?: now,
                            updatedAt = now
                        )
                        if (ex != null) e.id = ex.id
                        e
                    }
                    if (upserts.isNotEmpty()) seriesBox.putChunked(upserts, 2000)
                    if (deleteOrphans) {
                        val seen = upserts.map { it.seriesId }.toSet()
                        val toRemove = seriesBox.all.filter { it.seriesId !in seen }
                        if (toRemove.isNotEmpty()) seriesBox.remove(toRemove)
                    }
                    upserts.size
                }
                Triple(liveJob.await(), vodJob.await(), seriesJob.await())
            }
            runCatching { rebuildAggregatedIndexes(box) }
            res
        }
    }

    /**
     * On-demand import of a single series details + episodes into ObjectBox.
     * Upserts ObxSeries and replaces episodes for the given seriesId.
     */
    suspend fun importSeriesDetailOnce(seriesId: Int, clientOverride: XtreamClient? = null): Result<Int> = withContext(Dispatchers.IO) {
        // Global gate: respect M3U/Xtream workers & API switch
        if (!settings.m3uWorkersEnabled.first()) return@withContext Result.success(0)
        val existing: kotlinx.coroutines.CompletableDeferred<Result<Int>>? = synchronized(inflightSeries) { inflightSeries[seriesId] }
        if (existing != null) return@withContext existing.await()
        val client = clientOverride ?: runCatching { newClient() }.getOrElse { return@withContext Result.failure(it) }
        val wait = kotlinx.coroutines.CompletableDeferred<Result<Int>>()
        synchronized(inflightSeries) { inflightSeries[seriesId] = wait }
        val result = runCatching {
            val boxStore = ObxStore.get(context)
            val serBox = boxStore.boxFor<ObxSeries>()
            val epBox = boxStore.boxFor<ObxEpisode>()

            val d = client.getSeriesDetailFull(seriesId) ?: return@runCatching 0
            val images = d.images ?: emptyList()
            val existing = serBox.query(ObxSeries_.seriesId.equal(seriesId.toLong())).build().findFirst()
            val merged = if (existing != null) existing.copy(
                nameLower = (d.name ?: existing.name).lowercase(),
                sortTitleLower = sortKey(d.name ?: existing.name),
                name = d.name ?: existing.name,
                imagesJson = runCatching { Json.encodeToString(ListSerializer(String.serializer()), images) }.getOrNull() ?: existing.imagesJson,
                year = d.year ?: existing.year,
                yearKey = deriveYearKeyInt(d.year, d.name) ?: existing.yearKey,
                rating = d.rating ?: existing.rating,
                plot = d.plot ?: existing.plot,
                genre = d.genre ?: existing.genre,
                director = d.director ?: existing.director,
                cast = d.cast ?: existing.cast,
                imdbId = d.imdbId ?: existing.imdbId,
                tmdbId = d.tmdbId ?: existing.tmdbId,
                trailer = d.trailer ?: existing.trailer,
                country = d.country ?: existing.country,
                releaseDate = d.releaseDate ?: existing.releaseDate,
                // keep categoryId/providerKey, derive genreKey only if missing
                categoryId = existing.categoryId,
                providerKey = existing.providerKey,
                genreKey = existing.genreKey ?: deriveGenreKey(d.name, null, d.genre)
            ) else ObxSeries(
                seriesId = seriesId,
                nameLower = (d.name ?: "").lowercase(),
                sortTitleLower = sortKey(d.name),
                name = d.name ?: "",
                imagesJson = runCatching { Json.encodeToString(ListSerializer(String.serializer()), images) }.getOrNull(),
                year = d.year,
                yearKey = deriveYearKeyInt(d.year, d.name),
                rating = d.rating,
                plot = d.plot,
                genre = d.genre,
                director = d.director,
                cast = d.cast,
                imdbId = d.imdbId,
                tmdbId = d.tmdbId,
                trailer = d.trailer,
                country = d.country,
                releaseDate = d.releaseDate,
                categoryId = null,
                providerKey = null,
                genreKey = deriveGenreKey(d.name, null, d.genre)
            )
            serBox.put(merged)

            // Replace episodes for this series (chunked put)
            val existingEpisodes = epBox.query(ObxEpisode_.seriesId.equal(seriesId.toLong())).build().find()
            if (existingEpisodes.isNotEmpty()) epBox.remove(existingEpisodes)
            val episodes = mutableListOf<ObxEpisode>()
            d.seasons.forEach { s ->
                s.episodes.forEach { e ->
                    episodes += ObxEpisode(
                        seriesId = seriesId,
                        season = s.seasonNumber,
                        episodeNum = e.episodeNum,
                        episodeId = e.episodeId ?: 0,
                        title = e.title,
                        durationSecs = e.durationSecs,
                        rating = e.rating,
                        plot = e.plot,
                        airDate = e.airDate,
                        playExt = e.playExt,
                        imageUrl = e.posterUrl
                    )
                }
            }
            if (episodes.isNotEmpty()) epBox.putChunked(episodes, 2000)
            episodes.size
        }
        wait.complete(result)
        synchronized(inflightSeries) { inflightSeries.remove(seriesId) }
        result
    }

    /**
     * On-demand import of a single VOD details into ObjectBox.
     * Upserts ObxVod with images/plot/genre/year/trailer/containerExt and other fields.
     */
    suspend fun importVodDetailOnce(vodId: Int, clientOverride: XtreamClient? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        // Global gate: respect M3U/Xtream workers & API switch
        if (!settings.m3uWorkersEnabled.first()) return@withContext Result.success(false)
        val existing: kotlinx.coroutines.CompletableDeferred<Result<Boolean>>? = synchronized(inflightVod) { inflightVod[vodId] }
        if (existing != null) return@withContext existing.await()
        val client = clientOverride ?: runCatching { newClient() }.getOrElse { return@withContext Result.failure(it) }
        val wait = kotlinx.coroutines.CompletableDeferred<Result<Boolean>>()
        synchronized(inflightVod) { inflightVod[vodId] = wait }
        val result = runCatching {
            val boxStore = ObxStore.get(context)
            val vodBox = boxStore.boxFor<ObxVod>()
            val catBox = boxStore.boxFor<ObxCategory>()

            val d = client.getVodDetailFull(vodId) ?: return@runCatching false
            val images = d.images
            val existing = vodBox.query(ObxVod_.vodId.equal(vodId.toLong())).build().findFirst()
            // Try to derive provider/genre keys with available hints
            val catName = existing?.categoryId?.let { id ->
                catBox.query(ObxCategory_.kind.equal("vod").and(ObxCategory_.categoryId.equal(id))).build().findFirst()?.categoryName
            }
            val provider = bucketProvider("vod", catName, d.name, null)
            val gKey = deriveGenreKey(d.name, catName, d.genre)
            val y = d.year
            val yKey = deriveYearKeyInt(y, d.name)

            val merged = if (existing != null) existing.copy(
                nameLower = (d.name ?: existing.name).lowercase(),
                sortTitleLower = sortKey(d.name ?: existing.name),
                name = d.name ?: existing.name,
                poster = images.firstOrNull() ?: existing.poster,
                imagesJson = runCatching { Json.encodeToString(ListSerializer(String.serializer()), images) }.getOrNull() ?: existing.imagesJson,
                year = y ?: existing.year,
                yearKey = yKey ?: existing.yearKey,
                rating = d.rating ?: existing.rating,
                plot = d.plot ?: existing.plot,
                genre = d.genre ?: existing.genre,
                director = d.director ?: existing.director,
                cast = d.cast ?: existing.cast,
                country = d.country ?: existing.country,
                releaseDate = d.releaseDate ?: existing.releaseDate,
                imdbId = d.imdbId ?: existing.imdbId,
                tmdbId = d.tmdbId ?: existing.tmdbId,
                trailer = d.trailer ?: existing.trailer,
                containerExt = d.containerExt ?: existing.containerExt,
                durationSecs = d.durationSecs ?: existing.durationSecs,
                categoryId = existing.categoryId,
                providerKey = existing.providerKey ?: provider,
                genreKey = existing.genreKey ?: gKey
            ) else ObxVod(
                vodId = vodId,
                nameLower = d.name.lowercase(),
                sortTitleLower = sortKey(d.name),
                name = d.name,
                poster = images.firstOrNull(),
                imagesJson = runCatching { Json.encodeToString(ListSerializer(String.serializer()), images) }.getOrNull(),
                year = y,
                yearKey = yKey,
                rating = d.rating,
                plot = d.plot,
                genre = d.genre,
                director = d.director,
                cast = d.cast,
                country = d.country,
                releaseDate = d.releaseDate,
                imdbId = d.imdbId,
                tmdbId = d.tmdbId,
                trailer = d.trailer,
                containerExt = d.containerExt,
                durationSecs = d.durationSecs,
                categoryId = existing?.categoryId,
                providerKey = provider,
                genreKey = gKey
            )
            vodBox.put(merged)
            true
        }
        wait.complete(result)
        synchronized(inflightVod) { inflightVod.remove(vodId) }
        result
    }

    private fun deriveGenreKey(name: String?, categoryName: String?, explicitGenre: String? = null): String? {
        // Only use category name for genre normalization (no title/explicit scanning)
        val s = (categoryName ?: "").lowercase()
        if (s.isBlank()) return "other"
        fun has(vararg tokens: String): Boolean = tokens.any { t -> s.contains(t) }
        fun hasRe(re: Regex): Boolean = re.containsMatchIn(s)

        // Themed buckets from category naming only
        if (has("4k", "uhd")) return "4k"
        if (has("filmreihe", "saga", "collection", "kollektion", "collectie", "marvel", "star wars", "james bond", "bruce lee", "terence hill", "bud spencer")) return "collection"
        if (has("show")) return "show"
        if (has("abenteuer") || hasRe(Regex("\\bavventur")) || hasRe(Regex("\\bavontuur\\b"))) return "adventure"

        // Core genres (category-based only)
        if (has("drama")) return "drama"
        if (has("action", "azione")) return "action"
        if (has("komö", "komed", "comedy", "commedia")) return "comedy"
        if (has("kids", "kinder", "animation", "animazione")) return "kids"
        if (has("horror", "zombie", "zombies")) return "horror"
        if (has("thriller") || has("krimi", "crime", "trileri")) return "thriller"
        if (has("doku", "docu", "dokument", "documentary", "documentaire")) return "documentary"
        if (has("romance", "romant", "liebesfilm")) return "romance"
        if (has("family", "familie")) return "family"
        if (has("weihnacht", "noel", "christmas")) return "christmas"
        if (has("sci-fi", "science fiction", "scifi")) return "sci_fi"
        if (has("western")) return "western"
        if (has("krieg") || has("war") || has("guerre")) return "war"
        if (has("bollywood")) return "bollywood"
        if (has("anime", "manga")) return "anime"
        if (has("fantasy", "fantastico", "fantastique", "fantazija")) return "fantasy"
        if (has("martial arts", "arts martiaux", "martial", "kung fu")) return "martial_arts"
        if (has("classic", "classique", "klassik", "vieux films", "klassiker")) return "classic"

        // Fallback
        return "other"
    }

    /**
     * Delta import (upsert) for large datasets. Avoids full clears and fetches details only for new/changed items.
     * Returns counts Triple(live, vod, series) processed.
     */
    suspend fun importDelta(deleteOrphans: Boolean = true, includeLive: Boolean = true): Result<Triple<Int, Int, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val enableDetailsInDelta = false
            val client = newClient()
            val boxStore = ObxStore.get(context)
            val catBox = boxStore.boxFor<ObxCategory>()
            val liveBox = boxStore.boxFor<ObxLive>()
            val vodBox = boxStore.boxFor<ObxVod>()
            val seriesBox = boxStore.boxFor<ObxSeries>()

            // Kategorien: Diff (Upsert + optionales Löschen verwaister Kategorien)
            run {
                val liveCats = client.getLiveCategories(); val vodCats = client.getVodCategories(); val serCats = client.getSeriesCategories()
                upsertCategories(catBox, "live", liveCats.associate { it.category_id.orEmpty() to it.category_name.orEmpty() }, deleteOrphans = deleteOrphans)
                upsertCategories(catBox, "vod",  vodCats.associate  { it.category_id.orEmpty() to it.category_name.orEmpty() }, deleteOrphans = deleteOrphans)
                upsertCategories(catBox, "series", serCats.associate { it.category_id.orEmpty() to it.category_name.orEmpty() }, deleteOrphans = deleteOrphans)
            }

            // --- Live (upsert by streamId, aggregated per-category to avoid global caps) ---
            val liveSeen = mutableSetOf<Int>()
            if (includeLive) run {
                val liveCatRows = catBox.query(ObxCategory_.kind.equal("live")).build().find()
                val liveCats = liveCatRows.associateBy({ it.categoryId }, { it.categoryName })
                val catIds = liveCatRows.mapNotNull { it.categoryId }.ifEmpty { listOf<String?>(null) }
                catIds.forEach { catId ->
                    val chunk = client.getLiveStreams(categoryId = catId, offset = 0, limit = Int.MAX_VALUE)
                    if (chunk.isEmpty()) return@forEach
                    val toPut = mutableListOf<ObxLive>()
                    chunk.forEach { r ->
                        val sid = r.stream_id ?: return@forEach
                        if (!liveSeen.add(sid)) return@forEach
                        val existing = liveBox.query(ObxLive_.streamId.equal(sid.toLong())).build().findFirst()
                        val catName = liveCats[r.category_id]
                        val provider = bucketProvider("live", catName, r.name, null)
                        val gKey = deriveGenreKey(r.name, catName, null)
                        val entity = ObxLive(
                            streamId = sid,
                            nameLower = r.name.orEmpty().lowercase(),
                            sortTitleLower = sortKey(r.name),
                            name = r.name.orEmpty(),
                            logo = r.stream_icon,
                            epgChannelId = r.epg_channel_id,
                            tvArchive = r.tv_archive,
                            categoryId = r.category_id,
                            providerKey = provider,
                            genreKey = gKey
                        )
                        if (existing != null) entity.id = existing.id
                        toPut += entity
                    }
                    if (toPut.isNotEmpty()) liveBox.putChunked(toPut, 2000)
                }

                if (deleteOrphans && liveSeen.isNotEmpty()) {
                    val q = liveBox.query().build()
                    var offScan = 0L
                    val pageScan = 5000L
                    val toRemove = mutableListOf<ObxLive>()
                    while (true) {
                        val batch = q.find(offScan, pageScan)
                        if (batch.isEmpty()) break
                        batch.forEach { if (it.streamId !in liveSeen) toRemove += it }
                        offScan += batch.size
                    }
                    if (toRemove.isNotEmpty()) liveBox.remove(toRemove)
                }
            }

            // --- VOD (upsert; details only for new/changed) ---
            val vodSeen = mutableSetOf<Int>()
            run {
                val updates = mutableListOf<ObxVod>()
                val now = System.currentTimeMillis()
                val vodCatRows = catBox.query(ObxCategory_.kind.equal("vod")).build().find()
                val vodCats = vodCatRows.associateBy({ it.categoryId }, { it.categoryName })
                val toDetail = mutableListOf<Triple<Int, String?, String?>>()

                fun processListChunk(chunk: List<RawVod>, vodCats: Map<String, String?>) {
                    chunk.forEach { r ->
                        val vid = r.vod_id ?: return@forEach
                        if (!vodSeen.add(vid)) return@forEach
                        val existing = vodBox.query(ObxVod_.vodId.equal(vid.toLong())).build().findFirst()
                        val nameLower = r.name.orEmpty().lowercase()
                        val catIdRow = r.category_id
                        val listExt = sanitizeContainerExt(r.container_extension)
                        var needsDetail = existing == null || existing.nameLower != nameLower || existing.categoryId != catIdRow
                        if (!needsDetail && existing != null && existing.containerExt.isNullOrBlank() && listExt.isNullOrBlank()) {
                            needsDetail = true
                        }
                        if (needsDetail) {
                            // Always enqueue minimal upsert so the head is visible even without details.
                            // Details (when enabled) will enrich/overwrite later.
                            val catName = vodCats[catIdRow ?: ""]
                            val provider = bucketProvider("vod", catName, r.name, null)
                            val minimal = ObxVod(
                                vodId = vid,
                                nameLower = nameLower,
                                sortTitleLower = sortKey(r.name),
                                name = r.name.orEmpty(),
                                poster = r.stream_icon,
                                categoryId = catIdRow,
                                providerKey = provider,
                                genreKey = deriveGenreKey(r.name, catName, null),
                                containerExt = listExt,
                                importedAt = existing?.importedAt ?: now,
                                updatedAt = now
                            ).also { ex -> existing?.let { ex.id = it.id } }
                            updates += minimal
                            toDetail += Triple(vid, catIdRow, r.name)
                        } else if (existing != null) {
                            val catName = vodCats[catIdRow ?: ""]
                            val provider = bucketProvider("vod", catName, r.name, null)
                            val gKeyListOnly = existing.genreKey ?: deriveGenreKey(r.name, catName, null)
                            updates += existing.copy(
                                nameLower = nameLower,
                                sortTitleLower = sortKey(r.name),
                                categoryId = catIdRow,
                                providerKey = provider,
                                genreKey = gKeyListOnly,
                                containerExt = listExt ?: existing.containerExt,
                                importedAt = existing.importedAt,
                                updatedAt = now
                            )
                        }
                    }
                }

                // Opportunistic wildcard: try category_id=*, then fill gaps per category
                val wildcard = runCatching { client.getVodStreams(categoryId = "*", offset = 0, limit = Int.MAX_VALUE) }.getOrNull().orEmpty()
                val coveredCats = mutableSetOf<String>()
                if (wildcard.isNotEmpty()) {
                    processListChunk(wildcard, vodCats)
                    wildcard.forEach { c -> c.category_id?.let { coveredCats += it } }
                }

                // Fetch only categories not covered by wildcard
                val catIds = vodCatRows.mapNotNull { it.categoryId }.filterNot { it in coveredCats }.ifEmpty { emptyList() }
                catIds.forEach { catId ->
                    val chunk = client.getVodStreams(categoryId = catId, offset = 0, limit = Int.MAX_VALUE)
                    if (chunk.isEmpty()) return@forEach
                    processListChunk(chunk, vodCats)
                }

                if (updates.isNotEmpty()) vodBox.putChunked(updates, 2000)

                if (enableDetailsInDelta && toDetail.isNotEmpty()) {
                    val sem = Semaphore(4)
                    val results = coroutineScope {
                        toDetail.take(100).map { (vid, catId, nameFallback) ->
                            async(Dispatchers.IO) {
                                sem.withPermit {
                                    val d = client.getVodDetailFull(vid)
                                    val images = d?.images ?: emptyList()
                                    val catName = vodCats[catId.orEmpty()]
                                    val provider = bucketProvider("vod", catName, d?.name ?: nameFallback, null)
                                    val gKey = deriveGenreKey(d?.name ?: nameFallback, catName, d?.genre)
                                    val y = d?.year
                                    val yKey = deriveYearKeyInt(y, d?.name ?: nameFallback)
                                    val existing = vodBox.query(ObxVod_.vodId.equal(vid.toLong())).build().findFirst()
                                    val detExt = sanitizeContainerExt(d?.containerExt)
                                    val ext = detExt ?: existing?.containerExt
                                    val now2 = System.currentTimeMillis()
                                    val merged = if (existing != null) existing.copy(
                                        nameLower = (d?.name ?: existing.name).lowercase(),
                                        sortTitleLower = sortKey(d?.name ?: existing.name),
                                        name = d?.name ?: existing.name,
                                        poster = images.firstOrNull() ?: existing.poster,
                                        imagesJson = runCatching { Json.encodeToString(ListSerializer(String.serializer()), images) }.getOrNull() ?: existing.imagesJson,
                                        year = y ?: existing.year,
                                        yearKey = yKey ?: existing.yearKey,
                                        rating = d?.rating ?: existing.rating,
                                        plot = d?.plot ?: existing.plot,
                                        genre = d?.genre ?: existing.genre,
                                        director = d?.director ?: existing.director,
                                        cast = d?.cast ?: existing.cast,
                                        country = d?.country ?: existing.country,
                                        releaseDate = d?.releaseDate ?: existing.releaseDate,
                                        imdbId = d?.imdbId ?: existing.imdbId,
                                        tmdbId = d?.tmdbId ?: existing.tmdbId,
                                        trailer = d?.trailer ?: existing.trailer,
                                        containerExt = ext ?: existing.containerExt,
                                        // Keep original categoryId/providerKey unless missing
                                        categoryId = existing.categoryId ?: catId,
                                        providerKey = existing.providerKey ?: provider,
                                        genreKey = existing.genreKey ?: gKey,
                                        importedAt = existing.importedAt,
                                        updatedAt = now2
                                    ) else ObxVod(
                                        vodId = vid,
                                        nameLower = (d?.name ?: nameFallback.orEmpty()).lowercase(),
                                        sortTitleLower = sortKey(d?.name ?: nameFallback),
                                        name = d?.name ?: nameFallback.orEmpty(),
                                        poster = images.firstOrNull(),
                                        imagesJson = runCatching { Json.encodeToString(ListSerializer(String.serializer()), images) }.getOrNull(),
                                        year = y,
                                        yearKey = yKey,
                                        rating = d?.rating,
                                        plot = d?.plot,
                                        genre = d?.genre,
                                        director = d?.director,
                                        cast = d?.cast,
                                        country = d?.country,
                                        releaseDate = d?.releaseDate,
                                        imdbId = d?.imdbId,
                                        tmdbId = d?.tmdbId,
                                        trailer = d?.trailer,
                                        containerExt = ext,
                                        categoryId = catId,
                                        providerKey = provider,
                                        genreKey = gKey,
                                        importedAt = now2,
                                        updatedAt = now2
                                    )
                                    merged
                                }
                            }
                        }.awaitAll()
                    }
                    if (results.isNotEmpty()) vodBox.putChunked(results, 2000)
                }
                if (deleteOrphans && vodSeen.isNotEmpty()) {
                    val q = vodBox.query().build()
                    var offScan = 0L
                    val pageScan = 5000L
                    val toRemove = mutableListOf<ObxVod>()
                    while (true) {
                        val batch = q.find(offScan, pageScan)
                        if (batch.isEmpty()) break
                        batch.forEach { if (it.vodId !in vodSeen) toRemove += it }
                        offScan += batch.size
                    }
                    if (toRemove.isNotEmpty()) vodBox.remove(toRemove)
                }
            }

            // --- Series (upsert; details only for new/changed; chunked) ---
            val seriesSeen = mutableSetOf<Int>()
            run {
                val updates = mutableListOf<ObxSeries>()
                val serCatNameMap = catBox.query(ObxCategory_.kind.equal("series")).build().find().associateBy({ it.categoryId }, { it.categoryName })
                val toDetail = mutableListOf<Pair<Int, String?>>()
                val allSeriesHeads = client.getSeries(categoryId = null, offset = 0, limit = Int.MAX_VALUE)
                allSeriesHeads.forEach { r ->
                        val sid = r.series_id ?: return@forEach
                        seriesSeen += sid
                        val existing = seriesBox.query(ObxSeries_.seriesId.equal(sid.toLong())).build().findFirst()
                        val nameLower = r.name.orEmpty().lowercase()
                        val catId = r.category_id
                        var needsDetail = existing == null || existing.nameLower != nameLower || existing.categoryId != catId
                        if (!needsDetail) {
                            val epBox = boxStore.boxFor<ObxEpisode>()
                            val query = epBox.query(
                                ObxEpisode_.seriesId.equal(sid.toLong())
                                    .and(ObxEpisode_.episodeId.equal(0))
                            ).build()
                            needsDetail = runCatching { query.findFirst() != null }.getOrElse { false }
                            query.close()
                            if (!needsDetail) {
                                val missingExtQuery = epBox.query(ObxEpisode_.seriesId.equal(sid.toLong())).build()
                                needsDetail = runCatching {
                                    missingExtQuery.find().any { it.playExt.isNullOrBlank() }
                                }.getOrElse { false }
                                missingExtQuery.close()
                            }
                        }
                        if (needsDetail) {
                            // Upsert minimal head so the series exists even without detail import
                            val catName = serCatNameMap[catId.orEmpty()]
                            val provider = bucketProvider("series", catName, r.name, null)
                            val seedImagesJson = runCatching { r.cover?.let { Json.encodeToString(ListSerializer(String.serializer()), listOf(it)) } }.getOrNull()
                            val minimal = if (existing == null) ObxSeries(
                                seriesId = sid,
                                nameLower = nameLower,
                                sortTitleLower = sortKey(r.name),
                                name = r.name.orEmpty(),
                                imagesJson = seedImagesJson,
                                categoryId = catId,
                                providerKey = provider,
                                importedAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            ) else existing.copy(
                                nameLower = nameLower,
                                sortTitleLower = sortKey(r.name),
                                categoryId = catId,
                                providerKey = provider,
                                updatedAt = System.currentTimeMillis()
                            )
                            updates += minimal
                            toDetail += (sid to catId)
                        } else {
                            val catName = serCatNameMap[catId.orEmpty()]
                            val provider = bucketProvider("series", catName, r.name, null)
                            existing?.let {
                                updates += it.copy(
                                    nameLower = nameLower,
                                    sortTitleLower = sortKey(r.name),
                                    categoryId = catId,
                                    providerKey = provider,
                                    importedAt = it.importedAt,
                                    updatedAt = System.currentTimeMillis()
                                )
                            }
                        }
                }
                if (updates.isNotEmpty()) seriesBox.putChunked(updates, 2000)

                if (enableDetailsInDelta && toDetail.isNotEmpty()) {
                    val sem = Semaphore(4)
                    val results = coroutineScope {
                        toDetail.take(100).map { (sid, catId) ->
                            async(Dispatchers.IO) {
                                sem.withPermit {
                                    val d = client.getSeriesDetailFull(sid)
                                    val images = d?.images ?: emptyList()
                            val catName = serCatNameMap[catId.orEmpty()]
                                    val provider = bucketProvider("series", catName, d?.name, null)
                                    val gKey = deriveGenreKey(d?.name, catName, d?.genre)
                                    val y = d?.year
                                    val yKey = deriveYearKeyInt(y, d?.name)
                                    val existing = seriesBox.query(ObxSeries_.seriesId.equal(sid.toLong())).build().findFirst()
                                    val now = System.currentTimeMillis()
                                    val entity = ObxSeries(
                                        seriesId = sid,
                                        nameLower = (d?.name ?: existing?.name.orEmpty()).lowercase(),
                                        sortTitleLower = sortKey(d?.name ?: existing?.name),
                                        name = d?.name ?: existing?.name.orEmpty(),
                                        imagesJson = runCatching { Json.encodeToString(ListSerializer(String.serializer()), images) }.getOrNull(),
                                        year = y,
                                        yearKey = yKey,
                                        rating = d?.rating,
                                        plot = d?.plot,
                                        genre = d?.genre,
                                        director = d?.director,
                                        cast = d?.cast,
                                        imdbId = d?.imdbId,
                                        tmdbId = d?.tmdbId,
                                        trailer = d?.trailer,
                                        categoryId = catId,
                                        providerKey = provider,
                                        genreKey = gKey,
                                        importedAt = existing?.importedAt ?: now,
                                        updatedAt = now
                                    )
                                    if (existing != null) entity.id = existing.id
                                    entity
                                }
                            }
                        }.awaitAll()
                    }
                    if (results.isNotEmpty()) seriesBox.putChunked(results, 2000)
                }
                if (deleteOrphans && seriesSeen.isNotEmpty()) {
                    val q = seriesBox.query().build()
                    var offScan = 0L
                    val pageScan = 5000L
                    val toRemove = mutableListOf<ObxSeries>()
                    while (true) {
                        val batch = q.find(offScan, pageScan)
                        if (batch.isEmpty()) break
                        batch.forEach { if (it.seriesId !in seriesSeen) toRemove += it }
                        offScan += batch.size
                    }
                    if (toRemove.isNotEmpty()) seriesBox.remove(toRemove)
                }
            }

            runCatching { rebuildAggregatedIndexes(boxStore) }
            Triple(liveSeen.size, vodSeen.size, seriesSeen.size)
        }
    }

    suspend fun refreshDetailsChunk(vodLimit: Int = 40, seriesLimit: Int = 20): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!settings.m3uWorkersEnabled.first()) return@runCatching 0 to 0
            if (vodLimit <= 0 && seriesLimit <= 0) return@runCatching 0 to 0
            val client = newClient()
            val store = ObxStore.get(context)
            val vodBox = store.boxFor<ObxVod>()
            val seriesBox = store.boxFor<ObxSeries>()
            val epBox = store.boxFor<ObxEpisode>()

            fun needsVodDetail(v: ObxVod): Boolean =
                v.plot.isNullOrBlank() ||
                v.imagesJson.isNullOrBlank() ||
                v.containerExt.isNullOrBlank() ||
                v.rating == null ||
                v.genre.isNullOrBlank() ||
                v.durationSecs == null

            fun needsSeriesDetail(s: ObxSeries): Boolean {
                if (s.plot.isNullOrBlank() || s.imagesJson.isNullOrBlank()) return true
                val query = epBox.query(ObxEpisode_.seriesId.equal(s.seriesId.toLong())).build()
                return try {
                    val total = query.count()
                    // Für kleine Serien (<=80 Episoden) alle prüfen; sonst Sample (64)
                    if (total <= 80) query.find(0, total).any { it.playExt.isNullOrBlank() || it.imageUrl.isNullOrBlank() }
                    else query.find(0, 64).any { it.playExt.isNullOrBlank() || it.imageUrl.isNullOrBlank() }
                } finally {
                    query.close()
                }
            }

            val multiplier = 4
            var vodUpdated = 0
            if (vodLimit > 0) {
                val limit = (vodLimit * multiplier).coerceAtLeast(vodLimit)
                val query = vodBox.query().order(ObxVod_.updatedAt).build()
                val raw = try { query.find(0, limit.toLong()) } finally { query.close() }
                val candidates = raw.filter(::needsVodDetail).take(vodLimit)
                for (candidate in candidates) {
                    val result = importVodDetailOnce(candidate.vodId, client)
                    if (result.isSuccess && result.getOrDefault(false)) vodUpdated++
                }
            }

            var seriesUpdated = 0
            if (seriesLimit > 0) {
                val limit = (seriesLimit * multiplier).coerceAtLeast(seriesLimit)
                val query = seriesBox.query().order(ObxSeries_.updatedAt).build()
                val raw = try { query.find(0, limit.toLong()) } finally { query.close() }
                val candidates = mutableListOf<ObxSeries>()
                for (series in raw) {
                    if (candidates.size >= seriesLimit) break
                    if (needsSeriesDetail(series)) {
                        candidates += series
                    }
                }
                for (series in candidates) {
                    val result = importSeriesDetailOnce(series.seriesId, client)
                    if (result.isSuccess) seriesUpdated++
                }
            }

            runCatching { ObxStore.get(context).closeThreadResources() }
            vodUpdated to seriesUpdated
        }
    }

    private fun sortKey(name: String?): String {
        val s = name.orEmpty().trim().lowercase()
        val drop = listOf("the ", "a ", "an ", "der ", "die ", "das ", "le ", "la ", "les ", "el ", "los ", "las ")
        var out = s
        drop.forEach { art -> if (out.startsWith(art)) out = out.removePrefix(art) }
        out = out.replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")
        return out
    }

    private fun deriveYearKeyInt(explicit: Int?, name: String?): Int? {
        return explicit ?: com.chris.m3usuite.domain.selectors.extractYearFrom(name)
    }
    private suspend fun newClient(forceRefreshDiscovery: Boolean = false): XtreamClient = withContext(Dispatchers.IO) {
        val http = HttpClientFactory.create(context, settings)
        val store = ProviderCapabilityStore(context)
        val portStore = EndpointPortStore(context)
        val host = settings.xtHost.first()
        val user = settings.xtUser.first()
        val pass = settings.xtPass.first()
        val port = settings.xtPort.first()
        val scheme = if (port == 443) "https" else "http"
        val client = XtreamClient(http)
        client.initialize(
            scheme = scheme,
            host = host,
            username = user,
            password = pass,
            basePath = null,
            store = store,
            portStore = portStore,
            forceRefreshDiscovery = forceRefreshDiscovery,
            portOverride = port
        )
        client
    }

    /**
     * Full import into ObjectBox: categories, live, vod (with details), series (with seasons+episodes).
     * Designed for on-demand usage (UI can be wired later).
     */
    @Deprecated("Heavy path; prefer importDelta(deleteOrphans=true) in production")
    suspend fun importAllFull(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val client = newClient()
            val boxStore = ObxStore.get(context)
            val catBox = boxStore.boxFor<ObxCategory>()
            val liveBox = boxStore.boxFor<ObxLive>()
            val vodBox = boxStore.boxFor<ObxVod>()
            val seriesBox = boxStore.boxFor<ObxSeries>()
            val epBox = boxStore.boxFor<ObxEpisode>()

            // Categories
            val liveCats = client.getLiveCategories()
            val vodCats = client.getVodCategories()
            val serCats = client.getSeriesCategories()
            val liveCatMap = liveCats.associateBy({ it.category_id.orEmpty() }, { it.category_name })
            val vodCatMap = vodCats.associateBy({ it.category_id.orEmpty() }, { it.category_name })
            val serCatMap = serCats.associateBy({ it.category_id.orEmpty() }, { it.category_name })
            catBox.removeAll()
            catBox.put(liveCats.map { ObxCategory(kind = "live", categoryId = it.category_id.orEmpty(), categoryName = it.category_name) })
            catBox.put(vodCats.map { ObxCategory(kind = "vod", categoryId = it.category_id.orEmpty(), categoryName = it.category_name) })
            catBox.put(serCats.map { ObxCategory(kind = "series", categoryId = it.category_id.orEmpty(), categoryName = it.category_name) })

            // Live – bulk list
            val live = client.getLiveStreams(categoryId = null, offset = 0, limit = Int.MAX_VALUE)
            liveBox.removeAll()
            liveBox.put(live.mapNotNull { r ->
                val sid = r.stream_id ?: return@mapNotNull null
                val catName = liveCatMap[r.category_id.orEmpty()]
                val provider = bucketProvider("live", catName, r.name, null)
                val genreKey = deriveGenreKey(r.name, catName, null)
                ObxLive(
                    streamId = sid,
                    nameLower = r.name.orEmpty().lowercase(),
                    sortTitleLower = sortKey(r.name),
                    name = r.name.orEmpty(),
                    logo = r.stream_icon,
                    epgChannelId = r.epg_channel_id,
                    tvArchive = r.tv_archive,
                    categoryId = r.category_id,
                    providerKey = provider,
                    genreKey = genreKey
                )
            })

            // VOD – bulk list dann Details parallel, chunked put
            val vod = client.getVodStreams(categoryId = null, offset = 0, limit = Int.MAX_VALUE)
            vodBox.removeAll()
            val sem = Semaphore(6)
            val vodResults = coroutineScope {
                vod.mapNotNull { r ->
                    val vid = r.vod_id ?: return@mapNotNull null
                    async(Dispatchers.IO) {
                        sem.withPermit {
                            val d = client.getVodDetailFull(vid)
                            val images = d?.images ?: emptyList()
                            val catName = vodCatMap[r.category_id.orEmpty()]
                            val provider = bucketProvider("vod", catName, d?.name ?: r.name, null)
                            val gKey = deriveGenreKey(d?.name ?: r.name, catName, d?.genre)
                            val y = d?.year
                            val yKey = deriveYearKeyInt(y, d?.name ?: r.name)
                            val listExt = sanitizeContainerExt(r.container_extension)
                            val detailExt = sanitizeContainerExt(d?.containerExt)
                            val resolvedExt = detailExt ?: listExt
                            ObxVod(
                                vodId = vid,
                                nameLower = (d?.name ?: r.name.orEmpty()).lowercase(),
                                sortTitleLower = sortKey(d?.name ?: r.name),
                                name = d?.name ?: r.name.orEmpty(),
                                poster = images.firstOrNull(),
                                imagesJson = runCatching { Json.encodeToString(ListSerializer(String.serializer()), images) }.getOrNull(),
                                year = y,
                                yearKey = yKey,
                                rating = d?.rating,
                                plot = d?.plot,
                                genre = d?.genre,
                                director = d?.director,
                                cast = d?.cast,
                                country = d?.country,
                                releaseDate = d?.releaseDate,
                                imdbId = d?.imdbId,
                                tmdbId = d?.tmdbId,
                                trailer = d?.trailer,
                                containerExt = resolvedExt,
                                categoryId = r.category_id,
                                providerKey = provider,
                                genreKey = gKey
                            )
                        }
                    }
                }.awaitAll()
            }
            if (vodResults.isNotEmpty()) vodBox.putChunked(vodResults, 2000)

            // Series – aggregate list per-category, then Details + Episoden, chunked puts
            val serCatRows = catBox.query(ObxCategory_.kind.equal("series")).build().find()
            val seriesCatNameMap = serCatRows.associateBy({ it.categoryId }, { it.categoryName })
            val series = mutableListOf<RawSeries>()
            val serSeen = mutableSetOf<Int>()
            val serCatIds = serCatRows.mapNotNull { it.categoryId }.ifEmpty { listOf<String?>(null) }
            serCatIds.forEach { catId ->
                val chunk = client.getSeries(categoryId = catId, offset = 0, limit = Int.MAX_VALUE)
                if (chunk.isEmpty()) return@forEach
                chunk.forEach { r ->
                    val sid = r.series_id ?: return@forEach
                    if (serSeen.add(sid)) series += r
                }
            }
            seriesBox.removeAll(); epBox.removeAll()
            val seriesDetails = coroutineScope {
                val sem = Semaphore(6)
                series.mapNotNull { r ->
                    val sid = r.series_id ?: return@mapNotNull null
                    async(Dispatchers.IO) {
                        sem.withPermit {
                            val d = client.getSeriesDetailFull(sid)
                            val images = d?.images ?: emptyList()
                            val catName = seriesCatNameMap[r.category_id]
                            val provider = bucketProvider("series", catName, d?.name ?: r.name, null)
                            val gKey = deriveGenreKey(d?.name ?: r.name, catName, d?.genre)
                            val y = d?.year
                            val yKey = deriveYearKeyInt(y, d?.name ?: r.name)
                            val sEntity = ObxSeries(
                                seriesId = sid,
                                nameLower = (d?.name ?: r.name.orEmpty()).lowercase(),
                                sortTitleLower = sortKey(d?.name ?: r.name),
                                name = d?.name ?: r.name.orEmpty(),
                                imagesJson = runCatching { Json.encodeToString(ListSerializer(String.serializer()), images) }.getOrNull(),
                                year = y,
                                yearKey = yKey,
                                rating = d?.rating,
                                plot = d?.plot,
                                genre = d?.genre,
                                director = d?.director,
                                cast = d?.cast,
                                imdbId = d?.imdbId,
                                tmdbId = d?.tmdbId,
                                trailer = d?.trailer,
                                categoryId = r.category_id,
                                providerKey = provider,
                                genreKey = gKey
                            )
                            val episodes = mutableListOf<ObxEpisode>()
                            d?.seasons?.forEach { s ->
                                s.episodes.forEach { e ->
                                    episodes += ObxEpisode(
                                        seriesId = sid,
                                        season = s.seasonNumber,
                                        episodeNum = e.episodeNum,
                                        episodeId = e.episodeId ?: 0,
                                        title = e.title,
                                        durationSecs = e.durationSecs,
                                        rating = e.rating,
                                        plot = e.plot,
                                        airDate = e.airDate,
                                        playExt = e.playExt,
                                        imageUrl = e.posterUrl
                                    )
                                }
                            }
                            Pair(sEntity, episodes)
                        }
                    }
                }.awaitAll()
            }
            seriesDetails.forEach { (s, eps) ->
                seriesBox.put(s)
                if (eps.isNotEmpty()) epBox.putChunked(eps, 2000)
            }

            runCatching { rebuildAggregatedIndexes(boxStore) }
            live.size + vod.size + series.size
        }
    }

    // --- EPG upsert helpers (ObjectBox) ---
    suspend fun upsertEpgNowNext(streamId: Int?, channelId: String?, list: List<XtShortEPGProgramme>) = withContext(Dispatchers.IO) {
        val box = ObxStore.get(context).boxFor<ObxEpgNowNext>()
        val now = list.getOrNull(0)
        val next = list.getOrNull(1)
        val row = ObxEpgNowNext(
            streamId = streamId,
            channelId = channelId,
            nowTitle = now?.title,
            nowStartMs = now?.start?.toLongOrNull()?.times(1000),
            nowEndMs = now?.end?.toLongOrNull()?.times(1000),
            nextTitle = next?.title,
            nextStartMs = next?.start?.toLongOrNull()?.times(1000),
            nextEndMs = next?.end?.toLongOrNull()?.times(1000),
            updatedAt = System.currentTimeMillis()
        )
        val existing = when {
            !channelId.isNullOrBlank() -> box.query(ObxEpgNowNext_.channelId.equal(channelId)).build().findFirst()
            streamId != null -> box.query(ObxEpgNowNext_.streamId.equal(streamId.toLong())).build().findFirst()
            else -> null
        }
        if (existing != null) { row.id = existing.id }
        box.put(row)
    }

    // --- EPG prefetch for visible Live IDs (ultra performant, batched) ---
    suspend fun prefetchEpgForVisible(
        streamIds: List<Int>,
        perStreamLimit: Int = 2,
        parallelism: Int = 4
    ) = withContext(Dispatchers.IO) {
        if (streamIds.isEmpty()) return@withContext
        // Global gate + credentials guard: no Xtream access without valid creds
        val workers = runCatching { settings.m3uWorkersEnabled.first() }.getOrDefault(true)
        if (!workers) return@withContext
        val host = runCatching { settings.xtHost.first() }.getOrDefault("")
        val user = runCatching { settings.xtUser.first() }.getOrDefault("")
        val pass = runCatching { settings.xtPass.first() }.getOrDefault("")
        if (host.isBlank() || user.isBlank() || pass.isBlank()) return@withContext
        val client = newClient()
        val sem = Semaphore(parallelism)
        val box = ObxStore.get(context).boxFor<ObxEpgNowNext>()
        val rows = mutableListOf<ObxEpgNowNext>()
        coroutineScope {
            streamIds.distinct().forEach { sid ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        val json = runCatching { client.fetchShortEpg(sid, perStreamLimit) }.getOrNull()
                        val list = parseShortEpg(json)
                        if (list.isNotEmpty()) {
                            val now = list.getOrNull(0)
                            val next = list.getOrNull(1)
                            val obx = ObxEpgNowNext(
                                streamId = sid,
                                channelId = null,
                                nowTitle = now?.title,
                                nowStartMs = now?.start?.toLongOrNull()?.times(1000),
                                nowEndMs = now?.end?.toLongOrNull()?.times(1000),
                                nextTitle = next?.title,
                                nextStartMs = next?.start?.toLongOrNull()?.times(1000),
                                nextEndMs = next?.end?.toLongOrNull()?.times(1000),
                                updatedAt = System.currentTimeMillis()
                            )
                            synchronized(rows) { rows += obx }
                        }
                    }
                }
            }
        }
        if (rows.isNotEmpty()) {
            val existingBySid = box.query(ObxEpgNowNext_.streamId.greater(0)).build().find().associateBy { it.streamId }
            rows.forEach { r -> existingBySid[r.streamId]?.let { r.id = it.id } }
            box.put(rows) // klein (<= 50), kein Chunk nötig
        }
    }

    private fun parseShortEpg(json: String?): List<XtShortEPGProgramme> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val root = kotlinx.serialization.json.Json.parseToJsonElement(json)
            val arr = root.jsonArray
            arr.mapNotNull { el ->
                val obj = el.jsonObject
                XtShortEPGProgramme(
                    title = obj["title"]?.jsonPrimitive?.contentOrNull,
                    start = obj["start"]?.jsonPrimitive?.contentOrNull,
                    end = obj["end"]?.jsonPrimitive?.contentOrNull,
                )
            }
        } catch (_: Throwable) { emptyList() }
    }

    // --- Paged queries (for later UI wiring) ---
    suspend fun categories(kind: String): List<ObxCategory> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxCategory>()
            .query(ObxCategory_.kind.equal(kind))
            .order(ObxCategory_.categoryName)
            .build().find()
    }

    suspend fun livePaged(offset: Long, limit: Long): List<ObxLive> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxLive>()
            .query()
            .order(ObxLive_.sortTitleLower)
            .build().find(offset, limit)
    }
    suspend fun liveByCategoryPaged(categoryId: String, offset: Long, limit: Long): List<ObxLive> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxLive>()
            .query(ObxLive_.categoryId.equal(categoryId))
            .order(ObxLive_.sortTitleLower)
            .build().find(offset, limit)
    }
    suspend fun liveByProviderKeyPaged(key: String, offset: Long, limit: Long): List<ObxLive> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxLive>()
            .query(ObxLive_.providerKey.equal(key))
            .order(ObxLive_.nameLower)
            .build().find(offset, limit)
    }
    suspend fun liveByGenreKeyPaged(key: String, offset: Long, limit: Long): List<ObxLive> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxLive>()
            .query(ObxLive_.genreKey.equal(key))
            .order(ObxLive_.nameLower)
            .build().find(offset, limit)
    }

    suspend fun vodPaged(offset: Long, limit: Long): List<ObxVod> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxVod>()
            .query()
            .order(ObxVod_.sortTitleLower)
            .build().find(offset, limit)
    }
    suspend fun vodPagedNewest(offset: Long, limit: Long): List<ObxVod> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxVod>()
            .query()
            .orderDesc(ObxVod_.importedAt)
            .orderDesc(ObxVod_.yearKey)
            .order(ObxVod_.sortTitleLower)
            .build().find(offset, limit)
    }
    suspend fun vodByCategoryPaged(categoryId: String, offset: Long, limit: Long): List<ObxVod> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxVod>()
            .query(ObxVod_.categoryId.equal(categoryId))
            .order(ObxVod_.sortTitleLower)
            .build().find(offset, limit)
    }
    suspend fun vodByYearKeyPaged(year: Int, offset: Long, limit: Long): List<ObxVod> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxVod>()
            .query(ObxVod_.yearKey.equal(year))
            .order(ObxVod_.sortTitleLower)
            .build().find(offset, limit)
    }
    suspend fun vodByProviderKeyPaged(key: String, offset: Long, limit: Long): List<ObxVod> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxVod>()
            .query(ObxVod_.providerKey.equal(key))
            .order(ObxVod_.nameLower)
            .build().find(offset, limit)
    }
    suspend fun vodByProviderKeyNewest(key: String, offset: Long, limit: Long): List<ObxVod> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxVod>()
            .query(ObxVod_.providerKey.equal(key))
            .orderDesc(ObxVod_.importedAt)
            .orderDesc(ObxVod_.yearKey)
            .order(ObxVod_.sortTitleLower)
            .build().find(offset, limit)
    }
    suspend fun vodByGenreKeyPaged(key: String, offset: Long, limit: Long): List<ObxVod> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxVod>()
            .query(ObxVod_.genreKey.equal(key))
            .order(ObxVod_.nameLower)
            .build().find(offset, limit)
    }

    suspend fun vodYearKeys(): List<Int> = withContext(Dispatchers.IO) {
        val box = ObxStore.get(context).boxFor<ObxVod>()
        val rows = box.query(ObxVod_.yearKey.greater(0)).orderDesc(ObxVod_.yearKey).build().find()
        rows.mapNotNull { it.yearKey }.fold(mutableListOf<Int>()) { acc, v -> if (acc.lastOrNull() != v) acc.add(v); acc }
    }

    suspend fun seriesPaged(offset: Long, limit: Long): List<ObxSeries> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxSeries>()
            .query()
            .order(ObxSeries_.sortTitleLower)
            .build().find(offset, limit)
    }
    suspend fun seriesPagedNewest(offset: Long, limit: Long): List<ObxSeries> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxSeries>()
            .query()
            .orderDesc(ObxSeries_.importedAt)
            .orderDesc(ObxSeries_.yearKey)
            .order(ObxSeries_.sortTitleLower)
            .build().find(offset, limit)
    }
    suspend fun seriesByCategoryPaged(categoryId: String, offset: Long, limit: Long): List<ObxSeries> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxSeries>()
            .query(ObxSeries_.categoryId.equal(categoryId))
            .order(ObxSeries_.sortTitleLower)
            .build().find(offset, limit)
    }
    suspend fun seriesByYearKeyPaged(year: Int, offset: Long, limit: Long): List<ObxSeries> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxSeries>()
            .query(ObxSeries_.yearKey.equal(year))
            .order(ObxSeries_.sortTitleLower)
            .build().find(offset, limit)
    }
    suspend fun seriesByProviderKeyPaged(key: String, offset: Long, limit: Long): List<ObxSeries> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxSeries>()
            .query(ObxSeries_.providerKey.equal(key))
            .order(ObxSeries_.nameLower)
            .build().find(offset, limit)
    }
    suspend fun seriesByProviderKeyNewest(key: String, offset: Long, limit: Long): List<ObxSeries> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxSeries>()
            .query(ObxSeries_.providerKey.equal(key))
            .orderDesc(ObxSeries_.importedAt)
            .orderDesc(ObxSeries_.yearKey)
            .order(ObxSeries_.sortTitleLower)
            .build().find(offset, limit)
    }
    suspend fun seriesByGenreKeyPaged(key: String, offset: Long, limit: Long): List<ObxSeries> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxSeries>()
            .query(ObxSeries_.genreKey.equal(key))
            .order(ObxSeries_.nameLower)
            .build().find(offset, limit)
    }

    suspend fun seriesYearKeys(): List<Int> = withContext(Dispatchers.IO) {
        val box = ObxStore.get(context).boxFor<ObxSeries>()
        val rows = box.query(ObxSeries_.yearKey.greater(0)).orderDesc(ObxSeries_.yearKey).build().find()
        rows.mapNotNull { it.yearKey }.fold(mutableListOf<Int>()) { acc, v -> if (acc.lastOrNull() != v) acc.add(v); acc }
    }

    // Counts (for category sheet badges)
    suspend fun countLiveByCategory(categoryId: String): Long = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxLive>().query(ObxLive_.categoryId.equal(categoryId)).build().count()
    }
    suspend fun countVodByCategory(categoryId: String): Long = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxVod>().query(ObxVod_.categoryId.equal(categoryId)).build().count()
    }
    suspend fun countSeriesByCategory(categoryId: String): Long = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxSeries>().query(ObxSeries_.categoryId.equal(categoryId)).build().count()
    }

    suspend fun episodesForSeries(seriesId: Int): List<ObxEpisode> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxEpisode>()
            .query(ObxEpisode_.seriesId.equal(seriesId.toLong()))
            .order(ObxEpisode_.season).order(ObxEpisode_.episodeNum)
            .build().find()
    }

    // --- Search (ObjectBox-backed, case-insensitive contains) ---
    private suspend fun categoryIdsMatching(kind: String, qLower: String): Set<String> = withContext(Dispatchers.IO) {
        categories(kind).mapNotNull { it.categoryId.takeIf { _ -> (it.categoryName ?: "").lowercase().contains(qLower) } }.toSet()
    }
    suspend fun searchLiveByName(q: String, offset: Long, limit: Long): List<ObxLive> = withContext(Dispatchers.IO) {
        val ql = q.lowercase(); val box = ObxStore.get(context).boxFor<ObxLive>()
        val nameQ = box.query(ObxLive_.nameLower.contains(ql)).order(ObxLive_.nameLower).build()
        val nameCount = nameQ.count()
        val fromName = when {
            offset >= nameCount -> emptyList()
            else -> nameQ.find(offset, limit)
        }
        if (fromName.size >= limit) return@withContext fromName
        val need = (limit - fromName.size).toInt()
        val catIds = categoryIdsMatching("live", ql)
        if (catIds.isEmpty()) return@withContext fromName
        val catQ = box.query(ObxLive_.categoryId.oneOf(catIds.toTypedArray())).order(ObxLive_.nameLower).build()
        // fetch a bounded window to fill remainder
        val extra = catQ.find(0, (need + 200).toLong()).filterNot { x -> fromName.any { it.streamId == x.streamId } }
        (fromName + extra.take(need)).distinctBy { it.streamId }
    }
    suspend fun searchVodByName(q: String, offset: Long, limit: Long): List<ObxVod> = withContext(Dispatchers.IO) {
        val ql = q.lowercase(); val box = ObxStore.get(context).boxFor<ObxVod>()
        val nameQ = box.query(ObxVod_.nameLower.contains(ql)).order(ObxVod_.nameLower).build()
        val nameCount = nameQ.count()
        val fromName = when {
            offset >= nameCount -> emptyList()
            else -> nameQ.find(offset, limit)
        }
        if (fromName.size >= limit) return@withContext fromName
        val need = (limit - fromName.size).toInt()
        val catIds = categoryIdsMatching("vod", ql)
        if (catIds.isEmpty()) return@withContext fromName
        val catQ = box.query(ObxVod_.categoryId.oneOf(catIds.toTypedArray())).order(ObxVod_.nameLower).build()
        val extra = catQ.find(0, (need + 200).toLong()).filterNot { x -> fromName.any { it.vodId == x.vodId } }
        (fromName + extra.take(need)).distinctBy { it.vodId }
    }
    suspend fun searchSeriesByName(q: String, offset: Long, limit: Long): List<ObxSeries> = withContext(Dispatchers.IO) {
        val ql = q.lowercase(); val box = ObxStore.get(context).boxFor<ObxSeries>()
        val nameQ = box.query(ObxSeries_.nameLower.contains(ql)).order(ObxSeries_.nameLower).build()
        val nameCount = nameQ.count()
        val fromName = when {
            offset >= nameCount -> emptyList()
            else -> nameQ.find(offset, limit)
        }
        if (fromName.size >= limit) return@withContext fromName
        val need = (limit - fromName.size).toInt()
        val catIds = categoryIdsMatching("series", ql)
        if (catIds.isEmpty()) return@withContext fromName
        val catQ = box.query(ObxSeries_.categoryId.oneOf(catIds.toTypedArray())).order(ObxSeries_.nameLower).build()
        val extra = catQ.find(0, (need + 200).toLong()).filterNot { x -> fromName.any { it.seriesId == x.seriesId } }
        (fromName + extra.take(need)).distinctBy { it.seriesId }
    }

    // --- Distinct key lists (for grouped headers without in-memory scans) ---
    suspend fun indexProviderKeys(kind: String): List<String> = withContext(Dispatchers.IO) {
        val box = ObxStore.get(context).boxFor<ObxIndexProvider>()
        val rows = box.query(com.chris.m3usuite.data.obx.ObxIndexProvider_.kind.equal(kind)).build().find()
        if (rows.isEmpty()) return@withContext emptyList<String>()
        val sorted = rows.sortedWith(compareByDescending<com.chris.m3usuite.data.obx.ObxIndexProvider> { it.count }.thenBy { it.key })
        val (others, rest) = sorted.partition { it.key == "other" }
        val filteredRest = rest.map { it.key }
        val otherKey = others.firstOrNull()?.takeIf { it.count >= 10 }?.key
        if (otherKey != null) filteredRest + otherKey else filteredRest
    }
    suspend fun indexGenreKeys(kind: String): List<String> = withContext(Dispatchers.IO) {
        val box = ObxStore.get(context).boxFor<ObxIndexGenre>()
        val rows = box.query(com.chris.m3usuite.data.obx.ObxIndexGenre_.kind.equal(kind)).build()
            .find().sortedWith(compareByDescending<com.chris.m3usuite.data.obx.ObxIndexGenre> { it.count }.thenBy { it.key })
        if (rows.isNotEmpty()) return@withContext rows.map { it.key }
        // Fallback: when index not built yet but content exists, return a minimal bucket so UI shows at least "Unkategorisiert"
        val hasContent = when (kind) {
            "vod" -> ObxStore.get(context).boxFor(ObxVod::class.java).count() > 0
            "series" -> ObxStore.get(context).boxFor(ObxSeries::class.java).count() > 0
            "live" -> ObxStore.get(context).boxFor(ObxLive::class.java).count() > 0
            else -> false
        }
        if (hasContent) listOf("other") else emptyList()
    }
    suspend fun indexYearKeys(kind: String): List<Int> = withContext(Dispatchers.IO) {
        val box = ObxStore.get(context).boxFor<ObxIndexYear>()
        box.query(com.chris.m3usuite.data.obx.ObxIndexYear_.kind.equal(kind)).build()
            .find().sortedWith(compareByDescending<com.chris.m3usuite.data.obx.ObxIndexYear> { it.key }.thenByDescending { it.count })
            .map { it.key }
    }
    suspend fun liveProviderKeys(): List<String> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxLive>().query().build().property(ObxLive_.providerKey).distinct().findStrings().filterNotNull().filter { it.isNotBlank() }.sorted()
    }
    suspend fun liveGenreKeys(): List<String> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxLive>().query().build().property(ObxLive_.genreKey).distinct().findStrings().filterNotNull().filter { it.isNotBlank() }.sorted()
    }
    suspend fun vodProviderKeys(): List<String> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxVod>().query().build().property(ObxVod_.providerKey).distinct().findStrings().filterNotNull().filter { it.isNotBlank() }.sorted()
    }
    suspend fun vodGenreKeys(): List<String> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxVod>().query().build().property(ObxVod_.genreKey).distinct().findStrings().filterNotNull().filter { it.isNotBlank() }.sorted()
    }
    suspend fun vodYears(): List<Int> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxVod>().query().build().property(ObxVod_.yearKey).distinct().findInts().toList().sortedDescending()
    }
    suspend fun seriesProviderKeys(): List<String> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxSeries>().query().build().property(ObxSeries_.providerKey).distinct().findStrings().filterNotNull().filter { it.isNotBlank() }.sorted()
    }
    suspend fun seriesGenreKeys(): List<String> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxSeries>().query().build().property(ObxSeries_.genreKey).distinct().findStrings().filterNotNull().filter { it.isNotBlank() }.sorted()
    }
    suspend fun seriesYears(): List<Int> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxSeries>().query().build().property(ObxSeries_.yearKey).distinct().findInts().toList().sortedDescending()
    }

    suspend fun rebuildIndexes(): Unit = withContext(Dispatchers.IO) {
        rebuildAggregatedIndexes(ObxStore.get(context))
    }

    private fun rebuildAggregatedIndexes(boxStore: BoxStore) {
        val providerIndexBox = boxStore.boxFor<ObxIndexProvider>()
        val genreIndexBox = boxStore.boxFor<ObxIndexGenre>()
        val yearIndexBox = boxStore.boxFor<ObxIndexYear>()
        val liveBox = boxStore.boxFor<ObxLive>()
        val vodBox = boxStore.boxFor<ObxVod>()
        val seriesBox = boxStore.boxFor<ObxSeries>()

        updateProviderIndex(
            kind = "live",
            counts = collectStringCounts(liveBox, ObxLive_.providerKey) { raw -> raw?.takeUnless { it.isBlank() } ?: "other" },
            box = providerIndexBox
        )
        updateGenreIndex(
            kind = "live",
            counts = collectStringCounts(liveBox, ObxLive_.genreKey) { raw -> raw?.takeUnless { it.isBlank() } ?: "other" },
            box = genreIndexBox
        )

        updateProviderIndex(
            kind = "vod",
            counts = collectStringCounts(vodBox, ObxVod_.providerKey) { raw -> raw?.takeUnless { it.isBlank() } ?: "other" },
            box = providerIndexBox
        )
        // VOD genres: exclude adult content from the aggregated genre index so Adults stay only in the Adults umbrella
        run {
            val counts = mutableMapOf<String, Long>()
            val q = vodBox.query().build()
            val rows = try { q.find() } finally { q.close() }
            rows.forEach { r ->
                val prov = (r as ObxVod).providerKey ?: ""
                if (prov.startsWith("adult_")) return@forEach
                val key = r.genreKey?.takeUnless { it.isBlank() } ?: "other"
                counts[key] = (counts[key] ?: 0L) + 1L
            }
            updateGenreIndex(
                kind = "vod",
                counts = counts,
                box = genreIndexBox
            )
        }
        updateYearIndex(
            kind = "vod",
            counts = collectIntCounts(vodBox, ObxVod_.yearKey),
            box = yearIndexBox
        )

        updateProviderIndex(
            kind = "series",
            counts = collectStringCounts(seriesBox, ObxSeries_.providerKey) { raw -> raw?.takeUnless { it.isBlank() } ?: "other" },
            box = providerIndexBox
        )
        updateGenreIndex(
            kind = "series",
            counts = collectStringCounts(seriesBox, ObxSeries_.genreKey) { raw -> raw?.takeUnless { it.isBlank() } ?: "other" },
            box = genreIndexBox
        )
        updateYearIndex(
            kind = "series",
            counts = collectIntCounts(seriesBox, ObxSeries_.yearKey),
            box = yearIndexBox
        )
    }

    private fun updateProviderIndex(kind: String, counts: Map<String, Long>, box: Box<ObxIndexProvider>) {
        val query = box.query(com.chris.m3usuite.data.obx.ObxIndexProvider_.kind.equal(kind)).build()
        val existing = try { query.find() } finally { query.close() }
        if (counts.isEmpty()) {
            if (existing.isNotEmpty()) box.remove(existing)
            return
        }
        val existingByKey = existing.associateBy { it.key }
        val rows = counts.map { (key, count) ->
            val row = existingByKey[key]
            if (row != null) {
                row.count = count
                row
            } else {
                ObxIndexProvider(kind = kind, key = key, count = count)
            }
        }
        val toRemove = existing.filter { it.key !in counts.keys }
        if (toRemove.isNotEmpty()) box.remove(toRemove)
        if (rows.isNotEmpty()) box.putChunked(rows, 2000)
    }

    private fun updateGenreIndex(kind: String, counts: Map<String, Long>, box: Box<ObxIndexGenre>) {
        val query = box.query(com.chris.m3usuite.data.obx.ObxIndexGenre_.kind.equal(kind)).build()
        val existing = try { query.find() } finally { query.close() }
        if (counts.isEmpty()) {
            if (existing.isNotEmpty()) box.remove(existing)
            return
        }
        val existingByKey = existing.associateBy { it.key }
        val rows = counts.map { (key, count) ->
            val row = existingByKey[key]
            if (row != null) {
                row.count = count
                row
            } else {
                ObxIndexGenre(kind = kind, key = key, count = count)
            }
        }
        val toRemove = existing.filter { it.key !in counts.keys }
        if (toRemove.isNotEmpty()) box.remove(toRemove)
        if (rows.isNotEmpty()) box.putChunked(rows, 2000)
    }

    private fun updateYearIndex(kind: String, counts: Map<Int, Long>, box: Box<ObxIndexYear>) {
        val query = box.query(com.chris.m3usuite.data.obx.ObxIndexYear_.kind.equal(kind)).build()
        val existing = try { query.find() } finally { query.close() }
        if (counts.isEmpty()) {
            if (existing.isNotEmpty()) box.remove(existing)
            return
        }
        val existingByKey = existing.associateBy { it.key }
        val rows = counts.map { (key, count) ->
            val row = existingByKey[key]
            if (row != null) {
                row.count = count
                row
            } else {
                ObxIndexYear(kind = kind, key = key, count = count)
            }
        }
        val toRemove = existing.filter { it.key !in counts.keys }
        if (toRemove.isNotEmpty()) box.remove(toRemove)
        if (rows.isNotEmpty()) box.putChunked(rows, 2000)
    }

    private fun <E : Any> collectStringCounts(
        box: Box<E>,
        property: Property<E>,
        transform: (String?) -> String
    ): Map<String, Long> {
        val query = box.query().build()
        val values = try {
            query.property(property).findStrings() ?: emptyArray()
        } finally {
            query.close()
        }
        if (values.isEmpty()) return emptyMap()
        val counts = mutableMapOf<String, Long>()
        values.forEach { raw ->
            val key = transform(raw)
            counts[key] = (counts[key] ?: 0L) + 1L
        }
        return counts
    }

    private fun <E : Any> collectIntCounts(box: Box<E>, property: Property<E>): Map<Int, Long> {
        val query = box.query().build()
        val values = try {
            query.property(property).findInts() ?: IntArray(0)
        } finally {
            query.close()
        }
        if (values.isEmpty()) return emptyMap()
        val counts = mutableMapOf<Int, Long>()
        values.forEach { value ->
            val key = value.takeIf { it > 0 } ?: return@forEach
            counts[key] = (counts[key] ?: 0L) + 1L
        }
        return counts
    }

    // ---- year-based helpers ----

    suspend fun vodByYearsNewest(years: IntArray, offset: Long, limit: Long): List<ObxVod> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxVod>()
            .query(ObxVod_.yearKey.oneOf(years))
            .orderDesc(ObxVod_.importedAt)
            .orderDesc(ObxVod_.yearKey)
            .order(ObxVod_.sortTitleLower)
            .build().find(offset, limit)
    }

    suspend fun seriesByYearsNewest(years: IntArray, offset: Long, limit: Long): List<ObxSeries> = withContext(Dispatchers.IO) {
        ObxStore.get(context).boxFor<ObxSeries>()
            .query(ObxSeries_.yearKey.oneOf(years))
            .orderDesc(ObxSeries_.importedAt)
            .orderDesc(ObxSeries_.yearKey)
            .order(ObxSeries_.sortTitleLower)
            .build().find(offset, limit)
    }

    // ---- helpers ----
    private fun upsertCategories(catBox: Box<ObxCategory>, kind: String, desired: Map<String, String>, deleteOrphans: Boolean) {
        val existing = catBox.query(ObxCategory_.kind.equal(kind)).build().find()
        val existingById = existing.associateBy { it.categoryId.orEmpty() }
        val toUpsert = mutableListOf<ObxCategory>()
        desired.forEach { (id, name) ->
            val ex = existingById[id]
            if (ex == null) {
                toUpsert += ObxCategory(kind = kind, categoryId = id, categoryName = name)
            } else if ((ex.categoryName ?: "") != name) {
                toUpsert += ex.copy(categoryName = name)
            }
        }
        if (toUpsert.isNotEmpty()) catBox.putChunked(toUpsert, 2000)
        if (deleteOrphans) {
            val toRemove = existing.filter { it.categoryId.orEmpty() !in desired.keys }
            if (toRemove.isNotEmpty()) catBox.remove(toRemove)
        }
    }
}

private fun <T> Box<T>.putChunked(items: List<T>, chunkSize: Int = 2000) {
    var i = 0
    val n = items.size
    while (i < n) {
        val to = min(i + chunkSize, n)
        this.put(items.subList(i, to))
        i = to
    }
}
