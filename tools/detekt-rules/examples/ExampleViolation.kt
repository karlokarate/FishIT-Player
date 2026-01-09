package com.fishit.player.feature.example

import io.objectbox.BoxStore
import javax.inject.Inject

/**
 * Example violation of NoBoxStoreOutsideRepository rule.
 * 
 * This file demonstrates what would be flagged by the Detekt rule.
 * In a real codebase, this would be caught during CI/build.
 * 
 * VIOLATION: ViewModels must use repository interfaces, not BoxStore directly.
 */
class ExampleViolationViewModel @Inject constructor(
    private val boxStore: BoxStore, // ❌ VIOLATION: Direct BoxStore in ViewModel
) {
    fun loadData() {
        // Direct ObjectBox access from UI layer - architectural violation!
        val box = boxStore.boxFor(SomeEntity::class.java)
        val items = box.all
    }
}

/**
 * CORRECT PATTERN:
 * Use repository interface instead
 */
class ExampleCorrectViewModel @Inject constructor(
    private val repository: SomeRepository, // ✅ CORRECT: Use repository interface
) {
    fun loadData() {
        // Repository hides persistence implementation details
        val items = repository.getAll()
    }
}
