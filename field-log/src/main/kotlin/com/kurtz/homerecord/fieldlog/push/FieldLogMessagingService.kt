package com.kurtz.homerecord.fieldlog.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kurtz.homerecord.core.push.PushMessage
import com.kurtz.homerecord.fieldlog.HomeRecordApplication

/** FCM delivery (AC-B30): new tokens go to the backend, data messages become notifications. */
class FieldLogMessagingService : FirebaseMessagingService() {
    private val container get() = (application as HomeRecordApplication).container

    override fun onNewToken(token: String) {
        container.onPushToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (!container.pushRegistered()) return
        val parsed = PushMessage.parse(message.data) ?: return
        container.pushNotifier.show(parsed, message.data)
    }
}
