package com.kurtz.homerecord.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.StrictMode

/** Debug builds only: log (never crash on) main-thread disk/network access and leaked closeables, so QA can read them from logcat tag StrictMode. */
object DebugStrictMode {
    fun enableIfDebuggable(context: Context) {
        if (!isDebuggable(context)) return
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects().penaltyLog().build())
    }

    internal fun isDebuggable(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
