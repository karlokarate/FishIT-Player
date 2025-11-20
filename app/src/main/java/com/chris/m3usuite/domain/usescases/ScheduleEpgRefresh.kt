package com.chris.m3usuite.domain.usecases

import android.content.Context
import com.chris.m3usuite.work.SchedulingGateway

class ScheduleEpgRefresh(
    private val appContext: Context,
) {
    suspend operator fun invoke(aggressive: Boolean): Boolean =
        SchedulingGateway.refreshFavoritesEpgNow(appContext, aggressive = aggressive)
}
