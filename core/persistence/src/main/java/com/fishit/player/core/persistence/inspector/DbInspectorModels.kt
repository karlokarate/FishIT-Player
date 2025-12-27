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
