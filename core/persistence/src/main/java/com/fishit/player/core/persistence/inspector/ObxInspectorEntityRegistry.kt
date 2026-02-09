package com.fishit.player.core.persistence.inspector

import com.fishit.player.core.persistence.obx.NX_Category
import com.fishit.player.core.persistence.obx.NX_CloudOutboxEvent
import com.fishit.player.core.persistence.obx.NX_EpgEntry
import com.fishit.player.core.persistence.obx.NX_IngestLedger
import com.fishit.player.core.persistence.obx.NX_Profile
import com.fishit.player.core.persistence.obx.NX_ProfileRule
import com.fishit.player.core.persistence.obx.NX_ProfileUsage
import com.fishit.player.core.persistence.obx.NX_SourceAccount
import com.fishit.player.core.persistence.obx.NX_Work
import com.fishit.player.core.persistence.obx.NX_WorkCategoryRef
import com.fishit.player.core.persistence.obx.NX_WorkEmbedding
import com.fishit.player.core.persistence.obx.NX_WorkRedirect
import com.fishit.player.core.persistence.obx.NX_WorkRelation
import com.fishit.player.core.persistence.obx.NX_WorkRuntimeState
import com.fishit.player.core.persistence.obx.NX_WorkSourceRef
import com.fishit.player.core.persistence.obx.NX_WorkUserState
import com.fishit.player.core.persistence.obx.NX_WorkVariant
import com.fishit.player.core.persistence.obx.ObxCanonicalMedia
import com.fishit.player.core.persistence.obx.ObxCanonicalResumeMark
import com.fishit.player.core.persistence.obx.ObxMediaSourceRef
import com.fishit.player.core.persistence.obx.ObxTelegramMessage

/**
 * Explicit registry of ObjectBox entity classes.
 *
 * ObjectBox provides a model at runtime, but mapping it back to strongly-typed Box<T>
 * instances is awkward and not worth the complexity for a debug tool.
 *
 * Includes both:
 * - Legacy Obx* entities (v1 SSOT, deprecated)
 * - NX_* entities (v2 SSOT, current)
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
            // =================================================================
            // NX_* Entities (v2 SSOT - Primary for UI)
            // =================================================================
            EntitySpec("NX_Work", "NX: Work (UI SSOT)", NX_Work::class.java),
            EntitySpec("NX_WorkSourceRef", "NX: Work Source Ref", NX_WorkSourceRef::class.java),
            EntitySpec("NX_WorkVariant", "NX: Work Variant", NX_WorkVariant::class.java),
            EntitySpec("NX_WorkRelation", "NX: Work Relation", NX_WorkRelation::class.java),
            EntitySpec("NX_WorkUserState", "NX: Work User State", NX_WorkUserState::class.java),
            EntitySpec("NX_WorkRuntimeState", "NX: Work Runtime State", NX_WorkRuntimeState::class.java),
            EntitySpec("NX_IngestLedger", "NX: Ingest Ledger", NX_IngestLedger::class.java),
            EntitySpec("NX_Profile", "NX: Profile", NX_Profile::class.java),
            EntitySpec("NX_ProfileRule", "NX: Profile Rule", NX_ProfileRule::class.java),
            EntitySpec("NX_ProfileUsage", "NX: Profile Usage", NX_ProfileUsage::class.java),
            EntitySpec("NX_SourceAccount", "NX: Source Account", NX_SourceAccount::class.java),
            EntitySpec("NX_CloudOutboxEvent", "NX: Cloud Outbox", NX_CloudOutboxEvent::class.java),
            EntitySpec("NX_WorkEmbedding", "NX: Work Embedding", NX_WorkEmbedding::class.java),
            EntitySpec("NX_WorkRedirect", "NX: Work Redirect", NX_WorkRedirect::class.java),
            EntitySpec("NX_Category", "NX: Category", NX_Category::class.java),
            EntitySpec("NX_WorkCategoryRef", "NX: Work Category Ref", NX_WorkCategoryRef::class.java),
            EntitySpec("NX_EpgEntry", "NX: EPG Entry", NX_EpgEntry::class.java),
            // =================================================================
            // Transitional Obx* Entities (pending NX migration — P2/P3)
            // =================================================================
            EntitySpec("ObxTelegramMessage", "Telegram Message (→ P2)", ObxTelegramMessage::class.java),
            // Canonical identity (→ P3: consolidate with NX_Work)
            EntitySpec("ObxCanonicalMedia", "Canonical Media (→ P3)", ObxCanonicalMedia::class.java),
            EntitySpec("ObxMediaSourceRef", "Canonical Source Ref (→ P3)", ObxMediaSourceRef::class.java),
            EntitySpec("ObxCanonicalResumeMark", "Canonical Resume (→ P3)", ObxCanonicalResumeMark::class.java),
        )

    val byId: Map<String, EntitySpec<out Any>> = all.associateBy { it.id }
}
