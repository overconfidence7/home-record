package com.kurtz.homerecord.housemanual.emergency

import android.content.Context
import android.content.Intent
import com.kurtz.homerecord.housemanual.MainActivity

/**
 * The way in to HM-Emergency from outside the app (AC-H11): the quick settings tile and the home-screen widget start
 * [MainActivity] with [EXTRA] set, and the activity asks the shell for Home → Emergency.
 */
object EmergencyEntry {
    /** Boolean extra; short so a Maestro `launchApp` can pass it as an argument. */
    const val EXTRA = "open_emergency"

    fun intent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .putExtra(EXTRA, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** True when [intent] came from the tile or widget (or a flow passing `open_emergency`). */
    fun wants(intent: Intent?): Boolean = intent?.extras?.let { extras ->
        // Maestro passes launch arguments as strings; the tile and widget pass a boolean.
        @Suppress("DEPRECATION")
        when (val v = extras.get(EXTRA)) {
            is Boolean -> v
            is String -> v.equals("true", ignoreCase = true)
            else -> false
        }
    } ?: false
}
