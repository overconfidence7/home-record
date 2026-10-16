package com.kurtz.homerecord.fieldlog

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kurtz.homerecord.fieldlog.nav.FieldLogNavHost
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as FieldLogApp).container

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        container.shutterKeys.onKeyDown(keyCode, event.repeatCount) || super.onKeyDown(keyCode, event)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as FieldLogApp).container
        setContent {
            HomeRecordTheme { FieldLogNavHost(container) }
        }
    }
}
