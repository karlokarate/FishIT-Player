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
    suspend fun exportSchema(context: android.content.Context, toLogcat: Boolean = false): String
}
