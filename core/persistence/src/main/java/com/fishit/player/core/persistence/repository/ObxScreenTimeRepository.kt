package com.fishit.player.core.persistence.repository

import com.fishit.player.core.model.repository.ScreenTimeRepository
import com.fishit.player.core.persistence.obx.*
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBox-backed implementation of [ScreenTimeRepository].
 * 
 * Tracks screen time usage for kids profiles.
 */
@Singleton
class ObxScreenTimeRepository @Inject constructor(
    private val boxStore: BoxStore
) : ScreenTimeRepository {
    
    private val screenTimeBox = boxStore.boxFor<ObxScreenTimeEntry>()
    
    companion object {
        private const val DEFAULT_DAILY_LIMIT_MINUTES = 120 // 2 hours
    }
    
    override suspend fun getScreenTimeEntry(
        kidProfileId: Long,
        dayYyyymmdd: String
    ): ScreenTimeRepository.ScreenTimeEntry = withContext(Dispatchers.IO) {
        val existing = screenTimeBox.query(
            ObxScreenTimeEntry_.kidProfileId.equal(kidProfileId)
                .and(ObxScreenTimeEntry_.dayYyyymmdd.equal(dayYyyymmdd))
        )
            .build()
            .findFirst()
        
        if (existing != null) {
            ScreenTimeRepository.ScreenTimeEntry(
                kidProfileId = existing.kidProfileId,
                dayYyyymmdd = existing.dayYyyymmdd,
                usedMinutes = existing.usedMinutes,
                limitMinutes = existing.limitMinutes
            )
        } else {
            // Create new entry with default limit
            val newEntry = ObxScreenTimeEntry(
                kidProfileId = kidProfileId,
                dayYyyymmdd = dayYyyymmdd,
                usedMinutes = 0,
                limitMinutes = DEFAULT_DAILY_LIMIT_MINUTES
            )
            screenTimeBox.put(newEntry)
            
            ScreenTimeRepository.ScreenTimeEntry(
                kidProfileId = kidProfileId,
                dayYyyymmdd = dayYyyymmdd,
                usedMinutes = 0,
                limitMinutes = DEFAULT_DAILY_LIMIT_MINUTES
            )
        }
    }
    
    override suspend fun addUsedMinutes(
        kidProfileId: Long,
        dayYyyymmdd: String,
        minutes: Int
    ): Unit = withContext(Dispatchers.IO) {
        val entry = screenTimeBox.query(
            ObxScreenTimeEntry_.kidProfileId.equal(kidProfileId)
                .and(ObxScreenTimeEntry_.dayYyyymmdd.equal(dayYyyymmdd))
        )
            .build()
            .findFirst()
        
        if (entry != null) {
            entry.usedMinutes += minutes
            screenTimeBox.put(entry)
        } else {
            val newEntry = ObxScreenTimeEntry(
                kidProfileId = kidProfileId,
                dayYyyymmdd = dayYyyymmdd,
                usedMinutes = minutes,
                limitMinutes = DEFAULT_DAILY_LIMIT_MINUTES
            )
            screenTimeBox.put(newEntry)
        }
    }
    
    override suspend fun setDailyLimit(
        kidProfileId: Long,
        dayYyyymmdd: String,
        limitMinutes: Int
    ): Unit = withContext(Dispatchers.IO) {
        val entry = screenTimeBox.query(
            ObxScreenTimeEntry_.kidProfileId.equal(kidProfileId)
                .and(ObxScreenTimeEntry_.dayYyyymmdd.equal(dayYyyymmdd))
        )
            .build()
            .findFirst()
        
        if (entry != null) {
            entry.limitMinutes = limitMinutes
            screenTimeBox.put(entry)
        } else {
            val newEntry = ObxScreenTimeEntry(
                kidProfileId = kidProfileId,
                dayYyyymmdd = dayYyyymmdd,
                usedMinutes = 0,
                limitMinutes = limitMinutes
            )
            screenTimeBox.put(newEntry)
        }
    }
    
    override suspend fun hasRemainingTime(
        kidProfileId: Long,
        dayYyyymmdd: String
    ): Boolean = withContext(Dispatchers.IO) {
        val entry = getScreenTimeEntry(kidProfileId, dayYyyymmdd)
        entry.usedMinutes < entry.limitMinutes
    }
    
    override suspend fun getRemainingMinutes(
        kidProfileId: Long,
        dayYyyymmdd: String
    ): Int = withContext(Dispatchers.IO) {
        val entry = getScreenTimeEntry(kidProfileId, dayYyyymmdd)
        maxOf(0, entry.limitMinutes - entry.usedMinutes)
    }
    
    override suspend fun clearOldEntries(olderThanYyyymmdd: String): Unit = withContext(Dispatchers.IO) {
        val old = screenTimeBox.query(ObxScreenTimeEntry_.dayYyyymmdd.less(olderThanYyyymmdd))
            .build()
            .find()
        
        screenTimeBox.remove(old)
    }
}
