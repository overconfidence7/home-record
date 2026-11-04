package com.kurtz.homerecord.fieldlog.push

import android.content.Intent
import com.kurtz.homerecord.core.company.RoleGates
import com.kurtz.homerecord.core.push.PushMessage
import com.kurtz.homerecord.coreui.shell.TabRequest
import com.kurtz.homerecord.fieldlog.JobKey
import com.kurtz.homerecord.fieldlog.TaskFormKey
import com.kurtz.homerecord.fieldlog.TimesheetsKey
import com.kurtz.homerecord.fieldlog.jobs.JobTab

/**
 * Where tapping a push notification lands (AC-F36, AC-F42, T047b2). Back from the screen walks to that tab's root.
 *
 * - Task assigned: the job's Tasks tab; someone who manages the job's tasks lands in the task form over it, the way a
 *   task row on Today opens.
 * - Schedule changed: the Schedule tab on the notified day ([scheduleDay]).
 * - Change order signed: the job's Changes tab.
 * - Timesheet approved: Owner and Office get Timesheets on that week; everyone else sees their own week on Clock.
 *
 * A push for another company than the one this phone is in (sent before a switch) just opens Today.
 */
object PushDestination {
    fun of(message: PushMessage, companyId: String, gates: RoleGates): TabRequest {
        if (message.companyId != companyId) return TabRequest(TODAY)
        return when (message) {
            is PushMessage.TaskAssigned -> when {
                message.jobId.isBlank() -> TabRequest(TODAY)
                gates.canManageTasks(message.jobId) ->
                    TabRequest(JOBS, listOf(JobKey(message.jobId, JobTab.TASKS.name), TaskFormKey(message.jobId, message.taskId)))
                else -> TabRequest(JOBS, listOf(JobKey(message.jobId, JobTab.TASKS.name)))
            }
            is PushMessage.ScheduleChanged -> TabRequest(SCHEDULE)
            is PushMessage.ChangeOrderSigned ->
                if (message.jobId.isBlank()) TabRequest(TODAY) else TabRequest(JOBS, listOf(JobKey(message.jobId, JobTab.CHANGES.name)))
            is PushMessage.TimesheetApproved ->
                if (gates.role.isAdmin) TabRequest(MORE, listOf(TimesheetsKey(message.weekStart.toString()))) else TabRequest(CLOCK)
        }
    }

    /** The day FL-Schedule should show for a tapped schedule push in this company (AC-F42); null for any other push. */
    fun scheduleDay(message: PushMessage, companyId: String): java.time.LocalDate? =
        (message as? PushMessage.ScheduleChanged)?.takeIf { it.companyId == companyId }?.date

    private const val TODAY = "today"
    private const val JOBS = "jobs"
    private const val CLOCK = "clock"
    private const val SCHEDULE = "schedule"
    private const val MORE = "more"
}

/** The push data a notification tap carries back to MainActivity ([PushNotifier] writes it). */
object PushIntents {
    fun put(intent: Intent, data: Map<String, String>): Intent = intent.apply {
        action = PushNotifier.ACTION_OPEN_PUSH
        data.forEach { (k, v) -> putExtra(PushNotifier.EXTRA_PREFIX + k, v) }
    }

    /** The message a tapped notification was about, or null for any other intent. */
    fun messageOf(intent: Intent?): PushMessage? {
        if (intent?.action != PushNotifier.ACTION_OPEN_PUSH) return null
        val extras = intent.extras ?: return null
        val data = extras.keySet()
            .filter { it.startsWith(PushNotifier.EXTRA_PREFIX) }
            .mapNotNull { key -> extras.getString(key)?.let { key.removePrefix(PushNotifier.EXTRA_PREFIX) to it } }
            .toMap()
        return PushMessage.parse(data)
    }
}
