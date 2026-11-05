package com.kurtz.homerecord.fieldlog

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kurtz.homerecord.housemodel.ui.LocalUnitSystem
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.fieldlog.push.PushIntents
import com.kurtz.homerecord.fieldlog.widget.ClockWidget

class MainActivity : ComponentActivity() {
    private val container get() = (application as HomeRecordApplication).container

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleJoinLink(intent)
        handleWidget(intent)
        handlePush(intent)
    }

    /** A tapped push notification opens its task, day, change order or week once the tabs are up (T047b2). */
    private fun handlePush(intent: Intent?) {
        PushIntents.messageOf(intent)?.let { container.pendingPush.value = it }
    }

    /** The clock widget's button opens the Clock tab (AC-F17). */
    private fun handleWidget(intent: Intent?) {
        if (intent?.getBooleanExtra(ClockWidget.OPEN_CLOCK.name, false) == true) container.tabRequests.request("clock")
    }

    /** `fieldlog://join/<code>` from the invite page prefills the join form. */
    private fun handleJoinLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "fieldlog" && data.host == "join") {
            data.lastPathSegment?.let { container.pendingJoinCode.value = it }
        }
    }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleJoinLink(intent)
        if (savedInstanceState == null) {
            handleWidget(intent)
            handlePush(intent)
        }
        setContent {
            HomeRecordTheme(Brand.FieldLog) {
                val units by container.unitsStore.units.collectAsState()
                CompositionLocalProvider(LocalTagPrefix provides "fl", LocalUnitSystem provides units) {
                    Box(Modifier.semantics { testTagsAsResourceId = true }) {
                        FieldLogApp(container)
                    }
                }
            }
        }
    }
}
