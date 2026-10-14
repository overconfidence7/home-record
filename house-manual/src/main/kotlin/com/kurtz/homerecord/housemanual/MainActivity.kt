package com.kurtz.homerecord.housemanual

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kurtz.homerecord.housemanual.nav.HomeRecordNavHost
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as HouseManualApp).container
        setContent {
            HomeRecordTheme { HomeRecordNavHost(container) }
        }
    }
}
