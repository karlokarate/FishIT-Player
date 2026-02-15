package com.fishit.player.core.persistence.inspector

import android.content.Context
import android.net.Uri
import com.fishit.player.infra.logging.UnifiedLog
import io.objectbox.BoxStore
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * DEBUG-ONLY Runtime introspection utility for ObjectBox schema extraction.
 *
 * This utility enumerates the complete ObjectBox schema at runtime and exports it
 * as JSON for debugging, migration analysis, and documentation purposes.
 *
 * **Usage:**
 * - Intended for debug builds only
 * - Accessible via DB Inspector UI
 * - Can dump to file (app-specific files dir), logcat, or share intent
 *
 * **Issue #612 Phase 2:** Complete ObjectBox schema introspection dump feature.
 */
object ObjectBoxIntrospectionDump {
    private const val TAG = "ObjectBoxIntrospectionDump"
    private const val MAX_LOGCAT_CHUNK = 3000 // Logcat line size limit

    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    /**
     * Generate a complete schema dump from the BoxStore.
     *
     * Enumerates all entities, properties, relations, and metadata available
     * via ObjectBox's runtime model.
     *
     * @param boxStore The active ObjectBox store
     * @return Structured schema dump ready for export
     */
    suspend fun generateDump(boxStore: BoxStore): ObjectBoxSchemaDump =
        withContext(Dispatchers.IO) {
            UnifiedLog.i(TAG) { "Starting schema introspection..." }

            val entities =
                ObxInspectorEntityRegistry.all.map { spec ->
                    try {
                        extractEntityDump(boxStore, spec)
                    } catch (e: Exception) {
                        UnifiedLog.e(TAG, e) { "Failed to extract entity: ${spec.id}" }
                        EntityDump(
                            name = spec.id,
                            displayName = spec.displayName,
                            entityId = 0L,
                            rowCount = 0L,
                            properties = emptyList(),
                            relations = emptyList(),
                            error = e.message ?: "Unknown error",
                        )
                    }
                }

            val dateFormat =
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
            val dump =
                ObjectBoxSchemaDump(
                    timestamp = dateFormat.format(Date()),
                    objectBoxVersion = getObjectBoxVersion(),
                    storeSize = 0L, // Store size not easily accessible via public API
                    entityCount = entities.size,
                    entities = entities,
                )

            UnifiedLog.i(TAG) { "Schema introspection complete: ${dump.entityCount} entities" }
            dump
        }

    /**
     * Write schema dump to app-specific files directory.
     *
     * File will be saved as: `/data/data/<package>/files/obx_dump_<timestamp>.json`
     * Extractable via: `adb pull /data/data/<package>/files/obx_dump_<timestamp>.json`
     *
     * @param context Android context for file access
     * @param dump Schema dump to write
     * @return File object for the written dump
     */
    suspend fun dumpToFile(
        context: Context,
        dump: ObjectBoxSchemaDump,
    ): File =
        withContext(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "obx_dump_$timestamp.json"
            val file = File(context.filesDir, fileName)

            val jsonString = json.encodeToString(dump)
            file.writeText(jsonString)

            UnifiedLog.i(TAG) { "Schema dump written to: ${file.absolutePath} (${file.length()} bytes)" }
            file
        }

    /**
     * Dump schema to logcat in chunks (for quick inspection).
     *
     * Large schemas will be split across multiple log statements to avoid
     * logcat truncation.
     *
     * @param dump Schema dump to log
     */
    suspend fun dumpToLogcat(dump: ObjectBoxSchemaDump) =
        withContext(Dispatchers.IO) {
            val jsonString = json.encodeToString(dump)
            UnifiedLog.i(TAG) { "=== ObjectBox Schema Dump START ===" }
            UnifiedLog.i(TAG) { "Timestamp: ${dump.timestamp}" }
            UnifiedLog.i(TAG) { "ObjectBox Version: ${dump.objectBoxVersion}" }
            UnifiedLog.i(TAG) { "Store Size: ${dump.storeSize} bytes" }
            UnifiedLog.i(TAG) { "Entity Count: ${dump.entityCount}" }

            // Chunk large JSON for logcat
            val chunks = jsonString.chunked(MAX_LOGCAT_CHUNK)
            chunks.forEachIndexed { index, chunk ->
                UnifiedLog.i(TAG) { "Schema JSON (chunk ${index + 1}/${chunks.size}):\n$chunk" }
            }

            UnifiedLog.i(TAG) { "=== ObjectBox Schema Dump END ===" }
        }

    /**
     * Write schema dump to a user-selected URI via SAF (Storage Access Framework).
     *
     * This allows users to save the export to any location they choose
     * (Downloads, Google Drive, etc.) without needing root access.
     *
     * @param context Android context for content resolver access
     * @param uri User-selected destination URI from CreateDocument intent
     * @param dump Schema dump to write
     * @return Number of bytes written
     */
    suspend fun dumpToUri(
        context: Context,
        uri: Uri,
        dump: ObjectBoxSchemaDump,
    ): Long =
        withContext(Dispatchers.IO) {
            val jsonString = json.encodeToString(dump)
            val bytes = jsonString.toByteArray(Charsets.UTF_8)

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(bytes)
                outputStream.flush()
            } ?: throw IllegalStateException("Failed to open output stream for URI: $uri")

            UnifiedLog.i(TAG) { "Schema dump written to URI: $uri (${bytes.size} bytes)" }
            bytes.size.toLong()
        }

    /**
     * Write any JSON string to a user-selected URI via SAF.
     *
     * Generic helper for Work Graph exports and other JSON data.
     *
     * @param context Android context for content resolver access
     * @param uri User-selected destination URI
     * @param jsonContent JSON string to write
     * @return Number of bytes written
     */
    suspend fun writeJsonToUri(
        context: Context,
        uri: Uri,
        jsonContent: String,
    ): Long =
        withContext(Dispatchers.IO) {
            val bytes = jsonContent.toByteArray(Charsets.UTF_8)

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(bytes)
                outputStream.flush()
            } ?: throw IllegalStateException("Failed to open output stream for URI: $uri")

            UnifiedLog.i(TAG) { "JSON written to URI: $uri (${bytes.size} bytes)" }
            bytes.size.toLong()
        }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    private fun extractEntityDump(
        boxStore: BoxStore,
        spec: ObxInspectorEntityRegistry.EntitySpec<out Any>,
    ): EntityDump {
        val box = boxStore.boxFor(spec.clazz)
        val rowCount = box.count()

        // Get entity ID from ObjectBox internal model if available
        val entityId =
            try {
                // ObjectBox stores entity info in the model, but it's not easily accessible
                // For now, use a hash of the class name as a stable identifier
                spec.clazz.name
                    .hashCode()
                    .toLong()
            } catch (e: Exception) {
                0L
            }

        val properties = mutableListOf<PropertyDump>()
        val relations = mutableListOf<RelationDump>()

        // Reflect on fields to extract property information
        spec.clazz.declaredFields
            .filterNot { it.isSynthetic }
            .forEach { field ->
                field.isAccessible = true

                when {
                    // Detect ToOne/ToMany relations
                    ToOne::class.java.isAssignableFrom(field.type) -> {
                        relations.add(extractToOneRelation(field))
                    }
                    ToMany::class.java.isAssignableFrom(field.type) -> {
                        relations.add(extractToManyRelation(field))
                    }
                    else -> {
                        properties.add(extractProperty(field))
                    }
                }
            }

        return EntityDump(
            name = spec.id,
            displayName = spec.displayName,
            entityId = entityId,
            rowCount = rowCount,
            properties = properties.sortedBy { it.name },
            relations = relations.sortedBy { it.name },
        )
    }

    private fun extractProperty(field: Field): PropertyDump {
        val annotations = field.annotations.map { it.annotationClass.simpleName ?: "Unknown" }

        return PropertyDump(
            name = field.name,
            type = field.type.simpleName,
            fullType = field.genericType.toString(),
            isIndexed = annotations.contains("Index"),
            isUnique = annotations.contains("Unique"),
            isId = annotations.contains("Id"),
            annotations = annotations,
        )
    }

    private fun extractToOneRelation(field: Field): RelationDump {
        val targetEntity =
            try {
                val paramType = field.genericType as? ParameterizedType
                val targetClass = paramType?.actualTypeArguments?.firstOrNull() as? Class<*>
                targetClass?.simpleName ?: "Unknown"
            } catch (e: Exception) {
                "Unknown"
            }

        return RelationDump(
            name = field.name,
            type = "ToOne",
            targetEntity = targetEntity,
        )
    }

    private fun extractToManyRelation(field: Field): RelationDump {
        val targetEntity =
            try {
                val paramType = field.genericType as? ParameterizedType
                val targetClass = paramType?.actualTypeArguments?.firstOrNull() as? Class<*>
                targetClass?.simpleName ?: "Unknown"
            } catch (e: Exception) {
                "Unknown"
            }

        return RelationDump(
            name = field.name,
            type = "ToMany",
            targetEntity = targetEntity,
        )
    }

    private fun getObjectBoxVersion(): String =
        try {
            // Try to get ObjectBox version from package info
            BoxStore::class.java.`package`?.implementationVersion ?: "5.0.1"
        } catch (e: Exception) {
            "Unknown"
        }
}

// =============================================================================
// Data Classes for Schema Dump
// =============================================================================

/**
 * Complete ObjectBox schema dump.
 */
@Serializable
data class ObjectBoxSchemaDump(
    /** ISO 8601 timestamp of when dump was generated */
    val timestamp: String,
    /** ObjectBox library version */
    val objectBoxVersion: String,
    /** Total database file size in bytes */
    val storeSize: Long,
    /** Total number of entities in schema */
    val entityCount: Int,
    /** List of all entity definitions */
    val entities: List<EntityDump>,
)

/**
 * Single entity (table) definition.
 */
@Serializable
data class EntityDump(
    /** Entity class name (e.g., "ObxVod") */
    val name: String,
    /** Human-friendly display name */
    val displayName: String,
    /** ObjectBox internal entity ID */
    val entityId: Long,
    /** Current row count in this table */
    val rowCount: Long,
    /** List of properties (columns) */
    val properties: List<PropertyDump>,
    /** List of relations (ToOne/ToMany) */
    val relations: List<RelationDump>,
    /** Error message if extraction failed */
    val error: String? = null,
)

/**
 * Single property (column) definition.
 */
@Serializable
data class PropertyDump(
    /** Property field name */
    val name: String,
    /** Simple type name (e.g., "String", "Int") */
    val type: String,
    /** Full generic type (e.g., "kotlin.String") */
    val fullType: String,
    /** Whether property has @Index annotation */
    val isIndexed: Boolean,
    /** Whether property has @Unique annotation */
    val isUnique: Boolean,
    /** Whether property is the @Id field */
    val isId: Boolean,
    /** List of all annotations on this property */
    val annotations: List<String>,
)

/**
 * Relation definition (ToOne/ToMany).
 */
@Serializable
data class RelationDump(
    /** Relation field name */
    val name: String,
    /** Relation type: "ToOne" or "ToMany" */
    val type: String,
    /** Target entity class name */
    val targetEntity: String,
)
