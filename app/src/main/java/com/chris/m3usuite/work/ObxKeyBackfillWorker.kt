package com.chris.m3usuite.work

import android.content.Context
import androidx.work.*
import com.chris.m3usuite.data.obx.*
import com.chris.m3usuite.data.obx.ObxStore
import io.objectbox.Box
import io.objectbox.query.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.min

class ObxKeyBackfillWorker(appContext: Context, params: WorkerParameters): CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val boxStore = ObxStore.get(applicationContext)
            val liveBox = boxStore.boxFor(ObxLive::class.java)
            val vodBox = boxStore.boxFor(ObxVod::class.java)
            val seriesBox = boxStore.boxFor(ObxSeries::class.java)

            fun sortKey(name: String?): String {
                var out = name.orEmpty().trim().lowercase()
                listOf("the ", "a ", "an ", "der ", "die ", "das ", "le ", "la ", "les ", "el ", "los ", "las ")
                    .forEach { if (out.startsWith(it)) out = out.removePrefix(it) }
                out = out.replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")
                return out
            }
            fun providerKeyFrom(kindCatId: String?, name: String?): String? {
                // Resolve category name from id when possible; fall back to name when category appears to be a country bucket
                val store = ObxStore.get(applicationContext)
                val catName: String? = runCatching {
                    kindCatId?.let {
                        store.boxFor(ObxCategory::class.java)
                            .query(ObxCategory_.categoryId.equal(it))
                            .build().findFirst()?.categoryName
                    }
                }.getOrNull()
                val useName = catName?.trim()?.matches(Regex("^[A-Z]{2,3}$")) == true
                val source = if (useName) name else (catName ?: name)
                // We don't know the kind here; resolve by heuristics from existing entity loop
                return source?.let { it.trim() }?.takeIf { it.isNotBlank() }
            }

            val catBox = boxStore.boxFor(ObxCategory::class.java)
            val catNameById: Map<String, String?> = run {
                val rows = catBox.query().build().find()
                rows.associateBy({ it.categoryId }, { it.categoryName })
            }
            fun genreKeyFromCategoryId(catId: String?): String? {
                val raw = catId?.let { catNameById[it] }?.lowercase().orEmpty()
                if (raw.isBlank()) return null
                fun has(vararg t: String) = t.any { raw.contains(it) }
                fun hasRe(re: Regex) = re.containsMatchIn(raw)
                if (has("4k", "uhd")) return "4k"
                if (has("filmreihe", "saga", "collection", "kollektion", "collectie", "marvel", "star wars", "james bond", "bruce lee", "terence hill", "bud spencer")) return "collection"
                if (has("show")) return "show"
                if (has("abenteuer") || hasRe(Regex("\\bavventur")) || hasRe(Regex("\\bavontuur\\b"))) return "adventure"
                if (has("drama")) return "drama"
                if (has("action", "azione")) return "action"
                if (has("komö", "komed", "comedy", "commedia")) return "comedy"
                if (has("kids", "kinder", "animation", "animazione")) return "kids"
                if (has("horror") || has("zombie", "zombies")) return "horror"
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
                return "other"
            }
            fun yearKeyFrom(explicit: Int?, name: String?): Int? {
                return explicit ?: com.chris.m3usuite.domain.selectors.extractYearFrom(name)
            }

            suspend fun <T> backfillPaged(
                total: Long,
                pageSize: Long,
                query: Query<T>,
                box: Box<T>,
                mutate: (T) -> Boolean,
                chunkSize: Int = 2000
            ) {
                var off = 0L
                while (off < total) {
                    val page = query.find(off, pageSize)
                    if (page.isEmpty()) break
                    val changed = ArrayList<T>(page.size)
                    for (e in page) {
                        if (mutate(e)) changed.add(e)
                    }
                    if (changed.isNotEmpty()) box.putChunked(changed, chunkSize)
                    off += page.size
                }
            }

            fun isBadProviderKey(k: String?): Boolean {
                if (k.isNullOrBlank()) return true
                if (k.all { it.isDigit() }) return true
                if (Regex("^[a-z]{2,3}$").matches(k)) return true // likely country code
                return false
            }

            // Live
            val liveQuery = liveBox.query().build()
            backfillPaged(
                total = liveBox.count(),
                pageSize = 5000,
                query = liveQuery,
                box = liveBox,
                mutate = { e ->
                    var changed = false
                    if (e.sortTitleLower.isBlank()) { e.sortTitleLower = sortKey(e.name); changed = true }
                    run {
                        val newKey = com.chris.m3usuite.core.util.CategoryNormalizer
                            .normalizeBucket("live", e.categoryId, e.name, null)
                        if (isBadProviderKey(e.providerKey) && newKey != e.providerKey) { e.providerKey = newKey; changed = true }
                    }
                    if (e.genreKey.isNullOrBlank()) { e.genreKey = genreKeyFromCategoryId(e.categoryId); changed = true }
                    changed
                }
            )

            // VOD
            val vodQuery = vodBox.query().build()
            backfillPaged(
                total = vodBox.count(),
                pageSize = 4000,
                query = vodQuery,
                box = vodBox,
                mutate = { e ->
                    var changed = false
                    if (e.sortTitleLower.isBlank()) { e.sortTitleLower = sortKey(e.name); changed = true }
                    run {
                        val newKey = com.chris.m3usuite.core.util.CategoryNormalizer
                            .normalizeBucket("vod", e.categoryId, e.name, null)
                        if (isBadProviderKey(e.providerKey) && newKey != e.providerKey) { e.providerKey = newKey; changed = true }
                    }
                    if (e.genreKey.isNullOrBlank()) { e.genreKey = genreKeyFromCategoryId(e.categoryId); changed = true }
                    if (e.yearKey == null) { e.yearKey = yearKeyFrom(e.year, e.name); changed = true }
                    changed
                }
            )

            // Series
            val seriesQuery = seriesBox.query().build()
            backfillPaged(
                total = seriesBox.count(),
                pageSize = 4000,
                query = seriesQuery,
                box = seriesBox,
                mutate = { e ->
                    var changed = false
                    if (e.sortTitleLower.isBlank()) { e.sortTitleLower = sortKey(e.name); changed = true }
                    run {
                        val newKey = com.chris.m3usuite.core.util.CategoryNormalizer
                            .normalizeBucket("series", e.categoryId, e.name, null)
                        if (isBadProviderKey(e.providerKey) && newKey != e.providerKey) { e.providerKey = newKey; changed = true }
                    }
                    if (e.genreKey.isNullOrBlank()) { e.genreKey = genreKeyFromCategoryId(e.categoryId); changed = true }
                    if (e.yearKey == null) { e.yearKey = yearKeyFrom(e.year, e.name); changed = true }
                    changed
                }
            )

            // Clean up thread-locals used by ObjectBox on this worker thread
            boxStore.closeThreadResources()
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE = "obx_key_backfill_once"
        fun scheduleOnce(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresCharging(true)
                .build()
            val req = OneTimeWorkRequestBuilder<ObxKeyBackfillWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.KEEP, req)
        }
    }
}

/** Chunked put to limit single-transaction size and surface progressive observer updates. */
private fun <T> Box<T>.putChunked(items: List<T>, chunkSize: Int = 2000) {
    var i = 0
    val n = items.size
    while (i < n) {
        val to = min(i + chunkSize, n)
        this.put(items.subList(i, to))
        i = to
    }
}
