package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.repository.NxCategorySelectionRepository
import com.fishit.player.core.model.repository.NxCategorySelectionRepository.CategorySelection
import com.fishit.player.core.model.repository.NxCategorySelectionRepository.XtreamCategoryType
import com.fishit.player.core.persistence.obx.NX_XtreamCategorySelection

/**
 * Mapper between NX_XtreamCategorySelection entity and CategorySelection domain model.
 *
 * Part of Issue #669 - Sync by Category Implementation.
 */

internal fun NX_XtreamCategorySelection.toDomain(): CategorySelection =
    CategorySelection(
        accountKey = accountKey,
        categoryType = when (categoryType) {
            "VOD" -> XtreamCategoryType.VOD
            "SERIES" -> XtreamCategoryType.SERIES
            "LIVE" -> XtreamCategoryType.LIVE
            else -> XtreamCategoryType.VOD
        },
        sourceCategoryId = sourceCategoryId,
        categoryName = categoryName,
        isSelected = isSelected,
        parentId = parentId,
        sortOrder = sortOrder,
    )

internal fun CategorySelection.toEntity(): NX_XtreamCategorySelection =
    NX_XtreamCategorySelection(
        selectionKey = selectionKey,
        accountKey = accountKey,
        categoryType = categoryType.name,
        sourceCategoryId = sourceCategoryId,
        categoryName = categoryName,
        isSelected = isSelected,
        parentId = parentId,
        sortOrder = sortOrder,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
    )

internal fun CategorySelection.updateEntity(
    existing: NX_XtreamCategorySelection
): NX_XtreamCategorySelection =
    existing.apply {
        this.categoryName = this@updateEntity.categoryName
        this.isSelected = this@updateEntity.isSelected
        this.parentId = this@updateEntity.parentId
        this.sortOrder = this@updateEntity.sortOrder
        this.updatedAt = System.currentTimeMillis()
    }
