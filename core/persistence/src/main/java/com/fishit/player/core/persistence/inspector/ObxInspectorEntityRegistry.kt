package com.fishit.player.core.persistence.inspector

import com.fishit.player.core.persistence.obx.ObxCanonicalMedia
import com.fishit.player.core.persistence.obx.ObxCanonicalResumeMark
import com.fishit.player.core.persistence.obx.ObxCategory
import com.fishit.player.core.persistence.obx.ObxEpisode
import com.fishit.player.core.persistence.obx.ObxEpgNowNext
import com.fishit.player.core.persistence.obx.ObxIndexGenre
import com.fishit.player.core.persistence.obx.ObxIndexLang
import com.fishit.player.core.persistence.obx.ObxIndexProvider
import com.fishit.player.core.persistence.obx.ObxIndexQuality
import com.fishit.player.core.persistence.obx.ObxIndexYear
import com.fishit.player.core.persistence.obx.ObxKidCategoryAllow
import com.fishit.player.core.persistence.obx.ObxKidContentAllow
import com.fishit.player.core.persistence.obx.ObxKidContentBlock
import com.fishit.player.core.persistence.obx.ObxLive
import com.fishit.player.core.persistence.obx.ObxMediaSourceRef
import com.fishit.player.core.persistence.obx.ObxProfile
import com.fishit.player.core.persistence.obx.ObxProfilePermissions
import com.fishit.player.core.persistence.obx.ObxScreenTimeEntry
import com.fishit.player.core.persistence.obx.ObxSeries
import com.fishit.player.core.persistence.obx.ObxTelegramMessage
import com.fishit.player.core.persistence.obx.ObxVod

/**
 * Explicit registry of ObjectBox entity classes.
 *
 * ObjectBox provides a model at runtime, but mapping it back to strongly-typed Box<T>
 * instances is awkward and not worth the complexity for a debug tool.
 */
internal object ObxInspectorEntityRegistry {

    data class EntitySpec<T : Any>(
        val id: String,
        val displayName: String,
        val clazz: Class<T>,
    )

    /** All entity types stored in the v2 ObjectBox DB (core:persistence). */
    val all: List<EntitySpec<out Any>> =
        listOf(
            EntitySpec("ObxCategory", "Category", ObxCategory::class.java),
            EntitySpec("ObxLive", "Live", ObxLive::class.java),
            EntitySpec("ObxVod", "VOD", ObxVod::class.java),
            EntitySpec("ObxSeries", "Series", ObxSeries::class.java),
            EntitySpec("ObxEpisode", "Episode", ObxEpisode::class.java),
            EntitySpec("ObxEpgNowNext", "EPG Now/Next", ObxEpgNowNext::class.java),

            EntitySpec("ObxProfile", "Profile", ObxProfile::class.java),
            EntitySpec("ObxProfilePermissions", "Profile Permissions", ObxProfilePermissions::class.java),
            EntitySpec("ObxKidContentAllow", "Kid Allow: Content", ObxKidContentAllow::class.java),
            EntitySpec("ObxKidCategoryAllow", "Kid Allow: Category", ObxKidCategoryAllow::class.java),
            EntitySpec("ObxKidContentBlock", "Kid Block: Content", ObxKidContentBlock::class.java),
            EntitySpec("ObxScreenTimeEntry", "Screen Time", ObxScreenTimeEntry::class.java),
            EntitySpec("ObxTelegramMessage", "Telegram Message", ObxTelegramMessage::class.java),

            // Aggregated index tables
            EntitySpec("ObxIndexProvider", "Index: Provider", ObxIndexProvider::class.java),
            EntitySpec("ObxIndexYear", "Index: Year", ObxIndexYear::class.java),
            EntitySpec("ObxIndexGenre", "Index: Genre", ObxIndexGenre::class.java),
            EntitySpec("ObxIndexLang", "Index: Language", ObxIndexLang::class.java),
            EntitySpec("ObxIndexQuality", "Index: Quality", ObxIndexQuality::class.java),

            // Canonical identity (v2 SSOT)
            EntitySpec("ObxCanonicalMedia", "Canonical Media", ObxCanonicalMedia::class.java),
            EntitySpec("ObxMediaSourceRef", "Canonical Source Ref", ObxMediaSourceRef::class.java),
            EntitySpec("ObxCanonicalResumeMark", "Canonical Resume", ObxCanonicalResumeMark::class.java),
        )

    val byId: Map<String, EntitySpec<out Any>> = all.associateBy { it.id }
}
