package com.fishit.player.core.persistence.inspector

/**
 * Debug-only / power-user service for introspecting and editing ObjectBox data.
 *
 * ⚠️ This is intentionally "sharp". UI entry points must be hidden behind debug/developer
 * navigation. Do not surface this in normal user flows.
 */
interface ObxDatabaseInspector {
    /**
     * List all registered ObjectBox entity types (tables) with their row counts.
     */
    suspend fun listEntityTypes(): List<DbEntityTypeInfo>

    /**
     * Page through rows of a specific entity type.
     */
    suspend fun listRows(
        entityTypeId: String,
        offset: Long,
        limit: Long,
    ): DbPage<DbRowPreview>

    /**
     * Load one entity row by [id] and return a UI-friendly dump.
     */
    suspend fun getEntity(
        entityTypeId: String,
        id: Long,
    ): DbEntityDump?

    /**
     * Apply a patch to an entity row.
     *
     * @param patch Map of field name -> new string value. For nullable fields you may pass null
     *              to clear the value.
     */
    suspend fun updateFields(
        entityTypeId: String,
        id: Long,
        patch: Map<String, String?>,
    ): DbUpdateResult

    /** Delete a row by id. */
    suspend fun deleteEntity(
        entityTypeId: String,
        id: Long,
    ): Boolean

    /**
     * Export complete ObjectBox schema as JSON.
     *
     * @param toLogcat If true, dumps to logcat instead of returning file path
     * @return File path if exported to file, "Logcat" if dumped to logcat
     */
    suspend fun exportSchema(
        context: android.content.Context,
        toLogcat: Boolean = false,
    ): String

    /**
     * Export ObjectBox schema to a user-selected URI via SAF (Storage Access Framework).
     *
     * @param context Android context for content resolver access
     * @param uri User-selected destination URI from CreateDocument intent
     * @return Number of bytes written
     */
    suspend fun exportSchemaToUri(
        context: android.content.Context,
        uri: android.net.Uri,
    ): Long

    /**
     * Export a complete "Work Graph" for a given NX_Work entity.
     *
     * Collects the work and ALL related entities:
     * - NX_WorkSourceRef (all source references)
     * - NX_WorkVariant (all playback variants)
     * - NX_WorkRelation (parent/child relationships)
     * - NX_WorkUserState (user states for all profiles)
     * - NX_WorkCategoryRef (category links)
     * - NX_IngestLedger (audit trail)
     * - NX_WorkEmbedding (semantic embeddings, if any)
     * - NX_WorkRuntimeState (runtime states)
     *
     * @param workKey The workKey of the NX_Work to export (e.g., "MOVIE:inception:2010")
     * @return Complete work graph export, or null if work not found
     */
    suspend fun exportWorkGraph(workKey: String): DbWorkGraphExport?

    /**
     * Export a complete "Work Graph" to a user-selected URI via SAF.
     *
     * @param context Android context for content resolver access
     * @param uri User-selected destination URI from CreateDocument intent
     * @param workKey The workKey of the NX_Work to export
     * @return Number of bytes written, or -1 if work not found
     */
    suspend fun exportWorkGraphToUri(
        context: android.content.Context,
        uri: android.net.Uri,
        workKey: String,
    ): Long

    /**
     * Generate JSON string for a Work Graph export.
     *
     * Useful for clipboard copy or logcat dump.
     *
     * @param workKey The workKey of the NX_Work to export
     * @return JSON string, or null if work not found
     */
    suspend fun exportWorkGraphToJson(workKey: String): String?
}
