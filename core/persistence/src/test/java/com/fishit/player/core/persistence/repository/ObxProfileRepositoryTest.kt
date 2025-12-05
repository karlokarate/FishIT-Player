package com.fishit.player.core.persistence.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fishit.player.core.model.repository.ProfileRepository
import com.fishit.player.core.persistence.obx.ObxStore
import io.objectbox.BoxStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [ObxProfileRepository] verifying profile management.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ObxProfileRepositoryTest {
    private lateinit var boxStore: BoxStore
    private lateinit var repository: ObxProfileRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        boxStore = ObxStore.get(context)
        repository = ObxProfileRepository(boxStore)
    }

    @After
    fun tearDown() {
        boxStore.close()
        ObxStore.close()
    }

    @Test
    fun `test ensure default profile creates profile when none exists`() =
        runTest {
            repository.ensureDefaultProfile()

            val profiles = repository.getAllProfiles()
            assertTrue(profiles.isNotEmpty(), "Should have at least one profile")

            val defaultProfile = profiles.first()
            assertEquals(ProfileRepository.ProfileType.ADULT, defaultProfile.type)
            assertEquals("Default", defaultProfile.name)
        }

    @Test
    fun `test get active profile returns default when none set`() =
        runTest {
            val activeProfile = repository.getActiveProfile()

            assertNotNull(activeProfile)
            assertEquals(ProfileRepository.ProfileType.ADULT, activeProfile.type)
        }

    @Test
    fun `test create profile`() =
        runTest {
            val profileId =
                repository.createProfile(
                    name = "Kid Profile",
                    type = ProfileRepository.ProfileType.KID,
                    avatarPath = "/path/to/avatar.png",
                )

            assertTrue(profileId > 0)

            val profile = repository.getProfileById(profileId)
            assertNotNull(profile)
            assertEquals("Kid Profile", profile.name)
            assertEquals(ProfileRepository.ProfileType.KID, profile.type)
            assertEquals("/path/to/avatar.png", profile.avatarPath)
        }

    @Test
    fun `test update profile`() =
        runTest {
            val profileId = repository.createProfile("Original Name", ProfileRepository.ProfileType.ADULT)

            repository.updateProfile(profileId, "Updated Name", "/new/avatar.png")

            val updated = repository.getProfileById(profileId)
            assertNotNull(updated)
            assertEquals("Updated Name", updated.name)
            assertEquals("/new/avatar.png", updated.avatarPath)
        }

    @Test
    fun `test delete profile`() =
        runTest {
            // Ensure we have default profile
            repository.ensureDefaultProfile()

            // Create a second profile
            val profileId = repository.createProfile("To Delete", ProfileRepository.ProfileType.GUEST)

            repository.deleteProfile(profileId)

            val deleted = repository.getProfileById(profileId)
            assertEquals(null, deleted)
        }

    @Test(expected = IllegalStateException::class)
    fun `test cannot delete the only adult profile`() =
        runTest {
            repository.ensureDefaultProfile()

            val profiles = repository.getAllProfiles()
            val adultProfile = profiles.first { it.type == ProfileRepository.ProfileType.ADULT }

            // Should throw exception
            repository.deleteProfile(adultProfile.id)
        }
}
