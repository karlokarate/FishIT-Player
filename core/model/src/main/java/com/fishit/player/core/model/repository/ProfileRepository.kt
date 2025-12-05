package com.fishit.player.core.model.repository

/**
 * Repository interface for managing user profiles in FishIT Player v2.
 * 
 * Profiles control content filtering, permissions, and personalization.
 */
interface ProfileRepository {
    
    /**
     * Profile types supported by the system.
     */
    enum class ProfileType {
        ADULT,
        KID,
        GUEST
    }
    
    /**
     * Profile data model.
     */
    data class Profile(
        val id: Long,
        val name: String,
        val type: ProfileType,
        val avatarPath: String? = null,
        val createdAt: Long,
        val updatedAt: Long
    )
    
    /**
     * Get all profiles.
     */
    suspend fun getAllProfiles(): List<Profile>
    
    /**
     * Get a profile by ID.
     */
    suspend fun getProfileById(id: Long): Profile?
    
    /**
     * Get the currently active profile.
     * Returns the default adult profile if none is set.
     */
    suspend fun getActiveProfile(): Profile
    
    /**
     * Set the active profile.
     */
    suspend fun setActiveProfile(profileId: Long)
    
    /**
     * Create a new profile.
     * @return The ID of the created profile.
     */
    suspend fun createProfile(name: String, type: ProfileType, avatarPath: String? = null): Long
    
    /**
     * Update an existing profile.
     */
    suspend fun updateProfile(id: Long, name: String, avatarPath: String? = null)
    
    /**
     * Delete a profile.
     * Cannot delete the default adult profile or the currently active profile.
     */
    suspend fun deleteProfile(id: Long)
    
    /**
     * Ensure at least one default adult profile exists.
     * Called on app startup.
     */
    suspend fun ensureDefaultProfile()
}
