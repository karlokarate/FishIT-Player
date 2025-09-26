package com.chris.m3usuite.telegram.push

import android.util.Log
import com.chris.m3usuite.telegram.service.TelegramServiceClient
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FirebasePushService : FirebaseMessagingService() {
    private val svc by lazy { TelegramServiceClient(applicationContext) }

    private fun hasFirebaseApp(): Boolean = runCatching {
        FirebaseApp.getApps(this).isNotEmpty()
    }.getOrDefault(false)

    override fun onCreate() {
        super.onCreate()
        if (!hasFirebaseApp()) {
            Log.i("FirebasePushService", "FirebaseApp not configured – stopping service")
            stopSelf()
            return
        }
        svc.bind()
        // Opportunistic token fetch to ensure initial registration
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (!token.isNullOrBlank()) svc.registerFcm(token)
            }
        }.onFailure { err ->
            Log.w("FirebasePushService", "Failed to obtain FCM token", err)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        svc.unbind()
    }

    override fun onNewToken(token: String) {
        if (!hasFirebaseApp()) {
            Log.i("FirebasePushService", "Ignoring token update – FirebaseApp not configured")
            return
        }
        super.onNewToken(token)
        svc.registerFcm(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (!hasFirebaseApp()) {
            Log.i("FirebasePushService", "Ignoring push – FirebaseApp not configured")
            return
        }
        super.onMessageReceived(message)
        val payload = message.data["payload"] ?: message.rawData?.let { String(it) } ?: return
        svc.processPush(payload)
    }
}
