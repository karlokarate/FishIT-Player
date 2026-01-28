package com.fishit.player.core.persistence.inspector

/**
 * Data models for the in-app ObjectBox database inspector.
 *
 * These are deliberately UI-friendly (stringly-typed values, explicit editability flags)
 * to keep the settings/debug UI decoupled from ObjectBox internals.
 */

/** A single entity type (table) inside the ObjectBox database. */
data class DbEntityTypeInfo(
    /** Stable type ID used for navigation and lookups (e.g., "ObxVod"). */
    val id: String,
    /** Human-friendly display name. */
    val displayName: String,
    /** Total number of rows in the entity/table. */
    val count: Long,
)

/** A lightweight row preview for list screens. */
data class DbRowPreview(
    val id: Long,
    val title: String,
    val subtitle: String? = null,
)

/** Simple paging container used by the inspector UI. */
data class DbPage<T>(
    val items: List<T>,
    val offset: Long,
    val limit: Long,
    val total: Long,
)

/**
 * A single field of an entity.
 *
 * Values are exposed as strings because the UI edits via text input.
 */
data class DbFieldValue(
    val name: String,
    val type: String,
    val value: String? = null,
    val editable: Boolean = false,
    val nullable: Boolean = true,
)

/**
 * Full entity dump.
 *
 * This is intended for diagnostics and manual debugging, not for normal app flows.
 */
data class DbEntityDump(
    val entityTypeId: String,
    val id: Long,
    val fields: List<DbFieldValue>,
)

/** Result of applying an update patch to an entity. */
data class DbUpdateResult(
    val entityTypeId: String,
    val id: Long,
    val applied: Int,
    val errors: List<String> = emptyList(),
) {
    val isSuccess: Boolean get() = errors.isEmpty()
}

// =============================================================================
// Work Graph Export Models
// =============================================================================

/**
 * Complete export of an NX_Work and all related entities.
 *
 * This provides a machine-readable JSON dump of the entire "Work Graph"
 * for debugging, diagnostics, and data analysis.
 *
 * @property exportedAt ISO 8601 timestamp of export
 * @property workKey The canonical work key
 * @property work The main NX_Work entity fields
 * @property sourceRefs All NX_WorkSourceRef entities linked to this work
 * @property variants All NX_WorkVariant entities linked to this work
 * @property relations All NX_WorkRelation entries (both as parent and child)
 * @property userStates All NX_WorkUserState entries for this work
 * @property categories All NX_WorkCategoryRef entries for this work
 * @property ingestLedger All NX_IngestLedger entries referencing this work
 * @property embedding NX_WorkEmbedding if present
 * @property runtimeStates All NX_WorkRuntimeState entries
 */
data class DbWorkGraphExport(
    val exportedAt: String,
    val workKey: String,
    val work: DbEntityDump,
    val sourceRefs: List<DbEntityDump>,
    val variants: List<DbEntityDump>,
    val relations: List<DbWorkRelationExport>,
    val userStates: List<DbEntityDump>,
    val categories: List<DbEntityDump>,
    val ingestLedger: List<DbEntityDump>,
    val embedding: DbEntityDump?,
    val runtimeStates: List<DbEntityDump>,
)

/**
 * Wrapper for NX_WorkRelation with direction context.
 *
 * @property relation The relation entity dump
 * @property direction "PARENT" if this work is the parent, "CHILD" if this work is the child
 * @property relatedWorkKey The workKey of the other work in the relation
 */
data class DbWorkRelationExport(
    val relation: DbEntityDump,
    val direction: String,
    val relatedWorkKey: String?,
)
