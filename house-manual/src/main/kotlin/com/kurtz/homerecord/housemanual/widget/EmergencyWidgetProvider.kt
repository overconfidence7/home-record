package com.kurtz.homerecord.housemanual.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.kurtz.homerecord.housemanual.MainActivity
import com.kurtz.homerecord.housemanual.R

/** AC-86/AC-87: 2x1 "House emergency" widget showing the shutoff count; tap opens HM-Emergency. */
class EmergencyWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        // Count is pushed by [refresh]; draw the last known one until then.
        appWidgetIds.forEach { manager.updateAppWidget(it, buildViews(context, lastCount)) }
    }

    companion object {
        @Volatile private var lastCount = 0

        /** Redraws every placed widget with [count] shutoffs. */
        fun refresh(context: Context, count: Int) {
            lastCount = count
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, EmergencyWidgetProvider::class.java))
            ids.forEach { manager.updateAppWidget(it, buildViews(context, count)) }
        }

        fun buildViews(context: Context, count: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_emergency)
            views.setTextViewText(
                R.id.widget_count,
                context.resources.getQuantityString(R.plurals.home_shutoff_count, count, count),
            )
            val intent = Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_EMERGENCY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            return views
        }
    }
}
