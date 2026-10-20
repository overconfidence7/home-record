package com.kurtz.homerecord.fieldlog

import android.content.Intent
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
        setContent {
            HomeRecordTheme(Brand.FieldLog) {
                CompositionLocalProvider(LocalTagPrefix provides "fl") {
                    Box(Modifier.semantics { testTagsAsResourceId = true }) {
                        FieldLogApp(container)
                    }
                }
            }
        }
    }
}
