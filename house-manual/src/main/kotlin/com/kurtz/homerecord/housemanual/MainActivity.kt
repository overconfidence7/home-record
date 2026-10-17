package com.kurtz.homerecord.housemanual

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kurtz.homerecord.housemanual.billing.RevenueCatEntitlements
import com.kurtz.homerecord.housemanual.nav.HomeRecordNavHost
import com.kurtz.homerecord.housemanual.nav.ReminderRequest
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

class MainActivity : ComponentActivity() {
    private var emergencyRequests by mutableStateOf(0)
    private var reminderRequest by mutableStateOf<ReminderRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as HouseManualApp).container
        if (savedInstanceState == null) {
            if (intent?.action == ACTION_OPEN_EMERGENCY) emergencyRequests++
            readReminderRequest(intent)
            offerImport(container, intent)
        }
        setContent {
            HomeRecordTheme { HomeRecordNavHost(container, emergencyRequests, reminderRequest) }
        }
    }

    override fun onResume() {
        super.onResume()
        ((application as HouseManualApp).container.entitlements as? RevenueCatEntitlements)?.activity = this
    }

    override fun onPause() {
        ((application as HouseManualApp).container.entitlements as? RevenueCatEntitlements)?.activity = null
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_OPEN_EMERGENCY) emergencyRequests++
        readReminderRequest(intent)
        offerImport((application as HouseManualApp).container, intent)
    }

    private fun readReminderRequest(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_REMINDERS) return
        reminderRequest = ReminderRequest(intent.getStringExtra(EXTRA_TASK_ID), (reminderRequest?.serial ?: 0) + 1)
    }

    private fun offerImport(container: AppContainer, intent: Intent?) {
        val uri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return
        container.pendingImportUri.value = uri.toString()
    }

    companion object {
        const val ACTION_OPEN_EMERGENCY = "com.kurtz.homerecord.housemanual.OPEN_EMERGENCY"
        const val ACTION_OPEN_REMINDERS = "com.kurtz.homerecord.housemanual.OPEN_REMINDERS"
        const val EXTRA_TASK_ID = "taskId"
    }
}
