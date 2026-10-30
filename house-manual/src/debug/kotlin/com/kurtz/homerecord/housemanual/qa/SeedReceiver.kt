package com.kurtz.homerecord.housemanual.qa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug-only: `adb shell am broadcast --include-stopped-packages -a com.kurtz.homerecord.housemanual.qa.SEED -p <pkg>
 * --es scenario regression|long-list|reset`. Result code 1 means done; the device suite greps for `result=1`.
 * Flows seed through [SeedActivity] instead, after their `clearState`.
 */
class SeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scenario = intent.getStringExtra("scenario") ?: "regression"
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val ok = try {
                QaSeeder.run(context, scenario)
            } catch (e: Exception) {
                Log.e(TAG, "seed '$scenario' failed", e)
                false
            }
            pending.setResult(if (ok) 1 else 0, scenario, null)
            pending.finish()
        }
    }

    private companion object {
        const val TAG = "QaSeed"
    }
}
