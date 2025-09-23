package com.chris.m3usuite.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            // Re-schedule everything that's expected to persist across reboots
            SchedulingGateway.scheduleAll(context.applicationContext)
        }
    }
}
