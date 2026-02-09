package com.fishit.player.infra.data.nx.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.fishit.player.core.model.repository.NxCategorySelectionRepository
import com.fishit.player.core.model.repository.NxCategorySelectionRepository.CategorySelection
import com.fishit.player.core.model.repository.NxCategorySelectionRepository.XtreamCategoryType
import com.fishit.player.core.persistence.obx.NX_XtreamCategorySelection
import com.fishit.player.core.persistence.obx.NX_XtreamCategorySelection_
import com.fishit.player.infra.data.nx.mapper.toDomain
import com.fishit.player.infra.data.nx.mapper.toEntity
import com.fishit.player.infra.data.nx.mapper.updateEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import io.objectbox.Box
import io.objectbox.BoxStore
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// DataStore extension for category selection gate persistence (XOC-2)
// Key format: "xtream:{accountKey}:categorySelectionComplete" per contract
private val Context.categoryGateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "xtream_category_gate"
)

/**
 * ObjectBox implementation of [NxCategorySelectionRepository].
 *
 * Part of Issue #669 - Sync by Category Implementation.
 *
 * Manages Xtream category sync selections.
 * Uses DataStore for category selection gate persistence (XOC-2).
 */
@Singleton
class NxCategorySelectionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    boxStore: BoxStore,
) : NxCategorySelectionRepository {

    private val box: Box<NX_XtreamCategorySelection> = boxStore.boxFor(NX_XtreamCategorySelection::class.java)

    // ──────────────────────────────────────────────────────────────────────────
    // Observers
    // ──────────────────────────────────────────────────────────────────────────

    override fun observeForAccount(accountKey: String): Flow<List<CategorySelection>> =
        box.query(NX_XtreamCategorySelection_.accountKey.equal(accountKey, StringOrder.CASE_SENSITIVE))
            .order(NX_XtreamCategorySelection_.sortOrder)
            .build()
            .asFlow()
            .map { list -> list.map { it.toDomain() } }

    override fun observeByType(accountKey: String, categoryType: XtreamCategoryType): Flow<List<CategorySelection>> =
        box.query()
            .apply {
                equal(NX_XtreamCategorySelection_.accountKey, accountKey, StringOrder.CASE_SENSITIVE)
                equal(NX_XtreamCategorySelection_.categoryType, categoryType.name, StringOrder.CASE_SENSITIVE)
            }
            .order(NX_XtreamCategorySelection_.sortOrder)
            .build()
            .asFlow()
            .map { list -> list.map { it.toDomain() } }

    override fun observeSelected(accountKey: String): Flow<List<CategorySelection>> =
        box.query()
            .apply {
                equal(NX_XtreamCategorySelection_.accountKey, accountKey, StringOrder.CASE_SENSITIVE)
                equal(NX_XtreamCategorySelection_.isSelected, true)
            }
            .order(NX_XtreamCategorySelection_.sortOrder)
            .build()
            .asFlow()
            .map { list -> list.map { it.toDomain() } }

    // ──────────────────────────────────────────────────────────────────────────
    // Queries
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun getSelectedCategoryIds(
        accountKey: String,
        categoryType: XtreamCategoryType
    ): List<String> = withContext(Dispatchers.IO) {
        box.query()
            .apply {
                equal(NX_XtreamCategorySelection_.accountKey, accountKey, StringOrder.CASE_SENSITIVE)
                equal(NX_XtreamCategorySelection_.categoryType, categoryType.name, StringOrder.CASE_SENSITIVE)
                equal(NX_XtreamCategorySelection_.isSelected, true)
            }
            .build()
            .find()
            .map { it.sourceCategoryId }
    }

    override suspend fun isSelected(
        accountKey: String,
        categoryType: XtreamCategoryType,
        sourceCategoryId: String
    ): Boolean = withContext(Dispatchers.IO) {
        val key = NX_XtreamCategorySelection.generateKey(accountKey, categoryType.name, sourceCategoryId)
        box.query(NX_XtreamCategorySelection_.selectionKey.equal(key, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()
            ?.isSelected ?: false
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Writes
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun upsert(selection: CategorySelection): CategorySelection = withContext(Dispatchers.IO) {
        val existing = box.query(
            NX_XtreamCategorySelection_.selectionKey.equal(selection.selectionKey, StringOrder.CASE_SENSITIVE)
        ).build().findFirst()

        val entity = if (existing != null) {
            selection.updateEntity(existing)
        } else {
            selection.toEntity()
        }

        box.put(entity)
        entity.toDomain()
    }

    override suspend fun upsertAll(selections: List<CategorySelection>) = withContext(Dispatchers.IO) {
        if (selections.isEmpty()) return@withContext

        // Build map of existing entities by key
        val keys = selections.map { it.selectionKey }
        val existingMap = box.query()
            .`in`(NX_XtreamCategorySelection_.selectionKey, keys.toTypedArray(), StringOrder.CASE_SENSITIVE)
            .build()
            .find()
            .associateBy { it.selectionKey }

        // Map to entities (update existing or create new)
        val entities = selections.map { selection ->
            val existing = existingMap[selection.selectionKey]
            if (existing != null) {
                selection.updateEntity(existing)
            } else {
                selection.toEntity()
            }
        }

        box.put(entities)
    }

    override suspend fun setSelected(
        accountKey: String,
        categoryType: XtreamCategoryType,
        sourceCategoryId: String,
        isSelected: Boolean
    ) = withContext(Dispatchers.IO) {
        val key = NX_XtreamCategorySelection.generateKey(accountKey, categoryType.name, sourceCategoryId)
        val existing = box.query(NX_XtreamCategorySelection_.selectionKey.equal(key, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst()

        if (existing != null) {
            existing.isSelected = isSelected
            existing.updatedAt = System.currentTimeMillis()
            box.put(existing)
        }
        // If no existing entry, do nothing - selection must be created via upsert first
    }

    override suspend fun selectAll(accountKey: String, categoryType: XtreamCategoryType) = withContext(Dispatchers.IO) {
        val entities = box.query()
            .apply {
                equal(NX_XtreamCategorySelection_.accountKey, accountKey, StringOrder.CASE_SENSITIVE)
                equal(NX_XtreamCategorySelection_.categoryType, categoryType.name, StringOrder.CASE_SENSITIVE)
            }
            .build()
            .find()

        val now = System.currentTimeMillis()
        entities.forEach {
            it.isSelected = true
            it.updatedAt = now
        }
        box.put(entities)
    }

    override suspend fun deselectAll(accountKey: String, categoryType: XtreamCategoryType) = withContext(Dispatchers.IO) {
        val entities = box.query()
            .apply {
                equal(NX_XtreamCategorySelection_.accountKey, accountKey, StringOrder.CASE_SENSITIVE)
                equal(NX_XtreamCategorySelection_.categoryType, categoryType.name, StringOrder.CASE_SENSITIVE)
            }
            .build()
            .find()

        val now = System.currentTimeMillis()
        entities.forEach {
            it.isSelected = false
            it.updatedAt = now
        }
        box.put(entities)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cleanup
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun deleteForAccount(accountKey: String): Int = withContext(Dispatchers.IO) {
        // Clear gate flag so re-adding the same account forces category selection (XOC-9)
        setCategorySelectionComplete(accountKey, false)

        box.query(NX_XtreamCategorySelection_.accountKey.equal(accountKey, StringOrder.CASE_SENSITIVE))
            .build()
            .remove()
            .toInt()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Category Selection Gate (XOC-2: Sync Gate)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Checks if category selection has been completed for this account.
     *
     * Per XOC-2 contract: This gate MUST return true before ANY sync can start.
     * Key format: "xtream:{accountKey}:categorySelectionComplete"
     *
     * @param accountKey The Xtream account key
     * @return true if user has confirmed category selection, false otherwise
     */
    override suspend fun isCategorySelectionComplete(accountKey: String): Boolean {
        val key = booleanPreferencesKey("xtream:$accountKey:categorySelectionComplete")
        return context.categoryGateDataStore.data
            .map { prefs -> prefs[key] ?: false }
            .first()
    }

    /**
     * Sets the category selection completion state for this account.
     *
     * Per XOC-4 contract: ONLY called when user explicitly selects categories.
     * NEVER call with complete=true during preload or without user interaction.
     *
     * @param accountKey The Xtream account key
     * @param complete true when user confirms selection, false to require re-selection
     */
    override suspend fun setCategorySelectionComplete(accountKey: String, complete: Boolean) {
        val key = booleanPreferencesKey("xtream:$accountKey:categorySelectionComplete")
        context.categoryGateDataStore.edit { prefs ->
            prefs[key] = complete
        }
    }
}
