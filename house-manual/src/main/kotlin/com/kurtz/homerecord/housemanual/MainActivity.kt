package com.kurtz.homerecord.housemanual

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.res.stringResource
import com.kurtz.homerecord.ui.HomeRecordShellScreen
import com.kurtz.homerecord.ui.theme.HomeRecordTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeRecordTheme {
                HomeRecordShellScreen(title = stringResource(R.string.shell_title))
            }
        }
    }
}
