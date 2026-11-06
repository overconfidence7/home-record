package com.kurtz.homerecord.housemanual.reminders

import android.content.Context
import android.content.Intent
import com.kurtz.homerecord.housemanual.MainActivity
import com.kurtz.homerecord.housemanual.emergency.EmergencyEntry

/** The way in to HM-Reminders from the 08:00 notification (AC-C50, AC-H12): MainActivity with [EXTRA] set opens the tab. */
object ReminderEntry {
    /** Boolean extra; short so a Maestro `launchApp` can pass it as an argument. */
    const val EXTRA = "open_reminders"

    fun intent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .putExtra(EXTRA, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** True when [intent] came from the reminder notification (or a flow passing `open_reminders`). */
    fun wants(intent: Intent?): Boolean = EmergencyEntry.flag(intent, EXTRA)
}
