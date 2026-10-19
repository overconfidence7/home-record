package com.kurtz.homerecord.fieldlog

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as HomeRecordApplication).container

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleJoinLink(intent)
    }

    /** `fieldlog://join/<code>` from the invite page prefills the join form. */
    private fun handleJoinLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "fieldlog" && data.host == "join") {
            data.lastPathSegment?.let { container.pendingJoinCode.value = it }
        }
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
        setContent {
            HomeRecordTheme(Brand.FieldLog) {
                FieldLogApp(container)
            }
        }
    }
}
