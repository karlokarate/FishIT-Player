package com.fishit.player.core.persistence.inspector

import android.content.Context
import android.net.Uri
import com.fishit.player.core.persistence.obx.NX_Category
import com.fishit.player.core.persistence.obx.NX_IngestLedger
import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.NX_WorkCategoryRef
import com.fishit.player.core.persistence.obx.NX_WorkEmbedding
import com.fishit.player.core.persistence.obx.NX_WorkRelation
import com.fishit.player.core.persistence.obx.NX_WorkRuntimeState
import com.fishit.player.core.persistence.obx.NX_WorkSourceRef
import com.fishit.player.core.persistence.obx.NX_WorkUserState
import com.fishit.player.core.persistence.obx.NX_WorkVariant
import com.fishit.player.core.persistence.obx.NX_Work_
import com.fishit.player.core.persistence.obx.NX_WorkCategoryRef_
import com.fishit.player.core.persistence.obx.NX_WorkEmbedding_
import com.fishit.player.core.persistence.obx.NX_WorkRelation_
import com.fishit.player.core.persistence.obx.NX_WorkRuntimeState_
import com.fishit.player.core.persistence.obx.NX_WorkSourceRef_
import com.fishit.player.core.persistence.obx.NX_WorkUserState_
import com.fishit.player.core.persistence.obx.NX_WorkVariant_
import com.fishit.player.core.persistence.obx.NX_IngestLedger_
import com.fishit.player.infra.logging.UnifiedLog
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation backed by a live [BoxStore].
 */
@Singleton
class DefaultObxDatabaseInspector
    @Inject
    constructor(
        private val boxStore: BoxStore,
    ) : ObxDatabaseInspector {
        companion object {
            private const val TAG = "ObxDatabaseInspector"

            private val TITLE_CANDIDATES =
                listOf(
                    "canonicalTitle",
                    "canonicalKey",
                    "name",
                    "title",
                    "seriesName",
                    "episodeTitle",
                    "categoryName",
                    "sourceId",
                    "key",
                    "fileName",
                    "caption",
                )

            private val SUBTITLE_CANDIDATES =
                listOf(
                    "year",
                    "kind",
                    "tmdbId",
                    "imdbId",
                    "providerKey",
                    "genreKey",
                    "categoryId",
                    "chatId",
                    "messageId",
                    "remoteId",
                    "sourceType",
                )
        }

        override suspend fun listEntityTypes(): List<DbEntityTypeInfo> =
            withContext(Dispatchers.IO) {
                ObxInspectorEntityRegistry.all
                    .map { spec ->
                        val count = runCatching { boxStore.boxFor(spec.clazz).count() }.getOrElse { 0L }
                        DbEntityTypeInfo(
                            id = spec.id,
                            displayName = spec.displayName,
                            count = count,
                        )
                    }.sortedBy { it.displayName }
            }

        override suspend fun listRows(
            entityTypeId: String,
            offset: Long,
            limit: Long,
        ): DbPage<DbRowPreview> =
            withContext(Dispatchers.IO) {
                val spec =
                    ObxInspectorEntityRegistry.byId[entityTypeId]
                        ?: return@withContext DbPage(items = emptyList(), offset = offset, limit = limit, total = 0)

                val box = boxStore.boxFor(spec.clazz)
                val total = runCatching { box.count() }.getOrElse { 0L }

                val query = box.query().build()
                val entities =
                    try {
                        query.find(offset, limit)
                    } finally {
                        runCatching { query.close() }
                    }

                val previews =
                    entities.mapNotNull { entity ->
                        val id = readIdOrNull(entity) ?: return@mapNotNull null
                        DbRowPreview(
                            id = id,
                            title = buildTitle(entity),
                            subtitle = buildSubtitle(entity),
                        )
                    }

                DbPage(
                    items = previews,
                    offset = offset,
                    limit = limit,
                    total = total,
                )
            }

        override suspend fun getEntity(
            entityTypeId: String,
            id: Long,
        ): DbEntityDump? =
            withContext(Dispatchers.IO) {
                val spec = ObxInspectorEntityRegistry.byId[entityTypeId] ?: return@withContext null
                val box = boxStore.boxFor(spec.clazz)
                val entity = runCatching { box.get(id) }.getOrNull() ?: return@withContext null
                DbEntityDump(
                    entityTypeId = entityTypeId,
                    id = id,
                    fields = dumpFields(entity),
                )
            }

        override suspend fun updateFields(
            entityTypeId: String,
            id: Long,
            patch: Map<String, String?>,
        ): DbUpdateResult =
            withContext(Dispatchers.IO) {
                val spec =
                    ObxInspectorEntityRegistry.byId[entityTypeId]
                        ?: return@withContext DbUpdateResult(
                            entityTypeId = entityTypeId,
                            id = id,
                            applied = 0,
                            errors = listOf("Unknown entity type: $entityTypeId"),
                        )

                val box = boxStore.boxFor(spec.clazz)
                val entity =
                    runCatching { box.get(id) }.getOrNull()
                        ?: return@withContext DbUpdateResult(
                            entityTypeId = entityTypeId,
                            id = id,
                            applied = 0,
                            errors = listOf("Row not found (id=$id)"),
                        )

                val errors = mutableListOf<String>()
                var applied = 0

                val editableFields =
                    entity.javaClass.declaredFields
                        .filterNot { it.isSynthetic }
                        .associateBy { it.name }

                patch.forEach { (fieldName, rawValue) ->
                    if (fieldName == "id") {
                        errors += "Field 'id' is not editable"
                        return@forEach
                    }

                    val field = editableFields[fieldName]
                    if (field == null) {
                        errors += "Unknown field: $fieldName"
                        return@forEach
                    }

                    if (!isEditableField(field)) {
                        errors += "Field '$fieldName' is not editable"
                        return@forEach
                    }

                    val parsed = parseStringToFieldType(field, rawValue)
                    if (parsed is ParseResult.Error) {
                        errors += parsed.message
                        return@forEach
                    }

                    runCatching {
                        field.isAccessible = true
                        field.set(entity, (parsed as ParseResult.Ok).value)
                    }.onSuccess {
                        applied += 1
                    }.onFailure { t ->
                        errors += "Failed to set '$fieldName': ${t.message}"
                    }
                }

                if (applied > 0) {
                    runCatching {
                        @Suppress("UNCHECKED_CAST")
                        (box as io.objectbox.Box<Any>).put(entity)
                        UnifiedLog.i(TAG) { "Updated $entityTypeId(id=$id): applied=$applied, errors=${errors.size}" }
                    }.onFailure { t ->
                        UnifiedLog.e(TAG, t) { "Failed to persist update for $entityTypeId(id=$id)" }
                        errors += "Persist failed: ${t.message}"
                    }
                }

                DbUpdateResult(
                    entityTypeId = entityTypeId,
                    id = id,
                    applied = applied,
                    errors = errors,
                )
            }

        override suspend fun deleteEntity(
            entityTypeId: String,
            id: Long,
        ): Boolean =
            withContext(Dispatchers.IO) {
                val spec = ObxInspectorEntityRegistry.byId[entityTypeId] ?: return@withContext false
                val box = boxStore.boxFor(spec.clazz)
                runCatching {
                    val ok = box.remove(id)
                    if (ok) {
                        UnifiedLog.w(TAG) { "Deleted $entityTypeId(id=$id)" }
                    }
                    ok
                }.getOrElse {
                    UnifiedLog.e(TAG, it) { "Failed to delete $entityTypeId(id=$id)" }
                    false
                }
            }

        // =========================================================================
        // Reflection helpers (Java reflection; avoids kotlin-reflect dependency)
        // =========================================================================

        private fun dumpFields(entity: Any): List<DbFieldValue> {
            val fields =
                entity.javaClass.declaredFields
                    .filterNot { it.isSynthetic }
                    .sortedBy { it.name }

            return fields.map { field ->
                field.isAccessible = true
                val value = runCatching { field.get(entity) }.getOrNull()
                val valueStr = value?.let { safeValueToString(it) }

                DbFieldValue(
                    name = field.name,
                    type = prettyType(field),
                    value = valueStr,
                    editable = field.name != "id" && isEditableField(field),
                    nullable = !field.type.isPrimitive,
                )
            }
        }

        private fun buildTitle(entity: Any): String {
            val cls = entity.javaClass
            for (name in TITLE_CANDIDATES) {
                val f = runCatching { cls.getDeclaredField(name) }.getOrNull() ?: continue
                f.isAccessible = true
                val v = runCatching { f.get(entity) }.getOrNull()
                val s = v?.toString()?.trim().orEmpty()
                if (s.isNotBlank()) return s.take(80)
            }
            // Fallback: class name + id
            val id = readIdOrNull(entity)
            return if (id != null) "${cls.simpleName}#$id" else cls.simpleName
        }

        private fun buildSubtitle(entity: Any): String? {
            val cls = entity.javaClass
            for (name in SUBTITLE_CANDIDATES) {
                val f = runCatching { cls.getDeclaredField(name) }.getOrNull() ?: continue
                f.isAccessible = true
                val v = runCatching { f.get(entity) }.getOrNull() ?: continue
                val s = v.toString().trim()
                if (s.isNotBlank()) return "$name=$s".take(120)
            }
            return null
        }

        private fun readIdOrNull(entity: Any): Long? {
            val f = runCatching { entity.javaClass.getDeclaredField("id") }.getOrNull() ?: return null
            f.isAccessible = true
            val v = runCatching { f.get(entity) }.getOrNull() ?: return null
            return when (v) {
                is Long -> v
                is Number -> v.toLong()
                else -> null
            }
        }

        private fun isEditableField(field: Field): Boolean {
            if (field.isSynthetic) return false
            if (Modifier.isFinal(field.modifiers)) return false

            // Allow editing only of safe scalar types.
            val t = field.type
            return t.isPrimitive ||
                t == java.lang.Long::class.java ||
                t == java.lang.Integer::class.java ||
                t == java.lang.Short::class.java ||
                t == java.lang.Byte::class.java ||
                t == java.lang.Double::class.java ||
                t == java.lang.Float::class.java ||
                t == java.lang.Boolean::class.java ||
                t == String::class.java
        }

        private sealed interface ParseResult {
            data class Ok(
                val value: Any?,
            ) : ParseResult

            data class Error(
                val message: String,
            ) : ParseResult
        }

        private fun parseStringToFieldType(
            field: Field,
            rawValue: String?,
        ): ParseResult {
            val type = field.type

            if (rawValue == null) {
                return if (type.isPrimitive) {
                    ParseResult.Error("Field '${field.name}' is not nullable")
                } else {
                    ParseResult.Ok(null)
                }
            }

            val trimmed = rawValue.trim()
            if (trimmed.isEmpty()) {
                return if (type.isPrimitive) {
                    ParseResult.Error("Field '${field.name}' cannot be blank")
                } else {
                    ParseResult.Ok(null)
                }
            }

            return try {
                when (type) {
                    java.lang.Long.TYPE, java.lang.Long::class.java -> ParseResult.Ok(trimmed.toLong())
                    java.lang.Integer.TYPE, java.lang.Integer::class.java -> ParseResult.Ok(trimmed.toInt())
                    java.lang.Short.TYPE, java.lang.Short::class.java -> ParseResult.Ok(trimmed.toShort())
                    java.lang.Byte.TYPE, java.lang.Byte::class.java -> ParseResult.Ok(trimmed.toByte())
                    java.lang.Double.TYPE, java.lang.Double::class.java -> ParseResult.Ok(trimmed.toDouble())
                    java.lang.Float.TYPE, java.lang.Float::class.java -> ParseResult.Ok(trimmed.toFloat())
                    java.lang.Boolean.TYPE, java.lang.Boolean::class.java -> {
                        val b =
                            when (trimmed.lowercase()) {
                                "true", "1", "yes", "y" -> true
                                "false", "0", "no", "n" -> false
                                else -> return ParseResult.Error("Field '${field.name}' expects boolean (true/false)")
                            }
                        ParseResult.Ok(b)
                    }
                    String::class.java -> ParseResult.Ok(trimmed)
                    else -> ParseResult.Error("Unsupported field type for '${field.name}': ${type.simpleName}")
                }
            } catch (t: Throwable) {
                ParseResult.Error("Failed to parse '${field.name}' as ${type.simpleName}: ${t.message}")
            }
        }

        private fun prettyType(field: Field): String {
            val t = field.type
            val base =
                when (t) {
                    java.lang.Long.TYPE, java.lang.Long::class.java -> "Long"
                    java.lang.Integer.TYPE, java.lang.Integer::class.java -> "Int"
                    java.lang.Short.TYPE, java.lang.Short::class.java -> "Short"
                    java.lang.Byte.TYPE, java.lang.Byte::class.java -> "Byte"
                    java.lang.Double.TYPE, java.lang.Double::class.java -> "Double"
                    java.lang.Float.TYPE, java.lang.Float::class.java -> "Float"
                    java.lang.Boolean.TYPE, java.lang.Boolean::class.java -> "Boolean"
                    String::class.java -> "String"
                    else -> t.simpleName
                }
            return if (t.isPrimitive) base else "$base?"
        }

        private fun safeValueToString(value: Any): String {
            // Avoid accidentally dumping huge blobs / relation graphs.
            val s = value.toString()
            return if (s.length <= 400) s else s.take(400) + "…"
        }

        override suspend fun exportSchema(
            context: android.content.Context,
            toLogcat: Boolean,
        ): String =
            withContext(Dispatchers.IO) {
                val dump = ObjectBoxIntrospectionDump.generateDump(boxStore)

                if (toLogcat) {
                    ObjectBoxIntrospectionDump.dumpToLogcat(dump)
                    "Logcat"
                } else {
                    val file = ObjectBoxIntrospectionDump.dumpToFile(context, dump)
                    file.absolutePath
                }
            }

        override suspend fun exportSchemaToUri(
            context: Context,
            uri: Uri,
        ): Long =
            withContext(Dispatchers.IO) {
                val dump = ObjectBoxIntrospectionDump.generateDump(boxStore)
                ObjectBoxIntrospectionDump.dumpToUri(context, uri, dump)
            }

        // =========================================================================
        // Work Graph Export Implementation
        // =========================================================================

        private val workGraphJson = Json {
            prettyPrint = true
            encodeDefaults = true
        }

        override suspend fun exportWorkGraph(workKey: String): DbWorkGraphExport? =
            withContext(Dispatchers.IO) {
                UnifiedLog.i(TAG) { "Exporting Work Graph for: $workKey" }

                // 1. Find the NX_Work by workKey
                val workBox = boxStore.boxFor(NX_Work::class.java)
                val work = workBox.query(NX_Work_.workKey.equal(workKey)).build().use { it.findFirst() }
                    ?: run {
                        UnifiedLog.w(TAG) { "Work not found: $workKey" }
                        return@withContext null
                    }

                val workDump = dumpEntity(work, "NX_Work")

                // 2. Find all NX_WorkSourceRef by work relation (using indexed workId)
                val sourceRefBox = boxStore.boxFor(NX_WorkSourceRef::class.java)
                val sourceRefs = sourceRefBox.query(NX_WorkSourceRef_.workId.equal(work.id)).build().use { it.find() }
                val sourceRefDumps = sourceRefs.map { dumpEntity(it, "NX_WorkSourceRef") }

                // 3. Find all NX_WorkVariant by work relation (using indexed workId)
                val variantBox = boxStore.boxFor(NX_WorkVariant::class.java)
                val variants = variantBox.query(NX_WorkVariant_.workId.equal(work.id)).build().use { it.find() }
                val variantDumps = variants.map { dumpEntity(it, "NX_WorkVariant") }

                // 4. Find all NX_WorkRelation (as parent or child) using indexed relation IDs
                val relationBox = boxStore.boxFor(NX_WorkRelation::class.java)
                // Query for parent relations (this work is the parent)
                val parentRelations = relationBox.query(NX_WorkRelation_.parentWorkId.equal(work.id)).build().use { it.find() }
                // Query for child relations (this work is the child)
                val childRelations = relationBox.query(NX_WorkRelation_.childWorkId.equal(work.id)).build().use { it.find() }
                
                val relationExports = mutableListOf<DbWorkRelationExport>()
                parentRelations.forEach { rel ->
                    val childWork = rel.childWork.target
                    relationExports.add(
                        DbWorkRelationExport(
                            relation = dumpEntity(rel, "NX_WorkRelation"),
                            direction = "PARENT",
                            relatedWorkKey = childWork?.workKey,
                        )
                    )
                }
                childRelations.forEach { rel ->
                    val parentWork = rel.parentWork.target
                    relationExports.add(
                        DbWorkRelationExport(
                            relation = dumpEntity(rel, "NX_WorkRelation"),
                            direction = "CHILD",
                            relatedWorkKey = parentWork?.workKey,
                        )
                    )
                }

                // 5. Find all NX_WorkUserState by workKey
                val userStateBox = boxStore.boxFor(NX_WorkUserState::class.java)
                val userStates = userStateBox.query(NX_WorkUserState_.workKey.equal(workKey)).build().use { it.find() }
                val userStateDumps = userStates.map { dumpEntity(it, "NX_WorkUserState") }

                // 6. Find all NX_WorkCategoryRef by workKey
                val categoryRefBox = boxStore.boxFor(NX_WorkCategoryRef::class.java)
                val categoryRefs = categoryRefBox.query(NX_WorkCategoryRef_.workKey.equal(workKey)).build().use { it.find() }
                val categoryDumps = categoryRefs.map { dumpEntity(it, "NX_WorkCategoryRef") }

                // 7. Find NX_IngestLedger entries by resultWorkKey
                val ledgerBox = boxStore.boxFor(NX_IngestLedger::class.java)
                val ledgerEntries = ledgerBox.query(NX_IngestLedger_.resultWorkKey.equal(workKey)).build().use { it.find() }
                val ledgerDumps = ledgerEntries.map { dumpEntity(it, "NX_IngestLedger") }

                // 8. Find NX_WorkEmbedding by workKey
                val embeddingBox = boxStore.boxFor(NX_WorkEmbedding::class.java)
                val embedding = embeddingBox.query(NX_WorkEmbedding_.workKey.equal(workKey)).build().use { it.findFirst() }
                val embeddingDump = embedding?.let { dumpEntity(it, "NX_WorkEmbedding") }

                // 9. Find NX_WorkRuntimeState by workKey
                val runtimeStateBox = boxStore.boxFor(NX_WorkRuntimeState::class.java)
                val runtimeStates = runtimeStateBox.query(NX_WorkRuntimeState_.workKey.equal(workKey)).build().use { it.find() }
                val runtimeStateDumps = runtimeStates.map { dumpEntity(it, "NX_WorkRuntimeState") }

                // Build the export
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }

                val export = DbWorkGraphExport(
                    exportedAt = dateFormat.format(Date()),
                    workKey = workKey,
                    work = workDump,
                    sourceRefs = sourceRefDumps,
                    variants = variantDumps,
                    relations = relationExports,
                    userStates = userStateDumps,
                    categories = categoryDumps,
                    ingestLedger = ledgerDumps,
                    embedding = embeddingDump,
                    runtimeStates = runtimeStateDumps,
                )

                UnifiedLog.i(TAG) {
                    "Work Graph export complete: ${sourceRefDumps.size} sources, " +
                        "${variantDumps.size} variants, ${relationExports.size} relations, " +
                        "${userStateDumps.size} userStates"
                }

                export
            }

        override suspend fun exportWorkGraphToUri(
            context: Context,
            uri: Uri,
            workKey: String,
        ): Long =
            withContext(Dispatchers.IO) {
                val export = exportWorkGraph(workKey) ?: return@withContext -1L
                val jsonString = workGraphExportToJson(export)
                ObjectBoxIntrospectionDump.writeJsonToUri(context, uri, jsonString)
            }

        override suspend fun exportWorkGraphToJson(workKey: String): String? =
            withContext(Dispatchers.IO) {
                val export = exportWorkGraph(workKey) ?: return@withContext null
                workGraphExportToJson(export)
            }

        /**
         * Convert DbWorkGraphExport to pretty-printed JSON string.
         */
        private fun workGraphExportToJson(export: DbWorkGraphExport): String {
            // Convert to JsonObject manually since DbWorkGraphExport is not @Serializable
            val jsonObject = JsonObject(
                mapOf(
                    "exportedAt" to JsonPrimitive(export.exportedAt),
                    "workKey" to JsonPrimitive(export.workKey),
                    "work" to entityDumpToJson(export.work),
                    "sourceRefs" to JsonArray(export.sourceRefs.map { entityDumpToJson(it) }),
                    "variants" to JsonArray(export.variants.map { entityDumpToJson(it) }),
                    "relations" to JsonArray(export.relations.map { relationExportToJson(it) }),
                    "userStates" to JsonArray(export.userStates.map { entityDumpToJson(it) }),
                    "categories" to JsonArray(export.categories.map { entityDumpToJson(it) }),
                    "ingestLedger" to JsonArray(export.ingestLedger.map { entityDumpToJson(it) }),
                    "embedding" to (export.embedding?.let { entityDumpToJson(it) } ?: JsonNull),
                    "runtimeStates" to JsonArray(export.runtimeStates.map { entityDumpToJson(it) }),
                )
            )
            return workGraphJson.encodeToString(jsonObject)
        }

        private fun entityDumpToJson(dump: DbEntityDump): JsonObject {
            val fieldsObj = JsonObject(
                dump.fields.associate { field ->
                    field.name to (field.value?.let { JsonPrimitive(it) } ?: JsonNull)
                }
            )
            return JsonObject(
                mapOf(
                    "entityType" to JsonPrimitive(dump.entityTypeId),
                    "id" to JsonPrimitive(dump.id),
                    "fields" to fieldsObj,
                )
            )
        }

        private fun relationExportToJson(rel: DbWorkRelationExport): JsonObject {
            return JsonObject(
                mapOf(
                    "direction" to JsonPrimitive(rel.direction),
                    "relatedWorkKey" to (rel.relatedWorkKey?.let { JsonPrimitive(it) } ?: JsonNull),
                    "relation" to entityDumpToJson(rel.relation),
                )
            )
        }

        /**
         * Generic entity dump helper - works for any ObjectBox entity.
         */
        private fun <T : Any> dumpEntity(entity: T, entityTypeId: String): DbEntityDump {
            val id = readIdOrNull(entity) ?: 0L
            return DbEntityDump(
                entityTypeId = entityTypeId,
                id = id,
                fields = dumpFields(entity),
            )
        }
    }
