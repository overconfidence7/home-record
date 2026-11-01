package com.kurtz.homerecord.fieldlog.push

import android.content.SharedPreferences

/**
 * When Field Log asks for the notification permission (Android 13+, T047b2): the first time the person reaches the
 * company's tabs, the moment pushes start to matter (tasks get assigned and schedules change from then on). Asked once
 * per install; never when the system grants it without asking (Android 12 and older), when it is already granted, or
 * when this build has no push at all (no Firebase config), since then no notification could ever come.
 */
object NotificationAsk {
    fun shouldAsk(sdkInt: Int, granted: Boolean, askedBefore: Boolean, pushEnabled: Boolean): Boolean =
        pushEnabled && sdkInt >= TIRAMISU && !granted && !askedBefore

    private const val TIRAMISU = 33
}

/** Remembers that the ask was shown. Device-level, so signing out does not ask again. */
class NotificationAskStore(private val prefs: SharedPreferences) {
    fun askedBefore(): Boolean = prefs.getBoolean(KEY, false)

    fun markAsked() {
        prefs.edit().putBoolean(KEY, true).apply()
    }

    private companion object {
        const val KEY = "notification_permission_asked"
    }
}
