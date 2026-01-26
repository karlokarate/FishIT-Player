package com.fishit.player.infra.data.nx.repository

import com.fishit.player.core.model.repository.NxProfileUsageRepository
import com.fishit.player.core.model.repository.NxProfileUsageRepository.UsageDay
import com.fishit.player.core.persistence.obx.NX_Profile
import com.fishit.player.core.persistence.obx.NX_ProfileUsage
import com.fishit.player.core.persistence.obx.NX_ProfileUsage_
import com.fishit.player.core.persistence.obx.NX_Profile_
import com.fishit.player.infra.data.nx.mapper.toDomain
import com.fishit.player.infra.data.nx.mapper.toEntity
import io.objectbox.Box
import io.objectbox.BoxStore
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBox implementation of [NxProfileUsageRepository].
 *
 * Tracks daily usage per profile (watch time, sessions).
 */
@Singleton
class NxProfileUsageRepositoryImpl @Inject constructor(
    boxStore: BoxStore,
) : NxProfileUsageRepository {

    private val box: Box<NX_ProfileUsage> = boxStore.boxFor(NX_ProfileUsage::class.java)
    private val profileBox: Box<NX_Profile> = boxStore.boxFor(NX_Profile::class.java)

    // ──────────────────────────────────────────────────────────────────────────
    // Reads
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun get(profileKey: String, epochDay: Int): UsageDay? = withContext(Dispatchers.IO) {
        val profile = profileBox.query(NX_Profile_.profileKey.equal(profileKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst() ?: return@withContext null

        val date = epochDayToDate(epochDay)
        box.query(NX_ProfileUsage_.profileId.equal(profile.id))
            .and()
            .equal(NX_ProfileUsage_.date, date, StringOrder.CASE_SENSITIVE)
            .build()
            .findFirst()
            ?.toDomain(profileKey)
    }

    override fun observeRange(
        profileKey: String,
        fromEpochDay: Int,
        toEpochDay: Int,
    ): Flow<List<UsageDay>> =
        box.query()
            .order(NX_ProfileUsage_.date)
            .build()
            .asFlow()
            .map { usages ->
                val profile = profileBox.query(NX_Profile_.profileKey.equal(profileKey, StringOrder.CASE_SENSITIVE))
                    .build()
                    .findFirst() ?: return@map emptyList()

                val fromDate = epochDayToDate(fromEpochDay)
                val toDate = epochDayToDate(toEpochDay)

                usages
                    .filter { it.profileId == profile.id }
                    .filter { it.date >= fromDate && it.date <= toDate }
                    .map { it.toDomain(profileKey) }
            }

    // ──────────────────────────────────────────────────────────────────────────
    // Writes
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun upsert(day: UsageDay): UsageDay = withContext(Dispatchers.IO) {
        val profile = profileBox.query(NX_Profile_.profileKey.equal(day.profileKey, StringOrder.CASE_SENSITIVE))
            .build()
            .findFirst() ?: error("Profile not found: ${day.profileKey}")

        val date = epochDayToDate(day.epochDay)
        val existing = box.query(NX_ProfileUsage_.profileId.equal(profile.id))
            .and()
            .equal(NX_ProfileUsage_.date, date, StringOrder.CASE_SENSITIVE)
            .build()
            .findFirst()

        val entity = if (existing != null) {
            existing.apply {
                watchTimeMs = day.watchedMs
                itemsWatched = day.sessionsCount
                lastActivityAt = System.currentTimeMillis()
            }
        } else {
            day.toEntity(profile.id)
        }

        box.put(entity)
        entity.toDomain(day.profileKey)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun epochDayToDate(epochDay: Int): String {
        val ms = epochDay.toLong() * 24 * 60 * 60 * 1000
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = ms
        return String.format(
            "%04d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }
}
