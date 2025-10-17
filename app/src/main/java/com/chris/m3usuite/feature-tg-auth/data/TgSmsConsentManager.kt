package com.chris.m3usuite.feature_tg_auth.data

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import java.lang.ref.WeakReference
import java.util.regex.Pattern
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TgSmsConsentManager(
    private val appContext: Context,
    private val job: SupervisorJob = SupervisorJob(),
    private val scope: CoroutineScope = CoroutineScope(job + Dispatchers.Main.immediate)
) {
    private val codePattern: Pattern = Pattern.compile("\\b(\\d{5,6})\\b")
    private val random = Random(System.currentTimeMillis())

    private var activityRef: WeakReference<Activity>? = null
    private var launcher: ActivityResultLauncher<Intent>? = null
    private var onCode: ((String) -> Unit)? = null
    private var receiver: BroadcastReceiver? = null
    private var receiverRegistered: Boolean = false

    fun attach(activity: Activity, launcher: ActivityResultLauncher<Intent>, onCode: (String) -> Unit) {
        this.activityRef = WeakReference(activity)
        this.launcher = launcher
        this.onCode = onCode
        ensureReceiver()
    }

    fun detach() {
        onCode = null
        launcher = null
        activityRef = null
        unregisterReceiver()
        job.cancelChildren()
    }

    fun startConsent(jitterMs: Long = 0L) {
        val activity = activityRef?.get() ?: return
        scope.launch {
            if (jitterMs > 0L) delay(jitterMs)
            ensureReceiver()
            runCatching { SmsRetriever.getClient(activity).startSmsUserConsent(null) }
        }
    }

    fun handleConsentResult(result: ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK) {
            handleConsentCanceled()
            return
        }
        val data = result.data ?: return
        extractCode(data)?.let { code -> onCode?.invoke(code) }
    }

    fun handleConsentCanceled() {
        rearmWithJitter()
    }

    fun rearmWithJitter(minMs: Long = 400L, maxMs: Long = 1500L) {
        val delay = if (maxMs <= minMs) minMs else random.nextLong(minMs, maxMs)
        startConsent(delay)
    }

    private fun ensureReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        val rec = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != SmsRetriever.SMS_RETRIEVED_ACTION) return
                val extras = intent.extras ?: return
                val status = extras.getParcelable<Status>(SmsRetriever.EXTRA_STATUS) ?: return
                when (status.statusCode) {
                    CommonStatusCodes.SUCCESS -> {
                        val consentIntent = extras.getParcelable<Intent>(SmsRetriever.EXTRA_CONSENT_INTENT)
                        if (consentIntent != null) {
                            launcher?.launch(consentIntent)
                        }
                    }
                    CommonStatusCodes.TIMEOUT -> {
                        rearmWithJitter()
                    }
                }
            }
        }
        receiver = rec
        ContextCompat.registerReceiver(
            appContext,
            rec,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun unregisterReceiver() {
        val rec = receiver ?: return
        runCatching { appContext.unregisterReceiver(rec) }
        receiver = null
        receiverRegistered = false
    }

    private fun extractCode(data: Intent): String? {
        val message = data.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE) ?: return null
        val matcher = codePattern.matcher(message)
        return if (matcher.find()) matcher.group(1) else null
    }

}
