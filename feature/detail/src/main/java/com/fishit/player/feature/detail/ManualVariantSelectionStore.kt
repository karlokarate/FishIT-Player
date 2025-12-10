package com.fishit.player.feature.detail

import com.fishit.player.core.model.MediaVariant
import com.fishit.player.core.model.SourceKey
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory store for user's manual variant selections.
 *
 * When a user explicitly chooses a specific variant for a media item, this override is stored and
 * will be respected during playback.
 *
 * **Scope:** Per-session (not persisted). User selections reset on app restart. Future enhancement:
 * Persist to DataStore for permanence.
 *
 * **Usage:**
 * 1. User opens media details with multiple variants
 * 2. User taps "Choose version" and selects a specific variant
 * 3. Override is stored by globalId
 * 4. During playback, VariantPlaybackOrchestrator checks for override
 * 5. If override exists, that variant is tried first
 */
object ManualVariantSelectionStore {

    /** User-selected variant overrides, keyed by globalId. */
    private val overrides = ConcurrentHashMap<String, SourceKey>()

    /**
     * Set a manual override for a media item.
     *
     * @param globalId The media's canonical global ID
     * @param sourceKey The user-selected variant's source key
     */
    fun setOverride(globalId: String, sourceKey: SourceKey) {
        overrides[globalId] = sourceKey
    }

    /**
     * Get the manual override for a media item.
     *
     * @param globalId The media's canonical global ID
     * @return User-selected SourceKey, or null if no override
     */
    fun getOverride(globalId: String): SourceKey? {
        return overrides[globalId]
    }

    /**
     * Check if a media item has a manual override.
     *
     * @param globalId The media's canonical global ID
     * @return true if user has selected a specific variant
     */
    fun hasOverride(globalId: String): Boolean {
        return overrides.containsKey(globalId)
    }

    /**
     * Clear the override for a media item.
     *
     * @param globalId The media's canonical global ID
     */
    fun clearOverride(globalId: String) {
        overrides.remove(globalId)
    }

    /** Clear all overrides (e.g., on logout or reset). */
    fun clear() {
        overrides.clear()
    }

    /**
     * Apply override to variant list by moving the selected variant to first position.
     *
     * @param globalId The media's canonical global ID
     * @param variants List of variants to reorder
     * @return Reordered list with override first, or original list if no override
     */
    fun applyOverride(globalId: String, variants: List<MediaVariant>): List<MediaVariant> {
        val override = getOverride(globalId) ?: return variants

        val overrideVariant = variants.find { it.sourceKey == override } ?: return variants

        return listOf(overrideVariant) + variants.filter { it.sourceKey != override }
    }
}

/** UI model for variant selection dialog. */
data class VariantSelectionItem(
        val variant: MediaVariant,
        val displayLabel: String,
        val isSelected: Boolean,
        val isAvailable: Boolean,
)

/** Convert a list of variants to UI items for selection dialog. */
fun List<MediaVariant>.toSelectionItems(
        selectedSourceKey: SourceKey? = null,
): List<VariantSelectionItem> = map { variant ->
    VariantSelectionItem(
            variant = variant,
            displayLabel = variant.toDisplayLabel(),
            isSelected = variant.sourceKey == selectedSourceKey,
            isAvailable = variant.available,
    )
}
