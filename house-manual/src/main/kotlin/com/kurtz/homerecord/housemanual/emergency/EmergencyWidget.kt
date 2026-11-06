package com.kurtz.homerecord.housemanual.emergency

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.ColorFilter
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kurtz.homerecord.housemanual.HomeRecordApplication
import com.kurtz.homerecord.housemanual.MainActivity
import com.kurtz.homerecord.housemanual.R
import kotlinx.coroutines.flow.first

/** 2×1 home-screen widget: "Emergency" and the shutoff count; a tap opens HM-Emergency (AC-H11). Reads Room only. */
class EmergencyWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val text = text(context, load(context))
        provideContent { GlanceTheme { Content(context, text) } }
    }

    /** The home's shutoff count, or null before a home exists. */
    private suspend fun load(context: Context): Int? {
        val container = (context.applicationContext as HomeRecordApplication).container
        val home = container.homes.observeHome().first() ?: return null
        return container.items.countShutoffs(home.id)
    }

    @Composable
    private fun Content(context: Context, line: String) {
        val open = actionStartActivity(ComponentName(context, MainActivity::class.java), actionParametersOf(OPEN_EMERGENCY to true))
        Row(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).padding(horizontal = 12.dp).clickable(open),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                ImageProvider(R.drawable.ic_tile_emergency), contentDescription = null, modifier = GlanceModifier.size(24.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            )
            Spacer(GlanceModifier.width(8.dp))
            Column {
                Text(
                    context.getString(R.string.emergency_title),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
                Text(line, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
            }
        }
    }

    companion object {
        /** Glance puts the key name on the launch intent as the extra, the same one the tile uses. */
        val OPEN_EMERGENCY = ActionParameters.Key<Boolean>(EmergencyEntry.EXTRA)

        /** The count line: "3 shutoffs", or "No shutoffs yet" (also before a home exists). */
        fun text(context: Context, count: Int?): String =
            if (count == null || count == 0) context.getString(R.string.widget_emergency_none)
            else context.resources.getQuantityString(R.plurals.widget_emergency_count, count, count)
    }
}

class EmergencyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EmergencyWidget()
}
