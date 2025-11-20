package com.chris.m3usuite.data.repo

import android.content.Context
import com.chris.m3usuite.data.obx.ObxScreenTimeEntry
import com.chris.m3usuite.data.obx.ObxScreenTimeEntry_
import com.chris.m3usuite.data.obx.ObxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScreenTimeRepository(
    private val context: Context,
) {
    private val store get() = ObxStore.get(context)

    private fun todayKey(): String {
        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return fmt.format(cal.time)
    }

    private suspend fun ensureTodayEntry(kidId: Long): ObxScreenTimeEntry =
        withContext(Dispatchers.IO) {
            val day = todayKey()
            val box = store.boxFor(ObxScreenTimeEntry::class.java)
            var result: ObxScreenTimeEntry? = null
            store.runInTx {
                val q =
                    box
                        .query(
                            ObxScreenTimeEntry_.kidProfileId
                                .equal(kidId)
                                .and(ObxScreenTimeEntry_.dayYyyymmdd.equal(day)),
                        ).build()
                val got = q.findFirst()
                if (got != null) {
                    result = got
                } else {
                    val entry =
                        ObxScreenTimeEntry(
                            kidProfileId = kidId,
                            dayYyyymmdd = day,
                            usedMinutes = 0,
                            limitMinutes = 0,
                        )
                    box.put(entry)
                    result = entry
                }
            }
            result!!
        }

    suspend fun remainingMinutes(kidId: Long): Int =
        withContext(Dispatchers.IO) {
            val entry = ensureTodayEntry(kidId)
            (entry.limitMinutes - entry.usedMinutes).coerceAtLeast(0)
        }

    suspend fun setDailyLimit(
        kidId: Long,
        minutes: Int,
    ) = withContext(Dispatchers.IO) {
        val day = todayKey()
        val box = store.boxFor(ObxScreenTimeEntry::class.java)
        store.runInTx {
            val row =
                box
                    .query(
                        ObxScreenTimeEntry_.kidProfileId
                            .equal(kidId)
                            .and(ObxScreenTimeEntry_.dayYyyymmdd.equal(day)),
                    ).build()
                    .findFirst() ?: ObxScreenTimeEntry(
                    kidProfileId = kidId,
                    dayYyyymmdd = day,
                    usedMinutes = 0,
                    limitMinutes = 0,
                )
            row.limitMinutes = minutes.coerceAtLeast(0)
            box.put(row)
        }
    }

    suspend fun tickUsageIfPlaying(
        kidId: Long,
        deltaSecs: Int,
    ) = withContext(Dispatchers.IO) {
        if (deltaSecs <= 0) return@withContext
        val add = deltaSecs / 60
        if (add <= 0) return@withContext
        val day = todayKey()
        val box = store.boxFor(ObxScreenTimeEntry::class.java)
        store.runInTx {
            val row =
                box
                    .query(
                        ObxScreenTimeEntry_.kidProfileId
                            .equal(kidId)
                            .and(ObxScreenTimeEntry_.dayYyyymmdd.equal(day)),
                    ).build()
                    .findFirst() ?: ObxScreenTimeEntry(
                    kidProfileId = kidId,
                    dayYyyymmdd = day,
                    usedMinutes = 0,
                    limitMinutes = 0,
                )
            row.usedMinutes = (row.usedMinutes + add).coerceAtLeast(0)
            box.put(row)
        }
    }

    suspend fun resetToday(kidId: Long) =
        withContext(Dispatchers.IO) {
            val day = todayKey()
            val box = store.boxFor(ObxScreenTimeEntry::class.java)
            store.runInTx {
                val row =
                    box
                        .query(
                            ObxScreenTimeEntry_.kidProfileId
                                .equal(kidId)
                                .and(ObxScreenTimeEntry_.dayYyyymmdd.equal(day)),
                        ).build()
                        .findFirst() ?: ObxScreenTimeEntry(
                        kidProfileId = kidId,
                        dayYyyymmdd = day,
                        usedMinutes = 0,
                        limitMinutes = 0,
                    )
                row.usedMinutes = 0
                box.put(row)
            }
        }
}
