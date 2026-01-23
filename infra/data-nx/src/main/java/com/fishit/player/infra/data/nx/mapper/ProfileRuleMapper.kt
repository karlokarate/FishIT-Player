package com.fishit.player.infra.data.nx.mapper

import com.fishit.player.core.model.repository.NxProfileRuleRepository
import com.fishit.player.core.persistence.obx.NX_ProfileRule

/**
 * Mapper between NX_ProfileRule entity and NxProfileRuleRepository.ProfileRule domain model.
 *
 * Note: Domain model uses profileKey (String), Entity uses profileId (Long).
 * Caller must resolve profileKey ↔ profileId.
 */

internal fun NX_ProfileRule.toDomain(profileKey: String): NxProfileRuleRepository.ProfileRule =
    NxProfileRuleRepository.ProfileRule(
        profileKey = profileKey,
        ruleType = parseRuleType(ruleType),
        targetType = parseTargetType(ruleType),
        targetKey = ruleValue,
        enabled = isEnabled,
        createdAtMs = createdAt,
        expiresAtMs = null, // Entity doesn't store expiration
    )

internal fun NxProfileRuleRepository.ProfileRule.toEntity(profileId: Long): NX_ProfileRule =
    NX_ProfileRule(
        profileId = profileId,
        ruleType = "${ruleType.name}:${targetType.name}",
        ruleValue = targetKey,
        isEnabled = enabled,
        createdAt = if (createdAtMs > 0) createdAtMs else System.currentTimeMillis(),
    )

private fun parseRuleType(combined: String): NxProfileRuleRepository.RuleType {
    val typePart = combined.substringBefore(':')
    return when (typePart) {
        "ALLOW" -> NxProfileRuleRepository.RuleType.ALLOW
        "BLOCK" -> NxProfileRuleRepository.RuleType.BLOCK
        else -> NxProfileRuleRepository.RuleType.BLOCK
    }
}

private fun parseTargetType(combined: String): NxProfileRuleRepository.TargetType {
    val targetPart = combined.substringAfter(':', "UNKNOWN")
    return when (targetPart) {
        "WORK_KEY" -> NxProfileRuleRepository.TargetType.WORK_KEY
        "SOURCE_TYPE" -> NxProfileRuleRepository.TargetType.SOURCE_TYPE
        "SOURCE_ACCOUNT" -> NxProfileRuleRepository.TargetType.SOURCE_ACCOUNT
        "TAG" -> NxProfileRuleRepository.TargetType.TAG
        "RATING" -> NxProfileRuleRepository.TargetType.RATING
        else -> NxProfileRuleRepository.TargetType.UNKNOWN
    }
}
