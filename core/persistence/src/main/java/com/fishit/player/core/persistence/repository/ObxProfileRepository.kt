package com.fishit.player.core.persistence.repository

import com.fishit.player.core.model.repository.ProfileRepository
import com.fishit.player.core.persistence.obx.ObxProfile
import com.fishit.player.core.persistence.obx.ObxProfile_
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBox-backed implementation of [ProfileRepository].
 *
 * Manages profiles with automatic default profile creation.
 */
@Singleton
class ObxProfileRepository
    @Inject
    constructor(
        private val boxStore: BoxStore,
    ) : ProfileRepository {
        private val profileBox = boxStore.boxFor<ObxProfile>()

        override suspend fun getAllProfiles(): List<ProfileRepository.Profile> =
            withContext(Dispatchers.IO) {
                profileBox.all.map { it.toDomain() }
            }

        override suspend fun getProfileById(id: Long): ProfileRepository.Profile? =
            withContext(Dispatchers.IO) {
                profileBox.get(id)?.toDomain()
            }

        override suspend fun getActiveProfile(): ProfileRepository.Profile =
            withContext(Dispatchers.IO) {
                // For Phase 2, return the first adult profile or create default
                ensureDefaultProfile()
                profileBox
                    .query(ObxProfile_.type.equal("adult"))
                    .build()
                    .findFirst()
                    ?.toDomain()
                    ?: throw IllegalStateException("Default profile should exist after ensureDefaultProfile()")
            }

        override suspend fun setActiveProfile(profileId: Long) {
            // For Phase 2, this is a no-op. Active profile tracking will be added later.
            // Validate that profile exists
            withContext(Dispatchers.IO) {
                profileBox.get(profileId) ?: throw IllegalArgumentException("Profile not found: $profileId")
            }
        }

        override suspend fun createProfile(
            name: String,
            type: ProfileRepository.ProfileType,
            avatarPath: String?,
        ): Long =
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val profile =
                    ObxProfile(
                        name = name,
                        type = type.toObxType(),
                        avatarPath = avatarPath,
                        createdAt = now,
                        updatedAt = now,
                    )
                profileBox.put(profile)
            }

        override suspend fun updateProfile(
            id: Long,
            name: String,
            avatarPath: String?,
        ): Unit =
            withContext(Dispatchers.IO) {
                val profile = profileBox.get(id) ?: throw IllegalArgumentException("Profile not found: $id")
                profile.name = name
                profile.avatarPath = avatarPath
                profile.updatedAt = System.currentTimeMillis()
                profileBox.put(profile)
            }

        override suspend fun deleteProfile(id: Long): Unit =
            withContext(Dispatchers.IO) {
                val profile = profileBox.get(id) ?: return@withContext

                // Don't delete the only adult profile
                val adultCount =
                    profileBox
                        .query(ObxProfile_.type.equal("adult"))
                        .build()
                        .count()

                if (profile.type == "adult" && adultCount <= 1) {
                    throw IllegalStateException("Cannot delete the only adult profile")
                }

                profileBox.remove(id)
            }

        override suspend fun ensureDefaultProfile(): Unit =
            withContext(Dispatchers.IO) {
                val count = profileBox.count()
                if (count == 0L) {
                    val now = System.currentTimeMillis()
                    val defaultProfile =
                        ObxProfile(
                            name = "Default",
                            type = "adult",
                            createdAt = now,
                            updatedAt = now,
                        )
                    profileBox.put(defaultProfile)
                }
            }

        private fun ObxProfile.toDomain() =
            ProfileRepository.Profile(
                id = id,
                name = name,
                type =
                    when (type) {
                        "kid" -> ProfileRepository.ProfileType.KID
                        "guest" -> ProfileRepository.ProfileType.GUEST
                        else -> ProfileRepository.ProfileType.ADULT
                    },
                avatarPath = avatarPath,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )

        private fun ProfileRepository.ProfileType.toObxType() =
            when (this) {
                ProfileRepository.ProfileType.ADULT -> "adult"
                ProfileRepository.ProfileType.KID -> "kid"
                ProfileRepository.ProfileType.GUEST -> "guest"
            }
    }
