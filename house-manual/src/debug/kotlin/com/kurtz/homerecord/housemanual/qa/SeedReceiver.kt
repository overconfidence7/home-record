package com.kurtz.homerecord.housemanual.qa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kurtz.homerecord.housemanual.HomeRecordApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Debug-only: `adb shell am broadcast -a com.kurtz.homerecord.housemanual.qa.SEED -p <pkg> --es scenario regression|reset`.
 * Result code 1 means done; the device suite greps for `result=1`.
 */
class SeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as HomeRecordApplication).container
        val scenario = intent.getStringExtra("scenario") ?: "regression"
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val ok = try {
                when (scenario) {
                    "regression" -> {
                        val pdf = context.assets.open("qa/sample.pdf").use { it.readBytes() }
                        RegressionSeed.seed(container.db, container.homes, documentsDir = File(context.filesDir, "documents"), pdf = pdf)
                        true
                    }
                    "reset" -> {
                        RegressionSeed.reset(container.db)
                        File(context.filesDir, "documents").deleteRecursively()
                        true
                    }
                    else -> false
                }
            } catch (e: Exception) {
                false
            }
            pending.setResult(if (ok) 1 else 0, scenario, null)
            pending.finish()
        }
    }
}
