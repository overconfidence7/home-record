package com.kurtz.homerecord.fieldlog.qa

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.kurtz.homerecord.fieldlog.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Debug-only seed entry point a Maestro flow can open after its `clearState` wiped the suite's seed broadcast
 * (cr:qa-seed-wiped): `openLink: "homerecord-qa-fl://seed?scenario=regression"` (qa/flows/field-log/_seed.yaml).
 * Needs the dev backend at the app's API_BASE_URL. Seeds, then opens the app's own MainActivity in a new task; a failed seed stays on screen as "Seed failed: …" so the
 * flow fails at its next wait with the reason in the failure shot.
 */
class SeedActivity : ComponentActivity() {
    @SuppressLint("SetTextI18n") // debug-only status line for the flow's failure shot, never shown to users
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply { text = "Seeding…"; setPadding(48, 160, 48, 48) }
        setContentView(status)
        if (savedInstanceState != null) return
        val scenario = intent.data?.getQueryParameter("scenario") ?: "regression"
        lifecycleScope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    if (QaSeeder.run(applicationContext, scenario)) null else "unknown scenario '$scenario'"
                } catch (e: Exception) {
                    Log.e(TAG, "seed '$scenario' failed", e)
                    e.toString()
                }
            }
            if (error == null) {
                startActivity(Intent(this@SeedActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            } else {
                status.text = "Seed failed: $error"
            }
        }
    }

    private companion object {
        const val TAG = "QaSeed"
    }
}
