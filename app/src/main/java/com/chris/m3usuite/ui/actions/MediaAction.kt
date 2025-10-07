package com.chris.m3usuite.ui.actions

/** Identifiers for built-in media actions. */
enum class MediaActionId { Play, Resume, Trailer, AddToList, RemoveFromList, OpenEpg, Share }

/**
 * Declarative action model for detail screens and carousels.
 * - primary marks the main CTA (usually Play)
 * - badge is optional (e.g., time label for Resume)
 */
data class MediaAction(
    val id: MediaActionId,
    val label: String,
    val enabled: Boolean = true,
    val primary: Boolean = false,
    val badge: String? = null,
    val onClick: () -> Unit
)

