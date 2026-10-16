package com.kurtz.homerecord.fieldlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kurtz.homerecord.fieldlog.nav.FieldLogNavHost
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as FieldLogApp).container
        setContent {
            HomeRecordTheme { FieldLogNavHost(container) }
        }
    }
}
