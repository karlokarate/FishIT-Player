package com.chris.m3usuite.telegram.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.chris.m3usuite.telegram.service.TelegramServiceClient
import com.google.firebase.messaging.FirebaseMessaging

class FirebasePushService : FirebaseMessagingService() {
    private val svc by lazy { TelegramServiceClient(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        svc.bind()
        // Opportunistic token fetch to ensure initial registration
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (!token.isNullOrBlank()) svc.registerFcm(token)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        svc.unbind()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        svc.registerFcm(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val payload = message.data["payload"] ?: message.rawData?.let { String(it) } ?: return
        svc.processPush(payload)
    }
}
