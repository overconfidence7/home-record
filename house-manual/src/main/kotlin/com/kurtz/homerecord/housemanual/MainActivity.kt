package com.kurtz.homerecord.housemanual

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kurtz.homerecord.housemanual.nav.HomeRecordNavHost
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

class MainActivity : ComponentActivity() {
    private var emergencyRequests by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as HouseManualApp).container
        if (savedInstanceState == null && intent?.action == ACTION_OPEN_EMERGENCY) emergencyRequests++
        setContent {
            HomeRecordTheme { HomeRecordNavHost(container, emergencyRequests) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_OPEN_EMERGENCY) emergencyRequests++
    }

    companion object {
        const val ACTION_OPEN_EMERGENCY = "com.kurtz.homerecord.housemanual.OPEN_EMERGENCY"
    }
}
