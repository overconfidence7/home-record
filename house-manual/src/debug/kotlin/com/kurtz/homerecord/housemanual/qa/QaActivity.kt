package com.kurtz.homerecord.housemanual.qa

import android.content.pm.ActivityInfo
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

/**
 * Debug-only host for the screen catalog. Start it with action `com.kurtz.homerecord.housemanual.qa.OPEN_SCREEN`,
 * extra `screen=<catalog id>` and optionally `landscape=true`. Dark mode and font scale come from the system
 * settings, so the crawler and the device tour flip those instead.
 */
class QaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent.getBooleanExtra("landscape", false)) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val entry = ScreenCatalog.find(intent.getStringExtra("screen"))
        setContent {
            CompositionLocalProvider(LocalTagPrefix provides "hm") {
                Box(Modifier.semantics { testTagsAsResourceId = true }) { entry?.content?.invoke() }
            }
        }
    }
}
