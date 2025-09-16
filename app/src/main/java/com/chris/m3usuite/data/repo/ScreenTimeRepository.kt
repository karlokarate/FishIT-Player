package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.data.obx.ObxScreenTimeEntry
import com.chris.m3usuite.data.obx.ObxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScreenTimeRepository(private val context: Context) {
    private val box get() = ObxStore.get(context)

    private fun todayKey(): String {
        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return fmt.format(cal.time)
    }

    private suspend fun ensureTodayEntry(kidId: Long): ObxScreenTimeEntry = withContext(Dispatchers.IO) {
        val day = todayKey()
        val b = box.boxFor(ObxScreenTimeEntry::class.java)
        val q = b.query(com.chris.m3usuite.data.obx.ObxScreenTimeEntry_.kidProfileId.equal(kidId).and(com.chris.m3usuite.data.obx.ObxScreenTimeEntry_.dayYyyymmdd.equal(day))).build()
        val got = q.findFirst()
        if (got != null) got else {
            val entry = ObxScreenTimeEntry(kidProfileId = kidId, dayYyyymmdd = day, usedMinutes = 0, limitMinutes = 0)
            b.put(entry)
            q.findFirst()!!
        }
    }

    suspend fun remainingMinutes(kidId: Long): Int = withContext(Dispatchers.IO) {
        val entry = ensureTodayEntry(kidId)
        (entry.limitMinutes - entry.usedMinutes).coerceAtLeast(0)
    }

    suspend fun setDailyLimit(kidId: Long, minutes: Int) = withContext(Dispatchers.IO) {
        val day = todayKey()
        ensureTodayEntry(kidId)
        val b = box.boxFor(ObxScreenTimeEntry::class.java)
        val q = b.query(com.chris.m3usuite.data.obx.ObxScreenTimeEntry_.kidProfileId.equal(kidId).and(com.chris.m3usuite.data.obx.ObxScreenTimeEntry_.dayYyyymmdd.equal(day))).build()
        q.findFirst()?.let { row -> row.limitMinutes = minutes.coerceAtLeast(0); b.put(row) }
    }

    suspend fun tickUsageIfPlaying(kidId: Long, deltaSecs: Int) = withContext(Dispatchers.IO) {
        if (deltaSecs <= 0) return@withContext
        val day = todayKey()
        val entry = ensureTodayEntry(kidId)
        val added = (deltaSecs / 60)
        if (added <= 0) return@withContext
        val newUsed = (entry.usedMinutes + added).coerceAtLeast(0)
        val b = box.boxFor(ObxScreenTimeEntry::class.java)
        val q = b.query(com.chris.m3usuite.data.obx.ObxScreenTimeEntry_.kidProfileId.equal(kidId).and(com.chris.m3usuite.data.obx.ObxScreenTimeEntry_.dayYyyymmdd.equal(day))).build()
        q.findFirst()?.let { row -> row.usedMinutes = newUsed; b.put(row) }
    }

    suspend fun resetToday(kidId: Long) = withContext(Dispatchers.IO) {
        val day = todayKey()
        ensureTodayEntry(kidId)
        val b = box.boxFor(ObxScreenTimeEntry::class.java)
        val q = b.query(com.chris.m3usuite.data.obx.ObxScreenTimeEntry_.kidProfileId.equal(kidId).and(com.chris.m3usuite.data.obx.ObxScreenTimeEntry_.dayYyyymmdd.equal(day))).build()
        q.findFirst()?.let { row -> row.usedMinutes = 0; b.put(row) }
    }
}
