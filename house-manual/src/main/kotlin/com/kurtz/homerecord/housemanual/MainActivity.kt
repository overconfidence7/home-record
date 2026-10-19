package com.kurtz.homerecord.housemanual

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.res.stringResource
import com.kurtz.homerecord.coreui.EmptyAppScreen
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeRecordTheme(Brand.HouseManual) {
                EmptyAppScreen(stringResource(R.string.app_name))
            }
        }
    }
}
