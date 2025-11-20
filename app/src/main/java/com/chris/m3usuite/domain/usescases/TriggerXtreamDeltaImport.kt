package com.chris.m3usuite.domain.usecases

import android.content.Context
import com.chris.m3usuite.work.XtreamDeltaImportWorker

class TriggerXtreamDeltaImport(
    private val appContext: Context,
) {
    operator fun invoke(
        includeLive: Boolean,
        vodLimit: Int = 0,
        seriesLimit: Int = 0,
    ) {
        XtreamDeltaImportWorker.triggerOnce(
            context = appContext,
            includeLive = includeLive,
            vodLimit = vodLimit,
            seriesLimit = seriesLimit,
        )
    }
}
