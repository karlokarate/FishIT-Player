package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.ScreenTimeEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScreenTimeRepository(private val context: Context) {
    private val db get() = DbProvider.get(context)

    private fun todayKey(): String {
        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return fmt.format(cal.time)
    }

    private suspend fun ensureTodayEntry(kidId: Long): ScreenTimeEntry = withContext(Dispatchers.IO) {
        val day = todayKey()
        db.screenTimeDao().getForDay(kidId, day) ?: run {
            val entry = ScreenTimeEntry(kidProfileId = kidId, dayYyyymmdd = day, usedMinutes = 0, limitMinutes = 0)
            db.screenTimeDao().upsert(entry)
            db.screenTimeDao().getForDay(kidId, day)!!
        }
    }

    suspend fun remainingMinutes(kidId: Long): Int = withContext(Dispatchers.IO) {
        val entry = ensureTodayEntry(kidId)
        (entry.limitMinutes - entry.usedMinutes).coerceAtLeast(0)
    }

    suspend fun setDailyLimit(kidId: Long, minutes: Int) = withContext(Dispatchers.IO) {
        val day = todayKey()
        ensureTodayEntry(kidId)
        db.screenTimeDao().updateLimit(kidId, day, minutes.coerceAtLeast(0))
    }

    suspend fun tickUsageIfPlaying(kidId: Long, deltaSecs: Int) = withContext(Dispatchers.IO) {
        if (deltaSecs <= 0) return@withContext
        val day = todayKey()
        val entry = ensureTodayEntry(kidId)
        val added = (deltaSecs / 60)
        if (added <= 0) return@withContext
        val newUsed = (entry.usedMinutes + added).coerceAtLeast(0)
        db.screenTimeDao().updateUsed(kidId, day, newUsed)
    }
}

