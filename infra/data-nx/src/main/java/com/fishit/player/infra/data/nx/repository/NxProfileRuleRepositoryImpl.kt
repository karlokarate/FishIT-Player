package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxProfileRuleRepository
import com.fishit.player.core.model.repository.NxProfileRuleRepository.ProfileRule
import com.fishit.player.core.model.repository.NxProfileRuleRepository.RuleType
import com.fishit.player.core.model.repository.NxProfileRuleRepository.TargetType
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import com.fishit.player.core.persistence.obx.NX_Profile
import com.fishit.player.core.persistence.obx.NX_ProfileRule
import com.fishit.player.core.persistence.obx.NX_ProfileRule_
import com.fishit.player.core.persistence.obx.NX_Profile_
import com.fishit.player.infra.data.nx.mapper.toDomain
import com.fishit.player.infra.data.nx.mapper.toEntity
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBox implementation of [NxProfileRuleRepository].
 *
 * Manages content filtering rules per profile (allow/block).
 */
@Singleton
class NxProfileRuleRepositoryImpl
    @Inject
    constructor(
        boxStore: BoxStore,
    ) : NxProfileRuleRepository {
        private val box: Box<NX_ProfileRule> = boxStore.boxFor(NX_ProfileRule::class.java)
        private val profileBox: Box<NX_Profile> = boxStore.boxFor(NX_Profile::class.java)

        // ──────────────────────────────────────────────────────────────────────────
        // Reads
        // ──────────────────────────────────────────────────────────────────────────

        override fun observeRules(profileKey: String): Flow<List<ProfileRule>> =
            box
                .query()
                .order(NX_ProfileRule_.createdAt)
                .build()
                .asFlow()
                .map { rules ->
                    val profile =
                        profileBox
                            .query(NX_Profile_.profileKey.equal(profileKey, StringOrder.CASE_SENSITIVE))
                            .build()
                            .findFirst() ?: return@map emptyList()

                    rules
                        .filter { it.profileId == profile.id }
                        .map { it.toDomain(profileKey) }
                }

        // ──────────────────────────────────────────────────────────────────────────
        // Writes
        // ──────────────────────────────────────────────────────────────────────────

        override suspend fun upsert(rule: ProfileRule): ProfileRule =
            withContext(Dispatchers.IO) {
                val profile =
                    profileBox
                        .query(NX_Profile_.profileKey.equal(rule.profileKey, StringOrder.CASE_SENSITIVE))
                        .build()
                        .findFirst() ?: error("Profile not found: ${rule.profileKey}")

                val ruleTypeKey = "${rule.ruleType.name}:${rule.targetType.name}"

                // Find existing rule with same profile, type, and target
                val existing =
                    box
                        .query(NX_ProfileRule_.profileId.equal(profile.id))
                        .and()
                        .equal(NX_ProfileRule_.ruleType, ruleTypeKey, StringOrder.CASE_SENSITIVE)
                        .and()
                        .equal(NX_ProfileRule_.ruleValue, rule.targetKey, StringOrder.CASE_SENSITIVE)
                        .build()
                        .findFirst()

                val entity =
                    if (existing != null) {
                        existing.apply {
                            isEnabled = rule.enabled
                        }
                    } else {
                        rule.toEntity(profile.id)
                    }

                box.put(entity)
                entity.toDomain(rule.profileKey)
            }

        override suspend fun delete(
            profileKey: String,
            ruleType: RuleType,
            targetType: TargetType,
            targetKey: String,
        ): Boolean =
            withContext(Dispatchers.IO) {
                val profile =
                    profileBox
                        .query(NX_Profile_.profileKey.equal(profileKey, StringOrder.CASE_SENSITIVE))
                        .build()
                        .findFirst() ?: return@withContext false

                val ruleTypeKey = "${ruleType.name}:${targetType.name}"

                val deleted =
                    box
                        .query(NX_ProfileRule_.profileId.equal(profile.id))
                        .and()
                        .equal(NX_ProfileRule_.ruleType, ruleTypeKey, StringOrder.CASE_SENSITIVE)
                        .and()
                        .equal(NX_ProfileRule_.ruleValue, targetKey, StringOrder.CASE_SENSITIVE)
                        .build()
                        .remove()

                deleted > 0
            }
    }
