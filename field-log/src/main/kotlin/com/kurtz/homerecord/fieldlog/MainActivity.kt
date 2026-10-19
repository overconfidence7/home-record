package com.kurtz.homerecord.fieldlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as HomeRecordApplication).container

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
        setContent {
            HomeRecordTheme(Brand.FieldLog) {
                FieldLogApp(container.auth)
            }
        }
    }
}
