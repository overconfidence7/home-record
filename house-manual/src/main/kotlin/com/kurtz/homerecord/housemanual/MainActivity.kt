package com.kurtz.homerecord.housemanual

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as HomeRecordApplication).container

    override fun onStart() {
        super.onStart()
        container.syncCoordinator.setForeground(true)
    }

    override fun onStop() {
        container.syncCoordinator.setForeground(false)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        container.activityContext = this
    }

    override fun onPause() {
        container.activityContext = null
        super.onPause()
    }

    /** A `.homerecord` handed to House Manual by Open with or Share (AC-H17). */
    private val incoming = androidx.compose.runtime.mutableStateOf<android.net.Uri?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        incoming.value = handoffUri(intent)
        openEmergency(intent)
        openReminders(intent)
    }

    /** The 08:00 reminder notification: the Reminders tab (AC-C50, AC-H12). */
    private fun openReminders(intent: android.content.Intent?) {
        if (com.kurtz.homerecord.housemanual.reminders.ReminderEntry.wants(intent)) container.tabRequests.request("reminders")
    }

    /** The quick settings tile or the widget: Home → Emergency, once the tabs are up (AC-H11). */
    private fun openEmergency(intent: android.content.Intent?) {
        if (com.kurtz.homerecord.housemanual.emergency.EmergencyEntry.wants(intent)) container.tabRequests.request("home", listOf(EmergencyKey))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            incoming.value = handoffUri(intent)
            openEmergency(intent)
            openReminders(intent)
        }
        enableEdgeToEdge()
        setContent {
            HomeRecordTheme(Brand.HouseManual) {
                CompositionLocalProvider(LocalTagPrefix provides "hm") {
                    Box(Modifier.semantics { testTagsAsResourceId = true }) {
                        HouseManualApp(container, incoming.value, onIncomingHandled = { incoming.value = null })
                    }
                }
            }
        }
    }
}

/** The file of an Open with (VIEW) or Share (SEND) intent, or null for a normal launch. */
internal fun handoffUri(intent: android.content.Intent?): android.net.Uri? = when (intent?.action) {
    android.content.Intent.ACTION_VIEW -> intent.data
    android.content.Intent.ACTION_SEND -> androidx.core.content.IntentCompat.getParcelableExtra(intent, android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
    else -> null
}
