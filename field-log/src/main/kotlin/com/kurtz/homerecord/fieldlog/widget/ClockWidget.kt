package com.kurtz.homerecord.fieldlog.widget

import android.content.Context
import android.content.ComponentName
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.Button
import com.kurtz.homerecord.core.auth.AuthState
import com.kurtz.homerecord.core.auth.JwtSubject
import com.kurtz.homerecord.fieldlog.HomeRecordApplication
import com.kurtz.homerecord.fieldlog.MainActivity
import com.kurtz.homerecord.fieldlog.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date

/** 2x2 home-screen widget: clock status and a Clock in / Clock out button that opens FL-Clock (AC-F17). */
class ClockWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = load(context)
        provideContent { GlanceTheme { Content(context, state) } }
    }

    private suspend fun load(context: Context): ClockWidgetState {
        val container = (context.applicationContext as HomeRecordApplication).container
        // Sign-in is restored off the main thread at cold start; give it a moment.
        val auth = withTimeoutOrNull(3_000) { container.auth.state.first { it !is AuthState.Restoring } } ?: return ClockWidgetState.SignedOut
        val memberId = (auth as? AuthState.SignedIn)?.token?.let(JwtSubject::of) ?: return ClockWidgetState.SignedOut
        val companyId = container.company.current?.companyId ?: return ClockWidgetState.SignedOut
        val status = container.clock.status(companyId, memberId)
        val names = container.db.jobDao().listFor(companyId).associate { it.id to it.name }
        return ClockWidgetStateBuilder.build(status, names)
    }

    @Composable
    private fun Content(context: Context, state: ClockWidgetState) {
        val open = actionStartActivity(ComponentName(context, MainActivity::class.java), actionParametersOf(OPEN_CLOCK to true))
        val (headline, detail, button) = when (state) {
            ClockWidgetState.SignedOut -> Triple(context.getString(R.string.widget_signed_out), null, context.getString(R.string.widget_open))
            ClockWidgetState.Out -> Triple(context.getString(R.string.clock_out_status), null, context.getString(R.string.clock_in))
            is ClockWidgetState.Working -> Triple(
                context.getString(R.string.clock_working_status),
                detail(context, state.jobName, state.sinceAt, R.string.widget_since),
                context.getString(R.string.clock_out),
            )
            is ClockWidgetState.OnBreak -> Triple(
                context.getString(R.string.clock_break_status),
                detail(context, state.jobName, state.sinceAt, R.string.widget_break_since),
                context.getString(R.string.clock_out),
            )
        }
        Column(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).padding(12.dp).clickable(open),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                headline, GlanceModifier.fillMaxWidth(),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
            if (detail != null) {
                Text(
                    detail, GlanceModifier.fillMaxWidth(), maxLines = 2,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            Button(button, open, GlanceModifier.fillMaxWidth())
        }
    }

    private fun detail(context: Context, job: String?, since: Long, format: Int): String {
        val time = DateFormat.getTimeFormat(context).format(Date(since))
        val line = context.getString(format, time)
        return if (job == null) line else "$job\n$line"
    }

    companion object {
        /** Extra on the launch intent (Glance uses the key name as the extra name). */
        val OPEN_CLOCK = ActionParameters.Key<Boolean>("open_clock")
    }
}

class ClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClockWidget()
}
