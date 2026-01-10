/**
 * TEMP IMPLEMENTATION NOTES (REMOVE AFTER IMPLEMENTATION)
 * -------------------------------------------------------
 * - DOMAIN interface only.
 * - profileKey is stable (cloud-ready); do NOT expose local DB ids as SSOT.
 * - Implementation maps to NX_Profile entity in infra/data-nx.
 */
package com.fishit.player.core.model.repository

import kotlinx.coroutines.flow.Flow

interface NxProfileRepository {

    data class Profile(
        val profileKey: String,
        val displayName: String,
        val avatarKey: String? = null,
        val isKids: Boolean = false,
        val createdAtMs: Long = 0L,
        val updatedAtMs: Long = 0L,
        val isDeleted: Boolean = false,
    )

    suspend fun get(profileKey: String): Profile?

    fun observeAll(): Flow<List<Profile>>

    suspend fun upsert(profile: Profile): Profile

    suspend fun softDelete(profileKey: String): Boolean
}

