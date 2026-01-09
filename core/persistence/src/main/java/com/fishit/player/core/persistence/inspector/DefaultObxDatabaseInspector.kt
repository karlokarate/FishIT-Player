package com.fishit.player.core.persistence.inspector

import com.fishit.player.infra.logging.UnifiedLog
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.Field
import java.lang.reflect.Modifier
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
    }
