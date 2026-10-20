package com.kurtz.homerecord.core.sync

import android.content.Context
import com.kurtz.homerecord.core.Ids

/** The install's sync device id, created once; the server dedupes pushes by (device, outbox id). */
object DeviceIdStore {
    fun get(context: Context): String {
        val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
        return prefs.getString("deviceId", null) ?: Ids.newId().also { prefs.edit().putString("deviceId", it).apply() }
    }
}
