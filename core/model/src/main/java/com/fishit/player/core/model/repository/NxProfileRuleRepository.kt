/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - DOMAIN interface only.
 * - Rules unify allow/block + permissions.
 * - Implementation maps to NX_ProfileRule entity in infra/data-nx.
 * - UI should observe rules and apply gating in use-cases (fast).
 */
package com.fishit.player.core.model.repository

import kotlinx.coroutines.flow.Flow

interface NxProfileRuleRepository {

    enum class RuleType { ALLOW, BLOCK }

    enum class TargetType {
        WORK_KEY,
        SOURCE_TYPE,
        SOURCE_ACCOUNT, // e.g. "xtream:accA"
        TAG,
        RATING,
        UNKNOWN,
    }

    data class ProfileRule(
        val profileKey: String,
        val ruleType: RuleType,
        val targetType: TargetType,
        val targetKey: String,
        val enabled: Boolean = true,
        val createdAtMs: Long = 0L,
        val expiresAtMs: Long? = null,
    )

    fun observeRules(profileKey: String): Flow<List<ProfileRule>>

    suspend fun upsert(rule: ProfileRule): ProfileRule

    suspend fun delete(rule: ProfileRule): Boolean
}


